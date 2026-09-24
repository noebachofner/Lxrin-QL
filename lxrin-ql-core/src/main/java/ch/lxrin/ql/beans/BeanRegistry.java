package ch.lxrin.ql.beans;

/** Where {@link BEANS} gets its objects from: {@link SimpleBeanRegistry} or a DI container. */
public interface BeanRegistry {

    /** Returns the bean of the given type, creating it if needed. */
    <T> T get(Class<T> type);

    /** Registers an instance. */
    <T> void register(Class<T> type, T instance);
}
