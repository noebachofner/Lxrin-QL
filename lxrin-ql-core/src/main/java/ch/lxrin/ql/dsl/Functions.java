package ch.lxrin.ql.dsl;

import ch.lxrin.ql.render.QueryPart;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.types.DataType;
import ch.lxrin.ql.types.Literals;
import ch.lxrin.ql.types.SqlTypes;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The typed PostgreSQL function catalog. All methods are also available
 * through {@code import static ch.lxrin.ql.QL.*}.
 *
 * <p>Arguments typed {@code Field} are expressions (use {@code param(..)} or
 * the {@code T} overloads for values). {@code String} arguments that are
 * part of the query's <em>shape</em> – date parts, formats, separators, regex
 * patterns, JSON keys and paths, text-search configurations – are written as
 * escaped literals, so the same expression can be used in the select list and
 * in {@code GROUP BY}. Anything missing here can be defined with
 * {@link Routines}.</p>
 */
public class Functions extends Conditions {

    /** Static members only; extended by {@link Statements}. */
    protected Functions() {}

    // =========================================================================
    // Aggregates
    // =========================================================================

    /** {@code count(*)} */
    public static NumberAggregate<Long> count() {
        return Aggregates.number("count", SqlTypes.INT8, c -> c.append('*'));
    }

    /** {@code count(field)} – counts non-null values. */
    public static NumberAggregate<Long> count(Field<?> field) {
        return Aggregates.number("count", SqlTypes.INT8, field);
    }

    /** {@code count(DISTINCT field)} */
    public static NumberAggregate<Long> countDistinct(Field<?> field) {
        return count(field).distinct();
    }

    /** {@code sum(field)}; read as {@code BigDecimal}, so it cannot overflow. */
    public static NumberAggregate<BigDecimal> sum(Field<? extends Number> field) {
        return Aggregates.number("sum", SqlTypes.NUMERIC, field);
    }

    /** {@code avg(field)} */
    public static NumberAggregate<BigDecimal> avg(Field<? extends Number> field) {
        return Aggregates.number("avg", SqlTypes.NUMERIC, field);
    }

    /** {@code min(field)}; the result keeps the field's type family. */
    public static <T> AggregateFunction<T> min(Field<T> field) {
        return Aggregates.sameKind("min", field.type(), field);
    }

    /** {@code min(field)} for numbers. */
    public static <N extends Number> NumberAggregate<N> min(NumberField<N> field) {
        return Aggregates.number("min", field.type(), field);
    }

    /** {@code min(field)} for text. */
    public static StringAggregate min(StringField field) {
        return Aggregates.string("min", field.type(), field);
    }

    /** {@code max(field)}; the result keeps the field's type family. */
    public static <T> AggregateFunction<T> max(Field<T> field) {
        return Aggregates.sameKind("max", field.type(), field);
    }

    /** {@code max(field)} for numbers. */
    public static <N extends Number> NumberAggregate<N> max(NumberField<N> field) {
        return Aggregates.number("max", field.type(), field);
    }

    /** {@code max(field)} for text. */
    public static StringAggregate max(StringField field) {
        return Aggregates.string("max", field.type(), field);
    }

    /** {@code string_agg(field, 'separator')} – add {@code .orderBy(..)} for a defined order. */
    public static StringAggregate stringAgg(Field<String> field, String separator) {
        return Aggregates.string("string_agg", SqlTypes.TEXT, field, lit(separator));
    }

    /** {@code array_agg(field)} */
    public static <T> ArrayAggregate<T> arrayAgg(Field<T> field) {
        return Aggregates.array("array_agg", field.type().array(), field);
    }

    /** {@code json_agg(field)} */
    public static JsonAggregate<String> jsonAgg(Field<?> field) {
        return Aggregates.json("json_agg", SqlTypes.JSON, field);
    }

    /** {@code jsonb_agg(field)} */
    public static JsonAggregate<String> jsonbAgg(Field<?> field) {
        return Aggregates.json("jsonb_agg", SqlTypes.JSONB, field);
    }

    /** {@code json_object_agg(key, value)} */
    public static JsonAggregate<String> jsonObjectAgg(Field<String> key, Field<?> value) {
        return Aggregates.json("json_object_agg", SqlTypes.JSON, key, value);
    }

    /** {@code jsonb_object_agg(key, value)} */
    public static JsonAggregate<String> jsonbObjectAgg(Field<String> key, Field<?> value) {
        return Aggregates.json("jsonb_object_agg", SqlTypes.JSONB, key, value);
    }

    /** {@code bool_and(field)} – true if all values are true. */
    public static BooleanAggregate boolAnd(Field<Boolean> field) {
        return Aggregates.bool("bool_and", field);
    }

    /** {@code bool_or(field)} – true if any value is true. */
    public static BooleanAggregate boolOr(Field<Boolean> field) {
        return Aggregates.bool("bool_or", field);
    }

    /** {@code every(field)} – SQL-standard {@code bool_and}. */
    public static BooleanAggregate every(Field<Boolean> field) {
        return Aggregates.bool("every", field);
    }

    /** {@code bit_and(field)} */
    public static <N extends Number> NumberAggregate<N> bitAnd(NumberField<N> field) {
        return Aggregates.number("bit_and", field.type(), field);
    }

    /** {@code bit_or(field)} */
    public static <N extends Number> NumberAggregate<N> bitOr(NumberField<N> field) {
        return Aggregates.number("bit_or", field.type(), field);
    }

    /** {@code any_value(field)} – an arbitrary value of the group (PostgreSQL 16+). */
    public static <T> AggregateFunction<T> anyValue(Field<T> field) {
        return Aggregates.sameKind("any_value", field.type(), field);
    }

    /** {@code stddev(field)} */
    public static NumberAggregate<BigDecimal> stddev(Field<? extends Number> field) {
        return Aggregates.number("stddev", SqlTypes.NUMERIC, field);
    }

    /** {@code stddev_pop(field)} */
    public static NumberAggregate<BigDecimal> stddevPop(Field<? extends Number> field) {
        return Aggregates.number("stddev_pop", SqlTypes.NUMERIC, field);
    }

    /** {@code stddev_samp(field)} */
    public static NumberAggregate<BigDecimal> stddevSamp(Field<? extends Number> field) {
        return Aggregates.number("stddev_samp", SqlTypes.NUMERIC, field);
    }

    /** {@code variance(field)} */
    public static NumberAggregate<BigDecimal> variance(Field<? extends Number> field) {
        return Aggregates.number("variance", SqlTypes.NUMERIC, field);
    }

    /** {@code var_pop(field)} */
    public static NumberAggregate<BigDecimal> varPop(Field<? extends Number> field) {
        return Aggregates.number("var_pop", SqlTypes.NUMERIC, field);
    }

    /** {@code var_samp(field)} */
    public static NumberAggregate<BigDecimal> varSamp(Field<? extends Number> field) {
        return Aggregates.number("var_samp", SqlTypes.NUMERIC, field);
    }

    /** {@code corr(y, x)} – correlation coefficient. */
    public static NumberAggregate<Double> corr(Field<? extends Number> y, Field<? extends Number> x) {
        return Aggregates.number("corr", SqlTypes.FLOAT8, y, x);
    }

    /** {@code covar_pop(y, x)} */
    public static NumberAggregate<Double> covarPop(Field<? extends Number> y, Field<? extends Number> x) {
        return Aggregates.number("covar_pop", SqlTypes.FLOAT8, y, x);
    }

    /** {@code covar_samp(y, x)} */
    public static NumberAggregate<Double> covarSamp(Field<? extends Number> y, Field<? extends Number> x) {
        return Aggregates.number("covar_samp", SqlTypes.FLOAT8, y, x);
    }

    /** {@code regr_slope(y, x)} */
    public static NumberAggregate<Double> regrSlope(Field<? extends Number> y, Field<? extends Number> x) {
        return Aggregates.number("regr_slope", SqlTypes.FLOAT8, y, x);
    }

    /** {@code regr_intercept(y, x)} */
    public static NumberAggregate<Double> regrIntercept(Field<? extends Number> y, Field<? extends Number> x) {
        return Aggregates.number("regr_intercept", SqlTypes.FLOAT8, y, x);
    }

    /** {@code percentile_cont(fraction) WITHIN GROUP (ORDER BY sort)}, e.g. the median with 0.5. */
    public static NumberAggregate<Double> percentileCont(double fraction, SortField<? extends Number> sort) {
        return Aggregates.number("percentile_cont", SqlTypes.FLOAT8, lit(fraction)).withinGroup(sort);
    }

    /** {@code percentile_disc(fraction) WITHIN GROUP (ORDER BY sort)} – an actual value of the group. */
    public static <T> AggregateFunction<T> percentileDisc(double fraction, SortField<T> sort) {
        return Aggregates.sameKind("percentile_disc", sort.field().type(), lit(fraction)).withinGroup(sort);
    }

    /** {@code mode() WITHIN GROUP (ORDER BY sort)} – the most frequent value. */
    public static <T> AggregateFunction<T> mode(SortField<T> sort) {
        return Aggregates.sameKind("mode", sort.field().type()).withinGroup(sort);
    }

    // =========================================================================
    // Window functions
    // =========================================================================

    /** {@code row_number()} – use with {@code over(..)}. */
    public static WindowFunction<NumberField<Long>> rowNumber() {
        return numberWindow("row_number", SqlTypes.INT8);
    }

    /** {@code rank()} */
    public static WindowFunction<NumberField<Long>> rank() {
        return numberWindow("rank", SqlTypes.INT8);
    }

    /** {@code dense_rank()} */
    public static WindowFunction<NumberField<Long>> denseRank() {
        return numberWindow("dense_rank", SqlTypes.INT8);
    }

    /** {@code percent_rank()} */
    public static WindowFunction<NumberField<Double>> percentRank() {
        return numberWindow("percent_rank", SqlTypes.FLOAT8);
    }

    /** {@code cume_dist()} */
    public static WindowFunction<NumberField<Double>> cumeDist() {
        return numberWindow("cume_dist", SqlTypes.FLOAT8);
    }

    /** {@code ntile(buckets)} */
    public static WindowFunction<NumberField<Integer>> ntile(int buckets) {
        return numberWindow("ntile", SqlTypes.INT4, lit(buckets));
    }

    /** {@code lag(field)} – the value of the previous row. */
    public static <T> WindowFunction<Field<T>> lag(Field<T> field) {
        return valueWindow("lag", field);
    }

    /** {@code lag(field, offset)} */
    public static <T> WindowFunction<Field<T>> lag(Field<T> field, int offset) {
        return valueWindow("lag", field, lit(offset));
    }

    /** {@code lag(field, offset, default)} */
    public static <T> WindowFunction<Field<T>> lag(Field<T> field, int offset, T defaultValue) {
        return valueWindow("lag", field, lit(offset), param(defaultValue, field.type()));
    }

    /** {@code lead(field)} – the value of the next row. */
    public static <T> WindowFunction<Field<T>> lead(Field<T> field) {
        return valueWindow("lead", field);
    }

    /** {@code lead(field, offset)} */
    public static <T> WindowFunction<Field<T>> lead(Field<T> field, int offset) {
        return valueWindow("lead", field, lit(offset));
    }

    /** {@code lead(field, offset, default)} */
    public static <T> WindowFunction<Field<T>> lead(Field<T> field, int offset, T defaultValue) {
        return valueWindow("lead", field, lit(offset), param(defaultValue, field.type()));
    }

    /** {@code first_value(field)} */
    public static <T> WindowFunction<Field<T>> firstValue(Field<T> field) {
        return valueWindow("first_value", field);
    }

    /** {@code last_value(field)} – usually needs a frame up to {@code UNBOUNDED FOLLOWING}. */
    public static <T> WindowFunction<Field<T>> lastValue(Field<T> field) {
        return valueWindow("last_value", field);
    }

    /** {@code nth_value(field, n)} */
    public static <T> WindowFunction<Field<T>> nthValue(Field<T> field, int n) {
        return valueWindow("nth_value", field, lit(n));
    }

    private static <N extends Number> WindowFunction<NumberField<N>> numberWindow(String name, DataType<N> type, QueryPart... args) {
        QueryPart call = Ops.call(name, args);
        return new WindowFunction<>(call, part -> Fields.number(type, part));
    }

    private static <T> WindowFunction<Field<T>> valueWindow(String name, Field<T> field, QueryPart... more) {
        QueryPart[] args = new QueryPart[more.length + 1];
        args[0] = field;
        System.arraycopy(more, 0, args, 1, more.length);
        QueryPart call = Ops.call(name, args);
        return new WindowFunction<>(call, part -> Fields.of(field.type(), part));
    }

    // =========================================================================
    // Conditional expressions
    // =========================================================================

    /** {@code COALESCE(a, b, ...)} */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> Field<T> coalesce(Field<T> first, Field<T>... more) {
        return Fields.of(first.type(), Ops.call("COALESCE", prepend(first, more)));
    }

    /** {@code GREATEST(a, b, ...)} */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> Field<T> greatest(Field<T> first, Field<T>... more) {
        return Fields.of(first.type(), Ops.call("GREATEST", prepend(first, more)));
    }

    /** {@code LEAST(a, b, ...)} */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> Field<T> least(Field<T> first, Field<T>... more) {
        return Fields.of(first.type(), Ops.call("LEAST", prepend(first, more)));
    }

    /** {@code NULLIF(a, b)} */
    public static <T> Field<T> nullif(Field<T> a, Field<T> b) {
        return Fields.of(a.type(), Ops.call("NULLIF", a, b));
    }

    /** Starts a searched {@code CASE WHEN condition THEN result ...}. */
    public static <T> CaseWhen<T> caseWhen(Condition condition, Field<T> result) {
        return new CaseWhen<>(result.type(), List.of()).when(condition, result);
    }

    /** Starts a simple {@code CASE subject WHEN value THEN result ...}. */
    public static <S> CaseOf<S> caseOf(Field<S> subject) {
        return new CaseOf<>(subject);
    }

    // =========================================================================
    // Strings
    // =========================================================================

    /** {@code char_length(text)} */
    public static NumberField<Integer> charLength(Field<String> text) {
        return Fields.number(SqlTypes.INT4, Ops.call("char_length", text));
    }

    /** {@code length(text)} */
    public static NumberField<Integer> length(Field<String> text) {
        return Fields.number(SqlTypes.INT4, Ops.call("length", text));
    }

    /** {@code octet_length(text)} – size in bytes. */
    public static NumberField<Integer> octetLength(Field<String> text) {
        return Fields.number(SqlTypes.INT4, Ops.call("octet_length", text));
    }

    /** {@code bit_length(text)} */
    public static NumberField<Integer> bitLength(Field<String> text) {
        return Fields.number(SqlTypes.INT4, Ops.call("bit_length", text));
    }

    /** {@code lower(text)} */
    public static StringField lower(Field<String> text) {
        return Fields.string(Ops.call("lower", text));
    }

    /** {@code upper(text)} */
    public static StringField upper(Field<String> text) {
        return Fields.string(Ops.call("upper", text));
    }

    /** {@code initcap(text)} – capitalises each word. */
    public static StringField initcap(Field<String> text) {
        return Fields.string(Ops.call("initcap", text));
    }

    /** {@code concat(a, b, ...)} – {@code NULL} arguments are ignored. */
    public static StringField concat(Field<?>... values) {
        return Fields.string(Ops.call("concat", values));
    }

    /** {@code concat_ws('separator', a, b, ...)} */
    public static StringField concatWs(String separator, Field<?>... values) {
        return Fields.string(Ops.call("concat_ws", prepend(lit(separator), values)));
    }

    /** {@code substring(text, from)} – 1-based. */
    public static StringField substring(Field<String> text, int from) {
        return Fields.string(Ops.call("substring", text, lit(from)));
    }

    /** {@code substring(text, from, count)} – 1-based. */
    public static StringField substring(Field<String> text, int from, int count) {
        return Fields.string(Ops.call("substring", text, lit(from), lit(count)));
    }

    /** {@code substring(text FROM 'regex')} – the first match of a POSIX regular expression. */
    public static StringField substringRegex(Field<String> text, String regex) {
        return Fields.string(ctx -> ctx.append("substring(").visit(text).append(" FROM ").append(Literals.quote(regex)).append(')'));
    }

    /** {@code left(text, n)} */
    public static StringField left(Field<String> text, int n) {
        return Fields.string(Ops.call("left", text, lit(n)));
    }

    /** {@code right(text, n)} */
    public static StringField right(Field<String> text, int n) {
        return Fields.string(Ops.call("right", text, lit(n)));
    }

    /** {@code btrim(text)} */
    public static StringField trim(Field<String> text) {
        return Fields.string(Ops.call("btrim", text));
    }

    /** {@code btrim(text, 'characters')} */
    public static StringField btrim(Field<String> text, String characters) {
        return Fields.string(Ops.call("btrim", text, lit(characters)));
    }

    /** {@code ltrim(text)} */
    public static StringField ltrim(Field<String> text) {
        return Fields.string(Ops.call("ltrim", text));
    }

    /** {@code ltrim(text, 'characters')} */
    public static StringField ltrim(Field<String> text, String characters) {
        return Fields.string(Ops.call("ltrim", text, lit(characters)));
    }

    /** {@code rtrim(text)} */
    public static StringField rtrim(Field<String> text) {
        return Fields.string(Ops.call("rtrim", text));
    }

    /** {@code rtrim(text, 'characters')} */
    public static StringField rtrim(Field<String> text, String characters) {
        return Fields.string(Ops.call("rtrim", text, lit(characters)));
    }

    /** {@code lpad(text, length, 'fill')} */
    public static StringField lpad(Field<String> text, int length, String fill) {
        return Fields.string(Ops.call("lpad", text, lit(length), lit(fill)));
    }

    /** {@code rpad(text, length, 'fill')} */
    public static StringField rpad(Field<String> text, int length, String fill) {
        return Fields.string(Ops.call("rpad", text, lit(length), lit(fill)));
    }

    /** {@code replace(text, 'from', 'to')} */
    public static StringField replace(Field<String> text, String from, String to) {
        return Fields.string(Ops.call("replace", text, lit(from), lit(to)));
    }

    /** {@code replace(text, from, to)} with expressions. */
    public static StringField replace(Field<String> text, Field<String> from, Field<String> to) {
        return Fields.string(Ops.call("replace", text, from, to));
    }

    /** {@code translate(text, 'from', 'to')} – character-wise replacement. */
    public static StringField translate(Field<String> text, String from, String to) {
        return Fields.string(Ops.call("translate", text, lit(from), lit(to)));
    }

    /** {@code strpos(text, ?)} – 1-based position, 0 if not found. */
    public static NumberField<Integer> strpos(Field<String> text, String substring) {
        return Fields.number(SqlTypes.INT4, Ops.call("strpos", text, param(substring)));
    }

    /** {@code starts_with(text, ?)} */
    public static Condition startsWith(Field<String> text, String prefix) {
        return Fields.condition(Ops.call("starts_with", text, param(prefix)), true);
    }

    /** {@code reverse(text)} */
    public static StringField reverse(Field<String> text) {
        return Fields.string(Ops.call("reverse", text));
    }

    /** {@code repeat(text, n)} */
    public static StringField repeat(Field<String> text, int n) {
        return Fields.string(Ops.call("repeat", text, lit(n)));
    }

    /** {@code split_part(text, 'delimiter', n)} */
    public static StringField splitPart(Field<String> text, String delimiter, int n) {
        return Fields.string(Ops.call("split_part", text, lit(delimiter), lit(n)));
    }

    /** {@code string_to_array(text, 'delimiter')} */
    public static ArrayField<String> stringToArray(Field<String> text, String delimiter) {
        return Fields.array(SqlTypes.TEXT.array(), Ops.call("string_to_array", text, lit(delimiter)));
    }

    /** {@code format('format', args...)} – supports {@code %s}, {@code %I}, {@code %L}. */
    public static StringField format(String format, Field<?>... args) {
        return Fields.string(Ops.call("format", prepend(lit(format), args)));
    }

    /** {@code regexp_replace(text, 'pattern', 'replacement')} */
    public static StringField regexpReplace(Field<String> text, String pattern, String replacement) {
        return Fields.string(Ops.call("regexp_replace", text, lit(pattern), lit(replacement)));
    }

    /** {@code regexp_replace(text, 'pattern', 'replacement', 'flags')}, e.g. flags {@code "gi"}. */
    public static StringField regexpReplace(Field<String> text, String pattern, String replacement, String flags) {
        return Fields.string(Ops.call("regexp_replace", text, lit(pattern), lit(replacement), lit(flags)));
    }

    /** {@code regexp_match(text, 'pattern')} – the groups of the first match. */
    public static ArrayField<String> regexpMatch(Field<String> text, String pattern) {
        return Fields.array(SqlTypes.TEXT.array(), Ops.call("regexp_match", text, lit(pattern)));
    }

    /** {@code regexp_matches(text, 'pattern', 'flags')} – set-returning. */
    public static ArrayField<String> regexpMatches(Field<String> text, String pattern, String flags) {
        return Fields.array(SqlTypes.TEXT.array(), Ops.call("regexp_matches", text, lit(pattern), lit(flags)));
    }

    /** {@code regexp_split_to_array(text, 'pattern')} */
    public static ArrayField<String> regexpSplitToArray(Field<String> text, String pattern) {
        return Fields.array(SqlTypes.TEXT.array(), Ops.call("regexp_split_to_array", text, lit(pattern)));
    }

    /** {@code regexp_split_to_table(text, 'pattern')} – set-returning. */
    public static StringField regexpSplitToTable(Field<String> text, String pattern) {
        return Fields.string(Ops.call("regexp_split_to_table", text, lit(pattern)));
    }

    /** {@code regexp_count(text, 'pattern')} (PostgreSQL 15+) */
    public static NumberField<Integer> regexpCount(Field<String> text, String pattern) {
        return Fields.number(SqlTypes.INT4, Ops.call("regexp_count", text, lit(pattern)));
    }

    /** {@code regexp_substr(text, 'pattern')} (PostgreSQL 15+) */
    public static StringField regexpSubstr(Field<String> text, String pattern) {
        return Fields.string(Ops.call("regexp_substr", text, lit(pattern)));
    }

    /** {@code md5(text)} */
    public static StringField md5(Field<String> text) {
        return Fields.string(Ops.call("md5", text));
    }

    /** {@code sha256(bytes)} */
    public static Field<byte[]> sha256(Field<byte[]> bytes) {
        return Fields.of(SqlTypes.BYTEA, Ops.call("sha256", bytes));
    }

    /** {@code encode(bytes, 'format')} – {@code base64}, {@code hex} or {@code escape}. */
    public static StringField encode(Field<byte[]> bytes, String format) {
        return Fields.string(Ops.call("encode", bytes, lit(format)));
    }

    /** {@code decode(text, 'format')} */
    public static Field<byte[]> decode(Field<String> text, String format) {
        return Fields.of(SqlTypes.BYTEA, Ops.call("decode", text, lit(format)));
    }

    /** {@code convert_to(text, 'encoding')} */
    public static Field<byte[]> convertTo(Field<String> text, String encoding) {
        return Fields.of(SqlTypes.BYTEA, Ops.call("convert_to", text, lit(encoding)));
    }

    /** {@code quote_ident(text)} */
    public static StringField quoteIdent(Field<String> text) {
        return Fields.string(Ops.call("quote_ident", text));
    }

    /** {@code quote_literal(text)} */
    public static StringField quoteLiteral(Field<String> text) {
        return Fields.string(Ops.call("quote_literal", text));
    }

    /** {@code quote_nullable(text)} */
    public static StringField quoteNullable(Field<String> text) {
        return Fields.string(Ops.call("quote_nullable", text));
    }

    /** {@code to_hex(number)} */
    public static StringField toHex(Field<? extends Number> number) {
        return Fields.string(Ops.call("to_hex", number));
    }

    /** {@code ascii(text)} – the code of the first character. */
    public static NumberField<Integer> ascii(Field<String> text) {
        return Fields.number(SqlTypes.INT4, Ops.call("ascii", text));
    }

    /** {@code chr(code)} */
    public static StringField chr(Field<Integer> code) {
        return Fields.string(Ops.call("chr", code));
    }

    // =========================================================================
    // Mathematics
    // =========================================================================

    /** {@code abs(x)} */
    public static <N extends Number> NumberField<N> abs(NumberField<N> x) {
        return Fields.number(x.type(), Ops.call("abs", x));
    }

    /** {@code ceil(x)} */
    public static <N extends Number> NumberField<N> ceil(NumberField<N> x) {
        return Fields.number(x.type(), Ops.call("ceil", x));
    }

    /** {@code floor(x)} */
    public static <N extends Number> NumberField<N> floor(NumberField<N> x) {
        return Fields.number(x.type(), Ops.call("floor", x));
    }

    /** {@code round(x)} */
    public static <N extends Number> NumberField<N> round(NumberField<N> x) {
        return Fields.number(x.type(), Ops.call("round", x));
    }

    /** {@code round(x::numeric, decimals)} */
    public static NumberField<BigDecimal> round(Field<? extends Number> x, int decimals) {
        return Fields.number(SqlTypes.NUMERIC, Ops.call("round", Ops.cast(x, SqlTypes.NUMERIC), lit(decimals)));
    }

    /** {@code trunc(x)} */
    public static <N extends Number> NumberField<N> trunc(NumberField<N> x) {
        return Fields.number(x.type(), Ops.call("trunc", x));
    }

    /** {@code trunc(x::numeric, decimals)} */
    public static NumberField<BigDecimal> trunc(Field<? extends Number> x, int decimals) {
        return Fields.number(SqlTypes.NUMERIC, Ops.call("trunc", Ops.cast(x, SqlTypes.NUMERIC), lit(decimals)));
    }

    /** {@code mod(a, b)} */
    public static <N extends Number> NumberField<N> mod(NumberField<N> a, Field<? extends Number> b) {
        return Fields.number(a.type(), Ops.call("mod", a, b));
    }

    /** {@code div(a, b)} – integer quotient. */
    public static NumberField<BigDecimal> div(Field<? extends Number> a, Field<? extends Number> b) {
        return Fields.number(SqlTypes.NUMERIC, Ops.call("div", Ops.cast(a, SqlTypes.NUMERIC), Ops.cast(b, SqlTypes.NUMERIC)));
    }

    /** {@code power(base, exponent)} */
    public static NumberField<Double> power(Field<? extends Number> base, Field<? extends Number> exponent) {
        return float8("power", base, exponent);
    }

    /** {@code sqrt(x)} */
    public static NumberField<Double> sqrt(Field<? extends Number> x) {
        return float8("sqrt", x);
    }

    /** {@code cbrt(x)} */
    public static NumberField<Double> cbrt(Field<? extends Number> x) {
        return float8("cbrt", x);
    }

    /** {@code exp(x)} */
    public static NumberField<Double> exp(Field<? extends Number> x) {
        return float8("exp", x);
    }

    /** {@code ln(x)} */
    public static NumberField<Double> ln(Field<? extends Number> x) {
        return float8("ln", x);
    }

    /** {@code log10(x)} */
    public static NumberField<Double> log10(Field<? extends Number> x) {
        return float8("log10", x);
    }

    /** {@code log(base, x)} */
    public static NumberField<BigDecimal> log(Field<? extends Number> base, Field<? extends Number> x) {
        return Fields.number(SqlTypes.NUMERIC, Ops.call("log", Ops.cast(base, SqlTypes.NUMERIC), Ops.cast(x, SqlTypes.NUMERIC)));
    }

    /** {@code sign(x)} */
    public static <N extends Number> NumberField<N> sign(NumberField<N> x) {
        return Fields.number(x.type(), Ops.call("sign", x));
    }

    /** {@code pi()} */
    public static NumberField<Double> pi() {
        return float8("pi");
    }

    /** {@code random()} – a value in [0, 1). */
    public static NumberField<Double> random() {
        return float8("random");
    }

    /** {@code degrees(radians)} */
    public static NumberField<Double> degrees(Field<? extends Number> radians) {
        return float8("degrees", radians);
    }

    /** {@code radians(degrees)} */
    public static NumberField<Double> radians(Field<? extends Number> degrees) {
        return float8("radians", degrees);
    }

    /** {@code sin(x)} */
    public static NumberField<Double> sin(Field<? extends Number> x) {
        return float8("sin", x);
    }

    /** {@code cos(x)} */
    public static NumberField<Double> cos(Field<? extends Number> x) {
        return float8("cos", x);
    }

    /** {@code tan(x)} */
    public static NumberField<Double> tan(Field<? extends Number> x) {
        return float8("tan", x);
    }

    /** {@code asin(x)} */
    public static NumberField<Double> asin(Field<? extends Number> x) {
        return float8("asin", x);
    }

    /** {@code acos(x)} */
    public static NumberField<Double> acos(Field<? extends Number> x) {
        return float8("acos", x);
    }

    /** {@code atan(x)} */
    public static NumberField<Double> atan(Field<? extends Number> x) {
        return float8("atan", x);
    }

    /** {@code atan2(y, x)} */
    public static NumberField<Double> atan2(Field<? extends Number> y, Field<? extends Number> x) {
        return float8("atan2", y, x);
    }

    /** {@code gcd(a, b)} */
    public static <N extends Number> NumberField<N> gcd(NumberField<N> a, NumberField<N> b) {
        return Fields.number(a.type(), Ops.call("gcd", a, b));
    }

    /** {@code lcm(a, b)} */
    public static <N extends Number> NumberField<N> lcm(NumberField<N> a, NumberField<N> b) {
        return Fields.number(a.type(), Ops.call("lcm", a, b));
    }

    /** {@code width_bucket(x, low, high, buckets)} – the histogram bucket of {@code x}. */
    public static NumberField<Integer> widthBucket(Field<? extends Number> x, Field<? extends Number> low,
                                                   Field<? extends Number> high, int buckets) {
        return Fields.number(SqlTypes.INT4, Ops.call("width_bucket", x, low, high, lit(buckets)));
    }

    private static NumberField<Double> float8(String name, QueryPart... args) {
        return Fields.number(SqlTypes.FLOAT8, Ops.call(name, args));
    }

    // =========================================================================
    // Date and time
    // =========================================================================

    /** {@code now()} – the start of the current transaction. */
    public static TemporalField<Instant> now() {
        return Fields.temporal(SqlTypes.TIMESTAMPTZ, Ops.call("now"));
    }

    /** {@code clock_timestamp()} – the actual current time. */
    public static TemporalField<Instant> clockTimestamp() {
        return Fields.temporal(SqlTypes.TIMESTAMPTZ, Ops.call("clock_timestamp"));
    }

    /** {@code statement_timestamp()} */
    public static TemporalField<Instant> statementTimestamp() {
        return Fields.temporal(SqlTypes.TIMESTAMPTZ, Ops.call("statement_timestamp"));
    }

    /** {@code CURRENT_DATE} (in the session time zone) */
    public static TemporalField<LocalDate> currentDate() {
        return Fields.temporal(SqlTypes.DATE, ctx -> ctx.append("CURRENT_DATE"));
    }

    /** {@code LOCALTIME} */
    public static TemporalField<LocalTime> localTime() {
        return Fields.temporal(SqlTypes.TIME, ctx -> ctx.append("LOCALTIME"));
    }

    /** {@code LOCALTIMESTAMP} */
    public static TemporalField<LocalDateTime> localTimestamp() {
        return Fields.temporal(SqlTypes.TIMESTAMP, ctx -> ctx.append("LOCALTIMESTAMP"));
    }

    /** {@code CURRENT_TIMESTAMP} */
    public static TemporalField<Instant> currentTimestamp() {
        return Fields.temporal(SqlTypes.TIMESTAMPTZ, ctx -> ctx.append("CURRENT_TIMESTAMP"));
    }

    /** {@code CAST(date_trunc('part', value) AS type)} */
    public static <T> TemporalField<T> dateTrunc(DatePart part, TemporalField<T> value) {
        return value.truncate(part);
    }

    /** {@code EXTRACT(part FROM value)} */
    public static NumberField<BigDecimal> extract(DatePart part, TemporalField<?> value) {
        return value.extract(part);
    }

    /** {@code date_part('part', value)} */
    public static NumberField<Double> datePart(DatePart part, TemporalField<?> value) {
        return Fields.number(SqlTypes.FLOAT8, Ops.call("date_part", lit(part.sqlName()), value));
    }

    /** {@code date_bin('stride', value, origin)} (PostgreSQL 14+) – e.g. 15-minute buckets. */
    public static <T> TemporalField<T> dateBin(Duration stride, TemporalField<T> value, T origin) {
        return Fields.temporal(value.type(), Ops.call("date_bin", intervalLiteral(stride), value, param(origin, value.type())));
    }

    /** {@code age(end, start)} as text, because it has months and years. */
    public static StringField age(TemporalField<?> end, TemporalField<?> start) {
        return Fields.string(SqlTypes.INTERVAL_TEXT, Ops.call("age", end, start));
    }

    /** An interval bind parameter. */
    public static Field<Duration> interval(Duration duration) {
        return param(duration, SqlTypes.INTERVAL);
    }

    /** {@code make_date(year, month, day)} */
    public static TemporalField<LocalDate> makeDate(Field<Integer> year, Field<Integer> month, Field<Integer> day) {
        return Fields.temporal(SqlTypes.DATE, Ops.call("make_date", year, month, day));
    }

    /** {@code make_time(hour, minute, second)} */
    public static TemporalField<LocalTime> makeTime(Field<Integer> hour, Field<Integer> minute, Field<Double> second) {
        return Fields.temporal(SqlTypes.TIME, Ops.call("make_time", hour, minute, second));
    }

    /** {@code make_timestamp(year, month, day, hour, minute, second)} */
    public static TemporalField<LocalDateTime> makeTimestamp(Field<Integer> year, Field<Integer> month, Field<Integer> day,
                                                             Field<Integer> hour, Field<Integer> minute, Field<Double> second) {
        return Fields.temporal(SqlTypes.TIMESTAMP, Ops.call("make_timestamp", year, month, day, hour, minute, second));
    }

    /** {@code to_char(value, 'format')}, e.g. {@code "YYYY-MM-DD"} or {@code "FM999G990D00"}. */
    public static StringField toChar(Field<?> value, String format) {
        return Fields.string(Ops.call("to_char", value, lit(format)));
    }

    /** {@code to_date(text, 'format')} */
    public static TemporalField<LocalDate> toDate(Field<String> text, String format) {
        return Fields.temporal(SqlTypes.DATE, Ops.call("to_date", text, lit(format)));
    }

    /** {@code to_timestamp(text, 'format')} */
    public static TemporalField<Instant> toTimestamp(Field<String> text, String format) {
        return Fields.temporal(SqlTypes.TIMESTAMPTZ, Ops.call("to_timestamp", text, lit(format)));
    }

    /** {@code to_timestamp(epochSeconds)} */
    public static TemporalField<Instant> toTimestamp(NumberField<?> epochSeconds) {
        return Fields.temporal(SqlTypes.TIMESTAMPTZ, Ops.call("to_timestamp", epochSeconds));
    }

    /** {@code to_number(text, 'format')} */
    public static NumberField<BigDecimal> toNumber(Field<String> text, String format) {
        return Fields.number(SqlTypes.NUMERIC, Ops.call("to_number", text, lit(format)));
    }

    /** {@code (instant AT TIME ZONE 'zone')} – the local date-time in a zone. */
    public static TemporalField<LocalDateTime> localDateTimeAt(Field<Instant> instant, ZoneId zone) {
        return Fields.temporal(SqlTypes.TIMESTAMP, ctx -> ctx.append('(').visit(instant).append(" AT TIME ZONE ")
                .append(Literals.quote(zone.getId())).append(')'));
    }

    /** {@code (local AT TIME ZONE 'zone')} – the instant of a local date-time in a zone. */
    public static TemporalField<Instant> instantAt(Field<LocalDateTime> local, ZoneId zone) {
        return Fields.temporal(SqlTypes.TIMESTAMPTZ, ctx -> ctx.append('(').visit(local).append(" AT TIME ZONE ")
                .append(Literals.quote(zone.getId())).append(')'));
    }

    /** {@code generate_series(start, stop)} – set-returning, use with {@code tableOf(..)}. */
    public static NumberField<Integer> generateSeries(int start, int stop) {
        return Fields.number(SqlTypes.INT4, Ops.call("generate_series", lit(start), lit(stop)));
    }

    /**
     * {@code generate_series(start, stop, step)} for {@code timestamptz} or
     * {@code timestamp} bounds; set-returning, use with {@code tableOf(..)}.
     *
     * @throws IllegalArgumentException for {@code date} bounds, for which PostgreSQL returns timestamps;
     *                                  convert them first
     */
    public static <T> TemporalField<T> generateSeries(TemporalField<T> start, TemporalField<T> stop, Duration step) {
        if (start.type().javaType() == LocalDate.class) {
            throw new IllegalArgumentException("generate_series over dates returns timestamps; use timestamp bounds");
        }
        return Fields.temporal(start.type(), Ops.call("generate_series", start, stop, intervalLiteral(step)));
    }

    private static QueryPart intervalLiteral(Duration d) {
        return ctx -> ctx.append("CAST(").append(Literals.quote(d.toString())).append(" AS interval)");
    }

    // =========================================================================
    // Ranges
    // =========================================================================

    /** {@code daterange(lower, upper, '[)')} */
    public static Field<String> daterange(Field<LocalDate> lower, Field<LocalDate> upper) {
        return range("daterange", SqlTypes.DATERANGE, lower, upper, "[)");
    }

    /** {@code daterange(lower, upper, 'bounds')} with bounds such as {@code "[]"}. */
    public static Field<String> daterange(Field<LocalDate> lower, Field<LocalDate> upper, String bounds) {
        return range("daterange", SqlTypes.DATERANGE, lower, upper, bounds);
    }

    /** {@code tstzrange(lower, upper, '[)')} */
    public static Field<String> tstzrange(Field<Instant> lower, Field<Instant> upper) {
        return range("tstzrange", SqlTypes.TSTZRANGE, lower, upper, "[)");
    }

    /** {@code tsrange(lower, upper, '[)')} */
    public static Field<String> tsrange(Field<LocalDateTime> lower, Field<LocalDateTime> upper) {
        return range("tsrange", SqlTypes.TSRANGE, lower, upper, "[)");
    }

    /** {@code int4range(lower, upper, '[)')} */
    public static Field<String> int4range(Field<Integer> lower, Field<Integer> upper) {
        return range("int4range", SqlTypes.INT4RANGE, lower, upper, "[)");
    }

    /** {@code int8range(lower, upper, '[)')} */
    public static Field<String> int8range(Field<Long> lower, Field<Long> upper) {
        return range("int8range", SqlTypes.INT8RANGE, lower, upper, "[)");
    }

    /** {@code numrange(lower, upper, '[)')} */
    public static Field<String> numrange(Field<BigDecimal> lower, Field<BigDecimal> upper) {
        return range("numrange", SqlTypes.NUMRANGE, lower, upper, "[)");
    }

    /** {@code isempty(range)} */
    public static Condition isEmpty(Field<String> range) {
        return Fields.condition(Ops.call("isempty", range), true);
    }

    private static Field<String> range(String name, DataType<String> type, Field<?> lower, Field<?> upper, String bounds) {
        if (!bounds.matches("[\\[(][\\])]")) throw new IllegalArgumentException("invalid range bounds: " + bounds);
        return Fields.of(type, Ops.call(name, lower, upper, lit(bounds)));
    }

    // =========================================================================
    // JSON
    // =========================================================================

    /**
     * A key/value pair for {@link #jsonbBuildObject(JsonPair...)}.
     *
     * @param key   the key (a constant)
     * @param value the value expression
     */
    public record JsonPair(String key, Field<?> value) {
    }

    /** A pair for {@code jsonbBuildObject(..)}. */
    public static JsonPair pair(String key, Field<?> value) {
        return new JsonPair(key, AbstractDml.require(value));
    }

    /** {@code CAST(text AS jsonb)} */
    public static JsonField<String> jsonb(Field<String> text) {
        return Fields.json(SqlTypes.JSONB, Ops.cast(text, SqlTypes.JSONB));
    }

    /** {@code jsonb_build_object('k1', v1, 'k2', v2, ...)} */
    public static JsonField<String> jsonbBuildObject(JsonPair... pairs) {
        return Fields.json(SqlTypes.JSONB, Ops.call("jsonb_build_object", pairs(pairs)));
    }

    /** {@code json_build_object('k1', v1, ...)} */
    public static JsonField<String> jsonBuildObject(JsonPair... pairs) {
        return Fields.json(SqlTypes.JSON, Ops.call("json_build_object", pairs(pairs)));
    }

    /** {@code jsonb_build_array(values...)} */
    public static JsonField<String> jsonbBuildArray(Field<?>... values) {
        return Fields.json(SqlTypes.JSONB, Ops.call("jsonb_build_array", values));
    }

    /** {@code json_build_array(values...)} */
    public static JsonField<String> jsonBuildArray(Field<?>... values) {
        return Fields.json(SqlTypes.JSON, Ops.call("json_build_array", values));
    }

    /** {@code to_json(value)} */
    public static JsonField<String> toJson(Field<?> value) {
        return Fields.json(SqlTypes.JSON, Ops.call("to_json", value));
    }

    /** {@code to_jsonb(value)} */
    public static JsonField<String> toJsonb(Field<?> value) {
        return Fields.json(SqlTypes.JSONB, Ops.call("to_jsonb", value));
    }

    /** {@code row_to_json(t)} – a whole row of a table in the query as JSON. */
    public static JsonField<String> rowToJson(Table<?> table) {
        return Fields.json(SqlTypes.JSON, ctx -> ctx.append("row_to_json(").identifier(table.qualifier()).append(')'));
    }

    /** {@code jsonb_set(target, '{path}', value, createMissing)} */
    public static <T> JsonField<T> jsonbSet(JsonField<T> target, String[] path, Field<?> value, boolean createMissing) {
        return Fields.json(target.type(), Ops.call("jsonb_set", target, lit(Ops.textArray(path)), value, lit(createMissing)));
    }

    /** {@code jsonb_insert(target, '{path}', value)} */
    public static <T> JsonField<T> jsonbInsert(JsonField<T> target, String[] path, Field<?> value) {
        return Fields.json(target.type(), Ops.call("jsonb_insert", target, lit(Ops.textArray(path)), value));
    }

    /** {@code jsonb_typeof(json)} */
    public static StringField jsonbTypeof(JsonField<?> json) {
        return Fields.string(Ops.call("jsonb_typeof", json));
    }

    /** {@code jsonb_array_length(json)} */
    public static NumberField<Integer> jsonbArrayLength(JsonField<?> json) {
        return Fields.number(SqlTypes.INT4, Ops.call("jsonb_array_length", json));
    }

    /** {@code jsonb_array_elements(json)} – set-returning. */
    public static JsonField<String> jsonbArrayElements(JsonField<?> json) {
        return Fields.json(SqlTypes.JSONB, Ops.call("jsonb_array_elements", json));
    }

    /** {@code jsonb_array_elements_text(json)} – set-returning. */
    public static StringField jsonbArrayElementsText(JsonField<?> json) {
        return Fields.string(Ops.call("jsonb_array_elements_text", json));
    }

    /** {@code jsonb_object_keys(json)} – set-returning. */
    public static StringField jsonbObjectKeys(JsonField<?> json) {
        return Fields.string(Ops.call("jsonb_object_keys", json));
    }

    /** {@code jsonb_strip_nulls(json)} */
    public static <T> JsonField<T> jsonbStripNulls(JsonField<T> json) {
        return Fields.json(json.type(), Ops.call("jsonb_strip_nulls", json));
    }

    /** {@code jsonb_pretty(json)} */
    public static StringField jsonbPretty(JsonField<?> json) {
        return Fields.string(Ops.call("jsonb_pretty", json));
    }

    /** {@code jsonb_path_query(json, 'path')} – set-returning. */
    public static JsonField<String> jsonbPathQuery(JsonField<?> json, String jsonPath) {
        return Fields.json(SqlTypes.JSONB, Ops.call("jsonb_path_query", json, jsonPath(jsonPath)));
    }

    /** {@code jsonb_path_query_first(json, 'path')} */
    public static JsonField<String> jsonbPathQueryFirst(JsonField<?> json, String jsonPath) {
        return Fields.json(SqlTypes.JSONB, Ops.call("jsonb_path_query_first", json, jsonPath(jsonPath)));
    }

    /** {@code jsonb_path_query_array(json, 'path')} */
    public static JsonField<String> jsonbPathQueryArray(JsonField<?> json, String jsonPath) {
        return Fields.json(SqlTypes.JSONB, Ops.call("jsonb_path_query_array", json, jsonPath(jsonPath)));
    }

    private static QueryPart[] pairs(JsonPair[] pairs) {
        List<QueryPart> parts = new ArrayList<>();
        for (JsonPair p : pairs) {
            parts.add(lit(p.key()));
            parts.add(p.value());
        }
        return parts.toArray(new QueryPart[0]);
    }

    private static QueryPart jsonPath(String path) {
        return ctx -> ctx.append(Literals.quote(path)).append("::jsonpath");
    }

    // =========================================================================
    // Arrays
    // =========================================================================

    /** {@code ARRAY[a, b, c]} */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> ArrayField<T> array(Field<T> first, Field<T>... more) {
        QueryPart[] all = prepend(first, more);
        List<QueryPart> list = List.of(all);
        return Fields.array(first.type().array(), ctx -> ctx.append("ARRAY[").visitAll(list, ", ").append(']'));
    }

    /** {@code ARRAY(SELECT ...)} */
    public static <T> ArrayField<T> arrayOf(Select1<T> query) {
        return Fields.array(arrayType(query), ctx -> ctx.append("ARRAY").visit(query));
    }

    @SuppressWarnings("unchecked")
    private static <T> DataType<T[]> arrayType(Select1<T> query) {
        return ((DataType<T>) query.fields().get(0).type()).array();
    }

    /** {@code array_length(array, dimension)} */
    public static NumberField<Integer> arrayLength(ArrayField<?> array, int dimension) {
        return Fields.number(SqlTypes.INT4, Ops.call("array_length", array, lit(dimension)));
    }

    /** {@code cardinality(array)} */
    public static NumberField<Integer> cardinality(ArrayField<?> array) {
        return array.length();
    }

    /** {@code array_prepend(?, array)} */
    public static <E> ArrayField<E> arrayPrepend(E element, ArrayField<E> array) {
        return Fields.array(array.type(), Ops.call("array_prepend", param(element, array.elementType()), array));
    }

    /** {@code array_cat(a, b)} */
    public static <E> ArrayField<E> arrayCat(ArrayField<E> a, Field<E[]> b) {
        return Fields.array(a.type(), Ops.call("array_cat", a, b));
    }

    /** {@code array_replace(array, ?, ?)} */
    public static <E> ArrayField<E> arrayReplace(ArrayField<E> array, E from, E to) {
        return Fields.array(array.type(), Ops.call("array_replace", array, param(from, array.elementType()),
                param(to, array.elementType())));
    }

    /** {@code array_position(array, ?)} – 1-based, {@code NULL} if absent. */
    public static <E> NumberField<Integer> arrayPosition(ArrayField<E> array, E element) {
        return Fields.number(SqlTypes.INT4, Ops.call("array_position", array, param(element, array.elementType())));
    }

    /** {@code array_positions(array, ?)} */
    public static <E> ArrayField<Integer> arrayPositions(ArrayField<E> array, E element) {
        return Fields.array(SqlTypes.INT4.array(), Ops.call("array_positions", array, param(element, array.elementType())));
    }

    /** {@code array_to_string(array, 'separator')} */
    public static StringField arrayToString(ArrayField<?> array, String separator) {
        return Fields.string(Ops.call("array_to_string", array, lit(separator)));
    }

    /** {@code array_lower(array, dimension)} */
    public static NumberField<Integer> arrayLower(ArrayField<?> array, int dimension) {
        return Fields.number(SqlTypes.INT4, Ops.call("array_lower", array, lit(dimension)));
    }

    /** {@code array_upper(array, dimension)} */
    public static NumberField<Integer> arrayUpper(ArrayField<?> array, int dimension) {
        return Fields.number(SqlTypes.INT4, Ops.call("array_upper", array, lit(dimension)));
    }

    /** {@code unnest(array)} – set-returning. */
    public static <E> Field<E> unnest(ArrayField<E> array) {
        return Fields.of(array.elementType(), Ops.call("unnest", array));
    }

    // =========================================================================
    // Full-text search
    // =========================================================================

    /** {@code to_tsvector('config', document)} */
    public static Field<String> toTsvector(String config, Field<String> document) {
        return Fields.of(SqlTypes.TSVECTOR, Ops.call("to_tsvector", regconfig(config), document));
    }

    /** {@code to_tsvector(document)} with the default configuration. */
    public static Field<String> toTsvector(Field<String> document) {
        return Fields.of(SqlTypes.TSVECTOR, Ops.call("to_tsvector", document));
    }

    /** {@code to_tsquery('config', ?)} */
    public static Field<String> toTsquery(String config, String query) {
        return tsquery("to_tsquery", config, param(query));
    }

    /** {@code plainto_tsquery('config', ?)} – all words must match. */
    public static Field<String> plaintoTsquery(String config, String text) {
        return tsquery("plainto_tsquery", config, param(text));
    }

    /** {@code phraseto_tsquery('config', ?)} – the words must appear in order. */
    public static Field<String> phrasetoTsquery(String config, String text) {
        return tsquery("phraseto_tsquery", config, param(text));
    }

    /** {@code websearch_to_tsquery('config', ?)} – search-engine syntax for user input. */
    public static Field<String> websearchToTsquery(String config, String text) {
        return tsquery("websearch_to_tsquery", config, param(text));
    }

    /** {@code websearch_to_tsquery('config', text)} with an expression. */
    public static Field<String> websearchToTsquery(String config, Field<String> text) {
        return tsquery("websearch_to_tsquery", config, text);
    }

    /** {@code ts_rank(vector, query)} */
    public static NumberField<Float> tsRank(Field<String> vector, Field<String> query) {
        return Fields.number(SqlTypes.FLOAT4, Ops.call("ts_rank", vector, query));
    }

    /** {@code ts_rank_cd(vector, query)} – cover density ranking. */
    public static NumberField<Float> tsRankCd(Field<String> vector, Field<String> query) {
        return Fields.number(SqlTypes.FLOAT4, Ops.call("ts_rank_cd", vector, query));
    }

    /** {@code ts_headline('config', document, query)} – a highlighted excerpt. */
    public static StringField tsHeadline(String config, Field<String> document, Field<String> query) {
        return Fields.string(Ops.call("ts_headline", regconfig(config), document, query));
    }

    /** {@code setweight(vector, 'A'|'B'|'C'|'D')} */
    public static Field<String> setweight(Field<String> vector, char weight) {
        if ("ABCD".indexOf(weight) < 0) throw new IllegalArgumentException("weight must be A, B, C or D");
        return Fields.of(SqlTypes.TSVECTOR, Ops.call("setweight", vector, lit(String.valueOf(weight))));
    }

    private static Field<String> tsquery(String name, String config, QueryPart text) {
        return Fields.of(SqlTypes.TSQUERY, Ops.call(name, regconfig(config), text));
    }

    private static QueryPart regconfig(String config) {
        return ctx -> ctx.append(Literals.quote(config)).append("::regconfig");
    }

    // =========================================================================
    // Sequences, system information
    // =========================================================================

    /** {@code gen_random_uuid()} (version 4) */
    public static Field<UUID> genRandomUuid() {
        return Fields.of(SqlTypes.UUID, Ops.call("gen_random_uuid"));
    }

    /** {@code nextval('sequence')} */
    public static NumberField<Long> nextval(String sequence) {
        return Fields.number(SqlTypes.INT8, Ops.call("nextval", lit(sequence)));
    }

    /** {@code currval('sequence')} */
    public static NumberField<Long> currval(String sequence) {
        return Fields.number(SqlTypes.INT8, Ops.call("currval", lit(sequence)));
    }

    /** {@code setval('sequence', ?)} */
    public static NumberField<Long> setval(String sequence, long value) {
        return Fields.number(SqlTypes.INT8, Ops.call("setval", lit(sequence), param(value)));
    }

    /** {@code lastval()} */
    public static NumberField<Long> lastval() {
        return Fields.number(SqlTypes.INT8, Ops.call("lastval"));
    }

    /** {@code CURRENT_USER} */
    public static StringField currentUser() {
        return Fields.string(ctx -> ctx.append("CURRENT_USER"));
    }

    /** {@code SESSION_USER} */
    public static StringField sessionUser() {
        return Fields.string(ctx -> ctx.append("SESSION_USER"));
    }

    /** {@code current_schema()} */
    public static StringField currentSchema() {
        return Fields.string(Ops.call("current_schema"));
    }

    /** {@code current_database()} */
    public static StringField currentDatabase() {
        return Fields.string(Ops.call("current_database"));
    }

    /** {@code version()} */
    public static StringField version() {
        return Fields.string(Ops.call("version"));
    }

    /** {@code pg_typeof(value)::text} – handy for debugging. */
    public static StringField pgTypeof(Field<?> value) {
        return Fields.string(Ops.cast(Ops.call("pg_typeof", value), SqlTypes.TEXT));
    }

    // =========================================================================
    // GROUP BY elements
    // =========================================================================

    /** {@code ROLLUP (fields)} */
    public static GroupingElement rollup(Field<?>... fields) {
        List<Field<?>> list = List.of(fields);
        return ctx -> ctx.append("ROLLUP (").visitAll(list, ", ").append(')');
    }

    /** {@code CUBE (fields)} */
    public static GroupingElement cube(Field<?>... fields) {
        List<Field<?>> list = List.of(fields);
        return ctx -> ctx.append("CUBE (").visitAll(list, ", ").append(')');
    }

    /** One set for {@link #groupingSets}: {@code (a, b)} or {@code ()}. */
    public static GroupingElement groupingSet(Field<?>... fields) {
        List<Field<?>> list = List.of(fields);
        return ctx -> ctx.append('(').visitAll(list, ", ").append(')');
    }

    /** {@code GROUPING SETS ((a, b), (a), ())} */
    public static GroupingElement groupingSets(GroupingElement... sets) {
        List<GroupingElement> list = List.of(sets);
        return ctx -> ctx.append("GROUPING SETS (").visitAll(list, ", ").append(')');
    }

    /** {@code GROUPING(fields)} – which fields are aggregated away in a grouping set. */
    public static NumberField<Integer> grouping(Field<?>... fields) {
        return Fields.number(SqlTypes.INT4, Ops.call("GROUPING", fields));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static QueryPart lit(String text) {
        return Ops.literal(text);
    }

    private static QueryPart lit(long number) {
        String s = Long.toString(number);
        return ctx -> ctx.append(s);
    }

    private static QueryPart lit(double number) {
        String s = Literals.number(number);
        return ctx -> ctx.append(s);
    }

    private static QueryPart lit(boolean value) {
        return ctx -> ctx.append(value ? "TRUE" : "FALSE");
    }

    private static QueryPart[] prepend(QueryPart first, QueryPart[] more) {
        QueryPart[] all = new QueryPart[more.length + 1];
        all[0] = first;
        System.arraycopy(more, 0, all, 1, more.length);
        return all;
    }
}
