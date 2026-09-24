package ch.lxrin.ql.dsl;

import ch.lxrin.ql.render.QueryPart;
import ch.lxrin.ql.types.DataType;
import ch.lxrin.ql.types.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The typed PostgreSQL function catalog. All methods are also available
 * through {@code import static ch.lxrin.ql.dsl.Dsl.*}.
 *
 * <p>Constants such as date parts, formats, separators and JSON keys are
 * written as escaped literals (they are part of the query's shape, e.g. for
 * {@code GROUP BY}); values are bound.</p>
 */
public class Functions extends Values {

    /** Static members only; extended by {@link Dsl}. */
    protected Functions() {}

    // =========================================================================
    // Aggregates
    // =========================================================================

    /** {@code count(*)} */
    public static NumberAggregate<Long> count() {
        return Aggregates.number("count", SqlTypes.INT8, c -> c.append('*'));
    }

    /** {@code count(field)} – counts non-null values. */
    public static NumberAggregate<Long> count(Field<?> field) {
        return Aggregates.number("count", SqlTypes.INT8, field);
    }

    /** {@code count(DISTINCT field)} */
    public static NumberAggregate<Long> countDistinct(Field<?> field) {
        return count(field).distinct();
    }

    /** {@code sum(field)}; the result is read as {@code BigDecimal} so it cannot overflow. */
    public static NumberAggregate<BigDecimal> sum(Field<? extends Number> field) {
        return Aggregates.number("sum", SqlTypes.NUMERIC, field);
    }

    /** {@code avg(field)} */
    public static NumberAggregate<BigDecimal> avg(Field<? extends Number> field) {
        return Aggregates.number("avg", SqlTypes.NUMERIC, field);
    }

    /** {@code min(field)}; the result keeps the field's type family. */
    public static <T> AggregateFunction<T> min(Field<T> field) {
        return Aggregates.sameKind("min", field.type(), field);
    }

    /** {@code min(field)} for numbers. */
    public static <N extends Number> NumberAggregate<N> min(NumberField<N> field) {
        return Aggregates.number("min", field.type(), field);
    }

    /** {@code min(field)} for text. */
    public static StringAggregate min(StringField field) {
        return Aggregates.string("min", field.type(), field);
    }

    /** {@code max(field)}; the result keeps the field's type family. */
    public static <T> AggregateFunction<T> max(Field<T> field) {
        return Aggregates.sameKind("max", field.type(), field);
    }

    /** {@code max(field)} for numbers. */
    public static <N extends Number> NumberAggregate<N> max(NumberField<N> field) {
        return Aggregates.number("max", field.type(), field);
    }

    /** {@code max(field)} for text. */
    public static StringAggregate max(StringField field) {
        return Aggregates.string("max", field.type(), field);
    }

    // =========================================================================
    // Window functions
    // =========================================================================

    /** {@code row_number()} – use with {@code over(..)}. */
    public static WindowFunction<NumberField<Long>> rowNumber() {
        return window("row_number", SqlTypes.INT8);
    }

    /** {@code rank()} */
    public static WindowFunction<NumberField<Long>> rank() {
        return window("rank", SqlTypes.INT8);
    }

    /** {@code dense_rank()} */
    public static WindowFunction<NumberField<Long>> denseRank() {
        return window("dense_rank", SqlTypes.INT8);
    }

    static <N extends Number> WindowFunction<NumberField<N>> window(String name, DataType<N> type, QueryPart... args) {
        QueryPart call = Ops.call(name, args);
        return new WindowFunction<>(call, part -> Fields.number(type, part));
    }

    // =========================================================================
    // Conditional expressions
    // =========================================================================

    /** {@code COALESCE(a, b, ...)} */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> Field<T> coalesce(Field<T> first, Field<T>... more) {
        QueryPart[] args = new QueryPart[more.length + 1];
        args[0] = first;
        System.arraycopy(more, 0, args, 1, more.length);
        return Fields.of(first.type(), Ops.call("COALESCE", args));
    }

    /** {@code EXISTS (SELECT ...)} */
    public static Condition exists(AbstractSelect<?, ?> query) {
        return Fields.condition(ctx -> ctx.append("EXISTS ").visit(query), true);
    }

    /** {@code NOT EXISTS (SELECT ...)} */
    public static Condition notExists(AbstractSelect<?, ?> query) {
        return Fields.condition(ctx -> ctx.append("NOT EXISTS ").visit(query), true);
    }

    // =========================================================================
    // Date and time
    // =========================================================================

    /** {@code now()} – the start of the current transaction. */
    public static TemporalField<Instant> now() {
        return Fields.temporal(SqlTypes.TIMESTAMPTZ, Ops.call("now"));
    }

    // =========================================================================
    // GROUP BY elements
    // =========================================================================

    /** {@code ROLLUP (fields)} */
    public static GroupingElement rollup(Field<?>... fields) {
        List<Field<?>> list = List.of(fields);
        return ctx -> ctx.append("ROLLUP (").visitAll(list, ", ").append(')');
    }

    /** {@code CUBE (fields)} */
    public static GroupingElement cube(Field<?>... fields) {
        List<Field<?>> list = List.of(fields);
        return ctx -> ctx.append("CUBE (").visitAll(list, ", ").append(')');
    }

    /** One set for {@link #groupingSets}: {@code (a, b)} or {@code ()}. */
    public static GroupingElement groupingSet(Field<?>... fields) {
        List<Field<?>> list = List.of(fields);
        return ctx -> ctx.append('(').visitAll(list, ", ").append(')');
    }

    /** {@code GROUPING SETS ((a, b), (a), ())} */
    public static GroupingElement groupingSets(GroupingElement... sets) {
        List<GroupingElement> list = List.of(sets);
        return ctx -> ctx.append("GROUPING SETS (").visitAll(list, ", ").append(')');
    }

    /** {@code GROUPING(fields)} – which fields are aggregated away in a grouping set. */
    public static NumberField<Integer> grouping(Field<?>... fields) {
        return Fields.number(SqlTypes.INT4, Ops.call("GROUPING", fields));
    }
}
