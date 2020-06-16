/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.util.UnsafeArrayTypeReader;
import org.graalvm.compiler.core.common.util.UnsafeArrayTypeWriter;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.DuplicatedInNativeCode;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.util.NonmovableByteArrayReader;

@DuplicatedInNativeCode
public class CodeReferenceMapDecoder {

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
    public static boolean walkOffsetsFromPointer(PointerBase baseAddress, NonmovableArray<Byte> referenceMapEncoding, long referenceMapIndex, ObjectReferenceVisitor visitor) {
        assert referenceMapIndex != CodeInfoQueryResult.NO_REFERENCE_MAP;
        assert referenceMapEncoding.isNonNull();
        UnsignedWord uncompressedSize = WordFactory.unsigned(FrameAccess.uncompressedReferenceSize());
        UnsignedWord compressedSize = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getReferenceSize());

        Pointer objRef = (Pointer) baseAddress;
        long idx = referenceMapIndex;
        boolean firstRun = true;
        while (true) {
            /*
             * The following code is copied from TypeReader.getUV() and .getSV() because we cannot
             * allocate a TypeReader here which, in addition to returning the read variable-sized
             * values, can keep track of the index in the byte array. Even with an instance of
             * ReusableTypeReader, we would need to worry about this method being reentrant.
             */

            // Size of gap in bytes (negative means the next pointer has derived pointers)
            long gap = NonmovableByteArrayReader.getU1(referenceMapEncoding, idx++);
            if (gap >= UnsafeArrayTypeWriter.NUM_LOW_CODES) {
                long shift = UnsafeArrayTypeWriter.HIGH_WORD_SHIFT;
                for (int i = 2;; i++) {
                    long b = NonmovableByteArrayReader.getU1(referenceMapEncoding, idx++);
                    gap += b << shift;
                    if (b < UnsafeArrayTypeWriter.NUM_LOW_CODES || i == UnsafeArrayTypeWriter.MAX_BYTES) {
                        break;
                    }
                    shift += UnsafeArrayTypeWriter.HIGH_WORD_SHIFT;
                }
            }
            gap = UnsafeArrayTypeReader.decodeSign(gap);

            // Number of pointers (sign distinguishes between compression and uncompression)
            long count = NonmovableByteArrayReader.getU1(referenceMapEncoding, idx++);
            if (count >= UnsafeArrayTypeWriter.NUM_LOW_CODES) {
                long shift = UnsafeArrayTypeWriter.HIGH_WORD_SHIFT;
                for (int i = 2;; i++) {
                    long b = NonmovableByteArrayReader.getU1(referenceMapEncoding, idx++);
                    count += b << shift;
                    if (b < UnsafeArrayTypeWriter.NUM_LOW_CODES || i == UnsafeArrayTypeWriter.MAX_BYTES) {
                        break;
                    }
                    shift += UnsafeArrayTypeWriter.HIGH_WORD_SHIFT;
                }
            }
            count = UnsafeArrayTypeReader.decodeSign(count);

            if (gap == 0 && count == 0) {
                break; // reached end of table
            }

            boolean derived = false;
            if (!firstRun && gap < 0) {
                /* Derived pointer run */
                gap = -(gap + 1);
                derived = true;
            }
            firstRun = false;

            objRef = objRef.add(WordFactory.unsigned(gap));
            boolean compressed = (count < 0);
            UnsignedWord refSize = compressed ? compressedSize : uncompressedSize;
            count = (count < 0) ? -count : count;

            if (derived) {
                /*
                 * To correctly relocate a derived pointer, we need to know the value pointed to by
                 * the base reference and the derived reference before either one is relocated. This
                 * allows us to compute the inner offset, i.e. how much into the actual object does
                 * the derived reference point to.
                 */
                Pointer basePtr = baseAddress.isNull() ? objRef : objRef.readWord(0);

                final boolean visitResult = visitor.visitObjectReferenceInline(objRef, 0, compressed);
                if (!visitResult) {
                    return false;
                }

                /* count in this case is the number of derived references for this base pointer */
                for (long d = 0; d < count; d++) {
                    /* Offset in words from the base reference to the derived reference */
                    long refOffset = NonmovableByteArrayReader.getU1(referenceMapEncoding, idx++);
                    if (refOffset >= UnsafeArrayTypeWriter.NUM_LOW_CODES) {
                        long shift = UnsafeArrayTypeWriter.HIGH_WORD_SHIFT;
                        for (int i = 2;; i++) {
                            long b = NonmovableByteArrayReader.getU1(referenceMapEncoding, idx++);
                            refOffset += b << shift;
                            if (b < UnsafeArrayTypeWriter.NUM_LOW_CODES || i == UnsafeArrayTypeWriter.MAX_BYTES) {
                                break;
                            }
                            shift += UnsafeArrayTypeWriter.HIGH_WORD_SHIFT;
                        }
                    }
                    refOffset = UnsafeArrayTypeReader.decodeSign(refOffset);

                    Pointer derivedRef;
                    if (refOffset >= 0) {
                        derivedRef = objRef.add(WordFactory.unsigned(refOffset).multiply(refSize));
                    } else {
                        derivedRef = objRef.subtract(WordFactory.unsigned(-refOffset).multiply(refSize));
                    }

                    Pointer derivedPtr = baseAddress.isNull() ? derivedRef : derivedRef.readWord(0);
                    int innerOffset = NumUtil.safeToInt(derivedPtr.subtract(basePtr).rawValue());

                    final boolean derivedVisitResult = visitor.visitObjectReferenceInline(derivedRef, innerOffset, compressed);
                    if (!derivedVisitResult) {
                        return false;
                    }
                }
                objRef = objRef.add(refSize);
            } else {
                for (long c = 0; c < count; c += 1) {
                    final boolean visitResult = visitor.visitObjectReferenceInline(objRef, 0, compressed);
                    if (!visitResult) {
                        return false;
                    }
                    objRef = objRef.add(refSize);
                }
            }
        }
        return true;
    }
}
