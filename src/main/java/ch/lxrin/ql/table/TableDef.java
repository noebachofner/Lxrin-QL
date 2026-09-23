package ch.lxrin.ql.table;

import ch.lxrin.ql.expr.Expression;
import ch.lxrin.ql.expr.Renderable;
import ch.lxrin.ql.expr.RenderContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for typed table definitions.
 *
 * <p>Declare one subclass per table and its columns as fields:</p>
 * <pre>{@code
 * public class ProductTable extends TableDef {
 *     public final Column productNr = column("PRODUCT_NR");  // p.PRODUCT_NR, alias "productNr"
 *     public final Column name      = column("NAME");         // p.NAME,       alias "name"
 *     public final Column price     = column("PRICE");        // p.PRICE,      alias "price"
 *
 *     public ProductTable() {
 *         super("PRODUCT", "p");
 *     }
 * }
 * }</pre>
 *
 * <p>Use the same class with a different alias for self-joins by adding a
 * second constructor ({@code public ProductTable(String alias)}).</p>
 */
public abstract class TableDef implements Renderable {

    private final String tableName;
    private final String alias;
    private final List<Column> columns = new ArrayList<>();

    /**
     * @param tableName SQL table name, optionally schema-qualified, e.g. {@code "PRODUCT"} or {@code "sales.PRODUCT"}
     * @param alias     table alias used in queries, e.g. {@code "p"}
     */
    protected TableDef(String tableName, String alias) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("tableName must not be blank");
        }
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("alias must not be blank");
        }
        this.tableName = tableName;
        this.alias = alias;
    }

    // -------------------------------------------------------------------------
    // Column factory methods
    // -------------------------------------------------------------------------

    /**
     * Creates a column whose Java alias is derived from the SQL name
     * ({@code UPPER_SNAKE_CASE} &rarr; {@code lowerCamelCase}, e.g.
     * {@code "PRODUCT_NR"} &rarr; {@code "productNr"}).
     */
    protected Column column(String columnName) {
        if (columnName == null || columnName.isBlank()) {
            throw new IllegalArgumentException("columnName must not be blank");
        }
        return register(new Column(this, columnName, toCamelCase(columnName)));
    }

    /** Creates a column with an explicit Java alias. */
    protected Column column(String columnName, String javaAlias) {
        if (columnName == null || columnName.isBlank()) {
            throw new IllegalArgumentException("columnName must not be blank");
        }
        if (javaAlias == null || javaAlias.isBlank()) {
            throw new IllegalArgumentException("alias must not be blank");
        }
        return register(new Column(this, columnName, javaAlias));
    }

    private Column register(Column column) {
        columns.add(column);
        return column;
    }

    // -------------------------------------------------------------------------
    // SQL accessors
    // -------------------------------------------------------------------------

    /** Returns all columns in declaration order, e.g. for {@code select(p.columns())}. */
    public List<Column> columns() {
        return Collections.unmodifiableList(columns);
    }

    /** {@code alias.*} */
    public Expression all() {
        return ctx -> ctx.append(alias).append(".*");
    }

    /** Returns the {@code "TABLE_NAME alias"} fragment for {@code FROM}, e.g. {@code "PRODUCT p"}. */
    public String toFromSql() {
        return tableName + " " + alias;
    }

    /** Renders {@code TABLE_NAME alias}. */
    @Override
    public void render(RenderContext ctx) {
        ctx.append(tableName).append(' ').append(alias);
    }

    /** Returns the table name, e.g. {@code "PRODUCT"}. */
    public String getTableName() {
        return tableName;
    }

    /** Returns the table alias, e.g. {@code "p"}. */
    public String getAlias() {
        return alias;
    }

    @Override
    public String toString() {
        return toFromSql();
    }

    /**
     * Converts {@code UPPER_SNAKE_CASE} to {@code lowerCamelCase}:
     * {@code "PRODUCT_NR"} &rarr; {@code "productNr"}, {@code "ID"} &rarr; {@code "id"}.
     */
    static String toCamelCase(String snakeCase) {
        if (snakeCase == null || snakeCase.isBlank()) {
            return snakeCase;
        }
        String[] parts = snakeCase.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
                sb.append(parts[i].substring(1));
            }
        }
        return sb.toString();
    }
}
