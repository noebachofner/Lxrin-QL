package ch.lxrin.ql.dsl;

import ch.lxrin.ql.TestSchema.Role;
import ch.lxrin.ql.TestSchema.UsersTable;
import ch.lxrin.ql.render.RenderContext;
import ch.lxrin.ql.types.SqlTypes;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static ch.lxrin.ql.RenderTestSupport.assertSql;
import static ch.lxrin.ql.RenderTestSupport.sql;
import static ch.lxrin.ql.TestSchema.OrdersTable.ORDERS;
import static ch.lxrin.ql.TestSchema.UsersTable.USERS;
import static ch.lxrin.ql.dsl.Values.*;
import static org.junit.jupiter.api.Assertions.*;

class FieldTest {

    @Test
    void comparisonsBindValues() {
        Instant t = Instant.parse("2024-01-01T00:00:00Z");
        assertSql("users.created_at > ?", USERS.CREATED_AT.gt(t), t);
        assertSql("users.name = ?", USERS.NAME.eq("ACTIVE"), "ACTIVE");
        assertSql("users.role <> ?", USERS.ROLE.ne(Role.ADMIN), Role.ADMIN);
        assertSql("users.version >= ?", USERS.VERSION.ge(3L), 3L);
        assertSql("users.version < ?", USERS.VERSION.lt(3L), 3L);
        assertSql("users.version <= ?", USERS.VERSION.le(3L), 3L);
        assertSql("orders.user_id = users.id", ORDERS.USER_ID.eq(USERS.ID));
    }

    @Test
    void aStringIsAlwaysAValue() {
        assertSql("users.name = ?", USERS.NAME.eq("users.email"), "users.email");
        assertSql("users.name = ?", USERS.NAME.eq("x' OR '1'='1"), "x' OR '1'='1");
    }

    @Test
    void nullIsRejectedForEquality() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> USERS.NAME.eq((String) null));
        assertTrue(e.getMessage().contains("isNull"));
        assertSql("users.deleted_at IS NULL", USERS.DELETED_AT.eqOrIsNull(null));
        assertSql("users.name IS DISTINCT FROM ?", USERS.NAME.isDistinctFrom((String) null), (Object) null);
        assertSql("users.name IS NOT DISTINCT FROM ?", USERS.NAME.isNotDistinctFrom("a"), "a");
    }

    @Test
    void nullChecks() {
        assertSql("users.deleted_at IS NULL", USERS.DELETED_AT.isNull());
        assertSql("users.deleted_at IS NOT NULL", USERS.DELETED_AT.isNotNull());
    }

    @Test
    void inBindsOneArraySoTheSqlIsStable() {
        assertSql("users.role = ANY(?)", USERS.ROLE.in(List.of(Role.ADMIN, Role.USER)), (Object) new Role[]{Role.ADMIN, Role.USER});
        assertEquals(sql(USERS.NAME.in(List.of("a"))), sql(USERS.NAME.in(List.of("a", "b", "c"))));
        assertSql("users.name = ANY(?)", USERS.NAME.in(Set.of()), (Object) new String[0]);
        assertSql("users.name <> ALL(?)", USERS.NAME.notIn(List.of("a")), (Object) new String[]{"a"});
    }

    @Test
    void between() {
        assertSql("users.version BETWEEN ? AND ?", USERS.VERSION.between(1L, 5L), 1L, 5L);
        assertSql("users.version NOT BETWEEN ? AND ?", USERS.VERSION.notBetween(1L, 5L), 1L, 5L);
        assertSql("users.created_at BETWEEN users.created_at AND users.deleted_at",
                USERS.CREATED_AT.between(USERS.CREATED_AT, USERS.DELETED_AT));
    }

    @Test
    void stringOperationsEscapePatterns() {
        assertSql("users.email LIKE ?", USERS.EMAIL.endsWith("@gmail.com"), "%@gmail.com");
        assertSql("users.email LIKE ?", USERS.EMAIL.startsWith("50%_off\\"), "50\\%\\_off\\\\%");
        assertSql("users.email LIKE ?", USERS.EMAIL.contains("a"), "%a%");
        assertSql("users.email ILIKE ?", USERS.EMAIL.containsIgnoreCase("a"), "%a%");
        assertSql("users.email ILIKE ?", USERS.EMAIL.startsWithIgnoreCase("a"), "a%");
        assertSql("users.email ILIKE ?", USERS.EMAIL.endsWithIgnoreCase("a"), "%a");
        assertSql("users.email LIKE ?", USERS.EMAIL.like("a%"), "a%");
        assertSql("users.email NOT LIKE ?", USERS.EMAIL.notLike("a%"), "a%");
        assertSql("users.email ILIKE ?", USERS.EMAIL.ilike("a%"), "a%");
        assertSql("users.email NOT ILIKE ?", USERS.EMAIL.notIlike("a%"), "a%");
        assertSql("users.email ~ ?", USERS.EMAIL.matches("^a"), "^a");
        assertSql("users.email ~* ?", USERS.EMAIL.matchesIgnoreCase("^a"), "^a");
        assertSql("users.email !~ ?", USERS.EMAIL.notMatches("^a"), "^a");
        assertSql("users.email SIMILAR TO ?", USERS.EMAIL.similarTo("a%"), "a%");
        assertSql("(users.name || ?)", USERS.NAME.concat("!"), "!");
        assertSql("(users.name || users.email)", USERS.NAME.concat(USERS.EMAIL));
        assertSql("lower(users.name)", USERS.NAME.lower());
        assertSql("upper(btrim(users.name))", USERS.NAME.trim().upper());
        assertSql("char_length(users.name) > ?", USERS.NAME.length().gt(3), 3);
    }

    @Test
    void numberOperations() {
        assertSql("(users.version + ?)", USERS.VERSION.plus(1L), 1L);
        assertSql("((users.version * ?) - users.version)", USERS.VERSION.times(2L).minus(USERS.VERSION), 2L);
        assertSql("(users.version / ?)", USERS.VERSION.divide(2L), 2L);
        assertSql("(users.version % ?)", USERS.VERSION.mod(2L), 2L);
        assertSql("abs((- users.version))", USERS.VERSION.neg().abs());
        assertSql("(orders.total + users.version)", ORDERS.TOTAL.plus(USERS.VERSION));
    }

    @Test
    void temporalOperations() {
        assertSql("CAST((users.created_at + CAST(? AS interval)) AS timestamptz)",
                USERS.CREATED_AT.plus(Duration.ofDays(1)), Duration.ofDays(1));
        assertSql("CAST((orders.ordered_on - CAST(? AS interval)) AS date)",
                ORDERS.ORDERED_ON.minus(Duration.ofDays(7)), Duration.ofDays(7));
        assertSql("CAST(date_trunc('month', users.created_at) AS timestamptz)", USERS.CREATED_AT.truncate(DatePart.MONTH));
        assertSql("date_trunc('day', users.created_at, 'Europe/Zurich')",
                USERS.CREATED_AT.truncate(DatePart.DAY, ZoneId.of("Europe/Zurich")));
        assertSql("EXTRACT(YEAR FROM orders.ordered_on)", ORDERS.ORDERED_ON.extract(DatePart.YEAR));
    }

    @Test
    void jsonOperationsUseLiteralKeys() {
        assertSql("(users.settings -> 'theme')", USERS.SETTINGS.get("theme"));
        assertSql("(users.settings ->> 'theme') = ?", USERS.SETTINGS.getText("theme").eq("dark"), "dark");
        assertSql("(users.settings -> 0)", USERS.SETTINGS.get(0));
        assertSql("(users.settings ->> 1)", USERS.SETTINGS.getText(1));
        assertSql("(users.settings #> '{\"a\",\"b\"}')", USERS.SETTINGS.path("a", "b"));
        assertSql("(users.settings #>> '{\"a\"}')", USERS.SETTINGS.pathText("a"));
        assertSql("users.settings @> ?", USERS.SETTINGS.containsJson("{\"x\":1}"), "{\"x\":1}");
        assertSql("users.settings @> ?", USERS.SETTINGS.contains("{}"), "{}");
        assertSql("users.settings <@ ?", USERS.SETTINGS.containedBy("{}"), "{}");
        assertSql("jsonb_exists(users.settings, 'a')", USERS.SETTINGS.hasKey("a"));
        assertSql("jsonb_exists_any(users.settings, '{\"a\",\"b\"}')", USERS.SETTINGS.hasAnyKey("a", "b"));
        assertSql("jsonb_exists_all(users.settings, '{\"a\"}')", USERS.SETTINGS.hasAllKeys("a"));
        assertSql("jsonb_path_exists(users.settings, '$.a ? (@ > 1)'::jsonpath)", USERS.SETTINGS.pathExists("$.a ? (@ > 1)"));
        assertSql("jsonb_path_match(users.settings, '$.a > 1'::jsonpath)", USERS.SETTINGS.pathMatch("$.a > 1"));
        assertSql("(users.settings - 'a')", USERS.SETTINGS.deleteKey("a"));
        assertSql("(users.settings || users.settings)", USERS.SETTINGS.concat(USERS.SETTINGS));
        assertSql("(users.settings ->> 'x''y')", USERS.SETTINGS.getText("x'y"));
    }

    @Test
    void arrayOperations() {
        String[] vip = {"vip"};
        assertSql("users.tags @> ?", USERS.TAGS.contains(vip), (Object) vip);
        assertSql("users.tags <@ ?", USERS.TAGS.containedBy(vip), (Object) vip);
        assertSql("users.tags && ?", USERS.TAGS.overlaps(vip), (Object) vip);
        assertSql("? = ANY(users.tags)", USERS.TAGS.hasElement("vip"), "vip");
        assertSql("cardinality(users.tags)", USERS.TAGS.length());
        assertSql("(users.tags)[1]", USERS.TAGS.element(1));
        assertSql("array_append(users.tags, ?)", USERS.TAGS.append("x"), "x");
        assertSql("array_remove(users.tags, ?)", USERS.TAGS.remove("x"), "x");
        assertSame(SqlTypes.TEXT, USERS.TAGS.elementType());
    }

    @Test
    void conditionsCombine() {
        Condition a = USERS.ROLE.eq(Role.ADMIN);
        Condition b = USERS.DELETED_AT.isNull();
        assertSql("(users.role = ? AND users.deleted_at IS NULL)", a.and(b), Role.ADMIN);
        assertSql("(users.role = ? OR users.deleted_at IS NULL)", a.or(b), Role.ADMIN);
        assertSql("NOT (users.role = ?)", a.not(), Role.ADMIN);
        assertSql("((users.role = ? AND users.active) OR users.deleted_at IS NULL)", a.and(USERS.ACTIVE).or(b), Role.ADMIN);
        assertSql("users.active IS TRUE", USERS.ACTIVE.isTrue());
        assertSql("users.active IS NOT TRUE", USERS.ACTIVE.isNotTrue());
        assertSql("users.active IS FALSE", USERS.ACTIVE.isFalse());
        assertSql("users.active IS NOT FALSE", USERS.ACTIVE.isNotFalse());
        assertSql("(users.role = ?) IS NULL", a.isNull(), Role.ADMIN);
        assertSql("users.role = ?", a.andIf(false, () -> b), Role.ADMIN);
    }

    @Test
    void noConditionIsNeutral() {
        Condition none = Condition.noCondition();
        Condition a = USERS.ACTIVE.isTrue();
        assertSame(a, none.and(a));
        assertSame(a, a.or(none));
        assertSame(none, Condition.and());
        assertSql("TRUE", none);
        assertSql("(users.active IS TRUE AND users.deleted_at IS NULL)",
                Condition.and(null, a, none, USERS.DELETED_AT.isNull()));
        assertSame(a, Condition.or(List.of(a)));
    }

    @Test
    void aliasesDeclareOnlyInSelectLists() {
        StringField upper = USERS.NAME.upper().as("n");
        assertSql("n", upper);
        RenderContext ctx = new RenderContext();
        ctx.declaring(List.of(upper, USERS.ID), ", ");
        assertEquals("upper(users.name) AS n, users.id", ctx.sql());
        assertEquals("n", upper.name());
        assertEquals("m", upper.as("m").name());
        assertThrows(IllegalArgumentException.class, () -> USERS.NAME.as(""));
        assertSql("\"Order\"", USERS.NAME.as("Order"));
    }

    @Test
    void castCoalesceNullIf() {
        assertSql("CAST(users.version AS text)", USERS.VERSION.cast(SqlTypes.TEXT));
        assertTrue(USERS.VERSION.cast(SqlTypes.TEXT) instanceof StringField);
        assertSql("COALESCE(users.name, ?)", USERS.NAME.coalesce("?"), "?");
        assertSql("COALESCE(users.deleted_at, users.created_at)", USERS.DELETED_AT.coalesce(USERS.CREATED_AT));
        assertSql("NULLIF(users.name, ?)", USERS.NAME.nullIf(""), "");
    }

    @Test
    void sortFields() {
        assertSql("users.name ASC", USERS.NAME.asc());
        assertSql("users.name DESC NULLS LAST", USERS.NAME.desc().nullsLast());
        assertSql("users.name ASC NULLS FIRST", USERS.NAME.asc().nullsFirst());
        assertEquals(Boolean.TRUE, USERS.NAME.asc().nullsFirst().nullOrdering());
    }

    @Test
    void aliasedTablesQualifyColumns() {
        UsersTable u = USERS.as("u");
        assertSql("u.name = users.name", u.NAME.eq(USERS.NAME));
        assertSql("users AS u", u);
        assertNotEquals(u.NAME, USERS.NAME);
        assertEquals(USERS.as("u").NAME, u.NAME);
    }

    @Test
    void valuesAndInlineLiterals() {
        assertSql("?", param("x"), "x");
        assertSql("?", value(42L), 42L);
        assertSql("'O''Brien'", inline("O'Brien"));
        assertSql("42", inline(42));
        assertSql("TRUE", inline(true));
        assertSql("CAST(NULL AS text)", nullValue(SqlTypes.TEXT));
        assertSql("'ADMIN'::role", inline(Role.ADMIN, ch.lxrin.ql.TestSchema.ROLE_TYPE));
        assertThrows(IllegalArgumentException.class, () -> value(null));
    }

    @Test
    void rawSqlGoesThroughSqlOnly() {
        Field<Double> score = Sql.raw("similarity({0}, {1})", SqlTypes.FLOAT8, USERS.NAME, param("ada"));
        assertSql("similarity(users.name, ?) > ?", score.gt(0.3), "ada", 0.3);
        assertTrue(score instanceof NumberField);
        assertSql("(users.name @@ {x} ?)", Sql.condition("{0} @@ {x} {1}", USERS.NAME, param(1)), 1);
        assertThrows(IllegalArgumentException.class, () -> sql(Sql.raw("f({2})", SqlTypes.TEXT, USERS.NAME)));
        assertSql("legacy.t", Sql.table("legacy", "t"));
        assertSql("x.y", Sql.table("x").field("y", SqlTypes.TEXT));
    }

    @Test
    void columnMetadata() {
        assertTrue(USERS.DELETED_AT.nullable());
        assertFalse(USERS.NAME.nullable());
        assertTrue(USERS.ROLE.hasDefault());
        assertTrue(USERS.ID.primaryKey());
        assertSame(USERS, USERS.NAME.table());
        assertEquals(10, USERS.columns().size());
        assertSame(USERS.NAME, USERS.column("name").orElseThrow());
        assertSame(USERS.DELETED_AT, USERS.column("deleted_at", SqlTypes.TIMESTAMPTZ));
        assertThrows(IllegalArgumentException.class, () -> USERS.column("deleted_at", SqlTypes.TEXT));
        assertThrows(IllegalArgumentException.class, () -> USERS.column("nope", SqlTypes.TEXT));
        assertSql("users.name = ?", USERS.NAME.eqUnchecked("a"), "a");
        assertThrows(IllegalArgumentException.class, () -> USERS.NAME.eqUnchecked(1));
    }

    @Test
    void rowAccessByField() {
        Row.Shape shape = new Row.Shape(List.of(USERS.ID, USERS.NAME, USERS.EMAIL.as("mail")));
        Row row = shape.row(new Object[]{null, "Ada", "ada@example.org"});
        assertEquals("Ada", row.get(USERS.NAME));
        assertEquals("ada@example.org", row.get("mail"));
        assertEquals("Ada", row.get(USERS.as("x").NAME.equals(USERS.NAME) ? USERS.NAME : USERS.NAME));
        assertThrows(IllegalArgumentException.class, () -> row.get(USERS.ROLE));
        assertEquals(3, row.size());
        assertTrue(row.toString().contains("name=Ada"));
    }
}
