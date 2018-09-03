package com.oracle.truffle.espresso.intrinsics;

import static java.lang.annotation.ElementType.METHOD;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(value = {METHOD})
public @interface Intrinsic {
    String methodName() default "";

    boolean hasReceiver() default false;
}
