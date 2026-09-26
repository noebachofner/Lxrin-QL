package ch.lxrin.ql.gradle;

import ch.lxrin.ql.codegen.CodegenConfig;
import ch.lxrin.ql.codegen.SchemaSources;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The settings every LxrinQL task needs: which tables to read, and from which database. */
@DisableCachingByDefault(because = "Base class of the LxrinQL tasks")
public abstract class AbstractLxrinQlTask extends DefaultTask {

    /** Schemas to read. */
    @Input
    public abstract ListProperty<String> getSchemas();

    /** Schema referenced without name. */
    @Input
    public abstract Property<String> getDefaultSchema();

    /** Tables to include. */
    @Input
    public abstract ListProperty<String> getIncludes();

    /** Tables to exclude. */
    @Input
    public abstract ListProperty<String> getExcludes();

    /** Docker image. */
    @Input
    public abstract Property<String> getImage();

    /** Flyway migrations. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getFlywayMigrations();

    /** SQL scripts. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSqlScripts();

    /** Existing database URL. */
    @Input
    @Optional
    public abstract Property<String> getJdbcUrl();

    /** Existing database user. */
    @Input
    @Optional
    public abstract Property<String> getUser();

    /** Existing database password (not tracked as input). */
    @Internal
    public abstract Property<String> getPassword();

    /** Returns a configuration with the table selection. */
    protected CodegenConfig selection() {
        CodegenConfig config = new CodegenConfig()
                .schemas(getSchemas().get())
                .defaultSchema(getDefaultSchema().get());
        getIncludes().get().forEach(config::include);
        getExcludes().get().forEach(config::exclude);
        return config;
    }

    /** Returns the database settings. */
    protected SchemaSources.Database database() {
        return new SchemaSources.Database(getImage().get(), paths(getFlywayMigrations()), paths(getSqlScripts()),
                getJdbcUrl().getOrNull(), getUser().getOrNull(), getPassword().getOrNull());
    }

    /** Returns a log that writes to the Gradle logger. */
    protected SchemaSources.Log log() {
        return new SchemaSources.Log() {
            @Override
            public void info(String message) {
                getLogger().lifecycle(message);
            }

            @Override
            public void warn(String message) {
                getLogger().warn(message);
            }
        };
    }

    private static List<Path> paths(ConfigurableFileCollection files) {
        List<Path> result = new ArrayList<>();
        for (File f : files.getFiles()) result.add(f.toPath());
        return result;
    }
}
