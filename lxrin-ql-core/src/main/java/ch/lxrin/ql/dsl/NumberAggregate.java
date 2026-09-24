package ch.lxrin.ql.dsl;

/**
 * A numeric aggregate such as {@code count}, {@code sum} or {@code avg}.
 *
 * @param <N> the result type
 */
public interface NumberAggregate<N extends Number> extends AggregateFunction<N>, NumberField<N> {

    @Override
    NumberAggregate<N> distinct();

    @Override
    NumberAggregate<N> filter(Condition condition);

    @Override
    NumberAggregate<N> orderBy(SortField<?>... sortFields);

    @Override
    NumberAggregate<N> withinGroup(SortField<?>... sortFields);

    @Override
    NumberField<N> over();

    @Override
    NumberField<N> over(WindowSpec window);

    @Override
    NumberField<N> over(WindowDefinition window);
}
