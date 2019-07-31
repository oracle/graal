package com.oracle.truffle.espresso.substitutions;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE_USE;

@Retention(RetentionPolicy.RUNTIME)
@Target(value = {TYPE_USE})
public @interface InjectMeta {
}
