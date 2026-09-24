package ch.lxrin.ql.error;

import java.sql.SQLException;

/** A failure that may succeed when retried (serialization failure, deadlock, timeout). */
public class TransientDataAccessException extends DataAccessException {

    private static final long serialVersionUID = 1L;

    /** See {@link DataAccessException#DataAccessException}. */
    public TransientDataAccessException(String message, String sql, SQLException cause) {
        super(message, sql, cause);
    }
}
