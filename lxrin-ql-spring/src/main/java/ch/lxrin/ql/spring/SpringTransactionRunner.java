package ch.lxrin.ql.spring;

import ch.lxrin.ql.error.TransactionException;
import ch.lxrin.ql.runtime.Propagation;
import ch.lxrin.ql.runtime.TransactionRunner;
import ch.lxrin.ql.runtime.TransactionScope;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Runs LxrinQL transactions with Spring's {@link PlatformTransactionManager}.
 * The transaction scope (attributes and completion callbacks) is attached to
 * the Spring transaction as a {@link TransactionSynchronization}, so it is
 * suspended and resumed together with the transaction. Savepoints
 * ({@link Propagation#NESTED}) have their own rollback callbacks.
 */
public final class SpringTransactionRunner implements TransactionRunner {

    private final PlatformTransactionManager transactionManager;

    /** Creates the runner. */
    public SpringTransactionRunner(PlatformTransactionManager transactionManager) {
        this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager");
    }

    @Override
    public <T> T inTransaction(Propagation propagation, Supplier<T> work) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(behavior(propagation));
        boolean savepoint = propagation == Propagation.NESTED && TransactionSynchronizationManager.isActualTransactionActive();
        try {
            return template.execute(status -> {
                if (!savepoint) return work.get();
                Scope root = scope();
                Scope nested = root.pushNested();
                try {
                    T result = work.get();
                    root.popNested(nested, true);
                    return result;
                } catch (RuntimeException | Error e) {
                    root.popNested(nested, false);
                    throw e;
                }
            });
        } catch (org.springframework.transaction.IllegalTransactionStateException e) {
            throw new TransactionException(e.getMessage(), e);
        }
    }

    @Override
    public Optional<TransactionScope> current() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            return Optional.empty();
        }
        Scope root = scope();
        return Optional.of(root.innermost());
    }

    private Scope scope() {
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            if (s instanceof Scope && ((Scope) s).owner == this) return (Scope) s;
        }
        Scope scope = new Scope(this, null);
        TransactionSynchronizationManager.registerSynchronization(scope);
        return scope;
    }

    private static int behavior(Propagation propagation) {
        switch (propagation) {
            case REQUIRES_NEW:
                return TransactionDefinition.PROPAGATION_REQUIRES_NEW;
            case NESTED:
                return TransactionDefinition.PROPAGATION_NESTED;
            case MANDATORY:
                return TransactionDefinition.PROPAGATION_MANDATORY;
            default:
                return TransactionDefinition.PROPAGATION_REQUIRED;
        }
    }

    /** The LxrinQL scope of one Spring transaction (or of a savepoint within it). */
    static final class Scope implements TransactionScope, TransactionSynchronization {
        final SpringTransactionRunner owner;
        private final Scope parent;
        private final Map<Key<?>, Object> attributes;
        private final List<Runnable> onCommit;
        private final List<Runnable> onRollback = new ArrayList<>();
        private final Deque<Scope> nested = new ArrayDeque<>();
        private boolean rollbackOnly;

        Scope(SpringTransactionRunner owner, Scope parent) {
            this.owner = owner;
            this.parent = parent;
            this.attributes = parent == null ? new IdentityHashMap<>() : parent.attributes;
            this.onCommit = parent == null ? new ArrayList<>() : parent.onCommit;
        }

        Scope pushNested() {
            Scope child = new Scope(owner, innermost());
            nested.push(child);
            return child;
        }

        void popNested(Scope child, boolean success) {
            nested.remove(child);
            if (success) {
                child.parent.onRollback.addAll(child.onRollback);
            } else {
                run(reversed(child.onRollback));
            }
        }

        Scope innermost() {
            return nested.isEmpty() ? this : nested.peek();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T attribute(Key<T> key, Supplier<T> ifAbsent) {
            if (attributes.containsKey(key)) return (T) attributes.get(key);
            T value = ifAbsent.get();
            attributes.put(key, value);
            return value;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Optional<T> attribute(Key<T> key) {
            return Optional.ofNullable((T) attributes.get(key));
        }

        @Override
        public void afterCommit(Runnable callback) {
            onCommit.add(callback);
        }

        @Override
        public void afterRollback(Runnable callback) {
            onRollback.add(callback);
        }

        @Override
        public void setRollbackOnly() {
            if (parent != null) {
                parent.setRollbackOnly();
            } else {
                rollbackOnly = true;
            }
        }

        @Override
        public boolean isRollbackOnly() {
            return parent != null ? parent.isRollbackOnly() : rollbackOnly;
        }

        @Override
        public void beforeCommit(boolean readOnly) {
            if (parent == null && rollbackOnly) {
                throw new TransactionException("transaction rolled back because it was marked rollback-only", null);
            }
        }

        @Override
        public void afterCompletion(int status) {
            if (parent != null) return;
            if (status == STATUS_COMMITTED) run(onCommit);
            else run(reversed(onRollback));
        }

        private static List<Runnable> reversed(List<Runnable> list) {
            List<Runnable> copy = new ArrayList<>(list);
            java.util.Collections.reverse(copy);
            return copy;
        }

        private static void run(List<Runnable> callbacks) {
            for (Runnable r : callbacks) {
                try {
                    r.run();
                } catch (RuntimeException ignored) {
                    // completion callbacks must not hide the outcome
                }
            }
        }
    }
}
