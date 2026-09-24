package ch.lxrin.ql.dsl;

import ch.lxrin.ql.types.SqlTypes;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

import static ch.lxrin.ql.RenderTestSupport.assertSql;
import static ch.lxrin.ql.TestSchema.OrdersTable.ORDERS;
import static ch.lxrin.ql.TestSchema.UsersTable.USERS;
import static ch.lxrin.ql.dsl.Dsl.*;
import static org.junit.jupiter.api.Assertions.*;

class FunctionsTest {

    @Test
    void aggregatesWithModifiers() {
        assertSql("string_agg(users.name, ', ' ORDER BY users.name ASC)", stringAgg(USERS.NAME, ", ").orderBy(USERS.NAME.asc()));
        assertSql("array_agg(DISTINCT orders.id)", arrayAgg(ORDERS.ID).distinct());
        assertSql("percentile_cont(0.5) WITHIN GROUP (ORDER BY orders.total ASC)", percentileCont(0.5, ORDERS.TOTAL.asc()));
        assertSql("percentile_disc(0.9) WITHIN GROUP (ORDER BY orders.ordered_on ASC)", percentileDisc(0.9, ORDERS.ORDERED_ON.asc()));
        assertSql("mode() WITHIN GROUP (ORDER BY orders.status ASC)", mode(ORDERS.STATUS.asc()));
        assertSql("bool_and(users.active) FILTER (WHERE (users.role IS NOT NULL AND users.active))",
                boolAnd(USERS.ACTIVE).filter(USERS.ROLE.isNotNull()).filter(USERS.ACTIVE));
        assertSql("jsonb_object_agg(users.name, users.id)", jsonbObjectAgg(USERS.NAME, USERS.ID));
        assertSql("corr(orders.total, orders.id)", corr(ORDERS.TOTAL, ORDERS.ID));
        assertTrue(max(USERS.CREATED_AT).over() instanceof TemporalField);
        assertTrue(anyValue(USERS.NAME) instanceof StringAggregate);
    }

    @Test
    void windowFunctions() {
        assertSql("lag(orders.total, 1, ?) OVER (ORDER BY orders.id ASC)", lag(ORDERS.TOTAL, 1, BigDecimal.ZERO).over(orderBy(ORDERS.ID.asc())),
                BigDecimal.ZERO);
        assertSql("ntile(4) OVER ()", ntile(4).over());
        assertSql("nth_value(orders.id, 2) OVER ()", nthValue(ORDERS.ID, 2).over());
        assertSql("first_value(orders.id) OVER ()", firstValue(ORDERS.ID).over());
        assertSql("lead(orders.id) OVER ()", lead(ORDERS.ID).over());
    }

    @Test
    void caseExpressions() {
        assertSql("CASE WHEN users.active THEN ? WHEN users.role = ? THEN ? ELSE ? END",
                caseWhen(USERS.ACTIVE, param("a")).when(USERS.ROLE.eq(ch.lxrin.ql.TestSchema.Role.ADMIN), "b").otherwise("c"),
                "a", ch.lxrin.ql.TestSchema.Role.ADMIN, "b", "c");
        assertSql("CASE orders.status WHEN ? THEN 'new' WHEN ? THEN ? END",
                caseOf(ORDERS.STATUS).when("N", inline("new")).when("P", "paid").end(), "N", "P", "paid");
        assertSql("CASE WHEN users.active THEN users.name ELSE users.email END", caseWhen(USERS.ACTIVE, USERS.NAME).otherwise(USERS.EMAIL));
    }

    @Test
    void stringFunctionsWriteConstantsAsLiterals() {
        assertSql("regexp_replace(users.name, 'a''b', 'x', 'gi')", regexpReplace(USERS.NAME, "a'b", "x", "gi"));
        assertSql("concat_ws(' ', users.name, users.email)", concatWs(" ", USERS.NAME, USERS.EMAIL));
        assertSql("substring(users.name FROM '^A')", substringRegex(USERS.NAME, "^A"));
        assertSql("strpos(users.name, ?)", strpos(USERS.NAME, "x"), "x");
        assertSql("lpad(users.name, 5, '*')", lpad(USERS.NAME, 5, "*"));
        assertSql("format('%s-%s', users.name, users.id)", format("%s-%s", USERS.NAME, USERS.ID));
    }

    @Test
    void dateAndMathFunctions() {
        assertSql("date_bin(CAST('PT15M' AS interval), users.created_at, ?)",
                dateBin(Duration.ofMinutes(15), USERS.CREATED_AT, java.time.Instant.EPOCH), java.time.Instant.EPOCH);
        assertSql("(users.created_at AT TIME ZONE 'Europe/Zurich')", localDateTimeAt(USERS.CREATED_AT, ZoneId.of("Europe/Zurich")));
        assertSql("round(CAST(orders.total AS numeric), 1)", round(ORDERS.TOTAL, 1));
        assertSql("to_char(orders.ordered_on, 'DD.MM.YYYY')", toChar(ORDERS.ORDERED_ON, "DD.MM.YYYY"));
        assertSql("daterange(orders.ordered_on, ?, '[]')", daterange(ORDERS.ORDERED_ON, param(LocalDate.MAX), "[]"), LocalDate.MAX);
        assertThrows(IllegalArgumentException.class, () -> daterange(ORDERS.ORDERED_ON, ORDERS.ORDERED_ON, "x"));
    }

    @Test
    void jsonAndArrayFunctions() {
        assertSql("jsonb_build_object('id', users.id, 'n', users.name)", jsonbBuildObject(pair("id", USERS.ID), pair("n", USERS.NAME)));
        assertSql("jsonb_set(users.settings, '{\"a\",\"b\"}', to_jsonb(?), TRUE)",
                jsonbSet(USERS.SETTINGS, new String[]{"a", "b"}, toJsonb(param(1)), true), 1);
        assertSql("jsonb_path_query_first(users.settings, '$.a'::jsonpath)", jsonbPathQueryFirst(USERS.SETTINGS, "$.a"));
        assertSql("ARRAY[users.name, ?]", array(USERS.NAME, param("x")), "x");
        assertSql("ARRAY(SELECT orders.id FROM orders)", arrayOf(select(ORDERS.ID).from(ORDERS)));
        assertSql("array_to_string(users.tags, ',')", arrayToString(USERS.TAGS, ","));
        assertSql("unnest(users.tags)", unnest(USERS.TAGS));
    }

    @Test
    void fullTextSearch() {
        assertSql("to_tsvector('english'::regconfig, users.name) @@ websearch_to_tsquery('english'::regconfig, ?)",
                tsMatches(toTsvector("english", USERS.NAME), websearchToTsquery("english", "ada")), "ada");
        assertThrows(IllegalArgumentException.class, () -> setweight(toTsvector(USERS.NAME), 'X'));
    }

    @Test
    void routines() {
        Routines.Function2<String, String, Double> similarity = Routines.function("similarity", SqlTypes.TEXT, SqlTypes.TEXT, SqlTypes.FLOAT8);
        assertSql("similarity(users.name, ?) > ?", similarity.call(USERS.NAME, "ada").gt(0.3), "ada", 0.3);
        assertTrue(similarity.call(USERS.NAME, "x") instanceof NumberField);
        Routines.BinaryOperator<String, String, Double> distance = Routines.operator("<->", SqlTypes.TEXT, SqlTypes.TEXT, SqlTypes.FLOAT8);
        assertSql("(users.name <-> ?)", distance.apply(USERS.NAME, "x"), "x");
        assertSql("users.name %% ?", Routines.conditionOperator("%%", SqlTypes.TEXT, SqlTypes.TEXT).apply(USERS.NAME, "x"), "x");
        assertSql("my_schema.calc(orders.id, ?, ?)", Routines.varargsFunction("my_schema.calc", SqlTypes.NUMERIC)
                .call(ORDERS.ID, param(1), param("x")), 1, "x");
        assertSql("now2()", Routines.function("now2", SqlTypes.TIMESTAMPTZ).call());
        assertSql("f(?)", Routines.function("f", SqlTypes.INT4, SqlTypes.INT4).call(1), 1);
        assertSql("g(?, ?, ?)", Routines.function("g", SqlTypes.INT4, SqlTypes.INT4, SqlTypes.INT4, SqlTypes.INT4).call(1, 2, 3), 1, 2, 3);
        assertSql("my_median(orders.total) FILTER (WHERE orders.total > ?)",
                Routines.aggregate("my_median", SqlTypes.NUMERIC, SqlTypes.NUMERIC).call(ORDERS.TOTAL).filter(ORDERS.TOTAL.gt(BigDecimal.ONE)),
                BigDecimal.ONE);
        assertThrows(IllegalArgumentException.class, () -> Routines.function("f(); drop table x", SqlTypes.INT4));
        assertThrows(IllegalArgumentException.class, () -> Routines.operator("?|", SqlTypes.TEXT, SqlTypes.TEXT, SqlTypes.BOOL));
        assertThrows(IllegalArgumentException.class, () -> Routines.operator("a", SqlTypes.TEXT, SqlTypes.TEXT, SqlTypes.BOOL));
    }
}
