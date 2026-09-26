package ch.lxrin.ql.dsl;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Maps the rows of a {@code createContribution(Type.class, ..)} query into
 * {@code Type}. Everything is checked once, when the select list is known:
 *
 * <ul>
 *   <li>a <b>record</b>: every component is matched to a selected field by name
 *       ({@code created_at} or an alias {@code createdAt} → component
 *       {@code createdAt}); if the names do not match, by position when the
 *       numbers are equal. The field's Java type must fit the component type;</li>
 *   <li>{@link Row}: typed access by field;</li>
 *   <li>any other type: a single selected field of that type, e.g. {@code Long.class}
 *       for {@code c.select(count())}.</li>
 * </ul>
 */
final class ResultMapping {

    private ResultMapping() {}

    static <T> Function<Object[], T> of(Class<T> type, List<? extends Field<?>> fields) {
        if (type == null) throw new IllegalArgumentException("result type must not be null");
        if (fields.isEmpty()) throw new IllegalArgumentException("the select list must not be empty");
        if (type == Row.class) {
            Row.Shape shape = new Row.Shape(fields);
            return v -> type.cast(shape.row(v));
        }
        if (type.isRecord()) return record(type, fields);
        if (fields.size() == 1 && fits(type, fields.get(0).type().javaType())) {
            return v -> type.cast(v[0]);
        }
        throw new IllegalArgumentException("cannot map " + fields.size() + " field(s) into " + type.getName()
                + ": use a record, Row or a single field of that type");
    }

    private static <T> Function<Object[], T> record(Class<T> type, List<? extends Field<?>> fields) {
        RecordComponent[] components = type.getRecordComponents();
        int[] index = byName(components, fields);
        if (index == null) {
            if (components.length != fields.size()) {
                throw new IllegalArgumentException(type.getSimpleName() + " has " + components.length + " components, but "
                        + fields.size() + " fields are selected and their names do not match the components");
            }
            index = new int[components.length];
            for (int i = 0; i < index.length; i++) index[i] = i;
        }
        Class<?>[] parameterTypes = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            RecordComponent rc = components[i];
            Field<?> f = fields.get(index[i]);
            if (!fits(rc.getType(), f.type().javaType())) {
                throw new IllegalArgumentException(type.getSimpleName() + "." + rc.getName() + " is " + rc.getType().getSimpleName()
                        + ", but the field " + describe(f) + " is " + f.type().javaType().getSimpleName());
            }
            parameterTypes[i] = rc.getType();
        }
        Constructor<T> constructor;
        try {
            constructor = type.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
        } catch (NoSuchMethodException | RuntimeException e) {
            throw new IllegalArgumentException("cannot access the canonical constructor of " + type.getName(), e);
        }
        int[] order = index;
        return v -> {
            Object[] args = new Object[order.length];
            for (int i = 0; i < order.length; i++) {
                args[i] = v[order[i]];
                if (args[i] == null && parameterTypes[i].isPrimitive()) {
                    throw new IllegalStateException(type.getSimpleName() + "." + components[i].getName()
                            + " is primitive, but the value is NULL; use " + box(parameterTypes[i]).getSimpleName());
                }
            }
            try {
                return constructor.newInstance(args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                throw new IllegalStateException("constructor of " + type.getName() + " failed", cause);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("cannot create " + type.getName(), e);
            }
        };
    }

    /** Returns the field index per component, or {@code null} if not every component has a field of the same name. */
    private static int[] byName(RecordComponent[] components, List<? extends Field<?>> fields) {
        Map<String, Integer> names = new HashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            String name = fields.get(i).name();
            if (name == null) continue;
            if (names.putIfAbsent(normalize(name), i) != null) names.put(normalize(name), -1);
        }
        int[] index = new int[components.length];
        List<Integer> used = new ArrayList<>();
        for (int i = 0; i < components.length; i++) {
            Integer idx = names.get(normalize(components[i].getName()));
            if (idx == null || idx < 0 || used.contains(idx)) return null;
            used.add(idx);
            index[i] = idx;
        }
        return index;
    }

    private static String normalize(String name) {
        return name.replace("_", "").toLowerCase(Locale.ROOT);
    }

    private static boolean fits(Class<?> target, Class<?> source) {
        return box(target).isAssignableFrom(box(source));
    }

    private static String describe(Field<?> f) {
        return f.name() != null ? f.name() : f.toString();
    }

    private static Class<?> box(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == boolean.class) return Boolean.class;
        if (c == double.class) return Double.class;
        if (c == float.class) return Float.class;
        if (c == short.class) return Short.class;
        if (c == byte.class) return Byte.class;
        if (c == char.class) return Character.class;
        return Void.class;
    }
}
