package ch.lxrin.ql.spring;

import ch.lxrin.ql.RecordingExecutor;
import ch.lxrin.ql.audit.AuditListener;
import ch.lxrin.ql.audit.AuditSettings;
import ch.lxrin.ql.audit.AuditUser;
import ch.lxrin.ql.beans.BEANS;
import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.schema.AdHocTable;
import ch.lxrin.ql.types.SqlTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LxrinQlAuditAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LxrinQlAutoConfiguration.class, LxrinQlAuditAutoConfiguration.class))
            .withBean(DataSource.class, PGSimpleDataSource::new)
            .withBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(new PGSimpleDataSource()));

    @AfterEach
    void reset() {
        QueryContext.setDefault(null);
        BEANS.reset();
    }

    @Test
    void propertiesConfigureTheListenerAndItJoinsTheContext() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        AuditUser<UUID> user = AuditUser.of(SqlTypes.UUID, UUID::randomUUID);
        runner.withPropertyValues("lxrin.ql.audit.tables=app_user,asset", "lxrin.ql.audit.table-patterns=doc.*",
                        "lxrin.ql.audit.excluded-columns.app_user=last_seen_at,login_count", "lxrin.ql.audit.store-data-at-delete=true",
                        "lxrin.ql.audit.suffix=_history", "lxrin.ql.audit.revision.table=revinfo", "lxrin.ql.audit.revision.id-column=rev",
                        "lxrin.ql.audit.revision.schema=audit")
                .withBean(Clock.class, () -> clock)
                .withBean("auditUser", AuditUser.class, () -> user)
                .run(ctx -> {
                    AuditListener listener = ctx.getBean(AuditListener.class);
                    AuditSettings s = listener.settings();
                    assertTrue(s.audits(new AdHocTable(null, "app_user", null)));
                    assertTrue(s.audits(new AdHocTable(null, "documents", null)));
                    assertFalse(s.audits(new AdHocTable(null, "risk", null)));
                    assertEquals(java.util.Set.of("last_seen_at", "login_count"), s.excludedColumns(new AdHocTable(null, "app_user", null)));
                    assertTrue(s.storeDataAtDelete());
                    assertEquals("_history", s.suffix());
                    assertEquals("revinfo", s.revisionTable());
                    assertEquals("rev", s.revisionIdColumn());
                    assertEquals("audit", s.revisionSchema());
                    assertSame(user, s.user());
                    assertSame(clock, s.clock());
                    assertTrue(ctx.getBean(QueryContext.class).listeners().contains(listener));
                });
    }

    @Test
    void noListenerWithoutTablesWhenDisabledOrWithoutTheModule() {
        runner.run(ctx -> assertTrue(ctx.getBeansOfType(AuditListener.class).isEmpty()));
        runner.withPropertyValues("lxrin.ql.audit.tables=app_user", "lxrin.ql.audit.enabled=false")
                .run(ctx -> assertTrue(ctx.getBeansOfType(AuditListener.class).isEmpty()));
        runner.withPropertyValues("lxrin.ql.audit.tables=app_user").withClassLoader(new FilteredClassLoader(AuditListener.class))
                .run(ctx -> {
                    assertFalse(ctx.containsBean("lxrinAuditListener"));
                    assertNotNull(ctx.getBean(QueryContext.class));
                });
        AuditListener own = new AuditListener(AuditSettings.builder().tables("x").build());
        runner.withPropertyValues("lxrin.ql.audit.tables=app_user").withBean(AuditListener.class, () -> own)
                .withBean(QueryContextCustomizer.class, () -> b -> b.executor(new RecordingExecutor()))
                .run(ctx -> assertSame(own, ctx.getBean(AuditListener.class)));
    }
}
