package ch.lxrin.ql.dsl;

import ch.lxrin.ql.render.QueryPart;
import ch.lxrin.ql.types.DataType;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple {@code CASE subject WHEN value THEN result ... END}:
 * <pre>{@code
 * caseOf(ORDERS.STATUS).when("N", inline("new")).when("P", inline("paid")).otherwise(ORDERS.STATUS)
 * }</pre>
 *
 * @param <S> the subject type
 */
public final class CaseOf<S> {

    private final Field<S> subject;

    CaseOf(Field<S> subject) {
        this.subject = AbstractDml.require(subject);
    }

    /** Adds the first branch and fixes the result type. */
    public <T> Branches<S, T> when(S value, Field<T> result) {
        return new Branches<>(subject, result.type(), List.of()).when(value, result);
    }

    /**
     * The branches of a simple {@code CASE}.
     *
     * @param <S> the subject type
     * @param <T> the result type
     */
    public static final class Branches<S, T> {
        private final Field<S> subject;
        private final DataType<T> type;
        private final List<QueryPart[]> branches;

        Branches(Field<S> subject, DataType<T> type, List<QueryPart[]> branches) {
            this.subject = subject;
            this.type = type;
            this.branches = List.copyOf(branches);
        }

        /** Adds {@code WHEN ? THEN result}. */
        public Branches<S, T> when(S value, Field<T> result) {
            List<QueryPart[]> list = new ArrayList<>(branches);
            list.add(new QueryPart[]{Values.param(value, subject.type()), AbstractDml.require(result)});
            return new Branches<>(subject, type, list);
        }

        /** Adds {@code WHEN ? THEN ?}. */
        public Branches<S, T> when(S value, T result) {
            return when(value, Values.param(result, type));
        }

        /** {@code ELSE result END} */
        public Field<T> otherwise(Field<T> result) {
            return build(AbstractDml.require(result));
        }

        /** {@code ELSE ? END} */
        public Field<T> otherwise(T result) {
            return build(Values.param(result, type));
        }

        /** {@code END} without {@code ELSE}. */
        public Field<T> end() {
            return build(null);
        }

        private Field<T> build(QueryPart otherwise) {
            return Fields.of(type, ctx -> {
                ctx.append("CASE ").visit(subject);
                for (QueryPart[] b : branches) ctx.append(" WHEN ").visit(b[0]).append(" THEN ").visit(b[1]);
                if (otherwise != null) ctx.append(" ELSE ").visit(otherwise);
                ctx.append(" END");
            });
        }
    }
}
