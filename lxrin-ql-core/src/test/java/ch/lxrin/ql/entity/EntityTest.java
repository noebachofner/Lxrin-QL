package ch.lxrin.ql.entity;

import ch.lxrin.ql.TestSchema.UsersTable;
import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.spi.Change;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ch.lxrin.ql.TestSchema.UsersTable.USERS;
import static org.junit.jupiter.api.Assertions.*;

class EntityTest {

    /** A hand-written entity in the shape of generated code. */
    static final class Person extends Entity<UUID> {
        private UUID id;
        private String name;
        private String[] tags;
        private Long version;

        void setId(UUID v) {
            id = track(USERS.ID, id, v);
        }

        void setName(String v) {
            name = track(USERS.NAME, name, v);
        }

        void setTags(String[] v) {
            tags = track(USERS.TAGS, tags, v);
        }

        @Override
        public UUID id() {
            return id;
        }

        @Override
        public UsersTable table() {
            return USERS;
        }

        @Override
        protected Object readValue(Column<?> column) {
            switch (column.name()) {
                case "id": return id;
                case "name": return name;
                case "tags": return tags;
                case "version": return version;
                default: return null;
            }
        }

        @Override
        protected void writeValue(Column<?> column, Object value) {
            switch (column.name()) {
                case "id": id = (UUID) value; break;
                case "name": name = (String) value; break;
                case "tags": tags = (String[]) value; break;
                case "version": version = (Long) value; break;
                default: break;
            }
        }
    }

    @Test
    void newEntitiesTrackAssignedColumns() {
        Person p = new Person();
        assertTrue(p.isNew());
        assertFalse(p.isChanged());
        p.setName("Ada");
        assertTrue(p.isChanged(USERS.NAME));
        assertFalse(p.isChanged(USERS.ID));
        assertEquals(Map.of(USERS.NAME, new Change<>(null, "Ada")), p.changes());
        assertEquals(java.util.Set.of(USERS.NAME), EntityAccess.assignedColumns(p));
        assertTrue(p.toString().contains("NEW"));
    }

    @Test
    void persistentEntitiesTrackOldValues() {
        Person p = new Person();
        EntityAccess.load(p, List.of(USERS.ID, USERS.NAME, USERS.TAGS), new Object[]{UUID.randomUUID(), "Ada", new String[]{"a"}});
        assertTrue(p.isPersistent());
        assertFalse(p.isChanged());
        p.setName("Ada");
        assertFalse(p.isChanged(), "same value is no change");
        p.setTags(new String[]{"a"});
        assertFalse(p.isChanged(), "arrays are compared by content");
        p.setName("Grace");
        p.setName("Hopper");
        assertEquals(Map.of(USERS.NAME, new Change<>("Ada", "Hopper")), p.changes());
        assertEquals("Ada", p.originalValue(USERS.NAME));
        p.setName("Ada");
        assertFalse(p.isChanged(), "back to the original value");
        assertTrue(Entity.same(new BigDecimal("1.0"), new BigDecimal("1.00")));
        assertTrue(Entity.same(new byte[]{1}, new byte[]{1}));
    }

    @Test
    void snapshotsRestoreStateValuesAndChanges() {
        Person p = new Person();
        p.setName("Ada");
        EntityAccess.Snapshot snapshot = EntityAccess.snapshot(p, List.of(USERS.ID, USERS.NAME, USERS.VERSION));
        EntityAccess.load(p, List.of(USERS.ID, USERS.NAME, USERS.VERSION), new Object[]{UUID.randomUUID(), "Ada", 3L});
        EntityAccess.markDeleted(p);
        assertTrue(p.isDeleted());
        snapshot.restore();
        assertTrue(p.isNew());
        assertNull(p.id());
        assertEquals(Map.of(USERS.NAME, new Change<>(null, "Ada")), p.changes());
        assertEquals("Ada", EntityAccess.read(p, USERS.NAME));
        EntityAccess.write(p, USERS.NAME, "x");
        assertEquals("x", EntityAccess.read(p, USERS.NAME));
    }
}
