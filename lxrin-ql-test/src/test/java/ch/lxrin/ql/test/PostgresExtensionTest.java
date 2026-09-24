package ch.lxrin.ql.test;

import ch.lxrin.ql.dsl.Sql;
import ch.lxrin.ql.runtime.Propagation;
import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.schema.AdHocTable;
import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.types.SqlTypes;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

@LxrinPostgresTest(migrations = "classpath:db/test-migration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PostgresExtensionTest {

    static final AdHocTable ITEMS = Sql.table("items");
    static final Column<String> NAME = ITEMS.field("name", SqlTypes.TEXT);

    @Test
    @Order(1)
    void writesAreRolledBackAfterEachTest(QueryContext ctx, DataSource dataSource) {
        assertNotNull(dataSource);
        assertSame(ctx, QueryContext.getDefault());
        ctx.insertInto(ITEMS).set(NAME, "first").execute();
        assertEquals(1L, ctx.selectCount().from(ITEMS).fetchOne());
    }

    @Test
    @Order(2)
    void theNextTestStartsClean(QueryContext ctx) {
        assertEquals(0L, ctx.selectCount().from(ITEMS).fetchOne());
    }

    @Test
    @Order(3)
    void transactionsBecomeSavepoints(QueryContext ctx) {
        ctx.insertInto(ITEMS).set(NAME, "kept").execute();
        assertThrows(IllegalStateException.class, () -> ctx.transaction(Propagation.NESTED, () -> {
            ctx.insertInto(ITEMS).set(NAME, "undone").execute();
            throw new IllegalStateException("rollback to savepoint");
        }));
        ctx.transaction(() -> ctx.insertInto(ITEMS).set(NAME, "committed").execute());
        assertEquals(2L, ctx.selectCount().from(ITEMS).fetchOne());
        assertTrue(ctx.transaction(() -> ctx.currentTransaction().isPresent()));
    }
}
