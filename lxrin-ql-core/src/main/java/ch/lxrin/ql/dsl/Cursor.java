package ch.lxrin.ql.dsl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * The position after the last row of a page: the values of its
 * {@code ORDER BY} fields. {@link #encode()} turns it into an opaque,
 * URL-safe token for REST APIs; {@code seekAfter(token)} reads it back.
 *
 * <p>Tokens are not signed. They only contain sort-key values and are
 * always sent as bind parameters, so a manipulated token can at most skip
 * rows the caller is allowed to see anyway.</p>
 *
 * @param values the {@code ORDER BY} values of the last row
 */
public record Cursor(List<Object> values) {

    private static final char SEPARATOR = '\u001F';

    /** Creates a cursor with an immutable copy of the values. */
    public Cursor {
        values = java.util.Collections.unmodifiableList(new ArrayList<>(values));
    }

    /**
     * Encodes the cursor as a URL-safe string.
     *
     * @throws IllegalStateException if a value has a type without a text form
     *                               (String, numbers, Boolean, UUID, java.time types and enums are supported)
     */
    public String encode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(SEPARATOR);
            Object v = values.get(i);
            if (!supported(v)) throw new IllegalStateException("cannot encode cursor value of type " + v.getClass().getName());
            sb.append(v instanceof Enum ? ((Enum<?>) v).name() : v.toString());
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes a token for the given {@code ORDER BY} fields.
     *
     * @throws IllegalArgumentException if the token is malformed
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
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
        for (int i = 0; i < parts.length; i++) {
            Class<?> type = keys.get(i).type().javaType();
            String s = parts[i];
            try {
                Object v;
                if (type == String.class) v = s;
                else if (type == Long.class) v = Long.valueOf(s);
                else if (type == Integer.class) v = Integer.valueOf(s);
                else if (type == Short.class) v = Short.valueOf(s);
                else if (type == BigDecimal.class) v = new BigDecimal(s);
                else if (type == Double.class) v = Double.valueOf(s);
                else if (type == Float.class) v = Float.valueOf(s);
                else if (type == Boolean.class) v = Boolean.valueOf(s);
                else if (type == UUID.class) v = UUID.fromString(s);
                else if (type == Instant.class) v = Instant.parse(s);
                else if (type == LocalDate.class) v = LocalDate.parse(s);
                else if (type == LocalDateTime.class) v = LocalDateTime.parse(s);
                else if (type == LocalTime.class) v = LocalTime.parse(s);
                else if (type == OffsetDateTime.class) v = OffsetDateTime.parse(s);
                else if (type.isEnum()) v = Enum.valueOf((Class) type, s);
                else throw new IllegalArgumentException("cursor values of type " + type.getName() + " cannot be decoded");
                values.add(v);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("malformed cursor", e);
            }
        }
        return new Cursor(values);
    }

    private static boolean supported(Object v) {
        return v instanceof String || v instanceof Number || v instanceof Boolean || v instanceof UUID
                || v instanceof Instant || v instanceof LocalDate || v instanceof LocalDateTime || v instanceof LocalTime
                || v instanceof OffsetDateTime || v instanceof Enum;
    }
}
