package com.oracle.graal.microbenchmarks.graal.util;

import java.lang.annotation.*;

/**
 * Annotation for specifying a method based on a declaring class, a name and parameter types.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MethodSpec {
    /**
     * The class in which the method is declared. If not specified, the annotated class is used as
     * the declaring class.
     */
    Class<?> declaringClass() default MethodSpec.class;

    /**
     * The name of the method.
     */
    String name();

    /**
     * The types of the method's parameters. If not specified, then the {@linkplain #name() name} is
     * expected to be unique within the declaring class.
     */
    Class<?>[] parameters() default {void.class};
}
