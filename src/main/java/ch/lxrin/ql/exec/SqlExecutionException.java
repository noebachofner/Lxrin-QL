package ch.lxrin.ql.exec;

/**
 * Unchecked wrapper for {@link java.sql.SQLException}s thrown while executing
 * a statement. The message contains the failing SQL; {@link #getCause()}
 * holds the original exception (with SQLSTATE).
 */
public class SqlExecutionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message description including the SQL
     * @param cause   the original exception
     */
    public SqlExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Returns the SQLSTATE of the underlying {@link java.sql.SQLException}, or {@code null}. */
    public String getSqlState() {
        return getCause() instanceof java.sql.SQLException ? ((java.sql.SQLException) getCause()).getSQLState() : null;
    }
}
