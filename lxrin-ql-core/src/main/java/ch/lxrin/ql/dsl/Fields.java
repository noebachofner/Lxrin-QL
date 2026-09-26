package ch.lxrin.ql.dsl;

import ch.lxrin.ql.render.Identifiers;
import ch.lxrin.ql.render.QueryPart;
import ch.lxrin.ql.render.RenderContext;
import ch.lxrin.ql.types.DataType;
import ch.lxrin.ql.types.Kind;
import ch.lxrin.ql.types.SqlTypes;

import java.util.Objects;

/**
 * Creates typed fields from query parts. The kind of the {@link DataType}
 * decides which interface the result implements, so for example a
 * {@code text} expression is always a {@link StringField}.
 *
 * <p>This is a building block for function definitions and library
 * extensions; application code uses the DSL, {@link Routines} or
 * {@link Sql}.</p>
 */
public final class Fields {

    static final Condition NO_CONDITION = new ConditionExpr(ctx -> ctx.append("TRUE"), null, true);

    private Fields() {}

    /** Creates a field of the given type; the result implements the interface that matches the type's kind. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> Field<T> of(DataType<T> type, QueryPart body) {
        return (Field<T>) create((DataType) type, body, null);
    }

    /** Creates a {@code text} field. */
    public static StringField string(QueryPart body) {
        return new StringExpr(SqlTypes.TEXT, body, null);
    }

    /** Creates a text field of the given text type. */
    public static StringField string(DataType<String> type, QueryPart body) {
        return new StringExpr(type, body, null);
    }

    /** Creates a numeric field. */
    public static <N extends Number> NumberField<N> number(DataType<N> type, QueryPart body) {
        return new NumberExpr<>(type, body, null);
    }

    /** Creates a temporal field. */
    public static <T> TemporalField<T> temporal(DataType<T> type, QueryPart body) {
        return new TemporalExpr<>(type, body, null);
    }

    /** Creates a JSON field. */
    public static <T> JsonField<T> json(DataType<T> type, QueryPart body) {
        return new JsonExpr<>(type, body, null);
    }

    /** Creates an array field. */
    public static <E> ArrayField<E> array(DataType<E[]> type, QueryPart body) {
        return new ArrayExpr<>(type, body, null);
    }

    /** Creates a range field of the given range type. */
    public static RangeField range(DataType<String> type, QueryPart body) {
        return new RangeExpr(type, body, null);
    }

    /** Creates a {@code tsvector} field. */
    public static TsVectorField tsvector(QueryPart body) {
        return new TsVectorExpr(SqlTypes.TSVECTOR, body, null);
    }

    /** Returns {@code true} for the built-in range types, which are read and written as text. */
    public static boolean isRange(DataType<?> type) {
        return type.kind() == Kind.OTHER && type.javaType() == String.class && type.sqlName().endsWith("range")
                && !type.sqlName().endsWith("multirange");
    }

    /** Returns {@code true} for {@code tsvector}. */
    public static boolean isTsVector(DataType<?> type) {
        return type.kind() == Kind.OTHER && type.javaType() == String.class && "tsvector".equals(type.sqlName());
    }

    /**
     * Creates a condition.
     *
     * @param atomic {@code true} if the SQL needs no parentheses when used as an operand
     *               (function calls, parenthesised expressions)
     */
    public static Condition condition(QueryPart body, boolean atomic) {
        return new ConditionExpr(body, null, atomic);
    }

    /**
     * Returns the expression of a field without its alias, for places where an
     * alias cannot be referenced (e.g. {@code WHERE}).
     */
    public static QueryPart unaliased(Field<?> field) {
        if (field instanceof AbstractField && ((AbstractField<?>) field).alias != null) {
            QueryPart body = ((AbstractField<?>) field).body;
            return ctx -> ctx.nested(body);
        }
        return field;
    }

    /** Returns {@code field AS alias}. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static <T> Field<T> alias(Field<T> field, String alias) {
        Identifiers.requireName(alias);
        QueryPart body = field instanceof AbstractField ? ((AbstractField<?>) field).body : field;
        return (Field<T>) create((DataType) field.type(), body, alias);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static AbstractField<?> create(DataType type, QueryPart body, String alias) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(body, "body");
        Class<?> javaType = type.javaType();
        Kind kind = type.kind();
        if (kind == Kind.STRING && javaType == String.class) return new StringExpr(type, body, alias);
        if (kind == Kind.NUMBER && Number.class.isAssignableFrom(javaType)) return new NumberExpr(type, body, alias);
        if (kind == Kind.TEMPORAL) return new TemporalExpr(type, body, alias);
        if (kind == Kind.JSON) return new JsonExpr(type, body, alias);
        if (kind == Kind.ARRAY) return new ArrayExpr(type, body, alias);
        if (kind == Kind.BOOLEAN && javaType == Boolean.class) return new ConditionExpr(body, alias, true);
        if (isRange(type)) return new RangeExpr(type, body, alias);
        if (isTsVector(type)) return new TsVectorExpr(type, body, alias);
        return new GenericExpr(type, body, alias);
    }

    // -------------------------------------------------------------------------
    // Implementations
    // -------------------------------------------------------------------------

    abstract static class AbstractField<T> implements Field<T> {
        final DataType<T> type;
        final QueryPart body;
        final String alias;

        AbstractField(DataType<T> type, QueryPart body, String alias) {
            this.type = type;
            this.body = body;
            this.alias = alias;
        }

        @Override
        public DataType<T> type() {
            return type;
        }

        @Override
        public String name() {
            return alias;
        }

        @Override
        public void render(RenderContext ctx) {
            if (alias == null) {
                ctx.nested(body);
            } else if (ctx.declareAliases()) {
                ctx.nested(body).append(" AS ").identifier(alias);
            } else {
                ctx.identifier(alias);
            }
        }

        @Override
        public String toString() {
            RenderContext ctx = new RenderContext();
            ctx.visit(this);
            return ctx.sql();
        }
    }

    static final class GenericExpr<T> extends AbstractField<T> {
        GenericExpr(DataType<T> type, QueryPart body, String alias) {
            super(type, body, alias);
        }
    }

    static final class StringExpr extends AbstractField<String> implements StringField {
        StringExpr(DataType<String> type, QueryPart body, String alias) {
            super(type, body, alias);
        }
    }

    static final class NumberExpr<N extends Number> extends AbstractField<N> implements NumberField<N> {
        NumberExpr(DataType<N> type, QueryPart body, String alias) {
            super(type, body, alias);
        }
    }

    static final class TemporalExpr<T> extends AbstractField<T> implements TemporalField<T> {
        TemporalExpr(DataType<T> type, QueryPart body, String alias) {
            super(type, body, alias);
        }
    }

    static final class JsonExpr<T> extends AbstractField<T> implements JsonField<T> {
        JsonExpr(DataType<T> type, QueryPart body, String alias) {
            super(type, body, alias);
        }
    }

    static final class ArrayExpr<E> extends AbstractField<E[]> implements ArrayField<E> {
        ArrayExpr(DataType<E[]> type, QueryPart body, String alias) {
            super(type, body, alias);
        }
    }

    static final class RangeExpr extends AbstractField<String> implements RangeField {
        RangeExpr(DataType<String> type, QueryPart body, String alias) {
            super(type, body, alias);
        }
    }

    static final class TsVectorExpr extends AbstractField<String> implements TsVectorField {
        TsVectorExpr(DataType<String> type, QueryPart body, String alias) {
            super(type, body, alias);
        }
    }

    static final class ConditionExpr extends AbstractField<Boolean> implements Condition {
        private final boolean atomic;
        final String junction;
        final java.util.List<Condition> parts;

        ConditionExpr(QueryPart body, String alias, boolean atomic) {
            this(body, alias, atomic, null, null);
        }

        ConditionExpr(QueryPart body, String alias, boolean atomic, String junction, java.util.List<Condition> parts) {
            super(SqlTypes.BOOL, body, alias);
            this.atomic = atomic;
            this.junction = junction;
            this.parts = parts;
        }

        boolean atomic() {
            return atomic || alias != null;
        }

        @Override
        public DataType<Boolean> type() {
            return SqlTypes.BOOL;
        }
    }
}
