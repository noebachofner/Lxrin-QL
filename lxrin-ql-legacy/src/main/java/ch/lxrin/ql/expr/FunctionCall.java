package ch.lxrin.ql.expr;

import ch.lxrin.ql.condition.Condition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A SQL function call such as {@code lower(p.NAME)} or {@code count(*)}.
 *
 * <p>Aggregate and window features are available as immutable modifiers,
 * each returning a new instance:</p>
 * <pre>{@code
 * count().filter(eq(o.status, val("PAID")))            // count(*) FILTER (WHERE o.STATUS = :lq0)
 * stringAgg(p.name, "', '").orderBy(p.name.asc())       // string_agg(p.NAME, ', ' ORDER BY p.NAME ASC)
 * sum(o.total).distinct()                               // sum(DISTINCT o.TOTAL)
 * percentileCont(0.5).withinGroup(o.total.asc())        // percentile_cont(:lq0) WITHIN GROUP (ORDER BY o.TOTAL ASC)
 * rowNumber().over(partitionBy(o.customerId).orderBy(o.createdAt.desc()))
 * }</pre>
 */
public final class FunctionCall implements Expression {

    private final String name;
    private final List<Object> args;
    private final boolean distinct;
    private final List<Object> orderBy;
    private final List<Object> withinGroup;
    private final Condition filter;
    private final Object over;   // WindowSpec, window name (String) or null

    /**
     * @param name function name, rendered verbatim (e.g. {@code "jsonb_build_object"})
     * @param args arguments (expressions, SQL fragments or values to bind)
     */
    public FunctionCall(String name, Object... args) {
        this(requireName(name), args == null ? Collections.singletonList(null) : Arrays.asList(args),
                false, List.of(), List.of(), null, null);
    }

    private FunctionCall(String name, List<Object> args, boolean distinct, List<Object> orderBy,
                         List<Object> withinGroup, Condition filter, Object over) {
        this.name = name;
        this.args = args;
        this.distinct = distinct;
        this.orderBy = orderBy;
        this.withinGroup = withinGroup;
        this.filter = filter;
        this.over = over;
    }

    /** Returns the function name. */
    public String getName() {
        return name;
    }

    /** {@code name(DISTINCT args)} – for aggregates. */
    public FunctionCall distinct() {
        return new FunctionCall(name, args, true, orderBy, withinGroup, filter, over);
    }

    /** {@code name(args ORDER BY ...)} – ordered aggregates such as {@code string_agg}, {@code array_agg}. */
    public FunctionCall orderBy(Object... sortFields) {
        return new FunctionCall(name, args, distinct, list(sortFields), withinGroup, filter, over);
    }

    /** {@code name(args) WITHIN GROUP (ORDER BY ...)} – ordered-set aggregates such as {@code percentile_cont}. */
    public FunctionCall withinGroup(Object... sortFields) {
        return new FunctionCall(name, args, distinct, orderBy, list(sortFields), filter, over);
    }

    /** {@code name(args) FILTER (WHERE condition)} – aggregates only. */
    public FunctionCall filter(Condition condition) {
        return new FunctionCall(name, args, distinct, orderBy, withinGroup, condition, over);
    }

    /** {@code name(args) OVER ()} – window over the whole partition. */
    public FunctionCall over() {
        return new FunctionCall(name, args, distinct, orderBy, withinGroup, filter, new WindowSpec());
    }

    /** {@code name(args) OVER (PARTITION BY ... ORDER BY ... frame)} */
    public FunctionCall over(WindowSpec window) {
        return new FunctionCall(name, args, distinct, orderBy, withinGroup, filter, window);
    }

    /** {@code name(args) OVER windowName} – references a named window. */
    public FunctionCall over(String windowName) {
        return new FunctionCall(name, args, distinct, orderBy, withinGroup, filter, windowName);
    }

    @Override
    public void render(RenderContext ctx) {
        ctx.append(name).append('(');
        if (distinct) ctx.append("DISTINCT ");
        ctx.visitAll(args, ", ");
        if (!orderBy.isEmpty()) {
            ctx.append(" ORDER BY ").visitAll(orderBy, ", ");
        }
        ctx.append(')');
        if (!withinGroup.isEmpty()) {
            ctx.append(" WITHIN GROUP (ORDER BY ").visitAll(withinGroup, ", ").append(')');
        }
        if (filter != null) {
            ctx.append(" FILTER (WHERE ").visit(filter).append(')');
        }
        if (over instanceof WindowSpec) {
            ctx.append(" OVER ").visit(over);
        } else if (over != null) {
            ctx.append(" OVER ").append((String) over);
        }
    }

    private static List<Object> list(Object[] items) {
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(items)));
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("function name must not be blank");
        return name;
    }
}
