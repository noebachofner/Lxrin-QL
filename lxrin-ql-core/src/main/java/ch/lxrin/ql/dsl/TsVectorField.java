package ch.lxrin.ql.dsl;

/**
 * A {@code tsvector} expression. Generated {@code tsvector} columns implement
 * it; {@code toTsvector(..)} results can be wrapped with {@code Conditions.tsvector(field)}.
 */
public interface TsVectorField extends Field<String> {

    /** {@code this @@ query} – full-text match, e.g. with {@code websearchToTsquery("english", text)}. */
    default Condition tsMatches(Field<String> query) { return Ops.compare(this, "@@", Ops.field(query)); }

    @Override
    default TsVectorField as(String alias) { return (TsVectorField) Fields.alias(this, alias); }
}
