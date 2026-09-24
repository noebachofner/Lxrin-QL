package com.example.archsample.bad;

import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.spi.SoftDeletePolicy;

/** Bypasses a policy outside the allowed package. */
public final class BypassEverywhere {

    private BypassEverywhere() {}

    public static QueryContext all(QueryContext ctx) {
        return ctx.bypassing(SoftDeletePolicy.class);
    }
}
