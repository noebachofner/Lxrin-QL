package ch.lxrin.ql.dsl;

import ch.lxrin.ql.TestSchema.OrdersTable;
import ch.lxrin.ql.TestSchema.Role;
import ch.lxrin.ql.TestSchema.UsersTable;
import ch.lxrin.ql.render.RenderedSql;
import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.types.SqlTypes;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static ch.lxrin.ql.RenderTestSupport.assertSql;
import static ch.lxrin.ql.TestSchema.OrdersTable.ORDERS;
import static ch.lxrin.ql.TestSchema.UsersTable.USERS;
import static ch.lxrin.ql.dsl.Dsl.*;
import static org.junit.jupiter.api.Assertions.*;

class SelectTest {

    private static void assertRender(String sql, AbstractSelect<?, ?> select, Object... binds) {
        RenderedSql r = select.render();
        assertEquals(sql, r.sql());
        List<Object> values = new java.util.ArrayList<>();
        r.binds().forEach(b -> values.add(b.value() instanceof Object[] ? List.of((Object[]) b.value()) : b.value()));
        List<Object> expected = new java.util.ArrayList<>();
        for (Object o : binds) expected.add(o instanceof Object[] ? List.of((Object[]) o) : o);
        assertEquals(expected, values);
    }

    @Test
    void targetExample() {
        assertRender("SELECT users.id, users.name, users.email FROM users WHERE users.email LIKE ? ORDER BY users.name ASC",
                select(USERS.ID, USERS.NAME, USERS.EMAIL).from(USERS)
                        .where(USERS.EMAIL.endsWith("@gmail.com"))
                        .orderBy(USERS.NAME.asc()),
                "%@gmail.com");
    }

    @Test
    void selectFromListsAllColumns() {
        assertRender("SELECT users.id, users.name, users.email, users.role, users.active, users.created_at, users.deleted_at, "
                + "users.tags, users.settings, users.version FROM users WHERE users.role = ?",
                selectFrom(USERS).where(USERS.ROLE.eq(Role.ADMIN)), Role.ADMIN);
    }

    @Test
    void severalWhereCallsAreJoinedWithAnd() {
        assertRender("SELECT users.id FROM users WHERE (users.role = ? AND users.deleted_at IS NULL AND users.active)",
                select(USERS.ID).from(USERS).where(USERS.ROLE.eq(Role.ADMIN)).where(USERS.DELETED_AT.isNull(), USERS.ACTIVE),
                Role.ADMIN);
        assertRender("SELECT users.id FROM users", select(USERS.ID).from(USERS).whereIf(false, () -> USERS.ACTIVE));
        assertRender("SELECT users.id FROM users WHERE users.active", select(USERS.ID).from(USERS).whereIf(true, () -> USERS.ACTIVE));
    }

    @Test
    void joins() {
        UsersTable u = USERS.as("u");
        OrdersTable o = ORDERS.as("o");
        assertRender("SELECT u.name, o.total FROM users AS u JOIN orders AS o ON o.user_id = u.id",
                select(u.NAME, o.TOTAL).from(u).join(o).on(o.USER_ID.eq(u.ID)));
        assertRender("SELECT u.name FROM users AS u LEFT JOIN orders AS o ON o.user_id = u.id",
                select(u.NAME).from(u).leftJoin(o).onKey(ORDERS.FK_USER));
        assertRender("SELECT u.name FROM orders AS o RIGHT JOIN users AS u ON o.user_id = u.id",
                select(u.NAME).from(o).rightJoin(u).onKey(ORDERS.FK_USER));
        assertRender("SELECT users.name FROM users FULL JOIN orders USING (id)",
                select(USERS.NAME).from(USERS).fullJoin(ORDERS).using(ORDERS.ID));
        assertRender("SELECT users.name FROM users CROSS JOIN orders NATURAL JOIN orders AS o",
                select(USERS.NAME).from(USERS).crossJoin(ORDERS).naturalJoin(o));
        assertRender("SELECT users.name FROM users JOIN orders ON TRUE",
                select(USERS.NAME).from(USERS).innerJoin(ORDERS).onTrue().attach(null));
        assertThrows(IllegalArgumentException.class, () -> select(USERS.NAME).from(ORDERS).join(ORDERS.as("x")).onKey(ORDERS.FK_USER));
    }

    @Test
    void groupingHavingAndAggregates() {
        assertRender("SELECT orders.user_id, count(*) FILTER (WHERE orders.status = ?), sum(orders.total) FROM orders "
                        + "GROUP BY orders.user_id HAVING sum(orders.total) > ?",
                select(ORDERS.USER_ID, count().filter(ORDERS.STATUS.eq("PAID")), sum(ORDERS.TOTAL))
                        .from(ORDERS).groupBy(ORDERS.USER_ID).having(sum(ORDERS.TOTAL).gt(new BigDecimal("1000"))),
                "PAID", new BigDecimal("1000"));
        assertRender("SELECT count(DISTINCT orders.user_id), max(orders.total) FROM orders GROUP BY ROLLUP (orders.status, orders.user_id)",
                select(countDistinct(ORDERS.USER_ID), max(ORDERS.TOTAL)).from(ORDERS).groupBy(rollup(ORDERS.STATUS, ORDERS.USER_ID)));
        assertRender("SELECT GROUPING(orders.status) FROM orders GROUP BY GROUPING SETS ((orders.status), ()), CUBE (orders.user_id)",
                select(grouping(ORDERS.STATUS)).from(ORDERS)
                        .groupBy(groupingSets(groupingSet(ORDERS.STATUS), groupingSet()), cube(ORDERS.USER_ID)));
        assertRender("SELECT min(users.name), avg(orders.total) FROM users, orders",
                select(min(USERS.NAME), avg(ORDERS.TOTAL)).from(USERS, ORDERS));
    }

    @Test
    void windowFunctions() {
        WindowDefinition w = window("w", partitionBy(ORDERS.USER_ID).orderBy(ORDERS.ORDERED_ON.asc()));
        assertRender("SELECT row_number() OVER w, sum(orders.total) OVER (PARTITION BY orders.user_id ORDER BY orders.ordered_on ASC "
                        + "ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW), rank() OVER () FROM orders WINDOW w AS "
                        + "(PARTITION BY orders.user_id ORDER BY orders.ordered_on ASC)",
                select(rowNumber().over(w),
                        sum(ORDERS.TOTAL).over(partitionBy(ORDERS.USER_ID).orderBy(ORDERS.ORDERED_ON.asc())
                                .rowsBetween(unboundedPreceding(), currentRow())),
                        rank().over())
                        .from(ORDERS).window(w));
        assertSql("(ORDER BY orders.id ASC RANGE BETWEEN 2 PRECEDING AND 3 FOLLOWING EXCLUDE CURRENT ROW)",
                orderBy(ORDERS.ID.asc()).rangeBetween(preceding(2), following(3)).exclude(WindowSpec.Exclude.CURRENT_ROW));
        assertSql("(w GROUPS BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING)", WindowSpec.basedOn(w).groupsBetween(currentRow(), unboundedFollowing()));
        assertSql("(ROWS UNBOUNDED PRECEDING)", window().rows(unboundedPreceding()));
        assertSql("dense_rank() OVER (RANGE CURRENT ROW)", denseRank().over(window().range(currentRow())));
        assertThrows(IllegalStateException.class, () -> window().exclude(WindowSpec.Exclude.TIES));
        assertThrows(IllegalArgumentException.class, () -> preceding(-1));
        assertTrue(sum(ORDERS.TOTAL).over() instanceof NumberField);
    }

    @Test
    void orderLimitOffsetPaging() {
        assertRender("SELECT users.name FROM users ORDER BY users.name ASC, users.created_at DESC NULLS LAST LIMIT 20 OFFSET 40",
                select(USERS.NAME).from(USERS).orderBy(USERS.NAME.asc(), USERS.CREATED_AT.desc().nullsLast()).limit(20).offset(40));
        assertRender("SELECT users.name FROM users ORDER BY users.name ASC LIMIT 25 OFFSET 50",
                select(USERS.NAME).from(USERS).orderBy(USERS.NAME).page(2, 25));
        assertRender("SELECT orders.id FROM orders ORDER BY orders.total DESC FETCH FIRST 3 ROWS WITH TIES",
                select(ORDERS.ID).from(ORDERS).orderBy(ORDERS.TOTAL.desc()).limitWithTies(3));
        assertRender("SELECT users.name FROM users LIMIT ? OFFSET ?",
                select(USERS.NAME).from(USERS).limit(param(10)).offset(param(5L)), 10, 5L);
        assertThrows(IllegalArgumentException.class, () -> select(USERS.NAME).page(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> select(USERS.NAME).limit(-1));
    }

    @Test
    void distinctAndLocking() {
        assertRender("SELECT DISTINCT users.role FROM users", select(USERS.ROLE).distinct().from(USERS));
        assertRender("SELECT DISTINCT ON (orders.user_id) orders.user_id, orders.id FROM orders ORDER BY orders.user_id ASC, orders.ordered_on DESC",
                select(ORDERS.USER_ID, ORDERS.ID).distinctOn(ORDERS.USER_ID).from(ORDERS).orderBy(ORDERS.USER_ID.asc(), ORDERS.ORDERED_ON.desc()));
        assertRender("SELECT users.id FROM users FOR UPDATE OF users SKIP LOCKED FOR SHARE NOWAIT",
                select(USERS.ID).from(USERS).forUpdate().of(USERS).skipLocked().forShare().nowait());
        assertRender("SELECT users.id FROM users FOR NO KEY UPDATE FOR KEY SHARE",
                select(USERS.ID).from(USERS).forNoKeyUpdate().forKeyShare());
        assertThrows(IllegalStateException.class, () -> select(USERS.ID).skipLocked());
    }

    @Test
    void setOperations() {
        assertRender("SELECT users.email FROM users UNION (SELECT users.name FROM users) ORDER BY users.email ASC",
                select(USERS.EMAIL).from(USERS).union(select(USERS.NAME).from(USERS)).orderBy(USERS.EMAIL.asc()));
        assertRender("SELECT users.email FROM users UNION ALL (SELECT users.email FROM users) INTERSECT (SELECT users.email FROM users) "
                        + "INTERSECT ALL (SELECT users.email FROM users) EXCEPT (SELECT users.email FROM users) EXCEPT ALL (SELECT users.email FROM users)",
                select(USERS.EMAIL).from(USERS).unionAll(select(USERS.EMAIL).from(USERS)).intersect(select(USERS.EMAIL).from(USERS))
                        .intersectAll(select(USERS.EMAIL).from(USERS)).except(select(USERS.EMAIL).from(USERS))
                        .exceptAll(select(USERS.EMAIL).from(USERS)));
    }

    @Test
    void subqueries() {
        assertRender("SELECT users.name FROM users WHERE users.id IN (SELECT orders.user_id FROM orders WHERE orders.total > ?)",
                select(USERS.NAME).from(USERS).where(USERS.ID.in(select(ORDERS.USER_ID).from(ORDERS).where(ORDERS.TOTAL.gt(BigDecimal.TEN)))),
                BigDecimal.TEN);
        assertRender("SELECT users.name FROM users WHERE (EXISTS (SELECT 1 FROM orders WHERE orders.user_id = users.id) "
                        + "AND NOT EXISTS (SELECT 1 FROM orders WHERE orders.status = ?))",
                select(USERS.NAME).from(USERS).where(
                        exists(selectOne().from(ORDERS).where(ORDERS.USER_ID.eq(USERS.ID))),
                        notExists(selectOne().from(ORDERS).where(ORDERS.STATUS.eq("X")))),
                "X");
        Field<Long> orderCount = selectCount().from(ORDERS).where(ORDERS.USER_ID.eq(USERS.ID)).asField().as("orders");
        assertRender("SELECT users.name, (SELECT count(*) FROM orders WHERE orders.user_id = users.id) AS orders FROM users",
                select(USERS.NAME, orderCount).from(USERS));
        assertRender("SELECT users.id FROM users WHERE users.id NOT IN (SELECT orders.user_id FROM orders)",
                select(USERS.ID).from(USERS).where(USERS.ID.notIn(select(ORDERS.USER_ID).from(ORDERS))));
    }

    @Test
    void commonTableExpressions() {
        Field<BigDecimal> total = sum(ORDERS.TOTAL).as("total");
        Cte paid = cte("paid", select(ORDERS.USER_ID, total).from(ORDERS).where(ORDERS.STATUS.eq("PAID")).groupBy(ORDERS.USER_ID));
        assertRender("WITH paid(user_id, total) AS (SELECT orders.user_id, sum(orders.total) AS total FROM orders WHERE orders.status = ? "
                        + "GROUP BY orders.user_id) SELECT users.name, paid.total FROM users JOIN paid ON paid.user_id = users.id WHERE paid.total > ?",
                select(USERS.NAME, paid.field(total)).with(paid).from(USERS).join(paid).on(paid.field(ORDERS.USER_ID).eq(USERS.ID))
                        .where(paid.field(total).gt(BigDecimal.ONE)),
                "PAID", BigDecimal.ONE);
        assertThrows(IllegalArgumentException.class, () -> cte("x", select(sum(ORDERS.TOTAL)).from(ORDERS)));

        Cte t = recursiveCte("t");
        Column<Integer> n = t.declareColumn("n", SqlTypes.INT4);
        t.as(select(inline(1)).unionAll(select(n.cast(SqlTypes.INT4)).from(t).where(n.lt(10))));
        assertRender("WITH RECURSIVE t(n) AS (SELECT 1 UNION ALL (SELECT CAST(t.n AS int4) FROM t WHERE t.n < ?)) SELECT t.n FROM t",
                select(n).with(t).from(t), 10);
        Cte m = cte("m", select(USERS.ID).from(USERS)).materialized();
        assertRender("WITH m(id) AS MATERIALIZED (SELECT users.id FROM users) SELECT m.id FROM m", select(m.field(USERS.ID)).with(m).from(m));
        Cte nm = cte("nm", select(USERS.ID).from(USERS)).notMaterialized();
        assertRender("WITH nm(id) AS NOT MATERIALIZED (SELECT users.id FROM users) SELECT x.id FROM nm AS x",
                select(nm.as("x").field(USERS.ID)).with(nm).from(nm.as("x")));
    }

    @Test
    void dataModifyingCte() {
        Cte moved = cte("moved", deleteFrom(ORDERS).where(ORDERS.ORDERED_ON.lt(LocalDate.of(2020, 1, 1))).returning(ORDERS.ID, ORDERS.TOTAL));
        assertRender("WITH moved(id, total) AS (DELETE FROM orders WHERE orders.ordered_on < ? RETURNING orders.id, orders.total) "
                        + "SELECT count(*) FROM moved",
                selectCount().with(moved).from(moved), LocalDate.of(2020, 1, 1));
    }

    @Test
    void derivedLateralAndFunctionTables() {
        DerivedTable last = select(ORDERS.TOTAL, ORDERS.ORDERED_ON).from(ORDERS).where(ORDERS.USER_ID.eq(USERS.ID))
                .orderBy(ORDERS.ORDERED_ON.desc()).limit(1).asTable("lo");
        assertRender("SELECT users.name, lo.total FROM users LEFT JOIN LATERAL (SELECT orders.total, orders.ordered_on FROM orders "
                        + "WHERE orders.user_id = users.id ORDER BY orders.ordered_on DESC LIMIT 1) AS lo ON TRUE",
                select(USERS.NAME, last.field(ORDERS.TOTAL)).from(USERS).leftJoin(lateral(last)).onTrue());
        FunctionTable<Integer> nums = tableOf(Sql.raw("generate_series(1, {0})", SqlTypes.INT4, param(3)), "n");
        assertRender("SELECT n.value FROM generate_series(1, ?) AS n(value)", select(nums.value()).from(nums), 3);
        assertRender("SELECT m.value FROM LATERAL generate_series(1, ?) AS m(value)",
                select(nums.as("m").lateral().value()).from(nums.as("m").lateral()), 3);
    }

    @Test
    void keysetSeek() {
        Instant t = Instant.parse("2024-01-01T00:00:00Z");
        UUID id = UUID.randomUUID();
        assertRender("SELECT users.id, users.created_at FROM users WHERE (users.created_at, users.id) < (?, ?) "
                        + "ORDER BY users.created_at DESC, users.id DESC LIMIT 50",
                select(USERS.ID, USERS.CREATED_AT).from(USERS).orderBy(USERS.CREATED_AT.desc(), USERS.ID.desc()).seekAfter(t, id).limit(50),
                t, id);
        assertRender("SELECT users.id FROM users WHERE (users.name > ? OR (users.name = ? AND users.id < ?)) ORDER BY users.name ASC, users.id DESC",
                select(USERS.ID).from(USERS).orderBy(USERS.NAME.asc(), USERS.ID.desc()).seekAfter("m", id), "m", "m", id);
        assertRender("SELECT users.id FROM users WHERE users.name > ? ORDER BY users.name ASC",
                select(USERS.ID).from(USERS).orderBy(USERS.NAME.asc()).seekAfter((Cursor) null).seekAfter("m"), "m");
        String token = new Cursor(List.of("m")).encode();
        assertRender("SELECT users.id FROM users WHERE users.name > ? ORDER BY users.name ASC",
                select(USERS.ID).from(USERS).orderBy(USERS.NAME.asc()).seekAfterCursor(token), "m");
        assertRender("SELECT users.id FROM users ORDER BY users.name ASC",
                select(USERS.ID).from(USERS).orderBy(USERS.NAME.asc()).seekAfterCursor(null));
        assertThrows(IllegalStateException.class, () -> select(USERS.ID).from(USERS).orderBy(USERS.NAME.asc()).seekAfter("a", "b").render());
        assertThrows(IllegalArgumentException.class, () -> select(USERS.ID).from(USERS).orderBy(USERS.NAME.asc()).seekAfter(1).render());
    }

    @Test
    void cursorRoundTrip() {
        Instant t = Instant.parse("2024-01-01T00:00:00Z");
        UUID id = UUID.randomUUID();
        Cursor c = new Cursor(List.of(t, id, "aé", 5L, Role.ADMIN));
        String token = c.encode();
        assertFalse(token.contains("="));
        Cursor back = Cursor.decode(token, List.of(USERS.CREATED_AT, USERS.ID, USERS.NAME, USERS.VERSION, USERS.ROLE));
        assertEquals(c, back);
        assertThrows(IllegalArgumentException.class, () -> Cursor.decode("!!", List.of(USERS.ID)));
        assertThrows(IllegalArgumentException.class, () -> Cursor.decode(token, List.of(USERS.ID)));
        assertThrows(IllegalStateException.class, () -> new Cursor(List.of(new Object())).encode());
    }

    @Test
    void dynamicSelectAndMisc() {
        assertRender("SELECT users.id, users.name FROM users", select(List.of(USERS.ID, USERS.NAME)).from(USERS));
        assertRender("SELECT count(*) FROM users", selectCount().from(USERS));
        assertRender("SELECT now()", select(now()));
        assertRender("SELECT COALESCE(users.deleted_at, users.created_at, now()) FROM users",
                select(coalesce(USERS.DELETED_AT, USERS.CREATED_AT, now())).from(USERS));
        assertThrows(IllegalArgumentException.class, () -> select(USERS.ID).where((Condition) null));
        assertTrue(select(USERS.ID).from(USERS).toString().startsWith("SELECT users.id"));
        assertEquals(1, select(USERS.ID).fields().size());
    }
}
