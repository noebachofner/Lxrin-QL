package ch.lxrin.ql.dsl;

import ch.lxrin.ql.render.RenderContext;
import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.statement.Statement;
import ch.lxrin.ql.types.DataType;

import java.util.ArrayList;
import java.util.List;

/**
 * A common table expression. Its columns are the named output fields of its
 * query and are accessed with {@link #field(Field)}:
 *
 * <pre>{@code
 * Cte paid = cte("paid", select(ORDERS.USER_ID, sum(ORDERS.TOTAL).as("total"))
 *         .from(ORDERS).groupBy(ORDERS.USER_ID));
 * select(USERS.NAME, paid.field(ORDERS.USER_ID))
 *     .with(paid).from(USERS).join(paid).on(paid.field(ORDERS.USER_ID).eq(USERS.ID))
 * }</pre>
 *
 * <p>A recursive CTE declares its columns first, because its query refers to
 * itself: {@code Cte t = recursiveCte("t"); Column<Integer> n = t.declareColumn("n", SqlTypes.INT4); t.as(query)}.</p>
 */
public final class Cte extends Table<Row> {

    private final Holder holder;

    /** Shared by all aliases of the same CTE. */
    private static final class Holder {
        Statement body;
        boolean recursive;
        String materialization = "";
        final List<String> columnNames = new ArrayList<>();
        final List<DataType<?>> columnTypes = new ArrayList<>();
    }

    private Cte(String name, String alias, Holder holder) {
        super(null, name, alias);
        this.holder = holder;
        for (int i = 0; i < holder.columnNames.size(); i++) declare(holder.columnNames.get(i), holder.columnTypes.get(i));
    }

    /** Creates a CTE from a query. */
    public static Cte of(String name, CteSource source) {
        Holder h = new Holder();
        h.body = source.statement();
        for (Field<?> f : DerivedColumns.require(source.fields(), "CTE " + name)) {
            h.columnNames.add(f.name());
            h.columnTypes.add(f.type());
        }
        return new Cte(name, null, h);
    }

    /** Creates a recursive CTE whose columns are declared with {@link #declareColumn(String, DataType)} before {@link #as(CteSource)}. */
    public static Cte recursive(String name) {
        Holder h = new Holder();
        h.recursive = true;
        return new Cte(name, null, h);
    }

    /** Declares a column of a recursive CTE. */
    public <T> Column<T> declareColumn(String columnName, DataType<T> type) {
        if (holder.body != null) throw new IllegalStateException("columns must be declared before as(..)");
        holder.columnNames.add(columnName);
        holder.columnTypes.add(type);
        return declare(columnName, type);
    }

    /** Sets the query of a recursive CTE. */
    public Cte as(CteSource source) {
        if (holder.body != null) throw new IllegalStateException("the CTE query is already set");
        if (source.fields().size() != holder.columnNames.size()) {
            throw new IllegalArgumentException("the query returns " + source.fields().size() + " fields but "
                    + holder.columnNames.size() + " columns are declared");
        }
        holder.body = source.statement();
        return this;
    }

    private <T> Column<T> declare(String columnName, DataType<T> type) {
        return column(columnName, type, 0);
    }

    /** Returns this CTE's column for a field of its query (matched by output name). */
    public <T> Column<T> field(Field<T> queryField) {
        if (queryField.name() == null) throw new IllegalArgumentException("field has no name: " + queryField);
        return column(queryField.name(), queryField.type());
    }

    /** Returns {@code AS MATERIALIZED}. */
    public Cte materialized() {
        holder.materialization = "MATERIALIZED ";
        return this;
    }

    /** Returns {@code AS NOT MATERIALIZED}. */
    public Cte notMaterialized() {
        holder.materialization = "NOT MATERIALIZED ";
        return this;
    }

    /** Returns {@code true} if the CTE must be declared with {@code WITH RECURSIVE}. */
    public boolean recursive() {
        return holder.recursive;
    }

    /** Returns the body statement. */
    public Statement body() {
        return holder.body;
    }

    @Override
    public boolean supportsPolicies() {
        return false;
    }

    @Override
    public Cte as(String alias) {
        return new Cte(name(), alias, holder);
    }

    @Override
    public Row mapRow(Row row) {
        return row;
    }

    /** Renders {@code name(columns) AS [MATERIALIZED] (query)} for the {@code WITH} clause. */
    public void renderDefinition(RenderContext ctx) {
        if (holder.body == null) throw new IllegalStateException("CTE " + name() + " has no query; call as(..)");
        ctx.identifier(name()).append('(');
        for (int i = 0; i < holder.columnNames.size(); i++) {
            if (i > 0) ctx.append(", ");
            ctx.identifier(holder.columnNames.get(i));
        }
        ctx.append(") AS ").append(holder.materialization).append('(');
        holder.body.render(ctx);
        ctx.append(')');
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof Cte && ((Cte) o).holder == holder && ((Cte) o).alias().equals(alias());
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(holder) * 31 + alias().hashCode();
    }
}
