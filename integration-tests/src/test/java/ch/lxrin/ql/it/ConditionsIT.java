package ch.lxrin.ql.it;

import ch.lxrin.ql.dsl.Condition;
import ch.lxrin.ql.dsl.Conditions;
import ch.lxrin.ql.dsl.Field;
import ch.lxrin.ql.dsl.Sorts;
import ch.lxrin.ql.error.InvalidSortException;
import ch.lxrin.ql.error.InvalidStatementException;
import ch.lxrin.ql.it.db.AppRole;
import ch.lxrin.ql.it.types.UserId;
import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.types.UuidV7;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static ch.lxrin.ql.dsl.Dsl.*;
import static ch.lxrin.ql.it.db.Tables.*;
import static org.junit.jupiter.api.Assertions.*;

/** Every operator group of 3.1 and the dynamic features, executed against PostgreSQL. */
class ConditionsIT {

    private static final UUID ORG = UUID.randomUUID();
    private static final Instant JAN = Instant.parse("2025-01-01T00:00:00Z");
    private static final Instant FEB = Instant.parse("2025-02-01T00:00:00Z");
    private static final Instant MAR = Instant.parse("2025-03-01T00:00:00Z");

    private static QueryContext ctx;
    private static UserId ada;
    private static UserId alan;
    private static UserId grace;
    private static long laptop;
    private static long phone;
    private static long server;

    @BeforeAll
    static void setUp() {
        Postgres.migrateItSchema();
        ctx = QueryContext.builder().dataSource(Postgres.dataSource()).build();
        ada = user("Ada", "ada@example.org", AppRole.ADMIN, "{\"theme\": \"dark\", \"n\": 3}", "vip", "math");
        alan = user("Alan", "alan@example.org", AppRole.USER, "{\"theme\": \"light\"}", "math");
        grace = user("Grace_100%", "grace@navy.mil", AppRole.AUDITOR, null);
        laptop = asset(ada, "Laptop", "10.00");
        phone = asset(alan, "Phone", "5.50");
        server = asset(null, "Server", null);
        ctx.insertInto(RISK).set(RISK.ASSET_ID, laptop).set(RISK.THREAT, "theft").set(RISK.LIKELIHOOD, 3).set(RISK.IMPACT, 4).execute();
        ctx.insertInto(RISK).set(RISK.ASSET_ID, phone).set(RISK.THREAT, "loss").set(RISK.LIKELIHOOD, 2).set(RISK.IMPACT, 2).execute();
        booking(laptop, "[2025-01-01 00:00:00+00,2025-02-01 00:00:00+00)", "[2025-01-01,2025-02-01)", "Quiet room for the audit",
                new Integer[] {1, 2, 3}, "{\"floor\": 2, \"tags\": [\"audit\"]}");
        booking(phone, "[2025-02-01 00:00:00+00,2025-03-01 00:00:00+00)", null, "Travelling to London",
                new Integer[] {4}, "{\"floor\": 1}");
    }

    private static UserId user(String name, String email, AppRole role, String settings, String... tags) {
        UserId id = new UserId(UuidV7.generate());
        ctx.insertInto(USERS).set(USERS.ID, id).set(USERS.NAME, name).set(USERS.EMAIL, email).set(USERS.ROLE, role)
                .set(USERS.SETTINGS, settings).set(USERS.TAGS, tags).set(USERS.ORGANIZATION_ID, ORG).execute();
        return id;
    }

    private static long asset(UserId owner, String name, String value) {
        return ctx.insertInto(ASSET).set(ASSET.OWNER_ID, owner).set(ASSET.NAME, name)
                .set(ASSET.VALUE, value == null ? null : new BigDecimal(value)).set(ASSET.ORGANIZATION_ID, ORG)
                .returning(ASSET.ID).fetchOne();
    }

    private static void booking(long asset, String period, String days, String note, Integer[] slots, String attributes) {
        ctx.insertInto(BOOKING).set(BOOKING.ASSET_ID, asset).set(BOOKING.PERIOD, period).set(BOOKING.DAYS, days)
                .set(BOOKING.NOTE, note).set(BOOKING.SLOTS, slots).set(BOOKING.ATTRIBUTES, attributes).execute();
    }

    private static List<String> users(Condition condition) {
        return ctx.select(USERS.NAME).from(USERS).where(condition).orderBy(USERS.NAME.asc()).fetch();
    }

    private static List<String> assets(Condition condition) {
        return ctx.select(ASSET.NAME).from(ASSET).where(condition).orderBy(ASSET.NAME.asc()).fetch();
    }

    private static List<Long> bookings(Condition condition) {
        return ctx.select(BOOKING.ASSET_ID).from(BOOKING).where(condition).orderBy(BOOKING.ID.asc()).fetch();
    }

    private static void both(List<?> expected, List<?> staticForm, List<?> fluentForm) {
        assertEquals(expected, staticForm, "static form");
        assertEquals(expected, fluentForm, "fluent form");
    }

    @Test
    void comparison() {
        both(List.of("Ada"), users(eq(USERS.NAME, "Ada")), users(USERS.NAME.eq("Ada")));
        both(List.of("Alan", "Grace_100%"), users(ne(USERS.NAME, "Ada")), users(USERS.NAME.ne("Ada")));
        both(List.of("Laptop"), assets(gt(ASSET.VALUE, new BigDecimal("6"))), assets(ASSET.VALUE.gt(new BigDecimal("6"))));
        both(List.of("Laptop", "Phone"), assets(ge(ASSET.VALUE, new BigDecimal("5.50"))), assets(ASSET.VALUE.ge(new BigDecimal("5.50"))));
        both(List.of("Phone"), assets(lt(ASSET.VALUE, new BigDecimal("10"))), assets(ASSET.VALUE.lt(new BigDecimal("10"))));
        both(List.of("Laptop", "Phone"), assets(le(ASSET.VALUE, new BigDecimal("10"))), assets(ASSET.VALUE.le(new BigDecimal("10"))));
        both(List.of("Ada"), users(eq(USERS.ID, ada)), users(USERS.ID.eq(ada)));
        // IS DISTINCT FROM treats NULL as a value
        both(List.of("Phone", "Server"), assets(isDistinctFrom(ASSET.VALUE, new BigDecimal("10.00"))),
                assets(ASSET.VALUE.isDistinctFrom(new BigDecimal("10.00"))));
        both(List.of("Server"), assets(isNotDistinctFrom(ASSET.OWNER_ID, (UserId) null)), assets(ASSET.OWNER_ID.isNotDistinctFrom((UserId) null)));
    }

    @Test
    void between() {
        both(List.of("Laptop", "Phone"), assets(Conditions.between(ASSET.VALUE, new BigDecimal("5"), new BigDecimal("10"))),
                assets(ASSET.VALUE.between(new BigDecimal("5"), new BigDecimal("10"))));
        both(List.of("Laptop"), assets(notBetween(ASSET.VALUE, new BigDecimal("5"), new BigDecimal("6"))),
                assets(ASSET.VALUE.notBetween(new BigDecimal("5"), new BigDecimal("6"))));
        // reversed bounds: BETWEEN finds nothing, BETWEEN SYMMETRIC swaps them
        assertEquals(List.of(), assets(ASSET.VALUE.between(new BigDecimal("10"), new BigDecimal("5"))));
        both(List.of("Laptop", "Phone"), assets(betweenSymmetric(ASSET.VALUE, new BigDecimal("10"), new BigDecimal("5"))),
                assets(ASSET.VALUE.betweenSymmetric(new BigDecimal("10"), new BigDecimal("5"))));
        both(List.of("Laptop"), assets(notBetweenSymmetric(ASSET.VALUE, new BigDecimal("6"), new BigDecimal("5"))),
                assets(ASSET.VALUE.notBetweenSymmetric(new BigDecimal("6"), new BigDecimal("5"))));
    }

    @Test
    void inAnyAllExists() {
        both(List.of("Ada", "Alan"), users(in(USERS.NAME, "Ada", "Alan", "Nobody")), users(USERS.NAME.in(List.of("Ada", "Alan"))));
        both(List.of("Grace_100%"), users(notIn(USERS.ID, ada, alan)), users(USERS.ID.notIn(List.of(ada, alan))));
        both(List.of("Ada"), users(in(USERS.ROLE, AppRole.ADMIN)), users(USERS.ROLE.in(AppRole.ADMIN)));
        // an empty IN is always false, an empty NOT IN always true – also for NULL values
        both(List.of(), assets(in(ASSET.OWNER_ID, List.of())), assets(ASSET.OWNER_ID.in()));
        both(List.of("Laptop", "Phone", "Server"), assets(notIn(ASSET.OWNER_ID, List.of())), assets(ASSET.OWNER_ID.notIn()));
        // sub-queries
        both(List.of("Ada", "Alan"), users(in(USERS.ID, select(ASSET.OWNER_ID).from(ASSET))),
                users(USERS.ID.in(select(ASSET.OWNER_ID).from(ASSET))));
        both(List.of("Ada", "Alan"), users(exists(selectOne().from(ASSET).where(ASSET.OWNER_ID.eq(USERS.ID)))),
                users(exists(selectOne().from(ASSET).where(eq(ASSET.OWNER_ID, USERS.ID)))));
        assertEquals(List.of("Grace_100%"), users(notExists(selectOne().from(ASSET).where(ASSET.OWNER_ID.eq(USERS.ID)))));
        // ANY / ALL over sub-queries and arrays, with every comparison
        var values = select(ASSET.VALUE).from(ASSET).where(ASSET.VALUE.isNotNull());
        both(List.of("Laptop"), assets(ge(ASSET.VALUE, all(values))), assets(ASSET.VALUE.ge(all(values))));
        both(List.of("Laptop"), assets(gt(ASSET.VALUE, any(values))), assets(ASSET.VALUE.gt(any(values))));
        both(List.of("Phone"), assets(le(ASSET.VALUE, all(values))), assets(ASSET.VALUE.le(all(values))));
        both(List.of("Phone"), assets(lt(ASSET.VALUE, any(values))), assets(ASSET.VALUE.lt(any(values))));
        both(List.of("Laptop", "Phone"), assets(eq(ASSET.VALUE, any(values))), assets(ASSET.VALUE.eq(any(values))));
        both(List.of("Laptop"), assets(ne(ASSET.VALUE, all(select(ASSET.VALUE).from(ASSET).where(ASSET.NAME.eq("Phone"))))),
                assets(ASSET.VALUE.ne(all(select(ASSET.VALUE).from(ASSET).where(ASSET.NAME.eq("Phone"))))));
        assertEquals(List.of("Ada"), users(eq("vip", any(USERS.TAGS))));
        assertEquals(List.of("Grace_100%"), users(ne("math", all(USERS.TAGS))));
        assertEquals(List.of(laptop), bookings(gt(3, any(BOOKING.SLOTS))));
        assertEquals(List.of(phone), bookings(lt(3, all(BOOKING.SLOTS))));
    }

    @Test
    void nullAndBoolean() {
        both(List.of("Server"), assets(isNull(ASSET.OWNER_ID)), assets(ASSET.OWNER_ID.isNull()));
        both(List.of("Laptop", "Phone"), assets(isNotNull(ASSET.OWNER_ID)), assets(ASSET.OWNER_ID.isNotNull()));
        both(List.of("Ada", "Alan", "Grace_100%"), users(isTrue(USERS.ACTIVE)), users(USERS.ACTIVE.isTrue()));
        both(List.of(), users(isFalse(USERS.ACTIVE)), users(USERS.ACTIVE.isFalse()));
        // a comparison with NULL is unknown: IS NOT TRUE includes it, = excludes it
        both(List.of("Phone", "Server"), assets(isNotTrue(ASSET.VALUE.gt(new BigDecimal("6")))),
                assets(ASSET.VALUE.gt(new BigDecimal("6")).isNotTrue()));
        both(List.of("Laptop", "Server"), assets(isNotFalse(ASSET.VALUE.gt(new BigDecimal("6")))),
                assets(ASSET.VALUE.gt(new BigDecimal("6")).isNotFalse()));
    }

    @Test
    void text() {
        both(List.of("Ada", "Alan"), users(like(USERS.NAME, "A%")), users(USERS.NAME.like("A%")));
        both(List.of("Grace_100%"), users(notLike(USERS.NAME, "A%")), users(USERS.NAME.notLike("A%")));
        both(List.of("Ada", "Alan"), users(ilike(USERS.NAME, "a%")), users(USERS.NAME.ilike("a%")));
        both(List.of("Grace_100%"), users(notIlike(USERS.NAME, "a%")), users(USERS.NAME.notIlike("a%")));
        // custom escape character: "!%" is a literal percent sign
        both(List.of("Grace_100%"), users(like(USERS.NAME, "%100!%", '!')), users(USERS.NAME.like("%100!%", '!')));
        both(List.of("Grace_100%"), users(ilike(USERS.NAME, "grace!_%", '!')), users(USERS.NAME.ilike("grace!_%", '!')));
        assertEquals(List.of("Ada", "Alan"), users(USERS.NAME.notLike("%!_%", '!')));
        // startsWith / endsWith / contains escape the wildcards of the value
        assertEquals(List.of("Grace_100%"), users(USERS.NAME.contains("_100%")));
        assertEquals(List.of(), users(USERS.NAME.contains("_1000%")));
        both(List.of("Grace_100%"), users(endsWith(USERS.NAME, "0%")), users(USERS.NAME.endsWith("0%")));
        both(List.of("Ada", "Alan"), users(containsIgnoreCase(USERS.EMAIL, "EXAMPLE")), users(USERS.EMAIL.containsIgnoreCase("EXAMPLE")));
        both(List.of("Ada", "Alan"), users(startsWithIgnoreCase(USERS.NAME, "a")), users(USERS.NAME.startsWithIgnoreCase("a")));
        both(List.of("Grace_100%"), users(endsWithIgnoreCase(USERS.EMAIL, ".MIL")), users(USERS.EMAIL.endsWithIgnoreCase(".MIL")));
        both(List.of("Ada", "Alan"), users(similarTo(USERS.NAME, "A(da|lan)")), users(USERS.NAME.similarTo("A(da|lan)")));
        both(List.of("Grace_100%"), users(notSimilarTo(USERS.NAME, "A(da|lan)")), users(USERS.NAME.notSimilarTo("A(da|lan)")));
        both(List.of("Ada"), users(matches(USERS.NAME, "^Ad")), users(USERS.NAME.matches("^Ad")));
        both(List.of("Ada"), users(matchesIgnoreCase(USERS.NAME, "^aD")), users(USERS.NAME.matchesIgnoreCase("^aD")));
        both(List.of("Alan", "Grace_100%"), users(notMatches(USERS.NAME, "^Ad")), users(USERS.NAME.notMatches("^Ad")));
        both(List.of("Alan", "Grace_100%"), users(notMatchesIgnoreCase(USERS.NAME, "^aD")), users(USERS.NAME.notMatchesIgnoreCase("^aD")));
    }

    @Test
    void arrays() {
        both(List.of("Ada"), users(arrayContains(USERS.TAGS, "vip", "math")), users(USERS.TAGS.arrayContains("vip", "math")));
        both(List.of("Alan", "Grace_100%"), users(arrayContainedBy(USERS.TAGS, "math")), users(USERS.TAGS.arrayContainedBy("math")));
        both(List.of("Ada", "Alan"), users(arrayOverlaps(USERS.TAGS, "math", "chess")), users(USERS.TAGS.arrayOverlaps("math", "chess")));
        both(List.of(laptop), bookings(arrayContains(BOOKING.SLOTS, 2)), bookings(BOOKING.SLOTS.arrayContains(2)));
        assertEquals(List.of("Ada", "Alan", "Grace_100%"), users(USERS.TAGS.arrayContains(USERS.TAGS)));
    }

    @Test
    void ranges() {
        var february = tstzrange(param(FEB), param(MAR));
        var january = tstzrange(param(JAN), param(FEB));
        var wholeYear = tstzrange(param(JAN), param(Instant.parse("2026-01-01T00:00:00Z")));
        both(List.of(laptop), bookings(rangeContains(BOOKING.PERIOD, Instant.parse("2025-01-15T00:00:00Z"))),
                bookings(BOOKING.PERIOD.rangeContains(Instant.parse("2025-01-15T00:00:00Z"))));
        both(List.of(laptop), bookings(rangeContains(BOOKING.DAYS, LocalDate.of(2025, 1, 31))),
                bookings(BOOKING.DAYS.rangeContains(LocalDate.of(2025, 1, 31))));
        both(List.of(phone), bookings(rangeContains(BOOKING.PERIOD, february)), bookings(BOOKING.PERIOD.rangeContains(february)));
        both(List.of(laptop, phone), bookings(rangeContainedBy(BOOKING.PERIOD, wholeYear)), bookings(BOOKING.PERIOD.rangeContainedBy(wholeYear)));
        both(List.of(phone), bookings(rangeOverlaps(BOOKING.PERIOD, february)), bookings(BOOKING.PERIOD.rangeOverlaps(february)));
        both(List.of(laptop), bookings(strictlyLeftOf(BOOKING.PERIOD, february)), bookings(BOOKING.PERIOD.strictlyLeftOf(february)));
        both(List.of(phone), bookings(strictlyRightOf(BOOKING.PERIOD, january)), bookings(BOOKING.PERIOD.strictlyRightOf(january)));
        both(List.of(laptop, phone), bookings(notExtendsRightOf(BOOKING.PERIOD, february)), bookings(BOOKING.PERIOD.notExtendsRightOf(february)));
        both(List.of(laptop, phone), bookings(notExtendsLeftOf(BOOKING.PERIOD, january)), bookings(BOOKING.PERIOD.notExtendsLeftOf(january)));
        both(List.of(laptop), bookings(adjacentTo(BOOKING.PERIOD, february)), bookings(BOOKING.PERIOD.adjacentTo(february)));
        assertEquals(List.of(laptop, phone), bookings(rangeContainedBy(param(JAN), tstzrange(param(JAN), param(MAR)))));
        assertEquals(List.of(), bookings(BOOKING.PERIOD.isEmpty()));
    }

    @Test
    void json() {
        both(List.of("Ada"), users(jsonContains(USERS.SETTINGS, "{\"theme\": \"dark\"}")), users(USERS.SETTINGS.jsonContains("{\"theme\": \"dark\"}")));
        both(List.of("Alan"), users(jsonContainedBy(USERS.SETTINGS, "{\"theme\": \"light\", \"x\": 1}")),
                users(USERS.SETTINGS.jsonContainedBy("{\"theme\": \"light\", \"x\": 1}")));
        both(List.of("Ada"), users(hasKey(USERS.SETTINGS, "n")), users(USERS.SETTINGS.hasKey("n")));
        both(List.of("Ada", "Alan"), users(hasAnyKey(USERS.SETTINGS, "n", "theme")), users(USERS.SETTINGS.hasAnyKey("n", "theme")));
        both(List.of("Ada"), users(hasAllKeys(USERS.SETTINGS, "n", "theme")), users(USERS.SETTINGS.hasAllKeys("n", "theme")));
        both(List.of("Ada"), users(jsonPathExists(USERS.SETTINGS, "$.n ? (@ > 2)")), users(USERS.SETTINGS.jsonPathExists("$.n ? (@ > 2)")));
        both(List.of(laptop), bookings(jsonPathMatches(BOOKING.ATTRIBUTES, "$.floor > 1")),
                bookings(BOOKING.ATTRIBUTES.jsonPathMatches("$.floor > 1")));
        assertEquals(List.of(laptop), bookings(BOOKING.ATTRIBUTES.jsonContains("{\"tags\": [\"audit\"]}")));
    }

    @Test
    void fullTextSearch() {
        both(List.of(laptop), bookings(tsMatches(BOOKING.SEARCH, websearchToTsquery("english", "quiet rooms"))),
                bookings(BOOKING.SEARCH.tsMatches(websearchToTsquery("english", "quiet rooms"))));
        assertEquals(List.of(phone), bookings(tsvector(toTsvector("english", BOOKING.NOTE)).tsMatches(plaintoTsquery("english", "travel"))));
    }

    @Test
    void rowValues() {
        List<String> expected = List.of("theft");
        assertEquals(expected, ctx.select(RISK.THREAT).from(RISK).where(row(RISK.LIKELIHOOD, RISK.IMPACT).gt(2, 5)).fetch());
        assertEquals(expected, ctx.select(RISK.THREAT).from(RISK).where(row(RISK.LIKELIHOOD, RISK.IMPACT).ge(3, 4)).fetch());
        assertEquals(List.of("loss"), ctx.select(RISK.THREAT).from(RISK).where(row(RISK.LIKELIHOOD, RISK.IMPACT).lt(3, 0)).fetch());
        assertEquals(List.of("loss"), ctx.select(RISK.THREAT).from(RISK).where(row(RISK.LIKELIHOOD, RISK.IMPACT).le(2, 2)).fetch());
        assertEquals(List.of("loss"), ctx.select(RISK.THREAT).from(RISK).where(row(RISK.LIKELIHOOD, RISK.IMPACT).eq(2, 2)).fetch());
        assertEquals(expected, ctx.select(RISK.THREAT).from(RISK).where(row(RISK.LIKELIHOOD, RISK.IMPACT).ne(2, 2)).fetch());
        assertEquals(List.of("loss"), ctx.select(RISK.THREAT).from(RISK).where(row(RISK.LIKELIHOOD, RISK.IMPACT).eq(row(RISK.IMPACT, RISK.LIKELIHOOD))).fetch());
        assertEquals(List.of("Laptop", "Phone"), assets(row(ASSET.OWNER_ID, ASSET.NAME).in(List.of(List.of(ada, "Laptop"), List.of(alan, "Phone"),
                List.of(grace, "Laptop")))));
        // (NULL, 'Server') <> (ada, 'Laptop') is true because the names differ
        assertEquals(List.of("Phone", "Server"), assets(row(ASSET.OWNER_ID, ASSET.NAME).notIn(List.of(List.of(ada, "Laptop")))));
        assertEquals(List.of(), assets(row(ASSET.OWNER_ID, ASSET.NAME).in(List.of())));
        assertEquals(List.of("Laptop", "Phone", "Server"), assets(row(ASSET.OWNER_ID, ASSET.NAME).notIn(List.of())));
        assertEquals(List.of("Laptop"), assets(row(ASSET.ID, ASSET.NAME).in(select(RISK.ASSET_ID, RISK.THREAT.concat("x").as("n")).from(RISK)).not()
                .and(ASSET.ID.eq(laptop))));
    }

    @Test
    void logicNestedToAnyDepth() {
        Condition c = USERS.NAME.eq("Ada");
        for (int i = 0; i < 40; i++) c = i % 2 == 0 ? or(c, USERS.NAME.eq("x" + i)) : and(c, not(USERS.EMAIL.eq("y" + i)));
        assertEquals(List.of("Ada"), users(c));
        assertEquals(List.of("Ada", "Alan"), users(or(List.of(USERS.NAME.eq("Ada"), and(List.of(USERS.ROLE.eq(AppRole.USER), USERS.ACTIVE))))));
        assertEquals(List.of("Ada", "Alan", "Grace_100%"), users(and(noCondition(), or(), and(List.of()))));
    }

    @Test
    void dynamicFilterWithBuilderWhenAndIfPresent() {
        Optional<String> name = Optional.of("a");
        Optional<AppRole> role = Optional.empty();
        String email = null;
        Condition filter = Conditions.builder(USERS.DELETED_AT.isNull())
                .addIfPresent(name, USERS.NAME::containsIgnoreCase)
                .addIfPresent(role, USERS.ROLE::eq)
                .addIf(email != null, () -> USERS.EMAIL.eq(email))
                .build();
        assertEquals(List.of("Ada", "Alan", "Grace_100%"), users(filter));
        assertEquals(List.of("Ada"), users(and(USERS.NAME.eqIfPresent(Optional.of("Ada")), when(true, () -> USERS.ACTIVE),
                USERS.ROLE.eqIfPresent(role))));
    }

    @Test
    void sortsFromParameterAndDynamicSelectList() {
        Map<String, Field<?>> sortable = Map.of("username", USERS.NAME, "email", USERS.EMAIL);
        assertEquals(List.of("Grace_100%", "Alan", "Ada"),
                ctx.select(USERS.NAME).from(USERS).orderBy(Sorts.from("username,desc", sortable)).fetch());
        assertThrows(InvalidSortException.class, () -> Sorts.from("name; DROP TABLE app_user", sortable));
        List<Field<?>> fields = List.of(USERS.NAME, USERS.EMAIL);
        assertEquals("ada@example.org", ctx.select(fields).from(USERS).where(USERS.ID.eq(ada)).fetchOne().get(USERS.EMAIL));
    }

    @Test
    void joinIfAndCreateContribution() {
        record Owned(String name, String assetName) {}
        boolean onlyWithAssets = true;
        List<Owned> owned = ctx.createContribution(Owned.class, USERS, (c, b) -> c
                        .select(USERS.NAME, ASSET.NAME.as("asset_name"))
                        .joinIf(onlyWithAssets, ASSET, ASSET.FK_OWNER_ID)
                        .where(USERS.NAME.in(b.setList("Ada", "Alan")), ASSET.VALUE.ge(b.setBigDecimal(new BigDecimal("1"))))
                        .orderBy(USERS.NAME.asc()))
                .fetch();
        assertEquals(List.of(new Owned("Ada", "Laptop"), new Owned("Alan", "Phone")), owned);
        assertEquals(3L, ctx.createContribution(Long.class, USERS, (c, b) -> c.select(count())).fetchOne());
    }

    @Test
    void writesInBothStyles() {
        QueryContext tx = ctx;
        tx.transaction(() -> {
            long id = tx.createInsert(ASSET, (c, b) -> c.set(ASSET.NAME, b.setString("Tablet")).set(ASSET.ORGANIZATION_ID, ORG))
                    .returning(ASSET.ID).fetchOne();
            // PATCH: only present values are written
            Optional<String> newName = Optional.of("Tablet 2");
            Optional<BigDecimal> newValue = Optional.empty();
            assertEquals(1, tx.createUpdate(ASSET, (c, b) -> c
                    .setIfPresent(ASSET.NAME, newName)
                    .setIfPresent(ASSET.VALUE, newValue)
                    .setIf(true, ASSET.CLASSIFICATION, "public")
                    .where(ASSET.ID.eq(id))).execute());
            assertEquals("Tablet 2", tx.select(ASSET.NAME).from(ASSET).where(ASSET.ID.eq(id)).fetchOne());
            // an update or delete whose conditions all resolve to "no condition" is rejected and changes nothing
            Optional<String> missing = Optional.empty();
            assertThrows(InvalidStatementException.class, () -> tx.createDelete(ASSET, (c, b) -> c.where(ASSET.NAME.eqIfPresent(missing))).execute());
            assertThrows(InvalidStatementException.class, () -> tx.update(ASSET).set(ASSET.CLASSIFICATION, "secret")
                    .where(Conditions.builder().build()).execute());
            assertEquals(4L, tx.selectCount().from(ASSET).fetchOne());
            // upsert on the primary key
            tx.createUpsert(ASSET, (c, b) -> c.set(ASSET.ID, id).set(ASSET.NAME, "Tablet 3").set(ASSET.ORGANIZATION_ID, ORG)).execute();
            assertEquals("Tablet 3", tx.select(ASSET.NAME).from(ASSET).where(ASSET.ID.eq(id)).fetchOne());
            assertEquals(1, tx.createDelete(ASSET, (c, b) -> c.where(ASSET.ID.eq(b.setLong(id)))).execute());
        });
        assertEquals(3L, ctx.selectCount().from(ASSET).fetchOne());
    }
}
