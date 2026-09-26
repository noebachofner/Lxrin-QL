package ch.lxrin.ql.dsl;

import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.schema.Table;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The {@code c} of {@code createContribution(Type.class, TABLE, (c, b) -> ..)}:
 * starts the query, whose rows are mapped into {@code Type}. The returned
 * {@link Select} is the normal 3.x builder, so every operator, join and
 * dynamic feature is available:
 *
 * <pre>{@code
 * List<UserSummary> users = createContribution(UserSummary.class, USERS, (c, b) -> c
 *         .select(USERS.ID, USERS.NAME, USERS.EMAIL)
 *         .where(USERS.EMAIL.endsWith("@example.org"),
 *                USERS.CREATED_AT.ge(b.setInstant(since))))
 *         .fetch();
 * }</pre>
 *
 * @param <T> the result type
 */
public final class SelectScope<T> {

    private final QueryContext context;
    private final Class<T> type;
    private final Table<?> table;

    SelectScope(QueryContext context, Class<T> type, Table<?> table) {
        this.context = context;
        this.type = type;
        this.table = table;
    }

    /** Returns the table the query reads from, or {@code null}. */
    public Table<?> table() {
        return table;
    }

    /** {@code SELECT fields FROM table} */
    public Select<T> select(Field<?>... fields) {
        return select(Arrays.asList(fields));
    }

    /** {@code SELECT fields FROM table} for a dynamic select list. */
    public Select<T> select(List<? extends Field<?>> fields) {
        for (Field<?> f : fields) if (f == null) throw new IllegalArgumentException("select fields must not be null");
        Select<T> select = new Select<>(context, List.copyOf(fields), ResultMapping.of(type, fields));
        return table == null ? select : select.from(table);
    }

    /** {@code SELECT DISTINCT fields FROM table} */
    public Select<T> selectDistinct(Field<?>... fields) {
        return select(fields).distinct();
    }

    /** {@code SELECT <all columns of the table> FROM table} */
    public Select<T> selectAll() {
        if (table == null) throw new IllegalStateException("selectAll() needs a table");
        return select(new ArrayList<>(table.columns()));
    }
}
