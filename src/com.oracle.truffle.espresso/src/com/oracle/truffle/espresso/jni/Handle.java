package com.oracle.truffle.espresso.jni;

import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.runtime.StaticObject;

import static java.lang.annotation.ElementType.TYPE_USE;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker for parameters and return types to hint the primitive is a handle.
 *
 * <h4>Usage:</h4>
 * <p>
 * JNI handle:
 *
 * <pre>
 * public &#64;Handle(StaticObject.class) long NewGlobalRef(&#64;Handle(StaticObject.class) long handle) {
 * </pre>
 *
 * Field handle:
 *
 * <pre>
 * public boolean GetBooleanField(StaticObject object, &#64;Handle(Field.class) long fieldId)
 * </pre>
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {TYPE_USE})
public @interface Handle {
    /**
     * Class of the object referenced by the handle. Expected types are {@link Field},
     * {@link Method} and {@link StaticObject}.
     */
    Class<?> value() default Handle.class;
}
