# LxrinQL

A strongly typed query language and data access layer for **PostgreSQL**.
Application code contains no SQL text: no table names in strings, no column
names in strings, no string fragments. Tables, columns, entities and
repositories are generated from your database schema, and every Java value is a
bind parameter.

[![Java](https://img.shields.io/badge/Java-17%2B-blue?logo=openjdk)](https://adoptium.net/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-13%2B-336791?logo=postgresql)](https://www.postgresql.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4-6DB33F?logo=springboot)](https://spring.io/projects/spring-boot)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

```java
User user = new User();
user.setId(BEANS.get(UserRepository.class).createKey());      // UUID v7 or nextval, depending on the key
user.setName("Test");
user.setEmail("test@gmail.com");
BEANS.get(UserRepository.class).save(user);                     // INSERT … RETURNING *; the entity is now persistent

BEANS.get(UserRepository.class).saveAll(List.of(userA, userB, userC));   // one multi-row INSERT, one transaction

List<User> admins = BEANS.get(UserRepository.class)
        .findAll(USERS.ROLE.eq(Role.ADMIN).and(USERS.DELETED_AT.isNull()));

List<UserSummary> rows = select(USERS.ID, USERS.NAME, USERS.EMAIL)
        .from(USERS)
        .where(USERS.EMAIL.endsWith("@gmail.com"))
        .orderBy(USERS.NAME.asc())
        .fetch(UserSummary::new);                                // typed tuple → your record, checked by the compiler
```

`USERS.CREATED_AT.gt(Instant.now())` compiles. `USERS.CREATED_AT.eq("abc")` does
not, and neither does `USERS.EMAIL.plus(1)`.

---

## Modules

| Artifact (`ch.lxrin:…:3.0.1`) | What it is | Dependencies |
|---|---|---|
| `lxrin-ql-core` | DSL, types, runtime, entities, repositories, `BEANS`, extension points | none |
| `lxrin-ql-codegen` | Reads the schema from PostgreSQL and generates tables, rows, entities and repositories; CLI | PostgreSQL JDBC, Testcontainers, Flyway |
| `lxrin-ql-gradle-plugin` | Gradle plugin `ch.lxrin.ql.codegen` | codegen |
| `lxrin-ql-maven-plugin` | Maven plugin, goal `generate` | codegen |
| `lxrin-ql-spring` | Spring Boot 4 auto-configuration | core (Spring provided by the application) |
| `lxrin-ql-test` | SQL assertions, mock executor, PostgreSQL JUnit extension, ArchUnit rules | core, JUnit |
| `lxrin-ql-bom` | Aligns the versions of all modules | – |

## Features

- **Generated from your schema.** Tables with typed columns, keys and foreign keys,
  row records, entities with change tracking, repositories. Enums, arrays, JSON,
  identity and generated columns, views and composite keys are supported. Naming is
  configurable, and so are forced types for value objects.
- **A value is always a bind parameter.** Raw SQL is only possible through
  `Sql.raw(..)`, `Sql.condition(..)`, `Sql.table(..)` and `Sql.statement(..)`, and an
  ArchUnit rule can forbid it.
- **Typed queries.** `SELECT` with joins (including joins along foreign keys), grouping
  sets, window functions, CTEs (recursive, materialized, data-modifying), `LATERAL`,
  set operations, locking and keyset pagination. `INSERT` (multi-row, upserts),
  `UPDATE`, `DELETE`, `TRUNCATE` and `RETURNING`. About 240 typed PostgreSQL functions.
- **Repositories.** `createKey`, `save`, `saveAll` (multi-row inserts and batched
  updates in one transaction), `insert`, `update`, `delete`, finders, keyset pages.
  Stale-row and optimistic-lock detection. Entity state is restored when a
  transaction rolls back.
- **Extension points.** Statement listeners see the structure of every write and the
  affected rows, in the same transaction. Column conventions (`created_at`,
  `updated_by`, …), table policies (tenant isolation, soft delete), custom types,
  observers (logging, Micrometer) and typed user-defined functions and operators.
- **One `QueryContext`** holds the executor, transactions, listeners, conventions,
  policies and observers. It works with dependency injection and with several data
  sources. The static DSL only uses a default context.
- **Errors you can catch:** `UniqueViolationException` (with the generated
  constraint), `ForeignKeyViolationException`, `OptimisticLockException`,
  `StaleEntityException`, …
- **Spring Boot 4:** statements join `@Transactional`, repositories are beans, and
  `BEANS.get(..)` returns them.

---

## Installation

LxrinQL requires **Java 17+** and **PostgreSQL 13+**. The code generator starts a
disposable PostgreSQL with Docker (Testcontainers) and applies your Flyway
migrations, so the build needs Docker. Alternatively, point the generator at an
existing database.

**Gradle (Kotlin DSL)**

```kotlin
plugins {
    java
    id("ch.lxrin.ql.codegen") version "3.0.1"     // adds lxrin-ql-core to implementation
}

lxrinQl {
    packageName = "com.example.db"
    stripTablePrefixes.add("app_")               // app_user → User
    database {
        flywayMigrations.from("src/main/resources/db/migration")
    }
}

dependencies {
    implementation("ch.lxrin:lxrin-ql-spring:3.0.1")   // optional: Spring Boot 4
    runtimeOnly("org.postgresql:postgresql:42.7.13")
    testImplementation("ch.lxrin:lxrin-ql-test:3.0.1")
}
```

**Maven**

```xml
<dependencies>
    <dependency>
        <groupId>ch.lxrin</groupId>
        <artifactId>lxrin-ql-core</artifactId>
        <version>3.0.1</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>ch.lxrin</groupId>
            <artifactId>lxrin-ql-maven-plugin</artifactId>
            <version>3.0.1</version>
            <executions>
                <execution>
                    <goals><goal>generate</goal></goals>   <!-- bound to generate-sources -->
                </execution>
            </executions>
            <configuration>
                <packageName>com.example.db</packageName>
                <flywayMigrations>
                    <dir>${project.basedir}/src/main/resources/db/migration</dir>
                </flywayMigrations>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## Quick start

1. Write your schema as Flyway migrations (`V1__init.sql`, …).
2. Build: the plugin generates `UserTable` (constant `USERS`), `UserRow`, `User`,
   `UserRepositoryBase` and, once, `UserRepository` in `src/main/java` for your own
   queries.
3. Create a `QueryContext`. With Spring Boot, `lxrin-ql-spring` does this for you.

```java
QueryContext ctx = QueryContext.builder()
        .dataSource(dataSource)
        .convention(ColumnConventions.createdAt("created_at", clock))
        .policy(new SoftDeletePolicy("deleted_at", clock))
        .versionColumn("version")
        .build();
QueryContext.setDefault(ctx);                           // for the static DSL and BEANS

UserRepository users = new UserRepository(ctx);        // or BEANS.get(UserRepository.class)
User ada = users.findByEmail("ada@example.org").orElseThrow();   // generated from the unique key
ada.setName("Ada Lovelace");
users.save(ada);                                        // UPDATE app_user SET name = ?, version = version + 1 WHERE …
```

## Documentation

| Guide | Contents |
|---|---|
| [Getting started](docs/getting-started.md) | Set-up with Gradle or Maven, the first generated code, the first queries |
| [Code generation](docs/code-generation.md) | Gradle plugin, Maven plugin, CLI; naming, forced types, enums, what is generated |
| [Queries](docs/queries.md) | `SELECT`, joins, grouping, windows, CTEs, set operations, locking, paging, `INSERT`/`UPDATE`/`DELETE`/upserts |
| [Fields and types](docs/expressions.md) | Typed fields and conditions, data types and converters, bind parameters, literals, raw SQL |
| [Function reference](docs/functions.md) | The typed PostgreSQL function catalog |
| [Entities and repositories](docs/entities-and-repositories.md) | Change tracking, `save`, `saveAll`, keys, finders, optimistic locking, `BEANS` |
| [Execution](docs/execution.md) | `QueryContext`, transactions, streaming, errors, logging and metrics |
| [Extension points](docs/extension-points.md) | Statement listeners, column conventions, table policies, custom types and functions, an audit history example |
| [Spring Boot](docs/spring.md) | Auto-configuration, `@Transactional`, repositories as beans, properties |
| [Testing](docs/testing.md) | SQL assertions, mock executor, `@LxrinPostgresTest`, architecture rules |
| [Examples](docs/examples.md) | Recipes: search forms, paging, reports, upserts, job queues, JSON, full-text search |
| [Migration from 2.x](docs/migration-2-to-3.md) | Step-by-step migration, side by side with 2.x |
| [Design](docs/design/3.0.md) | The design of 3.0 and its decisions |
| [Releasing](docs/releasing.md) | Publishing to Maven Central and the Gradle Plugin Portal |
| [Changelog](CHANGELOG.md) | Release notes |

## Building and testing

```bash
./gradlew build
```

This compiles all modules and runs the unit tests and the Testcontainers
integration tests. The integration tests include example Gradle and Maven projects
that use the plugins, so the build needs Docker and Maven.

## License

[MIT](LICENSE). This project was created with AI assistance.
