package ch.lxrin.ql.test;

import ch.lxrin.ql.dsl.AbstractDml;
import ch.lxrin.ql.dsl.AbstractSelect;
import ch.lxrin.ql.dsl.Returning;
import ch.lxrin.ql.render.QueryPart;
import ch.lxrin.ql.render.RenderContext;
import ch.lxrin.ql.render.RenderedSql;
import ch.lxrin.ql.statement.Statement;

/**
 * Entry points for SQL assertions:
 * <pre>{@code
 * assertThatSql(select(USERS.NAME).from(USERS).where(USERS.EMAIL.endsWith("@x")))
 *     .isEqualTo("SELECT users.name FROM users WHERE users.email LIKE ?")
 *     .hasBinds("%@x");
 * }</pre>
 * Statements are rendered without table policies; use a {@link MockExecutor}
 * to see what a {@code QueryContext} actually sends.
 */
public final class SqlAssertions {

    private SqlAssertions() {}

    /** Assertions on a {@code SELECT}. */
    public static SqlAssert assertThatSql(AbstractSelect<?, ?> select) {
        return new SqlAssert(select.render());
    }

    /** Assertions on an {@code INSERT}, {@code UPDATE} or {@code DELETE}. */
    public static SqlAssert assertThatSql(AbstractDml<?, ?> dml) {
        return new SqlAssert(dml.render());
    }

    /** Assertions on a statement with {@code RETURNING}. */
    public static SqlAssert assertThatSql(Returning<?> returning) {
        return new SqlAssert(returning.render());
    }

    /** Assertions on a statement model. */
    public static SqlAssert assertThatSql(Statement statement) {
        RenderContext ctx = new RenderContext();
        statement.render(ctx);
        return new SqlAssert(ctx.result());
    }

    /** Assertions on any part, e.g. a condition. */
    public static SqlAssert assertThatSql(QueryPart part) {
        RenderContext ctx = new RenderContext();
        ctx.visit(part);
        return new SqlAssert(ctx.result());
    }

    /** Assertions on an already rendered statement, e.g. from {@link MockExecutor#statements()}. */
    public static SqlAssert assertThatSql(RenderedSql rendered) {
        return new SqlAssert(rendered);
    }
}
