# Examples

Recipes for common tasks. All examples assume
`import static ch.lxrin.ql.LxrinQL.*;` and these table definitions:

```java
public class CustomerTable extends TableDef {
    public final Column id        = column("ID");
    public final Column name      = column("NAME");
    public final Column email     = column("EMAIL");
    public final Column country   = column("COUNTRY");
    public final Column tags      = column("TAGS");        // text[]
    public final Column profile   = column("PROFILE");     // jsonb
    public final Column createdAt = column("CREATED_AT");  // timestamptz
    public final Column deletedAt = column("DELETED_AT");  // timestamptz, null = active
    public CustomerTable() { super("CUSTOMER", "c"); }
}

public class OrderTable extends TableDef {
    public final Column id         = column("ID");
    public final Column customerId = column("CUSTOMER_ID");
    public final Column total      = column("TOTAL");      // numeric
    public final Column status     = column("STATUS");
    public final Column orderedAt  = column("ORDERED_AT"); // timestamptz
    public OrderTable() { super("ORDERS", "o"); }
}

CustomerTable c = new CustomerTable();
OrderTable o = new OrderTable();
```

---

## 1. Search form with optional filters

Only the filters that are set end up in the SQL.

```java
public List<CustomerRow> search(CustomerFilter f) {
    return select(CustomerRow.class, c.id, c.name, c.email)
            .from(c)
            .whereIf(f.name() != null, () -> ilike(c.name, val("%" + f.name() + "%")))
            .whereIf(f.country() != null, () -> eq(c.country, val(f.country())))
            .whereIf(f.tag() != null, () -> contains(c.tags, val(new String[]{f.tag()})))
            .whereIf(f.createdAfter() != null, () -> ge(c.createdAt, val(f.createdAfter())))
            .orderBy(c.name)
            .limit(200)
            .multiple();
}
```

## 2. Paging with a total count

```java
SelectQuery<CustomerRow> base = select(CustomerRow.class, c.id, c.name, c.email)
        .from(c)
        .where(eq(c.country, val("CH")));

long total = base.fetchCount();                                  // before paging is applied
List<CustomerRow> page = base.orderBy(c.name).page(pageIndex, 25).multiple();
```

For a single round trip, use a window function instead:
`select(c.id, c.name, count().over().as("totalRows"))…page(i, 25)`.

## 3. Reporting: revenue per month with a running total

```java
record MonthlyRevenue(OffsetDateTime month, BigDecimal revenue, BigDecimal runningTotal, long orders) {}

Expression month = dateTrunc("month", o.orderedAt);

List<MonthlyRevenue> report = select(MonthlyRevenue.class,
            month.as("month"),
            sum(o.total).as("revenue"),
            sum(sum(o.total)).over(window().orderBy(month)).as("runningTotal"),
            count().as("orders"))
        .from(o)
        .where(eq(o.status, val("PAID")), ge(o.orderedAt, val(OffsetDateTime.now().minusYears(1))))
        .groupBy(month)
        .orderBy(month)
        .multiple();
```

## 4. Top N per group

The three largest orders per customer:

```java
SelectQuery<Object[]> ranked = select(o.customerId, o.id, o.total,
            rowNumber().over(partitionBy(o.customerId).orderBy(o.total.desc())).as("rn"))
        .from(o);

List<Object[]> top3 = select("r.CUSTOMER_ID", "r.ID", "r.TOTAL")
        .from(ranked.as("r"))
        .where(le("r.rn", inline(3)))
        .multiple();
```

For just the latest row per group, `DISTINCT ON` is shorter:

```java
select(o.customerId, o.id, o.orderedAt)
    .distinctOn(o.customerId)
    .from(o)
    .orderBy(o.customerId, o.orderedAt.desc())
    .multiple();
```

## 5. Latest related row with LATERAL

```java
select(c.name, "lo.TOTAL", "lo.ORDERED_AT")
    .from(c)
    .leftJoin(lateral(select(o.total, o.orderedAt).from(o)
            .where(eq(o.customerId, c.id))
            .orderBy(o.orderedAt.desc())
            .limit(1)
            .as("lo")))
    .multiple();
```

## 6. Upsert (insert or update)

```java
insertInto(c)
    .set(c.email, val(dto.email()))
    .set(c.name, val(dto.name()))
    .set(c.country, val(dto.country()))
    .onConflict(c.email)
    .doUpdateSetExcluded(c.name, c.country)
    .execute();
```

Insert only if the row is new, and learn whether it was inserted:

```java
Optional<Long> newId = insertInto(c)
    .set(c.email, val(email)).set(c.name, val(name))
    .onConflict(c.email).doNothing()
    .returning(c.id)
    .optional(Long.class);          // empty if the e-mail already existed
```

## 7. Job queue with SKIP LOCKED

Several workers can take jobs concurrently without blocking each other.
Run this inside a transaction:

```java
Table j = table("JOB", "j");   // or a JobTable class
Column id = j.col("ID"), status = j.col("STATUS"), startedAt = j.col("STARTED_AT"), createdAt = j.col("CREATED_AT");

List<Long> claimed = update(j)
    .set(status, val("RUNNING"))
    .set(startedAt, now())
    .where(in(id, select(id).from(j)
            .where(eq(status, val("QUEUED")))
            .orderBy(createdAt)
            .limit(10)
            .forUpdate().skipLocked()))
    .returning(id)
    .multiple(Long.class);
```

## 8. Working with JSONB

```java
// read nested values
select(c.name,
       jsonGetText(c.profile, "city").as("city"),
       jsonPathText(c.profile, "address", "zip").as("zip"))
    .from(c)
    .where(contains(c.profile, jsonb(val("{\"newsletter\": true}"))))    // index-friendly @>
    .multiple();

// update a single key
update(c)
    .set(c.profile, jsonbSet(c.profile, new String[]{"newsletter"}, jsonb(inline("false")), true))
    .where(eq(c.id, id))
    .execute();

// build JSON in SQL
createContribution(String.class)
    .select(jsonbAgg(jsonbBuildObject("id", o.id, "total", o.total)).orderBy(o.orderedAt))
    .from(o)
    .where(eq(o.customerId, id))
    .single();

// jsonpath
selectFrom(c).where(jsonbPathExists(c.profile, "$.orders[*] ? (@.total > 100)"));
```

## 9. Arrays

```java
// customers with any of the given tags
selectFrom(c).where(overlaps(c.tags, val(new String[]{"vip", "beta"})));

// many ids as one parameter (efficient for long lists)
selectFrom(c).where(eq(c.id, any(val(ids.toArray(Long[]::new)))));

// add a tag, remove duplicates in SQL
update(c).set(c.tags, arrayAppend(c.tags, val("vip")))
    .where(eq(c.id, id), not(contains(c.tags, val(new String[]{"vip"}))))
    .execute();

// aggregate into an array
select(o.customerId, arrayAgg(o.id).orderBy(o.id).as("orderIds")).from(o).groupBy(o.customerId);
```

## 10. Full-text search with ranking

```java
Expression doc = toTsvector("english", concatWs(" ", c.name, jsonGetText(c.profile, "bio")));
Expression q   = websearchToTsquery("english", val(userInput));

select(c.id, c.name, tsRank(doc, q).as("rank"),
       tsHeadline("english", jsonGetText(c.profile, "bio"), q).as("snippet"))
    .from(c)
    .where(tsMatches(doc, q))
    .orderBy(desc(tsRank(doc, q)))
    .limit(20)
    .multiple();
```

## 11. Recursive hierarchy (tree)

```java
Table cat = table("CATEGORY", "cat");
List<Object[]> tree = select("t.ID", "t.NAME", "t.DEPTH")
    .withRecursive("t(ID, NAME, DEPTH)",
        select(cat.col("ID"), cat.col("NAME"), inline(0)).from(cat).where(isNull(cat.col("PARENT_ID")))
            .unionAll(select(cat.col("ID"), cat.col("NAME"), raw("t.DEPTH + 1"))
                .from(cat).join("t", "t.ID = cat.PARENT_ID")))
    .from("t")
    .orderBy("t.DEPTH", "t.NAME")
    .multiple();
```

## 12. Calendar with gaps filled

Every day of a month, including days without orders:

```java
select(raw("d::date").as("day"), coalesce(sum(o.total), inline(0)).as("revenue"))
    .from(generateSeries(val(from), val(to), interval("1 day")).as("d"))
    .leftJoin(o, eq(dateTrunc("day", o.orderedAt), "d"))
    .groupBy("d")
    .orderBy("d")
    .multiple();
```

## 13. Archiving in one statement

```java
createContribution(Long.class)
    .with("moved", deleteFrom(o).where(lt(o.orderedAt, now().minus(interval("2 years")))).returning(o.all()))
    .with("archived", insertInto("ORDERS_ARCHIVE").select(selectFrom("moved")).returning(inline(1)))
    .select(count())
    .from("archived")
    .single();
```

## 14. Conditional aggregation (pivot)

```java
select(c.country,
       count().filter(eq(o.status, val("PAID"))).as("paid"),
       count().filter(eq(o.status, val("OPEN"))).as("open"),
       sum(o.total).filter(eq(o.status, val("PAID"))).as("revenue"))
    .from(c)
    .join(o, eq(o.customerId, c.id))
    .groupBy(c.country)
    .multiple();
```

## 15. Reusable query fragments

Expressions and conditions are plain objects, so you can build them once
and reuse them:

```java
final class CustomerQueries {
    static Condition isActive(CustomerTable c) {
        return and(isNull(c.deletedAt), ne(c.country, val("XX")));
    }

    static Expression displayName(CustomerTable c) {
        return coalesce(nullif(trim(c.name), inline("")), c.email);
    }
}

select(displayName(c).as("name")).from(c).where(isActive(c)).multiple();
```
