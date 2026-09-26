# Queries

All examples assume these imports and the generated tables `USERS` (`app_user`)
and `ORDERS` (`orders`, with the foreign key `FK_USER_ID` to `app_user`):

```java
import static ch.lxrin.ql.QL.*;
import static com.example.db.Tables.*;
```

Statements created with `QL.*` (or `QL.createContribution(..)` without the static
import) run on `QueryContext.getDefault()`. Statements
created with `ctx.select(..)`, `ctx.insertInto(..)`, … run on `ctx`. Everything else
is identical. Every builder can be rendered without running it: `render()` returns
the SQL with `?` placeholders and the typed binds, and `toString()` returns the same
as text.

- [Two styles](#two-styles): [createContribution](#the-createcontribution-style) · [select](#the-select-style)
- [SELECT](#select): [select list](#select-list-and-results) · [FROM and joins](#from-and-joins) · [WHERE](#where)
  · [grouping](#group-by-and-having) · [windows](#window-functions) · [ordering and paging](#order-by-limit-and-offset)
  · [keyset pagination](#keyset-pagination) · [set operations](#set-operations) · [sub-queries](#sub-queries)
  · [CTEs](#common-table-expressions) · [derived tables and LATERAL](#derived-tables-lateral-and-set-returning-functions)
  · [locking](#row-locking)
- [INSERT](#insert) · [upserts](#upserts) · [UPDATE](#update) · [DELETE](#delete) · [TRUNCATE](#truncate) · [RETURNING](#returning)

---

## Two styles

`ch.lxrin.ql.QL` is the entry point for both: `QL.createContribution(..)`,
`QL.select(..)`, `QL.eq(..)`, … With `import static ch.lxrin.ql.QL.*` the prefix can be
left out, as in the rest of this page.

LxrinQL has two ways to write a statement. Both share the same operators, type checks
and pipeline (policies, conventions, listeners, observers), and they can be mixed
freely. The `createContribution` style is a thin layer over the `select(..)` builders.

### The createContribution style

```java
record UserSummary(UUID id, String name, String email) {}

List<UserSummary> users = QL.createContribution(UserSummary.class, USERS, (c, b) -> c
        .select(USERS.ID, USERS.NAME, USERS.EMAIL)
        .where(USERS.EMAIL.endsWith("@example.org"),
               USERS.CREATED_AT.ge(b.setInstant(since)),
               USERS.ROLE.in(b.setList(roles)))
        .orderBy(USERS.NAME.asc()))
    .fetch();

// with import static ch.lxrin.ql.QL.* the prefix is optional
createInsert(USERS, (c, b) -> c.set(USERS.ID, id).set(USERS.NAME, b.setString(name))).execute();
createUpdate(USERS, (c, b) -> c.set(USERS.NAME, name).where(USERS.ID.eq(id))).execute();
createDelete(SESSIONS, (c, b) -> c.where(SESSIONS.EXPIRES_AT.lt(b.setInstant(now)))).execute();
createUpsert(USERS, (c, b) -> c.set(USERS.ID, id).set(USERS.NAME, name)).execute();   // ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name
```

- **`c` starts the statement.**
  - In `createContribution`, `c.select(..)` (or `c.select(List<Field<?>>)`,
    `c.selectDistinct(..)`, `c.selectAll()`) returns a normal `Select` with
    `FROM table` already set. Joins, grouping, CTEs, paging and everything else
    below work as usual.
  - In `createInsert`, `createUpdate`, `createDelete` and `createUpsert`, `c` is the
    `Insert`, `Update` or `Delete` builder itself.
  - `c.all()` confirms an `UPDATE` or `DELETE` of every row.
- **`b` creates explicit, typed bind parameters:**
  - `setString`, `setInt`, `setLong`, `setShort`, `setBigDecimal`, `setDouble`,
    `setFloat`, `setBoolean`, `setUuid`, `setInstant`, `setLocalDate`,
    `setLocalDateTime`, `setLocalTime`, `setDuration`, `setBytes`;
  - `set(value, type)` and `set(column, value)`;
  - `setNull(type)`;
  - `setList(..)` for `in(..)`.

  `b` is optional. A plain value is bound automatically, and
  `USERS.EMAIL.eq(email)` renders the same SQL as `USERS.EMAIL.eq(b.setString(email))`.
  `b.setX(null)` throws; use `b.setNull(type)`.
- **The result type** is one of:
  - a record: every component is matched to a selected field by name (`created_at`
    or an alias → `createdAt`). A component without a field of that name fails when
    the query is built, and the message lists every unmatched component and every
    unused field. Rename a field with `.as("name")`, or map by position explicitly
    with `c.mapByPosition().select(..)`. Values are never assigned by position
    silently, so reordering the select list cannot put them into the wrong components;
  - `Row`;
  - a single column's type, e.g. `Long.class` for `c.select(count())`.

  Types are checked when the query is built, not when rows arrive. A `null` for a
  primitive component fails with a clear message.
- **Without a table,** `createContribution(Type.class, (c, b) -> c.select(..).from(..))`
  leaves `FROM` to you.
- **On a context:** `ctx.createContribution(..)`, `ctx.createUpdate(..)`, … run on
  that `QueryContext` instead of the default.

### The select style

The same query with the fluent builders and a constructor reference:

```java
List<UserSummary> users = select(USERS.ID, USERS.NAME, USERS.EMAIL)
        .from(USERS)
        .where(USERS.EMAIL.endsWith("@example.org"), ge(USERS.CREATED_AT, since), in(USERS.ROLE, roles))
        .orderBy(USERS.NAME.asc())
        .fetch(UserSummary::new);
```

Here the compiler checks the mapping itself: `fetch(UserSummary::new)` only compiles
if the constructor takes `(UUID, String, String)`. The rest of this page uses this
style, and everything applies equally inside `createContribution`.

---

## SELECT

### Select list and results

| Start | Rows |
|---|---|
| `select(f1)` | the values of `f1`, e.g. `List<String>` |
| `select(f1, …, f16)` | typed tuples `Row2<T1, T2>` … `Row16`; `fetch(Record::new)` maps them |
| `select(List<Field<?>>)` | `Row`, with typed access `row.get(USERS.NAME)` (for dynamic select lists) |
| `selectFrom(USERS)` | the generated `UserRow` records |
| `selectCount()` | `count(*)` as `Long` |
| `selectOne()` | `1`, for `exists(..)` |

`col(field)` returns the field itself, so select lists can be written as in 2.x. It keeps
the field's type and works in both styles and in `returning(..)`:

```java
Select1<String> names = select(col(USERS.NAME)).from(USERS);                        // same as select(USERS.NAME)
String name = QL.createContribution(String.class, USERS, (c, b) -> c
        .select(col(USERS.NAME))
        .where(eq(USERS.ID, id)))
        .fetchOne();
UUID id = insertInto(USERS).set(USERS.NAME, "Ada").returning(col(USERS.ID)).fetchOne();
```

```java
List<String> names = select(USERS.NAME).from(USERS).fetch();

List<Row2<UUID, String>> pairs = select(USERS.ID, USERS.NAME).from(USERS).fetch();

record UserSummary(UUID id, String name, String email) {}
List<UserSummary> summaries = select(USERS.ID, USERS.NAME, USERS.EMAIL).from(USERS).fetch(UserSummary::new);

Row row = select(List.of(USERS.ID, USERS.NAME)).from(USERS).fetchFirst().orElseThrow();
String name = row.get(USERS.NAME);
```

Aliases: `upper(USERS.NAME).as("n")` renders `upper(app_user.name) AS n` in the
select list and `n` elsewhere (for example in `ORDER BY`).

`distinct()` renders `SELECT DISTINCT`. `distinctOn(ORDERS.USER_ID)` gives the first
row of each group; add a matching `ORDER BY`.

### FROM and joins

```java
select(USERS.NAME, ORDERS.TOTAL)
    .from(USERS)
    .join(ORDERS).on(ORDERS.USER_ID.eq(USERS.ID))           // JOIN … ON …
    .leftJoin(ORDERS).onKey(ORDERS.FK_USER_ID)                   // along a generated foreign key
    .rightJoin(t).on(..)  .fullJoin(t).on(..)
    .join(t).using(t.ID)                                      // JOIN t USING (id)
    .crossJoin(t)  .naturalJoin(t)
```

`onKey(..)` works in both directions: from the referencing table to the referenced
one and the other way round. Aliases are respected. For self-joins, alias the table:

```java
UserTable manager = USERS.as("m");
select(USERS.NAME, manager.NAME).from(USERS).join(manager).on(manager.ID.eq(USERS.MANAGER_ID))
```

Joins that a filter needs only sometimes: `joinIf(flag, ORDERS, () -> ORDERS.USER_ID.eq(USERS.ID))`,
`joinIf(flag, ORDERS, ORDERS.FK_USER_ID)` and the same with `leftJoinIf`. The condition
supplier is only called if the flag is set.

### WHERE

Every `where(..)` argument and every further `where(..)` call is joined with `AND`:

```java
.where(USERS.ROLE.eq(Role.ADMIN), USERS.DELETED_AT.isNull())
.where(USERS.NAME.startsWith("A").or(USERS.EMAIL.endsWith("@example.org")))
.whereIf(filter.name() != null, () -> USERS.NAME.containsIgnoreCase(filter.name()))
```

`where(List<Condition>)` adds a list of conditions. All operators, their static
forms (`eq(a, b)`, `in(x, list)`, …) and optional filters (`when`, `…IfPresent`,
`Conditions.builder()`) are listed in [Conditions](conditions.md).
`noCondition()` is the neutral start value for conditions built in loops.

### GROUP BY and HAVING

```java
select(ORDERS.USER_ID, count(), sum(ORDERS.TOTAL).filter(ORDERS.STATUS.eq("PAID")))
    .from(ORDERS)
    .groupBy(ORDERS.USER_ID)
    .having(sum(ORDERS.TOTAL).gt(new BigDecimal("1000")))

.groupBy(rollup(ORDERS.STATUS, ORDERS.USER_ID))
.groupBy(cube(ORDERS.STATUS, ORDERS.USER_ID))
.groupBy(groupingSets(groupingSet(ORDERS.STATUS), groupingSet(ORDERS.USER_ID), groupingSet()))
select(grouping(ORDERS.STATUS), …)
```

Aggregate modifiers return new objects and keep the type family:
`countDistinct(x)`, `sum(x).distinct()`, `stringAgg(USERS.NAME, ", ").orderBy(USERS.NAME.asc())`,
`count().filter(condition)`, `percentileCont(0.5, ORDERS.TOTAL.asc())` (median) and
`mode(ORDERS.STATUS.asc())`.

### Window functions

Window-only functions (`rowNumber()`, `rank()`, `lag(..)`, …) are not fields until
`over(..)` is called, so a forgotten window is a compile error. Aggregates can also
be used as window functions.

```java
rowNumber().over(partitionBy(ORDERS.USER_ID).orderBy(ORDERS.ORDERED_ON.desc()))
sum(ORDERS.TOTAL).over(partitionBy(ORDERS.USER_ID).orderBy(ORDERS.ORDERED_ON.asc())
        .rowsBetween(unboundedPreceding(), currentRow()))                 // running total
lag(ORDERS.TOTAL, 1, BigDecimal.ZERO).over(orderBy(ORDERS.ORDERED_ON.asc()))
rank().over()                                                              // OVER ()

WindowDefinition w = window("w", partitionBy(ORDERS.USER_ID).orderBy(ORDERS.ORDERED_ON.asc()));
select(rowNumber().over(w), sum(ORDERS.TOTAL).over(w)).from(ORDERS).window(w)
```

Frames: `rowsBetween`, `rangeBetween`, `groupsBetween`, `rows`, `range`, with
`unboundedPreceding()`, `preceding(n)`, `currentRow()`, `following(n)`,
`unboundedFollowing()` and `.exclude(WindowSpec.Exclude.CURRENT_ROW)`.

### ORDER BY, LIMIT and OFFSET

```java
.orderBy(USERS.NAME.asc(), USERS.CREATED_AT.desc().nullsLast())
.orderBy(USERS.NAME)                              // ASC
.limit(20).offset(40)                             // written as literals
.limit(param(pageSize))                           // bound
.page(2, 25)                                      // LIMIT 25 OFFSET 50 (first page = 0)
.orderBy(ORDERS.TOTAL.desc()).limitWithTies(3)    // FETCH FIRST 3 ROWS WITH TIES
.orderBy(sortFields)                              // a List<SortField<?>>
.orderBy(Sorts.from(request.sort(), SORTABLE))    // "name,desc" through a whitelist
```

`Sorts.from(param, Map<String, Field<?>>)` accepts only the keys of the whitelist and
the directions `asc` and `desc`. Anything else throws `InvalidSortException`; see
[dynamic statements](conditions.md#dynamic-statements).

### Keyset pagination

Keyset pagination selects the rows after the last row of the previous page. Unlike
`OFFSET` it stays fast on large tables and does not skip or repeat rows when data
changes between requests.

```java
Page<Row3<UUID, String, Instant>> page = select(USERS.ID, USERS.NAME, USERS.CREATED_AT)
        .from(USERS)
        .orderBy(USERS.CREATED_AT.desc(), USERS.ID.desc())     // end with a unique column
        .seekAfterCursor(request.cursor())                      // null for the first page
        .limit(50)
        .fetchPage();

page.items();                                                   // the rows
page.nextCursor().map(Cursor::encode);                          // an opaque, URL-safe token for the next page
```

- With uniform sort directions the condition is an index-friendly row comparison,
  `(created_at, id) < (?, ?)`. Mixed directions expand to the equivalent `OR` form.
- The `ORDER BY` fields must be in the select list.
- `seekAfter(values…)` and `seekAfter(Cursor)` take the values directly.
- Repositories offer the same through `findPage(condition, cursor, limit, orderBy…)`.

### Set operations

```java
select(USERS.EMAIL).from(USERS)
    .union(select(CUSTOMERS.EMAIL).from(CUSTOMERS))      // also unionAll, intersect(All), except(All)
    .orderBy(USERS.EMAIL.asc())                          // applies to the combined result
```

Both sides must be selects of the same types; the compiler checks this.

### Sub-queries

```java
.where(USERS.ID.in(select(ORDERS.USER_ID).from(ORDERS).where(ORDERS.TOTAL.gt(BigDecimal.TEN))))
.where(exists(selectOne().from(ORDERS).where(ORDERS.USER_ID.eq(USERS.ID))))
.where(notExists(…))

Field<Long> orderCount = selectCount().from(ORDERS).where(ORDERS.USER_ID.eq(USERS.ID)).asField().as("orders");
select(USERS.NAME, orderCount).from(USERS)                                    // scalar sub-query

select(arrayOf(select(ORDERS.ID).from(ORDERS).where(..)))                      // ARRAY(SELECT …)
```

`in(Collection)` binds **one array** (`= ANY(?)`), so the SQL text is the same for
any number of values: `USERS.ID.in(ids)`.

### Common table expressions

A CTE is a table built from a query. Its columns are the named output fields of the
query, accessed with `field(..)`:

```java
Field<BigDecimal> total = sum(ORDERS.TOTAL).as("total");
Cte paid = cte("paid", select(ORDERS.USER_ID, total).from(ORDERS).where(ORDERS.STATUS.eq("PAID")).groupBy(ORDERS.USER_ID));

select(USERS.NAME, paid.field(total))
    .with(paid)
    .from(USERS)
    .join(paid).on(paid.field(ORDERS.USER_ID).eq(USERS.ID))
```

`cte(..).materialized()` and `.notMaterialized()` control inlining (PostgreSQL 12+).

A **recursive** CTE refers to itself, so its columns are declared first:

```java
Cte t = recursiveCte("t");
NumberColumn<Integer> n = t.declareNumber("n", SqlTypes.INT4);
t.as(select(inline(1)).unionAll(select(n.plus(1)).from(t).where(n.lt(10))));
List<Integer> oneToTen = select(n).withRecursive(t).from(t).fetch();
```

**Data-modifying** CTEs take an `INSERT`, `UPDATE` or `DELETE` with `RETURNING`:

```java
Cte moved = cte("moved", deleteFrom(ORDERS).where(ORDERS.ORDERED_ON.lt(cutoff)).returning(ORDERS.ID, ORDERS.TOTAL));
long count = selectCount().with(moved).from(moved).fetchOne();
```

### Derived tables, LATERAL and set-returning functions

```java
DerivedTable last = select(ORDERS.TOTAL, ORDERS.ORDERED_ON).from(ORDERS)
        .where(ORDERS.USER_ID.eq(USERS.ID)).orderBy(ORDERS.ORDERED_ON.desc()).limit(1)
        .asTable("lo");
select(USERS.NAME, last.field(ORDERS.TOTAL)).from(USERS).leftJoin(lateral(last)).onTrue()

FunctionTable<Integer> n = tableOf(generateSeries(1, 10), "n");
select(n.value()).from(n)                               // generate_series(1, 10) AS n(value)
```

### Row locking

```java
.forUpdate()  .forNoKeyUpdate()  .forShare()  .forKeyShare()
.forUpdate().of(USERS)                  // FOR UPDATE OF app_user
.forUpdate().nowait()                   // LockNotAvailableException if locked
.forUpdate().skipLocked()               // job queues
```

---

## INSERT

```java
// column by column
insertInto(USERS)
    .set(USERS.ID, users.createKey())
    .set(USERS.NAME, "Ada")
    .set(USERS.EMAIL, "ada@example.org")
    .set(USERS.CREATED_AT, now())                 // expressions work too
    .execute();

// several rows, column by column; columns missing in a row get DEFAULT
insertInto(USERS).set(USERS.NAME, "A").newRow().set(USERS.NAME, "B").set(USERS.ROLE, Role.ADMIN).execute();

// typed columns and values
insertInto(USERS).columns(USERS.NAME, USERS.EMAIL)
    .values("Ada", "ada@example.org")
    .values("Alan", "alan@example.org")
    .execute();

// INSERT … SELECT (the select must have matching types)
insertInto(ARCHIVE).columns(ARCHIVE.ID, ARCHIVE.NAME).select(select(USERS.ID, USERS.NAME).from(USERS).where(..)).execute();

insertInto(EVENTS).defaultValues().execute();
insertInto(ORDERS).set(ORDERS.ID, 42L).overridingSystemValue().execute();   // GENERATED ALWAYS identity
```

### Upserts

```java
insertInto(USERS)
    .set(USERS.EMAIL, email).set(USERS.NAME, name)
    .onConflict(USERS.EMAIL)
    .doUpdateSetExcluded(USERS.NAME)                               // name = EXCLUDED.name
    .doUpdateSet(USERS.LOGIN_COUNT, USERS.LOGIN_COUNT.plus(1))     // any expression
    .doUpdateWhere(USERS.ACTIVE)                                    // DO UPDATE … WHERE
    .execute();

insertInto(USERS).set(..).doNothing()                              // ON CONFLICT DO NOTHING
insertInto(USERS).set(..).onConflictOnConstraint(USERS.UK_EMAIL).doNothing()
insertInto(USERS).set(..).onConflict(USERS.EMAIL).onConflictWhere(USERS.DELETED_AT.isNull()).doNothing()   // partial index
AbstractInsert.excluded(USERS.NAME)                                 // EXCLUDED.name as a typed field
```

## UPDATE

```java
update(USERS)
    .set(USERS.ROLE, Role.ADMIN)
    .set(USERS.LOGIN_COUNT, USERS.LOGIN_COUNT.plus(1))
    .setNull(USERS.DELETED_AT)
    .where(USERS.ID.eq(id))
    .execute();                                            // number of rows

update(ORDERS).set(ORDERS.STATUS, "VIP").from(USERS)       // UPDATE … FROM
    .where(ORDERS.USER_ID.eq(USERS.ID), USERS.ROLE.eq(Role.ADMIN))
    .execute();
```

An `UPDATE` without `WHERE` is rejected; call `.allRows()` (or `.all()`) to update
every row on purpose. This also applies when every condition resolves to
`noCondition()`, e.g. an empty `Conditions.builder()` or `eqIfPresent(Optional.empty())`.

For PATCH requests, `setIf(flag, column, value)` and `setIfPresent(column, Optional)`
set a column only if the flag is set or the value is present:

```java
update(USERS)
    .setIfPresent(USERS.NAME, patch.name())
    .setIfPresent(USERS.EMAIL, patch.email())
    .setIf(patch.deactivate(), USERS.ACTIVE, false)
    .where(USERS.ID.eq(id))
    .execute();
```

## DELETE

```java
deleteFrom(SESSIONS).where(SESSIONS.EXPIRES_AT.lt(Instant.now())).execute();
deleteFrom(ORDERS).using(USERS).where(ORDERS.USER_ID.eq(USERS.ID), USERS.DELETED_AT.isNotNull()).execute();
deleteFrom(TMP).allRows().execute();                       // required without WHERE
```

As with `UPDATE`, a `DELETE` whose conditions all resolve to `noCondition()` is
rejected.

With a [soft-delete policy](extension-points.md#table-policies), a `DELETE` becomes
an `UPDATE … SET deleted_at = now`.

## TRUNCATE

```java
truncate(IMPORT_STAGING).restartIdentity().cascade().execute();
```

## RETURNING

```java
UUID id = insertInto(USERS).set(USERS.NAME, "Ada").returning(USERS.ID).fetchOne();

Row2<UUID, Instant> created = insertInto(USERS).set(..).returning(USERS.ID, USERS.CREATED_AT).fetchOne();

List<UserRow> changed = update(USERS).set(USERS.ACTIVE, false).where(..).returningAll().fetch();

Optional<UUID> inserted = insertInto(USERS).set(..).onConflict(USERS.EMAIL).doNothing()
        .returning(USERS.ID).fetchOptional();                 // empty if the row already existed
```

Listeners may request more `RETURNING` columns (for example for an audit log). The
pipeline adds them to the statement and removes them from your result again.
