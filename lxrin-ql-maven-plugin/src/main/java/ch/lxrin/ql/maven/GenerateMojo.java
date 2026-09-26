package ch.lxrin.ql.maven;

import ch.lxrin.ql.codegen.CodeGenerator;
import ch.lxrin.ql.codegen.CodegenConfig;
import ch.lxrin.ql.codegen.SchemaSources;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Goal {@code generate}: generates LxrinQL tables, rows, entities and
 * repositories from a PostgreSQL schema, or from the schema snapshot without
 * Docker ({@code schemaSource}). Bound to {@code generate-sources} by default; the
 * output is added as a compile source root.
 *
 * <pre>
 * &lt;plugin&gt;
 *   &lt;groupId&gt;ch.lxrin&lt;/groupId&gt;
 *   &lt;artifactId&gt;lxrin-ql-maven-plugin&lt;/artifactId&gt;
 *   &lt;version&gt;3.2.0&lt;/version&gt;
 *   &lt;executions&gt;&lt;execution&gt;&lt;goals&gt;&lt;goal&gt;generate&lt;/goal&gt;&lt;/goals&gt;&lt;/execution&gt;&lt;/executions&gt;
 *   &lt;configuration&gt;
 *     &lt;packageName&gt;com.example.db&lt;/packageName&gt;
 *     &lt;flywayMigrations&gt;&lt;dir&gt;src/main/resources/db/migration&lt;/dir&gt;&lt;/flywayMigrations&gt;
 *   &lt;/configuration&gt;
 * &lt;/plugin&gt;
 * </pre>
 */
public class GenerateMojo extends AbstractLxrinQlMojo {

    /** Package of the generated code. */
    private String packageName;
    /** Directory of the generated sources. */
    private File outputDirectory;
    /** Directory of the generated resources. */
    private File resourcesDirectory;
    /** Directory where repository subclasses are created once. */
    private File repositoryStubDirectory;
    /** Table prefixes to strip. */
    private List<String> stripTablePrefixes;
    /** Whether to singularise entity names. */
    private boolean singularize = true;
    /** Entity name overrides. */
    private Map<String, String> entityNames;
    /** Table constant overrides. */
    private Map<String, String> tableConstants;
    /** Foreign key constant overrides. */
    private Map<String, String> foreignKeyNames;
    /** Enum mappings. */
    private Map<String, String> enumMappings;
    /** Forced types. */
    private List<ForcedType> forcedTypes;
    /** Where the schema is read from: auto, database or snapshot. */
    private String schemaSource;
    /** Whether to generate entities. */
    private boolean generateEntities = true;
    /** Whether to generate repositories. */
    private boolean generateRepositories = true;
    /** Whether generated classes contain Javadoc. */
    private boolean generateJavadoc = true;
    /** Whether repository stubs contain Javadoc; defaults to generateJavadoc. */
    private Boolean stubJavadoc;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("LxrinQL code generation skipped");
            return;
        }
        try {
            CodegenConfig config = config();
            CodeGenerator.Result result = new SchemaSources().generate(config, SchemaSources.Source.parse(schemaSource), database(),
                    snapshot(), log());
            getLog().info("LxrinQL: generated " + result.generated().size() + " files, created " + result.stubs().size()
                    + " repository stubs");
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw failure("LxrinQL code generation", e);
        }
        if (project != null) {
            project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
            Resource resource = new Resource();
            resource.setDirectory(resourcesDirectory.getAbsolutePath());
            project.addResource(resource);
        }
    }

    CodegenConfig config() throws MojoExecutionException {
        if (packageName == null) throw new MojoExecutionException("packageName is required");
        CodegenConfig config = selection()
                .packageName(packageName)
                .outputDirectory(outputDirectory.toPath())
                .resourcesDirectory(resourcesDirectory.toPath())
                .singularize(singularize)
                .generateEntities(generateEntities)
                .generateRepositories(generateRepositories)
                .generateJavadoc(generateJavadoc)
                .stubJavadoc(stubJavadoc);
        if (repositoryStubDirectory != null) config.repositoryStubDirectory(repositoryStubDirectory.toPath());
        if (stripTablePrefixes != null) stripTablePrefixes.forEach(config::stripTablePrefix);
        if (entityNames != null) entityNames.forEach(config::entityName);
        if (tableConstants != null) tableConstants.forEach(config::tableConstant);
        if (foreignKeyNames != null) foreignKeyNames.forEach(config::foreignKeyName);
        if (enumMappings != null) enumMappings.forEach(config::enumMapping);
        if (forcedTypes != null) {
            for (ForcedType f : forcedTypes) {
                config.forcedType(new CodegenConfig.ForcedType(f.tables, f.columns, f.sqlTypes, f.javaType, f.dataType));
            }
        }
        return config.validate();
    }

}
