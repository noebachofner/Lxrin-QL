# Function reference

All functions are static methods of `ch.lxrin.ql.dsl.Dsl` (`import static ch.lxrin.ql.dsl.Dsl.*;`).
This page is generated from `Functions.java`.

- Arguments typed `Field<...>` are expressions: columns, other functions, or values wrapped with `param(..)`.
- `String` arguments that belong to the shape of the query (date parts, formats, separators, regex patterns,
  JSON keys and paths, text-search configurations) are written as **escaped literals**, so the same expression
  can appear in the select list and in `GROUP BY`. Search texts and other user input are bound.
- Return types keep the type family: `lower(..)` is a `StringField`, `count()` a `NumberAggregate<Long>`.
- Anything missing can be defined with [`Routines`](extension-points.md#custom-functions-and-operators).

## Contents

- [Aggregates](#aggregates)
- [Window functions](#window-functions)
- [Conditional expressions](#conditional-expressions)
- [Strings](#strings)
- [Mathematics](#mathematics)
- [Date and time](#date-and-time)
- [Ranges](#ranges)
- [JSON](#json)
- [Arrays](#arrays)
- [Full-text search](#full-text-search)
- [Sequences, system information](#sequences-system-information)
- [GROUP BY elements](#group-by-elements)

## Aggregates

| Method | Returns | Description |
|---|---|---|
| `count()` | `NumberAggregate<Long>` | `count(*)` |
| `count(Field<?> field)` | `NumberAggregate<Long>` | `count(field)` – counts non-null values. |
| `countDistinct(Field<?> field)` | `NumberAggregate<Long>` | `count(DISTINCT field)` |
| `sum(Field<? extends Number> field)` | `NumberAggregate<BigDecimal>` | `sum(field)`; read as `BigDecimal`, so it cannot overflow. |
| `avg(Field<? extends Number> field)` | `NumberAggregate<BigDecimal>` | `avg(field)` |
| `min(Field<T> field)` | `AggregateFunction<T>` | `min(field)`; the result keeps the field's type family. |
| `min(NumberField<N> field)` | `NumberAggregate<N>` | `min(field)` for numbers. |
| `min(StringField field)` | `StringAggregate` | `min(field)` for text. |
| `max(Field<T> field)` | `AggregateFunction<T>` | `max(field)`; the result keeps the field's type family. |
| `max(NumberField<N> field)` | `NumberAggregate<N>` | `max(field)` for numbers. |
| `max(StringField field)` | `StringAggregate` | `max(field)` for text. |
| `stringAgg(Field<String> field, String separator)` | `StringAggregate` | `string_agg(field, 'separator')` – add `.orderBy(..)` for a defined order. |
| `arrayAgg(Field<T> field)` | `ArrayAggregate<T>` | `array_agg(field)` |
| `jsonAgg(Field<?> field)` | `JsonAggregate<String>` | `json_agg(field)` |
| `jsonbAgg(Field<?> field)` | `JsonAggregate<String>` | `jsonb_agg(field)` |
| `jsonObjectAgg(Field<String> key, Field<?> value)` | `JsonAggregate<String>` | `json_object_agg(key, value)` |
| `jsonbObjectAgg(Field<String> key, Field<?> value)` | `JsonAggregate<String>` | `jsonb_object_agg(key, value)` |
| `boolAnd(Field<Boolean> field)` | `BooleanAggregate` | `bool_and(field)` – true if all values are true. |
| `boolOr(Field<Boolean> field)` | `BooleanAggregate` | `bool_or(field)` – true if any value is true. |
| `every(Field<Boolean> field)` | `BooleanAggregate` | `every(field)` – SQL-standard `bool_and`. |
| `bitAnd(NumberField<N> field)` | `NumberAggregate<N>` | `bit_and(field)` |
| `bitOr(NumberField<N> field)` | `NumberAggregate<N>` | `bit_or(field)` |
| `anyValue(Field<T> field)` | `AggregateFunction<T>` | `any_value(field)` – an arbitrary value of the group (PostgreSQL 16+). |
| `stddev(Field<? extends Number> field)` | `NumberAggregate<BigDecimal>` | `stddev(field)` |
| `stddevPop(Field<? extends Number> field)` | `NumberAggregate<BigDecimal>` | `stddev_pop(field)` |
| `stddevSamp(Field<? extends Number> field)` | `NumberAggregate<BigDecimal>` | `stddev_samp(field)` |
| `variance(Field<? extends Number> field)` | `NumberAggregate<BigDecimal>` | `variance(field)` |
| `varPop(Field<? extends Number> field)` | `NumberAggregate<BigDecimal>` | `var_pop(field)` |
| `varSamp(Field<? extends Number> field)` | `NumberAggregate<BigDecimal>` | `var_samp(field)` |
| `corr(Field<? extends Number> y, Field<? extends Number> x)` | `NumberAggregate<Double>` | `corr(y, x)` – correlation coefficient. |
| `covarPop(Field<? extends Number> y, Field<? extends Number> x)` | `NumberAggregate<Double>` | `covar_pop(y, x)` |
| `covarSamp(Field<? extends Number> y, Field<? extends Number> x)` | `NumberAggregate<Double>` | `covar_samp(y, x)` |
| `regrSlope(Field<? extends Number> y, Field<? extends Number> x)` | `NumberAggregate<Double>` | `regr_slope(y, x)` |
| `regrIntercept(Field<? extends Number> y, Field<? extends Number> x)` | `NumberAggregate<Double>` | `regr_intercept(y, x)` |
| `percentileCont(double fraction, SortField<? extends Number> sort)` | `NumberAggregate<Double>` | `percentile_cont(fraction) WITHIN GROUP (ORDER BY sort)`, e.g. the median with 0.5. |
| `percentileDisc(double fraction, SortField<T> sort)` | `AggregateFunction<T>` | `percentile_disc(fraction) WITHIN GROUP (ORDER BY sort)` – an actual value of the group. |
| `mode(SortField<T> sort)` | `AggregateFunction<T>` | `mode() WITHIN GROUP (ORDER BY sort)` – the most frequent value. |

## Window functions

| Method | Returns | Description |
|---|---|---|
| `rowNumber()` | `WindowFunction<NumberField<Long>>` | `row_number()` – use with `over(..)`. |
| `rank()` | `WindowFunction<NumberField<Long>>` | `rank()` |
| `denseRank()` | `WindowFunction<NumberField<Long>>` | `dense_rank()` |
| `percentRank()` | `WindowFunction<NumberField<Double>>` | `percent_rank()` |
| `cumeDist()` | `WindowFunction<NumberField<Double>>` | `cume_dist()` |
| `ntile(int buckets)` | `WindowFunction<NumberField<Integer>>` | `ntile(buckets)` |
| `lag(Field<T> field)` | `WindowFunction<Field<T>>` | `lag(field)` – the value of the previous row. |
| `lag(Field<T> field, int offset)` | `WindowFunction<Field<T>>` | `lag(field, offset)` |
| `lag(Field<T> field, int offset, T defaultValue)` | `WindowFunction<Field<T>>` | `lag(field, offset, default)` |
| `lead(Field<T> field)` | `WindowFunction<Field<T>>` | `lead(field)` – the value of the next row. |
| `lead(Field<T> field, int offset)` | `WindowFunction<Field<T>>` | `lead(field, offset)` |
| `lead(Field<T> field, int offset, T defaultValue)` | `WindowFunction<Field<T>>` | `lead(field, offset, default)` |
| `firstValue(Field<T> field)` | `WindowFunction<Field<T>>` | `first_value(field)` |
| `lastValue(Field<T> field)` | `WindowFunction<Field<T>>` | `last_value(field)` – usually needs a frame up to `UNBOUNDED FOLLOWING`. |
| `nthValue(Field<T> field, int n)` | `WindowFunction<Field<T>>` | `nth_value(field, n)` |

## Conditional expressions

| Method | Returns | Description |
|---|---|---|
| `coalesce(Field<T> first, Field<T>... more)` | `Field<T>` | `COALESCE(a, b, ...)` |
| `greatest(Field<T> first, Field<T>... more)` | `Field<T>` | `GREATEST(a, b, ...)` |
| `least(Field<T> first, Field<T>... more)` | `Field<T>` | `LEAST(a, b, ...)` |
| `nullif(Field<T> a, Field<T> b)` | `Field<T>` | `NULLIF(a, b)` |
| `caseWhen(Condition condition, Field<T> result)` | `CaseWhen<T>` | Starts a searched `CASE WHEN condition THEN result ...`. |
| `caseOf(Field<S> subject)` | `CaseOf<S>` | Starts a simple `CASE subject WHEN value THEN result ...`. |
| `exists(AbstractSelect<?, ?> query)` | `Condition` | `EXISTS (SELECT ...)` |
| `notExists(AbstractSelect<?, ?> query)` | `Condition` | `NOT EXISTS (SELECT ...)` |
| `not(Condition condition)` | `Condition` | `NOT (condition)` |
| `and(Condition... conditions)` | `Condition` | `(c1 AND c2 ...)`; `null` entries are skipped. |
| `or(Condition... conditions)` | `Condition` | `(c1 OR c2 ...)`; `null` entries are skipped. |
| `noCondition()` | `Condition` | `TRUE`, neutral in `and(..)`. |

## Strings

| Method | Returns | Description |
|---|---|---|
| `charLength(Field<String> text)` | `NumberField<Integer>` | `char_length(text)` |
| `length(Field<String> text)` | `NumberField<Integer>` | `length(text)` |
| `octetLength(Field<String> text)` | `NumberField<Integer>` | `octet_length(text)` – size in bytes. |
| `bitLength(Field<String> text)` | `NumberField<Integer>` | `bit_length(text)` |
| `lower(Field<String> text)` | `StringField` | `lower(text)` |
| `upper(Field<String> text)` | `StringField` | `upper(text)` |
| `initcap(Field<String> text)` | `StringField` | `initcap(text)` – capitalises each word. |
| `concat(Field<?>... values)` | `StringField` | `concat(a, b, ...)` – `NULL` arguments are ignored. |
| `concatWs(String separator, Field<?>... values)` | `StringField` | `concat_ws('separator', a, b, ...)` |
| `substring(Field<String> text, int from)` | `StringField` | `substring(text, from)` – 1-based. |
| `substring(Field<String> text, int from, int count)` | `StringField` | `substring(text, from, count)` – 1-based. |
| `substringRegex(Field<String> text, String regex)` | `StringField` | `substring(text FROM 'regex')` – the first match of a POSIX regular expression. |
| `left(Field<String> text, int n)` | `StringField` | `left(text, n)` |
| `right(Field<String> text, int n)` | `StringField` | `right(text, n)` |
| `trim(Field<String> text)` | `StringField` | `btrim(text)` |
| `btrim(Field<String> text, String characters)` | `StringField` | `btrim(text, 'characters')` |
| `ltrim(Field<String> text)` | `StringField` | `ltrim(text)` |
| `ltrim(Field<String> text, String characters)` | `StringField` | `ltrim(text, 'characters')` |
| `rtrim(Field<String> text)` | `StringField` | `rtrim(text)` |
| `rtrim(Field<String> text, String characters)` | `StringField` | `rtrim(text, 'characters')` |
| `lpad(Field<String> text, int length, String fill)` | `StringField` | `lpad(text, length, 'fill')` |
| `rpad(Field<String> text, int length, String fill)` | `StringField` | `rpad(text, length, 'fill')` |
| `replace(Field<String> text, String from, String to)` | `StringField` | `replace(text, 'from', 'to')` |
| `replace(Field<String> text, Field<String> from, Field<String> to)` | `StringField` | `replace(text, from, to)` with expressions. |
| `translate(Field<String> text, String from, String to)` | `StringField` | `translate(text, 'from', 'to')` – character-wise replacement. |
| `strpos(Field<String> text, String substring)` | `NumberField<Integer>` | `strpos(text, ?)` – 1-based position, 0 if not found. |
| `startsWith(Field<String> text, String prefix)` | `Condition` | `starts_with(text, ?)` |
| `reverse(Field<String> text)` | `StringField` | `reverse(text)` |
| `repeat(Field<String> text, int n)` | `StringField` | `repeat(text, n)` |
| `splitPart(Field<String> text, String delimiter, int n)` | `StringField` | `split_part(text, 'delimiter', n)` |
| `stringToArray(Field<String> text, String delimiter)` | `ArrayField<String>` | `string_to_array(text, 'delimiter')` |
| `format(String format, Field<?>... args)` | `StringField` | `format('format', args...)` – supports `%s`, `%I`, `%L`. |
| `regexpReplace(Field<String> text, String pattern, String replacement)` | `StringField` | `regexp_replace(text, 'pattern', 'replacement')` |
| `regexpReplace(Field<String> text, String pattern, String replacement, String flags)` | `StringField` | `regexp_replace(text, 'pattern', 'replacement', 'flags')`, e.g. flags `"gi"`. |
| `regexpMatch(Field<String> text, String pattern)` | `ArrayField<String>` | `regexp_match(text, 'pattern')` – the groups of the first match. |
| `regexpMatches(Field<String> text, String pattern, String flags)` | `ArrayField<String>` | `regexp_matches(text, 'pattern', 'flags')` – set-returning. |
| `regexpSplitToArray(Field<String> text, String pattern)` | `ArrayField<String>` | `regexp_split_to_array(text, 'pattern')` |
| `regexpSplitToTable(Field<String> text, String pattern)` | `StringField` | `regexp_split_to_table(text, 'pattern')` – set-returning. |
| `regexpCount(Field<String> text, String pattern)` | `NumberField<Integer>` | `regexp_count(text, 'pattern')` (PostgreSQL 15+) |
| `regexpSubstr(Field<String> text, String pattern)` | `StringField` | `regexp_substr(text, 'pattern')` (PostgreSQL 15+) |
| `md5(Field<String> text)` | `StringField` | `md5(text)` |
| `sha256(Field<byte[]> bytes)` | `Field<byte[]>` | `sha256(bytes)` |
| `encode(Field<byte[]> bytes, String format)` | `StringField` | `encode(bytes, 'format')` – `base64`, `hex` or `escape`. |
| `decode(Field<String> text, String format)` | `Field<byte[]>` | `decode(text, 'format')` |
| `convertTo(Field<String> text, String encoding)` | `Field<byte[]>` | `convert_to(text, 'encoding')` |
| `quoteIdent(Field<String> text)` | `StringField` | `quote_ident(text)` |
| `quoteLiteral(Field<String> text)` | `StringField` | `quote_literal(text)` |
| `quoteNullable(Field<String> text)` | `StringField` | `quote_nullable(text)` |
| `toHex(Field<? extends Number> number)` | `StringField` | `to_hex(number)` |
| `ascii(Field<String> text)` | `NumberField<Integer>` | `ascii(text)` – the code of the first character. |
| `chr(Field<Integer> code)` | `StringField` | `chr(code)` |

## Mathematics

| Method | Returns | Description |
|---|---|---|
| `abs(NumberField<N> x)` | `NumberField<N>` | `abs(x)` |
| `ceil(NumberField<N> x)` | `NumberField<N>` | `ceil(x)` |
| `floor(NumberField<N> x)` | `NumberField<N>` | `floor(x)` |
| `round(NumberField<N> x)` | `NumberField<N>` | `round(x)` |
| `round(Field<? extends Number> x, int decimals)` | `NumberField<BigDecimal>` | `round(x::numeric, decimals)` |
| `trunc(NumberField<N> x)` | `NumberField<N>` | `trunc(x)` |
| `trunc(Field<? extends Number> x, int decimals)` | `NumberField<BigDecimal>` | `trunc(x::numeric, decimals)` |
| `mod(NumberField<N> a, Field<? extends Number> b)` | `NumberField<N>` | `mod(a, b)` |
| `div(Field<? extends Number> a, Field<? extends Number> b)` | `NumberField<BigDecimal>` | `div(a, b)` – integer quotient. |
| `power(Field<? extends Number> base, Field<? extends Number> exponent)` | `NumberField<Double>` | `power(base, exponent)` |
| `sqrt(Field<? extends Number> x)` | `NumberField<Double>` | `sqrt(x)` |
| `cbrt(Field<? extends Number> x)` | `NumberField<Double>` | `cbrt(x)` |
| `exp(Field<? extends Number> x)` | `NumberField<Double>` | `exp(x)` |
| `ln(Field<? extends Number> x)` | `NumberField<Double>` | `ln(x)` |
| `log10(Field<? extends Number> x)` | `NumberField<Double>` | `log10(x)` |
| `log(Field<? extends Number> base, Field<? extends Number> x)` | `NumberField<BigDecimal>` | `log(base, x)` |
| `sign(NumberField<N> x)` | `NumberField<N>` | `sign(x)` |
| `pi()` | `NumberField<Double>` | `pi()` |
| `random()` | `NumberField<Double>` | `random()` – a value in [0, 1). |
| `degrees(Field<? extends Number> radians)` | `NumberField<Double>` | `degrees(radians)` |
| `radians(Field<? extends Number> degrees)` | `NumberField<Double>` | `radians(degrees)` |
| `sin(Field<? extends Number> x)` | `NumberField<Double>` | `sin(x)` |
| `cos(Field<? extends Number> x)` | `NumberField<Double>` | `cos(x)` |
| `tan(Field<? extends Number> x)` | `NumberField<Double>` | `tan(x)` |
| `asin(Field<? extends Number> x)` | `NumberField<Double>` | `asin(x)` |
| `acos(Field<? extends Number> x)` | `NumberField<Double>` | `acos(x)` |
| `atan(Field<? extends Number> x)` | `NumberField<Double>` | `atan(x)` |
| `atan2(Field<? extends Number> y, Field<? extends Number> x)` | `NumberField<Double>` | `atan2(y, x)` |
| `gcd(NumberField<N> a, NumberField<N> b)` | `NumberField<N>` | `gcd(a, b)` |
| `lcm(NumberField<N> a, NumberField<N> b)` | `NumberField<N>` | `lcm(a, b)` |

## Date and time

| Method | Returns | Description |
|---|---|---|
| `now()` | `TemporalField<Instant>` | `now()` – the start of the current transaction. |
| `clockTimestamp()` | `TemporalField<Instant>` | `clock_timestamp()` – the actual current time. |
| `statementTimestamp()` | `TemporalField<Instant>` | `statement_timestamp()` |
| `currentDate()` | `TemporalField<LocalDate>` | `CURRENT_DATE` (in the session time zone) |
| `localTime()` | `TemporalField<LocalTime>` | `LOCALTIME` |
| `localTimestamp()` | `TemporalField<LocalDateTime>` | `LOCALTIMESTAMP` |
| `currentTimestamp()` | `TemporalField<Instant>` | `CURRENT_TIMESTAMP` |
| `dateTrunc(DatePart part, TemporalField<T> value)` | `TemporalField<T>` | `CAST(date_trunc('part', value) AS type)` |
| `extract(DatePart part, TemporalField<?> value)` | `NumberField<BigDecimal>` | `EXTRACT(part FROM value)` |
| `datePart(DatePart part, TemporalField<?> value)` | `NumberField<Double>` | `date_part('part', value)` |
| `dateBin(Duration stride, TemporalField<T> value, T origin)` | `TemporalField<T>` | `date_bin('stride', value, origin)` (PostgreSQL 14+) – e.g. 15-minute buckets. |
| `age(TemporalField<?> end, TemporalField<?> start)` | `StringField` | `age(end, start)` as text, because it has months and years. |
| `interval(Duration duration)` | `Field<Duration>` | An interval bind parameter. |
| `makeDate(Field<Integer> year, Field<Integer> month, Field<Integer> day)` | `TemporalField<LocalDate>` | `make_date(year, month, day)` |
| `makeTime(Field<Integer> hour, Field<Integer> minute, Field<Double> second)` | `TemporalField<LocalTime>` | `make_time(hour, minute, second)` |
| `toChar(Field<?> value, String format)` | `StringField` | `to_char(value, 'format')`, e.g. `"YYYY-MM-DD"` or `"FM999G990D00"`. |
| `toDate(Field<String> text, String format)` | `TemporalField<LocalDate>` | `to_date(text, 'format')` |
| `toTimestamp(Field<String> text, String format)` | `TemporalField<Instant>` | `to_timestamp(text, 'format')` |
| `toTimestamp(NumberField<?> epochSeconds)` | `TemporalField<Instant>` | `to_timestamp(epochSeconds)` |
| `toNumber(Field<String> text, String format)` | `NumberField<BigDecimal>` | `to_number(text, 'format')` |
| `localDateTimeAt(Field<Instant> instant, ZoneId zone)` | `TemporalField<LocalDateTime>` | `(instant AT TIME ZONE 'zone')` – the local date-time in a zone. |
| `instantAt(Field<LocalDateTime> local, ZoneId zone)` | `TemporalField<Instant>` | `(local AT TIME ZONE 'zone')` – the instant of a local date-time in a zone. |
| `generateSeries(int start, int stop)` | `NumberField<Integer>` | `generate_series(start, stop)` – set-returning, use with `tableOf(..)`. |
| `generateSeries(TemporalField<T> start, TemporalField<T> stop, Duration step)` | `TemporalField<T>` | `generate_series(start, stop, step)` for `timestamptz` or `timestamp` bounds; set-returning, use with `tableOf(..)`.  @throws IllegalArgumentException for `date` bounds, for which PostgreSQL returns timestamps; convert them first |

## Ranges

| Method | Returns | Description |
|---|---|---|
| `daterange(Field<LocalDate> lower, Field<LocalDate> upper)` | `Field<String>` | `daterange(lower, upper, '[)')` |
| `daterange(Field<LocalDate> lower, Field<LocalDate> upper, String bounds)` | `Field<String>` | `daterange(lower, upper, 'bounds')` with bounds such as `"[]"`. |
| `tstzrange(Field<Instant> lower, Field<Instant> upper)` | `Field<String>` | `tstzrange(lower, upper, '[)')` |
| `tsrange(Field<LocalDateTime> lower, Field<LocalDateTime> upper)` | `Field<String>` | `tsrange(lower, upper, '[)')` |
| `int4range(Field<Integer> lower, Field<Integer> upper)` | `Field<String>` | `int4range(lower, upper, '[)')` |
| `int8range(Field<Long> lower, Field<Long> upper)` | `Field<String>` | `int8range(lower, upper, '[)')` |
| `numrange(Field<BigDecimal> lower, Field<BigDecimal> upper)` | `Field<String>` | `numrange(lower, upper, '[)')` |
| `rangeContains(Field<String> range, Field<T> value)` | `Condition` | `range @> value` – the range contains the value. |
| `rangeOverlaps(Field<String> a, Field<String> b)` | `Condition` | `a && b` – the ranges overlap. |
| `isEmpty(Field<String> range)` | `Condition` | `isempty(range)` |

## JSON

| Method | Returns | Description |
|---|---|---|
| `pair(String key, Field<?> value)` | `JsonPair` | A pair for `jsonbBuildObject(..)`. |
| `jsonb(Field<String> text)` | `JsonField<String>` | `CAST(text AS jsonb)` |
| `jsonbBuildObject(JsonPair... pairs)` | `JsonField<String>` | `jsonb_build_object('k1', v1, 'k2', v2, ...)` |
| `jsonBuildObject(JsonPair... pairs)` | `JsonField<String>` | `json_build_object('k1', v1, ...)` |
| `jsonbBuildArray(Field<?>... values)` | `JsonField<String>` | `jsonb_build_array(values...)` |
| `jsonBuildArray(Field<?>... values)` | `JsonField<String>` | `json_build_array(values...)` |
| `toJson(Field<?> value)` | `JsonField<String>` | `to_json(value)` |
| `toJsonb(Field<?> value)` | `JsonField<String>` | `to_jsonb(value)` |
| `rowToJson(Table<?> table)` | `JsonField<String>` | `row_to_json(t)` – a whole row of a table in the query as JSON. |
| `jsonbSet(JsonField<T> target, String[] path, Field<?> value, boolean createMissing)` | `JsonField<T>` | `jsonb_set(target, '{path`', value, createMissing)} |
| `jsonbInsert(JsonField<T> target, String[] path, Field<?> value)` | `JsonField<T>` | `jsonb_insert(target, '{path`', value)} |
| `jsonbTypeof(JsonField<?> json)` | `StringField` | `jsonb_typeof(json)` |
| `jsonbArrayLength(JsonField<?> json)` | `NumberField<Integer>` | `jsonb_array_length(json)` |
| `jsonbArrayElements(JsonField<?> json)` | `JsonField<String>` | `jsonb_array_elements(json)` – set-returning. |
| `jsonbArrayElementsText(JsonField<?> json)` | `StringField` | `jsonb_array_elements_text(json)` – set-returning. |
| `jsonbObjectKeys(JsonField<?> json)` | `StringField` | `jsonb_object_keys(json)` – set-returning. |
| `jsonbStripNulls(JsonField<T> json)` | `JsonField<T>` | `jsonb_strip_nulls(json)` |
| `jsonbPretty(JsonField<?> json)` | `StringField` | `jsonb_pretty(json)` |
| `jsonbPathQuery(JsonField<?> json, String jsonPath)` | `JsonField<String>` | `jsonb_path_query(json, 'path')` – set-returning. |
| `jsonbPathQueryFirst(JsonField<?> json, String jsonPath)` | `JsonField<String>` | `jsonb_path_query_first(json, 'path')` |
| `jsonbPathQueryArray(JsonField<?> json, String jsonPath)` | `JsonField<String>` | `jsonb_path_query_array(json, 'path')` |

## Arrays

| Method | Returns | Description |
|---|---|---|
| `array(Field<T> first, Field<T>... more)` | `ArrayField<T>` | `ARRAY[a, b, c]` |
| `arrayOf(Select1<T> query)` | `ArrayField<T>` | `ARRAY(SELECT ...)` |
| `arrayLength(ArrayField<?> array, int dimension)` | `NumberField<Integer>` | `array_length(array, dimension)` |
| `cardinality(ArrayField<?> array)` | `NumberField<Integer>` | `cardinality(array)` |
| `arrayPrepend(E element, ArrayField<E> array)` | `ArrayField<E>` | `array_prepend(?, array)` |
| `arrayCat(ArrayField<E> a, Field<E[]> b)` | `ArrayField<E>` | `array_cat(a, b)` |
| `arrayReplace(ArrayField<E> array, E from, E to)` | `ArrayField<E>` | `array_replace(array, ?, ?)` |
| `arrayPosition(ArrayField<E> array, E element)` | `NumberField<Integer>` | `array_position(array, ?)` – 1-based, `NULL` if absent. |
| `arrayPositions(ArrayField<E> array, E element)` | `ArrayField<Integer>` | `array_positions(array, ?)` |
| `arrayToString(ArrayField<?> array, String separator)` | `StringField` | `array_to_string(array, 'separator')` |
| `arrayLower(ArrayField<?> array, int dimension)` | `NumberField<Integer>` | `array_lower(array, dimension)` |
| `arrayUpper(ArrayField<?> array, int dimension)` | `NumberField<Integer>` | `array_upper(array, dimension)` |
| `unnest(ArrayField<E> array)` | `Field<E>` | `unnest(array)` – set-returning. |

## Full-text search

| Method | Returns | Description |
|---|---|---|
| `toTsvector(String config, Field<String> document)` | `Field<String>` | `to_tsvector('config', document)` |
| `toTsvector(Field<String> document)` | `Field<String>` | `to_tsvector(document)` with the default configuration. |
| `toTsquery(String config, String query)` | `Field<String>` | `to_tsquery('config', ?)` |
| `plaintoTsquery(String config, String text)` | `Field<String>` | `plainto_tsquery('config', ?)` – all words must match. |
| `phrasetoTsquery(String config, String text)` | `Field<String>` | `phraseto_tsquery('config', ?)` – the words must appear in order. |
| `websearchToTsquery(String config, String text)` | `Field<String>` | `websearch_to_tsquery('config', ?)` – search-engine syntax for user input. |
| `websearchToTsquery(String config, Field<String> text)` | `Field<String>` | `websearch_to_tsquery('config', text)` with an expression. |
| `tsMatches(Field<String> vector, Field<String> query)` | `Condition` | `vector @@ query` |
| `tsRank(Field<String> vector, Field<String> query)` | `NumberField<Float>` | `ts_rank(vector, query)` |
| `tsRankCd(Field<String> vector, Field<String> query)` | `NumberField<Float>` | `ts_rank_cd(vector, query)` – cover density ranking. |
| `tsHeadline(String config, Field<String> document, Field<String> query)` | `StringField` | `ts_headline('config', document, query)` – a highlighted excerpt. |
| `setweight(Field<String> vector, char weight)` | `Field<String>` | `setweight(vector, 'A'\|'B'\|'C'\|'D')` |

## Sequences, system information

| Method | Returns | Description |
|---|---|---|
| `genRandomUuid()` | `Field<UUID>` | `gen_random_uuid()` (version 4) |
| `nextval(String sequence)` | `NumberField<Long>` | `nextval('sequence')` |
| `currval(String sequence)` | `NumberField<Long>` | `currval('sequence')` |
| `setval(String sequence, long value)` | `NumberField<Long>` | `setval('sequence', ?)` |
| `lastval()` | `NumberField<Long>` | `lastval()` |
| `currentUser()` | `StringField` | `CURRENT_USER` |
| `sessionUser()` | `StringField` | `SESSION_USER` |
| `currentSchema()` | `StringField` | `current_schema()` |
| `currentDatabase()` | `StringField` | `current_database()` |
| `version()` | `StringField` | `version()` |
| `pgTypeof(Field<?> value)` | `StringField` | `pg_typeof(value)::text` – handy for debugging. |

## GROUP BY elements

| Method | Returns | Description |
|---|---|---|
| `rollup(Field<?>... fields)` | `GroupingElement` | `ROLLUP (fields)` |
| `cube(Field<?>... fields)` | `GroupingElement` | `CUBE (fields)` |
| `groupingSet(Field<?>... fields)` | `GroupingElement` | One set for `groupingSets`: `(a, b)` or `()`. |
| `groupingSets(GroupingElement... sets)` | `GroupingElement` | `GROUPING SETS ((a, b), (a), ())` |
| `grouping(Field<?>... fields)` | `NumberField<Integer>` | `GROUPING(fields)` – which fields are aggregated away in a grouping set. |
