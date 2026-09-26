package ch.lxrin.ql.codegen;

/**
 * Removes comments from Java source code. String, character and text block literals are
 * copied unchanged; a line that held only a comment is removed completely, and trailing
 * blanks left by a removed comment are trimmed.
 */
final class Comments {

    private final String source;
    private final StringBuilder out;
    private int pos;
    private int lineStart;
    private boolean lineHadComment;

    private Comments(String source) {
        this.source = source;
        this.out = new StringBuilder(source.length());
    }

    /** Returns {@code source} without {@code //}, {@code /* *}{@code /} and Javadoc comments. */
    static String strip(String source) {
        return new Comments(source).run();
    }

    private String run() {
        int n = source.length();
        while (pos < n) {
            char c = source.charAt(pos);
            if (source.startsWith("\"\"\"", pos)) {
                copyTextBlock();
            } else if (c == '"' || c == '\'') {
                copyLiteral(c);
            } else if (source.startsWith("//", pos)) {
                int end = source.indexOf('\n', pos);
                pos = end < 0 ? n : end;
                lineHadComment = true;
            } else if (source.startsWith("/*", pos)) {
                int end = source.indexOf("*/", pos + 2);
                if (end < 0) throw new IllegalArgumentException("unterminated comment at offset " + pos);
                pos = end + 2;
                lineHadComment = true;
            } else {
                append(c);
                pos++;
            }
        }
        if (out.length() > lineStart) endLine(false);
        return out.toString();
    }

    private void copyLiteral(char quote) {
        append(quote);
        pos++;
        while (pos < source.length()) {
            char c = source.charAt(pos++);
            append(c);
            if (c == '\\' && pos < source.length()) {
                append(source.charAt(pos++));
            } else if (c == quote || c == '\n') {
                return;
            }
        }
    }

    private void copyTextBlock() {
        out.append("\"\"\"");
        pos += 3;
        while (pos < source.length()) {
            if (source.charAt(pos) == '\\' && pos + 1 < source.length()) {
                append(source.charAt(pos++));
                append(source.charAt(pos++));
            } else if (source.startsWith("\"\"\"", pos)) {
                out.append("\"\"\"");
                pos += 3;
                return;
            } else {
                append(source.charAt(pos++));
            }
        }
    }

    private void append(char c) {
        if (c == '\n') endLine(true);
        else out.append(c);
    }

    private void endLine(boolean newline) {
        if (lineHadComment) {
            int end = out.length();
            while (end > lineStart && (out.charAt(end - 1) == ' ' || out.charAt(end - 1) == '\t')) end--;
            out.setLength(end);
            if (end == lineStart) {
                lineHadComment = false;
                return;
            }
        }
        if (newline) out.append('\n');
        lineStart = out.length();
        lineHadComment = false;
    }
}
