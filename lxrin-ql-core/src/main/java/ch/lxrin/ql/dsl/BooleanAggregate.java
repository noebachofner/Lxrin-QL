package ch.lxrin.ql.dsl;

/** A boolean aggregate such as {@code bool_and}; usable as a condition in {@code HAVING}. */
public interface BooleanAggregate extends AggregateFunction<Boolean>, Condition {

    @Override
    BooleanAggregate distinct();

    @Override
    BooleanAggregate filter(Condition condition);

    @Override
    BooleanAggregate orderBy(SortField<?>... sortFields);

    @Override
    BooleanAggregate withinGroup(SortField<?>... sortFields);

    @Override
    Condition over();

    @Override
    Condition over(WindowSpec window);

    @Override
    Condition over(WindowDefinition window);
}
