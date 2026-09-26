package ch.lxrin.ql.test;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Runs a test class against a PostgreSQL container (Testcontainers, Docker
 * required). The container is shared by all test classes with the same image
 * and migrations; Flyway migrations are applied once. Test methods can take a
 * {@code QueryContext} or {@code DataSource} parameter.
 *
 * <p>With {@link #rollback()} (the default) each test runs on one connection
 * whose changes are rolled back afterwards, so tests do not see each other's
 * data. Transactions inside the test become savepoints then; use
 * {@code rollback = false} to test real commits.</p>
 *
 * <p>Needs {@code org.testcontainers:testcontainers-postgresql}, {@code org.flywaydb:flyway-core}
 * (+ {@code flyway-database-postgresql}) and the PostgreSQL driver on the test classpath.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ExtendWith(PostgresExtension.class)
public @interface LxrinPostgresTest {

    /** Flyway locations, e.g. {@code classpath:db/migration} (the default). */
    String[] migrations() default {"classpath:db/migration"};

    /** The Docker image. */
    String image() default "postgres:17-alpine";

    /** Whether each test's changes are rolled back. */
    boolean rollback() default true;

    /** Whether the context becomes {@code QueryContext.getDefault()} during each test. */
    boolean setDefault() default true;
}
