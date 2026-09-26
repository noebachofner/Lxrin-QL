# Fields and types

All examples assume `import static ch.lxrin.ql.dsl.Dsl.*;` and the generated tables.

## The rule: a Java value is always a bind parameter

Every DSL method that takes a Java value sends it as a typed bind parameter
(`?`). There are no `Object` operands and no string fragments.

```java
USERS.NAME.eq("x' OR '1'='1")        // app_user.name = ?    (the string is data)
USERS.CREATED_AT.gt(Instant.now())   // app_user.created_at > ?
USERS.CREATED_AT.eq("abc")           // does not compile
USERS.EMAIL.plus(1)                  // does not compile: arithmetic only on numbers
USERS.NAME.eq(USERS.VERSION)         // does not compile: String vs Long
```

The only way to write SQL text is [`Sql`](#raw-sql).

## Fields

`Field<T>` is any typed expression: a column, a function call, a parameter or a
sub-query. Families of types add their own operations:

| Interface | Types | Extra operations |
|---|---|---|
| `Field<T>` | all | comparisons, `isNull`, `in`, `between`, `asc`/`desc`, `as`, `cast`, `coalesce`, `nullIf` |
| `StringField` | text | `like`, `ilike`, `startsWith`, `endsWith`, `contains` (+ `…IgnoreCase`), `matches` (regex), `similarTo`, `concat`, `lower`, `upper`, `trim`, `length` |
| `NumberField<N>` | numbers | `plus`, `minus`, `times`, `divide`, `mod`, `neg`, `abs` |
| `TemporalField<T>` | date/time | `plus(Duration)`, `minus(Duration)`, `truncate(DatePart[, ZoneId])`, `extract(DatePart)` |
| `JsonField<T>` | json, jsonb | `get(key)`, `getText(key)`, `path(..)`, `pathText(..)`, `contains`, `containsJson`, `hasKey`, `hasAnyKey`, `hasAllKeys`, `pathExists`, `pathMatch`, `deleteKey`, `concat` |
| `ArrayField<E>` | arrays | `contains`, `containedBy`, `overlaps`, `hasElement`, `length`, `element(i)`, `append`, `remove` |
| `Condition` | boolean | `and`, `or`, `not`, `isTrue`, `isFalse`, … |

Generated columns implement the matching interface (`USERS.EMAIL` is a
`StringColumn`), and so do the results of functions (`lower(..)` is a
`StringField`, `count()` a `NumberAggregate<Long>`).

`startsWith`, `endsWith` and `contains` escape `%`, `_` and `\` in the text.
`like(..)` takes a pattern as is.

`TemporalField.plus(Duration)` keeps the column's type: `order_date + 7 days` stays a
`date`. `truncate(DatePart.DAY)` of a `timestamptz` uses the session time zone; pass
a `ZoneId` to make it explicit.

## Conditions

| Method | SQL |
|---|---|
| `eq`, `ne`, `gt`, `ge`, `lt`, `le` (value or field) | `=`, `<>`, `>`, `>=`, `<`, `<=` |
| `isNull()`, `isNotNull()` | `IS NULL`, `IS NOT NULL` |
| `eqOrIsNull(value)` | `= ?`, or `IS NULL` for `null` |
| `isDistinctFrom`, `isNotDistinctFrom` | null-safe comparisons |
| `in(Collection)`, `notIn(Collection)` | `= ANY(?)`, `<> ALL(?)` with **one** array parameter |
| `in(subquery)`, `notIn(subquery)` | `IN (SELECT …)` |
| `eqAny(arrayField)` | `= ANY(array)` |
| `between(a, b)`, `notBetween(a, b)` | `BETWEEN ? AND ?` |
| `exists(select)`, `notExists(select)` | `EXISTS (…)` |
| `a.and(b)`, `a.or(b)`, `a.not()` | `(a AND b)`, `(a OR b)`, `NOT (a)` |
| `Condition.and(list)`, `Condition.or(list)` | `null` and `noCondition()` are skipped |
| `Condition.noCondition()` | neutral: `TRUE` on its own, ignored by `and`/`or` |
| `a.andIf(flag, () -> b)` | adds `b` only if `flag` is set |

`eq(null)` does not compile (it is ambiguous), and `eq((String) null)` throws. Use
`isNull()`. `x = NULL` is never true in SQL.

Boolean columns are conditions: `where(USERS.ACTIVE)`.

## Data types

Every field has a `DataType<T>`: the SQL type, the Java type, and how values are
bound and read. Result values are read with the declared type, so there is no
reflection and no guessing from JDBC metadata.

| `SqlTypes` | SQL | Java |
|---|---|---|
| `TEXT`, `VARCHAR`, `CHAR`, `CITEXT` | text | `String` |
| `INT2`, `INT4`, `INT8`, `NUMERIC`, `FLOAT4`, `FLOAT8` | numbers | `Short`, `Integer`, `Long`, `BigDecimal`, `Float`, `Double` |
| `BOOL` | `boolean` | `Boolean` |
| `UUID` | `uuid` | `UUID` |
| `DATE`, `TIME`, `TIMESTAMP` | `date`, `time`, `timestamp` | `LocalDate`, `LocalTime`, `LocalDateTime` |
| `TIMESTAMPTZ` / `TIMESTAMPTZ_OFFSET` | `timestamptz` | `Instant` / `OffsetDateTime` (UTC) |
| `INTERVAL` / `INTERVAL_TEXT` | `interval` | `Duration` / text |
| `JSONB`, `JSON` | `jsonb`, `json` | JSON text |
| `jsonb(Class)`, `json(Class)` | `jsonb`, `json` | any class, via the context's `JsonCodec` |
| `BYTEA` | `bytea` | `byte[]` |
| `pgEnum(name, Enum.class[, label])` | enum type | a Java enum |
| `pgEnumByName(name, Enum.class, labels…)` | enum type | an existing Java enum (labels matched ignoring case) |
| `enumAsText(Enum.class)` | text | a Java enum stored by name |
| `DATERANGE`, …, `TSVECTOR`, `otherAsText(name)` | other types | text form |
| `T.array()` | `T[]` | Java array |

**Time zones.** No conversion uses the JVM's default time zone. A `timestamptz` is
an `Instant`, sent with offset UTC. A `timestamp` is a `LocalDateTime`.

### Value objects and converters

```java
public record UserId(UUID value) {}
public static final DataType<UserId> USER_ID = SqlTypes.UUID.map(UserId.class, UserId::new, UserId::value);

public static final DataType<Money> MONEY = SqlTypes.NUMERIC.convert(new Converter<Money, BigDecimal>() {
    public Class<Money> javaType() { return Money.class; }
    public Money fromDatabase(BigDecimal v) { return Money.of(v); }
    public BigDecimal toDatabase(Money v) { return v.amount(); }
});
```

Mapped types work everywhere: as column types (through
[forced types](code-generation.md#forced-types-value-objects-json-records)), as bind
parameters, in results, in entities and in keyset cursors.

`type.asSensitive()` marks a type whose values are shown as `***` in logs and error
messages (passwords, tokens, personal data).

## Parameters and literals

```java
param("text")                       // ? with type text; overloads for Integer, Long, BigDecimal, Instant, …
param(value, SqlTypes.UUID)         // any type
value(42L)                          // type derived from the class
inline("constant")                  // an escaped literal in the SQL text: 'constant'
inline(42)                          // 42
inline(Role.ADMIN, ROLE_TYPE)       // 'ADMIN'::role
nullValue(SqlTypes.TEXT)            // CAST(NULL AS text)
```

## CASE and casts

```java
caseWhen(USERS.ROLE.eq(Role.ADMIN), param("admin"))
    .when(USERS.ACTIVE, "active")
    .otherwise("inactive")

caseOf(ORDERS.STATUS).when("N", inline("new")).when("P", "paid").otherwise(ORDERS.STATUS)

USERS.VERSION.cast(SqlTypes.TEXT)     // CAST(app_user.version AS text), a StringField
```

## Raw SQL

`ch.lxrin.ql.dsl.Sql` is the only place where SQL text enters a statement. Arguments
of its templates (`{0}`, `{1}`, …) are fields and parameters, never strings, so user
input cannot become SQL even here:

```java
Field<Double> score = Sql.raw("similarity({0}, {1})", SqlTypes.FLOAT8, USERS.NAME, param(text));
Condition fts = Sql.condition("{0} @@ websearch_to_tsquery('simple', {1})", DOCS.TSV, param(q));
AdHocTable legacy = Sql.table("legacy_import");                // a table that is not generated
Column<String> code = legacy.field("code", SqlTypes.TEXT);
ctx.execute(Sql.statement("REFRESH MATERIALIZED VIEW CONCURRENTLY report"));
```

Prefer a typed definition with [`Routines`](extension-points.md#custom-functions-and-operators)
for functions and operators you use more than once. `LxrinArchRules.noRawSql()`
from `lxrin-ql-test` finds every use of `Sql` and can forbid it outside chosen packages.
