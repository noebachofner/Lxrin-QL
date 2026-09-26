package ch.lxrin.ql.spi;

import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.statement.StatementKind;

import java.util.List;

/**
 * What a listener or policy sees before a write statement runs. The
 * sub-interfaces give access to the statement model and allow changes.
 */
public interface WriteContext {

    /** Returns the target table. */
    Table<?> table();

    /** Returns the statement kind. */
    StatementKind kind();

    /** Returns where the statement comes from. */
    Origin origin();

    /** Returns the entities written by a repository call, or an empty list for DSL statements. */
    List<EntityWrite> entityWrites();

    /**
     * Requests columns in the affected rows given to after-listeners, regardless
     * of the {@code RETURNING} list of the caller.
     */
    void requestReturning(List<? extends Column<?>> columns);

    /** Requests columns in the affected rows given to after-listeners. */
    default void requestReturning(Column<?>... columns) {
        requestReturning(List.of(columns));
    }

    /** Rejects the statement with a {@code StatementRejectedException}. */
    void reject(String reason);

    /** Returns the query context the statement runs in. */
    QueryContext queryContext();
}
