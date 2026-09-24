package ch.lxrin.ql.exec;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Holds the default {@link SqlExecutor} used by statements that have no
 * explicit {@code .executor(..)}.
 *
 * <p>Resolution order:</p>
 * <ol>
 *   <li>the executor set with {@link #setDefault(SqlExecutor)}
 *       (also available as {@code LxrinQL.setDefaultExecutor(..)})</li>
 *   <li>the first {@link SqlExecutor} registered through
 *       {@link ServiceLoader} ({@code META-INF/services/ch.lxrin.ql.exec.SqlExecutor}),
 *       which lets an adapter library register itself without configuration</li>
 * </ol>
 */
public final class SqlExecutors {

    private static volatile SqlExecutor defaultExecutor;
    private static volatile SqlExecutor discovered;

    private SqlExecutors() {}

    /** Sets the application-wide default executor; {@code null} resets it. */
    public static void setDefault(SqlExecutor executor) {
        defaultExecutor = executor;
    }

    /**
     * Returns the default executor.
     *
     * @throws IllegalStateException if none is configured or discoverable
     */
    public static SqlExecutor getDefault() {
        SqlExecutor executor = defaultExecutor;
        if (executor != null) return executor;
        executor = discovered;
        if (executor != null) return executor;
        synchronized (SqlExecutors.class) {
            if (discovered == null) {
                Iterator<SqlExecutor> it = ServiceLoader.load(SqlExecutor.class).iterator();
                if (it.hasNext()) discovered = it.next();
            }
            if (discovered == null) {
                throw new IllegalStateException("No SqlExecutor configured. Call LxrinQL.setDefaultExecutor("
                        + "new JdbcSqlExecutor(dataSource)), pass .executor(..) to the statement, "
                        + "or register a SqlExecutor via META-INF/services.");
            }
            return discovered;
        }
    }
}
