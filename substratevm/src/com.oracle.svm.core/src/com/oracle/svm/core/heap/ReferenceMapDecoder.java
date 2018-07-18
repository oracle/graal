/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.amd64.FrameAccess;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.util.ByteArrayReader;

public class ReferenceMapDecoder {

    /**
     * Walk the reference map encoding from a Pointer, applying a visitor to each Object reference.
     *
     * @param baseAddress A Pointer to a collections of primitives and Object references.
     * @param referenceMapEncoding The encoding for the Object references in the collection.
     * @param referenceMapIndex The start index for the particular reference map in the encoding.
     * @param visitor The visitor to be applied to each Object reference.
     * @return false if any of the visits returned false, true otherwise.
     */
    @AlwaysInline("de-virtualize calls to ObjectReferenceVisitor")
    public static boolean walkOffsetsFromPointer(PointerBase baseAddress, byte[] referenceMapEncoding, long referenceMapIndex, ObjectReferenceVisitor visitor) {
        assert referenceMapIndex != CodeInfoQueryResult.NO_REFERENCE_MAP;
        assert referenceMapEncoding != null;
        UnsignedWord uncompressedSize = WordFactory.unsigned(FrameAccess.uncompressedReferenceSize());
        UnsignedWord compressedSize = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getReferenceSize());

        Pointer objRef = (Pointer) baseAddress;
        long idx = referenceMapIndex;
        while (true) {
            /*
             * The following code is copied from TypeReader.getUV() and .getSV() because we cannot
             * allocate a TypeReader here which, in addition to returning the read variable-sized
             * values, can keep track of the index in the byte array. Even with an instance of
             * ReusableTypeReader, we would need to worry about this method being reentrant.
             */
            int shift;
            long b;
            // Size of gap in bytes
            long gap = 0;
            shift = 0;
            do {
                b = ByteArrayReader.getU1(referenceMapEncoding, idx);
                gap |= (b & 0x7f) << shift;
                shift += 7;
                idx++;
            } while ((b & 0x80) != 0);
            // Number of pointers (sign distinguishes between compression and uncompression)
            long count = 0;
            shift = 0;
            do {
                b = ByteArrayReader.getU1(referenceMapEncoding, idx);
                count |= (b & 0x7f) << shift;
                shift += 7;
                idx++;
            } while ((b & 0x80) != 0);
            if ((b & 0x40) != 0 && shift < 64) {
                count |= -1L << shift;
            }

            if (gap == 0 && count == 0) {
                break; // reached end of table
            }

            objRef = objRef.add(WordFactory.unsigned(gap));
            boolean compressed = (count < 0);
            UnsignedWord refSize = compressed ? compressedSize : uncompressedSize;
            count = (count < 0) ? -count : count;
            for (long c = 0; c < count; c += 1) {
                final boolean visitResult = visitor.visitObjectReferenceInline(objRef, compressed);
                if (!visitResult) {
                    return false;
                }
                objRef = objRef.add(refSize);
            }
        }
        return true;
    }
}
