package ch.lxrin.ql.dsl;

import ch.lxrin.ql.render.RenderContext;
import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.statement.TruncateStatement;

import java.util.List;

/** A {@code TRUNCATE}: {@code truncate(A, B).restartIdentity().cascade().execute()}. */
public final class Truncate {

    private QueryContext context;
    private final TruncateStatement statement;

    /** Creates the builder; use {@code QL.truncate(..)} or {@code ctx.truncate(..)}. */
    public Truncate(QueryContext context, List<Table<?>> tables) {
        this.context = context;
        this.statement = new TruncateStatement(tables);
    }

    /** {@code RESTART IDENTITY} – resets owned sequences. */
    public Truncate restartIdentity() {
        statement.restartIdentity(true);
        return this;
    }

    /** {@code CASCADE} – also truncates referencing tables. */
    public Truncate cascade() {
        statement.cascade(true);
        return this;
    }

    /** Attaches the builder to a query context. */
    public Truncate attach(QueryContext queryContext) {
        this.context = queryContext;
        return this;
    }

    /** Executes the statement. */
    public void execute() {
        (context != null ? context : QueryContext.getDefault()).truncate(statement, QueryContext.ExecOptions.dsl());
    }

    /** Returns the statement model. */
    public TruncateStatement statement() {
        return statement;
    }

    @Override
    public String toString() {
        RenderContext ctx = new RenderContext();
        statement.render(ctx);
        return ctx.sql();
    }
}
