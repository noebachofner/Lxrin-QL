package ch.lxrin.ql.statement;

import ch.lxrin.ql.dsl.Condition;
import ch.lxrin.ql.dsl.Field;
import ch.lxrin.ql.render.RenderContext;
import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.schema.Table;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The model of an {@code UPDATE}. An update without {@code WHERE} is
 * rejected unless {@link #allRows(boolean)} is set.
 */
public final class UpdateStatement extends DmlStatement {

    private Map<Column<?>, Field<?>> assignments = new LinkedHashMap<>();
    private List<Table<?>> from = new ArrayList<>();
    private Condition where = Condition.noCondition();
    private boolean allRows;
    private StatementKind originalKind = StatementKind.UPDATE;

    /** Creates an empty {@code UPDATE table}. */
    public UpdateStatement(Table<?> table) {
        super(table);
    }

    @Override
    public StatementKind kind() {
        return StatementKind.UPDATE;
    }

    /** Returns {@link StatementKind#DELETE} if a policy turned a delete into this update (soft delete). */
    public StatementKind originalKind() {
        return originalKind;
    }

    /** Records the kind of statement this update replaces. */
    public void originalKind(StatementKind kind) {
        originalKind = kind;
    }

    /** Returns the assignments (mutable). */
    public Map<Column<?>, Field<?>> assignments() {
        return assignments;
    }

    /**
     * Sets {@code column = value}, or only if there is no assignment for the
     * column yet when {@code overwrite} is {@code false}.
     */
    public <T> void set(Column<T> column, Field<T> value, boolean overwrite) {
        if (!column.table().sameTable(table())) {
            throw new IllegalArgumentException("column " + column + " does not belong to " + table().qualifiedName());
        }
        if (column.generated()) throw new IllegalArgumentException("column " + column + " is generated and cannot be written");
        if (overwrite || !assignments.containsKey(column)) assignments.put(column, value);
    }

    /** Returns the {@code FROM} tables (mutable). */
    public List<Table<?>> from() {
        return from;
    }

    /** Returns the {@code WHERE} condition. */
    public Condition where() {
        return where;
    }

    /** Adds a condition with {@code AND}. */
    public void addWhere(Condition condition) {
        where = where.and(condition);
    }

    /** Allows an update without {@code WHERE}. */
    public void allRows(boolean value) {
        allRows = value;
    }

    /** Returns {@code true} if an update without {@code WHERE} is allowed. */
    public boolean allRows() {
        return allRows;
    }

    @Override
    public List<Table<?>> tables() {
        List<Table<?>> list = new ArrayList<>(super.tables());
        list.addAll(from);
        return list;
    }

    @Override
    public UpdateStatement copy() {
        UpdateStatement c = new UpdateStatement(table());
        copyInto(c);
        c.assignments = new LinkedHashMap<>(assignments);
        c.from = new ArrayList<>(from);
        c.where = where;
        c.allRows = allRows;
        c.originalKind = originalKind;
        return c;
    }

    @Override
    public void render(RenderContext ctx) {
        if (assignments.isEmpty()) throw new IllegalStateException("UPDATE needs at least one set(..)");
        if (where == Condition.noCondition() && !allRows) {
            throw new IllegalStateException("UPDATE without WHERE – add where(..) or call allRows()");
        }
        with.render(ctx);
        ctx.append("UPDATE ");
        renderTarget(ctx);
        ctx.append(" SET ");
        renderAssignments(ctx, assignments);
        Condition filter = Condition.and(ctx.policyFilter(table()));
        if (!from.isEmpty()) {
            ctx.append(" FROM ").visitAll(from, ", ");
            for (Table<?> t : from) filter = filter.and(Condition.and(ctx.policyFilter(t)));
        }
        Condition full = where.and(filter);
        if (full != Condition.noCondition()) ctx.append(" WHERE ").visit(full);
        renderReturning(ctx);
    }

    static void renderAssignments(RenderContext ctx, Map<Column<?>, Field<?>> assignments) {
        boolean first = true;
        for (Map.Entry<Column<?>, Field<?>> a : assignments.entrySet()) {
            if (!first) ctx.append(", ");
            ctx.identifier(a.getKey().name()).append(" = ").visit(a.getValue());
            first = false;
        }
    }
}
