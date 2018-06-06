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
     * Returns the length of the given array. It does not check if the provided object is an array,
     * so the caller has to check that beforehand.
     */
    public static native int readArrayLength(Object array);

    /**
     * Returns the hub of the given object.
     */
    public static native DynamicHub readHub(Object obj);

    /**
     * Installs the object header and zeros the rest of the object.
     */
    public static native Object formatObject(Pointer memory, Class<?> hub, boolean rememberedSet);

    /**
     * Installs the array header and zeros the rest of the object.
     */
    public static native Object formatArray(Pointer memory, Class<?> hub, int length, boolean rememberedSet, boolean unaligned);

    /**
     * Casts the result to a new static type without any type checking. The caller is responsible
     * for type safety.
     */
    public static native <T> T unsafeCast(Object obj, Class<T> toType);

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
     * Writes the stack pointer. Note that this is very dangerous. You have to know what you are
     * doing.
     */
    public static native void writeStackPointer(Pointer value);

    /**
     * Returns the value of the native instruction pointer.
     */
    public static native CodePointer readInstructionPointer();

    /**
     * Returns the value of the native stack pointer for the physical caller frame.
     */
    public static native Pointer readCallerStackPointer();

    /**
     * Returns the value of the native instruction pointer for the physical caller frame.
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
    public static native void farReturn(Object result, Pointer sp, CodePointer ip);

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
     * This method is a helper for the static analysis. It converts objects that have an unknown
     * value, e.g., objects that resulted from a low level memory read, i.e., word-to-object reads,
     * into proper objects. By default during analysis all objects that result from low level reads
     * are treated as having unknown value. We only convert them to proper objects when they are
     * used as a receiver object in calls, loads, stores, etc. Thus, objects that are never used as
     * proper Java objects, but only passed around as data, e.g., in the GC, will not interfere with
     * the points-to analysis. If an unknown value object is used as a proper object, for example as
     * a receiver for a call, an unsupported feature will be reported.
     *
     * The type parameter will reduce the type of the return value from the all instantiated types
     * to the type subtree of the specified type. If {@code Object.class} is specified no actual
     * type reduction is done.
     *
     * The method has a default implementation which just returns the obj parameter because it can
     * used in places that are reached at runtime (hence analyzed) but also during image building
     * (e.g., SharedConstantReflectionProvider.unboxPrimitive(JavaConstant)).
     *
     * For the analysis case a call to it is intercepted in
     * SubstrateGraphBuilderPlugins.registerKnownIntrinsicsPlugins() and replaced with a
     * ConvertUnknownValueNode which later is processed during analysis type flow graph building.
     **/
    @SuppressWarnings({"unchecked", "unused"})
    public static <T> T convertUnknownValue(Object obj, Class<T> type) {
        return (T) obj;
    }

}
