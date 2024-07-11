/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core.snippets;

import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.hub.DynamicHub;

/**
 * Functions that are implemented as compiler intrinsics. For implementation see
 * SubstrateGraphBuilderPlugins.registerKnownIntrinsicsPlugins().
 */
public class KnownIntrinsics {

    /**
     * Returns the value of the heap base.
     */
    public static native Pointer heapBase();

    /**
     * Returns the hub of the given object.
     */
    public static native DynamicHub readHub(Object obj);

    /**
     * Narrow down the range of values to exclude 0 as the possible pointer value.
     *
     * @param pointer that we are narrowing to non-null
     * @return a pointer with stamp non-null
     */
    public static native Pointer nonNullPointer(Pointer pointer);

    /**
     * Returns the value of the native stack pointer.
     */
    public static native Pointer readStackPointer();

    /**
     * Returns the value of the native stack pointer for the physical caller frame.
     *
     * The caller of this method must be annotated with {@link NeverInline} to ensure that the
     * physical caller frame is deterministic.
     */
    public static native Pointer readCallerStackPointer();

    /**
     * Returns the value of the native instruction pointer for the physical caller frame.
     *
     * The caller of this method must be annotated with {@link NeverInline} to ensure that the
     * physical caller frame is deterministic.
     */
    public static native CodePointer readReturnAddress();

    /**
     * Continues execution in the specified caller frame, at the specified instruction pointer. The
     * result is placed in the register used for object results, simulating the return from a method
     * with an Object return type.
     *
     * Note that this is very dangerous. You have to know what you are doing. The parameters are not
     * checked for correctness in any way.
     */
    public static native void farReturn(Object result, Pointer sp, CodePointer ip, boolean fromMethodWithCalleeSavedRegisters);

    /**
     * For deoptimization testing only. Performs a deoptimization in a regular method, but is a
     * no-op in a deoptimization target method.
     */
    public static native void testDeoptimize();

    /**
     * For deoptimization testing only. Folds to <code>true</code> in a deoptimization target method
     * and to <code>false</code> in a deoptimization source method.
     */
    public static native boolean isDeoptimizationTarget();

    /**
     * Casts the given object to the exact class represented by {@code clazz}. The cast succeeds
     * only if {@code object == null || object.getClass() == clazz} and thus fails for any subclass.
     *
     * @param object the object to be cast
     * @param clazz the class to check against, must not be null
     * @return the object after casting
     * @throws ClassCastException if the object is non-null and not exactly of the given class
     * @throws NullPointerException if the class argument is null
     * @see Class#cast(Object)
     */
    public static native <T> T castExact(Object object, Class<T> clazz);

    /**
     * Like {@link jdk.internal.misc.Unsafe#allocateInstance} but without the checks that the class
     * is an instance class, without the checks that the class was registered for unsafe allocation
     * using the reflection configuration, without checks that the class was seen as instantiated by
     * the static analysis, and without the check that the class is already initialized.
     */
    public static native Object unvalidatedAllocateInstance(Class<?> hub);
}
