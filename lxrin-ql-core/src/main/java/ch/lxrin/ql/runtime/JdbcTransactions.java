package ch.lxrin.ql.runtime;

import ch.lxrin.ql.error.SqlErrors;
import ch.lxrin.ql.error.TransactionException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Plain JDBC transactions for a {@link DataSource}: the connection of the
 * active transaction is bound to the current thread and used by every
 * statement of the same query context. Nested blocks use savepoints
 * ({@link Propagation#NESTED}) or a second connection
 * ({@link Propagation#REQUIRES_NEW}).
 *
 * <p>If a block that joined an outer transaction fails, the outer
 * transaction is marked rollback-only, so a caught exception cannot lead to
 * a partial commit.</p>
 */
public final class JdbcTransactions implements ConnectionProvider, TransactionRunner {

    private final DataSource dataSource;
    private final ThreadLocal<Deque<Tx>> stack = ThreadLocal.withInitial(ArrayDeque::new);

    private static final class Tx {
        final Connection connection;
        final SimpleScope scope;
        final boolean ownsConnection;

        Tx(Connection connection, SimpleScope scope, boolean ownsConnection) {
            this.connection = connection;
            this.scope = scope;
            this.ownsConnection = ownsConnection;
        }
    }

    /** Creates transactions for the given data source. */
    public JdbcTransactions(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Connection acquire() throws SQLException {
        Tx tx = stack.get().peek();
        return tx != null ? tx.connection : dataSource.getConnection();
    }

    @Override
    public void release(Connection connection) throws SQLException {
        for (Tx tx : stack.get()) {
            if (tx.connection == connection) return;
        }
        connection.close();
    }

    @Override
    public Optional<TransactionScope> current() {
        Tx tx = stack.get().peek();
        return tx == null ? Optional.empty() : Optional.of(tx.scope);
    }

    @Override
    public <T> T inTransaction(Propagation propagation, Supplier<T> work) {
        Objects.requireNonNull(work, "work");
        Tx current = stack.get().peek();
        switch (propagation) {
            case MANDATORY:
                if (current == null) throw new TransactionException("no active transaction for Propagation.MANDATORY", null);
                return participate(current, work);
            case REQUIRED:
                return current != null ? participate(current, work) : begin(work);
            case NESTED:
                return current != null ? savepoint(current, work) : begin(work);
            case REQUIRES_NEW:
                return begin(work);
            default:
                throw new IllegalArgumentException(String.valueOf(propagation));
        }
    }

    private <T> T participate(Tx tx, Supplier<T> work) {
        try {
            return work.get();
        } catch (RuntimeException | Error e) {
            tx.scope.setRollbackOnly();
            throw e;
        }
    }

    private <T> T begin(Supplier<T> work) {
        Connection con;
        boolean autoCommit;
        try {
            con = dataSource.getConnection();
            autoCommit = con.getAutoCommit();
            if (autoCommit) con.setAutoCommit(false);
        } catch (SQLException e) {
            throw new TransactionException("cannot begin transaction: " + e.getMessage(), SqlErrors.translate(e, null));
        }
        Tx tx = new Tx(con, new SimpleScope(null), true);
        stack.get().push(tx);
        boolean popped = false;
        try {
            T result;
            try {
                result = work.get();
            } catch (RuntimeException | Error e) {
                stack.get().pop();
                popped = true;
                rollback(tx);
                throw e;
            }
            stack.get().pop();
            popped = true;
            if (tx.scope.isRollbackOnly()) {
                rollback(tx);
                throw new TransactionException("transaction rolled back because it was marked rollback-only", null);
            }
            try {
                con.commit();
            } catch (SQLException e) {
                rollback(tx);
                throw new TransactionException("commit failed: " + e.getMessage(), SqlErrors.translate(e, null));
            }
            tx.scope.committed();
            return result;
        } finally {
            if (!popped) stack.get().pop();
            try {
                if (autoCommit) con.setAutoCommit(true);
            } catch (SQLException ignored) {
                // the connection is closed next anyway
            }
            try {
                con.close();
            } catch (SQLException ignored) {
                // closing must not hide the outcome
            }
        }
    }

    private <T> T savepoint(Tx outer, Supplier<T> work) {
        Savepoint savepoint;
        try {
            savepoint = outer.connection.setSavepoint();
        } catch (SQLException e) {
            throw new TransactionException("cannot create savepoint: " + e.getMessage(), SqlErrors.translate(e, null));
        }
        Tx nested = new Tx(outer.connection, new SimpleScope(outer.scope), false);
        stack.get().push(nested);
        try {
            T result = work.get();
            stack.get().pop();
            outer.connection.releaseSavepoint(savepoint);
            nested.scope.released();
            return result;
        } catch (RuntimeException | Error e) {
            if (stack.get().peek() == nested) stack.get().pop();
            try {
                outer.connection.rollback(savepoint);
            } catch (SQLException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
                outer.scope.setRollbackOnly();
            }
            nested.scope.rolledBack();
            throw e;
        } catch (SQLException e) {
            throw new TransactionException("cannot release savepoint: " + e.getMessage(), SqlErrors.translate(e, null));
        }
    }

    private static void rollback(Tx tx) {
        try {
            tx.connection.rollback();
        } catch (SQLException ignored) {
            // the original failure is more important
        }
        tx.scope.rolledBack();
    }
}
