# Conditions

A condition is a typed value (`Condition`, a `Field<Boolean>`). You can use it in
`where`, `having`, `join … on`, `caseWhen`, aggregate `filter`, `doUpdateWhere`,
`onConflictWhere`, table policies and repository methods such as `findAll(condition)`.
Conditions can be stored in variables, combined, passed to methods and nested to any
depth.

Every operator exists in two forms that render the same SQL with the same binds:

```java
import static ch.lxrin.ql.dsl.Dsl.*;          // includes ch.lxrin.ql.dsl.Conditions

eq(USERS.EMAIL, email)                          // static function
USERS.EMAIL.eq(email)                           // field method
```

Both forms work in the `createContribution` style and in the `select(..)` style (see
[Queries](queries.md)).

- [Rules](#rules)
- Operators: [comparison](#comparison) · [BETWEEN](#between) · [IN, ANY, ALL, EXISTS](#in-any-all-exists) ·
  [NULL and boolean](#null-and-boolean-tests) · [text](#text) · [arrays](#arrays) · [ranges](#ranges) ·
  [JSON](#json) · [full-text search](#full-text-search) · [row values](#row-values) · [logic](#logic)
- [Dynamic conditions](#dynamic-conditions) · [Dynamic statements](#dynamic-statements)

## Rules

- **Every value is a bind parameter.** `eq(USERS.NAME, name)` renders `app_user.name = ?`.
  In the `createContribution` style, `b.setString(name)` makes the parameter explicit.
  It is optional and renders the same SQL.
- **Types are checked by the compiler.** `eq(USERS.CREATED_AT, "abc")` does not compile.
- **`null` is never dropped silently.** A `null` value throws `IllegalArgumentException`.
  - Use `isNull(..)` for NULL checks.
  - `eqOrIsNull(value)` and `isDistinctFrom(value)` accept `null` on purpose.
  - For optional filters, use `when(..)`, `…IfPresent(Optional)` or a `ConditionBuilder`.
- **An empty `in` is always false; an empty `notIn` is always true.** The values are
  one array parameter (`= ANY(?)`, `<> ALL(?)`), and PostgreSQL evaluates
  `x = ANY('{}')` as false and `x <> ALL('{}')` as true, even when `x` is NULL.
- **`noCondition()` is neutral.** `and(..)` and `or(..)` ignore it, and on its own it
  renders `TRUE`. An `UPDATE` or `DELETE` whose conditions all resolve to
  `noCondition()` is rejected unless all rows are confirmed with `allRows()` / `c.all()`.

## Comparison

| Static | Fluent | SQL |
|---|---|---|
| `eq(a, b)` | `a.eq(b)` | `a = b` |
| `ne(a, b)` | `a.ne(b)` | `a <> b` |
| `gt(a, b)`, `ge(a, b)` | `a.gt(b)`, `a.ge(b)` | `a > b`, `a >= b` |
| `lt(a, b)`, `le(a, b)` | `a.lt(b)`, `a.le(b)` | `a < b`, `a <= b` |
| `isDistinctFrom(a, b)` | `a.isDistinctFrom(b)` | `a IS DISTINCT FROM b` (NULL-safe, `b` may be `null`) |
| `isNotDistinctFrom(a, b)` | `a.isNotDistinctFrom(b)` | `a IS NOT DISTINCT FROM b` |
| – | `a.eqOrIsNull(value)` | `a = ?`, or `a IS NULL` for `null` |

`b` is a value of the field's type or another field of the same type.

## BETWEEN

| Static | Fluent | SQL |
|---|---|---|
| `between(x, a, b)` | `x.between(a, b)` | `x BETWEEN ? AND ?` |
| `notBetween(x, a, b)` | `x.notBetween(a, b)` | `x NOT BETWEEN ? AND ?` |
| `betweenSymmetric(x, a, b)` | `x.betweenSymmetric(a, b)` | `x BETWEEN SYMMETRIC ? AND ?`: the bounds may be in any order |
| `notBetweenSymmetric(x, a, b)` | `x.notBetweenSymmetric(a, b)` | `x NOT BETWEEN SYMMETRIC ? AND ?` |

The bounds are values or fields.

## IN, ANY, ALL, EXISTS

| Static | Fluent | SQL |
|---|---|---|
| `in(x, "a", "b")` | `x.in("a", "b")` | `x = ANY(?)`: one array parameter |
| `in(x, list)` | `x.in(list)` | `x = ANY(?)` |
| `in(x, b.setList(list))` | `x.in(b.setList(list))` | `x = ANY(?)`, with the list made explicit |
| `in(x, subquery)` | `x.in(subquery)` | `x IN (SELECT …)` |
| `notIn(..)` (same forms) | `x.notIn(..)` | `x <> ALL(?)`, `x NOT IN (SELECT …)` |
| `exists(query)`, `notExists(query)` | – | `EXISTS (SELECT …)` |
| `gt(x, any(subquery))` | `x.gt(any(subquery))` | `x > ANY (SELECT …)` |
| `le(x, all(subquery))` | `x.le(all(subquery))` | `x <= ALL (SELECT …)` |
| `eq("vip", any(USERS.TAGS))` | `USERS.NAME.eq(any(USERS.TAGS))` | `? = ANY(app_user.tags)` |

`any(..)` and `all(..)` take a single-column sub-query or an array field, and work
with every comparison: `eq`, `ne`, `gt`, `ge`, `lt` and `le`.

Because `x NOT IN (SELECT …)` is never true if the sub-query returns a NULL, prefer
`notExists(..)` for nullable columns.

## NULL and boolean tests

| Static | Fluent | SQL |
|---|---|---|
| `isNull(x)`, `isNotNull(x)` | `x.isNull()`, `x.isNotNull()` | `x IS NULL`, `x IS NOT NULL` |
| `isTrue(c)`, `isNotTrue(c)` | `c.isTrue()`, `c.isNotTrue()` | `c IS TRUE`, `c IS NOT TRUE` (false or NULL) |
| `isFalse(c)`, `isNotFalse(c)` | `c.isFalse()`, `c.isNotFalse()` | `c IS FALSE`, `c IS NOT FALSE` (true or NULL) |

Boolean columns are conditions: `where(USERS.ACTIVE)`.

## Text

| Static | Fluent | SQL |
|---|---|---|
| `like(x, p)`, `notLike(x, p)` | `x.like(p)`, `x.notLike(p)` | `x LIKE ?`, `x NOT LIKE ?` |
| `ilike(x, p)`, `notIlike(x, p)` | `x.ilike(p)`, `x.notIlike(p)` | `x ILIKE ?`, `x NOT ILIKE ?` |
| `like(x, "100!%", '!')` | `x.like("100!%", '!')` | `x LIKE ? ESCAPE '!'` (also `notLike`, `ilike`, `notIlike`) |
| `startsWith(x, s)` | `x.startsWith(s)` | `starts_with(x, ?)` (static) and `x LIKE 's%'` (fluent, can use a `text_pattern_ops` index) |
| `endsWith(x, s)`, `contains(x, s)` | `x.endsWith(s)`, `x.contains(s)` | `x LIKE '%s'`, `x LIKE '%s%'` |
| `startsWithIgnoreCase`, `endsWithIgnoreCase`, `containsIgnoreCase` | the same | `ILIKE` |
| `similarTo(x, p)`, `notSimilarTo(x, p)` | `x.similarTo(p)`, `x.notSimilarTo(p)` | `x SIMILAR TO ?`, `x NOT SIMILAR TO ?` |
| `matches(x, re)`, `notMatches(x, re)` | `x.matches(re)`, `x.notMatches(re)` | `x ~ ?`, `x !~ ?` |
| `matchesIgnoreCase(x, re)`, `notMatchesIgnoreCase(x, re)` | the same | `x ~* ?`, `x !~* ?` |

`startsWith`, `endsWith` and `contains` escape `%`, `_` and `\` in the value, so
`contains("50%")` finds the text `50%` and nothing else. `like(..)` takes the pattern
as it is. The pattern may also be a field: `x.like(OTHER.PATTERN)`.

## Arrays

| Static | Fluent | SQL |
|---|---|---|
| `arrayContains(a, "x", "y")` | `a.arrayContains("x", "y")` | `a @> ?`: contains all given elements |
| `arrayContainedBy(a, …)` | `a.arrayContainedBy(…)` | `a <@ ?`: all elements are among the given ones |
| `arrayOverlaps(a, …)` | `a.arrayOverlaps(…)` | `a && ?`: at least one common element |
| `arrayContains(a, otherArray)` | `a.contains(otherArray)` | `a @> b` (also `containedBy`, `overlaps`) |
| `eq(value, any(a))` | `a.hasElement(value)` | `? = ANY(a)` |

## Ranges

Generated range columns (`daterange`, `tstzrange`, `int4range`, …) are
`RangeColumn`s. Range expressions such as `tstzrange(from, to)` are `RangeField`s.
Any other range expression can be wrapped with `range(field)`.

| Static | Fluent | SQL |
|---|---|---|
| `rangeContains(r, element)` | `r.rangeContains(element)` | `r @> ?`, for an `Instant`, `LocalDate`, `LocalDateTime`, `Integer`, `Long` or `BigDecimal` |
| `rangeContains(r, other)` | `r.rangeContains(other)` | `r @> other` (a range or element expression) |
| `rangeContainedBy(r, other)` | `r.rangeContainedBy(other)` | `r <@ other` |
| `rangeOverlaps(r, other)` | `r.rangeOverlaps(other)` | `r && other` |
| `strictlyLeftOf(r, other)`, `strictlyRightOf(r, other)` | the same | `r << other`, `r >> other` |
| `notExtendsRightOf(r, other)`, `notExtendsLeftOf(r, other)` | the same | `r &< other`, `r &> other` |
| `adjacentTo(r, other)` | `r.adjacentTo(other)` | `r -\|- other` |
| `isEmpty(r)` | `r.isEmpty()` | `isempty(r)` |

```java
where(BOOKING.PERIOD.rangeOverlaps(tstzrange(param(from), param(to))))
where(rangeContains(BOOKING.PERIOD, Instant.now()))
```

## JSON

| Static | Fluent | SQL |
|---|---|---|
| `jsonContains(j, v)` | `j.jsonContains(v)` (or `j.contains(v)`) | `j @> ?` |
| `jsonContainedBy(j, v)` | `j.jsonContainedBy(v)` | `j <@ ?` |
| `hasKey(j, "k")` | `j.hasKey("k")` | `jsonb_exists(j, 'k')`, the `?` operator |
| `hasAnyKey(j, "a", "b")`, `hasAllKeys(..)` | the same | `?\|`, `?&` |
| `jsonPathExists(j, "$.a ? (@ > 1)")` | `j.jsonPathExists(..)` | `jsonb_path_exists(j, '…'::jsonpath)` |
| `jsonPathMatches(j, "$.a > 1")` | `j.jsonPathMatches(..)` | `jsonb_path_match(j, '…'::jsonpath)` |

The operators `?`, `?|` and `?&` are written as functions, because JDBC would read `?`
as a parameter. Keys and JSON paths are part of the query's shape and are written as
escaped literals.

## Full-text search

| Static | Fluent | SQL |
|---|---|---|
| `tsMatches(v, q)` | `v.tsMatches(q)` | `v @@ q` |

Generated `tsvector` columns are `TsVectorColumn`s. `tsvector(toTsvector("english", X))`
turns an expression into a `TsVectorField`.

```java
where(BOOKING.SEARCH.tsMatches(websearchToTsquery("english", text)))
```

## Row values

`row(a, b, …)` builds a row value constructor. Values are bound with the types of the
row's fields. The number of values and their types are checked when the condition is
built.

```java
row(EVENT.CREATED_AT, EVENT.ID).gt(lastCreatedAt, lastId)            // (created_at, id) > (?, ?)
row(ASSET.OWNER_ID, ASSET.NAME).in(List.of(List.of(ada, "Laptop"),  // (owner_id, name) IN ((?, ?), (?, ?))
                                          List.of(alan, "Phone")))
row(ASSET.ID, ASSET.NAME).in(select(RISK.ASSET_ID, RISK.THREAT).from(RISK))
row(A.X, A.Y).eq(row(B.X, B.Y))
```

- Comparisons: `eq`, `ne`, `gt`, `ge`, `lt`, `le` (values or another row);
  `isDistinctFrom` and `isNotDistinctFrom` (another row).
- Membership: `in` and `notIn` with a list of value rows, row expressions or a
  sub-query. An empty list renders `FALSE` (`notIn`: `TRUE`).

## Logic

| Static | Fluent | SQL |
|---|---|---|
| `and(a, b, …)`, `and(list)` | `a.and(b)` | `(a AND b …)` |
| `or(a, b, …)`, `or(list)` | `a.or(b)` | `(a OR b …)` |
| `not(a)` | `a.not()` | `NOT (a)` |
| `noCondition()` | – | `TRUE`, neutral in `and` and `or` |

Conditions can be nested to any depth. Nested `AND`s (and `OR`s) are flattened, and
parentheses are added only where needed. For compatibility with 3.0, `and(..)` and
`or(..)` skip `null` entries. `where(..)`, `where(list)` and `ConditionBuilder`
reject them.

## Dynamic conditions

```java
// only the filters that are set end up in the SQL
Condition filter = and(
        USERS.DELETED_AT.isNull(),
        when(query != null, () -> USERS.NAME.containsIgnoreCase(query)),   // the supplier runs only if true
        USERS.ROLE.eqIfPresent(role),                                      // Optional<AppRole>
        ifPresent(createdAfter, USERS.CREATED_AT::ge));                    // Optional<Instant>

// or step by step
Condition filter = Conditions.builder(USERS.DELETED_AT.isNull())
        .addIf(query != null, () -> USERS.NAME.containsIgnoreCase(query))
        .addIfPresent(role, USERS.ROLE::eq)
        .addAll(extraConditions)
        .build();                                 // noCondition() if nothing was added
Conditions.orBuilder(..)                          // joins with OR
```

- `…IfPresent(Optional)` exists for `eq`, `ne`, `gt`, `ge`, `lt`, `le`, `in`, `like`,
  `ilike`, `startsWith` and `containsIgnoreCase`, as field methods and (for the
  comparisons and `in`) as static functions.
- An empty optional gives `noCondition()`. A present empty collection in
  `inIfPresent` still matches nothing.

## Dynamic statements

| Feature | Example |
|---|---|
| Dynamic select list | `select(List<Field<?>>)`, `c.select(fields)` |
| Lists of conditions | `.where(List<Condition>)`, `.having(List<Condition>)` |
| Dynamic sorting | `.orderBy(List<SortField<?>>)` |
| Sort parameter from a request | `.orderBy(Sorts.from("username,desc", Map.of("username", USERS.NAME)))` |
| Optional joins | `.joinIf(flag, ASSET, () -> ASSET.OWNER_ID.eq(USERS.ID))`, `.leftJoinIf(flag, ASSET, ASSET.FK_OWNER_ID)` |
| PATCH updates | `.setIf(flag, USERS.NAME, name)`, `.setIfPresent(USERS.EMAIL, Optional<String>)` |
| All rows on purpose | `.allRows()` / `c.all()` |

`Sorts.from(..)` accepts:

- `key`, `key,asc` or `key,desc`;
- several items separated by `;`, or given as a collection (e.g. the repeated `sort`
  parameters of Spring).

Keys must be in the whitelist map. Anything else throws `InvalidSortException`: an
unknown key, an unknown direction or an extra part. Map that exception to HTTP 400.
Nothing from the parameter is written into the SQL. A `null` or blank parameter
returns an empty list, or the defaults given with `Sorts.from(param, whitelist, defaults…)`.

```java
Optional<String> name = patch.name();
Optional<String> email = patch.email();
createUpdate(USERS, (c, b) -> c
        .setIfPresent(USERS.NAME, name)
        .setIfPresent(USERS.EMAIL, email)
        .where(USERS.ID.eq(id)))
    .execute();
```

An `UPDATE` in which no column is set is rejected. Check `hasAssignments()` first if a
PATCH may be empty.
