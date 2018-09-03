package com.oracle.truffle.espresso.impl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation functions as an alias for the java.lang.invoke.Stable annotation. It is specially
 * recognized during class file parsing in the same way as that annotation.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@interface Stable {
}
