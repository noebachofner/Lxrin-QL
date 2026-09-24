package ch.lxrin.ql.bind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Typed, mutable container for named SQL bind parameters.
 *
 * <p>Unlike the functional {@link BindMap}, {@code Binds} is a mutable object
 * with dedicated type-specific setter methods to improve clarity and prevent
 * accidental type mismatches at the call site.</p>
 *
 * <h2>Inline usage (recommended)</h2>
 * <p>Call the single-argument overloads directly inside conditions.
 * The bind name is auto-generated and the {@code :placeholder} is returned:</p>
 * <pre>{@code
 * Binds b = new Binds();
 *
 * createContribution(ProductBean.class)
 *     .from(product)
 *     .select(product.productNr)
 *     .select(product.name)
 *     .where(eq(product.productNr, b.setLong(selectedProductNr)),
 *            and(),
 *            eq(product.status, b.setString("ACTIVE")))
 *     .bind(b)
 *     .multiple();
 * }</pre>
 *
 * <h2>Named-parameter usage</h2>
 * <p>You can also supply explicit names when you prefer readability or need
 * to share a placeholder across multiple conditions:</p>
 * <pre>{@code
 * Binds b = new Binds()
 *     .setLong("productNr", selectedProductNr)
 *     .setString("status", "ACTIVE");
 * }</pre>
 */
public class Binds {

    private final Map<String, Object> values = new LinkedHashMap<>();
    private final AtomicInteger autoIndex = new AtomicInteger(0);

    // -------------------------------------------------------------------------
    // Type-specific setters – named (explicit bind name)
    // -------------------------------------------------------------------------

    /** Adds a {@code Long} bind parameter with an explicit name. */
    public Binds setLong(String name, Long value) {
        values.put(requireName(name), value);
        return this;
    }

    /** Adds an {@code Integer} bind parameter with an explicit name. */
    public Binds setInt(String name, Integer value) {
        values.put(requireName(name), value);
        return this;
    }

    /** Adds a {@code Double} bind parameter with an explicit name. */
    public Binds setDouble(String name, Double value) {
        values.put(requireName(name), value);
        return this;
    }

    /** Adds a {@link BigDecimal} bind parameter with an explicit name. */
    public Binds setBigDecimal(String name, BigDecimal value) {
        values.put(requireName(name), value);
        return this;
    }

    /** Adds a {@code String} bind parameter with an explicit name. */
    public Binds setString(String name, String value) {
        values.put(requireName(name), value);
        return this;
    }

    /** Adds a {@code Boolean} bind parameter with an explicit name. */
    public Binds setBoolean(String name, Boolean value) {
        values.put(requireName(name), value);
        return this;
    }

    /** Adds a {@link LocalDate} bind parameter with an explicit name. */
    public Binds setDate(String name, LocalDate value) {
        values.put(requireName(name), value);
        return this;
    }

    /** Adds a {@link LocalDateTime} bind parameter with an explicit name. */
    public Binds setDateTime(String name, LocalDateTime value) {
        values.put(requireName(name), value);
        return this;
    }

    /**
     * Generic setter with an explicit name – use a typed setter when possible.
     *
     * @param name  bind name (without leading {@code :})
     * @param value bind value
     */
    public Binds set(String name, Object value) {
        values.put(requireName(name), value);
        return this;
    }

    // -------------------------------------------------------------------------
    // Type-specific setters – inline / auto-named
    //
    // These single-argument overloads auto-generate a sequential bind name and
    // return the ":name" placeholder string so the call can be placed directly
    // inside a condition expression:
    //
    //   Binds b = new Binds();
    //   .where(eq(product.productNr, b.setLong(selectedProductNr)),
    //          and(),
    //          eq(product.status, b.setString("ACTIVE")))
    //   .bind(b)
    //
    // The auto-names are "p0", "p1", … and are local to this Binds instance.
    // -------------------------------------------------------------------------

    /**
     * Adds a {@code Long} bind parameter with an auto-generated name.
     *
     * @param value the value to bind
     * @return the {@code :placeholder} string (e.g. {@code ":p0"}) for use
     *         directly inside a condition expression
     */
    public String setLong(Long value) {
        return autoSet(value);
    }

    /**
     * Adds an {@code Integer} bind parameter with an auto-generated name.
     *
     * @param value the value to bind
     * @return the {@code :placeholder} string
     */
    public String setInt(Integer value) {
        return autoSet(value);
    }

    /**
     * Adds a {@code Double} bind parameter with an auto-generated name.
     *
     * @param value the value to bind
     * @return the {@code :placeholder} string
     */
    public String setDouble(Double value) {
        return autoSet(value);
    }

    /**
     * Adds a {@link BigDecimal} bind parameter with an auto-generated name.
     *
     * @param value the value to bind
     * @return the {@code :placeholder} string
     */
    public String setBigDecimal(BigDecimal value) {
        return autoSet(value);
    }

    /**
     * Adds a {@code String} bind parameter with an auto-generated name.
     *
     * @param value the value to bind
     * @return the {@code :placeholder} string
     */
    public String setString(String value) {
        return autoSet(value);
    }

    /**
     * Adds a {@code Boolean} bind parameter with an auto-generated name.
     *
     * @param value the value to bind
     * @return the {@code :placeholder} string
     */
    public String setBoolean(Boolean value) {
        return autoSet(value);
    }

    /**
     * Adds a {@link LocalDate} bind parameter with an auto-generated name.
     *
     * @param value the value to bind
     * @return the {@code :placeholder} string
     */
    public String setDate(LocalDate value) {
        return autoSet(value);
    }

    /**
     * Adds a {@link LocalDateTime} bind parameter with an auto-generated name.
     *
     * @param value the value to bind
     * @return the {@code :placeholder} string
     */
    public String setDateTime(LocalDateTime value) {
        return autoSet(value);
    }

    /**
     * Adds a generic bind parameter with an auto-generated name.
     *
     * @param value the value to bind
     * @return the {@code :placeholder} string
     */
    public String set(Object value) {
        return autoSet(value);
    }

    // -------------------------------------------------------------------------
    // Read access
    // -------------------------------------------------------------------------

    /**
     * Returns the value bound to {@code name}, or {@code null} if absent.
     */
    public Object get(String name) {
        return values.get(name);
    }

    /**
     * Returns {@code true} if no parameters have been set.
     */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * Returns an unmodifiable view of all bind entries.
     */
    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(values);
    }

    /**
     * Converts this {@code Binds} into an immutable {@link BindMap}.
     * The returned {@link BindMap} is a snapshot; further changes to this
     * {@code Binds} will not be reflected.
     */
    public BindMap toBindMap() {
        BindMap map = new BindMap();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            map = map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private String autoSet(Object value) {
        String name = "p" + autoIndex.getAndIncrement();
        values.put(name, value);
        return ":" + name;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("bind name must not be blank");
        }
        return name;
    }

    @Override
    public String toString() {
        return "Binds" + values;
    }
}
