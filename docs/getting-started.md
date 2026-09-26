# Getting started

This guide takes you from an empty project to the first queries.

## 1. Requirements

- Java 17 or newer
- PostgreSQL 13 or newer
- Gradle 8+ or Maven 3.6.3+
- Docker at build time, so the code generator can start a disposable PostgreSQL
  and apply your migrations (or an existing database, see
  [Code generation](code-generation.md#the-database))

## 2. Write the schema as migrations

`src/main/resources/db/migration/V1__users.sql`:

```sql
CREATE TYPE app_role AS ENUM ('admin', 'user');

CREATE TABLE app_user (
    id         uuid PRIMARY KEY,
    name       text        NOT NULL,
    email      text        NOT NULL UNIQUE,
    role       app_role    NOT NULL DEFAULT 'user',
    created_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    version    bigint      NOT NULL DEFAULT 0
);
```

## 3. Add the plugin

**Gradle**

Until the plugin is on the Gradle Plugin Portal, add Maven Central to the plugin
repositories in `settings.gradle.kts` (or `settings.gradle`, same content):

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

Kotlin DSL (`build.gradle.kts`):

```kotlin
plugins {
    java
    id("ch.lxrin.ql.codegen") version "3.2.0"
}

lxrinQl {
    packageName = "com.example.db"
    stripTablePrefixes.add("app_")
    tableConstants.put("app_user", "USERS")
    database {
        flywayMigrations.from("src/main/resources/db/migration")
    }
}

dependencies {
    runtimeOnly("org.postgresql:postgresql:42.7.13")
}
```

Groovy DSL (`build.gradle`):

```groovy
plugins {
    id 'java'
    id 'ch.lxrin.ql.codegen' version '3.2.0'
}

lxrinQl {
    packageName = 'com.example.db'
    stripTablePrefixes = ['app_']
    tableConstants = [app_user: 'USERS']
    database {
        flywayMigrations.from('src/main/resources/db/migration')
    }
}

dependencies {
    runtimeOnly 'org.postgresql:postgresql:42.7.13'
}
```

The plugin adds `ch.lxrin:lxrin-ql-core` to `implementation`. The task
`generateLxrinQl` runs before `compileJava` and is up to date while the
migrations and the configuration do not change.

**Maven**: see [Code generation › Maven](code-generation.md#maven).

## 4. Look at the generated code

For `app_user` the generator writes, in `build/generated/sources/lxrinql/main/java`:

| Class | Purpose |
|---|---|
| `UserTable` with the constant `USERS` | typed columns (`USERS.EMAIL` is a `StringColumn`), primary key, unique key `UK_EMAIL`, foreign keys |
| `UserRow` | an immutable record of one row, returned by `selectFrom(USERS)` |
| `User` | a mutable entity with getters and setters that tracks its changes |
| `UserRepositoryBase` | `save`, `saveAll`, `findById`, `findByEmail` (from the unique key), … |
| `AppRole` | the Java enum of the PostgreSQL enum |
| `Tables` | all table constants, for `import static com.example.db.Tables.*` |

It also creates `src/main/java/com/example/db/UserRepository.java` **once**. That
class belongs to you: add your own queries there. Regeneration never overwrites it.

## 5. Create a QueryContext

```java
QueryContext ctx = QueryContext.builder()
        .dataSource(dataSource)          // e.g. HikariCP
        .build();
QueryContext.setDefault(ctx);            // used by the static DSL and BEANS
```

With Spring Boot, add `ch.lxrin:lxrin-ql-spring` instead; it creates the context
and joins `@Transactional`. See [Spring Boot](spring.md).

## 6. Write and read data

`ch.lxrin.ql.QL` is the entry point: type `QL.` in the IDE to find every statement,
condition and function. With `import static ch.lxrin.ql.QL.*` you can leave out the
prefix, e.g. `createContribution(..)` instead of `QL.createContribution(..)`.

```java
import static ch.lxrin.ql.QL.*;
import static com.example.db.Tables.*;

UserRepository users = BEANS.get(UserRepository.class);

User ada = new User();
ada.setId(users.createKey());            // time-ordered UUID v7
ada.setName("Ada");
ada.setEmail("ada@example.org");
users.save(ada);                         // INSERT … RETURNING *: role, created_at and version are read back

ada.setName("Ada Lovelace");
users.save(ada);                         // UPDATE of the changed column only

record UserSummary(UUID id, String name, String email) {}
List<UserSummary> gmail = QL.createContribution(UserSummary.class, USERS, (c, b) -> c
        .select(USERS.ID, USERS.NAME, USERS.EMAIL)
        .where(USERS.EMAIL.endsWith("@gmail.com"), USERS.DELETED_AT.isNull())
        .orderBy(USERS.NAME.asc()))
        .fetch();

long admins = createContribution(Long.class, USERS, (c, b) -> c
        .select(count())
        .where(eq(USERS.ROLE, AppRole.ADMIN)))
        .fetchOne();
```

The same queries in the fluent `select` style:

```java
List<UserSummary> gmail = select(USERS.ID, USERS.NAME, USERS.EMAIL)
        .from(USERS)
        .where(USERS.EMAIL.endsWith("@gmail.com"), USERS.DELETED_AT.isNull())
        .orderBy(USERS.NAME.asc())
        .fetch(UserSummary::new);                // the constructor is checked by the compiler

long admins = selectCount().from(USERS).where(USERS.ROLE.eq(AppRole.ADMIN)).fetchOne();
```

Terminal operations of a `SELECT`:

| Method | Result |
|---|---|
| `fetch()` | all rows: values for one field, `RowN` tuples for several, row records for `selectFrom` |
| `fetch(Constructor::new)` | all rows mapped by a constructor reference (types checked by the compiler) |
| `fetchOne()` | exactly one row, otherwise `NoRowsException` / `TooManyRowsException` |
| `fetchOptional()` | zero or one row |
| `fetchFirst()` | the first row with `LIMIT 1` |
| `fetchCount()`, `fetchExists()` | `count(*)` of the query, `EXISTS (query)` |
| `stream()` | a lazily read stream; must be closed |
| `fetchPage()` | a keyset-paginated page with a cursor for the next one |

## Next steps

- [Queries](queries.md): everything the DSL can express, in both styles
- [Conditions](conditions.md): every operator, optional filters, dynamic sorting
- [Entities and repositories](entities-and-repositories.md)
- [Extension points](extension-points.md): audit listeners, conventions, tenant isolation
- [Migration from 2.x](migration-2-to-3.md)
