package ch.lxrin.ql.spi;

import ch.lxrin.ql.dsl.Condition;
import ch.lxrin.ql.statement.DeleteStatement;

/** The context of a {@code DELETE} before execution. */
public interface DeleteContext extends WriteContext {

    /** Returns the statement model; changes affect the executed statement. */
    DeleteStatement statement();

    /** Adds a {@code WHERE} condition. */
    void addCondition(Condition condition);
}
