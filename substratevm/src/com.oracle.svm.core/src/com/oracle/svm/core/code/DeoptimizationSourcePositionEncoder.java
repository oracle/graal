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

import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.core.common.util.FrequencyEncoder;
import org.graalvm.compiler.core.common.util.TypeConversion;
import org.graalvm.compiler.core.common.util.UnsafeArrayTypeWriter;
import org.graalvm.compiler.graph.NodeSourcePosition;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.c.NonmovableObjectArray;
import com.oracle.svm.core.util.ByteArrayReader;

public class DeoptimizationSourcePositionEncoder {

    private final FrequencyEncoder<Object> objectConstants;

    public DeoptimizationSourcePositionEncoder() {
        this.objectConstants = FrequencyEncoder.createIdentityEncoder();
    }

    public void encodeAndInstall(List<NodeSourcePosition> deoptimzationSourcePositions, CodeInfo target) {
        addObjectConstants(deoptimzationSourcePositions);
        Object[] encodedObjectConstants = objectConstants.encodeAll(new Object[objectConstants.getLength()]);

        UnsafeArrayTypeWriter encodingBuffer = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());
        EconomicMap<NodeSourcePosition, Long> sourcePositionStartOffsets = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
        NonmovableArray<Integer> deoptimizationStartOffsets = NonmovableArrays.createIntArray(deoptimzationSourcePositions.size());

        encodeSourcePositions(deoptimzationSourcePositions, sourcePositionStartOffsets, deoptimizationStartOffsets, encodingBuffer);
        NonmovableArray<Byte> deoptimizationEncodings = NonmovableArrays.createByteArray(TypeConversion.asS4(encodingBuffer.getBytesWritten()));
        encodingBuffer.toByteBuffer(NonmovableArrays.asByteBuffer(deoptimizationEncodings));

        install(target, deoptimizationStartOffsets, deoptimizationEncodings, encodedObjectConstants, deoptimzationSourcePositions);
    }

    @Uninterruptible(reason = "Nonmovable object arrays are not visible to GC until installed in target.")
    private static void install(CodeInfo target, NonmovableArray<Integer> deoptimizationStartOffsets, NonmovableArray<Byte> deoptimizationEncodings,
                    Object[] encodedObjectConstants, List<NodeSourcePosition> deoptimizationSourcePositions) {

        NonmovableObjectArray<Object> deoptimizationObjectConstants = NonmovableArrays.copyOfObjectArray(encodedObjectConstants);
        RuntimeMethodInfoAccess.setDeoptimizationMetadata(target, deoptimizationStartOffsets, deoptimizationEncodings, deoptimizationObjectConstants);

        afterInstallation(deoptimizationStartOffsets, deoptimizationEncodings, deoptimizationSourcePositions, deoptimizationObjectConstants);
    }

    @Uninterruptible(reason = "Safe for GC, but called from uninterruptible code.", calleeMustBe = false)
    private static void afterInstallation(NonmovableArray<Integer> deoptimizationStartOffsets, NonmovableArray<Byte> deoptimizationEncodings,
                    List<NodeSourcePosition> deoptimizationSourcePositions, NonmovableObjectArray<Object> deoptimizationObjectConstants) {

        verifyEncoding(deoptimizationSourcePositions, deoptimizationStartOffsets, deoptimizationEncodings, deoptimizationObjectConstants);
    }

    private void addObjectConstants(List<NodeSourcePosition> deoptimzationSourcePositions) {
        EconomicSet<NodeSourcePosition> processedPositions = EconomicSet.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
        for (NodeSourcePosition sourcePosition : deoptimzationSourcePositions) {
            addObjectConstants(sourcePosition, processedPositions);
        }
    }

    private void addObjectConstants(NodeSourcePosition sourcePosition, EconomicSet<NodeSourcePosition> processedPositions) {
        if (sourcePosition == null || processedPositions.contains(sourcePosition)) {
            return;
        }

        addObjectConstants(sourcePosition.getCaller(), processedPositions);
        objectConstants.addObject(sourcePosition.getMethod());
        processedPositions.add(sourcePosition);
    }

    private void encodeSourcePositions(List<NodeSourcePosition> deoptimzationSourcePositions, EconomicMap<NodeSourcePosition, Long> sourcePositionStartOffsets,
                    NonmovableArray<Integer> deoptimizationStartOffsets, UnsafeArrayTypeWriter encodingBuffer) {
        for (int i = 0; i < deoptimzationSourcePositions.size(); i++) {
            NodeSourcePosition sourcePosition = deoptimzationSourcePositions.get(i);
            int startOffset;
            if (sourcePosition == null) {
                startOffset = DeoptimizationSourcePositionDecoder.NO_SOURCE_POSITION;
            } else {
                startOffset = TypeConversion.asS4(encodeSourcePositions(sourcePosition, sourcePositionStartOffsets, encodingBuffer));
                assert startOffset > DeoptimizationSourcePositionDecoder.NO_SOURCE_POSITION;
            }
            NonmovableArrays.setInt(deoptimizationStartOffsets, i, startOffset);
        }
    }

    private long encodeSourcePositions(NodeSourcePosition sourcePosition, EconomicMap<NodeSourcePosition, Long> sourcePositionStartOffsets, UnsafeArrayTypeWriter encodingBuffer) {
        Long existingAbsoluteOffset = sourcePositionStartOffsets.get(sourcePosition);
        if (existingAbsoluteOffset != null) {
            return existingAbsoluteOffset;
        }

        long callerAbsoluteOffset = -1;
        if (sourcePosition.getCaller() != null) {
            callerAbsoluteOffset = encodeSourcePositions(sourcePosition.getCaller(), sourcePositionStartOffsets, encodingBuffer);
        }

        long startAbsoluteOffset = encodingBuffer.getBytesWritten();

        long callerRelativeOffset = DeoptimizationSourcePositionDecoder.NO_CALLER;
        if (sourcePosition.getCaller() != null) {
            callerRelativeOffset = startAbsoluteOffset - callerAbsoluteOffset;
            assert callerRelativeOffset > DeoptimizationSourcePositionDecoder.NO_CALLER;
        }

        encodingBuffer.putUV(callerRelativeOffset);
        encodingBuffer.putSV(sourcePosition.getBCI());
        encodingBuffer.putUV(objectConstants.getIndex(sourcePosition.getMethod()));

        sourcePositionStartOffsets.put(sourcePosition, startAbsoluteOffset);
        return startAbsoluteOffset;
    }

    private static boolean verifyEncoding(List<NodeSourcePosition> deoptimzationSourcePositions, NonmovableArray<Integer> deoptimizationStartOffsets,
                    NonmovableArray<Byte> deoptimizationEncodings, NonmovableObjectArray<Object> deoptimizationObjectConstants) {
        for (int i = 0; i < deoptimzationSourcePositions.size(); i++) {
            NodeSourcePosition originalSourcePosition = deoptimzationSourcePositions.get(i);
            NodeSourcePosition decodedSourcePosition = DeoptimizationSourcePositionDecoder.decode(i, deoptimizationStartOffsets, deoptimizationEncodings, deoptimizationObjectConstants);

            verifySourcePosition(originalSourcePosition, decodedSourcePosition);
        }
        return true;
    }

    private static void verifySourcePosition(NodeSourcePosition originalPosition, NodeSourcePosition decodedSourcePosition) {
        if (originalPosition == null) {
            assert decodedSourcePosition == null;
            return;
        }

        assert originalPosition.getBCI() == decodedSourcePosition.getBCI();
        assert originalPosition.getMethod().equals(decodedSourcePosition.getMethod());
        verifySourcePosition(originalPosition.getCaller(), decodedSourcePosition.getCaller());
    }
}
