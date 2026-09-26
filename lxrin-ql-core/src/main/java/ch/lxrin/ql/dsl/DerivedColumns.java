package ch.lxrin.ql.dsl;

import java.util.List;

/** Checks the output fields of a CTE or derived table. */
final class DerivedColumns {

    private DerivedColumns() {}

    static List<Field<?>> require(List<Field<?>> fields, String what) {
        if (fields.isEmpty()) throw new IllegalArgumentException(what + " needs at least one output field");
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).name() == null) {
                throw new IllegalArgumentException("field " + (i + 1) + " of " + what
                        + " has no name; give it an alias with as(\"name\")");
            }
        }
        return fields;
    }
}
