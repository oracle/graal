/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.replacements.ReplacementsUtil;
import jdk.graal.compiler.word.ObjectAccess;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.code.CodeUtil;

/**
 * The pointer to the hub is either an uncompressed absolute reference or a heap-base-relative
 * reference without a shift. This limits the address space where all hubs must be placed to 32/64
 * bits but due to the object alignment, the 3 least-significant bits can be reserved for the GC.
 *
 * Image heap objects are not marked explicitly, but must be treated differently in some regards. In
 * places where it is necessary to distinguish image heap objects, it is necessary to call
 * {@link Heap#isInImageHeap}.
 */
public final class ObjectHeaderImpl extends ObjectHeader {
    private static final UnsignedWord UNALIGNED_BIT = Word.unsigned(0b00001);
    private static final UnsignedWord REMSET_OR_MARKED1_BIT = Word.unsigned(0b00010);
    private static final UnsignedWord FORWARDED_OR_MARKED2_BIT = Word.unsigned(0b00100);
    private static final UnsignedWord MARKED_BITS = REMSET_OR_MARKED1_BIT.or(FORWARDED_OR_MARKED2_BIT);

    /**
     * Optional: per-object identity hash code state to avoid a fixed field, initially implicitly
     * initialized to {@link #IDHASH_STATE_UNASSIGNED}.
     */
    private static final int IDHASH_STATE_SHIFT = 3;
    private static final UnsignedWord IDHASH_STATE_BITS = Word.unsigned(0b11000);

    @SuppressWarnings("unused") //
    private static final UnsignedWord IDHASH_STATE_UNASSIGNED = Word.unsigned(0b00);
    private static final UnsignedWord IDHASH_STATE_FROM_ADDRESS = Word.unsigned(0b01);
    private static final UnsignedWord IDHASH_STATE_IN_FIELD = Word.unsigned(0b10);

    private final int numAlignmentBits;
    private final int numReservedHubBits;
    private final int numReservedExtraHubBits;

    private final int reservedHubBitsMask;

    @Platforms(Platform.HOSTED_ONLY.class)
    ObjectHeaderImpl() {
        numAlignmentBits = CodeUtil.log2(ConfigurationValues.getObjectLayout().getAlignment());
        int numMinReservedHubBits = 3;
        VMError.guarantee(numMinReservedHubBits <= numAlignmentBits, "Minimum set of reserved bits must be provided by object alignment");
        if (isIdentityHashFieldOptional()) {
            VMError.guarantee(ReferenceAccess.singleton().haveCompressedReferences(), "Ensures hubs (at the start of the image heap) remain addressable");
            numReservedHubBits = numMinReservedHubBits + 2;
            VMError.guarantee(numReservedHubBits <= numAlignmentBits || hasShift(),
                            "With no shift, forwarding references are stored directly in the header (with 64-bit, must be) and we cannot use non-alignment header bits");
        } else {
            numReservedHubBits = numMinReservedHubBits;
        }
        numReservedExtraHubBits = numReservedHubBits - numAlignmentBits;
        reservedHubBitsMask = (1 << numReservedHubBits) - 1;
    }

    @Fold
    public static ObjectHeaderImpl getObjectHeaderImpl() {
        ObjectHeaderImpl oh = HeapImpl.getObjectHeaderImpl();
        assert oh != null;
        return oh;
    }

    @Override
    public int getReservedHubBitsMask() {
        return reservedHubBitsMask;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public Pointer extractPotentialDynamicHubFromHeader(Word header) {
        if (ReferenceAccess.singleton().haveCompressedReferences()) {
            UnsignedWord hubBits = header.unsignedShiftRight(numReservedHubBits);
            UnsignedWord baseRelativeBits = hubBits.shiftLeft(numAlignmentBits);
            return KnownIntrinsics.heapBase().add(baseRelativeBits);
        } else {
            UnsignedWord pointerBits = clearBits(header);
            return (Pointer) pointerBits;
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public Word encodeAsUnmanagedObjectHeader(DynamicHub hub) {
        // Headers in unmanaged memory don't need any GC-specific bits set
        return encodeAsObjectHeader(hub, false, false);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @AlwaysInline(INLINE_INITIALIZE_HEADER_INIT_REASON)
    @Override
    protected void initializeObjectHeader(Pointer objectPointer, Word encodedHub, boolean isArrayLike, MemWriter writer) {
        ObjectLayout ol = ConfigurationValues.getObjectLayout();
        boolean isIdentityHashFieldInObjectHeader = ol.isIdentityHashFieldInObjectHeader() || ol.isIdentityHashFieldAtTypeSpecificOffset() && isArrayLike;
        if (getReferenceSize() == Integer.BYTES) {
            dynamicAssert(encodedHub.and(Word.unsigned(0xFFFFFFFF00000000L)).isNull(), "hub can only use 32 bits");
            if (isIdentityHashFieldInObjectHeader) {
                /* Use a single 64-bit write to initialize the hub and the identity hashcode. */
                dynamicAssert(ol.getObjectHeaderIdentityHashOffset() == getHubOffset() + 4, "assumed layout to optimize initializing write");
                writer.writeLong(objectPointer, getHubOffset(), encodedHub.rawValue());
            } else {
                writer.writeInt(objectPointer, getHubOffset(), (int) encodedHub.rawValue());
            }
        } else {
            writer.writeWord(objectPointer, getHubOffset(), encodedHub);
            if (isIdentityHashFieldInObjectHeader) {
                writer.writeInt(objectPointer, ol.getObjectHeaderIdentityHashOffset(), 0);
            }
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public Word readHeaderFromPointer(Pointer objectPointer) {
        if (getReferenceSize() == Integer.BYTES) {
            return Word.unsigned(objectPointer.readInt(getHubOffset()));
        }
        return objectPointer.readWord(getHubOffset());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public Word readHeaderFromObject(Object o) {
        if (getReferenceSize() == Integer.BYTES) {
            return Word.unsigned(ObjectAccess.readInt(o, getHubOffset()));
        }
        return ObjectAccess.readWord(o, getHubOffset());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public boolean hasOptionalIdentityHashField(Word header) {
        if (GraalDirectives.inIntrinsic()) {
            ReplacementsUtil.staticAssert(isIdentityHashFieldOptional(), "use only when hashcode fields are optional");
        } else {
            VMError.guarantee(isIdentityHashFieldOptional(), "use only when hashcode fields are optional");
        }

        UnsignedWord inFieldState = IDHASH_STATE_IN_FIELD.shiftLeft(IDHASH_STATE_SHIFT);
        return header.and(IDHASH_STATE_BITS).equal(inFieldState);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void setIdentityHashInField(Object o) {
        assert VMOperation.isGCInProgress();
        VMError.guarantee(isIdentityHashFieldOptional());
        UnsignedWord oldHeader = readHeaderFromObject(o);
        UnsignedWord inFieldState = IDHASH_STATE_IN_FIELD.shiftLeft(IDHASH_STATE_SHIFT);
        UnsignedWord newHeader = oldHeader.and(IDHASH_STATE_BITS.not()).or(inFieldState);
        writeHeaderToObject(o, newHeader);
        assert hasOptionalIdentityHashField(readHeaderFromObject(o));
    }

    /**
     * Set bits in an object's header to indicate that it has been assigned an identity hash code
     * that is based on its current address of the time of the call.
     *
     * This (currently) does not need to use atomic instructions because two threads can only modify
     * the header in the same way independent of each other. If this changes in the future by the
     * introduction of other header bits or by changes to the identity hash code states and their
     * transitions, this needs to be reconsidered.
     *
     * Still, this method and the caller that computes the identity hash code need to be
     * uninterruptible (atomic with regard to GC) so that no GC can occur and unexpectedly move the
     * object (changing its potential identity hash code), modify its object header, or introduce an
     * identity hash code field.
     */
    @Uninterruptible(reason = "Prevent a GC interfering with the object's identity hash state.", callerMustBe = true)
    @Override
    public void setIdentityHashFromAddress(Pointer ptr, Word currentHeader) {
        assert isIdentityHashFieldOptional() : "use only when hashcode fields are optional";
        assert !hasIdentityHashFromAddress(currentHeader) : "must not already have a hashcode";

        UnsignedWord fromAddressState = IDHASH_STATE_FROM_ADDRESS.shiftLeft(IDHASH_STATE_SHIFT);
        UnsignedWord newHeader = currentHeader.and(IDHASH_STATE_BITS.not()).or(fromAddressState);
        writeHeaderToObject(ptr.toObjectNonNull(), newHeader);

        assert hasIdentityHashFromAddress(readHeaderFromPointer(ptr));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public boolean hasIdentityHashFromAddress(Word header) {
        return hasIdentityHashFromAddressInline(header);
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean hasIdentityHashFromAddressInline(Word header) {
        if (GraalDirectives.inIntrinsic()) {
            ReplacementsUtil.staticAssert(isIdentityHashFieldOptional(), "use only when hashcode fields are optional");
        } else {
            assert isIdentityHashFieldOptional();
        }

        UnsignedWord fromAddressState = IDHASH_STATE_FROM_ADDRESS.shiftLeft(IDHASH_STATE_SHIFT);
        return header.and(IDHASH_STATE_BITS).equal(fromAddressState);
    }

    @AlwaysInline(value = "Helper method that needs to be optimized away.")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void dynamicAssert(boolean condition, String msg) {
        if (GraalDirectives.inIntrinsic()) {
            ReplacementsUtil.dynamicAssert(condition, msg);
        } else {
            assert condition : msg;
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void writeHeaderToObject(Object o, WordBase header) {
        if (getReferenceSize() == Integer.BYTES) {
            ObjectAccess.writeInt(o, getHubOffset(), (int) header.rawValue());
        } else {
            ObjectAccess.writeWord(o, getHubOffset(), header);
        }
    }

    @Override
    public Word encodeAsTLABObjectHeader(DynamicHub hub) {
        return encodeAsObjectHeader(hub, false, false);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public Word encodeAsObjectHeader(DynamicHub hub, boolean rememberedSet, boolean unaligned) {
        /*
         * All DynamicHub instances are in the native image heap and therefore do not move, so we
         * can convert the hub to a Pointer without any precautions.
         */
        Word result = Word.objectToUntrackedPointer(hub);
        if (SubstrateOptions.SpawnIsolates.getValue()) {
            result = result.subtract(KnownIntrinsics.heapBase());
            result = result.shiftLeft(numReservedExtraHubBits);
        }
        if (rememberedSet) {
            result = result.or(REMSET_OR_MARKED1_BIT);
        }
        if (unaligned) {
            result = result.or(UNALIGNED_BIT);
        }
        return result;
    }

    @Override
    public long encodeAsTLABObjectHeader(long hubOffsetFromHeapBase) {
        assert SubstrateOptions.SpawnIsolates.getValue();
        return hubOffsetFromHeapBase << numReservedExtraHubBits;
    }

    @Override
    public int constantHeaderSize() {
        if (!SubstrateOptions.SpawnIsolates.getValue()) {
            return -1;
        }
        return getReferenceSize();
    }

    /** Clear the object header bits from a header. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    UnsignedWord clearBits(UnsignedWord header) {
        UnsignedWord mask = Word.unsigned(reservedHubBitsMask);
        return header.and(mask.not());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isProducedHeapChunkZapped(UnsignedWord header) {
        if (getReferenceSize() == Integer.BYTES) {
            return header.equal(HeapParameters.getProducedHeapChunkZapInt());
        } else {
            return header.equal(HeapParameters.getProducedHeapChunkZapWord());
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isConsumedHeapChunkZapped(UnsignedWord header) {
        if (getReferenceSize() == Integer.BYTES) {
            return header.equal(HeapParameters.getConsumedHeapChunkZapInt());
        } else {
            return header.equal(HeapParameters.getConsumedHeapChunkZapWord());
        }
    }

    @Override
    public long encodeHubPointerForImageHeap(ImageHeapObject obj, long hubOffsetFromHeapBase) {
        long header = hubOffsetFromHeapBase << numReservedExtraHubBits;
        assert (header & reservedHubBitsMask) == 0 : "Object header bits must be zero initially";
        ChunkedImageHeapPartition partition = (ChunkedImageHeapPartition) obj.getPartition();
        if (partition.isWritable() && HeapImpl.usesImageHeapCardMarking()) {
            header |= REMSET_OR_MARKED1_BIT.rawValue();
        }
        if (partition.usesUnalignedObjects()) {
            header |= UNALIGNED_BIT.rawValue();
        }
        if (isIdentityHashFieldOptional()) {
            header |= (IDHASH_STATE_IN_FIELD.rawValue() << IDHASH_STATE_SHIFT);
        }
        return header;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void verifyDynamicHubOffset(long offsetFromHeapBase) {
        long referenceSizeMask = getReferenceSize() == Integer.BYTES ? 0xFFFF_FFFFL : -1L;
        long encoded = (offsetFromHeapBase << numReservedExtraHubBits) & referenceSizeMask;
        boolean shiftLosesInformation = (encoded >>> numReservedExtraHubBits != offsetFromHeapBase);
        if (shiftLosesInformation) {
            throw VMError.shouldNotReachHere("Hub is too far from heap base for encoding in object header");
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isAlignedObject(Object o) {
        return !isUnalignedObject(o);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isAlignedHeader(UnsignedWord header) {
        return !isUnalignedHeader(header);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isUnalignedObject(Object obj) {
        UnsignedWord header = getObjectHeaderImpl().readHeaderFromObject(obj);
        return isUnalignedHeader(header);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isUnalignedHeader(UnsignedWord header) {
        return header.and(UNALIGNED_BIT).notEqual(0);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setRememberedSetBit(Object o) {
        UnsignedWord oldHeader = getObjectHeaderImpl().readHeaderFromObject(o);
        assert oldHeader.and(FORWARDED_OR_MARKED2_BIT).equal(0);
        UnsignedWord newHeader = oldHeader.or(REMSET_OR_MARKED1_BIT);
        writeHeaderToObject(o, newHeader);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean hasRememberedSet(UnsignedWord header) {
        return header.and(REMSET_OR_MARKED1_BIT).notEqual(0);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setMarked(Object o) {
        if (!SerialGCOptions.useCompactingOldGen()) { // not guarantee(): always folds, prevent call
            throw VMError.shouldNotReachHere("Only for compacting GC.");
        }
        UnsignedWord header = getObjectHeaderImpl().readHeaderFromObject(o);
        assert header.and(FORWARDED_OR_MARKED2_BIT).equal(0) : "forwarded or already marked";
        /*
         * The remembered bit is already set if the object was in the old generation before, or
         * unset if it was only just absorbed from the young generation, in which case we set it.
         */
        writeHeaderToObject(o, header.or(MARKED_BITS));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void unsetMarkedAndKeepRememberedSetBit(Object o) {
        UnsignedWord header = getObjectHeaderImpl().readHeaderFromObject(o);
        assert isMarkedHeader(header);
        writeHeaderToObject(o, header.and(FORWARDED_OR_MARKED2_BIT.not()));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isMarked(Object o) {
        return isMarkedHeader(getObjectHeaderImpl().readHeaderFromObject(o));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isMarkedHeader(UnsignedWord header) {
        if (!SerialGCOptions.useCompactingOldGen()) {
            throw VMError.shouldNotReachHere("Only for compacting GC.");
        }
        return header.and(MARKED_BITS).equal(MARKED_BITS);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static boolean isPointerToForwardedObject(Pointer p) {
        Word header = getObjectHeaderImpl().readHeaderFromPointer(p);
        return isForwardedHeader(header);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isForwardedHeader(UnsignedWord header) {
        return header.and(MARKED_BITS).equal(FORWARDED_OR_MARKED2_BIT);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public Object getForwardedObject(Pointer ptr) {
        return getForwardedObject(ptr, readHeaderFromPointer(ptr));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public Object getForwardedObject(Pointer ptr, UnsignedWord header) {
        assert isForwardedHeader(header);
        if (ReferenceAccess.singleton().haveCompressedReferences()) {
            if (hasShift()) {
                // References compressed with shift have no bits to spare, so the forwarding
                // reference is stored separately, after the object header
                ObjectLayout layout = ConfigurationValues.getObjectLayout();
                assert layout.isAligned(getHubOffset()) && (2 * getReferenceSize()) <= layout.getAlignment() : "Forwarding reference must fit after hub";
                int forwardRefOffset = getHubOffset() + getReferenceSize();
                return ReferenceAccess.singleton().readObjectAt(ptr.add(forwardRefOffset), true);
            } else {
                return ReferenceAccess.singleton().uncompressReference(clearBits(header));
            }
        } else {
            return ((Pointer) clearBits(header)).toObject();
        }
    }

    /** In an Object, install a forwarding pointer to a different Object. */
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void installForwardingPointer(Object original, Object copy) {
        assert !isPointerToForwardedObject(Word.objectToUntrackedPointer(original));
        UnsignedWord forwardHeader = getForwardHeader(copy);
        ObjectAccess.writeLong(original, getHubOffset(), forwardHeader.rawValue());
        assert isPointerToForwardedObject(Word.objectToUntrackedPointer(original));
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private UnsignedWord getForwardHeader(Object copy) {
        UnsignedWord result;
        if (ReferenceAccess.singleton().haveCompressedReferences()) {
            UnsignedWord compressedCopy = ReferenceAccess.singleton().getCompressedRepresentation(copy);
            if (hasShift()) {
                // Compression with a shift uses all bits of a reference, so store the forwarding
                // pointer in the location following the hub pointer.
                result = compressedCopy.shiftLeft(32).or(Word.unsigned(0x00000000e0e0e0e0L));
            } else {
                result = compressedCopy;
            }
        } else {
            result = Word.objectToUntrackedPointer(copy);
        }

        assert getHeaderBitsFromHeader(result).equal(0);
        return result.or(FORWARDED_OR_MARKED2_BIT);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private UnsignedWord getHeaderBitsFromHeader(UnsignedWord header) {
        assert !isProducedHeapChunkZapped(header) : "Produced chunk zap value";
        assert !isConsumedHeapChunkZapped(header) : "Consumed chunk zap value";
        return header.and(reservedHubBitsMask);
    }

    @Fold
    static boolean hasShift() {
        return ReferenceAccess.singleton().getCompressEncoding().hasShift();
    }

    @Fold
    static boolean isIdentityHashFieldOptional() {
        return ConfigurationValues.getObjectLayout().isIdentityHashFieldOptional();
    }
}
