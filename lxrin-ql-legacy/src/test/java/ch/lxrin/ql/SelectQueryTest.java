package ch.lxrin.ql;

import ch.lxrin.ql.TestTables.OrderTable;
import ch.lxrin.ql.TestTables.PersonTable;
import ch.lxrin.ql.bind.BindMap;
import ch.lxrin.ql.bind.Binds;
import ch.lxrin.ql.exec.SqlExecutor;
import ch.lxrin.ql.query.RenderedSql;
import ch.lxrin.ql.query.SelectQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static ch.lxrin.ql.LxrinQL.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SelectQueryTest {

    private final PersonTable p = new PersonTable();
    private final OrderTable o = new OrderTable();
    private SqlExecutor executor;

    @BeforeEach
    void setUp() {
        executor = mock(SqlExecutor.class);
    }

    // =========================================================================
    // SELECT list, FROM, JOIN
    // =========================================================================

    @Test
    void selectAll() {
        assertEquals("SELECT * FROM MY_TABLE t", createContribution(Object[].class).from("MY_TABLE t").buildSql());
        assertEquals("SELECT * FROM PERSON p", selectFrom(p).buildSql());
    }

    @Test
    void selectColumnsAndAliases() {
        String sql = select(p.personNr, p.lastName.as("name"), count().as("cnt"))
                .select(as("t.X", "x"), "t.Y")
                .from(p)
                .buildSql();
        assertEquals("SELECT p.PERSON_NR, p.LAST_NAME AS name, count(*) AS cnt, t.X AS x, t.Y FROM PERSON p", sql);
    }

    @Test
    void selectExpandsColumnCollections() {
        assertEquals("SELECT p.PERSON_NR, p.FIRST_NAME, p.LAST_NAME, p.STATUS, p.AGE FROM PERSON p",
                select(p.columns()).from(p).buildSql());
    }

    @Test
    void selectWithoutFrom() {
        assertEquals("SELECT now()", select(now()).buildSql());
    }

    @Test
    void emptyStatementIsRejected() {
        assertThrows(IllegalStateException.class, () -> createContribution(Object[].class).buildSql());
    }

    @Test
    void distinctAndDistinctOn() {
        assertEquals("SELECT DISTINCT p.STATUS FROM PERSON p", selectDistinct(p.status).from(p).buildSql());
        assertEquals("SELECT DISTINCT ON (o.PERSON_NR) o.PERSON_NR, o.TOTAL FROM ORDERS o ORDER BY o.PERSON_NR ASC, o.CREATED_AT DESC",
                select(o.personNr, o.total).distinctOn(o.personNr).from(o)
                        .orderBy(o.personNr.asc(), o.createdAt.desc()).buildSql());
    }

    @Test
    void joins() {
        PersonTable manager = new PersonTable("m");
        String sql = select(p.lastName, o.total)
                .from(p)
                .join(o, eq(o.personNr, p.personNr))
                .leftJoin(manager, eq(manager.personNr, p.personNr), eq(manager.status, inline("A")))
                .rightJoin("X x", "x.ID = p.PERSON_NR")
                .fullJoin("Y y", "y.ID = p.PERSON_NR")
                .innerJoin("Z z", "z.ID = p.PERSON_NR")
                .crossJoin("W w")
                .naturalJoin("V")
                .joinUsing("U u", "PERSON_NR")
                .leftJoinUsing("T t", "A", "B")
                .join("LEFT JOIN ADDRESS a ON a.PERSON_NR = p.PERSON_NR")
                .buildSql();
        assertEquals("SELECT p.LAST_NAME, o.TOTAL FROM PERSON p"
                + " JOIN ORDERS o ON o.PERSON_NR = p.PERSON_NR"
                + " LEFT JOIN PERSON m ON m.PERSON_NR = p.PERSON_NR AND m.STATUS = 'A'"
                + " RIGHT JOIN X x ON x.ID = p.PERSON_NR"
                + " FULL JOIN Y y ON y.ID = p.PERSON_NR"
                + " INNER JOIN Z z ON z.ID = p.PERSON_NR"
                + " CROSS JOIN W w"
                + " NATURAL JOIN V"
                + " JOIN U u USING (PERSON_NR)"
                + " LEFT JOIN T t USING (A, B)"
                + " LEFT JOIN ADDRESS a ON a.PERSON_NR = p.PERSON_NR", sql);
    }

    @Test
    void lateralJoinAndDerivedTable() {
        SelectQuery<Object[]> lastOrder = select(o.total).from(o)
                .where(eq(o.personNr, p.personNr))
                .orderBy(o.createdAt.desc()).limit(1);
        String sql = select(p.lastName, "lo.TOTAL")
                .from(p)
                .leftJoin(lateral(lastOrder.as("lo")))
                .buildSql();
        assertEquals("SELECT p.LAST_NAME, lo.TOTAL FROM PERSON p LEFT JOIN LATERAL (SELECT o.TOTAL FROM ORDERS o"
                + " WHERE o.PERSON_NR = p.PERSON_NR ORDER BY o.CREATED_AT DESC LIMIT 1) AS lo ON TRUE", sql);

        assertEquals("SELECT x.n FROM generate_series(1, 3) AS x(n)",
                select("x.n").from(generateSeries(inline(1), inline(3)).as("x(n)")).buildSql());
    }

    // =========================================================================
    // WHERE
    // =========================================================================

    @Test
    void where_adjacentConditionsAreJoinedWithAnd() {
        assertEquals("SELECT * FROM PERSON p WHERE p.STATUS = :lq0 AND p.AGE >= :lq1",
                selectFrom(p).where(eq(p.status, val("A")), ge(p.age, 18)).buildSql());
    }

    @Test
    void where_explicitOperatorsAreKept() {
        assertEquals("SELECT * FROM PERSON p WHERE p.STATUS = :s AND p.AGE > :a OR p.AGE IS NULL",
                selectFrom(p).where(eq(p.status, ":s"), and(), gt(p.age, ":a"), or(), isNull(p.age)).buildSql());
    }

    @Test
    void where_multipleCallsWrapGroupsContainingOr() {
        String sql = selectFrom(p)
                .where(eq(p.status, ":a"), or(), eq(p.status, ":b"))
                .where(gt(p.age, ":age"))
                .buildSql();
        assertEquals("SELECT * FROM PERSON p WHERE (p.STATUS = :a OR p.STATUS = :b) AND p.AGE > :age", sql);
    }

    @Test
    void whereIf_skipsDisabledConditions() {
        String name = null;
        String sql = selectFrom(p)
                .whereIf(name != null, ilike(p.lastName, val(name)))
                .whereIf(true, ge(p.age, 18))
                .buildSql();
        assertEquals("SELECT * FROM PERSON p WHERE p.AGE >= :lq0", sql);
    }

    @Test
    void autoParametersAreCollected() {
        RenderedSql r = selectFrom(p).where(eq(p.status, val("ACTIVE")), in(p.age, List.of(18, 19))).build();
        assertEquals("SELECT * FROM PERSON p WHERE p.STATUS = :lq0 AND p.AGE IN (:lq1, :lq2)", r.sql());
        assertEquals("ACTIVE", r.binds().get("lq0"));
        assertEquals(18, r.binds().get("lq1"));
        assertEquals(19, r.binds().get("lq2"));
    }

    @Test
    void bindsObjectAndNamedBindsAreMerged() {
        Binds b = new Binds();
        SelectQuery<Object[]> q = selectFrom(p).where(eq(p.status, b.setString("A"))).bind(b).bind("x", 5);
        b.setLong("late", 7L);    // Binds are read at render time
        BindMap binds = q.getBinds();
        assertEquals("A", binds.get("p0"));
        assertEquals(5, binds.get("x"));
        assertEquals(7L, binds.get("late"));
    }

    @Test
    void subQueryBindsArePropagated() {
        SelectQuery<Object[]> sub = select(o.personNr).from(o).where(gt(o.total, ":minTotal")).bind("minTotal", 100);
        RenderedSql r = selectFrom(p).where(in(p.personNr, sub), eq(p.status, val("A"))).build();
        assertEquals("SELECT * FROM PERSON p WHERE p.PERSON_NR IN (SELECT o.PERSON_NR FROM ORDERS o WHERE o.TOTAL > :minTotal)"
                + " AND p.STATUS = :lq0", r.sql());
        assertEquals(100, r.binds().get("minTotal"));
        assertEquals("A", r.binds().get("lq0"));
    }

    // =========================================================================
    // GROUP BY, HAVING, WINDOW, ORDER, LIMIT
    // =========================================================================

    @Test
    void groupByHaving() {
        String sql = select(o.personNr, sum(o.total).as("total"))
                .from(o)
                .groupBy(o.personNr)
                .having(gt(sum(o.total), 1000))
                .buildSql();
        assertEquals("SELECT o.PERSON_NR, sum(o.TOTAL) AS total FROM ORDERS o GROUP BY o.PERSON_NR HAVING sum(o.TOTAL) > :lq0", sql);
    }

    @Test
    void advancedGrouping() {
        assertEquals("SELECT * FROM ORDERS o GROUP BY ROLLUP (o.STATUS, o.PERSON_NR)",
                selectFrom(o).groupBy(rollup(o.status, o.personNr)).buildSql());
        assertEquals("SELECT * FROM ORDERS o GROUP BY CUBE (o.STATUS)", selectFrom(o).groupBy(cube(o.status)).buildSql());
        assertEquals("SELECT * FROM ORDERS o GROUP BY GROUPING SETS ((o.STATUS, o.PERSON_NR), (o.STATUS), ())",
                selectFrom(o).groupBy(groupingSets(groupingSet(o.status, o.personNr), groupingSet(o.status), groupingSet())).buildSql());
    }

    @Test
    void namedWindow() {
        String sql = select(o.orderId, rowNumber().over("w"), sum(o.total).over("w"))
                .from(o)
                .window("w", partitionBy(o.personNr).orderBy(o.createdAt.asc()))
                .buildSql();
        assertEquals("SELECT o.ORDER_ID, row_number() OVER w, sum(o.TOTAL) OVER w FROM ORDERS o"
                + " WINDOW w AS (PARTITION BY o.PERSON_NR ORDER BY o.CREATED_AT ASC)", sql);
    }

    @Test
    void orderLimitOffsetAndPaging() {
        assertEquals("SELECT * FROM PERSON p ORDER BY p.LAST_NAME ASC NULLS LAST, p.AGE DESC LIMIT 10 OFFSET 20",
                selectFrom(p).orderBy(p.lastName.asc().nullsLast(), desc(p.age)).limit(10).offset(20).buildSql());
        assertEquals("SELECT * FROM PERSON p LIMIT 25 OFFSET 50", selectFrom(p).page(2, 25).buildSql());
        assertEquals("SELECT * FROM PERSON p LIMIT :lq0", selectFrom(p).limit(val(5)).buildSql());
        assertEquals("SELECT * FROM PERSON p ORDER BY p.AGE DESC FETCH FIRST 3 ROWS WITH TIES",
                selectFrom(p).orderBy(p.age.desc()).limitWithTies(3).buildSql());
    }

    @Test
    void rowLocking() {
        assertEquals("SELECT * FROM PERSON p FOR UPDATE", selectFrom(p).forUpdate().buildSql());
        assertEquals("SELECT * FROM PERSON p FOR NO KEY UPDATE NOWAIT", selectFrom(p).forNoKeyUpdate().nowait().buildSql());
        assertEquals("SELECT * FROM PERSON p LIMIT 1 FOR UPDATE OF p SKIP LOCKED",
                selectFrom(p).limit(1).forUpdate().of(p).skipLocked().buildSql());
        assertEquals("SELECT * FROM PERSON p FOR SHARE", selectFrom(p).forShare().buildSql());
        assertEquals("SELECT * FROM PERSON p FOR KEY SHARE", selectFrom(p).forKeyShare().buildSql());
        assertThrows(IllegalStateException.class, () -> selectFrom(p).nowait());
    }

    // =========================================================================
    // Set operations and CTEs
    // =========================================================================

    @Test
    void setOperations() {
        String sql = select(p.personNr).from(p)
                .union(select(o.personNr).from(o))
                .unionAll(select(inline(1)))
                .intersect(select(inline(2)))
                .intersectAll(select(inline(3)))
                .except(select(inline(4)))
                .exceptAll(select(inline(5)))
                .orderBy(inline(1))
                .buildSql();
        assertEquals("SELECT p.PERSON_NR FROM PERSON p UNION (SELECT o.PERSON_NR FROM ORDERS o) UNION ALL (SELECT 1)"
                + " INTERSECT (SELECT 2) INTERSECT ALL (SELECT 3) EXCEPT (SELECT 4) EXCEPT ALL (SELECT 5) ORDER BY 1", sql);
    }

    @Test
    void commonTableExpressions() {
        String sql = select("t.PERSON_NR", "t.total")
                .with("totals", select(o.personNr, sum(o.total).as("total")).from(o).groupBy(o.personNr))
                .withMaterialized("big", select(inline(1)))
                .from("totals t")
                .buildSql();
        assertEquals("WITH totals AS (SELECT o.PERSON_NR, sum(o.TOTAL) AS total FROM ORDERS o GROUP BY o.PERSON_NR),"
                + " big AS MATERIALIZED (SELECT 1) SELECT t.PERSON_NR, t.total FROM totals t", sql);
    }

    @Test
    void recursiveCte() {
        String sql = select("n")
                .withRecursive("t(n)", select(inline(1)).unionAll(select("n + 1").from("t").where(lt("n", inline(10)))))
                .from("t")
                .buildSql();
        assertEquals("WITH RECURSIVE t(n) AS (SELECT 1 UNION ALL (SELECT n + 1 FROM t WHERE n < 10)) SELECT n FROM t", sql);
    }

    // =========================================================================
    // Execution and mapping
    // =========================================================================

    public static class PersonBean {
        private Long personNr;
        private String lastName;

        public Long getPersonNr() { return personNr; }
        public void setPersonNr(Long personNr) { this.personNr = personNr; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
    }

    public record PersonRecord(long personNr, String lastName) {}

    @Test
    void multiple_mapsBeansByColumnAlias() {
        when(executor.select(anyString(), any())).thenReturn(new Object[][]{{1, "Lovelace"}, {2L, "Turing"}});
        List<PersonBean> result = createContribution(PersonBean.class)
                .select(p.personNr, p.lastName).from(p).executor(executor).multiple();
        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getPersonNr());
        assertEquals("Turing", result.get(1).getLastName());
    }

    @Test
    void multiple_mapsRecordsByPosition() {
        when(executor.select(anyString(), any())).thenReturn(new Object[][]{{new BigDecimal("7"), "Hopper"}});
        List<PersonRecord> result = select(PersonRecord.class, p.personNr, p.lastName).from(p).executor(executor).multiple();
        assertEquals(new PersonRecord(7L, "Hopper"), result.get(0));
    }

    @Test
    void single_mapsScalarAndConvertsNumbers() {
        when(executor.select(anyString(), any())).thenReturn(new Object[][]{{42}});
        Long count = createContribution(Long.class).select(count()).from(p).executor(executor).single();
        assertEquals(42L, count);
    }

    @Test
    void single_returnsNullAndOptionalEmptyWhenNoRows() {
        when(executor.select(anyString(), any())).thenReturn(new Object[0][]);
        assertNull(createContribution(String.class).select(p.lastName).from(p).executor(executor).single());
        assertTrue(createContribution(String.class).select(p.lastName).from(p).executor(executor).optional().isEmpty());
    }

    @Test
    void mapWith_usesCustomMapper() {
        when(executor.select(anyString(), any())).thenReturn(new Object[][]{{"a", "b"}});
        List<String> result = createContribution(String.class)
                .select(p.firstName, p.lastName).from(p)
                .mapWith(row -> row[0] + " " + row[1])
                .executor(executor).multiple();
        assertEquals(List.of("a b"), result);
    }

    @Test
    void multiple_passesSqlAndBindsToExecutor() {
        when(executor.select(anyString(), any())).thenReturn(new Object[0][]);
        select(p.personNr).from(p).where(eq(p.status, val("A"))).executor(executor).multiple();
        ArgumentCaptor<BindMap> binds = ArgumentCaptor.forClass(BindMap.class);
        verify(executor).select(org.mockito.ArgumentMatchers.eq("SELECT p.PERSON_NR FROM PERSON p WHERE p.STATUS = :lq0"), binds.capture());
        assertEquals("A", binds.getValue().get("lq0"));
    }

    @Test
    void fetchCountAndExists() {
        when(executor.select(org.mockito.ArgumentMatchers.eq("SELECT count(*) FROM (SELECT * FROM PERSON p WHERE p.AGE > :lq0) q"), any()))
                .thenReturn(new Object[][]{{3L}});
        when(executor.select(org.mockito.ArgumentMatchers.eq("SELECT EXISTS (SELECT * FROM PERSON p WHERE p.AGE > :lq0)"), any()))
                .thenReturn(new Object[][]{{true}});
        assertEquals(3L, selectFrom(p).where(gt(p.age, 1)).executor(executor).fetchCount());
        assertTrue(selectFrom(p).where(gt(p.age, 1)).executor(executor).fetchExists());
    }

    @Test
    void defaultExecutorIsUsed() {
        when(executor.select(anyString(), any())).thenReturn(new Object[][]{{1L}});
        try {
            setDefaultExecutor(executor);
            assertEquals(1L, createContribution(Long.class).select(count()).from(p).single());
        } finally {
            setDefaultExecutor(null);
        }
    }

    @Test
    void missingExecutorGivesHelpfulError() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> selectFrom(p).multiple());
        assertTrue(e.getMessage().contains("setDefaultExecutor"));
    }
}
