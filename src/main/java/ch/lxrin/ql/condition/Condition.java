package ch.lxrin.ql.condition;

import ch.lxrin.ql.expr.Expression;

/**
 * A boolean SQL expression used in {@code WHERE}, {@code HAVING},
 * {@code JOIN ... ON}, {@code FILTER (WHERE ...)} or {@code CASE WHEN}.
 *
 * <p>Conditions are usually created with the static factories in
 * {@link Conditions} (re-exported by {@code LxrinQL}) or with the fluent
 * methods on {@link Expression}. They can be combined fluently:</p>
 * <pre>{@code
 * p.status.eq(val("ACTIVE")).and(p.age.ge(18)).or(p.vip.isTrue())
 * // ((p.STATUS = :lq0 AND p.AGE >= :lq1) OR p.VIP IS TRUE)
 * }</pre>
 *
 * <p>Custom conditions can be written as lambdas:</p>
 * <pre>{@code
 * Condition c = ctx -> ctx.append("my_check(").visit(p.id).append(")");
 * }</pre>
 */
@FunctionalInterface
public interface Condition extends Expression {

    /** {@code (this AND other)} */
    default Condition and(Object other) {
        return Conditions.and(this, other);
    }

    /** {@code (this OR other)} */
    default Condition or(Object other) {
        return Conditions.or(this, other);
    }

    /** {@code NOT (this)} */
    default Condition not() {
        return Conditions.not(this);
    }
}
