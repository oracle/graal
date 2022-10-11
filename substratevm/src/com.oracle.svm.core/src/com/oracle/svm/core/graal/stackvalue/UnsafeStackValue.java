/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.stackvalue;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.Uninterruptible;

/**
 * {@link StackValue} without runtime checks for virtual threads.
 * <p>
 * Using stack allocation in a virtual thread that can migrate to another carrier thread is
 * dangerous because the thread retains the pointer to the stack memory from the original carrier
 * thread, which results in illegal memory accesses that are hard to debug. Threads migrate because
 * of synchronization, sleeping, waiting for I/O, or {@link Thread#yield()}. For that reason,
 * {@link StackValue} checks whether it is being used in a virtual thread, and if so, throws.
 * <p>
 * Stack allocation can still be needed in virtual threads and the safest way to use it is to
 * annotate methods as {@link Uninterruptible}, but that comes with restrictions which can be too
 * impractical. {@link UnsafeStackValue} permits allocating stack values without runtime checks and
 * should be used with caution
 * <li>in methods which can be expected to execute in a virtual thread context, which includes
 * safepoint operations such as garbage collection or JNI functions
 * <li>with the stack memory being used for as short as possible (e.g. for a single native call)
 * <li>without calling methods of other classes, but especially not user code, callbacks or
 * overridable methods which could cause a virtual thread to migrate (or later evolve to do so)
 * <li>when the method is not already {@link Uninterruptible}.
 * <p>
 * In the future, virtual threads could support preemption at safepoints, in which case restrictions
 * for stack allocation in virtual threads could tighten.
 */
public final class UnsafeStackValue {

    private UnsafeStackValue() {
    }

    @SuppressWarnings("unused")
    public static <T extends PointerBase> T get(Class<T> structType) {
        throw new IllegalStateException("Cannot invoke method during native image generation");
    }

    @SuppressWarnings("unused")
    public static <T extends PointerBase> T get(int numberOfElements, Class<T> structType) {
        throw new IllegalStateException("Cannot invoke method during native image generation");
    }

    @SuppressWarnings("unused")
    public static <T extends PointerBase> T get(int size) {
        throw new IllegalStateException("Cannot invoke method during native image generation");
    }

    @SuppressWarnings("unused")
    public static <T extends PointerBase> T get(int numberOfElements, int elementSize) {
        throw new IllegalStateException("Cannot invoke method during native image generation");
    }
}
