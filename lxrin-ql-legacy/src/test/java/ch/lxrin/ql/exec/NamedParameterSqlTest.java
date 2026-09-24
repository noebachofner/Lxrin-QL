package ch.lxrin.ql.exec;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NamedParameterSqlTest {

    @Test
    void replacesNamedPlaceholdersInOrder() {
        NamedParameterSql p = NamedParameterSql.parse("SELECT * FROM t WHERE a = :a AND b = :b OR a = :a", Map.of("a", 1, "b", "x"));
        assertEquals("SELECT * FROM t WHERE a = ? AND b = ? OR a = ?", p.sql());
        assertEquals(List.of(1, "x", 1), p.values());
    }

    @Test
    void leavesCastsStringsCommentsAndQuotedIdentifiersAlone() {
        String sql = "SELECT a::text, ':no', \"col:x\", E'it\\'s :no', $$ :no $$, $tag$ :no $tag$ -- :no\n"
                + "/* :no */ FROM t WHERE x = :yes";
        NamedParameterSql p = NamedParameterSql.parse(sql, Map.of("yes", 5));
        assertEquals(sql.replace(":yes", "?"), p.sql());
        assertEquals(List.of(5), p.values());
    }

    @Test
    void expandsCollections() {
        NamedParameterSql p = NamedParameterSql.parse("x IN (:ids) AND y IN (:none)", Map.of("ids", List.of(1, 2, 3), "none", List.of()));
        assertEquals("x IN (?, ?, ?) AND y IN (NULL)", p.sql());
        assertEquals(List.of(1, 2, 3), p.values());
    }

    @Test
    void escapesQuestionMarks() {
        assertEquals("SELECT data ?? 'k' FROM t", NamedParameterSql.parse("SELECT data ? 'k' FROM t", Map.of()).sql());
    }

    @Test
    void positionalDollarParametersAreNotDollarQuotes() {
        assertEquals("SELECT $1, ?", NamedParameterSql.parse("SELECT $1, :a", Map.of("a", 1)).sql());
    }

    @Test
    void missingBindIsReported() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> NamedParameterSql.parse("SELECT :missing", Map.of()));
        assertTrue(e.getMessage().contains(":missing"));
    }

    @Test
    void nullValuesAreAllowed() {
        java.util.HashMap<String, Object> binds = new java.util.HashMap<>();
        binds.put("n", null);
        assertEquals(java.util.Collections.singletonList(null), NamedParameterSql.parse(":n", binds).values());
    }
}
