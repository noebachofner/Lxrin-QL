package ch.lxrin.ql.schema;

import java.util.List;

/** A unique constraint or unique index. */
public final class UniqueKey implements Constraint {

    private final String name;
    private final Table<?> table;
    private final List<Column<?>> columns;

    UniqueKey(String name, Table<?> table, List<Column<?>> columns) {
        this.name = name;
        this.table = table;
        this.columns = List.copyOf(columns);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Table<?> table() {
        return table;
    }

    @Override
    public List<Column<?>> columns() {
        return columns;
    }

    @Override
    public String toString() {
        return "UniqueKey " + name + columns;
    }
}
