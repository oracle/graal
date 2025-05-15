package org.graalvm.nativeimage.hosted;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;

public interface DynamicJNIAccess {

    /**
     * Registers the provided classes for both JNI access and reflection at runtime, if the
     * {@code condition} is satisfied.
     */
    void register(RegistrationCondition condition, Class<?>... classes);

    /**
     * Registers the provided methods for both JNI access and reflection at runtime, if the
     * {@code condition} is satisfied.
     * {@link ReflectionDynamicAccess#register(RegistrationCondition, Executable...)} is implicitly
     * called, thereby this method also registers the declaring classes of those methods, including
     * all their members, for runtime reflection queries.
     */
    void register(RegistrationCondition condition, Executable... methods);

    /**
     * Registers the provided fields for both JNI access and reflection at runtime, if the
     * {@code condition} is satisfied.
     * {@link ReflectionDynamicAccess#register(RegistrationCondition, Field...)} is implicitly
     * called, thereby this method also registers the declaring classes of those fields, including
     * all their members, for runtime reflection queries.
     */
    void register(RegistrationCondition condition, Field... fields);
}
