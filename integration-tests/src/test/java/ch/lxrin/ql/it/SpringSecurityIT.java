package ch.lxrin.ql.it;

import ch.lxrin.ql.QL;
import ch.lxrin.ql.it.security.CurrentUser;
import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.spi.ColumnConvention;
import ch.lxrin.ql.spi.ColumnConventions;
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
import static ch.lxrin.ql.it.db.Tables.DOCUMENT;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code created_by} and {@code updated_by} filled with the user's UUID from the Spring Security context, and
 * {@link QL} used in a Spring bean without injecting the query context.
 */
@SpringBootTest(classes = SpringSecurityIT.App.class)
class SpringSecurityIT {

    @SpringBootApplication(proxyBeanMethods = false)
    static class App {
        @Bean
        ColumnConvention<UUID> createdBy() {
            return ColumnConventions.createdBy("created_by", UUID.class, CurrentUser::id);
        }

        @Bean
        ColumnConvention<UUID> updatedBy() {
            return ColumnConventions.updatedBy("updated_by", UUID.class, CurrentUser::id);
        }

        @Bean
        DocumentService documentService() {
            return new DocumentService();
        }
    }

    /** A service that uses {@code QL} directly: the context comes from {@code lxrin-ql-spring}. */
    @Service
    static class DocumentService {

        record Audit(String title, UUID createdBy, UUID updatedBy) {}

        @Transactional
        public void create(UUID id, String title) {
            QL.createInsert(DOCUMENT, (c, b) -> c.set(DOCUMENT.ID, b.setUuid(id)).set(DOCUMENT.TITLE, b.setString(title))).execute();
        }

        @Transactional
        public long rename(UUID id, String title) {
            return QL.createUpdate(DOCUMENT, (c, b) -> c
                            .set(DOCUMENT.TITLE, b.setString(title))
                            .where(eq(DOCUMENT.ID, b.setUuid(id))))
                    .execute();
        }

        public Audit audit(UUID id) {
            return QL.createContribution(Audit.class, DOCUMENT, (c, b) -> c
                            .select(col(DOCUMENT.TITLE), col(DOCUMENT.CREATED_BY), col(DOCUMENT.UPDATED_BY))
                            .where(eq(DOCUMENT.ID, b.setUuid(id))))
                    .fetchOne();
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
    DocumentService documents;
    @Autowired
    QueryContext ctx;

    private static void signIn(UUID user) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user.toString(), null, List.of()));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void whoColumnsComeFromTheSecurityContext() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID id = UUID.randomUUID();

        signIn(alice);
        documents.create(id, "Policy");
        assertEquals(new DocumentService.Audit("Policy", alice, alice), documents.audit(id));

        signIn(bob);
        assertEquals(1, documents.rename(id, "Policy v2"));
        assertEquals(new DocumentService.Audit("Policy v2", alice, bob), documents.audit(id), "created_by is kept on update");

        SecurityContextHolder.clearContext();
        UUID system = UUID.randomUUID();
        documents.create(system, "Nightly report");
        assertEquals(new DocumentService.Audit("Nightly report", null, null), documents.audit(system));
        assertSame(ctx, QueryContext.getDefault(), "QL runs on the Spring context");
    }
}
