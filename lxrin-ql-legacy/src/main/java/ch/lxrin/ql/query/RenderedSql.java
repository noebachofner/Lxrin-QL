package ch.lxrin.ql.query;

import ch.lxrin.ql.bind.BindMap;

/**
 * The result of rendering a statement: the SQL text with {@code :name}
 * placeholders and all bind parameters it needs.
 *
 * @param sql   SQL text
 * @param binds bind parameters, including the auto-generated {@code lqN} ones
 */
public record RenderedSql(String sql, BindMap binds) {

    @Override
    public String toString() {
        return sql + " " + binds;
    }
}
