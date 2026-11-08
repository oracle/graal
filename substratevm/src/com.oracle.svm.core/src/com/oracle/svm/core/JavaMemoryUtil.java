/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import com.oracle.svm.shared.config.ObjectLayout;
import com.oracle.svm.guest.staging.core.IntrinsicCandidate;
import com.oracle.svm.guest.staging.core.UnmanagedMemoryUtil;
import com.oracle.svm.shared.NeverInline;
import com.oracle.svm.shared.Uninterruptible;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.shared.util.UnsignedUtils;
import com.oracle.svm.shared.util.VMError;

import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.LIKELY_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;

import jdk.graal.compiler.api.replacements.Fold;

import org.graalvm.word.impl.BarrieredAccess;

/**
 * The methods in this class are mainly used to fill or copy Java heap memory. All methods guarantee
 * at least some level of atomicity and honor the Java memory model if they are used as documented.
 * For more information, please read the JavaDoc of the individual methods.
 * <p>
 * The valid use cases are listed below. For all other use cases, use {@link UnmanagedMemoryUtil}
 * instead.
 * <ul>
 * <li>Copying between Java arrays.</li>
 * <li>Copying between Java instance objects.</li>
 * <li>Copying between a Java object and a Java array.</li>
 * <li>Copying from native memory to the Java heap.</li>
 * <li>Copying from the Java heap to unmanaged memory.</li>
 * <li>Filling Java arrays.</li>
 * <li>Filling Java objects.</li>
 * </ul>
 * <p>
 * More specialized methods (e.g., methods for copying primitive arrays) are faster than more
 * generic ones. So, performance-wise it is best to call the most specific method that is applicable
 * to your use case.
 */
public final class JavaMemoryUtil {

    /**
     * Copy bytes from one Java object to another. The copied memory areas may overlap.
     *
     * The caller must take care that no GC barriers are required for the operation. Furthermore,
     * the offsets must be aligned to object field boundaries or array element boundaries in order
     * to ensure access atomicity according to the Java memory model (Java Language Specification,
     * 17.6).
     */
    public static void copy(Object from, UnsignedWord fromOffset, Object to, UnsignedWord toOffset, UnsignedWord size) {
        if (from != to || fromOffset.aboveThan(toOffset)) {
            copyForward(from, fromOffset, to, toOffset, size);
        } else if (fromOffset.notEqual(toOffset)) {
            copyBackward(from, fromOffset, to, toOffset, size);
        }
    }

    /**
     * Copy bytes from one Java object to another.
     *
     * The caller must take care that no GC barriers are required for the operation. Furthermore,
     * the offsets must be aligned to object field boundaries or array element boundaries in order
     * to ensure access atomicity according to the Java memory model (Java Language Specification,
     * 17.6).
     */
    @Uninterruptible(reason = "Objects must not move")
    public static void copyForward(Object from, UnsignedWord fromOffset, Object to, UnsignedWord toOffset, UnsignedWord size) {
        Pointer fromPtr = Word.objectToUntrackedPointer(from).add(fromOffset);
        Pointer toPtr = Word.objectToUntrackedPointer(to).add(toOffset);
        copyForward(fromPtr, toPtr, size);
    }

    /**
     * Copy bytes from one Java object to another.
     *
     * The caller must take care that no GC barriers are required for the operation. Furthermore,
     * the offsets must be aligned to object field boundaries or array element boundaries in order
     * to ensure access atomicity according to the Java memory model (Java Language Specification,
     * 17.6).
     */
    @Uninterruptible(reason = "Objects must not move")
    public static void copyBackward(Object from, UnsignedWord fromOffset, Object to, UnsignedWord toOffset, UnsignedWord size) {
        Pointer fromPtr = Word.objectToUntrackedPointer(from).add(fromOffset);
        Pointer toPtr = Word.objectToUntrackedPointer(to).add(toOffset);
        copyBackward(fromPtr, toPtr, size);
    }

    /**
     * Copy bytes from one memory area to another. The memory areas may overlap.
     *
     * If either memory area is on the Java heap, the caller must take care that no GC barriers are
     * required for the operation and the caller must be {@linkplain Uninterruptible
     * uninterruptible} or otherwise ensure that objects cannot move or be collected.
     *
     * When using this method to copy memory of Java objects, the addresses and size must align to
     * object field boundaries or array element boundaries in order to ensure access atomicity
     * according to the Java memory model (Java Language Specification, 17.6).
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void copy(Pointer from, Pointer to, UnsignedWord size) {
        if (from.aboveThan(to)) {
            copyForward(from, to, size);
        } else if (from.belowThan(to)) {
            copyBackward(from, to, size);
        }
    }

    /**
     * Copy bytes from one memory area to another.
     *
     * If either memory area is on the Java heap, the caller must take care that no GC barriers are
     * required for the operation and the caller must be {@linkplain Uninterruptible
     * uninterruptible} or otherwise ensure that objects cannot move or be collected.
     *
     * When using this method to copy memory of Java objects, the addresses and size must align to
     * object field boundaries or array element boundaries in order to ensure access atomicity
     * according to the Java memory model (Java Language Specification, 17.6).
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void copyForward(Pointer from, Pointer to, UnsignedWord size) {
        // Compute largest (up to 8) common alignment of from and to
        UnsignedWord diffBits = from.xor(to);
        UnsignedWord alignBits = diffBits.not().and(diffBits.subtract(1)).and(0x7);
        UnsignedWord alignment = alignBits.add(1);
        if (alignment.equal(1)) {
            for (UnsignedWord offset = Word.zero(); offset.belowThan(size); offset = offset.add(1)) {
                to.writeByte(offset, from.readByte(offset));
            }
            return;
        }

        UnsignedWord lowerSize = from.add(alignBits).and(alignBits.not()).subtract(from).and(alignBits);
        if (size.belowThan(lowerSize)) {
            lowerSize = size.and(lowerSize);
        }
        copyUnalignedLower(from, to, lowerSize);

        UnsignedWord offset = lowerSize;
        UnsignedWord alignedSize = size.subtract(lowerSize).and(alignBits.not());
        if (alignment.equal(8)) {
            UnmanagedMemoryUtil.copyLongsForward(from.add(offset), to.add(offset), alignedSize);
            offset = offset.add(alignedSize);
        } else {
            UnsignedWord alignedEndOffset = offset.add(alignedSize);
            if (alignment.equal(4)) {
                for (; offset.belowThan(alignedEndOffset); offset = offset.add(4)) {
                    to.writeInt(offset, from.readInt(offset));
                }
            } else {
                assert alignment.equal(2);
                for (; offset.belowThan(alignedEndOffset); offset = offset.add(2)) {
                    to.writeShort(offset, from.readShort(offset));
                }
            }
        }

        UnsignedWord upperSize = size.subtract(offset);
        copyUnalignedUpper(from.add(offset), to.add(offset), upperSize);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void copyUnalignedLower(Pointer from, Pointer to, UnsignedWord length) {
        UnsignedWord offset = Word.zero();
        if (length.and(1).notEqual(0)) {
            to.writeByte(offset, from.readByte(offset));
            offset = offset.add(1);
        }
        if (length.and(2).notEqual(0)) {
            to.writeShort(offset, from.readShort(offset));
            offset = offset.add(2);
        }
        if (length.and(4).notEqual(0)) {
            to.writeInt(offset, from.readInt(offset));
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void copyUnalignedUpper(Pointer from, Pointer to, UnsignedWord length) {
        UnsignedWord offset = Word.zero();
        if (length.and(4).notEqual(0)) {
            to.writeInt(offset, from.readInt(offset));
            offset = offset.add(4);
        }
        if (length.and(2).notEqual(0)) {
            to.writeShort(offset, from.readShort(offset));
            offset = offset.add(2);
        }
        if (length.and(1).notEqual(0)) {
            to.writeByte(offset, from.readByte(offset));
        }
    }

    /**
     * Copy bytes from one memory area to another.
     *
     * If either memory area is on the Java heap, the caller must take care that no GC barriers are
     * required for the operation and the caller must be {@linkplain Uninterruptible
     * uninterruptible} or otherwise ensure that objects cannot move or be collected.
     *
     * When using this method to copy memory of Java objects, the addresses and size must align to
     * object field boundaries or array element boundaries in order to ensure access atomicity
     * according to the Java memory model (Java Language Specification, 17.6).
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void copyBackward(Pointer from, Pointer to, UnsignedWord size) {
        // Compute largest (up to 8) common alignment of from and to
        UnsignedWord diffBits = from.xor(to);
        UnsignedWord alignBits = diffBits.not().and(diffBits.subtract(1)).and(0x7);
        UnsignedWord alignment = alignBits.add(1);
        if (alignment.equal(1)) {
            UnsignedWord offset = size;
            while (offset.aboveThan(0)) {
                offset = offset.subtract(1);
                to.writeByte(offset, from.readByte(offset));
            }
            return;
        }

        /*
         * Although the copyUnaligned methods do not copy backwards, it is safe to call them here
         * because we never let them copy overlapping memory due to how we handle alignment.
         */
        UnsignedWord upperSize = from.add(size).and(alignBits);
        if (size.belowThan(upperSize)) {
            upperSize = size.and(upperSize);
        }
        UnsignedWord offset = size.subtract(upperSize);
        copyUnalignedUpper(from.add(offset), to.add(offset), upperSize);

        UnsignedWord alignedSize = offset.and(alignBits.not());
        if (alignment.equal(8)) {
            offset = offset.subtract(alignedSize);
            UnmanagedMemoryUtil.copyLongsBackward(from.add(offset), to.add(offset), alignedSize);
        } else {
            if (alignment.equal(4)) {
                while (offset.aboveOrEqual(4)) {
                    offset = offset.subtract(4);
                    to.writeInt(offset, from.readInt(offset));
                }
            } else {
                assert alignment.equal(2);
                while (offset.aboveOrEqual(2)) {
                    offset = offset.subtract(2);
                    to.writeShort(offset, from.readShort(offset));
                }
            }
        }

        copyUnalignedLower(from, to, offset);
    }

    /**
     * The copy behind {@code Unsafe.copyMemory} and the JNI region functions. The caller does not
     * say what it copies, so the element size is taken from the alignment, like HotSpot's
     * {@code Copy::conjoint_memory_atomic}: a range whose start, end or size is odd can only hold
     * single bytes and takes the byte copy; anything else keeps the word-granular copy that never
     * tears a 2-, 4- or 8-byte element.
     */
    @Uninterruptible(reason = "Memory is on the heap, copying must not be interrupted.")
    public static void copyOnHeap(Object srcBase, UnsignedWord srcOffset, Object destBase, UnsignedWord destOffset, UnsignedWord size) {
        Pointer fromPtr = Word.objectToUntrackedPointer(srcBase).add(srcOffset);
        Pointer toPtr = Word.objectToUntrackedPointer(destBase).add(destOffset);
        if (fromPtr.or(toPtr).or(size).and(1).notEqual(0)) {
            copyBytes(fromPtr, toPtr, size);
        } else {
            UnmanagedMemoryUtil.copy(fromPtr, toPtr, size);
        }
    }

    /**
     * Set the bytes of a memory area to a given value.
     *
     * If either memory area is on the Java heap, the caller must take care that no GC barriers are
     * required for the operation and the caller must be {@linkplain Uninterruptible
     * uninterruptible} or otherwise ensure that objects cannot move or be collected.
     *
     * When using this method to copy memory of Java objects, the address and size must align to
     * object field boundaries or array element boundaries in order to ensure access atomicity
     * according to the Java memory model (Java Language Specification, 17.6).
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void fill(Pointer to, UnsignedWord size, byte value) {
        long v = value & 0xffL;
        v = (v << 8) | v;
        v = (v << 16) | v;
        v = (v << 32) | v;

        UnsignedWord alignMask = Word.unsigned(0x7);
        UnsignedWord lowerSize = Word.unsigned(0x8).subtract(to).and(alignMask);
        if (lowerSize.aboveThan(0)) {
            if (size.belowThan(lowerSize)) {
                lowerSize = size.and(lowerSize);
            }
            fillUnalignedLower(to, v, lowerSize);
        }

        UnsignedWord offset = lowerSize;
        UnsignedWord alignedSize = size.subtract(offset).and(alignMask.not());
        UnmanagedMemoryUtil.fillLongs(to.add(offset), alignedSize, v);
        offset = offset.add(alignedSize);

        UnsignedWord upperSize = size.subtract(offset);
        if (upperSize.aboveThan(0)) {
            fillUnalignedUpper(to.add(offset), v, upperSize);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void fillUnalignedLower(Pointer to, long value, UnsignedWord length) {
        UnsignedWord offset = Word.zero();
        if (length.and(1).notEqual(0)) {
            to.writeByte(offset, (byte) value);
            offset = offset.add(1);
        }
        if (length.and(2).notEqual(0)) {
            to.writeShort(offset, (short) value);
            offset = offset.add(2);
        }
        if (length.and(4).notEqual(0)) {
            to.writeInt(offset, (int) value);
            offset = offset.add(4);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void fillUnalignedUpper(Pointer to, long value, UnsignedWord upperSize) {
        UnsignedWord offset = Word.zero();
        if (upperSize.and(4).notEqual(0)) {
            to.writeInt(offset, (int) value);
            offset = offset.add(4);
        }
        if (upperSize.and(2).notEqual(0)) {
            to.writeShort(offset, (short) value);
            offset = offset.add(2);
        }
        if (upperSize.and(1).notEqual(0)) {
            to.writeByte(offset, (byte) value);
            offset = offset.add(1);
        }
    }

    @Uninterruptible(reason = "Accessed memory is on the heap, code must not be interrupted.")
    static void fillOnHeap(Object destBase, long destOffset, long bytes, byte bvalue) {
        Pointer fromPtr = Word.objectToUntrackedPointer(destBase).add(Word.unsigned(destOffset));
        fill(fromPtr, Word.unsigned(bytes), bvalue);
    }

    /**
     * Copy memory from one memory area to another, unconditionally reversing bytes of an element
     * according to the passed element size. The memory areas may overlap.
     *
     * If either memory area is on the Java heap, the caller must take care that no GC barriers are
     * required for the operation and the caller must be {@linkplain Uninterruptible
     * uninterruptible} or otherwise ensure that objects cannot move or be collected.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void copySwap(Pointer from, Pointer to, UnsignedWord size, UnsignedWord elementSize) {
        assert from.isNonNull() : "address must not be NULL";
        assert to.isNonNull() : "address must not be NULL";
        assert size.unsignedRemainder(elementSize).equal(0) : "byte count must be multiple of element size";

        if (elementSize.equal(2)) {
            copySwap2(from, to, size);
        } else if (elementSize.equal(4)) {
            copySwap4(from, to, size);
        } else if (elementSize.equal(8)) {
            copySwap8(from, to, size);
        } else {
            throw VMError.shouldNotReachHere("incorrect element size");
        }
    }

    @Uninterruptible(reason = "Accessed memory is on the heap, code must not be interrupted.")
    static void copySwapOnHeap(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes, long elemSize) {
        Pointer fromPtr = Word.objectToUntrackedPointer(srcBase).add(Word.unsigned(srcOffset));
        Pointer toPtr = Word.objectToUntrackedPointer(destBase).add(Word.unsigned(destOffset));
        copySwap(fromPtr, toPtr, Word.unsigned(bytes), Word.unsigned(elemSize));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void copySwap2(Pointer from, Pointer to, UnsignedWord size) {
        if (from.aboveOrEqual(to)) {
            for (UnsignedWord offset = Word.zero(); offset.belowThan(size); offset = offset.add(2)) {
                to.writeShort(offset, Short.reverseBytes(from.readShort(offset)));
            }
        } else {
            for (UnsignedWord offset = size; offset.aboveThan(0); offset = offset.subtract(2)) {
                to.writeShort(offset.subtract(2), Short.reverseBytes(from.readShort(offset.subtract(2))));
            }
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void copySwap4(Pointer from, Pointer to, UnsignedWord size) {
        if (from.aboveOrEqual(to)) {
            for (UnsignedWord offset = Word.zero(); offset.belowThan(size); offset = offset.add(4)) {
                to.writeInt(offset, Integer.reverseBytes(from.readInt(offset)));
            }
        } else {
            for (UnsignedWord offset = size; offset.aboveThan(0); offset = offset.subtract(4)) {
                to.writeInt(offset.subtract(4), Integer.reverseBytes(from.readInt(offset.subtract(4))));
            }
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void copySwap8(Pointer from, Pointer to, UnsignedWord size) {
        if (from.aboveOrEqual(to)) {
            for (UnsignedWord offset = Word.zero(); offset.belowThan(size); offset = offset.add(8)) {
                to.writeLong(offset, Long.reverseBytes(from.readLong(offset)));
            }
        } else {
            for (UnsignedWord offset = size; offset.aboveThan(0); offset = offset.subtract(8)) {
                to.writeLong(offset.subtract(8), Long.reverseBytes(from.readLong(offset.subtract(8))));
            }
        }
    }

    /**
     * Copies between Java object arrays. Emits the necessary read/write barriers but does *NOT*
     * perform any array store checks (i.e., it is up to the caller to ensure that the arrays are
     * compatible).
     */
    public static void copyObjectArrayForward(Object fromArray, int fromIndex, Object toArray, int toIndex, int length, int layoutEncoding) {
        assert length >= 0 : length;

        UnsignedWord fromOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, fromIndex);
        UnsignedWord toOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, toIndex);
        UnsignedWord count = Word.unsigned(length);

        copyReferencesForward(fromArray, fromOffset, toArray, toOffset, count);
    }

    /**
     * Copies between Java object arrays. Emits the necessary read/write barriers but does *NOT*
     * perform any array store checks (i.e., it is up to the caller to ensure that the arrays are
     * compatible).
     */
    public static void copyObjectArrayBackward(Object fromArray, int fromIndex, Object toArray, int toIndex, int length, int layoutEncoding) {
        assert length >= 0 : length;

        UnsignedWord fromOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, fromIndex);
        UnsignedWord toOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, toIndex);
        UnsignedWord count = Word.unsigned(length);

        copyReferencesBackward(fromArray, fromOffset, toArray, toOffset, count);
    }

    /**
     * Copies object references from one Java object to another Java object. Emits the necessary
     * read/write barriers but does *NOT* perform any array store checks (i.e., if this is used for
     * copying between arrays, it is up to the caller to ensure that the arrays are compatible).
     */
    public static void copyReferencesForward(Object from, UnsignedWord fromOffset, Object to, UnsignedWord toOffset, UnsignedWord length) {
        int elementSize = ObjectLayout.singleton().getReferenceSize();
        UnsignedWord size = length.multiply(elementSize);
        UnsignedWord copied = Word.zero();
        while (copied.belowThan(size)) {
            BarrieredAccess.writeObject(to, toOffset.add(copied), BarrieredAccess.readObject(from, fromOffset.add(copied)));
            copied = copied.add(elementSize);
        }
    }

    /**
     * Copies object references from one Java object to another Java object. Emits the necessary
     * read/write barriers but does *NOT* perform any array store checks (i.e., if this is used for
     * copying between arrays, it is up to the caller to ensure that the arrays are compatible).
     */
    public static void copyReferencesBackward(Object from, UnsignedWord fromOffset, Object to, UnsignedWord toOffset, UnsignedWord length) {
        int elementSize = ObjectLayout.singleton().getReferenceSize();
        UnsignedWord remaining = length.multiply(elementSize);
        while (remaining.aboveThan(0)) {
            remaining = remaining.subtract(elementSize);
            BarrieredAccess.writeObject(to, toOffset.add(remaining), BarrieredAccess.readObject(from, fromOffset.add(remaining)));
        }
    }

    /**
     * Below this many references, references are copied one at a time. A bulk copy pays a fixed
     * cost (out-of-line calls and the range barrier) that the per-reference loop does not, and the
     * loop is cheapest when the destination needs no card marking (e.g., a young array). Measured
     * on AArch64 with the serial GC, the bulk copy breaks even with the loop at about 24 references
     * when both arrays are young and 8-byte aligned, and at about 32 when they are not.
     */
    private static final int BULK_REFERENCE_COPY_MIN_LENGTH = 32;

    /**
     * Upper bound for the bytes copied without a safepoint check. Bounds the time-to-safepoint of a
     * thread that copies a large array.
     */
    private static final int BULK_REFERENCE_COPY_CHUNK_BYTES = 64 * 1024;

    /**
     * Whether an object array copy of {@code length} elements should use
     * {@link #copyObjectArrayForwardBulk} or {@link #copyObjectArrayBackwardBulk}. Folds to
     * {@code false} for heaps that do not support bulk reference copies. Callers check this once,
     * so that the short-copy loops of {@link #copyReferencesForward} and
     * {@link #copyReferencesBackward} stay as small as possible.
     */
    public static boolean useBulkObjectArrayCopy(int length) {
        return useBulkReferenceCopy() && length >= bulkObjectArrayCopyMinLength();
    }

    /**
     * Copies between Java object arrays in bulk, front to back, with one range barrier per chunk.
     * Does *NOT* perform any array store or bounds checks. The chunks bound the time-to-safepoint of
     * long copies. Forward order keeps overlapping copies with fromIndex > toIndex correct.
     */
    @NeverInline("Bulk copies are long; keep the callers small.")
    public static void copyObjectArrayForwardBulk(Object fromArray, int fromIndex, Object toArray, int toIndex, int length, int layoutEncoding) {
        int elementSize = ObjectLayout.singleton().getReferenceSize();
        UnsignedWord fromOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, fromIndex);
        UnsignedWord toOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, toIndex);
        UnsignedWord size = Word.unsigned(length).multiply(elementSize);
        UnsignedWord chunkSize = Word.unsigned(BULK_REFERENCE_COPY_CHUNK_BYTES);
        UnsignedWord copied = Word.zero();
        while (copied.belowThan(size)) {
            UnsignedWord bytes = UnsignedUtils.min(chunkSize, size.subtract(copied));
            copyReferencesChunkForward(fromArray, fromOffset.add(copied), toArray, toOffset.add(copied), bytes, elementSize);
            copied = copied.add(bytes);
        }
    }

    /**
     * Same as {@link #copyObjectArrayForwardBulk}, but back to front. Backward order keeps
     * overlapping copies with fromIndex < toIndex correct.
     */
    @NeverInline("Bulk copies are long; keep the callers small.")
    public static void copyObjectArrayBackwardBulk(Object fromArray, int fromIndex, Object toArray, int toIndex, int length, int layoutEncoding) {
        int elementSize = ObjectLayout.singleton().getReferenceSize();
        UnsignedWord fromOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, fromIndex);
        UnsignedWord toOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, toIndex);
        UnsignedWord chunkSize = Word.unsigned(BULK_REFERENCE_COPY_CHUNK_BYTES);
        UnsignedWord remaining = Word.unsigned(length).multiply(elementSize);
        while (remaining.aboveThan(0)) {
            UnsignedWord bytes = UnsignedUtils.min(chunkSize, remaining);
            remaining = remaining.subtract(bytes);
            copyReferencesChunkBackward(fromArray, fromOffset.add(remaining), toArray, toOffset.add(remaining), bytes, elementSize);
        }
    }

    @Fold
    static int bulkObjectArrayCopyMinLength() {
        return BULK_REFERENCE_COPY_MIN_LENGTH;
    }

    @Fold
    static boolean useBulkReferenceCopy() {
        return Heap.getHeap().supportsBulkReferenceCopy();
    }

    /*
     * Each reference must be copied atomically (Java Language Specification, 17.7). An 8-byte
     * access that is not 8-byte aligned is not guaranteed to be single-copy atomic (e.g., on
     * AArch64 without FEAT_LSE2, or when it crosses a 16-byte boundary with it), so it could tear a
     * 4-byte compressed reference. The copy therefore uses 8-byte accesses only where both source
     * and destination are 8-byte aligned, and 4-byte accesses otherwise.
     */
    @Uninterruptible(reason = "Raw object addresses; no GC between the barriers and the copy.")
    private static void copyReferencesChunkForward(Object from, UnsignedWord fromOffset, Object to, UnsignedWord toOffset, UnsignedWord size, int elementSize) {
        Pointer src = Word.objectToUntrackedPointer(from).add(fromOffset);
        Pointer dst = Word.objectToUntrackedPointer(to).add(toOffset);
        UnsignedWord count = size.unsignedDivide(elementSize);
        Heap heap = Heap.getHeap();
        heap.referenceRangePreWriteBarrier(to, dst, count);
        copyReferenceMemoryForward(src, dst, size);
        heap.referenceRangePostWriteBarrier(to, dst, count);
    }

    @Uninterruptible(reason = "Raw object addresses; no GC between the barriers and the copy.")
    private static void copyReferencesChunkBackward(Object from, UnsignedWord fromOffset, Object to, UnsignedWord toOffset, UnsignedWord size, int elementSize) {
        Pointer src = Word.objectToUntrackedPointer(from).add(fromOffset);
        Pointer dst = Word.objectToUntrackedPointer(to).add(toOffset);
        UnsignedWord count = size.unsignedDivide(elementSize);
        Heap heap = Heap.getHeap();
        heap.referenceRangePreWriteBarrier(to, dst, count);
        copyReferenceMemoryBackward(src, dst, size);
        heap.referenceRangePostWriteBarrier(to, dst, count);
    }

    /**
     * Copies {@code size} bytes of references in ascending address order. {@code src}, {@code dst}
     * and {@code size} must be multiples of the reference size, which is 4 or 8 bytes.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void copyReferenceMemoryForward(Pointer src, Pointer dst, UnsignedWord size) {
        if (probability(LIKELY_PROBABILITY, src.xor(dst).and(7).equal(0))) {
            UnsignedWord offset = Word.zero();
            if (src.and(7).notEqual(0)) {
                dst.writeInt(0, src.readInt(0));
                offset = Word.unsigned(4);
            }
            UnsignedWord longBytes = size.subtract(offset).and(Word.unsigned(7).not());
            UnmanagedMemoryUtil.copyLongsForward(src.add(offset), dst.add(offset), longBytes);
            offset = offset.add(longBytes);
            if (offset.belowThan(size)) {
                dst.writeInt(offset, src.readInt(offset));
            }
        } else {
            copyIntsForward(src, dst, size);
        }
    }

    /**
     * Copies {@code size} bytes of references in descending address order. Same requirements as
     * {@link #copyReferenceMemoryForward}.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void copyReferenceMemoryBackward(Pointer src, Pointer dst, UnsignedWord size) {
        if (probability(LIKELY_PROBABILITY, src.xor(dst).and(7).equal(0))) {
            UnsignedWord end = size;
            if (src.add(end).and(7).notEqual(0)) {
                end = end.subtract(4);
                dst.writeInt(end, src.readInt(end));
            }
            UnsignedWord longBytes = end.and(Word.unsigned(7).not());
            UnsignedWord head = end.subtract(longBytes);
            UnmanagedMemoryUtil.copyLongsBackward(src.add(head), dst.add(head), longBytes);
            if (head.notEqual(0)) {
                dst.writeInt(0, src.readInt(0));
            }
        } else {
            copyIntsBackward(src, dst, size);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void copyIntsForward(Pointer src, Pointer dst, UnsignedWord size) {
        UnsignedWord offset = Word.zero();
        for (UnsignedWord next = offset.add(16); next.belowOrEqual(size); next = offset.add(16)) {
            Pointer s = src.add(offset);
            Pointer d = dst.add(offset);
            int i0 = s.readInt(0);
            int i4 = s.readInt(4);
            int i8 = s.readInt(8);
            int i12 = s.readInt(12);
            d.writeInt(0, i0);
            d.writeInt(4, i4);
            d.writeInt(8, i8);
            d.writeInt(12, i12);
            offset = next;
        }
        while (offset.belowThan(size)) {
            dst.writeInt(offset, src.readInt(offset));
            offset = offset.add(4);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void copyIntsBackward(Pointer src, Pointer dst, UnsignedWord size) {
        UnsignedWord offset = size;
        while (offset.aboveOrEqual(16)) {
            offset = offset.subtract(16);
            Pointer s = src.add(offset);
            Pointer d = dst.add(offset);
            int i12 = s.readInt(12);
            int i8 = s.readInt(8);
            int i4 = s.readInt(4);
            int i0 = s.readInt(0);
            d.writeInt(12, i12);
            d.writeInt(8, i8);
            d.writeInt(4, i4);
            d.writeInt(0, i0);
        }
        while (offset.aboveThan(0)) {
            offset = offset.subtract(4);
            dst.writeInt(offset, src.readInt(offset));
        }
    }

    /**
     * Copies between Java object arrays. Emits the necessary read/write barriers and also performs
     * array store checks.
     */
    public static void copyObjectArrayForwardWithStoreCheck(Object fromArray, int fromIndex, Object toArray, int toIndex, int length) {
        /*
         * This performs also an array bounds check in every loop iteration. However, since we do a
         * store check in every loop iteration, we are slow anyways.
         */
        Object[] from = (Object[]) fromArray;
        Object[] to = (Object[]) toArray;
        for (int i = 0; i < length; i++) {
            to[toIndex + i] = from[fromIndex + i];
        }
    }

    /**
     * Copies between Java primitive arrays. Uninterruptible itself so that the byte copy can be
     * inlined here and a short copy costs one call, not two.
     */
    @Uninterruptible(reason = "Arrays must not move")
    public static void copyPrimitiveArrayForward(Object fromArray, int fromIndex, Object toArray, int toIndex, int length, int layoutEncoding) {
        assert length >= 0 : length;
        assert fromArray != null;
        assert toArray != null;

        UnsignedWord fromOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, fromIndex);
        UnsignedWord toOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, toIndex);
        int elementSize = LayoutEncoding.getArrayIndexScale(layoutEncoding);
        UnsignedWord size = Word.unsigned(elementSize).multiply(length);

        if (elementSize == 1) {
            copyByteArray(fromArray, fromOffset, toArray, toOffset, size);
        } else {
            copyPrimitiveArrayForward(fromArray, fromOffset, toArray, toOffset, size);
        }
    }

    @IntrinsicCandidate
    @Uninterruptible(reason = "Arrays must not move")
    public static void copyPrimitiveArrayForward(Object fromArray, UnsignedWord fromOffset, Object toArray, UnsignedWord toOffset, UnsignedWord size) {
        Pointer fromPtr = Word.objectToUntrackedPointer(fromArray).add(fromOffset);
        Pointer toPtr = Word.objectToUntrackedPointer(toArray).add(toOffset);
        copyPrimitiveArrayForward(fromPtr, toPtr, size);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void copyPrimitiveArrayForward(Pointer fromPtr, Pointer toPtr, UnsignedWord size) {
        /*
         * When copying primitive array data, we know that the offsets and the size are aligned to
         * the array element size. So, in terms of the atomicity that is required for the Java
         * memory model, we are fine as long as we guarantee that we are always copying multiples of
         * element size.
         */
        UnmanagedMemoryUtil.copyForward(fromPtr, toPtr, size);
    }

    /**
     * Copies between Java primitive arrays. Uninterruptible itself so that the byte copy can be
     * inlined here and a short copy costs one call, not two.
     */
    @Uninterruptible(reason = "Arrays must not move")
    public static void copyPrimitiveArrayBackward(Object fromArray, int fromIndex, Object toArray, int toIndex, int length, int layoutEncoding) {
        assert length >= 0 : length;
        assert fromArray != null;
        assert toArray != null;

        UnsignedWord fromOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, fromIndex);
        UnsignedWord toOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, toIndex);
        int elementSize = LayoutEncoding.getArrayIndexScale(layoutEncoding);
        UnsignedWord size = Word.unsigned(elementSize).multiply(length);

        if (elementSize == 1) {
            copyByteArray(fromArray, fromOffset, toArray, toOffset, size);
        } else {
            copyPrimitiveArrayBackward(fromArray, fromOffset, toArray, toOffset, size);
        }
    }

    @IntrinsicCandidate
    @Uninterruptible(reason = "Arrays must not move")
    private static void copyPrimitiveArrayBackward(Object fromArray, UnsignedWord fromOffset, Object toArray, UnsignedWord toOffset, UnsignedWord size) {
        Pointer fromPtr = Word.objectToUntrackedPointer(fromArray).add(fromOffset);
        Pointer toPtr = Word.objectToUntrackedPointer(toArray).add(toOffset);
        copyPrimitiveArrayBackward(fromPtr, toPtr, size);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void copyPrimitiveArrayBackward(Pointer fromPtr, Pointer toPtr, UnsignedWord size) {
        // See comment in copyPrimitiveArrayForward.
        UnmanagedMemoryUtil.copyBackward(fromPtr, toPtr, size);
    }

    /**
     * Smallest byte copy handed to the C library. Below it, the loads and stores of
     * {@link #copyBytes} inline into the caller and cost less than the call; from here on, the
     * library's 16- and 32-byte vector moves win over 8-byte words.
     */
    private static final int BYTE_COPY_LIBC_THRESHOLD = 32;

    /**
     * Copies between arrays whose elements are single bytes ({@code byte[]}, {@code boolean[]});
     * the ranges may overlap in either direction.
     *
     * A byte is the smallest unit the Java memory model asks us to keep whole, so this copy may use
     * moves of any width at any alignment, including moves that overlap each other, and may hand the
     * work to the C library's {@code memmove}, which uses the widest moves the CPU has (NEON, SVE).
     * Arrays of wider elements keep the word-granular copies, which never tear an element.
     */
    @IntrinsicCandidate
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void copyByteArray(Object fromArray, UnsignedWord fromOffset, Object toArray, UnsignedWord toOffset, UnsignedWord size) {
        Pointer fromPtr = Word.objectToUntrackedPointer(fromArray).add(fromOffset);
        Pointer toPtr = Word.objectToUntrackedPointer(toArray).add(toOffset);
        copyBytes(fromPtr, toPtr, size);
    }

    /**
     * Copies {@code size} bytes with {@code memmove} semantics: the ranges may overlap in either
     * direction. Short copies use two or four windows at both ends of the range (they overlap when
     * the size is not a multiple of the window), with every load done before the first store, so
     * there is no loop and no size-dependent tail code; the size tests form a balanced tree of at
     * most three compares. Longer copies go to the C library.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void copyBytes(Pointer from, Pointer to, UnsignedWord size) {
        if (size.belowThan(16)) {
            if (size.belowThan(4)) {
                if (size.belowThan(2)) {
                    if (size.equal(1)) {
                        to.writeByte(0, from.readByte(0));
                    }
                } else {
                    UnsignedWord end = size.subtract(2);
                    short a = from.readShort(0);
                    short b = from.readShort(end);
                    to.writeShort(0, a);
                    to.writeShort(end, b);
                }
            } else if (size.belowThan(8)) {
                UnsignedWord end = size.subtract(4);
                int a = from.readInt(0);
                int b = from.readInt(end);
                to.writeInt(0, a);
                to.writeInt(end, b);
            } else {
                UnsignedWord end = size.subtract(8);
                long a = from.readLong(0);
                long b = from.readLong(end);
                to.writeLong(0, a);
                to.writeLong(end, b);
            }
        } else if (size.belowThan(BYTE_COPY_LIBC_THRESHOLD)) {
            UnsignedWord end = size.subtract(16);
            long a0 = from.readLong(0);
            long a1 = from.readLong(8);
            long b0 = from.readLong(end);
            long b1 = from.readLong(end.add(8));
            to.writeLong(0, a0);
            to.writeLong(8, a1);
            to.writeLong(end, b0);
            to.writeLong(end.add(8), b1);
        } else {
            LibC.memmove(to, from, size);
        }
    }

    private JavaMemoryUtil() {
    }
}
