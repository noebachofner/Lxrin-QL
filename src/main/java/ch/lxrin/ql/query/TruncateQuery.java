package ch.lxrin.ql.query;

import ch.lxrin.ql.expr.RenderContext;
import ch.lxrin.ql.table.TableDef;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code TRUNCATE} statement: {@code truncate(a, b).restartIdentity().cascade().execute()}.
 */
public class TruncateQuery extends AbstractStatement<TruncateQuery> {

    private final List<Object> tables = new ArrayList<>();
    private boolean restartIdentity;
    private boolean cascade;

    /** @param tables {@code TableDef}s or table names */
    public TruncateQuery(Object... tables) {
        if (tables.length == 0) throw new IllegalArgumentException("at least one table required");
        for (Object t : tables) this.tables.add(t instanceof TableDef ? ((TableDef) t).getTableName() : t);
    }

    /** {@code RESTART IDENTITY} – reset owned sequences. */
    public TruncateQuery restartIdentity() {
        this.restartIdentity = true;
        return this;
    }

    /** {@code CASCADE} – also truncate referencing tables. */
    public TruncateQuery cascade() {
        this.cascade = true;
        return this;
    }

    /** Executes the statement. */
    public void execute() {
        RenderedSql rendered = build();
        resolveExecutor().execute(rendered.sql(), rendered.binds());
    }

    @Override
    protected void renderBody(RenderContext ctx) {
        ctx.append("TRUNCATE ").visitAll(tables, ", ");
        if (restartIdentity) ctx.append(" RESTART IDENTITY");
        if (cascade) ctx.append(" CASCADE");
    }
}
