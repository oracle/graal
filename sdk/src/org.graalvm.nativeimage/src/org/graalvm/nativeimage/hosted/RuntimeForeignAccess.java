/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeForeignAccessSupport;

@Platforms(Platform.HOSTED_ONLY.class)
public final class RuntimeForeignAccess {

    /**
     * Registers the provided function descriptor and options pair at image build time for downcalls
     * into foreign code. Required to get a downcall method handle using
     * {@link java.lang.foreign.Linker#downcallHandle} for the same descriptor and options at
     * runtime.
     * <p>
     * Even though this method is weakly typed for compatibility reasons, runtime checks will be
     * performed to ensure that the arguments have the expected type. It will be deprecated in favor
     * of strongly typed variant as soon as possible.
     *
     * @param desc A {@link java.lang.foreign.FunctionDescriptor} to register for downcalls.
     * @param options An array of {@link java.lang.foreign.Linker.Option} used for the downcalls.
     *
     * @since 23.1
     */
    public static void registerForDowncall(Object desc, Object... options) {
        ImageSingletons.lookup(RuntimeForeignAccessSupport.class).registerForDowncall(ConfigurationCondition.alwaysTrue(), desc, options);
    }

    /**
     * Registers the provided function descriptor and options pair at image build time for upcalls
     * from foreign code. Required to get an upcall stub function pointer using
     * {@link java.lang.foreign.Linker#upcallStub} for the same descriptor and options at runtime.
     * <p>
     * Even though this method is weakly typed for compatibility reasons, runtime checks will be
     * performed to ensure that the arguments have the expected type. It will be deprecated in favor
     * of strongly typed variant as soon as possible.
     *
     * @param desc A {@link java.lang.foreign.FunctionDescriptor} to register for upcalls.
     * @param options An array of {@link java.lang.foreign.Linker.Option} used for the upcalls.
     *
     * @since 24.1
     */
    public static void registerForUpcall(Object desc, Object... options) {
        ImageSingletons.lookup(RuntimeForeignAccessSupport.class).registerForUpcall(ConfigurationCondition.alwaysTrue(), desc, options);
    }

    /**
     * Registers a specific static method (denoted by a method handle) as a fast upcall target. This
     * will create a specialized upcall stub that will invoke only the specified method, which is
     * much faster than using {@link #registerForUpcall(Object, Object...)}).
     * <p>
     * The provided method handle must be a direct method handle. Those are most commonly created
     * using {@link java.lang.invoke.MethodHandles.Lookup#findStatic(Class, String, MethodType)}.
     * However, a strict requirement is that it must be possible to create a non-empty descriptor
     * for the method handle using {@link MethodHandle#describeConstable()}. The denoted static
     * method will also be registered for reflective access since run-time code will also create a
     * method handle to denoted static method.
     * </p>
     * <p>
     * Even though this method is weakly typed for compatibility reasons, runtime checks will be
     * performed to ensure that the arguments have the expected type. It will be deprecated in favor
     * of strongly typed variant as soon as possible.
     * </p>
     *
     * @param target A direct method handle denoting a static method.
     * @param desc A {@link java.lang.foreign.FunctionDescriptor} to register for upcalls.
     * @param options An array of {@link java.lang.foreign.Linker.Option} used for the upcalls.
     *
     * @since 24.2
     */
    public static void registerForDirectUpcall(MethodHandle target, Object desc, Object... options) {
        ImageSingletons.lookup(RuntimeForeignAccessSupport.class).registerForDirectUpcall(ConfigurationCondition.alwaysTrue(), target, desc, options);
    }

    private RuntimeForeignAccess() {
    }
}
