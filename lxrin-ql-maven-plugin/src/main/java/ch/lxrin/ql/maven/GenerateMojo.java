package ch.lxrin.ql.maven;

import ch.lxrin.ql.codegen.CodeGenerator;
import ch.lxrin.ql.codegen.CodegenConfig;
import ch.lxrin.ql.codegen.DatabaseProvisioner;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Goal {@code generate}: generates LxrinQL tables, rows, entities and
 * repositories from a PostgreSQL schema. Bound to {@code generate-sources}
 * by default; the output is added as a compile source root.
 *
 * <pre>
 * &lt;plugin&gt;
 *   &lt;groupId&gt;ch.lxrin&lt;/groupId&gt;
 *   &lt;artifactId&gt;lxrin-ql-maven-plugin&lt;/artifactId&gt;
 *   &lt;version&gt;3.1.0&lt;/version&gt;
 *   &lt;executions&gt;&lt;execution&gt;&lt;goals&gt;&lt;goal&gt;generate&lt;/goal&gt;&lt;/goals&gt;&lt;/execution&gt;&lt;/executions&gt;
 *   &lt;configuration&gt;
 *     &lt;packageName&gt;com.example.db&lt;/packageName&gt;
 *     &lt;flywayMigrations&gt;&lt;dir&gt;src/main/resources/db/migration&lt;/dir&gt;&lt;/flywayMigrations&gt;
 *   &lt;/configuration&gt;
 * &lt;/plugin&gt;
 * </pre>
 */
public class GenerateMojo extends AbstractMojo {

    /** The Maven project (injected). */
    private MavenProject project;
    /** Package of the generated code. */
    private String packageName;
    /** Directory of the generated sources. */
    private File outputDirectory;
    /** Directory of the generated resources. */
    private File resourcesDirectory;
    /** Directory where repository subclasses are created once. */
    private File repositoryStubDirectory;
    /** Schemas to read. */
    private List<String> schemas;
    /** Schema referenced without name. */
    private String defaultSchema;
    /** Tables to include (regular expressions). */
    private List<String> includes;
    /** Tables to exclude (regular expressions). */
    private List<String> excludes;
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
    /** Whether to generate entities. */
    private boolean generateEntities = true;
    /** Whether to generate repositories. */
    private boolean generateRepositories = true;
    /** Whether generated classes contain Javadoc. */
    private boolean generateJavadoc = true;
    /** Whether repository stubs contain Javadoc; defaults to generateJavadoc. */
    private Boolean stubJavadoc;
    /** Docker image of the disposable database. */
    private String image;
    /** Flyway migration directories. */
    private List<File> flywayMigrations;
    /** SQL scripts or directories. */
    private List<File> sqlScripts;
    /** JDBC URL of an existing database. */
    private String jdbcUrl;
    /** User of the existing database. */
    private String user;
    /** Password of the existing database. */
    private String password;
    /** Skips the goal. */
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("LxrinQL code generation skipped");
            return;
        }
        try {
            CodegenConfig config = config();
            try (DatabaseProvisioner db = provision(); Connection con = db.connect()) {
                CodeGenerator.Result result = CodeGenerator.generate(con, config);
                getLog().info("LxrinQL: generated " + result.generated().size() + " files, created " + result.stubs().size()
                        + " repository stubs");
            }
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("LxrinQL code generation failed: " + e.getMessage(), e);
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
        CodegenConfig config = new CodegenConfig()
                .packageName(packageName)
                .outputDirectory(outputDirectory.toPath())
                .resourcesDirectory(resourcesDirectory.toPath())
                .singularize(singularize)
                .generateEntities(generateEntities)
                .generateRepositories(generateRepositories)
                .generateJavadoc(generateJavadoc)
                .stubJavadoc(stubJavadoc);
        if (repositoryStubDirectory != null) config.repositoryStubDirectory(repositoryStubDirectory.toPath());
        if (schemas != null && !schemas.isEmpty()) config.schemas(schemas);
        if (defaultSchema != null) config.defaultSchema(defaultSchema);
        if (includes != null) includes.forEach(config::include);
        if (excludes != null) excludes.forEach(config::exclude);
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

    private DatabaseProvisioner provision() {
        if (jdbcUrl != null && !jdbcUrl.isBlank()) return DatabaseProvisioner.jdbc(jdbcUrl, user, password);
        return DatabaseProvisioner.testcontainer(image == null ? "postgres:17-alpine" : image, paths(flywayMigrations), paths(sqlScripts));
    }

    private static List<Path> paths(List<File> files) {
        List<Path> result = new ArrayList<>();
        if (files != null) for (File f : files) result.add(f.toPath());
        return result;
    }
}
