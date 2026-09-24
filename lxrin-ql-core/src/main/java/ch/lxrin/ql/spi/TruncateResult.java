package ch.lxrin.ql.spi;

import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.runtime.TransactionScope;
import ch.lxrin.ql.schema.Table;

import java.util.List;
import java.util.Map;

/** What an after-listener sees once a {@code TRUNCATE} has run. */
public interface TruncateResult {

    /** Returns the truncated tables. */
    List<Table<?>> tables();

    /** Returns the rows read before truncating, if requested with {@code captureRowsBeforeTruncate()}. */
    Map<Table<?>, List<AffectedRow>> capturedRows();

    /** Returns a query context on the same connection and transaction. */
    QueryContext dsl();

    /** Returns the current transaction. */
    TransactionScope transaction();
}
