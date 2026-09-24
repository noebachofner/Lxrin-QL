package ch.lxrin.ql.entity;

import ch.lxrin.ql.schema.Column;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Access to the tracking state of entities for repositories. Not meant for
 * application code.
 */
public final class EntityAccess {

    private EntityAccess() {}

    /** Returns the current value of a column. */
    public static Object read(Entity<?> entity, Column<?> column) {
        return entity.readValue(column);
    }

    /** Sets a column value without tracking. */
    public static void write(Entity<?> entity, Column<?> column, Object value) {
        entity.writeValue(column, value);
    }

    /** Returns the columns to insert for a new entity (those whose setters were called). */
    public static Set<Column<?>> assignedColumns(Entity<?> entity) {
        return new LinkedHashSet<>(entity.assigned);
    }

    /** Loads all values of a row and marks the entity persistent without changes. */
    public static void load(Entity<?> entity, List<Column<?>> columns, Object[] values) {
        for (int i = 0; i < columns.size(); i++) entity.writeValue(columns.get(i), values[i]);
        markPersistent(entity);
    }

    /** Marks the entity persistent and clears its changes. */
    public static void markPersistent(Entity<?> entity) {
        entity.state = Entity.State.PERSISTENT;
        entity.original.clear();
        entity.assigned.clear();
    }

    /** Marks the entity deleted. */
    public static void markDeleted(Entity<?> entity) {
        entity.state = Entity.State.DELETED;
    }

    /** Captures state, changes and values, to restore them after a rollback. */
    public static Snapshot snapshot(Entity<?> entity, List<Column<?>> columns) {
        Map<Column<?>, Object> values = new HashMap<>();
        for (Column<?> c : columns) values.put(c, entity.readValue(c));
        return new Snapshot(entity, entity.state, new LinkedHashMap<>(entity.original), new LinkedHashSet<>(entity.assigned), values);
    }

    /** A captured entity state. */
    public static final class Snapshot {
        private final Entity<?> entity;
        private final Entity.State state;
        private final Map<Column<?>, Object> original;
        private final Set<Column<?>> assigned;
        private final Map<Column<?>, Object> values;

        Snapshot(Entity<?> entity, Entity.State state, Map<Column<?>, Object> original, Set<Column<?>> assigned,
                 Map<Column<?>, Object> values) {
            this.entity = entity;
            this.state = state;
            this.original = original;
            this.assigned = assigned;
            this.values = values;
        }

        /** Restores the entity to the captured state. */
        public void restore() {
            for (Map.Entry<Column<?>, Object> e : values.entrySet()) entity.writeValue(e.getKey(), e.getValue());
            entity.state = state;
            entity.original.clear();
            entity.original.putAll(original);
            entity.assigned.clear();
            entity.assigned.addAll(assigned);
        }
    }
}
