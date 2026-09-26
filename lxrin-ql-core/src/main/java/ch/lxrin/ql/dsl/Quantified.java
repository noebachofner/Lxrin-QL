package ch.lxrin.ql.dsl;

import ch.lxrin.ql.render.QueryPart;
import ch.lxrin.ql.render.RenderContext;
import ch.lxrin.ql.types.DataType;

/**
 * The right-hand side of a quantified comparison: {@code ANY (…)} or
 * {@code ALL (…)} over a sub-query or an array, e.g.
 * {@code USERS.SCORE.gt(all(select(SCORES.VALUE).from(SCORES)))} or
 * {@code eq("vip", any(USERS.TAGS))}.
 *
 * @param <T> the element type
 */
public final class Quantified<T> implements QueryPart {

    private final DataType<T> elementType;
    private final String keyword;
    private final QueryPart operand;
    private final boolean subquery;

    private Quantified(DataType<T> elementType, String keyword, QueryPart operand, boolean subquery) {
        this.elementType = elementType;
        this.keyword = keyword;
        this.operand = java.util.Objects.requireNonNull(operand, "operand");
        this.subquery = subquery;
    }

    /** {@code ANY (SELECT …)} */
    public static <T> Quantified<T> any(Subquery<T> subquery) {
        return new Quantified<>(subquery.asField().type(), "ANY", subquery, true);
    }

    /** {@code ALL (SELECT …)} */
    public static <T> Quantified<T> all(Subquery<T> subquery) {
        return new Quantified<>(subquery.asField().type(), "ALL", subquery, true);
    }

    /** {@code ANY (array)} */
    public static <T> Quantified<T> any(Field<T[]> array) {
        return new Quantified<>(elementOf(array), "ANY", array, false);
    }

    /** {@code ALL (array)} */
    public static <T> Quantified<T> all(Field<T[]> array) {
        return new Quantified<>(elementOf(array), "ALL", array, false);
    }

    @SuppressWarnings("unchecked")
    private static <T> DataType<T> elementOf(Field<T[]> array) {
        DataType<?> element = array.type().elementType();
        if (element == null) throw new IllegalArgumentException(array + " is not an array");
        return (DataType<T>) element;
    }

    /** Returns the type of the elements compared with. */
    public DataType<T> elementType() {
        return elementType;
    }

    @Override
    public void render(RenderContext ctx) {
        ctx.append(keyword);
        if (subquery) {
            ctx.append(' ').visit(operand);
        } else {
            ctx.append('(').visit(operand).append(')');
        }
    }
}
