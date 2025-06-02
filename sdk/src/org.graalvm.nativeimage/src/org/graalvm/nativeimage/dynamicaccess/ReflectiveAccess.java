/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage.dynamicaccess;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RegistrationCondition;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;

/**
 * This interface is used to register classes, methods, and fields for use with the
 * {@link java.lang.reflect} API at runtime, and for serialization at runtime. An instance of this
 * interface is acquired via {@link Feature.AfterRegistrationAccess#getReflectiveAccess()}.
 * <p>
 * All methods in {@link ReflectiveAccess} require a {@link RegistrationCondition} as their first
 * parameter. A class and its members will be registered for dynamic access only if the specified
 * condition is satisfied.
 *
 * <h3>How to use</h3>
 *
 * {@link ReflectiveAccess} should only be used during {@link Feature#afterRegistration}. Any
 * attempt to register metadata in any other phase will result in an error.
 * <p>
 * <strong>Example:</strong>
 *
 * <pre>{@code @Override
 * public void afterRegistration(AfterRegistrationAccess access) {
 *     ReflectionAccess reflection = access.getReflectionAccess();
 *     RegistrationCondition condition = RegistrationCondition.typeReached(Condition.class);
 *     reflection.register(condition, Foo.class, Bar.class);
 *     reflection.register(RegistrationCondition.always(), Foo.class.getMethod("method"));
 *     reflection.registerUnsafeAllocation(condition, Foo.class);
 *     Class<?> proxyClass = reflection.registerProxy(condition, Interface1.class, Interface2.class);
 *     reflection.registerForSerialization(RegistrationCondition.always(), proxyClass);
 * }
 * }</pre>
 *
 * @since 25.0
 */
public interface ReflectiveAccess {

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
    void register(RegistrationCondition condition, Class<?>... classes);

    /**
     * Registers a class with the provided {@code className} for reflection at runtime, if the
     * {@code condition} is satisfied. This method should be used when
     * {@code --exact-reachability-metadata} is set: it makes calls to
     * {@code Class.forName(className)} throw {@link ClassNotFoundException} instead of throwing
     * {@link org.graalvm.nativeimage.MissingReflectionRegistrationError} when the class is not on
     * the classpath. If the class already exists on the classpath, this call is equivalent to the
     * {@link #register(RegistrationCondition, Class...)}.
     *
     * @since 25.0
     */
    void registerClassLookup(RegistrationCondition condition, String className);

    /**
     * Registers the provided {@code classes} for unsafe allocation at runtime, if the
     * {@code condition} is satisfied. Unsafe allocation can happen via
     * {@link sun.misc.Unsafe#allocateInstance(Class)} or from native code via
     * {@code AllocObject(jClass)}.
     *
     * @since 25.0
     */
    void registerUnsafeAllocation(RegistrationCondition condition, Class<?>... classes);

    /**
     * Registers the provided {@code methods} for reflective invocation at runtime, if the
     * {@code condition} is satisfied. This method also registers the declaring classes of the
     * provided methods for reflection at runtime. The methods will be invocable at runtime via
     * {@link java.lang.reflect.Method#invoke(java.lang.Object, java.lang.Object...)}.
     *
     * @since 25.0
     */
    void register(RegistrationCondition condition, Executable... methods);

    /**
     * Registers the provided {@code fields} for reflective access at runtime, if the
     * {@code condition} is satisfied. This method also registers the declaring classes of the
     * provided fields for reflection at runtime. The fields will be accessible at runtime via
     * {@link java.lang.reflect.Field#set(java.lang.Object, java.lang.Object)} and
     * {@link java.lang.reflect.Field#get(Object)}.
     *
     * @since 25.0
     */
    void register(RegistrationCondition condition, Field... fields);

    /**
     * Registers the provided classes for serialization at runtime, if the {@code condition} is
     * satisfied. This method also registers the provided classes for reflection at runtime.
     *
     * @since 25.0
     */
    void registerForSerialization(RegistrationCondition condition, Class<?>... classes);

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
     * Class<?> proxyClass = reflection.registerProxy(RegistrationCondition.always(), Interface1.class, Interface2.class);
     * reflection.register(RegistrationCondition.always(), proxyClass);
     * reflection.registerForSerialization(RegistrationCondition.always(), proxyClass);
     * }</pre>
     *
     * @return Proxy class defined by the provided interfaces, or {@code null} if no such proxy
     *         class can be created with the given interfaces
     *
     * @since 25.0
     */
    Class<?> registerProxy(RegistrationCondition condition, Class<?>... interfaces);
}
