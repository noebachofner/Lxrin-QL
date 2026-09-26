package ch.lxrin.ql.dsl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A range expression ({@code daterange}, {@code tstzrange}, {@code int4range}, …),
 * read and written in its text form, e.g. {@code [2024-01-01,2025-01-01)}.
 * Generated range columns implement it; any other range expression can be
 * wrapped with {@code Conditions.range(field)}.
 */
public interface RangeField extends Field<String> {

    /** {@code this @> other} – contains the other range or element. */
    default Condition rangeContains(Field<?> rangeOrElement) { return Ops.compare(this, "@>", Ops.field(rangeOrElement)); }

    /** {@code this @> ?} – contains the instant ({@code tstzrange}). */
    default Condition rangeContains(Instant element) { return rangeContains(Values.param(Ops.require(element, "rangeContains"))); }

    /** {@code this @> ?} – contains the date ({@code daterange}). */
    default Condition rangeContains(LocalDate element) { return rangeContains(Values.param(Ops.require(element, "rangeContains"))); }

    /** {@code this @> ?} – contains the timestamp ({@code tsrange}). */
    default Condition rangeContains(LocalDateTime element) { return rangeContains(Values.param(Ops.require(element, "rangeContains"))); }

    /** {@code this @> ?} – contains the number ({@code int4range}). */
    default Condition rangeContains(Integer element) { return rangeContains(Values.param(Ops.require(element, "rangeContains"))); }

    /** {@code this @> ?} – contains the number ({@code int8range}). */
    default Condition rangeContains(Long element) { return rangeContains(Values.param(Ops.require(element, "rangeContains"))); }

    /** {@code this @> ?} – contains the number ({@code numrange}). */
    default Condition rangeContains(BigDecimal element) { return rangeContains(Values.param(Ops.require(element, "rangeContains"))); }

    /** {@code this <@ other} – is contained by the other range. */
    default Condition rangeContainedBy(Field<String> other) { return Ops.compare(this, "<@", Ops.field(other)); }

    /** {@code this && other} – the ranges overlap. */
    default Condition rangeOverlaps(Field<String> other) { return Ops.compare(this, "&&", Ops.field(other)); }

    /** {@code this << other} – strictly left of the other range. */
    default Condition strictlyLeftOf(Field<String> other) { return Ops.compare(this, "<<", Ops.field(other)); }

    /** {@code this >> other} – strictly right of the other range. */
    default Condition strictlyRightOf(Field<String> other) { return Ops.compare(this, ">>", Ops.field(other)); }

    /** {@code this &< other} – does not extend to the right of the other range. */
    default Condition notExtendsRightOf(Field<String> other) { return Ops.compare(this, "&<", Ops.field(other)); }

    /** {@code this &> other} – does not extend to the left of the other range. */
    default Condition notExtendsLeftOf(Field<String> other) { return Ops.compare(this, "&>", Ops.field(other)); }

    /** {@code this -|- other} – the ranges are adjacent. */
    default Condition adjacentTo(Field<String> other) { return Ops.compare(this, "-|-", Ops.field(other)); }

    /** {@code isempty(this)} */
    default Condition isEmpty() { return Fields.condition(Ops.call("isempty", this), true); }

    @Override
    default RangeField as(String alias) { return (RangeField) Fields.alias(this, alias); }
}
