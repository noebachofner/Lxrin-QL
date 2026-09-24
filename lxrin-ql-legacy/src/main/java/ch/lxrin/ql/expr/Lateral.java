package ch.lxrin.ql.expr;

/**
 * Marks a sub-query or set-returning function in {@code FROM} / {@code JOIN}
 * as {@code LATERAL}, so it can reference columns of preceding tables.
 */
public final class Lateral implements Expression {

    private final Object source;

    /** @param source usually an aliased sub-query ({@code query.as("x")}) */
    public Lateral(Object source) {
        this.source = source;
    }

    @Override
    public void render(RenderContext ctx) {
        ctx.append("LATERAL ").visit(source);
    }
}
