package ch.lxrin.ql.query;

import ch.lxrin.ql.RowMapper;
import ch.lxrin.ql.condition.ConditionList;
import ch.lxrin.ql.exec.ResultMapping;
import ch.lxrin.ql.expr.RenderContext;
import ch.lxrin.ql.table.Column;
import ch.lxrin.ql.table.TableDef;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Common base of {@code INSERT}, {@code UPDATE} and {@code DELETE}:
 * {@code RETURNING} and execution.
 *
 * @param <SELF> the concrete builder type
 */
public abstract class AbstractDml<SELF extends AbstractDml<SELF>> extends AbstractStatement<SELF> {

    /** Target table: a {@link TableDef} or a SQL fragment. */
    protected final Object table;
    private final List<Object> returning = new ArrayList<>();

    protected AbstractDml(Object table) {
        if (table == null) throw new IllegalArgumentException("table must not be null");
        this.table = table;
    }

    /** Adds {@code RETURNING} items (columns, expressions, {@code "*"}). */
    public SELF returning(Object... items) {
        for (Object item : items) {
            if (item instanceof Collection) returning.addAll((Collection<?>) item);
            else returning.add(item);
        }
        return self();
    }

    /** Executes the statement and returns the number of affected rows. */
    public int execute() {
        RenderedSql rendered = build();
        return resolveExecutor().execute(rendered.sql(), rendered.binds());
    }

    /** Executes a statement with {@code RETURNING} and maps all returned rows. */
    public <R> List<R> multiple(Class<R> type) {
        return fetch(ResultMapping.forType(type, returningNames()));
    }

    /** Executes a statement with {@code RETURNING} and maps all returned rows with a custom mapper. */
    public <R> List<R> multiple(RowMapper<R> mapper) {
        return fetch(mapper);
    }

    /**
     * Executes a statement with {@code RETURNING} and returns the first
     * returned row, e.g. a generated id: {@code insertInto(p)...returning(p.id).single(Long.class)}.
     */
    public <R> R single(Class<R> type) {
        List<R> rows = multiple(type);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Like {@link #single(Class)} but wrapped in an {@link Optional}. */
    public <R> Optional<R> optional(Class<R> type) {
        return Optional.ofNullable(single(type));
    }

    private <R> List<R> fetch(RowMapper<R> mapper) {
        if (returning.isEmpty()) throw new IllegalStateException("add returning(..) to fetch rows from a data-modifying statement");
        RenderedSql rendered = build();
        Object[][] rows = resolveExecutor().select(rendered.sql(), rendered.binds());
        List<R> result = new ArrayList<>();
        if (rows != null) for (Object[] row : rows) result.add(mapper.map(row));
        return result;
    }

    private List<String> returningNames() {
        List<String> names = new ArrayList<>();
        for (Object item : returning) names.add(AbstractSelect.nameOf(item));
        return names;
    }

    /** Renders the {@code RETURNING} clause, if any. */
    protected void renderReturning(RenderContext ctx) {
        if (!returning.isEmpty()) ctx.append(" RETURNING ").visitAll(returning, ", ");
    }

    /** Renders the target table as {@code NAME alias} (or the SQL fragment). */
    protected void renderTable(RenderContext ctx, String aliasKeyword) {
        if (table instanceof TableDef) {
            TableDef t = (TableDef) table;
            ctx.append(t.getTableName()).append(aliasKeyword).append(t.getAlias());
        } else {
            ctx.visit(table);
        }
    }

    /** Renders a column reference without table qualifier, as required by {@code INSERT} and {@code SET}. */
    protected static void renderColumnName(RenderContext ctx, Object column) {
        if (column instanceof Column) {
            ctx.append(((Column) column).getName());
        } else if (column instanceof String) {
            ctx.append((String) column);
        } else {
            ctx.visit(column);
        }
    }

    /** Renders {@code col = value, col2 = value2}. */
    protected static void renderAssignments(RenderContext ctx, List<Object[]> assignments) {
        for (int i = 0; i < assignments.size(); i++) {
            if (i > 0) ctx.append(", ");
            Object[] a = assignments.get(i);
            if (a[0] instanceof Object[]) {                         // (a, b) = (SELECT ...)
                ctx.append('(');
                Object[] cols = (Object[]) a[0];
                for (int c = 0; c < cols.length; c++) {
                    if (c > 0) ctx.append(", ");
                    renderColumnName(ctx, cols[c]);
                }
                ctx.append(") = ");
                visitParenthesized(ctx, a[1]);
            } else {
                renderColumnName(ctx, a[0]);
                ctx.append(" = ").visit(a[1]);
            }
        }
    }

    /** Renders {@code WHERE ...} if the list is not empty. */
    protected static void renderWhere(RenderContext ctx, ConditionList where) {
        if (!where.isEmpty()) ctx.append(" WHERE ").visit(where);
    }

    static List<Object> list(Object... items) {
        return new ArrayList<>(Arrays.asList(items));
    }
}
