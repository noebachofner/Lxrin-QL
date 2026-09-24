package ch.lxrin.ql.schema;

import java.util.List;

/**
 * A named table constraint: a primary key, unique key or foreign key.
 * Constraint violations reported by PostgreSQL are mapped back to these
 * objects, see {@code UniqueViolationException#constraint()}.
 */
public interface Constraint {

    /** Returns the constraint name as known to PostgreSQL. */
    String name();

    /** Returns the table the constraint belongs to. */
    Table<?> table();

    /** Returns the constrained columns. */
    List<Column<?>> columns();
}
