package ch.lxrin.ql.dsl;

import ch.lxrin.ql.render.RenderContext;
import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.schema.Table;

/**
 * A set-returning function in {@code FROM}, for example
 * {@code tableOf(generateSeries(1, 10), "n")} renders
 * {@code generate_series(?, ?) AS n(value)}; its single column is {@link #value()}.
 *
 * @param <T> the type of the returned values
 */
public final class FunctionTable<T> extends Table<Row> {

    private final Field<T> function;
    private final boolean lateral;
    private final Column<T> value;

    FunctionTable(Field<T> function, String alias, boolean lateral) {
        super(null, alias, null);
        this.function = function;
        this.lateral = lateral;
        this.value = column("value", function.type(), 0);
    }

    /** Returns the column holding the function's values. */
    public Column<T> value() {
        return value;
    }

    /** Returns a {@code LATERAL} copy. */
    public FunctionTable<T> lateral() {
        return new FunctionTable<>(function, name(), true);
    }

    @Override
    public boolean supportsPolicies() {
        return false;
    }

    @Override
    public FunctionTable<T> as(String alias) {
        return new FunctionTable<>(function, alias, lateral);
    }

    @Override
    public Row mapRow(Row row) {
        return row;
    }

    @Override
    public void render(RenderContext ctx) {
        if (lateral) ctx.append("LATERAL ");
        ctx.visit(function).append(" AS ").identifier(name()).append("(value)");
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
