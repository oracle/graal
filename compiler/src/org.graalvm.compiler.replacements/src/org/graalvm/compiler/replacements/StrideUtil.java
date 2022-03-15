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
package org.graalvm.compiler.replacements;

import static org.graalvm.compiler.replacements.ArrayIndexOf.S1;
import static org.graalvm.compiler.replacements.ArrayIndexOf.S2;
import static org.graalvm.compiler.replacements.ArrayIndexOf.S4;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.ValueNode;

import jdk.vm.ci.meta.JavaKind;

/**
 * This class provides utility methods for "stride-agnostic" intrinsic nodes such as
 * {@link org.graalvm.compiler.replacements.nodes.ArrayRegionCompareToNode}. These intrinsics may
 * work with fixed strides of 1, 2 or 4 bytes per element, or with a dynamic parameter containing
 * the stride in log2 format, i.e. {@code 0 -> 1 byte, 1 -> 2 byte, 2 -> 4 byte}. If an intrinsic
 * method has two array parameters with potentially different strides, both log2 stride values are
 * encoded into one as follows: {@code (strideA * 3) + strideB}. This value is used inside the
 * intrinsic as a jump table index to dispatch to separately compiled versions for each possible
 * combination of strides.
 */
public final class StrideUtil {

    /**
     * If the constant stride parameter {@code strideA} is non-null, return it. Otherwise, extract
     * {@code strideA} from the constant direct stub call index value {@code stride}.
     */
    public static JavaKind getStrideA(ValueNode stride, JavaKind strideA) {
        if (strideA != null) {
            return strideA;
        }
        return getStrideA(stride.asJavaConstant().asInt());
    }

    /**
     * Extract {@code strideA} from {@code directStubCallIndex}.
     */
    public static JavaKind getStrideA(int directStubCallIndex) {
        return log2ToStride(directStubCallIndex / 3);
    }

    /**
     * If the constant stride parameter {@code strideB} is non-null, return it. Otherwise, extract
     * {@code strideB} from the constant direct stub call index value {@code stride}.
     */
    public static JavaKind getStrideB(ValueNode stride, JavaKind strideB) {
        if (strideB != null) {
            return strideB;
        }
        return getStrideB(stride.asJavaConstant().asInt());
    }

    /**
     * Extract {@code strideB} from {@code directStubCallIndex}.
     */
    public static JavaKind getStrideB(int directStubCallIndex) {
        return log2ToStride(directStubCallIndex % 3);
    }

    /**
     * If the constant stride parameters {@code strideA} and {@code strideB} are non-null, construct
     * a direct stub call index from them. Otherwise, return the direct stub call index contained in
     * the constant value {@code stride}.
     */
    public static int getDirectStubCallIndex(ValueNode stride, JavaKind strideA, JavaKind strideB) {
        if (strideA != null && strideB != null) {
            return (log2(strideA) * 3) + log2(strideB);
        }
        if (stride != null && stride.isJavaConstant()) {
            return stride.asJavaConstant().asInt();
        }
        return -1;
    }

    private static int log2(JavaKind stride) {
        return (Integer.SIZE - 1) - Integer.numberOfLeadingZeros(stride.getByteCount());
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
}
