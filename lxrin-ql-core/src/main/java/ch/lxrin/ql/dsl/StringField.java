package ch.lxrin.ql.dsl;

import ch.lxrin.ql.types.SqlTypes;

/**
 * A text expression. Patterns and search texts are always bound as
 * parameters; {@link #startsWith}, {@link #endsWith} and {@link #contains}
 * escape {@code %}, {@code _} and {@code \} in the given text.
 */
public interface StringField extends Field<String> {

    /** {@code this LIKE ?} with a pattern that may contain {@code %} and {@code _}. */
    default Condition like(String pattern) { return Ops.compare(this, "LIKE", Ops.value(this, pattern, "like")); }

    /** {@code this NOT LIKE ?} */
    default Condition notLike(String pattern) { return Ops.compare(this, "NOT LIKE", Ops.value(this, pattern, "notLike")); }

    /** {@code this ILIKE ?} – case-insensitive {@code LIKE}. */
    default Condition ilike(String pattern) { return Ops.compare(this, "ILIKE", Ops.value(this, pattern, "ilike")); }

    /** {@code this NOT ILIKE ?} */
    default Condition notIlike(String pattern) { return Ops.compare(this, "NOT ILIKE", Ops.value(this, pattern, "notIlike")); }

    /** {@code this LIKE 'prefix%'} with the prefix escaped. */
    default Condition startsWith(String prefix) { return like(Ops.escapeLike(prefix, "startsWith") + "%"); }

    /** {@code this LIKE '%suffix'} with the suffix escaped. */
    default Condition endsWith(String suffix) { return like("%" + Ops.escapeLike(suffix, "endsWith")); }

    /** {@code this LIKE '%text%'} with the text escaped. */
    default Condition contains(String text) { return like("%" + Ops.escapeLike(text, "contains") + "%"); }

    /** {@code this ILIKE 'prefix%'} with the prefix escaped. */
    default Condition startsWithIgnoreCase(String prefix) { return ilike(Ops.escapeLike(prefix, "startsWithIgnoreCase") + "%"); }

    /** {@code this ILIKE '%suffix'} with the suffix escaped. */
    default Condition endsWithIgnoreCase(String suffix) { return ilike("%" + Ops.escapeLike(suffix, "endsWithIgnoreCase")); }

    /** {@code this ILIKE '%text%'} with the text escaped. */
    default Condition containsIgnoreCase(String text) { return ilike("%" + Ops.escapeLike(text, "containsIgnoreCase") + "%"); }

    /** {@code this ~ ?} – POSIX regular expression. */
    default Condition matches(String regex) { return Ops.compare(this, "~", Ops.value(this, regex, "matches")); }

    /** {@code this ~* ?} – case-insensitive POSIX regular expression. */
    default Condition matchesIgnoreCase(String regex) { return Ops.compare(this, "~*", Ops.value(this, regex, "matchesIgnoreCase")); }

    /** {@code this !~ ?} */
    default Condition notMatches(String regex) { return Ops.compare(this, "!~", Ops.value(this, regex, "notMatches")); }

    /** {@code this SIMILAR TO ?} */
    default Condition similarTo(String pattern) { return Ops.compare(this, "SIMILAR TO", Ops.value(this, pattern, "similarTo")); }

    /** {@code (this || ?)} */
    default StringField concat(String text) { return Fields.string(Ops.binary(this, "||", Ops.value(this, text, "concat"))); }

    /** {@code (this || other)} */
    default StringField concat(Field<String> other) { return Fields.string(Ops.binary(this, "||", Ops.field(other))); }

    /** {@code lower(this)} */
    default StringField lower() { return Fields.string(Ops.call("lower", this)); }

    /** {@code upper(this)} */
    default StringField upper() { return Fields.string(Ops.call("upper", this)); }

    /** {@code btrim(this)} */
    default StringField trim() { return Fields.string(Ops.call("btrim", this)); }

    /** {@code char_length(this)} */
    default NumberField<Integer> length() { return Fields.number(SqlTypes.INT4, Ops.call("char_length", this)); }

    @Override
    default StringField as(String alias) { return (StringField) Fields.alias(this, alias); }

    @Override
    default StringField coalesce(String fallback) { return (StringField) Field.super.coalesce(fallback); }

    @Override
    default StringField coalesce(Field<String> other) { return (StringField) Field.super.coalesce(other); }
}
