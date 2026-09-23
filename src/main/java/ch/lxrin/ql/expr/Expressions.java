package ch.lxrin.ql.expr;

import java.util.regex.Pattern;

/**
 * Low-level factory methods for expressions. All of them are also available
 * through {@code import static ch.lxrin.ql.LxrinQL.*}.
 */
public class Expressions {

    private static final Pattern SQL_TYPE = Pattern.compile("[A-Za-z_][A-Za-z0-9_ .,()\\[\\]\"]*");

    /** Utility class; extended by the DSL entry point only. */
    protected Expressions() {}

    /**
     * A verbatim SQL fragment, e.g. {@code raw("t.NAME")} or {@code raw("now()")}.
     * Never pass user input to this method; use {@link #val(Object)} instead.
     */
    public static Expression raw(String sql) {
        if (sql == null) throw new IllegalArgumentException("sql must not be null");
        return ctx -> ctx.append(sql);
    }

    /**
     * A SQL template with {@code {0}}, {@code {1}}, ... placeholders.
     *
     * @see Template
     */
    public static Expression sql(String template, Object... args) {
        return new Template(template, args);
    }

    /** A bind parameter holding {@code value}; the name is generated automatically. */
    public static Expression val(Object value) {
        return new Param(value);
    }

    /** An escaped literal written into the SQL text. @see Literal */
    public static Expression inline(Object value) {
        return new Literal(value);
    }

    /** A double-quoted identifier: {@code ident("order")} renders {@code "order"}. */
    public static Expression ident(String... parts) {
        if (parts.length == 0) throw new IllegalArgumentException("at least one identifier part required");
        return ctx -> {
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) ctx.append('.');
                ctx.append('"').append(parts[i].replace("\"", "\"\"")).append('"');
            }
        };
    }

    /** {@code CAST(expr AS type)}, e.g. {@code cast(p.id, "text")} or {@code cast(val(s), "jsonb")}. */
    public static Expression cast(Object expression, String sqlType) {
        if (sqlType == null || !SQL_TYPE.matcher(sqlType).matches()) {
            throw new IllegalArgumentException("invalid SQL type: " + sqlType);
        }
        return ctx -> ctx.append("CAST(").visit(expression).append(" AS ").append(sqlType).append(')');
    }

    /** A binary operator expression: {@code (left op right)}. */
    public static Expression operator(Object left, String operator, Object right) {
        if (operator == null || operator.isBlank()) throw new IllegalArgumentException("operator must not be blank");
        return ctx -> ctx.append('(').visit(left).append(' ').append(operator).append(' ').visit(right).append(')');
    }

    /** A prefix operator expression: {@code (op operand)}, e.g. {@code prefix("-", x)}. */
    public static Expression prefix(String operator, Object operand) {
        return ctx -> ctx.append('(').append(operator).append(' ').visit(operand).append(')');
    }

    /** {@code (expr)} – explicit parentheses. */
    public static Expression parens(Object expression) {
        return ctx -> ctx.append('(').visit(expression).append(')');
    }

    /** {@code *} – for {@code SELECT *} or {@code count(*)}. */
    public static Expression asterisk() {
        return ctx -> ctx.append('*');
    }

    /** {@code DEFAULT} – for {@code INSERT} values and {@code UPDATE} assignments. */
    public static Expression defaultValue() {
        return ctx -> ctx.append("DEFAULT");
    }

    /** {@code LATERAL source} for {@code FROM} / {@code JOIN}. */
    public static Expression lateral(Object source) {
        return new Lateral(source);
    }

    /** {@code expr AS alias} for any element, including SQL fragments and sub-queries. */
    public static Expression as(Object expression, String alias) {
        return new AliasedExpression(expression, alias);
    }

    /** {@code expr ASC} */
    public static SortField asc(Object expression) {
        return SortField.asc(expression);
    }

    /** {@code expr DESC} */
    public static SortField desc(Object expression) {
        return SortField.desc(expression);
    }

    /** Starts a searched {@code CASE WHEN condition THEN result ... END}. */
    public static CaseExpression caseWhen(Object condition, Object result) {
        return CaseExpression.searched(condition, result);
    }

    /** Starts a simple {@code CASE subject WHEN value THEN result ... END}. */
    public static CaseExpression caseOf(Object subject) {
        return CaseExpression.simple(subject);
    }

    /** An empty window definition, to be refined with {@code partitionBy} / {@code orderBy}. */
    public static WindowSpec window() {
        return new WindowSpec();
    }

    /** {@code (PARTITION BY items)} */
    public static WindowSpec partitionBy(Object... items) {
        return new WindowSpec().partitionBy(items);
    }

    /** {@code UNBOUNDED PRECEDING} window frame bound. */
    public static String unboundedPreceding() { return WindowSpec.unboundedPreceding(); }

    /** {@code UNBOUNDED FOLLOWING} window frame bound. */
    public static String unboundedFollowing() { return WindowSpec.unboundedFollowing(); }

    /** {@code CURRENT ROW} window frame bound. */
    public static String currentRow() { return WindowSpec.currentRow(); }

    /** {@code n PRECEDING} window frame bound. */
    public static String preceding(long n) { return WindowSpec.preceding(n); }

    /** {@code n FOLLOWING} window frame bound. */
    public static String following(long n) { return WindowSpec.following(n); }
}
