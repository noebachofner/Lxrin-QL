package ch.lxrin.ql.gradle;

import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

import javax.inject.Inject;

/**
 * The {@code lxrinQl { }} extension.
 *
 * <pre>
 * lxrinQl {
 *     packageName = "com.example.db"
 *     stripTablePrefixes = listOf("app_")
 *     tableConstants = mapOf("app_user" to "USERS")
 *     forcedType("app_user", "id", "uuid", "com.example.UserId", "com.example.Types.USER_ID")
 *     database {
 *         flywayMigrations.from("src/main/resources/db/migration")
 *     }
 * }
 * </pre>
 */
public abstract class LxrinQlExtension {

    private final Database database;

    /** Creates the extension with defaults. */
    @Inject
    public LxrinQlExtension(ObjectFactory objects) {
        this.database = objects.newInstance(Database.class);
        getSchemas().convention(java.util.List.of("public"));
        getDefaultSchema().convention("public");
        getSingularize().convention(true);
        getGenerateEntities().convention(true);
        getGenerateRepositories().convention(true);
        getGenerateJavadoc().convention(true);
        getAddCoreDependency().convention(true);
        database.getImage().convention("postgres:17-alpine");
    }

    /** The package of the generated code (required). */
    public abstract Property<String> getPackageName();

    /** The schemas to read (default {@code public}). */
    public abstract ListProperty<String> getSchemas();

    /** The schema referenced without name (default {@code public}). */
    public abstract Property<String> getDefaultSchema();

    /** Regular expressions of tables to generate (default all). */
    public abstract ListProperty<String> getIncludes();

    /** Regular expressions of tables to skip (Flyway's history table is always skipped). */
    public abstract ListProperty<String> getExcludes();

    /** Table name prefixes stripped for class names, e.g. {@code app_}. */
    public abstract ListProperty<String> getStripTablePrefixes();

    /** Whether entity names are singular (default {@code true}). */
    public abstract Property<Boolean> getSingularize();

    /** Entity name overrides: table → name. */
    public abstract MapProperty<String, String> getEntityNames();

    /** Table constant overrides: table → constant. */
    public abstract MapProperty<String, String> getTableConstants();

    /** Foreign key constant overrides: constraint (or {@code table.constraint}) → constant. */
    public abstract MapProperty<String, String> getForeignKeyNames();

    /** PostgreSQL enums mapped to existing Java enums: type → class. */
    public abstract MapProperty<String, String> getEnumMappings();

    /** Forced types as {@code tables|columns|sqlTypes|javaType|dataType}; use {@link #forcedType}. */
    public abstract ListProperty<String> getForcedTypes();

    /** Whether entities are generated (default {@code true}). */
    public abstract Property<Boolean> getGenerateEntities();

    /** Whether repositories are generated (default {@code true}). */
    public abstract Property<Boolean> getGenerateRepositories();

    /** Whether generated classes contain Javadoc (default {@code true}); {@code false} leaves only the generated-file header. */
    public abstract Property<Boolean> getGenerateJavadoc();

    /** Whether the repository stubs contain Javadoc (default: {@link #getGenerateJavadoc()}). */
    public abstract Property<Boolean> getStubJavadoc();

    /** Where hand-written repository subclasses are created once (default {@code src/main/java}). */
    public abstract DirectoryProperty getRepositoryStubs();

    /** Whether {@code ch.lxrin:lxrin-ql-core} of the plugin's version is added to {@code implementation} (default {@code true}). */
    public abstract Property<Boolean> getAddCoreDependency();

    /** Returns the database settings. */
    @Nested
    public Database getDatabase() {
        return database;
    }

    /** Configures the database. */
    public void database(Action<? super Database> action) {
        action.execute(database);
    }

    /**
     * Maps matching columns to a custom type.
     *
     * @param tables   regular expression for table names
     * @param columns  regular expression for column names
     * @param sqlTypes regular expression for SQL type names
     * @param javaType fully qualified Java type
     * @param dataType Java expression of its DataType, e.g. {@code com.example.Types.USER_ID}
     */
    public void forcedType(String tables, String columns, String sqlTypes, String javaType, String dataType) {
        getForcedTypes().add(String.join("|", tables, columns, sqlTypes, javaType, dataType));
    }

    /** The database to read the schema from. */
    public abstract static class Database {

        /** Docker image of the disposable PostgreSQL (default {@code postgres:17-alpine}). */
        public abstract Property<String> getImage();

        /** Directories with Flyway migrations applied to the disposable database. */
        public abstract ConfigurableFileCollection getFlywayMigrations();

        /** Plain SQL scripts or directories applied after Flyway. */
        public abstract ConfigurableFileCollection getSqlScripts();

        /** JDBC URL of an existing database; if set, no container is started and nothing is migrated. */
        public abstract Property<String> getJdbcUrl();

        /** User of the existing database. */
        public abstract Property<String> getUser();

        /** Password of the existing database. */
        public abstract Property<String> getPassword();
    }
}
