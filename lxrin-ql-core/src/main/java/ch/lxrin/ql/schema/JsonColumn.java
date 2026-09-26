package ch.lxrin.ql.schema;

import ch.lxrin.ql.dsl.JsonField;
import ch.lxrin.ql.types.DataType;

/**
 * A {@code json} or {@code jsonb} column.
 *
 * @param <T> the Java type (JSON text or a mapped class)
 */
public class JsonColumn<T> extends Column<T> implements JsonField<T> {

    /** Creates the column; see {@link Column#Column(Table, String, DataType, int)}. */
    protected JsonColumn(Table<?> table, String name, DataType<T> type, int flags) {
        super(table, name, type, flags);
    }
}
