package ch.lxrin.ql.dsl;

import ch.lxrin.ql.render.Identifiers;
import ch.lxrin.ql.render.QueryPart;
import ch.lxrin.ql.render.RenderContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A window specification for {@code OVER (...)} and the {@code WINDOW} clause.
 * Immutable: every method returns a new instance.
 *
 * <pre>{@code
 * sum(ORDERS.TOTAL).over(partitionBy(ORDERS.USER_ID).orderBy(ORDERS.ORDERED_ON.asc())
 *         .rowsBetween(unboundedPreceding(), currentRow()))
 * }</pre>
 */
public final class WindowSpec implements QueryPart {

    /** Frame exclusion options. */
    public enum Exclude {
        /** {@code EXCLUDE CURRENT ROW} */
        CURRENT_ROW,
        /** {@code EXCLUDE GROUP} */
        GROUP,
        /** {@code EXCLUDE TIES} */
        TIES,
        /** {@code EXCLUDE NO OTHERS} */
        NO_OTHERS
    }

    private final String base;
    private final List<Field<?>> partitionBy;
    private final List<SortField<?>> orderBy;
    private final String frameUnit;
    private final FrameBound start;
    private final FrameBound end;
    private final Exclude exclude;

    WindowSpec(String base, List<Field<?>> partitionBy, List<SortField<?>> orderBy, String frameUnit,
               FrameBound start, FrameBound end, Exclude exclude) {
        this.base = base;
        this.partitionBy = List.copyOf(partitionBy);
        this.orderBy = List.copyOf(orderBy);
        this.frameUnit = frameUnit;
        this.start = start;
        this.end = end;
        this.exclude = exclude;
    }

    /** An empty window: {@code ()}. */
    public static WindowSpec empty() {
        return new WindowSpec(null, List.of(), List.of(), null, null, null, null);
    }

    /** A window that refines a named window: {@code (w ORDER BY ...)}. */
    public static WindowSpec basedOn(WindowDefinition window) {
        return new WindowSpec(window.name(), List.of(), List.of(), null, null, null, null);
    }

    /** Adds {@code PARTITION BY} fields. */
    public WindowSpec partitionBy(Field<?>... fields) {
        List<Field<?>> list = new ArrayList<>(partitionBy);
        list.addAll(Arrays.asList(fields));
        return new WindowSpec(base, list, orderBy, frameUnit, start, end, exclude);
    }

    /** Adds {@code ORDER BY} items. */
    public WindowSpec orderBy(SortField<?>... sortFields) {
        List<SortField<?>> list = new ArrayList<>(orderBy);
        list.addAll(Arrays.asList(sortFields));
        return new WindowSpec(base, partitionBy, list, frameUnit, start, end, exclude);
    }

    /** {@code ROWS BETWEEN start AND end} */
    public WindowSpec rowsBetween(FrameBound start, FrameBound end) {
        return frame("ROWS", start, end);
    }

    /** {@code RANGE BETWEEN start AND end} */
    public WindowSpec rangeBetween(FrameBound start, FrameBound end) {
        return frame("RANGE", start, end);
    }

    /** {@code GROUPS BETWEEN start AND end} */
    public WindowSpec groupsBetween(FrameBound start, FrameBound end) {
        return frame("GROUPS", start, end);
    }

    /** {@code ROWS start} */
    public WindowSpec rows(FrameBound start) {
        return frame("ROWS", start, null);
    }

    /** {@code RANGE start} */
    public WindowSpec range(FrameBound start) {
        return frame("RANGE", start, null);
    }

    /** Adds a frame exclusion, e.g. {@code EXCLUDE CURRENT ROW}. */
    public WindowSpec exclude(Exclude exclusion) {
        if (frameUnit == null) throw new IllegalStateException("exclude(..) needs a frame (rowsBetween, rangeBetween, ...)");
        return new WindowSpec(base, partitionBy, orderBy, frameUnit, start, end, exclusion);
    }

    private WindowSpec frame(String unit, FrameBound from, FrameBound to) {
        if (from == null) throw new IllegalArgumentException("frame start must not be null");
        return new WindowSpec(base, partitionBy, orderBy, unit, from, to, exclude);
    }

    @Override
    public void render(RenderContext ctx) {
        ctx.append('(');
        String sep = "";
        if (base != null) {
            ctx.append(Identifiers.quote(base));
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
        if (frameUnit != null) {
            ctx.append(sep).append(frameUnit).append(' ');
            if (end == null) {
                ctx.visit(start);
            } else {
                ctx.append("BETWEEN ").visit(start).append(" AND ").visit(end);
            }
            if (exclude != null) ctx.append(" EXCLUDE ").append(exclude.name().replace('_', ' '));
        }
        ctx.append(')');
    }
}
