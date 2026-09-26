package ch.lxrin.ql.codegen;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Configuration of the code generator. Used directly, by the Gradle and
 * Maven plugins and by the command line ({@link Main}).
 */
public final class CodegenConfig {

    /**
     * Maps matching columns to a custom Java type, e.g. a value object.
     *
     * @param tables     regular expression for table names ({@code .*} for all)
     * @param columns    regular expression for column names
     * @param sqlTypes   regular expression for the SQL type name ({@code .*} for all)
     * @param javaType   fully qualified Java type, e.g. {@code com.example.UserId}
     * @param dataType   Java expression of its {@code DataType}, e.g. {@code com.example.Types.USER_ID}
     */
    public record ForcedType(String tables, String columns, String sqlTypes, String javaType, String dataType) {

        /** Creates the mapping and validates the patterns. */
        public ForcedType {
            Pattern.compile(Objects.requireNonNull(tables, "tables"));
            Pattern.compile(Objects.requireNonNull(columns, "columns"));
            Pattern.compile(Objects.requireNonNull(sqlTypes, "sqlTypes"));
            Objects.requireNonNull(javaType, "javaType");
            Objects.requireNonNull(dataType, "dataType");
        }

        boolean matches(String table, String column, String sqlType) {
            return table.matches(tables) && column.matches(columns) && sqlType.matches(sqlTypes);
        }
    }

    private String packageName;
    private final List<String> schemas = new ArrayList<>(List.of("public"));
    private String defaultSchema = "public";
    private final List<String> includes = new ArrayList<>();
    private final List<String> excludes = new ArrayList<>(List.of("flyway_schema_history", "databasechangelog.*"));
    private final List<String> stripTablePrefixes = new ArrayList<>();
    private boolean singularize = true;
    private final Map<String, String> entityNames = new LinkedHashMap<>();
    private final Map<String, String> tableConstants = new LinkedHashMap<>();
    private final List<ForcedType> forcedTypes = new ArrayList<>();
    private final Map<String, String> enumMappings = new LinkedHashMap<>();
    private Path outputDirectory;
    private Path resourcesDirectory;
    private Path repositoryStubDirectory;
    private boolean generateEntities = true;
    private boolean generateRepositories = true;

    /** Sets the package of the generated code (required). */
    public CodegenConfig packageName(String value) {
        if (value == null || !value.matches("[a-z_][a-z0-9_]*(\\.[a-z_][a-z0-9_]*)*")) {
            throw new IllegalArgumentException("invalid package name: " + value);
        }
        packageName = value;
        return this;
    }

    /** Sets the schemas to read (default {@code public}). */
    public CodegenConfig schemas(List<String> values) {
        schemas.clear();
        schemas.addAll(values);
        return this;
    }

    /** Sets the schema whose tables are referenced without schema name (default {@code public}). */
    public CodegenConfig defaultSchema(String value) {
        defaultSchema = value;
        return this;
    }

    /** Only generates tables whose name matches one of these regular expressions (default: all). */
    public CodegenConfig include(String... regexes) {
        includes.addAll(List.of(regexes));
        return this;
    }

    /** Skips tables whose name matches (default: Flyway and Liquibase history tables). */
    public CodegenConfig exclude(String... regexes) {
        excludes.addAll(List.of(regexes));
        return this;
    }

    /** Strips a table name prefix before deriving class names, e.g. {@code app_}. */
    public CodegenConfig stripTablePrefix(String... prefixes) {
        stripTablePrefixes.addAll(List.of(prefixes));
        return this;
    }

    /** Whether table names are singularised for entity names ({@code users} → {@code User}); default {@code true}. */
    public CodegenConfig singularize(boolean value) {
        singularize = value;
        return this;
    }

    /** Overrides the entity name of a table, e.g. {@code entityName("app_user_role", "UserRole")}. */
    public CodegenConfig entityName(String table, String entity) {
        entityNames.put(table, entity);
        return this;
    }

    /** Overrides the constant name of a table, e.g. {@code tableConstant("app_user", "USERS")}. */
    public CodegenConfig tableConstant(String table, String constant) {
        tableConstants.put(table, constant);
        return this;
    }

    /** Adds a forced type. */
    public CodegenConfig forcedType(ForcedType type) {
        forcedTypes.add(type);
        return this;
    }

    /** Maps a PostgreSQL enum type to an existing Java enum instead of generating one. */
    public CodegenConfig enumMapping(String pgEnum, String javaEnum) {
        enumMappings.put(pgEnum, javaEnum);
        return this;
    }

    /** Sets the directory for generated sources (required); it is owned by the generator and cleaned. */
    public CodegenConfig outputDirectory(Path value) {
        outputDirectory = value;
        return this;
    }

    /** Sets the directory for generated resources (the repository index); optional. */
    public CodegenConfig resourcesDirectory(Path value) {
        resourcesDirectory = value;
        return this;
    }

    /**
     * Sets the source directory for the hand-written repository subclasses.
     * A subclass is written only if it does not exist yet; existing files are
     * never touched. Without this directory no subclasses are written.
     */
    public CodegenConfig repositoryStubDirectory(Path value) {
        repositoryStubDirectory = value;
        return this;
    }

    /** Whether entities are generated (default {@code true}). */
    public CodegenConfig generateEntities(boolean value) {
        generateEntities = value;
        return this;
    }

    /** Whether repositories are generated (default {@code true}; needs entities). */
    public CodegenConfig generateRepositories(boolean value) {
        generateRepositories = value;
        return this;
    }

    /** Checks that required settings are present. */
    public CodegenConfig validate() {
        if (packageName == null) throw new IllegalStateException("packageName is required");
        if (outputDirectory == null) throw new IllegalStateException("outputDirectory is required");
        if (schemas.isEmpty()) throw new IllegalStateException("at least one schema is required");
        return this;
    }

    String packageName() {
        return packageName;
    }

    List<String> schemas() {
        return schemas;
    }

    String defaultSchema() {
        return defaultSchema;
    }

    boolean included(String table) {
        for (String e : excludes) if (table.matches(e)) return false;
        if (includes.isEmpty()) return true;
        for (String i : includes) if (table.matches(i)) return true;
        return false;
    }

    List<String> stripTablePrefixes() {
        return stripTablePrefixes;
    }

    boolean singularize() {
        return singularize;
    }

    Map<String, String> entityNames() {
        return entityNames;
    }

    Map<String, String> tableConstants() {
        return tableConstants;
    }

    List<ForcedType> forcedTypes() {
        return forcedTypes;
    }

    Map<String, String> enumMappings() {
        return enumMappings;
    }

    Path outputDirectory() {
        return outputDirectory;
    }

    Path resourcesDirectory() {
        return resourcesDirectory;
    }

    Path repositoryStubDirectory() {
        return repositoryStubDirectory;
    }

    boolean generateEntities() {
        return generateEntities;
    }

    boolean generateRepositories() {
        return generateRepositories && generateEntities;
    }
}
