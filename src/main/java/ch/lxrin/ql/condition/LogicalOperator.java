package ch.lxrin.ql.condition;

import ch.lxrin.ql.expr.RenderContext;

/**
 * The {@code AND} / {@code OR} tokens used between conditions in the
 * list style:
 * <pre>{@code
 * .where(eq(p.status, val("ACTIVE")), or(), isNull(p.status))
 * }</pre>
 *
 * <p>Inside a {@code where(..)} call two adjacent conditions without an
 * explicit token are joined with {@code AND}.</p>
 */
public final class LogicalOperator implements Condition {

    /** Singleton AND operator. */
    public static final LogicalOperator AND = new LogicalOperator("AND");

    /** Singleton OR operator. */
    public static final LogicalOperator OR = new LogicalOperator("OR");

    private final String keyword;

    private LogicalOperator(String keyword) {
        this.keyword = keyword;
    }

    @Override
    public void render(RenderContext ctx) {
        ctx.append(keyword);
    }

    @Override
    public String toString() {
        return keyword;
    }
}
