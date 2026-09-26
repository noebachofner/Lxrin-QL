package ch.lxrin.ql.spring;

import ch.lxrin.ql.audit.AuditListener;
import ch.lxrin.ql.audit.AuditSettings;
import ch.lxrin.ql.audit.AuditUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.time.Clock;
import java.util.List;

/**
 * The audit history of {@code lxrin-ql-audit}, when it is on the class path and
 * {@code lxrin.ql.audit.tables} or {@code lxrin.ql.audit.table-patterns} is set: an
 * {@link AuditListener} bean, which {@link LxrinQlAutoConfiguration} adds to the query
 * context. The revision user comes from an {@link AuditUser} bean, the revision time from
 * a {@link Clock} bean if there is one.
 */
@AutoConfiguration(before = LxrinQlAutoConfiguration.class)
@ConditionalOnClass(AuditListener.class)
@ConditionalOnProperty(prefix = "lxrin.ql.audit", name = "enabled", matchIfMissing = true)
@Conditional(LxrinQlAuditAutoConfiguration.TablesConfigured.class)
@EnableConfigurationProperties(LxrinQlAuditProperties.class)
public class LxrinQlAuditAutoConfiguration {

    /** The audit listener. */
    @Bean
    @ConditionalOnMissingBean(AuditListener.class)
    public AuditListener lxrinAuditListener(LxrinQlAuditProperties properties, ObjectProvider<AuditUser<?>> user,
                                            ObjectProvider<Clock> clock) {
        AuditSettings.Builder settings = AuditSettings.builder()
                .tables(properties.getTables())
                .tablePatterns(properties.getTablePatterns())
                .suffix(properties.getSuffix())
                .storeDataAtDelete(properties.isStoreDataAtDelete())
                .revColumn(properties.getRevColumn())
                .revtypeColumn(properties.getRevtypeColumn())
                .revisionSchema(blankToNull(properties.getRevision().getSchema()))
                .revisionTable(properties.getRevision().getTable())
                .revisionIdColumn(properties.getRevision().getIdColumn())
                .revisionTimestampColumn(properties.getRevision().getTimestampColumn())
                .revisionUserColumn(properties.getRevision().getUserColumn());
        properties.getExcludedColumns().forEach(settings::excludeColumns);
        user.ifAvailable(settings::user);
        clock.ifAvailable(settings::clock);
        return new AuditListener(settings.build());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** Matches when audited tables or table patterns are configured. */
    static class TablesConfigured extends SpringBootCondition {
        @Override
        public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Binder binder = Binder.get(context.getEnvironment());
            Bindable<List<String>> names = Bindable.listOf(String.class);
            boolean configured = !binder.bind("lxrin.ql.audit.tables", names).orElse(List.of()).isEmpty()
                    || !binder.bind("lxrin.ql.audit.table-patterns", names).orElse(List.of()).isEmpty();
            return configured ? ConditionOutcome.match("audited tables are configured")
                    : ConditionOutcome.noMatch("neither lxrin.ql.audit.tables nor lxrin.ql.audit.table-patterns is set");
        }
    }
}
