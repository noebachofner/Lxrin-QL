package ch.lxrin.ql.dsl;

import ch.lxrin.ql.error.NoRowsException;
import ch.lxrin.ql.error.TooManyRowsException;
import ch.lxrin.ql.render.QueryPart;
import ch.lxrin.ql.render.RenderContext;
import ch.lxrin.ql.render.RenderedSql;
import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.statement.Join;
import ch.lxrin.ql.statement.Lock;
import ch.lxrin.ql.statement.SelectStatement;
import ch.lxrin.ql.types.SqlTypes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The clauses and terminal operations shared by all {@code SELECT} builders.
 *
 * <p>A builder is <em>attached</em> to a {@link QueryContext} when created
 * through {@code ctx.select(..)}; a builder from the static
 * {@code Dsl.select(..)} runs on {@link QueryContext#getDefault()}. The
 * builder can be executed any number of times.</p>
 *
 * @param <R> the row type returned by {@link #fetch()}
 * @param <S> the concrete builder type
 */
public abstract class AbstractSelect<R, S extends AbstractSelect<R, S>> implements CteSource, QueryPart {

    private QueryContext context;
    final SelectStatement statement = new SelectStatement();

    /**
     * @param context the query context, or {@code null} for the default context
     * @param fields  the select list
     */
    protected AbstractSelect(QueryContext context, List<? extends Field<?>> fields) {
        this.context = context;
        for (Field<?> f : fields) {
            if (f == null) throw new IllegalArgumentException("select fields must not be null");
            statement.fields().add(f);
        }
    }

    /** Maps the values of one result row (in select order) to the row type. */
    protected abstract R map(Object[] values);

    @SuppressWarnings("unchecked")
    private S self() {
        return (S) this;
    }

    // =========================================================================
    // SELECT list, FROM, JOIN
    // =========================================================================

    /** {@code SELECT DISTINCT} */
    public S distinct() {
        statement.distinct(true);
        return self();
    }

    /** {@code SELECT DISTINCT ON (fields)} – the first row of each group (with {@code ORDER BY}). */
    public S distinctOn(Field<?>... fields) {
        statement.distinctOn().addAll(Arrays.asList(fields));
        return self();
    }

    /** Adds {@code FROM} tables (comma-separated). */
    public S from(Table<?>... tables) {
        for (Table<?> t : tables) {
            if (t == null) throw new IllegalArgumentException("table must not be null");
            statement.from().add(t);
        }
        return self();
    }

    /** {@code JOIN table} – continue with {@code on(..)}, {@code onKey(..)} or {@code using(..)}. */
    public JoinStep<S> join(Table<?> table) {
        return new JoinStep<>(self(), statement, Join.Type.INNER, table);
    }

    /** Same as {@link #join(Table)}. */
    public JoinStep<S> innerJoin(Table<?> table) {
        return join(table);
    }

    /** {@code LEFT JOIN table} */
    public JoinStep<S> leftJoin(Table<?> table) {
        return new JoinStep<>(self(), statement, Join.Type.LEFT, table);
    }

    /** {@code RIGHT JOIN table} */
    public JoinStep<S> rightJoin(Table<?> table) {
        return new JoinStep<>(self(), statement, Join.Type.RIGHT, table);
    }

    /** {@code FULL JOIN table} */
    public JoinStep<S> fullJoin(Table<?> table) {
        return new JoinStep<>(self(), statement, Join.Type.FULL, table);
    }

    /** {@code CROSS JOIN table} */
    public S crossJoin(Table<?> table) {
        statement.joins().add(new Join(Join.Type.CROSS, table, null, List.of()));
        return self();
    }

    /** {@code NATURAL JOIN table} */
    public S naturalJoin(Table<?> table) {
        statement.joins().add(new Join(Join.Type.NATURAL, table, null, List.of()));
        return self();
    }

    // =========================================================================
    // WHERE / GROUP BY / HAVING / WINDOW
    // =========================================================================

    /** Adds conditions to {@code WHERE}; all conditions are joined with {@code AND}. */
    public S where(Condition... conditions) {
        for (Condition c : conditions) statement.addWhere(requireCondition(c));
        return self();
    }

    /** Adds a condition only if {@code apply} is {@code true}; the supplier is only called then. */
    public S whereIf(boolean apply, Supplier<Condition> condition) {
        return apply ? where(condition.get()) : self();
    }

    /** Adds {@code GROUP BY} fields. */
    public S groupBy(Field<?>... fields) {
        statement.groupBy().addAll(Arrays.asList(fields));
        return self();
    }

    /** Adds {@code GROUP BY ROLLUP/CUBE/GROUPING SETS} elements. */
    public S groupBy(GroupingElement... elements) {
        statement.groupBy().addAll(Arrays.asList(elements));
        return self();
    }

    /** Adds {@code HAVING} conditions, joined with {@code AND}. */
    public S having(Condition... conditions) {
        for (Condition c : conditions) statement.addHaving(requireCondition(c));
        return self();
    }

    /** Declares named windows ({@code WINDOW w AS (...)}). */
    public S window(WindowDefinition... windows) {
        statement.windows().addAll(Arrays.asList(windows));
        return self();
    }

    // =========================================================================
    // ORDER BY, LIMIT, OFFSET, keyset
    // =========================================================================

    /** Adds {@code ORDER BY} items. */
    public S orderBy(SortField<?>... sortFields) {
        statement.orderBy().addAll(Arrays.asList(sortFields));
        return self();
    }

    /** Adds {@code ORDER BY field ASC} items. */
    public S orderBy(Field<?>... fields) {
        for (Field<?> f : fields) statement.orderBy().add(f.asc());
        return self();
    }

    /** {@code LIMIT n} (written as a literal, so plans can use it). */
    public S limit(long n) {
        if (n < 0) throw new IllegalArgumentException("limit must not be negative");
        statement.limit(Values.inline(n));
        return self();
    }

    /** {@code LIMIT expr}, e.g. {@code limit(param(size))}. */
    public S limit(Field<? extends Number> n) {
        statement.limit(n);
        return self();
    }

    /** {@code OFFSET n} */
    public S offset(long n) {
        if (n < 0) throw new IllegalArgumentException("offset must not be negative");
        statement.offset(Values.inline(n));
        return self();
    }

    /** {@code OFFSET expr} */
    public S offset(Field<? extends Number> n) {
        statement.offset(n);
        return self();
    }

    /** {@code FETCH FIRST n ROWS WITH TIES} – needs {@code ORDER BY}. */
    public S limitWithTies(long n) {
        limit(n);
        statement.withTies(true);
        return self();
    }

    /** Offset paging: {@code LIMIT size OFFSET index * size} (the first page has index 0). */
    public S page(int pageIndex, int pageSize) {
        if (pageIndex < 0 || pageSize <= 0) throw new IllegalArgumentException("invalid page " + pageIndex + "/" + pageSize);
        return limit(pageSize).offset((long) pageIndex * pageSize);
    }

    /**
     * Keyset pagination: selects the rows after the given values of the
     * {@code ORDER BY} fields (one value per field, checked against the
     * fields' types). With uniform sort directions this renders an
     * index-friendly row comparison such as {@code (created_at, id) < (?, ?)}.
     */
    public S seekAfter(Object... values) {
        statement.seekAfter().clear();
        statement.seekAfter().addAll(Arrays.asList(values));
        return self();
    }

    /** Keyset pagination from a cursor returned by {@link #fetchPage()}. */
    public S seekAfter(Cursor cursor) {
        return cursor == null ? self() : seekAfter(cursor.values().toArray());
    }

    /**
     * Keyset pagination from an encoded cursor ({@link Cursor#encode()}), e.g.
     * a request parameter; {@code null} or empty starts at the beginning.
     */
    public S seekAfterCursor(String encodedCursor) {
        if (encodedCursor == null || encodedCursor.isEmpty()) return self();
        List<Field<?>> keys = new ArrayList<>();
        for (SortField<?> s : statement.orderBy()) keys.add(s.field());
        return seekAfter(Cursor.decode(encodedCursor, keys));
    }

    // =========================================================================
    // Locking and set operations
    // =========================================================================

    /** {@code FOR UPDATE} */
    public S forUpdate() {
        return lock("UPDATE");
    }

    /** {@code FOR NO KEY UPDATE} */
    public S forNoKeyUpdate() {
        return lock("NO KEY UPDATE");
    }

    /** {@code FOR SHARE} */
    public S forShare() {
        return lock("SHARE");
    }

    /** {@code FOR KEY SHARE} */
    public S forKeyShare() {
        return lock("KEY SHARE");
    }

    /** Restricts the last lock clause to tables: {@code OF t}. */
    public S of(Table<?>... tables) {
        Lock last = lastLock("of");
        List<Table<?>> of = new ArrayList<>(last.of());
        of.addAll(Arrays.asList(tables));
        replaceLastLock(new Lock(last.strength(), of, last.waitPolicy()));
        return self();
    }

    /** Adds {@code NOWAIT} to the last lock clause. */
    public S nowait() {
        Lock last = lastLock("nowait");
        replaceLastLock(new Lock(last.strength(), last.of(), "NOWAIT"));
        return self();
    }

    /** Adds {@code SKIP LOCKED} to the last lock clause (job queues). */
    public S skipLocked() {
        Lock last = lastLock("skipLocked");
        replaceLastLock(new Lock(last.strength(), last.of(), "SKIP LOCKED"));
        return self();
    }

    private S lock(String strength) {
        statement.locks().add(new Lock(strength, List.of(), null));
        return self();
    }

    private Lock lastLock(String method) {
        if (statement.locks().isEmpty()) throw new IllegalStateException(method + "() needs a preceding forUpdate()/forShare()");
        return statement.locks().get(statement.locks().size() - 1);
    }

    private void replaceLastLock(Lock lock) {
        statement.locks().set(statement.locks().size() - 1, lock);
    }

    /** {@code ... UNION (other)} */
    public S union(S other) {
        return setOperation("UNION", other);
    }

    /** {@code ... UNION ALL (other)} */
    public S unionAll(S other) {
        return setOperation("UNION ALL", other);
    }

    /** {@code ... INTERSECT (other)} */
    public S intersect(S other) {
        return setOperation("INTERSECT", other);
    }

    /** {@code ... INTERSECT ALL (other)} */
    public S intersectAll(S other) {
        return setOperation("INTERSECT ALL", other);
    }

    /** {@code ... EXCEPT (other)} */
    public S except(S other) {
        return setOperation("EXCEPT", other);
    }

    /** {@code ... EXCEPT ALL (other)} */
    public S exceptAll(S other) {
        return setOperation("EXCEPT ALL", other);
    }

    private S setOperation(String keyword, AbstractSelect<?, ?> other) {
        if (other == null) throw new IllegalArgumentException("query must not be null");
        if (other.statement.fields().size() != statement.fields().size()) {
            throw new IllegalArgumentException(keyword + " needs the same number of fields on both sides");
        }
        statement.setOperations().add(new SelectStatement.SetOperation(keyword, other.statement));
        return self();
    }

    // =========================================================================
    // WITH
    // =========================================================================

    /** Adds common table expressions: {@code WITH name AS (...)}. */
    public S with(Cte... ctes) {
        statement.with().add(false, ctes);
        return self();
    }

    /** Adds common table expressions and marks the clause {@code RECURSIVE}. */
    public S withRecursive(Cte... ctes) {
        statement.with().add(true, ctes);
        return self();
    }

    // =========================================================================
    // Execution
    // =========================================================================

    /** Attaches the builder to a query context. */
    public S attach(QueryContext queryContext) {
        this.context = queryContext;
        return self();
    }

    /** Returns the attached context, or the default context. */
    protected QueryContext context() {
        return context != null ? context : QueryContext.getDefault();
    }

    /** Executes the query and returns all rows (never {@code null}). */
    public List<R> fetch() {
        return context().fetch(statement, statement.fields(), this::map);
    }

    /** Executes the query and maps every row. */
    protected <X> List<X> fetchMapped(Function<? super R, ? extends X> mapper) {
        return context().fetch(statement, statement.fields(), v -> mapper.apply(map(v)));
    }

    /**
     * Executes the query and returns exactly one row.
     *
     * @throws NoRowsException      if there is no row
     * @throws TooManyRowsException if there are several rows
     */
    public R fetchOne() {
        List<R> rows = fetch();
        if (rows.isEmpty()) throw new NoRowsException("the query returned no row: " + this);
        if (rows.size() > 1) throw new TooManyRowsException("the query returned " + rows.size() + " rows, expected one: " + this);
        return rows.get(0);
    }

    /**
     * Executes the query and returns the row, if any.
     *
     * @throws TooManyRowsException if there are several rows
     */
    public Optional<R> fetchOptional() {
        List<R> rows = fetch();
        if (rows.size() > 1) throw new TooManyRowsException("the query returned " + rows.size() + " rows, expected at most one: " + this);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
    }

    /** Executes the query with {@code LIMIT 1} and returns the first row, if any. */
    public Optional<R> fetchFirst() {
        SelectStatement copy = statement.copy();
        copy.limit(Values.inline(1));
        List<R> rows = context().fetch(copy, copy.fields(), this::map);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
    }

    /** Returns the number of rows: {@code SELECT count(*) FROM (query) AS q}. */
    public long fetchCount() {
        SelectStatement count = new SelectStatement();
        count.fields().add(Fields.number(SqlTypes.INT8, ctx -> ctx.append("count(*)")));
        count.from().add(DerivedTable.wrap(statement, "q"));
        List<Long> rows = context().fetch(count, count.fields(), v -> (Long) v[0]);
        return rows.get(0);
    }

    /** Returns {@code true} if the query has at least one row: {@code SELECT EXISTS (query)}. */
    public boolean fetchExists() {
        SelectStatement exists = new SelectStatement();
        exists.fields().add(Fields.condition(ctx -> ctx.append("EXISTS ").visit(this), true));
        List<Boolean> rows = context().fetch(exists, exists.fields(), v -> (Boolean) v[0]);
        return Boolean.TRUE.equals(rows.get(0));
    }

    /**
     * Executes the query and streams the rows. The stream holds a database
     * connection and <strong>must be closed</strong> (use try-with-resources).
     * Inside a transaction rows are fetched in batches.
     */
    public Stream<R> stream() {
        return context().stream(statement, statement.fields(), this::map);
    }

    /**
     * Executes a keyset-paginated query ({@code orderBy(..)}, {@code limit(..)},
     * optionally {@code seekAfter(..)}) and returns the page with a cursor for
     * the next one. The {@code ORDER BY} fields must be part of the select list.
     */
    public Page<R> fetchPage() {
        if (statement.orderBy().isEmpty()) throw new IllegalStateException("fetchPage() needs orderBy(..)");
        if (statement.limit() == null) throw new IllegalStateException("fetchPage() needs limit(..)");
        Row.Shape shape = new Row.Shape(statement.fields());
        int[] keyIndexes = new int[statement.orderBy().size()];
        for (int i = 0; i < keyIndexes.length; i++) {
            keyIndexes[i] = shape.indexOf(statement.orderBy().get(i).field());
            if (keyIndexes[i] < 0) {
                throw new IllegalStateException("fetchPage() needs the ORDER BY field " + statement.orderBy().get(i).field()
                        + " in the select list");
            }
        }
        List<Object[]> raw = context().fetch(statement, statement.fields(), v -> v);
        List<R> items = new ArrayList<>(raw.size());
        for (Object[] v : raw) items.add(map(v));
        long limit = limitValue();
        Cursor next = null;
        if (!raw.isEmpty() && raw.size() >= limit) {
            Object[] last = raw.get(raw.size() - 1);
            List<Object> keys = new ArrayList<>();
            for (int i : keyIndexes) keys.add(last[i]);
            next = new Cursor(keys);
        }
        return new Page<>(items, next);
    }

    private long limitValue() {
        RenderContext ctx = new RenderContext();
        ctx.visit(statement.limit());
        try {
            return Long.parseLong(ctx.sql());
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    // =========================================================================
    // Model and rendering
    // =========================================================================

    /** Returns the statement model. */
    @Override
    public SelectStatement statement() {
        return statement;
    }

    /** Returns the select list. */
    @Override
    public List<Field<?>> fields() {
        return List.copyOf(statement.fields());
    }

    /** Uses this query as a table in {@code FROM}: {@code (SELECT ...) AS alias}. */
    public DerivedTable asTable(String alias) {
        return new DerivedTable(alias, statement, statement.fields(), false);
    }

    /** Renders the statement without table policies, for logging and tests. */
    public RenderedSql render() {
        RenderContext ctx = new RenderContext();
        statement.render(ctx);
        return ctx.result();
    }

    /** Renders the query in parentheses, for use as a sub-query. */
    @Override
    public void render(RenderContext ctx) {
        ctx.append('(');
        statement.render(ctx);
        ctx.append(')');
    }

    /** Returns the SQL (without policies), e.g. for logs. */
    @Override
    public String toString() {
        return render().toString();
    }

    static Condition requireCondition(Condition c) {
        if (c == null) throw new IllegalArgumentException("condition must not be null");
        return c;
    }
}
