/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.NumUtil;
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
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

/**
 * The low-order 3 bits of an ObjectHeader are used to mark things about this object instance.
 * <p>
 * The bits have two major purposes: figuring out whether, and how, to copy an object during
 * collections; and (in the CardRememberedSet heap) whether, and how, to maintain remembered sets on
 * oop stores. Since oop stores are (I think) way more common than collections, in this set of bits
 * I'm optimizing for distinguishing needing from not needing remembered set maintenance. On top of
 * that, I have to be able to figure out whether, and how, to copy an object.
 * <p>
 * One constant through all of this is that objects created by the fast-path don't want to do
 * anything with these bits, so the state 0 0 0 means whatever the fast-path allocation does.
 * <p>
 * A low-order 0 bit will mean that the object does not need any remembered set maintenance. That
 * leaves me 2 bits to distinguish objects in aligned chunks from objects in unaligned chunks (both
 * of which do move), from native image heap objects and system allocator objects (both of which
 * don't move).
 * <p>
 * A low-order 1 bit will mean that the object does need remembered set maintenance. That leaves me
 * 2 bits to distinguish objects in aligned chunks from object in unaligned chunks.
 * <p>
 * Since I need a bit pattern to mark forwarding pointers, I'll lump those with the bits that need
 * remembered set maintenance.
 * <p>
 * With those guidelines, here are the interpretation of the bits.
 * <table cellspacing="10">
 * <tr>
 * <th align="left"><u>Bit value</u></th>
 * <th align="left"><u>Remembered set</u></th>
 * <th align="left"><u>Storage</u></th>
 * <th align="left"><u>Details</u></th>
 * </tr>
 * <td>0 0 0</td>
 * <td>No</td>
 * <td>Aligned</td>
 * <td>The default setting, used by objects allocated in the young space on the fast-path.</td>
 * </tr>
 * <tr>
 * <td>0 0 1</td>
 * <td>Yes</td>
 * <td>Aligned</td>
 * <td>Modest objects in the old space.</td>
 * </tr>
 * <tr>
 * <td>0 1 0</td>
 * <td>No</td>
 * <td>Unaligned</td>
 * <td>Used for large arrays in the young space.</td>
 * </tr>
 * <tr>
 * <td>0 1 1</td>
 * <td>Yes</td>
 * <td>Unaligned</td>
 * <td>Large arrays in the old space. I don't distinguish arrays of primitives from arrays of
 * objects. There won't be any oop stores to the arrays of primitives.</td>
 * </tr>
 * <tr>
 * <td>1 0 0</td>
 * <td>No</td>
 * <td>System</td>
 * <td>Objects allocated by system allocators.</td>
 * </tr>
 * <tr>
 * <td>1 0 1</td>
 * <td>--</td>
 * <td>--</td>
 * <td>This value is unused.</td>
 * </tr>
 * <tr>
 * <tr>
 * <td>1 1 0</td>
 * <td>No</td>
 * <td>native image</td>
 * <td>Objects allocated in the native image heap.</td>
 * </tr>
 * <td>1 1 1</td>
 * <td>--</td>
 * <td>--</td>
 * <td>The upper bits are a forwarding pointer</td>
 * </tr>
 * </table>
 */
public class ObjectHeaderImpl extends ObjectHeader {
    // @formatter:off
    //                                Name                            Value                         // In hex:
    private static final UnsignedWord NO_REMEMBERED_SET_ALIGNED     = WordFactory.unsigned(0b000);  // 0 or 8.
    private static final UnsignedWord CARD_REMEMBERED_SET_ALIGNED   = WordFactory.unsigned(0b001);  // 1 or 9.
    private static final UnsignedWord NO_REMEMBERED_SET_UNALIGNED   = WordFactory.unsigned(0b010);  // 2 or a.
    private static final UnsignedWord CARD_REMEMBERED_SET_UNALIGNED = WordFactory.unsigned(0b011);  // 3 or b.
    private static final UnsignedWord UNUSED_100                    = WordFactory.unsigned(0b100);  // 4 or c.
    private static final UnsignedWord UNUSED_101                    = WordFactory.unsigned(0b101);  // 5 or d.
    private static final UnsignedWord BOOT_IMAGE                    = WordFactory.unsigned(0b110);  // 6 or e.
    private static final UnsignedWord FORWARDED                     = WordFactory.unsigned(0b111);  // 7 or f.

    private static final int ALL_RESERVED_BITS                      = NumUtil.safeToInt(FORWARDED.rawValue());
    private static final UnsignedWord MASK_HEADER_BITS              = WordFactory.unsigned(ALL_RESERVED_BITS);
    private static final UnsignedWord CLEAR_HEADER_BITS             = MASK_HEADER_BITS.not();
    // @formatter:on

    // Masks for write barriers.
    private static final UnsignedWord MASK_REMEMBERED_SET = CARD_REMEMBERED_SET_ALIGNED.and(CARD_REMEMBERED_SET_UNALIGNED);
    private static final UnsignedWord MASK_UNALIGNED = NO_REMEMBERED_SET_UNALIGNED.and(CARD_REMEMBERED_SET_UNALIGNED);

    /** Constructor for subclasses. */
    @Platforms(Platform.HOSTED_ONLY.class)
    ObjectHeaderImpl() {
    }

    @Fold
    public static ObjectHeaderImpl getObjectHeaderImpl() {
        final ObjectHeaderImpl oh = HeapImpl.getHeapImpl().getObjectHeaderImpl();
        assert oh != null;
        return oh;
    }

    @Override
    public int getReservedBitsMask() {
        assert MASK_HEADER_BITS.rawValue() == ALL_RESERVED_BITS;
        assert CLEAR_HEADER_BITS.rawValue() == ~ALL_RESERVED_BITS;
        return ALL_RESERVED_BITS;
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

    public static UnsignedWord readHeaderFromPointerCarefully(Pointer p) {
        VMError.guarantee(!p.isNull(), "ObjectHeader.readHeaderFromPointerCarefully:  p: null");
        if (!ReferenceAccess.singleton().haveCompressedReferences()) {
            // These tests are only useful if the original reference did not have to be
            // uncompressed, which would result in a different address than the zap word
            VMError.guarantee(p.notEqual(HeapPolicy.getProducedHeapChunkZapWord()), "ObjectHeader.readHeaderFromPointerCarefully:  p: producedZapValue");
            VMError.guarantee(p.notEqual(HeapPolicy.getConsumedHeapChunkZapWord()), "ObjectHeader.readHeaderFromPointerCarefully:  p: consumedZapValue");
        }
        final UnsignedWord header = readHeaderFromPointer(p);
        VMError.guarantee(header.notEqual(WordFactory.zero()), "ObjectHeader.readHeaderFromPointerCarefully:  header: 0");
        VMError.guarantee(!isProducedHeapChunkZapped(header), "ObjectHeader.readHeaderFromPointerCarefully:  header: producedZapValue");
        VMError.guarantee(!isConsumedHeapChunkZapped(header), "ObjectHeader.readHeaderFromPointerCarefully:  header: consumedZapValue");
        return header;
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
        final UnsignedWord header = readHeaderFromObject(o);
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

    public static DynamicHub readDynamicHubFromObjectCarefully(Object o) {
        readHeaderFromObjectCarefully(o);
        return KnownIntrinsics.readHub(o);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public DynamicHub dynamicHubFromObjectHeader(UnsignedWord header) {
        // Turn the Unsigned header into a Pointer, and then to an Object of type DynamicHub.
        final UnsignedWord pointerBits = clearBits(header);
        final Object objectValue;
        ReferenceAccess referenceAccess = ReferenceAccess.singleton();
        if (referenceAccess.haveCompressedReferences()) {
            UnsignedWord compressedBits = pointerBits.unsignedShiftRight(getCompressionShift());
            objectValue = referenceAccess.uncompressReference(compressedBits);
        } else {
            objectValue = ((Pointer) pointerBits).toObject();
        }
        return KnownIntrinsics.convertUnknownValue(objectValue, DynamicHub.class);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public void initializeHeaderOfNewObject(Pointer objectPointer, DynamicHub hub, HeapKind heapKind) {
        assert heapKind == HeapKind.Unmanaged;
        // headers in unmanaged memory don't need any GC-specific bits set
        Word objectHeader = encodeAsObjectHeader(hub, false, false);
        initializeHeaderOfNewObject(objectPointer, objectHeader);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void initializeHeaderOfNewObject(Pointer objectPointer, Word encodedHub) {
        if (getReferenceSize() == Integer.BYTES) {
            objectPointer.writeInt(getHubOffset(), (int) encodedHub.rawValue(), LocationIdentity.INIT_LOCATION);
        } else {
            objectPointer.writeWord(getHubOffset(), encodedHub, LocationIdentity.INIT_LOCATION);
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
    public Word encodeAsObjectHeader(DynamicHub hub, boolean rememberedSet, boolean unaligned) {
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
            result = result.or(MASK_REMEMBERED_SET);
        }
        if (unaligned) {
            result = result.or(MASK_UNALIGNED);
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
            return header.equal(HeapPolicy.getProducedHeapChunkZapInt());
        } else {
            return header.equal(HeapPolicy.getProducedHeapChunkZapWord());
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isConsumedHeapChunkZapped(UnsignedWord header) {
        if (getReferenceSize() == Integer.BYTES) {
            return header.equal(HeapPolicy.getConsumedHeapChunkZapInt());
        } else {
            return header.equal(HeapPolicy.getConsumedHeapChunkZapWord());
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    public long encodeAsImageHeapObjectHeader(long heapBaseRelativeAddress) {
        assert (heapBaseRelativeAddress & MASK_HEADER_BITS.rawValue()) == 0 : "Object header bits must be zero";
        return (heapBaseRelativeAddress | BOOT_IMAGE.rawValue());
    }

    public boolean isBootImageCarefully(Object o) {
        final UnsignedWord headerBits = readHeaderBitsFromObjectCarefully(o);
        return isBootImageHeaderBits(headerBits);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected boolean isBootImage(Object o) {
        final UnsignedWord headerBits = readHeaderBitsFromObject(o);
        return isBootImageHeaderBits(headerBits);
    }

    public boolean isBootImageHeader(UnsignedWord header) {
        final UnsignedWord headerBits = ObjectHeaderImpl.getHeaderBitsFromHeader(header);
        return isBootImageHeaderBits(headerBits);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean isBootImageHeaderBits(UnsignedWord headerBits) {
        return headerBitsEqual(headerBits, BOOT_IMAGE);
    }

    public boolean isForwardedHeader(UnsignedWord header) {
        final UnsignedWord headerBits = ObjectHeaderImpl.getHeaderBitsFromHeader(header);
        return isForwardedHeaderBits(headerBits);
    }

    public boolean isForwardedHeaderCarefully(UnsignedWord header) {
        final UnsignedWord headerBits = ObjectHeaderImpl.getHeaderBitsFromHeaderCarefully(header);
        return isForwardedHeaderBits(headerBits);
    }

    private static boolean isForwardedHeaderBits(UnsignedWord headerBits) {
        return headerBits.equal(FORWARDED);
    }

    public Pointer getForwardingPointer(Pointer objectPointer) {
        return Word.objectToUntrackedPointer(getForwardedObject(objectPointer));
    }

    public Object getForwardedObject(Pointer ptr) {
        UnsignedWord header = readHeaderFromPointer(ptr);
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

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isNonHeapAllocatedHeader(UnsignedWord header) {
        final UnsignedWord headerBits = ObjectHeaderImpl.getHeaderBitsFromHeader(header);
        return isNonHeapAllocatedHeaderBits(headerBits);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean isNonHeapAllocatedHeaderBits(UnsignedWord headerBits) {
        return (headerBits.equal(BOOT_IMAGE));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isNonHeapAllocated(Object o) {
        final UnsignedWord header = readHeaderFromObject(o);
        return isNonHeapAllocatedHeader(header);
    }

    public boolean isNonHeapAllocatedCarefully(Object o) {
        final UnsignedWord headerBits = ObjectHeaderImpl.readHeaderBitsFromObjectCarefully(o);
        return isNonHeapAllocatedHeader(headerBits);
    }

    public boolean isHeapAllocated(Object o) {
        final UnsignedWord header = ObjectHeaderImpl.readHeaderBitsFromObject(o);
        return isHeapAllocatedHeaderBits(header);
    }

    private static boolean isHeapAllocatedHeaderBits(UnsignedWord headerBits) {
        return (isAlignedHeaderBits(headerBits) || isUnalignedHeaderBits(headerBits));
    }

    public boolean isAlignedObject(Object o) {
        final UnsignedWord headerBits = ObjectHeaderImpl.readHeaderBitsFromObject(o);
        return isAlignedHeaderBits(headerBits);
    }

    protected boolean isAlignedHeader(UnsignedWord header) {
        final UnsignedWord headerBits = ObjectHeaderImpl.getHeaderBitsFromHeader(header);
        return isAlignedHeaderBits(headerBits);
    }

    private static boolean isAlignedHeaderBits(UnsignedWord headerBits) {
        /* An Object is aligned if the headerBits are either of these values. */
        return (isNoRememberedSetAlignedHeaderBits(headerBits) || isCardRememberedSetAlignedHeaderBits(headerBits));
    }

    private static boolean isNoRememberedSetAlignedHeaderBits(UnsignedWord headerBits) {
        return headerBitsEqual(headerBits, NO_REMEMBERED_SET_ALIGNED);
    }

    private static boolean isCardRememberedSetAlignedHeaderBits(UnsignedWord headerBits) {
        return headerBitsEqual(headerBits, CARD_REMEMBERED_SET_ALIGNED);
    }

    protected void setCardRememberedSetAligned(Object o) {
        setHeaderBitsOnObject(o, CARD_REMEMBERED_SET_ALIGNED);
    }

    protected boolean isUnalignedHeader(UnsignedWord header) {
        final UnsignedWord headerBits = ObjectHeaderImpl.getHeaderBitsFromHeader(header);
        return isUnalignedHeaderBits(headerBits);
    }

    public boolean isUnalignedObject(Object o) {
        final UnsignedWord headerBits = ObjectHeaderImpl.readHeaderBitsFromObject(o);
        return isUnalignedHeaderBits(headerBits);
    }

    private static boolean isUnalignedHeaderBits(UnsignedWord headerBits) {
        return (isNoRememberedSetUnalignedHeaderBits(headerBits) || isCardRememberedSetUnalignedHeaderBits(headerBits));
    }

    private static boolean isNoRememberedSetUnalignedHeaderBits(UnsignedWord headerBits) {
        return headerBitsEqual(headerBits, NO_REMEMBERED_SET_UNALIGNED);
    }

    private static boolean isCardRememberedSetUnalignedHeaderBits(UnsignedWord headerBits) {
        return headerBitsEqual(headerBits, CARD_REMEMBERED_SET_UNALIGNED);
    }

    private static void setCardRememberedSetUnaligned(Object o) {
        setHeaderBitsOnObject(o, CARD_REMEMBERED_SET_UNALIGNED);
    }

    public void setUnaligned(Object o) {
        /*
         * By default this sets the Object to unaligned *and* with a card remembered set. If I need
         * something else, I should set the other state explicitly.
         */
        setCardRememberedSetUnaligned(o);
    }

    /**
     * Is this the header of an object with a remembered set?
     *
     * @param header the full header to be examined.
     * @return true if the object has a remembered set, false otherwise.
     */
    public static boolean hasRememberedSet(UnsignedWord header) {
        // critical for write barrier performance
        return header.and(MASK_REMEMBERED_SET).notEqual(0);
    }

    /**
     * Is this the header of an unaligned heap object?
     *
     * Note: the header can only be from a heap object.
     *
     * @param header the full header to be examined.
     * @return true if the object is unaligned, false otherwise.
     *
     */
    public static boolean isHeapObjectUnaligned(UnsignedWord header) {
        // critical for write barrier performance
        return header.and(MASK_UNALIGNED).notEqual(0);
    }

    /** Install in an Object, a forwarding pointer to a different Object. */
    protected void installForwardingPointer(Object original, Object copy) {
        assert !isPointerToForwardedObject(Word.objectToUntrackedPointer(original));
        /* Turn the copy Object into a Pointer, and encode that as a forwarding pointer. */
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
        writeHeaderToObject(original, forwardHeader.or(FORWARDED));
        assert isPointerToForwardedObject(Word.objectToUntrackedPointer(original));
    }

    private boolean isPointerToForwardedObject(Pointer p) {
        final UnsignedWord header = readHeaderFromPointer(p);
        final boolean result = isForwardedHeader(header);
        return result;
    }

    protected boolean isPointerToForwardedObjectCarefully(Pointer p) {
        final UnsignedWord header = readHeaderFromPointerCarefully(p);
        final boolean result = isForwardedHeaderCarefully(header);
        return result;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected static UnsignedWord readHeaderBitsFromObject(Object o) {
        final UnsignedWord header = readHeaderFromObject(o);
        return ObjectHeaderImpl.getHeaderBitsFromHeader(header);
    }

    private static UnsignedWord readHeaderBitsFromObjectCarefully(Object o) {
        final UnsignedWord header = readHeaderFromObjectCarefully(o);
        return ObjectHeaderImpl.getHeaderBitsFromHeaderCarefully(header);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected static UnsignedWord getHeaderBitsFromHeader(UnsignedWord header) {
        assert !isProducedHeapChunkZapped(header) : "Produced chunk zap value";
        assert !isConsumedHeapChunkZapped(header) : "Consumed chunk zap value";
        return header.and(MASK_HEADER_BITS);
    }

    protected static UnsignedWord getHeaderBitsFromHeaderCarefully(UnsignedWord header) {
        VMError.guarantee(!isProducedHeapChunkZapped(header), "Produced chunk zap value");
        VMError.guarantee(!isConsumedHeapChunkZapped(header), "Consumed chunk zap value");
        return header.and(MASK_HEADER_BITS);
    }

    // TODO: Does this have to be atomic?
    private static void setHeaderBitsOnObject(Object o, UnsignedWord headerBits) {
        final UnsignedWord oldHeader = readHeaderFromObject(o);
        final UnsignedWord newHeader = clearBits(oldHeader).or(headerBits);
        writeHeaderToObject(o, newHeader);
    }

    /** Test if an object header has the specified bits. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean headerBitsEqual(UnsignedWord headerBits, UnsignedWord specifiedBits) {
        return headerBits.equal(specifiedBits);
    }

    public String toStringFromObject(Object o) {
        final UnsignedWord header = readHeaderFromObject(o);
        return toStringFromHeader(header);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int getHubOffset() {
        return ConfigurationValues.getObjectLayout().getHubOffset();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int getReferenceSize() {
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

    /** Debugging. Must not be used in Log messages that are expected to be optimized away. */
    public String toStringFromHeader(UnsignedWord header) {
        final UnsignedWord headerBits = ObjectHeaderImpl.getHeaderBitsFromHeader(header);
        /*
         * Careful: Since this is used during debugging of the collector, only return
         * statically-allocated Strings.
         */
        if (isNoRememberedSetAlignedHeaderBits(headerBits)) {
            return "NO_REMEMBERED_SET_ALIGNED";
        } else if (isNoRememberedSetUnalignedHeaderBits(headerBits)) {
            return "NO_REMEMBERED_SET_UNALIGNED";
        } else if (isBootImageHeaderBits(headerBits)) {
            return "BOOT_IMAGE";
        } else if (headerBits.equal(UNUSED_100)) {
            return "UNUSED_100";
        } else if (headerBits.equal(UNUSED_101)) {
            return "UNUSED_101";
        } else if (isCardRememberedSetAlignedHeaderBits(headerBits)) {
            return "CARD_REMEMBERED_SET_ALIGNED";
        } else if (isCardRememberedSetUnalignedHeaderBits(headerBits)) {
            return "CARD_REMEMBERED_SET_UNALIGNED";
        } else if (isForwardedHeader(headerBits)) {
            return "FORWARDED";
        } else {
            return "UNKNOWN_CARD_REMEMBERED_SET_OBJECT_HEADER_BITS";
        }
    }

    public Log logObjectHeader(Object obj, Log log) {
        log.string("  obj: ").hex(Word.objectToUntrackedPointer(obj));
        final UnsignedWord header = ObjectHeaderImpl.readHeaderFromObjectCarefully(obj);
        final DynamicHub hub = dynamicHubFromObjectHeader(header);
        log.string("  header: ").hex(header)
                        .string("  hub: ").hex(Word.objectToUntrackedPointer(hub))
                        .string("  bits: ").string(ObjectHeaderImpl.getObjectHeaderImpl().toStringFromHeader(header));
        if (!HeapImpl.getHeapImpl().assertHub(hub)) {
            log.string("  hub fails to verify");
        }
        return log;
    }

    /**
     * Debugging: Classifies a header so I can decide how much to trust it.
     *
     * <ul>
     * <li>Negative results fail in one way or another.</li>
     * <li>Positive results mean the header was valid.</li>
     * </ul>
     */
    public static int classifyHeader(UnsignedWord header) {
        /* Check for total failures. */
        if (header.equal(WordFactory.zero())) {
            return -1_000_000;
        }
        if (ObjectHeaderImpl.isProducedHeapChunkZapped(header)) {
            return -2_000_000;
        }
        if (ObjectHeaderImpl.isConsumedHeapChunkZapped(header)) {
            return -3_000_000;
        }
        /* Tease the header apart into the DynamicHub bits and the header bits. */
        final UnsignedWord headerBits = ObjectHeaderImpl.getHeaderBitsFromHeaderCarefully(header);
        final int headerBitsClassification;
        if (headerBits.equal(NO_REMEMBERED_SET_ALIGNED)) {
            headerBitsClassification = 1;
        } else if (headerBits.equal(CARD_REMEMBERED_SET_ALIGNED)) {
            headerBitsClassification = 2;
        } else if (headerBits.equal(NO_REMEMBERED_SET_UNALIGNED)) {
            headerBitsClassification = 3;
        } else if (headerBits.equal(CARD_REMEMBERED_SET_UNALIGNED)) {
            headerBitsClassification = 4;
        } else if (headerBits.equal(BOOT_IMAGE)) {
            headerBitsClassification = 5;
        } else if (headerBits.equal(FORWARDED)) {
            headerBitsClassification = 6;
        } else {
            headerBitsClassification = -1;
        }
        final DynamicHub hub = ObjectHeaderImpl.getObjectHeaderImpl().dynamicHubFromObjectHeader(header);
        final int hubClassification = HeapVerifierImpl.classifyObject(hub);
        return ((1000 * hubClassification) + headerBitsClassification);
    }
}
