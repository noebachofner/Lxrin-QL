package ch.lxrin.ql.bind;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds named bind parameters for a query.
 *
 * <p>Instances are immutable once passed to a query executor, but the builder
 * pattern copies the map for each {@code put} call so the fluent API remains
 * safe to use.</p>
 */
public class BindMap {

    private final Map<String, Object> binds;

    /** Creates an empty bind map. */
    public BindMap() {
        this.binds = new LinkedHashMap<>();
    }

    private BindMap(Map<String, Object> binds) {
        this.binds = new LinkedHashMap<>(binds);
    }

    /**
     * Adds or replaces a named bind parameter.
     *
     * @param name  bind name (without the leading {@code :})
     * @param value bind value
     * @return a new {@link BindMap} containing the added entry
     */
    public BindMap put(String name, Object value) {
        BindMap copy = new BindMap(this.binds);
        copy.binds.put(name, value);
        return copy;
    }

    /**
     * Returns an unmodifiable view of all bind entries.
     */
    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(binds);
    }

    /**
     * Returns the value for the given bind name, or {@code null} if absent.
     */
    public Object get(String name) {
        return binds.get(name);
    }

    /**
     * Returns {@code true} if this map contains no binds.
     */
    public boolean isEmpty() {
        return binds.isEmpty();
    }

    @Override
    public String toString() {
        return "BindMap" + binds;
    }
}
