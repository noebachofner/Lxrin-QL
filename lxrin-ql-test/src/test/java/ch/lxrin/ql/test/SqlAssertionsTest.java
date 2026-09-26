package ch.lxrin.ql.test;

import ch.lxrin.ql.TestSchema.Role;
import ch.lxrin.ql.render.RenderedSql;
import ch.lxrin.ql.runtime.QueryContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static ch.lxrin.ql.TestSchema.UsersTable.USERS;
import static ch.lxrin.ql.dsl.Dsl.*;
import static ch.lxrin.ql.test.MockExecutor.row;
import static ch.lxrin.ql.test.SqlAssertions.assertThatSql;
import static org.junit.jupiter.api.Assertions.*;

class SqlAssertionsTest {

    @Test
    void assertionsOnStatementsAndParts() {
        assertThatSql(select(USERS.NAME).from(USERS).where(USERS.EMAIL.endsWith("@x")))
                .isEqualTo("SELECT users.name FROM users WHERE users.email LIKE ?")
                .contains("LIKE").doesNotContain("ILIKE").hasBinds("%@x").isConsistent();
        assertThatSql(update(USERS).set(USERS.ROLE, Role.ADMIN).allRows()).isEqualTo("UPDATE users SET role = ?").hasBinds(Role.ADMIN);
        assertThatSql(deleteFrom(USERS).where(USERS.ACTIVE).returning(USERS.ID)).contains("RETURNING users.id").hasNoBinds();
        assertThatSql(USERS.TAGS.overlaps(new String[]{"a"})).hasBinds((Object) new String[]{"a"});
        assertThatSql(selectFrom(USERS).statement()).contains("FROM users");
        AssertionError e = assertThrows(AssertionError.class, () -> assertThatSql(select(USERS.NAME)).isEqualTo("x"));
        assertTrue(e.getMessage().contains("SELECT users.name"));
        assertThrows(AssertionError.class, () -> assertThatSql(select(USERS.NAME)).hasBinds(1));
        assertThrows(AssertionError.class, () -> assertThatSql(select(USERS.NAME)).contains("WHERE"));
        assertThrows(AssertionError.class, () -> assertThatSql(select(param(1))).hasNoBinds());
        assertThrows(AssertionError.class, () -> assertThatSql(new RenderedSql("? ?", List.of())).isConsistent());
    }

    @Test
    void mockExecutorScriptsAnswersAndRecordsStatements() {
        MockExecutor db = new MockExecutor();
        UUID id = UUID.randomUUID();
        db.whenSqlContains("FROM users").thenReturn(row(id, "Ada"));
        db.whenSqlContains("DELETE").thenAffect(3);
        db.whenSqlContains("TRUNCATE").thenThrow(new IllegalStateException("no"));
        db.whenSqlContains("WHERE users.active").times(1).thenReturn(row(id, "once"));
        QueryContext ctx = db.context();

        assertEquals("once", ctx.select(USERS.ID, USERS.NAME).from(USERS).where(USERS.ACTIVE).fetchOne().value2());
        assertEquals("Ada", ctx.select(USERS.ID, USERS.NAME).from(USERS).where(USERS.ACTIVE).fetchOne().value2());
        assertEquals(3, ctx.deleteFrom(USERS).allRows().execute());
        assertEquals(1, ctx.update(USERS).set(USERS.NAME, "x").allRows().execute());
        ch.lxrin.ql.error.LxrinQlException e = assertThrows(ch.lxrin.ql.error.LxrinQlException.class, () -> ctx.truncate(USERS).execute());
        assertInstanceOf(IllegalStateException.class, e.getCause());
        assertEquals(5, db.statements().size());
        assertThatSql(db.lastStatement()).isEqualTo("TRUNCATE users");
        db.reset();
        assertTrue(db.statements().isEmpty());
        assertThrows(AssertionError.class, db::lastStatement);
        assertTrue(ctx.select(USERS.ID).from(USERS).fetch().isEmpty());
    }
}
