package ch.lxrin.ql.query;

import ch.lxrin.ql.condition.ConditionList;
import ch.lxrin.ql.expr.RenderContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@code UPDATE} statement.
 *
 * <pre>{@code
 * update(p)
 *     .set(p.status, val("INACTIVE"))
 *     .set(p.modifiedAt, now())
 *     .where(lt(p.lastLogin, val(cutoff)))
 *     .execute();
 *
 * // UPDATE ... FROM
 * update(o).set(o.customerName, c.name)
 *     .from(c)
 *     .where(eq(o.customerId, c.id))
 *     .execute();
 * }</pre>
 *
 * <p>For safety an {@code UPDATE} without {@code WHERE} is rejected; call
 * {@link #allRows()} to update every row on purpose.</p>
 */
public class UpdateQuery extends AbstractDml<UpdateQuery> {

    private final List<Object[]> assignments = new ArrayList<>();
    private final List<Object> from = new ArrayList<>();
    private final ConditionList where = new ConditionList();
    private boolean allRows;

    /** @param table target table ({@code TableDef} or SQL fragment) */
    public UpdateQuery(Object table) {
        super(table);
    }

    /** {@code SET column = value} */
    public UpdateQuery set(Object column, Object value) {
        assignments.add(new Object[]{column, value});
        return this;
    }

    /** Adds the assignment only if {@code apply} is {@code true}. */
    public UpdateQuery setIf(boolean apply, Object column, Object value) {
        return apply ? set(column, value) : this;
    }

    /** {@code SET (col1, col2) = (SELECT ...)} */
    public UpdateQuery set(Object[] columns, Object subQuery) {
        assignments.add(new Object[]{columns, subQuery});
        return this;
    }

    /** {@code FROM sources} – join other tables into the update. */
    public UpdateQuery from(Object... sources) {
        from.addAll(Arrays.asList(sources));
        return this;
    }

    /** Adds {@code WHERE} conditions (same rules as {@code SELECT ... WHERE}). */
    public UpdateQuery where(Object... conditions) {
        where.add(conditions);
        return this;
    }

    /** Adds the conditions only if {@code apply} is {@code true}. */
    public UpdateQuery whereIf(boolean apply, Object... conditions) {
        return apply ? where(conditions) : this;
    }

    /**
     * Adds a lazily created condition only if {@code apply} is {@code true}, so
     * the condition may use values that are {@code null} when disabled:
     * {@code whereIf(name != null, () -> ilike(p.lastName, val(name + "%")))}.
     */
    public UpdateQuery whereIf(boolean apply, java.util.function.Supplier<?> condition) {
        return apply ? where(condition.get()) : this;
    }

    /** Confirms that the statement intentionally has no {@code WHERE} clause. */
    public UpdateQuery allRows() {
        this.allRows = true;
        return this;
    }

    @Override
    protected void renderBody(RenderContext ctx) {
        if (assignments.isEmpty()) throw new IllegalStateException("UPDATE needs at least one set(..)");
        if (where.isEmpty() && !allRows) {
            throw new IllegalStateException("UPDATE without WHERE – add where(..) or call allRows()");
        }
        ctx.append("UPDATE ");
        renderTable(ctx, " ");
        ctx.append(" SET ");
        renderAssignments(ctx, assignments);
        if (!from.isEmpty()) ctx.append(" FROM ").visitAll(from, ", ");
        renderWhere(ctx, where);
        renderReturning(ctx);
    }
}
