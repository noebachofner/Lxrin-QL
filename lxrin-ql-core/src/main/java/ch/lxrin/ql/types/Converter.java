package ch.lxrin.ql.types;

/**
 * Converts between a Java type used in application code ({@code J}) and a
 * type the database layer already knows ({@code D}).
 *
 * <pre>{@code
 * DataType<UserId> USER_ID = SqlTypes.UUID.convert(new Converter<UserId, UUID>() {
 *     public Class<UserId> javaType() { return UserId.class; }
 *     public UserId fromDatabase(UUID v) { return new UserId(v); }
 *     public UUID toDatabase(UserId v) { return v.value(); }
 * });
 * }</pre>
 *
 * <p>For simple cases {@link DataType#map(Class, java.util.function.Function, java.util.function.Function)}
 * is shorter.</p>
 *
 * @param <J> Java type
 * @param <D> database-side type
 */
public interface Converter<J, D> {

    /** Returns the Java type. */
    Class<J> javaType();

    /** Converts a database value (never {@code null}) to the Java type. */
    J fromDatabase(D value);

    /** Converts a Java value (never {@code null}) to the database type. */
    D toDatabase(J value);
}
