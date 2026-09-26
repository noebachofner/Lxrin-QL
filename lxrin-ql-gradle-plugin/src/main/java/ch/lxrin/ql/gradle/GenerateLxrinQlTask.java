package ch.lxrin.ql.gradle;

import ch.lxrin.ql.codegen.CodeGenerator;
import ch.lxrin.ql.codegen.CodegenConfig;
import ch.lxrin.ql.codegen.DatabaseProvisioner;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates the LxrinQL sources. Up to date (and cacheable) as long as the
 * migrations and the configuration do not change.
 */
@CacheableTask
public abstract class GenerateLxrinQlTask extends DefaultTask {

    /** The package of the generated code. */
    @Input
    public abstract Property<String> getPackageName();

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

    /** Prefixes to strip. */
    @Input
    public abstract ListProperty<String> getStripTablePrefixes();

    /** Whether to singularise. */
    @Input
    public abstract Property<Boolean> getSingularize();

    /** Entity name overrides. */
    @Input
    public abstract MapProperty<String, String> getEntityNames();

    /** Table constant overrides. */
    @Input
    public abstract MapProperty<String, String> getTableConstants();

    /** Foreign key constant overrides. */
    @Input
    public abstract MapProperty<String, String> getForeignKeyNames();

    /** Enum mappings. */
    @Input
    public abstract MapProperty<String, String> getEnumMappings();

    /** Forced types. */
    @Input
    public abstract ListProperty<String> getForcedTypes();

    /** Whether to generate entities. */
    @Input
    public abstract Property<Boolean> getGenerateEntities();

    /** Whether to generate repositories. */
    @Input
    public abstract Property<Boolean> getGenerateRepositories();

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

    /** Generated sources. */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /** Generated resources (repository index). */
    @OutputDirectory
    public abstract DirectoryProperty getResourcesDirectory();

    /** Where repository subclasses are created once; not an output, the files belong to the developer. */
    @Internal
    public abstract DirectoryProperty getRepositoryStubs();

    /** Runs the generator. */
    @TaskAction
    public void generate() {
        CodegenConfig config = new CodegenConfig()
                .packageName(getPackageName().get())
                .schemas(getSchemas().get())
                .defaultSchema(getDefaultSchema().get())
                .singularize(getSingularize().get())
                .generateEntities(getGenerateEntities().get())
                .generateRepositories(getGenerateRepositories().get())
                .outputDirectory(getOutputDirectory().get().getAsFile().toPath())
                .resourcesDirectory(getResourcesDirectory().get().getAsFile().toPath());
        if (getRepositoryStubs().isPresent()) config.repositoryStubDirectory(getRepositoryStubs().get().getAsFile().toPath());
        getIncludes().get().forEach(config::include);
        getExcludes().get().forEach(config::exclude);
        getStripTablePrefixes().get().forEach(config::stripTablePrefix);
        getEntityNames().get().forEach(config::entityName);
        getTableConstants().get().forEach(config::tableConstant);
        getForeignKeyNames().get().forEach(config::foreignKeyName);
        getEnumMappings().get().forEach(config::enumMapping);
        for (String spec : getForcedTypes().get()) {
            String[] p = spec.split("\\|");
            if (p.length != 5) throw new GradleException("invalid forced type: " + spec);
            config.forcedType(new CodegenConfig.ForcedType(p[0], p[1], p[2], p[3], p[4]));
        }
        try (DatabaseProvisioner db = provision(); Connection con = db.connect()) {
            CodeGenerator.Result result = CodeGenerator.generate(con, config);
            getLogger().lifecycle("LxrinQL: generated {} files, created {} repository stubs", result.generated().size(),
                    result.stubs().size());
        } catch (Exception e) {
            throw new GradleException("LxrinQL code generation failed: " + e.getMessage(), e);
        }
    }

    private DatabaseProvisioner provision() {
        if (getJdbcUrl().isPresent()) {
            return DatabaseProvisioner.jdbc(getJdbcUrl().get(), getUser().getOrNull(), getPassword().getOrNull());
        }
        return DatabaseProvisioner.testcontainer(getImage().get(), paths(getFlywayMigrations()), paths(getSqlScripts()));
    }

    private static List<Path> paths(ConfigurableFileCollection files) {
        List<Path> result = new ArrayList<>();
        for (File f : files.getFiles()) result.add(f.toPath());
        return result;
    }
}
