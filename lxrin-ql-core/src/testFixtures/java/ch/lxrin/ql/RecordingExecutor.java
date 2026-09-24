package ch.lxrin.ql;

import ch.lxrin.ql.render.Bind;
import ch.lxrin.ql.render.RenderedSql;
import ch.lxrin.ql.runtime.BatchResult;
import ch.lxrin.ql.runtime.ResultCursor;
import ch.lxrin.ql.runtime.SqlExecutor;
import ch.lxrin.ql.runtime.UpdateResult;
import ch.lxrin.ql.types.DataType;
import ch.lxrin.ql.types.ValueContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/** Records statements and returns queued rows; for unit tests without a database. */
public final class RecordingExecutor implements SqlExecutor {

    public final List<RenderedSql> statements = new ArrayList<>();
    private final Deque<List<Object[]>> results = new ArrayDeque<>();
    private final Deque<Long> counts = new ArrayDeque<>();

    /** Queues the rows of the next query or RETURNING statement. */
    public RecordingExecutor willReturn(Object[]... rows) {
        results.add(List.of(rows));
        return this;
    }

    /** Queues the affected row count of the next statement without RETURNING. */
    public RecordingExecutor willAffect(long count) {
        counts.add(count);
        return this;
    }

    public String lastSql() {
        return statements.get(statements.size() - 1).sql();
    }

    @Override
    public ResultCursor query(ValueContext context, RenderedSql sql, List<DataType<?>> resultTypes, int fetchSize) {
        statements.add(sql);
        Iterator<Object[]> it = (results.isEmpty() ? List.<Object[]>of() : results.poll()).iterator();
        return new ResultCursor() {
            @Override
            public Object[] next() {
                return it.hasNext() ? it.next() : null;
            }

            @Override
            public void close() {
            }
        };
    }

    @Override
    public UpdateResult update(ValueContext context, RenderedSql sql, List<DataType<?>> returningTypes) {
        statements.add(sql);
        if (!returningTypes.isEmpty()) {
            List<Object[]> rows = results.isEmpty() ? List.of() : results.poll();
            return new UpdateResult(rows.size(), rows);
        }
        return new UpdateResult(counts.isEmpty() ? 1 : counts.poll(), List.of());
    }

    @Override
    public BatchResult batch(ValueContext context, String sql, List<List<Bind<?>>> parameterSets,
                             List<String> returningColumns, List<DataType<?>> returningTypes) {
        for (List<Bind<?>> binds : parameterSets) statements.add(new RenderedSql(sql, binds));
        long[] c = new long[parameterSets.size()];
        java.util.Arrays.fill(c, 1);
        return new BatchResult(c, results.isEmpty() ? List.of() : results.poll());
    }
}
