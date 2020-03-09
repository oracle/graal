package com.oracle.truffle.espresso.jni;

import static java.lang.annotation.ElementType.TYPE_USE;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(value = {TYPE_USE})
public @interface Handle {
    /**
     * Class of the expected handle.
     */
    Class<?> value() default Handle.class;
}

