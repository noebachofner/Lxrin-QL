package ch.lxrin.ql.gradle;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class LxrinQlPluginTest {

    @Test
    void snapshotTasksShareTheDatabaseSettings() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply("ch.lxrin.ql.codegen");
        LxrinQlExtension ext = project.getExtensions().getByType(LxrinQlExtension.class);
        ext.getPackageName().set("com.example.db");
        ext.getExcludes().add("tmp_.*");
        ext.getDatabase().getFlywayMigrations().from("src/main/resources/db/migration");

        GenerateLxrinQlTask generate = (GenerateLxrinQlTask) project.getTasks().getByName(LxrinQlPlugin.TASK_NAME);
        LxrinQlSnapshotTask snapshot = (LxrinQlSnapshotTask) project.getTasks().getByName(LxrinQlPlugin.SNAPSHOT_TASK_NAME);
        LxrinQlCheckSnapshotTask check = (LxrinQlCheckSnapshotTask) project.getTasks().getByName(LxrinQlPlugin.CHECK_SNAPSHOT_TASK_NAME);
        File expected = project.file("src/main/lxrinql/schema.json");
        assertEquals("auto", generate.getSchemaSource().get());
        assertEquals(expected, generate.getSnapshotFile().get().getAsFile());
        assertEquals(expected, snapshot.getSnapshotFile().get().getAsFile());
        assertEquals(expected, check.getSnapshotFile().get().getAsFile());
        assertEquals(java.util.Set.of(expected), check.getSnapshotInput().getFiles());
        for (AbstractLxrinQlTask t : java.util.List.of(generate, snapshot, check)) {
            assertEquals(java.util.List.of("tmp_.*"), t.getExcludes().get());
            assertEquals(java.util.Set.of(project.file("src/main/resources/db/migration")), t.getFlywayMigrations().getFiles());
            assertEquals("postgres:17-alpine", t.getImage().get());
        }
        assertEquals("verification", check.getGroup());

        ext.getSchemaSource().set("snapshot");
        ext.getSnapshotFile().set(project.file("schema/lxrinql.json"));
        assertEquals("snapshot", generate.getSchemaSource().get());
        assertEquals(project.file("schema/lxrinql.json"), snapshot.getSnapshotFile().get().getAsFile());
    }

    @Test
    void javadocOptionsAreWired() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply("ch.lxrin.ql.codegen");
        LxrinQlExtension ext = project.getExtensions().getByType(LxrinQlExtension.class);
        ext.getPackageName().set("com.example.db");
        ext.getGenerateJavadoc().set(false);
        ext.getStubJavadoc().set(true);
        GenerateLxrinQlTask task = (GenerateLxrinQlTask) project.getTasks().getByName(LxrinQlPlugin.TASK_NAME);
        assertFalse(task.getGenerateJavadoc().get());
        assertTrue(task.getStubJavadoc().get());
    }

    @Test
    void registersTaskExtensionAndSourceDirectories() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply("ch.lxrin.ql.codegen");
        LxrinQlExtension ext = project.getExtensions().getByType(LxrinQlExtension.class);
        ext.getPackageName().set("com.example.db");
        ext.forcedType("t", "c", "uuid", "com.x.Id", "com.x.Types.ID");
        ext.getForeignKeyNames().put("app_user_created_by_fkey", "FK_CREATOR");
        ((ProjectInternal) project).evaluate();

        GenerateLxrinQlTask task = (GenerateLxrinQlTask) project.getTasks().getByName(LxrinQlPlugin.TASK_NAME);
        assertEquals("com.example.db", task.getPackageName().get());
        assertEquals(java.util.List.of("public"), task.getSchemas().get());
        assertEquals("postgres:17-alpine", task.getImage().get());
        assertEquals(java.util.List.of("t|c|uuid|com.x.Id|com.x.Types.ID"), task.getForcedTypes().get());
        assertEquals(java.util.Map.of("app_user_created_by_fkey", "FK_CREATOR"), task.getForeignKeyNames().get());
        assertTrue(task.getGenerateJavadoc().get());
        assertFalse(task.getStubJavadoc().isPresent(), "stubs follow generateJavadoc unless set");
        assertTrue(task.getRepositoryStubs().get().getAsFile().getPath().endsWith("src" + File.separator + "main" + File.separator + "java"));

        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        assertTrue(sourceSets.getByName("main").getJava().getSrcDirs().stream()
                .anyMatch(d -> d.getPath().contains("generated" + File.separator + "sources" + File.separator + "lxrinql")));
        Dependency core = project.getConfigurations().getByName("implementation").getDependencies().iterator().next();
        assertEquals("lxrin-ql-core", core.getName());
        assertEquals(LxrinQlPlugin.version(), core.getVersion());
        assertFalse(core.getVersion().contains("@"));
    }
}
