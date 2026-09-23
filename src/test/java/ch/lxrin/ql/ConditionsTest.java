package ch.lxrin.ql;

import ch.lxrin.ql.TestTables.PersonTable;
import ch.lxrin.ql.condition.Condition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ch.lxrin.ql.LxrinQL.*;
import static org.junit.jupiter.api.Assertions.*;

class ConditionsTest {

    private final PersonTable p = new PersonTable();

    @Test
    void comparisons() {
        assertEquals("p.STATUS = :status", eq(p.status, ":status").toSql());
        assertEquals("t.A <> t.B", ne("t.A", "t.B").toSql());
        assertEquals("p.AGE > :lq0", gt(p.age, 18).toSql());
        assertEquals("p.AGE >= :lq0", ge(p.age, 18).toSql());
        assertEquals("p.AGE < :lq0", lt(p.age, val(18)).toSql());
        assertEquals("p.AGE <= 65", le(p.age, inline(65)).toSql());
        assertEquals("p.STATUS IS DISTINCT FROM NULL", isDistinctFrom(p.status, null).toSql());
        assertEquals("p.STATUS IS NOT DISTINCT FROM :lq0", isNotDistinctFrom(p.status, val("A")).toSql());
        assertEquals("p.AGE <-> :lq0", compare(p.age, "<->", 3).toSql());
    }

    @Test
    void comparison_rejectsJavaNull() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> eq(p.status, null));
        assertTrue(e.getMessage().contains("isNull"));
        assertEquals("p.STATUS = :lq0", eq(p.status, val(null)).toSql());
    }

    @Test
    void fluentComparisonsOnColumns() {
        assertEquals("p.AGE >= :lq0", p.age.ge(18).toSql());
        assertEquals("p.LAST_NAME ILIKE 'A%'", p.lastName.ilike(inline("A%")).toSql());
        assertEquals("p.STATUS IS NULL", p.status.isNull().toSql());
        assertEquals("p.AGE BETWEEN 18 AND 65", p.age.between(inline(18), inline(65)).toSql());
        assertEquals("p.AGE IN (:lq0, :lq1)", p.age.in(1, 2).toSql());
    }

    @Test
    void patternMatching() {
        assertEquals("p.LAST_NAME LIKE :x", like(p.lastName, ":x").toSql());
        assertEquals("p.LAST_NAME NOT LIKE :x", notLike(p.lastName, ":x").toSql());
        assertEquals("p.LAST_NAME ILIKE :x", ilike(p.lastName, ":x").toSql());
        assertEquals("p.LAST_NAME NOT ILIKE :x", notIlike(p.lastName, ":x").toSql());
        assertEquals("p.LAST_NAME SIMILAR TO :x", similarTo(p.lastName, ":x").toSql());
        assertEquals("p.LAST_NAME NOT SIMILAR TO :x", notSimilarTo(p.lastName, ":x").toSql());
        assertEquals("p.LAST_NAME ~ :x", matches(p.lastName, ":x").toSql());
        assertEquals("p.LAST_NAME ~* :x", matchesIgnoreCase(p.lastName, ":x").toSql());
        assertEquals("p.LAST_NAME !~ :x", notMatches(p.lastName, ":x").toSql());
        assertEquals("p.LAST_NAME !~* :x", notMatchesIgnoreCase(p.lastName, ":x").toSql());
    }

    @Test
    void in_variants() {
        assertEquals("p.STATUS IN (:a, :b)", in(p.status, ":a", ":b").toSql());
        assertEquals("p.PERSON_NR IN (:lq0, :lq1, :lq2)", in(p.personNr, List.of(1L, 2L, 3L)).toSql());
        assertEquals("p.PERSON_NR IN (:lq0, :lq1)", in(p.personNr, (Object) new long[]{1, 2}).toSql());
        assertEquals("FALSE", in(p.personNr, List.of()).toSql());
        assertEquals("TRUE", notIn(p.personNr, List.of()).toSql());
        assertEquals("p.PERSON_NR NOT IN (:lq0)", notIn(p.personNr, 5L).toSql());
        assertEquals("p.PERSON_NR IN (SELECT o.PERSON_NR FROM ORDERS o)",
                in(p.personNr, select("o.PERSON_NR").from("ORDERS o")).toSql());
    }

    @Test
    void betweenNullAndBooleanTests() {
        assertEquals("p.AGE BETWEEN :a AND :b", between(p.age, ":a", ":b").toSql());
        assertEquals("p.AGE NOT BETWEEN :a AND :b", notBetween(p.age, ":a", ":b").toSql());
        assertEquals("p.AGE BETWEEN SYMMETRIC :a AND :b", betweenSymmetric(p.age, ":a", ":b").toSql());
        assertEquals("p.STATUS IS NULL", isNull(p.status).toSql());
        assertEquals("p.STATUS IS NOT NULL", isNotNull(p.status).toSql());
        assertEquals("t.ACTIVE IS TRUE", isTrue("t.ACTIVE").toSql());
        assertEquals("t.ACTIVE IS NOT TRUE", isNotTrue("t.ACTIVE").toSql());
        assertEquals("t.ACTIVE IS FALSE", isFalse("t.ACTIVE").toSql());
        assertEquals("t.ACTIVE IS NOT FALSE", isNotFalse("t.ACTIVE").toSql());
    }

    @Test
    void existsAnyAll() {
        assertEquals("EXISTS (SELECT 1 FROM ORDERS o)", exists(select(inline(1)).from("ORDERS o")).toSql());
        assertEquals("NOT EXISTS (SELECT 1)", notExists(select(inline(1))).toSql());
        assertEquals("p.PERSON_NR = ANY(:lq0)", eq(p.personNr, any(val(new Long[]{1L}))).toSql());
        assertEquals("p.AGE > ALL(SELECT o.TOTAL FROM ORDERS o)", gt(p.age, all(select("o.TOTAL").from("ORDERS o"))).toSql());
    }

    @Test
    void containmentJsonAndFullText() {
        assertEquals("t.TAGS @> :lq0", contains("t.TAGS", val(new String[]{"a"})).toSql());
        assertEquals("t.TAGS <@ t.ALL_TAGS", containedBy("t.TAGS", "t.ALL_TAGS").toSql());
        assertEquals("t.TAGS && t.OTHER", overlaps("t.TAGS", "t.OTHER").toSql());
        assertEquals("jsonb_exists(t.DATA, 'k')", jsonHasKey("t.DATA", inline("k")).toSql());
        assertEquals("jsonb_exists_any(t.DATA, :lq0)", jsonHasAnyKey("t.DATA", val(new String[]{"a"})).toSql());
        assertEquals("jsonb_exists_all(t.DATA, :lq0)", jsonHasAllKeys("t.DATA", val(new String[]{"a"})).toSql());
        assertEquals("t.DOC @@ to_tsquery(:lq0)", tsMatches("t.DOC", toTsquery(val("cat"))).toSql());
    }

    @Test
    void logicalCombinations() {
        assertEquals("AND", and().toSql());
        assertEquals("OR", or().toSql());
        assertEquals("(p.AGE > :lq0 AND p.STATUS IS NULL)", and(gt(p.age, 1), isNull(p.status)).toSql());
        assertEquals("(p.AGE > :lq0 OR p.STATUS IS NULL)", or(gt(p.age, 1), isNull(p.status)).toSql());
        assertEquals("p.STATUS IS NULL", and(null, isNull(p.status), null).toSql());
        assertEquals("TRUE", and((Object) null).toSql());
        assertEquals("FALSE", or((Object) null).toSql());
        assertEquals("NOT (p.STATUS IS NULL)", not(isNull(p.status)).toSql());
        assertEquals("(p.STATUS IS NULL OR p.AGE > :lq0)", group(isNull(p.status), or(), gt(p.age, 3)).toSql());
        assertEquals("(p.STATUS IS NULL AND p.AGE > :lq0)", group(isNull(p.status), gt(p.age, 3)).toSql());
    }

    @Test
    void fluentLogicalCombinations() {
        Condition c = p.status.eq(val("A")).and(p.age.ge(18)).or(p.status.isNull());
        assertEquals("((p.STATUS = :lq0 AND p.AGE >= :lq1) OR p.STATUS IS NULL)", c.toSql());
        assertEquals("NOT (p.AGE >= :lq0)", p.age.ge(18).not().toSql());
    }

    @Test
    void rawAndLambdaConditions() {
        assertEquals("t.ACTIVE", condition("t.ACTIVE").toSql());
        assertEquals("starts_with(p.LAST_NAME, 'A')", startsWith(p.lastName, inline("A")).toSql());
        Condition custom = ctx -> ctx.append("my_check(").visit(p.personNr).append(")");
        assertEquals("my_check(p.PERSON_NR)", custom.toSql());
    }

    @Test
    void invalidArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> eq("", ":x"));
        assertThrows(IllegalArgumentException.class, () -> eq((Object) null, ":x"));
        assertThrows(IllegalArgumentException.class, () -> isNull(null));
        assertThrows(IllegalArgumentException.class, () -> compare(p.age, " ", 1));
        assertThrows(IllegalArgumentException.class, () -> condition(" "));
    }
}
