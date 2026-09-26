package ch.lxrin.ql.test;

import ch.lxrin.ql.runtime.ConnectionProvider;
import ch.lxrin.ql.runtime.Propagation;
import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.runtime.TransactionRunner;
import ch.lxrin.ql.runtime.TransactionScope;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** The JUnit extension behind {@link LxrinPostgresTest}. */
public final class PostgresExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final Map<String, PGSimpleDataSource> DATABASES = new ConcurrentHashMap<>();
    private static final ExtensionContext.Namespace NS = ExtensionContext.Namespace.create(PostgresExtension.class);

    /** Returns the shared data source for an image and migration set, starting and migrating it on first use. */
    static synchronized DataSource database(String image, String[] migrations) {
        String key = image + "|" + String.join(",", migrations);
        return DATABASES.computeIfAbsent(key, k -> {
            PostgreSQLContainer container = new PostgreSQLContainer(image);
            container.start();
            PGSimpleDataSource ds = new PGSimpleDataSource();
            ds.setUrl(container.getJdbcUrl());
            ds.setUser(container.getUsername());
            ds.setPassword(container.getPassword());
            if (migrations.length > 0) Flyway.configure().dataSource(ds).locations(migrations).load().migrate();
            return ds;
        });
    }

    private static LxrinPostgresTest config(ExtensionContext context) {
        Class<?> c = context.getRequiredTestClass();
        while (c != null) {
            LxrinPostgresTest a = c.getAnnotation(LxrinPostgresTest.class);
            if (a != null) return a;
            c = c.getEnclosingClass();
        }
        throw new IllegalStateException("@LxrinPostgresTest is missing");
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        LxrinPostgresTest config = config(context);
        DataSource ds = database(config.image(), config.migrations());
        QueryContext ctx;
        if (config.rollback()) {
            Connection con = ds.getConnection();
            con.setAutoCommit(false);
            SingleConnection single = new SingleConnection(con);
            context.getStore(NS).put("connection", con);
            ctx = QueryContext.builder().connectionProvider(single).transactions(single).build();
        } else {
            ctx = QueryContext.builder().dataSource(ds).build();
        }
        context.getStore(NS).put("context", ctx);
        context.getStore(NS).put("dataSource", ds);
        if (config.setDefault()) QueryContext.setDefault(ctx);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        if (config(context).setDefault()) QueryContext.setDefault(null);
        Connection con = context.getStore(NS).remove("connection", Connection.class);
        if (con != null) {
            try {
                con.rollback();
            } finally {
                con.close();
            }
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext extension) {
        Class<?> type = parameter.getParameter().getType();
        return type == QueryContext.class || type == DataSource.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext extension) {
        Class<?> type = parameter.getParameter().getType();
        return extension.getStore(NS).get(type == QueryContext.class ? "context" : "dataSource");
    }

    /** One connection for the whole test; transactions are savepoints on it. */
    static final class SingleConnection implements ConnectionProvider, TransactionRunner {
        private final Connection connection;
        private final Deque<Scope> scopes = new ArrayDeque<>();

        SingleConnection(Connection connection) {
            this.connection = connection;
        }

        @Override
        public Connection acquire() {
            return connection;
        }

        @Override
        public void release(Connection c) {
            // the connection lives until the end of the test
        }

        @Override
        public <T> T inTransaction(Propagation propagation, Supplier<T> work) {
            if (propagation == Propagation.REQUIRED && !scopes.isEmpty()) return work.get();
            Savepoint savepoint;
            try {
                savepoint = connection.setSavepoint();
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
            Scope scope = new Scope(scopes.peek());
            scopes.push(scope);
            try {
                T result = work.get();
                scopes.pop();
                connection.releaseSavepoint(savepoint);
                scope.done(true);
                return result;
            } catch (RuntimeException | Error e) {
                scopes.remove(scope);
                try {
                    connection.rollback(savepoint);
                } catch (SQLException rollback) {
                    e.addSuppressed(rollback);
                }
                scope.done(false);
                throw e;
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public Optional<TransactionScope> current() {
            return Optional.ofNullable(scopes.peek());
        }
    }

    /** A savepoint scope; the outermost one runs its commit callbacks when it succeeds. */
    static final class Scope implements TransactionScope {
        private final Scope parent;
        private final Map<Key<?>, Object> attributes;
        private final List<Runnable> commit = new ArrayList<>();
        private final List<Runnable> rollback = new ArrayList<>();
        private boolean rollbackOnly;

        Scope(Scope parent) {
            this.parent = parent;
            this.attributes = parent == null ? new IdentityHashMap<>() : parent.attributes;
        }

        void done(boolean success) {
            if (success && !rollbackOnly) {
                if (parent == null) commit.forEach(Runnable::run);
                else {
                    parent.commit.addAll(commit);
                    parent.rollback.addAll(rollback);
                }
            } else {
                List<Runnable> r = new ArrayList<>(rollback);
                java.util.Collections.reverse(r);
                r.forEach(Runnable::run);
            }
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
            commit.add(callback);
        }

        @Override
        public void afterRollback(Runnable callback) {
            rollback.add(callback);
        }

        @Override
        public void setRollbackOnly() {
            rollbackOnly = true;
        }

        @Override
        public boolean isRollbackOnly() {
            return rollbackOnly;
        }
    }
}
