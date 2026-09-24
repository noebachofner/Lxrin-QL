package ch.lxrin.ql.codegen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Compares the generated code with golden files in {@code src/test/resources/golden} and
 * compiles it. Run with {@code -Dgolden.update=true} to rewrite the golden files after an
 * intended change, then review the diff.
 */
class CodeGeneratorTest {

    private static final Path GOLDEN = Path.of("src/test/resources/golden");

    @TempDir
    Path out;

    private CodeGenerator.Result generate() {
        CodegenConfig config = GoldenSchema.config(out);
        return CodeGenerator.generate(GoldenSchema.model(), config, new DefaultNamingStrategy(config));
    }

    @Test
    void generatedCodeMatchesGoldenFiles() throws IOException {
        CodeGenerator.Result result = generate();
        Path pkg = out.resolve("java/com/example/db");
        List<String> names;
        try (Stream<Path> s = Files.list(pkg)) {
            names = s.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
        }
        boolean update = Boolean.getBoolean("golden.update");
        if (update) {
            Files.createDirectories(GOLDEN);
            try (Stream<Path> old = Files.list(GOLDEN)) {
                for (Path p : (Iterable<Path>) old::iterator) Files.delete(p);
            }
        }
        List<String> expectedNames;
        for (String name : names) {
            Path actual = pkg.resolve(name);
            Path expected = GOLDEN.resolve(name + ".txt");
            if (update) Files.copy(actual, expected);
            assertEquals(Files.readString(expected), Files.readString(actual), "golden file " + name);
        }
        try (Stream<Path> s = Files.list(GOLDEN)) {
            expectedNames = s.map(p -> p.getFileName().toString().replace(".txt", "")).sorted().collect(Collectors.toList());
        }
        assertEquals(expectedNames, names);
        assertEquals(names.size() + 1, result.generated().size(), "sources plus the repository index");
        assertEquals("com.example.db.UserRepository\ncom.example.db.OrderRepository\ncom.example.db.MembershipRepository\n"
                        + "com.example.db.ActiveUserRepository\ncom.example.db.EventLogRepository\n",
                Files.readString(out.resolve("resources/META-INF/lxrin-ql/repositories")));
    }

    @Test
    void generatedCodeCompiles() throws IOException {
        generate();
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> s = Stream.concat(Files.walk(out.resolve("java")), Files.walk(out.resolve("stubs")))) {
            s.filter(p -> p.toString().endsWith(".java")).forEach(sources::add);
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            Path classes = Files.createDirectories(out.resolve("classes"));
            boolean ok = compiler.getTask(null, fm, diagnostics,
                    List.of("-classpath", System.getProperty("java.class.path"), "-d", classes.toString(), "-Xlint:all", "-Werror",
                            "-Xdoclint:all,-missing"),
                    null, fm.getJavaFileObjectsFromPaths(sources)).call();
            StringBuilder sb = new StringBuilder();
            for (Diagnostic<?> d : diagnostics.getDiagnostics()) sb.append(d).append('\n');
            assertTrue(ok, sb.toString());
        }
    }

    @Test
    void stubsAreCreatedOnceAndNeverOverwritten() throws IOException {
        CodeGenerator.Result first = generate();
        assertEquals(5, first.stubs().size());
        Path stub = out.resolve("stubs/com/example/db/UserRepository.java");
        Files.writeString(stub, Files.readString(stub).replace("{\n\n", "{\n\n    // my own code\n\n"));
        CodeGenerator.Result second = generate();
        assertEquals(0, second.stubs().size());
        assertTrue(Files.readString(stub).contains("// my own code"));
    }

    @Test
    void unchangedFilesAreNotRewrittenAndStaleFilesAreRemoved() throws IOException {
        generate();
        Path pkg = out.resolve("java/com/example/db");
        Path table = pkg.resolve("UserTable.java");
        FileTime old = FileTime.fromMillis(1_000_000);
        Files.setLastModifiedTime(table, old);
        Path stale = pkg.resolve("GoneTable.java");
        Files.writeString(stale, CodeGenerator.HEADER + "class GoneTable {}");
        Path handWritten = pkg.resolve("Keep.java");
        Files.writeString(handWritten, "class Keep {}");
        generate();
        assertEquals(old, Files.getLastModifiedTime(table));
        assertFalse(Files.exists(stale));
        assertTrue(Files.exists(handWritten));
    }

    @Test
    void conflictingEntityNamesAreReported() {
        CodegenConfig config = GoldenSchema.config(out).stripTablePrefix("sales_");
        SchemaModel model = new SchemaModel(List.of(
                new SchemaModel.TableModel("public", "users", "r", null, List.of(GoldenSchema.col("id", "int4", true)), null, List.of(), List.of()),
                new SchemaModel.TableModel("public", "user", "r", null, List.of(GoldenSchema.col("id", "int4", true)), null, List.of(), List.of())),
                List.of());
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> CodeGenerator.generate(model, config, new DefaultNamingStrategy(config)));
        assertTrue(e.getMessage().contains("entityName"));
    }

    @Test
    void configValidation() {
        assertThrows(IllegalArgumentException.class, () -> new CodegenConfig().packageName("Com.Example"));
        assertThrows(IllegalStateException.class, () -> new CodegenConfig().packageName("a.b").validate());
        assertThrows(java.util.regex.PatternSyntaxException.class, () -> new CodegenConfig.ForcedType("(", "x", "y", "a.B", "a.B.T"));
    }
}
