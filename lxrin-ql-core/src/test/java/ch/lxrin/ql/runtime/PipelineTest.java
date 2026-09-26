package ch.lxrin.ql.runtime;

import ch.lxrin.ql.RecordingExecutor;
import ch.lxrin.ql.TestSchema.Role;
import ch.lxrin.ql.TestSchema.UserRow;
import ch.lxrin.ql.dsl.Cursor;
import ch.lxrin.ql.dsl.Page;
import ch.lxrin.ql.dsl.Row2;
import ch.lxrin.ql.error.InvalidStatementException;
import ch.lxrin.ql.error.LxrinQlException;
import ch.lxrin.ql.error.NoRowsException;
import ch.lxrin.ql.error.StatementRejectedException;
import ch.lxrin.ql.error.TooManyRowsException;
import ch.lxrin.ql.render.Bind;
import ch.lxrin.ql.spi.AffectedRow;
import ch.lxrin.ql.spi.ColumnConventions;
import ch.lxrin.ql.spi.ExecutionObserver;
import ch.lxrin.ql.spi.InsertContext;
import ch.lxrin.ql.spi.Origin;
import ch.lxrin.ql.spi.SoftDeletePolicy;
import ch.lxrin.ql.spi.StatementEvent;
import ch.lxrin.ql.spi.StatementListener;
import ch.lxrin.ql.spi.TenantPolicy;
import ch.lxrin.ql.spi.UpdateContext;
import ch.lxrin.ql.spi.WriteResult;
import ch.lxrin.ql.statement.StatementKind;
import ch.lxrin.ql.statement.UpdateStatement;
import ch.lxrin.ql.types.SqlTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ch.lxrin.ql.TestSchema.OrdersTable.ORDERS;
import static ch.lxrin.ql.TestSchema.UsersTable.USERS;
import static ch.lxrin.ql.dsl.Dsl.*;
import static org.junit.jupiter.api.Assertions.*;

class PipelineTest {

    record UserSummary(UUID id, String name, String email) {}

    private final RecordingExecutor db = new RecordingExecutor();
    private final Clock clock = Clock.fixed(Instant.parse("2025-01-01T10:00:00Z"), ZoneOffset.UTC);

    private QueryContext ctx(java.util.function.Consumer<QueryContext.Builder> config) {
        QueryContext.Builder b = QueryContext.builder().executor(db);
        config.accept(b);
        return b.build();
    }

    private List<Object> binds(int statement) {
        List<Object> values = new ArrayList<>();
        for (Bind<?> b : db.statements.get(statement).binds()) values.add(b.value());
        return values;
    }

    @AfterEach
    void clearDefault() {
        QueryContext.setDefault(null);
    }

    @Test
    void fetchMapsTuplesAndConstructorReferences() {
        QueryContext ctx = ctx(b -> {});
        UUID id = UUID.randomUUID();
        db.willReturn(new Object[]{id, "Ada", "ada@x"});
        List<UserSummary> rows = ctx.select(USERS.ID, USERS.NAME, USERS.EMAIL).from(USERS).fetch(UserSummary::new);
        assertEquals(List.of(new UserSummary(id, "Ada", "ada@x")), rows);

        db.willReturn(new Object[]{id, "Ada"});
        Row2<UUID, String> row = ctx.select(USERS.ID, USERS.NAME).from(USERS).fetchOne();
        assertEquals(id, row.value1());
        assertEquals("Ada", row.value2());

        db.willReturn(new Object[]{"Ada"}, new Object[]{"Alan"});
        assertEquals(List.of("Ada", "Alan"), ctx.select(USERS.NAME).from(USERS).fetch());
        db.willReturn(new Object[]{"Ada"});
        assertEquals(List.of(3), ctx.select(USERS.NAME).from(USERS).fetch(String::length));
    }

    @Test
    void selectFromMapsRowRecords() {
        QueryContext ctx = ctx(b -> {});
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        db.willReturn(new Object[]{id, "Ada", "a@x", Role.ADMIN, true, now, null, new String[]{"x"}, "{}", 1L});
        UserRow row = ctx.selectFrom(USERS).fetchOne();
        assertEquals("Ada", row.name());
        assertEquals(Role.ADMIN, row.role());
        assertEquals(1L, row.version());
    }

    @Test
    void fetchOneAndOptionalCheckRowCounts() {
        QueryContext ctx = ctx(b -> {});
        assertThrows(NoRowsException.class, () -> ctx.select(USERS.NAME).from(USERS).fetchOne());
        db.willReturn(new Object[]{"a"}, new Object[]{"b"});
        assertThrows(TooManyRowsException.class, () -> ctx.select(USERS.NAME).from(USERS).fetchOne());
        db.willReturn(new Object[]{"a"}, new Object[]{"b"});
        assertThrows(TooManyRowsException.class, () -> ctx.select(USERS.NAME).from(USERS).fetchOptional());
        assertTrue(ctx.select(USERS.NAME).from(USERS).fetchOptional().isEmpty());
        db.willReturn(new Object[]{"a"});
        assertEquals("a", ctx.select(USERS.NAME).from(USERS).fetchFirst().orElseThrow());
        assertEquals("SELECT users.name FROM users LIMIT 1", db.lastSql());
    }

    @Test
    void countAndExists() {
        QueryContext ctx = ctx(b -> {});
        db.willReturn(new Object[]{42L});
        assertEquals(42L, ctx.select(USERS.ID).from(USERS).where(USERS.ACTIVE).fetchCount());
        assertEquals("SELECT count(*) FROM (SELECT users.id FROM users WHERE users.active) AS q", db.lastSql());
        db.willReturn(new Object[]{true});
        assertTrue(ctx.selectOne().from(USERS).fetchExists());
        assertEquals("SELECT EXISTS (SELECT 1 FROM users)", db.lastSql());
    }

    @Test
    void streamAndPage() {
        QueryContext ctx = ctx(b -> {});
        db.willReturn(new Object[]{"a"}, new Object[]{"b"});
        try (Stream<String> s = ctx.select(USERS.NAME).from(USERS).stream()) {
            assertEquals(List.of("a", "b"), s.collect(Collectors.toList()));
        }
        Instant t1 = Instant.parse("2024-01-02T00:00:00Z");
        UUID id1 = UUID.randomUUID();
        db.willReturn(new Object[]{UUID.randomUUID(), Instant.parse("2024-01-03T00:00:00Z")}, new Object[]{id1, t1});
        Page<Row2<UUID, Instant>> page = ctx.select(USERS.ID, USERS.CREATED_AT).from(USERS)
                .orderBy(USERS.CREATED_AT.desc(), USERS.ID.desc()).limit(2).fetchPage();
        assertEquals(2, page.items().size());
        assertTrue(page.hasNext());
        assertEquals(List.of(t1, id1), page.next().values());
        Cursor decoded = Cursor.decode(page.next().encode(), List.of(USERS.CREATED_AT, USERS.ID));
        assertEquals(page.next(), decoded);

        db.willReturn(new Object[]{UUID.randomUUID(), t1});
        Page<Row2<UUID, Instant>> last = ctx.select(USERS.ID, USERS.CREATED_AT).from(USERS)
                .orderBy(USERS.CREATED_AT.desc(), USERS.ID.desc()).seekAfter(decoded).limit(2).fetchPage();
        assertFalse(last.hasNext());
        assertTrue(db.lastSql().contains("WHERE (users.created_at, users.id) < (?, ?)"));
        assertThrows(IllegalStateException.class, () -> ctx.select(USERS.ID).from(USERS).orderBy(USERS.NAME.asc()).limit(1).fetchPage());
        assertThrows(IllegalStateException.class, () -> ctx.select(USERS.ID).from(USERS).limit(1).fetchPage());
    }

    @Test
    void defaultContextForStaticDsl() {
        assertThrows(IllegalStateException.class, () -> select(USERS.NAME).from(USERS).fetch());
        QueryContext.setDefault(ctx(b -> {}));
        db.willReturn(new Object[]{"a"});
        assertEquals(List.of("a"), select(USERS.NAME).from(USERS).fetch());
        assertTrue(QueryContext.findDefault().isPresent());
    }

    @Test
    void renderingErrorsBecomeInvalidStatementException() {
        QueryContext ctx = ctx(b -> {});
        assertThrows(InvalidStatementException.class, () -> ctx.update(USERS).set(USERS.NAME, "x").execute());
    }

    @Test
    void observersSeeEveryStatement() {
        List<String> events = new ArrayList<>();
        ExecutionObserver observer = new ExecutionObserver() {
            @Override
            public void onStart(StatementEvent e) {
                events.add("start " + e.kind() + " " + e.origin());
            }

            @Override
            public void onSuccess(StatementEvent e, Duration took, long rows) {
                events.add("ok " + rows + " " + e.tables());
            }

            @Override
            public void onError(StatementEvent e, Duration took, LxrinQlException error) {
                events.add("error");
            }
        };
        QueryContext ctx = ctx(b -> b.observer(observer).observer(new LoggingObserver(Duration.ZERO)));
        db.willReturn(new Object[]{"a"});
        ctx.select(USERS.NAME).from(USERS).fetch();
        ctx.withOrigin(Origin.repository("X.y")).deleteFrom(ORDERS).where(ORDERS.ID.eq(1L)).execute();
        assertEquals(List.of("start SELECT DSL", "ok 1 [users]", "start DELETE REPOSITORY:X.y", "ok 1 [orders]"), events);
    }

    @Test
    void softDeletePolicyFiltersReadsAndTurnsDeletesIntoUpdates() {
        QueryContext ctx = ctx(b -> b.policy(new SoftDeletePolicy("deleted_at", clock)));
        ctx.select(USERS.NAME, ORDERS.TOTAL).from(USERS).leftJoin(ORDERS).onKey(ORDERS.FK_USER).fetch();
        assertEquals("SELECT users.name, orders.total FROM users LEFT JOIN orders ON orders.user_id = users.id "
                + "WHERE users.deleted_at IS NULL", db.lastSql());

        ctx.select(USERS.NAME).from(USERS.as("u")).where(USERS.as("u").ACTIVE).fetch();
        assertEquals("SELECT users.name FROM users AS u WHERE (u.active AND u.deleted_at IS NULL)", db.lastSql());

        ctx.select(ORDERS.ID).from(ORDERS).where(ORDERS.USER_ID.in(ctx.select(USERS.ID).from(USERS))).fetch();
        assertEquals("SELECT orders.id FROM orders WHERE orders.user_id IN (SELECT users.id FROM users WHERE users.deleted_at IS NULL)",
                db.lastSql());

        UUID id = UUID.randomUUID();
        ctx.deleteFrom(USERS).where(USERS.ID.eq(id)).execute();
        assertEquals("UPDATE users SET deleted_at = ? WHERE (users.id = ? AND users.deleted_at IS NULL)", db.lastSql());
        assertEquals(List.of(clock.instant(), id), binds(db.statements.size() - 1));

        ctx.bypassing(SoftDeletePolicy.class).deleteFrom(USERS).where(USERS.ID.eq(id)).execute();
        assertEquals("DELETE FROM users WHERE users.id = ?", db.lastSql());
        ctx.bypassing(SoftDeletePolicy.class).select(USERS.NAME).from(USERS).fetch();
        assertEquals("SELECT users.name FROM users", db.lastSql());
        ctx.deleteFrom(ORDERS).where(ORDERS.ID.eq(1L)).execute();
        assertEquals("DELETE FROM orders WHERE orders.id = ?", db.lastSql());
    }

    @Test
    void tenantPolicy() {
        UUID tenant = UUID.randomUUID();
        TenantPolicy<UUID> policy = new TenantPolicy<>("id", SqlTypes.UUID, () -> tenant);
        QueryContext ctx = ctx(b -> b.policy(policy));
        ctx.select(USERS.NAME).from(USERS).fetch();
        assertEquals("SELECT users.name FROM users WHERE users.id = ?", db.lastSql());
        ctx.insertInto(USERS).set(USERS.ID, UUID.randomUUID()).set(USERS.NAME, "a").execute();
        assertEquals(tenant, binds(db.statements.size() - 1).get(0));
        assertThrows(StatementRejectedException.class, () -> ctx.update(USERS).set(USERS.ID, UUID.randomUUID()).allRows().execute());
        assertThrows(StatementRejectedException.class, () -> ctx.truncate(USERS).execute());
        ctx.update(USERS).set(USERS.NAME, "b").allRows().execute();
        assertEquals("UPDATE users SET name = ? WHERE users.id = ?", db.lastSql());
    }

    @Test
    void columnConventionsAndVersion() {
        QueryContext ctx = ctx(b -> b
                .convention(ColumnConventions.createdAt("created_at", clock))
                .convention(ColumnConventions.onInsertAndUpdate("name", String.class, c -> "by-convention"))
                .versionColumn("version"));
        ctx.insertInto(USERS).set(USERS.EMAIL, "a@x").execute();
        assertEquals("INSERT INTO users (email, name, created_at) VALUES (?, ?, ?)", db.lastSql());
        assertEquals(List.of("a@x", "by-convention", clock.instant()), binds(0));

        ctx.insertInto(USERS).set(USERS.NAME, "explicit").execute();
        assertEquals(List.of("explicit", clock.instant()), binds(1));

        ctx.update(USERS).set(USERS.EMAIL, "b@x").allRows().execute();
        assertEquals("UPDATE users SET email = ?, name = ?, version = (users.version + 1)", db.lastSql());

        QueryContext forced = ctx(b -> b.convention(ColumnConventions.onInsert("name", String.class, c -> "forced").forced()));
        forced.insertInto(USERS).set(USERS.NAME, "explicit").execute();
        assertEquals(List.of("forced"), binds(db.statements.size() - 1));

        QueryContext wrongType = ctx(b -> b.convention(ColumnConventions.onInsert("name", Instant.class, c -> Instant.now())));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> wrongType.insertInto(USERS).set(USERS.EMAIL, "x").execute());
        assertTrue(e.getMessage().contains("users.name"));
    }

    @Test
    void userConventions() {
        java.util.concurrent.atomic.AtomicReference<String> user = new java.util.concurrent.atomic.AtomicReference<>("alice");
        QueryContext ctx = ctx(b -> b
                .convention(ColumnConventions.createdBy("name", String.class, user::get))
                .convention(ColumnConventions.updatedBy("email", String.class, user::get)));
        ctx.insertInto(USERS).set(USERS.ID, UUID.randomUUID()).execute();
        assertEquals("INSERT INTO users (id, name, email) VALUES (?, ?, ?)", db.lastSql());
        assertEquals(List.of("alice", "alice"), binds(0).subList(1, 3));

        user.set("bob");
        ctx.update(USERS).set(USERS.ACTIVE, false).allRows().execute();
        assertEquals("UPDATE users SET active = ?, email = ?", db.lastSql(), "created_by is not touched by an update");
        assertEquals(List.of(false, "bob"), binds(1));

        user.set(null);
        ctx.insertInto(USERS).set(USERS.ID, UUID.randomUUID()).execute();
        assertEquals(java.util.Arrays.asList(null, null), binds(2).subList(1, 3), "no user, e.g. a system job");
        assertThrows(IllegalStateException.class, () -> ctx(b -> b.convention(ColumnConventions.createdBy("name", UUID.class,
                UUID::randomUUID))).insertInto(USERS).set(USERS.ID, UUID.randomUUID()).execute());
    }

    @Test
    void listenersSeeStructureAndAffectedRowsIndependentOfCallerReturning() {
        List<String> seen = new ArrayList<>();
        StatementListener listener = new StatementListener() {
            @Override
            public boolean appliesTo(ch.lxrin.ql.schema.Table<?> table) {
                return table.sameTable(USERS);
            }

            @Override
            public void beforeInsert(InsertContext c) {
                c.setValue(USERS.ROLE, Role.USER);
                c.requestReturning(USERS.ID, USERS.NAME);
            }

            @Override
            public void beforeUpdate(UpdateContext c) {
                c.addCondition(USERS.ACTIVE);
                c.requestReturning(c.table().columns());
            }

            @Override
            public void afterInsert(WriteResult r) {
                for (AffectedRow row : r.affectedRows()) seen.add("insert " + row.get(USERS.NAME) + " inserted=" + row.inserted());
                seen.add("origin " + r.dsl().origin());
                seen.add("tx " + r.transaction().attribute(ch.lxrin.ql.runtime.TransactionScope.key("x"), () -> 1));
            }

            @Override
            public void afterUpdate(WriteResult r) {
                seen.add("update " + r.rowCount() + " " + r.originalKind());
            }
        };
        QueryContext ctx = ctx(b -> b.listener(listener));
        UUID id = UUID.randomUUID();
        db.willReturn(new Object[]{Instant.EPOCH, id, "Ada"});
        Instant created = ctx.insertInto(USERS).set(USERS.NAME, "Ada").returning(USERS.CREATED_AT).fetchOne();
        assertEquals(Instant.EPOCH, created);
        assertEquals("INSERT INTO users (name, role) VALUES (?, ?) RETURNING users.created_at, users.id, users.name", db.lastSql());

        db.willReturn(new Object[]{id, "Ada"});
        assertEquals(1, ctx.insertInto(USERS).set(USERS.NAME, "Ada").execute());

        db.willReturn(new Object[]{id, "Ada", null, null, null, null, null, null, null, null});
        ctx.update(USERS).set(USERS.NAME, "B").where(USERS.ID.eq(id)).execute();
        assertTrue(db.lastSql().startsWith("UPDATE users SET name = ? WHERE (users.id = ? AND users.active) RETURNING users.id, users.name"));

        db.willReturn(new Object[]{id, "Ada", true});
        ctx.insertInto(USERS).set(USERS.NAME, "Ada").onConflict(USERS.EMAIL).doUpdateSetExcluded(USERS.NAME).execute();
        assertTrue(db.lastSql().endsWith("RETURNING users.id, users.name, (xmax = 0) AS lxrin_inserted"));

        assertEquals(List.of("insert Ada inserted=true", "origin LISTENER:", "tx 1", "insert Ada inserted=true", "origin LISTENER:", "tx 1",
                "update 1 UPDATE", "insert Ada inserted=true", "origin LISTENER:", "tx 1"),
                seen.stream().map(s -> s.replaceAll("LISTENER:\\S*", "LISTENER:")).collect(Collectors.toList()));

        ctx.deleteFrom(ORDERS).where(ORDERS.ID.eq(1L)).execute();
        assertEquals("DELETE FROM orders WHERE orders.id = ?", db.lastSql());
    }

    @Test
    void listenersCanReject() {
        StatementListener noTruncate = new StatementListener() {
            @Override
            public void beforeTruncate(ch.lxrin.ql.spi.TruncateContext c) {
                c.reject("no");
            }

            @Override
            public void beforeDelete(ch.lxrin.ql.spi.DeleteContext c) {
                c.reject("never");
            }
        };
        QueryContext ctx = ctx(b -> b.listener(noTruncate));
        assertThrows(StatementRejectedException.class, () -> ctx.truncate(USERS).execute());
        assertThrows(StatementRejectedException.class, () -> ctx.deleteFrom(USERS).allRows().execute());
        assertTrue(db.statements.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ctx(b -> b.listener(new StatementListener() {
            @Override
            public void beforeInsert(InsertContext c) {
                c.requestReturning(ORDERS.ID);
            }
        })).insertInto(USERS).set(USERS.NAME, "x").execute());
    }

    @Test
    void softDeleteIsVisibleToListenersAsUpdate() {
        List<StatementKind> kinds = new ArrayList<>();
        QueryContext ctx = ctx(b -> b.policy(new SoftDeletePolicy("deleted_at", clock)).listener(new StatementListener() {
            @Override
            public void afterUpdate(WriteResult r) {
                kinds.add(r.kind());
                kinds.add(r.originalKind());
            }
        }));
        ctx.deleteFrom(USERS).where(USERS.ID.eq(UUID.randomUUID())).execute();
        assertEquals(List.of(StatementKind.UPDATE, StatementKind.DELETE), kinds);
    }

    @Test
    void batchGroupsIdenticalStatements() {
        QueryContext ctx = ctx(b -> {});
        List<UpdateStatement> statements = new ArrayList<>();
        List<Object> keys = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            UUID id = UUID.randomUUID();
            UpdateStatement s = new UpdateStatement(USERS);
            s.set(i == 2 ? USERS.EMAIL : USERS.NAME, param("v" + i), true);
            s.addWhere(USERS.ID.eq(id));
            s.returning().add(USERS.ID);
            statements.add(s);
            keys.add(id);
        }
        db.willReturn(new Object[]{keys.get(0)}, new Object[]{keys.get(1)});
        db.willReturn(new Object[]{keys.get(2)});
        List<QueryContext.DmlResult<UUID>> results = ctx.executeBatch(statements, keys, v -> v[0], v -> (UUID) v[0],
                QueryContext.ExecOptions.dsl());
        assertEquals(3, results.size());
        assertEquals(List.of(keys.get(1)), results.get(1).rows());
        assertEquals(List.of(keys.get(2)), results.get(2).rows());
        assertEquals("UPDATE users SET email = ? WHERE users.id = ?", db.lastSql());
    }

    @Test
    void rawStatementsAndTransactionsWithoutDatabase() {
        QueryContext ctx = ctx(b -> {});
        assertEquals(1, ctx.execute(ch.lxrin.ql.dsl.Sql.statement("VACUUM {0}", param(1))));
        assertEquals("VACUUM ?", db.lastSql());
        List<String> events = new ArrayList<>();
        ctx.transaction(() -> {
            ctx.currentTransaction().orElseThrow().afterCommit(() -> events.add("commit"));
            ctx.transaction(Propagation.REQUIRED, () -> events.add("inner"));
        });
        assertEquals(List.of("inner", "commit"), events);
        assertThrows(RuntimeException.class, () -> ctx.transaction(() -> {
            ctx.currentTransaction().orElseThrow().afterRollback(() -> events.add("rollback"));
            throw new RuntimeException("x");
        }));
        assertEquals("rollback", events.get(2));
        assertThrows(ch.lxrin.ql.error.TransactionException.class, () -> ctx.transaction(Propagation.MANDATORY, () -> 1));
        assertEquals(Map.of(), Map.of());
    }

    @Test
    void builderValidation() {
        assertThrows(IllegalStateException.class, () -> QueryContext.builder().build());
        QueryContext base = ctx(b -> b.versionColumn("version"));
        QueryContext derived = base.derive(b -> b.batchSize(10));
        assertEquals(10, derived.batchSize());
        assertEquals("version", derived.versionColumn().orElseThrow());
        assertEquals(1, base.bypassing(SoftDeletePolicy.class).bypassedPolicies().size());
    }
}
