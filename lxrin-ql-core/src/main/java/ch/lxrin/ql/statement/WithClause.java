package ch.lxrin.ql.statement;

import ch.lxrin.ql.dsl.Cte;
import ch.lxrin.ql.render.RenderContext;

import java.util.ArrayList;
import java.util.List;

/** The common table expressions of a statement. */
public final class WithClause {

    private final List<Cte> ctes = new ArrayList<>();
    private boolean recursive;

    /** Adds CTEs. */
    public void add(boolean recursive, Cte... more) {
        this.recursive |= recursive;
        for (Cte c : more) {
            if (c == null) throw new IllegalArgumentException("CTE must not be null");
            this.recursive |= c.recursive();
            ctes.add(c);
        }
    }

    /** Returns the CTEs. */
    public List<Cte> ctes() {
        return List.copyOf(ctes);
    }

    /** Returns {@code true} if the clause is {@code WITH RECURSIVE}. */
    public boolean recursive() {
        return recursive;
    }

    WithClause copy() {
        WithClause copy = new WithClause();
        copy.ctes.addAll(ctes);
        copy.recursive = recursive;
        return copy;
    }

    void render(RenderContext ctx) {
        if (ctes.isEmpty()) return;
        ctx.append(recursive ? "WITH RECURSIVE " : "WITH ");
        for (int i = 0; i < ctes.size(); i++) {
            if (i > 0) ctx.append(", ");
            ctes.get(i).renderDefinition(ctx);
        }
        ctx.append(' ');
    }
}
