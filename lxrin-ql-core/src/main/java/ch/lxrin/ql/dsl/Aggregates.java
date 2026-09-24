package ch.lxrin.ql.dsl;

import ch.lxrin.ql.render.Identifiers;
import ch.lxrin.ql.render.QueryPart;
import ch.lxrin.ql.render.RenderContext;
import ch.lxrin.ql.types.DataType;

import java.util.List;

/**
 * Factories for aggregate functions of every type family. Used by the
 * function catalog and by {@link Routines} for user-defined aggregates.
 */
public final class Aggregates {

    private Aggregates() {}

    /** A numeric aggregate {@code name(args)}. */
    public static <N extends Number> NumberAggregate<N> number(String name, DataType<N> type, QueryPart... args) {
        return new NumberAgg<>(type, Spec.of(name, args));
    }

    /** A text aggregate. */
    public static StringAggregate string(String name, DataType<String> type, QueryPart... args) {
        return new StringAgg(type, Spec.of(name, args));
    }

    /** A boolean aggregate. */
    public static BooleanAggregate bool(String name, QueryPart... args) {
        return new BooleanAgg(Spec.of(name, args));
    }

    /** A JSON aggregate. */
    public static <T> JsonAggregate<T> json(String name, DataType<T> type, QueryPart... args) {
        return new JsonAgg<>(type, Spec.of(name, args));
    }

    /** An array aggregate. */
    public static <E> ArrayAggregate<E> array(String name, DataType<E[]> type, QueryPart... args) {
        return new ArrayAgg<>(type, Spec.of(name, args));
    }

    /** An aggregate of any other type. */
    public static <T> AggregateFunction<T> generic(String name, DataType<T> type, QueryPart... args) {
        return new GenericAgg<>(type, Spec.of(name, args));
    }

    /** An aggregate whose type family follows {@code type} (e.g. {@code min(x)} keeps the kind of {@code x}). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> AggregateFunction<T> sameKind(String name, DataType<T> type, QueryPart... args) {
        switch (type.kind()) {
            case NUMBER:
                return (AggregateFunction<T>) number(name, (DataType) type, args);
            case STRING:
                return (AggregateFunction<T>) string(name, (DataType) type, args);
            case BOOLEAN:
                return (AggregateFunction<T>) bool(name, args);
            case JSON:
                return json(name, type, args);
            case ARRAY:
                return (AggregateFunction<T>) array(name, (DataType) type, args);
            default:
                return generic(name, type, args);
        }
    }

    // -------------------------------------------------------------------------
    // Implementation
    // -------------------------------------------------------------------------

    static final class Spec {
        final String name;
        final List<QueryPart> args;
        final boolean distinct;
        final List<SortField<?>> orderBy;
        final List<SortField<?>> withinGroup;
        final Condition filter;

        Spec(String name, List<QueryPart> args, boolean distinct, List<SortField<?>> orderBy,
             List<SortField<?>> withinGroup, Condition filter) {
            this.name = name;
            this.args = args;
            this.distinct = distinct;
            this.orderBy = orderBy;
            this.withinGroup = withinGroup;
            this.filter = filter;
        }

        static Spec of(String name, QueryPart... args) {
            return new Spec(Identifiers.requireFunctionName(name), List.of(args), false, List.of(), List.of(), null);
        }

        Spec distinct() {
            return new Spec(name, args, true, orderBy, withinGroup, filter);
        }

        Spec filter(Condition c) {
            if (c == null) throw new IllegalArgumentException("filter condition must not be null");
            return new Spec(name, args, distinct, orderBy, withinGroup, filter == null ? c : filter.and(c));
        }

        Spec orderBy(SortField<?>... s) {
            return new Spec(name, args, distinct, List.of(s), withinGroup, filter);
        }

        Spec withinGroup(SortField<?>... s) {
            return new Spec(name, args, distinct, orderBy, List.of(s), filter);
        }

        void render(RenderContext ctx) {
            ctx.append(name).append('(');
            if (distinct) ctx.append("DISTINCT ");
            ctx.visitAll(args, ", ");
            if (!orderBy.isEmpty()) ctx.append(" ORDER BY ").visitAll(orderBy, ", ");
            ctx.append(')');
            if (!withinGroup.isEmpty()) ctx.append(" WITHIN GROUP (ORDER BY ").visitAll(withinGroup, ", ").append(')');
            if (filter != null) ctx.append(" FILTER (WHERE ").visit(filter).append(')');
        }
    }

    abstract static class Base<T, A> implements Field<T> {
        final DataType<T> type;
        final Spec spec;

        Base(DataType<T> type, Spec spec) {
            this.type = type;
            this.spec = spec;
        }

        abstract A with(Spec s);

        @Override
        public DataType<T> type() {
            return type;
        }

        @Override
        public void render(RenderContext ctx) {
            spec.render(ctx);
        }

        public A distinct() {
            return with(spec.distinct());
        }

        public A filter(Condition condition) {
            return with(spec.filter(condition));
        }

        public A orderBy(SortField<?>... sortFields) {
            return with(spec.orderBy(sortFields));
        }

        public A withinGroup(SortField<?>... sortFields) {
            return with(spec.withinGroup(sortFields));
        }

        QueryPart overPart(WindowSpec window) {
            return ctx -> ctx.visit(this).append(" OVER ").visit(window);
        }

        QueryPart overPart(WindowDefinition window) {
            return ctx -> ctx.visit(this).append(" OVER ").identifier(window.name());
        }

        @Override
        public String toString() {
            RenderContext ctx = new RenderContext();
            render(ctx);
            return ctx.sql();
        }
    }

    static final class NumberAgg<N extends Number> extends Base<N, NumberAggregate<N>> implements NumberAggregate<N> {
        NumberAgg(DataType<N> type, Spec spec) {
            super(type, spec);
        }

        @Override
        NumberAggregate<N> with(Spec s) {
            return new NumberAgg<>(type, s);
        }

        @Override
        public NumberField<N> over() {
            return over(WindowSpec.empty());
        }

        @Override
        public NumberField<N> over(WindowSpec window) {
            return Fields.number(type, overPart(window));
        }

        @Override
        public NumberField<N> over(WindowDefinition window) {
            return Fields.number(type, overPart(window));
        }
    }

    static final class StringAgg extends Base<String, StringAggregate> implements StringAggregate {
        StringAgg(DataType<String> type, Spec spec) {
            super(type, spec);
        }

        @Override
        StringAggregate with(Spec s) {
            return new StringAgg(type, s);
        }

        @Override
        public StringField over() {
            return over(WindowSpec.empty());
        }

        @Override
        public StringField over(WindowSpec window) {
            return Fields.string(type, overPart(window));
        }

        @Override
        public StringField over(WindowDefinition window) {
            return Fields.string(type, overPart(window));
        }
    }

    static final class BooleanAgg extends Base<Boolean, BooleanAggregate> implements BooleanAggregate {
        BooleanAgg(Spec spec) {
            super(ch.lxrin.ql.types.SqlTypes.BOOL, spec);
        }

        @Override
        BooleanAggregate with(Spec s) {
            return new BooleanAgg(s);
        }

        @Override
        public DataType<Boolean> type() {
            return type;
        }

        @Override
        public Condition over() {
            return over(WindowSpec.empty());
        }

        @Override
        public Condition over(WindowSpec window) {
            return Fields.condition(overPart(window), true);
        }

        @Override
        public Condition over(WindowDefinition window) {
            return Fields.condition(overPart(window), true);
        }
    }

    static final class JsonAgg<T> extends Base<T, JsonAggregate<T>> implements JsonAggregate<T> {
        JsonAgg(DataType<T> type, Spec spec) {
            super(type, spec);
        }

        @Override
        JsonAggregate<T> with(Spec s) {
            return new JsonAgg<>(type, s);
        }

        @Override
        public JsonField<T> over() {
            return over(WindowSpec.empty());
        }

        @Override
        public JsonField<T> over(WindowSpec window) {
            return Fields.json(type, overPart(window));
        }

        @Override
        public JsonField<T> over(WindowDefinition window) {
            return Fields.json(type, overPart(window));
        }
    }

    static final class ArrayAgg<E> extends Base<E[], ArrayAggregate<E>> implements ArrayAggregate<E> {
        ArrayAgg(DataType<E[]> type, Spec spec) {
            super(type, spec);
        }

        @Override
        ArrayAggregate<E> with(Spec s) {
            return new ArrayAgg<>(type, s);
        }

        @Override
        public ArrayField<E> over() {
            return over(WindowSpec.empty());
        }

        @Override
        public ArrayField<E> over(WindowSpec window) {
            return Fields.array(type, overPart(window));
        }

        @Override
        public ArrayField<E> over(WindowDefinition window) {
            return Fields.array(type, overPart(window));
        }
    }

    static final class GenericAgg<T> extends Base<T, AggregateFunction<T>> implements AggregateFunction<T> {
        GenericAgg(DataType<T> type, Spec spec) {
            super(type, spec);
        }

        @Override
        AggregateFunction<T> with(Spec s) {
            return new GenericAgg<>(type, s);
        }

        @Override
        public Field<T> over() {
            return over(WindowSpec.empty());
        }

        @Override
        public Field<T> over(WindowSpec window) {
            return Fields.of(type, overPart(window));
        }

        @Override
        public Field<T> over(WindowDefinition window) {
            return Fields.of(type, overPart(window));
        }
    }
}
