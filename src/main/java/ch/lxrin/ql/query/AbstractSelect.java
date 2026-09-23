package ch.lxrin.ql.query;

import ch.lxrin.ql.condition.ConditionList;
import ch.lxrin.ql.expr.AliasedExpression;
import ch.lxrin.ql.expr.Expressions;
import ch.lxrin.ql.expr.RenderContext;
import ch.lxrin.ql.expr.WindowSpec;
import ch.lxrin.ql.table.Column;
import ch.lxrin.ql.table.TableDef;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * All clauses of a PostgreSQL {@code SELECT} statement.
 *
 * <pre>
 * [WITH [RECURSIVE] ...]
 * SELECT [DISTINCT | DISTINCT ON (...)] items
 * [FROM ...] [JOIN ...]
 * [WHERE ...]
 * [GROUP BY ...] [HAVING ...]
 * [WINDOW ...]
 * [UNION | INTERSECT | EXCEPT [ALL] ...]
 * [ORDER BY ...]
 * [LIMIT n] [OFFSET n]
 * [FOR UPDATE | FOR NO KEY UPDATE | FOR SHARE | FOR KEY SHARE [OF ...] [NOWAIT | SKIP LOCKED]]
 * </pre>
 *
 * <p>Concrete builders add the terminal operations ({@link SelectQuery}
 * returns mapped rows; integrations may add their own).</p>
 *
 * @param <SELF> the concrete builder type
 */
public abstract class AbstractSelect<SELF extends AbstractSelect<SELF>> extends AbstractStatement<SELF> {

    private boolean distinct;
    private final List<Object> distinctOn = new ArrayList<>();
    private final List<Object> items = new ArrayList<>();
    private final List<Object> from = new ArrayList<>();
    private final List<Join> joins = new ArrayList<>();
    private final ConditionList where = new ConditionList();
    private final List<Object> groupBy = new ArrayList<>();
    private final ConditionList having = new ConditionList();
    private final List<Object[]> windows = new ArrayList<>();       // {name, spec}
    private final List<Object[]> setOperations = new ArrayList<>(); // {keyword, query}
    private final List<Object> orderBy = new ArrayList<>();
    private Object limit;
    private Object offset;
    private boolean withTies;
    private final List<Lock> locks = new ArrayList<>();

    // =========================================================================
    // SELECT list
    // =========================================================================

    /**
     * Adds items to the {@code SELECT} list: columns, expressions,
     * {@code expr.as("alias")}, sub-queries or SQL fragments. Collections
     * (such as {@code table.columns()}) are expanded.
     *
     * <p>A {@code String} is a SQL fragment; give it a name with
     * {@code as("t.FIRST_NAME", "firstName")} or {@code raw("t.FIRST_NAME").as("firstName")}.</p>
     */
    public SELF select(Object... items) {
        for (Object item : items) {
            if (item instanceof Collection) {
                this.items.addAll((Collection<?>) item);
            } else {
                this.items.add(item);
            }
        }
        return self();
    }

    /** {@code SELECT DISTINCT} */
    public SELF distinct() {
        this.distinct = true;
        return self();
    }

    /** {@code SELECT DISTINCT ON (expressions)} – PostgreSQL: first row of each group. */
    public SELF distinctOn(Object... expressions) {
        this.distinctOn.addAll(Arrays.asList(expressions));
        return self();
    }

    // =========================================================================
    // FROM and JOIN
    // =========================================================================

    /**
     * Adds {@code FROM} sources: table definitions, SQL fragments such as
     * {@code "PERSON p"}, aliased sub-queries ({@code query.as("x")}),
     * set-returning functions ({@code generateSeries(1, 10).as("n")}) or
     * {@code lateral(...)}. Several sources are separated by commas.
     */
    public SELF from(Object... sources) {
        from.addAll(Arrays.asList(sources));
        return self();
    }

    /** Appends a verbatim join clause, e.g. {@code "LEFT JOIN ADDRESS a ON a.PERSON_ID = p.ID"}. */
    public SELF join(String joinClause) {
        joins.add(new Join(null, joinClause, null));
        return self();
    }

    /** {@code [INNER] JOIN table ON conditions} */
    public SELF join(Object table, Object... on) {
        return addJoin("JOIN", table, on);
    }

    /** {@code INNER JOIN table ON conditions} */
    public SELF innerJoin(Object table, Object... on) {
        return addJoin("INNER JOIN", table, on);
    }

    /** {@code LEFT JOIN table ON conditions} ({@code ON TRUE} if no condition is given). */
    public SELF leftJoin(Object table, Object... on) {
        return addJoin("LEFT JOIN", table, on);
    }

    /** {@code RIGHT JOIN table ON conditions} */
    public SELF rightJoin(Object table, Object... on) {
        return addJoin("RIGHT JOIN", table, on);
    }

    /** {@code FULL JOIN table ON conditions} */
    public SELF fullJoin(Object table, Object... on) {
        return addJoin("FULL JOIN", table, on);
    }

    /** {@code CROSS JOIN table} */
    public SELF crossJoin(Object table) {
        joins.add(new Join("CROSS JOIN", table, null));
        return self();
    }

    /** {@code NATURAL JOIN table} */
    public SELF naturalJoin(Object table) {
        joins.add(new Join("NATURAL JOIN", table, null));
        return self();
    }

    /** {@code [INNER] JOIN table USING (columns)} */
    public SELF joinUsing(Object table, String... columns) {
        return addJoinUsing("JOIN", table, columns);
    }

    /** {@code LEFT JOIN table USING (columns)} */
    public SELF leftJoinUsing(Object table, String... columns) {
        return addJoinUsing("LEFT JOIN", table, columns);
    }

    private SELF addJoin(String keyword, Object table, Object[] on) {
        ConditionList conditions = new ConditionList();
        conditions.add(on);
        joins.add(new Join(keyword, table, conditions));
        return self();
    }

    private SELF addJoinUsing(String keyword, Object table, String[] columns) {
        if (columns.length == 0) throw new IllegalArgumentException("USING needs at least one column");
        joins.add(new Join(keyword, table, "USING (" + String.join(", ", columns) + ")"));
        return self();
    }

    // =========================================================================
    // WHERE / GROUP BY / HAVING / WINDOW
    // =========================================================================

    /**
     * Adds {@code WHERE} conditions. Adjacent conditions without an explicit
     * {@code and()} / {@code or()} are joined with {@code AND}; several
     * {@code where(..)} calls are joined with {@code AND} as well.
     *
     * @see ConditionList
     */
    public SELF where(Object... conditions) {
        where.add(conditions);
        return self();
    }

    /** Adds the conditions only if {@code apply} is {@code true} – handy for optional search filters. */
    public SELF whereIf(boolean apply, Object... conditions) {
        return apply ? where(conditions) : self();
    }

    /**
     * Adds a lazily created condition only if {@code apply} is {@code true}, so
     * the condition may use values that are {@code null} when disabled:
     * {@code whereIf(name != null, () -> ilike(p.lastName, val(name + "%")))}.
     */
    public SELF whereIf(boolean apply, java.util.function.Supplier<?> condition) {
        return apply ? where(condition.get()) : self();
    }

    /**
     * Adds {@code GROUP BY} items. Use {@code rollup(..)}, {@code cube(..)} or
     * {@code groupingSets(..)} for advanced grouping.
     */
    public SELF groupBy(Object... items) {
        groupBy.addAll(Arrays.asList(items));
        return self();
    }

    /** Adds {@code HAVING} conditions (same joining rules as {@link #where}). */
    public SELF having(Object... conditions) {
        having.add(conditions);
        return self();
    }

    /** Adds a named window: {@code WINDOW name AS (spec)}; reference it with {@code over("name")}. */
    public SELF window(String name, WindowSpec spec) {
        windows.add(new Object[]{name, spec});
        return self();
    }

    // =========================================================================
    // Set operations
    // =========================================================================

    /** {@code ... UNION (other)} – removes duplicates. */
    public SELF union(Object other) { return setOperation("UNION", other); }

    /** {@code ... UNION ALL (other)} */
    public SELF unionAll(Object other) { return setOperation("UNION ALL", other); }

    /** {@code ... INTERSECT (other)} */
    public SELF intersect(Object other) { return setOperation("INTERSECT", other); }

    /** {@code ... INTERSECT ALL (other)} */
    public SELF intersectAll(Object other) { return setOperation("INTERSECT ALL", other); }

    /** {@code ... EXCEPT (other)} */
    public SELF except(Object other) { return setOperation("EXCEPT", other); }

    /** {@code ... EXCEPT ALL (other)} */
    public SELF exceptAll(Object other) { return setOperation("EXCEPT ALL", other); }

    private SELF setOperation(String keyword, Object other) {
        if (other == null) throw new IllegalArgumentException("query must not be null");
        setOperations.add(new Object[]{keyword, other});
        return self();
    }

    // =========================================================================
    // ORDER BY / LIMIT / OFFSET
    // =========================================================================

    /** Adds {@code ORDER BY} items; use {@code col.desc()}, {@code desc(expr).nullsLast()} etc. */
    public SELF orderBy(Object... sortFields) {
        orderBy.addAll(Arrays.asList(sortFields));
        return self();
    }

    /** {@code LIMIT n} */
    public SELF limit(long n) {
        this.limit = Expressions.raw(Long.toString(n));
        return self();
    }

    /** {@code LIMIT expr} – e.g. a placeholder or {@code val(pageSize)}. */
    public SELF limit(Object n) {
        this.limit = n;
        return self();
    }

    /** {@code OFFSET n} */
    public SELF offset(long n) {
        this.offset = Expressions.raw(Long.toString(n));
        return self();
    }

    /** {@code OFFSET expr} */
    public SELF offset(Object n) {
        this.offset = n;
        return self();
    }

    /** {@code FETCH FIRST n ROWS WITH TIES} – requires {@code ORDER BY} (PostgreSQL 13+). */
    public SELF limitWithTies(long n) {
        limit(n);
        this.withTies = true;
        return self();
    }

    /** Convenience for paging: {@code LIMIT pageSize OFFSET pageIndex * pageSize} (page index starts at 0). */
    public SELF page(int pageIndex, int pageSize) {
        if (pageIndex < 0 || pageSize <= 0) throw new IllegalArgumentException("invalid page " + pageIndex + "/" + pageSize);
        return limit(pageSize).offset((long) pageIndex * pageSize);
    }

    // =========================================================================
    // Row locking
    // =========================================================================

    /** {@code FOR UPDATE} */
    public SELF forUpdate() { return lock("FOR UPDATE"); }

    /** {@code FOR NO KEY UPDATE} */
    public SELF forNoKeyUpdate() { return lock("FOR NO KEY UPDATE"); }

    /** {@code FOR SHARE} */
    public SELF forShare() { return lock("FOR SHARE"); }

    /** {@code FOR KEY SHARE} */
    public SELF forKeyShare() { return lock("FOR KEY SHARE"); }

    /** Restricts the last lock clause to the given tables: {@code OF p, a}. */
    public SELF of(TableDef... tables) {
        Lock lock = lastLock("of");
        for (TableDef t : tables) lock.of.add(t.getAlias());
        return self();
    }

    /** Adds {@code NOWAIT} to the last lock clause. */
    public SELF nowait() {
        lastLock("nowait").waitPolicy = "NOWAIT";
        return self();
    }

    /** Adds {@code SKIP LOCKED} to the last lock clause – typical for job queues. */
    public SELF skipLocked() {
        lastLock("skipLocked").waitPolicy = "SKIP LOCKED";
        return self();
    }

    private SELF lock(String strength) {
        locks.add(new Lock(strength));
        return self();
    }

    private Lock lastLock(String method) {
        if (locks.isEmpty()) throw new IllegalStateException(method + "() requires a preceding forUpdate()/forShare()");
        return locks.get(locks.size() - 1);
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    @Override
    protected void renderBody(RenderContext ctx) {
        if (items.isEmpty() && from.isEmpty()) {
            throw new IllegalStateException("SELECT needs at least a select item or a FROM clause – call .from(..) or .select(..)");
        }
        ctx.append("SELECT ");
        if (!distinctOn.isEmpty()) {
            ctx.append("DISTINCT ON (").visitAll(distinctOn, ", ").append(") ");
        } else if (distinct) {
            ctx.append("DISTINCT ");
        }
        if (items.isEmpty()) {
            ctx.append('*');
        } else {
            ctx.visitAll(items, ", ");
        }
        if (!from.isEmpty()) {
            ctx.append(" FROM ").visitAll(from, ", ");
        }
        for (Join join : joins) {
            ctx.append(' ');
            join.render(ctx);
        }
        if (!where.isEmpty()) {
            ctx.append(" WHERE ").visit(where);
        }
        if (!groupBy.isEmpty()) {
            ctx.append(" GROUP BY ").visitAll(groupBy, ", ");
        }
        if (!having.isEmpty()) {
            ctx.append(" HAVING ").visit(having);
        }
        if (!windows.isEmpty()) {
            ctx.append(" WINDOW ");
            for (int i = 0; i < windows.size(); i++) {
                if (i > 0) ctx.append(", ");
                ctx.append((String) windows.get(i)[0]).append(" AS ").visit(windows.get(i)[1]);
            }
        }
        for (Object[] op : setOperations) {
            ctx.append(' ').append((String) op[0]).append(' ');
            visitParenthesized(ctx, op[1]);
        }
        if (!orderBy.isEmpty()) {
            ctx.append(" ORDER BY ").visitAll(orderBy, ", ");
        }
        if (withTies) {
            if (offset != null) ctx.append(" OFFSET ").visit(offset).append(" ROWS");
            ctx.append(" FETCH FIRST ").visit(limit).append(" ROWS WITH TIES");
        } else {
            if (limit != null) ctx.append(" LIMIT ").visit(limit);
            if (offset != null) ctx.append(" OFFSET ").visit(offset);
        }
        for (Lock lock : locks) {
            ctx.append(' ').append(lock.strength);
            if (!lock.of.isEmpty()) ctx.append(" OF ").append(String.join(", ", lock.of));
            if (lock.waitPolicy != null) ctx.append(' ').append(lock.waitPolicy);
        }
    }

    // =========================================================================
    // Accessors for subclasses and integrations
    // =========================================================================

    /** Returns the {@code SELECT} items in order. */
    public List<Object> getSelectItems() {
        return List.copyOf(items);
    }

    /**
     * Returns the result name of every select item: the alias of
     * {@code expr.as(..)} / {@code select(expr, alias)}, the Java alias of a
     * {@link Column}, or {@code null} if the item has no name.
     */
    public List<String> getSelectNames() {
        List<String> names = new ArrayList<>(items.size());
        for (Object item : items) names.add(nameOf(item));
        return names;
    }

    static String nameOf(Object item) {
        if (item instanceof AliasedExpression) return ((AliasedExpression) item).getAlias();
        if (item instanceof Column) return ((Column) item).getAlias();
        return null;
    }

    // =========================================================================
    // Internal types
    // =========================================================================

    private static final class Join {
        final String keyword;      // null for a verbatim join clause
        final Object table;
        final Object condition;    // ConditionList, "USING (...)" or null

        Join(String keyword, Object table, Object condition) {
            this.keyword = keyword;
            this.table = table;
            this.condition = condition;
        }

        void render(RenderContext ctx) {
            if (keyword == null) {
                ctx.visit(table);
                return;
            }
            ctx.append(keyword).append(' ').visit(table);
            if (condition instanceof String) {
                ctx.append(' ').append((String) condition);
            } else if (condition instanceof ConditionList) {
                ConditionList on = (ConditionList) condition;
                ctx.append(" ON ");
                if (on.isEmpty()) ctx.append("TRUE");
                else ctx.visit(on);
            }
        }
    }

    private static final class Lock {
        final String strength;
        final List<String> of = new ArrayList<>();
        String waitPolicy;

        Lock(String strength) {
            this.strength = strength;
        }
    }
}
