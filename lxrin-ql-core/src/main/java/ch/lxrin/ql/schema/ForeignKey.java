package ch.lxrin.ql.schema;

import java.util.List;

/**
 * A foreign key. The referenced table is identified by name, so table
 * classes do not reference each other during class initialisation.
 */
public final class ForeignKey implements Constraint {

    private final String name;
    private final Table<?> table;
    private final List<Column<?>> columns;
    private final String referencedSchema;
    private final String referencedTable;
    private final List<String> referencedColumns;

    ForeignKey(String name, Table<?> table, List<Column<?>> columns, String referencedSchema, String referencedTable,
               List<String> referencedColumns) {
        if (columns.size() != referencedColumns.size()) {
            throw new IllegalArgumentException("foreign key " + name + ": column counts differ");
        }
        this.name = name;
        this.table = table;
        this.columns = List.copyOf(columns);
        this.referencedSchema = referencedSchema;
        this.referencedTable = referencedTable;
        this.referencedColumns = List.copyOf(referencedColumns);
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

    /** Returns the schema of the referenced table, or {@code null}. */
    public String referencedSchema() {
        return referencedSchema;
    }

    /** Returns the name of the referenced table. */
    public String referencedTable() {
        return referencedTable;
    }

    /** Returns the referenced column names (in the order of {@link #columns()}). */
    public List<String> referencedColumns() {
        return referencedColumns;
    }

    /** Returns {@code true} if {@code other} is the referenced table (any alias). */
    public boolean references(Table<?> other) {
        return other.name().equals(referencedTable)
                && (referencedSchema == null || other.schema() == null || referencedSchema.equals(other.schema()));
    }

    @Override
    public String toString() {
        return "ForeignKey " + name + columns + " -> " + referencedTable + referencedColumns;
    }
}
