package ch.lxrin.ql.spi;

import ch.lxrin.ql.dsl.Field;
import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.statement.InsertStatement;

/** The context of an {@code INSERT} before execution. */
public interface InsertContext extends WriteContext {

    /** Returns the statement model; changes affect the executed statement. */
    InsertStatement statement();

    /** Sets a column value on every row that has none yet. */
    <T> void setValue(Column<T> column, T value);

    /** Sets a column to an expression on every row that has none yet. */
    <T> void setValue(Column<T> column, Field<T> value);

    /** Sets a column value on every row, replacing values given by the caller. */
    <T> void forceValue(Column<T> column, T value);
}
