package ch.lxrin.ql.spi;

import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.runtime.TransactionScope;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.statement.StatementKind;

import java.util.List;

/** What an after-listener sees once a write statement has run. */
public interface WriteResult {

    /** Returns the target table. */
    Table<?> table();

    /** Returns the executed statement kind ({@code UPDATE} for a soft delete). */
    StatementKind kind();

    /** Returns the kind the caller asked for ({@code DELETE} for a soft delete). */
    StatementKind originalKind();

    /** Returns the number of affected rows. */
    long rowCount();

    /** Returns the affected rows with the requested columns. */
    List<AffectedRow> affectedRows();

    /** Returns {@code true} if the statement was an {@code INSERT … ON CONFLICT DO UPDATE}. */
    boolean wasUpsert();

    /** Returns the entities written by a repository call. */
    List<EntityWrite> entityWrites();

    /** Returns where the statement came from. */
    Origin origin();

    /**
     * Returns a query context on the same connection and transaction.
     * Statements run through it have the origin {@link Origin.Type#LISTENER}.
     */
    QueryContext dsl();

    /** Returns the current transaction, for transaction-scoped state such as an audit revision. */
    TransactionScope transaction();
}
