package ch.lxrin.ql.schema;

import ch.lxrin.ql.dsl.Row;
import ch.lxrin.ql.types.DataType;

/**
 * A table that is not generated, created with {@code Sql.table(..)}. Columns
 * are declared on first use with {@link #field(String, DataType)}.
 */
public final class AdHocTable extends Table<Row> {

    /**
     * @param schema the schema, or {@code null}
     * @param name   the table name
     * @param alias  the alias, or {@code null}
     */
    public AdHocTable(String schema, String name, String alias) {
        super(schema, name, alias);
    }

    /** Returns the column with the given name and type, declaring it on first use. */
    public synchronized <T> Column<T> field(String columnName, DataType<T> type) {
        if (column(columnName).isPresent()) return column(columnName, type);
        return column(columnName, type, 0);
    }

    @Override
    public AdHocTable as(String alias) {
        AdHocTable copy = new AdHocTable(schema(), name(), alias);
        for (Column<?> c : columns()) copy.copyColumn(c);
        return copy;
    }

    private <T> void copyColumn(Column<T> c) {
        column(c.name(), c.type(), c.flags());
    }

    @Override
    public Row mapRow(Row row) {
        return row;
    }
}
