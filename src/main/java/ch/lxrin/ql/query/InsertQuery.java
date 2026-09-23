package ch.lxrin.ql.query;

import ch.lxrin.ql.condition.ConditionList;
import ch.lxrin.ql.expr.RenderContext;
import ch.lxrin.ql.table.Column;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@code INSERT} statement including PostgreSQL upserts.
 *
 * <pre>{@code
 * // column / value pairs
 * Long id = insertInto(p)
 *     .set(p.firstName, val("Ada"))
 *     .set(p.lastName,  val("Lovelace"))
 *     .returning(p.personNr)
 *     .single(Long.class);
 *
 * // multi-row VALUES
 * insertInto(p).columns(p.firstName, p.lastName)
 *     .values(val("Ada"), val("Lovelace"))
 *     .values(val("Alan"), val("Turing"))
 *     .execute();
 *
 * // INSERT ... SELECT
 * insertInto(archive).columns(archive.id, archive.name)
 *     .select(select(p.personNr, p.lastName).from(p).where(p.status.eq(val("OLD"))))
 *     .execute();
 *
 * // upsert
 * insertInto(p).set(p.email, val(mail)).set(p.lastName, val(name))
 *     .onConflict(p.email).doUpdateSetExcluded(p.lastName)
 *     .execute();
 * }</pre>
 */
public class InsertQuery extends AbstractDml<InsertQuery> {

    private final List<Object> columns = new ArrayList<>();
    private final List<List<Object>> rows = new ArrayList<>();
    private List<Object> pendingRow;          // values collected via set(..)
    private Object source;                    // INSERT ... SELECT
    private boolean defaultValues;
    private String overriding;

    private List<Object> conflictTarget;      // null = no ON CONFLICT
    private String conflictConstraint;
    private final ConditionList conflictTargetWhere = new ConditionList();
    private boolean doNothing;
    private final List<Object[]> updateAssignments = new ArrayList<>();
    private final ConditionList updateWhere = new ConditionList();

    /** @param table target table ({@code TableDef} or SQL fragment) */
    public InsertQuery(Object table) {
        super(table);
    }

    /** Sets the column list. */
    public InsertQuery columns(Object... columns) {
        if (pendingRow != null) throw new IllegalStateException("columns(..) cannot be combined with set(..)");
        this.columns.addAll(Arrays.asList(columns));
        return this;
    }

    /** Adds a row of values; call repeatedly for a multi-row insert. */
    public InsertQuery values(Object... values) {
        if (pendingRow != null) throw new IllegalStateException("values(..) cannot be combined with set(..)");
        rows.add(list(values));
        return this;
    }

    /** Adds a column together with its value (single-row insert). */
    public InsertQuery set(Object column, Object value) {
        if (pendingRow == null) {
            if (!columns.isEmpty() || !rows.isEmpty()) {
                throw new IllegalStateException("set(..) cannot be combined with columns(..)/values(..)");
            }
            pendingRow = new ArrayList<>();
            rows.add(pendingRow);
        }
        columns.add(column);
        pendingRow.add(value);
        return this;
    }

    /** {@code INSERT INTO t (cols) SELECT ...} */
    public InsertQuery select(Object query) {
        this.source = query;
        return this;
    }

    /** {@code INSERT INTO t DEFAULT VALUES} */
    public InsertQuery defaultValues() {
        this.defaultValues = true;
        return this;
    }

    /** {@code OVERRIDING SYSTEM VALUE} – write explicit values into identity columns. */
    public InsertQuery overridingSystemValue() {
        this.overriding = "OVERRIDING SYSTEM VALUE";
        return this;
    }

    /** {@code OVERRIDING USER VALUE} */
    public InsertQuery overridingUserValue() {
        this.overriding = "OVERRIDING USER VALUE";
        return this;
    }

    // -------------------------------------------------------------------------
    // ON CONFLICT
    // -------------------------------------------------------------------------

    /** {@code ON CONFLICT (columns)} – follow with {@link #doNothing()} or {@code doUpdateSet..}. */
    public InsertQuery onConflict(Object... targetColumns) {
        this.conflictTarget = list(targetColumns);
        return this;
    }

    /** Adds an index predicate to the conflict target: {@code ON CONFLICT (cols) WHERE ...} (partial unique indexes). */
    public InsertQuery onConflictWhere(Object... conditions) {
        if (conflictTarget == null) throw new IllegalStateException("call onConflict(..) first");
        conflictTargetWhere.add(conditions);
        return this;
    }

    /** {@code ON CONFLICT ON CONSTRAINT name} */
    public InsertQuery onConflictOnConstraint(String constraintName) {
        this.conflictTarget = new ArrayList<>();
        this.conflictConstraint = constraintName;
        return this;
    }

    /** {@code ON CONFLICT DO NOTHING}; without a preceding {@code onConflict(..)} any conflict is ignored. */
    public InsertQuery doNothing() {
        if (conflictTarget == null) conflictTarget = new ArrayList<>();
        this.doNothing = true;
        return this;
    }

    /** {@code ON CONFLICT (...) DO UPDATE SET column = value} */
    public InsertQuery doUpdateSet(Object column, Object value) {
        if (conflictTarget == null) throw new IllegalStateException("call onConflict(..) first");
        updateAssignments.add(new Object[]{column, value});
        return this;
    }

    /** {@code DO UPDATE SET col = EXCLUDED.col} for each given column – the classic upsert. */
    public InsertQuery doUpdateSetExcluded(Column... columns) {
        for (Column c : columns) doUpdateSet(c, "EXCLUDED." + c.getName());
        return this;
    }

    /** {@code DO UPDATE SET ... WHERE conditions} */
    public InsertQuery doUpdateWhere(Object... conditions) {
        updateWhere.add(conditions);
        return this;
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    @Override
    protected void renderBody(RenderContext ctx) {
        ctx.append("INSERT INTO ");
        renderTable(ctx, " AS ");
        if (!columns.isEmpty()) {
            ctx.append(" (");
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) ctx.append(", ");
                renderColumnName(ctx, columns.get(i));
            }
            ctx.append(')');
        }
        if (overriding != null) ctx.append(' ').append(overriding);
        if (defaultValues) {
            ctx.append(" DEFAULT VALUES");
        } else if (source != null) {
            ctx.append(' ');
            if (source instanceof AbstractStatement) ((AbstractStatement<?>) source).renderStatement(ctx);
            else ctx.visit(source);
        } else {
            if (rows.isEmpty()) throw new IllegalStateException("INSERT needs values(..), set(..), select(..) or defaultValues()");
            ctx.append(" VALUES ");
            for (int r = 0; r < rows.size(); r++) {
                List<Object> row = rows.get(r);
                if (!columns.isEmpty() && row.size() != columns.size()) {
                    throw new IllegalStateException("row " + r + " has " + row.size() + " values but " + columns.size() + " columns");
                }
                if (r > 0) ctx.append(", ");
                ctx.append('(').visitAll(row, ", ").append(')');
            }
        }
        renderOnConflict(ctx);
        renderReturning(ctx);
    }

    private void renderOnConflict(RenderContext ctx) {
        if (conflictTarget == null) return;
        ctx.append(" ON CONFLICT");
        if (conflictConstraint != null) {
            ctx.append(" ON CONSTRAINT ").append(conflictConstraint);
        } else if (!conflictTarget.isEmpty()) {
            ctx.append(" (");
            for (int i = 0; i < conflictTarget.size(); i++) {
                if (i > 0) ctx.append(", ");
                renderColumnName(ctx, conflictTarget.get(i));
            }
            ctx.append(')');
            if (!conflictTargetWhere.isEmpty()) ctx.append(" WHERE ").visit(conflictTargetWhere);
        }
        if (doNothing || updateAssignments.isEmpty()) {
            if (!doNothing) throw new IllegalStateException("ON CONFLICT needs doNothing() or doUpdateSet(..)");
            ctx.append(" DO NOTHING");
        } else {
            ctx.append(" DO UPDATE SET ");
            renderAssignments(ctx, updateAssignments);
            renderWhere(ctx, updateWhere);
        }
    }
}
