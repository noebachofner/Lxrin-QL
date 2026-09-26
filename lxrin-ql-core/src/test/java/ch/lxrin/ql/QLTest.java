package ch.lxrin.ql;

import ch.lxrin.ql.dsl.Dsl;
import ch.lxrin.ql.dsl.Field;
import ch.lxrin.ql.dsl.Returning;
import ch.lxrin.ql.dsl.Select1;
import ch.lxrin.ql.dsl.Statements;
import ch.lxrin.ql.runtime.QueryContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import static ch.lxrin.ql.QL.*;
import static ch.lxrin.ql.TestSchema.UsersTable.USERS;
import static org.junit.jupiter.api.Assertions.*;

/** {@link QL} is the entry point; it and {@link Dsl} expose the same static API on the default context. */
class QLTest {

    private final RecordingExecutor db = new RecordingExecutor();

    @BeforeEach
    void setDefault() {
        QueryContext.setDefault(QueryContext.builder().executor(db).build());
    }

    @AfterEach
    void clearDefault() {
        QueryContext.setDefault(null);
    }

    private static Set<String> publicStatics(Class<?> type) {
        return Arrays.stream(type.getMethods())
                .filter(m -> Modifier.isStatic(m.getModifiers()) && Modifier.isPublic(m.getModifiers()))
                .map(Method::toGenericString)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void qlAndDslExposeTheSameStaticMethods() {
        Set<String> ql = publicStatics(QL.class);
        Set<String> dsl = publicStatics(Dsl.class);
        assertEquals(dsl, ql);
        Set<String> names = Arrays.stream(QL.class.getMethods()).filter(m -> Modifier.isStatic(m.getModifiers()))
                .map(Method::getName).collect(Collectors.toSet());
        for (String required : List.of("createContribution", "createInsert", "createUpdate", "createDelete", "createUpsert", "select",
                "selectFrom", "col", "eq", "and", "in", "isNull", "count", "coalesce", "param")) {
            assertTrue(names.contains(required), required);
        }
        assertTrue(ql.size() > 400, "conditions and the function catalog are inherited: " + ql.size());
    }

    @Test
    void entryPointsDeclareNothingOfTheirOwn() {
        for (Class<?> type : List.of(QL.class, Dsl.class)) {
            assertEquals(Statements.class, type.getSuperclass());
            assertTrue(Modifier.isFinal(type.getModifiers()));
            assertEquals(0, type.getDeclaredMethods().length, type + " must not declare methods; add them to Statements");
            assertTrue(Arrays.stream(type.getDeclaredConstructors()).allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        }
        assertFalse(Modifier.isFinal(Statements.class.getModifiers()));
        assertTrue(Arrays.stream(Statements.class.getDeclaredConstructors()).allMatch(c -> Modifier.isProtected(c.getModifiers())));
    }

    @Test
    void theTargetUsageRunsOnTheDefaultContext() {
        db.willReturn(new Object[] {"ada"});
        String name = QL.createContribution(String.class, USERS, (c, b) -> c
                        .select(col(USERS.NAME))
                        .where(and(
                                eq(USERS.EMAIL, b.setString("ada@example.org")),
                                in(USERS.NAME, "ada", "grace"),
                                isNull(USERS.DELETED_AT))))
                .fetchOne();
        assertEquals("ada", name);
        assertEquals("SELECT users.name FROM users WHERE (users.email = ? AND users.name = ANY(?) AND users.deleted_at IS NULL)",
                db.lastSql());

        db.willAffect(1);
        UUID id = UUID.randomUUID();
        long changed = QL.createUpdate(USERS, (c, b) -> c
                        .set(USERS.EMAIL, b.setString("new@example.org"))
                        .where(eq(USERS.ID, id)))
                .execute();
        assertEquals(1, changed);
        assertEquals("UPDATE users SET email = ? WHERE users.id = ?", db.lastSql());
    }

    @Test
    void colKeepsTheFieldAndItsType() {
        Select1<String> plain = select(col(USERS.NAME));
        Field<UUID> id = col(USERS.ID);
        assertSame(USERS.ID, id);
        assertEquals(select(USERS.NAME).from(USERS).render().sql(), plain.from(USERS).render().sql());
        assertEquals("SELECT users.id, users.name FROM users",
                QL.createContribution(ch.lxrin.ql.dsl.Row.class, USERS, (c, b) -> c.select(col(USERS.ID), col(USERS.NAME))).render().sql());
        Returning<UUID> returning = insertInto(USERS).set(USERS.NAME, "ada").returning(col(USERS.ID));
        assertEquals("INSERT INTO users (name) VALUES (?) RETURNING users.id", returning.render().sql());
        Returning<UUID> created = QL.createInsert(USERS, (c, b) -> c.set(USERS.NAME, b.setString("ada"))).returning(col(USERS.ID));
        assertEquals(returning.render().sql(), created.render().sql());
        assertThrows(IllegalArgumentException.class, () -> col(null));
    }
}
