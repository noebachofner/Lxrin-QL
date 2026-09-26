package ch.lxrin.ql.codegen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Choosing between database and snapshot, without Docker. */
class SchemaSourcesTest {

    @TempDir
    Path dir;

    private final List<String> messages = new ArrayList<>();
    private final SchemaSources.Log log = new SchemaSources.Log() {
        @Override
        public void info(String message) {
            messages.add("info " + message);
        }

        @Override
        public void warn(String message) {
            messages.add("warn " + message);
        }
    };
    private final SchemaSources noDocker = new SchemaSources(() -> false);

    private Path migrations() throws IOException {
        Path m = Files.createDirectories(dir.resolve("db/migration"));
        Files.writeString(m.resolve("V1__init.sql"), "CREATE TABLE app_users (id uuid PRIMARY KEY);\n");
        return m;
    }

    private SchemaSources.Database database() throws IOException {
        return SchemaSources.Database.container("postgres:17-alpine", List.of(migrations()), List.of());
    }

    private Path snapshot(SchemaSources.Database database) {
        Path file = dir.resolve("src/main/lxrinql/schema.json");
        new SchemaSnapshot(GoldenSchema.model(), database.migrationsHash()).write(file);
        return file;
    }

    private static String tree(Path root) throws IOException {
        StringBuilder b = new StringBuilder();
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : s.filter(Files::isRegularFile).sorted().collect(Collectors.toList())) {
                b.append(root.relativize(p)).append('\n').append(Files.readString(p));
            }
        }
        return b.toString();
    }

    @Test
    void autoWithoutDockerGeneratesFromTheSnapshot() throws Exception {
        SchemaSources.Database database = database();
        Path snapshot = snapshot(database);
        CodegenConfig config = GoldenSchema.config(dir.resolve("from-snapshot"));
        noDocker.generate(config, SchemaSources.Source.AUTO, database, snapshot, log);
        assertEquals(List.of("info LxrinQL: Docker is not available, generating from the schema snapshot " + snapshot), messages);

        CodegenConfig direct = GoldenSchema.config(dir.resolve("direct"));
        CodeGenerator.generate(GoldenSchema.model(), direct, new DefaultNamingStrategy(direct));
        assertEquals(tree(dir.resolve("direct")), tree(dir.resolve("from-snapshot")), "the same code as from the model");
    }

    @Test
    void snapshotSourceWarnsWhenTheMigrationsChanged() throws Exception {
        SchemaSources.Database database = database();
        Path snapshot = snapshot(database);
        Files.writeString(migrations().resolve("V2__more.sql"), "CREATE TABLE more (id int);\n");
        noDocker.load(GoldenSchema.config(dir.resolve("out")), SchemaSources.Source.SNAPSHOT, database, snapshot, log);
        assertEquals(2, messages.size());
        assertTrue(messages.get(0).startsWith("info LxrinQL: schemaSource is snapshot, generating from"), messages.get(0));
        assertTrue(messages.get(1).startsWith("warn LxrinQL: the migrations changed since the snapshot"), messages.get(1));
    }

    @Test
    void missingSnapshotsAreClearErrors() throws Exception {
        SchemaSources.Database database = database();
        Path missing = dir.resolve("none.json");
        CodegenConfig config = GoldenSchema.config(dir.resolve("out"));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> noDocker.load(config, SchemaSources.Source.AUTO, database, missing, log));
        assertTrue(e.getMessage().contains("Docker is not available and there is no schema snapshot at " + missing), e.getMessage());
        e = assertThrows(IllegalStateException.class, () -> noDocker.load(config, SchemaSources.Source.SNAPSHOT, database, missing, log));
        assertTrue(e.getMessage().contains("schemaSource is snapshot, but the snapshot " + missing + " does not exist"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> SchemaSources.Source.parse("docker"));
        assertEquals(SchemaSources.Source.AUTO, SchemaSources.Source.parse(null));
        assertEquals(SchemaSources.Source.SNAPSHOT, SchemaSources.Source.parse(" Snapshot "));
    }

    @Test
    void checkWithoutDockerComparesTheMigrationsHash() throws Exception {
        SchemaSources.Database database = database();
        Path snapshot = snapshot(database);
        CodegenConfig config = new CodegenConfig();
        assertTrue(noDocker.checkSnapshot(config, database, snapshot, log));
        assertTrue(messages.get(0).contains("Docker is not available, so only the migrations hash"), messages.get(0));
        Files.writeString(migrations().resolve("V2__more.sql"), "CREATE TABLE more (id int);\n");
        assertFalse(noDocker.checkSnapshot(config, database, snapshot, log));
        assertTrue(messages.get(messages.size() - 1).contains("is out of date: the migrations changed"));
        assertFalse(noDocker.checkSnapshot(config, database, dir.resolve("none.json"), log));
    }

    @Test
    void cliUsesTheSameSources() throws Exception {
        SchemaSources.Database database = database();
        Path snapshot = snapshot(database);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream o = new PrintStream(out, true, StandardCharsets.UTF_8);
        PrintStream e = new PrintStream(err, true, StandardCharsets.UTF_8);
        String m = dir.resolve("db/migration").toString();
        assertEquals(0, Main.run(new String[]{"--check-snapshot", snapshot.toString(), "--migrations", m}, noDocker, o, e));
        assertEquals(0, Main.run(new String[]{"--package", "com.example.db", "--output", dir.resolve("cli").toString(),
                "--schemas", "public,sales", "--snapshot", snapshot.toString(), "--schema-source", "snapshot", "--migrations", m,
                "--strip-prefixes", "app_", "--table-constants", "app_users=USERS", "--enum-mappings", "mood=ch.lxrin.ql.codegen.Mood",
                "--forced-types", "orders|payload|jsonb|ch.lxrin.ql.codegen.Payload|ch.lxrin.ql.codegen.Payload.TYPE"}, noDocker, o, e));
        assertTrue(Files.exists(dir.resolve("cli/com/example/db/UserTable.java")));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("LxrinQL: generated 21 files"), out.toString(StandardCharsets.UTF_8));
        Files.writeString(migrations().resolve("V2__more.sql"), "CREATE TABLE more (id int);\n");
        assertEquals(1, Main.run(new String[]{"--check-snapshot", snapshot.toString(), "--migrations", m}, noDocker, o, e));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("is out of date"), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void firstDifferenceNamesTheLine() {
        assertEquals("line 2 is b but the migrations give c", SchemaSources.firstDifference("a\nb\n", "a\nc\n"));
        assertEquals("line 2 is <end of file> but the migrations give c", SchemaSources.firstDifference("a", "a\nc"));
    }
}
