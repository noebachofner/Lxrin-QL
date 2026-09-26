package ch.lxrin.ql.schema;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * How new primary key values are created by {@code Repository.createKey()}.
 */
public final class KeyStrategy {

    /** The kinds of strategies. */
    public enum Type {
        /** No generated keys: natural or composite keys set by the application. */
        NONE,
        /** Time-ordered UUID version 7, created in Java. */
        UUID_V7,
        /** {@code nextval} of a database sequence (serial and identity columns). */
        SEQUENCE,
        /** A key supplier of the application. */
        CUSTOM
    }

    private static final KeyStrategy NONE = new KeyStrategy(Type.NONE, null, null);
    private static final KeyStrategy UUID_V7 = new KeyStrategy(Type.UUID_V7, null, null);

    private final Type type;
    private final String sequence;
    private final Supplier<?> supplier;

    private KeyStrategy(Type type, String sequence, Supplier<?> supplier) {
        this.type = type;
        this.sequence = sequence;
        this.supplier = supplier;
    }

    /** No generated keys. */
    public static KeyStrategy none() {
        return NONE;
    }

    /** UUID version 7 created in Java. */
    public static KeyStrategy uuidV7() {
        return UUID_V7;
    }

    /** {@code nextval('sequence')}; the name may be schema-qualified. */
    public static KeyStrategy sequence(String sequence) {
        return new KeyStrategy(Type.SEQUENCE, Objects.requireNonNull(sequence, "sequence"), null);
    }

    /** A custom key supplier. */
    public static KeyStrategy custom(Supplier<?> supplier) {
        return new KeyStrategy(Type.CUSTOM, null, Objects.requireNonNull(supplier, "supplier"));
    }

    /** Returns the kind. */
    public Type type() {
        return type;
    }

    /** Returns the sequence name for {@link Type#SEQUENCE}. */
    public String sequence() {
        return sequence;
    }

    /** Returns the supplier for {@link Type#CUSTOM}. */
    public Supplier<?> supplier() {
        return supplier;
    }

    @Override
    public String toString() {
        return type == Type.SEQUENCE ? "sequence(" + sequence + ")" : type.name();
    }
}
