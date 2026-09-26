package ch.lxrin.ql.schema;

import ch.lxrin.ql.dsl.RangeField;
import ch.lxrin.ql.types.DataType;

/** A range column ({@code daterange}, {@code tstzrange}, …), in its text form. */
public class RangeColumn extends Column<String> implements RangeField {

    /** Creates the column; see {@link Column#Column(Table, String, DataType, int)}. */
    protected RangeColumn(Table<?> table, String name, DataType<String> type, int flags) {
        super(table, name, type, flags);
    }
}
