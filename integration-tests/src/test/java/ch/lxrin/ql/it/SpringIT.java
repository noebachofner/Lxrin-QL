package ch.lxrin.ql.it;

import ch.lxrin.ql.beans.BEANS;
import ch.lxrin.ql.it.db.User;
import ch.lxrin.ql.it.db.UserRepository;
import ch.lxrin.ql.runtime.LoggingObserver;
import ch.lxrin.ql.runtime.Propagation;
import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.runtime.TransactionScope;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.spi.ColumnConvention;
import ch.lxrin.ql.spi.ColumnConventions;
import ch.lxrin.ql.spi.SoftDeletePolicy;
import ch.lxrin.ql.spi.StatementListener;
import ch.lxrin.ql.spi.WriteResult;
import ch.lxrin.ql.spring.ObservationExecutionObserver;
import ch.lxrin.ql.spring.SpringTransactionRunner;
import ch.lxrin.ql.types.SqlTypes;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static ch.lxrin.ql.it.db.Tables.USERS;
import static org.junit.jupiter.api.Assertions.*;

/** The Spring Boot 4 integration: auto-configuration, Spring transactions, repositories as beans, BEANS, Jackson, Micrometer. */
@SpringBootTest(classes = SpringIT.App.class, properties = {"lxrin.ql.version-column=version", "lxrin.ql.logging.binds=true"})
class SpringIT {

    static final List<String> OBSERVED = new CopyOnWriteArrayList<>();
    static final List<String> LISTENED = new CopyOnWriteArrayList<>();
    static final TransactionScope.Key<UUID> TX_ID = TransactionScope.key("tx id");

    @SpringBootApplication(proxyBeanMethods = false)
    static class App {
        @Bean
        SoftDeletePolicy softDelete() {
            return new SoftDeletePolicy("deleted_at", Clock.systemUTC());
        }

        @Bean
        ColumnConvention<String> createdBy() {
            return ColumnConventions.onInsert("created_by", String.class, c -> "spring");
        }

        @Bean
        @Order(1)
        StatementListener txListener() {
            return new StatementListener() {
                @Override
                public boolean appliesTo(Table<?> table) {
                    return table.sameTable(USERS);
                }

                @Override
                public void afterInsert(WriteResult r) {
                    LISTENED.add("insert in tx " + r.transaction().attribute(TX_ID, UUID::randomUUID));
                }
            };
        }

        @Bean
        ObservationRegistry observationRegistry() {
            ObservationRegistry registry = ObservationRegistry.create();
            registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
                @Override
                public boolean supportsContext(Observation.Context context) {
                    return true;
                }

                @Override
                public void onStop(Observation.Context context) {
                    OBSERVED.add(context.getName() + " " + context.getLowCardinalityKeyValue("kind").getValue());
                }
            });
            return registry;
        }

        @Bean
        UserService userService(UserRepository users, QueryContext ctx, JdbcTemplate jdbc) {
            return new UserService(users, ctx, jdbc);
        }
    }

    /** A Spring service with {@code @Transactional} methods. */
    @Service
    static class UserService {
        final UserRepository users;
        final QueryContext ctx;
        final JdbcTemplate jdbc;

        UserService(UserRepository users, QueryContext ctx, JdbcTemplate jdbc) {
            this.users = users;
            this.ctx = ctx;
            this.jdbc = jdbc;
        }

        @Transactional
        public void saveAndFail(User user) {
            users.save(user);
            throw new IllegalStateException("rollback");
        }

        @Transactional
        public Integer saveAndCountWithJdbc(User user) {
            users.save(user);
            return jdbc.queryForObject("SELECT count(*) FROM app_user WHERE id = ?", Integer.class, user.getId().value());
        }

        @Transactional
        public void saveTwoInOneTransaction(User a, User b) {
            users.save(a);
            users.save(b);
        }

        @Transactional
        public void nestedAndRequiresNew(User outer, User nested, User independent) {
            users.save(outer);
            assertThrows(IllegalStateException.class, () -> ctx.transaction(Propagation.NESTED, () -> {
                users.save(nested);
                throw new IllegalStateException("undo the savepoint only");
            }));
            ctx.transaction(Propagation.REQUIRES_NEW, () -> users.save(independent));
        }

        @Transactional
        public void markRollbackOnly(User user) {
            users.save(user);
            ctx.currentTransaction().orElseThrow().setRollbackOnly();
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", Postgres::jdbcUrl);
        registry.add("spring.datasource.username", Postgres::user);
        registry.add("spring.datasource.password", Postgres::password);
    }

    @BeforeAll
    static void schema() {
        Postgres.migrateItSchema();
    }

    @Autowired
    QueryContext ctx;
    @Autowired
    UserRepository users;
    @Autowired
    UserService service;
    @Autowired
    LoggingObserver loggingObserver;

    @BeforeEach
    void clean() {
        ctx.bypassing(SoftDeletePolicy.class).deleteFrom(USERS).allRows().execute();
        LISTENED.clear();
    }

    private User user(String name) {
        User u = new User();
        u.setId(users.createKey());
        u.setName(name);
        u.setEmail(name + "-" + UUID.randomUUID() + "@example.org");
        u.setOrganizationId(UUID.randomUUID());
        return u;
    }

    @Test
    void contextBeansDefaultsAndBeans() {
        assertSame(ctx, QueryContext.getDefault());
        assertSame(users, BEANS.get(UserRepository.class), "BEANS returns the Spring bean");
        assertInstanceOf(SpringTransactionRunner.class, ctx.transactions());
        assertEquals("version", ctx.versionColumn().orElseThrow());
        assertEquals(1, ctx.policies().size());
        assertEquals(1, ctx.conventions().size());
        assertEquals(1, ctx.listeners().size());
        assertTrue(ctx.observers().contains(loggingObserver));
        assertTrue(ctx.observers().stream().anyMatch(o -> o instanceof ObservationExecutionObserver));
    }

    @Test
    void transactionalRollbackUndoesTheWriteAndRestoresTheEntity() {
        User u = user("rollback");
        assertThrows(IllegalStateException.class, () -> service.saveAndFail(u));
        assertTrue(u.isNew(), "entity state restored after the Spring rollback");
        assertFalse(users.exists(USERS.ID.eq(u.getId())));
    }

    @Test
    void statementsJoinTheSpringTransaction() {
        User u = user("joined");
        assertEquals(1, service.saveAndCountWithJdbc(u), "JdbcTemplate sees the uncommitted row: same connection");
        assertEquals("spring", users.getById(u.getId()).getCreatedBy());
    }

    @Test
    void transactionScopeIsSharedWithinOneSpringTransaction() {
        service.saveTwoInOneTransaction(user("a"), user("b"));
        assertEquals(2, LISTENED.size());
        assertEquals(LISTENED.get(0), LISTENED.get(1), "one transaction attribute for both statements");
        users.save(user("c"));
        assertNotEquals(LISTENED.get(0), LISTENED.get(2));
    }

    @Test
    void nestedSavepointsAndRequiresNew() {
        User outer = user("outer");
        User nested = user("nested");
        User independent = user("independent");
        service.nestedAndRequiresNew(outer, nested, independent);
        assertTrue(users.exists(USERS.ID.eq(outer.getId())));
        assertFalse(users.exists(USERS.ID.eq(nested.getId())));
        assertTrue(nested.isNew(), "savepoint rollback restores the entity");
        assertTrue(users.exists(USERS.ID.eq(independent.getId())));
    }

    @Test
    void rollbackOnlyRollsBack() {
        User u = user("rollback-only");
        assertThrows(RuntimeException.class, () -> service.markRollbackOnly(u));
        assertFalse(users.exists(USERS.ID.eq(u.getId())));
    }

    @Test
    void jacksonCodecAndObservations() {
        User u = user("json");
        u.setSettings("{\"theme\": \"dark\", \"size\": 3}");
        users.save(u);
        @SuppressWarnings("unchecked")
        Map<String, Object> settings = ctx.select(USERS.SETTINGS.cast(SqlTypes.jsonb(Map.class))).from(USERS)
                .where(USERS.ID.eq(u.getId())).fetchOne();
        assertEquals("dark", settings.get("theme"));
        assertEquals(3, settings.get("size"));
        assertTrue(OBSERVED.contains("lxrin.ql.statement INSERT"));
        assertTrue(OBSERVED.contains("lxrin.ql.statement SELECT"));
    }

    @Test
    void staticDslUsesTheSpringContext() {
        users.save(user("static"));
        List<String> names = new ArrayList<>(ch.lxrin.ql.dsl.Dsl.select(USERS.NAME).from(USERS).fetch());
        assertEquals(List.of("static"), names);
    }
}
