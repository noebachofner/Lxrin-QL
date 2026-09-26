package ch.lxrin.ql.spi;

/**
 * Where a statement comes from: the DSL, a repository method or a listener.
 * Observers can use it as a metric tag; listeners use it to avoid recursion.
 *
 * @param type   the kind of origin
 * @param detail e.g. {@code UserRepository.save}, or {@code null}
 */
public record Origin(Type type, String detail) {

    /** Origin types. */
    public enum Type {
        /** Application code using the DSL. */
        DSL,
        /** A repository method. */
        REPOSITORY,
        /** A statement run by a statement listener, e.g. an audit insert. */
        LISTENER
    }

    /** The default origin of DSL statements. */
    public static final Origin DSL = new Origin(Type.DSL, null);

    /** A repository origin, e.g. {@code repository("UserRepository.save")}. */
    public static Origin repository(String method) {
        return new Origin(Type.REPOSITORY, method);
    }

    /** A listener origin. */
    public static Origin listener(String listener) {
        return new Origin(Type.LISTENER, listener);
    }

    @Override
    public String toString() {
        return detail == null ? type.name() : type + ":" + detail;
    }
}
