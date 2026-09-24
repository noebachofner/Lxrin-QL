package ch.lxrin.ql.dsl;

import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Compiles snippets against the library to prove that type errors are compile errors. */
class TypeSafetyTest {

    private static String compile(String body) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        String source = "import static ch.lxrin.ql.TestSchema.UsersTable.USERS;\n"
                + "import ch.lxrin.ql.dsl.*;\n"
                + "class Snippet { void run() { " + body + " } }";
        JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///Snippet.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Path out = Files.createTempDirectory("snippet");
        boolean ok = compiler.getTask(null, null, diagnostics,
                List.of("-classpath", System.getProperty("java.class.path"), "-d", out.toString()), null, List.of(file)).call();
        StringBuilder sb = new StringBuilder();
        for (Diagnostic<?> d : diagnostics.getDiagnostics()) sb.append(d.getMessage(null)).append('\n');
        return ok ? null : sb.toString();
    }

    @Test
    void validCodeCompiles() throws Exception {
        assertNull(compile("USERS.CREATED_AT.gt(java.time.Instant.now()); USERS.EMAIL.endsWith(\"x\");"));
    }

    @Test
    void comparingATimestampWithAStringDoesNotCompile() throws Exception {
        assertNotNull(compile("USERS.CREATED_AT.eq(\"abc\");"));
    }

    @Test
    void stringOperationsAreNotAvailableOnOtherTypes() throws Exception {
        assertNotNull(compile("USERS.CREATED_AT.like(\"abc\");"));
        assertNotNull(compile("USERS.ID.endsWith(\"abc\");"));
    }

    @Test
    void arithmeticIsNotAvailableOnText() throws Exception {
        assertNotNull(compile("USERS.EMAIL.plus(1);"));
    }

    @Test
    void comparingDifferentColumnTypesDoesNotCompile() throws Exception {
        assertNotNull(compile("USERS.NAME.eq(USERS.VERSION);"));
    }

    @Test
    void eqNullIsAmbiguous() throws Exception {
        assertNotNull(compile("USERS.NAME.eq(null);"));
    }
}
