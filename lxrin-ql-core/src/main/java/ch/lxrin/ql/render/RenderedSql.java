package ch.lxrin.ql.render;

import java.util.List;

/**
 * A rendered statement: SQL with {@code ?} placeholders plus its typed binds.
 *
 * @param sql   SQL text
 * @param binds bind parameters in placeholder order
 */
public record RenderedSql(String sql, List<Bind<?>> binds) {

    /** Creates an instance with an immutable copy of the binds. */
    public RenderedSql {
        binds = List.copyOf(binds);
    }

    /** Returns the SQL followed by the (redacted) bind values, for logs and error messages. */
    @Override
    public String toString() {
        return binds.isEmpty() ? sql : sql + " " + binds;
    }
}
