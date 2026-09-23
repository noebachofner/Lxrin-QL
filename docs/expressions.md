# Expressions & conditions

All examples assume `import static ch.lxrin.ql.LxrinQL.*;`.

## The operand rule

Almost every DSL method accepts `Object` operands. How an operand is rendered
depends only on its Java type:

| Operand | Rendered as | Example | SQL |
|---|---|---|---|
| `Expression` (column, function, sub-query, `val`, `inline`, …) | the expression | `p.lastName` | `p.LAST_NAME` |
| `String` | **SQL fragment, verbatim** | `"t.NAME"`, `":status"`, `"now()"` | `t.NAME` |
| `null` | `NULL` | | |
| anything else | bind parameter | `42`, `LocalDate.now()`, `UUID`, `new Long[]{…}` | `:lq0` |

So `eq(p.age, 18)` binds 18 automatically, but `eq(p.lastName, "Smith")`
renders `p.LAST_NAME = Smith`, which is SQL and not a value. Use
`val("Smith")` for text values:

```java
eq(p.lastName, val(name))        // p.LAST_NAME = :lq0   ✔
eq(p.lastName, b.setString(name))// p.LAST_NAME = :p0    ✔ (Binds)
eq(p.lastName, inline("Smith"))  // p.LAST_NAME = 'Smith' ✔ (escaped literal)
eq(p.lastName, "p.FIRST_NAME")   // column comparison    ✔
eq(p.lastName, name)             // ✘ user input becomes SQL
```

The function catalog has one addition to this rule. Parameters that are
**declared** as `String` or a primitive (formats, date fields, separators,
regex patterns, JSON keys, …) are constants and are written as escaped
literals: `toChar(o.createdAt, "YYYY-MM-DD")` → `to_char(o.CREATED_AT, 'YYYY-MM-DD')`.

## Values: `val`, `inline`, `raw`, `sql`, `ident`

| Method | Purpose | Example → SQL |
|---|---|---|
| `val(x)` | bind parameter, name generated | `val("Ada")` → `:lq0` |
| `inline(x)` | escaped literal in the SQL text | `inline("O'Brien")` → `'O''Brien'`, `inline(LocalDate.of(2024,1,31))` → `DATE '2024-01-31'` |
| `raw(sql)` | SQL fragment as an `Expression` (to call `.as`, `.eq`, …) | `raw("now()").as("ts")` |
| `sql(template, args…)` | template with `{0}`, `{1}` placeholders | `sql("{0} <-> {1}", p.location, val(point))` |
| `ident(parts…)` | quoted identifier | `ident("order", "desc")` → `"order"."desc"` |
| `asterisk()` | `*` | |
| `defaultValue()` | `DEFAULT` in `INSERT`/`UPDATE` | |

`inline` supports `null`, numbers, booleans, `LocalDate`, `LocalDateTime`,
`LocalTime`, `OffsetDateTime`, `ZonedDateTime`, `UUID`, enums (by name) and
text.

## Conditions

Each condition is available as a static method (`eq(a, b)`). The most common
ones are also fluent methods on every expression (`a.eq(b)`).

### Comparison

| Static | Fluent | SQL |
|---|---|---|
| `eq(a, b)` | `a.eq(b)` | `a = b` |
| `ne(a, b)` | `a.ne(b)` | `a <> b` |
| `gt / ge / lt / le` | `a.gt(b)` … | `>` `>=` `<` `<=` |
| `isDistinctFrom(a, b)` | `a.isDistinctFrom(b)` | `a IS DISTINCT FROM b` (null-safe `<>`) |
| `isNotDistinctFrom(a, b)` | `a.isNotDistinctFrom(b)` | null-safe `=` |
| `compare(a, "op", b)` | | any operator, e.g. `<->` |

A Java `null` as the right-hand operand is rejected with a clear message,
because `x = NULL` is never true. Use `isNull(x)` instead, or `val(null)`
if you really want a null parameter.

### NULL and boolean tests

`isNull(a)`, `isNotNull(a)`, `isTrue(a)`, `isNotTrue(a)`, `isFalse(a)`,
`isNotFalse(a)`. The fluent forms are `a.isNull()` and `a.isNotNull()`.

### Ranges and lists

```java
between(p.age, 18, 65)              // p.AGE BETWEEN :lq0 AND :lq1
notBetween(p.age, 18, 65)
betweenSymmetric(p.age, 65, 18)     // bounds in any order
in(p.status, val("A"), val("B"))    // p.STATUS IN (:lq0, :lq1)
in(p.personNr, idList)              // one parameter per element (Collection or array)
in(p.personNr, subQuery)            // IN (SELECT ...)
notIn(...)
```

An empty list renders `FALSE` for `in` and `TRUE` for `notIn`, so the
statement stays valid. For very large lists, bind one array instead:
`eq(p.personNr, any(val(ids.toArray(Long[]::new))))`.

### Pattern matching

| Method | SQL |
|---|---|
| `like(a, pattern)` / `notLike` | `LIKE` / `NOT LIKE` |
| `ilike(a, pattern)` / `notIlike` | case-insensitive `LIKE` |
| `similarTo(a, pattern)` / `notSimilarTo` | `SIMILAR TO` |
| `matches(a, regex)` / `notMatches` | `~` / `!~` (POSIX regex) |
| `matchesIgnoreCase(a, regex)` / `notMatchesIgnoreCase` | `~*` / `!~*` |
| `startsWith(a, prefix)` | `starts_with(a, prefix)` |

### Sub-queries and quantifiers

```java
exists(select(inline(1)).from(o).where(eq(o.personNr, p.personNr)))
notExists(...)
eq(p.personNr, any(val(new Long[]{1L, 2L})))     // = ANY(array)
gt(o.total, all(select(o.total).from(o)))         // > ALL(SELECT ...)
```

### Arrays, JSONB, ranges and full-text search

| Method | SQL | Works on |
|---|---|---|
| `contains(a, b)` | `a @> b` | arrays, jsonb, ranges |
| `containedBy(a, b)` | `a <@ b` | arrays, jsonb, ranges |
| `overlaps(a, b)` | `a && b` | arrays, ranges |
| `jsonHasKey(json, key)` | `jsonb_exists(json, key)` (the `?` operator) | jsonb |
| `jsonHasAnyKey(json, keysArray)` | `jsonb_exists_any(..)` (`?\|`) | jsonb |
| `jsonHasAllKeys(json, keysArray)` | `jsonb_exists_all(..)` (`?&`) | jsonb |
| `jsonbPathExists(json, "$.path")` | `jsonb_path_exists(..)` | jsonb |
| `jsonbPathMatch(json, "predicate")` | `jsonb_path_match(..)` | jsonb |
| `tsMatches(vector, query)` | `vector @@ query` | full-text search |
| `isEmpty(range)` | `isempty(range)` | ranges |

The JSON key tests are rendered as functions because a literal `?` clashes
with JDBC parameter markers. Note that the function form cannot use a GIN
index. When you need the index, use `contains(json, jsonb(val("{\"key\": …}")))`,
which renders the index-friendly `@>` operator.

### Combining conditions

```java
and(c1, c2, c3)                 // (c1 AND c2 AND c3), null entries skipped, empty → TRUE
or(c1, c2)                      // (c1 OR c2), null entries skipped, empty → FALSE
not(c)                          // NOT (c)
group(c1, or(), c2)             // (c1 OR c2) – list style with explicit tokens
c1.and(c2).or(c3)               // fluent: ((c1 AND c2) OR c3)
c.not()
condition("t.ACTIVE")           // SQL fragment as a condition
condition(function("my_check", p.id))   // boolean function as a condition
booleanFunction("my_check", p.id)       // shortcut for the line above
trueCondition(), falseCondition()
```

Custom conditions can be written as lambdas:

```java
Condition nearby = ctx -> ctx.append("ST_DWithin(").visit(s.location).append(", ")
                            .visit(val(point)).append(", ").visit(inline(500)).append(")");
```

## Arithmetic and concatenation

```java
o.total.plus(10)        // (o.TOTAL + :lq0)
o.total.minus(o.discount)
o.price.times(o.quantity)
o.total.divide(inline(100))
p.age.mod(2)
p.firstName.concat(inline(" ")).concat(p.lastName)   // (… || …)
operator(a, "#", b)      // any binary operator
prefix("-", a)           // unary operator
parens(a)                // explicit parentheses
```

Binary operators are always wrapped in parentheses, so nesting never changes
precedence.

## Aliases, casts and sorting

```java
upper(p.lastName).as("name")           // upper(p.LAST_NAME) AS name
as("p.A + p.B", "total")               // alias for a SQL fragment or sub-query
p.age.cast("text")                     // CAST(p.AGE AS text)
cast(val(json), "jsonb")               // CAST(:lq0 AS jsonb)
jsonb(val(json))                       // shortcut for the line above
p.lastName.asc()   p.age.desc().nullsLast()
asc(expr)   desc(expr).nullsFirst()
```

`cast` validates the type name, so it cannot be abused for SQL injection.

## CASE

```java
// searched CASE
caseWhen(lt(p.age, 18), inline("minor"))
    .when(lt(p.age, 65), inline("adult"))
    .otherwise(inline("senior"))
    .as("ageGroup")

// simple CASE
caseOf(o.status)
    .when(inline("N"), inline("new"))
    .when(inline("P"), inline("paid"))
    .otherwise(o.status)
```

## Window specifications

```java
partitionBy(o.personNr).orderBy(o.createdAt.desc())     // (PARTITION BY ... ORDER BY ...)
window().orderBy(o.createdAt).rowsBetween(unboundedPreceding(), currentRow())
window().orderBy(o.createdAt).rangeBetween(preceding(7), currentRow())
window().orderBy(o.createdAt).groupsBetween(preceding(1), following(1))
WindowSpec.basedOn("w").orderBy(o.total)                   // (w ORDER BY ...)
```

Pass the specification to `.over(..)` on any aggregate or window function.
`.over()` gives an empty window and `.over("name")` references a named window
(see [Queries › Window functions](queries.md#window-functions)).

## Custom functions and extensions

Anything missing from the catalog can be called like this:

```java
function("similarity", p.lastName, val(text))          // pg_trgm
function("ST_Distance", s.location, val(point))         // PostGIS
function("my_schema.calc_price", o.id).as("price")
function("my_agg", o.total).filter(...).over(...)       // the result supports all modifiers
sql("{0} <-> {1}", p.lastName, val(text))               // operators with special syntax
```
