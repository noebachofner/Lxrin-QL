package ch.lxrin.ql.spring;

import ch.lxrin.ql.runtime.QueryContext;

/** Customises the auto-configured {@link QueryContext} before it is built. */
@FunctionalInterface
public interface QueryContextCustomizer {

    /** Changes the builder. */
    void customize(QueryContext.Builder builder);
}
