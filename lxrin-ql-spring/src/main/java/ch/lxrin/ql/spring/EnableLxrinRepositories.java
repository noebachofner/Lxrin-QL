package ch.lxrin.ql.spring;

import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registers every LxrinQL repository (subclasses of {@code TableRepository}
 * and {@code ReadOnlyRepository}) found in the given packages as a bean.
 * Without this annotation, the repositories listed in the generated
 * {@code META-INF/lxrin-ql/repositories} index are registered.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(RepositoryRegistrar.class)
public @interface EnableLxrinRepositories {

    /** The packages to scan; defaults to the package of the annotated class. */
    String[] basePackages() default {};
}
