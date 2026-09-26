package ch.lxrin.ql.test;

import ch.lxrin.ql.render.Bind;
import ch.lxrin.ql.render.RenderedSql;
import ch.lxrin.ql.runtime.BatchResult;
import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.runtime.ResultCursor;
import ch.lxrin.ql.runtime.SqlExecutor;
import ch.lxrin.ql.runtime.UpdateResult;
import ch.lxrin.ql.types.DataType;
import ch.lxrin.ql.types.ValueContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/**
 * An executor for unit tests without a database. It records every statement
 * and answers with scripted rows:
 * <pre>{@code
 * MockExecutor db = new MockExecutor();
 * db.whenSql(sql -> sql.startsWith("SELECT")).thenReturn(row(id, "Ada"));
 * QueryContext ctx = db.context();
 * ...
 * assertThatSql(db.lastStatement()).isEqualTo("SELECT ...");
 * }</pre>
 * Unmatched queries return no rows; unmatched updates affect one row.
 */
public final class MockExecutor implements SqlExecutor {

    /** A scripted answer. */
    public final class Rule {
        private final Predicate<String> sql;
        private List<Object[]> rows = List.of();
        private long count = -1;
        private RuntimeException failure;
        private int times = Integer.MAX_VALUE;

        Rule(Predicate<String> sql) {
            this.sql = sql;
        }

        /** Answers with rows (for queries and {@code RETURNING}). */
        public MockExecutor thenReturn(Object[]... result) {
            rows = List.of(result);
            return MockExecutor.this;
        }

        /** Answers with an affected-row count (for statements without {@code RETURNING}). */
        public MockExecutor thenAffect(long affected) {
            count = affected;
            return MockExecutor.this;
        }

        /**
         * Throws the exception. Like any executor failure, an exception that is
         * not a {@code LxrinQlException} (for example {@code UniqueViolationException})
         * reaches the caller wrapped in one.
         */
        public MockExecutor thenThrow(RuntimeException exception) {
            failure = exception;
            return MockExecutor.this;
        }

        /** Limits the rule to the next {@code n} matching statements. */
        public Rule times(int n) {
            times = n;
            return this;
        }
    }

    private final List<Rule> rules = new ArrayList<>();
    private final List<RenderedSql> statements = Collections.synchronizedList(new ArrayList<>());

    /** A row for {@link Rule#thenReturn}. */
    public static Object[] row(Object... values) {
        return values;
    }

    /** Starts a rule for statements whose SQL matches. Later rules take precedence. */
    public Rule whenSql(Predicate<String> sql) {
        Rule rule = new Rule(sql);
        rules.add(0, rule);
        return rule;
    }

    /** Starts a rule for statements whose SQL contains a fragment. */
    public Rule whenSqlContains(String fragment) {
        return whenSql(s -> s.contains(fragment));
    }

    /** Returns a query context that uses this executor (no real transactions). */
    public QueryContext context() {
        return QueryContext.builder().executor(this).build();
    }

    /** Returns all recorded statements (a JDBC batch records one entry per parameter set). */
    public List<RenderedSql> statements() {
        return List.copyOf(statements);
    }

    /** Returns the last recorded statement. */
    public RenderedSql lastStatement() {
        if (statements.isEmpty()) throw new AssertionError("no statement was executed");
        return statements.get(statements.size() - 1);
    }

    /** Forgets recorded statements and rules. */
    public void reset() {
        statements.clear();
        rules.clear();
    }

    private Rule match(String sql) {
        synchronized (rules) {
            for (Rule r : rules) {
                if (r.times > 0 && r.sql.test(sql)) {
                    if (r.times != Integer.MAX_VALUE) r.times--;
                    if (r.failure != null) throw r.failure;
                    return r;
                }
            }
        }
        return null;
    }

    @Override
    public ResultCursor query(ValueContext context, RenderedSql sql, List<DataType<?>> resultTypes, int fetchSize) {
        statements.add(sql);
        Rule rule = match(sql.sql());
        Iterator<Object[]> it = (rule == null ? List.<Object[]>of() : rule.rows).iterator();
        return new ResultCursor() {
            @Override
            public Object[] next() {
                return it.hasNext() ? it.next().clone() : null;
            }

            @Override
            public void close() {
            }
        };
    }

    @Override
    public UpdateResult update(ValueContext context, RenderedSql sql, List<DataType<?>> returningTypes) {
        statements.add(sql);
        Rule rule = match(sql.sql());
        if (!returningTypes.isEmpty()) {
            List<Object[]> rows = rule == null ? List.of() : rule.rows;
            return new UpdateResult(rows.size(), rows);
        }
        return new UpdateResult(rule == null || rule.count < 0 ? 1 : rule.count, List.of());
    }

    @Override
    public BatchResult batch(ValueContext context, String sql, List<List<Bind<?>>> parameterSets, List<String> returningColumns,
                             List<DataType<?>> returningTypes) {
        for (List<Bind<?>> binds : parameterSets) statements.add(new RenderedSql(sql, binds));
        Rule rule = match(sql);
        long[] counts = new long[parameterSets.size()];
        Arrays.fill(counts, rule == null || rule.count < 0 ? 1 : rule.count);
        return new BatchResult(counts, rule == null ? List.of() : rule.rows);
    }
}
