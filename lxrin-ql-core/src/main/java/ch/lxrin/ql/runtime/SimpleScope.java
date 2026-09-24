package ch.lxrin.ql.runtime;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A transaction scope with attributes and callbacks. A nested scope (for a
 * savepoint) shares the attributes of its root and keeps its own rollback
 * callbacks until the savepoint is released.
 */
final class SimpleScope implements TransactionScope {

    private final SimpleScope parent;
    private final Map<Key<?>, Object> attributes;
    private final List<Runnable> onCommit;
    private final List<Runnable> onRollback = new ArrayList<>();
    private boolean rollbackOnly;

    SimpleScope(SimpleScope parent) {
        this.parent = parent;
        this.attributes = parent == null ? new IdentityHashMap<>() : parent.attributes;
        this.onCommit = parent == null ? new ArrayList<>() : parent.onCommit;
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
        if (parent != null) parent.setRollbackOnly();
        else rollbackOnly = true;
    }

    @Override
    public boolean isRollbackOnly() {
        return parent != null ? parent.isRollbackOnly() : rollbackOnly;
    }

    /** Called when a savepoint is released: rollback callbacks move to the parent. */
    void released() {
        if (parent != null) parent.onRollback.addAll(onRollback);
        onRollback.clear();
    }

    void committed() {
        run(onCommit);
    }

    void rolledBack() {
        List<Runnable> callbacks = new ArrayList<>(onRollback);
        java.util.Collections.reverse(callbacks);
        run(callbacks);
    }

    private static void run(List<Runnable> callbacks) {
        for (Runnable r : callbacks) {
            try {
                r.run();
            } catch (RuntimeException ignored) {
                // completion callbacks must not hide the outcome of the transaction
            }
        }
    }
}
