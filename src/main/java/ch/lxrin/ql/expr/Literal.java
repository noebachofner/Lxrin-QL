package ch.lxrin.ql.expr;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * A value that is written into the SQL text as an escaped literal instead of
 * being bound as a parameter.
 *
 * <p>Created with {@code inline(value)}. Prefer {@code val(value)} for user
 * input; inline literals are useful for constants that should be visible in
 * the SQL (for example to let the planner use a partial index).</p>
 *
 * <table>
 *   <caption>Rendering</caption>
 *   <tr><th>Java type</th><th>SQL</th></tr>
 *   <tr><td>{@code null}</td><td>{@code NULL}</td></tr>
 *   <tr><td>{@code Number}</td><td>{@code 42}, {@code 3.14}</td></tr>
 *   <tr><td>{@code Boolean}</td><td>{@code TRUE} / {@code FALSE}</td></tr>
 *   <tr><td>{@code LocalDate}</td><td>{@code DATE '2024-01-31'}</td></tr>
 *   <tr><td>{@code LocalDateTime}</td><td>{@code TIMESTAMP '2024-01-31T12:00'}</td></tr>
 *   <tr><td>{@code OffsetDateTime}</td><td>{@code TIMESTAMPTZ '...'}</td></tr>
 *   <tr><td>{@code UUID}</td><td>{@code UUID '...'}</td></tr>
 *   <tr><td>anything else</td><td>{@code 'text'} with quotes doubled</td></tr>
 * </table>
 */
public final class Literal implements Expression {

    private final Object value;

    /** @param value the literal value */
    public Literal(Object value) {
        this.value = value;
    }

    @Override
    public void render(RenderContext ctx) {
        ctx.append(toLiteral(value));
    }

    /** Converts a Java value into an escaped SQL literal. */
    public static String toLiteral(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Number) {
            String s = value.toString();
            if (!s.matches("-?[0-9]+(\\.[0-9]+)?([eE][-+]?[0-9]+)?")) {
                throw new IllegalArgumentException("not a finite number: " + s);
            }
            return s;
        }
        if (value instanceof Boolean) return ((Boolean) value) ? "TRUE" : "FALSE";
        if (value instanceof LocalDate) return "DATE " + quote(value.toString());
        if (value instanceof LocalDateTime) return "TIMESTAMP " + quote(value.toString());
        if (value instanceof LocalTime) return "TIME " + quote(value.toString());
        if (value instanceof OffsetDateTime || value instanceof ZonedDateTime) {
            Object v = value instanceof ZonedDateTime ? ((ZonedDateTime) value).toOffsetDateTime() : value;
            return "TIMESTAMPTZ " + quote(v.toString());
        }
        if (value instanceof UUID) return "UUID " + quote(value.toString());
        if (value instanceof Enum) return quote(((Enum<?>) value).name());
        return quote(value.toString());
    }

    /** Wraps text in single quotes, doubling embedded quotes. */
    public static String quote(String text) {
        if (text.indexOf('\0') >= 0) throw new IllegalArgumentException("NUL character is not allowed in SQL literals");
        return "'" + text.replace("'", "''") + "'";
    }
}
