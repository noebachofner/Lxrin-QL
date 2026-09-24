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
        Field f = GenerateMojo.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void configurationIsTranslated() throws Exception {
        GenerateMojo mojo = new GenerateMojo();
        set(mojo, "packageName", "com.example.db");
        set(mojo, "outputDirectory", new File("target/gen"));
        set(mojo, "resourcesDirectory", new File("target/res"));
        set(mojo, "schemas", List.of("public", "app"));
        set(mojo, "stripTablePrefixes", List.of("app_"));
        set(mojo, "tableConstants", Map.of("app_user", "USERS"));
        ForcedType f = new ForcedType();
        f.columns = "id";
        f.javaType = "com.x.UserId";
        f.dataType = "com.x.Types.USER_ID";
        set(mojo, "forcedTypes", List.of(f));
        assertNotNull(mojo.config());
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
    void descriptorDeclaresEveryField() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/META-INF/maven/plugin.xml")) {
            var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            var names = doc.getElementsByTagName("name");
            java.util.Set<String> params = new java.util.HashSet<>();
            for (int i = 1; i < names.getLength(); i++) params.add(names.item(i).getTextContent());
            for (Field field : GenerateMojo.class.getDeclaredFields()) {
                assertTrue(params.contains(field.getName()), "descriptor lacks parameter " + field.getName());
            }
            assertEquals("generate-sources", doc.getElementsByTagName("phase").item(0).getTextContent());
            assertFalse(doc.getElementsByTagName("version").item(0).getTextContent().contains("@"));
        }
    }
}
