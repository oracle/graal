package com.oracle.truffle.espresso.jni;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker for parameters and return types to hint the interop object is a native pointer.
 *
 * On the Java/Espresso side all pointers are represented as interop {@code TruffleObject}
 * instances. This annotation serves to generate proper signatures to communicate with native code.
 * It can also be used as a hint for fields and local variables; but no checks are performed and no
 * code is derived for those.
 *
 * <h4>Usage:</h4>
 * <p>
 * Receives a methodID and a pointer:
 *
 * <pre>
 * public void CallVoidMethodVarargs(&#64;Host(Object.class) StaticObject receiver, &#64;Handle(Method.class) long methodId, &#64;Pointer TruffleObject varargsPtr)
 * </pre>
 *
 * Returning a pointer:
 *
 * <pre>
 * public &#64;Pointer TruffleObject GetDoubleArrayElements(&#64;Host(double[].class) StaticObject array, &#64;Pointer TruffleObject isCopyPtr)
 * </pre>
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
public @interface Pointer {
}
