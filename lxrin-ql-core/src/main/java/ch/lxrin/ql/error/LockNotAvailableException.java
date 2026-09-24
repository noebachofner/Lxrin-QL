package ch.lxrin.ql.error;

import java.sql.SQLException;

/** A lock could not be acquired ({@code NOWAIT} or {@code lock_timeout}): SQLSTATE {@code 55P03}. */
public class LockNotAvailableException extends TransientDataAccessException {

    private static final long serialVersionUID = 1L;

    /** See {@link DataAccessException#DataAccessException}. */
    public LockNotAvailableException(String message, String sql, SQLException cause) {
        super(message, sql, cause);
    }
}
