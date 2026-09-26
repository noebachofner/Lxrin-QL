package ch.lxrin.ql.types;

/**
 * The family of a {@link DataType}. It decides which operations a typed
 * field offers, for example {@code like(..)} only for {@link #STRING}.
 */
public enum Kind {
    /** Text types ({@code text}, {@code varchar}, {@code citext}, ...). */
    STRING,
    /** Numeric types. */
    NUMBER,
    /** Date, time and timestamp types. */
    TEMPORAL,
    /** {@code boolean}. */
    BOOLEAN,
    /** {@code json} and {@code jsonb}. */
    JSON,
    /** Arrays of another type. */
    ARRAY,
    /** {@code bytea}. */
    BINARY,
    /** Everything else: {@code uuid}, enums, intervals, value objects. */
    OTHER
}
