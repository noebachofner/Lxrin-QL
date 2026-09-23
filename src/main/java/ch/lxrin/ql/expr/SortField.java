package ch.lxrin.ql.expr;

/**
 * An {@code ORDER BY} item: {@code expr [ASC|DESC] [NULLS FIRST|NULLS LAST]}.
 *
 * <p>Create instances with {@link Expression#asc()} / {@link Expression#desc()}
 * or the static {@code asc(..)} / {@code desc(..)} helpers.</p>
 */
public final class SortField implements Expression {

    private final Object expression;
    private final String direction;
    private final String nulls;

    SortField(Object expression, String direction, String nulls) {
        this.expression = expression;
        this.direction = direction;
        this.nulls = nulls;
    }

    /** Creates an ascending sort field for any element. */
    public static SortField asc(Object expression) {
        return new SortField(expression, "ASC", null);
    }

    /** Creates a descending sort field for any element. */
    public static SortField desc(Object expression) {
        return new SortField(expression, "DESC", null);
    }

    /** Appends {@code NULLS FIRST}. */
    public SortField nullsFirst() {
        return new SortField(expression, direction, "NULLS FIRST");
    }

    /** Appends {@code NULLS LAST}. */
    public SortField nullsLast() {
        return new SortField(expression, direction, "NULLS LAST");
    }

    @Override
    public void render(RenderContext ctx) {
        ctx.visit(expression);
        if (direction != null) ctx.append(' ').append(direction);
        if (nulls != null) ctx.append(' ').append(nulls);
    }
}
