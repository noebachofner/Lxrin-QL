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
                "--entity-names", "x=Y", "--singularize", "false", "--exclude", "tmp_.*", "--stubs", "s", "--resources", "r",
                "--default-schema", "app"});
        CodegenConfig config = Main.configure(p);
        assertEquals("com.example.db", config.packageName());
        assertEquals(java.util.List.of("public", "app"), config.schemas());
        assertEquals("USERS", config.tableConstants().get("app_user"));
        assertEquals("Y", config.entityNames().get("x"));
        assertEquals("com.x.Role", config.enumMappings().get("role"));
        assertEquals(1, config.forcedTypes().size());
        assertFalse(config.singularize());
        assertFalse(config.included("tmp_1"));
        assertEquals("app", config.defaultSchema());
        assertThrows(IllegalArgumentException.class, () -> Main.parse(new String[]{"--package"}));
        assertThrows(IllegalArgumentException.class, () -> Main.configure(new Properties()));
        assertThrows(IllegalArgumentException.class, () -> Main.configure(Main.parse(new String[]{"--package", "a", "--output", "o",
                "--forced-types", "a|b"})));
    }
}
