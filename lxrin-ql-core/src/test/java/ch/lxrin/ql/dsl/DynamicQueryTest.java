package ch.lxrin.ql.dsl;

import ch.lxrin.ql.RecordingExecutor;
import ch.lxrin.ql.TestSchema.Role;
import ch.lxrin.ql.TestSchema.UserRow;
import ch.lxrin.ql.error.InvalidSortException;
import ch.lxrin.ql.error.InvalidStatementException;
import ch.lxrin.ql.render.Bind;
import ch.lxrin.ql.render.RenderedSql;
import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.spi.TablePolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static ch.lxrin.ql.TestSchema.OrdersTable.ORDERS;
import static ch.lxrin.ql.TestSchema.UsersTable.USERS;
import static ch.lxrin.ql.dsl.Dsl.*;
import static org.junit.jupiter.api.Assertions.*;

/** Dynamic queries and the createContribution style. */
class DynamicQueryTest {

    private final RecordingExecutor db = new RecordingExecutor();
    private final QueryContext ctx = QueryContext.builder().executor(db).build();

    private static List<Object> binds(RenderedSql r) {
        List<Object> values = new ArrayList<>();
        for (Bind<?> b : r.binds()) values.add(b.value());
        return values;
    }

    // -------------------------------------------------------------------------
    // Dynamic select lists, ORDER BY, joins
    // -------------------------------------------------------------------------

    @Test
    void dynamicSelectListAndOrderBy() {
        List<Field<?>> fields = new ArrayList<>(List.of(USERS.ID, USERS.NAME));
        List<SortField<?>> sorts = List.of(USERS.NAME.desc(), USERS.ID.asc());
        assertEquals("SELECT users.id, users.name FROM users ORDER BY users.name DESC, users.id ASC",
                select(fields).from(USERS).orderBy(sorts).render().sql());
        assertEquals("SELECT users.id, users.name FROM users",
                select(fields).from(USERS).orderBy(List.<SortField<?>>of()).render().sql());
        List<SortField<?>> withNull = new ArrayList<>();
        withNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> selectFrom(USERS).orderBy(withNull));
    }

    @Test
    void sortsFromRequestParameterUseTheWhitelist() {
        Map<String, Field<?>> sortable = Map.of("username", USERS.NAME, "created", USERS.CREATED_AT);
        assertEquals("SELECT users.id FROM users ORDER BY users.name DESC",
                select(USERS.ID).from(USERS).orderBy(Sorts.from("username,desc", sortable)).render().sql());
        assertEquals("SELECT users.id FROM users ORDER BY users.name ASC, users.created_at DESC",
                select(USERS.ID).from(USERS).orderBy(Sorts.from("username;created,DESC", sortable)).render().sql());
        assertEquals(List.of(), Sorts.from((String) null, sortable));
        assertEquals(List.of(), Sorts.from("  ", sortable));
        assertEquals(1, Sorts.from(" username , asc ", sortable).size());
        assertEquals(2, Sorts.from(List.of("username,asc", "created"), sortable).size());
        assertEquals("users.id DESC", ch.lxrin.ql.RenderTestSupport.sql(Sorts.from(null, sortable, USERS.ID.desc()).get(0)));
        assertEquals("users.name ASC", ch.lxrin.ql.RenderTestSupport.sql(Sorts.from("username", sortable, USERS.ID.desc()).get(0)));

        assertThrows(InvalidSortException.class, () -> Sorts.from("password", sortable));
        assertThrows(InvalidSortException.class, () -> Sorts.from("users.name", sortable));
        assertThrows(InvalidSortException.class, () -> Sorts.from("username,desc; DROP TABLE users", sortable));
        assertThrows(InvalidSortException.class, () -> Sorts.from("username,sideways", sortable));
        assertThrows(InvalidSortException.class, () -> Sorts.from("username,asc,nulls", sortable));
        assertThrows(InvalidSortException.class, () -> Sorts.from("username;", sortable));
        assertThrows(InvalidSortException.class, () -> Sorts.from("Username", sortable));
        InvalidSortException e = assertThrows(InvalidSortException.class, () -> Sorts.from("x".repeat(500), sortable));
        assertTrue(e.getMessage().length() < 100, e.getMessage());
    }

    @Test
    void joinIf() {
        assertEquals("SELECT users.id FROM users JOIN orders ON orders.user_id = users.id WHERE orders.total > ?",
                select(USERS.ID).from(USERS)
                        .joinIf(true, ORDERS, () -> ORDERS.USER_ID.eq(USERS.ID))
                        .whereIf(true, () -> ORDERS.TOTAL.gt(BigDecimal.ONE))
                        .render().sql());
        assertEquals("SELECT users.id FROM users",
                select(USERS.ID).from(USERS).joinIf(false, ORDERS, () -> fail("not evaluated")).render().sql());
        assertEquals("SELECT users.id FROM users LEFT JOIN orders ON orders.user_id = users.id",
                select(USERS.ID).from(USERS).leftJoinIf(true, ORDERS, ORDERS.FK_USER).render().sql());
        assertEquals("SELECT users.id FROM users JOIN orders ON orders.user_id = users.id",
                select(USERS.ID).from(USERS).joinIf(true, ORDERS, ORDERS.FK_USER).render().sql());
        assertEquals("SELECT users.id FROM users",
                select(USERS.ID).from(USERS).leftJoinIf(false, ORDERS, () -> fail("not evaluated")).render().sql());
    }

    @Test
    void whereAndHavingWithLists() {
        List<Condition> filters = List.of(USERS.ACTIVE, USERS.NAME.eq("a"));
        RenderedSql r = select(USERS.ROLE).from(USERS).where(filters).groupBy(USERS.ROLE).having(List.of(count().gt(1L))).render();
        assertEquals("SELECT users.role FROM users WHERE (users.active AND users.name = ?) GROUP BY users.role HAVING count(*) > ?", r.sql());
        assertEquals(List.of("a", 1L), binds(r));
    }

    // -------------------------------------------------------------------------
    // UPDATE / DELETE safety and PATCH
    // -------------------------------------------------------------------------

    @Test
    void updateAndDeleteWhoseConditionsAreAllNoConditionAreRejected() {
        Condition none = Conditions.builder().addIfPresent(Optional.<String>empty(), USERS.NAME::eq).build();
        assertThrows(InvalidStatementException.class, () -> ctx.update(USERS).set(USERS.NAME, "x").where(none).execute());
        assertThrows(InvalidStatementException.class, () -> ctx.update(USERS).set(USERS.NAME, "x")
                .where(and(noCondition(), or(List.of()))).whereIf(false, () -> USERS.ACTIVE).execute());
        assertThrows(InvalidStatementException.class, () -> ctx.deleteFrom(USERS).where(List.of(noCondition(), none)).execute());
        assertThrows(InvalidStatementException.class, () -> ctx.createDelete(USERS, (c, b) -> c.where(when(false, () -> USERS.ACTIVE))).execute());
        assertThrows(InvalidStatementException.class, () -> ctx.createUpdate(USERS, (c, b) -> c.set(USERS.NAME, "x")).execute());
        assertTrue(db.statements.isEmpty());

        db.willAffect(3).willAffect(4).willAffect(5);
        assertEquals(3, ctx.update(USERS).set(USERS.NAME, "x").where(none).all().execute());
        assertEquals("UPDATE users SET name = ?", db.lastSql());
        assertEquals(4, ctx.createDelete(USERS, (c, b) -> c.all()).execute());
        assertEquals("DELETE FROM users", db.lastSql());
        assertEquals(5, ctx.createUpdate(USERS, (c, b) -> c.set(USERS.ACTIVE, false).all()).execute());
    }

    @Test
    void setIfAndSetIfPresentForPatch() {
        Optional<String> name = Optional.of("Ada");
        Optional<String> email = Optional.empty();
        UUID id = UUID.randomUUID();
        Update<UserRow> update = update(USERS)
                .setIfPresent(USERS.NAME, name)
                .setIfPresent(USERS.EMAIL, email)
                .setIf(true, USERS.ACTIVE, false)
                .setIf(false, USERS.ROLE, Role.ADMIN)
                .setIf(true, USERS.VERSION, USERS.VERSION.plus(1L))
                .where(USERS.ID.eq(id));
        assertTrue(update.hasAssignments());
        RenderedSql r = update.render();
        assertEquals("UPDATE users SET name = ?, active = ?, version = (users.version + ?) WHERE users.id = ?", r.sql());
        assertEquals(List.of("Ada", false, 1L, id), binds(r));
        assertFalse(update(USERS).setIfPresent(USERS.NAME, Optional.empty()).hasAssignments());

        assertEquals("INSERT INTO users (name) VALUES (?)",
                insertInto(USERS).setIfPresent(USERS.NAME, name).setIfPresent(USERS.EMAIL, email).setIf(false, USERS.ACTIVE, true).render().sql());
    }

    // -------------------------------------------------------------------------
    // createContribution style
    // -------------------------------------------------------------------------

    record UserSummary(UUID id, String name, String email) {}

    record Renamed(String email, UUID id) {}

    record Positional(UUID a, String b) {}

    record Primitive(long version) {}

    @Test
    void createContributionMapsRecordsByName() {
        UUID id = UUID.randomUUID();
        Instant since = Instant.parse("2025-01-01T00:00:00Z");
        db.willReturn(new Object[] {id, "Ada", "ada@example.org"});
        List<UserSummary> users = ctx.createContribution(UserSummary.class, USERS, (c, b) -> c
                        .select(USERS.ID, USERS.NAME, USERS.EMAIL)
                        .where(USERS.EMAIL.endsWith("@example.org"),
                               USERS.CREATED_AT.ge(b.setInstant(since)),
                               USERS.ROLE.in(b.setList(Role.ADMIN, Role.USER)))
                        .orderBy(USERS.NAME.asc()))
                .fetch();
        assertEquals(List.of(new UserSummary(id, "Ada", "ada@example.org")), users);
        assertEquals("SELECT users.id, users.name, users.email FROM users WHERE (users.email LIKE ? AND users.created_at >= ? "
                + "AND users.role = ANY(?)) ORDER BY users.name ASC", db.lastSql());

        // by name, in any order and with aliases
        db.willReturn(new Object[] {id, "ada@example.org"});
        assertEquals(new Renamed("ada@example.org", id),
                ctx.createContribution(Renamed.class, USERS, (c, b) -> c.select(USERS.ID, USERS.EMAIL)).fetchOne());
        db.willReturn(new Object[] {id, "x"});
        assertEquals(new Positional(id, "x"),
                ctx.createContribution(Positional.class, USERS, (c, b) -> c.select(USERS.ID.as("a"), USERS.NAME.as("b"))).fetchOne());
        // by position when the names do not match
        db.willReturn(new Object[] {id, "x"});
        assertEquals(new Positional(id, "x"),
                ctx.createContribution(Positional.class, USERS, (c, b) -> c.select(USERS.ID, USERS.NAME)).fetchOne());
    }

    @Test
    void createContributionChecksTypesWhenTheQueryIsBuilt() {
        assertThrows(IllegalArgumentException.class,
                () -> createContribution(UserSummary.class, USERS, (c, b) -> c.select(USERS.ID, USERS.VERSION, USERS.EMAIL)));
        assertThrows(IllegalArgumentException.class,
                () -> createContribution(Positional.class, USERS, (c, b) -> c.select(USERS.NAME, USERS.ID)));
        assertThrows(IllegalArgumentException.class,
                () -> createContribution(UserSummary.class, USERS, (c, b) -> c.select(USERS.ID)));
        assertThrows(IllegalArgumentException.class,
                () -> createContribution(StringBuilder.class, USERS, (c, b) -> c.select(USERS.NAME)));
        assertThrows(IllegalArgumentException.class, () -> createContribution(UserSummary.class, USERS, (c, b) -> null));
        assertThrows(IllegalArgumentException.class, () -> createContribution(String.class, USERS, (c, b) -> c.select(USERS.NAME)
                .where(USERS.EMAIL.eq(b.setString(null)))));

        db.willReturn(new Object[] {null});
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> ctx.createContribution(Primitive.class, USERS, (c, b) -> c.select(USERS.VERSION)).fetchOne());
        assertTrue(e.getMessage().contains("primitive"));
    }

    @Test
    void createContributionForScalarsRowsAndAllColumns() {
        db.willReturn(new Object[] {7L});
        assertEquals(7L, ctx.createContribution(Long.class, USERS, (c, b) -> c.select(count()).where(USERS.ACTIVE)).fetchOne());
        assertEquals("SELECT count(*) FROM users WHERE users.active", db.lastSql());

        db.willReturn(new Object[] {"Ada"});
        Row row = ctx.createContribution(Row.class, USERS, (c, b) -> c.select(List.of(USERS.NAME))).fetchOne();
        assertEquals("Ada", row.get(USERS.NAME));

        assertEquals("SELECT users.id, users.name, users.email, users.role, users.active, users.created_at, users.deleted_at, "
                        + "users.tags, users.settings, users.version FROM users",
                createContribution(Row.class, USERS, (c, b) -> c.selectAll()).render().sql());
        assertEquals("SELECT DISTINCT users.name FROM users",
                createContribution(String.class, USERS, (c, b) -> c.selectDistinct(USERS.NAME)).render().sql());
        assertEquals("SELECT users.name FROM users JOIN orders ON orders.user_id = users.id",
                createContribution(String.class, (c, b) -> c.select(USERS.NAME).from(USERS).join(ORDERS).onKey(ORDERS.FK_USER))
                        .render().sql());
    }

    @Test
    void bothStylesRenderTheSameStatement() {
        Instant since = Instant.parse("2025-01-01T00:00:00Z");
        RenderedSql contribution = createContribution(UserSummary.class, USERS, (c, b) -> c
                .select(USERS.ID, USERS.NAME, USERS.EMAIL)
                .where(USERS.CREATED_AT.ge(b.setInstant(since)), USERS.NAME.in(b.setList(List.of("a", "b")))))
                .render();
        RenderedSql dsl = select(USERS.ID, USERS.NAME, USERS.EMAIL).from(USERS)
                .where(ge(USERS.CREATED_AT, since), in(USERS.NAME, "a", "b"))
                .render();
        assertEquals(dsl.sql(), contribution.sql());
        assertEquals(binds(dsl).size(), binds(contribution).size());
        assertEquals(dsl.binds().get(0).value(), contribution.binds().get(0).value());
        assertArrayEquals((Object[]) dsl.binds().get(1).value(), (Object[]) contribution.binds().get(1).value());
    }

    @Test
    void createInsertUpdateDeleteAndUpsert() {
        UUID id = UUID.randomUUID();
        assertEquals("INSERT INTO users (id, name, email) VALUES (?, ?, ?)",
                createInsert(USERS, (c, b) -> c.set(USERS.ID, id).set(USERS.NAME, b.setString("Ada")).set(USERS.EMAIL, "a@x")).render().sql());
        RenderedSql update = createUpdate(USERS, (c, b) -> c
                .set(USERS.NAME, b.setString("Ada"))
                .setIfPresent(USERS.EMAIL, Optional.empty())
                .where(USERS.ID.eq(id))).render();
        assertEquals("UPDATE users SET name = ? WHERE users.id = ?", update.sql());
        assertEquals(List.of("Ada", id), binds(update));
        assertEquals("DELETE FROM users WHERE users.deleted_at < ?",
                createDelete(USERS, (c, b) -> c.where(USERS.DELETED_AT.lt(b.setInstant(Instant.EPOCH)))).render().sql());
        assertEquals("INSERT INTO users (id, name, email) VALUES (?, ?, ?) ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, "
                        + "email = EXCLUDED.email",
                createUpsert(USERS, (c, b) -> c.set(USERS.ID, id).set(USERS.NAME, "Ada").set(USERS.EMAIL, "a@x")).render().sql());
        assertEquals("INSERT INTO users (id) VALUES (?) ON CONFLICT (id) DO NOTHING",
                createUpsert(USERS, (c, b) -> c.set(USERS.ID, id)).render().sql());
        assertEquals("INSERT INTO users (id, email) VALUES (?, ?) ON CONFLICT (email) DO NOTHING",
                createUpsert(USERS, (c, b) -> c.set(USERS.ID, id).set(USERS.EMAIL, "a@x").onConflict(USERS.EMAIL).doNothing())
                        .render().sql());
    }

    @Test
    void contributionsRunThroughThePipeline() {
        TablePolicy onlyActive = new TablePolicy() {
            @Override
            public boolean appliesTo(ch.lxrin.ql.schema.Table<?> table) {
                return table.sameTable(USERS);
            }

            @Override
            public Condition filter(ch.lxrin.ql.schema.Table<?> table, ch.lxrin.ql.spi.PolicyContext context) {
                return USERS.ACTIVE;
            }
        };
        QueryContext policed = QueryContext.builder().executor(db).policy(onlyActive).build();
        db.willReturn();
        policed.createContribution(String.class, USERS, (c, b) -> c.select(USERS.NAME).where(USERS.NAME.startsWith("A"))).fetch();
        assertTrue(db.lastSql().contains("users.active"), db.lastSql());
    }

    @Test
    void bindsRejectNullAndOfferExplicitNull() {
        Binds b = Binds.INSTANCE;
        assertThrows(IllegalArgumentException.class, () -> b.setString(null));
        assertThrows(IllegalArgumentException.class, () -> b.setInstant(null));
        assertThrows(IllegalArgumentException.class, () -> b.setList((List<String>) null));
        assertEquals("UPDATE users SET deleted_at = CAST(NULL AS timestamptz) WHERE users.id = ?",
                createUpdate(USERS, (c, bb) -> c.set(USERS.DELETED_AT, bb.setNull(ch.lxrin.ql.types.SqlTypes.TIMESTAMPTZ))
                        .where(USERS.ID.eq(bb.set(USERS.ID, UUID.randomUUID())))).render().sql());
        assertEquals(List.of(1, 2L, (short) 3, 4.0, 5f, true, "x"), List.of(
                b.setInt(1), b.setLong(2L), b.setShort((short) 3), b.setDouble(4.0), b.setFloat(5f), b.setBoolean(true), b.setString("x"))
                .stream().map(f -> ch.lxrin.ql.RenderTestSupport.binds(f).get(0)).toList());
        assertEquals("int2", b.setShort((short) 1).type().sqlName());
        assertEquals("time", b.setLocalTime(java.time.LocalTime.NOON).type().sqlName());
        assertEquals("interval", b.setDuration(java.time.Duration.ofMinutes(1)).type().sqlName());
        assertEquals("bytea", b.setBytes(new byte[] {1}).type().sqlName());
        assertEquals("uuid", b.setUuid(UUID.randomUUID()).type().sqlName());
        assertEquals("numeric", b.setBigDecimal(BigDecimal.ONE).type().sqlName());
        assertEquals("date", b.setLocalDate(java.time.LocalDate.EPOCH).type().sqlName());
        assertEquals("timestamp", b.setLocalDateTime(java.time.LocalDateTime.MIN).type().sqlName());
        assertEquals("text", b.set("x", ch.lxrin.ql.types.SqlTypes.TEXT).type().sqlName());
    }
}
