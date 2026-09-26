package ch.lxrin.ql.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * The {@code ch.lxrin.ql.codegen} plugin: registers {@code generateLxrinQl},
 * {@code lxrinQlSnapshot} and {@code lxrinQlCheckSnapshot}, adds the output of
 * {@code generateLxrinQl} to the main source set (so {@code compileJava} depends on
 * it) and adds {@code lxrin-ql-core} to {@code implementation}.
 */
public class LxrinQlPlugin implements Plugin<Project> {

    /** The task name. */
    public static final String TASK_NAME = "generateLxrinQl";

    /** The name of the task that writes the schema snapshot. */
    public static final String SNAPSHOT_TASK_NAME = "lxrinQlSnapshot";

    /** The name of the task that checks the schema snapshot. */
    public static final String CHECK_SNAPSHOT_TASK_NAME = "lxrinQlCheckSnapshot";

    @Override
    public void apply(Project project) {
        LxrinQlExtension ext = project.getExtensions().create("lxrinQl", LxrinQlExtension.class);
        ext.getRepositoryStubs().convention(project.getLayout().getProjectDirectory().dir("src/main/java"));

        ext.getSnapshotFile().convention(project.getLayout().getProjectDirectory().file("src/main/lxrinql/schema.json"));

        TaskProvider<GenerateLxrinQlTask> task = project.getTasks().register(TASK_NAME, GenerateLxrinQlTask.class, t -> {
            t.setGroup("build");
            t.setDescription("Generates LxrinQL tables, rows, entities and repositories from the database schema or the snapshot.");
            common(t, ext);
            t.getPackageName().set(ext.getPackageName());
            t.getStripTablePrefixes().set(ext.getStripTablePrefixes());
            t.getSingularize().set(ext.getSingularize());
            t.getEntityNames().set(ext.getEntityNames());
            t.getTableConstants().set(ext.getTableConstants());
            t.getForeignKeyNames().set(ext.getForeignKeyNames());
            t.getEnumMappings().set(ext.getEnumMappings());
            t.getForcedTypes().set(ext.getForcedTypes());
            t.getGenerateEntities().set(ext.getGenerateEntities());
            t.getGenerateRepositories().set(ext.getGenerateRepositories());
            t.getGenerateJavadoc().set(ext.getGenerateJavadoc());
            t.getStubJavadoc().set(ext.getStubJavadoc());
            t.getSchemaSource().set(ext.getSchemaSource());
            t.getSnapshotFile().set(ext.getSnapshotFile());
            t.getSnapshotInput().from(ext.getSnapshotFile());
            t.getRepositoryStubs().set(ext.getRepositoryStubs());
            t.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("generated/sources/lxrinql/main/java"));
            t.getResourcesDirectory().set(project.getLayout().getBuildDirectory().dir("generated/resources/lxrinql/main"));
        });

        project.getTasks().register(SNAPSHOT_TASK_NAME, LxrinQlSnapshotTask.class, t -> {
            t.setGroup("build");
            t.setDescription("Writes the LxrinQL schema snapshot (needs Docker or a JDBC URL); commit it to generate without Docker.");
            common(t, ext);
            t.getSnapshotFile().set(ext.getSnapshotFile());
        });

        project.getTasks().register(CHECK_SNAPSHOT_TASK_NAME, LxrinQlCheckSnapshotTask.class, t -> {
            t.setGroup("verification");
            t.setDescription("Fails if the LxrinQL schema snapshot does not match the migrations.");
            common(t, ext);
            t.getSnapshotFile().set(ext.getSnapshotFile());
            t.getSnapshotInput().from(ext.getSnapshotFile());
        });

        project.getPlugins().withType(JavaPlugin.class, java -> {
            SourceSet main = project.getExtensions().getByType(SourceSetContainer.class).getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            main.getJava().srcDir(task.flatMap(GenerateLxrinQlTask::getOutputDirectory));
            main.getResources().srcDir(task.flatMap(GenerateLxrinQlTask::getResourcesDirectory));
            project.afterEvaluate(p -> {
                if (ext.getAddCoreDependency().get()) {
                    p.getDependencies().add(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, "ch.lxrin:lxrin-ql-core:" + version());
                }
            });
        });
    }

    private static void common(AbstractLxrinQlTask t, LxrinQlExtension ext) {
        t.getSchemas().set(ext.getSchemas());
        t.getDefaultSchema().set(ext.getDefaultSchema());
        t.getIncludes().set(ext.getIncludes());
        t.getExcludes().set(ext.getExcludes());
        t.getImage().set(ext.getDatabase().getImage());
        t.getFlywayMigrations().from(ext.getDatabase().getFlywayMigrations());
        t.getSqlScripts().from(ext.getDatabase().getSqlScripts());
        t.getJdbcUrl().set(ext.getDatabase().getJdbcUrl());
        t.getUser().set(ext.getDatabase().getUser());
        t.getPassword().set(ext.getDatabase().getPassword());
    }

    static String version() {
        try (InputStream in = LxrinQlPlugin.class.getResourceAsStream("/lxrin-ql-gradle-plugin.properties")) {
            Properties p = new Properties();
            p.load(in);
            return p.getProperty("version");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
