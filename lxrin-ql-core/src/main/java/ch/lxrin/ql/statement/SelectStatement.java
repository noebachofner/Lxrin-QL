package ch.lxrin.ql.statement;

import ch.lxrin.ql.dsl.Condition;
import ch.lxrin.ql.dsl.Field;
import ch.lxrin.ql.dsl.SortField;
import ch.lxrin.ql.dsl.WindowDefinition;
import ch.lxrin.ql.render.QueryPart;
import ch.lxrin.ql.render.RenderContext;
import ch.lxrin.ql.schema.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * The model of a {@code SELECT} statement:
 *
 * <pre>
 * [WITH [RECURSIVE] ...]
 * SELECT [DISTINCT | DISTINCT ON (...)] fields
 * [FROM ...] [JOIN ...] [WHERE ...] [GROUP BY ...] [HAVING ...] [WINDOW ...]
 * [UNION | INTERSECT | EXCEPT [ALL] ...] [ORDER BY ...]
 * [LIMIT n] [OFFSET n] | [OFFSET n ROWS FETCH FIRST n ROWS WITH TIES]
 * [FOR UPDATE | ... [OF ...] [NOWAIT | SKIP LOCKED]]
 * </pre>
 *
 * <p>Table policies are applied while rendering: the filter of every table
 * in {@code FROM} is added to {@code WHERE}, and that of a joined table to
 * its {@code ON} condition (so outer joins stay outer joins).</p>
 */
public final class SelectStatement implements Statement {

    /** A set operation: {@code UNION}, {@code INTERSECT}, {@code EXCEPT}, optionally {@code ALL}. */
    public record SetOperation(String keyword, SelectStatement query) {}

    private WithClause with = new WithClause();
    private boolean distinct;
    private List<Field<?>> distinctOn = new ArrayList<>();
    private List<Field<?>> fields = new ArrayList<>();
    private List<Table<?>> from = new ArrayList<>();
    private List<Join> joins = new ArrayList<>();
    private Condition where = Condition.noCondition();
    private List<QueryPart> groupBy = new ArrayList<>();
    private Condition having = Condition.noCondition();
    private List<WindowDefinition> windows = new ArrayList<>();
    private List<SetOperation> setOperations = new ArrayList<>();
    private List<SortField<?>> orderBy = new ArrayList<>();
    private QueryPart limit;
    private QueryPart offset;
    private boolean withTies;
    private List<Lock> locks = new ArrayList<>();
    private List<Object> seekAfter = new ArrayList<>();

    @Override
    public StatementKind kind() {
        return StatementKind.SELECT;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns the {@code WITH} clause. */
    public WithClause with() {
        return with;
    }

    /** Returns {@code true} for {@code SELECT DISTINCT}. */
    public boolean distinct() {
        return distinct;
    }

    /** Sets {@code DISTINCT}. */
    public void distinct(boolean value) {
        distinct = value;
    }

    /** Returns the {@code DISTINCT ON} fields (mutable). */
    public List<Field<?>> distinctOn() {
        return distinctOn;
    }

    /** Returns the selected fields (mutable). */
    public List<Field<?>> fields() {
        return fields;
    }

    /** Returns the {@code FROM} tables (mutable). */
    public List<Table<?>> from() {
        return from;
    }

    /** Returns the joins (mutable). */
    public List<Join> joins() {
        return joins;
    }

    /** Returns the {@code WHERE} condition. */
    public Condition where() {
        return where;
    }

    /** Adds a {@code WHERE} condition with {@code AND}. */
    public void addWhere(Condition condition) {
        where = where.and(condition);
    }

    /** Returns the {@code GROUP BY} items (mutable). */
    public List<QueryPart> groupBy() {
        return groupBy;
    }

    /** Returns the {@code HAVING} condition. */
    public Condition having() {
        return having;
    }

    /** Adds a {@code HAVING} condition with {@code AND}. */
    public void addHaving(Condition condition) {
        having = having.and(condition);
    }

    /** Returns the named windows (mutable). */
    public List<WindowDefinition> windows() {
        return windows;
    }

    /** Returns the set operations (mutable). */
    public List<SetOperation> setOperations() {
        return setOperations;
    }

    /** Returns the {@code ORDER BY} items (mutable). */
    public List<SortField<?>> orderBy() {
        return orderBy;
    }

    /** Returns the {@code LIMIT} expression or {@code null}. */
    public QueryPart limit() {
        return limit;
    }

    /** Sets the {@code LIMIT} expression. */
    public void limit(QueryPart value) {
        limit = value;
    }

    /** Returns the {@code OFFSET} expression or {@code null}. */
    public QueryPart offset() {
        return offset;
    }

    /** Sets the {@code OFFSET} expression. */
    public void offset(QueryPart value) {
        offset = value;
    }

    /** Sets {@code FETCH FIRST n ROWS WITH TIES}. */
    public void withTies(boolean value) {
        withTies = value;
    }

    /** Returns the lock clauses (mutable). */
    public List<Lock> locks() {
        return locks;
    }

    /** Returns the keyset values: rows after these {@code ORDER BY} values are selected (mutable). */
    public List<Object> seekAfter() {
        return seekAfter;
    }

    @Override
    public List<Table<?>> tables() {
        List<Table<?>> result = new ArrayList<>(from);
        for (Join j : joins) result.add(j.table());
        return result;
    }

    @Override
    public SelectStatement copy() {
        SelectStatement c = new SelectStatement();
        c.with = with.copy();
        c.distinct = distinct;
        c.distinctOn = new ArrayList<>(distinctOn);
        c.fields = new ArrayList<>(fields);
        c.from = new ArrayList<>(from);
        c.joins = new ArrayList<>(joins);
        c.where = where;
        c.groupBy = new ArrayList<>(groupBy);
        c.having = having;
        c.windows = new ArrayList<>(windows);
        c.setOperations = new ArrayList<>(setOperations);
        c.orderBy = new ArrayList<>(orderBy);
        c.limit = limit;
        c.offset = offset;
        c.withTies = withTies;
        c.locks = new ArrayList<>(locks);
        c.seekAfter = new ArrayList<>(seekAfter);
        return c;
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    @Override
    public void render(RenderContext ctx) {
        with.render(ctx);
        ctx.append("SELECT ");
        if (!distinctOn.isEmpty()) {
            ctx.append("DISTINCT ON (").visitAll(distinctOn, ", ").append(") ");
        } else if (distinct) {
            ctx.append("DISTINCT ");
        }
        if (fields.isEmpty()) {
            ctx.append('*');
        } else {
            ctx.declaring(fields, ", ");
        }
        Condition filter = Condition.noCondition();
        if (!from.isEmpty()) {
            ctx.append(" FROM ").visitAll(from, ", ");
            for (Table<?> t : from) filter = filter.and(Condition.and(ctx.policyFilter(t)));
        }
        for (Join join : joins) renderJoin(ctx, join);
        Condition fullWhere = where.and(filter);
        if (!seekAfter.isEmpty()) fullWhere = fullWhere.and(Keyset.after(orderBy, seekAfter));
        if (fullWhere != Condition.noCondition()) ctx.append(" WHERE ").visit(fullWhere);
        if (!groupBy.isEmpty()) ctx.append(" GROUP BY ").visitAll(groupBy, ", ");
        if (having != Condition.noCondition()) ctx.append(" HAVING ").visit(having);
        if (!windows.isEmpty()) ctx.append(" WINDOW ").visitAll(windows, ", ");
        for (SetOperation op : setOperations) {
            ctx.append(' ').append(op.keyword()).append(" (");
            op.query().render(ctx);
            ctx.append(')');
        }
        if (!orderBy.isEmpty()) ctx.append(" ORDER BY ").visitAll(orderBy, ", ");
        if (withTies) {
            if (limit == null) throw new IllegalStateException("WITH TIES needs a limit");
            if (orderBy.isEmpty()) throw new IllegalStateException("WITH TIES needs ORDER BY");
            if (offset != null) ctx.append(" OFFSET ").visit(offset).append(" ROWS");
            ctx.append(" FETCH FIRST ").visit(limit).append(" ROWS WITH TIES");
        } else {
            if (limit != null) ctx.append(" LIMIT ").visit(limit);
            if (offset != null) ctx.append(" OFFSET ").visit(offset);
        }
        for (Lock lock : locks) {
            ctx.append(" FOR ").append(lock.strength());
            if (!lock.of().isEmpty()) {
                ctx.append(" OF ");
                for (int i = 0; i < lock.of().size(); i++) {
                    if (i > 0) ctx.append(", ");
                    ctx.identifier(lock.of().get(i).qualifier());
                }
            }
            if (lock.waitPolicy() != null) ctx.append(' ').append(lock.waitPolicy());
        }
    }

    private static void renderJoin(RenderContext ctx, Join join) {
        ctx.append(' ').append(join.type().sql()).append(' ').visit(join.table());
        if (!join.using().isEmpty()) {
            ctx.append(" USING (");
            for (int i = 0; i < join.using().size(); i++) {
                if (i > 0) ctx.append(", ");
                ctx.identifier(join.using().get(i).name());
            }
            ctx.append(')');
            return;
        }
        if (join.type() == Join.Type.CROSS || join.type() == Join.Type.NATURAL) return;
        Condition on = join.on() == null ? Condition.noCondition() : join.on();
        on = on.and(Condition.and(ctx.policyFilter(join.table())));
        ctx.append(" ON ").visit(on);
    }
}
