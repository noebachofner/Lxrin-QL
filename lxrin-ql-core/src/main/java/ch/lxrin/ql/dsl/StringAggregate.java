package ch.lxrin.ql.dsl;

/** A text aggregate such as {@code string_agg} or {@code min(text)}. */
public interface StringAggregate extends AggregateFunction<String>, StringField {

    @Override
    StringAggregate distinct();

    @Override
    StringAggregate filter(Condition condition);

    @Override
    StringAggregate orderBy(SortField<?>... sortFields);

    @Override
    StringAggregate withinGroup(SortField<?>... sortFields);

    @Override
    StringField over();

    @Override
    StringField over(WindowSpec window);

    @Override
    StringField over(WindowDefinition window);
}
