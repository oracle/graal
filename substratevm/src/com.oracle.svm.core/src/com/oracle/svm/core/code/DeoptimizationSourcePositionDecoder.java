/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.code;

import org.graalvm.compiler.core.common.util.UnsafeArrayTypeReader;
import org.graalvm.compiler.graph.NodeSourcePosition;

import com.oracle.svm.core.util.ByteArrayReader;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class DeoptimizationSourcePositionDecoder {

    static final int NO_SOURCE_POSITION = -1;
    static final int NO_CALLER = 0;

    public static NodeSourcePosition decode(int deoptId, CodeInfoQueryResult codeInfo) {
        if (!(codeInfo.data instanceof RuntimeMethodInfo)) {
            /*
             * We only store the information for runtime compiled code, not for native image code.
             */
            return null;
        }
        RuntimeMethodInfo methodInfo = (RuntimeMethodInfo) codeInfo.data;

        return decode(deoptId, methodInfo.deoptimizationStartOffsets, methodInfo.deoptimizationEncodings, methodInfo.deoptimizationObjectConstants);
    }

    static NodeSourcePosition decode(int deoptId, int[] deoptimizationStartOffsets, byte[] deoptimizationEncodings, Object[] deoptimizationObjectConstants) {
        if (deoptId < 0 || deoptId >= deoptimizationStartOffsets.length) {
            return null;
        }

        int startOffset = deoptimizationStartOffsets[deoptId];
        if (startOffset == NO_SOURCE_POSITION) {
            return null;
        }

        UnsafeArrayTypeReader readBuffer = UnsafeArrayTypeReader.create(deoptimizationEncodings, 0, ByteArrayReader.supportsUnalignedMemoryAccess());
        return decodeSourcePosition(startOffset, deoptimizationObjectConstants, readBuffer);
    }

    private static NodeSourcePosition decodeSourcePosition(long startOffset, Object[] deoptimizationObjectConstants, UnsafeArrayTypeReader readBuffer) {
        readBuffer.setByteIndex(startOffset);
        long callerRelativeOffset = readBuffer.getUV();
        int bci = readBuffer.getSVInt();
        ResolvedJavaMethod method = (ResolvedJavaMethod) deoptimizationObjectConstants[readBuffer.getUVInt()];

        NodeSourcePosition caller = null;
        if (callerRelativeOffset != NO_CALLER) {
            caller = decodeSourcePosition(startOffset - callerRelativeOffset, deoptimizationObjectConstants, readBuffer);
        }

        return new NodeSourcePosition(caller, method, bci);
    }
}
