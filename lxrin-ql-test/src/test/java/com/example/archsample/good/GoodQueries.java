package com.example.archsample.good;

import ch.lxrin.ql.dsl.Condition;
import ch.lxrin.ql.dsl.Field;

/** Uses only the typed DSL. */
public final class GoodQueries {

    private GoodQueries() {}

    public static Condition named(Field<String> name, String value) {
        return name.eq(value);
    }
}
