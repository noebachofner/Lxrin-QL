package ch.lxrin.ql.spi;

import ch.lxrin.ql.schema.Table;

/**
 * Intercepts {@code INSERT}, {@code UPDATE}, {@code DELETE} and
 * {@code TRUNCATE}, whether they come from the DSL or from a repository.
 *
 * <p>Before execution a listener sees the statement's structure and may
 * change it (add values, conditions, requested returning columns) or reject
 * it. After execution it sees the affected rows and may run further
 * statements in the same transaction through {@link WriteResult#dsl()}. An
 * exception thrown by a listener rolls the transaction back.</p>
 *
 * <p>If no transaction is active, the statement and its listeners run in one
 * that is started automatically.</p>
 */
public interface StatementListener {

    /** Returns {@code true} if this listener is interested in writes to {@code table}. */
    default boolean appliesTo(Table<?> table) {
        return true;
    }

    /** Called before an {@code INSERT}. */
    default void beforeInsert(InsertContext context) {}

    /** Called before an {@code UPDATE} (including soft deletes). */
    default void beforeUpdate(UpdateContext context) {}

    /** Called before a {@code DELETE}. */
    default void beforeDelete(DeleteContext context) {}

    /** Called before a {@code TRUNCATE} of at least one applicable table. */
    default void beforeTruncate(TruncateContext context) {}

    /** Called after an {@code INSERT}. */
    default void afterInsert(WriteResult result) {}

    /** Called after an {@code UPDATE}. */
    default void afterUpdate(WriteResult result) {}

    /** Called after a {@code DELETE}. */
    default void afterDelete(WriteResult result) {}

    /** Called after a {@code TRUNCATE}. */
    default void afterTruncate(TruncateResult result) {}
}
