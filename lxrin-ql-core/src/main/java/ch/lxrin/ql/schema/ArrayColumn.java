package ch.lxrin.ql.schema;

import ch.lxrin.ql.dsl.ArrayField;
import ch.lxrin.ql.types.DataType;

/**
 * An array column.
 *
 * @param <E> the element type
 */
public class ArrayColumn<E> extends Column<E[]> implements ArrayField<E> {

    /** Creates the column; see {@link Column#Column(Table, String, DataType, int)}. */
    protected ArrayColumn(Table<?> table, String name, DataType<E[]> type, int flags) {
        super(table, name, type, flags);
    }
}
