package ch.lxrin.ql.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;

class GenerateMojoTest {

    private static void set(Object target, String name, Object value) throws Exception {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                // look in the superclass
            }
        }
        throw new NoSuchFieldException(name);
    }

    /** Returns the parameter fields of a mojo, including those of its LxrinQL base class. */
    private static java.util.Set<String> fields(Class<?> mojo) {
        java.util.Set<String> names = new java.util.TreeSet<>();
        for (Class<?> c = mojo; c != org.apache.maven.plugin.AbstractMojo.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) names.add(f.getName());
        }
        return names;
    }

    @Test
    void configurationIsTranslated() throws Exception {
        GenerateMojo mojo = new GenerateMojo();
        set(mojo, "packageName", "com.example.db");
        set(mojo, "outputDirectory", new File("target/gen"));
        set(mojo, "resourcesDirectory", new File("target/res"));
        set(mojo, "schemas", List.of("public", "app"));
        set(mojo, "schemaSource", "snapshot");
        set(mojo, "stripTablePrefixes", List.of("app_"));
        set(mojo, "tableConstants", Map.of("app_user", "USERS"));
        set(mojo, "foreignKeyNames", Map.of("app_user_created_by_fkey", "FK_CREATOR"));
        ForcedType f = new ForcedType();
        f.columns = "id";
        f.javaType = "com.x.UserId";
        f.dataType = "com.x.Types.USER_ID";
        set(mojo, "forcedTypes", List.of(f));
        assertNotNull(mojo.config());
        set(mojo, "generateJavadoc", false);
        set(mojo, "stubJavadoc", Boolean.TRUE);
        assertNotNull(mojo.config());
        set(mojo, "foreignKeyNames", Map.of("app_user_created_by_fkey", "not valid"));
        assertThrows(IllegalArgumentException.class, mojo::config);
        set(mojo, "foreignKeyNames", null);
        set(mojo, "packageName", null);
        assertThrows(MojoExecutionException.class, mojo::config);
    }

    @Test
    void skipDoesNothing() throws Exception {
        GenerateMojo mojo = new GenerateMojo();
        set(mojo, "skip", true);
        mojo.execute();
    }

    @Test
    void descriptorDeclaresEveryFieldOfEveryGoal() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/META-INF/maven/plugin.xml")) {
            var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            var mojos = doc.getElementsByTagName("mojo");
            Map<String, Class<?>> goals = Map.of("generate", GenerateMojo.class, "snapshot", SnapshotMojo.class,
                    "check-snapshot", CheckSnapshotMojo.class);
            assertEquals(goals.size(), mojos.getLength());
            for (int m = 0; m < mojos.getLength(); m++) {
                var mojo = (org.w3c.dom.Element) mojos.item(m);
                String goal = mojo.getElementsByTagName("goal").item(0).getTextContent();
                Class<?> type = goals.get(goal);
                assertEquals(type.getName(), mojo.getElementsByTagName("implementation").item(0).getTextContent());
                java.util.Set<String> params = new java.util.TreeSet<>();
                var parameters = mojo.getElementsByTagName("parameter");
                for (int i = 0; i < parameters.getLength(); i++) {
                    params.add(((org.w3c.dom.Element) parameters.item(i)).getElementsByTagName("name").item(0).getTextContent());
                }
                assertEquals(fields(type), params, "parameters of goal " + goal);
                var configuration = (org.w3c.dom.Element) mojo.getElementsByTagName("configuration").item(0);
                for (String p : params) assertEquals(1, configuration.getElementsByTagName(p).getLength(), goal + " configures " + p);
            }
            assertEquals("generate-sources", doc.getElementsByTagName("phase").item(0).getTextContent());
            assertFalse(doc.getElementsByTagName("version").item(0).getTextContent().contains("@"));
        }
    }

    @Test
    void snapshotGoalsHonourSkip() throws Exception {
        SnapshotMojo snapshot = new SnapshotMojo();
        set(snapshot, "skip", true);
        snapshot.execute();
        CheckSnapshotMojo check = new CheckSnapshotMojo();
        set(check, "skip", true);
        check.execute();
    }

    @Test
    void checkSnapshotFailsForAMissingSnapshot(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        CheckSnapshotMojo check = new CheckSnapshotMojo();
        set(check, "snapshotFile", dir.resolve("none.json").toFile());
        assertThrows(org.apache.maven.plugin.MojoFailureException.class, check::execute);
    }
}
