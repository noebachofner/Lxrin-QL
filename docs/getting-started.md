# Getting started

This guide takes you from an empty project to your first queries.

## 1. Requirements

- Java 17 or newer
- Gradle 8+ or Maven 3.8+
- A JDBC driver for your database. LxrinQL targets **PostgreSQL**. Portable
  parts such as basic `SELECT`s, joins and standard functions also work on
  other databases.

## 2. Add the dependency

Install the library into your local Maven repository:

```bash
git clone https://github.com/noebachofner/lxrin_ql.git
cd lxrin_ql
./gradlew publishToMavenLocal      # or: mvn install
```

Then add it to your project.

**Gradle (Kotlin DSL)**

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("ch.lxrin:lxrin-ql:2.0.0")
    runtimeOnly("org.postgresql:postgresql:42.7.10")
}
```

**Maven**

```xml
<dependencies>
    <dependency>
        <groupId>ch.lxrin</groupId>
        <artifactId>lxrin-ql</artifactId>
        <version>2.0.0</version>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <version>42.7.10</version>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

## 3. Define your tables

Create one `TableDef` subclass per table. Columns are `public final Column`
fields. Each column's Java alias is derived from its SQL name
(`FIRST_NAME` → `firstName`), and that alias is later used to map results
onto bean properties.

```java
import ch.lxrin.ql.table.Column;
import ch.lxrin.ql.table.TableDef;

public class PersonTable extends TableDef {
    public final Column personNr  = column("PERSON_NR");
    public final Column firstName = column("FIRST_NAME");
    public final Column lastName  = column("LAST_NAME");
    public final Column email     = column("EMAIL");
    public final Column status    = column("STATUS");
    public final Column birthDate = column("BIRTH_DATE");
    public final Column statusCd  = column("STATUS_CD", "statusCode"); // explicit Java alias

    public PersonTable() { this("p"); }
    public PersonTable(String alias) { super("PERSON", alias); }        // for self-joins
}
```

For tables you only need once, create an ad-hoc definition:

```java
Table a = table("ADDRESS", "a");
select(a.col("CITY")).from(a).where(eq(a.col("ZIP"), val("8000")));
```

## 4. Configure an executor

Statements are rendered to SQL and handed to a `SqlExecutor`. The library
ships with `JdbcSqlExecutor`:

```java
import ch.lxrin.ql.LxrinQL;
import ch.lxrin.ql.exec.JdbcSqlExecutor;

// once at startup – one pooled connection per statement, auto-commit
LxrinQL.setDefaultExecutor(new JdbcSqlExecutor(dataSource));
```

To use a single connection (for example inside your own transaction), pass
an executor to the statement:

```java
try (Connection con = dataSource.getConnection()) {
    con.setAutoCommit(false);
    SqlExecutor tx = JdbcSqlExecutor.forConnection(con);
    insertInto(o).set(o.total, val(total)).executor(tx).execute();
    update(s).set(s.stock, s.stock.minus(1)).where(eq(s.id, id)).executor(tx).execute();
    con.commit();
}
```

See [Execution & mapping](execution.md) for framework-managed transactions
and custom executors.

## 5. Write queries

```java
import static ch.lxrin.ql.LxrinQL.*;

PersonTable p = new PersonTable();

// rows as records (mapped by position)
record PersonRow(long personNr, String lastName) {}
List<PersonRow> rows = select(PersonRow.class, p.personNr, p.lastName)
        .from(p)
        .where(eq(p.status, val("ACTIVE")), ilike(p.lastName, val("A%")))
        .orderBy(p.lastName)
        .multiple();

// a single value
long count = createContribution(Long.class)
        .select(count())
        .from(p)
        .where(eq(p.status, val("ACTIVE")))
        .single();

// raw rows
List<Object[]> raw = select(p.personNr, upper(p.lastName)).from(p).multiple();

// inspect the SQL without running it
String sql = select(p.lastName).from(p).where(eq(p.status, val("A"))).buildSql();
// SELECT p.LAST_NAME FROM PERSON p WHERE p.STATUS = :lq0
```

`createContribution(Type.class)`, `query(Type.class)` and
`select(Type.class, items…)` all start a `SelectQuery<Type>`. To finish it:

| Method | Result |
|---|---|
| `.multiple()` | `List<T>` (never `null`) |
| `.single()` | first row or `null` |
| `.optional()` | first row as `Optional<T>` |
| `.fetchCount()` | `SELECT count(*) FROM (query)` |
| `.fetchExists()` | `SELECT EXISTS (query)` |

## 6. Change data

```java
Long id = insertInto(p)
        .set(p.firstName, val("Grace"))
        .set(p.lastName, val("Hopper"))
        .returning(p.personNr)
        .single(Long.class);

int changed = update(p)
        .set(p.status, val("INACTIVE"))
        .where(lt(p.birthDate, val(LocalDate.of(1900, 1, 1))))
        .execute();

deleteFrom(p).where(eq(p.personNr, id)).execute();
```

## Next steps

- [Queries](queries.md): every clause of `SELECT`, `INSERT`, `UPDATE` and `DELETE`
- [Expressions & conditions](expressions.md): how operands, conditions and expressions work
- [Function reference](functions.md): the PostgreSQL function catalog
- [Examples](examples.md): recipes for common tasks

## Optional: IntelliJ live template `qlid`

`live-templates/LxrinQL.xml` contains a live template that inserts a
persisted, auto-incrementing `long` literal (`1000L`, `1001L`, …). This is
useful for constant IDs such as code or enum tables. To install it, open
**File → Manage IDE Settings → Import Settings** and select the file. Then
type `qlid` and press Tab. The counter is stored in `~/.lxrin_ql_id_seq`.
