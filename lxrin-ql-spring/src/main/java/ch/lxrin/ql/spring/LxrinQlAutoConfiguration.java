package ch.lxrin.ql.spring;

import ch.lxrin.ql.beans.BEANS;
import ch.lxrin.ql.runtime.LoggingObserver;
import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.spi.ColumnConvention;
import ch.lxrin.ql.spi.ExecutionObserver;
import ch.lxrin.ql.spi.StatementListener;
import ch.lxrin.ql.spi.TablePolicy;
import ch.lxrin.ql.types.JsonCodec;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.context.ApplicationEvent;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.util.stream.Collectors;

/**
 * Auto-configuration of LxrinQL for Spring Boot 4:
 * <ul>
 *   <li>a {@link QueryContext} bean on the application's {@link DataSource}, joining Spring-managed transactions;</li>
 *   <li>all beans of type {@link StatementListener}, {@link TablePolicy}, {@link ColumnConvention},
 *       {@link ExecutionObserver} and {@link QueryContextCustomizer} (ordered by {@code @Order});</li>
 *   <li>the generated repositories as beans;</li>
 *   <li>a Jackson JSON codec and a Micrometer observer when those libraries are present;</li>
 *   <li>the context as default for the static DSL and {@code BEANS} backed by the application context.</li>
 * </ul>
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
        "org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration",
        "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration",
        "org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration"})
@ConditionalOnClass({DataSource.class, PlatformTransactionManager.class})
@EnableConfigurationProperties(LxrinQlProperties.class)
public class LxrinQlAutoConfiguration {

    /** The query context. */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnSingleCandidate(DataSource.class)
    @ConditionalOnBean(PlatformTransactionManager.class)
    public QueryContext lxrinQueryContext(DataSource dataSource, PlatformTransactionManager transactionManager,
                                          LxrinQlProperties properties,
                                          ObjectProvider<JsonCodec> jsonCodec,
                                          ObjectProvider<StatementListener> listeners,
                                          ObjectProvider<TablePolicy> policies,
                                          ObjectProvider<ColumnConvention<?>> conventions,
                                          ObjectProvider<ExecutionObserver> observers,
                                          ObjectProvider<QueryContextCustomizer> customizers) {
        QueryContext.Builder builder = QueryContext.builder()
                .connectionProvider(new SpringConnectionProvider(dataSource))
                .transactions(new SpringTransactionRunner(transactionManager))
                .batchSize(properties.getBatchSize())
                .fetchSize(properties.getFetchSize())
                .listeners(listeners.orderedStream().collect(Collectors.toList()))
                .policies(policies.orderedStream().collect(Collectors.toList()))
                .conventions(conventions.orderedStream().collect(Collectors.toList()))
                .observers(observers.orderedStream().collect(Collectors.toList()));
        jsonCodec.ifAvailable(builder::jsonCodec);
        if (properties.getVersionColumn() != null && !properties.getVersionColumn().isBlank()) {
            builder.versionColumn(properties.getVersionColumn());
        }
        customizers.orderedStream().forEach(c -> c.customize(builder));
        return builder.build();
    }

    /** Statement logging (logger {@code ch.lxrin.ql.sql}). */
    @Bean
    @ConditionalOnProperty(prefix = "lxrin.ql.logging", name = "enabled", matchIfMissing = true)
    public LoggingObserver lxrinLoggingObserver(LxrinQlProperties properties) {
        return new LoggingObserver(properties.getLogging().getSlowThreshold(), properties.getLogging().isBinds());
    }

    /** Registers the generated repositories listed in {@code META-INF/lxrin-ql/repositories}. */
    @Bean
    public static BeanDefinitionRegistryPostProcessor lxrinRepositoryIndexRegistrar() {
        return new BeanDefinitionRegistryPostProcessor() {
            @Override
            public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
                RepositoryRegistrar.registerIndexed(registry, LxrinQlAutoConfiguration.class.getClassLoader());
            }

            @Override
            public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
                // nothing to do
            }
        };
    }

    /** Sets the default context and lets {@code BEANS} delegate to the application context. */
    @Bean
    @ConditionalOnProperty(prefix = "lxrin.ql", name = "register-default", matchIfMissing = true)
    @ConditionalOnBean(QueryContext.class)
    public static SmartApplicationListener lxrinDefaultsRegistrar(ApplicationContext context) {
        return new SmartApplicationListener() {
            @Override
            public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
                return ContextRefreshedEvent.class.isAssignableFrom(eventType) || ContextClosedEvent.class.isAssignableFrom(eventType);
            }

            @Override
            public void onApplicationEvent(ApplicationEvent event) {
                if (event instanceof ContextRefreshedEvent && ((ContextRefreshedEvent) event).getApplicationContext() == context) {
                    QueryContext.setDefault(context.getBean(QueryContext.class));
                    BEANS.setRegistry(new SpringBeanRegistry(context));
                } else if (event instanceof ContextClosedEvent && ((ContextClosedEvent) event).getApplicationContext() == context) {
                    QueryContext.findDefault().filter(d -> d == context.getBean(QueryContext.class)).ifPresent(d -> QueryContext.setDefault(null));
                    if (BEANS.registry() instanceof SpringBeanRegistry) BEANS.reset();
                }
            }
        };
    }

    /** JSON through the application's Jackson 3 mapper. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JsonMapper.class)
    static class JacksonConfiguration {
        @Bean
        @ConditionalOnMissingBean(JsonCodec.class)
        @ConditionalOnBean(JsonMapper.class)
        JsonCodec lxrinJsonCodec(JsonMapper mapper) {
            return new JacksonJsonCodec(mapper);
        }
    }

    /** Metrics and tracing through Micrometer observations. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ObservationRegistry.class)
    static class ObservationConfiguration {
        @Bean
        @ConditionalOnBean(ObservationRegistry.class)
        ObservationExecutionObserver lxrinObservationObserver(ObservationRegistry registry) {
            return new ObservationExecutionObserver(registry);
        }
    }
}
