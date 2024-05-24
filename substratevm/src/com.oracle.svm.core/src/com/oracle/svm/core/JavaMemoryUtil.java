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

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.word.BarrieredAccess;
import jdk.graal.compiler.word.Word;

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
            for (UnsignedWord offset = WordFactory.zero(); offset.belowThan(size); offset = offset.add(1)) {
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
        UnsignedWord offset = WordFactory.zero();
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
        UnsignedWord offset = WordFactory.zero();
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

    @Uninterruptible(reason = "Memory is on the heap, copying must not be interrupted.")
    public static void copyOnHeap(Object srcBase, UnsignedWord srcOffset, Object destBase, UnsignedWord destOffset, UnsignedWord size) {
        Word fromPtr = Word.objectToUntrackedPointer(srcBase).add(srcOffset);
        Word toPtr = Word.objectToUntrackedPointer(destBase).add(destOffset);
        UnmanagedMemoryUtil.copy(fromPtr, toPtr, size);
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

        UnsignedWord alignMask = WordFactory.unsigned(0x7);
        UnsignedWord lowerSize = WordFactory.unsigned(0x8).subtract(to).and(alignMask);
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
        UnsignedWord offset = WordFactory.zero();
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
        UnsignedWord offset = WordFactory.zero();
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
        Word fromPtr = Word.objectToUntrackedPointer(destBase).add(WordFactory.unsigned(destOffset));
        fill(fromPtr, WordFactory.unsigned(bytes), bvalue);
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
        Word fromPtr = Word.objectToUntrackedPointer(srcBase).add(WordFactory.unsigned(srcOffset));
        Word toPtr = Word.objectToUntrackedPointer(destBase).add(WordFactory.unsigned(destOffset));
        copySwap(fromPtr, toPtr, WordFactory.unsigned(bytes), WordFactory.unsigned(elemSize));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void copySwap2(Pointer from, Pointer to, UnsignedWord size) {
        if (from.aboveThan(to)) {
            for (UnsignedWord offset = WordFactory.zero(); offset.belowThan(size); offset = offset.add(2)) {
                to.writeShort(offset, Short.reverseBytes(from.readShort(offset)));
            }
        } else if (from.belowThan(to)) {
            for (UnsignedWord offset = size; offset.aboveThan(0); offset = offset.subtract(2)) {
                to.writeShort(offset.subtract(2), Short.reverseBytes(from.readShort(offset.subtract(2))));
            }
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void copySwap4(Pointer from, Pointer to, UnsignedWord size) {
        if (from.aboveThan(to)) {
            for (UnsignedWord offset = WordFactory.zero(); offset.belowThan(size); offset = offset.add(4)) {
                to.writeInt(offset, Integer.reverseBytes(from.readInt(offset)));
            }
        } else if (from.belowThan(to)) {
            for (UnsignedWord offset = size; offset.aboveThan(0); offset = offset.subtract(4)) {
                to.writeInt(offset.subtract(4), Integer.reverseBytes(from.readInt(offset.subtract(4))));
            }
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void copySwap8(Pointer from, Pointer to, UnsignedWord size) {
        if (from.aboveThan(to)) {
            for (UnsignedWord offset = WordFactory.zero(); offset.belowThan(size); offset = offset.add(8)) {
                to.writeLong(offset, Long.reverseBytes(from.readLong(offset)));
            }
        } else if (from.belowThan(to)) {
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
        assert length >= 0;

        UnsignedWord fromOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, fromIndex);
        UnsignedWord toOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, toIndex);
        UnsignedWord count = WordFactory.unsigned(length);

        copyReferencesForward(fromArray, fromOffset, toArray, toOffset, count);
    }

    /**
     * Copies between Java object arrays. Emits the necessary read/write barriers but does *NOT*
     * perform any array store checks (i.e., it is up to the caller to ensure that the arrays are
     * compatible).
     */
    public static void copyObjectArrayBackward(Object fromArray, int fromIndex, Object toArray, int toIndex, int length, int layoutEncoding) {
        assert length >= 0;

        UnsignedWord fromOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, fromIndex);
        UnsignedWord toOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, toIndex);
        UnsignedWord count = WordFactory.unsigned(length);

        copyReferencesBackward(fromArray, fromOffset, toArray, toOffset, count);
    }

    /**
     * Copies object references from one Java object to another Java object. Emits the necessary
     * read/write barriers but does *NOT* perform any array store checks (i.e., if this is used for
     * copying between arrays, it is up to the caller to ensure that the arrays are compatible).
     */
    public static void copyReferencesForward(Object from, UnsignedWord fromOffset, Object to, UnsignedWord toOffset, UnsignedWord length) {
        int elementSize = ConfigurationValues.getObjectLayout().getReferenceSize();
        UnsignedWord size = length.multiply(elementSize);
        UnsignedWord copied = WordFactory.zero();
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
        int elementSize = ConfigurationValues.getObjectLayout().getReferenceSize();
        UnsignedWord remaining = length.multiply(elementSize);
        while (remaining.aboveThan(0)) {
            remaining = remaining.subtract(elementSize);
            BarrieredAccess.writeObject(to, toOffset.add(remaining), BarrieredAccess.readObject(from, fromOffset.add(remaining)));
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
     * Copies between Java primitive arrays.
     */
    public static void copyPrimitiveArrayForward(Object fromArray, int fromIndex, Object toArray, int toIndex, int length, int layoutEncoding) {
        assert length >= 0;
        assert fromArray != null;
        assert toArray != null;

        UnsignedWord fromOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, fromIndex);
        UnsignedWord toOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, toIndex);
        UnsignedWord elementSize = WordFactory.unsigned(LayoutEncoding.getArrayIndexScale(layoutEncoding));
        UnsignedWord size = elementSize.multiply(length);

        copyPrimitiveArrayForward(fromArray, fromOffset, toArray, toOffset, size);
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
     * Copies between Java primitive arrays.
     */
    public static void copyPrimitiveArrayBackward(Object fromArray, int fromIndex, Object toArray, int toIndex, int length, int layoutEncoding) {
        assert length >= 0;
        assert fromArray != null;
        assert toArray != null;

        UnsignedWord fromOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, fromIndex);
        UnsignedWord toOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, toIndex);
        UnsignedWord elementSize = WordFactory.unsigned(LayoutEncoding.getArrayIndexScale(layoutEncoding));
        UnsignedWord size = elementSize.multiply(length);

        copyPrimitiveArrayBackward(fromArray, fromOffset, toArray, toOffset, size);
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

    private JavaMemoryUtil() {
    }
}
