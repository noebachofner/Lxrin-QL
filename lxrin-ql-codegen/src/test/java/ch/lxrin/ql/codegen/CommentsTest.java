package ch.lxrin.ql.codegen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommentsTest {

    @Test
    void removesJavadocBlockAndLineComments() {
        String source = "/**\n * A class.\n */\npublic class A { // trailing\n\n    /** Field. */\n    int x; /* inline */ int y;\n"
                + "    // own line\n    /*\n     * block\n     */\n    void m() {}\n}\n";
        assertEquals("public class A {\n\n    int x;  int y;\n    void m() {}\n}\n", Comments.strip(source));
    }

    @Test
    void keepsLiteralsThatLookLikeComments() {
        String source = "class A {\n    String a = \"http://x /* no */\";\n    String b = \"quote \\\" // still\";\n"
                + "    char c = '/';\n    char d = '\\'';\n    String e = \"\"\"\n        /* text */ // block \\\"\"\"\n        \"\"\";\n}\n";
        assertEquals(source, Comments.strip(source));
    }

    @Test
    void keepsBlankLinesThatWereNotComments() {
        assertEquals("a\n\nb\n", Comments.strip("a\n\n// x\nb // y\n"));
        assertEquals("a", Comments.strip("a// end"));
        assertEquals("", Comments.strip("// only"));
        assertThrows(IllegalArgumentException.class, () -> Comments.strip("a /* open"));
    }
}
