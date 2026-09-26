package ch.lxrin.ql.dsl;

import ch.lxrin.ql.render.QueryPart;
import ch.lxrin.ql.types.DataType;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

/**
 * A typed SQL expression: a column, a function call, a bind parameter or a
 * sub-query.
 *
 * <p>All operations are typed. {@code USERS.CREATED_AT.gt(instant)} compiles,
 * {@code USERS.CREATED_AT.eq("abc")} does not. A Java value is always sent as
 * a bind parameter.</p>
 *
 * <p>Families of types add their own operations: {@link StringField},
 * {@link NumberField}, {@link TemporalField}, {@link JsonField},
 * {@link ArrayField} and {@link Condition} (booleans).</p>
 *
 * @param <T> the Java type of the expression's values
 */
public interface Field<T> extends QueryPart {

    /** Returns the SQL type of this expression. */
    DataType<T> type();

    /**
     * Returns the output name of this field: the alias, or the column name
     * for a column; {@code null} for an unnamed expression.
     */
    default String name() {
        return null;
    }

    // -------------------------------------------------------------------------
    // Comparison
    // -------------------------------------------------------------------------

    /** {@code this = ?}. A {@code null} value is rejected; use {@link #isNull()}. */
    default Condition eq(T value) { return Ops.compare(this, "=", Ops.value(this, value, "eq")); }

    /** {@code this = other} */
    default Condition eq(Field<T> other) { return Ops.compare(this, "=", Ops.field(other)); }

    /** {@code this <> ?} */
    default Condition ne(T value) { return Ops.compare(this, "<>", Ops.value(this, value, "ne")); }

    /** {@code this <> other} */
    default Condition ne(Field<T> other) { return Ops.compare(this, "<>", Ops.field(other)); }

    /** {@code this > ?} */
    default Condition gt(T value) { return Ops.compare(this, ">", Ops.value(this, value, "gt")); }

    /** {@code this > other} */
    default Condition gt(Field<T> other) { return Ops.compare(this, ">", Ops.field(other)); }

    /** {@code this >= ?} */
    default Condition ge(T value) { return Ops.compare(this, ">=", Ops.value(this, value, "ge")); }

    /** {@code this >= other} */
    default Condition ge(Field<T> other) { return Ops.compare(this, ">=", Ops.field(other)); }

    /** {@code this < ?} */
    default Condition lt(T value) { return Ops.compare(this, "<", Ops.value(this, value, "lt")); }

    /** {@code this < other} */
    default Condition lt(Field<T> other) { return Ops.compare(this, "<", Ops.field(other)); }

    /** {@code this <= ?} */
    default Condition le(T value) { return Ops.compare(this, "<=", Ops.value(this, value, "le")); }

    /** {@code this <= other} */
    default Condition le(Field<T> other) { return Ops.compare(this, "<=", Ops.field(other)); }

    /** {@code this IS NULL} */
    default Condition isNull() { return Ops.postfix(this, "IS NULL"); }

    /** {@code this IS NOT NULL} */
    default Condition isNotNull() { return Ops.postfix(this, "IS NOT NULL"); }

    /** {@code this = ?}, or {@code this IS NULL} if {@code value} is {@code null}. */
    default Condition eqOrIsNull(T value) { return value == null ? isNull() : eq(value); }

    /** {@code this IS DISTINCT FROM ?} – a null-safe {@code <>}; {@code null} is allowed. */
    default Condition isDistinctFrom(T value) { return Ops.compare(this, "IS DISTINCT FROM", Ops.nullableValue(this, value)); }

    /** {@code this IS DISTINCT FROM other} */
    default Condition isDistinctFrom(Field<T> other) { return Ops.compare(this, "IS DISTINCT FROM", Ops.field(other)); }

    /** {@code this IS NOT DISTINCT FROM ?} – a null-safe {@code =}; {@code null} is allowed. */
    default Condition isNotDistinctFrom(T value) { return Ops.compare(this, "IS NOT DISTINCT FROM", Ops.nullableValue(this, value)); }

    /** {@code this IS NOT DISTINCT FROM other} */
    default Condition isNotDistinctFrom(Field<T> other) { return Ops.compare(this, "IS NOT DISTINCT FROM", Ops.field(other)); }

    /**
     * {@code this = ANY(?)} with all values bound as <em>one</em> array
     * parameter, so the SQL is the same for any number of values. An empty
     * collection matches nothing.
     */
    default Condition in(Collection<? extends T> values) { return Ops.in(this, values, false); }

    /** {@code NOT (this = ANY(?))}; an empty collection matches every non-null value. */
    default Condition notIn(Collection<? extends T> values) { return Ops.in(this, values, true); }

    /** {@code this IN (SELECT ...)} */
    default Condition in(Subquery<? extends T> subquery) { return Ops.compare(this, "IN", subquery); }

    /** {@code this NOT IN (SELECT ...)} */
    default Condition notIn(Subquery<? extends T> subquery) { return Ops.compare(this, "NOT IN", subquery); }

    /** {@code this = ANY(array)} for an array expression. */
    default Condition eqAny(Field<T[]> array) {
        return Ops.compare(this, "=", ctx -> ctx.append("ANY(").visit(array).append(')'));
    }

    /** {@code this BETWEEN ? AND ?} */
    default Condition between(T from, T to) {
        return Ops.between(this, "BETWEEN", Ops.value(this, from, "between"), Ops.value(this, to, "between"));
    }

    /** {@code this BETWEEN from AND to} */
    default Condition between(Field<T> from, Field<T> to) {
        return Ops.between(this, "BETWEEN", Ops.field(from), Ops.field(to));
    }

    /** {@code this NOT BETWEEN ? AND ?} */
    default Condition notBetween(T from, T to) {
        return Ops.between(this, "NOT BETWEEN", Ops.value(this, from, "notBetween"), Ops.value(this, to, "notBetween"));
    }

    /** {@code this NOT BETWEEN from AND to} */
    default Condition notBetween(Field<T> from, Field<T> to) {
        return Ops.between(this, "NOT BETWEEN", Ops.field(from), Ops.field(to));
    }

    /** {@code this BETWEEN SYMMETRIC a AND b} */
    default Condition betweenSymmetric(Field<T> a, Field<T> b) {
        return Ops.between(this, "BETWEEN SYMMETRIC", Ops.field(a), Ops.field(b));
    }

    /** {@code this NOT BETWEEN SYMMETRIC a AND b} */
    default Condition notBetweenSymmetric(Field<T> a, Field<T> b) {
        return Ops.between(this, "NOT BETWEEN SYMMETRIC", Ops.field(a), Ops.field(b));
    }

    /** {@code this = ANY(?)} for the given values, bound as one array parameter. */
    @SuppressWarnings("unchecked")
    default Condition in(T... values) { return in(Arrays.asList(values)); }

    /** {@code this <> ALL(?)} for the given values. */
    @SuppressWarnings("unchecked")
    default Condition notIn(T... values) { return notIn(Arrays.asList(values)); }

    /** {@code this = ANY(?)} for a list created with {@code b.setList(..)}. */
    default Condition in(BindList<? extends T> values) { return in(values.values()); }

    /** {@code this <> ALL(?)} for a list created with {@code b.setList(..)}. */
    default Condition notIn(BindList<? extends T> values) { return notIn(values.values()); }

    /** {@code this BETWEEN SYMMETRIC ? AND ?} – the bounds may be given in any order. */
    default Condition betweenSymmetric(T a, T b) {
        return Ops.between(this, "BETWEEN SYMMETRIC", Ops.value(this, a, "betweenSymmetric"), Ops.value(this, b, "betweenSymmetric"));
    }

    /** {@code this NOT BETWEEN SYMMETRIC ? AND ?} */
    default Condition notBetweenSymmetric(T a, T b) {
        return Ops.between(this, "NOT BETWEEN SYMMETRIC", Ops.value(this, a, "notBetweenSymmetric"), Ops.value(this, b, "notBetweenSymmetric"));
    }

    // -------------------------------------------------------------------------
    // Quantified comparisons: ANY / ALL
    // -------------------------------------------------------------------------

    /** {@code this = ANY(..)} / {@code this = ALL(..)} */
    default Condition eq(Quantified<? extends T> q) { return Ops.compare(this, "=", q); }

    /** {@code this <> ANY(..)} / {@code this <> ALL(..)} */
    default Condition ne(Quantified<? extends T> q) { return Ops.compare(this, "<>", q); }

    /** {@code this > ANY(..)} / {@code this > ALL(..)} */
    default Condition gt(Quantified<? extends T> q) { return Ops.compare(this, ">", q); }

    /** {@code this >= ANY(..)} / {@code this >= ALL(..)} */
    default Condition ge(Quantified<? extends T> q) { return Ops.compare(this, ">=", q); }

    /** {@code this < ANY(..)} / {@code this < ALL(..)} */
    default Condition lt(Quantified<? extends T> q) { return Ops.compare(this, "<", q); }

    /** {@code this <= ANY(..)} / {@code this <= ALL(..)} */
    default Condition le(Quantified<? extends T> q) { return Ops.compare(this, "<=", q); }

    // -------------------------------------------------------------------------
    // Optional filters: …IfPresent
    // -------------------------------------------------------------------------

    /** {@code this = ?} if a value is present, else {@link Condition#noCondition()}. */
    default Condition eqIfPresent(Optional<? extends T> value) { return value.isPresent() ? eq(value.get()) : Condition.noCondition(); }

    /** {@code this <> ?} if a value is present, else no condition. */
    default Condition neIfPresent(Optional<? extends T> value) { return value.isPresent() ? ne(value.get()) : Condition.noCondition(); }

    /** {@code this > ?} if a value is present, else no condition. */
    default Condition gtIfPresent(Optional<? extends T> value) { return value.isPresent() ? gt(value.get()) : Condition.noCondition(); }

    /** {@code this >= ?} if a value is present, else no condition. */
    default Condition geIfPresent(Optional<? extends T> value) { return value.isPresent() ? ge(value.get()) : Condition.noCondition(); }

    /** {@code this < ?} if a value is present, else no condition. */
    default Condition ltIfPresent(Optional<? extends T> value) { return value.isPresent() ? lt(value.get()) : Condition.noCondition(); }

    /** {@code this <= ?} if a value is present, else no condition. */
    default Condition leIfPresent(Optional<? extends T> value) { return value.isPresent() ? le(value.get()) : Condition.noCondition(); }

    /** {@code this = ANY(?)} if a collection is present, else no condition (an empty collection still matches nothing). */
    default Condition inIfPresent(Optional<? extends Collection<? extends T>> values) {
        return values.isPresent() ? in(values.get()) : Condition.noCondition();
    }

    // -------------------------------------------------------------------------
    // Sorting, aliasing, casting
    // -------------------------------------------------------------------------

    /** {@code this ASC} */
    default SortField<T> asc() { return new SortField<>(this, true, null); }

    /** {@code this DESC} */
    default SortField<T> desc() { return new SortField<>(this, false, null); }

    /**
     * Gives the field an alias. In a select list it renders as
     * {@code expr AS alias}, elsewhere (e.g. in {@code ORDER BY}) as the alias.
     */
    default Field<T> as(String alias) { return Fields.alias(this, alias); }

    /** {@code CAST(this AS type)} */
    default <U> Field<U> cast(DataType<U> type) { return Fields.of(type, Ops.cast(this, type)); }

    /** {@code COALESCE(this, ?)} */
    default Field<T> coalesce(T fallback) { return Fields.of(type(), Ops.coalesce(this, Ops.value(this, fallback, "coalesce"))); }

    /** {@code COALESCE(this, other)} */
    default Field<T> coalesce(Field<T> other) { return Fields.of(type(), Ops.coalesce(this, Ops.field(other))); }

    /** {@code NULLIF(this, ?)} */
    default Field<T> nullIf(T value) {
        return Fields.of(type(), Ops.call("NULLIF", this, Ops.value(this, value, "nullIf")));
    }
}
