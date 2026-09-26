# Migrating from 2.x to 3.0

3.0 is a new API. Its main rule is the reverse of 2.x: **a Java value is always a
bind parameter**, and SQL text is only possible through `Sql`. Every 2.x construct
whose meaning would change fails to compile in 3.0; nothing changes silently.

## Migrate step by step, side by side

The 3.0 packages (`ch.lxrin.ql.dsl`, `.schema`, `.types`, `.runtime`, …) do not
overlap with the 2.x packages (`ch.lxrin.ql`, `.condition`, `.expr`, `.query`,
`.table`, `.exec`, …). Both versions can therefore be on the classpath at the same
time, and you can migrate one class at a time.

1. **Add 3.0 next to 2.x**:

   ```kotlin
   dependencies {
       implementation("ch.lxrin:lxrin-ql:2.0.0")          // until the last 2.x query is gone
       implementation("ch.lxrin:lxrin-ql-core:3.0.1")
   }
   ```

2. **Share transactions.** Both versions have to use the transaction's connection.
   - With Spring: add `lxrin-ql-spring` for 3.0, and for 2.x use the
     `DataSourceUtils`-based `ConnectionProvider` from the 2.x docs.
   - Without Spring: run 2.x statements with
     `.executor(JdbcSqlExecutor.forConnection(con))` on the connection of the
     transaction.
3. **Generate the tables** with the Gradle or Maven plugin from your existing
   migrations (see [Code generation](code-generation.md)). The generated tables
   replace your `TableDef` classes, which stay until nothing uses them.
4. **Migrate queries file by file** with the table below. The compiler shows what is
   left.
5. **Move CRUD code to repositories** where it helps (`save`, `saveAll`, `findById`, …).
6. **Remove 2.x** and add `LxrinArchRules.noRawSql()` to your architecture tests so that
   no SQL strings come back.

## Mapping

| 2.x | 3.0 |
|---|---|
| `ch.lxrin:lxrin-ql` | `ch.lxrin:lxrin-ql-core` (+ `-codegen`, `-gradle-plugin`, `-maven-plugin`, `-spring`, `-test`, `-bom`) |
| `import static ch.lxrin.ql.LxrinQL.*` | `import static ch.lxrin.ql.dsl.Dsl.*` and `import static com.example.db.Tables.*` |
| `class PersonTable extends TableDef { Column lastName = column("LAST_NAME"); }` | generated `PersonTable` with `StringColumn LAST_NAME` |
| `table("ADDRESS", "a").col("CITY")` | `Sql.table("address").field("city", SqlTypes.TEXT)` (better: generate the table) |
| `p.lastName` (untyped) | `PERSON.LAST_NAME` (a `StringColumn`) |
| `eq(p.status, val("ACTIVE"))` | `PERSON.STATUS.eq("ACTIVE")` |
| `ge(p.age, 18)` | `PERSON.AGE.ge(18)` |
| `eq(p.lastName, "p.FIRST_NAME")` (a SQL fragment) | `PERSON.LAST_NAME.eq(PERSON.FIRST_NAME)` |
| `eq(x, null)` → exception | `x.isNull()`; `eqOrIsNull(value)` for optional values |
| `in(p.id, idList)` (one parameter per element) | `PERSON.ID.in(idList)` (one array parameter, `= ANY(?)`) |
| `ilike(p.lastName, val("%" + s + "%"))` | `PERSON.LAST_NAME.containsIgnoreCase(s)` (escapes `%` and `_`) |
| `and(a, b)`, `or(a, b)`, `a.and(b)` | the same, typed: `Condition.and(..)`, `a.and(b)` |
| `where(a, or(), b)` (list style with tokens) | `where(a.or(b))` |
| `where("p.DELETED_AT IS NULL")` | `where(PERSON.DELETED_AT.isNull())`, or a `SoftDeletePolicy` |
| `condition("…")`, `raw("…")`, `sql("{0} <-> {1}", …)` | `Sql.condition("…", args)`, `Sql.raw("…", type, args)`; or `Routines` |
| `function("similarity", a, val(b))` | `Routines.function("similarity", …)` once, then `SIMILARITY.call(a, b)` |
| `val(x)`, `inline(x)` | not needed; `param(x)` / `inline(x)` where a field is required |
| `Binds`, `BindMap`, `.bind("name", v)`, `":name"` | removed: values are bound automatically; since 3.1 `b.setString(v)`, `b.setList(..)`, … make a parameter explicit (optional) |
| `createContribution(Type.class).select(..)` / `query(Type.class)` | since 3.1 `createContribution(Type.class, TABLE, (c, b) -> c.select(..).where(..)).fetch()`, or `select(..)` |
| `select(PersonDto.class, p.id, p.name).multiple()` | `select(PERSON.ID, PERSON.NAME).from(PERSON).fetch(PersonDto::new)` |
| bean mapping by alias (reflection) | constructor references (`fetch(Dto::new)`), `RowN` tuples, generated row records |
| `.multiple()` | `.fetch()` |
| `.single()` (first row or `null`) | `.fetchOne()` (exactly one), `.fetchOptional()`, `.fetchFirst()` |
| `.optional()` | `.fetchOptional()` |
| `RowMapper` over `Object[]` | `fetch(Function)` on typed rows |
| `.join(o, eq(o.personNr, p.personNr))` | `.join(ORDERS).on(ORDERS.PERSON_NR.eq(PERSON.PERSON_NR))` or `.onKey(ORDERS.FK_PERSON)` |
| `.join("LEFT JOIN a ON …")` | `.leftJoin(ADDRESS).on(..)` |
| `.with("r", query)` + `"r.total"` | `Cte r = cte("r", query)` + `r.field(total)` |
| `window().rowsBetween(unboundedPreceding(), currentRow())` (strings) | the same, with typed `FrameBound`s |
| `dateTrunc("month", x)`, `extract("YEAR", x)` | `dateTrunc(DatePart.MONTH, x)`, `extract(DatePart.YEAR, x)` |
| `jsonbBuildObject("id", o.id, …)` | `jsonbBuildObject(pair("id", ORDERS.ID), …)` |
| `insertInto(p).set(p.name, val(n)).returning(p.id).single(Long.class)` | `insertInto(PERSON).set(PERSON.NAME, n).returning(PERSON.ID).fetchOne()` |
| `.returning(..).multiple(Changed.class)` | `.returning(a, b).fetch(row -> row.map(Changed::new))` |
| `doUpdateSetExcluded(p.lastName)` | unchanged |
| `LxrinQL.setDefaultExecutor(new JdbcSqlExecutor(ds))` | `QueryContext.setDefault(QueryContext.builder().dataSource(ds).build())` |
| `.executor(JdbcSqlExecutor.forConnection(con))` | `ctx.transaction(() -> …)` or `@Transactional` |
| `SqlExecutor.select/execute(String, BindMap)` | `SqlExecutor.query/update/batch` with typed binds (rarely implemented yourself) |
| `ServiceLoader` discovery of executors | removed; configure a `QueryContext` |
| `SqlExecutionException` + `getSqlState()` | `UniqueViolationException`, `ForeignKeyViolationException`, … (`getSqlState()` still exists) |
| `ResultMapping.convert(..)` | `DataType` / `Converter` |
| `live-templates/LxrinQL.xml` (`qlid`) | removed; keys come from `repository.createKey()` |

## Behaviour that differs

- **Time zones.** Timestamps are converted without the JVM default time zone:
  `timestamptz` is an `Instant` (sent with offset UTC), `timestamp` a `LocalDateTime`.
  2.x converted between them with the default time zone.
- **Results** are read with the column's declared type instead of the JDBC metadata,
  and records are mapped by a constructor reference whose parameter types the
  compiler checks, instead of by position at runtime.
- **`IN` lists** are one array parameter, so the SQL text no longer depends on the
  list size.
- **Rendered SQL** uses `?` placeholders and quotes identifiers only where needed.
  Tables are referenced by name (`app_user.name`) or by the alias you give them
  (`USERS.as("u")`).
- **`UPDATE`/`DELETE` without `WHERE`** still need `.allRows()`. The error is now an
  `InvalidStatementException` when the statement runs.
