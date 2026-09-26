package ch.lxrin.ql.error;

import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.schema.Constraint;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** A constraint was violated. The constraint is resolved against the generated table metadata. */
public class ConstraintViolationException extends DataAccessException {

    private static final long serialVersionUID = 1L;

    private final String constraintName;
    private final String tableName;
    private final transient Constraint constraint;

    /**
     * @param message        description
     * @param sql            the failing SQL
     * @param cause          the JDBC exception
     * @param constraintName the constraint name reported by PostgreSQL, may be {@code null}
     * @param tableName      the table name reported by PostgreSQL, may be {@code null}
     * @param constraint     the matching generated constraint, may be {@code null}
     */
    public ConstraintViolationException(String message, String sql, SQLException cause, String constraintName,
                                        String tableName, Constraint constraint) {
        super(message, sql, cause);
        this.constraintName = constraintName;
        this.tableName = tableName;
        this.constraint = constraint;
    }

    /** Returns the constraint name reported by PostgreSQL. */
    public Optional<String> constraintName() {
        return Optional.ofNullable(constraintName);
    }

    /** Returns the table name reported by PostgreSQL. */
    public Optional<String> tableName() {
        return Optional.ofNullable(tableName);
    }

    /** Returns the violated constraint as declared in the generated tables, e.g. {@code USERS.UK_EMAIL}. */
    public Optional<Constraint> constraint() {
        return Optional.ofNullable(constraint);
    }

    /** Returns the columns of the violated constraint, if known. */
    public List<Column<?>> columns() {
        return constraint == null ? List.of() : constraint.columns();
    }

    /** Returns {@code true} if the given constraint was violated. */
    public boolean isViolated(Constraint c) {
        return c != null && c.name().equals(constraintName);
    }
}
