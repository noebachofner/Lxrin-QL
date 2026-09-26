package ch.lxrin.ql.types;

import java.util.regex.Pattern;

/** Validation of SQL type names. */
final class SqlNames {

    private static final Pattern TYPE_NAME = Pattern.compile(
            "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?( [A-Za-z_]+)*(\\([0-9, ]+\\))?(\\[\\])*|\"[^\"]+\"(\\.\"[^\"]+\")?");

    private SqlNames() {}

    static String requireTypeName(String name) {
        if (name == null || !TYPE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("invalid SQL type name: " + name);
        }
        return name;
    }
}
