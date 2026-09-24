package ch.lxrin.ql.spi;

import ch.lxrin.ql.schema.Column;

import java.util.Map;

/**
 * An entity being written by a repository, with its changes.
 *
 * @param entity  the entity object
 * @param newRow  {@code true} for an insert
 * @param changes the changed columns with old and new values (for an insert: all set columns)
 */
public record EntityWrite(Object entity, boolean newRow, Map<Column<?>, Change<?>> changes) {

    /** Creates the record with an immutable copy of the changes. */
    public EntityWrite {
        changes = Map.copyOf(changes);
    }
}
