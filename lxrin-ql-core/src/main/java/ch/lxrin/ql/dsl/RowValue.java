package ch.lxrin.ql.dsl;

import ch.lxrin.ql.render.QueryPart;
import ch.lxrin.ql.render.RenderContext;
import ch.lxrin.ql.types.DataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * A row value constructor {@code (a, b, …)} for row comparisons and
 * {@code IN} with rows:
 *
 * <pre>{@code
 * row(EVENTS.CREATED_AT, EVENTS.ID).gt(lastCreatedAt, lastId)
 * row(ASSETS.OWNER_ID, ASSETS.NAME).in(List.of(List.of(ownerA, "Laptop"), List.of(ownerB, "Phone")))
 * }</pre>
 *
 * <p>Values are bound with the types of the row's fields, and their number and
 * Java types are checked when the condition is built. Comparing with another
 * row requires the same arity and field types.</p>
 */
public final class RowValue implements QueryPart {

    private final List<QueryPart> parts;
    private final List<DataType<?>> types;

    private RowValue(List<QueryPart> parts, List<DataType<?>> types) {
        this.parts = List.copyOf(parts);
        this.types = List.copyOf(types);
    }

    /** {@code (f1, f2, …)} of at least two fields. */
    public static RowValue of(Field<?>... fields) {
        if (fields.length < 2) throw new IllegalArgumentException("a row value needs at least two fields");
        List<QueryPart> parts = new ArrayList<>();
        List<DataType<?>> types = new ArrayList<>();
        for (Field<?> f : fields) {
            parts.add(Ops.field(f));
            types.add(f.type());
        }
        return new RowValue(parts, types);
    }

    /** Returns the number of fields. */
    public int arity() {
        return parts.size();
    }

    /** Binds values with the types of this row's fields. */
    private RowValue bind(List<?> values, String operation) {
        if (values == null) throw new IllegalArgumentException("the values of " + operation + "(..) must not be null");
        if (values.size() != types.size()) {
            throw new IllegalArgumentException(operation + "(..) needs " + types.size() + " values, got " + values.size());
        }
        List<QueryPart> bound = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            Object v = values.get(i);
            if (v == null) throw new IllegalArgumentException("value " + (i + 1) + " of " + operation + "(..) must not be null");
            bound.add(bindOne(types.get(i), v));
        }
        return new RowValue(bound, types);
    }

    private static <T> QueryPart bindOne(DataType<T> type, Object value) {
        T checked = type.cast(value);
        return ctx -> ctx.bind(type, checked);
    }

    private RowValue check(RowValue other) {
        if (other == null) throw new IllegalArgumentException("row must not be null");
        if (other.arity() != arity()) throw new IllegalArgumentException("rows of different arity: " + arity() + " and " + other.arity());
        for (int i = 0; i < types.size(); i++) {
            if (!types.get(i).javaType().equals(other.types.get(i).javaType())) {
                throw new IllegalArgumentException("row field " + (i + 1) + " has type " + types.get(i).javaType().getSimpleName()
                        + ", the other row " + other.types.get(i).javaType().getSimpleName());
            }
        }
        return other;
    }

    /** {@code (a, b) = (c, d)} */
    public Condition eq(RowValue other) { return Ops.compare(this, "=", check(other)); }

    /** {@code (a, b) = (?, ?)} */
    public Condition eq(Object... values) { return Ops.compare(this, "=", bind(Arrays.asList(values), "eq")); }

    /** {@code (a, b) <> (c, d)} */
    public Condition ne(RowValue other) { return Ops.compare(this, "<>", check(other)); }

    /** {@code (a, b) <> (?, ?)} */
    public Condition ne(Object... values) { return Ops.compare(this, "<>", bind(Arrays.asList(values), "ne")); }

    /** {@code (a, b) > (c, d)} – lexicographic, e.g. for keyset pagination. */
    public Condition gt(RowValue other) { return Ops.compare(this, ">", check(other)); }

    /** {@code (a, b) > (?, ?)} */
    public Condition gt(Object... values) { return Ops.compare(this, ">", bind(Arrays.asList(values), "gt")); }

    /** {@code (a, b) >= (c, d)} */
    public Condition ge(RowValue other) { return Ops.compare(this, ">=", check(other)); }

    /** {@code (a, b) >= (?, ?)} */
    public Condition ge(Object... values) { return Ops.compare(this, ">=", bind(Arrays.asList(values), "ge")); }

    /** {@code (a, b) < (c, d)} */
    public Condition lt(RowValue other) { return Ops.compare(this, "<", check(other)); }

    /** {@code (a, b) < (?, ?)} */
    public Condition lt(Object... values) { return Ops.compare(this, "<", bind(Arrays.asList(values), "lt")); }

    /** {@code (a, b) <= (c, d)} */
    public Condition le(RowValue other) { return Ops.compare(this, "<=", check(other)); }

    /** {@code (a, b) <= (?, ?)} */
    public Condition le(Object... values) { return Ops.compare(this, "<=", bind(Arrays.asList(values), "le")); }

    /** {@code (a, b) IS DISTINCT FROM (c, d)} */
    public Condition isDistinctFrom(RowValue other) { return Ops.compare(this, "IS DISTINCT FROM", check(other)); }

    /** {@code (a, b) IS NOT DISTINCT FROM (c, d)} */
    public Condition isNotDistinctFrom(RowValue other) { return Ops.compare(this, "IS NOT DISTINCT FROM", check(other)); }

    /**
     * {@code (a, b) IN ((?, ?), (?, ?))} – each element is one row of values.
     * An empty collection is always false.
     */
    public Condition in(Collection<? extends List<?>> rows) { return inList(rows, false); }

    /** {@code (a, b) NOT IN ((?, ?), …)}; an empty collection is always true. */
    public Condition notIn(Collection<? extends List<?>> rows) { return inList(rows, true); }

    /** {@code (a, b) IN ((c, d), (e, f))} for row expressions. */
    public Condition in(RowValue... rows) { return inRows(Arrays.asList(rows), false); }

    /** {@code (a, b) NOT IN ((c, d), …)} */
    public Condition notIn(RowValue... rows) { return inRows(Arrays.asList(rows), true); }

    /** {@code (a, b) IN (SELECT x, y …)} */
    public Condition in(AbstractSelect<?, ?> subquery) { return Ops.compare(this, "IN", requireQuery(subquery)); }

    /** {@code (a, b) NOT IN (SELECT x, y …)} */
    public Condition notIn(AbstractSelect<?, ?> subquery) { return Ops.compare(this, "NOT IN", requireQuery(subquery)); }

    private QueryPart requireQuery(AbstractSelect<?, ?> subquery) {
        if (subquery == null) throw new IllegalArgumentException("sub-query must not be null");
        if (subquery.fields().size() != arity()) {
            throw new IllegalArgumentException("the sub-query returns " + subquery.fields().size() + " columns, the row has " + arity());
        }
        return subquery;
    }

    private Condition inList(Collection<? extends List<?>> rows, boolean negated) {
        if (rows == null) throw new IllegalArgumentException("rows must not be null");
        List<RowValue> bound = new ArrayList<>();
        for (List<?> r : rows) bound.add(bind(r, negated ? "notIn" : "in"));
        return render(bound, negated);
    }

    private Condition inRows(List<RowValue> rows, boolean negated) {
        for (RowValue r : rows) check(r);
        return render(rows, negated);
    }

    private Condition render(List<RowValue> rows, boolean negated) {
        if (rows.isEmpty()) return Fields.condition(ctx -> ctx.append(negated ? "TRUE" : "FALSE"), true);
        List<RowValue> frozen = List.copyOf(rows);
        RowValue self = this;
        return Fields.condition(ctx -> ctx.visit(self).append(negated ? " NOT IN (" : " IN (").visitAll(frozen, ", ").append(')'), false);
    }

    @Override
    public void render(RenderContext ctx) {
        ctx.append('(').visitAll(parts, ", ").append(')');
    }
}
