/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.shared.Uninterruptible;

/**
 * Utilities for reading and writing derived references. Derived references can only occur on the
 * stack or in a {@link StoredContinuation}. They can point inside an object and therefore need to
 * use the correct reference encoding for compressed, heap-base-relative, and absolute references
 * without materializing the value as an object.
 */
public final class DerivedReferenceSupport {
    private DerivedReferenceSupport() {
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static int getReferenceSize(boolean compressed) {
        return compressed ? ObjectLayout.singleton().getReferenceSize() : FrameAccess.uncompressedReferenceSize();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static Pointer readReferenceAsPointer(Pointer referenceSlot, boolean compressed) {
        if (!compressed) {
            return referenceSlot.readWord(0);
        }

        UnsignedWord rawReference = readRawCompressedReference(referenceSlot);
        if (rawReference.equal(0)) {
            return Word.nullPointer();
        }
        return KnownIntrinsics.heapBase().add(rawReference.shiftLeft(ReferenceAccess.singleton().getCompressionShift()));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void writeReference(Pointer referenceSlot, Pointer value, boolean compressed) {
        if (!compressed) {
            referenceSlot.writeWord(0, value);
            return;
        }

        UnsignedWord rawReference = Word.zero();
        if (value.isNonNull()) {
            int compressionShift = ReferenceAccess.singleton().getCompressionShift();
            UnsignedWord uncompressedOffset = value.subtract(KnownIntrinsics.heapBase());
            if (compressionShift != 0) {
                UnsignedWord alignmentMask = Word.unsigned((1L << compressionShift) - 1);
                assert uncompressedOffset.and(alignmentMask).equal(0) : "Cannot encode an unaligned derived reference as a compressed reference.";
            }
            rawReference = uncompressedOffset.unsignedShiftRight(compressionShift);
        }

        if (ObjectLayout.singleton().getReferenceSize() == Integer.BYTES) {
            referenceSlot.writeInt(0, (int) rawReference.rawValue());
        } else {
            assert ObjectLayout.singleton().getReferenceSize() == Long.BYTES;
            referenceSlot.writeWord(0, rawReference);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static UnsignedWord readRawCompressedReference(Pointer referenceSlot) {
        if (ObjectLayout.singleton().getReferenceSize() == Integer.BYTES) {
            return Word.unsigned(referenceSlot.readInt(0) & 0xFFFFFFFFL);
        }

        assert ObjectLayout.singleton().getReferenceSize() == Long.BYTES;
        return referenceSlot.readWord(0);
    }
}
