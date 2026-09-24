package ch.lxrin.ql.runtime;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.function.Supplier;

/** A {@link TransactionRunner} that only provides scopes, without database transactions. */
final class NoTransactions implements TransactionRunner {

    private final ThreadLocal<Deque<SimpleScope>> stack = ThreadLocal.withInitial(ArrayDeque::new);

    @Override
    public <T> T inTransaction(Propagation propagation, Supplier<T> work) {
        Deque<SimpleScope> scopes = stack.get();
        if (!scopes.isEmpty() && propagation != Propagation.REQUIRES_NEW) return work.get();
        if (scopes.isEmpty() && propagation == Propagation.MANDATORY) {
            throw new ch.lxrin.ql.error.TransactionException("no active transaction", null);
        }
        SimpleScope scope = new SimpleScope(null);
        scopes.push(scope);
        try {
            T result = work.get();
            scopes.pop();
            if (scope.isRollbackOnly()) {
                scope.rolledBack();
                throw new ch.lxrin.ql.error.TransactionException("transaction was marked rollback-only", null);
            }
            scope.committed();
            return result;
        } catch (RuntimeException | Error e) {
            if (scopes.peek() == scope) scopes.pop();
            scope.rolledBack();
            throw e;
        }
    }

    @Override
    public Optional<TransactionScope> current() {
        return Optional.ofNullable(stack.get().peek());
    }
}
