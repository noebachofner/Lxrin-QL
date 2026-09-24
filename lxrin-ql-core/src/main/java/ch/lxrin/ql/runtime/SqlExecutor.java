package ch.lxrin.ql.runtime;

import ch.lxrin.ql.render.Bind;
import ch.lxrin.ql.render.RenderedSql;
import ch.lxrin.ql.types.DataType;
import ch.lxrin.ql.types.ValueContext;

import java.sql.SQLException;
import java.util.List;

/**
 * Sends rendered statements to the database. {@link JdbcExecutor} is the
 * implementation for JDBC; {@code lxrin-ql-test} has a mock for unit tests.
 *
 * <p>Values are bound and read with their {@link DataType}s; the connection
 * is available through {@link ValueContext#connection()}.</p>
 */
public interface SqlExecutor {

    /**
     * Runs a query.
     *
     * @param resultTypes the type of every result column
     * @param fetchSize   the JDBC fetch size, 0 for the driver default
     */
    ResultCursor query(ValueContext context, RenderedSql sql, List<DataType<?>> resultTypes, int fetchSize) throws SQLException;

    /**
     * Runs a data-modifying statement.
     *
     * @param returningTypes the types of the {@code RETURNING} columns, empty without {@code RETURNING}
     */
    UpdateResult update(ValueContext context, RenderedSql sql, List<DataType<?>> returningTypes) throws SQLException;

    /**
     * Runs one statement with several parameter sets as a JDBC batch.
     *
     * @param sql              SQL with {@code ?} placeholders, without {@code RETURNING}
     * @param parameterSets    the binds of each execution
     * @param returningColumns column names whose values are returned (generated keys), may be empty
     * @param returningTypes   their types
     */
    BatchResult batch(ValueContext context, String sql, List<List<Bind<?>>> parameterSets, List<String> returningColumns,
                      List<DataType<?>> returningTypes) throws SQLException;
}
