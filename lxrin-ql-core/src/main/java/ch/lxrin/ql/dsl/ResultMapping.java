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
 *       {@code createdAt}). A component without a field is an error that lists every
 *       unmatched component and every unused field; there is no silent fallback to the
 *       position. Only {@link SelectScope#mapByPosition()} maps by position. The
 *       field's Java type must fit the component type;</li>
 *   <li>{@link Row}: typed access by field;</li>
 *   <li>any other type: a single selected field of that type, e.g. {@code Long.class}
 *       for {@code c.select(count())}.</li>
 * </ul>
 */
final class ResultMapping {

    private ResultMapping() {}

    static <T> Function<Object[], T> of(Class<T> type, List<? extends Field<?>> fields, boolean byPosition) {
        if (type == null) throw new IllegalArgumentException("result type must not be null");
        if (fields.isEmpty()) throw new IllegalArgumentException("the select list must not be empty");
        if (type == Row.class) {
            Row.Shape shape = new Row.Shape(fields);
            return v -> type.cast(shape.row(v));
        }
        if (type.isRecord()) return record(type, fields, byPosition);
        if (byPosition) throw new IllegalArgumentException("mapByPosition() needs a record type, not " + type.getName());
        if (fields.size() == 1 && fits(type, fields.get(0).type().javaType())) {
            return v -> type.cast(v[0]);
        }
        throw new IllegalArgumentException("cannot map " + fields.size() + " field(s) into " + type.getName()
                + ": use a record, Row or a single field of that type");
    }

    private static <T> Function<Object[], T> record(Class<T> type, List<? extends Field<?>> fields, boolean byPosition) {
        RecordComponent[] components = type.getRecordComponents();
        int[] index = byPosition ? byPosition(type, components, fields) : byName(type, components, fields);
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

    private static int[] byPosition(Class<?> type, RecordComponent[] components, List<? extends Field<?>> fields) {
        if (components.length != fields.size()) {
            throw new IllegalArgumentException(type.getSimpleName() + " has " + components.length + " components, but "
                    + fields.size() + " fields are selected; mapByPosition() needs one field per component");
        }
        int[] index = new int[components.length];
        for (int i = 0; i < index.length; i++) index[i] = i;
        return index;
    }

    /** Returns the field index per component; fails with every unmatched component and unused field. */
    private static int[] byName(Class<?> type, RecordComponent[] components, List<? extends Field<?>> fields) {
        Map<String, List<Integer>> names = new HashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            String name = fields.get(i).name();
            if (name != null) names.computeIfAbsent(normalize(name), k -> new ArrayList<>()).add(i);
        }
        int[] index = new int[components.length];
        boolean[] used = new boolean[fields.size()];
        List<String> unmatched = new ArrayList<>();
        List<String> ambiguous = new ArrayList<>();
        for (int i = 0; i < components.length; i++) {
            RecordComponent rc = components[i];
            List<Integer> candidates = names.getOrDefault(normalize(rc.getName()), List.of());
            if (candidates.size() == 1) {
                index[i] = candidates.get(0);
                used[index[i]] = true;
            } else {
                String component = rc.getName() + " (" + rc.getType().getSimpleName() + ")";
                if (candidates.isEmpty()) unmatched.add(component);
                else ambiguous.add(component + " matches " + candidates.size() + " fields");
                for (int c : candidates) used[c] = true;
            }
        }
        if (unmatched.isEmpty() && ambiguous.isEmpty()) return index;
        List<String> unused = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) if (!used[i]) unused.add(describe(fields.get(i)));
        StringBuilder message = new StringBuilder("cannot map the select list into ").append(type.getSimpleName()).append(" by name:");
        if (!unmatched.isEmpty()) message.append(" no field for component(s) ").append(String.join(", ", unmatched)).append(';');
        if (!ambiguous.isEmpty()) message.append(" ambiguous component(s) ").append(String.join(", ", ambiguous)).append(';');
        message.append(" unused field(s): ").append(unused.isEmpty() ? "none" : String.join(", ", unused)).append('.')
                .append(" Fields match components by column name or alias (snake_case = camelCase); rename a field with")
                .append(" .as(\"name\"), or call c.mapByPosition() to map by position.");
        throw new IllegalArgumentException(message.toString());
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
