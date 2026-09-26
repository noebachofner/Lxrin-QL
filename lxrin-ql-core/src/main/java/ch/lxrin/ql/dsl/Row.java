package ch.lxrin.ql.dsl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A result row with typed access by field: {@code row.get(USERS.NAME)}
 * returns a {@code String}. Used for dynamic select lists; fixed select lists
 * return typed tuples ({@code Row2<A, B>}, ...).
 */
public final class Row {

    /** The fields of a result, shared by all rows of that result. */
    public static final class Shape {
        private final List<Field<?>> fields;
        private final Map<Field<?>, Integer> byIdentity = new IdentityHashMap<>();
        private final Map<Field<?>, Integer> byEquality = new HashMap<>();
        private final Map<String, Integer> byName = new HashMap<>();

        /** Creates the shape of a result with the given fields. */
        public Shape(List<? extends Field<?>> fields) {
            this.fields = List.copyOf(fields);
            for (int i = 0; i < this.fields.size(); i++) {
                Field<?> f = this.fields.get(i);
                byIdentity.putIfAbsent(f, i);
                byEquality.putIfAbsent(f, i);
                if (f.name() != null) byName.putIfAbsent(f.name(), i);
            }
        }

        /** Returns the fields in result order. */
        public List<Field<?>> fields() {
            return fields;
        }

        /** Returns the index of {@code field}, or -1. */
        public int indexOf(Field<?> field) {
            Integer i = byIdentity.get(field);
            if (i == null) i = byEquality.get(field);
            return i == null ? -1 : i;
        }

        /** Returns the index of the field with the given output name, or -1. */
        public int indexOf(String name) {
            return byName.getOrDefault(name, -1);
        }

        /** Creates a row of this shape. */
        public Row row(Object[] values) {
            if (values.length != fields.size()) {
                throw new IllegalArgumentException("expected " + fields.size() + " values, got " + values.length);
            }
            return new Row(this, values);
        }
    }

    private final Shape shape;
    private final Object[] values;

    private Row(Shape shape, Object[] values) {
        this.shape = shape;
        this.values = values;
    }

    /** Returns the value of {@code field}. */
    @SuppressWarnings("unchecked")
    public <T> T get(Field<T> field) {
        int i = shape.indexOf(field);
        if (i < 0) throw new IllegalArgumentException("field " + field + " is not part of this row " + shape.fields);
        return (T) values[i];
    }

    /** Returns the value at a position (0-based). */
    public Object get(int index) {
        return values[index];
    }

    /** Returns the value with the given output name (column name or alias). */
    public Object get(String name) {
        int i = shape.indexOf(name);
        if (i < 0) throw new IllegalArgumentException("no field named " + name + " in this row");
        return values[i];
    }

    /** Returns {@code true} if the row contains {@code field}. */
    public boolean contains(Field<?> field) {
        return shape.indexOf(field) >= 0;
    }

    /** Returns the number of values. */
    public int size() {
        return values.length;
    }

    /** Returns the fields of this row. */
    public List<Field<?>> fields() {
        return shape.fields;
    }

    /** Returns a copy of the values. */
    public Object[] values() {
        return values.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Row && Arrays.deepEquals(values, ((Row) o).values);
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(values);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Row{");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(", ");
            Field<?> f = shape.fields.get(i);
            sb.append(f.name() != null ? f.name() : "#" + i).append('=');
            Object v = values[i];
            sb.append(v instanceof Object[] ? Arrays.deepToString((Object[]) v) : Objects.toString(v));
        }
        return sb.append('}').toString();
    }
}
