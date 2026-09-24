package ch.lxrin.ql.test;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit rules that keep SQL text out of application code:
 * <pre>{@code
 * @AnalyzeClasses(packages = "com.example")
 * class ArchitectureTest {
 *     @ArchTest static final ArchRule noRawSql = LxrinArchRules.noRawSql();
 *     @ArchTest static final ArchRule bypass = LxrinArchRules.policyBypassOnlyIn("com.example.admin..");
 * }
 * }</pre>
 * Needs {@code com.tngtech.archunit:archunit} on the test classpath.
 */
public final class LxrinArchRules {

    private static final String LIBRARY = "ch.lxrin.ql..";

    private LxrinArchRules() {}

    /** No class may use {@code ch.lxrin.ql.dsl.Sql} (raw SQL). */
    public static ArchRule noRawSql() {
        return noRawSqlOutside();
    }

    /** Only classes in the given packages (ArchUnit syntax, e.g. {@code "..migration.."}) may use {@code Sql}. */
    public static ArchRule noRawSqlOutside(String... allowedPackages) {
        return noClasses().that().resideOutsideOfPackages(with(allowedPackages))
                .should().dependOnClassesThat().haveFullyQualifiedName("ch.lxrin.ql.dsl.Sql")
                .as("raw SQL (ch.lxrin.ql.dsl.Sql) is only allowed in " + String.join(", ", allowedPackages))
                .allowEmptyShould(true);
    }

    /** No class may write SQL text itself by implementing {@code QueryPart} or calling {@code RenderContext.append}. */
    public static ArchRule noHandWrittenQueryParts() {
        return noClasses().that().resideOutsideOfPackage(LIBRARY)
                .should().callMethod(ch.lxrin.ql.render.RenderContext.class, "append", String.class)
                .orShould().implement("ch.lxrin.ql.render.QueryPart")
                .as("SQL text is only written by LxrinQL itself or through ch.lxrin.ql.dsl.Sql")
                .allowEmptyShould(true);
    }

    /** Table policies may only be bypassed in the given packages. */
    public static ArchRule policyBypassOnlyIn(String... allowedPackages) {
        return noClasses().that().resideOutsideOfPackages(with(allowedPackages))
                .should().callMethodWhere(com.tngtech.archunit.core.domain.JavaCall.Predicates.target(
                        com.tngtech.archunit.core.domain.properties.HasName.Predicates.name("bypassing")))
                .as("table policies may only be bypassed in " + String.join(", ", allowedPackages))
                .allowEmptyShould(true);
    }

    /** Plain JDBC ({@code java.sql}) may only be used in the given packages. */
    public static ArchRule noJdbcOutside(String... allowedPackages) {
        return noClasses().that().resideOutsideOfPackages(with(allowedPackages))
                .should().dependOnClassesThat().resideInAPackage("java.sql..")
                .as("plain JDBC is only allowed in " + String.join(", ", allowedPackages))
                .allowEmptyShould(true);
    }

    private static String[] with(String[] allowed) {
        String[] all = new String[allowed.length + 1];
        all[0] = LIBRARY;
        System.arraycopy(allowed, 0, all, 1, allowed.length);
        return all;
    }
}
