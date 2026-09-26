package ch.lxrin.ql.spi;

import ch.lxrin.ql.schema.Column;

import java.util.Collections;
import java.util.Map;

/**
 * A row written by a statement, with the columns listeners requested through
 * {@code requestReturning(..)}.
 */
public final class AffectedRow {

    private final Map<Column<?>, Object> values;
    private final Boolean inserted;

    /**
     * @param values   the returned column values
     * @param inserted for upserts: {@code true} if the row was inserted, {@code false} if updated; else {@code null}
     */
    public AffectedRow(Map<Column<?>, Object> values, Boolean inserted) {
        this.values = Collections.unmodifiableMap(values);
        this.inserted = inserted;
    }

    /** Returns the value of a requested column. */
    @SuppressWarnings("unchecked")
    public <T> T get(Column<T> column) {
        if (!values.containsKey(column)) {
            throw new IllegalArgumentException("column " + column + " was not requested with requestReturning(..)");
        }
        return (T) values.get(column);
    }

    /** Returns all returned values by column. */
    public Map<Column<?>, Object> values() {
        return values;
    }

    /** For an upsert: {@code true} if this row was inserted; {@code true} for plain inserts too. */
    public boolean inserted() {
        return inserted == null || inserted;
    }

    @Override
    public String toString() {
        return "AffectedRow" + values;
    }
}
