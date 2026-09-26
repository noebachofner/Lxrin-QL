package ch.lxrin.ql.dsl;

import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.schema.Table;

import java.util.Map;
import java.util.Optional;

/**
 * An {@code INSERT} built column by column:
 * <pre>{@code
 * insertInto(USERS).set(USERS.ID, UuidV7.generate()).set(USERS.NAME, "Ada").execute();
 * }</pre>
 * For several rows use {@link #newRow()} or the typed {@code columns(..).values(..)} form.
 *
 * @param <R> the table's row type
 */
public final class Insert<R> extends InsertColumns<R> {

    private Map<Column<?>, Field<?>> currentRow;

    /** Creates the builder; use {@code QL.insertInto(..)} or {@code ctx.insertInto(..)}. */
    public Insert(QueryContext context, Table<R> table) {
        super(context, table);
    }

    /** Sets a column value in the current row; {@code null} inserts {@code NULL}. */
    public <T> Insert<R> set(Column<T> column, T value) {
        return set(column, Values.param(value, column.type()));
    }

    /** Sets a column to an expression in the current row. */
    public <T> Insert<R> set(Column<T> column, Field<T> value) {
        return setField(column, value);
    }

    /** Sets a column value only if {@code apply} is {@code true}; otherwise the column gets its default. */
    public <T> Insert<R> setIf(boolean apply, Column<T> column, T value) {
        return apply ? set(column, value) : this;
    }

    /** Sets a column value if it is present; otherwise the column gets its default. */
    public <T> Insert<R> setIfPresent(Column<T> column, Optional<? extends T> value) {
        if (value == null) throw new IllegalArgumentException("optional must not be null");
        return value.isPresent() ? set(column, (T) value.get()) : this;
    }

    private <T> Insert<R> setField(Column<T> column, Field<T> value) {
        if (statement.source() != null) throw new IllegalStateException("set(..) cannot be combined with select(..)");
        if (column.generated()) throw new IllegalArgumentException("column " + column + " is generated and cannot be written");
        if (!column.table().sameTable(table())) {
            throw new IllegalArgumentException("column " + column + " does not belong to " + table().qualifiedName());
        }
        row().put(column, require(value));
        return this;
    }

    /**
     * Sets a column from a value whose type is only known at runtime, e.g. when
     * copying an {@code AffectedRow} into an audit table.
     *
     * @throws IllegalArgumentException if the value does not fit the column type
     */
    @SuppressWarnings("unchecked")
    public Insert<R> setUnchecked(Column<?> column, Object value) {
        Column<Object> c = (Column<Object>) column;
        return setField(c, Values.param(c.type().cast(value), c.type()));
    }

    /** Starts another row for a multi-row insert. */
    public Insert<R> newRow() {
        if (currentRow == null || currentRow.isEmpty()) throw new IllegalStateException("the current row is empty");
        currentRow = statement.addRow();
        return this;
    }

    /** {@code INSERT INTO t DEFAULT VALUES} */
    public Insert<R> defaultValues() {
        if (!statement.rows().isEmpty()) throw new IllegalStateException("defaultValues() cannot be combined with set(..)");
        statement.defaultValues(true);
        return this;
    }

    private Map<Column<?>, Field<?>> row() {
        if (currentRow == null) currentRow = statement.addRow();
        return currentRow;
    }
}
