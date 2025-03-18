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
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;

//Checkstyle: allow reflection

/**
 * This class provides methods that can be called during native image generation to register
 * classes, methods, and fields for reflection at run time.
 *
 * @since 19.0
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class RuntimeReflection {

    /**
     * Makes the provided classes available for reflection at run time. A call to
     * {@link Class#forName} for the names of the classes will return the classes at run time.
     *
     * @since 19.0
     */
    public static void register(Class<?>... classes) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).register(ConfigurationCondition.alwaysTrue(), classes);
    }

    /**
     * Makes the provided class available for reflection at run time. A call to
     * {@link Class#forName} for the name of the class will return the class (if it exists) or a
     * {@link ClassNotFoundException} at run time.
     *
     * @since 23.0
     */
    public static void registerClassLookup(String className) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerClassLookup(ConfigurationCondition.alwaysTrue(), className);
    }

    /**
     * Makes the provided methods available for reflection at run time. The methods will be returned
     * by {@link Class#getMethod}, {@link Class#getDeclaredMethod(String, Class[])}, and all the
     * other methods on {@link Class} that return a single method.
     *
     * @since 19.0
     */
    public static void register(Executable... methods) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).register(ConfigurationCondition.alwaysTrue(), false, methods);
    }

    /**
     * Makes the provided methods available for reflection queries at run time. The methods will be
     * returned by {@link Class#getMethod}, {@link Class#getDeclaredMethod(String, Class[])}, and
     * all the other methods on {@link Class} that return a single method, but will not be invocable
     * and will not be considered reachable.
     *
     * @since 21.3
     */
    public static void registerAsQueried(Executable... methods) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).register(ConfigurationCondition.alwaysTrue(), true, methods);
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
    public static void registerMethodLookup(Class<?> declaringClass, String methodName, Class<?>... parameterTypes) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerMethodLookup(ConfigurationCondition.alwaysTrue(), declaringClass, methodName, parameterTypes);
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
    public static void registerConstructorLookup(Class<?> declaringClass, Class<?>... parameterTypes) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerConstructorLookup(ConfigurationCondition.alwaysTrue(), declaringClass, parameterTypes);
    }

    /**
     * Makes the provided fields available for reflection at run time. The fields will be returned
     * by {@link Class#getField}, {@link Class#getDeclaredField(String)},and all the other methods
     * on {@link Class} that return a single field.
     *
     * @since 19.0
     */
    public static void register(Field... fields) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).register(ConfigurationCondition.alwaysTrue(), false, fields);
    }

    /**
     * Makes the provided field available for reflection at run time. The field will be returned by
     * {@link Class#getField}, {@link Class#getDeclaredField(String)}, and all the other methods on
     * {@link Class} that return a single field. If the field doesn't exist a
     * {@link NoSuchFieldException} will be thrown when calling these methods at run-time.
     *
     * @since 19.0
     */
    public static void registerFieldLookup(Class<?> declaringClass, String fieldName) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerFieldLookup(ConfigurationCondition.alwaysTrue(), declaringClass, fieldName);
    }

    /**
     * Allows calling {@link Class#getClasses()} on the provided class at run time.
     *
     * @since 23.0
     */
    public static void registerAllClasses(Class<?> declaringClass) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerAllClassesQuery(ConfigurationCondition.alwaysTrue(), declaringClass);
    }

    /**
     * Allows calling {@link Class#getDeclaredClasses()} on the provided class at run time.
     *
     * @since 23.0
     */
    public static void registerAllDeclaredClasses(Class<?> declaringClass) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerAllDeclaredClassesQuery(ConfigurationCondition.alwaysTrue(), declaringClass);
    }

    /**
     * Allows calling {@link Class#getMethods()} on the provided class at run time. The methods will
     * also be registered for individual queries.
     *
     * @since 23.0
     */
    public static void registerAllMethods(Class<?> declaringClass) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerAllMethodsQuery(ConfigurationCondition.alwaysTrue(), true, declaringClass);
    }

    /**
     * Allows calling {@link Class#getDeclaredMethods()} on the provided class at run time. The
     * methods will also be registered for individual queries.
     *
     * @since 23.0
     */
    public static void registerAllDeclaredMethods(Class<?> declaringClass) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerAllDeclaredMethodsQuery(ConfigurationCondition.alwaysTrue(), true, declaringClass);
    }

    /**
     * Allows calling {@link Class#getConstructors()} on the provided class at run time. The
     * constructors will also be registered for individual queries.
     *
     * @since 23.0
     */
    public static void registerAllConstructors(Class<?> declaringClass) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerAllConstructorsQuery(ConfigurationCondition.alwaysTrue(), true, declaringClass);
    }

    /**
     * Allows calling {@link Class#getDeclaredConstructors()} on the provided class at run time. The
     * constructors will also be registered for individual queries.
     *
     * @since 23.0
     */
    public static void registerAllDeclaredConstructors(Class<?> declaringClass) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerAllDeclaredConstructorsQuery(ConfigurationCondition.alwaysTrue(), true, declaringClass);
    }

    /**
     * Allows calling {@link Class#getFields()} on the provided class at run time. The fields will
     * also be registered for individual queries.
     *
     * @since 23.0
     */
    public static void registerAllFields(Class<?> declaringClass) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerAllFields(ConfigurationCondition.alwaysTrue(), declaringClass);
    }

    /**
     * Allows calling {@link Class#getDeclaredFields()} on the provided class at run time. The
     * fields will also be registered for individual queries.
     *
     * @since 23.0
     */
    public static void registerAllDeclaredFields(Class<?> declaringClass) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerAllDeclaredFields(ConfigurationCondition.alwaysTrue(), declaringClass);
    }

    /**
     * Allows calling {@link Class#getNestMembers()} on the provided class at run time.
     *
     * @since 23.0
     */
    public static void registerAllNestMembers(Class<?> declaringClass) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerAllNestMembersQuery(ConfigurationCondition.alwaysTrue(), declaringClass);
    }

    /**
     * Allows calling {@link Class#getPermittedSubclasses()} on the provided class at run time.
     *
     * @since 23.0
     */
    public static void registerAllPermittedSubclasses(Class<?> declaringClass) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerAllPermittedSubclassesQuery(ConfigurationCondition.alwaysTrue(), declaringClass);
    }

    /**
     * Allows calling {@link Class#getRecordComponents()} on the provided class at run time.
     *
     * @since 23.0
     */
    public static void registerAllRecordComponents(Class<?> declaringClass) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerAllRecordComponentsQuery(ConfigurationCondition.alwaysTrue(), declaringClass);
    }

    /**
     * Allows calling {@link Class#getSigners()} on the provided class at run time.
     *
     * @since 23.0
     */
    public static void registerAllSigners(Class<?> declaringClass) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerAllSignersQuery(ConfigurationCondition.alwaysTrue(), declaringClass);
    }

    /**
     * @deprecated Use {@link #register(Field...)} instead. Parameter {@code finalIsWritable} no
     *             longer serves a purpose.
     * @since 19.0
     */
    @SuppressWarnings("unused")
    @Deprecated(since = "21.1")
    public static void register(boolean finalIsWritable, Field... fields) {
        register(fields);
    }

    /**
     * @deprecated Use {@link #register(Field...)} instead. Parameters {@code finalIsWritable} and
     *             {@code allowUnsafeAccess} no longer serve a purpose.
     * @since 21.0
     */
    @SuppressWarnings("unused")
    @Deprecated(since = "21.1")
    public static void register(boolean finalIsWritable, boolean allowUnsafeAccess, Field... fields) {
        register(fields);
    }

    /**
     * Makes the provided classes available for reflective instantiation by
     * {@link Class#newInstance}. This is equivalent to registering the nullary constructors of the
     * classes.
     *
     * @since 19.0
     */
    public static void registerForReflectiveInstantiation(Class<?>... classes) {
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

    private RuntimeReflection() {
    }
}
