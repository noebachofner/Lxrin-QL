package ch.lxrin.ql.audit;

import ch.lxrin.ql.RecordingExecutor;
import ch.lxrin.ql.TestSchema.Role;
import ch.lxrin.ql.error.StatementRejectedException;
import ch.lxrin.ql.render.Bind;
import ch.lxrin.ql.render.RenderedSql;
import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.spi.ColumnConventions;
import ch.lxrin.ql.types.SqlTypes;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static ch.lxrin.ql.TestSchema.OrdersTable.ORDERS;
import static ch.lxrin.ql.TestSchema.UsersTable.USERS;
import static org.junit.jupiter.api.Assertions.*;

/** The SQL the audit listener writes, without a database. */
class AuditListenerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final RecordingExecutor db = new RecordingExecutor();
    private final UUID user = UUID.randomUUID();

    private QueryContext ctx(AuditSettings.Builder settings) {
        return QueryContext.builder().executor(db)
                .listener(new AuditListener(settings.clock(Clock.fixed(NOW, ZoneOffset.UTC)).user(AuditUser.of(SqlTypes.UUID, () -> user)).build()))
                .convention(ColumnConventions.onInsertAndUpdate("name", String.class, c -> "by convention"))
                .versionColumn("version")
                .build();
    }

    private Object[] userRow(UUID id) {
        return new Object[] {id, "Ada", "ada@example.org", Role.USER, true, NOW, null, new String[0], null, 0L};
    }

    private static List<Object> binds(RenderedSql sql) {
        List<Object> values = new ArrayList<>();
        for (Bind<?> b : sql.binds()) values.add(b.value());
        return values;
    }

    @Test
    void anInsertWritesARevisionAndTheFullRow() {
        QueryContext ctx = ctx(AuditSettings.builder().tables("users").excludeColumns("users", "tags", "settings"));
        UUID id = UUID.randomUUID();
        db.willReturn(userRow(id)).willReturn(new Object[] {41L});
        ctx.insertInto(USERS).set(USERS.ID, id).set(USERS.EMAIL, "ada@example.org").execute();
        assertEquals(3, db.statements.size());
        assertTrue(db.statements.get(0).sql().startsWith("INSERT INTO users (id, email, name) VALUES (?, ?, ?) RETURNING"),
                db.statements.get(0).sql());
        assertEquals("INSERT INTO revision (revised_at, user_id) VALUES (?, ?) RETURNING revision.id", db.statements.get(1).sql());
        assertEquals(List.of(NOW, user), binds(db.statements.get(1)));
        assertEquals("INSERT INTO users_aud (rev, revtype, id, name, email, role, active, created_at, deleted_at, version)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (rev, id) DO UPDATE SET name = EXCLUDED.name,"
                + " email = EXCLUDED.email, role = EXCLUDED.role, active = EXCLUDED.active, created_at = EXCLUDED.created_at,"
                + " deleted_at = EXCLUDED.deleted_at, version = EXCLUDED.version,"
                + " revtype = CASE WHEN EXCLUDED.revtype = ? THEN ? ELSE users_aud.revtype END", db.statements.get(2).sql());
        List<Object> binds = binds(db.statements.get(2));
        assertEquals(List.of(41L, (short) 0, id, "Ada", "ada@example.org"), binds.subList(0, 5),
                "stored as returned: no convention, no version increment");
    }

    @Test
    void oneRevisionPerTransactionAndDeletesKeepOnlyTheKey() {
        QueryContext ctx = ctx(AuditSettings.builder().tables("public.users"));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        db.willReturn(userRow(a), userRow(b)).willReturn(new Object[] {7L});
        db.willReturn(userRow(a));
        ctx.transaction(() -> {
            ctx.update(USERS).set(USERS.ACTIVE, false).allRows().execute();
            ctx.deleteFrom(USERS).where(USERS.ID.eq(a)).execute();
        });
        long revisions = db.statements.stream().filter(s -> s.sql().startsWith("INSERT INTO revision")).count();
        assertEquals(1, revisions);
        RenderedSql delete = db.statements.get(db.statements.size() - 1);
        assertTrue(delete.sql().startsWith("INSERT INTO users_aud (rev, revtype, id) VALUES (?, ?, ?)"), delete.sql());
        assertEquals(List.of(7L, (short) 2, a), binds(delete).subList(0, 3));
        assertEquals((short) 1, binds(db.statements.get(3)).get(1), "the update is MOD");
    }

    @Test
    void storeDataAtDeleteAndUpserts() {
        QueryContext ctx = ctx(AuditSettings.builder().tablePatterns("us.*").storeDataAtDelete(true));
        UUID a = UUID.randomUUID();
        db.willReturn(userRow(a)).willReturn(new Object[] {3L});
        ctx.deleteFrom(USERS).where(USERS.ID.eq(a)).execute();
        RenderedSql delete = db.statements.get(2);
        assertTrue(delete.sql().contains("(rev, revtype, id, name, email"), delete.sql());
        assertEquals((short) 2, binds(delete).get(1));
    }

    @Test
    void selectionTruncateAndMissingKeys() {
        AuditSettings settings = AuditSettings.builder().tables("users").tablePatterns("ord.*").build();
        assertTrue(settings.audits(USERS));
        assertTrue(settings.audits(ORDERS));
        assertFalse(settings.audits(new ch.lxrin.ql.schema.AdHocTable(null, "users_aud", null)), "audit tables are never audited");
        assertFalse(settings.audits(new ch.lxrin.ql.schema.AdHocTable(null, "revision", null)));
        assertFalse(AuditSettings.builder().build().audits(USERS));
        assertThrows(IllegalArgumentException.class, () -> AuditSettings.builder().tables(" "));
        assertThrows(java.util.regex.PatternSyntaxException.class, () -> AuditSettings.builder().tablePatterns("("));

        QueryContext ctx = ctx(AuditSettings.builder().tables("users"));
        assertThrows(StatementRejectedException.class, () -> ctx.truncate(USERS).execute());
        assertEquals(RevisionType.DEL, RevisionType.of(2));
        assertThrows(IllegalArgumentException.class, () -> RevisionType.of(3));
    }

    @Test
    void aRolledBackRevisionIsNotReused() {
        QueryContext ctx = ctx(AuditSettings.builder().tables("users"));
        UUID a = UUID.randomUUID();
        db.willReturn(userRow(a)).willReturn(new Object[] {1L});
        assertThrows(IllegalStateException.class, () -> ctx.transaction(() -> {
            ctx.insertInto(USERS).set(USERS.ID, a).execute();
            throw new IllegalStateException("rollback");
        }));
        db.willReturn(userRow(a)).willReturn(new Object[] {2L});
        ctx.insertInto(USERS).set(USERS.ID, a).execute();
        assertEquals(2L, binds(db.statements.get(db.statements.size() - 1)).get(0));
    }
}
