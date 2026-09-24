package ch.lxrin.ql.spi;

import ch.lxrin.ql.runtime.QueryContext;

/** What a table policy can access while it builds filters. */
public interface PolicyContext {

    /** Returns the query context the statement runs in. */
    QueryContext queryContext();
}
