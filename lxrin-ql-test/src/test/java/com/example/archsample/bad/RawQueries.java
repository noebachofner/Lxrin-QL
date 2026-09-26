package com.example.archsample.bad;

import ch.lxrin.ql.dsl.Condition;
import ch.lxrin.ql.dsl.Sql;
import ch.lxrin.ql.render.QueryPart;
import ch.lxrin.ql.render.RenderContext;

/** Violates the rules on purpose. */
public final class RawQueries implements QueryPart {

    public static Condition raw() {
        return Sql.condition("1 = 1");
    }

    @Override
    public void render(RenderContext ctx) {
        ctx.append("now()");
    }

    public static java.sql.Connection jdbc() {
        return null;
    }
}
