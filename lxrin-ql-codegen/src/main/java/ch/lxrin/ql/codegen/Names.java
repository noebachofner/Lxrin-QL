package ch.lxrin.ql.codegen;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Helpers to derive Java identifiers from SQL names. */
public final class Names {

    private static final Set<String> KEYWORDS = Set.of("abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for",
            "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw",
            "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null", "var", "record", "yield", "sealed",
            "permits", "non-sealed", "_");

    private static final Map<String, String> IRREGULAR = Map.of("people", "person", "children", "child", "men", "man",
            "women", "woman", "mice", "mouse", "geese", "goose", "teeth", "tooth", "feet", "foot");

    private static final Set<String> UNCOUNTABLE = Set.of("data", "information", "equipment", "news", "series", "species",
            "metadata", "status", "access", "audit", "history", "settings");

    private Names() {}

    /** {@code display_name} → {@code displayName} (valid Java identifier). */
    public static String camel(String sql) {
        String pascal = pascal(sql);
        String camel = pascal.isEmpty() ? pascal : Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
        return javaIdentifier(camel);
    }

    /** {@code order_status} → {@code OrderStatus}. */
    public static String pascal(String sql) {
        StringBuilder sb = new StringBuilder();
        boolean upper = true;
        for (char c : sql.toCharArray()) {
            if (!Character.isLetterOrDigit(c)) {
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(c);
            }
        }
        String result = sb.toString();
        if (result.isEmpty() || Character.isDigit(result.charAt(0))) result = "_" + result;
        return result;
    }

    /** {@code displayName} or {@code display-name} → {@code DISPLAY_NAME}. */
    public static String upperSnake(String name) {
        StringBuilder sb = new StringBuilder();
        char previous = 0;
        for (char c : name.toCharArray()) {
            if (!Character.isLetterOrDigit(c)) {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '_') sb.append('_');
            } else {
                if (Character.isUpperCase(c) && Character.isLowerCase(previous)) sb.append('_');
                sb.append(Character.toUpperCase(c));
            }
            previous = c;
        }
        String result = sb.toString().replaceAll("_+$", "");
        if (result.isEmpty() || Character.isDigit(result.charAt(0))) result = "_" + result;
        return result;
    }

    /** Appends {@code _} to Java keywords. */
    public static String javaIdentifier(String name) {
        return KEYWORDS.contains(name) ? name + "_" : name;
    }

    /**
     * Singularises the last word of a snake-case name with simple English
     * rules: {@code users} → {@code user}, {@code categories} → {@code category},
     * {@code addresses} → {@code address}, {@code people} → {@code person}.
     */
    public static String singular(String name) {
        int idx = name.lastIndexOf('_');
        String head = idx < 0 ? "" : name.substring(0, idx + 1);
        String word = idx < 0 ? name : name.substring(idx + 1);
        String lower = word.toLowerCase(Locale.ROOT);
        String result;
        if (IRREGULAR.containsKey(lower)) result = IRREGULAR.get(lower);
        else if (UNCOUNTABLE.contains(lower) || lower.length() <= 2) result = word;
        else if (lower.endsWith("ies") && lower.length() > 3) result = word.substring(0, word.length() - 3) + "y";
        else if (lower.endsWith("sses") || lower.endsWith("shes") || lower.endsWith("ches") || lower.endsWith("xes")
                || lower.endsWith("zzes")) result = word.substring(0, word.length() - 2);
        else if (lower.endsWith("uses")) result = word.substring(0, word.length() - 2);
        else if (lower.endsWith("ss") || lower.endsWith("us") || lower.endsWith("is")) result = word;
        else if (lower.endsWith("s")) result = word.substring(0, word.length() - 1);
        else result = word;
        return head + result;
    }
}
