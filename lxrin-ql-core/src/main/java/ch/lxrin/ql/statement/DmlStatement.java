package ch.lxrin.ql.statement;

import ch.lxrin.ql.dsl.Field;
import ch.lxrin.ql.render.RenderContext;
import ch.lxrin.ql.schema.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * Common part of {@code INSERT}, {@code UPDATE} and {@code DELETE}: the
 * target table, {@code WITH} and {@code RETURNING}.
 *
 * <p>{@code RETURNING} has two parts: the fields the caller asked for, and
 * extra fields requested by listeners. Both are rendered; the result is split
 * again after execution so the caller only sees its own projection.</p>
 */
public abstract class DmlStatement implements Statement {

    private final Table<?> table;
    WithClause with = new WithClause();
    List<Field<?>> returning = new ArrayList<>();
    List<Field<?>> extraReturning = new ArrayList<>();

    DmlStatement(Table<?> table) {
        if (table == null) throw new IllegalArgumentException("table must not be null");
        if (table.readOnly()) throw new IllegalArgumentException("table " + table.qualifiedName() + " is read-only");
        this.table = table;
    }

    /** Returns the target table. */
    public Table<?> table() {
        return table;
    }

    /** Returns the {@code WITH} clause. */
    public WithClause with() {
        return with;
    }

    /** Returns the {@code RETURNING} fields requested by the caller (mutable). */
    public List<Field<?>> returning() {
        return returning;
    }

    /** Returns the {@code RETURNING} fields requested by listeners (mutable). */
    public List<Field<?>> extraReturning() {
        return extraReturning;
    }

    /** Returns the caller's fields followed by the extra fields. */
    public List<Field<?>> allReturning() {
        List<Field<?>> all = new ArrayList<>(returning);
        all.addAll(extraReturning);
        return all;
    }

    @Override
    public List<Table<?>> tables() {
        return List.of(table);
    }

    void copyInto(DmlStatement copy) {
        copy.with = with.copy();
        copy.returning = new ArrayList<>(returning);
        copy.extraReturning = new ArrayList<>(extraReturning);
    }

    void renderReturning(RenderContext ctx) {
        List<Field<?>> all = allReturning();
        if (!all.isEmpty()) ctx.append(" RETURNING ").declaring(all, ", ");
    }

    void renderTarget(RenderContext ctx) {
        table.renderName(ctx);
        table.alias().ifPresent(a -> ctx.append(" AS ").identifier(a));
    }
}
