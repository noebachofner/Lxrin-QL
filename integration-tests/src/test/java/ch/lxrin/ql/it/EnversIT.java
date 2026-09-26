package ch.lxrin.ql.it;

import ch.lxrin.ql.audit.AuditListener;
import ch.lxrin.ql.audit.AuditSettings;
import ch.lxrin.ql.audit.AuditUser;
import ch.lxrin.ql.it.db.NoteAudRow;
import ch.lxrin.ql.it.db.RevisionRow;
import ch.lxrin.ql.it.envers.EnversNote;
import ch.lxrin.ql.it.envers.EnversRevision;
import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.types.SqlTypes;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static ch.lxrin.ql.QL.*;
import static ch.lxrin.ql.it.db.Tables.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * lxrin-ql-audit and Hibernate Envers share one history: Envers reads the rows written by LxrinQL, and LxrinQL
 * continues the history written by Envers.
 */
class EnversIT {

    private final Clock clock = Clock.fixed(Instant.parse("2025-06-01T12:00:00Z"), ZoneOffset.UTC);
    private final UUID lxrinUser = UUID.randomUUID();
    private final UUID enversUser = UUID.randomUUID();
    private SessionFactory hibernate;
    private QueryContext ctx;

    @BeforeEach
    void setUp() {
        Postgres.migrateItSchema();
        hibernate = new Configuration()
                .addAnnotatedClass(EnversNote.class)
                .addAnnotatedClass(EnversRevision.class)
                .setProperty("hibernate.connection.url", Postgres.jdbcUrl())
                .setProperty("hibernate.connection.username", Postgres.user())
                .setProperty("hibernate.connection.password", Postgres.password())
                .setProperty("hibernate.hbm2ddl.auto", "none")
                .setProperty("org.hibernate.envers.audit_table_suffix", "_aud")
                .setProperty("org.hibernate.envers.revision_field_name", "rev")
                .setProperty("org.hibernate.envers.revision_type_field_name", "revtype")
                .buildSessionFactory();
        EnversRevision.CURRENT_USER.set(enversUser);
        ctx = QueryContext.builder()
                .dataSource(Postgres.dataSource())
                .listener(new AuditListener(AuditSettings.builder().tables("note").clock(clock)
                        .user(AuditUser.of(SqlTypes.UUID, () -> lxrinUser)).build()))
                .build();
    }

    @AfterEach
    void tearDown() {
        hibernate.close();
    }

    private void inHibernate(Consumer<Session> work) {
        hibernate.inTransaction(work);
    }

    private <T> T withReader(Function<AuditReader, T> work) {
        try (Session session = hibernate.openSession()) {
            return work.apply(AuditReaderFactory.get(session));
        }
    }

    private List<NoteAudRow> history(UUID id) {
        return ctx.selectFrom(NOTE_AUD).where(NOTE_AUD.ID.eq(id)).orderBy(NOTE_AUD.REV.asc()).fetch();
    }

    @Test
    void enversReadsTheHistoryWrittenByLxrinQl() {
        UUID id = UUID.randomUUID();
        ctx.createInsert(NOTE, (c, b) -> c.set(NOTE.ID, id).set(NOTE.TITLE, b.setString("Draft"))).execute();
        ctx.createUpdate(NOTE, (c, b) -> c.set(NOTE.TITLE, b.setString("Final")).where(eq(NOTE.ID, id))).execute();
        ctx.createDelete(NOTE, (c, b) -> c.where(eq(NOTE.ID, id))).execute();

        List<Number> revisions = withReader(r -> r.getRevisions(EnversNote.class, id));
        assertEquals(3, revisions.size());
        assertEquals("Draft", withReader(r -> r.find(EnversNote.class, id, revisions.get(0))).getTitle());
        assertEquals("Final", withReader(r -> r.find(EnversNote.class, id, revisions.get(1))).getTitle());
        assertNull(withReader(r -> r.find(EnversNote.class, id, revisions.get(2))), "deleted in the third revision");

        @SuppressWarnings("unchecked")
        List<Object[]> changes = withReader(r -> r.createQuery().forRevisionsOfEntity(EnversNote.class, false, true)
                .getResultList());
        assertEquals(List.of(RevisionType.ADD, RevisionType.MOD, RevisionType.DEL),
                changes.stream().map(c -> (RevisionType) c[2]).toList());
        EnversRevision revision = (EnversRevision) changes.get(0)[1];
        assertEquals(lxrinUser, revision.getUserId());
        assertEquals(clock.instant(), revision.getRevisedAt());
    }

    @Test
    void lxrinQlContinuesTheHistoryWrittenByEnvers() {
        UUID id = UUID.randomUUID();
        inHibernate(s -> s.persist(new EnversNote(id, "Draft")));
        inHibernate(s -> s.find(EnversNote.class, id).setTitle("Reviewed"));

        List<NoteAudRow> written = history(id);
        assertEquals(List.of((short) 0, (short) 1), written.stream().map(NoteAudRow::revtype).toList());
        assertEquals("Reviewed", written.get(1).title());
        RevisionRow enversRevision = ctx.selectFrom(REVISION).where(REVISION.ID.eq(written.get(1).rev())).fetchOne();
        assertEquals(enversUser, enversRevision.userId());

        ctx.createUpdate(NOTE, (c, b) -> c.set(NOTE.TITLE, b.setString("Published")).where(eq(NOTE.ID, id))).execute();
        inHibernate(s -> s.find(EnversNote.class, id).setTitle("Published v2"));
        ctx.createDelete(NOTE, (c, b) -> c.where(eq(NOTE.ID, id))).execute();

        List<NoteAudRow> history = history(id);
        assertEquals(List.of("Draft", "Reviewed", "Published", "Published v2"),
                history.subList(0, 4).stream().map(NoteAudRow::title).toList());
        assertEquals(List.of((short) 0, (short) 1, (short) 1, (short) 1, (short) 2), history.stream().map(NoteAudRow::revtype).toList());
        List<Long> revs = history.stream().map(NoteAudRow::rev).toList();
        assertEquals(revs.stream().sorted().distinct().toList(), revs, "one increasing revision per change, from both writers");

        List<Number> seenByEnvers = withReader(r -> r.getRevisions(EnversNote.class, id));
        assertEquals(revs, seenByEnvers.stream().map(Number::longValue).toList());
        assertEquals("Published", withReader(r -> r.find(EnversNote.class, id, seenByEnvers.get(2))).getTitle());
        assertEquals(lxrinUser, withReader(r -> r.findRevision(EnversRevision.class, seenByEnvers.get(2))).getUserId());
    }
}
