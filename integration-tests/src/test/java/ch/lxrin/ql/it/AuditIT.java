package ch.lxrin.ql.it;

import ch.lxrin.ql.dsl.Row2;
import ch.lxrin.ql.error.StatementRejectedException;
import ch.lxrin.ql.it.db.Asset;
import ch.lxrin.ql.it.db.AssetRepository;
import ch.lxrin.ql.it.db.RevisionRow;
import ch.lxrin.ql.it.db.User;
import ch.lxrin.ql.it.db.UserAudRow;
import ch.lxrin.ql.it.db.UserRepository;
import ch.lxrin.ql.it.isms.IsmsAuditListener;
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
import java.util.stream.Collectors;

import static ch.lxrin.ql.it.db.Tables.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The ISMS requirements, built on top of LxrinQL through extension points: audit history with one revision
 * per transaction, tenant isolation, soft delete and "who changed it" columns filled from the current user.
 */
class AuditIT {

    private final Clock clock = Clock.fixed(Instant.parse("2025-06-01T12:00:00Z"), ZoneOffset.UTC);
    private final AtomicReference<String> currentUser = new AtomicReference<>("alice@keycloak");
    private final AtomicReference<UUID> currentOrg = new AtomicReference<>(UUID.randomUUID());
    private QueryContext ctx;
    private QueryContext admin;
    private UserRepository users;
    private AssetRepository assets;

    @BeforeEach
    void setUp() {
        Postgres.migrateItSchema();
        ctx = QueryContext.builder()
                .dataSource(Postgres.dataSource())
                .listener(new IsmsAuditListener(currentUser::get, clock))
                .policy(new TenantPolicy<>("organization_id", SqlTypes.UUID, currentOrg::get))
                .policy(new SoftDeletePolicy("deleted_at", clock))
                .convention(ColumnConventions.onInsert("created_by", String.class, c -> currentUser.get()))
                .convention(ColumnConventions.updatedAt("updated_at", clock))
                .convention(ColumnConventions.onInsertAndUpdate("updated_by", String.class, c -> currentUser.get()))
                .versionColumn("version")
                .build();
        admin = ctx.bypassing(TenantPolicy.class, SoftDeletePolicy.class);
        users = new UserRepository(ctx);
        assets = new AssetRepository(ctx);
    }

    private User user(String name) {
        User u = new User();
        u.setId(users.createKey());
        u.setName(name);
        u.setEmail(name.toLowerCase() + "@isms.example");
        return u;
    }

    private List<UserAudRow> history() {
        return admin.selectFrom(USER_AUD).orderBy(USER_AUD.REV.asc(), USER_AUD.NAME.asc()).fetch();
    }

    private List<String> revtypes() {
        return history().stream().map(r -> r.name() + ":" + r.revtype()).collect(Collectors.toList());
    }

    @Test
    void entitySaveWritesARevisionAndAnAuditRow() {
        User alice = users.save(user("Alice"));
        assertEquals(currentOrg.get(), alice.getOrganizationId(), "tenant set by the policy");
        assertEquals("alice@keycloak", alice.getCreatedBy(), "who-columns from the current user");
        List<RevisionRow> revisions = admin.selectFrom(REVISION).fetch();
        assertEquals(1, revisions.size());
        assertEquals("alice@keycloak", revisions.get(0).createdBy());
        assertEquals(clock.instant(), revisions.get(0).createdAt());
        UserAudRow audit = history().get(0);
        assertEquals("ADD", audit.revtype());
        assertEquals(revisions.get(0).id(), audit.rev());
        assertEquals(alice.getId().value(), audit.id());
        assertEquals(alice.getEmail(), audit.email());
        assertEquals(alice.getVersion(), audit.version());
    }

    @Test
    void saveAllIsOneRevision() {
        User a = users.save(user("A"));
        a.setName("A2");
        currentUser.set("bob@keycloak");
        users.saveAll(List.of(a, user("B"), user("C")));
        List<RevisionRow> revisions = admin.selectFrom(REVISION).orderBy(REVISION.ID.asc()).fetch();
        assertEquals(2, revisions.size());
        assertEquals("bob@keycloak", revisions.get(1).createdBy());
        assertEquals(List.of("A:ADD", "A2:MOD", "B:ADD", "C:ADD"), revtypes());
        assertEquals(3, history().stream().filter(h -> h.rev().equals(revisions.get(1).id())).count());
    }

    @Test
    void bulkUpdatesDeletesAndUpsertsAreAudited() {
        users.saveAll(List.of(user("A"), user("B")));
        ctx.update(USERS).set(USERS.ACTIVE, false).allRows().execute();
        users.delete(users.findByEmail("a@isms.example").orElseThrow());
        ctx.insertInto(USERS).set(USERS.ID, users.createKey()).set(USERS.NAME, "B again").set(USERS.EMAIL, "b@isms.example")
                .onConflict(USERS.EMAIL).doUpdateSetExcluded(USERS.NAME).execute();
        ctx.insertInto(USERS).set(USERS.ID, users.createKey()).set(USERS.NAME, "D").set(USERS.EMAIL, "d@isms.example")
                .onConflict(USERS.EMAIL).doUpdateSetExcluded(USERS.NAME).execute();
        admin.deleteFrom(USERS).where(USERS.NAME.eq("D")).execute();
        assertEquals(List.of("A:ADD", "B:ADD", "A:MOD", "B:MOD", "A:DEL", "B again:MOD", "D:ADD", "D:DEL"), revtypes());
        UserAudRow softDelete = history().get(4);
        assertEquals(clock.instant(), softDelete.deletedAt());
    }

    @Test
    void severalStatementsInOneTransactionShareTheRevision() {
        ctx.transaction(() -> {
            users.save(user("A"));
            Asset laptop = new Asset();
            laptop.setName("Laptop");
            laptop.setValue(new BigDecimal("1200"));
            assets.save(laptop);
            ctx.update(USERS).set(USERS.ACTIVE, false).allRows().execute();
        });
        assertEquals(1L, admin.selectCount().from(REVISION).fetchOne());
        assertEquals(1L, admin.selectCount().from(ASSET_AUD).fetchOne());
        assertEquals(1, history().size(), "one audit row per entity and revision");
        assertEquals("ADD", history().get(0).revtype(), "added in this revision");
        assertFalse(history().get(0).active(), "with the latest state");
    }

    @Test
    void aRollbackLeavesNoHistory() {
        assertThrows(IllegalStateException.class, () -> ctx.transaction(() -> {
            users.save(user("A"));
            throw new IllegalStateException("cancel");
        }));
        assertEquals(0L, admin.selectCount().from(REVISION).fetchOne());
        assertTrue(history().isEmpty());
        assertThrows(StatementRejectedException.class, () -> admin.truncate(USERS).cascade().execute());
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
