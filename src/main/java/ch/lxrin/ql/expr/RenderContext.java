package ch.lxrin.ql.expr;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Collects the SQL text and the automatically generated bind parameters while
 * a statement is being rendered.
 *
 * <p>Every object that can appear in a query is rendered through
 * {@link #visit(Object)}, which applies the library-wide operand rule:</p>
 * <ul>
 *   <li>{@link Renderable} (expressions, tables, queries) &rarr; rendered by
 *       the element itself</li>
 *   <li>{@link String} &rarr; appended verbatim as a SQL fragment
 *       (e.g. {@code "t.NAME"} or a placeholder such as {@code ":p0"})</li>
 *   <li>{@code null} &rarr; the SQL literal {@code NULL}</li>
 *   <li>anything else (numbers, booleans, dates, UUIDs, arrays, ...) &rarr;
 *       registered as a bind parameter and rendered as {@code :lqN}</li>
 * </ul>
 *
 * <p>A fresh context is used for every render, so the generated parameter
 * names are deterministic ({@code lq0}, {@code lq1}, ...).</p>
 */
public final class RenderContext {

    /** Prefix of automatically generated bind parameter names. */
    public static final String PARAM_PREFIX = "lq";

    private final StringBuilder sql = new StringBuilder();
    private final Map<String, Object> params = new LinkedHashMap<>();
    private int paramCounter;

    /** Appends a raw SQL fragment. */
    public RenderContext append(String fragment) {
        sql.append(fragment);
        return this;
    }

    /** Appends a single character. */
    public RenderContext append(char c) {
        sql.append(c);
        return this;
    }

    /**
     * Renders an arbitrary query element according to the operand rule
     * described in the class documentation.
     */
    public RenderContext visit(Object item) {
        if (item == null) {
            sql.append("NULL");
        } else if (item instanceof Renderable) {
            ((Renderable) item).render(this);
        } else if (item instanceof String) {
            sql.append((String) item);
        } else {
            bind(item);
        }
        return this;
    }

    /**
     * Renders all items separated by {@code separator}. Nested collections are
     * flattened, so {@code List.of(1, 2, 3)} renders as {@code :lq0, :lq1, :lq2}.
     */
    public RenderContext visitAll(Collection<?> items, String separator) {
        boolean first = true;
        for (Object item : items) {
            if (!first) sql.append(separator);
            if (item instanceof Collection) {
                visitAll((Collection<?>) item, separator);
            } else {
                visit(item);
            }
            first = false;
        }
        return this;
    }

    /**
     * Registers {@code value} as a new bind parameter and appends its
     * placeholder.
     *
     * @return the generated bind name (without the leading colon)
     */
    public String bind(Object value) {
        String name = PARAM_PREFIX + paramCounter++;
        params.put(name, value);
        sql.append(':').append(name);
        return name;
    }

    /**
     * Registers an explicitly named bind parameter without writing SQL. Used
     * for values supplied through {@code bind(name, value)} or {@code Binds},
     * including those of nested sub-queries.
     */
    public RenderContext bindNamed(String name, Object value) {
        params.put(name, value);
        return this;
    }

    /** Returns the SQL rendered so far. */
    public String sql() {
        return sql.toString();
    }

    /** Returns all bind parameters: generated ones and explicitly named ones. */
    public Map<String, Object> params() {
        return Collections.unmodifiableMap(params);
    }
}
