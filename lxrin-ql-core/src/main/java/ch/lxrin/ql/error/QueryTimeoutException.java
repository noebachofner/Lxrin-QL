package ch.lxrin.ql.error;

import java.sql.SQLException;

/** The statement was cancelled because of a timeout: SQLSTATE {@code 57014}. */
public class QueryTimeoutException extends TransientDataAccessException {

    private static final long serialVersionUID = 1L;

    /** See {@link DataAccessException#DataAccessException}. */
    public QueryTimeoutException(String message, String sql, SQLException cause) {
        super(message, sql, cause);
    }
}
