package ch.lxrin.ql.dsl;

import ch.lxrin.ql.types.DataType;
import ch.lxrin.ql.types.SqlTypes;

/**
 * An array expression.
 *
 * @param <E> the element type
 */
public interface ArrayField<E> extends Field<E[]> {

    /** Returns the element type. */
    @SuppressWarnings("unchecked")
    default DataType<E> elementType() {
        return (DataType<E>) type().elementType();
    }

    /** {@code this @> ?} – contains all given elements. */
    default Condition contains(E[] elements) { return Ops.compare(this, "@>", Ops.value(this, elements, "contains")); }

    /** {@code this <@ ?} – all elements are among the given ones. */
    default Condition containedBy(E[] elements) { return Ops.compare(this, "<@", Ops.value(this, elements, "containedBy")); }

    /** {@code this && ?} – has at least one of the given elements. */
    default Condition overlaps(E[] elements) { return Ops.compare(this, "&&", Ops.value(this, elements, "overlaps")); }

    /** {@code this @> other} – contains all elements of the other array. */
    default Condition contains(Field<E[]> other) { return Ops.compare(this, "@>", Ops.field(other)); }

    /** {@code this <@ other} */
    default Condition containedBy(Field<E[]> other) { return Ops.compare(this, "<@", Ops.field(other)); }

    /** {@code this && other} */
    default Condition overlaps(Field<E[]> other) { return Ops.compare(this, "&&", Ops.field(other)); }

    /** {@code this @> ?} – contains all given elements; the same as {@link #contains(Object[])}. */
    @SuppressWarnings("unchecked")
    default Condition arrayContains(E... elements) { return Ops.compare(this, "@>", Ops.value(this, elements, "arrayContains")); }

    /** {@code this @> other} */
    default Condition arrayContains(Field<E[]> other) { return contains(other); }

    /** {@code this <@ ?} – all elements are among the given ones. */
    @SuppressWarnings("unchecked")
    default Condition arrayContainedBy(E... elements) { return Ops.compare(this, "<@", Ops.value(this, elements, "arrayContainedBy")); }

    /** {@code this <@ other} */
    default Condition arrayContainedBy(Field<E[]> other) { return containedBy(other); }

    /** {@code this && ?} – has at least one of the given elements. */
    @SuppressWarnings("unchecked")
    default Condition arrayOverlaps(E... elements) { return Ops.compare(this, "&&", Ops.value(this, elements, "arrayOverlaps")); }

    /** {@code this && other} */
    default Condition arrayOverlaps(Field<E[]> other) { return overlaps(other); }

    /** {@code ? = ANY(this)} – contains the element. */
    default Condition hasElement(E element) {
        if (element == null) throw new IllegalArgumentException("element must not be null");
        ArrayField<E> self = this;
        return Fields.condition(ctx -> ctx.bind(elementType(), element).append(" = ANY(").visit(self).append(')'), false);
    }

    /** {@code cardinality(this)} – the total number of elements. */
    default NumberField<Integer> length() { return Fields.number(SqlTypes.INT4, Ops.call("cardinality", this)); }

    /** {@code (this)[index]} – 1-based element access. */
    default Field<E> element(int index) {
        ArrayField<E> self = this;
        return Fields.of(elementType(), ctx -> ctx.append('(').visit(self).append(")[").append(Integer.toString(index)).append(']'));
    }

    /** {@code array_append(this, ?)} */
    default ArrayField<E> append(E element) {
        ArrayField<E> self = this;
        return Fields.array(type(), ctx -> ctx.append("array_append(").visit(self).append(", ")
                .bind(elementType(), element).append(')'));
    }

    /** {@code array_remove(this, ?)} */
    default ArrayField<E> remove(E element) {
        ArrayField<E> self = this;
        return Fields.array(type(), ctx -> ctx.append("array_remove(").visit(self).append(", ")
                .bind(elementType(), element).append(')'));
    }

    @Override
    @SuppressWarnings("unchecked")
    default ArrayField<E> as(String alias) { return (ArrayField<E>) Fields.alias(this, alias); }
}
