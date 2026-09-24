package ch.lxrin.ql.dsl;

import ch.lxrin.ql.render.QueryPart;
import ch.lxrin.ql.types.DataType;

import java.util.ArrayList;
import java.util.List;

/**
 * A searched {@code CASE WHEN condition THEN result ... [ELSE result] END}.
 * Immutable; the result type is fixed by the first branch.
 *
 * <pre>{@code
 * caseWhen(USERS.ROLE.eq(Role.ADMIN), inline("admin"))
 *     .when(USERS.ACTIVE, "active")
 *     .otherwise("inactive")
 * }</pre>
 *
 * @param <T> the result type
 */
public final class CaseWhen<T> {

    private final DataType<T> type;
    private final List<QueryPart[]> branches;

    CaseWhen(DataType<T> type, List<QueryPart[]> branches) {
        this.type = type;
        this.branches = List.copyOf(branches);
    }

    /** Adds {@code WHEN condition THEN result}. */
    public CaseWhen<T> when(Condition condition, Field<T> result) {
        List<QueryPart[]> list = new ArrayList<>(branches);
        list.add(new QueryPart[]{AbstractSelect.requireCondition(condition), AbstractDml.require(result)});
        return new CaseWhen<>(type, list);
    }

    /** Adds {@code WHEN condition THEN ?}. */
    public CaseWhen<T> when(Condition condition, T result) {
        return when(condition, Values.param(result, type));
    }

    /** {@code ELSE result END} */
    public Field<T> otherwise(Field<T> result) {
        return build(AbstractDml.require(result));
    }

    /** {@code ELSE ? END} */
    public Field<T> otherwise(T result) {
        return build(Values.param(result, type));
    }

    /** {@code END} without {@code ELSE} (the result is {@code NULL} if no branch matches). */
    public Field<T> end() {
        return build(null);
    }

    private Field<T> build(QueryPart otherwise) {
        List<QueryPart[]> list = branches;
        return Fields.of(type, ctx -> {
            ctx.append("CASE");
            for (QueryPart[] b : list) ctx.append(" WHEN ").visit(b[0]).append(" THEN ").visit(b[1]);
            if (otherwise != null) ctx.append(" ELSE ").visit(otherwise);
            ctx.append(" END");
        });
    }
}
