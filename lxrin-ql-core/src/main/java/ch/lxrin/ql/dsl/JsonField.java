package ch.lxrin.ql.dsl;

import ch.lxrin.ql.types.Literals;
import ch.lxrin.ql.types.SqlTypes;

/**
 * A {@code json} or {@code jsonb} expression. Keys and paths are constants
 * and are written as escaped literals, so the same expression can appear in
 * the select list and in {@code GROUP BY}.
 *
 * @param <T> the Java type of the JSON value (JSON text or a mapped class)
 */
public interface JsonField<T> extends Field<T> {

    /** {@code (this -> 'key')} – an object field as JSON. */
    default JsonField<String> get(String key) { return Fields.json(SqlTypes.JSONB, Ops.binary(this, "->", Ops.literal(key))); }

    /** {@code (this -> index)} – an array element as JSON (0-based, negative from the end). */
    default JsonField<String> get(int index) { return Fields.json(SqlTypes.JSONB, Ops.binary(this, "->", ctx -> ctx.append(Integer.toString(index)))); }

    /** {@code (this ->> 'key')} – an object field as text. */
    default StringField getText(String key) { return Fields.string(Ops.binary(this, "->>", Ops.literal(key))); }

    /** {@code (this ->> index)} – an array element as text. */
    default StringField getText(int index) { return Fields.string(Ops.binary(this, "->>", ctx -> ctx.append(Integer.toString(index)))); }

    /** {@code (this #> '{a,b}')} – the value at a path as JSON. */
    default JsonField<String> path(String... path) { return Fields.json(SqlTypes.JSONB, Ops.binary(this, "#>", Ops.literal(Ops.textArray(path)))); }

    /** {@code (this #>> '{a,b}')} – the value at a path as text. */
    default StringField pathText(String... path) { return Fields.string(Ops.binary(this, "#>>", Ops.literal(Ops.textArray(path)))); }

    /** {@code this @> ?} – contains the given JSON value (index-friendly for {@code jsonb}). */
    default Condition contains(T value) { return Ops.compare(this, "@>", Ops.value(this, value, "contains")); }

    /** {@code this @> CAST(? AS jsonb)} – contains the given JSON text. */
    default Condition containsJson(String json) {
        if (json == null) throw new IllegalArgumentException("json must not be null");
        return Ops.compare(this, "@>", ctx -> ctx.bind(SqlTypes.JSONB, json));
    }

    /** {@code this <@ ?} */
    default Condition containedBy(T value) { return Ops.compare(this, "<@", Ops.value(this, value, "containedBy")); }

    /** {@code this @> other} */
    default Condition contains(Field<T> other) { return Ops.compare(this, "@>", Ops.field(other)); }

    /** {@code this <@ other} */
    default Condition containedBy(Field<T> other) { return Ops.compare(this, "<@", Ops.field(other)); }

    /** {@code this @> ?} – the same as {@link #contains(Object)}. */
    default Condition jsonContains(T value) { return Ops.compare(this, "@>", Ops.value(this, value, "jsonContains")); }

    /** {@code this @> other} */
    default Condition jsonContains(Field<T> other) { return contains(other); }

    /** {@code this <@ ?} */
    default Condition jsonContainedBy(T value) { return Ops.compare(this, "<@", Ops.value(this, value, "jsonContainedBy")); }

    /** {@code this <@ other} */
    default Condition jsonContainedBy(Field<T> other) { return containedBy(other); }

    /** {@code jsonb_path_exists(this, 'jsonpath')} – the same as {@link #pathExists(String)}. */
    default Condition jsonPathExists(String jsonPath) { return pathExists(jsonPath); }

    /** {@code jsonb_path_match(this, 'predicate')} – the same as {@link #pathMatch(String)}. */
    default Condition jsonPathMatches(String jsonPathPredicate) { return pathMatch(jsonPathPredicate); }

    /** {@code jsonb_exists(this, 'key')} – the {@code ?} operator, written as a function because of JDBC. */
    default Condition hasKey(String key) { return Fields.condition(Ops.call("jsonb_exists", this, Ops.literal(key)), true); }

    /** {@code jsonb_exists_any(this, '{a,b}')} – the {@code ?|} operator. */
    default Condition hasAnyKey(String... keys) {
        return Fields.condition(Ops.call("jsonb_exists_any", this, Ops.literal(Ops.textArray(keys))), true);
    }

    /** {@code jsonb_exists_all(this, '{a,b}')} – the {@code ?&} operator. */
    default Condition hasAllKeys(String... keys) {
        return Fields.condition(Ops.call("jsonb_exists_all", this, Ops.literal(Ops.textArray(keys))), true);
    }

    /** {@code jsonb_path_exists(this, 'jsonpath')} */
    default Condition pathExists(String jsonPath) {
        Ops.require(jsonPath, "pathExists");
        return Fields.condition(ctx -> ctx.append("jsonb_path_exists(").visit(this).append(", ")
                .append(Literals.quote(jsonPath)).append("::jsonpath)"), true);
    }

    /** {@code jsonb_path_match(this, 'predicate')} */
    default Condition pathMatch(String jsonPathPredicate) {
        Ops.require(jsonPathPredicate, "pathMatch");
        return Fields.condition(ctx -> ctx.append("jsonb_path_match(").visit(this).append(", ")
                .append(Literals.quote(jsonPathPredicate)).append("::jsonpath)"), true);
    }

    /** {@code (this - 'key')} – removes a key. */
    default JsonField<T> deleteKey(String key) { return Fields.json(type(), Ops.binary(this, "-", Ops.literal(key))); }

    /** {@code (this || other)} – merges two {@code jsonb} values. */
    default JsonField<T> concat(Field<?> other) { return Fields.json(type(), Ops.binary(this, "||", Ops.field(other))); }

    @Override
    @SuppressWarnings("unchecked")
    default JsonField<T> as(String alias) { return (JsonField<T>) Fields.alias(this, alias); }
}
