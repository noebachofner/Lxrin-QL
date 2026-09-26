package ch.lxrin.ql.schema;

import ch.lxrin.ql.dsl.TemporalField;
import ch.lxrin.ql.types.DataType;

/**
 * A date, time or timestamp column.
 *
 * @param <T> the Java type
 */
public class TemporalColumn<T> extends Column<T> implements TemporalField<T> {

    /** Creates the column; see {@link Column#Column(Table, String, DataType, int)}. */
    protected TemporalColumn(Table<?> table, String name, DataType<T> type, int flags) {
        super(table, name, type, flags);
    }
}
