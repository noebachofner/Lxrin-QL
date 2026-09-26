package ch.lxrin.ql.codegen;

import ch.lxrin.ql.codegen.SchemaModel.ForeignKeyModel;
import ch.lxrin.ql.codegen.SchemaModel.KeyModel;
import ch.lxrin.ql.codegen.SchemaModel.TableModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ch.lxrin.ql.codegen.GoldenSchema.col;
import static org.junit.jupiter.api.Assertions.*;

/** Foreign key constants are named after their own columns and never depend on other keys. */
class ForeignKeyNamesTest {

    private static final Pattern FK = Pattern.compile("ForeignKey (\\w+) = foreignKey\\(\"(\\w+)\"");

    @TempDir
    Path out;

    private static ForeignKeyModel fk(String name, String referenced, String... columns) {
        return new ForeignKeyModel(name, List.of(columns), "public", referenced, List.of("id"));
    }

    private static TableModel user(List<ForeignKeyModel> foreignKeys) {
        return new TableModel("public", "app_user", "r", null, List.of(col("id", "uuid", true), col("created_by", "uuid", false),
                col("updated_by", "uuid", false), col("manager_id", "uuid", false), col("team_id", "uuid", false)),
                new KeyModel("app_user_pkey", List.of("id")), List.of(), foreignKeys);
    }

    private static TableModel team() {
        return new TableModel("public", "team", "r", null, List.of(col("id", "uuid", true)), new KeyModel("team_pkey", List.of("id")),
                List.of(), List.of());
    }

    /** Returns constraint name → constant of the generated {@code UserTable}. */
    private Map<String, String> generate(List<TableModel> tables, UnaryOperator<CodegenConfig> configure) throws IOException {
        CodegenConfig config = configure.apply(new CodegenConfig().packageName("com.example.db").stripTablePrefix("app_")
                .outputDirectory(out.resolve("java")));
        CodeGenerator.generate(new SchemaModel(tables, List.of()), config, new DefaultNamingStrategy(config));
        String source = Files.readString(out.resolve("java/com/example/db/UserTable.java"));
        Map<String, String> names = new LinkedHashMap<>();
        Matcher m = FK.matcher(source);
        while (m.find()) names.put(m.group(2), m.group(1));
        return names;
    }

    private Map<String, String> generate(List<ForeignKeyModel> foreignKeys) throws IOException {
        return generate(List.of(user(foreignKeys), team()), c -> c);
    }

    @Test
    void twoKeysToTheOwnTableAreNamedAfterTheirColumns() throws IOException {
        Map<String, String> names = generate(List.of(fk("app_user_created_by_fkey", "app_user", "created_by"),
                fk("app_user_updated_by_fkey", "app_user", "updated_by")));
        assertEquals(Map.of("app_user_created_by_fkey", "FK_CREATED_BY", "app_user_updated_by_fkey", "FK_UPDATED_BY"), names);
    }

    @Test
    void namesDoNotDependOnTheOrderOfTheKeys() throws IOException {
        List<ForeignKeyModel> keys = new ArrayList<>(List.of(fk("app_user_created_by_fkey", "app_user", "created_by"),
                fk("app_user_updated_by_fkey", "app_user", "updated_by"), fk("app_user_team_id_fkey", "team", "team_id")));
        Map<String, String> forward = generate(keys);
        Collections.reverse(keys);
        assertEquals(forward, generate(keys));
    }

    @Test
    void addingAThirdKeyDoesNotRenameTheFirstTwo() throws IOException {
        ForeignKeyModel createdBy = fk("app_user_created_by_fkey", "app_user", "created_by");
        ForeignKeyModel updatedBy = fk("app_user_updated_by_fkey", "app_user", "updated_by");
        Map<String, String> before = generate(List.of(createdBy, updatedBy));
        Map<String, String> after = generate(List.of(fk("app_user_manager_id_fkey", "app_user", "manager_id"), createdBy, updatedBy));
        assertEquals(before.get(createdBy.name()), after.get(createdBy.name()));
        assertEquals(before.get(updatedBy.name()), after.get(updatedBy.name()));
        assertEquals("FK_MANAGER_ID", after.get("app_user_manager_id_fkey"));
    }

    @Test
    void compositeKeysJoinTheirColumns() throws IOException {
        TableModel user = user(List.of(new ForeignKeyModel("app_user_team_fkey", List.of("team_id", "manager_id"), "public", "team",
                List.of("id", "lead_id"))));
        assertEquals(Map.of("app_user_team_fkey", "FK_TEAM_ID_MANAGER_ID"), generate(List.of(user, team()), c -> c));
    }

    @Test
    void twoKeysOnTheSameColumnsAreAnError() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> generate(List.of(
                fk("app_user_team_id_fkey", "team", "team_id"), fk("app_user_team_id_fkey1", "app_user", "team_id"))));
        assertTrue(e.getMessage().contains("app_user_team_id_fkey "), e.getMessage());
        assertTrue(e.getMessage().contains("app_user_team_id_fkey1"), e.getMessage());
        assertTrue(e.getMessage().contains("FK_TEAM_ID"), e.getMessage());
        assertTrue(e.getMessage().contains("foreignKeyNames"), e.getMessage());
    }

    @Test
    void aKeyNamedLikeAColumnIsAnError() {
        TableModel table = new TableModel("public", "app_user", "r", null, List.of(col("id", "uuid", true), col("parent", "uuid", false),
                col("fk_parent", "uuid", false)), new KeyModel("app_user_pkey", List.of("id")), List.of(),
                List.of(fk("app_user_parent_fkey", "app_user", "parent")));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> generate(List.of(table), c -> c));
        assertTrue(e.getMessage().contains("column fk_parent"), e.getMessage());
    }

    @Test
    void anOverrideCollidingWithAnotherKeyIsAnError() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> generate(List.of(user(List.of(
                fk("app_user_created_by_fkey", "app_user", "created_by"), fk("app_user_updated_by_fkey", "app_user", "updated_by"))), team()),
                c -> c.foreignKeyName("app_user_updated_by_fkey", "FK_CREATED_BY")));
        assertTrue(e.getMessage().contains("app_user_created_by_fkey and the foreign key app_user_updated_by_fkey"), e.getMessage());
    }

    @Test
    void overridesByConstraintOrQualifiedName() throws IOException {
        List<ForeignKeyModel> keys = List.of(fk("app_user_created_by_fkey", "app_user", "created_by"),
                fk("app_user_updated_by_fkey", "app_user", "updated_by"));
        Map<String, String> names = generate(List.of(user(keys), team()), c -> c
                .foreignKeyName("app_user_created_by_fkey", "FK_CREATOR")
                .foreignKeyName("app_user_updated_by_fkey", "FK_IGNORED")
                .foreignKeyName("app_user.app_user_updated_by_fkey", "FK_EDITOR"));
        assertEquals(Map.of("app_user_created_by_fkey", "FK_CREATOR", "app_user_updated_by_fkey", "FK_EDITOR"), names);
        assertThrows(IllegalArgumentException.class, () -> new CodegenConfig().foreignKeyName("x_fkey", "1ABC"));
        assertThrows(IllegalArgumentException.class, () -> new CodegenConfig().foreignKeyName(" ", "FK_X"));
    }
}
