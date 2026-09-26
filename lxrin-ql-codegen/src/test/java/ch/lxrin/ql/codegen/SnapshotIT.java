package ch.lxrin.ql.codegen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Snapshots written from real migrations (Testcontainers), checked, and used for generation. */
class SnapshotIT {

    @TempDir
    Path dir;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int cli(SchemaSources sources, String... args) throws Exception {
        return Main.run(args, sources, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
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
    void snapshotIsCheckedAndGeneratesTheSameCodeAsTheDatabase() throws Exception {
        Path migrations = Files.createDirectories(dir.resolve("migrations"));
        Files.writeString(migrations.resolve("V1__schema.sql"), String.join("\n",
                "CREATE TYPE level AS ENUM ('low', 'high');",
                "CREATE TABLE app_user (id uuid PRIMARY KEY, name text NOT NULL, created_by uuid REFERENCES app_user (id),",
                "  lvl level NOT NULL DEFAULT 'low', tags text[], period tstzrange, seq bigint GENERATED ALWAYS AS IDENTITY);",
                "COMMENT ON TABLE app_user IS 'People \"who\" sign in';",
                "CREATE TABLE item (id serial PRIMARY KEY, owner_id uuid NOT NULL REFERENCES app_user (id), code text UNIQUE);",
                "CREATE VIEW item_codes AS SELECT code FROM item;"));
        Path snapshot = dir.resolve("src/main/lxrinql/schema.json");
        String m = migrations.toString();
        SchemaSources docker = new SchemaSources();

        assertEquals(0, cli(docker, "--write-snapshot", snapshot.toString(), "--migrations", m));
        String written = Files.readString(snapshot);
        assertTrue(written.contains("\"name\": \"app_user\", \"kind\": \"r\", \"comment\": \"People \\\"who\\\" sign in\""), written);
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("wrote the schema snapshot"));
        assertEquals(0, cli(docker, "--check-snapshot", snapshot.toString(), "--migrations", m));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("is up to date"));

        List<String> common = List.of("--package", "com.example.db", "--migrations", m, "--strip-prefixes", "app_",
                "--snapshot", snapshot.toString());
        String[] fromDatabase = Stream.concat(common.stream(), Stream.of("--output", dir.resolve("db").toString(),
                "--schema-source", "database")).toArray(String[]::new);
        String[] fromSnapshot = Stream.concat(common.stream(), Stream.of("--output", dir.resolve("snap").toString(),
                "--schema-source", "snapshot")).toArray(String[]::new);
        assertEquals(0, cli(docker, fromDatabase));
        assertEquals(0, cli(new SchemaSources(() -> false), fromSnapshot));
        assertEquals(tree(dir.resolve("db")), tree(dir.resolve("snap")));
        assertTrue(Files.exists(dir.resolve("snap/com/example/db/UserTable.java")));

        Files.writeString(migrations.resolve("V2__more.sql"), "ALTER TABLE item ADD COLUMN note text;\n");
        assertEquals(1, cli(docker, "--check-snapshot", snapshot.toString(), "--migrations", m));
        String errors = err.toString(StandardCharsets.UTF_8);
        assertTrue(errors.contains("is out of date; line 3 is \"migrations\""), errors);
        assertEquals(0, cli(docker, "--write-snapshot", snapshot.toString(), "--migrations", m));
        assertTrue(Files.readString(snapshot).contains("{\"name\": \"note\", \"type\": \"text\""));
        assertEquals(0, cli(docker, "--check-snapshot", snapshot.toString(), "--migrations", m));
    }
}
