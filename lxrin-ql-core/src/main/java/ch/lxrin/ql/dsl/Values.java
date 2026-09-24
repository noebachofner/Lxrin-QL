package ch.lxrin.ql.dsl;

import ch.lxrin.ql.types.DataType;
import ch.lxrin.ql.types.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Bind parameters and inline literals as fields. All methods are also
 * available through {@code import static ch.lxrin.ql.dsl.Dsl.*}.
 */
public class Values {

    /** Static members only; extended by {@link Dsl}. */
    protected Values() {}

    /** A bind parameter of the given type; {@code null} is allowed. */
    public static <T> Field<T> param(T value, DataType<T> type) {
        T checked = type.cast(value);
        return Fields.of(type, ctx -> ctx.bind(type, checked));
    }

    /** A {@code text} bind parameter. */
    public static StringField param(String value) {
        return Fields.string(ctx -> ctx.bind(SqlTypes.TEXT, value));
    }

    /** An {@code int4} bind parameter. */
    public static NumberField<Integer> param(Integer value) {
        return Fields.number(SqlTypes.INT4, ctx -> ctx.bind(SqlTypes.INT4, value));
    }

    /** An {@code int8} bind parameter. */
    public static NumberField<Long> param(Long value) {
        return Fields.number(SqlTypes.INT8, ctx -> ctx.bind(SqlTypes.INT8, value));
    }

    /** A {@code numeric} bind parameter. */
    public static NumberField<BigDecimal> param(BigDecimal value) {
        return Fields.number(SqlTypes.NUMERIC, ctx -> ctx.bind(SqlTypes.NUMERIC, value));
    }

    /** A {@code float8} bind parameter. */
    public static NumberField<Double> param(Double value) {
        return Fields.number(SqlTypes.FLOAT8, ctx -> ctx.bind(SqlTypes.FLOAT8, value));
    }

    /** A {@code boolean} bind parameter. */
    public static Condition param(Boolean value) {
        return Fields.condition(ctx -> ctx.bind(SqlTypes.BOOL, value), true);
    }

    /** A {@code uuid} bind parameter. */
    public static Field<UUID> param(UUID value) {
        return param(value, SqlTypes.UUID);
    }

    /** A {@code timestamptz} bind parameter. */
    public static TemporalField<Instant> param(Instant value) {
        return Fields.temporal(SqlTypes.TIMESTAMPTZ, ctx -> ctx.bind(SqlTypes.TIMESTAMPTZ, value));
    }

    /** A {@code date} bind parameter. */
    public static TemporalField<LocalDate> param(LocalDate value) {
        return Fields.temporal(SqlTypes.DATE, ctx -> ctx.bind(SqlTypes.DATE, value));
    }

    /** A {@code timestamp} bind parameter. */
    public static TemporalField<LocalDateTime> param(LocalDateTime value) {
        return Fields.temporal(SqlTypes.TIMESTAMP, ctx -> ctx.bind(SqlTypes.TIMESTAMP, value));
    }

    /**
     * A bind parameter whose type is derived from the value's class
     * ({@link SqlTypes#forClass(Class)}).
     *
     * @throws IllegalArgumentException for {@code null} or classes without a default type
     */
    @SuppressWarnings("unchecked")
    public static <T> Field<T> value(T value) {
        if (value == null) throw new IllegalArgumentException("value must not be null; use param(null, type)");
        return param(value, SqlTypes.forClass((Class<T>) value.getClass()));
    }

    /**
     * An escaped literal written into the SQL text instead of a bind
     * parameter, e.g. for constants that should be visible to the planner.
     */
    public static <T> Field<T> inline(T value, DataType<T> type) {
        String literal = value == null ? "NULL" : type.access().literal(type.cast(value));
        return Fields.of(type, ctx -> ctx.append(literal));
    }

    /** An escaped text literal. */
    public static StringField inline(String value) {
        return (StringField) inline(value, SqlTypes.TEXT);
    }

    /** An integer literal. */
    @SuppressWarnings("unchecked")
    public static NumberField<Integer> inline(int value) {
        return (NumberField<Integer>) inline(value, SqlTypes.INT4);
    }

    /** A bigint literal. */
    @SuppressWarnings("unchecked")
    public static NumberField<Long> inline(long value) {
        return (NumberField<Long>) inline(value, SqlTypes.INT8);
    }

    /** A boolean literal. */
    public static Condition inline(boolean value) {
        return (Condition) inline(value, SqlTypes.BOOL);
    }

    /** A typed {@code NULL}: {@code CAST(NULL AS type)}. */
    public static <T> Field<T> nullValue(DataType<T> type) {
        return Fields.of(type, ctx -> ctx.append("CAST(NULL AS ").append(type.sqlName()).append(')'));
    }
}
