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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

/**
 * This interface is used to register classes, methods, and fields for foreign access at run time.
 * An instance of this interface is acquired via
 * {@link Feature.AfterRegistrationAccess#getForeignAccess()}.
 * <p>
 * All methods in {@link ForeignAccess} require a {@link AccessCondition} as their first parameter.
 * Registration for foreign access will happen only if the specified condition is satisfied.
 *
 * <h3>How to use</h3>
 *
 * {@link ForeignAccess} should only be used during {@link Feature#afterRegistration}. Any attempt
 * to register metadata in any other phase will result in an error.
 * <p>
 * <strong>Example:</strong>
 *
 * <pre>{@code @Override
 * public void afterRegistration(AfterRegistrationAccess access) {
 *     ForeignAccess foreignAccess = access.getForeignAccess();
 *     AccessCondition condition = AccessCondition.typeReached(ConditionType.class);
 *     foreignAccess.registerForDowncall(condition, java.lang.foreign.ValueLayout.JAVA_INT);
 * }
 * }</pre>
 *
 * @since 25.0.1
 */
@SuppressWarnings("all")
public interface ForeignAccess {

    /**
     * Registers the provided function descriptor and options pair at image build time for downcalls
     * into foreign code, if the {@code condition} is satisfied. Required to get a downcall method
     * handle using {@link java.lang.foreign.Linker#downcallHandle} for the same descriptor and
     * options at run time.
     * <p>
     * Even though this method is weakly typed for compatibility reasons, run-time checks will be
     * performed to ensure that the arguments have the expected type. It will be deprecated in favor
     * of strongly typed variant as soon as possible.
     *
     * @param condition represents the condition that needs to be satisfied in order to access
     *            target resources.
     * @param desc A {@link java.lang.foreign.FunctionDescriptor} to register for downcalls.
     * @param options An array of {@link java.lang.foreign.Linker.Option} used for the downcalls.
     *
     * @since 25.0.1
     */
    void registerForDowncall(AccessCondition condition, Object desc, Object... options);

    /**
     * Registers the provided function descriptor and options pair at image build time for upcalls
     * from foreign code, if the {@code condition} is satisfied. Required to get an upcall stub
     * function pointer using {@link java.lang.foreign.Linker#upcallStub} for the same descriptor
     * and options at run time.
     * <p>
     * Even though this method is weakly typed for compatibility reasons, run-time checks will be
     * performed to ensure that the arguments have the expected type. It will be deprecated in favor
     * of strongly typed variant as soon as possible.
     *
     * @param condition represents the condition that needs to be satisfied in order to access
     *            target resources.
     * @param desc A {@link java.lang.foreign.FunctionDescriptor} to register for upcalls.
     * @param options An array of {@link java.lang.foreign.Linker.Option} used for the upcalls.
     *
     * @since 25.0.1
     */
    void registerForUpcall(AccessCondition condition, Object desc, Object... options);

    /**
     * Registers a specific static method (denoted by a method handle) as a fast upcall target, if
     * the {@code condition} is satisfied. This will create a specialized upcall stub that will
     * invoke only the specified method, which is much faster than using
     * {@link #registerForUpcall(AccessCondition, Object, Object...)}).
     * <p>
     * The provided method handle must be a direct method handle. Those are most commonly created
     * using {@link java.lang.invoke.MethodHandles.Lookup#findStatic(Class, String, MethodType)}.
     * However, a strict requirement is that it must be possible to create a non-empty descriptor
     * for the method handle using {@link MethodHandle#describeConstable()}. The denoted static
     * method will also be registered for reflective access since run-time code will also create a
     * method handle to denoted static method.
     * </p>
     * <p>
     * Even though this method is weakly typed for compatibility reasons, run-time checks will be
     * performed to ensure that the arguments have the expected type. It will be deprecated in favor
     * of strongly typed variant as soon as possible.
     * </p>
     *
     * @param condition represents the condition that needs to be satisfied in order to access
     *            target resources.
     * @param target A direct method handle denoting a static method.
     * @param desc A {@link java.lang.foreign.FunctionDescriptor} to register for upcalls.
     * @param options An array of {@link java.lang.foreign.Linker.Option} used for the upcalls.
     *
     * @since 25.0.1
     */
    void registerForDirectUpcall(AccessCondition condition, MethodHandle target, Object desc, Object... options);
}
