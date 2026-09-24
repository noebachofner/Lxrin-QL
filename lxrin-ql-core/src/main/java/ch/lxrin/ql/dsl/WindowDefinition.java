package ch.lxrin.ql.dsl;

import ch.lxrin.ql.render.Identifiers;
import ch.lxrin.ql.render.QueryPart;
import ch.lxrin.ql.render.RenderContext;

/**
 * A named window for the {@code WINDOW} clause:
 * <pre>{@code
 * WindowDefinition w = window("w", partitionBy(ORDERS.USER_ID).orderBy(ORDERS.ORDERED_ON.asc()));
 * select(rowNumber().over(w), sum(ORDERS.TOTAL).over(w)).from(ORDERS).window(w)
 * }</pre>
 */
public final class WindowDefinition implements QueryPart {

    private final String name;
    private final WindowSpec spec;

    WindowDefinition(String name, WindowSpec spec) {
        this.name = Identifiers.requireName(name);
        this.spec = spec;
    }

    /** Returns the window name. */
    public String name() {
        return name;
    }

    /** Renders {@code name AS (spec)} for the {@code WINDOW} clause. */
    @Override
    public void render(RenderContext ctx) {
        ctx.identifier(name).append(" AS ").visit(spec);
    }
}
