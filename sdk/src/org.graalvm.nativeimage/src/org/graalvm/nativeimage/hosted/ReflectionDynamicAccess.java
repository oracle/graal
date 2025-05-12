package org.graalvm.nativeimage.hosted;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;

/**
 * This interface is used to register classes, methods, fields for reflection, serialization, and
 * JNI access at runtime.
 *
 * {@link ReflectionDynamicAccess} is based on {@link RegistrationCondition}. Registration of any
 * class and its members for dynamic access will succeed only if condition is met.
 *
 * {@link ReflectionDynamicAccess} should only be used at {@link Feature#afterRegistration}.
 */
public interface ReflectionDynamicAccess {

    /**
     * Makes the provided classes available for reflection at runtime, and all of their accessible
     * members available for reflection queries at run time if {@code condition} is satisfied. A
     * call to {@link Class#forName} for the names of the classes will return the classes at run
     * time.
     *
     * @param condition needs to be satisfied for inclusion of types for reflection at runtime
     */
    void register(RegistrationCondition condition, Class<?>... classes);

    /**
     * Makes the provided class available for reflection at runtime if {@code condition} is
     * satisfied. A call to {@link Class#forName} for the name of the class will return the class
     * (if it exists) or a {@link ClassNotFoundException} at run time.
     */
    void registerClassLookup(RegistrationCondition condition, String className);

    /**
     * Makes the provided methods available for reflection at runtime if {@code condition} is
     * satisfied. The methods will be returned by {@link Class#getMethod},
     * {@link Class#getDeclaredMethod(String, Class[])}, and all the other methods on {@link Class}
     * that return a single method. Methods can be invoked reflectively.
     */
    void register(RegistrationCondition condition, Executable... methods);

    /**
     * Makes the provided fields available for reflection at runtime if {@code condition} is
     * satisfied. The fields will be returned by {@link Class#getField},
     * {@link Class#getDeclaredField(String)}, and all the other methods on {@link Class} that
     * return a single field. The fields can be accessed reflectively.
     */
    void register(RegistrationCondition condition, Field... fields);

    /**
     * Makes the provided classes available for both serialization and reflection at runtime if
     * {@code condition} is satisfied.
     */
    void registerForSerialization(RegistrationCondition condition, Class<?>... classes);

    /**
     * Makes the provided classes available for both JNI access and reflection at runtime if
     * {@code condition} is satisfied.
     */
    void registerForJNIAccess(RegistrationCondition condition, Class<?>... classes);
}
