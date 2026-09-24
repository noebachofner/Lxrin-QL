package ch.lxrin.ql.error;

import ch.lxrin.ql.render.RenderedSql;
import ch.lxrin.ql.schema.Constraint;
import ch.lxrin.ql.schema.Constraints;

import java.lang.reflect.Method;
import java.sql.SQLException;

/**
 * Translates {@link SQLException}s into the LxrinQL exception hierarchy by
 * SQLSTATE. Constraint, table and schema names are read from the PostgreSQL
 * driver's server error message (through reflection, so core does not depend
 * on the driver) and resolved against the generated table metadata.
 */
public final class SqlErrors {

    private SqlErrors() {}

    /** Translates an exception raised by {@code sql}. */
    public static DataAccessException translate(SQLException e, RenderedSql sql) {
        String state = e.getSQLState() == null ? "" : e.getSQLState();
        String sqlText = sql == null ? null : sql.sql();
        String message = e.getMessage() + " [SQLSTATE " + state + "]" + (sql == null ? "" : "\nSQL: " + sql);
        String schema = serverDetail(e, "getSchema");
        String table = serverDetail(e, "getTable");
        String constraintName = serverDetail(e, "getConstraint");
        Constraint constraint = Constraints.find(schema, constraintName).orElse(null);
        switch (state) {
            case "23505":
                return new UniqueViolationException(message, sqlText, e, constraintName, table, constraint);
            case "23503":
                return new ForeignKeyViolationException(message, sqlText, e, constraintName, table, constraint);
            case "23502":
                return new NotNullViolationException(message, sqlText, e, constraintName, table, constraint);
            case "23514":
                return new CheckViolationException(message, sqlText, e, constraintName, table, constraint);
            case "23P01":
                return new ExclusionViolationException(message, sqlText, e, constraintName, table, constraint);
            case "40001":
                return new SerializationFailureException(message, sqlText, e);
            case "40P01":
                return new DeadlockException(message, sqlText, e);
            case "57014":
                return new QueryTimeoutException(message, sqlText, e);
            case "55P03":
                return new LockNotAvailableException(message, sqlText, e);
            default:
                if (state.startsWith("23")) {
                    return new ConstraintViolationException(message, sqlText, e, constraintName, table, constraint);
                }
                if (state.startsWith("40")) return new TransientDataAccessException(message, sqlText, e);
                return new DataAccessException(message, sqlText, e);
        }
    }

    private static String serverDetail(SQLException e, String getter) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            try {
                Method m = t.getClass().getMethod("getServerErrorMessage");
                Object details = m.invoke(t);
                if (details == null) return null;
                Object value = details.getClass().getMethod(getter).invoke(details);
                return value == null ? null : value.toString();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // not a PostgreSQL exception
            }
        }
        return null;
    }
}
