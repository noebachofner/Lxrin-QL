package ch.lxrin.ql.dsl;

import ch.lxrin.ql.render.Identifiers;
import ch.lxrin.ql.render.QueryPart;
import ch.lxrin.ql.types.DataType;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Typed definitions of functions, aggregates and operators that are not in
 * the catalog: extensions such as {@code pg_trgm} or PostGIS, or your own
 * database functions. Define them once as constants; no library change is
 * needed.
 *
 * <pre>{@code
 * public final class Pg {
 *     public static final Function2<String, String, Double> SIMILARITY =
 *             Routines.function("similarity", SqlTypes.TEXT, SqlTypes.TEXT, SqlTypes.FLOAT8);
 *     public static final BinaryOperator<String, String, Double> DISTANCE =
 *             Routines.operator("<->", SqlTypes.TEXT, SqlTypes.TEXT, SqlTypes.FLOAT8);
 * }
 *
 * select(USERS.NAME, Pg.SIMILARITY.call(USERS.NAME, "ada").as("score")).from(USERS)
 * }</pre>
 *
 * <p>Names must be identifiers (optionally schema-qualified); operator
 * symbols may only contain PostgreSQL operator characters. Both are checked
 * when the definition is created.</p>
 */
public final class Routines {

    private static final Pattern OPERATOR = Pattern.compile("[+\\-*/<>=~!@#%^&|`?]{1,63}");

    private Routines() {}

    /** A function without arguments. */
    public static <R> Function0<R> function(String name, DataType<R> returns) {
        String n = Identifiers.requireFunctionName(name);
        Objects.requireNonNull(returns, "returns");
        return () -> Fields.of(returns, Ops.call(n));
    }

    /** A function with one argument. */
    public static <A, R> Function1<A, R> function(String name, DataType<A> a, DataType<R> returns) {
        String n = Identifiers.requireFunctionName(name);
        Objects.requireNonNull(a, "a");
        return new Function1<>(n, a, returns);
    }

    /** A function with two arguments. */
    public static <A, B, R> Function2<A, B, R> function(String name, DataType<A> a, DataType<B> b, DataType<R> returns) {
        return new Function2<>(Identifiers.requireFunctionName(name), a, b, returns);
    }

    /** A function with three arguments. */
    public static <A, B, C, R> Function3<A, B, C, R> function(String name, DataType<A> a, DataType<B> b, DataType<C> c,
                                                              DataType<R> returns) {
        return new Function3<>(Identifiers.requireFunctionName(name), a, b, c, returns);
    }

    /** A function with any number of arguments of any type. */
    public static <R> VarargsFunction<R> varargsFunction(String name, DataType<R> returns) {
        return new VarargsFunction<>(Identifiers.requireFunctionName(name), returns);
    }

    /** An aggregate with one argument; the result supports {@code filter}, {@code orderBy} and {@code over}. */
    public static <A, R> Aggregate1<A, R> aggregate(String name, DataType<A> a, DataType<R> returns) {
        return new Aggregate1<>(Identifiers.requireFunctionName(name), a, returns);
    }

    /** A binary operator, rendered as {@code (left op right)}. */
    public static <L, R, T> BinaryOperator<L, R, T> operator(String symbol, DataType<L> left, DataType<R> right, DataType<T> result) {
        if (symbol == null || !OPERATOR.matcher(symbol).matches()) throw new IllegalArgumentException("invalid operator: " + symbol);
        if (symbol.contains("?")) throw new IllegalArgumentException("operators with '?' clash with JDBC placeholders: " + symbol);
        return new BinaryOperator<>(symbol, left, right, result);
    }

    /** A binary operator returning a boolean, used as a condition. */
    public static <L, R> ConditionOperator<L, R> conditionOperator(String symbol, DataType<L> left, DataType<R> right) {
        if (symbol == null || !OPERATOR.matcher(symbol).matches() || symbol.contains("?")) {
            throw new IllegalArgumentException("invalid operator: " + symbol);
        }
        return new ConditionOperator<>(symbol, left, right);
    }

    // -------------------------------------------------------------------------
    // Definition types
    // -------------------------------------------------------------------------

    /**
     * A function without arguments.
     *
     * @param <R> the result type
     */
    @FunctionalInterface
    public interface Function0<R> {
        /** Returns the call {@code name()}. */
        Field<R> call();
    }

    /**
     * A function with one argument.
     *
     * @param <A> the argument type
     * @param <R> the result type
     */
    public static final class Function1<A, R> {
        private final String name;
        private final DataType<A> a;
        private final DataType<R> returns;

        Function1(String name, DataType<A> a, DataType<R> returns) {
            this.name = name;
            this.a = a;
            this.returns = Objects.requireNonNull(returns, "returns");
        }

        /** {@code name(arg)} */
        public Field<R> call(Field<A> arg) {
            return Fields.of(returns, Ops.call(name, Ops.field(arg)));
        }

        /** {@code name(?)} */
        public Field<R> call(A arg) {
            return call(Values.param(arg, a));
        }
    }

    /**
     * A function with two arguments.
     *
     * @param <A> first argument type
     * @param <B> second argument type
     * @param <R> result type
     */
    public static final class Function2<A, B, R> {
        private final String name;
        private final DataType<A> a;
        private final DataType<B> b;
        private final DataType<R> returns;

        Function2(String name, DataType<A> a, DataType<B> b, DataType<R> returns) {
            this.name = name;
            this.a = Objects.requireNonNull(a, "a");
            this.b = Objects.requireNonNull(b, "b");
            this.returns = Objects.requireNonNull(returns, "returns");
        }

        /** {@code name(a, b)} */
        public Field<R> call(Field<A> first, Field<B> second) {
            return Fields.of(returns, Ops.call(name, Ops.field(first), Ops.field(second)));
        }

        /** {@code name(a, ?)} */
        public Field<R> call(Field<A> first, B second) {
            return call(first, Values.param(second, b));
        }

        /** {@code name(?, ?)} */
        public Field<R> call(A first, B second) {
            return call(Values.param(first, a), Values.param(second, b));
        }
    }

    /**
     * A function with three arguments.
     *
     * @param <A> first argument type
     * @param <B> second argument type
     * @param <C> third argument type
     * @param <R> result type
     */
    public static final class Function3<A, B, C, R> {
        private final String name;
        private final DataType<A> a;
        private final DataType<B> b;
        private final DataType<C> c;
        private final DataType<R> returns;

        Function3(String name, DataType<A> a, DataType<B> b, DataType<C> c, DataType<R> returns) {
            this.name = name;
            this.a = Objects.requireNonNull(a, "a");
            this.b = Objects.requireNonNull(b, "b");
            this.c = Objects.requireNonNull(c, "c");
            this.returns = Objects.requireNonNull(returns, "returns");
        }

        /** {@code name(a, b, c)} */
        public Field<R> call(Field<A> first, Field<B> second, Field<C> third) {
            return Fields.of(returns, Ops.call(name, Ops.field(first), Ops.field(second), Ops.field(third)));
        }

        /** {@code name(?, ?, ?)} */
        public Field<R> call(A first, B second, C third) {
            return call(Values.param(first, a), Values.param(second, b), Values.param(third, c));
        }
    }

    /**
     * A function with any number of arguments.
     *
     * @param <R> result type
     */
    public static final class VarargsFunction<R> {
        private final String name;
        private final DataType<R> returns;

        VarargsFunction(String name, DataType<R> returns) {
            this.name = name;
            this.returns = Objects.requireNonNull(returns, "returns");
        }

        /** {@code name(args...)} */
        public Field<R> call(Field<?>... args) {
            QueryPart[] parts = new QueryPart[args.length];
            for (int i = 0; i < args.length; i++) parts[i] = Ops.field(args[i]);
            return Fields.of(returns, Ops.call(name, parts));
        }
    }

    /**
     * An aggregate with one argument.
     *
     * @param <A> argument type
     * @param <R> result type
     */
    public static final class Aggregate1<A, R> {
        private final String name;
        private final DataType<A> a;
        private final DataType<R> returns;

        Aggregate1(String name, DataType<A> a, DataType<R> returns) {
            this.name = name;
            this.a = Objects.requireNonNull(a, "a");
            this.returns = Objects.requireNonNull(returns, "returns");
        }

        /** {@code name(arg)} */
        public AggregateFunction<R> call(Field<A> arg) {
            return Aggregates.sameKind(name, returns, Ops.field(arg));
        }

        /** Returns the argument type. */
        public DataType<A> argumentType() {
            return a;
        }
    }

    /**
     * A binary operator.
     *
     * @param <L> left type
     * @param <R> right type
     * @param <T> result type
     */
    public static final class BinaryOperator<L, R, T> {
        private final String symbol;
        private final DataType<R> right;
        private final DataType<T> result;

        BinaryOperator(String symbol, DataType<L> left, DataType<R> right, DataType<T> result) {
            this.symbol = symbol;
            Objects.requireNonNull(left, "left");
            this.right = Objects.requireNonNull(right, "right");
            this.result = Objects.requireNonNull(result, "result");
        }

        /** {@code (left op right)} */
        public Field<T> apply(Field<L> l, Field<R> r) {
            return Fields.of(result, Ops.binary(Ops.field(l), symbol, Ops.field(r)));
        }

        /** {@code (left op ?)} */
        public Field<T> apply(Field<L> l, R r) {
            return apply(l, Values.param(r, right));
        }
    }

    /**
     * A binary operator that returns a boolean.
     *
     * @param <L> left type
     * @param <R> right type
     */
    public static final class ConditionOperator<L, R> {
        private final String symbol;
        private final DataType<R> right;

        ConditionOperator(String symbol, DataType<L> left, DataType<R> right) {
            this.symbol = symbol;
            Objects.requireNonNull(left, "left");
            this.right = Objects.requireNonNull(right, "right");
        }

        /** {@code left op right} */
        public Condition apply(Field<L> l, Field<R> r) {
            return Ops.compare(l, symbol, r);
        }

        /** {@code left op ?} */
        public Condition apply(Field<L> l, R r) {
            return apply(l, Values.param(r, right));
        }
    }
}
