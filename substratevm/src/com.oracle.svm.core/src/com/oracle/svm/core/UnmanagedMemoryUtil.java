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

import jdk.graal.compiler.word.Word;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.config.ConfigurationValues;

/**
 * The methods in this class are mainly used to fill or copy unmanaged (i.e., <b>non</b>-Java heap)
 * memory. None of the methods cares about Java semantics like GC barriers or the Java memory model.
 * The valid use cases are listed below. For all other use cases, use {@link JavaMemoryUtil}
 * instead.
 * <ul>
 * <li>Copying between unmanaged memory.</li>
 * <li>Filling unmanaged memory.</li>
 * </ul>
 *
 * <p>
 * All operations in this class that copy memory guarantee to always use the largest-possible data
 * type for each individual read and write operation, e.g.:
 * <ul>
 * <li>Copying 60 bytes may be split into 7 operations that copy 8, 8, 8, 8, 8, 8 and 4 bytes.
 * However, it is guaranteed that no operation will copy less than 4 bytes.</li>
 * <li>Copying 15 bytes may be split into 4 operations that copy 8, 4, 2, and 1 bytes.</li>
 * </ul>
 * <p>
 * In some situations (e.g., during a serial GC or if it is guaranteed that all involved objects are
 * not yet visible to other threads), the methods in this class may also be used for objects that
 * live in the Java heap. However, those usages should be kept to a minimum.
 */
public final class UnmanagedMemoryUtil {
    /**
     * Copy bytes from one memory area to another. The memory areas may overlap. Guarantees to use
     * the largest-possible data type for each individual read and write operation.
     */
    @IntrinsicCandidate
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void copy(Pointer from, Pointer to, UnsignedWord size) {
        if (from.aboveThan(to)) {
            copyForward(from, to, size);
        } else if (from.belowThan(to)) {
            copyBackward(from, to, size);
        }
    }

    /**
     * Copy bytes from one memory area to another. Guarantees to use the largest-possible data type
     * for each individual read and write operation.
     */
    @IntrinsicCandidate
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void copyForward(Pointer from, Pointer to, UnsignedWord size) {
        UnsignedWord alignBits = Word.unsigned(0x7);
        UnsignedWord alignedSize = size.and(alignBits.not());
        copyLongsForward(from, to, alignedSize);

        if (alignedSize.notEqual(size)) {
            UnsignedWord offset = alignedSize;
            if (size.and(4).notEqual(0)) {
                to.writeInt(offset, from.readInt(offset));
                offset = offset.add(4);
            }
            if (size.and(2).notEqual(0)) {
                to.writeShort(offset, from.readShort(offset));
                offset = offset.add(2);
            }
            if (size.and(1).notEqual(0)) {
                to.writeByte(offset, from.readByte(offset));
                offset = offset.add(1);
            }
            assert offset.equal(size);
        }
    }

    /**
     * Copy bytes from one memory area to another. Guarantees to use the largest-possible data type
     * for each individual read and write operation.
     */
    @IntrinsicCandidate
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void copyBackward(Pointer from, Pointer to, UnsignedWord size) {
        UnsignedWord alignBits = Word.unsigned(0x7);
        UnsignedWord alignedSize = size.and(alignBits.not());
        UnsignedWord unalignedSize = size.subtract(alignedSize);
        copyLongsBackward(from.add(unalignedSize), to.add(unalignedSize), alignedSize);

        if (unalignedSize.aboveThan(0)) {
            UnsignedWord offset = unalignedSize;
            if (size.and(4).notEqual(0)) {
                offset = offset.subtract(4);
                to.writeInt(offset, from.readInt(offset));
            }
            if (size.and(2).notEqual(0)) {
                offset = offset.subtract(2);
                to.writeShort(offset, from.readShort(offset));
            }
            if (size.and(1).notEqual(0)) {
                offset = offset.subtract(1);
                to.writeByte(offset, from.readByte(offset));
            }
            assert offset.equal(0);
        }
    }

    /**
     * Copy bytes from one memory area to another.
     */
    @IntrinsicCandidate
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void copyLongsForward(Pointer from, Pointer to, UnsignedWord size) {
        assert size.unsignedRemainder(8).equal(0);
        UnsignedWord offset = Word.zero();
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

    @IntrinsicCandidate
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void copyWordsForward(Pointer from, Pointer to, UnsignedWord size) {
        int wordSize = ConfigurationValues.getTarget().wordSize;
        int stepSize = 4 * wordSize;
        Pointer src = from;
        Pointer dst = to;
        Pointer srcEnd = src.add(size);

        while (src.add(stepSize).belowOrEqual(srcEnd)) {
            WordBase w0 = src.readWord(0 * wordSize);
            WordBase w8 = src.readWord(1 * wordSize);
            WordBase w16 = src.readWord(2 * wordSize);
            WordBase w24 = src.readWord(3 * wordSize);
            dst.writeWord(0 * wordSize, w0);
            dst.writeWord(1 * wordSize, w8);
            dst.writeWord(2 * wordSize, w16);
            dst.writeWord(3 * wordSize, w24);

            src = src.add(stepSize);
            dst = dst.add(stepSize);
        }

        while (src.belowThan(srcEnd)) {
            dst.writeWord(Word.zero(), src.readWord(Word.zero()));
            src = src.add(wordSize);
            dst = dst.add(wordSize);
        }

        assert src.equal(srcEnd);
        assert dst.equal(to.add(size));
    }

    /**
     * Copy bytes from one memory area to another.
     */
    @IntrinsicCandidate
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void copyLongsBackward(Pointer from, Pointer to, UnsignedWord size) {
        assert size.unsignedRemainder(8).equal(0);
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
    private static UnsignedWord compareLongs(Pointer x, Pointer y, UnsignedWord size) {
        assert size.unsignedRemainder(8).equal(0);
        UnsignedWord offset = Word.zero();
        while (offset.belowThan(size)) {
            if (x.readLong(offset) != y.readLong(offset)) {
                return offset;
            }
            offset = offset.add(Long.BYTES);
        }
        return offset;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static UnsignedWord compareBytes(Pointer x, Pointer y, UnsignedWord size) {
        UnsignedWord offset = Word.zero();
        while (offset.belowThan(size)) {
            if (x.readByte(offset) != y.readByte(offset)) {
                return offset;
            }
            offset = offset.add(1);
        }
        return offset;
    }

    /**
     * Compares two memory areas. Returns the number of bytes from the beginning which are
     * equivalent, which, if a difference is found, is the offset of the first different byte.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord compare(Pointer x, Pointer y, UnsignedWord size) {
        UnsignedWord alignBits = Word.unsigned(0x7);
        UnsignedWord alignedSize = size.and(alignBits.not());
        UnsignedWord offset = compareLongs(x, y, alignedSize);
        return offset.add(compareBytes(x.add(offset), y.add(offset), size.subtract(offset)));
    }

    /**
     * Set the bytes of a memory area to a given value. Does *NOT* guarantee any size for the
     * individual read/write operations and therefore does not guarantee any atomicity.
     */
    @IntrinsicCandidate
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void fill(Pointer to, UnsignedWord size, byte value) {
        long v = value & 0xffL;
        v = (v << 8) | v;
        v = (v << 16) | v;
        v = (v << 32) | v;

        UnsignedWord alignBits = Word.unsigned(0x7);
        UnsignedWord alignedSize = size.and(alignBits.not());
        fillLongs(to, alignedSize, v);

        if (alignedSize.notEqual(size)) {
            UnsignedWord offset = alignedSize;
            if (size.and(4).notEqual(0)) {
                to.writeInt(offset, (int) v);
                offset = offset.add(4);
            }
            if (size.and(2).notEqual(0)) {
                to.writeShort(offset, (short) v);
                offset = offset.add(2);
            }
            if (size.and(1).notEqual(0)) {
                to.writeByte(offset, (byte) v);
                offset = offset.add(1);
            }
            assert offset.equal(size);
        }
    }

    /**
     * Set the bytes of a memory area to a given value.
     */
    @IntrinsicCandidate
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void fillLongs(Pointer to, UnsignedWord size, long longValue) {
        assert size.unsignedRemainder(8).equal(0);
        UnsignedWord offset = Word.zero();
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

    private UnmanagedMemoryUtil() {
    }
}
