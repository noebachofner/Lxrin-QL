package ch.lxrin.ql.render;

import ch.lxrin.ql.types.DataType;

import java.util.Arrays;

/**
 * A typed bind parameter of a rendered statement.
 *
 * @param type  the SQL type used to bind the value
 * @param value the value, may be {@code null}
 * @param <T>   the Java type
 */
public record Bind<T>(DataType<T> type, T value) {

    /** Creates a bind parameter after checking the value's Java type. */
    public static <T> Bind<T> of(DataType<T> type, Object value) {
        return new Bind<>(type, type.cast(value));
    }

    /** Returns the value for logs and error messages: sensitive values are shown as {@code ***}. */
    public String display() {
        if (value == null) return "null";
        if (type.sensitive()) return "***";
        if (value instanceof Object[]) return Arrays.deepToString((Object[]) value);
        if (value instanceof byte[]) return "<" + ((byte[]) value).length + " bytes>";
        String s = value.toString();
        return value instanceof CharSequence ? "'" + (s.length() > 200 ? s.substring(0, 200) + "…" : s) + "'" : s;
    }

    @Override
    public String toString() {
        return display();
    }
}
