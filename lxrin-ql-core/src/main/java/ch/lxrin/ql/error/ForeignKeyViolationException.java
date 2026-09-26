package ch.lxrin.ql.error;

import ch.lxrin.ql.schema.Constraint;

import java.sql.SQLException;

/** A foreign key was violated: SQLSTATE {@code 23503}. */
public class ForeignKeyViolationException extends ConstraintViolationException {

    private static final long serialVersionUID = 1L;

    /** See {@link ConstraintViolationException#ConstraintViolationException}. */
    public ForeignKeyViolationException(String message, String sql, SQLException cause, String constraintName, String tableName,
            Constraint constraint) {
        super(message, sql, cause, constraintName, tableName, constraint);
    }
}
