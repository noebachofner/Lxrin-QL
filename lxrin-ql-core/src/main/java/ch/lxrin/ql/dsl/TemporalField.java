package ch.lxrin.ql.dsl;

import ch.lxrin.ql.types.Literals;
import ch.lxrin.ql.types.SqlTypes;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;

/**
 * A date, time or timestamp expression.
 *
 * @param <T> the Java type ({@code Instant}, {@code LocalDate}, ...)
 */
public interface TemporalField<T> extends Field<T> {

    /** {@code CAST((this + ?::interval) AS type)} – the result keeps the type of this field. */
    default TemporalField<T> plus(Duration duration) { return shift("+", duration); }

    /** {@code CAST((this - ?::interval) AS type)} */
    default TemporalField<T> minus(Duration duration) { return shift("-", duration); }

    /** {@code CAST((this + interval) AS type)} */
    default TemporalField<T> plus(Field<Duration> interval) {
        return Fields.temporal(type(), Ops.cast(Ops.binary(this, "+", Ops.field(interval)), type()));
    }

    /** {@code CAST((this - interval) AS type)} */
    default TemporalField<T> minus(Field<Duration> interval) {
        return Fields.temporal(type(), Ops.cast(Ops.binary(this, "-", Ops.field(interval)), type()));
    }

    /**
     * {@code CAST(date_trunc('part', this) AS type)}. For {@code timestamptz}
     * the session time zone applies; use {@link #truncate(DatePart, ZoneId)}
     * to make it explicit.
     */
    default TemporalField<T> truncate(DatePart part) {
        TemporalField<T> self = this;
        return Fields.temporal(type(), Ops.cast(
                ctx -> ctx.append("date_trunc(").append(Literals.quote(part.sqlName())).append(", ").visit(self).append(')'),
                type()));
    }

    /** {@code date_trunc('part', this, 'zone')} (PostgreSQL 12+). */
    default TemporalField<T> truncate(DatePart part, ZoneId zone) {
        TemporalField<T> self = this;
        return Fields.temporal(type(), ctx -> ctx.append("date_trunc(").append(Literals.quote(part.sqlName()))
                .append(", ").visit(self).append(", ").append(Literals.quote(zone.getId())).append(')'));
    }

    /** {@code EXTRACT(part FROM this)} */
    default NumberField<BigDecimal> extract(DatePart part) {
        TemporalField<T> self = this;
        return Fields.number(SqlTypes.NUMERIC,
                ctx -> ctx.append("EXTRACT(").append(part.name()).append(" FROM ").visit(self).append(')'));
    }

    private TemporalField<T> shift(String operator, Duration duration) {
        if (duration == null) throw new IllegalArgumentException("duration must not be null");
        return Fields.temporal(type(), Ops.cast(
                Ops.binary(this, operator, ctx -> ctx.append("CAST(").bind(SqlTypes.INTERVAL, duration).append(" AS interval)")),
                type()));
    }

    @Override
    @SuppressWarnings("unchecked")
    default TemporalField<T> as(String alias) { return (TemporalField<T>) Fields.alias(this, alias); }

    @Override
    @SuppressWarnings("unchecked")
    default TemporalField<T> coalesce(T fallback) { return (TemporalField<T>) Field.super.coalesce(fallback); }

    @Override
    @SuppressWarnings("unchecked")
    default TemporalField<T> coalesce(Field<T> other) { return (TemporalField<T>) Field.super.coalesce(other); }
}
