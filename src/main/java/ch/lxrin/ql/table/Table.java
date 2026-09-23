package ch.lxrin.ql.table;

/**
 * An ad-hoc table definition for tables without a dedicated
 * {@link TableDef} subclass.
 *
 * <pre>{@code
 * Table a = table("ADDRESS", "a");
 * select(a.col("CITY")).from(a).where(eq(a.col("ZIP"), val("8000"))).multiple();
 * }</pre>
 */
public final class Table extends TableDef {

    private final java.util.Map<String, Column> byName = new java.util.HashMap<>();

    /**
     * @param tableName SQL table name
     * @param alias     table alias
     */
    public Table(String tableName, String alias) {
        super(tableName, alias);
    }

    /** Returns a column of this table (alias derived as in {@link TableDef#column(String)}). */
    public Column col(String columnName) {
        return byName.computeIfAbsent(columnName, this::column);
    }

    /** Returns a column of this table with an explicit Java alias. */
    public Column col(String columnName, String javaAlias) {
        return column(columnName, javaAlias);
    }
}
