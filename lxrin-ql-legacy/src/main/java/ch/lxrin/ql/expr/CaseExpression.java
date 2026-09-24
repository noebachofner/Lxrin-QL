package ch.lxrin.ql.expr;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@code CASE} expression, either searched or simple.
 *
 * <pre>{@code
 * // searched CASE
 * caseWhen(lt(p.age, 18), inline("minor"))
 *     .when(lt(p.age, 65), inline("adult"))
 *     .otherwise(inline("senior"))
 *     .as("ageGroup")
 *
 * // simple CASE
 * caseOf(o.status)
 *     .when(inline("N"), inline("new"))
 *     .when(inline("P"), inline("paid"))
 *     .otherwise(inline("unknown"))
 * }</pre>
 */
public final class CaseExpression implements Expression {

    private final Object subject;             // null for a searched CASE
    private final List<Object[]> branches;
    private final Object otherwise;
    private final boolean hasOtherwise;

    private CaseExpression(Object subject, List<Object[]> branches, Object otherwise, boolean hasOtherwise) {
        this.subject = subject;
        this.branches = branches;
        this.otherwise = otherwise;
        this.hasOtherwise = hasOtherwise;
    }

    /** Starts a searched CASE: {@code CASE WHEN condition THEN result}. */
    public static CaseExpression searched(Object condition, Object result) {
        return new CaseExpression(null, List.of(), null, false).when(condition, result);
    }

    /** Starts a simple CASE: {@code CASE subject WHEN ... }. Add branches with {@link #when}. */
    public static CaseExpression simple(Object subject) {
        if (subject == null) throw new IllegalArgumentException("subject must not be null");
        return new CaseExpression(subject, List.of(), null, false);
    }

    /** Adds a {@code WHEN test THEN result} branch. */
    public CaseExpression when(Object test, Object result) {
        List<Object[]> list = new ArrayList<>(branches);
        list.add(new Object[]{test, result});
        return new CaseExpression(subject, List.copyOf(list), otherwise, hasOtherwise);
    }

    /** Adds the {@code ELSE result} branch. */
    public CaseExpression otherwise(Object result) {
        return new CaseExpression(subject, branches, result, true);
    }

    @Override
    public void render(RenderContext ctx) {
        if (branches.isEmpty()) throw new IllegalStateException("CASE needs at least one WHEN branch");
        ctx.append("CASE");
        if (subject != null) ctx.append(' ').visit(subject);
        for (Object[] branch : branches) {
            ctx.append(" WHEN ").visit(branch[0]).append(" THEN ").visit(branch[1]);
        }
        if (hasOtherwise) ctx.append(" ELSE ").visit(otherwise);
        ctx.append(" END");
    }
}
