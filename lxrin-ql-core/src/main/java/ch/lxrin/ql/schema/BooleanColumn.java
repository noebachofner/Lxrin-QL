package ch.lxrin.ql.schema;

import ch.lxrin.ql.dsl.Condition;
import ch.lxrin.ql.types.DataType;

/** A {@code boolean} column. It is a {@link Condition}, so {@code where(USERS.ACTIVE)} works. */
public class BooleanColumn extends Column<Boolean> implements Condition {

    /** Creates the column; see {@link Column#Column(Table, String, DataType, int)}. */
    protected BooleanColumn(Table<?> table, String name, DataType<Boolean> type, int flags) {
        super(table, name, type, flags);
    }

    @Override
    public DataType<Boolean> type() {
        return super.type();
    }
}
