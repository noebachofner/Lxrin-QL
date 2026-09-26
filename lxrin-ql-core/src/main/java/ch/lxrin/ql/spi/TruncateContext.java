package ch.lxrin.ql.spi;

import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.statement.TruncateStatement;

import java.util.List;

/**
 * The context of a {@code TRUNCATE} before execution. {@code TRUNCATE} cannot
 * return rows; a listener can reject it or ask for the rows to be read
 * (and locked) first.
 */
public interface TruncateContext {

    /** Returns the statement model. */
    TruncateStatement statement();

    /** Returns the truncated tables. */
    List<Table<?>> tables();

    /** Returns where the statement comes from. */
    Origin origin();

    /** Rejects the statement. */
    void reject(String reason);

    /** Reads all rows of the truncated tables with {@code SELECT … FOR UPDATE} before truncating. */
    void captureRowsBeforeTruncate();

    /** Returns the query context. */
    QueryContext queryContext();
}
