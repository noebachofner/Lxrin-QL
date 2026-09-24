package ch.lxrin.ql.dsl;

/**
 * A numeric expression. Arithmetic keeps the Java type {@code N}; cast with
 * {@link #cast(ch.lxrin.ql.types.DataType)} when the SQL result type differs.
 *
 * @param <N> the Java number type
 */
public interface NumberField<N extends Number> extends Field<N> {

    /** {@code (this + ?)} */
    default NumberField<N> plus(N value) { return arithmetic("+", Ops.value(this, value, "plus")); }

    /** {@code (this + other)} */
    default NumberField<N> plus(Field<? extends Number> other) { return arithmetic("+", Ops.field(other)); }

    /** {@code (this - ?)} */
    default NumberField<N> minus(N value) { return arithmetic("-", Ops.value(this, value, "minus")); }

    /** {@code (this - other)} */
    default NumberField<N> minus(Field<? extends Number> other) { return arithmetic("-", Ops.field(other)); }

    /** {@code (this * ?)} */
    default NumberField<N> times(N value) { return arithmetic("*", Ops.value(this, value, "times")); }

    /** {@code (this * other)} */
    default NumberField<N> times(Field<? extends Number> other) { return arithmetic("*", Ops.field(other)); }

    /** {@code (this / ?)} – integer division for integer types. */
    default NumberField<N> divide(N value) { return arithmetic("/", Ops.value(this, value, "divide")); }

    /** {@code (this / other)} */
    default NumberField<N> divide(Field<? extends Number> other) { return arithmetic("/", Ops.field(other)); }

    /** {@code (this % ?)} */
    default NumberField<N> mod(N value) { return arithmetic("%", Ops.value(this, value, "mod")); }

    /** {@code (this % other)} */
    default NumberField<N> mod(Field<? extends Number> other) { return arithmetic("%", Ops.field(other)); }

    /** {@code (- this)} */
    default NumberField<N> neg() {
        NumberField<N> self = this;
        return Fields.number(type(), ctx -> ctx.append("(- ").visit(self).append(')'));
    }

    /** {@code abs(this)} */
    default NumberField<N> abs() { return Fields.number(type(), Ops.call("abs", this)); }

    private NumberField<N> arithmetic(String operator, ch.lxrin.ql.render.QueryPart right) {
        return Fields.number(type(), Ops.binary(this, operator, right));
    }

    @Override
    @SuppressWarnings("unchecked")
    default NumberField<N> as(String alias) { return (NumberField<N>) Fields.alias(this, alias); }

    @Override
    @SuppressWarnings("unchecked")
    default NumberField<N> coalesce(N fallback) { return (NumberField<N>) Field.super.coalesce(fallback); }

    @Override
    @SuppressWarnings("unchecked")
    default NumberField<N> coalesce(Field<N> other) { return (NumberField<N>) Field.super.coalesce(other); }
}
