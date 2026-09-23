package ch.lxrin.ql.table;

import ch.lxrin.ql.expr.Expression;
import ch.lxrin.ql.expr.RenderContext;

/**
 * A typed reference to a database column. It knows
 * <ul>
 *   <li>its qualified SQL expression, e.g. {@code p.PRODUCT_NR} (used in
 *       {@code SELECT}, {@code WHERE}, {@code ORDER BY}, ...),</li>
 *   <li>its unqualified column name, e.g. {@code PRODUCT_NR} (used in
 *       {@code INSERT} column lists and {@code UPDATE ... SET}),</li>
 *   <li>a Java-style alias, e.g. {@code productNr} (used to map result
 *       columns to bean properties).</li>
 * </ul>
 *
 * <p>Columns are created by {@link TableDef#column(String)}. As an
 * {@link Expression} they offer the fluent condition API:</p>
 * <pre>{@code
 * ProductTable p = new ProductTable();
 * select(p.productNr, p.name).from(p).where(p.price.gt(100)).multiple();
 * }</pre>
 */
public class Column implements Expression {

    private final TableDef table;
    private final String name;
    private final String alias;

    /** Package-private – use {@link TableDef#column} to create instances. */
    Column(TableDef table, String name, String alias) {
        this.table = table;
        this.name = name;
        this.alias = alias;
    }

    /** Returns the qualified SQL expression, e.g. {@code "p.PRODUCT_NR"}. */
    @Override
    public String toSql() {
        return table.getAlias() + "." + name;
    }

    @Override
    public void render(RenderContext ctx) {
        ctx.append(table.getAlias()).append('.').append(name);
    }

    /** Returns the unqualified column name, e.g. {@code "PRODUCT_NR"}. */
    public String getName() {
        return name;
    }

    /** Returns the Java-style alias, e.g. {@code "productNr"}. */
    public String getAlias() {
        return alias;
    }

    /** Returns the table this column belongs to. */
    public TableDef getTable() {
        return table;
    }

    /** Returns the qualified SQL expression (same as {@link #toSql()}). */
    @Override
    public String toString() {
        return toSql();
    }
}
