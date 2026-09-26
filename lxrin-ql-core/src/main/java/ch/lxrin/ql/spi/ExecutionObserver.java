package ch.lxrin.ql.spi;

import ch.lxrin.ql.error.LxrinQlException;

import java.time.Duration;

/**
 * Observes every executed statement: for logging, metrics and tracing.
 * Observers must not throw; exceptions are ignored.
 */
public interface ExecutionObserver {

    /** Called before the statement is sent to the database. */
    default void onStart(StatementEvent event) {}

    /**
     * Called after successful execution.
     *
     * @param rows the number of returned rows for queries, or of affected rows otherwise
     */
    default void onSuccess(StatementEvent event, Duration took, long rows) {}

    /** Called after a failure. */
    default void onError(StatementEvent event, Duration took, LxrinQlException error) {}
}
