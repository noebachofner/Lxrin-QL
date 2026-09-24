package ch.lxrin.ql.spi;

import ch.lxrin.ql.dsl.Condition;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.statement.DeleteStatement;
import ch.lxrin.ql.statement.DmlStatement;

/**
 * A rule applied to every statement that touches a table, for example
 * tenant isolation or soft delete.
 *
 * <p>{@link #filter} is added for every reference of the table: {@code FROM}
 * (to {@code WHERE}), joins (to {@code ON}), sub-queries, CTE bodies and the
 * {@code WHERE} of {@code UPDATE}/{@code DELETE}. Bypassing a policy is
 * explicit: {@code ctx.bypassing(MyPolicy.class)}.</p>
 */
public interface TablePolicy {

    /** Returns {@code true} if the policy applies to {@code table}. */
    boolean appliesTo(Table<?> table);

    /** Returns the filter for a reference of {@code table} (which may carry an alias). */
    default Condition filter(Table<?> table, PolicyContext context) {
        return Condition.noCondition();
    }

    /** Called before an {@code INSERT} into an applicable table, e.g. to set the tenant column. */
    default void onInsert(InsertContext context) {}

    /** Called before an {@code UPDATE} of an applicable table. */
    default void onUpdate(UpdateContext context) {}

    /** May replace a {@code DELETE}, e.g. by an {@code UPDATE} for soft delete. */
    default DmlStatement onDelete(DeleteStatement statement, PolicyContext context) {
        return statement;
    }

    /** Called before a {@code TRUNCATE} that includes an applicable table, e.g. to reject it. */
    default void onTruncate(TruncateContext context) {}
}
