package com.oracle.truffle.espresso.intrinsics;

import static java.lang.annotation.ElementType.TYPE_USE;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(value = {TYPE_USE})
public @interface Type {
    Class<?> value() default Type.class;

    String typeName() default "";
}
