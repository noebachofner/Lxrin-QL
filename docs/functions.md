# Function reference

All functions are static methods of `LxrinQL` (via `import static ch.lxrin.ql.LxrinQL.*`).
Parameters typed `Object` follow the [operand rule](expressions.md#the-operand-rule): expressions are rendered, a `String` is SQL, and other values are bound. Parameters typed `String` or a primitive are **constants** and are written as escaped literals.

Functions that return `FunctionCall` support these modifiers:

| Modifier | SQL |
|---|---|
| `.distinct()` | `f(DISTINCT …)` |
| `.orderBy(sort…)` | `f(… ORDER BY …)` (ordered aggregates) |
| `.withinGroup(sort…)` | `f(…) WITHIN GROUP (ORDER BY …)` |
| `.filter(condition)` | `f(…) FILTER (WHERE …)` |
| `.over()` / `.over(windowSpec)` / `.over("name")` | window function call |
| `.as("alias")`, `.eq(..)`, `.plus(..)`, … | everything an `Expression` can do |

Anything not listed here can be called with `function("name", args…)`.
The catalog is tested against PostgreSQL 17. Functions marked with a version need at least that release.

## Contents

- [Generic](#generic)
- [Aggregate functions](#aggregate-functions)
- [Window functions (use with .over(..))](#window-functions-use-with-over)
- [Conditional expressions](#conditional-expressions)
- [String functions](#string-functions)
- [Mathematical functions](#mathematical-functions)
- [Date and time](#date-and-time)
- [Ranges](#ranges)
- [JSON / JSONB](#json--jsonb)
- [Arrays](#arrays)
- [Full-text search](#full-text-search)
- [Sequences, system information, misc](#sequences-system-information-misc)

## Generic

| Method | SQL / description |
|---|---|
| `function(String name, Object... args)` | Calls any SQL function: `function("similarity", p.name, val(text))` renders `similarity(p.NAME, :lq0)`. The result supports `filter`, `orderBy`, `withinGroup` and `over`. |
| `booleanFunction(String name, Object... args)` | Calls a function that returns a boolean and uses it as a condition. |

## Aggregate functions

| Method | SQL / description |
|---|---|
| `count()` | `count(*)` |
| `count(Object expr)` | `count(expr)` – counts non-null values. |
| `countDistinct(Object expr)` | `count(DISTINCT expr)` |
| `sum(Object expr)` | `sum(expr)` |
| `avg(Object expr)` | `avg(expr)` |
| `min(Object expr)` | `min(expr)` |
| `max(Object expr)` | `max(expr)` |
| `stringAgg(Object expr, String separator)` | `string_agg(expr, 'separator')` – add `.orderBy(..)` for a defined order. |
| `arrayAgg(Object expr)` | `array_agg(expr)` |
| `jsonAgg(Object expr)` | `json_agg(expr)` |
| `jsonbAgg(Object expr)` | `jsonb_agg(expr)` |
| `jsonObjectAgg(Object key, Object value)` | `json_object_agg(key, value)` |
| `jsonbObjectAgg(Object key, Object value)` | `jsonb_object_agg(key, value)` |
| `boolAnd(Object expr)` | `bool_and(expr)` – true if all values are true. |
| `boolOr(Object expr)` | `bool_or(expr)` – true if any value is true. |
| `every(Object expr)` | `every(expr)` – SQL-standard `bool_and`. |
| `bitAnd(Object expr)` | `bit_and(expr)` |
| `bitOr(Object expr)` | `bit_or(expr)` |
| `anyValue(Object expr)` | `any_value(expr)` – an arbitrary non-null value (PostgreSQL 16+). |
| `stddev(Object expr)` | `stddev(expr)` |
| `stddevPop(Object expr)` | `stddev_pop(expr)` |
| `stddevSamp(Object expr)` | `stddev_samp(expr)` |
| `variance(Object expr)` | `variance(expr)` |
| `varPop(Object expr)` | `var_pop(expr)` |
| `varSamp(Object expr)` | `var_samp(expr)` |
| `corr(Object y, Object x)` | `corr(y, x)` – correlation coefficient. |
| `covarPop(Object y, Object x)` | `covar_pop(y, x)` |
| `covarSamp(Object y, Object x)` | `covar_samp(y, x)` |
| `regrSlope(Object y, Object x)` | `regr_slope(y, x)` |
| `regrIntercept(Object y, Object x)` | `regr_intercept(y, x)` |
| `percentileCont(double fraction)` | `percentile_cont(fraction)` – combine with `.withinGroup(expr.asc())`. |
| `percentileDisc(double fraction)` | `percentile_disc(fraction)` – combine with `.withinGroup(expr.asc())`. |
| `mode()` | `mode()` – most frequent value; combine with `.withinGroup(expr.asc())`. |
| `grouping(Object... exprs)` | `GROUPING(exprs)` – tells which columns are aggregated in a grouping set. |
| `rollup(Object... exprs)` | `ROLLUP (exprs)` for `GROUP BY`. |
| `cube(Object... exprs)` | `CUBE (exprs)` for `GROUP BY`. |
| `groupingSets(Object... sets)` | `GROUPING SETS (...)` for `GROUP BY`; build each set with `groupingSet(Object...)`: `groupingSets(groupingSet(a, b), groupingSet(a), groupingSet())`. |
| `groupingSet(Object... exprs)` | One set for `groupingSets`: `(a, b)` or `()`. |

## Window functions (use with .over(..))

| Method | SQL / description |
|---|---|
| `rowNumber()` | `row_number()` |
| `rank()` | `rank()` |
| `denseRank()` | `dense_rank()` |
| `percentRank()` | `percent_rank()` |
| `cumeDist()` | `cume_dist()` |
| `ntile(int buckets)` | `ntile(buckets)` |
| `lag(Object expr)` | `lag(expr)` – value of the previous row. |
| `lag(Object expr, int offset)` | `lag(expr, offset)` |
| `lag(Object expr, int offset, Object defaultValue)` | `lag(expr, offset, default)` |
| `lead(Object expr)` | `lead(expr)` – value of the next row. |
| `lead(Object expr, int offset)` | `lead(expr, offset)` |
| `lead(Object expr, int offset, Object defaultValue)` | `lead(expr, offset, default)` |
| `firstValue(Object expr)` | `first_value(expr)` |
| `lastValue(Object expr)` | `last_value(expr)` – usually needs a frame such as `rowsBetween(unboundedPreceding(), unboundedFollowing())`. |
| `nthValue(Object expr, int n)` | `nth_value(expr, n)` |

## Conditional expressions

| Method | SQL / description |
|---|---|
| `coalesce(Object... values)` | `COALESCE(a, b, ...)` – first non-null argument. |
| `nullif(Object a, Object b)` | `NULLIF(a, b)` – null if both are equal. |
| `greatest(Object... values)` | `GREATEST(a, b, ...)` |
| `least(Object... values)` | `LEAST(a, b, ...)` |

## String functions

| Method | SQL / description |
|---|---|
| `length(Object text)` | `length(text)` |
| `charLength(Object text)` | `char_length(text)` |
| `octetLength(Object text)` | `octet_length(text)` – size in bytes. |
| `bitLength(Object text)` | `bit_length(text)` |
| `lower(Object text)` | `lower(text)` – also lower bound of a range. |
| `upper(Object text)` | `upper(text)` – also upper bound of a range. |
| `initcap(Object text)` | `initcap(text)` – capitalises each word. |
| `concat(Object... values)` | `concat(a, b, ...)` – null arguments are ignored. |
| `concatWs(String separator, Object... values)` | `concat_ws('separator', a, b, ...)` |
| `substring(Object text, int from)` | `substring(text, from)` – 1-based. |
| `substring(Object text, int from, int count)` | `substring(text, from, count)` – 1-based. |
| `substring(Object text, Object from, Object count)` | `substring(text, from, count)` with expression arguments. |
| `substringRegex(Object text, String regex)` | `substring(text FROM 'regex')` – first match of a POSIX regular expression. |
| `left(Object text, int n)` | `left(text, n)` |
| `right(Object text, int n)` | `right(text, n)` |
| `trim(Object text)` | `btrim(text)` – removes leading and trailing spaces. |
| `btrim(Object text, String characters)` | `btrim(text, 'characters')` |
| `ltrim(Object text)` | `ltrim(text)` |
| `ltrim(Object text, String characters)` | `ltrim(text, 'characters')` |
| `rtrim(Object text)` | `rtrim(text)` |
| `rtrim(Object text, String characters)` | `rtrim(text, 'characters')` |
| `lpad(Object text, int length, String fill)` | `lpad(text, length, 'fill')` |
| `rpad(Object text, int length, String fill)` | `rpad(text, length, 'fill')` |
| `replace(Object text, String from, String to)` | `replace(text, 'from', 'to')` |
| `replace(Object text, Object from, Object to)` | `replace(text, from, to)` with expression arguments. |
| `translate(Object text, String from, String to)` | `translate(text, 'from', 'to')` – character-wise replacement. |
| `strpos(Object text, Object substring)` | `strpos(text, substring)` – 1-based position, 0 if not found. |
| `startsWith(Object text, Object prefix)` | `starts_with(text, prefix)` |
| `reverse(Object text)` | `reverse(text)` |
| `repeat(Object text, int n)` | `repeat(text, n)` |
| `splitPart(Object text, String delimiter, int n)` | `split_part(text, 'delimiter', n)` |
| `stringToArray(Object text, String delimiter)` | `string_to_array(text, 'delimiter')` |
| `format(String format, Object... args)` | `format('format', args...)` – like `printf`; supports `%s`, `%I`, `%L`. |
| `regexpReplace(Object text, String pattern, String replacement)` | `regexp_replace(text, 'pattern', 'replacement')` |
| `regexpReplace(Object text, String pattern, String replacement, String flags)` | `regexp_replace(text, 'pattern', 'replacement', 'flags')` – e.g. flags `"gi"`. |
| `regexpMatch(Object text, String pattern)` | `regexp_match(text, 'pattern')` – text array of the first match's groups. |
| `regexpMatches(Object text, String pattern, String flags)` | `regexp_matches(text, 'pattern', 'flags')` – set of matches (flag `g` for all). |
| `regexpSplitToArray(Object text, String pattern)` | `regexp_split_to_array(text, 'pattern')` |
| `regexpSplitToTable(Object text, String pattern)` | `regexp_split_to_table(text, 'pattern')` |
| `regexpCount(Object text, String pattern)` | `regexp_count(text, 'pattern')` (PostgreSQL 15+) |
| `regexpSubstr(Object text, String pattern)` | `regexp_substr(text, 'pattern')` (PostgreSQL 15+) |
| `md5(Object text)` | `md5(text)` |
| `sha256(Object bytes)` | `sha256(bytes)` |
| `encode(Object bytes, String format)` | `encode(bytes, 'format')` – format `base64`, `hex` or `escape`. |
| `decode(Object text, String format)` | `decode(text, 'format')` |
| `convertTo(Object text, String encoding)` | `convert_to(text, 'encoding')` – text to bytes. |
| `quoteIdent(Object text)` | `quote_ident(text)` |
| `quoteLiteral(Object text)` | `quote_literal(text)` |
| `quoteNullable(Object text)` | `quote_nullable(text)` |
| `toHex(Object number)` | `to_hex(number)` |
| `ascii(Object text)` | `ascii(text)` – code of the first character. |
| `chr(Object code)` | `chr(code)` |

## Mathematical functions

| Method | SQL / description |
|---|---|
| `abs(Object x)` | `abs(x)` |
| `ceil(Object x)` | `ceil(x)` |
| `floor(Object x)` | `floor(x)` |
| `round(Object x)` | `round(x)` |
| `round(Object x, int decimals)` | `round(x, decimals)` – `x` must be `numeric`. |
| `trunc(Object x)` | `trunc(x)` |
| `trunc(Object x, int decimals)` | `trunc(x, decimals)` |
| `mod(Object a, Object b)` | `mod(a, b)` |
| `div(Object a, Object b)` | `div(a, b)` – integer quotient. |
| `power(Object base, Object exponent)` | `power(base, exponent)` |
| `sqrt(Object x)` | `sqrt(x)` |
| `cbrt(Object x)` | `cbrt(x)` |
| `exp(Object x)` | `exp(x)` |
| `ln(Object x)` | `ln(x)` |
| `log10(Object x)` | `log10(x)` |
| `log(Object base, Object x)` | `log(base, x)` |
| `sign(Object x)` | `sign(x)` |
| `pi()` | `pi()` |
| `random()` | `random()` – value in [0, 1). |
| `degrees(Object radians)` | `degrees(radians)` |
| `radians(Object degrees)` | `radians(degrees)` |
| `sin(Object x)` | `sin(x)` |
| `cos(Object x)` | `cos(x)` |
| `tan(Object x)` | `tan(x)` |
| `asin(Object x)` | `asin(x)` |
| `acos(Object x)` | `acos(x)` |
| `atan(Object x)` | `atan(x)` |
| `atan2(Object y, Object x)` | `atan2(y, x)` |
| `gcd(Object a, Object b)` | `gcd(a, b)` |
| `lcm(Object a, Object b)` | `lcm(a, b)` |
| `widthBucket(Object x, Object low, Object high, int buckets)` | `width_bucket(x, low, high, buckets)` – histogram bucket number. |

## Date and time

| Method | SQL / description |
|---|---|
| `now()` | `now()` – start of the current transaction. |
| `clockTimestamp()` | `clock_timestamp()` – actual current time. |
| `statementTimestamp()` | `statement_timestamp()` |
| `currentDate()` | `CURRENT_DATE` |
| `currentTime()` | `CURRENT_TIME` |
| `currentTimestamp()` | `CURRENT_TIMESTAMP` |
| `localTime()` | `LOCALTIME` |
| `localTimestamp()` | `LOCALTIMESTAMP` |
| `dateTrunc(String field, Object source)` | `date_trunc('field', source)` – field e.g. `"day"`, `"month"`, `"year"`. |
| `dateTrunc(String field, Object source, String timeZone)` | `date_trunc('field', source, 'time zone')` (PostgreSQL 12+) |
| `datePart(String field, Object source)` | `date_part('field', source)` |
| `extract(String field, Object source)` | `EXTRACT(field FROM source)` – field e.g. `"YEAR"`, `"DOW"`, `"EPOCH"`. |
| `dateBin(String stride, Object source, Object origin)` | `date_bin('stride', source, origin)` (PostgreSQL 14+) – e.g. stride `"15 minutes"`. |
| `age(Object timestamp)` | `age(timestamp)` – interval from the timestamp to today. |
| `age(Object end, Object start)` | `age(end, start)` |
| `interval(String text)` | `INTERVAL 'text'`, e.g. `interval("3 days")`, `interval("1 hour 30 minutes")`. |
| `makeDate(Object year, Object month, Object day)` | `make_date(year, month, day)` |
| `makeTime(Object hour, Object minute, Object second)` | `make_time(hour, minute, second)` |
| `makeTimestamp(Object year, Object month, Object day, Object hour, Object minute, Object second)` | `make_timestamp(year, month, day, hour, minute, second)` |
| `toChar(Object value, String format)` | `to_char(value, 'format')` – e.g. `"YYYY-MM-DD HH24:MI"` or `"FM999G990D00"`. |
| `toDate(Object text, String format)` | `to_date(text, 'format')` |
| `toTimestamp(Object text, String format)` | `to_timestamp(text, 'format')` |
| `toTimestamp(Object epochSeconds)` | `to_timestamp(epochSeconds)` |
| `toNumber(Object text, String format)` | `to_number(text, 'format')` |
| `atTimeZone(Object source, String zone)` | `(source AT TIME ZONE 'zone')`, e.g. `atTimeZone(o.createdAt, "Europe/Zurich")`. |
| `atTimeZone(Object source, Object zone)` | `(source AT TIME ZONE zone)` with an expression as zone. |
| `justifyDays(Object interval)` | `justify_days(interval)` |
| `justifyHours(Object interval)` | `justify_hours(interval)` |
| `justifyInterval(Object interval)` | `justify_interval(interval)` |
| `generateSeries(Object start, Object stop)` | `generate_series(start, stop)` – numbers or, with a step, timestamps. Use in `FROM`. |
| `generateSeries(Object start, Object stop, Object step)` | `generate_series(start, stop, step)`, e.g. `generateSeries(from, to, interval("1 day"))`. |

## Ranges

| Method | SQL / description |
|---|---|
| `daterange(Object lower, Object upper)` | `daterange(lower, upper)` – bounds `[)`. |
| `daterange(Object lower, Object upper, String bounds)` | `daterange(lower, upper, 'bounds')` – bounds e.g. `"[]"`. |
| `tsrange(Object lower, Object upper)` | `tsrange(lower, upper)` |
| `tstzrange(Object lower, Object upper)` | `tstzrange(lower, upper)` |
| `int4range(Object lower, Object upper)` | `int4range(lower, upper)` |
| `int8range(Object lower, Object upper)` | `int8range(lower, upper)` |
| `numrange(Object lower, Object upper)` | `numrange(lower, upper)` |
| `isEmpty(Object range)` | `isempty(range)` |

## JSON / JSONB

| Method | SQL / description |
|---|---|
| `jsonGet(Object json, String key)` | `(json -> 'key')` – object field as JSON. |
| `jsonGet(Object json, int index)` | `(json -> index)` – array element as JSON (0-based, negative counts from the end). |
| `jsonGetText(Object json, String key)` | `(json ->> 'key')` – object field as text. |
| `jsonGetText(Object json, int index)` | `(json ->> index)` – array element as text. |
| `jsonPath(Object json, String... path)` | `(json #> '{a,b}')` – value at a path as JSON. |
| `jsonPathText(Object json, String... path)` | `(json #>> '{a,b}')` – value at a path as text. |
| `jsonb(Object value)` | `CAST(value AS jsonb)` – e.g. `jsonb(val(jsonString))`. |
| `jsonbBuildObject(Object... keyValues)` | `jsonb_build_object(k1, v1, k2, v2, ...)` – keys given as Java strings become literals. |
| `jsonBuildObject(Object... keyValues)` | `json_build_object(k1, v1, ...)` – keys given as Java strings become literals. |
| `jsonbBuildArray(Object... values)` | `jsonb_build_array(values...)` |
| `jsonBuildArray(Object... values)` | `json_build_array(values...)` |
| `toJson(Object value)` | `to_json(value)` |
| `toJsonb(Object value)` | `to_jsonb(value)` |
| `rowToJson(Object row)` | `row_to_json(row)` – e.g. `rowToJson(raw("p"))` for the whole row of alias `p`. |
| `jsonbSet(Object target, String[] path, Object newValue)` | `jsonb_set(target, '{path}', newValue)` |
| `jsonbSet(Object target, String[] path, Object newValue, boolean createMissing)` | `jsonb_set(target, '{path}', newValue, createMissing)` |
| `jsonbInsert(Object target, String[] path, Object newValue)` | `jsonb_insert(target, '{path}', newValue)` |
| `jsonbConcat(Object a, Object b)` | `(a \|\| b)` – merge two jsonb values. |
| `jsonbDelete(Object json, String key)` | `(json - 'key')` – remove a key. |
| `jsonbDeletePath(Object json, String... path)` | `(json #- '{path}')` – remove the value at a path. |
| `jsonbTypeof(Object json)` | `jsonb_typeof(json)` |
| `jsonbArrayLength(Object json)` | `jsonb_array_length(json)` |
| `jsonbArrayElements(Object json)` | `jsonb_array_elements(json)` – set-returning, use in `FROM`. |
| `jsonbArrayElementsText(Object json)` | `jsonb_array_elements_text(json)` – set-returning. |
| `jsonbEach(Object json)` | `jsonb_each(json)` – set of key/value pairs. |
| `jsonbEachText(Object json)` | `jsonb_each_text(json)` |
| `jsonbObjectKeys(Object json)` | `jsonb_object_keys(json)` |
| `jsonbStripNulls(Object json)` | `jsonb_strip_nulls(json)` |
| `jsonbPretty(Object json)` | `jsonb_pretty(json)` |
| `jsonbPathQuery(Object json, String jsonPath)` | `jsonb_path_query(json, 'jsonpath')` – set-returning. |
| `jsonbPathQueryFirst(Object json, String jsonPath)` | `jsonb_path_query_first(json, 'jsonpath')` |
| `jsonbPathQueryArray(Object json, String jsonPath)` | `jsonb_path_query_array(json, 'jsonpath')` |
| `jsonbPathExists(Object json, String jsonPath)` | `jsonb_path_exists(json, 'jsonpath')` |
| `jsonbPathMatch(Object json, String jsonPath)` | `jsonb_path_match(json, 'jsonpath predicate')` |

## Arrays

| Method | SQL / description |
|---|---|
| `array(Object... elements)` | `ARRAY[a, b, c]` |
| `arrayOf(Object subQuery)` | `ARRAY(SELECT ...)` – collects a sub-query into an array. |
| `arrayElement(Object array, int index)` | `(array)[index]` – 1-based element access. |
| `arrayLength(Object array, int dimension)` | `array_length(array, dimension)` |
| `cardinality(Object array)` | `cardinality(array)` – total number of elements. |
| `arrayAppend(Object array, Object element)` | `array_append(array, element)` |
| `arrayPrepend(Object element, Object array)` | `array_prepend(element, array)` |
| `arrayCat(Object a, Object b)` | `array_cat(a, b)` |
| `arrayRemove(Object array, Object element)` | `array_remove(array, element)` |
| `arrayReplace(Object array, Object from, Object to)` | `array_replace(array, from, to)` |
| `arrayPosition(Object array, Object element)` | `array_position(array, element)` |
| `arrayPositions(Object array, Object element)` | `array_positions(array, element)` |
| `arrayToString(Object array, String separator)` | `array_to_string(array, 'separator')` |
| `arrayLower(Object array, int dimension)` | `array_lower(array, dimension)` |
| `arrayUpper(Object array, int dimension)` | `array_upper(array, dimension)` |
| `unnest(Object array)` | `unnest(array)` – set-returning, use in `FROM` or the select list. |

## Full-text search

| Method | SQL / description |
|---|---|
| `toTsvector(Object document)` | `to_tsvector(document)` – uses the default text search configuration. |
| `toTsvector(String config, Object document)` | `to_tsvector('config', document)` – e.g. config `"english"`. |
| `toTsquery(Object query)` | `to_tsquery(query)` |
| `toTsquery(String config, Object query)` | `to_tsquery('config', query)` |
| `plaintoTsquery(String config, Object text)` | `plainto_tsquery('config', text)` – plain text, all words must match. |
| `phrasetoTsquery(String config, Object text)` | `phraseto_tsquery('config', text)` – words must appear in order. |
| `websearchToTsquery(String config, Object text)` | `websearch_to_tsquery('config', text)` – Google-like syntax (`"quoted" -excluded or`). |
| `tsRank(Object vector, Object query)` | `ts_rank(vector, query)` |
| `tsRankCd(Object vector, Object query)` | `ts_rank_cd(vector, query)` – cover density ranking. |
| `tsHeadline(String config, Object document, Object query)` | `ts_headline('config', document, query)` – highlighted excerpt. |
| `setweight(Object vector, String weight)` | `setweight(vector, 'A'\|'B'\|'C'\|'D')` |

## Sequences, system information, misc

| Method | SQL / description |
|---|---|
| `genRandomUuid()` | `gen_random_uuid()` (PostgreSQL 13+) |
| `nextval(String sequence)` | `nextval('sequence')` |
| `currval(String sequence)` | `currval('sequence')` |
| `setval(String sequence, Object value)` | `setval('sequence', value)` |
| `lastval()` | `lastval()` |
| `currentUser()` | `CURRENT_USER` |
| `sessionUser()` | `SESSION_USER` |
| `currentSchema()` | `current_schema()` |
| `currentDatabase()` | `current_database()` |
| `version()` | `version()` |
| `pgTypeof(Object value)` | `pg_typeof(value)` – handy for debugging. |
| `row(Object... values)` | `ROW(a, b, ...)` – row constructor, e.g. for `eq(row(a, b), row(x, y))`. |
## See also

- Conditions and operators (`eq`, `in`, `like`, `matches`, `contains`, `overlaps`, `tsMatches`, `exists`, `any`/`all`, …) are listed in [Expressions & conditions](expressions.md#conditions).
- Building blocks such as `val`, `inline`, `raw`, `sql`, `cast`, `caseWhen`, `caseOf`, `window`, `partitionBy`, `lateral`, `asc`/`desc` and `defaultValue` are covered in [Expressions & conditions](expressions.md).
