# Extension points

Every statement goes through the same pipeline, whether it comes from the DSL or
from a repository:

```
statement model ─► table policies ─► column conventions ─► listeners (before) ─► render
               ─► observers ─► execute ─► listeners (after) ─► result for the caller
```

The statement is an inspectable model (`ch.lxrin.ql.statement`). Policies,
conventions and listeners read and change that model; it becomes SQL only at the
end.

## Statement listeners

A `StatementListener` intercepts `INSERT`, `UPDATE`, `DELETE` and `TRUNCATE`:

```java
public interface StatementListener {
    default boolean appliesTo(Table<?> table) { return true; }

    default void beforeInsert(InsertContext c) {}
    default void beforeUpdate(UpdateContext c) {}
    default void beforeDelete(DeleteContext c) {}
    default void beforeTruncate(TruncateContext c) {}

    default void afterInsert(WriteResult r) {}
    default void afterUpdate(WriteResult r) {}
    default void afterDelete(WriteResult r) {}
    default void afterTruncate(TruncateResult r) {}
}
```

**Before execution** a listener sees the structure of the statement and may change it:

| Context | Reads | Changes |
|---|---|---|
| `InsertContext` | table, `statement()` (rows as column → value maps, `ON CONFLICT`), `entityWrites()` | `setValue(col, value)`, `forceValue(..)`, `requestReturning(cols)`, `reject(reason)` |
| `UpdateContext` | table, assignments, `WHERE`, `originalKind()` (`DELETE` for soft deletes), entity writes | `set(col, value)`, `addCondition(..)`, `requestReturning(..)`, `reject(..)` |
| `DeleteContext` | table, `WHERE`, entity writes | `addCondition(..)`, `requestReturning(..)`, `reject(..)` |
| `TruncateContext` | tables | `reject(..)`, `captureRowsBeforeTruncate()` |

`entityWrites()` contains the entities of a repository call with their changes
(old and new values per column).

**After execution** a `WriteResult` provides:

- `affectedRows()`: the rows with the requested columns, **even when the caller asked
  for other `RETURNING` columns or none**. The pipeline adds the requested columns and
  removes them from the caller's result.
- `rowCount()`, `wasUpsert()`, and for upserts `row.inserted()` (from `xmax = 0`).
- `dsl()`: a query context on **the same connection and transaction**. Its statements
  report `Origin.Type.LISTENER`, which helps to avoid recursion.
- `transaction()`: the `TransactionScope`, for transaction-scoped state such as one
  audit revision per transaction.

If no transaction is active, the statement and its listeners run in one that starts
automatically. An exception thrown by a listener rolls everything back.

Limits: `TRUNCATE` returns no rows. A listener can reject it, or call
`captureRowsBeforeTruncate()` to read (and lock) the rows first.

### Example: audit history

The ISMS keeps a full history: one `revision` row per transaction (who, when), and
for every changed row a copy in `<table>_aud`. It is built entirely on the extension
points; the complete, tested version is
[`IsmsAuditListener`](../integration-tests/src/test/java/ch/lxrin/ql/it/isms/IsmsAuditListener.java).

```java
public final class AuditListener implements StatementListener {
    private static final TransactionScope.Key<Long> REVISION_ID = TransactionScope.key("audit.revision");
    private final Map<Table<?>, Table<?>> auditTables = Map.of(USERS, USER_AUD, ASSET, ASSET_AUD);

    public boolean appliesTo(Table<?> t) { return auditTables.keySet().stream().anyMatch(a -> a.sameTable(t)); }

    public void beforeInsert(InsertContext c) { c.requestReturning(c.table().columns()); }
    public void beforeUpdate(UpdateContext c) { c.requestReturning(c.table().columns()); }
    public void beforeDelete(DeleteContext c) { c.requestReturning(c.table().columns()); }
    public void beforeTruncate(TruncateContext c) { c.reject("audited tables cannot be truncated"); }

    public void afterInsert(WriteResult r) { write(r, "ADD"); }
    public void afterUpdate(WriteResult r) { write(r, r.originalKind() == StatementKind.DELETE ? "DEL" : "MOD"); }
    public void afterDelete(WriteResult r) { write(r, "DEL"); }

    private void write(WriteResult r, String revtype) {
        long rev = r.transaction().attribute(REVISION_ID, () -> r.dsl().insertInto(REVISION)
                .set(REVISION.CREATED_BY, currentUser.get())
                .set(REVISION.CREATED_AT, clock.instant())
                .returning(REVISION.ID).fetchOne());
        Table<?> aud = auditTables.get(r.table());
        for (AffectedRow row : r.affectedRows()) {
            Insert<?> insert = r.dsl().insertInto(aud)
                    .setUnchecked(aud.column("rev").orElseThrow(), rev)
                    .setUnchecked(aud.column("revtype").orElseThrow(), revtype);
            row.values().forEach((column, value) -> insert.setUnchecked(aud.column(column.name()).orElseThrow(), value));
            insert.execute();
        }
    }
}
```

It covers entity saves, `saveAll`, bulk `update(..)` and `deleteAll(..)`, soft
deletes and upserts, because all of them go through the pipeline.

## Column conventions

Columns such as `created_at`, `created_by`, `updated_at` and `updated_by` are filled
automatically, with no code at the call site:

```java
QueryContext.builder()
    .convention(ColumnConventions.createdAt("created_at", clock))
    .convention(ColumnConventions.onInsert("created_by", String.class, c -> currentUser.get()))
    .convention(ColumnConventions.updatedAt("updated_at", clock))                        // insert and update
    .convention(ColumnConventions.onInsertAndUpdate("updated_by", String.class, c -> currentUser.get()))
    .convention(ColumnConventions.expression("synced_at", Instant.class, ColumnConvention.When.UPDATE, () -> now()))
```

- A convention applies to every table that has a column with that name. Use
  `.where(column -> …)` to narrow it.
- A convention does not replace a value set by the caller, unless it is `.forced()`.
- The value is bound with the column's own type (value objects work). If the Java
  type does not match the column, the first write fails with a clear message.
- An update that changes nothing does not run, so it does not touch `updated_at`.
- **Version column**: `versionColumn("version")` increments the version on every
  update and checks it on entity writes (see
  [optimistic locking](entities-and-repositories.md#writing)).

## Table policies

A `TablePolicy` applies to every statement that touches a table:

```java
public interface TablePolicy {
    boolean appliesTo(Table<?> table);
    default Condition filter(Table<?> table, PolicyContext c) { return Condition.noCondition(); }
    default void onInsert(InsertContext c) {}
    default void onUpdate(UpdateContext c) {}
    default DmlStatement onDelete(DeleteStatement d, PolicyContext c) { return d; }
    default void onTruncate(TruncateContext c) {}
}
```

The filter is added to **every reference** of the table:

- `FROM`, to `WHERE`;
- joined tables, to `ON`, so outer joins stay outer joins;
- sub-queries and CTE bodies;
- the `WHERE` of `UPDATE` and `DELETE`;
- repository methods.

Two policies ship with the core:

| Policy | Effect |
|---|---|
| `SoftDeletePolicy("deleted_at", clock)` | only rows with `deleted_at IS NULL`; `DELETE` becomes `UPDATE … SET deleted_at = now` (listeners see an `UPDATE` with `originalKind() == DELETE`) |
| `TenantPolicy<>("organization_id", SqlTypes.UUID, currentOrg)` | only rows of the current tenant; inserts get the current tenant; changing the tenant column and `TRUNCATE` are rejected |

Bypassing is explicit and easy to find in code:

```java
ctx.bypassing(SoftDeletePolicy.class).select(USERS.NAME).from(USERS).fetch();
users.bypassing(TenantPolicy.class).findAll();
```

`LxrinArchRules.policyBypassOnlyIn("..admin..")` from `lxrin-ql-test` restricts where
that may happen. Statements written with `Sql.raw` are not policy-aware.

## Custom types

Types are registered through `DataType` and `Converter` (see
[Fields and types](expressions.md#data-types)). JSON records use
`SqlTypes.jsonb(MyRecord.class)` with the context's `JsonCodec`; `lxrin-ql-spring`
configures a Jackson codec automatically. To use a custom type for generated
columns, declare a [forced type](code-generation.md#forced-types-value-objects-json-records).

## Custom functions and operators

Functions, aggregates and operators that are not in the catalog are defined once, as
typed constants:

```java
public final class Pg {
    public static final Routines.Function2<String, String, Double> SIMILARITY =
            Routines.function("similarity", SqlTypes.TEXT, SqlTypes.TEXT, SqlTypes.FLOAT8);
    public static final Routines.BinaryOperator<String, String, Double> DISTANCE =
            Routines.operator("<->", SqlTypes.TEXT, SqlTypes.TEXT, SqlTypes.FLOAT8);
    public static final Routines.ConditionOperator<String, String> SIMILAR =
            Routines.conditionOperator("%", SqlTypes.TEXT, SqlTypes.TEXT);
    public static final Routines.Aggregate1<BigDecimal, BigDecimal> MEDIAN =
            Routines.aggregate("my_schema.median", SqlTypes.NUMERIC, SqlTypes.NUMERIC);
}

select(USERS.NAME, Pg.SIMILARITY.call(USERS.NAME, "ada").as("score"))
    .from(USERS)
    .where(Pg.SIMILAR.apply(USERS.NAME, "ada"))
    .orderBy(Pg.DISTANCE.apply(USERS.NAME, "ada").asc())
```

Names must be identifiers (optionally schema-qualified). Operator symbols are checked
against PostgreSQL's operator characters; symbols with `?` are rejected because they
clash with JDBC placeholders.
