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
    private final boolean byPosition;

    SelectScope(QueryContext context, Class<T> type, Table<?> table) {
        this(context, type, table, false);
    }

    private SelectScope(QueryContext context, Class<T> type, Table<?> table, boolean byPosition) {
        this.context = context;
        this.type = type;
        this.table = table;
        this.byPosition = byPosition;
    }

    /**
     * Maps the selected fields into the record by position instead of by name:
     * {@code c.mapByPosition().select(USERS.ID, USERS.NAME)} for {@code record Pair(UUID a, String b)}.
     * By default a record component without a field of the same name is an error, so that
     * reordering the select list can never put values into the wrong components silently.
     */
    public SelectScope<T> mapByPosition() {
        return new SelectScope<>(context, type, table, true);
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
        Select<T> select = new Select<>(context, List.copyOf(fields), ResultMapping.of(type, fields, byPosition));
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
