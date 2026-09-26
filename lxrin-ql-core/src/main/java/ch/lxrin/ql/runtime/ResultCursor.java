package ch.lxrin.ql.runtime;

import java.sql.SQLException;

/** Rows of a query, read one at a time. */
public interface ResultCursor extends AutoCloseable {

    /** Returns the next row, or {@code null} after the last one. */
    Object[] next() throws SQLException;

    /** Releases the underlying result set and statement. */
    @Override
    void close() throws SQLException;
}
