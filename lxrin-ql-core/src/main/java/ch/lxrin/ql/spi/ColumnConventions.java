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
 *     .convention(ColumnConventions.createdBy("created_by", UUID.class, currentUser::id))
 *     .convention(ColumnConventions.updatedAt("updated_at", clock))
 *     .convention(ColumnConventions.updatedBy("updated_by", UUID.class, currentUser::id))
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

    /**
     * {@code created_by}-style column set on insert to the current user, e.g. the user's UUID
     * from the security context. The supplier may return {@code null}, e.g. for system jobs.
     */
    public static <T> ColumnConvention<T> createdBy(String column, Class<T> type, Supplier<? extends T> user) {
        return onInsert(column, type, c -> user.get());
    }

    /** {@code updated_by}-style column set on insert and update to the current user, like {@link #updatedAt}. */
    public static <T> ColumnConvention<T> updatedBy(String column, Class<T> type, Supplier<? extends T> user) {
        return onInsertAndUpdate(column, type, c -> user.get());
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
