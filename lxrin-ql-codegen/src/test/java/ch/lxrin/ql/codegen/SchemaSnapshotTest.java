package ch.lxrin.ql.codegen;

import ch.lxrin.ql.codegen.SchemaModel.ColumnModel;
import ch.lxrin.ql.codegen.SchemaModel.KeyModel;
import ch.lxrin.ql.codegen.SchemaModel.TableModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** The snapshot file format: round trip, stable text, hashing. */
class SchemaSnapshotTest {

    private static final Path GOLDEN = Path.of("src/test/resources/golden-snapshot/schema.json");

    @TempDir
    Path dir;

    @Test
    void roundTripKeepsTheModel() {
        SchemaSnapshot snapshot = new SchemaSnapshot(GoldenSchema.model(), "sha256:abc");
        SchemaSnapshot parsed = SchemaSnapshot.parse(snapshot.toJson());
        assertEquals(snapshot.model(), parsed.model());
        assertEquals("sha256:abc", parsed.migrations());
        assertEquals(snapshot.toJson(), parsed.toJson());
    }

    @Test
    void textIsStableAndDiffFriendly() throws IOException {
        String json = new SchemaSnapshot(GoldenSchema.model(), "sha256:0").toJson();
        if (Boolean.getBoolean("golden.update")) {
            Files.createDirectories(GOLDEN.getParent());
            Files.writeString(GOLDEN, json);
        }
        assertEquals(Files.readString(GOLDEN), json);
        assertFalse(json.contains("\r"));
        assertTrue(json.contains("\n        {\"name\": \"display_name\", \"type\": \"varchar\""), "one line per column");
    }

    @Test
    void escapesAndEmptyTables() {
        ColumnModel odd = new ColumnModel("we\"ird\\name", "text", "pg_catalog", "b", null, null, null, false, false, "", "",
                "line 1\nline 2\t\u0001", null);
        TableModel table = new TableModel("public", "t", "v", "a \"quoted\" comment", List.of(odd), null, List.of(), List.of());
        TableModel empty = new TableModel("public", "e", "r", null, List.of(), new KeyModel("e_pkey", List.of()), List.of(), List.of());
        SchemaModel model = new SchemaModel(List.of(table, empty), List.of());
        String json = new SchemaSnapshot(model, "sha256:x").toJson();
        assertEquals(model, SchemaSnapshot.parse(json).model());
        assertEquals(json.lines().count(), SchemaSnapshot.parse(json).toJson().lines().count());
    }

    @Test
    void invalidFilesAreReportedClearly() throws IOException {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> SchemaSnapshot.parse("{\"format\": 2, \"migrations\": \"x\", \"enums\": [], \"tables\": []}"));
        assertTrue(e.getMessage().contains("unsupported LxrinQL snapshot format 2"), e.getMessage());
        e = assertThrows(IllegalArgumentException.class, () -> SchemaSnapshot.parse("{\n\"format\": 1,\n\"tables\": [}"));
        assertTrue(e.getMessage().contains("line 3"), e.getMessage());
        Path file = dir.resolve("broken.json");
        Files.writeString(file, "{");
        e = assertThrows(IllegalArgumentException.class, () -> SchemaSnapshot.read(file));
        assertTrue(e.getMessage().contains(file.toString()), e.getMessage());
    }

    @Test
    void writeOnlyChangesTheFileWhenTheContentChanges() throws IOException {
        Path file = dir.resolve("src/main/lxrinql/schema.json");
        SchemaSnapshot snapshot = new SchemaSnapshot(GoldenSchema.model(), "sha256:0");
        snapshot.write(file);
        java.nio.file.attribute.FileTime old = java.nio.file.attribute.FileTime.fromMillis(1_000_000);
        Files.setLastModifiedTime(file, old);
        snapshot.write(file);
        assertEquals(old, Files.getLastModifiedTime(file));
        assertEquals(snapshot, SchemaSnapshot.read(file));
    }

    @Test
    void selectAppliesTheConfiguredTables() {
        SchemaSnapshot snapshot = new SchemaSnapshot(GoldenSchema.model(), "sha256:0");
        CodegenConfig config = new CodegenConfig().schemas(List.of("public")).exclude("event_.*");
        List<String> names = snapshot.select(config).tables().stream().map(TableModel::name).collect(Collectors.toList());
        assertEquals(List.of("app_users", "memberships", "active_users"), names);
        assertEquals(2, snapshot.select(config).enums().size());
    }

    @Test
    void hashCoversNamesAndContentButNotLineEndings() throws IOException {
        Path migrations = Files.createDirectories(dir.resolve("db/migration/sub"));
        Files.writeString(migrations.resolve("V1__a.sql"), "CREATE TABLE a (id int);\n");
        Files.writeString(migrations.resolve("V2__b.sql"), "CREATE TABLE b (id int);\n");
        Path script = Files.writeString(dir.resolve("extra.sql"), "SELECT 1;\n");
        List<Path> roots = List.of(dir.resolve("db/migration"), script);
        String hash = SchemaSnapshot.hash(roots);
        assertTrue(hash.matches("sha256:[0-9a-f]{64}"), hash);
        assertEquals(hash, SchemaSnapshot.hash(roots));

        Files.writeString(migrations.resolve("V2__b.sql"), "CREATE TABLE b (id int);\r\n");
        assertEquals(hash, SchemaSnapshot.hash(roots), "CRLF checkouts give the same hash");
        Files.writeString(migrations.resolve("V2__b.sql"), "CREATE TABLE b (id bigint);\n");
        assertNotEquals(hash, SchemaSnapshot.hash(roots));
        Files.writeString(migrations.resolve("V2__b.sql"), "CREATE TABLE b (id int);\n");
        Files.move(migrations.resolve("V2__b.sql"), migrations.resolve("V3__b.sql"));
        assertNotEquals(hash, SchemaSnapshot.hash(roots), "renaming a migration changes the hash");
        assertEquals(SchemaSnapshot.hash(List.of()), SchemaSnapshot.hash(List.of(dir.resolve("missing"))));
        List<Path> files;
        try (Stream<Path> s = Files.list(migrations)) {
            files = s.collect(Collectors.toCollection(ArrayList::new));
        }
        assertEquals(2, files.size());
    }
}
