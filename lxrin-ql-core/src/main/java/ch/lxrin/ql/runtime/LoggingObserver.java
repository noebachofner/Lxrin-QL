package ch.lxrin.ql.runtime;

import ch.lxrin.ql.error.LxrinQlException;
import ch.lxrin.ql.spi.ExecutionObserver;
import ch.lxrin.ql.spi.StatementEvent;

import java.time.Duration;

/**
 * Logs statements through {@link System.Logger} (no dependency; bridges to
 * SLF4J, Log4j or JUL exist): every statement at {@code DEBUG}, slow ones at
 * {@code WARNING}, failures at {@code DEBUG} (they are thrown anyway).
 * Sensitive bind values are always redacted.
 */
public final class LoggingObserver implements ExecutionObserver {

    private static final System.Logger LOG = System.getLogger("ch.lxrin.ql.sql");

    private final Duration slowThreshold;
    private final boolean logBinds;

    /** Logs slow statements (taking at least {@code slowThreshold}) as warnings; binds are logged. */
    public LoggingObserver(Duration slowThreshold) {
        this(slowThreshold, true);
    }

    /**
     * @param slowThreshold statements taking at least this long are logged as warnings
     * @param logBinds      whether bind values are included
     */
    public LoggingObserver(Duration slowThreshold, boolean logBinds) {
        this.slowThreshold = slowThreshold;
        this.logBinds = logBinds;
    }

    @Override
    public void onSuccess(StatementEvent event, Duration took, long rows) {
        boolean slow = slowThreshold != null && took.compareTo(slowThreshold) >= 0;
        System.Logger.Level level = slow ? System.Logger.Level.WARNING : System.Logger.Level.DEBUG;
        if (LOG.isLoggable(level)) {
            LOG.log(level, () -> (slow ? "slow statement " : "") + describe(event) + " – " + took.toMillis() + " ms, " + rows + " rows");
        }
    }

    @Override
    public void onError(StatementEvent event, Duration took, LxrinQlException error) {
        if (LOG.isLoggable(System.Logger.Level.DEBUG)) {
            LOG.log(System.Logger.Level.DEBUG, () -> "failed " + describe(event) + " – " + error.getMessage());
        }
    }

    private String describe(StatementEvent event) {
        String sql = logBinds ? event.sql().toString() : event.sql().sql();
        String batch = event.batchSize() > 1 ? " (batch of " + event.batchSize() + ")" : "";
        return "[" + event.origin() + "] " + sql + batch;
    }
}
