package org.graalvm.nativeimage.hosted;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;

/**
 * This interface is used to register classes, methods, fields for reflection, serialization, and
 * JNI access at runtime.
 *
 * All methods in {@link ReflectionDynamicAccess} require a {@link RegistrationCondition} as their
 * first parameter. A class and its members will be registered for dynamic access only if the
 * specified condition is satisfied.
 *
 * {@link ReflectionDynamicAccess} should only be used during {@link Feature#afterRegistration}. Any
 * attempt of registration in any other phase will result as an error.
 */
public interface ReflectionDynamicAccess {

    /**
     * Registers the provided classes for reflection at runtime, and all of their accessible members
     * available for reflection queries at runtime, if the {@code condition} is satisfied. A call to
     * {@link Class#forName} for the names of the classes will return the classes at runtime.
     *
     * If the class is not registered for reflection at runtime, {@link Class#forName} will throw
     * {@link org.graalvm.nativeimage.MissingReflectionRegistrationError}.
     */
    void register(RegistrationCondition condition, Class<?>... classes);

    /**
     * If the {@code condition} is satisfied, this method registers the class with the provided name
     * for reflection at runtime.
     * <ul>
     * <li>If the class exists, a call to {@link Class#forName} for the name of the class will
     * return the class at runtime.</li>
     * <li>Otherwise, {@link Class#forName} will throw {@link ClassNotFoundException} at
     * runtime.</li>
     * </ul>
     *
     * If the class is not registered for reflection at runtime, {@link Class#forName} will throw
     * {@link org.graalvm.nativeimage.MissingReflectionRegistrationError}.
     */
    void registerClassLookup(RegistrationCondition condition, String className);

    /**
     * Registers the provided methods for reflective invocation at runtime, along with the declaring
     * classes of those methods, including all their members, for reflection queries at runtime, if
     * the {@code condition} is satisfied. The methods will be invocable via
     * {@link java.lang.reflect.Method#invoke(java.lang.Object, java.lang.Object...)}.
     */
    void register(RegistrationCondition condition, Executable... methods);

    /**
     * Registers the provided fields for reflective access at runtime, along with the declaring
     * classes of those fields, including all their members, for reflection queries at runtime, if
     * the {@code condition} is satisfied. The fields will be accessible via
     * {@link java.lang.reflect.Field#set(java.lang.Object, java.lang.Object)} and
     * {@link java.lang.reflect.Field#get(Object)}.
     */
    void register(RegistrationCondition condition, Field... fields);

    /**
     * Registers the provided classes for both serialization and reflection at runtime, if the
     * {@code condition} is satisfied.
     */
    void registerForSerialization(RegistrationCondition condition, Class<?>... classes);
}
