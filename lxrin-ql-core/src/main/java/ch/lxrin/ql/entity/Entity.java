package ch.lxrin.ql.entity;

import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.spi.Change;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Base class of generated entities. It holds the entity's state and tracks
 * changes; the generated subclass is a plain data object with one field,
 * getter and setter per column.
 *
 * <ul>
 *   <li><b>New</b>: created with {@code new User()}. The columns whose
 *       setters were called are inserted; all others get their database
 *       defaults.</li>
 *   <li><b>Persistent</b>: loaded by a repository or saved. Setters record the
 *       old value, so an update writes only changed columns.</li>
 *   <li><b>Deleted</b>: deleted through a repository.</li>
 * </ul>
 *
 * <p>Setting a field to its current value is not a change, and setting it
 * back to its original value removes the change. Entities are not
 * thread-safe and use identity equality.</p>
 *
 * @param <K> the primary key type
 */
public abstract class Entity<K> {

    /** The lifecycle state. */
    public enum State {
        /** Not yet inserted. */
        NEW,
        /** Loaded or saved. */
        PERSISTENT,
        /** Deleted. */
        DELETED
    }

    State state = State.NEW;
    final Map<Column<?>, Object> original = new LinkedHashMap<>();
    final Set<Column<?>> assigned = new LinkedHashSet<>();

    /** Creates a new entity. */
    protected Entity() {}

    /** Returns the primary key value (a key record for composite keys). */
    public abstract K id();

    /** Returns the table of this entity. */
    public abstract Table<?> table();

    /** Returns the current value of a column. Generated. */
    protected abstract Object readValue(Column<?> column);

    /** Sets a column value without tracking. Generated; used when loading. */
    protected abstract void writeValue(Column<?> column, Object value);

    /**
     * Records the change of a column; called by generated setters.
     *
     * @return {@code newValue}, to be assigned to the field
     */
    protected final <T> T track(Column<T> column, T oldValue, T newValue) {
        if (state == State.NEW) {
            assigned.add(column);
        } else if (!original.containsKey(column)) {
            if (!same(oldValue, newValue)) original.put(column, oldValue);
        } else if (same(original.get(column), newValue)) {
            original.remove(column);
        }
        return newValue;
    }

    /** Returns the lifecycle state. */
    public final State state() {
        return state;
    }

    /** Returns {@code true} until the entity is inserted. */
    public final boolean isNew() {
        return state == State.NEW;
    }

    /** Returns {@code true} for loaded or saved entities. */
    public final boolean isPersistent() {
        return state == State.PERSISTENT;
    }

    /** Returns {@code true} after deletion. */
    public final boolean isDeleted() {
        return state == State.DELETED;
    }

    /** Returns {@code true} if there is something to write. */
    public final boolean isChanged() {
        return state == State.NEW ? !assigned.isEmpty() : !original.isEmpty();
    }

    /** Returns {@code true} if the column was set (new entity) or changed (persistent entity). */
    public final boolean isChanged(Column<?> column) {
        return state == State.NEW ? assigned.contains(column) : original.containsKey(column);
    }

    /**
     * Returns the changes with old and new values: for a new entity every set
     * column (old value {@code null}), for a persistent entity every changed column.
     */
    public final Map<Column<?>, Change<?>> changes() {
        Map<Column<?>, Change<?>> result = new LinkedHashMap<>();
        if (state == State.NEW) {
            for (Column<?> c : assigned) result.put(c, new Change<>(null, readValue(c)));
        } else {
            for (Map.Entry<Column<?>, Object> e : original.entrySet()) {
                result.put(e.getKey(), new Change<>(e.getValue(), readValue(e.getKey())));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /** Returns the value a column had when the entity was loaded or last saved. */
    @SuppressWarnings("unchecked")
    public final <T> T originalValue(Column<T> column) {
        return original.containsKey(column) ? (T) original.get(column) : (T) readValue(column);
    }

    static boolean same(Object a, Object b) {
        if (a instanceof BigDecimal && b instanceof BigDecimal) return ((BigDecimal) a).compareTo((BigDecimal) b) == 0;
        if (a instanceof Object[] && b instanceof Object[]) return Arrays.deepEquals((Object[]) a, (Object[]) b);
        if (a instanceof byte[] && b instanceof byte[]) return Arrays.equals((byte[]) a, (byte[]) b);
        return Objects.equals(a, b);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + state + ", id=" + id() + "]";
    }
}
