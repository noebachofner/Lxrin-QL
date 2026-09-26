package ch.lxrin.ql.dsl;

import ch.lxrin.ql.types.SqlTypes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * A boolean expression, used in {@code WHERE}, {@code HAVING}, {@code ON},
 * {@code FILTER (WHERE ..)} and {@code CASE WHEN}.
 *
 * <pre>{@code
 * USERS.ROLE.eq(Role.ADMIN).and(USERS.DELETED_AT.isNull())
 * }</pre>
 *
 * <p>Boolean columns are conditions too: {@code where(USERS.ACTIVE)}.</p>
 */
public interface Condition extends Field<Boolean> {

    /**
     * The neutral condition: {@code and}/{@code or} ignore it, and on its own
     * it renders {@code TRUE}. Useful as the start value for optional filters.
     */
    static Condition noCondition() {
        return Fields.NO_CONDITION;
    }

    /** {@code (c1 AND c2 AND ...)}; {@code null} and {@link #noCondition()} entries are skipped. */
    static Condition and(Condition... conditions) {
        return Ops.junction("AND", Arrays.asList(conditions));
    }

    /** {@code (c1 AND c2 AND ...)} for a collection of conditions. */
    static Condition and(Collection<? extends Condition> conditions) {
        return Ops.junction("AND", new ArrayList<>(conditions));
    }

    /** {@code (c1 OR c2 OR ...)}; {@code null} and {@link #noCondition()} entries are skipped. */
    static Condition or(Condition... conditions) {
        return Ops.junction("OR", Arrays.asList(conditions));
    }

    /** {@code (c1 OR c2 OR ...)} for a collection of conditions. */
    static Condition or(Collection<? extends Condition> conditions) {
        return Ops.junction("OR", new ArrayList<>(conditions));
    }

    /** Turns a boolean field into a condition. */
    static Condition of(Field<Boolean> booleanField) {
        if (booleanField instanceof Condition) return (Condition) booleanField;
        return Fields.condition(Ops.field(booleanField), true);
    }

    /** {@code (this AND other)} */
    default Condition and(Condition other) { return Ops.junction("AND", List.of(this, other)); }

    /** {@code (this OR other)} */
    default Condition or(Condition other) { return Ops.junction("OR", List.of(this, other)); }

    /** {@code (this AND other)} if {@code apply} is {@code true}, else {@code this}. */
    default Condition andIf(boolean apply, java.util.function.Supplier<Condition> other) {
        return apply ? and(other.get()) : this;
    }

    /** {@code NOT (this)} */
    default Condition not() {
        Condition self = this;
        return Fields.condition(ctx -> ctx.append("NOT (").visit(self).append(')'), true);
    }

    /** {@code this IS TRUE} */
    default Condition isTrue() { return Ops.postfix(this, "IS TRUE"); }

    /** {@code this IS NOT TRUE} – false or null. */
    default Condition isNotTrue() { return Ops.postfix(this, "IS NOT TRUE"); }

    /** {@code this IS FALSE} */
    default Condition isFalse() { return Ops.postfix(this, "IS FALSE"); }

    /** {@code this IS NOT FALSE} – true or null. */
    default Condition isNotFalse() { return Ops.postfix(this, "IS NOT FALSE"); }

    @Override
    default Condition as(String alias) { return (Condition) Fields.alias(this, alias); }

    /** Returns {@code SqlTypes.BOOL}. */
    @Override
    default ch.lxrin.ql.types.DataType<Boolean> type() { return SqlTypes.BOOL; }
}
