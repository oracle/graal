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
package org.graalvm.compiler.core.common;

import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.debug.GraalError;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaKind;

import static jdk.vm.ci.code.ValueUtil.isIllegal;

/**
 * This class provides utility methods for "stride-agnostic" intrinsic nodes such as
 * {@code org.graalvm.compiler.replacements.nodes.ArrayRegionCompareToNode}. These intrinsics may
 * work with fixed strides of 1, 2 or 4 bytes per element, or with a dynamic parameter containing
 * the stride in log2 format, i.e. {@code 0 -> 1 byte, 1 -> 2 byte, 2 -> 4 byte}. If an intrinsic
 * method has two array parameters with potentially different strides, both log2 stride values are
 * encoded into one as follows: {@code (strideA * N_STRIDES) + strideB}. This value is used inside
 * the intrinsic as a jump table index to dispatch to separately compiled versions for each possible
 * combination of strides.
 */
public final class StrideUtil {
    /**
     * Number of possible values of a single stride parameter. Currently, possible values are 0, 1,
     * and 2.
     */
    public static final int N_STRIDES = 3;
    /**
     * Short aliases for intrinsics that take {@link JavaKind} parameters to describe a stride in
     * bytes. The naming is "S" for "stride", followed by the stride width in bytes.
     */
    public static final JavaKind S1 = JavaKind.Byte;
    public static final JavaKind S2 = JavaKind.Char;
    public static final JavaKind S4 = JavaKind.Int;
    /**
     * Alias for intrinsics that take a {@link JavaKind} parameter to declare the type of an array
     * parameter. This parameter "NONE" means that the array type is unknown or a native buffer has
     * been used instead.
     */
    public static final JavaKind NONE = JavaKind.Void;

    /**
     * Extract {@code strideA} from {@code directStubCallIndex}.
     */
    public static JavaKind getConstantStrideA(int directStubCallIndex) {
        return log2ToStride(directStubCallIndex / N_STRIDES);
    }

    /**
     * Extract {@code strideB} from {@code directStubCallIndex}.
     */
    public static JavaKind getConstantStrideB(int directStubCallIndex) {
        return log2ToStride(directStubCallIndex % N_STRIDES);
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
     * Get the given stride's log2 form.
     */
    public static int log2(JavaKind stride) {
        return CodeUtil.log2(stride.getByteCount());
    }

    /**
     * Convert the given log2 stride value to a {@link JavaKind} value.
     */
    public static JavaKind log2ToStride(int log2Stride) {
        switch (log2Stride) {
            case 0:
                return S1;
            case 1:
                return S2;
            case 2:
                return S4;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    /**
     * Returns {@code true} if the {@code dynamicStride} parameter is unused, which implies constant
     * strides must be used instead.
     */
    public static boolean useConstantStrides(Value dynamicStride) {
        return isIllegal(dynamicStride);
    }
}
