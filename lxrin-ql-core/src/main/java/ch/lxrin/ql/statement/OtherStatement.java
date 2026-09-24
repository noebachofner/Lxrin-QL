package ch.lxrin.ql.statement;

import ch.lxrin.ql.render.QueryPart;
import ch.lxrin.ql.render.RenderContext;
import ch.lxrin.ql.schema.Table;

import java.util.List;

/** A statement written with {@code Sql.statement(..)}. */
public final class OtherStatement implements Statement {

    private final QueryPart body;

    /** Wraps a raw statement. */
    public OtherStatement(QueryPart body) {
        this.body = body;
    }

    @Override
    public StatementKind kind() {
        return StatementKind.OTHER;
    }

    @Override
    public OtherStatement copy() {
        return this;
    }

    @Override
    public List<Table<?>> tables() {
        return List.of();
    }

    @Override
    public void render(RenderContext ctx) {
        ctx.visit(body);
    }
}
