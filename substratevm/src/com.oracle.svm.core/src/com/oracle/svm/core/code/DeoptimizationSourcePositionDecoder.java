/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.nativeimage.c.function.CodePointer;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.c.NonmovableObjectArray;
import com.oracle.svm.core.util.NonmovableByteArrayTypeReader;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class DeoptimizationSourcePositionDecoder {

    static final int NO_SOURCE_POSITION = -1;
    static final int NO_CALLER = 0;

    @Uninterruptible(reason = "Must prevent the GC from freeing the CodeInfo object.")
    public static NodeSourcePosition decode(int deoptId, CodePointer ip) {
        UntetheredCodeInfo untetheredInfo = CodeInfoTable.lookupCodeInfo(ip);
        if (untetheredInfo.isNull() || untetheredInfo.equal(CodeInfoTable.getImageCodeInfo())) {
            /* We only have information for runtime compiled code, not for native image code. */
            return null;
        }

        Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
        try {
            CodeInfo info = CodeInfoAccess.convert(untetheredInfo, tether);
            return decode0(deoptId, info);
        } finally {
            CodeInfoAccess.releaseTether(untetheredInfo, tether);
        }
    }

    @Uninterruptible(reason = "Wrap the now safe call to interruptibly decode the source position.", calleeMustBe = false)
    private static NodeSourcePosition decode0(int deoptId, CodeInfo info) {
        return decode(deoptId, CodeInfoAccess.getDeoptimizationStartOffsets(info), CodeInfoAccess.getDeoptimizationEncodings(info), CodeInfoAccess.getDeoptimizationObjectConstants(info));
    }

    static NodeSourcePosition decode(int deoptId, NonmovableArray<Integer> deoptimizationStartOffsets,
                    NonmovableArray<Byte> deoptimizationEncodings, NonmovableObjectArray<Object> deoptimizationObjectConstants) {
        if (deoptId < 0 || deoptId >= NonmovableArrays.lengthOf(deoptimizationStartOffsets)) {
            return null;
        }

        int startOffset = NonmovableArrays.getInt(deoptimizationStartOffsets, deoptId);
        if (startOffset == NO_SOURCE_POSITION) {
            return null;
        }

        NonmovableByteArrayTypeReader readBuffer = new NonmovableByteArrayTypeReader(deoptimizationEncodings, 0);
        return decodeSourcePosition(startOffset, deoptimizationObjectConstants, readBuffer);
    }

    private static NodeSourcePosition decodeSourcePosition(long startOffset, NonmovableObjectArray<Object> deoptimizationObjectConstants, NonmovableByteArrayTypeReader readBuffer) {
        readBuffer.setByteIndex(startOffset);
        long callerRelativeOffset = readBuffer.getUV();
        int bci = readBuffer.getSVInt();
        ResolvedJavaMethod method = (ResolvedJavaMethod) NonmovableArrays.getObject(deoptimizationObjectConstants, readBuffer.getUVInt());

        NodeSourcePosition caller = null;
        if (callerRelativeOffset != NO_CALLER) {
            caller = decodeSourcePosition(startOffset - callerRelativeOffset, deoptimizationObjectConstants, readBuffer);
        }

        return new NodeSourcePosition(caller, method, bci);
    }
}
