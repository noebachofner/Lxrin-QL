package ch.lxrin.ql.dsl;

import ch.lxrin.ql.types.DataType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Every condition operator as a static function. Each one has the same
 * meaning, SQL and type checks as the field method of the same name:
 * {@code eq(USERS.EMAIL, email)} is {@code USERS.EMAIL.eq(email)}. All
 * methods are also available through {@code import static ch.lxrin.ql.dsl.Dsl.*}.
 *
 * <p>Values are always bind parameters, and {@code null} values are rejected
 * instead of being dropped; use {@link #when(boolean, Supplier)}, the
 * {@code …IfPresent} functions or a {@link ConditionBuilder} for optional filters.
 * An empty {@code in(..)} is always false, an empty {@code notIn(..)} always true.</p>
 *
 * <pre>{@code
 * where(and(
 *         eq(USERS.ROLE, Role.ADMIN),
 *         or(isNull(USERS.DELETED_AT), gt(USERS.DELETED_AT, since)),
 *         in(USERS.ID, ids),
 *         when(query != null, () -> containsIgnoreCase(USERS.NAME, query))))
 * }</pre>
 */
public class Conditions extends Values {

    /** Static members only; extended by {@link Functions}. */
    protected Conditions() {}

    // =========================================================================
    // Logic
    // =========================================================================

    /** {@code (c1 AND c2 ...)}; {@link #noCondition()} entries are skipped. */
    public static Condition and(Condition... conditions) {
        return Condition.and(conditions);
    }

    /** {@code (c1 AND c2 ...)} for a list of conditions; an empty list is {@link #noCondition()}. */
    public static Condition and(Collection<? extends Condition> conditions) {
        return Condition.and(conditions);
    }

    /** {@code (c1 OR c2 ...)}; {@link #noCondition()} entries are skipped. */
    public static Condition or(Condition... conditions) {
        return Condition.or(conditions);
    }

    /** {@code (c1 OR c2 ...)} for a list of conditions; an empty list is {@link #noCondition()}. */
    public static Condition or(Collection<? extends Condition> conditions) {
        return Condition.or(conditions);
    }

    /** {@code NOT (condition)} */
    public static Condition not(Condition condition) {
        return AbstractSelect.requireCondition(condition).not();
    }

    /** {@code TRUE}, neutral in {@code and(..)} and {@code or(..)}. */
    public static Condition noCondition() {
        return Condition.noCondition();
    }

    /**
     * The condition if {@code apply} is {@code true}, else {@link #noCondition()}.
     * The supplier is only called when the condition applies.
     */
    public static Condition when(boolean apply, Supplier<? extends Condition> condition) {
        if (!apply) return Condition.noCondition();
        return AbstractSelect.requireCondition(condition.get());
    }

    /**
     * The condition for the value if it is present, else {@link #noCondition()}:
     * {@code ifPresent(email, USERS.EMAIL::eq)}.
     */
    public static <V> Condition ifPresent(Optional<V> value, Function<? super V, ? extends Condition> condition) {
        if (value == null) throw new IllegalArgumentException("optional must not be null");
        return value.isPresent() ? AbstractSelect.requireCondition(condition.apply(value.get())) : Condition.noCondition();
    }

    /** A builder that joins its conditions with {@code AND}. */
    public static ConditionBuilder builder(Condition... initial) {
        return new ConditionBuilder("AND").add(initial);
    }

    /** A builder that joins its conditions with {@code OR}. */
    public static ConditionBuilder orBuilder(Condition... initial) {
        return new ConditionBuilder("OR").add(initial);
    }

    /** {@code EXISTS (SELECT ...)} */
    public static Condition exists(AbstractSelect<?, ?> query) {
        return Fields.condition(ctx -> ctx.append("EXISTS ").visit(query), true);
    }

    /** {@code NOT EXISTS (SELECT ...)} */
    public static Condition notExists(AbstractSelect<?, ?> query) {
        return Fields.condition(ctx -> ctx.append("NOT EXISTS ").visit(query), true);
    }

    // =========================================================================
    // Comparison
    // =========================================================================

    /** {@code field = ?} */
    public static <T> Condition eq(Field<T> field, T value) { return field.eq(value); }

    /** {@code a = b} */
    public static <T> Condition eq(Field<T> a, Field<T> b) { return a.eq(b); }

    /** {@code field = ANY(..)} / {@code field = ALL(..)} */
    public static <T> Condition eq(Field<T> field, Quantified<? extends T> q) { return field.eq(q); }

    /** {@code ? = ANY(..)}, e.g. {@code eq("vip", any(USERS.TAGS))}. */
    public static <T> Condition eq(T value, Quantified<T> q) { return quantified(value, "=", q); }

    /** {@code field <> ?} */
    public static <T> Condition ne(Field<T> field, T value) { return field.ne(value); }

    /** {@code a <> b} */
    public static <T> Condition ne(Field<T> a, Field<T> b) { return a.ne(b); }

    /** {@code field <> ANY(..)} / {@code field <> ALL(..)} */
    public static <T> Condition ne(Field<T> field, Quantified<? extends T> q) { return field.ne(q); }

    /** {@code ? <> ANY(..)} / {@code ? <> ALL(..)} */
    public static <T> Condition ne(T value, Quantified<T> q) { return quantified(value, "<>", q); }

    /** {@code field > ?} */
    public static <T> Condition gt(Field<T> field, T value) { return field.gt(value); }

    /** {@code a > b} */
    public static <T> Condition gt(Field<T> a, Field<T> b) { return a.gt(b); }

    /** {@code field > ANY(..)} / {@code field > ALL(..)} */
    public static <T> Condition gt(Field<T> field, Quantified<? extends T> q) { return field.gt(q); }

    /** {@code ? > ANY(..)} / {@code ? > ALL(..)} */
    public static <T> Condition gt(T value, Quantified<T> q) { return quantified(value, ">", q); }

    /** {@code field >= ?} */
    public static <T> Condition ge(Field<T> field, T value) { return field.ge(value); }

    /** {@code a >= b} */
    public static <T> Condition ge(Field<T> a, Field<T> b) { return a.ge(b); }

    /** {@code field >= ANY(..)} / {@code field >= ALL(..)} */
    public static <T> Condition ge(Field<T> field, Quantified<? extends T> q) { return field.ge(q); }

    /** {@code ? >= ANY(..)} / {@code ? >= ALL(..)} */
    public static <T> Condition ge(T value, Quantified<T> q) { return quantified(value, ">=", q); }

    /** {@code field < ?} */
    public static <T> Condition lt(Field<T> field, T value) { return field.lt(value); }

    /** {@code a < b} */
    public static <T> Condition lt(Field<T> a, Field<T> b) { return a.lt(b); }

    /** {@code field < ANY(..)} / {@code field < ALL(..)} */
    public static <T> Condition lt(Field<T> field, Quantified<? extends T> q) { return field.lt(q); }

    /** {@code ? < ANY(..)} / {@code ? < ALL(..)} */
    public static <T> Condition lt(T value, Quantified<T> q) { return quantified(value, "<", q); }

    /** {@code field <= ?} */
    public static <T> Condition le(Field<T> field, T value) { return field.le(value); }

    /** {@code a <= b} */
    public static <T> Condition le(Field<T> a, Field<T> b) { return a.le(b); }

    /** {@code field <= ANY(..)} / {@code field <= ALL(..)} */
    public static <T> Condition le(Field<T> field, Quantified<? extends T> q) { return field.le(q); }

    /** {@code ? <= ANY(..)} / {@code ? <= ALL(..)} */
    public static <T> Condition le(T value, Quantified<T> q) { return quantified(value, "<=", q); }

    /** {@code field IS DISTINCT FROM ?}; {@code null} is allowed and bound as {@code NULL}. */
    public static <T> Condition isDistinctFrom(Field<T> field, T value) { return field.isDistinctFrom(value); }

    /** {@code a IS DISTINCT FROM b} */
    public static <T> Condition isDistinctFrom(Field<T> a, Field<T> b) { return a.isDistinctFrom(b); }

    /** {@code field IS NOT DISTINCT FROM ?}; {@code null} is allowed. */
    public static <T> Condition isNotDistinctFrom(Field<T> field, T value) { return field.isNotDistinctFrom(value); }

    /** {@code a IS NOT DISTINCT FROM b} */
    public static <T> Condition isNotDistinctFrom(Field<T> a, Field<T> b) { return a.isNotDistinctFrom(b); }

    private static <T> Condition quantified(T value, String operator, Quantified<T> q) {
        if (value == null) throw new IllegalArgumentException("the value compared with ANY/ALL must not be null");
        if (q == null) throw new IllegalArgumentException("ANY/ALL must not be null");
        DataType<T> type = q.elementType();
        T checked = type.cast(value);
        return Ops.compare(ctx -> ctx.bind(type, checked), operator, q);
    }

    // =========================================================================
    // BETWEEN
    // =========================================================================

    /** {@code field BETWEEN ? AND ?} */
    public static <T> Condition between(Field<T> field, T from, T to) { return field.between(from, to); }

    /** {@code field BETWEEN from AND to} */
    public static <T> Condition between(Field<T> field, Field<T> from, Field<T> to) { return field.between(from, to); }

    /** {@code field NOT BETWEEN ? AND ?} */
    public static <T> Condition notBetween(Field<T> field, T from, T to) { return field.notBetween(from, to); }

    /** {@code field NOT BETWEEN from AND to} */
    public static <T> Condition notBetween(Field<T> field, Field<T> from, Field<T> to) { return field.notBetween(from, to); }

    /** {@code field BETWEEN SYMMETRIC ? AND ?} – the bounds may be in any order. */
    public static <T> Condition betweenSymmetric(Field<T> field, T a, T b) { return field.betweenSymmetric(a, b); }

    /** {@code field BETWEEN SYMMETRIC a AND b} */
    public static <T> Condition betweenSymmetric(Field<T> field, Field<T> a, Field<T> b) { return field.betweenSymmetric(a, b); }

    /** {@code field NOT BETWEEN SYMMETRIC ? AND ?} */
    public static <T> Condition notBetweenSymmetric(Field<T> field, T a, T b) { return field.notBetweenSymmetric(a, b); }

    /** {@code field NOT BETWEEN SYMMETRIC a AND b} */
    public static <T> Condition notBetweenSymmetric(Field<T> field, Field<T> a, Field<T> b) { return field.notBetweenSymmetric(a, b); }

    // =========================================================================
    // IN, ANY, ALL
    // =========================================================================

    /** {@code field = ANY(?)} – one array parameter; no values is always false. */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> Condition in(Field<T> field, T... values) { return field.in(values); }

    /** {@code field = ANY(?)} for a collection; an empty collection is always false. */
    public static <T> Condition in(Field<T> field, Collection<? extends T> values) { return field.in(values); }

    /** {@code field = ANY(?)} for a list created with {@code b.setList(..)}. */
    public static <T> Condition in(Field<T> field, BindList<? extends T> values) { return field.in(values); }

    /** {@code field IN (SELECT ...)} */
    public static <T> Condition in(Field<T> field, Subquery<? extends T> subquery) { return field.in(subquery); }

    /** {@code field <> ALL(?)}; no values is always true. */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> Condition notIn(Field<T> field, T... values) { return field.notIn(values); }

    /** {@code field <> ALL(?)} for a collection; an empty collection is always true. */
    public static <T> Condition notIn(Field<T> field, Collection<? extends T> values) { return field.notIn(values); }

    /** {@code field <> ALL(?)} for a list created with {@code b.setList(..)}. */
    public static <T> Condition notIn(Field<T> field, BindList<? extends T> values) { return field.notIn(values); }

    /** {@code field NOT IN (SELECT ...)} */
    public static <T> Condition notIn(Field<T> field, Subquery<? extends T> subquery) { return field.notIn(subquery); }

    /** {@code ANY (SELECT ...)}, the right-hand side of a comparison: {@code gt(price, any(select(..)))}. */
    public static <T> Quantified<T> any(Subquery<T> subquery) { return Quantified.any(subquery); }

    /** {@code ALL (SELECT ...)} */
    public static <T> Quantified<T> all(Subquery<T> subquery) { return Quantified.all(subquery); }

    /** {@code ANY (array)}: {@code eq("vip", any(USERS.TAGS))}. */
    public static <T> Quantified<T> any(Field<T[]> array) { return Quantified.any(array); }

    /** {@code ALL (array)} */
    public static <T> Quantified<T> all(Field<T[]> array) { return Quantified.all(array); }

    // =========================================================================
    // NULL and boolean tests
    // =========================================================================

    /** {@code field IS NULL} */
    public static Condition isNull(Field<?> field) { return Ops.postfix(Ops.field(field), "IS NULL"); }

    /** {@code field IS NOT NULL} */
    public static Condition isNotNull(Field<?> field) { return Ops.postfix(Ops.field(field), "IS NOT NULL"); }

    /** {@code condition IS TRUE} */
    public static Condition isTrue(Field<Boolean> condition) { return Condition.of(condition).isTrue(); }

    /** {@code condition IS NOT TRUE} – false or null. */
    public static Condition isNotTrue(Field<Boolean> condition) { return Condition.of(condition).isNotTrue(); }

    /** {@code condition IS FALSE} */
    public static Condition isFalse(Field<Boolean> condition) { return Condition.of(condition).isFalse(); }

    /** {@code condition IS NOT FALSE} – true or null. */
    public static Condition isNotFalse(Field<Boolean> condition) { return Condition.of(condition).isNotFalse(); }

    // =========================================================================
    // Text
    // =========================================================================

    /** {@code text LIKE ?} */
    public static Condition like(StringField text, String pattern) { return text.like(pattern); }

    /** {@code text LIKE ? ESCAPE 'c'} */
    public static Condition like(StringField text, String pattern, char escape) { return text.like(pattern, escape); }

    /** {@code text LIKE pattern} */
    public static Condition like(StringField text, Field<String> pattern) { return text.like(pattern); }

    /** {@code text NOT LIKE ?} */
    public static Condition notLike(StringField text, String pattern) { return text.notLike(pattern); }

    /** {@code text NOT LIKE ? ESCAPE 'c'} */
    public static Condition notLike(StringField text, String pattern, char escape) { return text.notLike(pattern, escape); }

    /** {@code text ILIKE ?} */
    public static Condition ilike(StringField text, String pattern) { return text.ilike(pattern); }

    /** {@code text ILIKE ? ESCAPE 'c'} */
    public static Condition ilike(StringField text, String pattern, char escape) { return text.ilike(pattern, escape); }

    /** {@code text ILIKE pattern} */
    public static Condition ilike(StringField text, Field<String> pattern) { return text.ilike(pattern); }

    /** {@code text NOT ILIKE ?} */
    public static Condition notIlike(StringField text, String pattern) { return text.notIlike(pattern); }

    /** {@code text NOT ILIKE ? ESCAPE 'c'} */
    public static Condition notIlike(StringField text, String pattern, char escape) { return text.notIlike(pattern, escape); }

    /** {@code text LIKE '%suffix'} with {@code %} and {@code _} escaped. */
    public static Condition endsWith(StringField text, String suffix) { return text.endsWith(suffix); }

    /** {@code text LIKE '%part%'} with {@code %} and {@code _} escaped. */
    public static Condition contains(StringField text, String part) { return text.contains(part); }

    /** {@code text ILIKE 'prefix%'} */
    public static Condition startsWithIgnoreCase(StringField text, String prefix) { return text.startsWithIgnoreCase(prefix); }

    /** {@code text ILIKE '%suffix'} */
    public static Condition endsWithIgnoreCase(StringField text, String suffix) { return text.endsWithIgnoreCase(suffix); }

    /** {@code text ILIKE '%part%'} */
    public static Condition containsIgnoreCase(StringField text, String part) { return text.containsIgnoreCase(part); }

    /** {@code text SIMILAR TO ?} */
    public static Condition similarTo(StringField text, String pattern) { return text.similarTo(pattern); }

    /** {@code text NOT SIMILAR TO ?} */
    public static Condition notSimilarTo(StringField text, String pattern) { return text.notSimilarTo(pattern); }

    /** {@code text ~ ?} – POSIX regular expression. */
    public static Condition matches(StringField text, String regex) { return text.matches(regex); }

    /** {@code text ~* ?} */
    public static Condition matchesIgnoreCase(StringField text, String regex) { return text.matchesIgnoreCase(regex); }

    /** {@code text !~ ?} */
    public static Condition notMatches(StringField text, String regex) { return text.notMatches(regex); }

    /** {@code text !~* ?} */
    public static Condition notMatchesIgnoreCase(StringField text, String regex) { return text.notMatchesIgnoreCase(regex); }

    // =========================================================================
    // Arrays
    // =========================================================================

    /** {@code array @> ?} – contains all given elements. */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> Condition arrayContains(ArrayField<E> array, E... elements) { return array.arrayContains(elements); }

    /** {@code a @> b} */
    public static <E> Condition arrayContains(ArrayField<E> a, Field<E[]> b) { return a.arrayContains(b); }

    /** {@code array <@ ?} – all elements are among the given ones. */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> Condition arrayContainedBy(ArrayField<E> array, E... elements) { return array.arrayContainedBy(elements); }

    /** {@code a <@ b} */
    public static <E> Condition arrayContainedBy(ArrayField<E> a, Field<E[]> b) { return a.arrayContainedBy(b); }

    /** {@code array && ?} – has at least one of the given elements. */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> Condition arrayOverlaps(ArrayField<E> array, E... elements) { return array.arrayOverlaps(elements); }

    /** {@code a && b} */
    public static <E> Condition arrayOverlaps(ArrayField<E> a, Field<E[]> b) { return a.arrayOverlaps(b); }

    // =========================================================================
    // Ranges
    // =========================================================================

    /** Returns a range expression as a {@link RangeField}, e.g. the result of {@code tstzrange(..)}. */
    public static RangeField range(Field<String> range) {
        if (range instanceof RangeField) return (RangeField) range;
        if (range == null) throw new IllegalArgumentException("range must not be null");
        return Fields.range(range.type(), Fields.unaliased(range));
    }

    /** {@code range @> value} – the range contains the element or range. */
    public static <T> Condition rangeContains(Field<String> range, Field<T> value) { return range(range).rangeContains(value); }

    /** {@code range @> ?} */
    public static Condition rangeContains(Field<String> range, Instant element) { return range(range).rangeContains(element); }

    /** {@code range @> ?} */
    public static Condition rangeContains(Field<String> range, LocalDate element) { return range(range).rangeContains(element); }

    /** {@code range @> ?} */
    public static Condition rangeContains(Field<String> range, LocalDateTime element) { return range(range).rangeContains(element); }

    /** {@code range @> ?} */
    public static Condition rangeContains(Field<String> range, Integer element) { return range(range).rangeContains(element); }

    /** {@code range @> ?} */
    public static Condition rangeContains(Field<String> range, Long element) { return range(range).rangeContains(element); }

    /** {@code range @> ?} */
    public static Condition rangeContains(Field<String> range, BigDecimal element) { return range(range).rangeContains(element); }

    /** {@code a <@ b} – the range (or element) {@code a} is contained by the range {@code b}. */
    public static Condition rangeContainedBy(Field<?> a, Field<String> b) { return Ops.compare(Ops.field(a), "<@", Ops.field(b)); }

    /** {@code a && b} – the ranges overlap. */
    public static Condition rangeOverlaps(Field<String> a, Field<String> b) { return range(a).rangeOverlaps(b); }

    /** {@code a << b} – strictly left of. */
    public static Condition strictlyLeftOf(Field<String> a, Field<String> b) { return range(a).strictlyLeftOf(b); }

    /** {@code a >> b} – strictly right of. */
    public static Condition strictlyRightOf(Field<String> a, Field<String> b) { return range(a).strictlyRightOf(b); }

    /** {@code a &< b} – does not extend to the right of. */
    public static Condition notExtendsRightOf(Field<String> a, Field<String> b) { return range(a).notExtendsRightOf(b); }

    /** {@code a &> b} – does not extend to the left of. */
    public static Condition notExtendsLeftOf(Field<String> a, Field<String> b) { return range(a).notExtendsLeftOf(b); }

    /** {@code a -|- b} – adjacent. */
    public static Condition adjacentTo(Field<String> a, Field<String> b) { return range(a).adjacentTo(b); }

    // =========================================================================
    // JSON
    // =========================================================================

    /** {@code json @> ?} */
    public static <T> Condition jsonContains(JsonField<T> json, T value) { return json.jsonContains(value); }

    /** {@code a @> b} */
    public static <T> Condition jsonContains(JsonField<T> a, Field<T> b) { return a.jsonContains(b); }

    /** {@code json <@ ?} */
    public static <T> Condition jsonContainedBy(JsonField<T> json, T value) { return json.jsonContainedBy(value); }

    /** {@code a <@ b} */
    public static <T> Condition jsonContainedBy(JsonField<T> a, Field<T> b) { return a.jsonContainedBy(b); }

    /** {@code jsonb_exists(json, 'key')} – the {@code ?} operator. */
    public static Condition hasKey(JsonField<?> json, String key) { return json.hasKey(key); }

    /** {@code jsonb_exists_any(json, '{a,b}')} – the {@code ?|} operator. */
    public static Condition hasAnyKey(JsonField<?> json, String... keys) { return json.hasAnyKey(keys); }

    /** {@code jsonb_exists_all(json, '{a,b}')} – the {@code ?&} operator. */
    public static Condition hasAllKeys(JsonField<?> json, String... keys) { return json.hasAllKeys(keys); }

    /** {@code jsonb_path_exists(json, 'path')} */
    public static Condition jsonPathExists(JsonField<?> json, String jsonPath) { return json.jsonPathExists(jsonPath); }

    /** {@code jsonb_path_match(json, 'predicate')} */
    public static Condition jsonPathMatches(JsonField<?> json, String jsonPathPredicate) { return json.jsonPathMatches(jsonPathPredicate); }

    // =========================================================================
    // Full-text search
    // =========================================================================

    /** Returns a {@code tsvector} expression as a {@link TsVectorField}, e.g. the result of {@code toTsvector(..)}. */
    public static TsVectorField tsvector(Field<String> vector) {
        if (vector instanceof TsVectorField) return (TsVectorField) vector;
        if (vector == null) throw new IllegalArgumentException("vector must not be null");
        return Fields.tsvector(Fields.unaliased(vector));
    }

    /** {@code vector @@ query} */
    public static Condition tsMatches(Field<String> vector, Field<String> query) { return tsvector(vector).tsMatches(query); }

    // =========================================================================
    // Row values
    // =========================================================================

    /** {@code (f1, f2, …)} for row comparisons and {@code IN} with rows. */
    public static RowValue row(Field<?>... fields) { return RowValue.of(fields); }

    // =========================================================================
    // Optional filters
    // =========================================================================

    /** {@code field = ?} if present, else {@link #noCondition()}. */
    public static <T> Condition eqIfPresent(Field<T> field, Optional<? extends T> value) { return field.eqIfPresent(value); }

    /** {@code field <> ?} if present, else no condition. */
    public static <T> Condition neIfPresent(Field<T> field, Optional<? extends T> value) { return field.neIfPresent(value); }

    /** {@code field > ?} if present, else no condition. */
    public static <T> Condition gtIfPresent(Field<T> field, Optional<? extends T> value) { return field.gtIfPresent(value); }

    /** {@code field >= ?} if present, else no condition. */
    public static <T> Condition geIfPresent(Field<T> field, Optional<? extends T> value) { return field.geIfPresent(value); }

    /** {@code field < ?} if present, else no condition. */
    public static <T> Condition ltIfPresent(Field<T> field, Optional<? extends T> value) { return field.ltIfPresent(value); }

    /** {@code field <= ?} if present, else no condition. */
    public static <T> Condition leIfPresent(Field<T> field, Optional<? extends T> value) { return field.leIfPresent(value); }

    /** {@code field = ANY(?)} if present, else no condition; a present empty collection matches nothing. */
    public static <T> Condition inIfPresent(Field<T> field, Optional<? extends Collection<? extends T>> values) {
        return field.inIfPresent(values);
    }

    static List<Condition> copy(Collection<? extends Condition> conditions) {
        List<Condition> list = new ArrayList<>();
        for (Condition c : conditions) list.add(AbstractSelect.requireCondition(c));
        return list;
    }
}
