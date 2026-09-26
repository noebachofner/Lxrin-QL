package ch.lxrin.ql.audit;

import ch.lxrin.ql.types.DataType;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * The user stored in each revision row, supplied by the application, e.g. the user's UUID
 * from the security context:
 *
 * <pre>{@code
 * AuditUser.of(SqlTypes.UUID, CurrentUser::id)
 * }</pre>
 *
 * @param <T> the type of the revision's user column
 */
public interface AuditUser<T> {

    /** Returns the SQL type of the user column. */
    DataType<T> type();

    /** Returns the current user, or {@code null} if there is none (e.g. a scheduled job). */
    T current();

    /** Creates an audit user from a type and a supplier. */
    static <T> AuditUser<T> of(DataType<T> type, Supplier<? extends T> current) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(current, "current");
        return new AuditUser<T>() {
            @Override
            public DataType<T> type() {
                return type;
            }

            @Override
            public T current() {
                return current.get();
            }
        };
    }
}
