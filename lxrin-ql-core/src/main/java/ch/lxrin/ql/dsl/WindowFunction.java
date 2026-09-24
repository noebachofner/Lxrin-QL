package ch.lxrin.ql.dsl;

import ch.lxrin.ql.render.QueryPart;

import java.util.function.Function;

/**
 * A function that is only valid with a window, such as {@code row_number()}
 * or {@code lag(..)}. It is not a field until {@code over(..)} is called, so
 * forgetting the window is a compile error.
 *
 * @param <F> the field type returned by {@code over(..)}
 */
public final class WindowFunction<F extends Field<?>> {

    private final QueryPart call;
    private final Function<QueryPart, F> wrap;

    WindowFunction(QueryPart call, Function<QueryPart, F> wrap) {
        this.call = call;
        this.wrap = wrap;
    }

    /** {@code f() OVER ()} */
    public F over() {
        return over(WindowSpec.empty());
    }

    /** {@code f() OVER (spec)} */
    public F over(WindowSpec window) {
        return wrap.apply(ctx -> ctx.visit(call).append(" OVER ").visit(window));
    }

    /** {@code f() OVER name} */
    public F over(WindowDefinition window) {
        return wrap.apply(ctx -> ctx.visit(call).append(" OVER ").identifier(window.name()));
    }
}
