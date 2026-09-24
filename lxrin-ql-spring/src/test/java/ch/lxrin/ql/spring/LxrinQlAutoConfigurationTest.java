package ch.lxrin.ql.spring;

import ch.lxrin.ql.RecordingExecutor;
import ch.lxrin.ql.beans.BEANS;
import ch.lxrin.ql.runtime.LoggingObserver;
import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.types.JsonCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LxrinQlAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LxrinQlAutoConfiguration.class))
            .withBean(DataSource.class, PGSimpleDataSource::new)
            .withBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(new PGSimpleDataSource()));

    @AfterEach
    void reset() {
        QueryContext.setDefault(null);
        BEANS.reset();
    }

    @Test
    void createsTheContextAndRegistersDefaults() {
        runner.withPropertyValues("lxrin.ql.batch-size=42", "lxrin.ql.version-column=version").run(ctx -> {
            QueryContext query = ctx.getBean(QueryContext.class);
            assertEquals(42, query.batchSize());
            assertEquals("version", query.versionColumn().orElseThrow());
            assertInstanceOf(SpringTransactionRunner.class, query.transactions());
            assertSame(query, QueryContext.getDefault());
            assertInstanceOf(SpringBeanRegistry.class, BEANS.registry());
            assertSame(query, BEANS.get(QueryContext.class));
            assertTrue(query.observers().stream().anyMatch(o -> o instanceof LoggingObserver));
        });
        assertTrue(QueryContext.findDefault().isEmpty(), "cleared when the context closes");
    }

    @Test
    void customizersRunAndPropertiesSwitchFeaturesOff() {
        runner.withPropertyValues("lxrin.ql.logging.enabled=false", "lxrin.ql.register-default=false")
                .withBean(QueryContextCustomizer.class, () -> b -> b.fetchSize(7).executor(new RecordingExecutor()))
                .run(ctx -> {
                    QueryContext query = ctx.getBean(QueryContext.class);
                    assertTrue(query.observers().isEmpty());
                    assertTrue(QueryContext.findDefault().isEmpty());
                });
    }

    @Test
    void backsOffWithoutDataSourceOrWithOwnContext() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(LxrinQlAutoConfiguration.class))
                .run(ctx -> assertFalse(ctx.containsBean("lxrinQueryContext")));
        QueryContext own = QueryContext.builder().executor(new RecordingExecutor()).build();
        runner.withBean("myContext", QueryContext.class, () -> own).run(ctx -> assertSame(own, ctx.getBean(QueryContext.class)));
    }

    @Test
    void jacksonCodecWhenAMapperIsPresent() {
        runner.withBean(JsonMapper.class, JsonMapper::new).run(ctx -> {
            JsonCodec codec = ctx.getBean(JsonCodec.class);
            assertEquals("{\"a\":1}", codec.write(Map.of("a", 1)));
            assertEquals(List.of(1, 2), codec.read("[1,2]", List.class));
        });
    }

    @Test
    void beanRegistryRegistersInstances() {
        runner.run(ctx -> {
            SpringBeanRegistry registry = new SpringBeanRegistry(ctx);
            registry.register(StringBuilder.class, new StringBuilder("x"));
            assertEquals("x", registry.get(StringBuilder.class).toString());
        });
    }
}
