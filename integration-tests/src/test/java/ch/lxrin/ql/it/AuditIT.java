package ch.lxrin.ql.it;

import ch.lxrin.ql.audit.AuditListener;
import ch.lxrin.ql.audit.AuditSettings;
import ch.lxrin.ql.audit.AuditUser;
import ch.lxrin.ql.dsl.Row2;
import ch.lxrin.ql.error.StatementRejectedException;
import ch.lxrin.ql.it.db.Asset;
import ch.lxrin.ql.it.db.AssetRepository;
import ch.lxrin.ql.it.db.RevisionRow;
import ch.lxrin.ql.it.db.User;
import ch.lxrin.ql.it.db.UserAudRow;
import ch.lxrin.ql.it.db.UserRepository;
import ch.lxrin.ql.runtime.Propagation;
import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.spi.ColumnConventions;
import ch.lxrin.ql.spi.SoftDeletePolicy;
import ch.lxrin.ql.spi.TenantPolicy;
import ch.lxrin.ql.types.SqlTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static ch.lxrin.ql.QL.*;
import static ch.lxrin.ql.it.db.Tables.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The ISMS requirements on top of LxrinQL: audit history with lxrin-ql-audit (one revision per transaction, Envers
 * layout), tenant isolation, soft delete and "who changed it" columns filled from the current user.
 */
class AuditIT {

    private final Clock clock = Clock.fixed(Instant.parse("2025-06-01T12:00:00Z"), ZoneOffset.UTC);
    private final AtomicReference<String> currentUser = new AtomicReference<>("alice@keycloak");
    private final AtomicReference<UUID> currentUserId = new AtomicReference<>(UUID.randomUUID());
    private final AtomicReference<UUID> currentOrg = new AtomicReference<>(UUID.randomUUID());
    private QueryContext ctx;
    private QueryContext admin;
    private UserRepository users;
    private AssetRepository assets;

    private QueryContext context(UnaryOperator<AuditSettings.Builder> audit) {
        AuditSettings settings = audit.apply(AuditSettings.builder()
                .user(AuditUser.of(SqlTypes.UUID, currentUserId::get))
                .clock(clock)).build();
        return QueryContext.builder()
                .dataSource(Postgres.dataSource())
                .listener(new AuditListener(settings))
                .policy(new TenantPolicy<>("organization_id", SqlTypes.UUID, currentOrg::get))
                .policy(new SoftDeletePolicy("deleted_at", clock))
                .convention(ColumnConventions.createdBy("created_by", String.class, currentUser::get))
                .convention(ColumnConventions.updatedAt("updated_at", clock))
                .convention(ColumnConventions.updatedBy("updated_by", String.class, currentUser::get))
                .versionColumn("version")
                .build();
    }

    private void use(QueryContext context) {
        ctx = context;
        admin = ctx.bypassing(TenantPolicy.class, SoftDeletePolicy.class);
        users = new UserRepository(ctx);
        assets = new AssetRepository(ctx);
    }

    @BeforeEach
    void setUp() {
        Postgres.migrateItSchema();
        use(context(a -> a.tables("app_user", "asset").excludeColumns("app_user", "last_seen_at").storeDataAtDelete(true)));
    }

    private User user(String name) {
        User u = new User();
        u.setId(users.createKey());
        u.setName(name);
        u.setEmail(name.toLowerCase() + "@isms.example");
        return u;
    }

    private Asset asset(String name) {
        Asset a = new Asset();
        a.setName(name);
        a.setValue(new BigDecimal("1200"));
        return a;
    }

    private List<UserAudRow> history() {
        return admin.selectFrom(USER_AUD).orderBy(USER_AUD.REV.asc(), USER_AUD.NAME.asc()).fetch();
    }

    private List<RevisionRow> revisions() {
        return admin.selectFrom(REVISION).orderBy(REVISION.ID.asc()).fetch();
    }

    private List<String> revtypes() {
        return history().stream().map(r -> r.name() + ":" + r.revtype()).collect(Collectors.toList());
    }

    @Test
    void entitySaveWritesARevisionAndTheFullRow() {
        User alice = users.save(user("Alice"));
        assertEquals(currentOrg.get(), alice.getOrganizationId(), "tenant set by the policy");
        assertEquals("alice@keycloak", alice.getCreatedBy(), "who-columns from the current user");
        List<RevisionRow> revisions = revisions();
        assertEquals(1, revisions.size());
        assertEquals(currentUserId.get(), revisions.get(0).userId());
        assertEquals(clock.instant(), revisions.get(0).revisedAt());
        UserAudRow audit = history().get(0);
        assertEquals((short) 0, audit.revtype());
        assertEquals(revisions.get(0).id(), audit.rev());
        assertEquals(alice.getId().value(), audit.id());
        assertEquals(alice.getEmail(), audit.email());
        assertEquals(alice.getVersion(), audit.version());
        assertEquals(alice.getCreatedBy(), audit.createdBy());
    }

    @Test
    void saveAllIsOneRevision() {
        User a = users.save(user("A"));
        a.setName("A2");
        currentUserId.set(UUID.randomUUID());
        users.saveAll(List.of(a, user("B"), user("C")));
        List<RevisionRow> revisions = revisions();
        assertEquals(2, revisions.size());
        assertEquals(currentUserId.get(), revisions.get(1).userId());
        assertEquals(List.of("A:0", "A2:1", "B:0", "C:0"), revtypes());
        assertEquals(3, history().stream().filter(h -> h.rev().equals(revisions.get(1).id())).count());
    }

    @Test
    void dslUpdatesDeletesSoftDeletesAndUpsertsAreAudited() {
        users.saveAll(List.of(user("A"), user("B")));
        ctx.update(USERS).set(USERS.ACTIVE, false).allRows().execute();
        users.delete(users.findByEmail("a@isms.example").orElseThrow());
        ctx.insertInto(USERS).set(USERS.ID, users.createKey()).set(USERS.NAME, "B again").set(USERS.EMAIL, "b@isms.example")
                .onConflict(USERS.EMAIL).doUpdateSetExcluded(USERS.NAME).execute();
        ctx.insertInto(USERS).set(USERS.ID, users.createKey()).set(USERS.NAME, "D").set(USERS.EMAIL, "d@isms.example")
                .onConflict(USERS.EMAIL).doUpdateSetExcluded(USERS.NAME).execute();
        admin.deleteFrom(USERS).where(USERS.NAME.eq("D")).execute();
        assertEquals(List.of("A:0", "B:0", "A:1", "B:1", "A:2", "B again:1", "D:0", "D:2"), revtypes());
        UserAudRow softDelete = history().get(4);
        assertEquals(clock.instant(), softDelete.deletedAt(), "storeDataAtDelete keeps the row of a (soft) delete");
    }

    @Test
    void theCreateStyleIsAudited() {
        ctx.createInsert(USERS, (c, b) -> c.set(USERS.ID, users.createKey()).set(USERS.NAME, b.setString("Grace"))
                .set(USERS.EMAIL, b.setString("grace@isms.example"))).execute();
        ctx.createUpdate(USERS, (c, b) -> c.set(USERS.NAME, b.setString("Grace H.")).where(eq(USERS.EMAIL, "grace@isms.example")))
                .execute();
        ctx.createUpsert(USERS, (c, b) -> c.set(USERS.ID, users.findByEmail("grace@isms.example").orElseThrow().getId())
                .set(USERS.NAME, b.setString("Grace Hopper")).set(USERS.EMAIL, "grace@isms.example")).execute();
        ctx.createDelete(USERS, (c, b) -> c.where(eq(USERS.EMAIL, "grace@isms.example"))).execute();
        assertEquals(List.of("Grace:0", "Grace H.:1", "Grace Hopper:1", "Grace Hopper:2"), revtypes());
        assertEquals(4, revisions().size(), "one revision per statement outside a transaction");
    }

    @Test
    void repositoryDeletesAreAudited() {
        users.saveAll(List.of(user("A"), user("B"), user("C")));
        User c = users.findByEmail("c@isms.example").orElseThrow();
        assertEquals(2, users.deleteAll(USERS.NAME.in("A", "B")));
        users.deleteById(c.getId());
        assertEquals(List.of("A:0", "B:0", "C:0", "A:2", "B:2", "C:2"), revtypes());
    }

    @Test
    void severalStatementsInOneTransactionShareTheRevision() {
        ctx.transaction(() -> {
            users.save(user("A"));
            assets.save(asset("Laptop"));
            ctx.update(USERS).set(USERS.ACTIVE, false).allRows().execute();
        });
        assertEquals(1, revisions().size());
        assertEquals(1L, admin.selectCount().from(ASSET_AUD).fetchOne());
        assertEquals(1, history().size(), "one audit row per row and revision");
        assertEquals((short) 0, history().get(0).revtype(), "added in this revision");
        assertFalse(history().get(0).active(), "with the latest state");
    }

    @Test
    void aRollbackLeavesNoHistory() {
        assertThrows(IllegalStateException.class, () -> ctx.transaction(() -> {
            users.save(user("A"));
            throw new IllegalStateException("cancel");
        }));
        assertEquals(0, revisions().size());
        assertTrue(history().isEmpty());
        assertThrows(StatementRejectedException.class, () -> admin.truncate(USERS).cascade().execute());
    }

    @Test
    void aRolledBackSavepointDiscardsItsRevision() {
        ctx.transaction(() -> {
            assertThrows(IllegalStateException.class, () -> ctx.transaction(Propagation.NESTED, () -> {
                users.save(user("A"));
                throw new IllegalStateException("undo the savepoint");
            }));
            users.save(user("B"));
        });
        assertEquals(1, revisions().size());
        assertEquals(List.of("B:0"), revtypes());
        assertEquals(revisions().get(0).id(), history().get(0).rev());
    }

    @Test
    void excludedColumnsAreNotCopied() {
        User a = user("A");
        a.setLastSeenAt(clock.instant());
        users.save(a);
        a.setLastSeenAt(clock.instant().plusSeconds(60));
        users.save(a);
        assertEquals(List.of("A:0", "A:1"), revtypes(), "app_user_aud has no last_seen_at column");
    }

    @Test
    void deleteRowsKeepOnlyTheKeyByDefault() {
        use(context(a -> a.tables("app_user").excludeColumns("app_user", "last_seen_at")));
        User a = users.save(user("A"));
        admin.deleteFrom(USERS).where(USERS.ID.eq(a.getId())).execute();
        UserAudRow deleted = history().get(1);
        assertEquals((short) 2, deleted.revtype());
        assertEquals(a.getId().value(), deleted.id());
        assertNull(deleted.name());
        assertNull(deleted.email());
    }

    @Test
    void tablesCanBeSelectedByPattern() {
        use(context(a -> a.tablePatterns("as+et")));
        users.save(user("A"));
        assets.save(asset("Laptop"));
        assertTrue(history().isEmpty(), "app_user is not audited");
        assertEquals(1L, admin.selectCount().from(ASSET_AUD).fetchOne());
    }

    @Test
    void tenantsAreIsolated() {
        users.save(user("Mine"));
        UUID mine = currentOrg.get();
        currentOrg.set(UUID.randomUUID());
        users.save(user("Theirs"));
        assertEquals(List.of("Theirs"), users.findAll().stream().map(User::getName).collect(Collectors.toList()));
        assertEquals(0, ctx.update(USERS).set(USERS.NAME, "hijack").where(USERS.NAME.eq("Mine")).execute());
        currentOrg.set(mine);
        assertEquals(List.of("Mine"), users.findAll().stream().map(User::getName).collect(Collectors.toList()));
        List<Row2<String, UUID>> all = admin.select(USERS.NAME, USERS.ORGANIZATION_ID).from(USERS).orderBy(USERS.NAME.asc()).fetch();
        assertEquals(2, all.size());
        assertNotEquals(all.get(0).value2(), all.get(1).value2());
    }
}
