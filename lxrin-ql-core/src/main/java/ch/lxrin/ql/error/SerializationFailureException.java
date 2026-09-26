package ch.lxrin.ql.error;

import java.sql.SQLException;

/** The transaction could not be serialized: SQLSTATE {@code 40001}. Retry it. */
public class SerializationFailureException extends TransientDataAccessException {

    private static final long serialVersionUID = 1L;

    /** See {@link DataAccessException#DataAccessException}. */
    public SerializationFailureException(String message, String sql, SQLException cause) {
        super(message, sql, cause);
    }
}
