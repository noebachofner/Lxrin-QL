package ch.lxrin.ql.exec;

import ch.lxrin.ql.bind.BindMap;

/**
 * Executes rendered SQL. The SQL uses named placeholders ({@code :name});
 * the values are in the {@link BindMap}.
 *
 * <p>Implementations:</p>
 * <ul>
 *   <li>{@link JdbcSqlExecutor} – plain JDBC ({@code DataSource} or {@code Connection}), part of the core</li>
 *   <li>your own – e.g. a mock in unit tests or an adapter to a framework's
 *       SQL service (a few lines: render, forward SQL and {@link BindMap})</li>
 * </ul>
 *
 * @see SqlExecutors#setDefault(SqlExecutor)
 */
public interface SqlExecutor {

    /**
     * Executes a query and returns all rows as {@code result[row][column]}.
     *
     * @param sql   SQL with {@code :name} placeholders
     * @param binds bind parameters
     * @return rows &times; columns (never {@code null} for well-behaved implementations)
     */
    Object[][] select(String sql, BindMap binds);

    /**
     * Executes a data-modifying statement.
     *
     * @param sql   SQL with {@code :name} placeholders
     * @param binds bind parameters
     * @return number of affected rows
     */
    int execute(String sql, BindMap binds);
}
