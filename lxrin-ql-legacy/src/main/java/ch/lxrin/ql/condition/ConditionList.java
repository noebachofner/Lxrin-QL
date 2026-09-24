package ch.lxrin.ql.condition;

import ch.lxrin.ql.expr.Renderable;
import ch.lxrin.ql.expr.RenderContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The content of a {@code WHERE}, {@code HAVING} or {@code ON} clause, built
 * from one or more {@code where(..)} calls.
 *
 * <ul>
 *   <li>Within one call, items are rendered in order; two adjacent conditions
 *       without an explicit {@code and()} / {@code or()} token are joined with
 *       {@code AND}.</li>
 *   <li>Several calls are joined with {@code AND}. A call that contains an
 *       {@code or()} token is wrapped in parentheses so that it keeps its
 *       meaning.</li>
 * </ul>
 */
public final class ConditionList implements Renderable {

    private final List<List<Object>> groups = new ArrayList<>();

    /** Adds the items of one {@code where(..)} call. Empty calls are ignored. */
    public void add(Object... items) {
        if (items == null || items.length == 0) return;
        groups.add(new ArrayList<>(Arrays.asList(items)));
    }

    /** Returns {@code true} if no condition has been added. */
    public boolean isEmpty() {
        return groups.isEmpty();
    }

    /** Copies all groups of another list into this one. */
    public void addAll(ConditionList other) {
        for (List<Object> g : other.groups) groups.add(new ArrayList<>(g));
    }

    @Override
    public void render(RenderContext ctx) {
        boolean multiple = groups.size() > 1;
        for (int g = 0; g < groups.size(); g++) {
            if (g > 0) ctx.append(" AND ");
            List<Object> items = groups.get(g);
            boolean wrap = multiple && items.contains(LogicalOperator.OR);
            if (wrap) ctx.append('(');
            renderGroup(ctx, items);
            if (wrap) ctx.append(')');
        }
    }

    private static void renderGroup(RenderContext ctx, List<Object> items) {
        boolean previousWasOperand = false;
        boolean first = true;
        for (Object item : items) {
            boolean isOperator = item instanceof LogicalOperator;
            if (!first) {
                ctx.append(' ');
                if (!isOperator && previousWasOperand) ctx.append("AND ");
            }
            ctx.visit(item);
            previousWasOperand = !isOperator;
            first = false;
        }
    }
}
