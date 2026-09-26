package ch.lxrin.ql.maven;

import ch.lxrin.ql.codegen.CodegenConfig;
import ch.lxrin.ql.codegen.SchemaSources;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The parameters every LxrinQL goal needs: which tables to read, from which database, and the snapshot file. */
public abstract class AbstractLxrinQlMojo extends AbstractMojo {

    /** The Maven project (injected). */
    protected MavenProject project;
    /** Schemas to read. */
    private List<String> schemas;
    /** Schema referenced without name. */
    private String defaultSchema;
    /** Tables to include (regular expressions). */
    private List<String> includes;
    /** Tables to exclude (regular expressions). */
    private List<String> excludes;
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
    /** The schema snapshot file. */
    protected File snapshotFile;
    /** Skips the goal. */
    protected boolean skip;

    /** Returns a configuration with the table selection. */
    protected CodegenConfig selection() {
        CodegenConfig config = new CodegenConfig();
        if (schemas != null && !schemas.isEmpty()) config.schemas(schemas);
        if (defaultSchema != null) config.defaultSchema(defaultSchema);
        if (includes != null) includes.forEach(config::include);
        if (excludes != null) excludes.forEach(config::exclude);
        return config;
    }

    /** Returns the database settings. */
    protected SchemaSources.Database database() {
        return new SchemaSources.Database(image == null ? "postgres:17-alpine" : image, paths(flywayMigrations), paths(sqlScripts),
                jdbcUrl, user, password);
    }

    /** Returns the snapshot file, or {@code null}. */
    protected Path snapshot() {
        return snapshotFile == null ? null : snapshotFile.toPath();
    }

    /** Returns a log that writes to the Maven log. */
    protected SchemaSources.Log log() {
        return new SchemaSources.Log() {
            @Override
            public void info(String message) {
                getLog().info(message);
            }

            @Override
            public void warn(String message) {
                getLog().warn(message);
            }
        };
    }

    /** Wraps a failure. */
    protected static MojoExecutionException failure(String what, Exception e) {
        return new MojoExecutionException(what + " failed: " + e.getMessage(), e);
    }

    private static List<Path> paths(List<File> files) {
        List<Path> result = new ArrayList<>();
        if (files != null) for (File f : files) result.add(f.toPath());
        return result;
    }
}
