package ch.lxrin.ql.types;

import java.lang.reflect.Type;

/**
 * Converts Java objects to JSON text and back. Used by
 * {@link SqlTypes#jsonb(Class)} and {@link SqlTypes#json(Class)}.
 *
 * <p>The core module ships no implementation because it has no
 * dependencies. {@code lxrin-ql-spring} registers a Jackson based codec
 * automatically; elsewhere, configure one with
 * {@code QueryContext.builder().jsonCodec(..)}.</p>
 */
public interface JsonCodec {

    /** Serialises {@code value} to JSON text. */
    String write(Object value);

    /** Parses JSON text into an instance of {@code type}. */
    <T> T read(String json, Type type);
}
