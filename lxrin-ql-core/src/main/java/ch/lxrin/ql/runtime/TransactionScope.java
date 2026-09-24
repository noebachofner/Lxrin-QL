package ch.lxrin.ql.runtime;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The active transaction as seen by application code and listeners:
 * transaction-scoped attributes and completion callbacks.
 *
 * <pre>{@code
 * Long revision = scope.attribute(REVISION, () -> createRevisionRow());   // once per transaction
 * }</pre>
 */
public interface TransactionScope {

    /**
     * A typed attribute key.
     *
     * @param <T> the attribute type
     */
    final class Key<T> {
        private final String name;

        private Key(String name) {
            this.name = Objects.requireNonNull(name, "name");
        }

        /** Returns the name, for messages. */
        public String name() {
            return name;
        }

        @Override
        public String toString() {
            return "Key[" + name + "]";
        }
    }

    /** Creates a new key; keys are compared by identity. */
    static <T> Key<T> key(String name) {
        return new Key<>(name);
    }

    /** Returns the attribute, computing and storing it on first access in this transaction. */
    <T> T attribute(Key<T> key, Supplier<T> ifAbsent);

    /** Returns the attribute, if set. */
    <T> Optional<T> attribute(Key<T> key);

    /** Runs {@code callback} after a successful commit. */
    void afterCommit(Runnable callback);

    /** Runs {@code callback} after a rollback (also of a nested savepoint that contains this registration). */
    void afterRollback(Runnable callback);

    /** Marks the transaction so that it rolls back instead of committing. */
    void setRollbackOnly();

    /** Returns {@code true} if the transaction will roll back. */
    boolean isRollbackOnly();
}
