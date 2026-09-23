package ch.lxrin.ql.condition;

import ch.lxrin.ql.expr.Expression;
import ch.lxrin.ql.expr.Expressions;
import ch.lxrin.ql.query.AbstractStatement;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Static factory methods for {@link Condition}s. All methods are also
 * available through {@code import static ch.lxrin.ql.LxrinQL.*}.
 *
 * <p>Every operand is an {@code Object} and follows the library-wide rule:</p>
 * <ul>
 *   <li>{@link Expression} (column, function, {@code val(..)}, sub-query, ...) &rarr; rendered</li>
 *   <li>{@code String} &rarr; SQL fragment, e.g. {@code "t.STATUS"} or a placeholder {@code ":status"}</li>
 *   <li>other Java values &rarr; bound as parameters automatically</li>
 * </ul>
 *
 * <pre>{@code
 * eq(p.status, val("ACTIVE"))          // p.STATUS = :lq0
 * eq(p.status, b.setString("ACTIVE"))  // p.STATUS = :p0   (Binds)
 * ge(p.age, 18)                        // p.AGE >= :lq1
 * eq("p.STATUS", ":status")            // p.STATUS = :status (named bind)
 * }</pre>
 */
public class Conditions extends Expressions {

    /** Utility class; extended by the DSL entry point only. */
    protected Conditions() {}

    // =========================================================================
    // Comparison
    // =========================================================================

    /** {@code left = right} */
    public static Condition eq(Object left, Object right) { return compare(left, "=", right); }

    /** {@code left <> right} */
    public static Condition ne(Object left, Object right) { return compare(left, "<>", right); }

    /** {@code left > right} */
    public static Condition gt(Object left, Object right) { return compare(left, ">", right); }

    /** {@code left >= right} */
    public static Condition ge(Object left, Object right) { return compare(left, ">=", right); }

    /** {@code left < right} */
    public static Condition lt(Object left, Object right) { return compare(left, "<", right); }

    /** {@code left <= right} */
    public static Condition le(Object left, Object right) { return compare(left, "<=", right); }

    /** {@code left IS DISTINCT FROM right} – null-safe inequality. */
    public static Condition isDistinctFrom(Object left, Object right) { return compare(left, "IS DISTINCT FROM", nullToLiteral(right)); }

    /** {@code left IS NOT DISTINCT FROM right} – null-safe equality. */
    public static Condition isNotDistinctFrom(Object left, Object right) { return compare(left, "IS NOT DISTINCT FROM", nullToLiteral(right)); }

    /**
     * {@code left operator right} with an arbitrary operator, e.g.
     * {@code compare(p.location, "<->", val(point))}.
     *
     * <p>A Java {@code null} on the right side is rejected because
     * {@code x = NULL} is never true; use {@link #isNull(Object)} or pass
     * {@code val(null)} / {@code inline(null)} explicitly.</p>
     */
    public static Condition compare(Object left, String operator, Object right) {
        if (operator == null || operator.isBlank()) throw new IllegalArgumentException("operator must not be blank");
        requireOperand(left, "left operand");
        if (right == null) {
            throw new IllegalArgumentException("right operand of '" + operator
                    + "' must not be null – use isNull(..) or val(null)");
        }
        return ctx -> ctx.visit(left).append(' ').append(operator).append(' ').visit(right);
    }

    // =========================================================================
    // Pattern matching
    // =========================================================================

    /** {@code value LIKE pattern} */
    public static Condition like(Object value, Object pattern) { return compare(value, "LIKE", pattern); }

    /** {@code value NOT LIKE pattern} */
    public static Condition notLike(Object value, Object pattern) { return compare(value, "NOT LIKE", pattern); }

    /** {@code value ILIKE pattern} – case-insensitive {@code LIKE}. */
    public static Condition ilike(Object value, Object pattern) { return compare(value, "ILIKE", pattern); }

    /** {@code value NOT ILIKE pattern} */
    public static Condition notIlike(Object value, Object pattern) { return compare(value, "NOT ILIKE", pattern); }

    /** {@code value SIMILAR TO pattern} – SQL regular expression. */
    public static Condition similarTo(Object value, Object pattern) { return compare(value, "SIMILAR TO", pattern); }

    /** {@code value NOT SIMILAR TO pattern} */
    public static Condition notSimilarTo(Object value, Object pattern) { return compare(value, "NOT SIMILAR TO", pattern); }

    /** {@code value ~ regex} – POSIX regular expression, case-sensitive. */
    public static Condition matches(Object value, Object regex) { return compare(value, "~", regex); }

    /** {@code value ~* regex} – POSIX regular expression, case-insensitive. */
    public static Condition matchesIgnoreCase(Object value, Object regex) { return compare(value, "~*", regex); }

    /** {@code value !~ regex} */
    public static Condition notMatches(Object value, Object regex) { return compare(value, "!~", regex); }

    /** {@code value !~* regex} */
    public static Condition notMatchesIgnoreCase(Object value, Object regex) { return compare(value, "!~*", regex); }

    // =========================================================================
    // IN / BETWEEN / NULL / boolean tests
    // =========================================================================

    /**
     * {@code value IN (...)}.
     *
     * <ul>
     *   <li>{@code in(p.id, 1L, 2L, 3L)} &rarr; {@code p.ID IN (:lq0, :lq1, :lq2)}</li>
     *   <li>{@code in(p.id, idList)} &rarr; one parameter per list element</li>
     *   <li>{@code in(p.id, subQuery)} &rarr; {@code p.ID IN (SELECT ...)}</li>
     *   <li>{@code in(p.status, ":a", ":b")} &rarr; {@code p.STATUS IN (:a, :b)}</li>
     * </ul>
     * An empty list renders {@code FALSE}, so the query stays valid.
     */
    public static Condition in(Object value, Object... values) { return inCondition(value, values, false); }

    /** {@code value NOT IN (...)}; an empty list renders {@code TRUE}. @see #in(Object, Object...) */
    public static Condition notIn(Object value, Object... values) { return inCondition(value, values, true); }

    /** {@code value BETWEEN from AND to} */
    public static Condition between(Object value, Object from, Object to) {
        return betweenCondition(value, "BETWEEN", from, to);
    }

    /** {@code value NOT BETWEEN from AND to} */
    public static Condition notBetween(Object value, Object from, Object to) {
        return betweenCondition(value, "NOT BETWEEN", from, to);
    }

    /** {@code value BETWEEN SYMMETRIC a AND b} – bounds may be given in any order. */
    public static Condition betweenSymmetric(Object value, Object a, Object b) {
        return betweenCondition(value, "BETWEEN SYMMETRIC", a, b);
    }

    /** {@code value IS NULL} */
    public static Condition isNull(Object value) { return postfix(value, "IS NULL"); }

    /** {@code value IS NOT NULL} */
    public static Condition isNotNull(Object value) { return postfix(value, "IS NOT NULL"); }

    /** {@code value IS TRUE} */
    public static Condition isTrue(Object value) { return postfix(value, "IS TRUE"); }

    /** {@code value IS NOT TRUE} – false or null. */
    public static Condition isNotTrue(Object value) { return postfix(value, "IS NOT TRUE"); }

    /** {@code value IS FALSE} */
    public static Condition isFalse(Object value) { return postfix(value, "IS FALSE"); }

    /** {@code value IS NOT FALSE} – true or null. */
    public static Condition isNotFalse(Object value) { return postfix(value, "IS NOT FALSE"); }

    // =========================================================================
    // Sub-queries, ANY / ALL
    // =========================================================================

    /** {@code EXISTS (SELECT ...)} */
    public static Condition exists(Object subQuery) {
        requireOperand(subQuery, "sub-query");
        return ctx -> ctx.append("EXISTS ").visit(subQuery);
    }

    /** {@code NOT EXISTS (SELECT ...)} */
    public static Condition notExists(Object subQuery) {
        requireOperand(subQuery, "sub-query");
        return ctx -> ctx.append("NOT EXISTS ").visit(subQuery);
    }

    /**
     * {@code ANY(array_or_subquery)} for use as a comparison operand:
     * {@code eq(p.id, any(val(new Long[]{1L, 2L})))} renders
     * {@code p.ID = ANY(:lq0)}.
     */
    public static Expression any(Object arrayOrSubQuery) { return quantifier("ANY", arrayOrSubQuery); }

    /** {@code ALL(array_or_subquery)}, e.g. {@code gt(o.total, all(subQuery))}. */
    public static Expression all(Object arrayOrSubQuery) { return quantifier("ALL", arrayOrSubQuery); }

    // =========================================================================
    // Containment operators (arrays, jsonb, ranges) and full-text search
    // =========================================================================

    /** {@code left @> right} – arrays, {@code jsonb} and ranges: left contains right. */
    public static Condition contains(Object left, Object right) { return compare(left, "@>", right); }

    /** {@code left <@ right} – left is contained by right. */
    public static Condition containedBy(Object left, Object right) { return compare(left, "<@", right); }

    /** {@code left && right} – arrays and ranges overlap. */
    public static Condition overlaps(Object left, Object right) { return compare(left, "&&", right); }

    /**
     * {@code jsonb_exists(json, key)} – the {@code jsonb ? key} operator,
     * written as a function so it cannot clash with JDBC {@code ?} placeholders.
     */
    public static Condition jsonHasKey(Object json, Object key) {
        return ctx -> ctx.append("jsonb_exists(").visit(json).append(", ").visit(key).append(')');
    }

    /** {@code jsonb_exists_any(json, keys)} – the {@code ?|} operator. {@code keys} is a text array. */
    public static Condition jsonHasAnyKey(Object json, Object keys) {
        return ctx -> ctx.append("jsonb_exists_any(").visit(json).append(", ").visit(keys).append(')');
    }

    /** {@code jsonb_exists_all(json, keys)} – the {@code ?&} operator. {@code keys} is a text array. */
    public static Condition jsonHasAllKeys(Object json, Object keys) {
        return ctx -> ctx.append("jsonb_exists_all(").visit(json).append(", ").visit(keys).append(')');
    }

    /** {@code vector @@ query} – full-text search match. */
    public static Condition tsMatches(Object vector, Object query) { return compare(vector, "@@", query); }

    // =========================================================================
    // Logical combination
    // =========================================================================

    /** The {@code AND} token for the list style: {@code where(a, and(), b)}. */
    public static Condition and() { return LogicalOperator.AND; }

    /** The {@code OR} token for the list style: {@code where(a, or(), b)}. */
    public static Condition or() { return LogicalOperator.OR; }

    /**
     * {@code (c1 AND c2 AND ...)}. {@code null} entries are skipped, which makes
     * optional filters easy; if nothing remains, {@code TRUE} is rendered.
     */
    public static Condition and(Object... conditions) { return junction("AND", "TRUE", conditions); }

    /**
     * {@code (c1 OR c2 OR ...)}. {@code null} entries are skipped; if nothing
     * remains, {@code FALSE} is rendered.
     */
    public static Condition or(Object... conditions) { return junction("OR", "FALSE", conditions); }

    /** {@code NOT (condition)} */
    public static Condition not(Object condition) {
        requireOperand(condition, "condition");
        return ctx -> ctx.append("NOT (").visit(condition).append(')');
    }

    /**
     * Wraps the given items in parentheses, list style:
     * {@code group(eq(a, 1), or(), eq(b, 2))} &rarr; {@code (a = :lq0 OR b = :lq1)}.
     */
    public static Condition group(Object... items) {
        ConditionList list = new ConditionList();
        list.add(items);
        return ctx -> {
            ctx.append('(');
            list.render(ctx);
            ctx.append(')');
        };
    }

    /** A condition from a SQL fragment, e.g. {@code condition("t.ACTIVE")}. */
    public static Condition condition(String sql) {
        if (sql == null || sql.isBlank()) throw new IllegalArgumentException("sql must not be blank");
        return ctx -> ctx.append(sql);
    }

    /** Turns any boolean expression (e.g. a function call) into a condition. */
    public static Condition condition(Expression booleanExpression) {
        requireOperand(booleanExpression, "expression");
        return booleanExpression::render;
    }

    /** {@code TRUE} */
    public static Condition trueCondition() { return ctx -> ctx.append("TRUE"); }

    /** {@code FALSE} */
    public static Condition falseCondition() { return ctx -> ctx.append("FALSE"); }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private static Object nullToLiteral(Object value) {
        return value == null ? inline(null) : value;
    }

    private static Condition postfix(Object value, String keyword) {
        requireOperand(value, "operand");
        return ctx -> ctx.visit(value).append(' ').append(keyword);
    }

    private static Condition betweenCondition(Object value, String keyword, Object from, Object to) {
        requireOperand(value, "operand");
        return ctx -> ctx.visit(value).append(' ').append(keyword).append(' ')
                .visit(from).append(" AND ").visit(to);
    }

    private static Expression quantifier(String keyword, Object operand) {
        requireOperand(operand, "operand");
        return ctx -> {
            ctx.append(keyword);
            if (operand instanceof AbstractStatement) {
                ctx.visit(operand);                      // renders "(SELECT ...)"
            } else {
                ctx.append('(').visit(operand).append(')');
            }
        };
    }

    private static Condition inCondition(Object value, Object[] values, boolean negated) {
        requireOperand(value, "operand");
        if (values == null) throw new IllegalArgumentException("values must not be null");
        String keyword = negated ? " NOT IN " : " IN ";
        if (values.length == 1 && values[0] instanceof AbstractStatement) {
            Object subQuery = values[0];
            return ctx -> ctx.visit(value).append(keyword).visit(subQuery);
        }
        List<Object> flat = new ArrayList<>();
        for (Object v : values) flatten(v, flat);
        if (flat.isEmpty()) {
            return negated ? trueCondition() : falseCondition();
        }
        return ctx -> ctx.visit(value).append(keyword).append('(').visitAll(flat, ", ").append(')');
    }

    private static void flatten(Object value, List<Object> out) {
        if (value instanceof Collection) {
            for (Object v : (Collection<?>) value) flatten(v, out);
        } else if (value != null && value.getClass().isArray() && !(value instanceof byte[])) {
            int n = Array.getLength(value);
            for (int i = 0; i < n; i++) out.add(Array.get(value, i));
        } else {
            out.add(value);
        }
    }

    private static Condition junction(String keyword, String whenEmpty, Object[] conditions) {
        List<Object> parts = new ArrayList<>();
        if (conditions != null) {
            for (Object c : conditions) if (c != null) parts.add(c);
        }
        return ctx -> {
            if (parts.isEmpty()) {
                ctx.append(whenEmpty);
            } else if (parts.size() == 1) {
                ctx.visit(parts.get(0));
            } else {
                ctx.append('(').visitAll(parts, " " + keyword + " ").append(')');
            }
        };
    }

    static void requireOperand(Object operand, String what) {
        if (operand == null) throw new IllegalArgumentException(what + " must not be null");
        if (operand instanceof String && ((String) operand).isBlank()) {
            throw new IllegalArgumentException(what + " must not be blank");
        }
    }
}
