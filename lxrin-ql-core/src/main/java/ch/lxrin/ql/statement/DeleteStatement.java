package ch.lxrin.ql.statement;

import ch.lxrin.ql.dsl.Condition;
import ch.lxrin.ql.render.RenderContext;
import ch.lxrin.ql.schema.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * The model of a {@code DELETE}. A delete without {@code WHERE} is rejected
 * unless {@link #allRows(boolean)} is set.
 */
public final class DeleteStatement extends DmlStatement {

    private List<Table<?>> using = new ArrayList<>();
    private Condition where = Condition.noCondition();
    private boolean allRows;

    /** Creates an empty {@code DELETE FROM table}. */
    public DeleteStatement(Table<?> table) {
        super(table);
    }

    @Override
    public StatementKind kind() {
        return StatementKind.DELETE;
    }

    /** Returns the {@code USING} tables (mutable). */
    public List<Table<?>> using() {
        return using;
    }

    /** Returns the {@code WHERE} condition. */
    public Condition where() {
        return where;
    }

    /** Adds a condition with {@code AND}. */
    public void addWhere(Condition condition) {
        where = where.and(condition);
    }

    /** Allows a delete without {@code WHERE}. */
    public void allRows(boolean value) {
        allRows = value;
    }

    /** Returns {@code true} if a delete without {@code WHERE} is allowed. */
    public boolean allRows() {
        return allRows;
    }

    /**
     * Converts this delete into an update of the same rows (used by soft-delete
     * policies). {@code WITH}, {@code WHERE}, {@code USING} (as {@code FROM})
     * and {@code RETURNING} are kept; the caller adds the assignments.
     */
    public UpdateStatement toUpdate() {
        UpdateStatement u = new UpdateStatement(table());
        copyInto(u);
        u.from().addAll(using);
        u.addWhere(where);
        u.allRows(allRows);
        u.originalKind(StatementKind.DELETE);
        return u;
    }

    @Override
    public List<Table<?>> tables() {
        List<Table<?>> list = new ArrayList<>(super.tables());
        list.addAll(using);
        return list;
    }

    @Override
    public DeleteStatement copy() {
        DeleteStatement c = new DeleteStatement(table());
        copyInto(c);
        c.using = new ArrayList<>(using);
        c.where = where;
        c.allRows = allRows;
        return c;
    }

    @Override
    public void render(RenderContext ctx) {
        if (where == Condition.noCondition() && !allRows) {
            throw new IllegalStateException("DELETE without WHERE – add where(..) or call allRows()");
        }
        with.render(ctx);
        ctx.append("DELETE FROM ");
        renderTarget(ctx);
        Condition filter = Condition.and(ctx.policyFilter(table()));
        if (!using.isEmpty()) {
            ctx.append(" USING ").visitAll(using, ", ");
            for (Table<?> t : using) filter = filter.and(Condition.and(ctx.policyFilter(t)));
        }
        Condition full = where.and(filter);
        if (full != Condition.noCondition()) ctx.append(" WHERE ").visit(full);
        renderReturning(ctx);
    }
}
