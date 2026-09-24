package ch.lxrin.ql;

import ch.lxrin.ql.TestTables.OrderTable;
import ch.lxrin.ql.TestTables.PersonTable;
import ch.lxrin.ql.expr.Expression;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static ch.lxrin.ql.LxrinQL.*;
import static org.junit.jupiter.api.Assertions.*;

class FunctionsTest {

    private final PersonTable p = new PersonTable();
    private final OrderTable o = new OrderTable();

    private static void assertSql(String expected, Expression e) {
        assertEquals(expected, e.toSql());
    }

    @Test
    void aggregates() {
        assertSql("count(*)", count());
        assertSql("count(o.TOTAL)", count(o.total));
        assertSql("count(DISTINCT o.PERSON_NR)", countDistinct(o.personNr));
        assertSql("sum(o.TOTAL)", sum(o.total));
        assertSql("avg(o.TOTAL)", avg(o.total));
        assertSql("min(o.TOTAL)", min(o.total));
        assertSql("max(o.TOTAL)", max(o.total));
        assertSql("string_agg(p.LAST_NAME, ', ' ORDER BY p.LAST_NAME ASC)", stringAgg(p.lastName, ", ").orderBy(p.lastName.asc()));
        assertSql("array_agg(DISTINCT p.STATUS)", arrayAgg(p.status).distinct());
        assertSql("jsonb_agg(o.DATA)", jsonbAgg(o.data));
        assertSql("jsonb_object_agg(p.LAST_NAME, p.AGE)", jsonbObjectAgg(p.lastName, p.age));
        assertSql("bool_and(t.OK)", boolAnd("t.OK"));
        assertSql("count(*) FILTER (WHERE o.STATUS = 'PAID')", count().filter(eq(o.status, inline("PAID"))));
        assertSql("percentile_cont(0.5) WITHIN GROUP (ORDER BY o.TOTAL ASC)", percentileCont(0.5).withinGroup(o.total.asc()));
        assertSql("mode() WITHIN GROUP (ORDER BY p.STATUS)", mode().withinGroup(p.status));
        assertSql("corr(o.TOTAL, p.AGE)", corr(o.total, p.age));
    }

    @Test
    void windowFunctions() {
        assertSql("row_number() OVER (PARTITION BY o.PERSON_NR ORDER BY o.CREATED_AT DESC)",
                rowNumber().over(partitionBy(o.personNr).orderBy(o.createdAt.desc())));
        assertSql("rank() OVER ()", rank().over());
        assertSql("sum(o.TOTAL) OVER (ORDER BY o.CREATED_AT ASC ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)",
                sum(o.total).over(window().orderBy(o.createdAt.asc()).rowsBetween(unboundedPreceding(), currentRow())));
        assertSql("avg(o.TOTAL) OVER (ORDER BY o.CREATED_AT ASC ROWS BETWEEN 2 PRECEDING AND 2 FOLLOWING)",
                avg(o.total).over(window().orderBy(o.createdAt.asc()).rowsBetween(preceding(2), following(2))));
        assertSql("lag(o.TOTAL, 1, 0) OVER (ORDER BY o.CREATED_AT)", lag(o.total, 1, inline(0)).over(window().orderBy(o.createdAt)));
        assertSql("lead(o.TOTAL)", lead(o.total));
        assertSql("ntile(4)", ntile(4));
        assertSql("nth_value(o.TOTAL, 2)", nthValue(o.total, 2));
        assertSql("dense_rank() OVER w", denseRank().over("w"));
    }

    @Test
    void conditionalExpressions() {
        assertSql("COALESCE(p.LAST_NAME, '-')", coalesce(p.lastName, inline("-")));
        assertSql("NULLIF(p.AGE, 0)", nullif(p.age, inline(0)));
        assertSql("GREATEST(p.AGE, 18)", greatest(p.age, inline(18)));
        assertSql("LEAST(p.AGE, 65)", least(p.age, inline(65)));
        assertSql("CASE WHEN p.AGE < 18 THEN 'minor' WHEN p.AGE < 65 THEN 'adult' ELSE 'senior' END",
                caseWhen(lt(p.age, inline(18)), inline("minor")).when(lt(p.age, inline(65)), inline("adult")).otherwise(inline("senior")));
        assertSql("CASE o.STATUS WHEN 'N' THEN 'new' END", caseOf(o.status).when(inline("N"), inline("new")));
        assertThrows(IllegalStateException.class, () -> caseOf(o.status).toSql());
    }

    @Test
    void stringFunctions() {
        assertSql("lower(p.LAST_NAME)", lower(p.lastName));
        assertSql("upper(p.LAST_NAME)", upper(p.lastName));
        assertSql("initcap(p.LAST_NAME)", initcap(p.lastName));
        assertSql("length(p.LAST_NAME)", length(p.lastName));
        assertSql("concat(p.FIRST_NAME, ' ', p.LAST_NAME)", concat(p.firstName, inline(" "), p.lastName));
        assertSql("concat_ws(' ', p.FIRST_NAME, p.LAST_NAME)", concatWs(" ", p.firstName, p.lastName));
        assertSql("(p.FIRST_NAME || p.LAST_NAME)", p.firstName.concat(p.lastName));
        assertSql("substring(p.LAST_NAME, 1, 3)", substring(p.lastName, 1, 3));
        assertSql("substring(p.LAST_NAME FROM '[0-9]+')", substringRegex(p.lastName, "[0-9]+"));
        assertSql("left(p.LAST_NAME, 2)", left(p.lastName, 2));
        assertSql("right(p.LAST_NAME, 2)", right(p.lastName, 2));
        assertSql("btrim(p.LAST_NAME)", trim(p.lastName));
        assertSql("btrim(p.LAST_NAME, 'x')", btrim(p.lastName, "x"));
        assertSql("lpad(p.LAST_NAME, 10, '0')", lpad(p.lastName, 10, "0"));
        assertSql("replace(p.LAST_NAME, 'O''', 'O')", replace(p.lastName, "O'", "O"));
        assertSql("replace(p.LAST_NAME, :lq0, p.FIRST_NAME)", replace(p.lastName, val("a"), p.firstName));
        assertSql("split_part(p.LAST_NAME, '-', 2)", splitPart(p.lastName, "-", 2));
        assertSql("regexp_replace(p.LAST_NAME, '\\s+', ' ', 'g')", regexpReplace(p.lastName, "\\s+", " ", "g"));
        assertSql("regexp_match(p.LAST_NAME, '^(\\w+)')", regexpMatch(p.lastName, "^(\\w+)"));
        assertSql("format('%s-%s', p.FIRST_NAME, p.LAST_NAME)", format("%s-%s", p.firstName, p.lastName));
        assertSql("md5(p.LAST_NAME)", md5(p.lastName));
        assertSql("strpos(p.LAST_NAME, :lq0)", strpos(p.lastName, val("x")));
        assertSql("string_to_array(p.LAST_NAME, ',')", stringToArray(p.lastName, ","));
    }

    @Test
    void mathFunctions() {
        assertSql("round(o.TOTAL, 2)", round(o.total, 2));
        assertSql("abs(o.TOTAL)", abs(o.total));
        assertSql("ceil(o.TOTAL)", ceil(o.total));
        assertSql("floor(o.TOTAL)", floor(o.total));
        assertSql("mod(p.AGE, 2)", mod(p.age, inline(2)));
        assertSql("power(p.AGE, 2)", power(p.age, inline(2)));
        assertSql("((o.TOTAL * 1.1) - :lq0)", o.total.times(inline(1.1)).minus(5));
        assertSql("(o.TOTAL / 2)", o.total.divide(inline(2)));
        assertSql("width_bucket(p.AGE, 0, 100, 10)", widthBucket(p.age, inline(0), inline(100), 10));
        assertSql("random()", random());
    }

    @Test
    void dateTimeFunctions() {
        assertSql("now()", now());
        assertSql("CURRENT_DATE", currentDate());
        assertSql("date_trunc('month', o.CREATED_AT)", dateTrunc("month", o.createdAt));
        assertSql("date_trunc('day', o.CREATED_AT, 'Europe/Zurich')", dateTrunc("day", o.createdAt, "Europe/Zurich"));
        assertSql("EXTRACT(YEAR FROM o.CREATED_AT)", extract("YEAR", o.createdAt));
        assertThrows(IllegalArgumentException.class, () -> extract("YEAR; DROP", o.createdAt));
        assertSql("date_part('dow', o.CREATED_AT)", datePart("dow", o.createdAt));
        assertSql("age(o.CREATED_AT)", age(o.createdAt));
        assertSql("(now() - INTERVAL '7 days')", now().minus(interval("7 days")));
        assertSql("to_char(o.CREATED_AT, 'YYYY-MM-DD')", toChar(o.createdAt, "YYYY-MM-DD"));
        assertSql("to_date(:lq0, 'DD.MM.YYYY')", toDate(val("01.02.2024"), "DD.MM.YYYY"));
        assertSql("(o.CREATED_AT AT TIME ZONE 'UTC')", atTimeZone(o.createdAt, "UTC"));
        assertSql("make_date(2024, 1, 31)", makeDate(inline(2024), inline(1), inline(31)));
        assertSql("generate_series(:lq0, :lq1, INTERVAL '1 day')",
                generateSeries(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), interval("1 day")));
        assertSql("date_bin(INTERVAL '15 minutes', o.CREATED_AT, TIMESTAMP '2000-01-01T00:00')",
                dateBin("15 minutes", o.createdAt, inline(java.time.LocalDateTime.of(2000, 1, 1, 0, 0))));
    }

    @Test
    void jsonFunctions() {
        assertSql("(o.DATA -> 'customer')", jsonGet(o.data, "customer"));
        assertSql("(o.DATA -> 0)", jsonGet(o.data, 0));
        assertSql("(o.DATA ->> 'name')", jsonGetText(o.data, "name"));
        assertSql("(o.DATA #> '{\"a\",\"b\"}')", jsonPath(o.data, "a", "b"));
        assertSql("(o.DATA #>> '{\"a\",\"b c\"}')", jsonPathText(o.data, "a", "b c"));
        assertSql("jsonb_build_object('id', o.ORDER_ID, 'total', o.TOTAL)", jsonbBuildObject("id", o.orderId, "total", o.total));
        assertThrows(IllegalArgumentException.class, () -> jsonbBuildObject("id"));
        assertSql("jsonb_set(o.DATA, '{\"status\"}', CAST(:lq0 AS jsonb), TRUE)",
                jsonbSet(o.data, new String[]{"status"}, jsonb(val("\"x\"")), true));
        assertSql("(o.DATA - 'tmp')", jsonbDelete(o.data, "tmp"));
        assertSql("(o.DATA #- '{\"a\"}')", jsonbDeletePath(o.data, "a"));
        assertSql("jsonb_path_exists(o.DATA, '$.items[*] ? (@.qty > 1)')", jsonbPathExists(o.data, "$.items[*] ? (@.qty > 1)"));
        assertSql("jsonb_array_elements(o.DATA)", jsonbArrayElements(o.data));
        assertSql("CAST(:lq0 AS jsonb)", jsonb(val("{}")));
        assertSql("to_jsonb(p.LAST_NAME)", toJsonb(p.lastName));
    }

    @Test
    void arrayFunctions() {
        assertSql("ARRAY[1, 2, 3]", array(inline(1), inline(2), inline(3)));
        assertSql("ARRAY(SELECT p.LAST_NAME FROM PERSON p)", arrayOf(select(p.lastName).from(p)));
        assertSql("(t.TAGS)[1]", arrayElement("t.TAGS", 1));
        assertSql("array_length(t.TAGS, 1)", arrayLength("t.TAGS", 1));
        assertSql("cardinality(t.TAGS)", cardinality("t.TAGS"));
        assertSql("array_append(t.TAGS, :lq0)", arrayAppend("t.TAGS", val("x")));
        assertSql("array_to_string(t.TAGS, ',')", arrayToString("t.TAGS", ","));
        assertSql("unnest(t.TAGS)", unnest("t.TAGS"));
        assertSql("array_position(t.TAGS, :lq0)", arrayPosition("t.TAGS", val("x")));
    }

    @Test
    void fullTextSearch() {
        assertSql("to_tsvector(CAST('english' AS regconfig), p.LAST_NAME) @@ websearch_to_tsquery(CAST('english' AS regconfig), :lq0)",
                tsMatches(toTsvector("english", p.lastName), websearchToTsquery("english", val("ada -turing"))));
        assertSql("ts_rank(t.DOC, t.Q)", tsRank("t.DOC", "t.Q"));
        assertSql("ts_headline(CAST('simple' AS regconfig), t.BODY, t.Q)", tsHeadline("simple", "t.BODY", "t.Q"));
        assertSql("setweight(t.DOC, 'A')", setweight("t.DOC", "A"));
    }

    @Test
    void rangesAndMisc() {
        assertSql("daterange(o.CREATED_AT, :lq0, '[]')", daterange(o.createdAt, LocalDate.of(2024, 1, 1), "[]"));
        assertSql("isempty(t.R)", isEmpty("t.R"));
        assertSql("gen_random_uuid()", genRandomUuid());
        assertSql("nextval('person_seq')", nextval("person_seq"));
        assertSql("CURRENT_USER", currentUser());
        assertSql("pg_typeof(p.AGE)", pgTypeof(p.age));
        assertSql("ROW(p.AGE, p.STATUS)", row(p.age, p.status));
        assertSql("similarity(p.LAST_NAME, :lq0)", function("similarity", p.lastName, val("x")));
        assertSql("my_flag(p.AGE)", booleanFunction("my_flag", p.age));
    }

    @Test
    void castsLiteralsAndIdentifiers() {
        assertSql("CAST(p.AGE AS text)", p.age.cast("text"));
        assertSql("CAST(:lq0 AS numeric(10,2))", cast(val(1), "numeric(10,2)"));
        assertThrows(IllegalArgumentException.class, () -> cast(p.age, "int); DROP TABLE x; --"));
        assertSql("'O''Brien'", inline("O'Brien"));
        assertSql("DATE '2024-01-31'", inline(LocalDate.of(2024, 1, 31)));
        assertSql("TRUE", inline(true));
        assertSql("NULL", inline(null));
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertSql("UUID '00000000-0000-0000-0000-000000000001'", inline(id));
        assertSql("\"order\".\"na\"\"me\"", ident("order", "na\"me"));
        assertSql("EXTRACT(EPOCH FROM {x}) {7", sql("EXTRACT({0} FROM {x}) {7", "EPOCH"));
        assertThrows(IllegalArgumentException.class, () -> sql("{1}", "a").toSql());
    }
}
