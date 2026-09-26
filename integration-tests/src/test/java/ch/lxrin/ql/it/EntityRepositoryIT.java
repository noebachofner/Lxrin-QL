package ch.lxrin.ql.it;

import ch.lxrin.ql.beans.BEANS;
import ch.lxrin.ql.dsl.Page;
import ch.lxrin.ql.error.EntityNotFoundException;
import ch.lxrin.ql.error.OptimisticLockException;
import ch.lxrin.ql.error.StaleEntityException;
import ch.lxrin.ql.error.TooManyRowsException;
import ch.lxrin.ql.error.UniqueViolationException;
import ch.lxrin.ql.it.db.ActiveUserRepository;
import ch.lxrin.ql.it.db.AppRole;
import ch.lxrin.ql.it.db.Asset;
import ch.lxrin.ql.it.db.AssetRepository;
import ch.lxrin.ql.it.db.Risk;
import ch.lxrin.ql.it.db.RiskKey;
import ch.lxrin.ql.it.db.RiskRepository;
import ch.lxrin.ql.it.db.User;
import ch.lxrin.ql.it.db.UserRepository;
import ch.lxrin.ql.it.db.UserRow;
import ch.lxrin.ql.it.types.UserId;
import ch.lxrin.ql.render.RenderedSql;
import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.spi.Change;
import ch.lxrin.ql.spi.ColumnConventions;
import ch.lxrin.ql.spi.ExecutionObserver;
import ch.lxrin.ql.spi.SoftDeletePolicy;
import ch.lxrin.ql.spi.StatementEvent;
import ch.lxrin.ql.types.UuidV7;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static ch.lxrin.ql.dsl.Dsl.*;
import static ch.lxrin.ql.it.db.Tables.*;
import static org.junit.jupiter.api.Assertions.*;

/** Entities and repositories, generated from the migrations, against PostgreSQL. */
class EntityRepositoryIT {

    private final List<StatementEvent> statements = new ArrayList<>();
    private final Clock clock = Clock.fixed(Instant.parse("2025-05-01T10:00:00Z"), ZoneOffset.UTC);
    private final UUID org = UUID.randomUUID();
    private QueryContext ctx;
    private UserRepository users;
    private AssetRepository assets;

    @BeforeEach
    void setUp() {
        Postgres.migrateItSchema();
        ctx = QueryContext.builder()
                .dataSource(Postgres.dataSource())
                .versionColumn("version")
                .policy(new SoftDeletePolicy("deleted_at", clock))
                .convention(ColumnConventions.onInsert("created_by", String.class, c -> "tester"))
                .convention(ColumnConventions.updatedAt("updated_at", clock))
                .convention(ColumnConventions.onInsertAndUpdate("updated_by", String.class, c -> "tester"))
                .observer(new ExecutionObserver() {
                    @Override
                    public void onSuccess(StatementEvent e, Duration took, long rows) {
                        statements.add(e);
                    }
                })
                .build();
        users = new UserRepository(ctx);
        assets = new AssetRepository(ctx);
    }

    @AfterEach
    void tearDown() {
        QueryContext.setDefault(null);
        BEANS.reset();
    }

    private User newUser(String name) {
        User user = new User();
        user.setId(users.createKey());
        user.setName(name);
        user.setEmail(name.toLowerCase() + "@example.org");
        user.setOrganizationId(org);
        return user;
    }

    private String lastSql() {
        return statements.get(statements.size() - 1).sql().sql();
    }

    @Test
    void theTargetDeveloperExperience() {
        QueryContext.setDefault(ctx);
        User user = new User();
        user.setId(BEANS.get(UserRepository.class).createKey());
        user.setName("Test");
        user.setEmail("test@gmail.com");
        user.setOrganizationId(org);
        BEANS.get(UserRepository.class).save(user);
        BEANS.get(UserRepository.class).saveAll(List.of(newUser("A"), newUser("B"), newUser("C")));

        User admin = newUser("Admin");
        admin.setRole(AppRole.ADMIN);
        users.save(admin);
        List<User> admins = BEANS.get(UserRepository.class).findAll(USERS.ROLE.eq(AppRole.ADMIN).and(USERS.DELETED_AT.isNull()));
        assertEquals(List.of("Admin"), admins.stream().map(User::getName).collect(Collectors.toList()));

        record UserSummary(UserId id, String name, String email) {}
        List<UserSummary> rows = select(USERS.ID, USERS.NAME, USERS.EMAIL).from(USERS)
                .where(USERS.EMAIL.endsWith("@gmail.com")).orderBy(USERS.NAME.asc()).fetch(UserSummary::new);
        assertEquals(List.of(new UserSummary(user.getId(), "Test", "test@gmail.com")), rows);
        assertSame(BEANS.get(UserRepository.class), BEANS.get(UserRepository.class));
    }

    @Test
    void saveInsertsThenUpdatesOnlyChangedColumns() {
        User user = newUser("Ada");
        assertTrue(user.isNew());
        assertEquals(7, UuidV7.generate().version());
        assertEquals(7, user.getId().value().version());
        users.save(user);
        assertTrue(user.isPersistent());
        assertFalse(user.isChanged());
        assertEquals(AppRole.USER, user.getRole(), "database default read back");
        assertEquals(0L, user.getVersion());
        assertEquals("tester", user.getCreatedBy(), "convention read back");
        assertNotNull(user.getCreatedAt());
        assertArrayEquals(new String[0], user.getTags());
        assertTrue(lastSql().startsWith("INSERT INTO app_user (id, name, email, organization_id, created_by, updated_at, updated_by) VALUES"));

        int before = statements.size();
        users.save(user);
        assertEquals(before, statements.size(), "no statement for an unchanged entity");

        user.setName("Ada Lovelace");
        user.setName("Ada Lovelace");
        assertEquals(Map.of(USERS.NAME, new Change<>("Ada", "Ada Lovelace")), user.changes());
        users.save(user);
        assertEquals("UPDATE app_user SET name = ?, updated_at = ?, updated_by = ?, version = (app_user.version + 1) "
                + "WHERE (app_user.id = ? AND app_user.version = ? AND app_user.deleted_at IS NULL) RETURNING app_user.id, "
                + "app_user.name, app_user.email, app_user.role, app_user.active, app_user.tags, app_user.settings, "
                + "app_user.organization_id, app_user.created_at, app_user.created_by, app_user.updated_at, app_user.updated_by, "
                + "app_user.deleted_at, app_user.version, app_user.last_seen_at", lastSql());
        assertEquals(1L, user.getVersion());
        assertEquals(clock.instant(), user.getUpdatedAt());

        user.setName("temp");
        user.setName("Ada Lovelace");
        assertFalse(user.isChanged(), "setting the original value back removes the change");
        assertEquals("Ada Lovelace", user.originalValue(USERS.NAME));
    }

    @Test
    void newEntitiesAreDecidedByStateNotById() {
        User user = newUser("Grace");
        assertNotNull(user.getId());
        users.save(user);
        assertTrue(lastSql().startsWith("INSERT"));
        user.setActive(false);
        users.save(user);
        assertTrue(lastSql().startsWith("UPDATE"));
        User detached = new User();
        detached.setId(user.getId());
        detached.setName("Grace H.");
        users.update(detached);
        assertEquals("Grace H.", users.getById(user.getId()).getName());
        assertThrows(UniqueViolationException.class, () -> users.insert(detached));
    }

    @Test
    void finders() {
        users.saveAll(List.of(newUser("Ada"), newUser("Alan"), newUser("Grace")));
        User ada = users.findByEmail("ada@example.org").orElseThrow();
        assertEquals(ada.getName(), users.getById(ada.getId()).getName());
        assertTrue(users.findById(new UserId(UUID.randomUUID())).isEmpty());
        assertThrows(EntityNotFoundException.class, () -> users.getById(new UserId(UUID.randomUUID())));
        assertEquals(3, users.count());
        assertEquals(2, users.count(USERS.NAME.startsWith("A")));
        assertTrue(users.exists(USERS.NAME.eq("Grace")));
        assertThrows(TooManyRowsException.class, () -> users.findOne(USERS.NAME.startsWith("A")));
        assertEquals(List.of("Ada", "Alan", "Grace"), users.findAll(noCondition(), USERS.NAME.asc()).stream().map(User::getName).toList());
        assertEquals(2, users.findAllById(List.of(ada.getId(), users.findByEmail("alan@example.org").orElseThrow().getId())).size());

        List<String> names = new ArrayList<>();
        String cursor = null;
        do {
            Page<User> page = users.findPage(noCondition(), cursor, 2, USERS.NAME.asc(), USERS.ID.asc());
            page.items().forEach(u -> names.add(u.getName()));
            cursor = page.nextCursor().map(ch.lxrin.ql.dsl.Cursor::encode).orElse(null);
        } while (cursor != null);
        assertEquals(List.of("Ada", "Alan", "Grace"), names);

        ActiveUserRepository active = new ActiveUserRepository(ctx);
        assertEquals(3, active.count());
        assertEquals("Ada", active.findAll(ACTIVE_USER.NAME.eq("Ada")).get(0).name());
        UserRow row = ctx.selectFrom(USERS).where(USERS.NAME.eq("Ada")).fetchOne();
        assertEquals("Ada", User.fromRow(row).getName());
        assertTrue(User.fromRow(row).isPersistent());
        assertEquals(row.id(), User.fromRow(row).toRow().id());
    }

    @Test
    void sequencesCompositeKeysAndGeneratedColumns() {
        User owner = users.save(newUser("Owner"));
        Long key = assets.createKey();
        List<Long> keys = assets.createKeys(3);
        assertEquals(3, keys.size());
        assertTrue(keys.get(0) > key);

        Asset laptop = new Asset();
        laptop.setName("Laptop");
        laptop.setOwnerId(owner.getId());
        laptop.setOrganizationId(org);
        laptop.setValue(new BigDecimal("1999.90"));
        assets.save(laptop);
        assertNotNull(laptop.getId(), "identity value read back");
        assertEquals("internal", laptop.getClassification());
        assertEquals(laptop.getId(), assets.findByOrganizationIdAndName(org, "Laptop").orElseThrow().getId());

        RiskRepository risks = new RiskRepository(ctx);
        Risk theft = new Risk();
        theft.setAssetId(laptop.getId());
        theft.setThreat("theft");
        theft.setLikelihood(3);
        theft.setImpact(4);
        risks.save(theft);
        assertEquals(12, theft.getScore(), "generated column read back");
        assertEquals(new RiskKey(laptop.getId(), "theft"), theft.id());
        theft.setImpact(5);
        risks.save(theft);
        assertEquals(15, risks.getById(new RiskKey(laptop.getId(), "theft")).getScore());
        assertThrows(UnsupportedOperationException.class, risks::createKey);

        List<String> joined = ctx.select(ASSET.NAME, USERS.NAME).from(ASSET).join(USERS).onKey(ASSET.FK_OWNER_ID).fetch(
                (a, u) -> a + "/" + u);
        assertEquals(List.of("Laptop/Owner"), joined);
    }

    @Test
    void saveAllIsAtomicAndBatched() {
        User a = newUser("A");
        User b = newUser("B");
        users.saveAll(List.of(a, b));
        assertEquals(1, statements.stream().filter(e -> e.sql().sql().startsWith("INSERT")).count(), "one multi-row insert");

        a.setName("A2");
        b.setName("B2");
        User c = newUser("C");
        statements.clear();
        users.saveAll(List.of(a, b, c));
        assertEquals(List.of("INSERT", "UPDATE"), statements.stream().map(e -> e.kind().name()).collect(Collectors.toList()));
        assertEquals(2, statements.get(1).batchSize(), "one JDBC batch for both updates");
        assertEquals(1L, a.getVersion());
        assertTrue(c.isPersistent());

        User d = newUser("D");
        User duplicate = newUser("E");
        duplicate.setEmail("a@example.org");
        a.setName("A3");
        assertThrows(UniqueViolationException.class, () -> users.saveAll(List.of(a, d, duplicate)));
        assertEquals(3, users.count(), "nothing of the failed saveAll was stored");
        assertTrue(d.isNew(), "entity state is restored after the rollback");
        assertEquals("A3", a.getName());
        assertTrue(a.isChanged());
        assertEquals(1L, a.getVersion());

        // the insert succeeds, then a batched update fails: the insert is rolled back and its entity is new again
        User f = newUser("F");
        b.setName("B3");
        ctx.bypassing(SoftDeletePolicy.class).deleteFrom(USERS).where(USERS.ID.eq(b.getId())).execute();
        assertThrows(StaleEntityException.class, () -> users.saveAll(List.of(f, a, b)));
        assertTrue(f.isNew());
        assertNull(f.getCreatedAt(), "values read back by the rolled-back insert are restored");
        assertEquals(1L, a.getVersion(), "the successful update of a is rolled back too");
        assertEquals(2, users.count());
    }

    @Test
    void largeSaveAllIsChunked() {
        QueryContext small = ctx.derive(b -> b.batchSize(10));
        UserRepository repo = new UserRepository(small);
        List<User> many = new ArrayList<>();
        for (int i = 0; i < 25; i++) many.add(newUser("U" + i));
        statements.clear();
        repo.saveAll(many);
        assertEquals(3, statements.size());
        assertEquals(25, repo.count());
        assertTrue(many.stream().allMatch(User::isPersistent));
    }

    @Test
    void staleAndOptimisticLocking() {
        User user = users.save(newUser("Ada"));
        User copy = users.getById(user.getId());
        copy.setName("changed elsewhere");
        users.save(copy);

        user.setName("mine");
        OptimisticLockException lock = assertThrows(OptimisticLockException.class, () -> users.save(user));
        assertTrue(lock.getMessage().contains("version 0"));

        User gone = users.save(newUser("Gone"));
        ctx.bypassing(SoftDeletePolicy.class).deleteFrom(USERS).where(USERS.ID.eq(gone.getId())).execute();
        gone.setName("x");
        assertThrows(StaleEntityException.class, () -> users.save(gone));
        assertThrows(StaleEntityException.class, () -> users.delete(gone));
    }

    @Test
    void deletesUseTheSoftDeletePolicy() {
        User a = users.save(newUser("A"));
        User b = users.save(newUser("B"));
        users.delete(a);
        assertTrue(a.isDeleted());
        assertThrows(IllegalStateException.class, () -> users.save(a));
        assertTrue(users.findById(a.getId()).isEmpty());
        UserRepository all = users.bypassing(SoftDeletePolicy.class);
        assertEquals(clock.instant(), all.getById(a.getId()).getDeletedAt());
        users.deleteById(b.getId());
        assertThrows(EntityNotFoundException.class, () -> users.deleteById(b.getId()));
        assertEquals(0, users.count());
        assertEquals(2, all.count());
        assertEquals(2, all.deleteAll(USERS.NAME.in(List.of("A", "B"))));
        assertEquals(0, all.count());
        assertThrows(IllegalStateException.class, () -> users.delete(newUser("new")));
    }

    @Test
    void entityStateIsRestoredWhenTheSurroundingTransactionRollsBack() {
        User user = newUser("Tx");
        assertThrows(IllegalStateException.class, () -> ctx.transaction(() -> {
            users.save(user);
            assertTrue(user.isPersistent());
            throw new IllegalStateException("rollback");
        }));
        assertTrue(user.isNew());
        users.save(user);
        assertEquals(1, users.count());
    }

    @Test
    void listenersSeeEntityChanges() {
        List<String> seen = new ArrayList<>();
        UserRepository watched = new UserRepository(ctx.derive(b -> b.listener(new ch.lxrin.ql.spi.StatementListener() {
            @Override
            public void afterUpdate(ch.lxrin.ql.spi.WriteResult r) {
                r.entityWrites().forEach(w -> w.changes().forEach((c, ch) -> seen.add(c.name() + ":" + ch.oldValue() + "->" + ch.newValue())));
                seen.add("origin " + r.origin());
            }
        })));
        User user = watched.save(newUser("Ada"));
        user.setName("Ada L.");
        watched.save(user);
        assertEquals(List.of("name:Ada->Ada L.", "origin REPOSITORY:UserRepository.update"), seen);
        RenderedSql sql = statements.get(statements.size() - 1).sql();
        assertTrue(sql.toString().contains("'Ada L.'"));
    }
}
