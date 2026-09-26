package ch.lxrin.ql.dsl;

import ch.lxrin.ql.runtime.QueryContext;

import java.util.List;
import java.util.function.Function;

/**
 * A {@code SELECT} whose rows are mapped by a function: {@code selectFrom(USERS)}
 * returns the table's row records, {@code select(List<Field<?>>)} returns {@link Row}s.
 *
 * @param <R> the row type
 */
public final class Select<R> extends AbstractSelect<R, Select<R>> {

    private final Function<Object[], R> mapper;

    /**
     * @param context the query context, or {@code null} for the default
     * @param fields  the select list
     * @param mapper  maps the values of one row
     */
    public Select(QueryContext context, List<? extends Field<?>> fields, Function<Object[], R> mapper) {
        super(context, fields);
        this.mapper = mapper;
    }

    @Override
    protected R map(Object[] values) {
        return mapper.apply(values);
    }

    /** Executes the query and maps every row. */
    public <X> List<X> fetch(Function<? super R, ? extends X> rowMapper) {
        return fetchMapped(rowMapper);
    }
}
