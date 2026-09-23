package ch.lxrin.ql.expr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A window definition for {@code OVER (...)} and the {@code WINDOW} clause.
 *
 * <pre>{@code
 * sum(o.total).over(partitionBy(o.customerId)
 *                       .orderBy(o.createdAt.asc())
 *                       .rowsBetween(unboundedPreceding(), currentRow()))
 * // sum(o.TOTAL) OVER (PARTITION BY o.CUSTOMER_ID ORDER BY o.CREATED_AT ASC
 * //                    ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)
 * }</pre>
 */
public final class WindowSpec implements Expression {

    private final String baseWindow;
    private final List<Object> partitionBy;
    private final List<Object> orderBy;
    private final String frame;

    /** Creates an empty window: {@code ()}. */
    public WindowSpec() {
        this(null, List.of(), List.of(), null);
    }

    private WindowSpec(String baseWindow, List<Object> partitionBy, List<Object> orderBy, String frame) {
        this.baseWindow = baseWindow;
        this.partitionBy = partitionBy;
        this.orderBy = orderBy;
        this.frame = frame;
    }

    /** Creates a window that extends a named window: {@code (name ORDER BY ...)}. */
    public static WindowSpec basedOn(String windowName) {
        return new WindowSpec(windowName, List.of(), List.of(), null);
    }

    /** Adds {@code PARTITION BY} items. */
    public WindowSpec partitionBy(Object... items) {
        return new WindowSpec(baseWindow, concat(partitionBy, items), orderBy, frame);
    }

    /** Adds {@code ORDER BY} items. */
    public WindowSpec orderBy(Object... sortFields) {
        return new WindowSpec(baseWindow, partitionBy, concat(orderBy, sortFields), frame);
    }

    /** {@code ROWS BETWEEN start AND end} */
    public WindowSpec rowsBetween(String start, String end) {
        return frame("ROWS BETWEEN " + start + " AND " + end);
    }

    /** {@code RANGE BETWEEN start AND end} */
    public WindowSpec rangeBetween(String start, String end) {
        return frame("RANGE BETWEEN " + start + " AND " + end);
    }

    /** {@code GROUPS BETWEEN start AND end} */
    public WindowSpec groupsBetween(String start, String end) {
        return frame("GROUPS BETWEEN " + start + " AND " + end);
    }

    /** {@code ROWS start} (e.g. {@code ROWS UNBOUNDED PRECEDING}) */
    public WindowSpec rows(String start) {
        return frame("ROWS " + start);
    }

    /** {@code RANGE start} */
    public WindowSpec range(String start) {
        return frame("RANGE " + start);
    }

    /**
     * Sets an arbitrary frame clause, e.g.
     * {@code "ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING EXCLUDE CURRENT ROW"}.
     */
    public WindowSpec frame(String frameClause) {
        return new WindowSpec(baseWindow, partitionBy, orderBy, frameClause);
    }

    // ------------------------------------------------------------------ frame bounds

    /** {@code UNBOUNDED PRECEDING} */
    public static String unboundedPreceding() { return "UNBOUNDED PRECEDING"; }

    /** {@code UNBOUNDED FOLLOWING} */
    public static String unboundedFollowing() { return "UNBOUNDED FOLLOWING"; }

    /** {@code CURRENT ROW} */
    public static String currentRow() { return "CURRENT ROW"; }

    /** {@code n PRECEDING} */
    public static String preceding(long n) { return n + " PRECEDING"; }

    /** {@code n FOLLOWING} */
    public static String following(long n) { return n + " FOLLOWING"; }

    @Override
    public void render(RenderContext ctx) {
        ctx.append('(');
        String sep = "";
        if (baseWindow != null) {
            ctx.append(baseWindow);
            sep = " ";
        }
        if (!partitionBy.isEmpty()) {
            ctx.append(sep).append("PARTITION BY ").visitAll(partitionBy, ", ");
            sep = " ";
        }
        if (!orderBy.isEmpty()) {
            ctx.append(sep).append("ORDER BY ").visitAll(orderBy, ", ");
            sep = " ";
        }
        if (frame != null) {
            ctx.append(sep).append(frame);
        }
        ctx.append(')');
    }

    private static List<Object> concat(List<Object> base, Object[] more) {
        List<Object> list = new ArrayList<>(base);
        list.addAll(Arrays.asList(more));
        return List.copyOf(list);
    }
}
