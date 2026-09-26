package ch.lxrin.ql.schema;

import ch.lxrin.ql.dsl.TsVectorField;
import ch.lxrin.ql.types.DataType;

/** A {@code tsvector} column. */
public class TsVectorColumn extends Column<String> implements TsVectorField {

    /** Creates the column; see {@link Column#Column(Table, String, DataType, int)}. */
    protected TsVectorColumn(Table<?> table, String name, DataType<String> type, int flags) {
        super(table, name, type, flags);
    }
}
