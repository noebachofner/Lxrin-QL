# Queries

All examples assume `import static ch.lxrin.ql.LxrinQL.*;` and these tables:

```java
PersonTable p = new PersonTable();   // PERSON p   (PERSON_NR, FIRST_NAME, LAST_NAME, STATUS, AGE, ...)
OrderTable  o = new OrderTable();    // ORDERS o   (ORDER_ID, PERSON_NR, TOTAL, STATUS, CREATED_AT, DATA)
```

Every builder can show its SQL with `buildSql()` and its SQL plus parameters
with `build()`. Clauses can be added in any order, because the SQL is always
rendered in the correct order.

- [SELECT](#select)
  - [Select list](#select-list) · [FROM](#from) · [Joins](#joins) · [WHERE](#where)
  - [GROUP BY / HAVING](#group-by--having) · [Window functions](#window-functions)
  - [ORDER BY / LIMIT / paging](#order-by--limit--paging) · [Set operations](#set-operations)
  - [Sub-queries](#sub-queries) · [CTEs](#common-table-expressions-with) · [Row locking](#row-locking)
- [INSERT](#insert) · [Upsert](#upsert-on-conflict) · [UPDATE](#update) · [DELETE](#delete) · [TRUNCATE](#truncate)
- [RETURNING](#returning) · [Data-modifying CTEs](#data-modifying-ctes)

---

## SELECT

### Starting a query

| Factory | Result type |
|---|---|
| `select(items…)` | `SelectQuery<Object[]>` |
| `select(Type.class, items…)` | `SelectQuery<Type>` |
| `selectDistinct(items…)` | `SelectQuery<Object[]>` with `DISTINCT` |
| `selectFrom(table)` | `SELECT * FROM table` |
| `createContribution(Type.class)` / `query(Type.class)` | empty `SelectQuery<Type>` |

### Select list

```java
select(p.personNr, p.lastName)                          // columns
select(p.columns())                                     // all declared columns
select(p.all())                                         // p.*
select(upper(p.lastName).as("name"))                    // expression with alias
select(as("p.FIRST_NAME || ' ' || p.LAST_NAME", "fullName"))  // SQL fragment with alias
select(count().as("cnt"), max(o.total))
select(caseWhen(ge(p.age, 18), inline("adult")).otherwise(inline("minor")).as("ageGroup"))
select(select(count()).from(o).where(eq(o.personNr, p.personNr)).as("orderCount"))  // scalar sub-query
```

`.select(..)` can be called several times, and each call appends. A `String`
item is a SQL fragment. Aliases come from `.as(..)` or from a column's Java
alias, and the result mapper uses these names.

```java
.distinct()                          // SELECT DISTINCT ...
.distinctOn(o.personNr)              // SELECT DISTINCT ON (o.PERSON_NR) ...  – first row per group
```

A query without `FROM` is valid: `select(now(), version()).single()`.

### FROM

```java
.from(p)                                         // PERSON p
.from(p, o)                                      // PERSON p, ORDERS o
.from("PERSON p")                                // SQL fragment
.from(subQuery.as("x"))                          // derived table: (SELECT ...) AS x
.from(generateSeries(inline(1), inline(10)).as("n"))   // set-returning function
.from(unnest(val(ids)).as("t(id)"))
.from(p, lateral(subQuery.as("x")))
```

### Joins

```java
.join(o, eq(o.personNr, p.personNr))                 // JOIN ORDERS o ON ...
.innerJoin(o, eq(o.personNr, p.personNr))            // INNER JOIN
.leftJoin(o, eq(o.personNr, p.personNr), eq(o.status, val("PAID")))   // several ON conditions → AND
.rightJoin(o, ...)
.fullJoin(o, ...)
.crossJoin(o)
.naturalJoin("ADDRESS")
.joinUsing("ADDRESS a", "PERSON_NR")                 // JOIN ADDRESS a USING (PERSON_NR)
.leftJoinUsing("ADDRESS a", "PERSON_NR")
.leftJoin(lateral(lastOrder.as("lo")))               // LEFT JOIN LATERAL (...) AS lo ON TRUE
.join("LEFT JOIN ADDRESS a ON a.PERSON_NR = p.PERSON_NR")   // verbatim join clause
```

A join without an `ON` condition renders `ON TRUE`, which is the usual form
for `LATERAL` joins. For a self-join, create the table with another alias:
`PersonTable manager = new PersonTable("m")`.

### WHERE

```java
.where(eq(p.status, val("ACTIVE")), ge(p.age, 18))           // … AND …
.where(eq(p.status, val("A")), or(), eq(p.status, val("B")))  // explicit tokens are kept
.where(or(eq(p.status, val("A")), isNull(p.status)))         // grouped: (… OR …)
.where(p.age.between(18, 65).and(p.lastName.ilike(val("A%"))))   // fluent style
.whereIf(filter.status() != null, eq(p.status, val(filter.status())))
.whereIf(name != null, () -> ilike(p.lastName, val(name + "%")))  // lazy
.where("p.DELETED_AT IS NULL")                                // SQL fragment
```

Joining rules:

- Within one `where(..)` call, two adjacent conditions without an `and()` or
  `or()` token between them are joined with `AND`.
- Several `where(..)` calls are joined with `AND`. A call that contains an
  `or()` token is wrapped in parentheses, so
  `.where(a, or(), b).where(c)` renders `(a OR b) AND c`.
- `and(c1, c2, …)` / `or(c1, c2, …)` skip `null` entries. That is useful for
  optional filters: `and(nameFilter, statusFilter)`.

The complete list of conditions is in [Expressions & conditions](expressions.md#conditions).

### GROUP BY / HAVING

```java
select(o.personNr, count().as("orders"), sum(o.total).as("revenue"))
    .from(o)
    .groupBy(o.personNr)
    .having(gt(sum(o.total), 1000))

.groupBy(rollup(o.status, o.personNr))                 // GROUP BY ROLLUP (...)
.groupBy(cube(o.status, dateTrunc("month", o.createdAt)))
.groupBy(groupingSets(groupingSet(o.status), groupingSet(o.personNr), groupingSet()))
select(o.status, grouping(o.status).as("isTotal"), sum(o.total))   // GROUPING(...)
```

Aggregate modifiers:

```java
count().filter(eq(o.status, val("PAID")))             // count(*) FILTER (WHERE ...)
countDistinct(o.personNr)                             // count(DISTINCT ...)
sum(o.total).distinct()
stringAgg(p.lastName, ", ").orderBy(p.lastName.asc()) // string_agg(... ORDER BY ...)
percentileCont(0.5).withinGroup(o.total.asc())        // median
mode().withinGroup(o.status)
```

### Window functions

```java
rowNumber().over(partitionBy(o.personNr).orderBy(o.createdAt.desc()))
sum(o.total).over(partitionBy(o.personNr))                          // total per person on every row
sum(o.total).over(window().orderBy(o.createdAt)
        .rowsBetween(unboundedPreceding(), currentRow()))           // running total
avg(o.total).over(window().orderBy(o.createdAt).rowsBetween(preceding(2), following(2)))
lag(o.total, 1, inline(0)).over(window().orderBy(o.createdAt))
rank().over()                                                       // OVER ()

// named window
select(rowNumber().over("w"), sum(o.total).over("w"))
    .from(o)
    .window("w", partitionBy(o.personNr).orderBy(o.createdAt))
```

Frames: `rowsBetween`, `rangeBetween`, `groupsBetween`, `rows`, `range`, or
`frame("ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING EXCLUDE CURRENT ROW")`.

### ORDER BY / LIMIT / paging

```java
.orderBy(p.lastName)                                 // default direction
.orderBy(p.lastName.asc(), p.age.desc().nullsLast())
.orderBy(desc(count()), inline(1))                   // by expression / position
.limit(20).offset(40)
.limit(val(pageSize))                                // bound
.page(2, 25)                                         // LIMIT 25 OFFSET 50 (page index starts at 0)
.orderBy(o.total.desc()).limitWithTies(3)            // FETCH FIRST 3 ROWS WITH TIES
```

To show a page together with the total number of rows, run the same query
twice: `.page(i, size).multiple()` for the page and `.fetchCount()` on a copy
without paging.

### Set operations

```java
select(p.email).from(p)
    .union(select(c.email).from(c))          // UNION (SELECT ...)
    .unionAll(...) .intersect(...) .intersectAll(...) .except(...) .exceptAll(...)
    .orderBy(inline(1))                      // applies to the combined result
```

### Sub-queries

Every statement is also an expression. When nested it is rendered in
parentheses, and its bind parameters are merged into the outer statement.

```java
.where(in(p.personNr, select(o.personNr).from(o).where(gt(o.total, 1000))))
.where(exists(select(inline(1)).from(o).where(eq(o.personNr, p.personNr))))
.where(notExists(...))
.where(gt(o.total, all(select(o.total).from(o).where(...))))
.where(eq(p.personNr, any(val(new Long[]{1L, 2L}))))       // = ANY(array)
select(arrayOf(select(o.orderId).from(o).where(...)).as("orderIds"))   // ARRAY(SELECT ...)
```

### Common table expressions (WITH)

```java
SelectQuery<Object[]> revenue = select(o.personNr, sum(o.total).as("total"))
        .from(o).groupBy(o.personNr);

select(p.lastName, "r.total")
    .with("r", revenue)                                // WITH r AS (...)
    .from(p)
    .join("r", "r.PERSON_NR = p.PERSON_NR")

.with("t(a, b)", query)                               // with column list
.withMaterialized("t", query)                          // AS MATERIALIZED (PostgreSQL 12+)
.withNotMaterialized("t", query)

// recursive: numbers 1..10
createContribution(Integer.class)
    .withRecursive("t(n)", select(inline(1)).unionAll(select("n + 1").from("t").where(lt("n", inline(10)))))
    .select("n").from("t")
    .multiple();
```

### Row locking

```java
.forUpdate()                     // FOR UPDATE
.forNoKeyUpdate()                // FOR NO KEY UPDATE
.forShare()  .forKeyShare()
.forUpdate().of(p)               // FOR UPDATE OF p
.forUpdate().nowait()            // fail immediately if locked
.forUpdate().skipLocked()        // skip locked rows (job queues)
```

---

## INSERT

```java
// column / value pairs (single row)
insertInto(p)
    .set(p.firstName, val("Ada"))
    .set(p.lastName, val("Lovelace"))
    .set(p.createdAt, now())
    .execute();

// multi-row VALUES
insertInto(p).columns(p.firstName, p.lastName)
    .values(val("Ada"), val("Lovelace"))
    .values(val("Alan"), val("Turing"))
    .execute();

// INSERT ... SELECT
insertInto(archive).columns(archive.personNr, archive.lastName)
    .select(select(p.personNr, p.lastName).from(p).where(eq(p.status, val("DELETED"))))
    .execute();

insertInto(p).defaultValues().execute();                // DEFAULT VALUES
insertInto(p).set(p.personNr, 42L).overridingSystemValue();   // write into an identity column
insertInto(p).set(p.age, defaultValue())                 // DEFAULT for one column
```

The target renders as `INSERT INTO PERSON AS p`, so the alias can be used in
`ON CONFLICT … DO UPDATE` and `RETURNING`.

### Upsert (ON CONFLICT)

```java
insertInto(p)
    .set(p.email, val(email))
    .set(p.lastName, val(name))
    .onConflict(p.email)
    .doUpdateSetExcluded(p.lastName)                         // LAST_NAME = EXCLUDED.LAST_NAME
    .doUpdateSet(p.loginCount, p.loginCount.plus(1))         // any expression
    .doUpdateWhere(ne(p.status, val("LOCKED")))               // DO UPDATE ... WHERE
    .execute();

insertInto(p).set(p.email, val(email)).doNothing()                  // ON CONFLICT DO NOTHING
insertInto(p).set(...).onConflict(p.email).doNothing()
insertInto(p).set(...).onConflictOnConstraint("person_email_key").doNothing()
insertInto(p).set(...).onConflict(p.email).onConflictWhere(isNull(p.deletedAt)).doNothing()  // partial index
```

## UPDATE

```java
update(p)
    .set(p.status, val("INACTIVE"))
    .set(p.age, p.age.plus(1))                                // expressions
    .set(p.modifiedAt, now())
    .setIf(newEmail != null, p.email, val(newEmail))          // conditional assignment
    .where(eq(p.personNr, id))
    .execute();                                               // number of rows

// UPDATE ... FROM (join)
update(o).set(o.status, val("VIP"))
    .from(p)
    .where(eq(o.personNr, p.personNr), eq(p.status, val("VIP")))
    .execute();

// multi-column assignment from a sub-query
update(o).set(new Object[]{o.status, o.total}, select(x.status, x.total).from(x).where(eq(x.id, o.orderId)))
```

For safety, an `UPDATE` without `WHERE` throws an exception. Call `.allRows()`
if you really mean to update every row.

## DELETE

```java
deleteFrom(p).where(eq(p.personNr, id)).execute();

// DELETE ... USING (join)
deleteFrom(o).using(p)
    .where(eq(o.personNr, p.personNr), eq(p.status, val("DELETED")))
    .execute();

deleteFrom(tmp).allRows().execute();     // required without WHERE
```

## TRUNCATE

```java
truncate(o, p).restartIdentity().cascade().execute();
```

## RETURNING

`INSERT`, `UPDATE` and `DELETE` support `RETURNING`. The returned rows are
mapped like `SELECT` results:

```java
Long id = insertInto(p).set(p.lastName, val("X")).returning(p.personNr).single(Long.class);

record Changed(long personNr, String status) {}
List<Changed> changed = update(p).set(p.status, val("A")).where(isNull(p.status))
        .returning(p.personNr, p.status)
        .multiple(Changed.class);

Optional<Long> deleted = deleteFrom(p).where(eq(p.email, val(mail))).returning(p.personNr).optional(Long.class);
List<Object[]> all = deleteFrom(o).where(...).returning(o.all()).multiple(Object[].class);
```

## Data-modifying CTEs

PostgreSQL lets `INSERT`, `UPDATE` and `DELETE` with `RETURNING` run inside
`WITH`. This moves rows in a single statement:

```java
createContribution(Long.class)
    .with("moved", deleteFrom(o).where(lt(o.createdAt, now().minus(interval("1 year")))).returning(o.all()))
    .with("archived", insertInto("ORDERS_ARCHIVE").select(selectFrom("moved")).returning(inline(1)))
    .select(count())
    .from("archived")
    .single();
```
