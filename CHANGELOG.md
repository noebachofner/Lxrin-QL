# Changelog

## 3.0.1

A new, strongly typed API. Application code no longer contains SQL text: tables,
columns, entities and repositories are generated from the database schema, and every
Java value is a bind parameter. See the [migration guide](docs/migration-2-to-3.md);
2.x and 3.0 can run side by side during the migration.

### Modules

- `lxrin-ql-core`: DSL, types, runtime, entities, repositories, `BEANS`, extension
  points. Still no runtime dependencies; Java 17.
- `lxrin-ql-codegen`: reads `pg_catalog` and generates tables, row records, entities,
  enums and repositories; command line interface.
- `lxrin-ql-gradle-plugin` (`ch.lxrin.ql.codegen`, on Maven Central and the Gradle
  Plugin Portal) and `lxrin-ql-maven-plugin` (goal `generate`). Both start a disposable
  PostgreSQL with Testcontainers and apply Flyway migrations.
- `lxrin-ql-spring`: Spring Boot 4 auto-configuration.
- `lxrin-ql-test`: SQL assertions, a mock executor, `@LxrinPostgresTest`, ArchUnit rules.
- `lxrin-ql-bom`.
- The build uses Gradle only; `pom.xml` was removed. Maven projects remain fully
  supported as consumers.

### New

- **Typed fields.** `Field<T>` with `StringField`, `NumberField`, `TemporalField`,
  `JsonField`, `ArrayField` and `Condition`. Type errors are compile errors.
- **`DataType` and converters.** Value objects, enums (PostgreSQL enums and text),
  typed JSON through a `JsonCodec`, arrays and sensitive values. Temporal types
  never use the JVM default time zone.
- **Raw SQL only through `Sql`** (`raw`, `condition`, `table`, `statement`), with
  typed template arguments.
- **Typed results.** `select(a, b, c)` returns `Row3<A, B, C>`, and
  `fetch(Record::new)` maps with a constructor reference. `selectFrom(TABLE)` returns
  the generated row records.
- **Statement model** (`ch.lxrin.ql.statement`) that is rendered only at the end.
- **All 2.x SQL features, typed.**
  - Joins, including `onKey(foreignKey)`.
  - Grouping sets, windows with typed frames and set operations.
  - CTEs: typed columns, recursive, materialized and data-modifying.
  - Derived, `LATERAL` and set-returning tables, and row locking.
  - `INSERT` (column by column, typed multi-row, `INSERT … SELECT`), upserts,
    `UPDATE`, `DELETE`, `TRUNCATE` and typed `RETURNING`.
- **Keyset pagination**: `seekAfter`, `fetchPage` and opaque cursors.
- **Streaming** with `stream()`.
- **Typed function catalog**: about 240 functions, plus `Routines` for user-defined
  functions, aggregates and operators.
- **`QueryContext`**: executor, transactions (`REQUIRED`, `REQUIRES_NEW`, `NESTED`,
  `MANDATORY`; rollback-only protection), listeners, conventions, policies and
  observers. The static entry point only uses a default context.
- **Entities** with state and change tracking (old and new values). Only assigned
  columns are inserted, and only changed columns are updated.
- **Repositories**:
  - `createKey` (UUID v7 or `nextval`), `save`, `saveAll` (multi-row inserts and
    JDBC-batched updates in one transaction), `insert`, `update`, `delete`,
    `deleteById` and `deleteAll`;
  - finders, unique-key finders and keyset pages;
  - `RETURNING *` read-back, stale-row and optimistic-lock detection;
  - entity state is restored on rollback.
- **`BEANS.get(..)`** with automatic registration of repositories. It delegates to
  Spring when `lxrin-ql-spring` is present.
- **Statement listeners** see the structure of every write and can change it. They see
  the affected rows independently of the caller's `RETURNING`, and run in the same
  transaction and on the same connection.
- **Column conventions** (`created_at`, `updated_by`, …) and a version column.
- **Table policies**, applied to every table reference: `SoftDeletePolicy`,
  `TenantPolicy`, and explicit `bypassing(..)`.
- **Specific exceptions**: `UniqueViolationException` (with the generated constraint),
  `ForeignKeyViolationException`, `NotNullViolationException`,
  `CheckViolationException`, `SerializationFailureException`, `DeadlockException`,
  `LockNotAvailableException`, `QueryTimeoutException`, `StaleEntityException`,
  `OptimisticLockException`, …
- **Observability**: `ExecutionObserver`, `LoggingObserver` (no dependency), and a
  Micrometer `Observation` observer in the Spring module.
- **Tests**:
  - unit tests for every module;
  - a Testcontainers integration suite covering the DSL and the function catalog,
    code generation, entities, repositories, change tracking, conventions, policies,
    listeners (including an ISMS-style audit history) and Spring;
  - example Maven and Gradle consumer projects.

### Removed

- `String` operands as SQL, `Object` operands, `val(..)` as a requirement, `Binds`,
  `BindMap` and named `:placeholders`.
- `TableDef`, `Table`, the untyped `Column`, `createContribution(..)`, `query(..)`,
  `select(Class, …)`, `RowMapper` and the reflection-based `ResultMapping`.
- The static default executor and `ServiceLoader` discovery.
- The `qlid` IntelliJ live template.

## 2.0.0

A complete rework. LxrinQL became a framework-independent library with broad
PostgreSQL coverage and was built with both Gradle and Maven.

- Expression model: columns, functions, parameters, conditions and statements
  can be nested anywhere; automatic bind parameters with `val(..)`.
- A complete `SELECT` (CTEs, all join types, grouping sets, windows, set operations,
  locking), `INSERT`/`UPDATE`/`DELETE`/upserts with `RETURNING`, `TRUNCATE`.
- About 250 PostgreSQL functions and operators.
- `JdbcSqlExecutor`, result mapping to records, beans and scalars.
- `UPDATE`/`DELETE` without `WHERE` rejected; `eq(x, null)` rejected.
- The Eclipse Scout dependency was removed.

## 1.0.0

- Initial release: `QueryBuilder`, `SelectIntoBuilder`, basic conditions,
  `TableDef`/`Column`, `Binds`, Eclipse Scout executor.
