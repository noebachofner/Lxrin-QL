package ch.lxrin.ql.statement;

import ch.lxrin.ql.dsl.Condition;
import ch.lxrin.ql.dsl.Field;
import ch.lxrin.ql.render.QueryPart;
import ch.lxrin.ql.render.RenderContext;
import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.schema.Table;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The model of an {@code INSERT}, including {@code ON CONFLICT}.
 *
 * <p>Rows are maps from column to value. The column list is the union of all
 * rows' columns; a row without a value for a column gets {@code DEFAULT}. So
 * rows with different sets of columns fit into one multi-row statement.</p>
 */
public final class InsertStatement extends DmlStatement {

    /** {@code OVERRIDING SYSTEM VALUE} or {@code OVERRIDING USER VALUE}. */
    public enum Overriding {
        /** Write explicit values into {@code GENERATED ALWAYS} identity columns. */
        SYSTEM_VALUE,
        /** Ignore the given values for identity columns. */
        USER_VALUE
    }

    private List<Map<Column<?>, Field<?>>> rows = new ArrayList<>();
    private List<Column<?>> sourceColumns = new ArrayList<>();
    private SelectStatement source;
    private boolean defaultValues;
    private Overriding overriding;

    private boolean onConflict;
    private List<Column<?>> conflictColumns = new ArrayList<>();
    private String conflictConstraint;
    private Condition conflictWhere = Condition.noCondition();
    private boolean doNothing;
    private Map<Column<?>, Field<?>> updates = new LinkedHashMap<>();
    private Condition updateWhere = Condition.noCondition();

    /** Creates an empty {@code INSERT INTO table}. */
    public InsertStatement(Table<?> table) {
        super(table);
    }

    @Override
    public StatementKind kind() {
        return StatementKind.INSERT;
    }

    // -------------------------------------------------------------------------
    // Values
    // -------------------------------------------------------------------------

    /** Returns the rows (mutable list of mutable maps). */
    public List<Map<Column<?>, Field<?>>> rows() {
        return rows;
    }

    /** Adds an empty row and returns it. */
    public Map<Column<?>, Field<?>> addRow() {
        Map<Column<?>, Field<?>> row = new LinkedHashMap<>();
        rows.add(row);
        return row;
    }

    /** Returns the column list: the union of the columns of all rows, or the columns of an {@code INSERT … SELECT}. */
    public List<Column<?>> columns() {
        if (source != null) return List.copyOf(sourceColumns);
        Set<Column<?>> set = new LinkedHashSet<>();
        for (Map<Column<?>, Field<?>> row : rows) set.addAll(row.keySet());
        return new ArrayList<>(set);
    }

    /**
     * Sets {@code column} to {@code value} in every row where it has no value yet
     * (or in every row if {@code overwrite} is {@code true}). Used by column conventions and listeners.
     *
     * @throws IllegalStateException for {@code INSERT … SELECT} and {@code DEFAULT VALUES}
     */
    public <T> void setValue(Column<T> column, Field<T> value, boolean overwrite) {
        requireColumnOf(column);
        if (source != null) throw new IllegalStateException("cannot add a column value to INSERT … SELECT");
        if (defaultValues) {
            defaultValues = false;
            addRow();
        }
        for (Map<Column<?>, Field<?>> row : rows) {
            if (overwrite || !row.containsKey(column)) row.put(column, value);
        }
    }

    /** Returns {@code INSERT … SELECT}'s query, or {@code null}. */
    public SelectStatement source() {
        return source;
    }

    /** Sets {@code INSERT INTO t (columns) SELECT ...}. */
    public void source(List<Column<?>> columns, SelectStatement query) {
        for (Column<?> c : columns) requireColumnOf(c);
        sourceColumns = new ArrayList<>(columns);
        source = query;
    }

    /** Returns {@code true} for {@code DEFAULT VALUES}. */
    public boolean defaultValues() {
        return defaultValues;
    }

    /** Sets {@code DEFAULT VALUES}. */
    public void defaultValues(boolean value) {
        defaultValues = value;
    }

    /** Returns the {@code OVERRIDING} option or {@code null}. */
    public Overriding overriding() {
        return overriding;
    }

    /** Sets the {@code OVERRIDING} option. */
    public void overriding(Overriding value) {
        overriding = value;
    }

    // -------------------------------------------------------------------------
    // ON CONFLICT
    // -------------------------------------------------------------------------

    /** Returns {@code true} if the statement has an {@code ON CONFLICT} clause. */
    public boolean onConflict() {
        return onConflict;
    }

    /** Sets {@code ON CONFLICT (columns)}; an empty list means any conflict. */
    public void onConflict(List<Column<?>> columns) {
        onConflict = true;
        conflictColumns = new ArrayList<>(columns);
        conflictConstraint = null;
    }

    /** Sets {@code ON CONFLICT ON CONSTRAINT name}. */
    public void onConflictOnConstraint(String constraint) {
        onConflict = true;
        conflictColumns = new ArrayList<>();
        conflictConstraint = constraint;
    }

    /** Adds an index predicate to the conflict target. */
    public void addConflictWhere(Condition condition) {
        conflictWhere = conflictWhere.and(condition);
    }

    /** Sets {@code DO NOTHING}. */
    public void doNothing() {
        onConflict = true;
        doNothing = true;
    }

    /** Returns {@code true} for {@code DO NOTHING}. */
    public boolean isDoNothing() {
        return doNothing;
    }

    /** Returns the {@code DO UPDATE SET} assignments (mutable). */
    public Map<Column<?>, Field<?>> updates() {
        return updates;
    }

    /** Adds a {@code DO UPDATE ... WHERE} condition. */
    public void addUpdateWhere(Condition condition) {
        updateWhere = updateWhere.and(condition);
    }

    /** Returns {@code true} for an upsert ({@code ON CONFLICT DO UPDATE}). */
    public boolean isUpsert() {
        return onConflict && !doNothing && !updates.isEmpty();
    }

    // -------------------------------------------------------------------------
    // Copy and render
    // -------------------------------------------------------------------------

    @Override
    public InsertStatement copy() {
        InsertStatement c = new InsertStatement(table());
        copyInto(c);
        c.rows = new ArrayList<>();
        for (Map<Column<?>, Field<?>> row : rows) c.rows.add(new LinkedHashMap<>(row));
        c.sourceColumns = new ArrayList<>(sourceColumns);
        c.source = source == null ? null : source.copy();
        c.defaultValues = defaultValues;
        c.overriding = overriding;
        c.onConflict = onConflict;
        c.conflictColumns = new ArrayList<>(conflictColumns);
        c.conflictConstraint = conflictConstraint;
        c.conflictWhere = conflictWhere;
        c.doNothing = doNothing;
        c.updates = new LinkedHashMap<>(updates);
        c.updateWhere = updateWhere;
        return c;
    }

    @Override
    public void render(RenderContext ctx) {
        with.render(ctx);
        ctx.append("INSERT INTO ");
        renderTarget(ctx);
        List<Column<?>> columns = columns();
        if (!columns.isEmpty()) {
            ctx.append(" (");
            renderColumnNames(ctx, columns);
            ctx.append(')');
        }
        if (overriding != null) ctx.append(" OVERRIDING ").append(overriding.name().replace('_', ' '));
        if (source != null) {
            ctx.append(' ');
            source.render(ctx);
        } else if (defaultValues || rows.isEmpty() || columns.isEmpty()) {
            if (!defaultValues && rows.isEmpty()) {
                throw new IllegalStateException("INSERT needs values, a SELECT or defaultValues()");
            }
            if (rows.size() > 1) throw new IllegalStateException("DEFAULT VALUES inserts exactly one row");
            ctx.append(" DEFAULT VALUES");
        } else {
            ctx.append(" VALUES ");
            for (int r = 0; r < rows.size(); r++) {
                if (r > 0) ctx.append(", ");
                ctx.append('(');
                Map<Column<?>, Field<?>> row = rows.get(r);
                for (int i = 0; i < columns.size(); i++) {
                    if (i > 0) ctx.append(", ");
                    Field<?> value = row.get(columns.get(i));
                    if (value == null) ctx.append("DEFAULT");
                    else ctx.visit(value);
                }
                ctx.append(')');
            }
        }
        renderOnConflict(ctx);
        renderReturning(ctx);
    }

    private void renderOnConflict(RenderContext ctx) {
        if (!onConflict) return;
        ctx.append(" ON CONFLICT");
        if (conflictConstraint != null) {
            ctx.append(" ON CONSTRAINT ").identifier(conflictConstraint);
        } else if (!conflictColumns.isEmpty()) {
            ctx.append(" (");
            renderColumnNames(ctx, conflictColumns);
            ctx.append(')');
            if (conflictWhere != Condition.noCondition()) {
                ctx.append(" WHERE ").withQualification(false, conflictWhere);
            }
        }
        if (doNothing || updates.isEmpty()) {
            if (!doNothing) throw new IllegalStateException("ON CONFLICT needs doNothing() or doUpdateSet(..)");
            ctx.append(" DO NOTHING");
            return;
        }
        if (conflictColumns.isEmpty() && conflictConstraint == null) {
            throw new IllegalStateException("ON CONFLICT DO UPDATE needs a conflict target: onConflict(columns) or onConflictOnConstraint(..)");
        }
        ctx.append(" DO UPDATE SET ");
        UpdateStatement.renderAssignments(ctx, updates);
        if (updateWhere != Condition.noCondition()) ctx.append(" WHERE ").visit(updateWhere);
    }

    static void renderColumnNames(RenderContext ctx, List<Column<?>> columns) {
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) ctx.append(", ");
            ctx.identifier(columns.get(i).name());
        }
    }

    private void requireColumnOf(Column<?> column) {
        if (!column.table().sameTable(table())) {
            throw new IllegalArgumentException("column " + column + " does not belong to " + table().qualifiedName());
        }
        if (column.generated()) throw new IllegalArgumentException("column " + column + " is generated and cannot be written");
    }

    /** Returns an {@code EXCLUDED.column} reference for {@code DO UPDATE SET}. */
    public static <T> QueryPart excluded(Column<T> column) {
        return ctx -> ctx.append("EXCLUDED.").identifier(column.name());
    }
}
