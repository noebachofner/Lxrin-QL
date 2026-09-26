# Code generation

`lxrin-ql-codegen` reads the schema from `pg_catalog` of a live PostgreSQL and
writes typed Java sources. It runs through the Gradle plugin, the Maven plugin or
its command line.

## What is generated

For every table (entity name `User` for the table `app_user` in these examples):

| File | Content |
|---|---|
| `UserTable` | `public static final UserTable USERS`; one typed column per database column (`StringColumn`, `NumberColumn<Long>`, `TemporalColumn<Instant>`, `BooleanColumn`, `JsonColumn<String>`, `ArrayColumn<String>`, `RangeColumn`, `TsVectorColumn`, `Column<T>` for UUIDs, enums and value objects) with nullability, default, identity and generated flags; `PK` (with the key strategy), `UK_…` for unique constraints and unique indexes, `FK_<columns>` for foreign keys (named after the key's own columns, e.g. `FK_CREATED_BY` or `FK_ORDER_ID_LINE_NO`; see [Foreign key names](#foreign-key-names)); `as(alias)` for aliases; `mapRow(..)` |
| `UserRow` | a record with one component per column |
| `User` | an entity with a field, getter and setter per column (no setter for generated columns), `id()`, `toRow()`, `fromRow(..)` |
| `UserKey` | a key record, for composite primary keys |
| `UserRepositoryBase` | extends `TableRepository`; `findBy…` methods for every unique key |
| `UserRepository` | created **once** in the stub directory (`src/main/java`); never overwritten |
| `AppRole` | a Java enum for every PostgreSQL enum type (label ↔ constant, `AppRole.TYPE`) |
| `Tables` | every table constant, for `import static …Tables.*` |
| `META-INF/lxrin-ql/repositories` | an index of the repositories, used by `lxrin-ql-spring` |

Views, materialized views and tables without a primary key get a table class, a
row record and a read-only repository (`ReadOnlyRepository`), but no entity.

Columns, keys and enums keep the comments of the database as Javadoc. For code bases
that do not allow comments, `generateJavadoc = false` generates the same code without
any comment, and `stubJavadoc` controls the repository stubs separately.

### Type mapping

| PostgreSQL | Java | Column class |
|---|---|---|
| `smallint`, `integer`, `bigint` | `Short`, `Integer`, `Long` | `NumberColumn` |
| `numeric`, `real`, `double precision` | `BigDecimal`, `Float`, `Double` | `NumberColumn` |
| `text`, `varchar`, `char`, `citext`, `name` | `String` | `StringColumn` |
| `boolean` | `Boolean` | `BooleanColumn` |
| `uuid` | `UUID` | `Column` |
| `date`, `time`, `timestamp`, `timestamptz` | `LocalDate`, `LocalTime`, `LocalDateTime`, `Instant` | `TemporalColumn` |
| `interval` | `Duration` | `Column` |
| `json`, `jsonb` | `String` (or a forced type) | `JsonColumn` |
| `bytea` | `byte[]` | `Column` |
| enum types | generated enum (or a mapped existing enum) | `Column` |
| arrays of any of these | `T[]` | `ArrayColumn` |
| domains | their base type | |
| `daterange`, `tsrange`, `tstzrange`, `int4range`, `int8range`, `numrange` | `String` (text form) | `RangeColumn` (range operators) |
| `tsvector` | `String` (text form) | `TsVectorColumn` (`tsMatches`) |
| `tsquery`, `inet`, other types | `String` (text form) | `Column` |

### Foreign key names

A foreign key constant is `FK_` followed by the key's own columns in upper snake case:

| Foreign key | Constant |
|---|---|
| `app_user.created_by → app_user(id)` | `USERS.FK_CREATED_BY` |
| `app_user.updated_by → app_user(id)` | `USERS.FK_UPDATED_BY` |
| `order_line(order_id, line_no) → …` | `ORDER_LINE.FK_ORDER_ID_LINE_NO` |

The name depends only on the key itself, so adding, removing or reordering other keys
never renames a constant. Two keys on the same columns, or a key whose constant equals a
column constant, stop the generator with an error that names both. Resolve it, or keep a
name from 3.1, with `foreignKeyNames`: a map from the constraint name (or
`table.constraint`) to the constant.

### Key strategies

`createKey()` of the repository uses the strategy of the primary key:

- a `uuid` primary key: `KeyStrategy.uuidV7()`, a time-ordered UUID created in Java;
- a serial or identity column: `KeyStrategy.sequence("schema.table_id_seq")` (`nextval`);
- anything else: no generated keys.

Identity columns declared `GENERATED ALWAYS` are written with `OVERRIDING SYSTEM VALUE`
when the application supplies a key.

## Configuration

| Setting | Default | Meaning |
|---|---|---|
| `packageName` | – (required) | package of the generated code |
| `schemas` | `public` | schemas to read |
| `defaultSchema` | `public` | tables in this schema are referenced without schema name |
| `includes` / `excludes` | all / Flyway and Liquibase history tables | regular expressions for table names |
| `stripTablePrefixes` | none | prefixes removed before deriving names (`app_user` → `User`) |
| `singularize` | `true` | `users` → `User`, `categories` → `Category`, `addresses` → `Address`, `people` → `Person` |
| `entityNames` | – | per-table entity names, e.g. `app_user_role` → `UserRole` |
| `tableConstants` | UPPER_SNAKE of the table name | per-table constants, e.g. `app_user` → `USERS` |
| `foreignKeyNames` | `FK_` + the key's columns | per-key constants: constraint name (or `table.constraint`) → constant, e.g. `app_user_created_by_fkey` → `FK_CREATOR` |
| `enumMappings` | – | use an existing Java enum for a PostgreSQL enum (labels are matched ignoring case and separators) |
| `forcedTypes` | – | custom Java types for matching columns, see below |
| `generateEntities` / `generateRepositories` | `true` | turn parts off |
| `generateJavadoc` | `true` | `false`: generated classes contain no comments at all, except the `// Generated by LxrinQL codegen – do not edit.` header that marks the files the generator owns |
| `stubJavadoc` | value of `generateJavadoc` | whether the repository stubs (`UserRepository`) contain Javadoc; stubs never get a header |
| repository stub directory | `src/main/java` | where `UserRepository` is created once |

Naming can also be replaced entirely by implementing `NamingStrategy` when you call
the generator directly (`CodeGenerator.generate(model, config, naming)`).

### Forced types (value objects, JSON records)

A forced type gives matching columns a custom Java type and its `DataType`:

```java
public record UserId(UUID value) {}

public final class Types {
    public static final DataType<UserId> USER_ID = SqlTypes.UUID.map(UserId.class, UserId::new, UserId::value);
    public static final DataType<Settings> SETTINGS = SqlTypes.jsonb(Settings.class);   // needs a JsonCodec
}
```

```kotlin
lxrinQl {
    //        tables       columns          SQL types  Java type                 DataType expression
    forcedType("app_user", "id",            "uuid",    "com.example.UserId",     "com.example.Types.USER_ID")
    forcedType(".*",       "owner_id|.*_by_id", "uuid", "com.example.UserId",     "com.example.Types.USER_ID")
    forcedType(".*",       "settings",      "jsonb",   "com.example.Settings",   "com.example.Types.SETTINGS")
}
```

Give foreign key columns the same forced type as the columns they reference, so that
joins such as `onKey(ASSET.FK_OWNER_ID)` compare equal types.

## Gradle

The plugin `ch.lxrin.ql.codegen` is published to Maven Central. Until it is also
available on the Gradle Plugin Portal, add Maven Central to the plugin repositories in
`settings.gradle(.kts)`:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

```groovy
// settings.gradle
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

Without it, Gradle reports `Plugin [id: 'ch.lxrin.ql.codegen', version: '3.2.0'] was not
found`.

**Kotlin DSL** (`build.gradle.kts`)

```kotlin
plugins {
    java
    id("ch.lxrin.ql.codegen") version "3.2.0"
}

lxrinQl {
    packageName = "com.example.db"
    schemas = listOf("public", "audit")
    stripTablePrefixes.add("app_")
    tableConstants.put("app_user", "USERS")
    entityNames.put("app_user_role", "UserRole")
    foreignKeyNames.put("app_user_created_by_fkey", "FK_CREATOR")
    enumMappings.put("app_role", "com.example.Role")
    excludes.add("tmp_.*")
    forcedType("app_user", "id", "uuid", "com.example.UserId", "com.example.Types.USER_ID")
    // generateJavadoc = false                               // no comments in generated code
    // schemaSource = "auto"                                 // auto, database or snapshot
    // snapshotFile = layout.projectDirectory.file("src/main/lxrinql/schema.json")
    database {
        image = "postgres:17-alpine"                         // the disposable database
        flywayMigrations.from("src/main/resources/db/migration")
        sqlScripts.from("src/test/resources/extra.sql")       // plain SQL applied after Flyway
    }
    // repositoryStubs = layout.projectDirectory.dir("src/main/java")
    // addCoreDependency = false                             // add lxrin-ql-core yourself
}
```

**Groovy DSL** (`build.gradle`)

```groovy
plugins {
    id 'java'
    id 'ch.lxrin.ql.codegen' version '3.2.0'
}

lxrinQl {
    packageName = 'com.example.db'
    schemas = ['public', 'audit']
    stripTablePrefixes = ['app_']
    tableConstants = [app_user: 'USERS']
    entityNames = [app_user_role: 'UserRole']
    foreignKeyNames = [app_user_created_by_fkey: 'FK_CREATOR']
    enumMappings = [app_role: 'com.example.Role']
    excludes = ['tmp_.*']
    forcedType 'app_user', 'id', 'uuid', 'com.example.UserId', 'com.example.Types.USER_ID'
    // generateJavadoc = false
    // schemaSource = 'auto'
    // snapshotFile = layout.projectDirectory.file('src/main/lxrinql/schema.json')
    database {
        image = 'postgres:17-alpine'
        flywayMigrations.from('src/main/resources/db/migration')
        sqlScripts.from('src/test/resources/extra.sql')
    }
    // repositoryStubs = layout.projectDirectory.dir('src/main/java')
    // addCoreDependency = false
}
```

In Groovy, map properties are assigned a map literal (`tableConstants = [app_user: 'USERS']`)
or extended with `tableConstants.put('app_user', 'USERS')`, and list properties a list
literal. Keys that are not plain identifiers need quotes: `['public.app_user': 'USERS']`.
The [Groovy example project](../integration-tests/consumers/groovy-consumer/build.gradle)
is built by the integration tests.

- The task `generateLxrinQl` is cacheable. Its inputs are the configuration, the
  migration files and the snapshot.
- Its output is added to the `main` source set (`build/generated/sources/lxrinql/main/java`
  and `build/generated/resources/lxrinql/main`).
- `lxrinQlSnapshot` and `lxrinQlCheckSnapshot`: see [Without Docker](#without-docker).

## Maven

```xml
<plugin>
    <groupId>ch.lxrin</groupId>
    <artifactId>lxrin-ql-maven-plugin</artifactId>
    <version>3.2.0</version>
    <executions>
        <execution>
            <goals><goal>generate</goal></goals>
        </execution>
    </executions>
    <configuration>
        <packageName>com.example.db</packageName>
        <schemas><schema>public</schema></schemas>
        <stripTablePrefixes><prefix>app_</prefix></stripTablePrefixes>
        <tableConstants><app_user>USERS</app_user></tableConstants>
        <entityNames><app_user_role>UserRole</app_user_role></entityNames>
        <enumMappings><app_role>com.example.Role</app_role></enumMappings>
        <forcedTypes>
            <forcedType>
                <tables>app_user</tables>
                <columns>id</columns>
                <sqlTypes>uuid</sqlTypes>
                <javaType>com.example.UserId</javaType>
                <dataType>com.example.Types.USER_ID</dataType>
            </forcedType>
        </forcedTypes>
        <flywayMigrations>
            <dir>${project.basedir}/src/main/resources/db/migration</dir>
        </flywayMigrations>
        <!-- or an existing database: <jdbcUrl>, <user>, <password> -->
    </configuration>
</plugin>
```

- The goal `generate` is bound to `generate-sources`. It adds
  `target/generated-sources/lxrinql` as a compile source root and
  `target/generated-resources/lxrinql` as a resource directory.
- The plugin needs Maven 3.6.3 or newer.
- Goals `snapshot` (`mvn lxrin-ql:snapshot`) and `check-snapshot`
  (`mvn lxrin-ql:check-snapshot`) and the parameters `schemaSource` and `snapshotFile`:
  see [Without Docker](#without-docker).
- Other parameters: `outputDirectory`, `resourcesDirectory`,
  `repositoryStubDirectory` (default `src/main/java`), `defaultSchema`,
  `includes`, `excludes`, `singularize`, `foreignKeyNames`, `generateEntities`,
  `generateRepositories`, `generateJavadoc`, `stubJavadoc`, `schemaSource`, `snapshotFile`,
  `image`, `sqlScripts` and `skip` (`-Dlxrinql.skip`).

## Command line

```bash
java -jar lxrin-ql-codegen-3.2.0.jar \
    --package com.example.db --output build/generated/java --resources build/generated/resources \
    --stubs src/main/java --migrations src/main/resources/db/migration \
    --strip-prefixes app_ --table-constants app_user=USERS \
    --forced-types "app_user|id|uuid|com.example.UserId|com.example.Types.USER_ID"
java -jar lxrin-ql-codegen-3.2.0.jar --config codegen.properties
```

The jar needs its dependencies on the class path, e.g. through your build tool.

Further options: `--foreign-key-names constraint=FK_NAME,…`, `--generate-javadoc false`,
`--stub-javadoc true`, and for snapshots `--write-snapshot file`, `--check-snapshot file`
(exit status 1 if out of date), `--snapshot file` and `--schema-source auto|database|snapshot`.

## The database

By default the generator starts `postgres:17-alpine` with Testcontainers (Docker
required), applies the Flyway migrations and SQL scripts, reads the schema and stops
the container. Without Docker it can generate from a committed snapshot, see
[Without Docker](#without-docker). Use the same major PostgreSQL version as in production.

To read an existing database instead, set `database { jdbcUrl = …; user = …; password = … }`
(Gradle) or `<jdbcUrl>`, `<user>`, `<password>` (Maven). Nothing is migrated then.

## Without Docker

Reading the schema from migrations needs Docker. For CI runners and machines without
Docker, and for faster IDE imports, commit a **schema snapshot** and generate from it:

1. On a machine with Docker, write the snapshot and commit it:

   ```bash
   ./gradlew lxrinQlSnapshot          # Maven: mvn lxrin-ql:snapshot
   git add src/main/lxrinql/schema.json
   ```

   The file is JSON with one line per column and key, so a migration shows up as a
   small diff. It also contains a hash of the migration files.

2. `generateLxrinQl` chooses the source with `schemaSource`:

   | `schemaSource` | Schema from |
   |---|---|
   | `auto` (default) | the database if a JDBC URL is set or Docker is available, otherwise the snapshot. The log says which, and warns if the migrations changed since the snapshot was written |
   | `database` | always the database (fails without Docker unless a JDBC URL is set) |
   | `snapshot` | always the snapshot; no database, no Docker |

   Both sources produce identical code.

3. In CI, check that the snapshot is up to date:

   ```bash
   ./gradlew lxrinQlCheckSnapshot     # Maven: mvn lxrin-ql:check-snapshot
   ```

   With Docker it reads the schema from the migrations and compares the whole snapshot.
   Without Docker it compares only the hash of the migrations. It fails with the first
   differing line and asks you to run `lxrinQlSnapshot` again.

Write the snapshot again after changing `schemas`, `includes` or `excludes`; the check
detects this too. `./gradlew lxrinQlSnapshot build` writes the snapshot first and then
generates from it.
