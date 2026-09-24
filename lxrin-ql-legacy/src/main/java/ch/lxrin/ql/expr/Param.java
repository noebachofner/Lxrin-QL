package ch.lxrin.ql.expr;

/**
 * A value that is sent to the database as a bind parameter.
 *
 * <p>Created with {@code val(value)}. Rendering registers the value in the
 * {@link RenderContext} under an auto-generated name ({@code :lq0}, ...),
 * so no separate {@code bind(..)} call is needed.</p>
 *
 * <pre>{@code
 * .where(eq(p.lastName, val(searchText)))   // p.LAST_NAME = :lq0
 * }</pre>
 */
public final class Param implements Expression {

    private final Object value;

    /** @param value the value to bind; may be {@code null} */
    public Param(Object value) {
        this.value = value;
    }

    /** Returns the bound value. */
    public Object getValue() {
        return value;
    }

    @Override
    public void render(RenderContext ctx) {
        ctx.bind(value);
    }
}
