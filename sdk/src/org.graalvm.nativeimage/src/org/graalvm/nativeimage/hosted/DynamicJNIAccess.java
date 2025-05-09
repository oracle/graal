package org.graalvm.nativeimage.hosted;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;

/**
 * This interface is used to register classes, methods, fields for JNI access at runtime.
 *
 * All methods in {@link DynamicJNIAccess} require a {@link RegistrationCondition} as their first
 * parameter. A class and its members will be registered for dynamic access only if the specified
 * condition is satisfied.
 *
 * {@link DynamicJNIAccess} should only be used during {@link Feature#afterRegistration}. Any
 * attempt of registration in any other phase will result in an error.
 */
public interface DynamicJNIAccess {

    /**
     * Registers the provided classes for JNI access at runtime, if the {@code condition} is
     * satisfied.
     */
    void register(RegistrationCondition condition, Class<?>... classes);

    /**
     * Registers the provided methods for JNI access at runtime, if the {@code condition} is
     * satisfied.
     */
    void register(RegistrationCondition condition, Executable... methods);

    /**
     * Registers the provided fields for JNI access at runtime, if the {@code condition} is
     * satisfied.
     */
    void register(RegistrationCondition condition, Field... fields);
}
