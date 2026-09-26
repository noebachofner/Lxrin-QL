package ch.lxrin.ql.error;

import ch.lxrin.ql.schema.Constraint;

import java.sql.SQLException;

/** A {@code NOT NULL} constraint was violated: SQLSTATE {@code 23502}. */
public class NotNullViolationException extends ConstraintViolationException {

    private static final long serialVersionUID = 1L;

    /** See {@link ConstraintViolationException#ConstraintViolationException}. */
    public NotNullViolationException(String message, String sql, SQLException cause, String constraintName, String tableName,
            Constraint constraint) {
        super(message, sql, cause, constraintName, tableName, constraint);
    }
}
