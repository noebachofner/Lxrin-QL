package ch.lxrin.ql.query;

import ch.lxrin.ql.condition.ConditionList;
import ch.lxrin.ql.expr.RenderContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@code DELETE} statement.
 *
 * <pre>{@code
 * deleteFrom(s).where(lt(s.expiresAt, now())).execute();
 *
 * // DELETE ... USING
 * deleteFrom(o).using(c)
 *     .where(eq(o.customerId, c.id), eq(c.status, val("CLOSED")))
 *     .returning(o.id)
 *     .multiple(Long.class);
 * }</pre>
 *
 * <p>For safety a {@code DELETE} without {@code WHERE} is rejected; call
 * {@link #allRows()} to delete every row on purpose.</p>
 */
public class DeleteQuery extends AbstractDml<DeleteQuery> {

    private final List<Object> using = new ArrayList<>();
    private final ConditionList where = new ConditionList();
    private boolean allRows;

    /** @param table target table ({@code TableDef} or SQL fragment) */
    public DeleteQuery(Object table) {
        super(table);
    }

    /** {@code USING sources} – join other tables into the delete. */
    public DeleteQuery using(Object... sources) {
        using.addAll(Arrays.asList(sources));
        return this;
    }

    /** Adds {@code WHERE} conditions. */
    public DeleteQuery where(Object... conditions) {
        where.add(conditions);
        return this;
    }

    /** Adds the conditions only if {@code apply} is {@code true}. */
    public DeleteQuery whereIf(boolean apply, Object... conditions) {
        return apply ? where(conditions) : this;
    }

    /**
     * Adds a lazily created condition only if {@code apply} is {@code true}, so
     * the condition may use values that are {@code null} when disabled:
     * {@code whereIf(name != null, () -> ilike(p.lastName, val(name + "%")))}.
     */
    public DeleteQuery whereIf(boolean apply, java.util.function.Supplier<?> condition) {
        return apply ? where(condition.get()) : this;
    }

    /** Confirms that the statement intentionally has no {@code WHERE} clause. */
    public DeleteQuery allRows() {
        this.allRows = true;
        return this;
    }

    @Override
    protected void renderBody(RenderContext ctx) {
        if (where.isEmpty() && !allRows) {
            throw new IllegalStateException("DELETE without WHERE – add where(..) or call allRows()");
        }
        ctx.append("DELETE FROM ");
        renderTable(ctx, " ");
        if (!using.isEmpty()) ctx.append(" USING ").visitAll(using, ", ");
        renderWhere(ctx, where);
        renderReturning(ctx);
    }
}
