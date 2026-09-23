# Execution & mapping

This page covers how parameters travel with a statement, how statements are
executed and how result rows become Java objects.

## Rendering

Every statement can be rendered without being executed:

```java
RenderedSql r = select(p.lastName).from(p).where(eq(p.status, val("A"))).build();
r.sql();     // SELECT p.LAST_NAME FROM PERSON p WHERE p.STATUS = :lq0
r.binds();   // BindMap{lq0=A}

String sql = query.buildSql();       // SQL only
BindMap binds = query.getBinds();    // parameters only
query.toString();                    // same as buildSql()
```

The SQL uses **named placeholders** (`:name`). Every render creates a new
context, so the generated names (`lq0`, `lq1`, …) are deterministic and the
same builder can be rendered or executed any number of times.

## Parameters

There are three ways to supply values, and you can mix them freely.

### 1. `val(value)`: automatic (recommended)

```java
.where(eq(p.status, val("ACTIVE")), ge(p.age, 18))   // non-String values are bound automatically
```

### 2. `Binds`: typed, inline placeholders

```java
Binds b = new Binds();
select(p.lastName).from(p)
    .where(eq(p.personNr, b.setLong(personNr)), eq(p.status, b.setString("ACTIVE")))
    .bind(b)
    .multiple();
// ... WHERE p.PERSON_NR = :p0 AND p.STATUS = :p1
```

The single-argument setters (`setLong`, `setInt`, `setDouble`,
`setBigDecimal`, `setString`, `setBoolean`, `setDate`, `setDateTime`, `set`)
register the value under an automatic name and return its placeholder. The
two-argument setters (`b.setString("status", "A")`) use your own name.
`Binds` is read when the statement is rendered, so values added after
`.bind(b)` are still included.

### 3. Named placeholders

```java
select(p.lastName).from(p)
    .where(eq(p.status, ":status"), ge(p.age, ":minAge"))
    .bind("status", "ACTIVE")
    .bind("minAge", 18)
```

Bind parameters of sub-queries and CTEs are merged into the outer statement
automatically.

## Executors

A `SqlExecutor` receives the rendered SQL and its `BindMap`:

```java
public interface SqlExecutor {
    Object[][] select(String sql, BindMap binds);   // queries and RETURNING
    int execute(String sql, BindMap binds);         // INSERT / UPDATE / DELETE / TRUNCATE
}
```

The executor for a statement is chosen in this order:

1. `.executor(executor)` on the statement
2. `LxrinQL.setDefaultExecutor(executor)` (application-wide)
3. the first `SqlExecutor` registered via `ServiceLoader`
   (`META-INF/services/ch.lxrin.ql.exec.SqlExecutor`)

If none is found, an `IllegalStateException` explains how to configure one.

### JdbcSqlExecutor

The built-in executor for plain JDBC:

```java
new JdbcSqlExecutor(dataSource)                 // new connection per statement, closed afterwards
JdbcSqlExecutor.forConnection(connection)       // fixed connection, never closed by the executor
new JdbcSqlExecutor(connectionProvider)         // your own acquire/release strategy
```

What it does:

- converts `:name` placeholders to JDBC `?` and leaves string literals,
  quoted identifiers, dollar-quoted strings, comments and `::` casts
  untouched
- expands a `Collection` parameter to `?, ?, ?` (`IN (:ids)`)
- sends Java arrays as SQL arrays (`text[]`, `int8[]`, `uuid[]`, …), `Instant`
  as `timestamptz` and enums by name
- returns `date` as `LocalDate`, `timestamp` as `LocalDateTime`, `timestamptz`
  as `OffsetDateTime`, `json`/`jsonb` as `String` and SQL arrays as Java arrays
- wraps `SQLException` in the unchecked `SqlExecutionException`, whose message
  contains the SQL and whose `getSqlState()` returns the SQLSTATE

Override `toJdbcValue(..)` or `setParameters(..)` to customise type handling.

### Transactions

`JdbcSqlExecutor(dataSource)` works in auto-commit mode. For a transaction,
use the connection that owns it.

**Manually:**

```java
try (Connection con = dataSource.getConnection()) {
    con.setAutoCommit(false);
    SqlExecutor tx = JdbcSqlExecutor.forConnection(con);
    ...statements with .executor(tx)...
    con.commit();
}
```

**Framework-managed transactions:** plug the framework's "current connection"
into a `ConnectionProvider`. With Spring, for example:

```java
SqlExecutor executor = new JdbcSqlExecutor(new JdbcSqlExecutor.ConnectionProvider() {
    public Connection acquire() { return DataSourceUtils.getConnection(dataSource); }
    public void release(Connection c) { DataSourceUtils.releaseConnection(c, dataSource); }
});
LxrinQL.setDefaultExecutor(executor);   // statements now join @Transactional transactions
```

A transaction-aware `DataSource` proxy (Spring's `TransactionAwareDataSourceProxy`,
the container `DataSource` in Jakarta EE, …) passed to
`new JdbcSqlExecutor(dataSource)` has the same effect.

### Custom executors

Any framework that runs SQL with named parameters can be connected in a few
lines. The executor only forwards the SQL and the `BindMap`:

```java
public class MyFrameworkExecutor implements SqlExecutor {
    @Override
    public Object[][] select(String sql, BindMap binds) {
        return MyFramework.sql().query(sql, binds.asMap());
    }

    @Override
    public int execute(String sql, BindMap binds) {
        return MyFramework.sql().update(sql, binds.asMap());
    }
}
```

If your framework only understands positional `?` parameters, convert the
statement first with `NamedParameterSql.parse(sql, binds.asMap())`. It
returns the JDBC SQL and the ordered values.

If you add a `META-INF/services/ch.lxrin.ql.exec.SqlExecutor` file that
contains the class name, the executor becomes the default automatically.

## Result mapping

`SelectQuery<T>` and `returning(..).multiple(Type.class)` map every row
according to the target type:

| Target type | Mapping |
|---|---|
| `Object[]` | the raw row |
| simple types: `String`, numbers, `Boolean`, `UUID`, enums, `java.time.*`, arrays | first column, converted |
| `record` | canonical constructor, **columns in select order** |
| any other class | bean with a no-arg constructor; each **named** select item is written to the matching setter or field |

Names come from column aliases (`p.firstName` → `firstName`) and from
`.as("name")`. Matching ignores case and underscores, so `FIRST_NAME`,
`firstname` and `firstName` all match. Values are converted where needed:
numbers are widened or narrowed, `java.sql.*` becomes `java.time.*`, strings
become enums or UUIDs, and arrays become arrays or lists of another element
type.

```java
record Row(long id, String name, LocalDate since) {}
List<Row> rows = select(Row.class, p.personNr, p.lastName, p.createdAt).from(p).multiple();

public class PersonBean { private Long personNr; private String lastName; /* setters */ }
List<PersonBean> beans = createContribution(PersonBean.class).select(p.personNr, p.lastName).from(p).multiple();

Long count = createContribution(Long.class).select(count()).from(p).single();
```

For complete control, provide a `RowMapper`:

```java
createContribution(PersonDto.class)
    .select(p.personNr, p.firstName, p.lastName)
    .from(p)
    .mapWith(row -> new PersonDto((Long) row[0], row[1] + " " + row[2]))
    .multiple();
```

`ResultMapping.convert(value, Type.class)` is public if you need the same
conversions in your own mapper.

## Testing

Because execution goes through an interface, statements can be tested
without a database:

```java
SqlExecutor executor = mock(SqlExecutor.class);
when(executor.select(anyString(), any())).thenReturn(new Object[][]{{1L, "Ada"}});

List<PersonRow> rows = select(PersonRow.class, p.personNr, p.lastName).from(p).executor(executor).multiple();

verify(executor).select(eq("SELECT p.PERSON_NR, p.LAST_NAME FROM PERSON p"), any());
```

Or assert on the generated SQL directly with `buildSql()` and `getBinds()`.

LxrinQL's own PostgreSQL integration test (`PostgresIntegrationTest`) shows
how to run statements against a real database. It is enabled by the
`LXRIN_QL_PG_URL` environment variable.
