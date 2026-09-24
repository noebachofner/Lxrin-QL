package ch.lxrin.ql.render;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validation and quoting of SQL identifiers (table, column and alias names).
 *
 * <p>An identifier is written without quotes when it is a lower-case name
 * that is not a reserved word; otherwise it is double-quoted, so names such
 * as {@code "user"}, {@code "Order"} or {@code "first name"} are always
 * valid.</p>
 */
public final class Identifiers {

    private static final Pattern PLAIN = Pattern.compile("[a-z_][a-z0-9_$]*");
    private static final Pattern FUNCTION_NAME = Pattern.compile(
            "[A-Za-z_][A-Za-z0-9_$]*(\\.[A-Za-z_][A-Za-z0-9_$]*)?");

    /** PostgreSQL reserved key words (including those that may only be function or type names). */
    private static final Set<String> RESERVED = Set.of(
            "all", "analyse", "analyze", "and", "any", "array", "as", "asc", "asymmetric", "authorization",
            "binary", "both", "case", "cast", "check", "collate", "collation", "column", "concurrently",
            "constraint", "create", "cross", "current_catalog", "current_date", "current_role",
            "current_schema", "current_time", "current_timestamp", "current_user", "default", "deferrable",
            "desc", "distinct", "do", "else", "end", "except", "false", "fetch", "for", "foreign", "freeze",
            "from", "full", "grant", "group", "having", "ilike", "in", "initially", "inner", "intersect",
            "into", "is", "isnull", "join", "lateral", "leading", "left", "like", "limit", "localtime",
            "localtimestamp", "natural", "not", "notnull", "null", "offset", "on", "only", "or", "order",
            "outer", "overlaps", "placing", "primary", "references", "returning", "right", "select",
            "session_user", "similar", "some", "symmetric", "system_user", "table", "tablesample", "then",
            "to", "trailing", "true", "union", "unique", "user", "using", "variadic", "verbose", "when",
            "where", "window", "with");

    private Identifiers() {}

    /** Returns the identifier, double-quoted if necessary. */
    public static String quote(String identifier) {
        requireName(identifier);
        if (PLAIN.matcher(identifier).matches() && !RESERVED.contains(identifier)) return identifier;
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    /** Checks that a name is usable as an identifier: not blank and without NUL characters. */
    public static String requireName(String name) {
        if (name == null || name.isBlank() || name.indexOf('\0') >= 0 || name.length() > 63) {
            throw new IllegalArgumentException("invalid SQL identifier: " + name);
        }
        return name;
    }

    /**
     * Checks a function name such as {@code similarity} or {@code my_schema.calc}.
     * Function names are written without quotes, so only letters, digits,
     * {@code _} and {@code $} are allowed.
     */
    public static String requireFunctionName(String name) {
        if (name == null || !FUNCTION_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("invalid function name: " + name);
        }
        return name;
    }
}
