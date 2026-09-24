package ch.lxrin.ql.expr;

/**
 * Something that can write itself as SQL into a {@link RenderContext}.
 *
 * <p>This is the root type of everything the library renders. Most elements
 * are {@link Expression}s; table definitions are only {@code Renderable}
 * because they can appear in {@code FROM} / {@code JOIN} but not in
 * value positions.</p>
 */
@FunctionalInterface
public interface Renderable {

    /** Writes this element's SQL into the given context. */
    void render(RenderContext ctx);
}
