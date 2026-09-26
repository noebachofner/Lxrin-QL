package ch.lxrin.ql.beans;

import java.util.Objects;

/**
 * A tiny bean lookup for applications without a DI framework:
 * {@code BEANS.get(UserRepository.class).save(user)}.
 *
 * <p>By default beans come from a {@link SimpleBeanRegistry}, which creates
 * singletons on first use (repositories get the default {@code QueryContext}).
 * {@code lxrin-ql-spring} delegates to the Spring {@code ApplicationContext},
 * so {@code BEANS.get(..)} and constructor injection return the same bean.</p>
 */
public final class BEANS {

    private static volatile BeanRegistry registry = new SimpleBeanRegistry();

    private BEANS() {}

    /** Returns the bean of the given type. */
    public static <T> T get(Class<T> type) {
        return registry.get(Objects.requireNonNull(type, "type"));
    }

    /** Registers an instance, e.g. a {@code Clock}, for applications without DI. */
    public static <T> void register(Class<T> type, T instance) {
        registry.register(type, instance);
    }

    /** Replaces the registry (done by {@code lxrin-ql-spring}). */
    public static void setRegistry(BeanRegistry newRegistry) {
        registry = Objects.requireNonNull(newRegistry, "registry");
    }

    /** Returns the current registry. */
    public static BeanRegistry registry() {
        return registry;
    }

    /** Restores a fresh {@link SimpleBeanRegistry}, e.g. between tests. */
    public static void reset() {
        registry = new SimpleBeanRegistry();
    }
}
