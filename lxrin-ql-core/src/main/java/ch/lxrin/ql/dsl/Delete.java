package ch.lxrin.ql.dsl;

import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.statement.DeleteStatement;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * A {@code DELETE}. For safety a delete without {@code WHERE} is rejected;
 * call {@link #allRows()} to delete every row on purpose.
 *
 * @param <R> the table's row type
 */
public final class Delete<R> extends AbstractDml<R, Delete<R>> {

    private final DeleteStatement statement;

    /** Creates the builder; use {@code Dsl.deleteFrom(..)} or {@code ctx.deleteFrom(..)}. */
    public Delete(QueryContext context, Table<R> table) {
        super(context, table);
        this.statement = new DeleteStatement(table);
    }

    @Override
    public DeleteStatement dmlStatement() {
        return statement;
    }

    /** {@code USING tables} – joins other tables into the delete. */
    public Delete<R> using(Table<?>... tables) {
        statement.using().addAll(Arrays.asList(tables));
        return this;
    }

    /** Adds {@code WHERE} conditions, joined with {@code AND}. */
    public Delete<R> where(Condition... conditions) {
        for (Condition c : conditions) statement.addWhere(AbstractSelect.requireCondition(c));
        return this;
    }

    /** Adds a condition only if {@code apply} is {@code true}. */
    public Delete<R> whereIf(boolean apply, Supplier<Condition> condition) {
        return apply ? where(condition.get()) : this;
    }

    /** Confirms that the delete intentionally has no {@code WHERE}. */
    public Delete<R> allRows() {
        statement.allRows(true);
        return this;
    }
}
