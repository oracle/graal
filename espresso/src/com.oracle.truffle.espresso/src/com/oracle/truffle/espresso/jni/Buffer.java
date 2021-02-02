package com.oracle.truffle.espresso.jni;

import static java.lang.annotation.ElementType.TYPE_USE;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.oracle.truffle.api.interop.InteropLibrary;

/**
 * Marker for parameters and return values that can be treated like
 * {@link InteropLibrary#hasBufferElements(Object) Truffle buffers}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {TYPE_USE})
public @interface Buffer {
}
