# LxrinQL

A fluent, type-safe SQL query builder for Java with broad **PostgreSQL** coverage.

[![Java](https://img.shields.io/badge/Java-17%2B-blue?logo=openjdk)](https://adoptium.net/)
[![Gradle](https://img.shields.io/badge/Gradle-9-02303A?logo=gradle)](https://gradle.org/)
[![Maven](https://img.shields.io/badge/Maven-3.8%2B-C71A36?logo=apache-maven)](https://maven.apache.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

```java
import static ch.lxrin.ql.LxrinQL.*;

PersonTable p = new PersonTable();
OrderTable  o = new OrderTable();

List<CustomerRevenue> top = select(CustomerRevenue.class,
            p.lastName,
            sum(o.total).filter(eq(o.status, val("PAID"))).as("revenue"),
            rank().over(window().orderBy(desc(sum(o.total)))).as("rank"))
        .from(p)
        .leftJoin(o, eq(o.personNr, p.personNr))
        .where(ge(o.createdAt, now().minus(interval("1 year"))))
        .groupBy(p.lastName)
        .orderBy(inline(3))
        .limit(10)
        .multiple();
```

LxrinQL renders readable SQL with named bind parameters, runs it through any
JDBC `DataSource` or `Connection` (or an executor of your own) and maps the
rows to records, beans or simple values. The library has **no runtime
dependencies** and works with any framework.

---

## Features

- **Typed tables and columns.** Declare `TableDef` classes once and write `p.lastName` instead of `"p.LAST_NAME"`.
- **Every PostgreSQL SELECT clause:** `WITH [RECURSIVE]` (also `MATERIALIZED`), `DISTINCT ON`, all join types including `LATERAL` and `USING`, `GROUP BY ROLLUP/CUBE/GROUPING SETS`, `HAVING`, `WINDOW`, `UNION/INTERSECT/EXCEPT [ALL]`, `ORDER BY … NULLS FIRST/LAST`, `LIMIT/OFFSET`, `FETCH … WITH TIES`, and `FOR UPDATE/SHARE … SKIP LOCKED/NOWAIT`.
- **Data modification:** `INSERT` (multi-row, `INSERT … SELECT`, `DEFAULT VALUES`), upserts with `ON CONFLICT … DO NOTHING/DO UPDATE`, `UPDATE … FROM`, `DELETE … USING`, `RETURNING`, data-modifying CTEs and `TRUNCATE`.
- **About 250 functions and operators:** aggregates with `FILTER`, `DISTINCT`, `ORDER BY` and `WITHIN GROUP`; window functions; string, regex, math, date/time, interval, range, JSON/JSONB and jsonpath, array and full-text search functions; `CASE`, `CAST`, `COALESCE` and more. Any other function works through `function("name", args…)`.
- **Safe parameters:** `val(x)` binds values automatically and `Binds` gives you typed, named parameters. `inline(x)` writes an escaped literal.
- **Result mapping** to records (by position), beans (by column alias), scalars and `Object[]`, or a custom `RowMapper`.
- **Pluggable execution:** `JdbcSqlExecutor` ships with the library and handles `:name` parsing, collection expansion, SQL arrays and `java.time`. You can also implement the two-method `SqlExecutor` interface yourself.
- **Safety nets:** `UPDATE`/`DELETE` without `WHERE` is rejected, `eq(x, null)` is rejected in favour of `isNull`, and SQL errors carry the failing statement and its SQLSTATE.
- **Builds with Gradle and Maven.** Both builds produce the same artifact.

---

## Installation

LxrinQL requires **Java 17+**. Coordinates: `ch.lxrin:lxrin-ql:2.0.0`.

The library is not on Maven Central yet. Install it into your local Maven
repository once, and it is then available to both Gradle and Maven projects:

```bash
git clone https://github.com/noebachofner/lxrin_ql.git
cd lxrin_ql
./gradlew publishToMavenLocal      # or: mvn install
```

**Gradle (Kotlin DSL)**

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("ch.lxrin:lxrin-ql:2.0.0")
    runtimeOnly("org.postgresql:postgresql:42.7.10")   // your JDBC driver
}
```

**Gradle (Groovy DSL)**

```groovy
repositories { mavenLocal(); mavenCentral() }
dependencies {
    implementation 'ch.lxrin:lxrin-ql:2.0.0'
}
```

**Maven**

```xml
<dependency>
    <groupId>ch.lxrin</groupId>
    <artifactId>lxrin-ql</artifactId>
    <version>2.0.0</version>
</dependency>
```

**Gradle composite build (no publishing).** If the sources sit next to your
project, add `includeBuild("../lxrin_ql")` to your `settings.gradle.kts` and
keep the dependency above. Gradle substitutes it with the local build.

---

## Quick start

**1. Describe your tables:**

```java
public class PersonTable extends TableDef {
    public final Column personNr  = column("PERSON_NR");   // p.PERSON_NR, alias "personNr"
    public final Column firstName = column("FIRST_NAME");
    public final Column lastName  = column("LAST_NAME");
    public final Column status    = column("STATUS");

    public PersonTable() { super("PERSON", "p"); }
}
```

**2. Configure an executor** (once, at startup):

```java
LxrinQL.setDefaultExecutor(new JdbcSqlExecutor(dataSource));
```

**3. Write queries:**

```java
import static ch.lxrin.ql.LxrinQL.*;

PersonTable p = new PersonTable();

record PersonDto(long personNr, String firstName, String lastName) {}

List<PersonDto> active = select(PersonDto.class, p.personNr, p.firstName, p.lastName)
        .from(p)
        .where(eq(p.status, val("ACTIVE")))
        .orderBy(p.lastName.asc())
        .multiple();
// SELECT p.PERSON_NR, p.FIRST_NAME, p.LAST_NAME FROM PERSON p WHERE p.STATUS = :lq0 ORDER BY p.LAST_NAME ASC

Long id = insertInto(p)
        .set(p.firstName, val("Ada"))
        .set(p.lastName, val("Lovelace"))
        .returning(p.personNr)
        .single(Long.class);

update(p).set(p.status, val("INACTIVE")).where(eq(p.personNr, id)).execute();
```

### The one rule to remember

| You pass                            | LxrinQL renders                        |
|-------------------------------------|----------------------------------------|
| a column, function, `val(..)`, sub-query | the expression                    |
| a `String`                          | **SQL, verbatim** (e.g. `"t.NAME"`, `":status"`) |
| any other Java value (`42`, `LocalDate`, `UUID`, arrays, …) | a bind parameter (`:lqN`) |

Always wrap user-supplied text in `val(text)`. A plain `String` goes into the
statement as SQL, so it is never treated as data.

---

## Documentation

| Guide | Contents |
|---|---|
| [Getting started](docs/getting-started.md) | Setup with Gradle or Maven, executors, first queries |
| [Queries](docs/queries.md) | `SELECT` clauses, joins, grouping, CTEs, set operations, locking, `INSERT`/`UPDATE`/`DELETE`/upsert |
| [Expressions & conditions](docs/expressions.md) | Operand rule, all conditions, `CASE`, casts, windows, literals, custom SQL |
| [Function reference](docs/functions.md) | The full PostgreSQL function catalog, by category |
| [Execution & mapping](docs/execution.md) | Parameters, `Binds`, executors, transactions, result mapping, testing, custom adapters |
| [Examples](docs/examples.md) | Real-world recipes: search forms, paging, reporting, upserts, job queues, JSON, full-text search |
| [Changelog](CHANGELOG.md) | Release notes and the migration guide from 1.x |

---

## Building and testing

```bash
./gradlew build          # compile, test, jar + sources + javadoc
mvn verify               # the same with Maven
```

The unit tests need no database. The PostgreSQL integration test runs when
`LXRIN_QL_PG_URL` is set:

```bash
docker run -d --rm --name pg -e POSTGRES_PASSWORD=test -p 5432:5432 postgres:17
LXRIN_QL_PG_URL='jdbc:postgresql://localhost:5432/postgres?user=postgres&password=test' ./gradlew test
```

---

## License

[MIT](LICENSE). This project was created with AI assistance.
