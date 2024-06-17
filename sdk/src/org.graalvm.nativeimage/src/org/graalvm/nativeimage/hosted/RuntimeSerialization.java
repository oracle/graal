/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeSerializationSupport;

/**
 * This class provides methods that can be called before and during analysis, to register classes
 * for serialization at image runtime.
 *
 * @since 21.3
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class RuntimeSerialization {

    /**
     * Register the specified serialization target class itself and all associated classes.
     * <p>
     * According to the Java Object Serialization Specification, the associated classes include 1)
     * all the target class' non-static and non-transient fields types and their associated classes;
     * 2) other fields defined in the customised writeObject(ObjectOutputStream) and
     * readObject(ObjectInputStream). This method can automatically explore all possible
     * serialization target classes in the first scenario, but can't figure out the classes in the
     * second scenario.
     * <p>
     * Another limitation is the specified {@code clazz} must have no subclasses (effectively
     * final). Otherwise, the actual serialization target class could be any subclass of the
     * specified class at runtime.
     *
     * @param clazz the serialization target class
     * @since 21.3
     */
    public static void registerIncludingAssociatedClasses(Class<?> clazz) {
        RuntimeSerializationSupport.singleton().registerIncludingAssociatedClasses(ConfigurationCondition.alwaysTrue(), clazz);
    }

    /**
     * Makes the provided classes available for serialization at runtime.
     *
     * @since 21.3
     */
    public static void register(Class<?>... classes) {
        RuntimeSerializationSupport.singleton().register(ConfigurationCondition.alwaysTrue(), classes);
    }

    /**
     * Makes the provided class available for serialization at runtime but uses the provided
     * customTargetConstructorClazz for deserialization.
     * <p>
     * In some cases an application might explicitly make calls to
     * {@code ReflectionFactory.newConstructorForSerialization(Class<?> cl, Constructor<?> constructorToCall)}
     * where the passed `constructorToCall` differs from what would automatically be used if regular
     * deserialization of `cl` would happen. This method exists to also support such usecases.
     *
     * @since 21.3
     */
    public static void registerWithTargetConstructorClass(Class<?> clazz, Class<?> customTargetConstructorClazz) {
        RuntimeSerializationSupport.singleton().registerWithTargetConstructorClass(ConfigurationCondition.alwaysTrue(), clazz, customTargetConstructorClazz);
    }

    /**
     * Makes a class available for serialization at runtime that is created for the lambda
     * expressions (a class that has a $deserializeLambda$ method) specified by the
     * lambdaCapturingClass.
     *
     * @since 22.3
     */
    public static void registerLambdaCapturingClass(Class<?> lambdaCapturingClass) {
        RuntimeSerializationSupport.singleton().registerLambdaCapturingClass(ConfigurationCondition.alwaysTrue(), lambdaCapturingClass);
    }

    /**
     * Makes a dynamic proxy class (class that extends {@link java.lang.reflect.Proxy}) available
     * for serialization at runtime that is specified by the given interfaces the proxy class
     * implements.
     *
     * @since 22.3
     */
    public static void registerProxyClass(Class<?>... implementedInterfaces) {
        RuntimeSerializationSupport.singleton().registerProxyClass(ConfigurationCondition.alwaysTrue(), implementedInterfaces);
    }

    private RuntimeSerialization() {
    }
}
