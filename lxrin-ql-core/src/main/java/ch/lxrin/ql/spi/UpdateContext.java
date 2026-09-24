package ch.lxrin.ql.spi;

import ch.lxrin.ql.dsl.Condition;
import ch.lxrin.ql.dsl.Field;
import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.statement.StatementKind;
import ch.lxrin.ql.statement.UpdateStatement;

/** The context of an {@code UPDATE} before execution. */
public interface UpdateContext extends WriteContext {

    /** Returns the statement model; changes affect the executed statement. */
    UpdateStatement statement();

    /** Returns {@link StatementKind#DELETE} if a soft-delete policy turned a delete into this update. */
    default StatementKind originalKind() {
        return statement().originalKind();
    }

    /** Adds an assignment unless the caller already assigns the column. */
    <T> void set(Column<T> column, T value);

    /** Adds an assignment to an expression unless the caller already assigns the column. */
    <T> void set(Column<T> column, Field<T> value);

    /** Adds a {@code WHERE} condition. */
    void addCondition(Condition condition);
}
