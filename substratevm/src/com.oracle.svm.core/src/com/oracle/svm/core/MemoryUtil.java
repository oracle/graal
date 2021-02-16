/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.util.VMError;

public final class MemoryUtil {

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

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void copyForward(Pointer from, Pointer to, UnsignedWord size) {
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
            copyAlignedLongsForward(from.add(offset), to.add(offset), alignedSize);
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

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void copyAlignedLongsForward(Pointer from, Pointer to, UnsignedWord size) {
        assert from.aboveOrEqual(to);
        UnsignedWord offset = WordFactory.zero();
        for (UnsignedWord next = offset.add(32); next.belowOrEqual(size); next = offset.add(32)) {
            Pointer src = from.add(offset);
            Pointer dst = to.add(offset);
            long l0 = src.readLong(0);
            long l8 = src.readLong(8);
            long l16 = src.readLong(16);
            long l24 = src.readLong(24);
            dst.writeLong(0, l0);
            dst.writeLong(8, l8);
            dst.writeLong(16, l16);
            dst.writeLong(24, l24);
            offset = next;
        }
        while (offset.belowThan(size)) {
            to.writeLong(offset, from.readLong(offset));
            offset = offset.add(8);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void copyAlignedLongsBackward(Pointer from, Pointer to, UnsignedWord size) {
        assert from.belowOrEqual(to);
        UnsignedWord offset = size;
        while (offset.aboveOrEqual(32)) {
            offset = offset.subtract(32);
            Pointer src = from.add(offset);
            Pointer dst = to.add(offset);
            long l24 = src.readLong(24);
            long l16 = src.readLong(16);
            long l8 = src.readLong(8);
            long l0 = src.readLong(0);
            dst.writeLong(24, l24);
            dst.writeLong(16, l16);
            dst.writeLong(8, l8);
            dst.writeLong(0, l0);
        }
        while (offset.aboveOrEqual(8)) {
            offset = offset.subtract(8);
            to.writeLong(offset, from.readLong(offset));
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void copyBackward(Pointer from, Pointer to, UnsignedWord size) {
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
            copyAlignedLongsBackward(from.add(offset), to.add(offset), alignedSize);
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

    /** Implementation of {@code Unsafe.copyMemory}. */
    public static void unsafeCopyMemory(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes) {
        // Stricter about access atomicity than required by Unsafe.copyMemory Javadoc
        if (srcBase != null || destBase != null) {
            copyOnHeap(srcBase, srcOffset, destBase, destOffset, bytes);
        } else {
            copy(WordFactory.pointer(srcOffset), WordFactory.pointer(destOffset), WordFactory.unsigned(bytes));
        }
    }

    @Uninterruptible(reason = "Memory is on the heap, copying must not be interrupted.")
    private static void copyOnHeap(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes) {
        copy(Word.objectToUntrackedPointer(srcBase).add(WordFactory.unsigned(srcOffset)),
                        Word.objectToUntrackedPointer(destBase).add(WordFactory.unsigned(destOffset)),
                        WordFactory.unsigned(bytes));
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
    @Uninterruptible(reason = "Called from uninterruptible code, but may be inlined.", mayBeInlined = true)
    public static void fill(Pointer to, UnsignedWord size, byte value) {
        long v = value & 0xffL;
        v = (v << 8) | v;
        v = (v << 16) | v;
        v = (v << 32) | v;

        UnsignedWord offset = WordFactory.zero();
        UnsignedWord alignMask = WordFactory.unsigned(0x7);

        UnsignedWord lowerSize = to.add(alignMask).and(alignMask.not()).subtract(to);
        if (size.belowThan(lowerSize)) {
            lowerSize = size.and(lowerSize);
        }
        if (lowerSize.and(1).notEqual(0)) {
            to.writeByte(offset, (byte) v);
            offset = offset.add(1);
        }
        if (lowerSize.and(2).notEqual(0)) {
            to.writeShort(offset, (short) v);
            offset = offset.add(2);
        }
        if (lowerSize.and(4).notEqual(0)) {
            to.writeInt(offset, (int) v);
            offset = offset.add(4);
        }

        UnsignedWord alignedSize = size.subtract(lowerSize).and(alignMask.not());
        fillLongsAligned(to.add(lowerSize), alignedSize, v);
        offset = lowerSize.add(alignedSize);

        UnsignedWord upperSize = size.subtract(offset);
        if (upperSize.and(4).notEqual(0)) {
            to.writeInt(offset, (int) v);
            offset = offset.add(4);
        }
        if (upperSize.and(2).notEqual(0)) {
            to.writeShort(offset, (short) v);
            offset = offset.add(2);
        }
        if (upperSize.and(1).notEqual(0)) {
            to.writeByte(offset, (byte) v);
            offset = offset.add(1);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void fillLongsAligned(Pointer to, UnsignedWord size, long longValue) {
        UnsignedWord offset = WordFactory.zero();
        for (UnsignedWord next = offset.add(32); next.belowOrEqual(size); next = offset.add(32)) {
            Pointer p = to.add(offset);
            p.writeLong(0, longValue);
            p.writeLong(8, longValue);
            p.writeLong(16, longValue);
            p.writeLong(24, longValue);
            offset = next;
        }
        for (; offset.belowThan(size); offset = offset.add(8)) {
            to.writeLong(offset, longValue);
        }
    }

    /** Implementation of {@code Unsafe.setMemory}. */
    public static void unsafeSetMemory(Object destBase, long destOffset, long bytes, byte bvalue) {
        // Stricter about access atomicity than required by Unsafe.setMemory Javadoc
        if (destBase != null) {
            fillOnHeap(destBase, destOffset, bytes, bvalue);
        } else {
            fill(WordFactory.pointer(destOffset), WordFactory.unsigned(bytes), bvalue);
        }
    }

    @Uninterruptible(reason = "Accessed memory is on the heap, code must not be interrupted.")
    private static void fillOnHeap(Object destBase, long destOffset, long bytes, byte bvalue) {
        MemoryUtil.fill(Word.objectToUntrackedPointer(destBase).add(WordFactory.unsigned(destOffset)), WordFactory.unsigned(bytes), bvalue);
    }

    /**
     * Copy memory from one memory area to another, unconditionally reversing bytes of an element
     * according to the passed element size. The memory areas may overlap.
     *
     * This method does <b>NOT</b> guarantee access atomicity for fields and array elements
     * according to the Java memory model (Java Language Specification 17.6).
     *
     * If either memory area is on the Java heap, the caller must take care that no GC barriers are
     * required for the operation and the caller must be {@linkplain Uninterruptible
     * uninterruptible} or otherwise ensure that objects cannot move or be collected.
     */
    @Uninterruptible(reason = "Called from uninterruptible code, but may be inlined.", mayBeInlined = true)
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
    private static void copySwapOnHeap(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes, long elemSize) {
        copySwap(Word.objectToUntrackedPointer(srcBase).add(WordFactory.unsigned(srcOffset)),
                        Word.objectToUntrackedPointer(destBase).add(WordFactory.unsigned(destOffset)),
                        WordFactory.unsigned(bytes), WordFactory.unsigned(elemSize));
    }

    /** Implementation of {@code Unsafe.copySwapMemory}. */
    public static void unsafeCopySwapMemory(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes, long elemSize) {
        if (srcBase != null || destBase != null) {
            copySwapOnHeap(srcBase, srcOffset, destBase, destOffset, bytes, elemSize);
        } else {
            copySwap(WordFactory.unsigned(srcOffset), WordFactory.unsigned(destOffset), WordFactory.unsigned(bytes), WordFactory.unsigned(elemSize));
        }
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

    private MemoryUtil() {
    }
}
