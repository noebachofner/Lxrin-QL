package ch.lxrin.ql.dsl;

import ch.lxrin.ql.render.QueryPart;
import ch.lxrin.ql.render.RenderContext;

/** A window frame bound such as {@code UNBOUNDED PRECEDING} or {@code 3 PRECEDING}. */
public final class FrameBound implements QueryPart {

    private static final FrameBound UNBOUNDED_PRECEDING = new FrameBound("UNBOUNDED PRECEDING");
    private static final FrameBound UNBOUNDED_FOLLOWING = new FrameBound("UNBOUNDED FOLLOWING");
    private static final FrameBound CURRENT_ROW = new FrameBound("CURRENT ROW");

    private final String sql;

    private FrameBound(String sql) {
        this.sql = sql;
    }

    /** {@code UNBOUNDED PRECEDING} */
    public static FrameBound unboundedPreceding() {
        return UNBOUNDED_PRECEDING;
    }

    /** {@code UNBOUNDED FOLLOWING} */
    public static FrameBound unboundedFollowing() {
        return UNBOUNDED_FOLLOWING;
    }

    /** {@code CURRENT ROW} */
    public static FrameBound currentRow() {
        return CURRENT_ROW;
    }

    /** {@code n PRECEDING} */
    public static FrameBound preceding(long n) {
        if (n < 0) throw new IllegalArgumentException("n must not be negative");
        return new FrameBound(n + " PRECEDING");
    }

    /** {@code n FOLLOWING} */
    public static FrameBound following(long n) {
        if (n < 0) throw new IllegalArgumentException("n must not be negative");
        return new FrameBound(n + " FOLLOWING");
    }

    @Override
    public void render(RenderContext ctx) {
        ctx.append(sql);
    }
}
