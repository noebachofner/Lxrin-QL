package ch.lxrin.ql.expr;

import ch.lxrin.ql.condition.Condition;
import ch.lxrin.ql.condition.Conditions;

/**
 * Any piece of SQL that can be rendered: columns, functions, literals,
 * parameters, conditions and sub-queries.
 *
 * <p>Besides {@link #render(RenderContext)} the interface offers a fluent
 * API so that expressions can be combined without static imports:</p>
 * <pre>{@code
 * p.age.ge(18)                      // p.AGE >= :lq0
 * p.lastName.ilike("'%son'")         // p.LAST_NAME ILIKE '%son'
 * p.price.times(p.quantity).as("total")
 * p.createdAt.desc().nullsLast()
 * }</pre>
 *
 * <p>Operands follow the rule described in {@link RenderContext}: a
 * {@code String} is a SQL fragment, any other Java value becomes a bind
 * parameter.</p>
 *
 * <p>Custom expressions can be written as lambdas:</p>
 * <pre>{@code
 * Expression e = ctx -> ctx.append("my_function(").visit(p.id).append(")");
 * }</pre>
 */
@FunctionalInterface
public interface Expression extends Renderable {

    /**
     * Renders this expression on its own and returns the SQL. Values that
     * would become bind parameters are shown as {@code :lqN} placeholders.
     */
    default String toSql() {
        RenderContext ctx = new RenderContext();
        render(ctx);
        return ctx.sql();
    }

    // -------------------------------------------------------------------------
    // Aliasing, casting, sorting
    // -------------------------------------------------------------------------

    /** {@code expr AS alias} */
    default Expression as(String alias) {
        return new AliasedExpression(this, alias);
    }

    /** {@code CAST(expr AS type)} */
    default Expression cast(String sqlType) {
        return Expressions.cast(this, sqlType);
    }

    /** {@code expr ASC} */
    default SortField asc() {
        return new SortField(this, "ASC", null);
    }

    /** {@code expr DESC} */
    default SortField desc() {
        return new SortField(this, "DESC", null);
    }

    // -------------------------------------------------------------------------
    // Comparison conditions
    // -------------------------------------------------------------------------

    /** {@code expr = value} */
    default Condition eq(Object value) { return Conditions.eq(this, value); }

    /** {@code expr <> value} */
    default Condition ne(Object value) { return Conditions.ne(this, value); }

    /** {@code expr > value} */
    default Condition gt(Object value) { return Conditions.gt(this, value); }

    /** {@code expr >= value} */
    default Condition ge(Object value) { return Conditions.ge(this, value); }

    /** {@code expr < value} */
    default Condition lt(Object value) { return Conditions.lt(this, value); }

    /** {@code expr <= value} */
    default Condition le(Object value) { return Conditions.le(this, value); }

    /** {@code expr LIKE pattern} */
    default Condition like(Object pattern) { return Conditions.like(this, pattern); }

    /** {@code expr NOT LIKE pattern} */
    default Condition notLike(Object pattern) { return Conditions.notLike(this, pattern); }

    /** {@code expr ILIKE pattern} */
    default Condition ilike(Object pattern) { return Conditions.ilike(this, pattern); }

    /** {@code expr NOT ILIKE pattern} */
    default Condition notIlike(Object pattern) { return Conditions.notIlike(this, pattern); }

    /** {@code expr IN (v1, v2, ...)} or {@code expr IN (SELECT ...)} */
    default Condition in(Object... values) { return Conditions.in(this, values); }

    /** {@code expr NOT IN (v1, v2, ...)} */
    default Condition notIn(Object... values) { return Conditions.notIn(this, values); }

    /** {@code expr BETWEEN from AND to} */
    default Condition between(Object from, Object to) { return Conditions.between(this, from, to); }

    /** {@code expr IS NULL} */
    default Condition isNull() { return Conditions.isNull(this); }

    /** {@code expr IS NOT NULL} */
    default Condition isNotNull() { return Conditions.isNotNull(this); }

    /** {@code expr IS DISTINCT FROM value} */
    default Condition isDistinctFrom(Object value) { return Conditions.isDistinctFrom(this, value); }

    /** {@code expr IS NOT DISTINCT FROM value} */
    default Condition isNotDistinctFrom(Object value) { return Conditions.isNotDistinctFrom(this, value); }

    // -------------------------------------------------------------------------
    // Arithmetic and string concatenation
    // -------------------------------------------------------------------------

    /** {@code (expr + value)} */
    default Expression plus(Object value) { return Expressions.operator(this, "+", value); }

    /** {@code (expr - value)} */
    default Expression minus(Object value) { return Expressions.operator(this, "-", value); }

    /** {@code (expr * value)} */
    default Expression times(Object value) { return Expressions.operator(this, "*", value); }

    /** {@code (expr / value)} */
    default Expression divide(Object value) { return Expressions.operator(this, "/", value); }

    /** {@code (expr % value)} */
    default Expression mod(Object value) { return Expressions.operator(this, "%", value); }

    /** {@code (expr || value)} */
    default Expression concat(Object value) { return Expressions.operator(this, "||", value); }
}
