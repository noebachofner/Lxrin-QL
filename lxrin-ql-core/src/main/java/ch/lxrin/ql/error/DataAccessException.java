package ch.lxrin.ql.error;

import java.sql.SQLException;

/** A statement failed in the database. */
public class DataAccessException extends LxrinQlException {

    private static final long serialVersionUID = 1L;

    private final String sql;
    private final String sqlState;

    /**
     * @param message description including SQL and binds
     * @param sql     the failing SQL
     * @param cause   the JDBC exception
     */
    public DataAccessException(String message, String sql, SQLException cause) {
        super(message, cause);
        this.sql = sql;
        this.sqlState = cause == null ? null : cause.getSQLState();
    }

    /** Returns the failing SQL (with {@code ?} placeholders). */
    public String getSql() {
        return sql;
    }

    /** Returns the SQLSTATE, e.g. {@code 23505}. */
    public String getSqlState() {
        return sqlState;
    }
}
