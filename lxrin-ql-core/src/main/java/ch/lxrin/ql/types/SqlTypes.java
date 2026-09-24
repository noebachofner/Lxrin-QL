package ch.lxrin.ql.types;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * The built-in {@link DataType}s.
 *
 * <p>All temporal types are converted without the JVM's default time zone:
 * {@code timestamptz} is an {@link Instant} (sent as a UTC offset date-time),
 * {@code timestamp} is a {@link LocalDateTime}, {@code date} a
 * {@link LocalDate} and {@code time} a {@link LocalTime}.</p>
 */
public final class SqlTypes {

    private SqlTypes() {}

    // =========================================================================
    // Text
    // =========================================================================

    /** {@code text} ↔ {@code String}. */
    public static final DataType<String> TEXT = string("text");
    /** {@code varchar} ↔ {@code String}. */
    public static final DataType<String> VARCHAR = string("varchar");
    /** {@code char(n)} ↔ {@code String}. */
    public static final DataType<String> CHAR = string("bpchar");
    /** {@code citext} (extension) ↔ {@code String}. */
    public static final DataType<String> CITEXT = string("citext");

    // =========================================================================
    // Numbers
    // =========================================================================

    /** {@code smallint} ↔ {@code Short}. */
    public static final DataType<Short> INT2 = number("int2", Short.class, Number::shortValue);
    /** {@code integer} ↔ {@code Integer}. */
    public static final DataType<Integer> INT4 = number("int4", Integer.class, Number::intValue);
    /** {@code bigint} ↔ {@code Long}. */
    public static final DataType<Long> INT8 = number("int8", Long.class, Number::longValue);
    /** {@code numeric} ↔ {@code BigDecimal}. */
    public static final DataType<BigDecimal> NUMERIC = number("numeric", BigDecimal.class, SqlTypes::toBigDecimal);
    /** {@code real} ↔ {@code Float}. */
    public static final DataType<Float> FLOAT4 = number("float4", Float.class, Number::floatValue);
    /** {@code double precision} ↔ {@code Double}. */
    public static final DataType<Double> FLOAT8 = number("float8", Double.class, Number::doubleValue);

    // =========================================================================
    // Boolean, UUID, binary
    // =========================================================================

    /** {@code boolean} ↔ {@code Boolean}. */
    public static final DataType<Boolean> BOOL = DataType.of("bool", Boolean.class, Kind.BOOLEAN, new SimpleAccess<>() {
        @Override
        public void set(ValueContext ctx, PreparedStatement ps, int index, Boolean value) throws SQLException {
            ps.setBoolean(index, value);
        }

        @Override
        public Boolean get(ValueContext ctx, ResultSet rs, int index) throws SQLException {
            Object v = rs.getObject(index);
            return v == null ? null : fromArrayElement(ctx, v);
        }

        @Override
        public Boolean fromArrayElement(ValueContext ctx, Object element) {
            if (element instanceof Boolean) return (Boolean) element;
            if (element instanceof String) return "t".equals(element) || "true".equalsIgnoreCase((String) element);
            return ((Number) element).intValue() != 0;
        }

        @Override
        public String literal(Boolean value) {
            return value ? "TRUE" : "FALSE";
        }
    });

    /** {@code uuid} ↔ {@code UUID}. */
    public static final DataType<UUID> UUID = DataType.of("uuid", java.util.UUID.class, Kind.OTHER, new SimpleAccess<>() {
        @Override
        public void set(ValueContext ctx, PreparedStatement ps, int index, UUID value) throws SQLException {
            ps.setObject(index, value);
        }

        @Override
        public UUID get(ValueContext ctx, ResultSet rs, int index) throws SQLException {
            Object v = rs.getObject(index);
            return v == null ? null : fromArrayElement(ctx, v);
        }

        @Override
        public UUID fromArrayElement(ValueContext ctx, Object element) {
            return element instanceof UUID ? (UUID) element : java.util.UUID.fromString(element.toString());
        }

        @Override
        public String literal(UUID value) {
            return "UUID " + Literals.quote(value.toString());
        }
    });

    /** {@code bytea} ↔ {@code byte[]}. */
    public static final DataType<byte[]> BYTEA = DataType.of("bytea", byte[].class, Kind.BINARY, new SimpleAccess<>() {
        @Override
        public void set(ValueContext ctx, PreparedStatement ps, int index, byte[] value) throws SQLException {
            ps.setBytes(index, value);
        }

        @Override
        public byte[] get(ValueContext ctx, ResultSet rs, int index) throws SQLException {
            return rs.getBytes(index);
        }

        @Override
        public byte[] fromArrayElement(ValueContext ctx, Object element) {
            return (byte[]) element;
        }
    });

    // =========================================================================
    // Date and time
    // =========================================================================

    /** {@code date} ↔ {@code LocalDate}. */
    public static final DataType<LocalDate> DATE = temporal("date", LocalDate.class, "DATE");
    /** {@code time} ↔ {@code LocalTime}. */
    public static final DataType<LocalTime> TIME = temporal("time", LocalTime.class, "TIME");
    /** {@code timestamp} (without time zone) ↔ {@code LocalDateTime}. */
    public static final DataType<LocalDateTime> TIMESTAMP = temporal("timestamp", LocalDateTime.class, "TIMESTAMP");

    /** {@code timestamptz} ↔ {@code OffsetDateTime}, always read with offset UTC. */
    public static final DataType<OffsetDateTime> TIMESTAMPTZ_OFFSET = DataType.of("timestamptz", OffsetDateTime.class,
            Kind.TEMPORAL, new SimpleAccess<>() {
                @Override
                public void set(ValueContext ctx, PreparedStatement ps, int index, OffsetDateTime value) throws SQLException {
                    ps.setObject(index, value);
                }

                @Override
                public OffsetDateTime get(ValueContext ctx, ResultSet rs, int index) throws SQLException {
                    OffsetDateTime v = rs.getObject(index, OffsetDateTime.class);
                    return v == null ? null : v.withOffsetSameInstant(ZoneOffset.UTC);
                }

                @Override
                public OffsetDateTime fromArrayElement(ValueContext ctx, Object element) {
                    if (element instanceof OffsetDateTime) return ((OffsetDateTime) element).withOffsetSameInstant(ZoneOffset.UTC);
                    if (element instanceof java.sql.Timestamp) {
                        return ((java.sql.Timestamp) element).toInstant().atOffset(ZoneOffset.UTC);
                    }
                    return OffsetDateTime.parse(element.toString());
                }

                @Override
                public String literal(OffsetDateTime value) {
                    return "TIMESTAMPTZ " + Literals.quote(value.toString());
                }
            });

    /** {@code timestamptz} ↔ {@code Instant}. */
    public static final DataType<Instant> TIMESTAMPTZ = DataType.of("timestamptz", Instant.class, Kind.TEMPORAL,
            new SimpleAccess<>() {
                @Override
                public void set(ValueContext ctx, PreparedStatement ps, int index, Instant value) throws SQLException {
                    ps.setObject(index, OffsetDateTime.ofInstant(value, ZoneOffset.UTC));
                }

                @Override
                public Instant get(ValueContext ctx, ResultSet rs, int index) throws SQLException {
                    OffsetDateTime v = rs.getObject(index, OffsetDateTime.class);
                    return v == null ? null : v.toInstant();
                }

                @Override
                public Object toArrayElement(ValueContext ctx, Instant value) {
                    return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
                }

                @Override
                public Instant fromArrayElement(ValueContext ctx, Object element) {
                    if (element instanceof java.sql.Timestamp) return ((java.sql.Timestamp) element).toInstant();
                    if (element instanceof OffsetDateTime) return ((OffsetDateTime) element).toInstant();
                    return OffsetDateTime.parse(element.toString()).toInstant();
                }

                @Override
                public String literal(Instant value) {
                    return "TIMESTAMPTZ " + Literals.quote(OffsetDateTime.ofInstant(value, ZoneOffset.UTC).toString());
                }
            });

    /**
     * {@code interval} ↔ {@code Duration}. Reading an interval with months or
     * years fails, because such intervals have no fixed length.
     */
    public static final DataType<Duration> INTERVAL = DataType.of("interval", Duration.class, Kind.OTHER, new SimpleAccess<>() {
        @Override
        public void set(ValueContext ctx, PreparedStatement ps, int index, Duration value) throws SQLException {
            ps.setObject(index, value.toString(), Types.OTHER);
        }

        @Override
        public Duration get(ValueContext ctx, ResultSet rs, int index) throws SQLException {
            String v = rs.getString(index);
            return v == null ? null : Intervals.parse(v);
        }

        @Override
        public Object toArrayElement(ValueContext ctx, Duration value) {
            return value.toString();
        }

        @Override
        public Duration fromArrayElement(ValueContext ctx, Object element) {
            return Intervals.parse(element.toString());
        }

        @Override
        public String literal(Duration value) {
            return "INTERVAL " + Literals.quote(value.toString());
        }
    });

    /** {@code interval} ↔ its text form, for intervals with months or years. */
    public static final DataType<String> INTERVAL_TEXT = other("interval", Kind.OTHER);

    // =========================================================================
    // JSON
    // =========================================================================

    /** {@code jsonb} ↔ JSON text. */
    public static final DataType<String> JSONB = other("jsonb", Kind.JSON);
    /** {@code json} ↔ JSON text. */
    public static final DataType<String> JSON = other("json", Kind.JSON);

    /** {@code daterange} ↔ its text form, e.g. {@code [2024-01-01,2024-02-01)}. */
    public static final DataType<String> DATERANGE = other("daterange", Kind.OTHER);
    /** {@code tsrange} ↔ its text form. */
    public static final DataType<String> TSRANGE = other("tsrange", Kind.OTHER);
    /** {@code tstzrange} ↔ its text form. */
    public static final DataType<String> TSTZRANGE = other("tstzrange", Kind.OTHER);
    /** {@code int4range} ↔ its text form. */
    public static final DataType<String> INT4RANGE = other("int4range", Kind.OTHER);
    /** {@code int8range} ↔ its text form. */
    public static final DataType<String> INT8RANGE = other("int8range", Kind.OTHER);
    /** {@code numrange} ↔ its text form. */
    public static final DataType<String> NUMRANGE = other("numrange", Kind.OTHER);

    /** {@code tsvector} ↔ its text form. */
    public static final DataType<String> TSVECTOR = other("tsvector", Kind.OTHER);
    /** {@code tsquery} ↔ its text form. */
    public static final DataType<String> TSQUERY = other("tsquery", Kind.OTHER);

    /**
     * {@code jsonb} ↔ any Java type, converted with the {@link JsonCodec} of the query context.
     *
     * @param type the Java class, e.g. a record
     */
    public static <T> DataType<T> jsonb(Class<T> type) {
        return jsonType("jsonb", type, type);
    }

    /** {@code json} ↔ any Java type, converted with the {@link JsonCodec} of the query context. */
    public static <T> DataType<T> json(Class<T> type) {
        return jsonType("json", type, type);
    }

    /**
     * {@code jsonb} ↔ a generic Java type such as {@code List<Tag>}.
     *
     * @param rawType     the class used as the Java type of the column
     * @param genericType the full generic type given to the codec
     */
    public static <T> DataType<T> jsonb(Class<T> rawType, Type genericType) {
        return jsonType("jsonb", rawType, genericType);
    }

    private static <T> DataType<T> jsonType(String sqlName, Class<T> rawType, Type genericType) {
        Objects.requireNonNull(rawType, "type");
        return DataType.of(sqlName, rawType, Kind.JSON, new DataType.Access<>() {
            @Override
            public void set(ValueContext ctx, PreparedStatement ps, int index, T value) throws SQLException {
                ps.setObject(index, ctx.jsonCodec().write(value), Types.OTHER);
            }

            @Override
            public T get(ValueContext ctx, ResultSet rs, int index) throws SQLException {
                String v = rs.getString(index);
                return v == null ? null : ctx.jsonCodec().read(v, genericType);
            }

            @Override
            public Object toArrayElement(ValueContext ctx, T value) {
                return ctx.jsonCodec().write(value);
            }

            @Override
            public T fromArrayElement(ValueContext ctx, Object element) {
                return ctx.jsonCodec().read(element.toString(), genericType);
            }
        });
    }

    // =========================================================================
    // Enums
    // =========================================================================

    /**
     * A PostgreSQL enum type whose labels equal the Java constant names.
     *
     * @param sqlName  the enum type, optionally schema-qualified (e.g. {@code "app.role"})
     * @param enumType the Java enum
     */
    public static <E extends Enum<E>> DataType<E> pgEnum(String sqlName, Class<E> enumType) {
        return pgEnum(sqlName, enumType, Enum::name);
    }

    /**
     * A PostgreSQL enum type with a label mapping, e.g.
     * {@code pgEnum("role", Role.class, e -> e.name().toLowerCase())} for labels such as {@code admin}.
     */
    public static <E extends Enum<E>> DataType<E> pgEnum(String sqlName, Class<E> enumType, Function<E, String> label) {
        Map<String, E> byLabel = new java.util.HashMap<>();
        for (E e : enumType.getEnumConstants()) {
            try {
                byLabel.put(label.apply(e), e);
            } catch (IllegalArgumentException noLabel) {
                // a Java constant without a database label cannot be read, and fails when written
            }
        }
        return DataType.of(sqlName, enumType, Kind.OTHER, new SimpleAccess<>() {
            @Override
            public void set(ValueContext ctx, PreparedStatement ps, int index, E value) throws SQLException {
                ps.setObject(index, label.apply(value), Types.OTHER);
            }

            @Override
            public E get(ValueContext ctx, ResultSet rs, int index) throws SQLException {
                String v = rs.getString(index);
                return v == null ? null : fromArrayElement(ctx, v);
            }

            @Override
            public Object toArrayElement(ValueContext ctx, E value) {
                return label.apply(value);
            }

            @Override
            public E fromArrayElement(ValueContext ctx, Object element) {
                E e = byLabel.get(element.toString());
                if (e == null) throw new IllegalStateException("unknown label '" + element + "' for " + enumType.getName());
                return e;
            }

            @Override
            public String literal(E value) {
                return Literals.quote(label.apply(value)) + "::" + sqlName;
            }
        });
    }

    /**
     * A PostgreSQL enum type mapped to an existing Java enum whose constant
     * names match the labels ignoring case and separators (e.g. label
     * {@code in-progress} ↔ constant {@code IN_PROGRESS}).
     *
     * @param labels the labels of the PostgreSQL type
     * @throws IllegalArgumentException if a label has no matching constant
     */
    public static <E extends Enum<E>> DataType<E> pgEnumByName(String sqlName, Class<E> enumType, String... labels) {
        Map<E, String> labelOf = new java.util.EnumMap<>(enumType);
        for (String label : labels) {
            String normalized = label.replaceAll("[^A-Za-z0-9]", "_").toUpperCase(java.util.Locale.ROOT);
            E match = null;
            for (E e : enumType.getEnumConstants()) if (e.name().equalsIgnoreCase(normalized)) match = e;
            if (match == null) throw new IllegalArgumentException("no constant of " + enumType.getName() + " for label " + label);
            labelOf.put(match, label);
        }
        return pgEnum(sqlName, enumType, e -> {
            String label = labelOf.get(e);
            if (label == null) throw new IllegalArgumentException(e + " has no label in PostgreSQL type " + sqlName);
            return label;
        });
    }

    /**
     * Any other PostgreSQL type (e.g. {@code inet}, {@code money}, {@code xml})
     * in its text form, bound as an untyped value.
     */
    public static DataType<String> otherAsText(String sqlName) {
        return other(sqlName, Kind.OTHER);
    }

    /** An enum stored by its constant name in a text column. */
    public static <E extends Enum<E>> DataType<E> enumAsText(Class<E> enumType) {
        return TEXT.map(enumType, s -> Enum.valueOf(enumType, s), Enum::name);
    }

    // =========================================================================
    // Lookup
    // =========================================================================

    /**
     * Returns the default type for a Java class: {@code String} → {@code text},
     * {@code Integer} → {@code int4}, {@code Instant} → {@code timestamptz}, ...
     *
     * @throws IllegalArgumentException for classes without a default type
     */
    @SuppressWarnings("unchecked")
    public static <T> DataType<T> forClass(Class<T> type) {
        DataType<?> t;
        if (type == String.class) t = TEXT;
        else if (type == Integer.class || type == int.class) t = INT4;
        else if (type == Long.class || type == long.class) t = INT8;
        else if (type == Short.class || type == short.class) t = INT2;
        else if (type == BigDecimal.class) t = NUMERIC;
        else if (type == Double.class || type == double.class) t = FLOAT8;
        else if (type == Float.class || type == float.class) t = FLOAT4;
        else if (type == Boolean.class || type == boolean.class) t = BOOL;
        else if (type == java.util.UUID.class) t = UUID;
        else if (type == LocalDate.class) t = DATE;
        else if (type == LocalTime.class) t = TIME;
        else if (type == LocalDateTime.class) t = TIMESTAMP;
        else if (type == Instant.class) t = TIMESTAMPTZ;
        else if (type == OffsetDateTime.class) t = TIMESTAMPTZ_OFFSET;
        else if (type == Duration.class) t = INTERVAL;
        else if (type == byte[].class) t = BYTEA;
        else if (type.isArray() && !type.getComponentType().isPrimitive()) t = forClass(type.getComponentType()).array();
        else throw new IllegalArgumentException("no default SQL type for " + type.getName()
                    + "; pass a DataType explicitly, e.g. param(value, SqlTypes.X)");
        return (DataType<T>) t;
    }

    // =========================================================================
    // Factories
    // =========================================================================

    /** Base class for accesses whose array elements need no conversion when binding. */
    private abstract static class SimpleAccess<T> implements DataType.Access<T> {
        @Override
        public Object toArrayElement(ValueContext ctx, T value) {
            return value;
        }
    }

    private static DataType<String> string(String sqlName) {
        return DataType.of(sqlName, String.class, Kind.STRING, new SimpleAccess<>() {
            @Override
            public void set(ValueContext ctx, PreparedStatement ps, int index, String value) throws SQLException {
                ps.setString(index, value);
            }

            @Override
            public String get(ValueContext ctx, ResultSet rs, int index) throws SQLException {
                return rs.getString(index);
            }

            @Override
            public String fromArrayElement(ValueContext ctx, Object element) {
                return element.toString();
            }

            @Override
            public String literal(String value) {
                return Literals.quote(value);
            }
        });
    }

    private static DataType<String> other(String sqlName, Kind kind) {
        return DataType.of(sqlName, String.class, kind, new SimpleAccess<>() {
            @Override
            public void set(ValueContext ctx, PreparedStatement ps, int index, String value) throws SQLException {
                ps.setObject(index, value, Types.OTHER);
            }

            @Override
            public String get(ValueContext ctx, ResultSet rs, int index) throws SQLException {
                return rs.getString(index);
            }

            @Override
            public String fromArrayElement(ValueContext ctx, Object element) {
                return element.toString();
            }

            @Override
            public String literal(String value) {
                return Literals.quote(value) + "::" + sqlName;
            }
        });
    }

    private static <N extends Number> DataType<N> number(String sqlName, Class<N> type, Function<Number, N> convert) {
        return DataType.of(sqlName, type, Kind.NUMBER, new SimpleAccess<>() {
            @Override
            public void set(ValueContext ctx, PreparedStatement ps, int index, N value) throws SQLException {
                ps.setObject(index, value);
            }

            @Override
            public N get(ValueContext ctx, ResultSet rs, int index) throws SQLException {
                Object v = rs.getObject(index);
                return v == null ? null : fromArrayElement(ctx, v);
            }

            @Override
            public N fromArrayElement(ValueContext ctx, Object element) {
                if (type.isInstance(element)) return type.cast(element);
                if (element instanceof Number) return convert.apply((Number) element);
                return convert.apply(new BigDecimal(element.toString()));
            }

            @Override
            public String literal(N value) {
                return Literals.number(value);
            }
        });
    }

    private static BigDecimal toBigDecimal(Number n) {
        if (n instanceof BigDecimal) return (BigDecimal) n;
        if (n instanceof Double || n instanceof Float) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(n.toString());
    }

    private static <T> DataType<T> temporal(String sqlName, Class<T> type, String literalPrefix) {
        return DataType.of(sqlName, type, Kind.TEMPORAL, new SimpleAccess<>() {
            @Override
            public void set(ValueContext ctx, PreparedStatement ps, int index, T value) throws SQLException {
                ps.setObject(index, value);
            }

            @Override
            public T get(ValueContext ctx, ResultSet rs, int index) throws SQLException {
                return rs.getObject(index, type);
            }

            @Override
            public T fromArrayElement(ValueContext ctx, Object element) {
                if (type.isInstance(element)) return type.cast(element);
                Object v = element;
                if (v instanceof java.sql.Date) v = ((java.sql.Date) v).toLocalDate();
                else if (v instanceof java.sql.Time) v = ((java.sql.Time) v).toLocalTime();
                else if (v instanceof java.sql.Timestamp) v = ((java.sql.Timestamp) v).toLocalDateTime();
                if (type.isInstance(v)) return type.cast(v);
                String s = element.toString().replace(' ', 'T');
                if (type == LocalDate.class) return type.cast(LocalDate.parse(s));
                if (type == LocalTime.class) return type.cast(LocalTime.parse(s));
                return type.cast(LocalDateTime.parse(s));
            }

            @Override
            public String literal(T value) {
                return literalPrefix + " " + Literals.quote(value.toString());
            }
        });
    }
}
