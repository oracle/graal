/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.gc.shenandoah;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.replacements.ReplacementsUtil;
import jdk.graal.compiler.word.ObjectAccess;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.code.CodeUtil;

/** The object header consists of a 32/64 bit mark word and a 32 bit hub pointer. */
public class ShenandoahObjectHeader extends ObjectHeader {
    private final int numAlignmentBits;
    private final int hubBits;
    private final int numReservedHeaderBits;
    private final int numReservedHubBits;

    @Platforms(Platform.HOSTED_ONLY.class)
    public ShenandoahObjectHeader() {
        numAlignmentBits = CodeUtil.log2(ConfigurationValues.getObjectLayout().getAlignment());
        if (useCompressedReferences()) {
            /*
             * Use 27 bits for the hub pointer, and 37 bits for the mark word and other VM-internal
             * data. This limits the address space where all hubs must be placed to 1024 MB.
             */
            hubBits = 27;
            numReservedHeaderBits = 37;
            numReservedHubBits = 5;
        } else {
            /*
             * No reserved bits (64-bit mark word, 32-bit hub pointer). The hub pointer is not
             * compressed, which limits the address space where all hubs must be placed to 4096 MB.
             */
            hubBits = 32;
            numReservedHeaderBits = 0;
            numReservedHubBits = 0;
        }
    }

    @Fold
    public static ShenandoahObjectHeader get() {
        return (ShenandoahObjectHeader) ShenandoahHeap.get().getObjectHeader();
    }

    @Fold
    public static int getMarkWordOffset() {
        return 0;
    }

    @Override
    public int getReservedHubBitsMask() {
        return (1 << numReservedHubBits) - 1;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private long getReservedHeaderBitsMask() {
        return (1L << numReservedHeaderBits) - 1;
    }

    @Override
    public Word encodeAsTLABObjectHeader(DynamicHub hub) {
        return encodeAsObjectHeader(hub);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private Word encodeAsObjectHeader(DynamicHub hub) {
        Word heapBaseRelativeAddress = Word.objectToUntrackedPointer(hub).subtract(KnownIntrinsics.heapBase());
        assertInHubAddressSpace(heapBaseRelativeAddress);

        if (useCompressedReferences()) {
            Word result = heapBaseRelativeAddress.shiftLeft(numReservedHeaderBits - numAlignmentBits);
            assertReservedHeaderBitsZero(result);
            return result;
        }
        return heapBaseRelativeAddress;
    }

    @Override
    public long encodeAsTLABObjectHeader(long hubOffsetFromHeapBase) {
        if (useCompressedReferences()) {
            return hubOffsetFromHeapBase << (numReservedHeaderBits - numAlignmentBits);
        } else {
            return hubOffsetFromHeapBase;
        }
    }

    @Override
    public int constantHeaderSize() {
        return useCompressedReferences() ? Long.BYTES : Integer.BYTES;
    }

    @Override
    public long encodeHubPointerForImageHeap(ImageHeapObject obj, long hubOffsetFromHeapBase) {
        assert isInHubAddressSpace(hubOffsetFromHeapBase) : hubOffsetFromHeapBase;

        if (useCompressedReferences()) {
            long result = hubOffsetFromHeapBase << (numReservedHubBits - numAlignmentBits);
            assert (result & getReservedHubBitsMask()) == 0 : "all reserved bits must be zero";
            return result;
        }
        return hubOffsetFromHeapBase;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void verifyDynamicHubOffset(long offsetFromHeapBase) {
        if (!isInHubAddressSpace(offsetFromHeapBase)) {
            throw VMError.shouldNotReachHere("Hub is too far from heap base for encoding in object header");
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @Override
    public Word readHeaderFromPointer(Pointer objectPointer) {
        if (useCompressedReferences()) {
            return objectPointer.readWord(getMarkWordOffset());
        }
        return Word.unsigned(objectPointer.readInt(getHubOffset()));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @Override
    public Word readHeaderFromObject(Object o) {
        if (useCompressedReferences()) {
            return ObjectAccess.readWord(o, getMarkWordOffset());
        }
        return Word.unsigned(ObjectAccess.readInt(o, getHubOffset()));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @Override
    public Pointer extractPotentialDynamicHubFromHeader(Word header) {
        if (useCompressedReferences()) {
            UnsignedWord hubPart = header.unsignedShiftRight(numReservedHeaderBits);
            UnsignedWord baseRelativeBits = hubPart.shiftLeft(numAlignmentBits);
            return KnownIntrinsics.heapBase().add(baseRelativeBits);
        }
        return header.add(KnownIntrinsics.heapBase());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @Override
    public Word encodeAsUnmanagedObjectHeader(DynamicHub hub) {
        return encodeAsObjectHeader(hub);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @AlwaysInline(INLINE_INITIALIZE_HEADER_INIT_REASON)
    @Override
    protected void initializeObjectHeader(Pointer objectPointer, Word objectHeader, boolean isArrayLike, MemWriter writer) {
        if (useCompressedReferences()) {
            /* Write the mark word and the hub ptr as a single 64-bit value. */
            assertReservedHeaderBitsZero(objectHeader);
            writer.writeWord(objectPointer, getMarkWordOffset(), objectHeader);
        } else {
            /* objectHeader only stores the uncompressed, 4 byte hub pointer. */
            assertInHubAddressSpace(objectHeader);
            writer.writeWord(objectPointer, getMarkWordOffset(), Word.zero());
            writer.writeInt(objectPointer, getHubOffset(), (int) objectHeader.rawValue());
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @Override
    public boolean hasOptionalIdentityHashField(Word header) {
        if (GraalDirectives.inIntrinsic()) {
            ReplacementsUtil.staticAssert(false, "all objects have the identity hash code field in the object header");
            return false;
        } else {
            throw VMError.shouldNotReachHereAtRuntime();
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @Override
    public boolean hasIdentityHashFromAddress(Word header) {
        ReplacementsUtil.staticAssert(false, "all objects have the identity hash code field in the object header");
        return false;
    }

    @Uninterruptible(reason = "Prevent a GC interfering with the object's identity hash state.", callerMustBe = true)
    @Override
    public void setIdentityHashFromAddress(Pointer ptr, Word currentHeader) {
        ReplacementsUtil.staticAssert(false, "identity hash codes are never computed from addresses");
    }

    @AlwaysInline("Otherwise, there is no guarantee that this is optimized away if assertions are disabled.")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void assertReservedHeaderBitsZero(Word objectHeader) {
        if (!GraalDirectives.inIntrinsic()) {
            assert areReservedHeaderBitsZero(objectHeader) : "all reserved bits must be zero";
        } else if (ReplacementsUtil.REPLACEMENTS_ASSERTIONS_ENABLED) {
            ReplacementsUtil.dynamicAssert(areReservedHeaderBitsZero(objectHeader), "all reserved bits must be zero");
        }
    }

    @AlwaysInline("Otherwise, there is no guarantee that this is optimized away if assertions are disabled.")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void assertInHubAddressSpace(Word heapBaseRelativeAddress) {
        if (!GraalDirectives.inIntrinsic()) {
            assert isInHubAddressSpace(heapBaseRelativeAddress.rawValue()) : "must be in hub-specific address space";
        } else if (ReplacementsUtil.REPLACEMENTS_ASSERTIONS_ENABLED) {
            ReplacementsUtil.dynamicAssert(isInHubAddressSpace(heapBaseRelativeAddress.rawValue()), "must be in hub-specific address space");
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private boolean areReservedHeaderBitsZero(Word objectHeader) {
        return objectHeader.and(Word.unsigned(getReservedHeaderBitsMask())).isNull();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private boolean isInHubAddressSpace(long heapBaseRelativeAddress) {
        long hubAddressSpaceSize = getHubAddressSpaceSize();
        return Long.compareUnsigned(hubAddressSpaceSize, heapBaseRelativeAddress) > 0;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private long getHubAddressSpaceSize() {
        int hubAddressSpaceBits = hubBits;
        if (useCompressedReferences()) {
            hubAddressSpaceBits += numAlignmentBits;
        }
        return (1L << hubAddressSpaceBits) - 1;
    }

    @Fold
    static boolean useCompressedReferences() {
        return ReferenceAccess.singleton().getCompressionShift() > 0;
    }
}
