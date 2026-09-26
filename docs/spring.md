# Spring Boot

`ch.lxrin:lxrin-ql-spring` integrates LxrinQL with Spring Boot 4. Spring itself is
provided by your application.

```kotlin
dependencies {
    implementation("ch.lxrin:lxrin-ql-spring:3.1.0")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
}
```

## What the auto-configuration does

- **A `QueryContext` bean** on the application's `DataSource`:
  - connections come from `DataSourceUtils`, so LxrinQL statements, `JdbcTemplate` and
    JPA share `@Transactional` transactions;
  - `ctx.transaction(..)` delegates to the `PlatformTransactionManager`
    (`NESTED` uses savepoints);
  - the transaction scope (attributes, commit and rollback callbacks) is attached to
    the Spring transaction.
- **Extension beans are collected**, in `@Order`: every bean of type
  `StatementListener`, `TablePolicy`, `ColumnConvention`, `ExecutionObserver` and
  `QueryContextCustomizer`.
- **Repositories become beans.** The generated repositories listed in
  `META-INF/lxrin-ql/repositories` are registered automatically; alternatively use
  `@EnableLxrinRepositories(basePackages = "com.example.db")`.
- **Default context and `BEANS`.** When the context has started,
  `QueryContext.getDefault()` returns the bean (for the static `Dsl.*`), and
  `BEANS.get(..)` delegates to the `ApplicationContext`, so
  `BEANS.get(UserRepository.class)` returns the injected bean.
- **JSON** uses the application's Jackson 3 `JsonMapper` (for `SqlTypes.jsonb(MyRecord.class)`).
- **Micrometer**: with an `ObservationRegistry`, every statement becomes an observation
  `lxrin.ql.statement`, with metrics and tracing spans.
- **Logging** through `LoggingObserver` (logger `ch.lxrin.ql.sql`).

The context backs off if you define your own `QueryContext` bean.

## Example

```java
@Configuration
class PersistenceConfig {

    @Bean
    SoftDeletePolicy softDelete(Clock clock) {
        return new SoftDeletePolicy("deleted_at", clock);
    }

    @Bean
    TenantPolicy<UUID> tenants(CurrentTenant tenant) {
        return new TenantPolicy<>("organization_id", SqlTypes.UUID, tenant::id);
    }

    @Bean
    ColumnConvention<UUID> createdBy() {
        return ColumnConventions.createdBy("created_by", UUID.class, CurrentUser::id);
    }

    @Bean
    ColumnConvention<UUID> updatedBy() {
        return ColumnConventions.updatedBy("updated_by", UUID.class, CurrentUser::id);
    }

    @Bean
    ColumnConvention<Instant> updatedAt(Clock clock) {
        return ColumnConventions.updatedAt("updated_at", clock);
    }

    @Bean
    AuditUser<UUID> auditUser() {
        return AuditUser.of(SqlTypes.UUID, CurrentUser::id);   // with lxrin-ql-audit and lxrin.ql.audit.tables
    }
}

@Service
class UserService {
    private final UserRepository users;

    UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional
    public void rename(UserId id, String name) {
        User user = users.getById(id);
        user.setName(name);
        users.save(user);
    }
}
```

### The current user from Spring Security

`CurrentUser::id` above reads the user's UUID from the security context. With Keycloak
and `spring-boot-starter-oauth2-resource-server`, the authentication name is the token's
`sub` claim, which is the user's UUID:

```java
public final class CurrentUser {

    private CurrentUser() {}

    public static UUID id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return null;   // e.g. a scheduled job
        return UUID.fromString(authentication.getName());
    }
}
```

`createdBy` sets the column on insert; `updatedBy` sets it on insert and update. A
`null` from the supplier writes `NULL`. The supplier runs in the thread that executes
the statement, which is the request thread for `@Transactional` services. The tested
version is
[`SpringSecurityIT`](../integration-tests/src/test/java/ch/lxrin/ql/it/SpringSecurityIT.java).

## Properties

| Property | Default | Meaning |
|---|---|---|
| `lxrin.ql.version-column` | – | optimistic-locking column, e.g. `version` |
| `lxrin.ql.batch-size` | `1000` | rows per multi-row insert and JDBC batch |
| `lxrin.ql.fetch-size` | `500` | JDBC fetch size of `stream()` |
| `lxrin.ql.register-default` | `true` | set the default context and let `BEANS` delegate to Spring |
| `lxrin.ql.logging.enabled` | `true` | register the `LoggingObserver` |
| `lxrin.ql.logging.slow-threshold` | `500ms` | slower statements are logged as warnings |
| `lxrin.ql.logging.binds` | `false` | include bind values in the log (sensitive values are always redacted) |

The audit history (`lxrin-ql-audit`) has its own properties, `lxrin.ql.audit.*`. See
[Audit history](audit.md#with-spring-boot).

To change the builder beyond these properties, declare a `QueryContextCustomizer` bean.
