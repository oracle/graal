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

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.replacements.ReplacementsUtil;
import org.graalvm.compiler.word.ObjectAccess;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

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
    private static final UnsignedWord UNALIGNED_BIT = WordFactory.unsigned(0b001);
    private static final UnsignedWord REMEMBERED_SET_BIT = WordFactory.unsigned(0b010);
    private static final UnsignedWord FORWARDED_BIT = WordFactory.unsigned(0b100);

    private static final int RESERVED_BITS_MASK = 0b111;
    private static final UnsignedWord MASK_HEADER_BITS = WordFactory.unsigned(RESERVED_BITS_MASK);
    private static final UnsignedWord CLEAR_HEADER_BITS = MASK_HEADER_BITS.not();

    @Platforms(Platform.HOSTED_ONLY.class)
    ObjectHeaderImpl() {
    }

    @Fold
    public static ObjectHeaderImpl getObjectHeaderImpl() {
        ObjectHeaderImpl oh = HeapImpl.getHeapImpl().getObjectHeaderImpl();
        assert oh != null;
        return oh;
    }

    @Override
    public int getReservedBitsMask() {
        assert MASK_HEADER_BITS.rawValue() == RESERVED_BITS_MASK;
        assert CLEAR_HEADER_BITS.rawValue() == ~RESERVED_BITS_MASK;
        return RESERVED_BITS_MASK;
    }

    /**
     * Read the header of the object at the specified address. When compressed references are
     * enabled, the specified address must be the uncompressed absolute address of the object in
     * memory.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord readHeaderFromPointer(Pointer objectPointer) {
        if (getReferenceSize() == Integer.BYTES) {
            return WordFactory.unsigned(objectPointer.readInt(getHubOffset()));
        } else {
            return objectPointer.readWord(getHubOffset());
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord readHeaderFromObject(Object o) {
        if (getReferenceSize() == Integer.BYTES) {
            return WordFactory.unsigned(ObjectAccess.readInt(o, getHubOffset()));
        } else {
            return ObjectAccess.readWord(o, getHubOffset());
        }
    }

    public static UnsignedWord readHeaderFromObjectCarefully(Object o) {
        VMError.guarantee(o != null, "ObjectHeader.readHeaderFromObjectCarefully:  o: null");
        UnsignedWord header = readHeaderFromObject(o);
        VMError.guarantee(header.notEqual(WordFactory.zero()), "ObjectHeader.readHeaderFromObjectCarefully:  header: 0");
        VMError.guarantee(!isProducedHeapChunkZapped(header), "ObjectHeader.readHeaderFromObjectCarefully:  header: producedZapValue");
        VMError.guarantee(!isConsumedHeapChunkZapped(header), "ObjectHeader.readHeaderFromObjectCarefully:  header: consumedZapValue");
        return header;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public DynamicHub readDynamicHubFromPointer(Pointer ptr) {
        UnsignedWord header = readHeaderFromPointer(ptr);
        return dynamicHubFromObjectHeader(header);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public DynamicHub dynamicHubFromObjectHeader(UnsignedWord header) {
        UnsignedWord pointerBits = clearBits(header);
        Object objectValue;
        ReferenceAccess referenceAccess = ReferenceAccess.singleton();
        if (referenceAccess.haveCompressedReferences()) {
            UnsignedWord compressedBits = pointerBits.unsignedShiftRight(getCompressionShift());
            objectValue = referenceAccess.uncompressReference(compressedBits);
        } else {
            objectValue = ((Pointer) pointerBits).toObject();
        }
        return (DynamicHub) objectValue;
    }

    @Override
    public Pointer readPotentialDynamicHubFromPointer(Pointer ptr) {
        UnsignedWord potentialHeader = ObjectHeaderImpl.readHeaderFromPointer(ptr);
        UnsignedWord pointerBits = clearBits(potentialHeader);
        if (ReferenceAccess.singleton().haveCompressedReferences()) {
            UnsignedWord compressedBits = pointerBits.unsignedShiftRight(getCompressionShift());
            return KnownIntrinsics.heapBase().add(compressedBits.shiftLeft(getCompressionShift()));
        } else {
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
    public void initializeHeaderOfNewObject(Pointer objectPointer, Word encodedHub) {
        if (getReferenceSize() == Integer.BYTES) {
            dynamicAssert(getIdentityHashCodeOffset() == getHubOffset() + 4, "assumed layout to optimize initializing write");
            dynamicAssert(encodedHub.and(WordFactory.unsigned(0xFFFFFFFF00000000L)).isNull(), "hub can only use 32 bit");

            objectPointer.writeLong(getHubOffset(), encodedHub.rawValue(), LocationIdentity.INIT_LOCATION);
        } else {
            objectPointer.writeWord(getHubOffset(), encodedHub, LocationIdentity.INIT_LOCATION);
            objectPointer.writeInt(getIdentityHashCodeOffset(), 0, LocationIdentity.INIT_LOCATION);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void dynamicAssert(boolean condition, String msg) {
        if (GraalDirectives.inIntrinsic()) {
            ReplacementsUtil.dynamicAssert(condition, msg);
        } else {
            assert condition : msg;
        }
    }

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

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static Word encodeAsObjectHeader(DynamicHub hub, boolean rememberedSet, boolean unaligned) {
        /*
         * All DynamicHub instances are in the native image heap and therefore do not move, so we
         * can convert the hub to a Pointer without any precautions.
         */
        Word result = Word.objectToUntrackedPointer(hub);
        if (SubstrateOptions.SpawnIsolates.getValue()) {
            if (hasBase()) {
                result = result.subtract(KnownIntrinsics.heapBase());
            }
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
    public static UnsignedWord clearBits(UnsignedWord header) {
        return header.and(CLEAR_HEADER_BITS);
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
        long header = hubOffsetFromHeapBase;
        assert (header & MASK_HEADER_BITS.rawValue()) == 0 : "Object header bits must be zero initially";
        if (HeapImpl.usesImageHeapCardMarking()) {
            if (obj.getPartition() instanceof ChunkedImageHeapPartition) {
                ChunkedImageHeapPartition partition = (ChunkedImageHeapPartition) obj.getPartition();
                if (partition.isWritable()) {
                    header |= REMEMBERED_SET_BIT.rawValue();
                }
                if (partition.usesUnalignedObjects()) {
                    header |= UNALIGNED_BIT.rawValue();
                }
            } else {
                assert obj.getPartition() instanceof FillerObjectDummyPartition;
            }
        }
        return header;
    }

    public static boolean isAlignedObject(Object o) {
        return !isUnalignedObject(o);
    }

    public static boolean isAlignedHeader(UnsignedWord header) {
        return !isUnalignedHeader(header);
    }

    public static boolean isUnalignedObject(Object obj) {
        UnsignedWord header = ObjectHeaderImpl.readHeaderFromObject(obj);
        return isUnalignedHeader(header);
    }

    public static boolean isUnalignedHeader(UnsignedWord header) {
        return header.and(UNALIGNED_BIT).notEqual(0);
    }

    public static void setRememberedSetBit(Object o) {
        UnsignedWord oldHeader = readHeaderFromObject(o);
        UnsignedWord newHeader = oldHeader.or(REMEMBERED_SET_BIT);
        writeHeaderToObject(o, newHeader);
    }

    public static boolean hasRememberedSet(UnsignedWord header) {
        return header.and(REMEMBERED_SET_BIT).notEqual(0);
    }

    public static boolean isPointerToForwardedObject(Pointer p) {
        UnsignedWord header = readHeaderFromPointer(p);
        return isForwardedHeader(header);
    }

    public static boolean isForwardedHeader(UnsignedWord header) {
        return testForwardedHeaderBit(header);
    }

    private static boolean testForwardedHeaderBit(UnsignedWord headerBits) {
        return headerBits.and(FORWARDED_BIT).notEqual(0);
    }

    static Object getForwardedObject(Pointer ptr) {
        return getForwardedObject(ptr, readHeaderFromPointer(ptr));
    }

    static Object getForwardedObject(Pointer ptr, UnsignedWord header) {
        assert isForwardedHeader(header);
        if (ReferenceAccess.singleton().haveCompressedReferences()) {
            if (ReferenceAccess.singleton().getCompressEncoding().hasShift()) {
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
    static void installForwardingPointer(Object original, Object copy) {
        assert !isPointerToForwardedObject(Word.objectToUntrackedPointer(original));
        UnsignedWord forwardHeader;
        if (ReferenceAccess.singleton().haveCompressedReferences()) {
            if (ReferenceAccess.singleton().getCompressEncoding().hasShift()) {
                // Compression with a shift uses all bits of a reference, so store the forwarding
                // pointer in the location following the hub pointer.
                forwardHeader = WordFactory.unsigned(0xf0f0f0f0f0f0f0f0L);
                ObjectAccess.writeObject(original, getHubOffset() + getReferenceSize(), copy);
            } else {
                forwardHeader = ReferenceAccess.singleton().getCompressedRepresentation(copy);
            }
        } else {
            forwardHeader = Word.objectToUntrackedPointer(copy);
        }
        assert ObjectHeaderImpl.getHeaderBitsFromHeader(forwardHeader).equal(0);
        writeHeaderToObject(original, forwardHeader.or(FORWARDED_BIT));
        assert isPointerToForwardedObject(Word.objectToUntrackedPointer(original));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static UnsignedWord getHeaderBitsFromHeader(UnsignedWord header) {
        assert !isProducedHeapChunkZapped(header) : "Produced chunk zap value";
        assert !isConsumedHeapChunkZapped(header) : "Consumed chunk zap value";
        return header.and(MASK_HEADER_BITS);
    }

    static UnsignedWord getHeaderBitsFromHeaderCarefully(UnsignedWord header) {
        VMError.guarantee(!isProducedHeapChunkZapped(header), "Produced chunk zap value");
        VMError.guarantee(!isConsumedHeapChunkZapped(header), "Consumed chunk zap value");
        return header.and(MASK_HEADER_BITS);
    }

    @Fold
    static int getHubOffset() {
        return ConfigurationValues.getObjectLayout().getHubOffset();
    }

    @Fold
    static int getIdentityHashCodeOffset() {
        return ConfigurationValues.getObjectLayout().getIdentityHashCodeOffset();
    }

    @Fold
    static int getReferenceSize() {
        return ConfigurationValues.getObjectLayout().getReferenceSize();
    }

    @Fold
    static boolean hasBase() {
        return ImageSingletons.lookup(CompressEncoding.class).hasBase();
    }

    @Fold
    static int getCompressionShift() {
        return ReferenceAccess.singleton().getCompressEncoding().getShift();
    }
}
