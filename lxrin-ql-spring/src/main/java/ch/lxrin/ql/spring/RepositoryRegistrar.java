package ch.lxrin.ql.spring;

import ch.lxrin.ql.repository.ReadOnlyRepository;
import ch.lxrin.ql.repository.TableRepository;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Registers LxrinQL repositories as beans: from {@link EnableLxrinRepositories} or from the generated index. */
final class RepositoryRegistrar implements ImportBeanDefinitionRegistrar {

    static final String INDEX = "META-INF/lxrin-ql/repositories";

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(EnableLxrinRepositories.class.getName());
        String[] packages = attributes == null ? new String[0] : (String[]) attributes.get("basePackages");
        if (packages.length == 0) packages = new String[]{ClassUtils.getPackageName(metadata.getClassName())};
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(TableRepository.class));
        scanner.addIncludeFilter(new AssignableTypeFilter(ReadOnlyRepository.class));
        Set<String> classes = new LinkedHashSet<>();
        for (String p : packages) {
            for (BeanDefinition candidate : scanner.findCandidateComponents(p)) classes.add(candidate.getBeanClassName());
        }
        register(registry, classes, getClass().getClassLoader());
    }

    /** Registers the repositories of the generated index files on the classpath. */
    static void registerIndexed(BeanDefinitionRegistry registry, ClassLoader loader) {
        Set<String> classes = new LinkedHashSet<>();
        try {
            Enumeration<URL> urls = loader.getResources(INDEX);
            while (urls.hasMoreElements()) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(urls.nextElement().openStream(), StandardCharsets.UTF_8))) {
                    in.lines().map(String::trim).filter(l -> !l.isEmpty() && !l.startsWith("#")).forEach(classes::add);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        register(registry, classes, loader);
    }

    private static void register(BeanDefinitionRegistry registry, Set<String> classes, ClassLoader loader) {
        for (String className : classes) {
            Class<?> type;
            try {
                type = ClassUtils.forName(className, loader);
            } catch (ClassNotFoundException | LinkageError e) {
                continue;   // listed in an index but not on this classpath
            }
            if (java.lang.reflect.Modifier.isAbstract(type.getModifiers()) || alreadyDefined(registry, type)) continue;
            RootBeanDefinition definition = new RootBeanDefinition(type);
            definition.setAutowireMode(RootBeanDefinition.AUTOWIRE_CONSTRUCTOR);
            registry.registerBeanDefinition(beanName(type), definition);
        }
    }

    private static boolean alreadyDefined(BeanDefinitionRegistry registry, Class<?> type) {
        for (String name : registry.getBeanDefinitionNames()) {
            if (type.getName().equals(registry.getBeanDefinition(name).getBeanClassName())) return true;
        }
        return false;
    }

    private static String beanName(Class<?> type) {
        String simple = type.getSimpleName();
        return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }
}
