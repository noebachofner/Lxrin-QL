package ch.lxrin.ql.dsl;

import ch.lxrin.ql.TestSchema.Role;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static ch.lxrin.ql.RenderTestSupport.assertSql;
import static ch.lxrin.ql.RenderTestSupport.sql;
import static ch.lxrin.ql.TestSchema.BookingsTable.BOOKINGS;
import static ch.lxrin.ql.TestSchema.OrdersTable.ORDERS;
import static ch.lxrin.ql.TestSchema.UsersTable.USERS;
import static ch.lxrin.ql.dsl.Dsl.*;
import static org.junit.jupiter.api.Assertions.*;

/** Every operator in its static and its fluent form: same SQL, same binds. */
class ConditionsTest {

    private static final Instant T1 = Instant.parse("2025-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2025-12-31T00:00:00Z");

    /** Asserts that both forms render {@code expected} with the given binds. */
    private static void same(String expected, Condition staticForm, Condition fluentForm, Object... binds) {
        assertSql(expected, staticForm, binds);
        assertSql(expected, fluentForm, binds);
    }

    @Nested
    class Comparison {
        @Test
        void valuesAndFields() {
            same("users.name = ?", eq(USERS.NAME, "Ada"), USERS.NAME.eq("Ada"), "Ada");
            same("users.name <> ?", ne(USERS.NAME, "Ada"), USERS.NAME.ne("Ada"), "Ada");
            same("users.version > ?", gt(USERS.VERSION, 1L), USERS.VERSION.gt(1L), 1L);
            same("users.version >= ?", ge(USERS.VERSION, 1L), USERS.VERSION.ge(1L), 1L);
            same("users.version < ?", lt(USERS.VERSION, 1L), USERS.VERSION.lt(1L), 1L);
            same("users.version <= ?", le(USERS.VERSION, 1L), USERS.VERSION.le(1L), 1L);
            same("users.name = users.email", eq(USERS.NAME, USERS.EMAIL), USERS.NAME.eq(USERS.EMAIL));
            same("users.name <> users.email", ne(USERS.NAME, USERS.EMAIL), USERS.NAME.ne(USERS.EMAIL));
            same("users.created_at > users.deleted_at", gt(USERS.CREATED_AT, USERS.DELETED_AT), USERS.CREATED_AT.gt(USERS.DELETED_AT));
            same("users.created_at >= users.deleted_at", ge(USERS.CREATED_AT, USERS.DELETED_AT), USERS.CREATED_AT.ge(USERS.DELETED_AT));
            same("users.created_at < users.deleted_at", lt(USERS.CREATED_AT, USERS.DELETED_AT), USERS.CREATED_AT.lt(USERS.DELETED_AT));
            same("users.created_at <= users.deleted_at", le(USERS.CREATED_AT, USERS.DELETED_AT), USERS.CREATED_AT.le(USERS.DELETED_AT));
        }

        @Test
        void distinctFrom() {
            same("users.deleted_at IS DISTINCT FROM ?", isDistinctFrom(USERS.DELETED_AT, T1), USERS.DELETED_AT.isDistinctFrom(T1), T1);
            same("users.deleted_at IS DISTINCT FROM ?", isDistinctFrom(USERS.DELETED_AT, (Instant) null),
                    USERS.DELETED_AT.isDistinctFrom((Instant) null), (Object) null);
            same("users.deleted_at IS NOT DISTINCT FROM users.created_at", isNotDistinctFrom(USERS.DELETED_AT, USERS.CREATED_AT),
                    USERS.DELETED_AT.isNotDistinctFrom(USERS.CREATED_AT));
            same("users.name IS DISTINCT FROM users.email", isDistinctFrom(USERS.NAME, USERS.EMAIL), USERS.NAME.isDistinctFrom(USERS.EMAIL));
            same("users.name IS NOT DISTINCT FROM ?", isNotDistinctFrom(USERS.NAME, "x"), USERS.NAME.isNotDistinctFrom("x"), "x");
        }

        @Test
        void nullValuesAreRejectedNotDropped() {
            assertThrows(IllegalArgumentException.class, () -> eq(USERS.NAME, (String) null));
            assertThrows(IllegalArgumentException.class, () -> USERS.NAME.gt((String) null));
            assertThrows(IllegalArgumentException.class, () -> between(USERS.VERSION, 1L, null));
            assertThrows(IllegalArgumentException.class, () -> like(USERS.NAME, (String) null));
            assertThrows(IllegalArgumentException.class, () -> in(USERS.NAME, (List<String>) null));
            assertThrows(IllegalArgumentException.class, () -> eq(USERS.NAME, (Field<String>) null));
            assertThrows(IllegalArgumentException.class, () -> USERS.SETTINGS.jsonContains((String) null));
            assertThrows(IllegalArgumentException.class, () -> BOOKINGS.PERIOD.rangeContains((Instant) null));
        }
    }

    @Nested
    class Between {
        @Test
        void allForms() {
            same("users.version BETWEEN ? AND ?", between(USERS.VERSION, 1L, 5L), USERS.VERSION.between(1L, 5L), 1L, 5L);
            same("users.version NOT BETWEEN ? AND ?", notBetween(USERS.VERSION, 1L, 5L), USERS.VERSION.notBetween(1L, 5L), 1L, 5L);
            same("users.version BETWEEN SYMMETRIC ? AND ?", betweenSymmetric(USERS.VERSION, 5L, 1L),
                    USERS.VERSION.betweenSymmetric(5L, 1L), 5L, 1L);
            same("users.version NOT BETWEEN SYMMETRIC ? AND ?", notBetweenSymmetric(USERS.VERSION, 5L, 1L),
                    USERS.VERSION.notBetweenSymmetric(5L, 1L), 5L, 1L);
            same("users.created_at BETWEEN users.deleted_at AND now()", between(USERS.CREATED_AT, USERS.DELETED_AT, now()),
                    USERS.CREATED_AT.between(USERS.DELETED_AT, now()));
            same("users.created_at NOT BETWEEN users.deleted_at AND now()", notBetween(USERS.CREATED_AT, USERS.DELETED_AT, now()),
                    USERS.CREATED_AT.notBetween(USERS.DELETED_AT, now()));
            same("users.created_at BETWEEN SYMMETRIC now() AND users.deleted_at", betweenSymmetric(USERS.CREATED_AT, now(), USERS.DELETED_AT),
                    USERS.CREATED_AT.betweenSymmetric(now(), USERS.DELETED_AT));
            same("users.created_at NOT BETWEEN SYMMETRIC now() AND users.deleted_at",
                    notBetweenSymmetric(USERS.CREATED_AT, now(), USERS.DELETED_AT), USERS.CREATED_AT.notBetweenSymmetric(now(), USERS.DELETED_AT));
        }
    }

    @Nested
    class InAnyAll {
        @Test
        void inWithVarargsCollectionAndBindList() {
            List<Object> ab = List.of("a", "b");
            same("users.name = ANY(?)", in(USERS.NAME, "a", "b"), USERS.NAME.in("a", "b"), ab);
            same("users.name = ANY(?)", in(USERS.NAME, List.of("a", "b")), USERS.NAME.in(List.of("a", "b")), ab);
            same("users.name = ANY(?)", in(USERS.NAME, Binds.INSTANCE.setList("a", "b")),
                    USERS.NAME.in(Binds.INSTANCE.setList(List.of("a", "b"))), ab);
            same("users.name <> ALL(?)", notIn(USERS.NAME, "a", "b"), USERS.NAME.notIn("a", "b"), ab);
            same("users.name <> ALL(?)", notIn(USERS.NAME, List.of("a", "b")), USERS.NAME.notIn(List.of("a", "b")), ab);
            same("users.name <> ALL(?)", notIn(USERS.NAME, Binds.INSTANCE.setList(List.of("a", "b"))),
                    USERS.NAME.notIn(Binds.INSTANCE.setList("a", "b")), ab);
        }

        @Test
        void emptyInIsOneEmptyArrayParameter() {
            // = ANY('{}') is false and <> ALL('{}') is true in PostgreSQL, also for NULL operands
            same("users.name = ANY(?)", in(USERS.NAME, List.of()), USERS.NAME.in(), List.of());
            same("users.name <> ALL(?)", notIn(USERS.NAME, List.of()), USERS.NAME.notIn(), List.of());
        }

        @Test
        void inSubquery() {
            same("users.id IN (SELECT orders.user_id FROM orders)", in(USERS.ID, select(ORDERS.USER_ID).from(ORDERS)),
                    USERS.ID.in(select(ORDERS.USER_ID).from(ORDERS)));
            same("users.id NOT IN (SELECT orders.user_id FROM orders)", notIn(USERS.ID, select(ORDERS.USER_ID).from(ORDERS)),
                    USERS.ID.notIn(select(ORDERS.USER_ID).from(ORDERS)));
        }

        @Test
        void existsAndNotExists() {
            assertSql("EXISTS (SELECT 1 FROM orders WHERE orders.user_id = users.id)",
                    exists(selectOne().from(ORDERS).where(ORDERS.USER_ID.eq(USERS.ID))));
            assertSql("NOT EXISTS (SELECT 1 FROM orders)", notExists(selectOne().from(ORDERS)));
        }

        @Test
        void anyAndAllWithEveryComparison() {
            Select1<BigDecimal> totals = select(ORDERS.TOTAL).from(ORDERS);
            NumberField<BigDecimal> x = param(BigDecimal.ONE);
            same("? = ANY (SELECT orders.total FROM orders)", eq(x, any(totals)), x.eq(any(totals)), BigDecimal.ONE);
            same("? <> ALL (SELECT orders.total FROM orders)", ne(x, all(totals)), x.ne(all(totals)), BigDecimal.ONE);
            same("? > ALL (SELECT orders.total FROM orders)", gt(x, all(totals)), x.gt(all(totals)), BigDecimal.ONE);
            same("? >= ANY (SELECT orders.total FROM orders)", ge(x, any(totals)), x.ge(any(totals)), BigDecimal.ONE);
            same("? < ALL (SELECT orders.total FROM orders)", lt(x, all(totals)), x.lt(all(totals)), BigDecimal.ONE);
            same("? <= ANY (SELECT orders.total FROM orders)", le(x, any(totals)), x.le(any(totals)), BigDecimal.ONE);
        }

        @Test
        void valueComparedWithArrayColumn() {
            assertSql("? = ANY(users.tags)", eq("vip", any(USERS.TAGS)), "vip");
            assertSql("? <> ALL(users.tags)", ne("vip", all(USERS.TAGS)), "vip");
            assertSql("? > ANY(bookings.slots)", gt(3, any(BOOKINGS.SLOTS)), 3);
            assertSql("? >= ALL(bookings.slots)", ge(3, all(BOOKINGS.SLOTS)), 3);
            assertSql("? < ANY(bookings.slots)", lt(3, any(BOOKINGS.SLOTS)), 3);
            assertSql("? <= ALL(bookings.slots)", le(3, all(BOOKINGS.SLOTS)), 3);
            assertSql("users.name = ANY(users.tags)", USERS.NAME.eq(any(USERS.TAGS)));
            assertThrows(IllegalArgumentException.class, () -> eq((String) null, any(USERS.TAGS)));
        }
    }

    @Nested
    class NullAndBoolean {
        @Test
        void tests() {
            same("users.deleted_at IS NULL", isNull(USERS.DELETED_AT), USERS.DELETED_AT.isNull());
            same("users.deleted_at IS NOT NULL", isNotNull(USERS.DELETED_AT), USERS.DELETED_AT.isNotNull());
            same("users.active IS TRUE", isTrue(USERS.ACTIVE), USERS.ACTIVE.isTrue());
            same("users.active IS NOT TRUE", isNotTrue(USERS.ACTIVE), USERS.ACTIVE.isNotTrue());
            same("users.active IS FALSE", isFalse(USERS.ACTIVE), USERS.ACTIVE.isFalse());
            same("users.active IS NOT FALSE", isNotFalse(USERS.ACTIVE), USERS.ACTIVE.isNotFalse());
            same("(users.name = ?) IS TRUE", isTrue(USERS.NAME.eq("a")), USERS.NAME.eq("a").isTrue(), "a");
        }
    }

    @Nested
    class Text {
        @Test
        void likeFamily() {
            same("users.name LIKE ?", like(USERS.NAME, "A%"), USERS.NAME.like("A%"), "A%");
            same("users.name NOT LIKE ?", notLike(USERS.NAME, "A%"), USERS.NAME.notLike("A%"), "A%");
            same("users.name ILIKE ?", ilike(USERS.NAME, "a%"), USERS.NAME.ilike("a%"), "a%");
            same("users.name NOT ILIKE ?", notIlike(USERS.NAME, "a%"), USERS.NAME.notIlike("a%"), "a%");
            same("users.name LIKE users.email", like(USERS.NAME, USERS.EMAIL), USERS.NAME.like(USERS.EMAIL));
            same("users.name ILIKE users.email", ilike(USERS.NAME, USERS.EMAIL), USERS.NAME.ilike(USERS.EMAIL));
            assertSql("users.name NOT LIKE users.email", USERS.NAME.notLike(USERS.EMAIL));
            assertSql("users.name NOT ILIKE users.email", USERS.NAME.notIlike(USERS.EMAIL));
        }

        @Test
        void escapeCharacter() {
            same("users.name LIKE ? ESCAPE '!'", like(USERS.NAME, "100!%%", '!'), USERS.NAME.like("100!%%", '!'), "100!%%");
            same("users.name NOT LIKE ? ESCAPE '!'", notLike(USERS.NAME, "x!_", '!'), USERS.NAME.notLike("x!_", '!'), "x!_");
            same("users.name ILIKE ? ESCAPE '#'", ilike(USERS.NAME, "#%", '#'), USERS.NAME.ilike("#%", '#'), "#%");
            same("users.name NOT ILIKE ? ESCAPE '#'", notIlike(USERS.NAME, "#%", '#'), USERS.NAME.notIlike("#%", '#'), "#%");
            assertThrows(IllegalArgumentException.class, () -> USERS.NAME.like("x", '\''));
        }

        @Test
        void prefixSuffixContainsEscapeWildcards() {
            assertSql("users.name LIKE ?", USERS.NAME.startsWith("50%_"), "50\\%\\_%");
            same("users.name LIKE ?", endsWith(USERS.NAME, "_x"), USERS.NAME.endsWith("_x"), "%\\_x");
            same("users.name LIKE ?", contains(USERS.NAME, "a%b"), USERS.NAME.contains("a%b"), "%a\\%b%");
            same("users.name ILIKE ?", startsWithIgnoreCase(USERS.NAME, "ab"), USERS.NAME.startsWithIgnoreCase("ab"), "ab%");
            same("users.name ILIKE ?", endsWithIgnoreCase(USERS.NAME, "ab"), USERS.NAME.endsWithIgnoreCase("ab"), "%ab");
            same("users.name ILIKE ?", containsIgnoreCase(USERS.NAME, "ab"), USERS.NAME.containsIgnoreCase("ab"), "%ab%");
            assertSql("starts_with(users.name, ?)", startsWith(USERS.NAME, "ab"), "ab");
        }

        @Test
        void similarAndRegex() {
            same("users.name SIMILAR TO ?", similarTo(USERS.NAME, "(a|b)%"), USERS.NAME.similarTo("(a|b)%"), "(a|b)%");
            same("users.name NOT SIMILAR TO ?", notSimilarTo(USERS.NAME, "(a|b)%"), USERS.NAME.notSimilarTo("(a|b)%"), "(a|b)%");
            same("users.name ~ ?", matches(USERS.NAME, "^A"), USERS.NAME.matches("^A"), "^A");
            same("users.name ~* ?", matchesIgnoreCase(USERS.NAME, "^a"), USERS.NAME.matchesIgnoreCase("^a"), "^a");
            same("users.name !~ ?", notMatches(USERS.NAME, "^A"), USERS.NAME.notMatches("^A"), "^A");
            same("users.name !~* ?", notMatchesIgnoreCase(USERS.NAME, "^a"), USERS.NAME.notMatchesIgnoreCase("^a"), "^a");
            assertSql("users.name ~ users.email", USERS.NAME.matches(USERS.EMAIL));
        }
    }

    @Nested
    class Arrays {
        @Test
        void containmentAndOverlap() {
            List<Object> vip = List.of("vip");
            same("users.tags @> ?", arrayContains(USERS.TAGS, "vip"), USERS.TAGS.arrayContains("vip"), vip);
            same("users.tags <@ ?", arrayContainedBy(USERS.TAGS, "vip"), USERS.TAGS.arrayContainedBy("vip"), vip);
            same("users.tags && ?", arrayOverlaps(USERS.TAGS, "vip"), USERS.TAGS.arrayOverlaps("vip"), vip);
            same("users.tags @> users.tags", arrayContains(USERS.TAGS, USERS.TAGS), USERS.TAGS.contains(USERS.TAGS));
            same("users.tags <@ users.tags", arrayContainedBy(USERS.TAGS, USERS.TAGS), USERS.TAGS.containedBy(USERS.TAGS));
            same("users.tags && users.tags", arrayOverlaps(USERS.TAGS, USERS.TAGS), USERS.TAGS.overlaps(USERS.TAGS));
            assertSql("users.tags @> ?", USERS.TAGS.contains(new String[] {"a", "b"}), List.of("a", "b"));
            assertSql("? = ANY(users.tags)", USERS.TAGS.hasElement("vip"), "vip");
        }
    }

    @Nested
    class Ranges {
        @Test
        void operators() {
            RangeField other = range(tstzrange(param(T1), param(T2)));
            String o = "tstzrange(?, ?, '[)')";
            same("bookings.period @> ?", rangeContains(BOOKINGS.PERIOD, T1), BOOKINGS.PERIOD.rangeContains(T1), T1);
            same("bookings.period @> " + o, rangeContains(BOOKINGS.PERIOD, other), BOOKINGS.PERIOD.rangeContains(other), T1, T2);
            same("bookings.period <@ " + o, rangeContainedBy(BOOKINGS.PERIOD, other), BOOKINGS.PERIOD.rangeContainedBy(other), T1, T2);
            same("bookings.period && " + o, rangeOverlaps(BOOKINGS.PERIOD, other), BOOKINGS.PERIOD.rangeOverlaps(other), T1, T2);
            same("bookings.period << " + o, strictlyLeftOf(BOOKINGS.PERIOD, other), BOOKINGS.PERIOD.strictlyLeftOf(other), T1, T2);
            same("bookings.period >> " + o, strictlyRightOf(BOOKINGS.PERIOD, other), BOOKINGS.PERIOD.strictlyRightOf(other), T1, T2);
            same("bookings.period &< " + o, notExtendsRightOf(BOOKINGS.PERIOD, other), BOOKINGS.PERIOD.notExtendsRightOf(other), T1, T2);
            same("bookings.period &> " + o, notExtendsLeftOf(BOOKINGS.PERIOD, other), BOOKINGS.PERIOD.notExtendsLeftOf(other), T1, T2);
            same("bookings.period -|- " + o, adjacentTo(BOOKINGS.PERIOD, other), BOOKINGS.PERIOD.adjacentTo(other), T1, T2);
            same("isempty(bookings.period)", isEmpty(BOOKINGS.PERIOD), BOOKINGS.PERIOD.isEmpty());
            assertSql("? <@ bookings.period", rangeContainedBy(param(T1), BOOKINGS.PERIOD), T1);
        }

        @Test
        void elementValuesAreBoundWithTheirType() {
            assertSql("daterange(?, ?, '[)') @> ?", rangeContains(daterange(param(LocalDate.MIN), param(LocalDate.MAX)), LocalDate.EPOCH),
                    LocalDate.MIN, LocalDate.MAX, LocalDate.EPOCH);
            assertSql("int4range(?, ?, '[)') @> ?", rangeContains(int4range(param(1), param(5)), 3), 1, 5, 3);
            assertSql("int8range(?, ?, '[)') @> ?", rangeContains(int8range(param(1L), param(5L)), 3L), 1L, 5L, 3L);
        }

        @Test
        void rangeExpressionsAreRangeFields() {
            assertInstanceOf(RangeField.class, tstzrange(param(T1), param(T2)));
            assertInstanceOf(RangeField.class, BOOKINGS.PERIOD.as("p"));
            assertSql("bookings.period", range(BOOKINGS.PERIOD));
        }
    }

    @Nested
    class Json {
        @Test
        void operators() {
            same("users.settings @> ?", jsonContains(USERS.SETTINGS, "{\"a\":1}"), USERS.SETTINGS.jsonContains("{\"a\":1}"), "{\"a\":1}");
            same("users.settings <@ ?", jsonContainedBy(USERS.SETTINGS, "{\"a\":1}"), USERS.SETTINGS.jsonContainedBy("{\"a\":1}"), "{\"a\":1}");
            same("users.settings @> users.settings", jsonContains(USERS.SETTINGS, USERS.SETTINGS), USERS.SETTINGS.contains(USERS.SETTINGS));
            same("users.settings <@ users.settings", jsonContainedBy(USERS.SETTINGS, USERS.SETTINGS),
                    USERS.SETTINGS.containedBy(USERS.SETTINGS));
            same("jsonb_exists(users.settings, 'theme')", hasKey(USERS.SETTINGS, "theme"), USERS.SETTINGS.hasKey("theme"));
            same("jsonb_exists_any(users.settings, '{\"a\",\"b\"}')", hasAnyKey(USERS.SETTINGS, "a", "b"), USERS.SETTINGS.hasAnyKey("a", "b"));
            same("jsonb_exists_all(users.settings, '{\"a\",\"b\"}')", hasAllKeys(USERS.SETTINGS, "a", "b"), USERS.SETTINGS.hasAllKeys("a", "b"));
            same("jsonb_path_exists(users.settings, '$.a ? (@ > 1)'::jsonpath)", jsonPathExists(USERS.SETTINGS, "$.a ? (@ > 1)"),
                    USERS.SETTINGS.jsonPathExists("$.a ? (@ > 1)"));
            same("jsonb_path_match(users.settings, '$.a > 1'::jsonpath)", jsonPathMatches(USERS.SETTINGS, "$.a > 1"),
                    USERS.SETTINGS.jsonPathMatches("$.a > 1"));
            assertSql("jsonb_path_exists(users.settings, 'it''s'::jsonpath)", USERS.SETTINGS.jsonPathExists("it's"));
        }
    }

    @Nested
    class FullText {
        @Test
        void tsMatchesBothForms() {
            same("bookings.search @@ websearch_to_tsquery('english'::regconfig, ?)",
                    tsMatches(BOOKINGS.SEARCH, websearchToTsquery("english", "quiet room")),
                    BOOKINGS.SEARCH.tsMatches(websearchToTsquery("english", "quiet room")), "quiet room");
            assertSql("to_tsvector('english'::regconfig, users.name) @@ plainto_tsquery('english'::regconfig, ?)",
                    tsvector(toTsvector("english", USERS.NAME)).tsMatches(plaintoTsquery("english", "ada")), "ada");
        }
    }

    @Nested
    class RowValues {
        @Test
        void comparisons() {
            UUID id = UUID.randomUUID();
            assertSql("(users.created_at, users.id) > (?, ?)", row(USERS.CREATED_AT, USERS.ID).gt(T1, id), T1, id);
            assertSql("(users.created_at, users.id) >= (?, ?)", row(USERS.CREATED_AT, USERS.ID).ge(T1, id), T1, id);
            assertSql("(users.created_at, users.id) < (?, ?)", row(USERS.CREATED_AT, USERS.ID).lt(T1, id), T1, id);
            assertSql("(users.created_at, users.id) <= (?, ?)", row(USERS.CREATED_AT, USERS.ID).le(T1, id), T1, id);
            assertSql("(users.name, users.email) = (?, ?)", row(USERS.NAME, USERS.EMAIL).eq("a", "b"), "a", "b");
            assertSql("(users.name, users.email) <> (?, ?)", row(USERS.NAME, USERS.EMAIL).ne("a", "b"), "a", "b");
            assertSql("(users.name, users.email) = (users.email, users.name)", row(USERS.NAME, USERS.EMAIL).eq(row(USERS.EMAIL, USERS.NAME)));
            assertSql("(users.name, users.email) IS DISTINCT FROM (users.email, users.name)",
                    row(USERS.NAME, USERS.EMAIL).isDistinctFrom(row(USERS.EMAIL, USERS.NAME)));
        }

        @Test
        void typesAndArityAreChecked() {
            assertThrows(IllegalArgumentException.class, () -> row(USERS.NAME, USERS.EMAIL).eq("a"));
            assertThrows(IllegalArgumentException.class, () -> row(USERS.NAME, USERS.VERSION).eq("a", "b"));
            assertThrows(IllegalArgumentException.class, () -> row(USERS.NAME, USERS.EMAIL).eq("a", null));
            assertThrows(IllegalArgumentException.class, () -> row(USERS.NAME, USERS.VERSION).eq(row(USERS.NAME, USERS.EMAIL)));
            assertThrows(IllegalArgumentException.class, () -> row(USERS.NAME));
        }

        @Test
        void inWithRows() {
            assertSql("(users.name, users.version) IN ((?, ?), (?, ?))",
                    row(USERS.NAME, USERS.VERSION).in(List.of(List.of("a", 1L), List.of("b", 2L))), "a", 1L, "b", 2L);
            assertSql("(users.name, users.version) NOT IN ((?, ?))",
                    row(USERS.NAME, USERS.VERSION).notIn(List.of(List.of("a", 1L))), "a", 1L);
            assertSql("FALSE", row(USERS.NAME, USERS.VERSION).in(List.of()));
            assertSql("TRUE", row(USERS.NAME, USERS.VERSION).notIn(List.of()));
            assertSql("(orders.user_id, orders.status) IN (SELECT users.id, users.name FROM users)",
                    row(ORDERS.USER_ID, ORDERS.STATUS).in(select(USERS.ID, USERS.NAME).from(USERS)));
            assertSql("(orders.user_id, orders.status) NOT IN (SELECT users.id, users.name FROM users)",
                    row(ORDERS.USER_ID, ORDERS.STATUS).notIn(select(USERS.ID, USERS.NAME).from(USERS)));
            assertThrows(IllegalArgumentException.class, () -> row(USERS.NAME, USERS.EMAIL).in(select(USERS.NAME).from(USERS)));
            assertSql("(users.name, users.email) IN ((users.email, users.name))",
                    row(USERS.NAME, USERS.EMAIL).in(row(USERS.EMAIL, USERS.NAME)));
        }
    }

    @Nested
    class Logic {
        @Test
        void varargsAndCollections() {
            Condition a = USERS.NAME.eq("a");
            Condition b = USERS.EMAIL.eq("b");
            assertSql("(users.name = ? AND users.email = ?)", and(a, b), "a", "b");
            assertSql("(users.name = ? AND users.email = ?)", and(List.of(a, b)), "a", "b");
            assertSql("(users.name = ? OR users.email = ?)", or(a, b), "a", "b");
            assertSql("(users.name = ? OR users.email = ?)", or(List.of(a, b)), "a", "b");
            assertSql("NOT (users.name = ?)", not(a), "a");
            assertSql("(users.name = ? AND users.email = ?)", a.and(b), "a", "b");
            assertSql("(users.name = ? OR users.email = ?)", a.or(b), "a", "b");
            assertSql("NOT (users.name = ?)", a.not(), "a");
        }

        @Test
        void deepNesting() {
            Condition c = USERS.NAME.eq("x");
            for (int i = 0; i < 50; i++) c = i % 2 == 0 ? or(c, USERS.VERSION.eq((long) i)) : and(not(c), USERS.ACTIVE);
            String sql = sql(c);
            assertEquals(sql.chars().filter(ch -> ch == '(').count(), sql.chars().filter(ch -> ch == ')').count());
            assertTrue(sql.startsWith("(NOT (((NOT ((("), sql);
            assertEquals(26, ch.lxrin.ql.RenderTestSupport.binds(c).size());
            // nested junctions of the same kind are flattened
            assertSql("(users.name = ? AND users.email = ? AND users.active)",
                    and(and(USERS.NAME.eq("a"), USERS.EMAIL.eq("b")), and(List.of(USERS.ACTIVE))), "a", "b");
            assertSql("((users.name = ? OR users.email = ?) AND (users.active OR users.version = ?))",
                    and(or(USERS.NAME.eq("a"), USERS.EMAIL.eq("b")), or(USERS.ACTIVE, USERS.VERSION.eq(1L))), "a", "b", 1L);
        }
    }

    @Nested
    class Dynamic {
        @Test
        void noConditionIsNeutral() {
            Condition a = USERS.NAME.eq("a");
            assertSame(a, and(noCondition(), a, noCondition()));
            assertSame(a, or(List.of(noCondition(), a)));
            assertSame(noCondition(), and(List.of()));
            assertSame(noCondition(), or());
            assertSql("TRUE", noCondition());
        }

        @Test
        void whenAndIfPresent() {
            assertSame(noCondition(), when(false, () -> fail("not evaluated")));
            assertSql("users.name = ?", when(true, () -> USERS.NAME.eq("a")), "a");
            assertThrows(IllegalArgumentException.class, () -> when(true, () -> null));
            assertSame(noCondition(), ifPresent(Optional.<String>empty(), USERS.NAME::eq));
            assertSql("users.name = ?", ifPresent(Optional.of("a"), USERS.NAME::eq), "a");
            same("users.name = ?", eqIfPresent(USERS.NAME, Optional.of("a")), USERS.NAME.eqIfPresent(Optional.of("a")), "a");
            assertSame(noCondition(), USERS.NAME.eqIfPresent(Optional.empty()));
            same("users.version <> ?", neIfPresent(USERS.VERSION, Optional.of(1L)), USERS.VERSION.neIfPresent(Optional.of(1L)), 1L);
            same("users.version > ?", gtIfPresent(USERS.VERSION, Optional.of(1L)), USERS.VERSION.gtIfPresent(Optional.of(1L)), 1L);
            same("users.version >= ?", geIfPresent(USERS.VERSION, Optional.of(1L)), USERS.VERSION.geIfPresent(Optional.of(1L)), 1L);
            same("users.version < ?", ltIfPresent(USERS.VERSION, Optional.of(1L)), USERS.VERSION.ltIfPresent(Optional.of(1L)), 1L);
            same("users.version <= ?", leIfPresent(USERS.VERSION, Optional.of(1L)), USERS.VERSION.leIfPresent(Optional.of(1L)), 1L);
            same("users.name = ANY(?)", inIfPresent(USERS.NAME, Optional.of(List.of("a"))),
                    USERS.NAME.inIfPresent(Optional.of(List.of("a"))), List.of("a"));
            assertSame(noCondition(), inIfPresent(USERS.NAME, Optional.empty()));
            assertSql("users.name ILIKE ?", USERS.NAME.ilikeIfPresent(Optional.of("a%")), "a%");
            assertSql("users.name LIKE ?", USERS.NAME.likeIfPresent(Optional.of("a%")), "a%");
            assertSql("users.name LIKE ?", USERS.NAME.startsWithIfPresent(Optional.of("a")), "a%");
            assertSql("users.name ILIKE ?", USERS.NAME.containsIgnoreCaseIfPresent(Optional.of("a")), "%a%");
            assertSame(noCondition(), USERS.NAME.containsIgnoreCaseIfPresent(Optional.empty()));
        }

        @Test
        void builder() {
            Condition c = Conditions.builder(USERS.DELETED_AT.isNull())
                    .addIf(false, () -> fail("not evaluated"))
                    .addIf(true, () -> USERS.ROLE.eq(Role.ADMIN))
                    .addIfPresent(Optional.of("ad"), USERS.NAME::containsIgnoreCase)
                    .addIfPresent(Optional.<String>empty(), USERS.EMAIL::eq)
                    .addAll(List.of(USERS.ACTIVE))
                    .build();
            assertSql("(users.deleted_at IS NULL AND users.role = ? AND users.name ILIKE ? AND users.active)", c, Role.ADMIN, "%ad%");
            assertSql("(users.name = ? OR users.email = ?)",
                    Conditions.orBuilder().add(USERS.NAME.eq("a"), USERS.EMAIL.eq("b")).build(), "a", "b");
            assertTrue(Conditions.builder().isEmpty());
            assertTrue(Conditions.builder(noCondition()).isEmpty());
            assertSame(noCondition(), Conditions.builder().build());
            assertThrows(IllegalArgumentException.class, () -> Conditions.builder().add((Condition) null));
            List<Condition> withNull = new ArrayList<>();
            withNull.add(null);
            assertThrows(IllegalArgumentException.class, () -> Conditions.builder().addAll(withNull));
            assertThrows(IllegalArgumentException.class, () -> selectFrom(USERS).where(withNull));
        }

        @Test
        void conditionsWorkEverywhere() {
            Condition admin = eq(USERS.ROLE, Role.ADMIN);
            assertEquals("SELECT count(*) FILTER (WHERE users.role = ?) FROM users", select(count().filter(admin)).from(USERS).render().sql());
            assertSql("CASE WHEN users.role = ? THEN ? ELSE ? END", caseWhen(admin, param("a")).otherwise(param("b")), Role.ADMIN, "a", "b");
            assertEquals("SELECT users.id FROM users JOIN orders ON (orders.user_id = users.id AND orders.total > ?) GROUP BY users.id HAVING count(*) > ?",
                    select(USERS.ID).from(USERS).join(ORDERS).on(and(eq(ORDERS.USER_ID, USERS.ID), gt(ORDERS.TOTAL, BigDecimal.TEN)))
                            .groupBy(USERS.ID).having(gt(count(), 1L)).render().sql());
        }
    }
}
