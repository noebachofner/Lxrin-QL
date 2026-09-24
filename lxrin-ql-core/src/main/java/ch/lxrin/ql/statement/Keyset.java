package ch.lxrin.ql.statement;

import ch.lxrin.ql.dsl.Condition;
import ch.lxrin.ql.dsl.Field;
import ch.lxrin.ql.dsl.Fields;
import ch.lxrin.ql.dsl.SortField;
import ch.lxrin.ql.render.QueryPart;
import ch.lxrin.ql.render.RenderContext;
import ch.lxrin.ql.types.DataType;

import java.util.ArrayList;
import java.util.List;

/** Builds the condition for keyset pagination ("seek after the last row"). */
final class Keyset {

    private Keyset() {}

    static Condition after(List<SortField<?>> orderBy, List<Object> values) {
        if (orderBy.size() != values.size()) {
            throw new IllegalStateException("seekAfter(..) needs one value per ORDER BY field: " + orderBy.size()
                    + " ORDER BY fields, " + values.size() + " values");
        }
        for (int i = 0; i < values.size(); i++) {
            Field<?> f = orderBy.get(i).field();
            if (values.get(i) == null) throw new IllegalArgumentException("seekAfter(..) values must not be null");
            f.type().cast(values.get(i));
        }
        boolean uniform = orderBy.stream().allMatch(s -> s.ascending() == orderBy.get(0).ascending());
        if (uniform) {
            String op = orderBy.get(0).ascending() ? " > " : " < ";
            if (orderBy.size() == 1) {
                return Fields.condition(ctx -> ctx.visit(Fields.unaliased(orderBy.get(0).field())).append(op).visit(bind(orderBy.get(0).field(), values.get(0))), false);
            }
            return Fields.condition(ctx -> {
                ctx.append('(');
                for (int i = 0; i < orderBy.size(); i++) {
                    if (i > 0) ctx.append(", ");
                    ctx.visit(Fields.unaliased(orderBy.get(i).field()));
                }
                ctx.append(')').append(op).append('(');
                for (int i = 0; i < orderBy.size(); i++) {
                    if (i > 0) ctx.append(", ");
                    ctx.visit(bind(orderBy.get(i).field(), values.get(i)));
                }
                ctx.append(')');
            }, false);
        }
        // mixed directions: (a > ?) OR (a = ? AND b < ?) OR ...
        List<Condition> alternatives = new ArrayList<>();
        for (int i = 0; i < orderBy.size(); i++) {
            List<Condition> parts = new ArrayList<>();
            for (int j = 0; j < i; j++) parts.add(compare(orderBy.get(j).field(), " = ", values.get(j)));
            parts.add(compare(orderBy.get(i).field(), orderBy.get(i).ascending() ? " > " : " < ", values.get(i)));
            alternatives.add(Condition.and(parts));
        }
        return Condition.or(alternatives);
    }

    private static Condition compare(Field<?> field, String op, Object value) {
        QueryPart bound = bind(field, value);
        QueryPart plain = Fields.unaliased(field);
        return Fields.condition(ctx -> ctx.visit(plain).append(op).visit(bound), false);
    }

    private static <T> QueryPart bind(Field<T> field, Object value) {
        DataType<T> type = field.type();
        T v = type.cast(value);
        return (RenderContext ctx) -> ctx.bind(type, v);
    }
}
