package ch.lxrin.ql.dsl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Collects conditions step by step and joins them with {@code AND}
 * ({@link Conditions#builder(Condition...)}) or {@code OR}
 * ({@link Conditions#orBuilder(Condition...)}):
 *
 * <pre>{@code
 * Condition filter = Conditions.builder(USERS.DELETED_AT.isNull())
 *         .addIf(role != null, () -> USERS.ROLE.eq(role))
 *         .addIfPresent(nameFilter, USERS.NAME::containsIgnoreCase)
 *         .build();
 * }</pre>
 *
 * <p>{@code null} conditions are rejected. A builder without conditions
 * builds {@link Condition#noCondition()}, which an {@code UPDATE} or
 * {@code DELETE} rejects unless all rows are confirmed.</p>
 */
public final class ConditionBuilder {

    private final String junction;
    private final List<Condition> conditions = new ArrayList<>();

    ConditionBuilder(String junction) {
        this.junction = junction;
    }

    /** Adds conditions. */
    public ConditionBuilder add(Condition... more) {
        for (Condition c : more) conditions.add(AbstractSelect.requireCondition(c));
        return this;
    }

    /** Adds a collection of conditions. */
    public ConditionBuilder addAll(Collection<? extends Condition> more) {
        conditions.addAll(Conditions.copy(more));
        return this;
    }

    /** Adds the condition only if {@code apply} is {@code true}; the supplier is only called then. */
    public ConditionBuilder addIf(boolean apply, Supplier<? extends Condition> condition) {
        if (apply) add(condition.get());
        return this;
    }

    /** Adds the condition for the value if it is present. */
    public <V> ConditionBuilder addIfPresent(Optional<V> value, Function<? super V, ? extends Condition> condition) {
        conditions.add(Conditions.ifPresent(value, condition));
        return this;
    }

    /** Returns {@code true} if no condition other than {@link Condition#noCondition()} was added. */
    public boolean isEmpty() {
        for (Condition c : conditions) if (c != Condition.noCondition()) return false;
        return true;
    }

    /** Joins the conditions; {@link Condition#noCondition()} if there are none. */
    public Condition build() {
        return "OR".equals(junction) ? Condition.or(conditions) : Condition.and(conditions);
    }
}
