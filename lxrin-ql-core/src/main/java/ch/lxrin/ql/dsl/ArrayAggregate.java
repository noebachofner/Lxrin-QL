package ch.lxrin.ql.dsl;

/**
 * An array aggregate such as {@code array_agg}.
 *
 * @param <E> the element type
 */
public interface ArrayAggregate<E> extends AggregateFunction<E[]>, ArrayField<E> {

    @Override
    ArrayAggregate<E> distinct();

    @Override
    ArrayAggregate<E> filter(Condition condition);

    @Override
    ArrayAggregate<E> orderBy(SortField<?>... sortFields);

    @Override
    ArrayAggregate<E> withinGroup(SortField<?>... sortFields);

    @Override
    ArrayField<E> over();

    @Override
    ArrayField<E> over(WindowSpec window);

    @Override
    ArrayField<E> over(WindowDefinition window);
}
