/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.common;

import static jdk.vm.ci.code.ValueUtil.isIllegal;

import jdk.vm.ci.meta.Value;

/**
 * This class provides utility methods for "stride-agnostic" intrinsic nodes such as
 * {@link jdk.graal.compiler.replacements.nodes.ArrayRegionCompareToNode}. These intrinsics may work
 * with fixed strides of 1, 2 or 4 bytes per element, or with a dynamic parameter containing the
 * stride in log2 format, i.e. {@code 0 -> 1 byte, 1 -> 2 byte, 2 -> 4 byte}. If an intrinsic method
 * has two array parameters with potentially different strides, both log2 stride values are encoded
 * into one as follows: {@code (strideA * N_STRIDES) + strideB}. This value is used inside the
 * intrinsic as a jump table index to dispatch to separately compiled versions for each possible
 * combination of strides.
 */
public final class StrideUtil {
    /**
     * Number of possible values of a single stride parameter. Currently, possible values are 0, 1,
     * and 2. Note that {@link Stride#S8} is not supported.
     */
    public static final int N_STRIDES = 3;

    /**
     * Extract {@code strideA} from {@code directStubCallIndex}.
     */
    public static Stride getConstantStrideA(int directStubCallIndex) {
        return Stride.fromLog2(directStubCallIndex / N_STRIDES);
    }

    /**
     * Extract {@code strideB} from {@code directStubCallIndex}.
     */
    public static Stride getConstantStrideB(int directStubCallIndex) {
        return Stride.fromLog2(directStubCallIndex % N_STRIDES);
    }

    /**
     * Compute the jump table index for two given strides {@code strideA} and {@code strideB}.
     */
    public static int getDirectStubCallIndex(Stride strideA, Stride strideB) {
        return getDirectStubCallIndex(strideA.log2, strideB.log2);
    }

    /**
     * Encode the given stride values into one direct stub call index.
     */
    public static int getDirectStubCallIndex(int log2StrideA, int log2StrideB) {
        assert 0 <= log2StrideA && log2StrideA < N_STRIDES : log2StrideA;
        assert 0 <= log2StrideB && log2StrideB < N_STRIDES : log2StrideB;
        return (log2StrideA * N_STRIDES) + log2StrideB;
    }

    /**
     * Returns {@code true} if the {@code dynamicStride} parameter is unused, which implies constant
     * strides must be used instead.
     */
    public static boolean useConstantStrides(Value dynamicStride) {
        return isIllegal(dynamicStride);
    }
}
