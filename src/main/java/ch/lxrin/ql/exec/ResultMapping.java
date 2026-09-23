package ch.lxrin.ql.exec;

import ch.lxrin.ql.RowMapper;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Default row mapping used when no {@link RowMapper} is given.
 *
 * <table>
 *   <caption>Mapping rules</caption>
 *   <tr><th>Target type</th><th>Result</th></tr>
 *   <tr><td>{@code Object[]}</td><td>the raw row</td></tr>
 *   <tr><td>simple type ({@code String}, numbers, {@code Boolean}, {@code java.time}, {@code UUID}, enums)</td>
 *       <td>the first column, converted</td></tr>
 *   <tr><td>record</td><td>canonical constructor, columns in order</td></tr>
 *   <tr><td>other classes</td><td>bean with a no-arg constructor; each named select item is written to
 *       the matching setter or field (case-insensitive, underscores ignored)</td></tr>
 * </table>
 */
public final class ResultMapping {

    private ResultMapping() {}

    /**
     * Creates a mapper for {@code type}.
     *
     * @param type  target type
     * @param names result column names (select item aliases, may contain {@code null})
     */
    @SuppressWarnings("unchecked")
    public static <T> RowMapper<T> forType(Class<T> type, List<String> names) {
        if (type == Object[].class || type == Object.class) {
            return row -> (T) row;
        }
        if (isSimpleType(type)) {
            return row -> convert(row.length == 0 ? null : row[0], type);
        }
        if (type.isRecord()) {
            return recordMapper(type);
        }
        return beanMapper(type, names);
    }

    /** Returns {@code true} for types that are mapped from a single column. */
    public static boolean isSimpleType(Class<?> type) {
        return type.isPrimitive() || type.isEnum() || type.isArray()
                || Number.class.isAssignableFrom(type)
                || type == String.class || type == Boolean.class || type == Character.class
                || type == UUID.class || type == byte[].class
                || type.getName().startsWith("java.time.");
    }

    // -------------------------------------------------------------------------
    // Records and beans
    // -------------------------------------------------------------------------

    private static <T> RowMapper<T> recordMapper(Class<T> type) {
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) types[i] = components[i].getType();
        Constructor<T> ctor;
        try {
            ctor = type.getDeclaredConstructor(types);
            ctor.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalArgumentException("cannot access canonical constructor of " + type.getName(), e);
        }
        return row -> {
            if (row.length != components.length) {
                throw new IllegalStateException("record " + type.getSimpleName() + " has " + components.length
                        + " components but the row has " + row.length + " columns");
            }
            Object[] args = new Object[row.length];
            for (int i = 0; i < row.length; i++) args[i] = convert(row[i], types[i]);
            try {
                return ctor.newInstance(args);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("cannot create " + type.getName(), e);
            }
        };
    }

    private static <T> RowMapper<T> beanMapper(Class<T> type, List<String> names) {
        Constructor<T> ctor;
        try {
            ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalArgumentException(type.getName() + " needs a no-arg constructor, or use mapWith(..)", e);
        }
        Map<String, Property> properties = properties(type);
        Property[] targets = new Property[names.size()];
        boolean any = false;
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if (name != null) {
                targets[i] = properties.get(normalize(name));
                any |= targets[i] != null;
            }
        }
        if (!any) {
            throw new IllegalArgumentException("no select item matches a property of " + type.getName()
                    + " – name the items (column aliases or expr.as(\"property\")) or use mapWith(..)");
        }
        return row -> {
            try {
                T bean = ctor.newInstance();
                for (int i = 0; i < targets.length && i < row.length; i++) {
                    if (targets[i] != null) targets[i].set(bean, row[i]);
                }
                return bean;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("cannot populate " + type.getName(), e);
            }
        };
    }

    private static Map<String, Property> properties(Class<?> type) {
        Map<String, Property> result = new HashMap<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers())) continue;
                if (!f.trySetAccessible()) continue;
                result.putIfAbsent(normalize(f.getName()), new Property(null, f));
            }
        }
        for (Method m : type.getMethods()) {
            if (m.getName().startsWith("set") && m.getName().length() > 3 && m.getParameterCount() == 1
                    && !Modifier.isStatic(m.getModifiers())) {
                result.put(normalize(m.getName().substring(3)), new Property(m, null));
            }
        }
        return result;
    }

    private static String normalize(String name) {
        return name.replace("_", "").replace("\"", "").toLowerCase(Locale.ROOT);
    }

    private static final class Property {
        final Method setter;
        final Field field;

        Property(Method setter, Field field) {
            this.setter = setter;
            this.field = field;
        }

        void set(Object bean, Object value) throws ReflectiveOperationException {
            if (setter != null) setter.invoke(bean, convert(value, setter.getParameterTypes()[0]));
            else field.set(bean, convert(value, field.getType()));
        }
    }

    // -------------------------------------------------------------------------
    // Value conversion
    // -------------------------------------------------------------------------

    /**
     * Converts a database value into {@code target}: number widening and
     * narrowing, {@code java.sql} to {@code java.time}, strings to enums and
     * UUIDs, and so on. Returns the value unchanged if no conversion applies.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> T convert(Object value, Class<T> target) {
        if (value == null) {
            if (target.isPrimitive()) {
                if (target == boolean.class) return (T) Boolean.FALSE;
                if (target == char.class) return (T) Character.valueOf('\0');
                return convert(0, target);
            }
            return null;
        }
        Class<?> boxed = box(target);
        if (boxed.isInstance(value)) return (T) value;

        if (value instanceof Number) {
            Number n = (Number) value;
            if (boxed == Long.class) return (T) Long.valueOf(n.longValue());
            if (boxed == Integer.class) return (T) Integer.valueOf(n.intValue());
            if (boxed == Short.class) return (T) Short.valueOf(n.shortValue());
            if (boxed == Byte.class) return (T) Byte.valueOf(n.byteValue());
            if (boxed == Double.class) return (T) Double.valueOf(n.doubleValue());
            if (boxed == Float.class) return (T) Float.valueOf(n.floatValue());
            if (boxed == BigDecimal.class) return (T) new BigDecimal(n.toString());
            if (boxed == BigInteger.class) return (T) new BigDecimal(n.toString()).toBigInteger();
            if (boxed == Boolean.class) return (T) Boolean.valueOf(n.intValue() != 0);
            if (boxed == String.class) return (T) n.toString();
        }
        if (boxed == String.class) return (T) value.toString();
        if (boxed.isEnum() && value instanceof String) return (T) Enum.valueOf((Class<Enum>) boxed, (String) value);
        if (boxed == UUID.class && value instanceof String) return (T) UUID.fromString((String) value);
        if (boxed == Boolean.class && value instanceof String) return (T) Boolean.valueOf((String) value);
        if (boxed == Character.class && value instanceof String && ((String) value).length() == 1) {
            return (T) Character.valueOf(((String) value).charAt(0));
        }

        // java.sql and java.time
        if (value instanceof java.sql.Timestamp) value = ((java.sql.Timestamp) value).toLocalDateTime();
        else if (value instanceof java.sql.Date) value = ((java.sql.Date) value).toLocalDate();
        else if (value instanceof java.sql.Time) value = ((java.sql.Time) value).toLocalTime();
        else if (value instanceof java.util.Date) value = ((java.util.Date) value).toInstant();
        if (boxed.isInstance(value)) return (T) value;

        if (boxed == LocalDate.class) {
            if (value instanceof LocalDateTime) return (T) ((LocalDateTime) value).toLocalDate();
            if (value instanceof OffsetDateTime) return (T) ((OffsetDateTime) value).toLocalDate();
        }
        if (boxed == LocalDateTime.class) {
            if (value instanceof LocalDate) return (T) ((LocalDate) value).atStartOfDay();
            if (value instanceof OffsetDateTime) return (T) ((OffsetDateTime) value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            if (value instanceof Instant) return (T) LocalDateTime.ofInstant((Instant) value, ZoneId.systemDefault());
        }
        if (boxed == LocalTime.class && value instanceof LocalDateTime) return (T) ((LocalDateTime) value).toLocalTime();
        if (boxed == Instant.class) {
            if (value instanceof OffsetDateTime) return (T) ((OffsetDateTime) value).toInstant();
            if (value instanceof LocalDateTime) return (T) ((LocalDateTime) value).atZone(ZoneId.systemDefault()).toInstant();
        }
        if (boxed == OffsetDateTime.class) {
            if (value instanceof Instant) return (T) OffsetDateTime.ofInstant((Instant) value, ZoneId.systemDefault());
            if (value instanceof LocalDateTime) return (T) ((LocalDateTime) value).atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
        if (boxed == ZonedDateTime.class && value instanceof OffsetDateTime) return (T) ((OffsetDateTime) value).toZonedDateTime();
        if (boxed == java.util.Date.class && value instanceof Instant) return (T) java.util.Date.from((Instant) value);
        if (boxed == java.util.Date.class && value instanceof LocalDateTime) {
            return (T) java.util.Date.from(((LocalDateTime) value).atZone(ZoneId.systemDefault()).toInstant());
        }

        // arrays, e.g. text[] -> String[] or List
        if (boxed.isArray() && value.getClass().isArray()) {
            Class<?> component = boxed.getComponentType();
            int n = Array.getLength(value);
            Object array = Array.newInstance(component, n);
            for (int i = 0; i < n; i++) Array.set(array, i, convert(Array.get(value, i), component));
            return (T) array;
        }
        if (List.class.isAssignableFrom(boxed) && value.getClass().isArray()) {
            int n = Array.getLength(value);
            List<Object> list = new java.util.ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(Array.get(value, i));
            return (T) list;
        }
        return (T) value;   // let the caller fail with a ClassCastException if incompatible
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == boolean.class) return Boolean.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
    }
}
