package ch.lxrin.ql.codegen;

import ch.lxrin.ql.codegen.SchemaModel.ColumnModel;
import ch.lxrin.ql.codegen.SchemaModel.EnumModel;
import ch.lxrin.ql.codegen.SchemaModel.ForeignKeyModel;
import ch.lxrin.ql.codegen.SchemaModel.KeyModel;
import ch.lxrin.ql.codegen.SchemaModel.TableModel;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The schema model as a checked-in, diff-friendly JSON file, so code can be generated
 * without a database. One line per column and key, keys in a fixed order, {@code \n}
 * line endings; the same schema always gives the same text.
 *
 * <pre>
 * {
 *   "format": 1,
 *   "migrations": "sha256:…",
 *   "enums": [
 *     {"schema": "public", "name": "app_role", "labels": ["admin", "user"]}
 *   ],
 *   "tables": [
 *     {
 *       "schema": "public", "name": "app_user", "kind": "r",
 *       "columns": [
 *         {"name": "id", "type": "uuid", "typeSchema": "pg_catalog", "typeKind": "b", "notNull": true, …}
 *       ],
 *       "primaryKey": {"name": "app_user_pkey", "columns": ["id"]},
 *       "uniqueKeys": [],
 *       "foreignKeys": []
 *     }
 *   ]
 * }
 * </pre>
 *
 * @param model      the schema
 * @param migrations the hash of the migrations the schema was read from ({@link #hash(List)})
 */
public record SchemaSnapshot(SchemaModel model, String migrations) {

    /** The file format version. */
    public static final int FORMAT = 1;

    /** Renders the snapshot. */
    public String toJson() {
        StringBuilder b = new StringBuilder("{\n");
        b.append("  \"format\": ").append(FORMAT).append(",\n");
        b.append("  \"migrations\": ").append(Json.quote(migrations)).append(",\n");
        b.append("  \"enums\": [");
        List<EnumModel> enums = model.enums();
        for (int i = 0; i < enums.size(); i++) {
            EnumModel e = enums.get(i);
            b.append(i == 0 ? "\n" : ",\n").append("    {\"schema\": ").append(Json.quote(e.schema())).append(", \"name\": ")
                    .append(Json.quote(e.name())).append(", \"labels\": ").append(Json.strings(e.labels())).append('}');
        }
        b.append(enums.isEmpty() ? "],\n" : "\n  ],\n");
        b.append("  \"tables\": [");
        List<TableModel> tables = model.tables();
        for (int i = 0; i < tables.size(); i++) {
            b.append(i == 0 ? "\n" : ",\n");
            table(b, tables.get(i));
        }
        b.append(tables.isEmpty() ? "]\n" : "\n  ]\n");
        return b.append("}\n").toString();
    }

    private static void table(StringBuilder b, TableModel t) {
        b.append("    {\n      \"schema\": ").append(Json.quote(t.schema())).append(", \"name\": ").append(Json.quote(t.name()))
                .append(", \"kind\": ").append(Json.quote(t.kind()));
        if (t.comment() != null) b.append(", \"comment\": ").append(Json.quote(t.comment()));
        b.append(",\n      \"columns\": [");
        for (int i = 0; i < t.columns().size(); i++) {
            ColumnModel c = t.columns().get(i);
            b.append(i == 0 ? "\n" : ",\n").append("        {");
            field(b, "name", c.name(), true);
            field(b, "type", c.type(), false);
            field(b, "typeSchema", c.typeSchema(), false);
            field(b, "typeKind", c.typeKind(), false);
            field(b, "elementType", c.elementType(), false);
            field(b, "elementKind", c.elementKind(), false);
            field(b, "elementSchema", c.elementSchema(), false);
            b.append(", \"notNull\": ").append(c.notNull()).append(", \"hasDefault\": ").append(c.hasDefault());
            field(b, "identity", c.identity(), false);
            field(b, "generated", c.generated(), false);
            field(b, "comment", c.comment(), false);
            field(b, "sequence", c.sequence(), false);
            b.append('}');
        }
        b.append(t.columns().isEmpty() ? "],\n" : "\n      ],\n");
        b.append("      \"primaryKey\": ").append(t.primaryKey() == null ? "null" : key(t.primaryKey())).append(",\n");
        b.append("      \"uniqueKeys\": [");
        for (int i = 0; i < t.uniqueKeys().size(); i++) b.append(i == 0 ? "\n" : ",\n").append("        ").append(key(t.uniqueKeys().get(i)));
        b.append(t.uniqueKeys().isEmpty() ? "],\n" : "\n      ],\n");
        b.append("      \"foreignKeys\": [");
        for (int i = 0; i < t.foreignKeys().size(); i++) {
            ForeignKeyModel fk = t.foreignKeys().get(i);
            b.append(i == 0 ? "\n" : ",\n").append("        {\"name\": ").append(Json.quote(fk.name())).append(", \"columns\": ")
                    .append(Json.strings(fk.columns())).append(", \"referencedSchema\": ").append(Json.quote(fk.referencedSchema()))
                    .append(", \"referencedTable\": ").append(Json.quote(fk.referencedTable())).append(", \"referencedColumns\": ")
                    .append(Json.strings(fk.referencedColumns())).append('}');
        }
        b.append(t.foreignKeys().isEmpty() ? "]\n" : "\n      ]\n");
        b.append("    }");
    }

    private static String key(KeyModel k) {
        return "{\"name\": " + Json.quote(k.name()) + ", \"columns\": " + Json.strings(k.columns()) + "}";
    }

    private static void field(StringBuilder b, String name, String value, boolean first) {
        if (value == null) return;
        if (!first) b.append(", ");
        b.append('"').append(name).append("\": ").append(Json.quote(value));
    }

    /** Parses a snapshot file's text. */
    public static SchemaSnapshot parse(String json) {
        Map<String, Object> root = Json.object(Json.parse(json), "snapshot");
        Object format = root.get("format");
        if (!(format instanceof Long) || (Long) format != FORMAT) {
            throw new IllegalArgumentException("unsupported LxrinQL snapshot format " + format + "; expected " + FORMAT
                    + ". Write the snapshot again with this version (lxrinQlSnapshot).");
        }
        List<EnumModel> enums = new ArrayList<>();
        for (Object o : Json.list(root.get("enums"), "enums")) {
            Map<String, Object> e = Json.object(o, "enum");
            enums.add(new EnumModel(Json.string(e, "schema"), Json.string(e, "name"), Json.stringList(e.get("labels"))));
        }
        List<TableModel> tables = new ArrayList<>();
        for (Object o : Json.list(root.get("tables"), "tables")) {
            Map<String, Object> t = Json.object(o, "table");
            List<ColumnModel> columns = new ArrayList<>();
            for (Object co : Json.list(t.get("columns"), "columns")) {
                Map<String, Object> c = Json.object(co, "column");
                columns.add(new ColumnModel(Json.string(c, "name"), Json.optString(c, "type"), Json.optString(c, "typeSchema"),
                        Json.optString(c, "typeKind"), Json.optString(c, "elementType"), Json.optString(c, "elementKind"),
                        Json.optString(c, "elementSchema"), Boolean.TRUE.equals(c.get("notNull")), Boolean.TRUE.equals(c.get("hasDefault")),
                        Json.optString(c, "identity"), Json.optString(c, "generated"), Json.optString(c, "comment"),
                        Json.optString(c, "sequence")));
            }
            KeyModel primaryKey = t.get("primaryKey") == null ? null : parseKey(t.get("primaryKey"));
            List<KeyModel> uniqueKeys = new ArrayList<>();
            for (Object k : Json.list(t.get("uniqueKeys"), "uniqueKeys")) uniqueKeys.add(parseKey(k));
            List<ForeignKeyModel> foreignKeys = new ArrayList<>();
            for (Object fo : Json.list(t.get("foreignKeys"), "foreignKeys")) {
                Map<String, Object> f = Json.object(fo, "foreign key");
                foreignKeys.add(new ForeignKeyModel(Json.string(f, "name"), Json.stringList(f.get("columns")),
                        Json.string(f, "referencedSchema"), Json.string(f, "referencedTable"), Json.stringList(f.get("referencedColumns"))));
            }
            tables.add(new TableModel(Json.string(t, "schema"), Json.string(t, "name"), Json.string(t, "kind"), Json.optString(t, "comment"),
                    columns, primaryKey, uniqueKeys, foreignKeys));
        }
        return new SchemaSnapshot(new SchemaModel(tables, enums), Json.string(root, "migrations"));
    }

    private static KeyModel parseKey(Object o) {
        Map<String, Object> k = Json.object(o, "key");
        return new KeyModel(Json.string(k, "name"), Json.stringList(k.get("columns")));
    }

    /** Reads a snapshot file. */
    public static SchemaSnapshot read(Path file) {
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the LxrinQL snapshot " + file, e);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid LxrinQL snapshot " + file + ": " + e.getMessage(), e);
        }
    }

    /** Writes the snapshot, only if its content changed. */
    public void write(Path file) {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            byte[] bytes = toJson().getBytes(StandardCharsets.UTF_8);
            if (!Files.exists(file) || !java.util.Arrays.equals(Files.readAllBytes(file), bytes)) Files.write(file, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write the LxrinQL snapshot " + file, e);
        }
    }

    /** Returns the model restricted to the tables the configuration selects. */
    public SchemaModel select(CodegenConfig config) {
        List<TableModel> tables = new ArrayList<>();
        for (TableModel t : model.tables()) if (config.schemas().contains(t.schema()) && config.included(t.name())) tables.add(t);
        return new SchemaModel(tables, model.enums());
    }

    /**
     * Hashes migration and script files: every regular file under the given files and
     * directories, by path relative to its root and content, with line endings
     * normalised to {@code \n}. Missing paths are skipped.
     */
    public static String hash(List<Path> roots) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int r = 0; r < roots.size(); r++) {
                Path root = roots.get(r);
                if (!Files.exists(root)) continue;
                Map<String, Path> files = new LinkedHashMap<>();
                if (Files.isDirectory(root)) {
                    try (Stream<Path> s = Files.walk(root)) {
                        s.filter(Files::isRegularFile).sorted().forEach(f -> files.put(root.relativize(f).toString().replace('\\', '/'), f));
                    }
                } else {
                    files.put(root.getFileName().toString(), root);
                }
                for (Map.Entry<String, Path> f : files.entrySet()) {
                    String content = Files.readString(f.getValue(), StandardCharsets.UTF_8).replace("\r\n", "\n");
                    digest.update((r + ":" + f.getKey() + "\n" + content.length() + "\n" + content).getBytes(StandardCharsets.UTF_8));
                }
            }
            StringBuilder hex = new StringBuilder("sha256:");
            for (byte x : digest.digest()) hex.append(String.format("%02x", x));
            return hex.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot hash the migrations", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A minimal JSON reader and writer for the snapshot format; no dependency. */
    static final class Json {

        private final String text;
        private int pos;

        private Json(String text) {
            this.text = text;
        }

        static String quote(String s) {
            StringBuilder b = new StringBuilder("\"");
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"': b.append("\\\""); break;
                    case '\\': b.append("\\\\"); break;
                    case '\n': b.append("\\n"); break;
                    case '\r': b.append("\\r"); break;
                    case '\t': b.append("\\t"); break;
                    default:
                        if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                        else b.append(c);
                }
            }
            return b.append('"').toString();
        }

        static String strings(List<String> values) {
            StringBuilder b = new StringBuilder("[");
            for (int i = 0; i < values.size(); i++) b.append(i == 0 ? "" : ", ").append(quote(values.get(i)));
            return b.append(']').toString();
        }

        static Object parse(String text) {
            Json json = new Json(text);
            Object value = json.value();
            json.skipWhitespace();
            if (json.pos != text.length()) throw json.error("unexpected content");
            return value;
        }

        @SuppressWarnings("unchecked")
        static Map<String, Object> object(Object o, String what) {
            if (!(o instanceof Map)) throw new IllegalArgumentException("expected an object for " + what);
            return (Map<String, Object>) o;
        }

        @SuppressWarnings("unchecked")
        static List<Object> list(Object o, String what) {
            if (!(o instanceof List)) throw new IllegalArgumentException("expected an array for " + what);
            return (List<Object>) o;
        }

        static String string(Map<String, Object> o, String key) {
            Object v = o.get(key);
            if (!(v instanceof String)) throw new IllegalArgumentException("expected a string for \"" + key + "\"");
            return (String) v;
        }

        static String optString(Map<String, Object> o, String key) {
            return o.get(key) == null ? null : string(o, key);
        }

        static List<String> stringList(Object o) {
            List<String> result = new ArrayList<>();
            for (Object v : list(o, "a list of names")) {
                if (!(v instanceof String)) throw new IllegalArgumentException("expected strings in a list of names");
                result.add((String) v);
            }
            return result;
        }

        private Object value() {
            skipWhitespace();
            if (pos >= text.length()) throw error("unexpected end");
            char c = text.charAt(pos);
            switch (c) {
                case '{': return parseObject();
                case '[': return parseArray();
                case '"': return parseString();
                case 't': return literal("true", Boolean.TRUE);
                case 'f': return literal("false", Boolean.FALSE);
                case 'n': return literal("null", null);
                default:
                    if (c == '-' || Character.isDigit(c)) return parseNumber();
                    throw error("unexpected character '" + c + "'");
            }
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> result = new LinkedHashMap<>();
            pos++;
            skipWhitespace();
            if (peek('}')) return result;
            while (true) {
                skipWhitespace();
                if (pos >= text.length() || text.charAt(pos) != '"') throw error("expected a name");
                String name = parseString();
                skipWhitespace();
                expect(':');
                result.put(name, value());
                skipWhitespace();
                if (peek('}')) return result;
                expect(',');
            }
        }

        private List<Object> parseArray() {
            List<Object> result = new ArrayList<>();
            pos++;
            skipWhitespace();
            if (peek(']')) return result;
            while (true) {
                result.add(value());
                skipWhitespace();
                if (peek(']')) return result;
                expect(',');
            }
        }

        private String parseString() {
            StringBuilder b = new StringBuilder();
            pos++;
            while (pos < text.length()) {
                char c = text.charAt(pos++);
                if (c == '"') return b.toString();
                if (c != '\\') {
                    b.append(c);
                    continue;
                }
                if (pos >= text.length()) break;
                char e = text.charAt(pos++);
                switch (e) {
                    case 'n': b.append('\n'); break;
                    case 'r': b.append('\r'); break;
                    case 't': b.append('\t'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'u':
                        if (pos + 4 > text.length()) throw error("invalid escape");
                        b.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                        break;
                    default: b.append(e);
                }
            }
            throw error("unterminated string");
        }

        private Long parseNumber() {
            int start = pos;
            if (text.charAt(pos) == '-') pos++;
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
            try {
                return Long.parseLong(text.substring(start, pos));
            } catch (NumberFormatException e) {
                throw error("invalid number");
            }
        }

        private Object literal(String word, Object value) {
            if (!text.startsWith(word, pos)) throw error("unexpected token");
            pos += word.length();
            return value;
        }

        private boolean peek(char c) {
            if (pos < text.length() && text.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        private void expect(char c) {
            skipWhitespace();
            if (!peek(c)) throw error("expected '" + c + "'");
        }

        private void skipWhitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++;
        }

        private IllegalArgumentException error(String message) {
            int line = 1;
            for (int i = 0; i < Math.min(pos, text.length()); i++) if (text.charAt(i) == '\n') line++;
            return new IllegalArgumentException(message + " at line " + line);
        }
    }
}
