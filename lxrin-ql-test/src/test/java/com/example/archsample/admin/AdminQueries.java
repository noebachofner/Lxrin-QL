package com.example.archsample.admin;

import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.spi.SoftDeletePolicy;

/** Allowed to bypass policies. */
public final class AdminQueries {

    private AdminQueries() {}

    public static QueryContext all(QueryContext ctx) {
        return ctx.bypassing(SoftDeletePolicy.class);
    }
}
