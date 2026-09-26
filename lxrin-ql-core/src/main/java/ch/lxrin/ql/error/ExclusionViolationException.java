package ch.lxrin.ql.error;

import ch.lxrin.ql.schema.Constraint;

import java.sql.SQLException;

/** An exclusion constraint was violated: SQLSTATE {@code 23P01}. */
public class ExclusionViolationException extends ConstraintViolationException {

    private static final long serialVersionUID = 1L;

    /** See {@link ConstraintViolationException#ConstraintViolationException}. */
    public ExclusionViolationException(String message, String sql, SQLException cause, String constraintName, String tableName,
            Constraint constraint) {
        super(message, sql, cause, constraintName, tableName, constraint);
    }
}
