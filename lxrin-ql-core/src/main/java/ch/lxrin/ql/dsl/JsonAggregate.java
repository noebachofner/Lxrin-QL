package ch.lxrin.ql.dsl;

/**
 * A JSON aggregate such as {@code jsonb_agg}.
 *
 * @param <T> the Java type of the JSON result
 */
public interface JsonAggregate<T> extends AggregateFunction<T>, JsonField<T> {

    @Override
    JsonAggregate<T> distinct();

    @Override
    JsonAggregate<T> filter(Condition condition);

    @Override
    JsonAggregate<T> orderBy(SortField<?>... sortFields);

    @Override
    JsonAggregate<T> withinGroup(SortField<?>... sortFields);

    @Override
    JsonField<T> over();

    @Override
    JsonField<T> over(WindowSpec window);

    @Override
    JsonField<T> over(WindowDefinition window);
}
