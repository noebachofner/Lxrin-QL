package ch.lxrin.ql.dsl;

import ch.lxrin.ql.TestSchema.Role;
import ch.lxrin.ql.TestSchema.UserRow;
import ch.lxrin.ql.render.Bind;
import ch.lxrin.ql.render.RenderedSql;
import ch.lxrin.ql.types.SqlTypes;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static ch.lxrin.ql.TestSchema.OrdersTable.ORDERS;
import static ch.lxrin.ql.TestSchema.UsersTable.USERS;
import static ch.lxrin.ql.dsl.Dsl.*;
import static org.junit.jupiter.api.Assertions.*;

class DmlTest {

    private static void assertRender(String sql, RenderedSql r, Object... binds) {
        assertEquals(sql, r.sql());
        List<Object> values = new ArrayList<>();
        for (Bind<?> b : r.binds()) values.add(b.value());
        assertEquals(java.util.Arrays.asList(binds), values);
    }

    @Test
    void insertSetStyle() {
        UUID id = UUID.randomUUID();
        assertRender("INSERT INTO users (id, name, email) VALUES (?, ?, ?)",
                insertInto(USERS).set(USERS.ID, id).set(USERS.NAME, "Ada").set(USERS.EMAIL, "ada@example.org").render(),
                id, "Ada", "ada@example.org");
    }

    @Test
    void insertMultipleRowsFillsMissingColumnsWithDefault() {
        assertRender("INSERT INTO users (name, email, role) VALUES (?, ?, DEFAULT), (?, DEFAULT, ?)",
                insertInto(USERS).set(USERS.NAME, "A").set(USERS.EMAIL, "a@x").newRow().set(USERS.NAME, "B").set(USERS.ROLE, Role.ADMIN).render(),
                "A", "a@x", "B", Role.ADMIN);
        assertThrows(IllegalStateException.class, () -> insertInto(USERS).newRow());
    }

    @Test
    void insertTypedColumnsAndValues() {
        assertRender("INSERT INTO users (name, email) VALUES (?, ?), (?, ?)",
                insertInto(USERS).columns(USERS.NAME, USERS.EMAIL).values("Ada", "a@x").values("Alan", "b@x").render(),
                "Ada", "a@x", "Alan", "b@x");
        assertRender("INSERT INTO users (name, created_at) VALUES (upper(?), now())",
                insertInto(USERS).columns(USERS.NAME, USERS.CREATED_AT).values(param("a").upper(), now()).render(), "a");
        assertRender("INSERT INTO users (name, email) SELECT users.name, users.email FROM users WHERE users.active",
                insertInto(USERS).columns(USERS.NAME, USERS.EMAIL).select(select(USERS.NAME, USERS.EMAIL).from(USERS).where(USERS.ACTIVE)).render());
        assertRender("INSERT INTO users (name) VALUES (?)", insertInto(USERS).columns(USERS.NAME).values((String) null).render(), (Object) null);
        assertThrows(IllegalStateException.class, () -> insertInto(USERS).set(USERS.NAME, "x").columns(USERS.NAME));
    }

    @Test
    void insertDefaultValuesAndOverriding() {
        assertRender("INSERT INTO orders DEFAULT VALUES", insertInto(ORDERS).defaultValues().render());
        assertRender("INSERT INTO orders (id) OVERRIDING SYSTEM VALUE VALUES (?)",
                insertInto(ORDERS).set(ORDERS.ID, 5L).overridingSystemValue().render(), 5L);
        assertRender("INSERT INTO orders (id) OVERRIDING USER VALUE VALUES (?)",
                insertInto(ORDERS).set(ORDERS.ID, 5L).overridingUserValue().render(), 5L);
        assertThrows(IllegalStateException.class, () -> insertInto(ORDERS).render());
    }

    @Test
    void upserts() {
        assertRender("INSERT INTO users (email, name) VALUES (?, ?) ON CONFLICT (email) DO UPDATE SET name = EXCLUDED.name, "
                        + "version = (users.version + ?) WHERE users.active",
                insertInto(USERS).set(USERS.EMAIL, "a@x").set(USERS.NAME, "A")
                        .onConflict(USERS.EMAIL).doUpdateSetExcluded(USERS.NAME).doUpdateSet(USERS.VERSION, USERS.VERSION.plus(1L))
                        .doUpdateWhere(USERS.ACTIVE).render(),
                "a@x", "A", 1L);
        assertRender("INSERT INTO users (email) VALUES (?) ON CONFLICT DO NOTHING",
                insertInto(USERS).set(USERS.EMAIL, "a@x").doNothing().render(), "a@x");
        assertRender("INSERT INTO users (email) VALUES (?) ON CONFLICT ON CONSTRAINT users_email_key DO NOTHING",
                insertInto(USERS).set(USERS.EMAIL, "a@x").onConflictOnConstraint(USERS.UK_EMAIL).doNothing().render(), "a@x");
        assertRender("INSERT INTO users (email) VALUES (?) ON CONFLICT (email) WHERE deleted_at IS NULL DO UPDATE SET role = ?",
                insertInto(USERS).set(USERS.EMAIL, "a@x").onConflict(USERS.EMAIL).onConflictWhere(USERS.DELETED_AT.isNull())
                        .doUpdateSet(USERS.ROLE, Role.USER).render(), "a@x", Role.USER);
        assertThrows(IllegalStateException.class, () -> insertInto(USERS).set(USERS.EMAIL, "x").doUpdateSet(USERS.NAME, "y"));
        assertThrows(IllegalStateException.class, () -> insertInto(USERS).set(USERS.EMAIL, "x").onConflict(USERS.EMAIL).render());
        assertThrows(IllegalStateException.class, () -> insertInto(USERS).set(USERS.EMAIL, "x").onConflict().doUpdateSet(USERS.NAME, "y").render());
    }

    @Test
    void returning() {
        Returning<UUID> one = insertInto(USERS).set(USERS.NAME, "A").returning(USERS.ID);
        assertEquals("INSERT INTO users (name) VALUES (?) RETURNING users.id", one.render().sql());
        Returning<Row2<UUID, Instant>> two = insertInto(USERS).set(USERS.NAME, "A").returning(USERS.ID, USERS.CREATED_AT);
        assertEquals("INSERT INTO users (name) VALUES (?) RETURNING users.id, users.created_at", two.render().sql());
        Returning<UserRow> all = Dsl.update(USERS).set(USERS.NAME, "B").where(USERS.ID.eq(UUID.randomUUID())).returningAll();
        assertTrue(all.render().sql().endsWith("RETURNING users.id, users.name, users.email, users.role, users.active, "
                + "users.created_at, users.deleted_at, users.tags, users.settings, users.version"));
        assertEquals(10, all.fields().size());
        assertEquals("DELETE FROM orders WHERE orders.id = ? RETURNING upper(orders.status) AS s",
                deleteFrom(ORDERS).where(ORDERS.ID.eq(1L)).returning(ORDERS.STATUS.upper().as("s")).render().sql());
    }

    @Test
    void update() {
        UUID id = UUID.randomUUID();
        assertRender("UPDATE users SET role = ?, deleted_at = CAST(NULL AS timestamptz), version = (users.version + ?) WHERE users.id = ?",
                Dsl.update(USERS).set(USERS.ROLE, Role.ADMIN).setNull(USERS.DELETED_AT).set(USERS.VERSION, USERS.VERSION.plus(1L))
                        .where(USERS.ID.eq(id)).render(),
                Role.ADMIN, 1L, id);
        assertRender("UPDATE orders AS o SET status = ? FROM users WHERE (o.user_id = users.id AND users.active)",
                Dsl.update(ORDERS.as("o")).set(ORDERS.as("o").STATUS, "VIP").from(USERS)
                        .where(ORDERS.as("o").USER_ID.eq(USERS.ID)).where(USERS.ACTIVE).render(), "VIP");
        assertRender("UPDATE users SET active = ?", Dsl.update(USERS).set(USERS.ACTIVE, false).allRows().render(), false);
        assertThrows(IllegalStateException.class, () -> Dsl.update(USERS).set(USERS.ACTIVE, false).render());
        assertThrows(IllegalStateException.class, () -> Dsl.update(USERS).where(USERS.ACTIVE).render());
        assertRender("UPDATE users SET name = ? WHERE users.active", Dsl.update(USERS).setUnchecked(USERS.NAME, "x")
                .whereIf(true, () -> USERS.ACTIVE).whereIf(false, () -> USERS.ACTIVE).render(), "x");
        assertThrows(IllegalArgumentException.class, () -> Dsl.update(USERS).setUnchecked(USERS.NAME, 1));
        assertThrows(IllegalArgumentException.class, () -> Dsl.update(USERS).set(ORDERS.STATUS, "x"));
    }

    @Test
    void delete() {
        assertRender("DELETE FROM orders USING users WHERE (orders.user_id = users.id AND users.deleted_at IS NOT NULL)",
                deleteFrom(ORDERS).using(USERS).where(ORDERS.USER_ID.eq(USERS.ID), USERS.DELETED_AT.isNotNull()).render());
        assertRender("DELETE FROM orders", deleteFrom(ORDERS).allRows().render());
        assertThrows(IllegalStateException.class, () -> deleteFrom(ORDERS).render());
        assertRender("DELETE FROM orders WHERE orders.ordered_on < ?",
                deleteFrom(ORDERS).whereIf(true, () -> ORDERS.ORDERED_ON.lt(LocalDate.of(2020, 1, 1))).render(), LocalDate.of(2020, 1, 1));
    }

    @Test
    void truncate() {
        assertEquals("TRUNCATE orders, users RESTART IDENTITY CASCADE", Dsl.truncate(ORDERS, USERS).restartIdentity().cascade().toString());
        assertThrows(IllegalArgumentException.class, Dsl::truncate);
    }

    @Test
    void insertSetUncheckedAndGuards() {
        assertRender("INSERT INTO orders (total) VALUES (?)", insertInto(ORDERS).setUnchecked(ORDERS.TOTAL, BigDecimal.ONE).render(), BigDecimal.ONE);
        assertThrows(IllegalArgumentException.class, () -> insertInto(ORDERS).setUnchecked(ORDERS.TOTAL, "1"));
        assertThrows(IllegalArgumentException.class, () -> insertInto(ORDERS).set(USERS.NAME, "x"));
        assertThrows(IllegalStateException.class, () -> insertInto(ORDERS).set(ORDERS.ID, 1L).defaultValues());
        assertEquals("INSERT INTO users (tags) VALUES (?)", insertInto(USERS).set(USERS.TAGS, new String[]{"a"}).render().sql());
        assertEquals(SqlTypes.TEXT.array().sqlName(), insertInto(USERS).set(USERS.TAGS, new String[]{"a"}).render().binds().get(0).type().sqlName());
    }
}
