package ch.lxrin.ql.runtime;

import ch.lxrin.ql.dsl.Condition;
import ch.lxrin.ql.dsl.Field;
import ch.lxrin.ql.dsl.Fields;
import ch.lxrin.ql.error.InvalidStatementException;
import ch.lxrin.ql.error.LxrinQlException;
import ch.lxrin.ql.error.SqlErrors;
import ch.lxrin.ql.render.Bind;
import ch.lxrin.ql.render.RenderContext;
import ch.lxrin.ql.render.RenderedSql;
import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.spi.AffectedRow;
import ch.lxrin.ql.spi.ColumnConvention;
import ch.lxrin.ql.spi.ExecutionObserver;
import ch.lxrin.ql.spi.Origin;
import ch.lxrin.ql.spi.PolicyContext;
import ch.lxrin.ql.spi.StatementEvent;
import ch.lxrin.ql.spi.StatementListener;
import ch.lxrin.ql.spi.TablePolicy;
import ch.lxrin.ql.statement.DeleteStatement;
import ch.lxrin.ql.statement.DmlStatement;
import ch.lxrin.ql.statement.InsertStatement;
import ch.lxrin.ql.statement.OtherStatement;
import ch.lxrin.ql.statement.SelectStatement;
import ch.lxrin.ql.statement.Statement;
import ch.lxrin.ql.statement.TruncateStatement;
import ch.lxrin.ql.statement.UpdateStatement;
import ch.lxrin.ql.types.DataType;
import ch.lxrin.ql.types.Kind;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Runs statements: table policies, column conventions and listeners, then
 * rendering, observers and execution. Every statement of a query context
 * goes through here, from the DSL and from repositories alike.
 */
final class Pipeline {

    private static final String INSERTED_FLAG = "lxrin_inserted";

    private final QueryContext ctx;
    private final PolicyContext policyContext;

    Pipeline(QueryContext ctx) {
        this.ctx = ctx;
        this.policyContext = () -> ctx;
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    RenderedSql render(Statement statement) {
        RenderContext rc = new RenderContext(this::filterFor);
        try {
            statement.render(rc);
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new InvalidStatementException(e.getMessage(), e);
        }
        return rc.result();
    }

    private List<Condition> filterFor(Table<?> table) {
        List<Condition> filters = new ArrayList<>();
        for (TablePolicy p : ctx.policies) {
            if (ctx.policyActive(p, table)) filters.add(p.filter(table, policyContext));
        }
        return filters;
    }

    // =========================================================================
    // Queries
    // =========================================================================

    <R> List<R> fetch(SelectStatement statement, List<Field<?>> fields, Function<Object[], R> mapper) {
        RenderedSql sql = render(statement);
        List<DataType<?>> types = types(fields);
        List<Object[]> rows = run(event(statement, sql, null, 1), vc -> {
            List<Object[]> result = new ArrayList<>();
            try (ResultCursor cursor = ctx.executor.query(vc, sql, types, 0)) {
                for (Object[] row = cursor.next(); row != null; row = cursor.next()) result.add(row);
            }
            return result;
        }, List::size);
        List<R> mapped = new ArrayList<>(rows.size());
        for (Object[] row : rows) mapped.add(mapper.apply(row));
        return mapped;
    }

    <R> Stream<R> stream(SelectStatement statement, List<Field<?>> fields, Function<Object[], R> mapper) {
        RenderedSql sql = render(statement);
        List<DataType<?>> types = types(fields);
        StatementEvent event = event(statement, sql, null, 1);
        long start = System.nanoTime();
        notifyStart(event);
        Connection con = null;
        ResultCursor cursor;
        try {
            con = acquire();
            cursor = ctx.executor.query(new ExecValueContext(con, ctx.jsonCodec), sql, types, ctx.fetchSize);
        } catch (SQLException e) {
            release(con);
            throw failed(event, start, SqlErrors.translate(e, sql));
        } catch (RuntimeException e) {
            release(con);
            throw failed(event, start, wrap(e));
        }
        Connection connection = con;
        long[] count = {0};
        Iterator<R> iterator = new Iterator<>() {
            private Object[] next;
            private boolean done;

            @Override
            public boolean hasNext() {
                if (next == null && !done) {
                    try {
                        next = cursor.next();
                    } catch (SQLException e) {
                        throw SqlErrors.translate(e, sql);
                    }
                    done = next == null;
                }
                return next != null;
            }

            @Override
            public R next() {
                if (!hasNext()) throw new NoSuchElementException();
                Object[] row = next;
                next = null;
                count[0]++;
                return mapper.apply(row);
            }
        };
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false)
                .onClose(() -> {
                    try {
                        cursor.close();
                    } catch (SQLException ignored) {
                        // nothing to do
                    } finally {
                        release(connection);
                        notifySuccess(event, start, count[0]);
                    }
                });
    }

    // =========================================================================
    // INSERT / UPDATE / DELETE
    // =========================================================================

    <X> QueryContext.DmlResult<X> executeDml(DmlStatement original, Function<Object[], X> mapper, QueryContext.ExecOptions options) {
        DmlStatement statement = applyDeletePolicies((DmlStatement) original.copy());
        List<StatementListener> listeners = listenersFor(statement.table());
        Origin origin = origin(options);
        Supplier<QueryContext.DmlResult<X>> work = () -> {
            WriteContexts.Base write = prepare(statement, origin, options, listeners);
            int callerCount = statement.returning().size();
            addExtraReturning(statement, write, listeners);
            RenderedSql sql = render(statement);
            List<DataType<?>> types = types(statement.allReturning());
            UpdateResult result = run(event(statement, sql, origin, 1), vc -> ctx.executor.update(vc, sql, types), UpdateResult::count);
            List<X> rows = new ArrayList<>();
            if (callerCount > 0) {
                for (Object[] v : result.rows()) rows.add(mapper.apply(Arrays.copyOf(v, callerCount)));
            }
            afterListeners(write, result.count(), result.rows(), callerCount, listeners);
            return new QueryContext.DmlResult<>(result.count(), rows);
        };
        return listeners.isEmpty() ? work.get() : ctx.transactions.inTransaction(Propagation.REQUIRED, work);
    }

    <X> List<QueryContext.DmlResult<X>> executeBatch(List<UpdateStatement> originals, List<Object> keys,
                                                     Function<Object[], Object> keyOf, Function<Object[], X> mapper,
                                                     QueryContext.ExecOptions options) {
        if (originals.isEmpty()) return List.of();
        if (keys.size() != originals.size()) throw new IllegalArgumentException("one key per statement required");
        Origin origin = origin(options);
        List<StatementListener> listeners = listenersFor(originals.get(0).table());
        Supplier<List<QueryContext.DmlResult<X>>> work = () -> {
            int n = originals.size();
            List<UpdateStatement> statements = new ArrayList<>(n);
            List<WriteContexts.Base> writes = new ArrayList<>(n);
            List<Integer> callerCounts = new ArrayList<>(n);
            List<RenderedSql> sqls = new ArrayList<>(n);
            List<List<Field<?>>> returnings = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                UpdateStatement s = originals.get(i).copy();
                QueryContext.ExecOptions single = new QueryContext.ExecOptions(options.origin(),
                        options.entityWrites().isEmpty() ? List.of() : List.of(options.entityWrites().get(i)));
                WriteContexts.Base write = prepare(s, origin, single, listeners);
                callerCounts.add(s.returning().size());
                addExtraReturning(s, write, listeners);
                List<Field<?>> returning = s.allReturning();
                for (Field<?> f : returning) {
                    if (!(f instanceof Column)) throw new IllegalArgumentException("batched updates can only return columns");
                }
                s.returning().clear();
                s.extraReturning().clear();
                statements.add(s);
                writes.add(write);
                returnings.add(returning);
                sqls.add(render(s));
            }
            List<QueryContext.DmlResult<X>> results = new ArrayList<>(n);
            int i = 0;
            while (i < n) {
                int j = i + 1;
                while (j < n && j - i < ctx.batchSize && sqls.get(j).sql().equals(sqls.get(i).sql())
                        && returnings.get(j).equals(returnings.get(i))) j++;
                List<List<Bind<?>>> sets = new ArrayList<>();
                for (int k = i; k < j; k++) sets.add(sqls.get(k).binds());
                List<Field<?>> returning = returnings.get(i);
                List<String> names = new ArrayList<>();
                for (Field<?> f : returning) names.add(((Column<?>) f).name());
                List<DataType<?>> types = types(returning);
                RenderedSql first = sqls.get(i);
                BatchResult batch = run(event(statements.get(i), first, origin, j - i),
                        vc -> ctx.executor.batch(vc, first.sql(), sets, names, types),
                        r -> Arrays.stream(r.counts()).map(c -> Math.max(c, 0)).sum());
                Map<Object, List<Object[]>> rowsByKey = new HashMap<>();
                if (!returning.isEmpty()) {
                    for (Object[] row : batch.rows()) rowsByKey.computeIfAbsent(keyOf.apply(row), k -> new ArrayList<>()).add(row);
                }
                for (int k = i; k < j; k++) {
                    List<Object[]> rows = rowsByKey.getOrDefault(keys.get(k), List.of());
                    long count = batch.counts()[k - i];
                    if (count < 0) count = rows.size();
                    int callerCount = callerCounts.get(k);
                    List<X> mapped = new ArrayList<>();
                    if (callerCount > 0) for (Object[] v : rows) mapped.add(mapper.apply(Arrays.copyOf(v, callerCount)));
                    afterListeners(writes.get(k), count, rows, callerCount, listeners);
                    results.add(new QueryContext.DmlResult<>(count, mapped));
                }
                i = j;
            }
            return results;
        };
        return listeners.isEmpty() ? work.get() : ctx.transactions.inTransaction(Propagation.REQUIRED, work);
    }

    private DmlStatement applyDeletePolicies(DmlStatement statement) {
        DmlStatement current = statement;
        for (TablePolicy p : ctx.policies) {
            if (current instanceof DeleteStatement && ctx.policyActive(p, current.table())) {
                current = p.onDelete((DeleteStatement) current, policyContext);
            }
        }
        return current;
    }

    /** Policies, conventions and before-listeners; returns the write context. */
    private WriteContexts.Base prepare(DmlStatement statement, Origin origin, QueryContext.ExecOptions options,
                                       List<StatementListener> listeners) {
        WriteContexts.Base write;
        if (statement instanceof InsertStatement) {
            WriteContexts.Insert c = new WriteContexts.Insert(ctx, (InsertStatement) statement, origin, options.entityWrites());
            for (TablePolicy p : ctx.policies) if (ctx.policyActive(p, statement.table())) p.onInsert(c);
            applyInsertConventions((InsertStatement) statement, c);
            for (StatementListener l : listeners) l.beforeInsert(c);
            write = c;
        } else if (statement instanceof UpdateStatement) {
            WriteContexts.Update c = new WriteContexts.Update(ctx, (UpdateStatement) statement, origin, options.entityWrites());
            for (TablePolicy p : ctx.policies) if (ctx.policyActive(p, statement.table())) p.onUpdate(c);
            applyUpdateConventions((UpdateStatement) statement, c);
            for (StatementListener l : listeners) l.beforeUpdate(c);
            write = c;
        } else if (statement instanceof DeleteStatement) {
            WriteContexts.Delete c = new WriteContexts.Delete(ctx, (DeleteStatement) statement, origin, options.entityWrites());
            for (StatementListener l : listeners) l.beforeDelete(c);
            write = c;
        } else {
            throw new IllegalArgumentException("unsupported statement " + statement.getClass().getName());
        }
        return write;
    }

    private void addExtraReturning(DmlStatement statement, WriteContexts.Base write, List<StatementListener> listeners) {
        statement.extraReturning().addAll(write.requested);
        if (!listeners.isEmpty() && statement instanceof InsertStatement && ((InsertStatement) statement).isUpsert()) {
            statement.extraReturning().add(Fields.condition(c -> c.append("(xmax = 0)"), true).as(INSERTED_FLAG));
        }
    }

    private void afterListeners(WriteContexts.Base write, long count, List<Object[]> rawRows, int callerCount,
                                List<StatementListener> listeners) {
        if (listeners.isEmpty()) return;
        List<Column<?>> requested = WriteContexts.list(write.requested);
        boolean upsert = write.statement instanceof InsertStatement && ((InsertStatement) write.statement).isUpsert();
        List<AffectedRow> affected = new ArrayList<>(rawRows.size());
        for (Object[] v : rawRows) {
            Map<Column<?>, Object> values = new LinkedHashMap<>();
            for (int i = 0; i < requested.size(); i++) values.put(requested.get(i), v[callerCount + i]);
            Boolean inserted = upsert ? (Boolean) v[callerCount + requested.size()] : null;
            affected.add(new AffectedRow(values, inserted));
        }
        WriteContexts.Result result = new WriteContexts.Result(write, count, List.copyOf(affected), upsert, null);
        for (StatementListener l : listeners) {
            WriteContexts.Result r = result.forListener(l);
            switch (write.statement.kind()) {
                case INSERT:
                    l.afterInsert(r);
                    break;
                case UPDATE:
                    l.afterUpdate(r);
                    break;
                case DELETE:
                    l.afterDelete(r);
                    break;
                default:
                    break;
            }
        }
    }

    private void applyInsertConventions(InsertStatement statement, WriteContexts.Insert write) {
        if (statement.source() != null || ctx.conventions.isEmpty()) return;
        for (Column<?> column : statement.table().columns()) {
            if (column.generated()) continue;
            for (ColumnConvention<?> convention : ctx.conventions) {
                if (convention.onInsert() && convention.appliesTo(column)) insertValue(statement, convention, column, write);
            }
        }
    }

    private static <T> void insertValue(InsertStatement statement, ColumnConvention<T> convention, Column<?> column,
                                        WriteContexts.Insert write) {
        Field<T> value = convention.valueFor(column, write);
        statement.setValue(convention.typed(column), value, convention.isForced());
    }

    private void applyUpdateConventions(UpdateStatement statement, WriteContexts.Update write) {
        if (statement.assignments().isEmpty()) return;
        for (Column<?> column : statement.table().columns()) {
            if (column.generated()) continue;
            for (ColumnConvention<?> convention : ctx.conventions) {
                if (convention.onUpdate() && convention.appliesTo(column)) updateValue(statement, convention, column, write);
            }
        }
        if (ctx.versionColumn != null) {
            statement.table().column(ctx.versionColumn).ifPresent(v -> incrementVersion(statement, v));
        }
    }

    private static <T> void updateValue(UpdateStatement statement, ColumnConvention<T> convention, Column<?> column,
                                        WriteContexts.Update write) {
        Field<T> value = convention.valueFor(column, write);
        statement.set(convention.typed(column), value, convention.isForced());
    }

    private static <T> void incrementVersion(UpdateStatement statement, Column<T> version) {
        if (version.type().kind() != Kind.NUMBER) {
            throw new IllegalStateException("version column " + version + " must be numeric");
        }
        if (!statement.assignments().containsKey(version)) {
            statement.set(version, Fields.of(version.type(), c -> c.append('(').visit(version).append(" + 1)")), false);
        }
    }

    // =========================================================================
    // TRUNCATE and other statements
    // =========================================================================

    void truncate(TruncateStatement original, QueryContext.ExecOptions options) {
        TruncateStatement statement = original.copy();
        Origin origin = origin(options);
        List<StatementListener> listeners = new ArrayList<>();
        for (StatementListener l : ctx.listeners) {
            if (statement.tables().stream().anyMatch(l::appliesTo)) listeners.add(l);
        }
        Supplier<Void> work = () -> {
            WriteContexts.Truncate c = new WriteContexts.Truncate(ctx, statement, origin);
            for (TablePolicy p : ctx.policies) {
                if (statement.tables().stream().anyMatch(t -> ctx.policyActive(p, t))) p.onTruncate(c);
            }
            for (StatementListener l : listeners) l.beforeTruncate(c);
            Map<Table<?>, List<AffectedRow>> captured = new LinkedHashMap<>();
            if (c.capture) {
                for (Table<?> t : statement.tables()) captured.put(t, capture(t));
            }
            RenderedSql sql = render(statement);
            run(event(statement, sql, origin, 1), vc -> ctx.executor.update(vc, sql, List.of()), UpdateResult::count);
            WriteContexts.TruncateDone done = new WriteContexts.TruncateDone(c, captured);
            for (StatementListener l : listeners) l.afterTruncate(done);
            return null;
        };
        if (listeners.isEmpty()) work.get();
        else ctx.transactions.inTransaction(Propagation.REQUIRED, work);
    }

    private List<AffectedRow> capture(Table<?> table) {
        SelectStatement select = new SelectStatement();
        select.fields().addAll(table.columns());
        select.from().add(table);
        select.locks().add(new ch.lxrin.ql.statement.Lock("UPDATE", List.of(), null));
        List<Column<?>> columns = new ArrayList<>(table.columns());
        List<Field<?>> fields = new ArrayList<>(columns);
        RenderedSql sql = renderPlain(select);
        List<Object[]> rows = run(event(select, sql, Origin.listener("truncate-capture"), 1), vc -> {
            List<Object[]> result = new ArrayList<>();
            try (ResultCursor cursor = ctx.executor.query(vc, sql, types(fields), 0)) {
                for (Object[] r = cursor.next(); r != null; r = cursor.next()) result.add(r);
            }
            return result;
        }, List::size);
        List<AffectedRow> affected = new ArrayList<>();
        for (Object[] v : rows) {
            Map<Column<?>, Object> values = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) values.put(columns.get(i), v[i]);
            affected.add(new AffectedRow(values, null));
        }
        return affected;
    }

    private RenderedSql renderPlain(Statement statement) {
        RenderContext rc = new RenderContext();
        statement.render(rc);
        return rc.result();
    }

    long executeOther(OtherStatement statement) {
        RenderedSql sql = render(statement);
        return run(event(statement, sql, null, 1), vc -> ctx.executor.update(vc, sql, List.of()), UpdateResult::count).count();
    }

    // =========================================================================
    // Execution with observers
    // =========================================================================

    private interface Work<T> {
        T run(ExecValueContext vc) throws SQLException;
    }

    private <T> T run(StatementEvent event, Work<T> work, ToLongFunction<T> rows) {
        long start = System.nanoTime();
        notifyStart(event);
        Connection con = null;
        try {
            con = acquire();
            T result = work.run(new ExecValueContext(con, ctx.jsonCodec));
            notifySuccess(event, start, rows.applyAsLong(result));
            return result;
        } catch (SQLException e) {
            throw failed(event, start, SqlErrors.translate(e, event.sql()));
        } catch (RuntimeException e) {
            throw failed(event, start, wrap(e));
        } finally {
            release(con);
        }
    }

    private Connection acquire() throws SQLException {
        return ctx.connections == null ? null : ctx.connections.acquire();
    }

    private void release(Connection con) {
        if (con == null || ctx.connections == null) return;
        try {
            ctx.connections.release(con);
        } catch (SQLException ignored) {
            // releasing must not hide the result
        }
    }

    private static LxrinQlException wrap(RuntimeException e) {
        return e instanceof LxrinQlException ? (LxrinQlException) e : new LxrinQlException(e.getMessage(), e);
    }

    private LxrinQlException failed(StatementEvent event, long start, LxrinQlException error) {
        Duration took = Duration.ofNanos(System.nanoTime() - start);
        for (ExecutionObserver o : ctx.observers) {
            try {
                o.onError(event, took, error);
            } catch (RuntimeException ignored) {
                // observers must not break execution
            }
        }
        return error;
    }

    private void notifyStart(StatementEvent event) {
        for (ExecutionObserver o : ctx.observers) {
            try {
                o.onStart(event);
            } catch (RuntimeException ignored) {
                // observers must not break execution
            }
        }
    }

    private void notifySuccess(StatementEvent event, long start, long rows) {
        Duration took = Duration.ofNanos(System.nanoTime() - start);
        for (ExecutionObserver o : ctx.observers) {
            try {
                o.onSuccess(event, took, rows);
            } catch (RuntimeException ignored) {
                // observers must not break execution
            }
        }
    }

    private StatementEvent event(Statement statement, RenderedSql sql, Origin origin, int batchSize) {
        return new StatementEvent(statement.kind(), statement.tables(), sql, origin != null ? origin : ctx.origin, batchSize);
    }

    private Origin origin(QueryContext.ExecOptions options) {
        return options.origin() != null ? options.origin() : ctx.origin;
    }

    private List<StatementListener> listenersFor(Table<?> table) {
        List<StatementListener> result = new ArrayList<>();
        for (StatementListener l : ctx.listeners) if (l.appliesTo(table)) result.add(l);
        return result;
    }

    private static List<DataType<?>> types(List<Field<?>> fields) {
        List<DataType<?>> types = new ArrayList<>(fields.size());
        for (Field<?> f : fields) types.add(f.type());
        return types;
    }
}
