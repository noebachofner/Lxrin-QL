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
    void registersTaskExtensionAndSourceDirectories() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply("ch.lxrin.ql.codegen");
        LxrinQlExtension ext = project.getExtensions().getByType(LxrinQlExtension.class);
        ext.getPackageName().set("com.example.db");
        ext.forcedType("t", "c", "uuid", "com.x.Id", "com.x.Types.ID");
        ((ProjectInternal) project).evaluate();

        GenerateLxrinQlTask task = (GenerateLxrinQlTask) project.getTasks().getByName(LxrinQlPlugin.TASK_NAME);
        assertEquals("com.example.db", task.getPackageName().get());
        assertEquals(java.util.List.of("public"), task.getSchemas().get());
        assertEquals("postgres:17-alpine", task.getImage().get());
        assertEquals(java.util.List.of("t|c|uuid|com.x.Id|com.x.Types.ID"), task.getForcedTypes().get());
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
