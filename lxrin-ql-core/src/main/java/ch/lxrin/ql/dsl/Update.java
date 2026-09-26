package ch.lxrin.ql.dsl;

import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.statement.UpdateStatement;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * An {@code UPDATE}. For safety an update without {@code WHERE} is rejected;
 * call {@link #allRows()} to update every row on purpose.
 *
 * @param <R> the table's row type
 */
public final class Update<R> extends AbstractDml<R, Update<R>> {

    private final UpdateStatement statement;

    /** Creates the builder; use {@code Dsl.update(..)} or {@code ctx.update(..)}. */
    public Update(QueryContext context, Table<R> table) {
        super(context, table);
        this.statement = new UpdateStatement(table);
    }

    @Override
    public UpdateStatement dmlStatement() {
        return statement;
    }

    /** {@code SET column = ?}; {@code null} sets {@code NULL}. */
    public <T> Update<R> set(Column<T> column, T value) {
        return set(column, Values.param(value, column.type()));
    }

    /** {@code SET column = expr} */
    public <T> Update<R> set(Column<T> column, Field<T> value) {
        return setField(column, value);
    }

    private <T> Update<R> setField(Column<T> column, Field<T> value) {
        statement.set(column, require(value), true);
        return this;
    }

    /** {@code SET column = ?} only if {@code apply} is {@code true}, e.g. for PATCH requests. */
    public <T> Update<R> setIf(boolean apply, Column<T> column, T value) {
        return apply ? set(column, value) : this;
    }

    /** {@code SET column = expr} only if {@code apply} is {@code true}. */
    public <T> Update<R> setIf(boolean apply, Column<T> column, Field<T> value) {
        return apply ? set(column, value) : this;
    }

    /**
     * {@code SET column = ?} if the value is present. An empty optional leaves
     * the column unchanged; use {@link #setNull(Column)} to clear it.
     */
    public <T> Update<R> setIfPresent(Column<T> column, Optional<? extends T> value) {
        if (value == null) throw new IllegalArgumentException("optional must not be null");
        return value.isPresent() ? set(column, (T) value.get()) : this;
    }

    /** Returns {@code true} if at least one column is set. */
    public boolean hasAssignments() {
        return !statement.assignments().isEmpty();
    }

    /** {@code SET column = NULL} */
    public <T> Update<R> setNull(Column<T> column) {
        return set(column, Values.nullValue(column.type()));
    }

    /** Sets a column from a value whose type is only known at runtime (checked). */
    @SuppressWarnings("unchecked")
    public Update<R> setUnchecked(Column<?> column, Object value) {
        Column<Object> c = (Column<Object>) column;
        return setField(c, Values.param(c.type().cast(value), c.type()));
    }

    /** {@code FROM tables} – joins other tables into the update. */
    public Update<R> from(Table<?>... tables) {
        statement.from().addAll(Arrays.asList(tables));
        return this;
    }

    /** Adds {@code WHERE} conditions, joined with {@code AND}. */
    public Update<R> where(Condition... conditions) {
        for (Condition c : conditions) statement.addWhere(AbstractSelect.requireCondition(c));
        return this;
    }

    /** Adds a list of {@code WHERE} conditions, joined with {@code AND}; {@code null} entries are rejected. */
    public Update<R> where(Collection<? extends Condition> conditions) {
        for (Condition c : Conditions.copy(conditions)) statement.addWhere(c);
        return this;
    }

    /** Adds a condition only if {@code apply} is {@code true}. */
    public Update<R> whereIf(boolean apply, Supplier<Condition> condition) {
        return apply ? where(condition.get()) : this;
    }

    /** The same as {@link #allRows()}. */
    public Update<R> all() {
        return allRows();
    }

    /** Confirms that the update intentionally has no {@code WHERE}. */
    public Update<R> allRows() {
        statement.allRows(true);
        return this;
    }
}
