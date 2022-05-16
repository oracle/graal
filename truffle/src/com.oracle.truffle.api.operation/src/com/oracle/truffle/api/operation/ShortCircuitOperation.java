package com.oracle.truffle.api.operation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Repeatable(ShortCircuitOperations.class)
public @interface ShortCircuitOperation {
    String name();

    boolean continueWhen();

    Class<?> booleanConverter() default void.class;
}