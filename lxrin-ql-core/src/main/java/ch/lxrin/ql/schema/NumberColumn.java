package ch.lxrin.ql.schema;

import ch.lxrin.ql.dsl.NumberField;
import ch.lxrin.ql.types.DataType;

/**
 * A numeric column.
 *
 * @param <N> the Java number type
 */
public class NumberColumn<N extends Number> extends Column<N> implements NumberField<N> {

    /** Creates the column; see {@link Column#Column(Table, String, DataType, int)}. */
    protected NumberColumn(Table<?> table, String name, DataType<N> type, int flags) {
        super(table, name, type, flags);
    }
}
