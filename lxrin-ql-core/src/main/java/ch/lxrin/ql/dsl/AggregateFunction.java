package ch.lxrin.ql.dsl;

/**
 * An aggregate function call with its modifiers. Each modifier returns a new
 * instance; {@code over(..)} turns the aggregate into a window function.
 *
 * <pre>{@code
 * count().filter(ORDERS.STATUS.eq("PAID"))                     // count(*) FILTER (WHERE ...)
 * stringAgg(USERS.NAME, ", ").orderBy(USERS.NAME.asc())        // string_agg(... ORDER BY ...)
 * sum(ORDERS.TOTAL).over(partitionBy(ORDERS.USER_ID))          // window function
 * }</pre>
 *
 * @param <T> the result type
 */
public interface AggregateFunction<T> extends Field<T> {

    /** {@code f(DISTINCT ...)} */
    AggregateFunction<T> distinct();

    /** {@code f(...) FILTER (WHERE condition)} */
    AggregateFunction<T> filter(Condition condition);

    /** {@code f(... ORDER BY ...)} – ordered aggregates such as {@code string_agg}. */
    AggregateFunction<T> orderBy(SortField<?>... sortFields);

    /** {@code f(...) WITHIN GROUP (ORDER BY ...)} – ordered-set aggregates such as {@code percentile_cont}. */
    AggregateFunction<T> withinGroup(SortField<?>... sortFields);

    /** {@code f(...) OVER ()} */
    Field<T> over();

    /** {@code f(...) OVER (spec)} */
    Field<T> over(WindowSpec window);

    /** {@code f(...) OVER name} */
    Field<T> over(WindowDefinition window);
}
