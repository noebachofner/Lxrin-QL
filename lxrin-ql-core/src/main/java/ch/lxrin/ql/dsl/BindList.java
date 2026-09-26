package ch.lxrin.ql.dsl;

import java.util.List;
import java.util.Objects;

/**
 * A list of values for {@code in(..)} / {@code notIn(..)}, created with
 * {@code b.setList(values)}. The values are bound as <em>one</em> array
 * parameter with the type of the field they are compared with.
 *
 * @param <T> the element type
 */
public final class BindList<T> {

    private final List<T> values;

    BindList(java.util.Collection<? extends T> values) {
        this.values = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(Objects.requireNonNull(values, "values")));
    }

    /** Returns the values. */
    public List<T> values() {
        return values;
    }

    @Override
    public String toString() {
        return "BindList" + values;
    }
}
