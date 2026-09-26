package ch.lxrin.ql.gradle;

import ch.lxrin.ql.codegen.CodeGenerator;
import ch.lxrin.ql.codegen.CodegenConfig;
import ch.lxrin.ql.codegen.SchemaSources;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
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

import java.nio.file.Path;

/**
 * Generates the LxrinQL sources from the database or from the schema snapshot
 * ({@link #getSchemaSource()}). Up to date (and cacheable) as long as the migrations,
 * the snapshot and the configuration do not change.
 */
@CacheableTask
public abstract class GenerateLxrinQlTask extends AbstractLxrinQlTask {

    /** The package of the generated code. */
    @Input
    public abstract Property<String> getPackageName();





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

    /** Whether generated classes contain Javadoc. */
    @Input
    public abstract Property<Boolean> getGenerateJavadoc();

    /** Whether repository stubs contain Javadoc; defaults to {@link #getGenerateJavadoc()}. */
    @Input
    @Optional
    public abstract Property<Boolean> getStubJavadoc();







    /** {@code auto} (default), {@code database} or {@code snapshot}. */
    @Input
    public abstract Property<String> getSchemaSource();

    /** The schema snapshot used without Docker. */
    @Internal
    public abstract RegularFileProperty getSnapshotFile();

    /** The snapshot file as an input; it may be missing. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSnapshotInput();

    /** Generated sources. */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /** Generated resources (repository index). */
    @OutputDirectory
    public abstract DirectoryProperty getResourcesDirectory();

    /** Where repository subclasses are created once; not an output, the files belong to the developer. */
    @Internal
    public abstract DirectoryProperty getRepositoryStubs();

    /** Separates the parts of a forced type written by {@link LxrinQlExtension#forcedType}; patterns may contain {@code |}. */
    static final String FORCED_TYPE_SEPARATOR = "\t";

    static CodegenConfig.ForcedType forcedType(String spec) {
        String[] p = spec.contains(FORCED_TYPE_SEPARATOR) ? spec.split(FORCED_TYPE_SEPARATOR, -1) : spec.split("\\|", -1);
        if (p.length != 5) {
            throw new GradleException("invalid forced type: " + spec.replace(FORCED_TYPE_SEPARATOR, ", ")
                    + "; use forcedType(tables, columns, sqlTypes, javaType, dataType)");
        }
        return new CodegenConfig.ForcedType(p[0], p[1], p[2], p[3], p[4]);
    }

    /** Runs the generator. */
    @TaskAction
    public void generate() {
        CodegenConfig config = selection()
                .packageName(getPackageName().get())
                .singularize(getSingularize().get())
                .generateEntities(getGenerateEntities().get())
                .generateRepositories(getGenerateRepositories().get())
                .generateJavadoc(getGenerateJavadoc().get())
                .stubJavadoc(getStubJavadoc().getOrNull())
                .outputDirectory(getOutputDirectory().get().getAsFile().toPath())
                .resourcesDirectory(getResourcesDirectory().get().getAsFile().toPath());
        if (getRepositoryStubs().isPresent()) config.repositoryStubDirectory(getRepositoryStubs().get().getAsFile().toPath());
        getStripTablePrefixes().get().forEach(config::stripTablePrefix);
        getEntityNames().get().forEach(config::entityName);
        getTableConstants().get().forEach(config::tableConstant);
        getForeignKeyNames().get().forEach(config::foreignKeyName);
        getEnumMappings().get().forEach(config::enumMapping);
        for (String spec : getForcedTypes().get()) config.forcedType(forcedType(spec));
        try {
            Path snapshot = getSnapshotFile().isPresent() ? getSnapshotFile().get().getAsFile().toPath() : null;
            CodeGenerator.Result result = new SchemaSources().generate(config, SchemaSources.Source.parse(getSchemaSource().get()),
                    database(), snapshot, log());
            getLogger().lifecycle("LxrinQL: generated {} files, created {} repository stubs", result.generated().size(),
                    result.stubs().size());
        } catch (Exception e) {
            throw new GradleException("LxrinQL code generation failed: " + e.getMessage(), e);
        }
    }
}
