package ch.lxrin.ql.schema;

import ch.lxrin.ql.dsl.Condition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * The primary key of a table.
 *
 * <p>For a single-column key the key type {@code K} is the column type. A
 * composite key uses a generated key record and two functions that convert
 * between the record and the column values.</p>
 *
 * @param <K> the key type
 */
public final class PrimaryKey<K> implements Constraint {

    private final String name;
    private final Table<?> table;
    private final List<Column<?>> columns;
    private final Class<K> keyType;
    private final KeyStrategy strategy;
    private final Function<Object[], K> fromValues;
    private final Function<K, Object[]> toValues;

    PrimaryKey(String name, Table<?> table, List<Column<?>> columns, Class<K> keyType, KeyStrategy strategy,
               Function<Object[], K> fromValues, Function<K, Object[]> toValues) {
        this.name = name;
        this.table = table;
        this.columns = List.copyOf(columns);
        this.keyType = keyType;
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.fromValues = fromValues;
        this.toValues = toValues;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Table<?> table() {
        return table;
    }

    @Override
    public List<Column<?>> columns() {
        return columns;
    }

    /** Returns the Java type of the key. */
    public Class<K> keyType() {
        return keyType;
    }

    /** Returns how new keys are created. */
    public KeyStrategy strategy() {
        return strategy;
    }

    /** Returns {@code true} for a key with more than one column. */
    public boolean composite() {
        return columns.size() > 1;
    }

    /** Builds a key from its column values (in column order). */
    public K keyOf(Object[] values) {
        return fromValues.apply(values);
    }

    /** Returns the column values of a key (in column order). */
    public Object[] valuesOf(K key) {
        return toValues.apply(key);
    }

    /** Returns the condition that selects the row with the given key. */
    public Condition matches(K key) {
        Objects.requireNonNull(key, "key");
        Object[] values = valuesOf(key);
        List<Condition> parts = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) parts.add(columns.get(i).eqUnchecked(values[i]));
        return Condition.and(parts);
    }

    @Override
    public String toString() {
        return "PrimaryKey" + columns;
    }
}
