package ch.lxrin.ql.error;

import ch.lxrin.ql.schema.Constraint;

import java.sql.SQLException;

/** A {@code CHECK} constraint was violated: SQLSTATE {@code 23514}. */
public class CheckViolationException extends ConstraintViolationException {

    private static final long serialVersionUID = 1L;

    /** See {@link ConstraintViolationException#ConstraintViolationException}. */
    public CheckViolationException(String message, String sql, SQLException cause, String constraintName, String tableName,
            Constraint constraint) {
        super(message, sql, cause, constraintName, tableName, constraint);
    }
}
