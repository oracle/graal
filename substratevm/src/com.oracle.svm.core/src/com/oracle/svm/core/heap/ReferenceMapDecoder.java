/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.svm.core.heap;

import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.util.ByteArrayReader;

public class ReferenceMapDecoder {

    protected static final int GAP_END_OF_TABLE = 0xFF;

    /**
     * Walk the reference map encoding from a Pointer, applying a visitor to each Object reference.
     *
     * @param baseAddress A Pointer to a collections of primitives and Object references.
     * @param referenceMapEncoding The encoding for the Object references in the collection.
     * @param referenceMapIndex The start index for the particular reference map in the encoding.
     * @param visitor The ObjectRefernceVisitor to be applied to each Object reference.
     * @return false if any of the visits returned false, true otherwise.
     */
    @AlwaysInline("de-virtualize calls to ObjectReferenceVisitor")
    public static boolean walkOffsetsFromPointer(PointerBase baseAddress, byte[] referenceMapEncoding, long referenceMapIndex, ObjectReferenceVisitor visitor) {
        assert referenceMapIndex != CodeInfoQueryResult.NO_REFERENCE_MAP;
        assert referenceMapEncoding != null;
        UnsignedWord uncompressedSize = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getReferenceSize());
        UnsignedWord compressedSize = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getCompressedReferenceSize());
        UnsignedWord slotSize = WordFactory.unsigned(SubstrateReferenceMap.getSlotSizeInBytes());

        Pointer objRef = (Pointer) baseAddress;
        long idx = referenceMapIndex;
        while (true) {
            int gap = ByteArrayReader.getU1(referenceMapEncoding, idx);
            if (gap == GAP_END_OF_TABLE) {
                break;
            }
            int count = ByteArrayReader.getS1(referenceMapEncoding, idx + 1);

            // Skip a gap.
            objRef = objRef.add(slotSize.multiply(gap));
            // Visit the offsets.
            boolean compressed = (count < 0);
            UnsignedWord refSize = compressed ? compressedSize : uncompressedSize;
            count = (count < 0) ? -count : count;
            for (int c = 0; c < count; c += 1) {
                final boolean visitResult = visitor.visitObjectReferenceInline(objRef, compressed);
                if (!visitResult) {
                    return false;
                }
                objRef = objRef.add(refSize);
            }

            idx += 2;
        }
        return true;
    }
}
