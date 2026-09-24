package ch.lxrin.ql.test;

import ch.lxrin.ql.render.Bind;
import ch.lxrin.ql.render.RenderedSql;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Fluent assertions on rendered SQL; create with {@link SqlAssertions#assertThatSql}.
 * Failures throw {@link AssertionError}, so any test framework works.
 */
public final class SqlAssert {

    private final RenderedSql rendered;

    SqlAssert(RenderedSql rendered) {
        this.rendered = rendered;
    }

    /** Checks the SQL text exactly. */
    public SqlAssert isEqualTo(String sql) {
        if (!rendered.sql().equals(sql)) fail("expected SQL\n  " + sql + "\nbut was\n  " + rendered.sql());
        return this;
    }

    /** Checks that the SQL contains a fragment. */
    public SqlAssert contains(String fragment) {
        if (!rendered.sql().contains(fragment)) fail("expected SQL to contain\n  " + fragment + "\nbut was\n  " + rendered.sql());
        return this;
    }

    /** Checks that the SQL does not contain a fragment. */
    public SqlAssert doesNotContain(String fragment) {
        if (rendered.sql().contains(fragment)) fail("expected SQL not to contain\n  " + fragment + "\nbut was\n  " + rendered.sql());
        return this;
    }

    /** Checks the bind values in order (arrays are compared by content). */
    public SqlAssert hasBinds(Object... expected) {
        List<Object> actual = binds();
        List<Object> wanted = new ArrayList<>();
        for (Object o : expected) wanted.add(o instanceof Object[] ? Arrays.asList((Object[]) o) : o);
        if (!actual.equals(wanted)) fail("expected binds " + wanted + " but were " + actual + "\nSQL: " + rendered.sql());
        return this;
    }

    /** Checks that there are no bind values. */
    public SqlAssert hasNoBinds() {
        if (!rendered.binds().isEmpty()) fail("expected no binds but were " + binds());
        return this;
    }

    /** Checks the number of {@code ?} placeholders against the binds and returns this. */
    public SqlAssert isConsistent() {
        long placeholders = rendered.sql().chars().filter(c -> c == '?').count();
        if (placeholders != rendered.binds().size()) {
            fail(placeholders + " placeholders but " + rendered.binds().size() + " binds: " + rendered);
        }
        return this;
    }

    /** Returns the rendered statement for custom checks. */
    public RenderedSql rendered() {
        return rendered;
    }

    private List<Object> binds() {
        List<Object> values = new ArrayList<>();
        for (Bind<?> b : rendered.binds()) values.add(b.value() instanceof Object[] ? Arrays.asList((Object[]) b.value()) : b.value());
        return values;
    }

    private static void fail(String message) {
        throw new AssertionError(Objects.requireNonNull(message));
    }
}
