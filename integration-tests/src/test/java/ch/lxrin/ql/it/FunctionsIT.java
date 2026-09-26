package ch.lxrin.ql.it;

import ch.lxrin.ql.dsl.DatePart;
import ch.lxrin.ql.dsl.Field;
import ch.lxrin.ql.dsl.FunctionTable;
import ch.lxrin.ql.dsl.Routines;
import ch.lxrin.ql.dsl.Row;
import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.types.SqlTypes;
import ch.lxrin.ql.types.UuidV7;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static ch.lxrin.ql.TestSchema.OrdersTable.ORDERS;
import static ch.lxrin.ql.TestSchema.Role;
import static ch.lxrin.ql.TestSchema.UsersTable.USERS;
import static ch.lxrin.ql.dsl.Dsl.*;
import static org.junit.jupiter.api.Assertions.*;

/** Executes every function of the catalog once against PostgreSQL. */
class FunctionsIT {

    private static QueryContext ctx;
    private static UUID ada;

    @BeforeAll
    static void setUp() {
        Postgres.resetTestSchema();
        ctx = QueryContext.builder().dataSource(Postgres.dataSource()).build();
        ada = UuidV7.generate();
        ctx.insertInto(USERS).set(USERS.ID, ada).set(USERS.NAME, "Ada").set(USERS.EMAIL, "ada@example.org").set(USERS.ROLE, Role.ADMIN)
                .set(USERS.TAGS, new String[]{"vip", "math"}).set(USERS.SETTINGS, "{\"city\": \"London\", \"n\": [1, 2]}").execute();
        ctx.insertInto(ORDERS).columns(ORDERS.USER_ID, ORDERS.TOTAL, ORDERS.STATUS, ORDERS.ORDERED_ON)
                .values(ada, new BigDecimal("10.00"), "PAID", LocalDate.of(2024, 1, 5))
                .values(ada, new BigDecimal("30.00"), "OPEN", LocalDate.of(2024, 2, 5))
                .execute();
    }

    private static Row row(Field<?>... fields) {
        return ctx.select(List.of(fields)).from(USERS).where(USERS.ID.eq(ada)).fetchOne();
    }

    @Test
    void aggregates() {
        Row r = ctx.select(List.of(count(), count(ORDERS.ID), countDistinct(ORDERS.STATUS), sum(ORDERS.TOTAL), avg(ORDERS.TOTAL),
                        min(ORDERS.TOTAL), max(ORDERS.STATUS), min(ORDERS.ORDERED_ON), stringAgg(ORDERS.STATUS, ",").orderBy(ORDERS.STATUS.asc()),
                        arrayAgg(ORDERS.ID).orderBy(ORDERS.ID.asc()), jsonAgg(ORDERS.STATUS), jsonbAgg(ORDERS.TOTAL),
                        jsonObjectAgg(ORDERS.STATUS, ORDERS.TOTAL), jsonbObjectAgg(ORDERS.STATUS, ORDERS.ID),
                        boolAnd(ORDERS.TOTAL.gt(BigDecimal.ZERO)), boolOr(ORDERS.STATUS.eq("PAID")), every(ORDERS.STATUS.eq("PAID")),
                        bitAnd(ORDERS.ID), bitOr(ORDERS.ID), anyValue(ORDERS.STATUS), stddev(ORDERS.TOTAL), stddevPop(ORDERS.TOTAL),
                        stddevSamp(ORDERS.TOTAL), variance(ORDERS.TOTAL), varPop(ORDERS.TOTAL), varSamp(ORDERS.TOTAL),
                        corr(ORDERS.TOTAL, ORDERS.ID), covarPop(ORDERS.TOTAL, ORDERS.ID), covarSamp(ORDERS.TOTAL, ORDERS.ID),
                        regrSlope(ORDERS.TOTAL, ORDERS.ID), regrIntercept(ORDERS.TOTAL, ORDERS.ID),
                        percentileCont(0.5, ORDERS.TOTAL.asc()), percentileDisc(0.5, ORDERS.ORDERED_ON.asc()), mode(ORDERS.STATUS.asc()),
                        count().filter(ORDERS.STATUS.eq("PAID"))))
                .from(ORDERS).fetchOne();
        assertEquals(2L, r.get(0));
        assertEquals(new BigDecimal("40.00"), r.get(3));
        assertEquals("OPEN,PAID", r.get(8));
        assertEquals(20.0, (Double) r.get(31), 0.001);
        assertEquals(LocalDate.of(2024, 1, 5), r.get(32));
        assertEquals(1L, r.get(34));
    }

    @Test
    void windowFunctions() {
        List<Row> rows = ctx.select(List.of(rowNumber().over(orderBy(ORDERS.ID.asc())), rank().over(orderBy(ORDERS.ID.asc())),
                        denseRank().over(orderBy(ORDERS.ID.asc())), percentRank().over(orderBy(ORDERS.ID.asc())),
                        cumeDist().over(orderBy(ORDERS.ID.asc())), ntile(2).over(orderBy(ORDERS.ID.asc())),
                        lag(ORDERS.TOTAL).over(orderBy(ORDERS.ID.asc())), lag(ORDERS.TOTAL, 1).over(orderBy(ORDERS.ID.asc())),
                        lag(ORDERS.TOTAL, 1, BigDecimal.ZERO).over(orderBy(ORDERS.ID.asc())), lead(ORDERS.TOTAL).over(orderBy(ORDERS.ID.asc())),
                        lead(ORDERS.TOTAL, 1).over(orderBy(ORDERS.ID.asc())), lead(ORDERS.TOTAL, 1, BigDecimal.ZERO).over(orderBy(ORDERS.ID.asc())),
                        firstValue(ORDERS.TOTAL).over(orderBy(ORDERS.ID.asc())),
                        lastValue(ORDERS.TOTAL).over(orderBy(ORDERS.ID.asc()).rowsBetween(unboundedPreceding(), unboundedFollowing())),
                        nthValue(ORDERS.TOTAL, 2).over(orderBy(ORDERS.ID.asc()).rowsBetween(unboundedPreceding(), unboundedFollowing())),
                        sum(ORDERS.TOTAL).over(), avg(ORDERS.TOTAL).over(partitionBy(ORDERS.STATUS))))
                .from(ORDERS).orderBy(ORDERS.ID.asc()).fetch();
        assertEquals(2, rows.size());
        assertEquals(new BigDecimal("0"), ((BigDecimal) rows.get(0).get(8)).stripTrailingZeros());
        assertEquals(new BigDecimal("30.00"), rows.get(0).get(13));
    }

    @Test
    void stringFunctions() {
        Row r = row(charLength(USERS.NAME), length(USERS.NAME), octetLength(USERS.NAME), bitLength(USERS.NAME), lower(USERS.NAME),
                upper(USERS.NAME), initcap(param("ada lovelace")), concat(USERS.NAME, param(1)), concatWs("-", USERS.NAME, USERS.EMAIL),
                substring(USERS.NAME, 2), substring(USERS.NAME, 1, 2), substringRegex(USERS.EMAIL, "@(.*)$"), left(USERS.NAME, 1),
                right(USERS.NAME, 1), trim(param("  x ")), btrim(param("xxAxx"), "x"), ltrim(param("  a")), ltrim(param("xa"), "x"),
                rtrim(param("a  ")), rtrim(param("ax"), "x"), lpad(USERS.NAME, 5, "*"), rpad(USERS.NAME, 5, "*"),
                replace(USERS.NAME, "A", "4"), replace(USERS.NAME, param("d"), param("D")), translate(USERS.NAME, "da", "DA"),
                strpos(USERS.EMAIL, "@"), startsWith(USERS.NAME, "A"), reverse(USERS.NAME), repeat(USERS.NAME, 2),
                splitPart(USERS.EMAIL, "@", 2), stringToArray(param("a,b"), ","), format("%s/%s", USERS.NAME, USERS.ROLE),
                regexpReplace(USERS.NAME, "a", "4"), regexpReplace(USERS.NAME, "a", "4", "gi"), regexpMatch(USERS.EMAIL, "(\\w+)@"),
                regexpSplitToArray(USERS.EMAIL, "[@.]"), regexpCount(USERS.NAME, "a"), regexpSubstr(USERS.EMAIL, "\\w+"),
                md5(USERS.NAME), sha256(convertTo(USERS.NAME, "UTF8")), encode(convertTo(USERS.NAME, "UTF8"), "base64"),
                decode(param("QWRh"), "base64"), quoteIdent(param("a b")), quoteLiteral(USERS.NAME), quoteNullable(USERS.NAME),
                toHex(param(255)), ascii(USERS.NAME), chr(param(65)), USERS.NAME.lower(), USERS.NAME.trim(), USERS.NAME.length());
        assertEquals(3, r.get(0));
        assertEquals("Ada-ada@example.org", r.get(8));
        assertEquals("example.org", r.get(11));
        assertEquals("**Ada", r.get(20));
        assertEquals(true, r.get(26));
        assertArrayEquals(new String[]{"a", "b"}, (String[]) r.get(30));
        assertEquals("Ada/ADMIN", r.get(31));
        assertEquals("4d4", r.get(33));
        assertEquals("QWRh", r.get(40));
        assertEquals("ff", r.get(45));
        assertEquals("A", r.get(47));
    }

    @Test
    void mathFunctions() {
        Field<BigDecimal> x = param(new BigDecimal("2.5"));
        Field<Double> y = param(0.5d);
        Row r = row(abs(param(new BigDecimal("-2.5"))), ceil(param(new BigDecimal("2.1"))), floor(param(new BigDecimal("2.9"))),
                round(param(new BigDecimal("2.5"))), round(x, 0), trunc(param(new BigDecimal("2.9"))), trunc(x, 0),
                mod(param(7), param(3)), div(param(7), param(2)), power(param(2), param(10)), sqrt(param(16)), cbrt(param(27)),
                exp(param(0)), ln(param(1)), log10(param(100)), log(param(2), param(8)), sign(param(-3)), pi(), random(),
                degrees(pi()), radians(param(180)), sin(y), cos(y), tan(y), asin(y), acos(y), atan(y), atan2(y, y),
                gcd(param(12), param(18)), lcm(param(4), param(6)), widthBucket(param(5), param(0), param(10), 5));
        assertEquals(new BigDecimal("2.5"), r.get(0));
        assertEquals(1, r.get(7));
        assertEquals(1024.0, (Double) r.get(9), 0.0001);
        assertEquals(3, ((BigDecimal) r.get(15)).setScale(0, java.math.RoundingMode.HALF_UP).intValue());
        assertEquals(6, r.get(28));
        assertEquals(3, r.get(30));
    }

    @Test
    void dateTimeFunctions() {
        Row r = row(now(), clockTimestamp(), statementTimestamp(), currentDate(), localTime(), localTimestamp(), currentTimestamp(),
                dateTrunc(DatePart.MONTH, USERS.CREATED_AT), extract(DatePart.YEAR, USERS.CREATED_AT), datePart(DatePart.DOW, USERS.CREATED_AT),
                dateBin(Duration.ofMinutes(15), USERS.CREATED_AT, Instant.EPOCH), age(USERS.CREATED_AT, param(Instant.EPOCH)),
                makeDate(param(2024), param(2), param(29)), makeTime(param(12), param(30), param(0.0d)),
                makeTimestamp(param(2024), param(1), param(1), param(0), param(0), param(1.5d)), toChar(param(LocalDate.of(2024, 5, 17)), "DD.MM.YYYY"),
                toDate(param("17.05.2024"), "DD.MM.YYYY"), toTimestamp(param("2024-05-17 10:00"), "YYYY-MM-DD HH24:MI"),
                toTimestamp(param(0L)), toNumber(param("1,234.5"), "9G999D9"),
                localDateTimeAt(param(Instant.parse("2024-01-01T12:00:00Z")), ZoneId.of("Europe/Zurich")),
                instantAt(param(LocalDateTime.of(2024, 1, 1, 13, 0)), ZoneId.of("Europe/Zurich")),
                USERS.CREATED_AT.extract(DatePart.EPOCH), ORDERS_FREE_DATE());
        assertEquals(LocalDate.of(2024, 2, 29), r.get(12));
        assertEquals("17.05.2024", r.get(15));
        assertEquals(LocalDate.of(2024, 5, 17), r.get(16));
        assertEquals(Instant.EPOCH, r.get(18));
        assertEquals(LocalDateTime.of(2024, 1, 1, 13, 0), r.get(20));
        assertEquals(Instant.parse("2024-01-01T12:00:00Z"), r.get(21));
    }

    private static Field<LocalDate> ORDERS_FREE_DATE() {
        return param(LocalDate.of(2024, 1, 1)).plus(Duration.ofDays(3));
    }

    @Test
    void setReturningFunctionsAndRanges() {
        FunctionTable<Integer> n = tableOf(generateSeries(1, 3), "n");
        assertEquals(List.of(1, 2, 3), ctx.select(n.value()).from(n).orderBy(n.value().asc()).fetch());
        FunctionTable<Instant> days = tableOf(generateSeries(param(Instant.parse("2024-01-01T00:00:00Z")),
                param(Instant.parse("2024-01-03T00:00:00Z")), Duration.ofDays(1)), "d");
        assertEquals(Instant.parse("2024-01-03T00:00:00Z"), ctx.select(days.value()).from(days).orderBy(days.value().desc()).fetchFirst().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> generateSeries(param(LocalDate.MIN), param(LocalDate.MAX), Duration.ofDays(1)));
        assertEquals(List.of("math", "vip"), ctx.select(unnest(USERS.TAGS)).from(USERS).fetch().stream().sorted().toList());
        Row r = row(daterange(param(LocalDate.of(2024, 1, 1)), param(LocalDate.of(2024, 2, 1))),
                tstzrange(param(Instant.EPOCH), now()), tsrange(param(LocalDateTime.MIN.withYear(2000)), localTimestamp()),
                int4range(param(1), param(5)), int8range(param(1L), param(5L)), numrange(param(BigDecimal.ONE), param(BigDecimal.TEN)),
                rangeContains(int4range(param(1), param(5)), param(3)), rangeOverlaps(int4range(param(1), param(5)), int4range(param(4), param(9))),
                isEmpty(int4range(param(1), param(1))));
        assertEquals("[2024-01-01,2024-02-01)", r.get(0));
        assertEquals(true, r.get(6));
        assertEquals(true, r.get(7));
        assertEquals(true, r.get(8));
        assertEquals(List.of("a", "b"), ctx.select(regexpSplitToTable(param("a b"), " ")).fetch());
    }

    @Test
    void jsonFunctions() {
        Row r = row(jsonb(param("{\"a\": 1}")), jsonbBuildObject(pair("id", USERS.ID), pair("n", USERS.NAME)),
                jsonBuildObject(pair("x", param(1))), jsonbBuildArray(USERS.NAME, param(2)), jsonBuildArray(USERS.NAME),
                toJson(USERS.NAME), toJsonb(USERS.TAGS), rowToJson(USERS), jsonbSet(USERS.SETTINGS, new String[]{"city"}, toJsonb(param("Paris")), true),
                jsonbInsert(USERS.SETTINGS, new String[]{"n", "0"}, toJsonb(param(0))), jsonbTypeof(USERS.SETTINGS),
                jsonbArrayLength(USERS.SETTINGS.get("n")), jsonbStripNulls(USERS.SETTINGS), jsonbPretty(USERS.SETTINGS),
                jsonbPathQueryFirst(USERS.SETTINGS, "$.n[1]"), jsonbPathQueryArray(USERS.SETTINGS, "$.n[*]"),
                USERS.SETTINGS.getText("city"), USERS.SETTINGS.pathText("n", "0"), USERS.SETTINGS.hasKey("city"),
                USERS.SETTINGS.hasAnyKey("x", "city"), USERS.SETTINGS.hasAllKeys("n", "city"), USERS.SETTINGS.pathExists("$.n[*] ? (@ > 1)"),
                USERS.SETTINGS.pathMatch("$.n[0] == 1"), USERS.SETTINGS.containsJson("{\"city\": \"London\"}"),
                USERS.SETTINGS.deleteKey("n"), USERS.SETTINGS.concat(jsonb(param("{\"z\": 1}"))));
        assertTrue(((String) r.get(1)).contains("\"n\": \"Ada\""));
        assertTrue(((String) r.get(8)).contains("Paris"));
        assertEquals("object", r.get(10));
        assertEquals(2, r.get(11));
        assertEquals("2", r.get(14));
        assertEquals("London", r.get(16));
        assertEquals("1", r.get(17));
        for (int i = 18; i <= 23; i++) assertEquals(true, r.get(i), "index " + i);
        assertEquals(List.of("1", "2"), ctx.select(jsonbArrayElementsText(USERS.SETTINGS.get("n"))).from(USERS).fetch());
        assertEquals(2, ctx.select(jsonbArrayElements(USERS.SETTINGS.get("n"))).from(USERS).fetch().size());
        assertEquals(List.of("city", "n"), ctx.select(jsonbObjectKeys(USERS.SETTINGS)).from(USERS).fetch().stream().sorted().toList());
        assertEquals(2, ctx.select(jsonbPathQuery(USERS.SETTINGS, "$.n[*]")).from(USERS).fetch().size());
    }

    @Test
    void arrayFunctions() {
        Row r = row(array(USERS.NAME, param("x")), arrayOf(ctx.select(ORDERS.ID).from(ORDERS).orderBy(ORDERS.ID.asc())),
                arrayLength(USERS.TAGS, 1), cardinality(USERS.TAGS), USERS.TAGS.append("new"), arrayPrepend("first", USERS.TAGS),
                arrayCat(USERS.TAGS, USERS.TAGS), USERS.TAGS.remove("vip"), arrayReplace(USERS.TAGS, "vip", "VIP"),
                arrayPosition(USERS.TAGS, "math"), arrayPositions(USERS.TAGS, "math"), arrayToString(USERS.TAGS, "|"),
                arrayLower(USERS.TAGS, 1), arrayUpper(USERS.TAGS, 1), USERS.TAGS.element(1), USERS.TAGS.contains(new String[]{"vip"}),
                USERS.TAGS.overlaps(new String[]{"x", "math"}), USERS.TAGS.hasElement("vip"), USERS.TAGS.containedBy(new String[]{"vip", "math", "x"}));
        assertArrayEquals(new String[]{"Ada", "x"}, (String[]) r.get(0));
        assertEquals(2, ((Long[]) r.get(1)).length);
        assertEquals(2, r.get(3));
        assertEquals("vip|math", r.get(11));
        assertEquals("vip", r.get(14));
        for (int i = 15; i <= 18; i++) assertEquals(true, r.get(i));
    }

    @Test
    void fullTextSearchAndMisc() {
        Field<String> doc = toTsvector("english", USERS.NAME.concat(" loves mathematics"));
        Row r = row(tsMatches(doc, websearchToTsquery("english", "mathematics")), tsRank(doc, plaintoTsquery("english", "ada")),
                tsRankCd(doc, toTsquery("english", "ada & math:*")), tsHeadline("english", USERS.NAME, phrasetoTsquery("english", "ada")),
                setweight(doc, 'A'), toTsvector(USERS.NAME), genRandomUuid(), currentUser(), sessionUser(), currentSchema(),
                currentDatabase(), version(), pgTypeof(USERS.TAGS), nextval("orders_id_seq"), currval("orders_id_seq"), lastval(),
                websearchToTsquery("simple", USERS.NAME));
        assertEquals(true, r.get(0));
        assertTrue((Float) r.get(1) > 0);
        assertEquals("<b>Ada</b>", r.get(3));
        assertInstanceOf(UUID.class, r.get(6));
        assertEquals("text[]", r.get(12));
        assertEquals(r.get(13), r.get(14));
        assertEquals(10L, ctx.select(setval("orders_id_seq", 10L)).fetchOne());
    }

    @Test
    void conditionalExpressionsAndRoutines() {
        Row r = row(coalesce(USERS.DELETED_AT, USERS.CREATED_AT), greatest(param(1), param(3), param(2)), least(param(1), param(3)),
                nullif(param(1), param(1)), caseWhen(USERS.ACTIVE, param("on")).otherwise("off"),
                caseOf(USERS.NAME).when("Ada", param("A")).otherwise("?"), not(USERS.ACTIVE), and(USERS.ACTIVE, noCondition()),
                or(USERS.ACTIVE.not(), USERS.ACTIVE));
        assertEquals(3, r.get(1));
        assertNull(r.get(3));
        assertEquals("on", r.get(4));
        assertEquals("A", r.get(5));
        assertEquals(false, r.get(6));
        assertEquals(true, r.get(8));
        Routines.Function2<String, Integer, String> repeat = Routines.function("repeat", SqlTypes.TEXT, SqlTypes.INT4, SqlTypes.TEXT);
        assertEquals("AdaAda", ctx.select(repeat.call(USERS.NAME, 2)).from(USERS).fetchOne());
        Routines.BinaryOperator<Integer, Integer, Integer> shift = Routines.operator("<<", SqlTypes.INT4, SqlTypes.INT4, SqlTypes.INT4);
        assertEquals(8, ctx.select(shift.apply(param(1), 3)).fetchOne());
        assertEquals(new BigDecimal("40.00"), ctx.select(Routines.aggregate("sum", SqlTypes.NUMERIC, SqlTypes.NUMERIC).call(ORDERS.TOTAL))
                .from(ORDERS).fetchOne());
    }
}
