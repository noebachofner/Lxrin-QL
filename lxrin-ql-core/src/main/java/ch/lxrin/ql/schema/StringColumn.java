package ch.lxrin.ql.schema;

import ch.lxrin.ql.dsl.StringField;
import ch.lxrin.ql.types.DataType;

/** A text column. */
public class StringColumn extends Column<String> implements StringField {

    /** Creates the column; see {@link Column#Column(Table, String, DataType, int)}. */
    protected StringColumn(Table<?> table, String name, DataType<String> type, int flags) {
        super(table, name, type, flags);
    }
}
