/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Executable;
import java.lang.reflect.Field;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.dynamicaccess.AccessCondition;
import org.graalvm.nativeimage.impl.RuntimeJNIAccessSupport;

/**
 * This interface is used to register classes, methods, and fields for JNI access at runtime. An
 * instance of this interface is acquired via
 * {@link Feature.AfterRegistrationAccess#getRuntimeJNIAccess()}.
 *
 * All methods in {@link RuntimeJNIAccess} require a {@link AccessCondition} as their first
 * parameter. A class and its members will be registered for JNI access only if the specified
 * condition is satisfied.
 *
 * <h3>How to use</h3>
 *
 * {@link RuntimeJNIAccess} should only be used during {@link Feature#afterRegistration}. Any
 * attempt to register metadata in any other phase will result in an error.
 * <p>
 * <strong>Example:</strong>
 *
 * <pre>{@code @Override
 * public void afterRegistration(AfterRegistrationAccess access) {
 *     RuntimeJNIAccess jniAccess = access.getRuntimeJNIAccess();
 *     AccessCondition condition = AccessCondition.typeReached(Condition.class);
 *     jniAccess.register(condition, Foo.class);
 *     jniAccess.register(condition, Foo.class.getMethod("method"));
 *     jniAccess.register(condition, Foo.class.getField("field"));
 * }
 * }</pre>
 *
 * @see <a href=https://docs.oracle.com/en/java/javase/17/docs/specs/jni/functions.html>Java docs -
 *      JNI functions</a>
 *
 * @since 22.3
 */
@Platforms(Platform.HOSTED_ONLY.class)
public interface RuntimeJNIAccess {

    /**
     * Registers the provided classes for JNI access at runtime, if the {@code condition} is
     * satisfied.
     *
     * @since 25.0
     */
    void register(AccessCondition condition, Class<?>... classes);

    /**
     * Registers the provided methods for JNI access at runtime, if the {@code condition} is
     * satisfied.
     *
     * @since 25.0
     */
    void register(AccessCondition condition, Executable... methods);

    /**
     * Registers the provided fields for JNI access at runtime, if the {@code condition} is
     * satisfied.
     *
     * @since 25.0
     */
    void register(AccessCondition condition, Field... fields);

    /**
     * Makes the provided classes available for JNI access at run time. Needed when native code
     * looks up Java classes via <a href=
     * "https://docs.oracle.com/en/java/javase/17/docs/specs/jni/functions.html#findclass">FindClass</a>.
     *
     * @since 22.3
     */
    static void register(Class<?>... classes) {
        ImageSingletons.lookup(RuntimeJNIAccessSupport.class).register(AccessCondition.unconditional(), classes);
    }

    /**
     * Makes the provided methods available for JNI access at run time. Needed when native code
     * looks up Java methods via <a href=
     * "https://docs.oracle.com/en/java/javase/17/docs/specs/jni/functions.html#getmethodid">GetMethodID</a>
     * or <a href=
     * "https://docs.oracle.com/en/java/javase/17/docs/specs/jni/functions.html#getstaticmethodid">GetStaticMethodID</a>.
     *
     * @since 22.3
     */
    static void register(Executable... methods) {
        ImageSingletons.lookup(RuntimeJNIAccessSupport.class).register(AccessCondition.unconditional(), false, methods);
    }

    /**
     * Makes the provided fields available for JNI access at run time. Needed when native code looks
     * up Java fields via <a href=
     * "https://docs.oracle.com/en/java/javase/17/docs/specs/jni/functions.html#getfieldid">GetFieldID</a>
     * or <a href=
     * "https://docs.oracle.com/en/java/javase/17/docs/specs/jni/functions.html#getstaticfieldid">GetStaticFieldID</a>.
     *
     * @since 22.3
     */
    static void register(Field... fields) {
        ImageSingletons.lookup(RuntimeJNIAccessSupport.class).register(AccessCondition.unconditional(), false, fields);
    }
}
