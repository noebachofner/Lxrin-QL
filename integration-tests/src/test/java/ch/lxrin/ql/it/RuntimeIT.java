package ch.lxrin.ql.it;

import ch.lxrin.ql.TestSchema.OrderRow;
import ch.lxrin.ql.TestSchema.Role;
import ch.lxrin.ql.TestSchema.UserRow;
import ch.lxrin.ql.TestSchema.UsersTable;
import ch.lxrin.ql.dsl.Cte;
import ch.lxrin.ql.dsl.Cursor;
import ch.lxrin.ql.dsl.DerivedTable;
import ch.lxrin.ql.dsl.Field;
import ch.lxrin.ql.dsl.Page;
import ch.lxrin.ql.dsl.Row2;
import ch.lxrin.ql.dsl.Sql;
import ch.lxrin.ql.error.CheckViolationException;
import ch.lxrin.ql.error.ForeignKeyViolationException;
import ch.lxrin.ql.error.LockNotAvailableException;
import ch.lxrin.ql.error.LxrinQlException;
import ch.lxrin.ql.error.NotNullViolationException;
import ch.lxrin.ql.error.QueryTimeoutException;
import ch.lxrin.ql.error.TransactionException;
import ch.lxrin.ql.error.UniqueViolationException;
import ch.lxrin.ql.runtime.Propagation;
import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.spi.ExecutionObserver;
import ch.lxrin.ql.spi.StatementEvent;
import ch.lxrin.ql.statement.UpdateStatement;
import ch.lxrin.ql.types.SqlTypes;
import ch.lxrin.ql.types.UuidV7;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ch.lxrin.ql.TestSchema.OrdersTable.ORDERS;
import static ch.lxrin.ql.TestSchema.UsersTable.USERS;
import static ch.lxrin.ql.dsl.Dsl.*;
import static org.junit.jupiter.api.Assertions.*;

/** Runs the DSL and the runtime against PostgreSQL. */
class RuntimeIT {

    record UserSummary(UUID id, String name, String email) {}

    private final List<String> sqlLog = new ArrayList<>();
    private QueryContext ctx;
    private UUID ada;
    private UUID alan;

    @BeforeEach
    void setUp() {
        Postgres.resetTestSchema();
        ctx = QueryContext.builder()
                .dataSource(Postgres.dataSource())
                .observer(new ExecutionObserver() {
                    @Override
                    public void onSuccess(StatementEvent e, Duration took, long rows) {
                        sqlLog.add(e.sql().sql());
                    }
                })
                .build();
        ada = UuidV7.generate();
        alan = UuidV7.generate();
        ctx.insertInto(USERS).columns(USERS.ID, USERS.NAME, USERS.EMAIL, USERS.ROLE, USERS.TAGS, USERS.SETTINGS)
                .values(ada, "Ada", "ada@example.org", Role.ADMIN, new String[]{"vip", "math"}, "{\"theme\": \"dark\"}")
                .values(alan, "Alan", "alan@gmail.com", Role.USER, new String[]{"math"}, null)
                .execute();
        ctx.insertInto(ORDERS).columns(ORDERS.USER_ID, ORDERS.TOTAL, ORDERS.STATUS, ORDERS.ORDERED_ON)
                .values(ada, new BigDecimal("100.00"), "PAID", LocalDate.of(2024, 1, 5))
                .values(ada, new BigDecimal("50.50"), "PAID", LocalDate.of(2024, 2, 10))
                .values(ada, new BigDecimal("20.00"), "OPEN", LocalDate.of(2024, 2, 11))
                .values(alan, new BigDecimal("75.00"), "PAID", LocalDate.of(2024, 3, 1))
                .execute();
    }

    @Test
    void targetExampleAndTypedResults() {
        List<Row2<UUID, String>> rows = ctx.select(USERS.ID, USERS.NAME).from(USERS)
                .where(USERS.EMAIL.endsWith("@gmail.com")).orderBy(USERS.NAME.asc()).fetch();
        assertEquals(List.of(new Row2<>(alan, "Alan")), rows);
        List<UserSummary> summaries = ctx.select(USERS.ID, USERS.NAME, USERS.EMAIL).from(USERS).orderBy(USERS.NAME.asc())
                .fetch(UserSummary::new);
        assertEquals(new UserSummary(ada, "Ada", "ada@example.org"), summaries.get(0));
    }

    @Test
    void allTypesRoundTrip() {
        UserRow row = ctx.selectFrom(USERS).where(USERS.ID.eq(ada)).fetchOne();
        assertEquals(ada, row.id());
        assertEquals(Role.ADMIN, row.role());
        assertTrue(row.active());
        assertArrayEquals(new String[]{"vip", "math"}, row.tags());
        assertTrue(row.settings().contains("dark"));
        assertEquals(0L, row.version());
        assertNull(row.deletedAt());
        assertTrue(Duration.between(row.createdAt(), Instant.now()).abs().toMinutes() < 5);

        Instant t = Instant.parse("2024-06-01T12:34:56.123456Z");
        ctx.update(USERS).set(USERS.DELETED_AT, t).where(USERS.ID.eq(ada)).execute();
        assertEquals(t, ctx.select(USERS.DELETED_AT).from(USERS).where(USERS.ID.eq(ada)).fetchOne());

        assertEquals(Duration.ofHours(26).plusSeconds(3),
                ctx.select(interval(Duration.ofHours(26).plusSeconds(3))).fetchOne());
        assertEquals(LocalDate.of(2024, 1, 5), ctx.select(min(ORDERS.ORDERED_ON)).from(ORDERS).fetchOne());
        OrderRow order = ctx.selectFrom(ORDERS).orderBy(ORDERS.ID.asc()).fetchFirst().orElseThrow();
        assertEquals(new BigDecimal("100.00"), order.total());
    }

    @Test
    void inBindsOneArray() {
        assertEquals(2, ctx.select(USERS.ID).from(USERS).where(USERS.ROLE.in(List.of(Role.ADMIN, Role.USER))).fetch().size());
        assertEquals(1, ctx.select(USERS.ID).from(USERS).where(USERS.ID.in(List.of(ada))).fetch().size());
        assertEquals(0, ctx.select(USERS.ID).from(USERS).where(USERS.NAME.in(List.of())).fetch().size());
        assertEquals(1, ctx.select(USERS.ID).from(USERS).where(USERS.NAME.notIn(List.of("Ada"))).fetch().size());
        assertTrue(sqlLog.contains("SELECT users.id FROM users WHERE users.id = ANY(?)"));
    }

    @Test
    void joinsAggregatesWindowsAndSubqueries() {
        List<Row2<String, BigDecimal>> revenue = ctx.select(USERS.NAME, sum(ORDERS.TOTAL).filter(ORDERS.STATUS.eq("PAID")))
                .from(USERS).leftJoin(ORDERS).onKey(ORDERS.FK_USER)
                .groupBy(USERS.NAME).orderBy(USERS.NAME.asc()).fetch();
        assertEquals(new BigDecimal("150.50"), revenue.get(0).value2());
        assertEquals(new BigDecimal("75.00"), revenue.get(1).value2());

        List<Long> ranks = ctx.select(rank().over(orderBy(ORDERS.TOTAL.desc()))).from(ORDERS).orderBy(ORDERS.TOTAL.desc()).fetch();
        assertEquals(List.of(1L, 2L, 3L, 4L), ranks);

        List<BigDecimal> running = ctx.select(sum(ORDERS.TOTAL).over(partitionBy(ORDERS.USER_ID).orderBy(ORDERS.ORDERED_ON.asc())
                        .rowsBetween(unboundedPreceding(), currentRow())))
                .from(ORDERS).where(ORDERS.USER_ID.eq(ada)).orderBy(ORDERS.ORDERED_ON.asc()).fetch();
        assertEquals(new BigDecimal("170.50"), running.get(2));

        List<String> buyers = ctx.select(USERS.NAME).from(USERS)
                .where(exists(ctx.selectOne().from(ORDERS).where(ORDERS.USER_ID.eq(USERS.ID), ORDERS.STATUS.eq("OPEN")))).fetch();
        assertEquals(List.of("Ada"), buyers);

        Field<Long> orderCount = ctx.selectCount().from(ORDERS).where(ORDERS.USER_ID.eq(USERS.ID)).asField().as("orders");
        assertEquals(List.of(3L, 1L), ctx.select(orderCount).from(USERS).orderBy(USERS.NAME.asc()).fetch());

        assertEquals(java.util.Set.of("ada@example.org", "Ada", "alan@gmail.com", "Alan"),
                java.util.Set.copyOf(ctx.select(USERS.EMAIL).from(USERS).union(ctx.select(USERS.NAME).from(USERS))
                        .orderBy(USERS.EMAIL.asc()).fetch()));
    }

    @Test
    void ctesAndLateral() {
        Field<BigDecimal> total = sum(ORDERS.TOTAL).as("total");
        Cte paid = cte("paid", ctx.select(ORDERS.USER_ID, total).from(ORDERS).where(ORDERS.STATUS.eq("PAID")).groupBy(ORDERS.USER_ID));
        List<String> big = ctx.select(USERS.NAME).with(paid).from(USERS).join(paid).on(paid.field(ORDERS.USER_ID).eq(USERS.ID))
                .where(paid.field(total).gt(new BigDecimal("100"))).fetch();
        assertEquals(List.of("Ada"), big);

        Cte t = recursiveCte("t");
        ch.lxrin.ql.schema.NumberColumn<Integer> n = t.declareNumber("n", SqlTypes.INT4);
        t.as(ctx.select(inline(1)).unionAll(ctx.select(n.plus(1)).from(t).where(n.lt(5))));
        assertEquals(List.of(1, 2, 3, 4, 5), ctx.select(n).withRecursive(t).from(t).orderBy(n.asc()).fetch());

        DerivedTable last = ctx.select(ORDERS.TOTAL).from(ORDERS).where(ORDERS.USER_ID.eq(USERS.ID))
                .orderBy(ORDERS.ORDERED_ON.desc()).limit(1).asTable("lo");
        List<Row2<String, BigDecimal>> latest = ctx.select(USERS.NAME, last.field(ORDERS.TOTAL)).from(USERS)
                .leftJoin(lateral(last)).onTrue().orderBy(USERS.NAME.asc()).fetch();
        assertEquals(new BigDecimal("20.00"), latest.get(0).value2());

        Cte moved = cte("moved", ctx.deleteFrom(ORDERS).where(ORDERS.STATUS.eq("OPEN")).returning(ORDERS.ID));
        assertEquals(1L, ctx.selectCount().with(moved).from(moved).fetchOne());
        assertEquals(3L, ctx.selectCount().from(ORDERS).fetchOne());
    }

    @Test
    void keysetPaginationWalksAllRows() {
        List<Long> seen = new ArrayList<>();
        String token = null;
        do {
            Page<Row2<Long, LocalDate>> page = ctx.select(ORDERS.ID, ORDERS.ORDERED_ON).from(ORDERS)
                    .orderBy(ORDERS.ORDERED_ON.desc(), ORDERS.ID.desc()).seekAfterCursor(token).limit(3).fetchPage();
            page.items().forEach(r -> seen.add(r.value1()));
            token = page.nextCursor().map(Cursor::encode).orElse(null);
        } while (token != null);
        assertEquals(4, seen.size());
        assertEquals(4, seen.stream().distinct().count());
        assertEquals(4L, ctx.select(ORDERS.ID).from(ORDERS).fetchCount());
        assertTrue(ctx.selectOne().from(ORDERS).where(ORDERS.STATUS.eq("OPEN")).fetchExists());
    }

    @Test
    void streamsInsideATransaction() {
        List<String> names = ctx.transaction(() -> {
            try (Stream<String> s = ctx.select(USERS.NAME).from(USERS).orderBy(USERS.NAME.asc()).stream()) {
                return s.collect(Collectors.toList());
            }
        });
        assertEquals(List.of("Ada", "Alan"), names);
    }

    @Test
    void dmlWithReturningAndUpserts() {
        UUID grace = UuidV7.generate();
        Instant created = ctx.insertInto(USERS).set(USERS.ID, grace).set(USERS.NAME, "Grace").set(USERS.EMAIL, "grace@example.org")
                .returning(USERS.CREATED_AT).fetchOne();
        assertNotNull(created);
        Long id = ctx.insertInto(ORDERS).set(ORDERS.USER_ID, grace).set(ORDERS.TOTAL, BigDecimal.ONE).set(ORDERS.STATUS, "OPEN")
                .set(ORDERS.ORDERED_ON, LocalDate.now()).returning(ORDERS.ID).fetchOne();
        assertEquals(5L, id);

        Optional<UUID> none = ctx.insertInto(USERS).set(USERS.ID, UuidV7.generate()).set(USERS.NAME, "X").set(USERS.EMAIL, "ada@example.org")
                .onConflict(USERS.EMAIL).doNothing().returning(USERS.ID).fetchOptional();
        assertTrue(none.isEmpty());

        ctx.insertInto(USERS).set(USERS.ID, UuidV7.generate()).set(USERS.NAME, "Ada Lovelace").set(USERS.EMAIL, "ada@example.org")
                .onConflict(USERS.EMAIL).doUpdateSetExcluded(USERS.NAME).doUpdateSet(USERS.VERSION, USERS.VERSION.plus(1L)).execute();
        UserRow updated = ctx.selectFrom(USERS).where(USERS.ID.eq(ada)).fetchOne();
        assertEquals("Ada Lovelace", updated.name());
        assertEquals(1L, updated.version());

        assertEquals(3, ctx.update(ORDERS).set(ORDERS.STATUS, "ARCHIVED").where(ORDERS.USER_ID.eq(ada)).execute());
        List<Long> deleted = ctx.deleteFrom(ORDERS).where(ORDERS.STATUS.eq("ARCHIVED")).returning(ORDERS.ID).fetch();
        assertEquals(3, deleted.size());
        List<UserRow> all = ctx.update(USERS).set(USERS.ACTIVE, false).allRows().returningAll().fetch();
        assertEquals(3, all.size());
        assertFalse(all.get(0).active());

        ctx.truncate(ORDERS).restartIdentity().execute();
        assertEquals(0L, ctx.selectCount().from(ORDERS).fetchOne());
        assertEquals(1, ctx.execute(Sql.statement("SELECT 1")));
    }

    @Test
    void transactionsCommitRollBackAndNest() {
        UUID id1 = UuidV7.generate();
        List<String> callbacks = new ArrayList<>();
        ctx.transaction(() -> {
            insertUser(id1, "t1");
            ctx.currentTransaction().orElseThrow().afterCommit(() -> callbacks.add("commit"));
        });
        assertEquals(List.of("commit"), callbacks);
        assertTrue(userExists(id1));

        UUID id2 = UuidV7.generate();
        assertThrows(IllegalStateException.class, () -> ctx.transaction(() -> {
            insertUser(id2, "t2");
            ctx.currentTransaction().orElseThrow().afterRollback(() -> callbacks.add("rollback"));
            throw new IllegalStateException("boom");
        }));
        assertFalse(userExists(id2));
        assertEquals("rollback", callbacks.get(1));

        UUID outer = UuidV7.generate();
        UUID nested = UuidV7.generate();
        ctx.transaction(() -> {
            insertUser(outer, "outer");
            assertThrows(IllegalStateException.class, () -> ctx.transaction(Propagation.NESTED, () -> {
                insertUser(nested, "nested");
                throw new IllegalStateException("undo nested only");
            }));
        });
        assertTrue(userExists(outer));
        assertFalse(userExists(nested));

        UUID independent = UuidV7.generate();
        assertThrows(IllegalStateException.class, () -> ctx.transaction(() -> {
            ctx.transaction(Propagation.REQUIRES_NEW, () -> insertUser(independent, "independent"));
            throw new IllegalStateException("outer fails");
        }));
        assertTrue(userExists(independent));

        UUID partial = UuidV7.generate();
        assertThrows(TransactionException.class, () -> ctx.transaction(() -> {
            insertUser(partial, "partial");
            try {
                ctx.transaction(() -> {
                    throw new IllegalStateException("inner fails but is caught");
                });
            } catch (IllegalStateException ignored) {
                // the outer transaction is rollback-only now
            }
        }));
        assertFalse(userExists(partial));
    }

    @Test
    void errorsAreMappedToSpecificExceptions() {
        UniqueViolationException unique = assertThrows(UniqueViolationException.class,
                () -> insertUser(UuidV7.generate(), "Ada", "ada@example.org"));
        assertSame(USERS.UK_EMAIL, unique.constraint().orElseThrow());
        assertEquals(List.of(USERS.EMAIL), unique.columns());
        assertEquals("23505", unique.getSqlState());
        assertTrue(unique.getMessage().contains("INSERT INTO users"));

        ForeignKeyViolationException fk = assertThrows(ForeignKeyViolationException.class, () -> ctx.insertInto(ORDERS)
                .set(ORDERS.USER_ID, UUID.randomUUID()).set(ORDERS.TOTAL, BigDecimal.ONE).set(ORDERS.STATUS, "X")
                .set(ORDERS.ORDERED_ON, LocalDate.now()).execute());
        assertTrue(fk.isViolated(ORDERS.FK_USER));

        assertThrows(NotNullViolationException.class, () -> ctx.insertInto(USERS).set(USERS.ID, UuidV7.generate())
                .set(USERS.EMAIL, "n@x").execute());
        CheckViolationException check = assertThrows(CheckViolationException.class,
                () -> ctx.update(ORDERS).set(ORDERS.TOTAL, new BigDecimal("-1")).allRows().execute());
        assertEquals("orders_total_check", check.constraintName().orElseThrow());

        ctx.transaction(() -> {
            ctx.select(USERS.ID).from(USERS).where(USERS.ID.eq(ada)).forUpdate().fetch();
            assertThrows(LockNotAvailableException.class, () -> ctx.transaction(Propagation.REQUIRES_NEW,
                    () -> ctx.select(USERS.ID).from(USERS).where(USERS.ID.eq(ada)).forUpdate().nowait().fetch()));
        });

        assertThrows(QueryTimeoutException.class, () -> ctx.transaction(() -> {
            ctx.execute(Sql.statement("SET LOCAL statement_timeout = 50"));
            return ctx.select(Sql.raw("pg_sleep(1)", SqlTypes.TEXT)).fetch();
        }));
        LxrinQlException noCodec = assertThrows(LxrinQlException.class, () -> ctx.select(USERS.SETTINGS.cast(SqlTypes.jsonb(Object.class)))
                .from(USERS).fetch());
        assertTrue(noCodec.getMessage().contains("JsonCodec"));
    }

    @Test
    void jdbcBatchUpdatesReturnRows() {
        List<UpdateStatement> statements = new ArrayList<>();
        List<Object> keys = List.of(ada, alan);
        for (Object key : keys) {
            UpdateStatement s = new UpdateStatement(USERS);
            s.set(USERS.NAME, param("batched"), true);
            s.addWhere(USERS.ID.eq((UUID) key));
            s.returning().add(USERS.ID);
            s.returning().add(USERS.NAME);
            statements.add(s);
        }
        List<QueryContext.DmlResult<Row2<UUID, String>>> results = ctx.executeBatch(statements, keys, v -> v[0],
                v -> new Row2<>((UUID) v[0], (String) v[1]), QueryContext.ExecOptions.dsl());
        assertEquals(2, results.size());
        assertEquals(1, results.get(0).count());
        assertEquals(List.of(new Row2<>(ada, "batched")), results.get(0).rows());
        assertEquals(List.of(new Row2<>(alan, "batched")), results.get(1).rows());
    }

    @Test
    void aliasesAndSelfJoins() {
        UsersTable other = USERS.as("other");
        List<Row2<String, String>> pairs = ctx.select(USERS.NAME, other.NAME).from(USERS).join(other)
                .on(other.ID.ne(USERS.ID)).orderBy(USERS.NAME.asc()).fetch();
        assertEquals(List.of(new Row2<>("Ada", "Alan"), new Row2<>("Alan", "Ada")), pairs);
    }

    @Test
    void temporalOperationsRun() {
        Instant later = ctx.select(USERS.CREATED_AT.plus(Duration.ofDays(1))).from(USERS).where(USERS.ID.eq(ada)).fetchOne();
        Instant created = ctx.select(USERS.CREATED_AT).from(USERS).where(USERS.ID.eq(ada)).fetchOne();
        assertEquals(created.plus(1, ChronoUnit.DAYS), later);
        assertEquals(LocalDate.of(2024, 1, 1), ctx.select(ORDERS.ORDERED_ON.truncate(ch.lxrin.ql.dsl.DatePart.MONTH))
                .from(ORDERS).orderBy(ORDERS.ORDERED_ON.asc()).fetchFirst().orElseThrow());
    }

    private void insertUser(UUID id, String name) {
        insertUser(id, name, name + "@example.org");
    }

    private void insertUser(UUID id, String name, String email) {
        ctx.insertInto(USERS).set(USERS.ID, id).set(USERS.NAME, name).set(USERS.EMAIL, email).execute();
    }

    private boolean userExists(UUID id) {
        return ctx.selectOne().from(USERS).where(USERS.ID.eq(id)).fetchExists();
    }
}
