package ch.lxrin.ql.spi;

import ch.lxrin.ql.dsl.Field;
import ch.lxrin.ql.schema.Column;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Factories for {@link ColumnConvention}s.
 *
 * <pre>{@code
 * QueryContext.builder()
 *     .convention(ColumnConventions.createdAt("created_at", clock))
 *     .convention(ColumnConventions.onInsert("created_by", String.class, c -> currentUser.get()))
 *     .convention(ColumnConventions.updatedAt("updated_at", clock))
 * }</pre>
 */
public final class ColumnConventions {

    private ColumnConventions() {}

    /** Sets columns named {@code column} on insert to the supplied value. */
    public static <T> ColumnConvention<T> onInsert(String column, Class<T> type, Function<WriteContext, T> value) {
        return of(column, type, ColumnConvention.When.INSERT, value);
    }

    /** Sets columns named {@code column} on update. */
    public static <T> ColumnConvention<T> onUpdate(String column, Class<T> type, Function<WriteContext, T> value) {
        return of(column, type, ColumnConvention.When.UPDATE, value);
    }

    /** Sets columns named {@code column} on insert and update. */
    public static <T> ColumnConvention<T> onInsertAndUpdate(String column, Class<T> type, Function<WriteContext, T> value) {
        return of(column, type, ColumnConvention.When.INSERT_AND_UPDATE, value);
    }

    /** A convention with an expression value, e.g. {@code now()}. */
    public static <T> ColumnConvention<T> expression(String column, Class<T> type, ColumnConvention.When when,
                                                     Supplier<Field<T>> expression) {
        return new ColumnConvention<>(column + " = expression", byName(column), type, when, false, null, c -> expression.get());
    }

    /** {@code created_at}-style column set on insert from a clock. */
    public static ColumnConvention<Instant> createdAt(String column, Clock clock) {
        return onInsert(column, Instant.class, c -> clock.instant());
    }

    /** {@code updated_at}-style column set on insert and update from a clock. */
    public static ColumnConvention<Instant> updatedAt(String column, Clock clock) {
        return onInsertAndUpdate(column, Instant.class, c -> clock.instant());
    }

    private static <T> ColumnConvention<T> of(String column, Class<T> type, ColumnConvention.When when,
                                              Function<WriteContext, T> value) {
        return new ColumnConvention<>(column, byName(column), type, when, false, value, null);
    }

    private static java.util.function.Predicate<Column<?>> byName(String column) {
        ch.lxrin.ql.render.Identifiers.requireName(column);
        return c -> c.name().equals(column);
    }
}
