package ch.lxrin.ql.types;

import java.lang.reflect.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Function;

/**
 * A SQL type together with its Java representation and the rules to bind
 * and read values over JDBC.
 *
 * <p>Every field, column and bind parameter has a {@code DataType}. Binding,
 * reading results and entity fields all go through it, so a type registered
 * once (for example a value object) is used consistently everywhere.</p>
 *
 * <p>Built-in types are in {@link SqlTypes}. New types are usually derived
 * from an existing one:</p>
 * <pre>{@code
 * public static final DataType<UserId> USER_ID = SqlTypes.UUID.map(UserId.class, UserId::new, UserId::value);
 * }</pre>
 *
 * @param <T> the Java type
 */
public final class DataType<T> {

    /**
     * Low-level JDBC access for one type. Implement it only for types that
     * cannot be derived with {@link DataType#map} or {@link DataType#convert}.
     *
     * @param <T> the Java type
     */
    public interface Access<T> {

        /** Binds a non-null value. */
        void set(ValueContext ctx, PreparedStatement ps, int index, T value) throws SQLException;

        /** Reads a value; returns {@code null} for SQL {@code NULL}. */
        T get(ValueContext ctx, ResultSet rs, int index) throws SQLException;

        /** Converts a non-null value to an element for {@code Connection.createArrayOf}. */
        Object toArrayElement(ValueContext ctx, T value);

        /** Converts an element of a JDBC array (never {@code null}) to the Java type. */
        T fromArrayElement(ValueContext ctx, Object element);

        /**
         * Returns the value as an escaped SQL literal, used by {@code Dsl.inline(..)}.
         *
         * @throws UnsupportedOperationException if the type has no literal form
         */
        default String literal(T value) {
            throw new UnsupportedOperationException("no SQL literal form for this type");
        }
    }

    private final String sqlName;
    private final Class<T> javaType;
    private final Kind kind;
    private final Access<T> access;
    private final boolean sensitive;
    private final DataType<?> elementType;

    private DataType(String sqlName, Class<T> javaType, Kind kind, Access<T> access, boolean sensitive,
                     DataType<?> elementType) {
        this.sqlName = Objects.requireNonNull(sqlName, "sqlName");
        this.javaType = Objects.requireNonNull(javaType, "javaType");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.access = Objects.requireNonNull(access, "access");
        this.sensitive = sensitive;
        this.elementType = elementType;
    }

    /**
     * Creates a new base type.
     *
     * @param sqlName  the SQL type name, used in casts and for arrays (e.g. {@code "text"})
     * @param javaType the Java type
     * @param kind     the family, which decides the available operations
     * @param access   JDBC access
     */
    public static <T> DataType<T> of(String sqlName, Class<T> javaType, Kind kind, Access<T> access) {
        SqlNames.requireTypeName(sqlName);
        return new DataType<>(sqlName, javaType, kind, access, false, null);
    }

    /** Returns the SQL type name, e.g. {@code "timestamptz"} or {@code "text[]"}. */
    public String sqlName() {
        return sqlName;
    }

    /** Returns the Java type. */
    public Class<T> javaType() {
        return javaType;
    }

    /** Returns the family of this type. */
    public Kind kind() {
        return kind;
    }

    /** Returns {@code true} if bind values of this type are redacted in logs and exceptions. */
    public boolean sensitive() {
        return sensitive;
    }

    /** Returns the element type of an array type, or {@code null}. */
    public DataType<?> elementType() {
        return elementType;
    }

    /** Returns the JDBC access of this type. */
    public Access<T> access() {
        return access;
    }

    // -------------------------------------------------------------------------
    // Derived types
    // -------------------------------------------------------------------------

    /** Returns a copy whose bind values are shown as {@code ***} in logs and error messages. */
    public DataType<T> asSensitive() {
        return new DataType<>(sqlName, javaType, kind, access, true, elementType);
    }

    /** Returns a copy with another {@link Kind}, e.g. to offer string operations on a value object. */
    public DataType<T> withKind(Kind newKind) {
        return new DataType<>(sqlName, javaType, newKind, access, sensitive, elementType);
    }

    /**
     * Derives a type for a value object or another Java representation of the
     * same SQL type. The result has {@link Kind#OTHER}.
     *
     * @param type   the new Java type
     * @param fromDb converts a database value (never {@code null})
     * @param toDb   converts a Java value (never {@code null})
     */
    public <U> DataType<U> map(Class<U> type, Function<? super T, ? extends U> fromDb, Function<? super U, ? extends T> toDb) {
        Objects.requireNonNull(fromDb, "fromDb");
        Objects.requireNonNull(toDb, "toDb");
        return convert(new Converter<U, T>() {
            @Override
            public Class<U> javaType() {
                return type;
            }

            @Override
            public U fromDatabase(T value) {
                return fromDb.apply(value);
            }

            @Override
            public T toDatabase(U value) {
                return toDb.apply(value);
            }
        });
    }

    /** Derives a type through a {@link Converter}. The result has {@link Kind#OTHER}. */
    public <U> DataType<U> convert(Converter<U, T> converter) {
        Objects.requireNonNull(converter, "converter");
        Access<T> base = access;
        Access<U> mapped = new Access<>() {
            @Override
            public void set(ValueContext ctx, PreparedStatement ps, int index, U value) throws SQLException {
                base.set(ctx, ps, index, converter.toDatabase(value));
            }

            @Override
            public U get(ValueContext ctx, ResultSet rs, int index) throws SQLException {
                T v = base.get(ctx, rs, index);
                return v == null ? null : converter.fromDatabase(v);
            }

            @Override
            public Object toArrayElement(ValueContext ctx, U value) {
                return base.toArrayElement(ctx, converter.toDatabase(value));
            }

            @Override
            public U fromArrayElement(ValueContext ctx, Object element) {
                return converter.fromDatabase(base.fromArrayElement(ctx, element));
            }

            @Override
            public String literal(U value) {
                return base.literal(converter.toDatabase(value));
            }
        };
        return new DataType<>(sqlName, converter.javaType(), Kind.OTHER, mapped, sensitive, null);
    }

    /** Returns the array type of this type, e.g. {@code text[]} for {@code text}. */
    @SuppressWarnings("unchecked")
    public DataType<T[]> array() {
        if (elementType != null) throw new UnsupportedOperationException("nested arrays are not supported: " + sqlName);
        Class<T[]> arrayClass = (Class<T[]>) Array.newInstance(javaType, 0).getClass();
        DataType<T> element = this;
        String baseName = sqlName;
        Access<T[]> arrayAccess = new Access<>() {
            @Override
            public void set(ValueContext ctx, PreparedStatement ps, int index, T[] value) throws SQLException {
                Object[] elements = new Object[value.length];
                for (int i = 0; i < value.length; i++) {
                    elements[i] = value[i] == null ? null : access.toArrayElement(ctx, value[i]);
                }
                ps.setArray(index, ctx.connection().createArrayOf(baseName, elements));
            }

            @Override
            public T[] get(ValueContext ctx, ResultSet rs, int index) throws SQLException {
                java.sql.Array array = rs.getArray(index);
                if (array == null) return null;
                try {
                    return fromArrayElement(ctx, array.getArray());
                } finally {
                    array.free();
                }
            }

            @Override
            public Object toArrayElement(ValueContext ctx, T[] value) {
                throw new UnsupportedOperationException("nested arrays are not supported");
            }

            @Override
            public T[] fromArrayElement(ValueContext ctx, Object raw) {
                int n = Array.getLength(raw);
                T[] result = (T[]) Array.newInstance(javaType, n);
                for (int i = 0; i < n; i++) {
                    Object e = Array.get(raw, i);
                    result[i] = e == null ? null : access.fromArrayElement(ctx, e);
                }
                return result;
            }

            @Override
            public String literal(T[] value) {
                StringBuilder sb = new StringBuilder("ARRAY[");
                for (int i = 0; i < value.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(value[i] == null ? "NULL" : access.literal(value[i]));
                }
                return sb.append("]::").append(baseName).append("[]").toString();
            }
        };
        return new DataType<>(sqlName + "[]", arrayClass, Kind.ARRAY, arrayAccess, sensitive, element);
    }

    /**
     * Converts a plain JDBC value of the underlying SQL type (for example a
     * {@code UUID} or a {@code Long} from {@code nextval}) to this type's Java
     * type, applying mappings such as value objects.
     */
    public T fromRaw(Object value) {
        if (value == null) return null;
        return access.fromArrayElement(NO_CONTEXT, value);
    }

    /**
     * Returns the value's text form on the database side (e.g. the UUID of a
     * value object, an enum label, an ISO timestamp), used for opaque cursors.
     */
    public String encodeText(T value) {
        return String.valueOf(access.toArrayElement(NO_CONTEXT, value));
    }

    /** Parses a text form produced by {@link #encodeText(Object)}. */
    public T decodeText(String text) {
        return access.fromArrayElement(NO_CONTEXT, text);
    }

    private static final ValueContext NO_CONTEXT = new ValueContext() {
        @Override
        public java.sql.Connection connection() {
            throw new IllegalStateException("no connection available");
        }

        @Override
        public JsonCodec jsonCodec() {
            throw new IllegalStateException("no JSON codec available");
        }
    };

    /** Returns {@code true} if {@code value} can be bound with this type. */
    public boolean accepts(Object value) {
        return value == null || javaType.isInstance(value);
    }

    /**
     * Casts {@code value} to this type's Java type after a runtime check. Used
     * by generic code such as listeners that copy values between columns.
     *
     * @throws IllegalArgumentException if the value has another type
     */
    public T cast(Object value) {
        if (!accepts(value)) {
            throw new IllegalArgumentException("value of type " + value.getClass().getName()
                    + " does not fit SQL type " + sqlName + " (" + javaType.getName() + ")");
        }
        return javaType.cast(value);
    }

    @Override
    public String toString() {
        return sqlName + "<" + javaType.getSimpleName() + ">";
    }
}
