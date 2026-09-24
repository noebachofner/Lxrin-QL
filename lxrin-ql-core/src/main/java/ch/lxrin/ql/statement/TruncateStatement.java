package ch.lxrin.ql.statement;

import ch.lxrin.ql.render.RenderContext;
import ch.lxrin.ql.schema.Table;

import java.util.ArrayList;
import java.util.List;

/** The model of a {@code TRUNCATE}. */
public final class TruncateStatement implements Statement {

    private final List<Table<?>> tables;
    private boolean restartIdentity;
    private boolean cascade;

    /** Creates {@code TRUNCATE tables}. */
    public TruncateStatement(List<Table<?>> tables) {
        if (tables.isEmpty()) throw new IllegalArgumentException("at least one table required");
        for (Table<?> t : tables) {
            if (t.readOnly()) throw new IllegalArgumentException("table " + t.qualifiedName() + " is read-only");
        }
        this.tables = new ArrayList<>(tables);
    }

    @Override
    public StatementKind kind() {
        return StatementKind.TRUNCATE;
    }

    @Override
    public List<Table<?>> tables() {
        return List.copyOf(tables);
    }

    /** Sets {@code RESTART IDENTITY}. */
    public void restartIdentity(boolean value) {
        restartIdentity = value;
    }

    /** Sets {@code CASCADE}. */
    public void cascade(boolean value) {
        cascade = value;
    }

    /** Returns {@code true} for {@code CASCADE}. */
    public boolean cascade() {
        return cascade;
    }

    @Override
    public TruncateStatement copy() {
        TruncateStatement c = new TruncateStatement(tables);
        c.restartIdentity = restartIdentity;
        c.cascade = cascade;
        return c;
    }

    @Override
    public void render(RenderContext ctx) {
        ctx.append("TRUNCATE ");
        for (int i = 0; i < tables.size(); i++) {
            if (i > 0) ctx.append(", ");
            tables.get(i).renderName(ctx);
        }
        if (restartIdentity) ctx.append(" RESTART IDENTITY");
        if (cascade) ctx.append(" CASCADE");
    }
}
