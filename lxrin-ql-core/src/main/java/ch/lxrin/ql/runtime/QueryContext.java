package ch.lxrin.ql.runtime;

import ch.lxrin.ql.dsl.Delete;
import ch.lxrin.ql.dsl.Field;
import ch.lxrin.ql.dsl.Fields;
import ch.lxrin.ql.dsl.Insert;
import ch.lxrin.ql.dsl.Row;
import ch.lxrin.ql.dsl.Select;
import ch.lxrin.ql.dsl.Select1;
import ch.lxrin.ql.dsl.Sql;
import ch.lxrin.ql.dsl.Truncate;
import ch.lxrin.ql.dsl.Update;
import ch.lxrin.ql.dsl.Values;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.spi.ColumnConvention;
import ch.lxrin.ql.spi.EntityWrite;
import ch.lxrin.ql.spi.ExecutionObserver;
import ch.lxrin.ql.spi.Origin;
import ch.lxrin.ql.spi.StatementListener;
import ch.lxrin.ql.spi.TablePolicy;
import ch.lxrin.ql.statement.DmlStatement;
import ch.lxrin.ql.statement.OtherStatement;
import ch.lxrin.ql.statement.SelectStatement;
import ch.lxrin.ql.statement.TruncateStatement;
import ch.lxrin.ql.statement.UpdateStatement;
import ch.lxrin.ql.types.JsonCodec;
import ch.lxrin.ql.types.SqlTypes;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Everything a statement needs to run: the executor, connections,
 * transactions, JSON codec, statement listeners, column conventions, table
 * policies and observers.
 *
 * <p>A context is immutable and thread-safe. Create one per application or
 * data source, usually as a bean. It is also a DSL entry point:
 * {@code ctx.select(..)}, {@code ctx.insertInto(..)} and so on create
 * statements attached to it.</p>
 *
 * <pre>{@code
 * QueryContext ctx = QueryContext.builder()
 *         .dataSource(dataSource)
 *         .convention(ColumnConventions.createdAt("created_at", clock))
 *         .policy(new SoftDeletePolicy("deleted_at", clock))
 *         .listener(new AuditListener())
 *         .observer(new LoggingObserver(Duration.ofMillis(500)))
 *         .build();
 * }</pre>
 *
 * <p>The static {@code Dsl.*} entry points and {@code BEANS} use the default
 * context, set with {@link #setDefault(QueryContext)}.</p>
 */
public final class QueryContext extends ArityContextBase {

    private static volatile QueryContext defaultContext;

    final SqlExecutor executor;
    final ConnectionProvider connections;
    final TransactionRunner transactions;
    final JsonCodec jsonCodec;
    final List<StatementListener> listeners;
    final List<ColumnConvention<?>> conventions;
    final List<TablePolicy> policies;
    final List<ExecutionObserver> observers;
    final String versionColumn;
    final int fetchSize;
    final int batchSize;
    final Set<Class<? extends TablePolicy>> bypassed;
    final Origin origin;
    private final Pipeline pipeline;

    private QueryContext(Builder b, Set<Class<? extends TablePolicy>> bypassed, Origin origin) {
        this.executor = b.executor != null ? b.executor : new JdbcExecutor();
        this.connections = b.connections;
        this.transactions = b.transactions != null ? b.transactions : TransactionRunner.none();
        this.jsonCodec = b.jsonCodec;
        this.listeners = List.copyOf(b.listeners);
        this.conventions = List.copyOf(b.conventions);
        this.policies = List.copyOf(b.policies);
        this.observers = List.copyOf(b.observers);
        this.versionColumn = b.versionColumn;
        this.fetchSize = b.fetchSize;
        this.batchSize = b.batchSize;
        this.bypassed = Set.copyOf(bypassed);
        this.origin = origin;
        this.pipeline = new Pipeline(this);
    }

    @Override
    QueryContext self() {
        return this;
    }

    // =========================================================================
    // Construction and default context
    // =========================================================================

    /** Starts a new builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Returns a builder initialised with this context's configuration. */
    public Builder toBuilder() {
        Builder b = new Builder();
        b.executor = executor;
        b.connections = connections;
        b.transactions = transactions;
        b.jsonCodec = jsonCodec;
        b.listeners.addAll(listeners);
        b.conventions.addAll(conventions);
        b.policies.addAll(policies);
        b.observers.addAll(observers);
        b.versionColumn = versionColumn;
        b.fetchSize = fetchSize;
        b.batchSize = batchSize;
        return b;
    }

    /** Returns a variant of this context, e.g. {@code ctx.derive(b -> b.listener(extra))}. */
    public QueryContext derive(Consumer<Builder> changes) {
        Builder b = toBuilder();
        changes.accept(b);
        return new QueryContext(b, bypassed, origin);
    }

    /**
     * Returns a context in which the given table policies do not apply. This
     * is the explicit, searchable way to see soft-deleted rows or rows of
     * other tenants.
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public final QueryContext bypassing(Class<? extends TablePolicy>... policyTypes) {
        Set<Class<? extends TablePolicy>> set = new HashSet<>(bypassed);
        set.addAll(Arrays.asList(policyTypes));
        return new QueryContext(toBuilder(), set, origin);
    }

    /** Returns a context whose statements report the given origin to listeners and observers. */
    public QueryContext withOrigin(Origin newOrigin) {
        return new QueryContext(toBuilder(), bypassed, Objects.requireNonNull(newOrigin, "origin"));
    }

    /** Sets the context used by the static {@code Dsl.*} entry points and {@code BEANS}; {@code null} clears it. */
    public static void setDefault(QueryContext context) {
        defaultContext = context;
    }

    /**
     * Returns the default context.
     *
     * @throws IllegalStateException if none is set
     */
    public static QueryContext getDefault() {
        QueryContext ctx = defaultContext;
        if (ctx == null) {
            throw new IllegalStateException("No default QueryContext. Call QueryContext.setDefault(QueryContext.builder()"
                    + ".dataSource(dataSource).build()), create statements with ctx.select(..), or use lxrin-ql-spring.");
        }
        return ctx;
    }

    /** Returns the default context, if set. */
    public static Optional<QueryContext> findDefault() {
        return Optional.ofNullable(defaultContext);
    }

    // =========================================================================
    // DSL entry points
    // =========================================================================

    /** {@code SELECT <all columns> FROM table}; rows are the table's row records. */
    public <R> Select<R> selectFrom(Table<R> table) {
        return newSelectFrom(this, table);
    }

    /** {@code SELECT} of a dynamic list of fields; rows are {@link Row}s. */
    public Select<Row> select(List<? extends Field<?>> fields) {
        return newDynamicSelect(this, fields);
    }

    /** {@code SELECT count(*)}; add {@code .from(..)}. */
    public Select1<Long> selectCount() {
        return new Select1<>(this, Fields.number(SqlTypes.INT8, ctx -> ctx.append("count(*)")));
    }

    /** {@code SELECT 1}, e.g. for {@code EXISTS} sub-queries. */
    public Select1<Integer> selectOne() {
        return new Select1<>(this, Values.inline(1));
    }

    /** Starts an {@code INSERT INTO table}. */
    public <R> Insert<R> insertInto(Table<R> table) {
        return new Insert<>(this, table);
    }

    /** Starts an {@code UPDATE table}. */
    public <R> Update<R> update(Table<R> table) {
        return new Update<>(this, table);
    }

    /** Starts a {@code DELETE FROM table}. */
    public <R> Delete<R> deleteFrom(Table<R> table) {
        return new Delete<>(this, table);
    }

    /** Starts a {@code TRUNCATE}. */
    public Truncate truncate(Table<?>... tables) {
        return new Truncate(this, Arrays.asList(tables));
    }

    /** Runs a statement written with {@code Sql.statement(..)} and returns the number of affected rows. */
    public long execute(Sql.RawStatement statement) {
        return pipeline.executeOther(new OtherStatement(statement));
    }

    /** Creates {@code selectFrom} builders; used by the static DSL as well. */
    public static <R> Select<R> newSelectFrom(QueryContext context, Table<R> table) {
        List<Field<?>> columns = new ArrayList<>(table.columns());
        if (columns.isEmpty()) throw new IllegalArgumentException("table " + table + " has no declared columns");
        Row.Shape shape = new Row.Shape(columns);
        return new Select<R>(context, columns, v -> table.mapRow(shape.row(v))).from(table);
    }

    /** Creates dynamic select builders; used by the static DSL as well. */
    public static Select<Row> newDynamicSelect(QueryContext context, List<? extends Field<?>> fields) {
        Row.Shape shape = new Row.Shape(fields);
        return new Select<>(context, fields, shape::row);
    }

    // =========================================================================
    // Transactions
    // =========================================================================

    /** Runs {@code work} in a transaction ({@link Propagation#REQUIRED}). */
    public void transaction(Runnable work) {
        transactions.inTransaction(Propagation.REQUIRED, () -> {
            work.run();
            return null;
        });
    }

    /** Runs {@code work} in a transaction ({@link Propagation#REQUIRED}) and returns its result. */
    public <T> T transaction(Supplier<T> work) {
        return transactions.inTransaction(Propagation.REQUIRED, work);
    }

    /** Runs {@code work} in a transaction with the given propagation. */
    public <T> T transaction(Propagation propagation, Supplier<T> work) {
        return transactions.inTransaction(propagation, work);
    }

    /** Returns the active transaction, if any. */
    public Optional<TransactionScope> currentTransaction() {
        return transactions.current();
    }

    // =========================================================================
    // Execution (used by the DSL builders and repositories)
    // =========================================================================

    /**
     * Options for statements run by builders and repositories.
     *
     * @param origin       where the statement comes from
     * @param entityWrites entities written by a repository call
     */
    public record ExecOptions(Origin origin, List<EntityWrite> entityWrites) {

        /** Creates options; the entity list is copied. */
        public ExecOptions {
            entityWrites = List.copyOf(entityWrites);
        }

        private static final ExecOptions DSL = new ExecOptions(null, List.of());

        /** Options for DSL statements (the context's origin applies). */
        public static ExecOptions dsl() {
            return DSL;
        }
    }

    /**
     * The result of a data-modifying statement.
     *
     * @param count the number of affected rows
     * @param rows  the caller's {@code RETURNING} rows, mapped
     * @param <X>   the row type
     */
    public record DmlResult<X>(long count, List<X> rows) {
    }

    /** Runs a query. Used by the select builders. */
    public <R> List<R> fetch(SelectStatement statement, List<Field<?>> fields, Function<Object[], R> mapper) {
        return pipeline.fetch(statement, fields, mapper);
    }

    /** Runs a query and streams the rows; the stream must be closed. Used by the select builders. */
    public <R> Stream<R> stream(SelectStatement statement, List<Field<?>> fields, Function<Object[], R> mapper) {
        return pipeline.stream(statement, fields, mapper);
    }

    /** Runs an {@code INSERT}, {@code UPDATE} or {@code DELETE} through policies, conventions and listeners. */
    public <X> DmlResult<X> executeDml(DmlStatement statement, Function<Object[], X> mapper, ExecOptions options) {
        return pipeline.executeDml(statement, mapper, options);
    }

    /**
     * Runs updates of single rows as JDBC batches (statements that render to
     * the same SQL are batched together). Each statement's first
     * {@code RETURNING} fields must be columns of its table. Used by
     * {@code saveAll}.
     *
     * @param keyOf extracts the key of a returned row, to attribute rows to statements
     * @param keys  the key of the row each statement updates
     */
    public <X> List<DmlResult<X>> executeBatch(List<UpdateStatement> statements, List<Object> keys,
                                               Function<Object[], Object> keyOf, Function<Object[], X> mapper,
                                               ExecOptions options) {
        return pipeline.executeBatch(statements, keys, keyOf, mapper, options);
    }

    /** Runs a {@code TRUNCATE} through policies and listeners. */
    public void truncate(TruncateStatement statement, ExecOptions options) {
        pipeline.truncate(statement, options);
    }

    // =========================================================================
    // Configuration accessors
    // =========================================================================

    /** Returns the statement listeners. */
    public List<StatementListener> listeners() {
        return listeners;
    }

    /** Returns the column conventions. */
    public List<ColumnConvention<?>> conventions() {
        return conventions;
    }

    /** Returns the table policies. */
    public List<TablePolicy> policies() {
        return policies;
    }

    /** Returns the observers. */
    public List<ExecutionObserver> observers() {
        return observers;
    }

    /** Returns the name of the optimistic-locking version column, if configured. */
    public Optional<String> versionColumn() {
        return Optional.ofNullable(versionColumn);
    }

    /** Returns the policies bypassed in this context. */
    public Set<Class<? extends TablePolicy>> bypassedPolicies() {
        return bypassed;
    }

    /** Returns the origin reported for statements of this context. */
    public Origin origin() {
        return origin;
    }

    /** Returns the maximum number of rows per multi-row insert and per JDBC batch. */
    public int batchSize() {
        return batchSize;
    }

    /** Returns the transaction runner. */
    public TransactionRunner transactions() {
        return transactions;
    }

    /** Returns {@code true} if the policy applies to {@code table} in this context (not bypassed). */
    public boolean policyActive(TablePolicy policy, Table<?> table) {
        return table.supportsPolicies() && !isBypassed(policy) && policy.appliesTo(table);
    }

    private boolean isBypassed(TablePolicy policy) {
        for (Class<? extends TablePolicy> c : bypassed) if (c.isInstance(policy)) return true;
        return false;
    }

    // =========================================================================
    // Builder
    // =========================================================================

    /** Builds a {@link QueryContext}. */
    public static final class Builder {
        private SqlExecutor executor;
        private ConnectionProvider connections;
        private TransactionRunner transactions;
        private JsonCodec jsonCodec;
        private final List<StatementListener> listeners = new ArrayList<>();
        private final List<ColumnConvention<?>> conventions = new ArrayList<>();
        private final List<TablePolicy> policies = new ArrayList<>();
        private final List<ExecutionObserver> observers = new ArrayList<>();
        private String versionColumn;
        private int fetchSize = 500;
        private int batchSize = 1000;

        private Builder() {}

        /** Uses a data source with LxrinQL's own JDBC transactions. */
        public Builder dataSource(DataSource dataSource) {
            JdbcTransactions tx = new JdbcTransactions(dataSource);
            this.connections = tx;
            this.transactions = tx;
            return this;
        }

        /** Uses a custom connection provider (e.g. one bound to a framework's transactions). */
        public Builder connectionProvider(ConnectionProvider provider) {
            this.connections = Objects.requireNonNull(provider, "provider");
            return this;
        }

        /** Uses a custom transaction runner. */
        public Builder transactions(TransactionRunner runner) {
            this.transactions = Objects.requireNonNull(runner, "runner");
            return this;
        }

        /** Uses a custom executor, e.g. a mock in unit tests. */
        public Builder executor(SqlExecutor sqlExecutor) {
            this.executor = Objects.requireNonNull(sqlExecutor, "executor");
            return this;
        }

        /** Sets the codec for JSON-mapped types. */
        public Builder jsonCodec(JsonCodec codec) {
            this.jsonCodec = codec;
            return this;
        }

        /** Adds a statement listener. */
        public Builder listener(StatementListener listener) {
            listeners.add(Objects.requireNonNull(listener, "listener"));
            return this;
        }

        /** Adds statement listeners. */
        public Builder listeners(Collection<? extends StatementListener> more) {
            more.forEach(this::listener);
            return this;
        }

        /** Adds a column convention. */
        public Builder convention(ColumnConvention<?> convention) {
            conventions.add(Objects.requireNonNull(convention, "convention"));
            return this;
        }

        /** Adds column conventions. */
        public Builder conventions(Collection<? extends ColumnConvention<?>> more) {
            more.forEach(this::convention);
            return this;
        }

        /** Adds a table policy. */
        public Builder policy(TablePolicy policy) {
            policies.add(Objects.requireNonNull(policy, "policy"));
            return this;
        }

        /** Adds table policies. */
        public Builder policies(Collection<? extends TablePolicy> more) {
            more.forEach(this::policy);
            return this;
        }

        /** Adds an execution observer. */
        public Builder observer(ExecutionObserver observer) {
            observers.add(Objects.requireNonNull(observer, "observer"));
            return this;
        }

        /** Adds execution observers. */
        public Builder observers(Collection<? extends ExecutionObserver> more) {
            more.forEach(this::observer);
            return this;
        }

        /**
         * Enables optimistic locking through a numeric column with this name:
         * entity updates check and increment it, bulk updates increment it.
         */
        public Builder versionColumn(String column) {
            this.versionColumn = column;
            return this;
        }

        /** Sets the JDBC fetch size for streamed queries (default 500). */
        public Builder fetchSize(int size) {
            this.fetchSize = size;
            return this;
        }

        /** Sets the maximum rows per multi-row insert and per JDBC batch (default 1000). */
        public Builder batchSize(int size) {
            if (size <= 0) throw new IllegalArgumentException("batch size must be positive");
            this.batchSize = size;
            return this;
        }

        /** Builds the context. */
        public QueryContext build() {
            if (executor == null && connections == null) {
                throw new IllegalStateException("configure dataSource(..), connectionProvider(..) or executor(..)");
            }
            if (connections != null && transactions == null) {
                throw new IllegalStateException("a custom connectionProvider(..) needs transactions(..) as well");
            }
            return new QueryContext(this, Set.of(), Origin.DSL);
        }
    }
}
