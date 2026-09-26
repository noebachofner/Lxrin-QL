# Execution

## QueryContext

A `QueryContext` holds everything a statement needs. It is immutable and
thread-safe; create one per application or data source.

```java
QueryContext ctx = QueryContext.builder()
        .dataSource(dataSource)                                   // JDBC transactions of LxrinQL itself
        .jsonCodec(myCodec)                                       // for SqlTypes.jsonb(SomeClass.class)
        .listener(new AuditListener())
        .convention(ColumnConventions.createdAt("created_at", clock))
        .policy(new SoftDeletePolicy("deleted_at", clock))
        .observer(new LoggingObserver(Duration.ofMillis(500)))
        .versionColumn("version")
        .batchSize(1000)                                          // rows per multi-row insert / JDBC batch
        .fetchSize(500)                                           // for stream()
        .build();
```

| Builder method | Purpose |
|---|---|
| `dataSource(ds)` | connections and transactions from a `DataSource` (`JdbcTransactions`) |
| `connectionProvider(..)` + `transactions(..)` | your own, e.g. framework-managed transactions (`lxrin-ql-spring` does this) |
| `executor(..)` | a custom `SqlExecutor`, e.g. `MockExecutor` in unit tests |

Variants:

```java
ctx.derive(b -> b.listener(extra))                    // same configuration plus changes
ctx.bypassing(SoftDeletePolicy.class)                 // without a policy (explicit and searchable)
ctx.withOrigin(Origin.repository("Report.monthly"))   // origin reported to listeners and observers
```

### Attached and static statements

`ctx.select(..)`, `ctx.insertInto(..)`, `ctx.update(..)`, `ctx.deleteFrom(..)`,
`ctx.truncate(..)` and `ctx.selectFrom(..)` create statements attached to `ctx`.

The static `Dsl.*` methods create statements that run on `QueryContext.getDefault()`,
set with `QueryContext.setDefault(ctx)` (Spring does this). A statement can also be
attached later: `select(..).attach(ctx)`.

## Transactions

```java
ctx.transaction(() -> {                                   // Propagation.REQUIRED
    users.save(a);
    orders.save(b);
});
Long id = ctx.transaction(() -> insertInto(..).returning(..).fetchOne());
ctx.transaction(Propagation.REQUIRES_NEW, () -> auditLog.write(..));   // independent transaction
ctx.transaction(Propagation.NESTED, () -> tryOptionalStep());          // savepoint
ctx.transaction(Propagation.MANDATORY, () -> ..);                      // fails without a transaction
```

- Without a transaction, every statement runs in auto-commit mode.
- A statement with statement listeners runs, together with the listeners, in a
  transaction that starts automatically if none is active.
- If a block that joined an outer transaction throws, the outer transaction is marked
  rollback-only. A caught exception therefore cannot lead to a partial commit
  (`TransactionException` at the end).
- `ctx.currentTransaction()` returns the `TransactionScope` with transaction-scoped
  attributes (`scope.attribute(key, supplier)`) and callbacks (`afterCommit`,
  `afterRollback`).
- With `lxrin-ql-spring`, the same methods delegate to Spring's
  `PlatformTransactionManager`, and statements join `@Transactional` methods.

## Streaming

```java
try (Stream<UserRow> rows = selectFrom(USERS).stream()) {
    rows.forEach(this::export);
}
```

The stream holds a connection until it is closed. Inside a transaction, rows are
fetched in batches of `fetchSize`.

## Errors

All exceptions are unchecked and extend `LxrinQlException`. Errors from the database
carry the failing SQL, the bind values (sensitive values redacted) and the SQLSTATE.

| Exception | When |
|---|---|
| `UniqueViolationException` | 23505; `constraint()` returns the generated key, e.g. `USERS.UK_EMAIL` |
| `ForeignKeyViolationException` | 23503; `isViolated(ORDERS.FK_USER)` |
| `NotNullViolationException`, `CheckViolationException`, `ExclusionViolationException` | 23502, 23514, 23P01 |
| `SerializationFailureException`, `DeadlockException` | 40001, 40P01 (both `TransientDataAccessException`: retry) |
| `LockNotAvailableException`, `QueryTimeoutException` | 55P03 (`NOWAIT`, `lock_timeout`), 57014 |
| `DataAccessException` | any other SQL error |
| `EntityNotFoundException` | `getById`, `deleteById` |
| `StaleEntityException`, `OptimisticLockException` | entity update/delete matched no row / wrong version |
| `NoRowsException`, `TooManyRowsException` | `fetchOne`, `fetchOptional`, `findOne` |
| `InvalidStatementException` | e.g. `UPDATE` without `WHERE` |
| `StatementRejectedException` | a listener or policy rejected the statement |
| `TransactionException` | begin/commit failed, rollback-only |

```java
try {
    users.save(user);
} catch (UniqueViolationException e) {
    if (e.isViolated(USERS.UK_EMAIL)) throw new EmailTakenException(user.getEmail());
    throw e;
}
```

## Logging, metrics and tracing

An `ExecutionObserver` sees every statement:

```java
public interface ExecutionObserver {
    default void onStart(StatementEvent event) {}
    default void onSuccess(StatementEvent event, Duration took, long rows) {}
    default void onError(StatementEvent event, Duration took, LxrinQlException error) {}
}
```

`StatementEvent` provides:

- the statement kind and the tables;
- the rendered SQL and binds (`event.sql()`; its `toString()` redacts sensitive values);
- the origin (DSL, `REPOSITORY:UserRepository.save`, `LISTENER:…`);
- the batch size.

Built in:

- `LoggingObserver`: every statement at `DEBUG`, slow statements at `WARNING`, through
  `System.Logger` (logger `ch.lxrin.ql.sql`). It needs no dependency and bridges to
  SLF4J and Log4j exist.
- `ObservationExecutionObserver` in `lxrin-ql-spring`: a Micrometer `Observation`
  named `lxrin.ql.statement`, with the keys `kind`, `tables` and `origin`. This gives
  timers and tracing spans. It is configured automatically when Micrometer is present.

## Custom executors

`SqlExecutor` receives rendered SQL with `?` placeholders and typed binds.
`JdbcExecutor` is the implementation for JDBC. Implement the interface to route
statements elsewhere, or use `MockExecutor` from `lxrin-ql-test` in unit tests.
