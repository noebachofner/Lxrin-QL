package ch.lxrin.ql.dsl;

import ch.lxrin.ql.types.DataType;
import ch.lxrin.ql.types.SqlTypes;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;

/**
 * The {@code b} of {@code createContribution(.., (c, b) -> ..)}: creates
 * explicit, typed bind parameters. It is optional – plain values are bound
 * automatically ({@code USERS.EMAIL.eq(email)} and
 * {@code USERS.EMAIL.eq(b.setString(email))} render the same SQL) – but it
 * makes the parameter visible where a field is required, e.g.
 * {@code b.setInstant(since).minus(..)} or {@code coalesce(.., b.setString(..))}.
 *
 * <p>{@code null} is rejected by every {@code setX(..)}; use
 * {@link #setNull(DataType)} for an explicit {@code NULL}.</p>
 */
public final class Binds {

    static final Binds INSTANCE = new Binds();

    private Binds() {}

    private static <T> T require(T value, String method) {
        if (value == null) throw new IllegalArgumentException(method + "(null) – use setNull(type) for an explicit NULL");
        return value;
    }

    /** A {@code text} parameter. */
    public StringField setString(String value) { return Values.param(require(value, "setString")); }

    /** An {@code int2} parameter. */
    public NumberField<Short> setShort(Short value) { return (NumberField<Short>) Values.param(require(value, "setShort"), SqlTypes.INT2); }

    /** An {@code int4} parameter. */
    public NumberField<Integer> setInt(Integer value) { return Values.param(require(value, "setInt")); }

    /** An {@code int8} parameter. */
    public NumberField<Long> setLong(Long value) { return Values.param(require(value, "setLong")); }

    /** A {@code numeric} parameter. */
    public NumberField<BigDecimal> setBigDecimal(BigDecimal value) { return Values.param(require(value, "setBigDecimal")); }

    /** A {@code float8} parameter. */
    public NumberField<Double> setDouble(Double value) { return Values.param(require(value, "setDouble")); }

    /** A {@code float4} parameter. */
    public NumberField<Float> setFloat(Float value) { return (NumberField<Float>) Values.param(require(value, "setFloat"), SqlTypes.FLOAT4); }

    /** A {@code bool} parameter. */
    public Condition setBoolean(Boolean value) { return Values.param(require(value, "setBoolean")); }

    /** A {@code uuid} parameter. */
    public Field<UUID> setUuid(UUID value) { return Values.param(require(value, "setUuid")); }

    /** A {@code timestamptz} parameter. */
    public TemporalField<Instant> setInstant(Instant value) { return Values.param(require(value, "setInstant")); }

    /** A {@code date} parameter. */
    public TemporalField<LocalDate> setLocalDate(LocalDate value) { return Values.param(require(value, "setLocalDate")); }

    /** A {@code timestamp} parameter. */
    public TemporalField<LocalDateTime> setLocalDateTime(LocalDateTime value) { return Values.param(require(value, "setLocalDateTime")); }

    /** A {@code time} parameter. */
    public TemporalField<LocalTime> setLocalTime(LocalTime value) {
        return (TemporalField<LocalTime>) Values.param(require(value, "setLocalTime"), SqlTypes.TIME);
    }

    /** An {@code interval} parameter. */
    public Field<Duration> setDuration(Duration value) { return Values.param(require(value, "setDuration"), SqlTypes.INTERVAL); }

    /** A {@code bytea} parameter. */
    public Field<byte[]> setBytes(byte[] value) { return Values.param(require(value, "setBytes"), SqlTypes.BYTEA); }

    /** A parameter of any type, e.g. an enum or a value object: {@code b.set(role, AppRole.TYPE)}. */
    public <T> Field<T> set(T value, DataType<T> type) { return Values.param(require(value, "set"), type); }

    /** A parameter with the type of a column or field: {@code b.set(USERS.ID, id)}. */
    public <T> Field<T> set(Field<T> like, T value) { return Values.param(require(value, "set"), like.type()); }

    /** An explicit {@code NULL} of the given type. */
    public <T> Field<T> setNull(DataType<T> type) { return Values.nullValue(type); }

    /**
     * A list for {@code in(..)} and {@code notIn(..)}, bound as one array
     * parameter with the type of the field it is compared with. An empty list
     * matches nothing ({@code notIn}: everything).
     */
    public <T> BindList<T> setList(Collection<? extends T> values) {
        if (values == null) throw new IllegalArgumentException("setList(null) – pass an empty list to match nothing");
        return new BindList<>(values);
    }

    /** A list for {@code in(..)} from values. */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public final <T> BindList<T> setList(T... values) {
        return setList(Arrays.asList(values));
    }
}
