package ch.lxrin.ql.expr;

import java.util.Objects;

/**
 * An expression with an alias: {@code expr AS alias}.
 *
 * <p>In a {@code SELECT} list the alias is also the name used by the default
 * bean mapper and by integrations that map result columns by name.</p>
 */
public final class AliasedExpression implements Expression {

    private final Object expression;
    private final String alias;

    /**
     * @param expression the aliased element (expression, sub-query or SQL fragment)
     * @param alias      the alias, rendered verbatim
     */
    public AliasedExpression(Object expression, String alias) {
        if (alias == null || alias.isBlank()) throw new IllegalArgumentException("alias must not be blank");
        this.expression = expression;
        this.alias = alias;
    }

    /** Returns the wrapped element. */
    public Object getExpression() {
        return expression;
    }

    /** Returns the alias. */
    public String getAlias() {
        return alias;
    }

    @Override
    public void render(RenderContext ctx) {
        ctx.visit(expression).append(" AS ").append(alias);
    }

    @Override
    public Expression as(String newAlias) {
        return new AliasedExpression(expression, newAlias);
    }

    @Override
    public String toString() {
        return Objects.toString(toSql());
    }
}
