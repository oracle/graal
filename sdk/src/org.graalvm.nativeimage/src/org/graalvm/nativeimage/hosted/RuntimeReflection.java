/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.nativeimage.hosted;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.dynamicaccess.AccessCondition;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;

//Checkstyle: allow reflection

/**
 * This interface is used to register classes, methods, and fields for use with the
 * {@link java.lang.reflect} API at runtime, and for serialization at runtime. An instance of this
 * interface is acquired via {@link Feature.AfterRegistrationAccess#getRuntimeReflection()}.
 * <p>
 * All methods in {@link RuntimeReflection} require a {@link AccessCondition} as their first
 * parameter. A class and its members will be registered for dynamic access only if the specified
 * condition is satisfied.
 *
 * <h3>How to use</h3>
 *
 * {@link RuntimeReflection} should only be used during {@link Feature#afterRegistration}. Any
 * attempt to register metadata in any other phase will result in an error.
 * <p>
 * <strong>Example:</strong>
 *
 * <pre>{@code @Override
 * public void afterRegistration(AfterRegistrationAccess access) {
 *     RuntimeReflection reflection = access.getRuntimeReflection();
 *     AccessCondition condition = AccessCondition.typeReached(Condition.class);
 *     reflection.register(condition, Foo.class, Bar.class);
 *     reflection.register(AccessCondition.alwaysTrue(), Foo.class.getMethod("method"));
 *     reflection.registerUnsafeAllocation(condition, Foo.class);
 *     Class<?> proxyClass = reflection.registerProxy(condition, Interface1.class, Interface2.class);
 *     reflection.registerForSerialization(AccessCondition.alwaysTrue(), proxyClass);
 * }
 * }</pre>
 *
 * @since 19.0
 */
@Platforms(Platform.HOSTED_ONLY.class)
public interface RuntimeReflection {

    /**
     * Registers the provided classes for reflection at runtime, if the {@code condition} is
     * satisfied. This means all reflection methods defined by {@link java.lang.Class} are
     * accessible at runtime for those classes.
     * <p>
     * If a class is not registered for reflection at runtime, {@link Class#forName} will throw
     * {@link ClassNotFoundException}.
     *
     * @since 25.0
     */
    void register(AccessCondition condition, Class<?>... classes);

    /**
     * Registers a class with the provided {@code className} for reflection at runtime, if the
     * {@code condition} is satisfied. This method should be used when
     * {@code --exact-reachability-metadata} is set: it makes calls to
     * {@code Class.forName(className)} throw {@link ClassNotFoundException} instead of throwing
     * {@link org.graalvm.nativeimage.MissingReflectionRegistrationError} when the class is not on
     * the classpath. If the class already exists on the classpath, this call is equivalent to the
     * {@link #register(AccessCondition, Class...)}.
     *
     * @since 25.0
     */
    void registerClassLookup(AccessCondition condition, String className);

    /**
     * Registers the provided {@code classes} for unsafe allocation at runtime, if the
     * {@code condition} is satisfied. Unsafe allocation can happen via
     * {@link sun.misc.Unsafe#allocateInstance(Class)} or from native code via
     * {@code AllocObject(jClass)}.
     *
     * @since 25.0
     */
    void registerUnsafeAllocation(AccessCondition condition, Class<?>... classes);

    /**
     * Registers the provided {@code methods} for reflective invocation at runtime, if the
     * {@code condition} is satisfied. This method also registers the declaring classes of the
     * provided methods for reflection at runtime. The methods will be invocable at runtime via
     * {@link java.lang.reflect.Method#invoke(java.lang.Object, java.lang.Object...)}.
     *
     * @since 25.0
     */
    void register(AccessCondition condition, Executable... methods);

    /**
     * Registers the provided {@code fields} for reflective access at runtime, if the
     * {@code condition} is satisfied. This method also registers the declaring classes of the
     * provided fields for reflection at runtime. The fields will be accessible at runtime via
     * {@link java.lang.reflect.Field#set(java.lang.Object, java.lang.Object)} and
     * {@link java.lang.reflect.Field#get(Object)}.
     *
     * @since 25.0
     */
    void register(AccessCondition condition, Field... fields);

    /**
     * Registers the provided classes for serialization at runtime, if the {@code condition} is
     * satisfied. This method also registers the provided classes for reflection at runtime.
     *
     * @since 25.0
     */
    void registerForSerialization(AccessCondition condition, Class<?>... classes);

    /**
     * Registers a {@link java.lang.reflect.Proxy} class in the system classloader that implements
     * the specified {@code interfaces}, if the {@code condition} is satisfied. The proxy class is
     * fully specified by the interfaces it implements, and proxy instances matching that
     * specification can be created at runtime. The returned proxy class can be used in registration
     * for reflection and serialization at runtime.
     * <p>
     * <strong>NOTE:</strong> The order of the interfaces provided in the {@code interfaces}
     * parameter is significant; different orderings will produce distinct proxy classes.
     * <p>
     * <strong>Example</strong>:
     *
     * <pre>{@code
     * Class<?> proxyClass = reflection.registerProxy(AccessCondition.alwaysTrue(), Interface1.class, Interface2.class);
     * reflection.register(AccessCondition.alwaysTrue(), proxyClass);
     * reflection.registerForSerialization(AccessCondition.alwaysTrue(), proxyClass);
     * }</pre>
     *
     * @return Proxy class defined by the provided interfaces, or {@code null} if no such proxy
     *         class can be created with the given interfaces
     *
     * @since 25.0
     */
    Class<?> registerProxy(AccessCondition condition, Class<?>... interfaces);

    /**
     * Makes the provided classes available for reflection at run time. A call to
     * {@link Class#forName} for the names of the classes will return the classes at run time.
     *
     * @since 19.0
     */
    static void register(Class<?>... classes) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).register(AccessCondition.unconditional(), classes);
    }

    /**
     * Makes the provided class available for reflection at run time. A call to
     * {@link Class#forName} for the name of the class will return the class (if it exists) or a
     * {@link ClassNotFoundException} at run time.
     *
     * @since 23.0
     */
    static void registerClassLookup(String className) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerClassLookup(AccessCondition.unconditional(), className);
    }

    /**
     * Makes the provided methods available for reflection at run time. The methods will be returned
     * by {@link Class#getMethod}, {@link Class#getDeclaredMethod(String, Class[])}, and all the
     * other methods on {@link Class} that return a single method.
     *
     * @since 19.0
     */
    static void register(Executable... methods) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).register(AccessCondition.unconditional(), false, methods);
    }

    /**
     * Makes the provided methods available for reflection queries at run time. The methods will be
     * returned by {@link Class#getMethod}, {@link Class#getDeclaredMethod(String, Class[])}, and
     * all the other methods on {@link Class} that return a single method, but will not be invocable
     * and will not be considered reachable.
     *
     * @since 21.3
     */
    static void registerAsQueried(Executable... methods) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).register(AccessCondition.unconditional(), true, methods);
    }

    /**
     * Makes the provided method available for reflection queries at run time. The method will be
     * returned by {@link Class#getMethod}, {@link Class#getDeclaredMethod(String, Class[])}, and
     * all the other methods on {@link Class} that return a single method, but will not be invocable
     * and will not be considered reachable. If the method doesn't exist a
     * {@link NoSuchMethodException} will be thrown when calling these methods at run-time.
     *
     * @since 23.0
     */
    static void registerMethodLookup(Class<?> declaringClass, String methodName, Class<?>... parameterTypes) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerMethodLookup(AccessCondition.unconditional(), declaringClass, methodName, parameterTypes);
    }

    /**
     * Makes the provided constructor available for reflection queries at run time. The constructor
     * will be returned by {@link Class#getConstructor},
     * {@link Class#getDeclaredConstructor(Class[])}, and all the other methods on {@link Class}
     * that return a single constructor, but will not be invocable and will not be considered
     * reachable. If the constructor doesn't exist a {@link NoSuchMethodException} will be thrown
     * when calling these methods at run-time.
     *
     * @since 23.0
     */
    static void registerConstructorLookup(Class<?> declaringClass, Class<?>... parameterTypes) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerConstructorLookup(AccessCondition.unconditional(), declaringClass, parameterTypes);
    }

    /**
     * Makes the provided fields available for reflection at run time. The fields will be returned
     * by {@link Class#getField}, {@link Class#getDeclaredField(String)},and all the other methods
     * on {@link Class} that return a single field.
     *
     * @since 19.0
     */
    static void register(Field... fields) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).register(AccessCondition.unconditional(), false, fields);
    }

    /**
     * Makes the provided field available for reflection at run time. The field will be returned by
     * {@link Class#getField}, {@link Class#getDeclaredField(String)}, and all the other methods on
     * {@link Class} that return a single field. If the field doesn't exist a
     * {@link NoSuchFieldException} will be thrown when calling these methods at run-time.
     *
     * @since 19.0
     */
    static void registerFieldLookup(Class<?> declaringClass, String fieldName) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerFieldLookup(AccessCondition.unconditional(), declaringClass, fieldName);
    }

    /**
     * Allows calling {@link Class#getClasses()} on the provided class at run time.
     *
     * @since 23.0
     */
    static void registerAllClasses(Class<?> declaringClass) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerAllClassesQuery(AccessCondition.unconditional(), declaringClass);
    }

    /**
     * Allows calling {@link Class#getDeclaredClasses()} on the provided class at run time.
     *
     * @since 23.0
     */
    static void registerAllDeclaredClasses(Class<?> declaringClass) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerAllDeclaredClassesQuery(AccessCondition.unconditional(), declaringClass);
    }

    /**
     * Allows calling {@link Class#getMethods()} on the provided class at run time. The methods will
     * also be registered for individual queries.
     *
     * @since 23.0
     */
    static void registerAllMethods(Class<?> declaringClass) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerAllMethodsQuery(AccessCondition.unconditional(), true, declaringClass);
    }

    /**
     * Allows calling {@link Class#getDeclaredMethods()} on the provided class at run time. The
     * methods will also be registered for individual queries.
     *
     * @since 23.0
     */
    static void registerAllDeclaredMethods(Class<?> declaringClass) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerAllDeclaredMethodsQuery(AccessCondition.unconditional(), true, declaringClass);
    }

    /**
     * Allows calling {@link Class#getConstructors()} on the provided class at run time. The
     * constructors will also be registered for individual queries.
     *
     * @since 23.0
     */
    static void registerAllConstructors(Class<?> declaringClass) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerAllConstructorsQuery(AccessCondition.unconditional(), true, declaringClass);
    }

    /**
     * Allows calling {@link Class#getDeclaredConstructors()} on the provided class at run time. The
     * constructors will also be registered for individual queries.
     *
     * @since 23.0
     */
    static void registerAllDeclaredConstructors(Class<?> declaringClass) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerAllDeclaredConstructorsQuery(AccessCondition.unconditional(), true, declaringClass);
    }

    /**
     * Allows calling {@link Class#getFields()} on the provided class at run time. The fields will
     * also be registered for individual queries.
     *
     * @since 23.0
     */
    static void registerAllFields(Class<?> declaringClass) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerAllFields(AccessCondition.unconditional(), declaringClass);
    }

    /**
     * Allows calling {@link Class#getDeclaredFields()} on the provided class at run time. The
     * fields will also be registered for individual queries.
     *
     * @since 23.0
     */
    static void registerAllDeclaredFields(Class<?> declaringClass) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerAllDeclaredFields(AccessCondition.unconditional(), declaringClass);
    }

    /**
     * Allows calling {@link Class#getNestMembers()} on the provided class at run time.
     *
     * @since 23.0
     */
    static void registerAllNestMembers(Class<?> declaringClass) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerAllNestMembersQuery(AccessCondition.unconditional(), declaringClass);
    }

    /**
     * Allows calling {@link Class#getPermittedSubclasses()} on the provided class at run time.
     *
     * @since 23.0
     */
    static void registerAllPermittedSubclasses(Class<?> declaringClass) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerAllPermittedSubclassesQuery(AccessCondition.unconditional(), declaringClass);
    }

    /**
     * Allows calling {@link Class#getRecordComponents()} on the provided class at run time.
     *
     * @since 23.0
     */
    static void registerAllRecordComponents(Class<?> declaringClass) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerAllRecordComponentsQuery(AccessCondition.unconditional(), declaringClass);
    }

    /**
     * Allows calling {@link Class#getSigners()} on the provided class at run time.
     *
     * @since 23.0
     */
    static void registerAllSigners(Class<?> declaringClass) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerAllSignersQuery(AccessCondition.unconditional(), declaringClass);
    }

    /**
     * @deprecated Use {@link #register(Field...)} instead. Parameter {@code finalIsWritable} no
     *             longer serves a purpose.
     * @since 19.0
     */
    @SuppressWarnings("unused")
    @Deprecated(since = "21.1")
    static void register(boolean finalIsWritable, Field... fields) {
        register(fields);
    }

    /**
     * @deprecated Use {@link #register(Field...)} instead. Parameters {@code finalIsWritable} and
     *             {@code allowUnsafeAccess} no longer serve a purpose.
     * @since 21.0
     */
    @SuppressWarnings("unused")
    @Deprecated(since = "21.1")
    static void register(boolean finalIsWritable, boolean allowUnsafeAccess, Field... fields) {
        register(fields);
    }

    /**
     * Makes the provided classes available for reflective instantiation by
     * {@link Class#newInstance}. This is equivalent to registering the nullary constructors of the
     * classes.
     *
     * @since 19.0
     */
    static void registerForReflectiveInstantiation(Class<?>... classes) {
        for (Class<?> clazz : classes) {
            if (clazz.isArray() || clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
                throw new IllegalArgumentException("Class " + clazz.getTypeName() + " cannot be instantiated reflectively. It must be a non-abstract instance type.");
            }

            Constructor<?> nullaryConstructor;
            try {
                nullaryConstructor = clazz.getDeclaredConstructor();
            } catch (NoSuchMethodException ex) {
                throw new IllegalArgumentException("Class " + clazz.getTypeName() + " cannot be instantiated reflectively . It does not have a nullary constructor.");
            }

            register(nullaryConstructor);
        }
    }
}
