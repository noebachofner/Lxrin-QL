package ch.lxrin.ql.function;

import ch.lxrin.ql.condition.Condition;
import ch.lxrin.ql.condition.Conditions;
import ch.lxrin.ql.expr.Expression;
import ch.lxrin.ql.expr.FunctionCall;
import ch.lxrin.ql.expr.Literal;
import ch.lxrin.ql.expr.Template;

import java.util.regex.Pattern;

/**
 * PostgreSQL functions and operators. All methods are also available through
 * {@code import static ch.lxrin.ql.LxrinQL.*}.
 *
 * <h2>Argument rules</h2>
 * <ul>
 *   <li>Parameters typed {@code Object} follow the operand rule: expressions
 *       are rendered, {@code String}s are SQL fragments and other values are
 *       bound as parameters.</li>
 *   <li>Parameters typed {@code String} or a Java primitive are
 *       <em>constants</em> (date fields, formats, separators, patterns,
 *       JSON keys, ...) and are written as escaped literals, e.g.
 *       {@code toChar(o.createdAt, "YYYY-MM-DD")} &rarr;
 *       {@code to_char(o.CREATED_AT, 'YYYY-MM-DD')}.</li>
 *   <li>Any function that is not listed here can be called with
 *       {@link #function(String, Object...)}.</li>
 * </ul>
 */
public class Functions extends Conditions {

    private static final Pattern FIELD = Pattern.compile("[A-Za-z_]+");

    /** Utility class; extended by the DSL entry point only. */
    protected Functions() {}

    // =========================================================================
    // Generic
    // =========================================================================

    /**
     * Calls any SQL function: {@code function("similarity", p.name, val(text))}
     * renders {@code similarity(p.NAME, :lq0)}. The result supports
     * {@code filter}, {@code orderBy}, {@code withinGroup} and {@code over}.
     */
    public static FunctionCall function(String name, Object... args) {
        return new FunctionCall(name, args);
    }

    /** Calls a function that returns a boolean and uses it as a condition. */
    public static Condition booleanFunction(String name, Object... args) {
        return condition(new FunctionCall(name, args));
    }

    // =========================================================================
    // Aggregate functions
    // =========================================================================

    /** {@code count(*)} */
    public static FunctionCall count() { return fn("count", asterisk()); }

    /** {@code count(expr)} – counts non-null values. */
    public static FunctionCall count(Object expr) { return fn("count", expr); }

    /** {@code count(DISTINCT expr)} */
    public static FunctionCall countDistinct(Object expr) { return fn("count", expr).distinct(); }

    /** {@code sum(expr)} */
    public static FunctionCall sum(Object expr) { return fn("sum", expr); }

    /** {@code avg(expr)} */
    public static FunctionCall avg(Object expr) { return fn("avg", expr); }

    /** {@code min(expr)} */
    public static FunctionCall min(Object expr) { return fn("min", expr); }

    /** {@code max(expr)} */
    public static FunctionCall max(Object expr) { return fn("max", expr); }

    /** {@code string_agg(expr, 'separator')} – add {@code .orderBy(..)} for a defined order. */
    public static FunctionCall stringAgg(Object expr, String separator) { return fn("string_agg", expr, lit(separator)); }

    /** {@code array_agg(expr)} */
    public static FunctionCall arrayAgg(Object expr) { return fn("array_agg", expr); }

    /** {@code json_agg(expr)} */
    public static FunctionCall jsonAgg(Object expr) { return fn("json_agg", expr); }

    /** {@code jsonb_agg(expr)} */
    public static FunctionCall jsonbAgg(Object expr) { return fn("jsonb_agg", expr); }

    /** {@code json_object_agg(key, value)} */
    public static FunctionCall jsonObjectAgg(Object key, Object value) { return fn("json_object_agg", key, value); }

    /** {@code jsonb_object_agg(key, value)} */
    public static FunctionCall jsonbObjectAgg(Object key, Object value) { return fn("jsonb_object_agg", key, value); }

    /** {@code bool_and(expr)} – true if all values are true. */
    public static FunctionCall boolAnd(Object expr) { return fn("bool_and", expr); }

    /** {@code bool_or(expr)} – true if any value is true. */
    public static FunctionCall boolOr(Object expr) { return fn("bool_or", expr); }

    /** {@code every(expr)} – SQL-standard {@code bool_and}. */
    public static FunctionCall every(Object expr) { return fn("every", expr); }

    /** {@code bit_and(expr)} */
    public static FunctionCall bitAnd(Object expr) { return fn("bit_and", expr); }

    /** {@code bit_or(expr)} */
    public static FunctionCall bitOr(Object expr) { return fn("bit_or", expr); }

    /** {@code any_value(expr)} – an arbitrary non-null value (PostgreSQL 16+). */
    public static FunctionCall anyValue(Object expr) { return fn("any_value", expr); }

    /** {@code stddev(expr)} */
    public static FunctionCall stddev(Object expr) { return fn("stddev", expr); }

    /** {@code stddev_pop(expr)} */
    public static FunctionCall stddevPop(Object expr) { return fn("stddev_pop", expr); }

    /** {@code stddev_samp(expr)} */
    public static FunctionCall stddevSamp(Object expr) { return fn("stddev_samp", expr); }

    /** {@code variance(expr)} */
    public static FunctionCall variance(Object expr) { return fn("variance", expr); }

    /** {@code var_pop(expr)} */
    public static FunctionCall varPop(Object expr) { return fn("var_pop", expr); }

    /** {@code var_samp(expr)} */
    public static FunctionCall varSamp(Object expr) { return fn("var_samp", expr); }

    /** {@code corr(y, x)} – correlation coefficient. */
    public static FunctionCall corr(Object y, Object x) { return fn("corr", y, x); }

    /** {@code covar_pop(y, x)} */
    public static FunctionCall covarPop(Object y, Object x) { return fn("covar_pop", y, x); }

    /** {@code covar_samp(y, x)} */
    public static FunctionCall covarSamp(Object y, Object x) { return fn("covar_samp", y, x); }

    /** {@code regr_slope(y, x)} */
    public static FunctionCall regrSlope(Object y, Object x) { return fn("regr_slope", y, x); }

    /** {@code regr_intercept(y, x)} */
    public static FunctionCall regrIntercept(Object y, Object x) { return fn("regr_intercept", y, x); }

    /** {@code percentile_cont(fraction)} – combine with {@code .withinGroup(expr.asc())}. */
    public static FunctionCall percentileCont(double fraction) { return fn("percentile_cont", lit(fraction)); }

    /** {@code percentile_disc(fraction)} – combine with {@code .withinGroup(expr.asc())}. */
    public static FunctionCall percentileDisc(double fraction) { return fn("percentile_disc", lit(fraction)); }

    /** {@code mode()} – most frequent value; combine with {@code .withinGroup(expr.asc())}. */
    public static FunctionCall mode() { return fn("mode"); }

    // ---------------------------------------------------------------- grouping

    /** {@code GROUPING(exprs)} – tells which columns are aggregated in a grouping set. */
    public static FunctionCall grouping(Object... exprs) { return fn("GROUPING", exprs); }

    /** {@code ROLLUP (exprs)} for {@code GROUP BY}. */
    public static Expression rollup(Object... exprs) { return keywordList("ROLLUP", exprs); }

    /** {@code CUBE (exprs)} for {@code GROUP BY}. */
    public static Expression cube(Object... exprs) { return keywordList("CUBE", exprs); }

    /**
     * {@code GROUPING SETS (...)} for {@code GROUP BY}; build each set with
     * {@link #groupingSet(Object...)}: {@code groupingSets(groupingSet(a, b), groupingSet(a), groupingSet())}.
     */
    public static Expression groupingSets(Object... sets) { return keywordList("GROUPING SETS", sets); }

    /** One set for {@link #groupingSets}: {@code (a, b)} or {@code ()}. */
    public static Expression groupingSet(Object... exprs) { return keywordList("", exprs); }

    // =========================================================================
    // Window functions (use with .over(..))
    // =========================================================================

    /** {@code row_number()} */
    public static FunctionCall rowNumber() { return fn("row_number"); }

    /** {@code rank()} */
    public static FunctionCall rank() { return fn("rank"); }

    /** {@code dense_rank()} */
    public static FunctionCall denseRank() { return fn("dense_rank"); }

    /** {@code percent_rank()} */
    public static FunctionCall percentRank() { return fn("percent_rank"); }

    /** {@code cume_dist()} */
    public static FunctionCall cumeDist() { return fn("cume_dist"); }

    /** {@code ntile(buckets)} */
    public static FunctionCall ntile(int buckets) { return fn("ntile", lit(buckets)); }

    /** {@code lag(expr)} – value of the previous row. */
    public static FunctionCall lag(Object expr) { return fn("lag", expr); }

    /** {@code lag(expr, offset)} */
    public static FunctionCall lag(Object expr, int offset) { return fn("lag", expr, lit(offset)); }

    /** {@code lag(expr, offset, default)} */
    public static FunctionCall lag(Object expr, int offset, Object defaultValue) { return fn("lag", expr, lit(offset), defaultValue); }

    /** {@code lead(expr)} – value of the next row. */
    public static FunctionCall lead(Object expr) { return fn("lead", expr); }

    /** {@code lead(expr, offset)} */
    public static FunctionCall lead(Object expr, int offset) { return fn("lead", expr, lit(offset)); }

    /** {@code lead(expr, offset, default)} */
    public static FunctionCall lead(Object expr, int offset, Object defaultValue) { return fn("lead", expr, lit(offset), defaultValue); }

    /** {@code first_value(expr)} */
    public static FunctionCall firstValue(Object expr) { return fn("first_value", expr); }

    /** {@code last_value(expr)} – usually needs a frame such as {@code rowsBetween(unboundedPreceding(), unboundedFollowing())}. */
    public static FunctionCall lastValue(Object expr) { return fn("last_value", expr); }

    /** {@code nth_value(expr, n)} */
    public static FunctionCall nthValue(Object expr, int n) { return fn("nth_value", expr, lit(n)); }

    // =========================================================================
    // Conditional expressions
    // =========================================================================

    /** {@code COALESCE(a, b, ...)} – first non-null argument. */
    public static FunctionCall coalesce(Object... values) { return fn("COALESCE", values); }

    /** {@code NULLIF(a, b)} – null if both are equal. */
    public static FunctionCall nullif(Object a, Object b) { return fn("NULLIF", a, b); }

    /** {@code GREATEST(a, b, ...)} */
    public static FunctionCall greatest(Object... values) { return fn("GREATEST", values); }

    /** {@code LEAST(a, b, ...)} */
    public static FunctionCall least(Object... values) { return fn("LEAST", values); }

    // =========================================================================
    // String functions
    // =========================================================================

    /** {@code length(text)} */
    public static FunctionCall length(Object text) { return fn("length", text); }

    /** {@code char_length(text)} */
    public static FunctionCall charLength(Object text) { return fn("char_length", text); }

    /** {@code octet_length(text)} – size in bytes. */
    public static FunctionCall octetLength(Object text) { return fn("octet_length", text); }

    /** {@code bit_length(text)} */
    public static FunctionCall bitLength(Object text) { return fn("bit_length", text); }

    /** {@code lower(text)} – also lower bound of a range. */
    public static FunctionCall lower(Object text) { return fn("lower", text); }

    /** {@code upper(text)} – also upper bound of a range. */
    public static FunctionCall upper(Object text) { return fn("upper", text); }

    /** {@code initcap(text)} – capitalises each word. */
    public static FunctionCall initcap(Object text) { return fn("initcap", text); }

    /** {@code concat(a, b, ...)} – null arguments are ignored. */
    public static FunctionCall concat(Object... values) { return fn("concat", values); }

    /** {@code concat_ws('separator', a, b, ...)} */
    public static FunctionCall concatWs(String separator, Object... values) {
        Object[] args = new Object[values.length + 1];
        args[0] = lit(separator);
        System.arraycopy(values, 0, args, 1, values.length);
        return fn("concat_ws", args);
    }

    /** {@code substring(text, from)} – 1-based. */
    public static FunctionCall substring(Object text, int from) { return fn("substring", text, lit(from)); }

    /** {@code substring(text, from, count)} – 1-based. */
    public static FunctionCall substring(Object text, int from, int count) { return fn("substring", text, lit(from), lit(count)); }

    /** {@code substring(text, from, count)} with expression arguments. */
    public static FunctionCall substring(Object text, Object from, Object count) { return fn("substring", text, from, count); }

    /** {@code substring(text FROM 'regex')} – first match of a POSIX regular expression. */
    public static Expression substringRegex(Object text, String regex) {
        return new Template("substring({0} FROM {1})", text, lit(regex));
    }

    /** {@code left(text, n)} */
    public static FunctionCall left(Object text, int n) { return fn("left", text, lit(n)); }

    /** {@code right(text, n)} */
    public static FunctionCall right(Object text, int n) { return fn("right", text, lit(n)); }

    /** {@code btrim(text)} – removes leading and trailing spaces. */
    public static FunctionCall trim(Object text) { return fn("btrim", text); }

    /** {@code btrim(text, 'characters')} */
    public static FunctionCall btrim(Object text, String characters) { return fn("btrim", text, lit(characters)); }

    /** {@code ltrim(text)} */
    public static FunctionCall ltrim(Object text) { return fn("ltrim", text); }

    /** {@code ltrim(text, 'characters')} */
    public static FunctionCall ltrim(Object text, String characters) { return fn("ltrim", text, lit(characters)); }

    /** {@code rtrim(text)} */
    public static FunctionCall rtrim(Object text) { return fn("rtrim", text); }

    /** {@code rtrim(text, 'characters')} */
    public static FunctionCall rtrim(Object text, String characters) { return fn("rtrim", text, lit(characters)); }

    /** {@code lpad(text, length, 'fill')} */
    public static FunctionCall lpad(Object text, int length, String fill) { return fn("lpad", text, lit(length), lit(fill)); }

    /** {@code rpad(text, length, 'fill')} */
    public static FunctionCall rpad(Object text, int length, String fill) { return fn("rpad", text, lit(length), lit(fill)); }

    /** {@code replace(text, 'from', 'to')} */
    public static FunctionCall replace(Object text, String from, String to) { return fn("replace", text, lit(from), lit(to)); }

    /** {@code replace(text, from, to)} with expression arguments. */
    public static FunctionCall replace(Object text, Object from, Object to) { return fn("replace", text, from, to); }

    /** {@code translate(text, 'from', 'to')} – character-wise replacement. */
    public static FunctionCall translate(Object text, String from, String to) { return fn("translate", text, lit(from), lit(to)); }

    /** {@code strpos(text, substring)} – 1-based position, 0 if not found. */
    public static FunctionCall strpos(Object text, Object substring) { return fn("strpos", text, substring); }

    /** {@code starts_with(text, prefix)} */
    public static Condition startsWith(Object text, Object prefix) { return condition(fn("starts_with", text, prefix)); }

    /** {@code reverse(text)} */
    public static FunctionCall reverse(Object text) { return fn("reverse", text); }

    /** {@code repeat(text, n)} */
    public static FunctionCall repeat(Object text, int n) { return fn("repeat", text, lit(n)); }

    /** {@code split_part(text, 'delimiter', n)} */
    public static FunctionCall splitPart(Object text, String delimiter, int n) { return fn("split_part", text, lit(delimiter), lit(n)); }

    /** {@code string_to_array(text, 'delimiter')} */
    public static FunctionCall stringToArray(Object text, String delimiter) { return fn("string_to_array", text, lit(delimiter)); }

    /** {@code format('format', args...)} – like {@code printf}; supports {@code %s}, {@code %I}, {@code %L}. */
    public static FunctionCall format(String format, Object... args) {
        Object[] all = new Object[args.length + 1];
        all[0] = lit(format);
        System.arraycopy(args, 0, all, 1, args.length);
        return fn("format", all);
    }

    /** {@code regexp_replace(text, 'pattern', 'replacement')} */
    public static FunctionCall regexpReplace(Object text, String pattern, String replacement) {
        return fn("regexp_replace", text, lit(pattern), lit(replacement));
    }

    /** {@code regexp_replace(text, 'pattern', 'replacement', 'flags')} – e.g. flags {@code "gi"}. */
    public static FunctionCall regexpReplace(Object text, String pattern, String replacement, String flags) {
        return fn("regexp_replace", text, lit(pattern), lit(replacement), lit(flags));
    }

    /** {@code regexp_match(text, 'pattern')} – text array of the first match's groups. */
    public static FunctionCall regexpMatch(Object text, String pattern) { return fn("regexp_match", text, lit(pattern)); }

    /** {@code regexp_matches(text, 'pattern', 'flags')} – set of matches (flag {@code g} for all). */
    public static FunctionCall regexpMatches(Object text, String pattern, String flags) {
        return fn("regexp_matches", text, lit(pattern), lit(flags));
    }

    /** {@code regexp_split_to_array(text, 'pattern')} */
    public static FunctionCall regexpSplitToArray(Object text, String pattern) { return fn("regexp_split_to_array", text, lit(pattern)); }

    /** {@code regexp_split_to_table(text, 'pattern')} */
    public static FunctionCall regexpSplitToTable(Object text, String pattern) { return fn("regexp_split_to_table", text, lit(pattern)); }

    /** {@code regexp_count(text, 'pattern')} (PostgreSQL 15+) */
    public static FunctionCall regexpCount(Object text, String pattern) { return fn("regexp_count", text, lit(pattern)); }

    /** {@code regexp_substr(text, 'pattern')} (PostgreSQL 15+) */
    public static FunctionCall regexpSubstr(Object text, String pattern) { return fn("regexp_substr", text, lit(pattern)); }

    /** {@code md5(text)} */
    public static FunctionCall md5(Object text) { return fn("md5", text); }

    /** {@code sha256(bytes)} */
    public static FunctionCall sha256(Object bytes) { return fn("sha256", bytes); }

    /** {@code encode(bytes, 'format')} – format {@code base64}, {@code hex} or {@code escape}. */
    public static FunctionCall encode(Object bytes, String format) { return fn("encode", bytes, lit(format)); }

    /** {@code decode(text, 'format')} */
    public static FunctionCall decode(Object text, String format) { return fn("decode", text, lit(format)); }

    /** {@code convert_to(text, 'encoding')} – text to bytes. */
    public static FunctionCall convertTo(Object text, String encoding) { return fn("convert_to", text, lit(encoding)); }

    /** {@code quote_ident(text)} */
    public static FunctionCall quoteIdent(Object text) { return fn("quote_ident", text); }

    /** {@code quote_literal(text)} */
    public static FunctionCall quoteLiteral(Object text) { return fn("quote_literal", text); }

    /** {@code quote_nullable(text)} */
    public static FunctionCall quoteNullable(Object text) { return fn("quote_nullable", text); }

    /** {@code to_hex(number)} */
    public static FunctionCall toHex(Object number) { return fn("to_hex", number); }

    /** {@code ascii(text)} – code of the first character. */
    public static FunctionCall ascii(Object text) { return fn("ascii", text); }

    /** {@code chr(code)} */
    public static FunctionCall chr(Object code) { return fn("chr", code); }

    // =========================================================================
    // Mathematical functions
    // =========================================================================

    /** {@code abs(x)} */
    public static FunctionCall abs(Object x) { return fn("abs", x); }

    /** {@code ceil(x)} */
    public static FunctionCall ceil(Object x) { return fn("ceil", x); }

    /** {@code floor(x)} */
    public static FunctionCall floor(Object x) { return fn("floor", x); }

    /** {@code round(x)} */
    public static FunctionCall round(Object x) { return fn("round", x); }

    /** {@code round(x, decimals)} – {@code x} must be {@code numeric}. */
    public static FunctionCall round(Object x, int decimals) { return fn("round", x, lit(decimals)); }

    /** {@code trunc(x)} */
    public static FunctionCall trunc(Object x) { return fn("trunc", x); }

    /** {@code trunc(x, decimals)} */
    public static FunctionCall trunc(Object x, int decimals) { return fn("trunc", x, lit(decimals)); }

    /** {@code mod(a, b)} */
    public static FunctionCall mod(Object a, Object b) { return fn("mod", a, b); }

    /** {@code div(a, b)} – integer quotient. */
    public static FunctionCall div(Object a, Object b) { return fn("div", a, b); }

    /** {@code power(base, exponent)} */
    public static FunctionCall power(Object base, Object exponent) { return fn("power", base, exponent); }

    /** {@code sqrt(x)} */
    public static FunctionCall sqrt(Object x) { return fn("sqrt", x); }

    /** {@code cbrt(x)} */
    public static FunctionCall cbrt(Object x) { return fn("cbrt", x); }

    /** {@code exp(x)} */
    public static FunctionCall exp(Object x) { return fn("exp", x); }

    /** {@code ln(x)} */
    public static FunctionCall ln(Object x) { return fn("ln", x); }

    /** {@code log10(x)} */
    public static FunctionCall log10(Object x) { return fn("log10", x); }

    /** {@code log(base, x)} */
    public static FunctionCall log(Object base, Object x) { return fn("log", base, x); }

    /** {@code sign(x)} */
    public static FunctionCall sign(Object x) { return fn("sign", x); }

    /** {@code pi()} */
    public static FunctionCall pi() { return fn("pi"); }

    /** {@code random()} – value in [0, 1). */
    public static FunctionCall random() { return fn("random"); }

    /** {@code degrees(radians)} */
    public static FunctionCall degrees(Object radians) { return fn("degrees", radians); }

    /** {@code radians(degrees)} */
    public static FunctionCall radians(Object degrees) { return fn("radians", degrees); }

    /** {@code sin(x)} */
    public static FunctionCall sin(Object x) { return fn("sin", x); }

    /** {@code cos(x)} */
    public static FunctionCall cos(Object x) { return fn("cos", x); }

    /** {@code tan(x)} */
    public static FunctionCall tan(Object x) { return fn("tan", x); }

    /** {@code asin(x)} */
    public static FunctionCall asin(Object x) { return fn("asin", x); }

    /** {@code acos(x)} */
    public static FunctionCall acos(Object x) { return fn("acos", x); }

    /** {@code atan(x)} */
    public static FunctionCall atan(Object x) { return fn("atan", x); }

    /** {@code atan2(y, x)} */
    public static FunctionCall atan2(Object y, Object x) { return fn("atan2", y, x); }

    /** {@code gcd(a, b)} */
    public static FunctionCall gcd(Object a, Object b) { return fn("gcd", a, b); }

    /** {@code lcm(a, b)} */
    public static FunctionCall lcm(Object a, Object b) { return fn("lcm", a, b); }

    /** {@code width_bucket(x, low, high, buckets)} – histogram bucket number. */
    public static FunctionCall widthBucket(Object x, Object low, Object high, int buckets) {
        return fn("width_bucket", x, low, high, lit(buckets));
    }

    // =========================================================================
    // Date and time
    // =========================================================================

    /** {@code now()} – start of the current transaction. */
    public static FunctionCall now() { return fn("now"); }

    /** {@code clock_timestamp()} – actual current time. */
    public static FunctionCall clockTimestamp() { return fn("clock_timestamp"); }

    /** {@code statement_timestamp()} */
    public static FunctionCall statementTimestamp() { return fn("statement_timestamp"); }

    /** {@code CURRENT_DATE} */
    public static Expression currentDate() { return raw("CURRENT_DATE"); }

    /** {@code CURRENT_TIME} */
    public static Expression currentTime() { return raw("CURRENT_TIME"); }

    /** {@code CURRENT_TIMESTAMP} */
    public static Expression currentTimestamp() { return raw("CURRENT_TIMESTAMP"); }

    /** {@code LOCALTIME} */
    public static Expression localTime() { return raw("LOCALTIME"); }

    /** {@code LOCALTIMESTAMP} */
    public static Expression localTimestamp() { return raw("LOCALTIMESTAMP"); }

    /** {@code date_trunc('field', source)} – field e.g. {@code "day"}, {@code "month"}, {@code "year"}. */
    public static FunctionCall dateTrunc(String field, Object source) { return fn("date_trunc", lit(field), source); }

    /** {@code date_trunc('field', source, 'time zone')} (PostgreSQL 12+) */
    public static FunctionCall dateTrunc(String field, Object source, String timeZone) {
        return fn("date_trunc", lit(field), source, lit(timeZone));
    }

    /** {@code date_part('field', source)} */
    public static FunctionCall datePart(String field, Object source) { return fn("date_part", lit(field), source); }

    /** {@code EXTRACT(field FROM source)} – field e.g. {@code "YEAR"}, {@code "DOW"}, {@code "EPOCH"}. */
    public static Expression extract(String field, Object source) {
        if (field == null || !FIELD.matcher(field).matches()) throw new IllegalArgumentException("invalid field: " + field);
        return new Template("EXTRACT(" + field + " FROM {0})", source);
    }

    /** {@code date_bin('stride', source, origin)} (PostgreSQL 14+) – e.g. stride {@code "15 minutes"}. */
    public static FunctionCall dateBin(String stride, Object source, Object origin) {
        return fn("date_bin", interval(stride), source, origin);
    }

    /** {@code age(timestamp)} – interval from the timestamp to today. */
    public static FunctionCall age(Object timestamp) { return fn("age", timestamp); }

    /** {@code age(end, start)} */
    public static FunctionCall age(Object end, Object start) { return fn("age", end, start); }

    /** {@code INTERVAL 'text'}, e.g. {@code interval("3 days")}, {@code interval("1 hour 30 minutes")}. */
    public static Expression interval(String text) {
        String literal = Literal.quote(text);
        return ctx -> ctx.append("INTERVAL ").append(literal);
    }

    /** {@code make_date(year, month, day)} */
    public static FunctionCall makeDate(Object year, Object month, Object day) { return fn("make_date", year, month, day); }

    /** {@code make_time(hour, minute, second)} */
    public static FunctionCall makeTime(Object hour, Object minute, Object second) { return fn("make_time", hour, minute, second); }

    /** {@code make_timestamp(year, month, day, hour, minute, second)} */
    public static FunctionCall makeTimestamp(Object year, Object month, Object day, Object hour, Object minute, Object second) {
        return fn("make_timestamp", year, month, day, hour, minute, second);
    }

    /** {@code make_interval(years, months, weeks, days, hours, mins, secs)} */
    public static FunctionCall makeInterval(Object years, Object months, Object weeks, Object days,
                                            Object hours, Object mins, Object secs) {
        return fn("make_interval", years, months, weeks, days, hours, mins, secs);
    }

    /** {@code to_char(value, 'format')} – e.g. {@code "YYYY-MM-DD HH24:MI"} or {@code "FM999G990D00"}. */
    public static FunctionCall toChar(Object value, String format) { return fn("to_char", value, lit(format)); }

    /** {@code to_date(text, 'format')} */
    public static FunctionCall toDate(Object text, String format) { return fn("to_date", text, lit(format)); }

    /** {@code to_timestamp(text, 'format')} */
    public static FunctionCall toTimestamp(Object text, String format) { return fn("to_timestamp", text, lit(format)); }

    /** {@code to_timestamp(epochSeconds)} */
    public static FunctionCall toTimestamp(Object epochSeconds) { return fn("to_timestamp", epochSeconds); }

    /** {@code to_number(text, 'format')} */
    public static FunctionCall toNumber(Object text, String format) { return fn("to_number", text, lit(format)); }

    /** {@code (source AT TIME ZONE 'zone')}, e.g. {@code atTimeZone(o.createdAt, "Europe/Zurich")}. */
    public static Expression atTimeZone(Object source, String zone) {
        return new Template("({0} AT TIME ZONE {1})", source, lit(zone));
    }

    /** {@code (source AT TIME ZONE zone)} with an expression as zone. */
    public static Expression atTimeZone(Object source, Object zone) {
        return new Template("({0} AT TIME ZONE {1})", source, zone);
    }

    /** {@code justify_days(interval)} */
    public static FunctionCall justifyDays(Object interval) { return fn("justify_days", interval); }

    /** {@code justify_hours(interval)} */
    public static FunctionCall justifyHours(Object interval) { return fn("justify_hours", interval); }

    /** {@code justify_interval(interval)} */
    public static FunctionCall justifyInterval(Object interval) { return fn("justify_interval", interval); }

    /** {@code generate_series(start, stop)} – numbers or, with a step, timestamps. Use in {@code FROM}. */
    public static FunctionCall generateSeries(Object start, Object stop) { return fn("generate_series", start, stop); }

    /** {@code generate_series(start, stop, step)}, e.g. {@code generateSeries(from, to, interval("1 day"))}. */
    public static FunctionCall generateSeries(Object start, Object stop, Object step) {
        return fn("generate_series", start, stop, step);
    }

    // =========================================================================
    // Ranges
    // =========================================================================

    /** {@code daterange(lower, upper)} – bounds {@code [)}. */
    public static FunctionCall daterange(Object lower, Object upper) { return fn("daterange", lower, upper); }

    /** {@code daterange(lower, upper, 'bounds')} – bounds e.g. {@code "[]"}. */
    public static FunctionCall daterange(Object lower, Object upper, String bounds) { return fn("daterange", lower, upper, lit(bounds)); }

    /** {@code tsrange(lower, upper)} */
    public static FunctionCall tsrange(Object lower, Object upper) { return fn("tsrange", lower, upper); }

    /** {@code tstzrange(lower, upper)} */
    public static FunctionCall tstzrange(Object lower, Object upper) { return fn("tstzrange", lower, upper); }

    /** {@code int4range(lower, upper)} */
    public static FunctionCall int4range(Object lower, Object upper) { return fn("int4range", lower, upper); }

    /** {@code int8range(lower, upper)} */
    public static FunctionCall int8range(Object lower, Object upper) { return fn("int8range", lower, upper); }

    /** {@code numrange(lower, upper)} */
    public static FunctionCall numrange(Object lower, Object upper) { return fn("numrange", lower, upper); }

    /** {@code isempty(range)} */
    public static Condition isEmpty(Object range) { return condition(fn("isempty", range)); }

    // =========================================================================
    // JSON / JSONB
    // =========================================================================

    /** {@code (json -> 'key')} – object field as JSON. */
    public static Expression jsonGet(Object json, String key) { return operator(json, "->", lit(key)); }

    /** {@code (json -> index)} – array element as JSON (0-based, negative counts from the end). */
    public static Expression jsonGet(Object json, int index) { return operator(json, "->", lit(index)); }

    /** {@code (json ->> 'key')} – object field as text. */
    public static Expression jsonGetText(Object json, String key) { return operator(json, "->>", lit(key)); }

    /** {@code (json ->> index)} – array element as text. */
    public static Expression jsonGetText(Object json, int index) { return operator(json, "->>", lit(index)); }

    /** {@code (json #> '{a,b}')} – value at a path as JSON. */
    public static Expression jsonPath(Object json, String... path) { return operator(json, "#>", lit(textArray(path))); }

    /** {@code (json #>> '{a,b}')} – value at a path as text. */
    public static Expression jsonPathText(Object json, String... path) { return operator(json, "#>>", lit(textArray(path))); }

    /** {@code CAST(value AS jsonb)} – e.g. {@code jsonb(val(jsonString))}. */
    public static Expression jsonb(Object value) { return cast(value, "jsonb"); }

    /** {@code jsonb_build_object(k1, v1, k2, v2, ...)} – keys given as Java strings become literals. */
    public static FunctionCall jsonbBuildObject(Object... keyValues) { return fn("jsonb_build_object", keysAsLiterals(keyValues)); }

    /** {@code json_build_object(k1, v1, ...)} – keys given as Java strings become literals. */
    public static FunctionCall jsonBuildObject(Object... keyValues) { return fn("json_build_object", keysAsLiterals(keyValues)); }

    /** {@code jsonb_build_array(values...)} */
    public static FunctionCall jsonbBuildArray(Object... values) { return fn("jsonb_build_array", values); }

    /** {@code json_build_array(values...)} */
    public static FunctionCall jsonBuildArray(Object... values) { return fn("json_build_array", values); }

    /** {@code to_json(value)} */
    public static FunctionCall toJson(Object value) { return fn("to_json", value); }

    /** {@code to_jsonb(value)} */
    public static FunctionCall toJsonb(Object value) { return fn("to_jsonb", value); }

    /** {@code row_to_json(row)} – e.g. {@code rowToJson(raw("p"))} for the whole row of alias {@code p}. */
    public static FunctionCall rowToJson(Object row) { return fn("row_to_json", row); }

    /** {@code jsonb_set(target, '{path}', newValue)} */
    public static FunctionCall jsonbSet(Object target, String[] path, Object newValue) {
        return fn("jsonb_set", target, lit(textArray(path)), newValue);
    }

    /** {@code jsonb_set(target, '{path}', newValue, createMissing)} */
    public static FunctionCall jsonbSet(Object target, String[] path, Object newValue, boolean createMissing) {
        return fn("jsonb_set", target, lit(textArray(path)), newValue, lit(createMissing));
    }

    /** {@code jsonb_insert(target, '{path}', newValue)} */
    public static FunctionCall jsonbInsert(Object target, String[] path, Object newValue) {
        return fn("jsonb_insert", target, lit(textArray(path)), newValue);
    }

    /** {@code (a || b)} – merge two jsonb values. */
    public static Expression jsonbConcat(Object a, Object b) { return operator(a, "||", b); }

    /** {@code (json - 'key')} – remove a key. */
    public static Expression jsonbDelete(Object json, String key) { return operator(json, "-", lit(key)); }

    /** {@code (json #- '{path}')} – remove the value at a path. */
    public static Expression jsonbDeletePath(Object json, String... path) { return operator(json, "#-", lit(textArray(path))); }

    /** {@code jsonb_typeof(json)} */
    public static FunctionCall jsonbTypeof(Object json) { return fn("jsonb_typeof", json); }

    /** {@code jsonb_array_length(json)} */
    public static FunctionCall jsonbArrayLength(Object json) { return fn("jsonb_array_length", json); }

    /** {@code jsonb_array_elements(json)} – set-returning, use in {@code FROM}. */
    public static FunctionCall jsonbArrayElements(Object json) { return fn("jsonb_array_elements", json); }

    /** {@code jsonb_array_elements_text(json)} – set-returning. */
    public static FunctionCall jsonbArrayElementsText(Object json) { return fn("jsonb_array_elements_text", json); }

    /** {@code jsonb_each(json)} – set of key/value pairs. */
    public static FunctionCall jsonbEach(Object json) { return fn("jsonb_each", json); }

    /** {@code jsonb_each_text(json)} */
    public static FunctionCall jsonbEachText(Object json) { return fn("jsonb_each_text", json); }

    /** {@code jsonb_object_keys(json)} */
    public static FunctionCall jsonbObjectKeys(Object json) { return fn("jsonb_object_keys", json); }

    /** {@code jsonb_strip_nulls(json)} */
    public static FunctionCall jsonbStripNulls(Object json) { return fn("jsonb_strip_nulls", json); }

    /** {@code jsonb_pretty(json)} */
    public static FunctionCall jsonbPretty(Object json) { return fn("jsonb_pretty", json); }

    /** {@code jsonb_path_query(json, 'jsonpath')} – set-returning. */
    public static FunctionCall jsonbPathQuery(Object json, String jsonPath) { return fn("jsonb_path_query", json, lit(jsonPath)); }

    /** {@code jsonb_path_query_first(json, 'jsonpath')} */
    public static FunctionCall jsonbPathQueryFirst(Object json, String jsonPath) { return fn("jsonb_path_query_first", json, lit(jsonPath)); }

    /** {@code jsonb_path_query_array(json, 'jsonpath')} */
    public static FunctionCall jsonbPathQueryArray(Object json, String jsonPath) { return fn("jsonb_path_query_array", json, lit(jsonPath)); }

    /** {@code jsonb_path_exists(json, 'jsonpath')} */
    public static Condition jsonbPathExists(Object json, String jsonPath) {
        return condition(fn("jsonb_path_exists", json, lit(jsonPath)));
    }

    /** {@code jsonb_path_match(json, 'jsonpath predicate')} */
    public static Condition jsonbPathMatch(Object json, String jsonPath) {
        return condition(fn("jsonb_path_match", json, lit(jsonPath)));
    }

    // =========================================================================
    // Arrays
    // =========================================================================

    /** {@code ARRAY[a, b, c]} */
    public static Expression array(Object... elements) {
        return ctx -> ctx.append("ARRAY[").visitAll(java.util.Arrays.asList(elements), ", ").append(']');
    }

    /** {@code ARRAY(SELECT ...)} – collects a sub-query into an array. */
    public static Expression arrayOf(Object subQuery) {
        return ctx -> ctx.append("ARRAY").visit(subQuery);
    }

    /** {@code (array)[index]} – 1-based element access. */
    public static Expression arrayElement(Object array, int index) {
        return ctx -> ctx.append('(').visit(array).append(")[").append(Integer.toString(index)).append(']');
    }

    /** {@code array_length(array, dimension)} */
    public static FunctionCall arrayLength(Object array, int dimension) { return fn("array_length", array, lit(dimension)); }

    /** {@code cardinality(array)} – total number of elements. */
    public static FunctionCall cardinality(Object array) { return fn("cardinality", array); }

    /** {@code array_append(array, element)} */
    public static FunctionCall arrayAppend(Object array, Object element) { return fn("array_append", array, element); }

    /** {@code array_prepend(element, array)} */
    public static FunctionCall arrayPrepend(Object element, Object array) { return fn("array_prepend", element, array); }

    /** {@code array_cat(a, b)} */
    public static FunctionCall arrayCat(Object a, Object b) { return fn("array_cat", a, b); }

    /** {@code array_remove(array, element)} */
    public static FunctionCall arrayRemove(Object array, Object element) { return fn("array_remove", array, element); }

    /** {@code array_replace(array, from, to)} */
    public static FunctionCall arrayReplace(Object array, Object from, Object to) { return fn("array_replace", array, from, to); }

    /** {@code array_position(array, element)} */
    public static FunctionCall arrayPosition(Object array, Object element) { return fn("array_position", array, element); }

    /** {@code array_positions(array, element)} */
    public static FunctionCall arrayPositions(Object array, Object element) { return fn("array_positions", array, element); }

    /** {@code array_to_string(array, 'separator')} */
    public static FunctionCall arrayToString(Object array, String separator) { return fn("array_to_string", array, lit(separator)); }

    /** {@code array_lower(array, dimension)} */
    public static FunctionCall arrayLower(Object array, int dimension) { return fn("array_lower", array, lit(dimension)); }

    /** {@code array_upper(array, dimension)} */
    public static FunctionCall arrayUpper(Object array, int dimension) { return fn("array_upper", array, lit(dimension)); }

    /** {@code unnest(array)} – set-returning, use in {@code FROM} or the select list. */
    public static FunctionCall unnest(Object array) { return fn("unnest", array); }

    // =========================================================================
    // Full-text search
    // =========================================================================

    /** {@code to_tsvector(document)} – uses the default text search configuration. */
    public static FunctionCall toTsvector(Object document) { return fn("to_tsvector", document); }

    /** {@code to_tsvector('config', document)} – e.g. config {@code "english"}. */
    public static FunctionCall toTsvector(String config, Object document) { return fn("to_tsvector", regconfig(config), document); }

    /** {@code to_tsquery(query)} */
    public static FunctionCall toTsquery(Object query) { return fn("to_tsquery", query); }

    /** {@code to_tsquery('config', query)} */
    public static FunctionCall toTsquery(String config, Object query) { return fn("to_tsquery", regconfig(config), query); }

    /** {@code plainto_tsquery('config', text)} – plain text, all words must match. */
    public static FunctionCall plaintoTsquery(String config, Object text) { return fn("plainto_tsquery", regconfig(config), text); }

    /** {@code phraseto_tsquery('config', text)} – words must appear in order. */
    public static FunctionCall phrasetoTsquery(String config, Object text) { return fn("phraseto_tsquery", regconfig(config), text); }

    /** {@code websearch_to_tsquery('config', text)} – Google-like syntax ({@code "quoted" -excluded or}). */
    public static FunctionCall websearchToTsquery(String config, Object text) {
        return fn("websearch_to_tsquery", regconfig(config), text);
    }

    /** {@code ts_rank(vector, query)} */
    public static FunctionCall tsRank(Object vector, Object query) { return fn("ts_rank", vector, query); }

    /** {@code ts_rank_cd(vector, query)} – cover density ranking. */
    public static FunctionCall tsRankCd(Object vector, Object query) { return fn("ts_rank_cd", vector, query); }

    /** {@code ts_headline('config', document, query)} – highlighted excerpt. */
    public static FunctionCall tsHeadline(String config, Object document, Object query) {
        return fn("ts_headline", regconfig(config), document, query);
    }

    /** {@code setweight(vector, 'A'|'B'|'C'|'D')} */
    public static FunctionCall setweight(Object vector, String weight) { return fn("setweight", vector, lit(weight)); }

    // =========================================================================
    // Sequences, system information, misc
    // =========================================================================

    /** {@code gen_random_uuid()} (PostgreSQL 13+) */
    public static FunctionCall genRandomUuid() { return fn("gen_random_uuid"); }

    /** {@code nextval('sequence')} */
    public static FunctionCall nextval(String sequence) { return fn("nextval", lit(sequence)); }

    /** {@code currval('sequence')} */
    public static FunctionCall currval(String sequence) { return fn("currval", lit(sequence)); }

    /** {@code setval('sequence', value)} */
    public static FunctionCall setval(String sequence, Object value) { return fn("setval", lit(sequence), value); }

    /** {@code lastval()} */
    public static FunctionCall lastval() { return fn("lastval"); }

    /** {@code CURRENT_USER} */
    public static Expression currentUser() { return raw("CURRENT_USER"); }

    /** {@code SESSION_USER} */
    public static Expression sessionUser() { return raw("SESSION_USER"); }

    /** {@code current_schema()} */
    public static FunctionCall currentSchema() { return fn("current_schema"); }

    /** {@code current_database()} */
    public static FunctionCall currentDatabase() { return fn("current_database"); }

    /** {@code version()} */
    public static FunctionCall version() { return fn("version"); }

    /** {@code pg_typeof(value)} – handy for debugging. */
    public static FunctionCall pgTypeof(Object value) { return fn("pg_typeof", value); }

    /** {@code ROW(a, b, ...)} – row constructor, e.g. for {@code eq(row(a, b), row(x, y))}. */
    public static FunctionCall row(Object... values) { return fn("ROW", values); }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private static FunctionCall fn(String name, Object... args) {
        return new FunctionCall(name, args);
    }

    private static Expression lit(Object value) {
        return new Literal(value);
    }

    private static Expression regconfig(String config) {
        return cast(lit(config), "regconfig");
    }

    private static Expression keywordList(String keyword, Object[] items) {
        return ctx -> {
            if (!keyword.isEmpty()) ctx.append(keyword).append(' ');
            ctx.append('(').visitAll(java.util.Arrays.asList(items), ", ").append(')');
        };
    }

    /** Builds a PostgreSQL text-array literal such as {@code {a,"b c"}}. */
    static String textArray(String... elements) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < elements.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(elements[i].replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return sb.append('}').toString();
    }

    private static Object[] keysAsLiterals(Object[] keyValues) {
        if (keyValues.length % 2 != 0) throw new IllegalArgumentException("key/value arguments must come in pairs");
        Object[] result = keyValues.clone();
        for (int i = 0; i < result.length; i += 2) {
            if (result[i] instanceof String) result[i] = lit(result[i]);
        }
        return result;
    }
}
