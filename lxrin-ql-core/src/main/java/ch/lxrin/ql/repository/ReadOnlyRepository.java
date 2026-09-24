package ch.lxrin.ql.repository;

import ch.lxrin.ql.dsl.Condition;
import ch.lxrin.ql.dsl.Page;
import ch.lxrin.ql.dsl.Select;
import ch.lxrin.ql.dsl.SortField;
import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.spi.Origin;

import java.util.List;
import java.util.Optional;

/**
 * Queries of a view or of a table without primary key. Rows are the
 * generated row records.
 *
 * @param <R> the row record type
 */
public abstract class ReadOnlyRepository<R> {

    private final QueryContext context;
    private final Table<R> table;

    /**
     * @param context the query context
     * @param table   the table or view
     */
    protected ReadOnlyRepository(QueryContext context, Table<R> table) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        this.context = context;
        this.table = table;
    }

    /** Returns the table. */
    public Table<R> table() {
        return table;
    }

    /** Returns the query context, with the repository as origin, for custom queries. */
    protected QueryContext ctx() {
        return context.withOrigin(Origin.repository(getClass().getSimpleName()));
    }

    /** Returns all rows matching {@code where}, ordered by {@code orderBy}. */
    public List<R> findAll(Condition where, SortField<?>... orderBy) {
        return ctx().selectFrom(table).where(where).orderBy(orderBy).fetch();
    }

    /** Returns all rows. */
    public List<R> findAll() {
        return findAll(Condition.noCondition());
    }

    /** Returns the only row matching {@code where}, if any; fails if there are several. */
    public Optional<R> findOne(Condition where) {
        return ctx().selectFrom(table).where(where).fetchOptional();
    }

    /**
     * Returns a keyset-paginated page.
     *
     * @param cursor  the encoded cursor of the previous page, or {@code null} for the first page
     * @param limit   the page size
     * @param orderBy the sort order; it should end with a unique column
     */
    public Page<R> findPage(Condition where, String cursor, int limit, SortField<?>... orderBy) {
        Select<R> select = ctx().selectFrom(table).where(where).orderBy(orderBy);
        return select.seekAfterCursor(cursor).limit(limit).fetchPage();
    }

    /** Returns {@code true} if a row matches. */
    public boolean exists(Condition where) {
        return ctx().selectOne().from(table).where(where).fetchExists();
    }

    /** Returns the number of matching rows. */
    public long count(Condition where) {
        return ctx().selectCount().from(table).where(where).fetchOne();
    }

    /** Returns the number of rows. */
    public long count() {
        return count(Condition.noCondition());
    }
}
