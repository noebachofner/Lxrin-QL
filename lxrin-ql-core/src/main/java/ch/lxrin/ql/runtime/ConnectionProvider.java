package ch.lxrin.ql.runtime;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Supplies the connection for one statement and takes it back afterwards.
 * The Spring integration uses {@code DataSourceUtils}, so statements join
 * Spring-managed transactions.
 */
public interface ConnectionProvider {

    /** Returns the connection for one statement. */
    Connection acquire() throws SQLException;

    /** Called after the statement; closes the connection or leaves it to the transaction. */
    void release(Connection connection) throws SQLException;
}
