package ch.lxrin.ql.render;

/**
 * Anything that can write itself as SQL: fields, conditions, tables and
 * statements.
 *
 * <p>Application code does not normally implement this interface. Writing SQL
 * text directly is what {@link ch.lxrin.ql.dsl.Sql} is for, and the
 * {@code lxrin-ql-test} architecture rules can forbid other implementations.</p>
 */
@FunctionalInterface
public interface QueryPart {

    /** Writes this part's SQL and bind parameters into the context. */
    void render(RenderContext ctx);
}
