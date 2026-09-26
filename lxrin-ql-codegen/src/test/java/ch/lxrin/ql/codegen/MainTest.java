package ch.lxrin.ql.codegen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

    @TempDir
    Path dir;

    @Test
    void optionsAndConfigFile() throws Exception {
        Path file = dir.resolve("codegen.properties");
        Files.writeString(file, "package=com.example.db\noutput=" + dir.resolve("out").toString().replace("\\", "/") + "\nschemas=public,app\n");
        Properties p = Main.parse(new String[]{"--config", file.toString(), "--strip-prefixes", "app_", "--table-constants", "app_user=USERS",
                "--forced-types", "app_user|id|uuid|com.x.UserId|com.x.Types.USER_ID", "--enum-mappings", "role=com.x.Role",
                "--entity-names", "x=Y", "--foreign-key-names", "app_user_created_by_fkey=FK_CREATOR,app_user.x_fkey=FK_X", "--singularize", "false", "--exclude", "tmp_.*", "--stubs", "s", "--resources", "r",
                "--default-schema", "app", "--generate-javadoc", "false"});
        CodegenConfig config = Main.configure(p);
        assertEquals("com.example.db", config.packageName());
        assertEquals(java.util.List.of("public", "app"), config.schemas());
        assertEquals("USERS", config.tableConstants().get("app_user"));
        assertEquals("Y", config.entityNames().get("x"));
        assertEquals("FK_CREATOR", config.foreignKeyConstant("app_user", "app_user_created_by_fkey"));
        assertEquals("FK_X", config.foreignKeyConstant("app_user", "x_fkey"));
        assertNull(config.foreignKeyConstant("other", "x_fkey"));
        assertEquals("com.x.Role", config.enumMappings().get("role"));
        assertEquals(1, config.forcedTypes().size());
        assertFalse(config.singularize());
        assertFalse(config.included("tmp_1"));
        assertEquals("app", config.defaultSchema());
        assertFalse(config.generateJavadoc());
        assertFalse(config.stubJavadoc(), "stubs follow generate-javadoc");
        CodegenConfig stubs = Main.configure(Main.parse(new String[]{"--package", "a", "--output", "o", "--stub-javadoc", "true"}));
        assertTrue(stubs.generateJavadoc());
        assertTrue(stubs.stubJavadoc());
        assertThrows(IllegalArgumentException.class, () -> Main.configure(Main.parse(new String[]{"--package", "a", "--output", "o",
                "--generate-javadoc", "no"})));
        assertThrows(IllegalArgumentException.class, () -> Main.parse(new String[]{"--package"}));
        assertThrows(IllegalArgumentException.class, () -> Main.configure(new Properties()));
        assertThrows(IllegalArgumentException.class, () -> Main.configure(Main.parse(new String[]{"--package", "a", "--output", "o",
                "--forced-types", "a|b"})));
    }
}
