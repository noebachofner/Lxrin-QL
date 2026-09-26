package ch.lxrin.ql.schema;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of all constraints declared by loaded table classes, used to map
 * PostgreSQL constraint violations to typed constraint objects.
 */
public final class Constraints {

    private static final Map<String, Constraint> BY_NAME = new ConcurrentHashMap<>();

    private Constraints() {}

    static void register(Constraint constraint) {
        if (constraint.table().alias().isEmpty()) {
            BY_NAME.putIfAbsent(key(constraint.table().schema(), constraint.name()), constraint);
            BY_NAME.putIfAbsent(key(null, constraint.name()), constraint);
        }
    }

    /**
     * Finds a constraint by name.
     *
     * @param schema the schema, or {@code null} if unknown
     * @param name   the constraint name
     */
    public static Optional<Constraint> find(String schema, String name) {
        if (name == null) return Optional.empty();
        Constraint c = BY_NAME.get(key(schema, name));
        return Optional.ofNullable(c != null ? c : BY_NAME.get(key(null, name)));
    }

    private static String key(String schema, String name) {
        return (schema == null ? "" : schema) + "." + name;
    }
}
