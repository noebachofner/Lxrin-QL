package ch.lxrin.ql.error;

import java.sql.SQLException;

/** A deadlock was detected: SQLSTATE {@code 40P01}. Retry the transaction. */
public class DeadlockException extends TransientDataAccessException {

    private static final long serialVersionUID = 1L;

    /** See {@link DataAccessException#DataAccessException}. */
    public DeadlockException(String message, String sql, SQLException cause) {
        super(message, sql, cause);
    }
}
