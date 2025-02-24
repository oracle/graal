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

import java.lang.reflect.Executable;
import java.lang.reflect.Field;

import org.graalvm.nativeimage.hosted.Feature;

/**
 * This interface is used to register classes, methods, and fields for use with the
 * {@link java.lang.reflect} API at run time, and for serialization at run time. An instance of this
 * interface is acquired via {@link Feature.AfterRegistrationAccess#getReflectiveAccess()}.
 * <p>
 * All methods in {@link ReflectiveAccess} require a {@link AccessCondition} as their first
 * parameter. A class and its members will be accessible at run-time only if the specified condition
 * is satisfied.
 *
 * <h3>How to use</h3>
 *
 * {@link ReflectiveAccess} should only be used during {@link Feature#afterRegistration}. Attempts
 * to register metadata in any other phase of the {@link Feature} API will result in an error.
 * <p>
 * <strong>Example:</strong>
 *
 * <pre>{@code @Override
 * public void afterRegistration(AfterRegistrationAccess access) {
 *     ReflectionAccess reflection = access.getReflectionAccess();
 *
 *     AccessCondition condition = AccessCondition.typeReached(ConditionType.class);
 *     reflection.register(condition, Foo.class, Bar.class);
 *
 *     reflection.register(AccessCondition.unconditional(), Foo.class.getMethod("method"));
 *
 *     reflection.registerForUnsafeAllocation(condition, Foo.class);
 *
 *     Class<?> proxyClass = reflection.registerProxy(condition, Interface1.class, Interface2.class);
 *     reflection.registerForSerialization(AccessCondition.unconditional(), proxyClass);
 * }
 * }</pre>
 *
 * @since 25.0.1
 */
public interface ReflectiveAccess {

    /**
     * Registers the provided classes for reflection at run time, if the {@code condition} is
     * satisfied. This means all reflection methods defined by the {@link java.lang.Class} (except
     * {@link Class#arrayType()}) are accessible at run time for those classes.
     * <p>
     * If a class is not registered for reflection at run time, the following methods will throw
     * {@link ClassNotFoundException}:
     * <ul>
     * <li>{@link Class#forName(String)}</li>
     * <li>{@link Class#forName(Module, String)}</li>
     * <li>{@link Class#forName(String, boolean, ClassLoader)}</li>
     * <li>{@link ClassLoader#loadClass(String)}</li>
     * </ul>
     *
     * @since 25.0.1
     */
    void register(AccessCondition condition, Class<?>... classes);

    /**
     * Registers the provided {@code executables} (methods and constructors) for reflective
     * invocation at run time, if the {@code condition} is satisfied. This method also registers the
     * declaring classes of the provided executables for reflection at run time. The executables
     * will be invocable at run time via
     * {@link java.lang.reflect.Method#invoke(java.lang.Object, java.lang.Object...)}.
     *
     * @since 25.0.1
     */
    void register(AccessCondition condition, Executable... executables);

    /**
     * Registers the provided {@code fields} for reflective access at run time, if the
     * {@code condition} is satisfied. This method also registers the declaring classes of the
     * provided fields for reflection at run time.
     * <p>
     * The fields will be accessible at run time via following methods:
     * <ul>
     * <li>{@link java.lang.reflect.Field#get}
     * <li>{@link java.lang.reflect.Field#set}
     * <li>{@link java.lang.reflect.Field#getByte}</li>
     * <li>{@link java.lang.reflect.Field#setByte}</li>
     * <li>{@link java.lang.reflect.Field#getChar}</li>
     * <li>{@link java.lang.reflect.Field#setChar}</li>
     * <li>{@link java.lang.reflect.Field#getShort}</li>
     * <li>{@link java.lang.reflect.Field#setShort}</li>
     * <li>{@link java.lang.reflect.Field#getInt}</li>
     * <li>{@link java.lang.reflect.Field#setInt}</li>
     * <li>{@link java.lang.reflect.Field#getLong}</li>
     * <li>{@link java.lang.reflect.Field#setLong}</li>
     * <li>{@link java.lang.reflect.Field#getFloat}</li>
     * <li>{@link java.lang.reflect.Field#setFloat}</li>
     * <li>{@link java.lang.reflect.Field#getDouble}</li>
     * <li>{@link java.lang.reflect.Field#setDouble}</li>
     * </ul>
     *
     * @since 25.0.1
     */
    void register(AccessCondition condition, Field... fields);

    /**
     * Registers the provided classes for serialization at run time, if the {@code condition} is
     * satisfied. This method also registers the provided classes for reflection at run time.
     * <p>
     * The following methods require a type to be registered for serialization:
     * <ul>
     * <li>{@link java.io.ObjectOutputStream#writeObject}</li>
     * <li>{@link java.io.ObjectOutputStream#writeUnshared}</li>
     * <li>{@link java.io.ObjectInputStream#readObject()}</li>
     * <li>{@link java.io.ObjectInputStream#readUnshared}</li>
     * </ul>
     *
     * @since 25.0.1
     */
    void registerForSerialization(AccessCondition condition, Class<?>... classes);

    /**
     * Registers a {@link java.lang.reflect.Proxy} class in the system classloader that implements
     * the specified {@code interfaces}, if the {@code condition} is satisfied. The proxy class is
     * fully specified by the interfaces it implements, and proxy instances matching that
     * specification can be created at run time. The returned proxy class can be used in
     * registration for serialization at run time. <blockquote> <strong>NOTE</strong>: The order of
     * the interfaces provided in the {@code interfaces} parameter is significant; different
     * orderings will produce distinct proxy classes. </blockquote>
     * <p>
     * <strong>Example</strong>:
     *
     * <pre>{@code
     * Class<?> proxyClass = reflection.registerProxy(AccessCondition.unconditional(), Interface1.class, Interface2.class);
     * reflection.registerForSerialization(AccessCondition.unconditional(), proxyClass);
     * }</pre>
     *
     * @return Proxy class defined by the provided interfaces, or {@code null} if no such proxy
     *         class can be created with the given interfaces
     *
     * @since 25.0.1
     */
    Class<?> registerProxy(AccessCondition condition, Class<?>... interfaces);

    /**
     * Registers the provided {@code classes} for unsafe allocation at run time, if the
     * {@code condition} is satisfied. Unsafe allocation can happen via
     * {@link sun.misc.Unsafe#allocateInstance(Class)} or from native code via
     * {@code AllocObject(jClass)}.
     *
     * @since 25.0.1
     */
    void registerForUnsafeAllocation(AccessCondition condition, Class<?>... classes);
}
