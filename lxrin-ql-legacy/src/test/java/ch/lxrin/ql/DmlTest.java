package ch.lxrin.ql;

import ch.lxrin.ql.TestTables.OrderTable;
import ch.lxrin.ql.TestTables.PersonTable;
import ch.lxrin.ql.exec.SqlExecutor;
import ch.lxrin.ql.query.RenderedSql;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ch.lxrin.ql.LxrinQL.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DmlTest {

    private final PersonTable p = new PersonTable();
    private final OrderTable o = new OrderTable();

    // =========================================================================
    // INSERT
    // =========================================================================

    @Test
    void insert_withSet() {
        RenderedSql r = insertInto(p).set(p.firstName, val("Ada")).set(p.lastName, val("Lovelace")).set(p.age, defaultValue()).build();
        assertEquals("INSERT INTO PERSON AS p (FIRST_NAME, LAST_NAME, AGE) VALUES (:lq0, :lq1, DEFAULT)", r.sql());
        assertEquals("Ada", r.binds().get("lq0"));
    }

    @Test
    void insert_multiRowValues() {
        String sql = insertInto(p).columns(p.firstName, p.lastName)
                .values(val("Ada"), val("Lovelace"))
                .values(val("Alan"), val("Turing"))
                .buildSql();
        assertEquals("INSERT INTO PERSON AS p (FIRST_NAME, LAST_NAME) VALUES (:lq0, :lq1), (:lq2, :lq3)", sql);
    }

    @Test
    void insert_valueCountMustMatchColumns() {
        assertThrows(IllegalStateException.class,
                () -> insertInto(p).columns(p.firstName, p.lastName).values(val("x")).buildSql());
        assertThrows(IllegalStateException.class, () -> insertInto(p).set(p.age, 1).columns(p.lastName));
        assertThrows(IllegalStateException.class, () -> insertInto(p).buildSql());
    }

    @Test
    void insert_select() {
        String sql = insertInto("PERSON_ARCHIVE").columns("PERSON_NR", "LAST_NAME")
                .select(select(p.personNr, p.lastName).from(p).where(eq(p.status, inline("OLD"))))
                .buildSql();
        assertEquals("INSERT INTO PERSON_ARCHIVE (PERSON_NR, LAST_NAME) SELECT p.PERSON_NR, p.LAST_NAME FROM PERSON p WHERE p.STATUS = 'OLD'", sql);
    }

    @Test
    void insert_defaultValuesAndOverriding() {
        assertEquals("INSERT INTO PERSON AS p DEFAULT VALUES", insertInto(p).defaultValues().buildSql());
        assertEquals("INSERT INTO PERSON AS p (PERSON_NR) OVERRIDING SYSTEM VALUE VALUES (:lq0)",
                insertInto(p).set(p.personNr, 5L).overridingSystemValue().buildSql());
    }

    @Test
    void insert_onConflictDoNothing() {
        assertEquals("INSERT INTO PERSON AS p (PERSON_NR) VALUES (:lq0) ON CONFLICT DO NOTHING",
                insertInto(p).set(p.personNr, 1L).doNothing().buildSql());
        assertEquals("INSERT INTO PERSON AS p (PERSON_NR) VALUES (:lq0) ON CONFLICT (PERSON_NR) DO NOTHING",
                insertInto(p).set(p.personNr, 1L).onConflict(p.personNr).doNothing().buildSql());
        assertEquals("INSERT INTO PERSON AS p (PERSON_NR) VALUES (:lq0) ON CONFLICT ON CONSTRAINT person_pk DO NOTHING",
                insertInto(p).set(p.personNr, 1L).onConflictOnConstraint("person_pk").doNothing().buildSql());
    }

    @Test
    void insert_upsert() {
        String sql = insertInto(p)
                .set(p.personNr, 1L).set(p.lastName, val("X")).set(p.age, 3)
                .onConflict(p.personNr).onConflictWhere(isNotNull(p.status))
                .doUpdateSetExcluded(p.lastName)
                .doUpdateSet(p.age, p.age.plus(raw("EXCLUDED.AGE")))
                .doUpdateWhere(ne(p.status, inline("LOCKED")))
                .returning(p.personNr, p.age)
                .buildSql();
        assertEquals("INSERT INTO PERSON AS p (PERSON_NR, LAST_NAME, AGE) VALUES (:lq0, :lq1, :lq2)"
                + " ON CONFLICT (PERSON_NR) WHERE p.STATUS IS NOT NULL"
                + " DO UPDATE SET LAST_NAME = EXCLUDED.LAST_NAME, AGE = (p.AGE + EXCLUDED.AGE)"
                + " WHERE p.STATUS <> 'LOCKED' RETURNING p.PERSON_NR, p.AGE", sql);
    }

    @Test
    void insert_onConflictWithoutActionIsRejected() {
        assertThrows(IllegalStateException.class, () -> insertInto(p).set(p.personNr, 1L).onConflict(p.personNr).buildSql());
        assertThrows(IllegalStateException.class, () -> insertInto(p).doUpdateSet(p.age, 1));
    }

    @Test
    void insert_returningSingleUsesSelectAndMapsValue() {
        SqlExecutor executor = mock(SqlExecutor.class);
        when(executor.select(anyString(), any())).thenReturn(new Object[][]{{99}});
        Long id = insertInto(p).set(p.lastName, val("X")).returning(p.personNr).executor(executor).single(Long.class);
        assertEquals(99L, id);
    }

    @Test
    void insert_executeReturnsRowCount() {
        SqlExecutor executor = mock(SqlExecutor.class);
        when(executor.execute(anyString(), any())).thenReturn(1);
        assertEquals(1, insertInto(p).set(p.lastName, val("X")).executor(executor).execute());
    }

    @Test
    void fetchWithoutReturningIsRejected() {
        assertThrows(IllegalStateException.class, () -> insertInto(p).set(p.age, 1).single(Long.class));
    }

    // =========================================================================
    // UPDATE
    // =========================================================================

    @Test
    void update_setWhereReturning() {
        String sql = update(p)
                .set(p.status, val("INACTIVE"))
                .set(p.age, p.age.plus(1))
                .setIf(false, p.lastName, val("never"))
                .where(lt(p.age, 18))
                .returning(p.personNr)
                .buildSql();
        assertEquals("UPDATE PERSON p SET STATUS = :lq0, AGE = (p.AGE + :lq1) WHERE p.AGE < :lq2 RETURNING p.PERSON_NR", sql);
    }

    @Test
    void update_fromAndMultiColumnSubquery() {
        String sql = update(o)
                .set(new Object[]{o.status, o.total}, select(p.status, p.age).from(p).where(eq(p.personNr, o.personNr)))
                .from("PERSON x")
                .where(eq(o.personNr, "x.PERSON_NR"))
                .buildSql();
        assertEquals("UPDATE ORDERS o SET (STATUS, TOTAL) = (SELECT p.STATUS, p.AGE FROM PERSON p WHERE p.PERSON_NR = o.PERSON_NR)"
                + " FROM PERSON x WHERE o.PERSON_NR = x.PERSON_NR", sql);
    }

    @Test
    void update_withoutWhereNeedsConfirmation() {
        assertThrows(IllegalStateException.class, () -> update(p).set(p.age, 1).buildSql());
        assertEquals("UPDATE PERSON p SET AGE = :lq0", update(p).set(p.age, 1).allRows().buildSql());
        assertThrows(IllegalStateException.class, () -> update(p).allRows().buildSql());
    }

    @Test
    void update_whereIfLazy() {
        String name = null;
        assertEquals("UPDATE PERSON p SET AGE = :lq0 WHERE p.AGE > :lq1",
                update(p).set(p.age, 1).whereIf(name != null, () -> eq(p.lastName, name)).where(gt(p.age, 0)).buildSql());
    }

    // =========================================================================
    // DELETE / TRUNCATE / data-modifying CTE
    // =========================================================================

    @Test
    void delete_usingWhereReturning() {
        String sql = deleteFrom(o).using(p)
                .where(eq(o.personNr, p.personNr), eq(p.status, inline("CLOSED")))
                .returning(o.orderId)
                .buildSql();
        assertEquals("DELETE FROM ORDERS o USING PERSON p WHERE o.PERSON_NR = p.PERSON_NR AND p.STATUS = 'CLOSED' RETURNING o.ORDER_ID", sql);
    }

    @Test
    void delete_withoutWhereNeedsConfirmation() {
        assertThrows(IllegalStateException.class, () -> deleteFrom(p).buildSql());
        assertEquals("DELETE FROM PERSON p", deleteFrom(p).allRows().buildSql());
    }

    @Test
    void truncateTables() {
        assertEquals("TRUNCATE PERSON, ORDERS RESTART IDENTITY CASCADE", truncate(p, o).restartIdentity().cascade().buildSql());
    }

    @Test
    void dataModifyingCte() {
        String sql = select(count())
                .with("moved", deleteFrom(o).where(lt(o.createdAt, raw("now() - INTERVAL '1 year'"))).returning(o.all()))
                .from("moved")
                .buildSql();
        assertEquals("WITH moved AS (DELETE FROM ORDERS o WHERE o.CREATED_AT < now() - INTERVAL '1 year' RETURNING o.*)"
                + " SELECT count(*) FROM moved", sql);
    }

    @Test
    void returningMapsMultipleRows() {
        SqlExecutor executor = mock(SqlExecutor.class);
        when(executor.select(anyString(), any())).thenReturn(new Object[][]{{1L}, {2L}});
        List<Long> ids = deleteFrom(o).where(isNull(o.status)).returning(o.orderId).executor(executor).multiple(Long.class);
        assertEquals(List.of(1L, 2L), ids);
    }
}
