package ch.lxrin.ql.spring;

import ch.lxrin.ql.beans.BeanRegistry;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Objects;

/**
 * Lets {@code BEANS.get(..)} return beans of the Spring {@link ApplicationContext},
 * so {@code BEANS.get(UserRepository.class)} and constructor injection give the same instance.
 */
public final class SpringBeanRegistry implements BeanRegistry {

    private final ApplicationContext context;

    /** Creates the registry. */
    public SpringBeanRegistry(ApplicationContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    @Override
    public <T> T get(Class<T> type) {
        return context.getBean(type);
    }

    @Override
    public <T> void register(Class<T> type, T instance) {
        if (!(context instanceof ConfigurableApplicationContext)) {
            throw new IllegalStateException("cannot register beans in " + context.getClass().getName());
        }
        ConfigurableListableBeanFactory factory = ((ConfigurableApplicationContext) context).getBeanFactory();
        factory.registerSingleton(type.getName(), instance);
    }
}
