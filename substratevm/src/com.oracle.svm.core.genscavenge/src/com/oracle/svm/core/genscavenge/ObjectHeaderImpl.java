/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

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
    private static final UnsignedWord UNALIGNED_BIT = WordFactory.unsigned(0b00001);
    private static final UnsignedWord REMEMBERED_SET_BIT = WordFactory.unsigned(0b00010);
    private static final UnsignedWord FORWARDED_BIT = WordFactory.unsigned(0b00100);

    /**
     * Optional: per-object identity hash code state to avoid a fixed field, initially implicitly
     * initialized to {@link #IDHASH_STATE_UNASSIGNED}.
     */
    private static final int IDHASH_STATE_SHIFT = 3;
    private static final UnsignedWord IDHASH_STATE_BITS = WordFactory.unsigned(0b11000);

    @SuppressWarnings("unused") //
    private static final UnsignedWord IDHASH_STATE_UNASSIGNED = WordFactory.unsigned(0b00);
    private static final UnsignedWord IDHASH_STATE_FROM_ADDRESS = WordFactory.unsigned(0b01);
    private static final UnsignedWord IDHASH_STATE_IN_FIELD = WordFactory.unsigned(0b10);

    private final int numAlignmentBits;
    private final int numReservedBits;
    private final int numReservedExtraBits;

    private final int reservedBitsMask;

    @Platforms(Platform.HOSTED_ONLY.class)
    ObjectHeaderImpl() {
        numAlignmentBits = CodeUtil.log2(ConfigurationValues.getObjectLayout().getAlignment());
        int numMinimumReservedBits = 3;
        VMError.guarantee(numMinimumReservedBits <= numAlignmentBits, "Minimum set of reserved bits must be provided by object alignment");
        if (isIdentityHashFieldOptional()) {
            VMError.guarantee(ReferenceAccess.singleton().haveCompressedReferences(), "Ensures hubs (at the start of the image heap) remain addressable");
            numReservedBits = numMinimumReservedBits + 2;
            VMError.guarantee(numReservedBits <= numAlignmentBits || hasShift(),
                            "With no shift, forwarding references are stored directly in the header (with 64-bit, must be) and we cannot use non-alignment header bits");
        } else {
            numReservedBits = numMinimumReservedBits;
        }
        numReservedExtraBits = numReservedBits - numAlignmentBits;
        reservedBitsMask = (1 << numReservedBits) - 1;
    }

    @Fold
    public static ObjectHeaderImpl getObjectHeaderImpl() {
        ObjectHeaderImpl oh = HeapImpl.getObjectHeaderImpl();
        assert oh != null;
        return oh;
    }

    @Override
    public int getReservedBitsMask() {
        return reservedBitsMask;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public Pointer extractPotentialDynamicHubFromHeader(Word header) {
        if (ReferenceAccess.singleton().haveCompressedReferences()) {
            UnsignedWord hubBits = header.unsignedShiftRight(numReservedBits);
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
    @Override
    public void initializeHeaderOfNewObject(Pointer objectPointer, Word encodedHub, boolean isArrayLike) {
        ObjectLayout ol = ConfigurationValues.getObjectLayout();
        boolean isIdentityHashFieldInObjectHeader = ol.isIdentityHashFieldInObjectHeader() || ol.isIdentityHashFieldAtTypeSpecificOffset() && isArrayLike;
        if (getReferenceSize() == Integer.BYTES) {
            dynamicAssert(encodedHub.and(WordFactory.unsigned(0xFFFFFFFF00000000L)).isNull(), "hub can only use 32 bits");
            if (isIdentityHashFieldInObjectHeader) {
                /* Use a single 64-bit write to initialize the hub and the identity hashcode. */
                dynamicAssert(ol.getObjectHeaderIdentityHashOffset() == getHubOffset() + 4, "assumed layout to optimize initializing write");
                objectPointer.writeLong(getHubOffset(), encodedHub.rawValue(), LocationIdentity.INIT_LOCATION);
            } else {
                objectPointer.writeInt(getHubOffset(), (int) encodedHub.rawValue(), LocationIdentity.INIT_LOCATION);
            }
        } else {
            objectPointer.writeWord(getHubOffset(), encodedHub, LocationIdentity.INIT_LOCATION);
            if (isIdentityHashFieldInObjectHeader) {
                objectPointer.writeInt(ol.getObjectHeaderIdentityHashOffset(), 0, LocationIdentity.INIT_LOCATION);
            }
        }
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
        if (GraalDirectives.inIntrinsic()) {
            ReplacementsUtil.staticAssert(isIdentityHashFieldOptional(), "use only when hashcode fields are optional");
        } else {
            assert isIdentityHashFieldOptional() : "use only when hashcode fields are optional";
            assert !hasIdentityHashFromAddress(currentHeader) : "must not already have a hashcode";
        }

        UnsignedWord fromAddressState = IDHASH_STATE_FROM_ADDRESS.shiftLeft(IDHASH_STATE_SHIFT);
        UnsignedWord newHeader = currentHeader.and(IDHASH_STATE_BITS.not()).or(fromAddressState);
        writeHeaderToObject(ptr.toObjectNonNull(), newHeader);
        if (!GraalDirectives.inIntrinsic()) {
            assert hasIdentityHashFromAddress(readHeaderFromObject(ptr));
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public boolean hasIdentityHashFromAddress(Word header) {
        return hasIdentityHashFromAddressInline(header);
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static boolean hasIdentityHashFromAddressInline(Word header) {
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
            result = result.shiftLeft(numReservedExtraBits);
        }
        if (rememberedSet) {
            result = result.or(REMEMBERED_SET_BIT);
        }
        if (unaligned) {
            result = result.or(UNALIGNED_BIT);
        }
        return result;
    }

    /** Clear the object header bits from a header. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    UnsignedWord clearBits(UnsignedWord header) {
        UnsignedWord mask = WordFactory.unsigned(reservedBitsMask);
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
    public long encodeAsImageHeapObjectHeader(ImageHeapObject obj, long hubOffsetFromHeapBase) {
        long header = hubOffsetFromHeapBase << numReservedExtraBits;
        VMError.guarantee((header >>> numReservedExtraBits) == hubOffsetFromHeapBase, "Hub is too far from heap base for encoding in object header");
        assert (header & reservedBitsMask) == 0 : "Object header bits must be zero initially";
        if (obj.getPartition() instanceof ChunkedImageHeapPartition partition) {
            if (partition.isWritable() && HeapImpl.usesImageHeapCardMarking()) {
                header |= REMEMBERED_SET_BIT.rawValue();
            }
            if (partition.usesUnalignedObjects()) {
                header |= UNALIGNED_BIT.rawValue();
            }
        } else {
            assert obj.getPartition() instanceof FillerObjectDummyPartition;
        }
        if (isIdentityHashFieldOptional()) {
            header |= (IDHASH_STATE_IN_FIELD.rawValue() << IDHASH_STATE_SHIFT);
        }
        return header;
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
        UnsignedWord header = readHeaderFromObject(obj);
        return isUnalignedHeader(header);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isUnalignedHeader(UnsignedWord header) {
        return header.and(UNALIGNED_BIT).notEqual(0);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setRememberedSetBit(Object o) {
        UnsignedWord oldHeader = readHeaderFromObject(o);
        UnsignedWord newHeader = oldHeader.or(REMEMBERED_SET_BIT);
        writeHeaderToObject(o, newHeader);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean hasRememberedSet(UnsignedWord header) {
        return header.and(REMEMBERED_SET_BIT).notEqual(0);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static boolean isPointerToForwardedObject(Pointer p) {
        Word header = readHeaderFromPointer(p);
        return isForwardedHeader(header);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isForwardedHeader(UnsignedWord header) {
        return header.and(FORWARDED_BIT).notEqual(0);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    Object getForwardedObject(Pointer ptr) {
        return getForwardedObject(ptr, readHeaderFromPointer(ptr));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    Object getForwardedObject(Pointer ptr, UnsignedWord header) {
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
                result = compressedCopy.shiftLeft(32).or(WordFactory.unsigned(0x00000000e0e0e0e0L));
            } else {
                result = compressedCopy;
            }
        } else {
            result = Word.objectToUntrackedPointer(copy);
        }

        assert getHeaderBitsFromHeader(result).equal(0);
        return result.or(FORWARDED_BIT);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private UnsignedWord getHeaderBitsFromHeader(UnsignedWord header) {
        assert !isProducedHeapChunkZapped(header) : "Produced chunk zap value";
        assert !isConsumedHeapChunkZapped(header) : "Consumed chunk zap value";
        return header.and(reservedBitsMask);
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
