package com.oracle.truffle.espresso.intrinsics;

import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(value = {TYPE})
public @interface EspressoIntrinsics {
    Class<?> value() default EspressoIntrinsics.class;
}
