package ch.lxrin.ql.codegen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Reads a real schema from PostgreSQL (Testcontainers) and generates code from migrations. */
class SchemaReaderIT {

    @TempDir
    Path dir;

    @Test
    void readsCatalogFeaturesAndGeneratesFromMigrations() throws Exception {
        Path migrations = Files.createDirectories(dir.resolve("migrations"));
        Files.writeString(migrations.resolve("V1__schema.sql"), String.join("\n",
                "CREATE SCHEMA app;",
                "CREATE TYPE app.level AS ENUM ('low', 'high');",
                "CREATE DOMAIN email AS text CHECK (VALUE LIKE '%@%');",
                "CREATE TABLE app.item (id serial PRIMARY KEY, code text NOT NULL, mail email, levels app.level[],",
                "  lvl app.level NOT NULL DEFAULT 'low', twice int GENERATED ALWAYS AS (id * 2) STORED,",
                "  seq_id bigint GENERATED ALWAYS AS IDENTITY);",
                "CREATE UNIQUE INDEX item_code_idx ON app.item (code);",
                "CREATE TABLE app.part (item_id int REFERENCES app.item (id), n int, PRIMARY KEY (item_id, n));",
                "CREATE TABLE app.measure (at date NOT NULL, v numeric) PARTITION BY RANGE (at);",
                "CREATE TABLE app.measure_2024 PARTITION OF app.measure FOR VALUES FROM ('2024-01-01') TO ('2025-01-01');",
                "CREATE MATERIALIZED VIEW app.item_count AS SELECT count(*) AS n FROM app.item;",
                "COMMENT ON COLUMN app.item.code IS 'Item code';"));
        CodegenConfig config = new CodegenConfig().packageName("com.example.app").schemas(List.of("app")).defaultSchema("app")
                .outputDirectory(dir.resolve("out"));
        try (DatabaseProvisioner db = DatabaseProvisioner.testcontainer("postgres:17-alpine", List.of(migrations), List.of());
             Connection con = db.connect()) {
            SchemaModel model = SchemaReader.read(con, config);
            List<String> names = model.tables().stream().map(SchemaModel.TableModel::name).toList();
            assertEquals(List.of("item", "item_count", "measure", "part"), names);
            SchemaModel.TableModel item = model.tables().get(0);
            assertEquals("app.item_id_seq", item.columns().get(0).sequence(), "serial column gets its sequence");
            SchemaModel.ColumnModel mail = item.columns().get(2);
            assertEquals("text", mail.type(), "domain resolved to its base type");
            SchemaModel.ColumnModel levels = item.columns().get(3);
            assertEquals("level", levels.elementType());
            assertEquals("e", levels.elementKind());
            assertEquals("s", item.columns().get(5).generated());
            assertEquals("a", item.columns().get(6).identity());
            assertEquals("Item code", item.columns().get(1).comment());
            assertEquals(List.of("code"), item.uniqueKeys().get(0).columns());
            assertEquals("m", model.tables().get(1).kind());
            assertEquals(List.of("item_id", "n"), model.tables().get(3).primaryKey().columns());
            assertEquals("item", model.tables().get(3).foreignKeys().get(0).referencedTable());
            assertEquals(List.of("low", "high"), model.enums().get(0).labels());

            CodeGenerator.Result result = CodeGenerator.generate(con, config);
            assertTrue(result.generated().stream().anyMatch(p -> p.endsWith("Level.java")));
            String table = Files.readString(dir.resolve("out/com/example/app/ItemTable.java"));
            assertTrue(table.contains("KeyStrategy.sequence(\"app.item_id_seq\")"), table);
            assertTrue(table.contains("arrayColumn(\"levels\", Level.TYPE.array()"), table);
            assertTrue(table.contains("Column.IDENTITY_ALWAYS"), table);
            assertTrue(Files.readString(dir.resolve("out/com/example/app/ItemCountTable.java")).contains("return true;"));
        }
    }
}
