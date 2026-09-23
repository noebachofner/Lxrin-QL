package ch.lxrin.ql.query;

import ch.lxrin.ql.bind.BindMap;
import ch.lxrin.ql.bind.Binds;
import ch.lxrin.ql.exec.SqlExecutor;
import ch.lxrin.ql.exec.SqlExecutors;
import ch.lxrin.ql.expr.Expression;
import ch.lxrin.ql.expr.RenderContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Common base of all statements: common table expressions ({@code WITH}),
 * explicit bind parameters, executor selection and rendering.
 *
 * <p>A statement is also an {@link Expression}: when nested inside another
 * statement it renders in parentheses, which makes it usable as a sub-query,
 * derived table or CTE body.</p>
 *
 * @param <SELF> the concrete builder type, returned by fluent methods
 */
public abstract class AbstractStatement<SELF extends AbstractStatement<SELF>> implements Expression {

    private final List<Object[]> ctes = new ArrayList<>();    // {name, materialization keyword, statement}
    private boolean recursive;
    private BindMap namedBinds = new BindMap();
    private final List<Binds> bindSources = new ArrayList<>();
    private SqlExecutor executor;

    @SuppressWarnings("unchecked")
    protected final SELF self() {
        return (SELF) this;
    }

    // -------------------------------------------------------------------------
    // WITH
    // -------------------------------------------------------------------------

    /**
     * Adds a common table expression: {@code WITH name AS (statement)}.
     *
     * @param name      CTE name, optionally with a column list, e.g. {@code "totals(customer_id, total)"}
     * @param statement a {@code SELECT} or a data-modifying statement with {@code RETURNING}
     */
    public SELF with(String name, Object statement) {
        return addCte(name, "", statement);
    }

    /** Adds a CTE and marks the {@code WITH} clause as {@code RECURSIVE}. */
    public SELF withRecursive(String name, Object statement) {
        recursive = true;
        return with(name, statement);
    }

    /** Adds a CTE with {@code AS MATERIALIZED (...)} (PostgreSQL 12+). */
    public SELF withMaterialized(String name, Object statement) {
        return addCte(name, "MATERIALIZED ", statement);
    }

    /** Adds a CTE with {@code AS NOT MATERIALIZED (...)} (PostgreSQL 12+). */
    public SELF withNotMaterialized(String name, Object statement) {
        return addCte(name, "NOT MATERIALIZED ", statement);
    }

    private SELF addCte(String name, String keyword, Object statement) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("CTE name must not be blank");
        if (statement == null) throw new IllegalArgumentException("CTE statement must not be null");
        ctes.add(new Object[]{name, keyword, statement});
        return self();
    }

    // -------------------------------------------------------------------------
    // Binds and execution
    // -------------------------------------------------------------------------

    /**
     * Registers a {@link Binds} container. Its values are read when the
     * statement is rendered, so values added later are included as well.
     */
    public SELF bind(Binds binds) {
        if (binds == null) throw new IllegalArgumentException("binds must not be null");
        bindSources.add(binds);
        return self();
    }

    /** Adds a named bind parameter for a {@code :name} placeholder. */
    public SELF bind(String name, Object value) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("bind name must not be blank");
        namedBinds = namedBinds.put(name, value);
        return self();
    }

    /** Adds all entries of a {@link BindMap}. */
    public SELF bind(BindMap binds) {
        for (Map.Entry<String, Object> e : binds.asMap().entrySet()) {
            namedBinds = namedBinds.put(e.getKey(), e.getValue());
        }
        return self();
    }

    /** Uses a specific executor for this statement instead of the default one. */
    public SELF executor(SqlExecutor executor) {
        this.executor = executor;
        return self();
    }

    /** Returns the executor for this statement: the explicit one or the configured default. */
    protected SqlExecutor resolveExecutor() {
        return executor != null ? executor : SqlExecutors.getDefault();
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    /** Renders the statement and collects every bind parameter it needs. */
    public RenderedSql build() {
        RenderContext ctx = new RenderContext();
        renderStatement(ctx);
        return new RenderedSql(ctx.sql(), toBindMap(ctx));
    }

    /** Converts the parameters collected in a context into a {@link BindMap}. */
    protected static BindMap toBindMap(RenderContext ctx) {
        BindMap map = new BindMap();
        for (Map.Entry<String, Object> e : ctx.params().entrySet()) {
            map = map.put(e.getKey(), e.getValue());
        }
        return map;
    }

    /** Returns the SQL without executing it (useful for logging and tests). */
    public String buildSql() {
        return build().sql();
    }

    /** Returns all bind parameters of the rendered statement. */
    public BindMap getBinds() {
        return build().binds();
    }

    /** Renders the statement in parentheses, for use as a sub-query. */
    @Override
    public void render(RenderContext ctx) {
        ctx.append('(');
        renderStatement(ctx);
        ctx.append(')');
    }

    /** Renders the complete statement without surrounding parentheses. */
    public void renderStatement(RenderContext ctx) {
        for (Map.Entry<String, Object> e : namedBinds.asMap().entrySet()) {
            ctx.bindNamed(e.getKey(), e.getValue());
        }
        for (Binds b : bindSources) {
            for (Map.Entry<String, Object> e : b.asMap().entrySet()) {
                ctx.bindNamed(e.getKey(), e.getValue());
            }
        }
        if (!ctes.isEmpty()) {
            ctx.append(recursive ? "WITH RECURSIVE " : "WITH ");
            for (int i = 0; i < ctes.size(); i++) {
                if (i > 0) ctx.append(", ");
                Object[] cte = ctes.get(i);
                ctx.append((String) cte[0]).append(" AS ").append((String) cte[1]);
                visitParenthesized(ctx, cte[2]);
            }
            ctx.append(' ');
        }
        renderBody(ctx);
    }

    /** Renders the statement itself (everything after the {@code WITH} clause). */
    protected abstract void renderBody(RenderContext ctx);

    @Override
    public String toString() {
        return buildSql();
    }

    /** Renders a statement in parentheses; statements add their own, other elements get them here. */
    static void visitParenthesized(RenderContext ctx, Object statement) {
        if (statement instanceof AbstractStatement) {
            ctx.visit(statement);
        } else {
            ctx.append('(').visit(statement).append(')');
        }
    }
}
