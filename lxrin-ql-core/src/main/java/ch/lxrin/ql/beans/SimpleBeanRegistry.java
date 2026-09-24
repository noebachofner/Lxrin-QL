package ch.lxrin.ql.beans;

import ch.lxrin.ql.runtime.QueryContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates singletons on first use. A bean is created through its public
 * constructor with the most parameters it can satisfy: a
 * {@link QueryContext} parameter gets {@link QueryContext#getDefault()},
 * other parameters get beans of their type. Repositories therefore register
 * themselves automatically.
 */
public final class SimpleBeanRegistry implements BeanRegistry {

    private final Map<Class<?>, Object> beans = new ConcurrentHashMap<>();
    private final ThreadLocal<Deque<Class<?>>> creating = ThreadLocal.withInitial(ArrayDeque::new);

    @Override
    public <T> T get(Class<T> type) {
        Object bean = beans.get(type);
        if (bean != null) return type.cast(bean);
        synchronized (this) {
            bean = beans.get(type);
            if (bean == null) {
                bean = create(type);
                beans.put(type, bean);
            }
            return type.cast(bean);
        }
    }

    @Override
    public <T> void register(Class<T> type, T instance) {
        beans.put(type, type.cast(instance));
    }

    private Object create(Class<?> type) {
        if (type == QueryContext.class) return QueryContext.getDefault();
        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            throw new IllegalStateException("cannot create " + type.getName() + ": register an instance with BEANS.register(..)");
        }
        Deque<Class<?>> stack = creating.get();
        if (stack.contains(type)) throw new IllegalStateException("circular dependency: " + stack + " -> " + type.getName());
        stack.push(type);
        try {
            Constructor<?>[] constructors = type.getConstructors();
            java.util.Arrays.sort(constructors, Comparator.comparingInt(Constructor::getParameterCount));
            if (constructors.length == 0) throw new IllegalStateException(type.getName() + " has no public constructor");
            Constructor<?> ctor = constructors[constructors.length - 1];
            Object[] args = new Object[ctor.getParameterCount()];
            Class<?>[] types = ctor.getParameterTypes();
            for (int i = 0; i < args.length; i++) args[i] = get(types[i]);
            return ctor.newInstance(args);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("cannot create " + type.getName() + ": " + e.getCause(), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot create " + type.getName(), e);
        } finally {
            stack.pop();
        }
    }
}
