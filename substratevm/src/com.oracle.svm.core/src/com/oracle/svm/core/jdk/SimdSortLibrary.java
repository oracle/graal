/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.word.Pointer;

import com.oracle.svm.shared.Uninterruptible;

/**
 * Native entry points for the JDK's statically linked {@code libsimdsort} library.
 * <p>
 * The marker method below registers {@code libm}; {@link CLibrary#dependsOn} orders an already
 * registered dependency but does not register a dynamic library by itself.
 */
@Platforms(Platform.LINUX_AMD64.class)
@CLibrary(value = "simdsort", requireStatic = true, dependsOn = "m")
final class SimdSortLibrary {

    private SimdSortLibrary() {
    }

    @CLibrary("m")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static void requireLibM() {
    }

    @CFunction(value = "avx2_sort", transition = Transition.NO_TRANSITION)
    static native void avx2Sort(Pointer array, int elementType, int fromIndex, int toIndex);

    @CFunction(value = "avx2_partition", transition = Transition.NO_TRANSITION)
    static native void avx2Partition(Pointer array, int elementType, int fromIndex, int toIndex, Pointer pivotIndices, int pivotIndex1, int pivotIndex2);

    @CFunction(value = "avx512_sort", transition = Transition.NO_TRANSITION)
    static native void avx512Sort(Pointer array, int elementType, int fromIndex, int toIndex);

    @CFunction(value = "avx512_partition", transition = Transition.NO_TRANSITION)
    static native void avx512Partition(Pointer array, int elementType, int fromIndex, int toIndex, Pointer pivotIndices, int pivotIndex1, int pivotIndex2);
}
