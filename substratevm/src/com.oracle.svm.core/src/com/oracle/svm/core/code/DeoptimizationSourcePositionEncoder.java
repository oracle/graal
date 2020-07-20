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

    public void encodeAndInstall(List<NodeSourcePosition> deoptSourcePositions, CodeInfo target, ReferenceAdjuster adjuster) {
        addObjectConstants(deoptSourcePositions);
        Object[] encodedObjectConstants = objectConstants.encodeAll(new Object[objectConstants.getLength()]);

        UnsafeArrayTypeWriter encodingBuffer = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());
        EconomicMap<NodeSourcePosition, Long> sourcePositionStartOffsets = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
        NonmovableArray<Integer> deoptStartOffsets = NonmovableArrays.createIntArray(deoptSourcePositions.size());

        encodeSourcePositions(deoptSourcePositions, sourcePositionStartOffsets, deoptStartOffsets, encodingBuffer);
        NonmovableArray<Byte> deoptEncodings = NonmovableArrays.createByteArray(TypeConversion.asS4(encodingBuffer.getBytesWritten()));
        encodingBuffer.toByteBuffer(NonmovableArrays.asByteBuffer(deoptEncodings));

        install(target, deoptStartOffsets, deoptEncodings, encodedObjectConstants, deoptSourcePositions, adjuster);
    }

    @Uninterruptible(reason = "Nonmovable object arrays are not visible to GC until installed in target.")
    private static void install(CodeInfo target, NonmovableArray<Integer> deoptStartOffsets, NonmovableArray<Byte> deoptEncodings,
                    Object[] encodedObjectConstants, List<NodeSourcePosition> deoptSourcePositions, ReferenceAdjuster adjuster) {

        NonmovableObjectArray<Object> deoptObjectConstants = adjuster.copyOfObjectArray(encodedObjectConstants);
        RuntimeCodeInfoAccess.setDeoptimizationMetadata(target, deoptStartOffsets, deoptEncodings, deoptObjectConstants);

        afterInstallation(deoptStartOffsets, deoptEncodings, deoptSourcePositions, deoptObjectConstants, adjuster);
    }

    @Uninterruptible(reason = "Safe for GC, but called from uninterruptible code.", calleeMustBe = false)
    private static void afterInstallation(NonmovableArray<Integer> deoptStartOffsets, NonmovableArray<Byte> deoptEncodings,
                    List<NodeSourcePosition> deoptSourcePositions, NonmovableObjectArray<Object> deoptObjectConstants, ReferenceAdjuster adjuster) {

        assert !adjuster.isFinished() || verifyEncoding(deoptSourcePositions, deoptStartOffsets, deoptEncodings, deoptObjectConstants);
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

    private void encodeSourcePositions(List<NodeSourcePosition> deoptSourcePositions, EconomicMap<NodeSourcePosition, Long> sourcePositionStartOffsets,
                    NonmovableArray<Integer> deoptStartOffsets, UnsafeArrayTypeWriter encodingBuffer) {
        for (int i = 0; i < deoptSourcePositions.size(); i++) {
            NodeSourcePosition sourcePosition = deoptSourcePositions.get(i);
            int startOffset;
            if (sourcePosition == null) {
                startOffset = DeoptimizationSourcePositionDecoder.NO_SOURCE_POSITION;
            } else {
                startOffset = TypeConversion.asS4(encodeSourcePositions(sourcePosition, sourcePositionStartOffsets, encodingBuffer));
                assert startOffset > DeoptimizationSourcePositionDecoder.NO_SOURCE_POSITION;
            }
            NonmovableArrays.setInt(deoptStartOffsets, i, startOffset);
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

    private static boolean verifyEncoding(List<NodeSourcePosition> deoptSourcePositions, NonmovableArray<Integer> deoptStartOffsets,
                    NonmovableArray<Byte> deoptEncodings, NonmovableObjectArray<Object> deoptObjectConstants) {

        for (int i = 0; i < deoptSourcePositions.size(); i++) {
            NodeSourcePosition originalSourcePosition = deoptSourcePositions.get(i);
            NodeSourcePosition decodedSourcePosition = DeoptimizationSourcePositionDecoder.decode(i, deoptStartOffsets, deoptEncodings, deoptObjectConstants);

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
