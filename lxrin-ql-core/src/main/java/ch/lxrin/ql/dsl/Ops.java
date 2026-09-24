package ch.lxrin.ql.dsl;

import ch.lxrin.ql.render.QueryPart;
import ch.lxrin.ql.types.DataType;
import ch.lxrin.ql.types.Literals;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Building blocks shared by the field interfaces and the function catalog. */
final class Ops {

    private Ops() {}

    /** A bind parameter with the type of {@code like}; {@code null} is rejected. */
    static <T> QueryPart value(Field<T> like, T value, String operation) {
        if (value == null) {
            throw new IllegalArgumentException("the value of " + operation + "(..) must not be null"
                    + ("eq".equals(operation) || "ne".equals(operation) ? " – use isNull()/isNotNull() or eqOrIsNull(..)" : ""));
        }
        DataType<T> type = like.type();
        T checked = type.cast(value);
        return ctx -> ctx.bind(type, checked);
    }

    /** A bind parameter with the type of {@code like}; {@code null} is allowed. */
    static <T> QueryPart nullableValue(Field<T> like, T value) {
        DataType<T> type = like.type();
        T checked = type.cast(value);
        return ctx -> ctx.bind(type, checked);
    }

    /** A field used as an operand; conditions that are not atomic get parentheses. */
    static QueryPart field(Field<?> field) {
        if (field == null) throw new IllegalArgumentException("field must not be null – use isNull()/isNotNull() for NULL checks");
        return operand(field);
    }

    static QueryPart operand(QueryPart part) {
        if (part instanceof Fields.ConditionExpr && !((Fields.ConditionExpr) part).atomic()) {
            return ctx -> ctx.append('(').visit(part).append(')');
        }
        return part;
    }

    static Condition compare(QueryPart left, String operator, QueryPart right) {
        QueryPart l = operand(left);
        QueryPart r = operand(right);
        return Fields.condition(ctx -> ctx.visit(l).append(' ').append(operator).append(' ').visit(r), false);
    }

    static Condition postfix(QueryPart operand, String keyword) {
        QueryPart o = operand(operand);
        return Fields.condition(ctx -> ctx.visit(o).append(' ').append(keyword), false);
    }

    static Condition between(QueryPart value, String keyword, QueryPart from, QueryPart to) {
        QueryPart v = operand(value);
        return Fields.condition(ctx -> ctx.visit(v).append(' ').append(keyword).append(' ').visit(operand(from))
                .append(" AND ").visit(operand(to)), false);
    }

    static QueryPart binary(QueryPart left, String operator, QueryPart right) {
        QueryPart l = operand(left);
        QueryPart r = operand(right);
        return ctx -> ctx.append('(').visit(l).append(' ').append(operator).append(' ').visit(r).append(')');
    }

    static QueryPart call(String name, QueryPart... args) {
        List<QueryPart> list = List.of(args);
        return ctx -> ctx.append(name).append('(').visitAll(list, ", ").append(')');
    }

    static QueryPart cast(QueryPart part, DataType<?> type) {
        String sqlName = type.sqlName();
        return ctx -> ctx.append("CAST(").visit(part).append(" AS ").append(sqlName).append(')');
    }

    static QueryPart coalesce(QueryPart a, QueryPart b) {
        return call("COALESCE", a, b);
    }

    static QueryPart literal(String text) {
        if (text == null) throw new IllegalArgumentException("constant must not be null");
        String quoted = Literals.quote(text);
        return ctx -> ctx.append(quoted);
    }

    /** Builds a PostgreSQL text-array literal such as {@code {a,"b c"}}. */
    static String textArray(String... elements) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < elements.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(elements[i].replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return sb.append('}').toString();
    }

    /** Escapes {@code %}, {@code _} and {@code \} for use in a {@code LIKE} pattern. */
    static String escapeLike(String text, String operation) {
        if (text == null) throw new IllegalArgumentException("the text of " + operation + "(..) must not be null");
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @SuppressWarnings("unchecked")
    static <T> Condition in(Field<T> field, Collection<? extends T> values, boolean negated) {
        if (values == null) throw new IllegalArgumentException("values must not be null");
        DataType<T> type = field.type();
        T[] array = (T[]) Array.newInstance(type.javaType(), values.size());
        int i = 0;
        for (T v : values) array[i++] = type.cast(v);
        DataType<T[]> arrayType = type.array();
        return compare(field, negated ? "<>" : "=",
                ctx -> ctx.append(negated ? "ALL(" : "ANY(").bind(arrayType, array).append(')'));
    }

    static Condition junction(String keyword, List<? extends Condition> conditions) {
        List<Condition> parts = new ArrayList<>();
        for (Condition c : conditions) {
            if (c == null || c == Fields.NO_CONDITION) continue;
            if (c instanceof Fields.ConditionExpr && keyword.equals(((Fields.ConditionExpr) c).junction)
                    && ((Fields.ConditionExpr) c).name() == null) {
                parts.addAll(((Fields.ConditionExpr) c).parts);
            } else {
                parts.add(c);
            }
        }
        if (parts.isEmpty()) return Fields.NO_CONDITION;
        if (parts.size() == 1) return parts.get(0);
        String separator = " " + keyword + " ";
        List<Condition> frozen = List.copyOf(parts);
        return new Fields.ConditionExpr(ctx -> ctx.append('(').visitAll(frozen, separator).append(')'), null, true, keyword, frozen);
    }
}
