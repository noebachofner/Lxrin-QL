package ch.lxrin.ql.runtime;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Runs blocks of work in transactions. The core implementation manages JDBC
 * connections itself ({@link JdbcTransactions}); {@code lxrin-ql-spring}
 * delegates to Spring's {@code PlatformTransactionManager}.
 */
public interface TransactionRunner {

    /**
     * Runs {@code work} in a transaction. A {@link RuntimeException} or
     * {@link Error} rolls it back and is rethrown.
     */
    <T> T inTransaction(Propagation propagation, Supplier<T> work);

    /** Returns the active transaction of the current thread, if any. */
    Optional<TransactionScope> current();

    /**
     * Returns a runner without real transactions, for contexts without a
     * database (for example with a mock executor). Attributes and callbacks
     * still work.
     */
    static TransactionRunner none() {
        return new NoTransactions();
    }
}
