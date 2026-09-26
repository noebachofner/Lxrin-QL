package ch.lxrin.ql.it;

import ch.lxrin.ql.QL;
import ch.lxrin.ql.audit.AuditListener;
import ch.lxrin.ql.audit.AuditUser;
import ch.lxrin.ql.it.db.NoteAudRow;
import ch.lxrin.ql.it.db.RevisionRow;
import ch.lxrin.ql.it.security.CurrentUser;
import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.types.SqlTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static ch.lxrin.ql.QL.*;
import static ch.lxrin.ql.it.db.Tables.*;
import static org.junit.jupiter.api.Assertions.*;

/** lxrin-ql-audit configured by Spring Boot properties: one revision per {@code @Transactional} method, user from Spring Security. */
@SpringBootTest(classes = SpringAuditIT.App.class, properties = {"lxrin.ql.audit.tables=note", "lxrin.ql.audit.store-data-at-delete=true"})
class SpringAuditIT {

    @SpringBootApplication(proxyBeanMethods = false)
    static class App {
        @Bean
        AuditUser<UUID> auditUser() {
            return AuditUser.of(SqlTypes.UUID, CurrentUser::id);
        }

        @Bean
        NoteService noteService() {
            return new NoteService();
        }
    }

    @Service
    static class NoteService {
        @Transactional
        public void writeTwo(UUID a, UUID b) {
            QL.createInsert(NOTE, (c, x) -> c.set(NOTE.ID, a).set(NOTE.TITLE, x.setString("A"))).execute();
            QL.createInsert(NOTE, (c, x) -> c.set(NOTE.ID, b).set(NOTE.TITLE, x.setString("B"))).execute();
            QL.createUpdate(NOTE, (c, x) -> c.set(NOTE.BODY, x.setString("text")).where(eq(NOTE.ID, a))).execute();
        }

        @Transactional
        public void writeAndFail(UUID id) {
            QL.createInsert(NOTE, (c, x) -> c.set(NOTE.ID, id).set(NOTE.TITLE, x.setString("lost"))).execute();
            throw new IllegalStateException("rollback");
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", Postgres::jdbcUrl);
        registry.add("spring.datasource.username", Postgres::user);
        registry.add("spring.datasource.password", Postgres::password);
    }

    @BeforeAll
    static void schema() {
        Postgres.migrateItSchema();
    }

    @Autowired
    NoteService notes;
    @Autowired
    QueryContext ctx;
    @Autowired
    AuditListener audit;

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void oneRevisionPerTransactionWithTheSignedInUser() {
        UUID user = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user.toString(), null, List.of()));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        notes.writeTwo(a, b);
        assertThrows(IllegalStateException.class, () -> notes.writeAndFail(UUID.randomUUID()));

        assertTrue(ctx.listeners().contains(audit));
        List<RevisionRow> revisions = ctx.selectFrom(REVISION).fetch();
        assertEquals(1, revisions.size(), "the rolled back transaction left no revision");
        assertEquals(user, revisions.get(0).userId());
        List<NoteAudRow> history = ctx.selectFrom(NOTE_AUD).orderBy(NOTE_AUD.TITLE.asc()).fetch();
        assertEquals(List.of("A:0:text", "B:0:null"), history.stream().map(h -> h.title() + ":" + h.revtype() + ":" + h.body()).toList());
    }
}
