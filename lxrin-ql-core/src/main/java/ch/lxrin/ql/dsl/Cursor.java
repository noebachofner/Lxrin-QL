package ch.lxrin.ql.dsl;

import ch.lxrin.ql.types.DataType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The position after the last row of a page: the values of its
 * {@code ORDER BY} fields. {@link #encode()} turns it into an opaque,
 * URL-safe token for REST APIs; {@code seekAfterCursor(token)} reads it back.
 *
 * <p>Values are encoded in their database-side text form through the sort
 * fields' {@link DataType}s, so value objects, enums and timestamps work.
 * Tokens are not signed: they only contain sort-key values, which are always
 * bound as parameters, so a manipulated token can at most change where the
 * page starts.</p>
 */
public final class Cursor {

    private static final char SEPARATOR = '\u001F';

    private final List<Object> values;
    private final List<DataType<?>> types;

    /** Creates a cursor from raw values; it can be passed to {@code seekAfter(cursor)}. */
    public Cursor(List<Object> values) {
        this(values, List.of());
    }

    Cursor(List<Object> values, List<DataType<?>> types) {
        this.values = Collections.unmodifiableList(new ArrayList<>(values));
        this.types = List.copyOf(types);
    }

    /** Returns the {@code ORDER BY} values of the last row. */
    public List<Object> values() {
        return values;
    }

    /**
     * Encodes the cursor as a URL-safe string.
     *
     * @throws IllegalStateException if a value has no text form (e.g. JSON)
     */
    public String encode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(SEPARATOR);
            sb.append(encodeValue(i));
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private String encodeValue(int i) {
        Object v = values.get(i);
        try {
            if (i < types.size()) return ((DataType) types.get(i)).encodeText(v);
            if (v instanceof Enum) return ((Enum<?>) v).name();
            return DataTypesOf.forValue(v).encodeText(v);
        } catch (RuntimeException e) {
            throw new IllegalStateException("cannot encode cursor value of type " + v.getClass().getName(), e);
        }
    }

    /**
     * Decodes a token for the given {@code ORDER BY} fields.
     *
     * @throws IllegalArgumentException if the token is malformed
     */
    public static Cursor decode(String token, List<Field<?>> keys) {
        String text;
        try {
            text = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("malformed cursor", e);
        }
        String[] parts = text.split(String.valueOf(SEPARATOR), -1);
        if (parts.length != keys.size()) throw new IllegalArgumentException("cursor does not match the ORDER BY fields");
        List<Object> values = new ArrayList<>();
        List<DataType<?>> types = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            DataType<?> type = keys.get(i).type();
            try {
                Object v = type.javaType().isEnum() && !type.sqlName().contains(".") && type.kind() == ch.lxrin.ql.types.Kind.OTHER
                        ? decodeEnumOrLabel(type, parts[i]) : type.decodeText(parts[i]);
                values.add(v);
                types.add(type);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("malformed cursor", e);
            }
        }
        return new Cursor(values, types);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object decodeEnumOrLabel(DataType<?> type, String text) {
        try {
            return type.decodeText(text);
        } catch (RuntimeException notALabel) {
            return Enum.valueOf((Class) type.javaType(), text);
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Cursor && values.equals(((Cursor) o).values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public String toString() {
        return "Cursor" + values;
    }

    /** Default types for cursors created from raw values. */
    private static final class DataTypesOf {
        @SuppressWarnings("unchecked")
        static DataType<Object> forValue(Object v) {
            return (DataType<Object>) ch.lxrin.ql.types.SqlTypes.forClass(v.getClass());
        }
    }
}
