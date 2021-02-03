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

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;

/**
 * Used to fill or copy native memory (i.e., *non*-Java heap memory). None of the methods in this
 * class care about Java semantics like GC barriers or the Java memory model. They don't even
 * guarantee the rather relaxed atomicity requirements of {@code Unsafe}.
 *
 * If atomicity is needed, use {@link JavaMemoryUtil} but please read the Javadoc on
 * {@link JavaMemoryUtil} first as there are still a few pitfalls.
 */
public final class UnmanagedMemoryUtil {
    /**
     * Copy bytes from one memory area to another. The memory areas may overlap.
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
        assert from.aboveThan(to);
        UnsignedWord lowerSize = WordFactory.unsigned(0x8).subtract(from).and(0x7);
        if (lowerSize.aboveThan(0)) {
            if (size.belowThan(lowerSize)) {
                lowerSize = size.and(lowerSize);
            }
            copyUnalignedLower(from, to, lowerSize);
        }

        // TEMP (chaeubl): test performance - not sure if the unrolling is worth it
        UnsignedWord offset = lowerSize;
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
        for (UnsignedWord next = offset.add(8); next.belowOrEqual(size); next = offset.add(8)) {
            to.writeLong(offset, from.readLong(offset));
            offset = next;
        }

        if (offset.belowThan(size)) {
            UnsignedWord upperSize = size.subtract(offset);
            copyUnalignedUpper(from.add(offset), to.add(offset), upperSize);
        }
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
    private static void copyBackward(Pointer from, Pointer to, UnsignedWord size) {
        assert from.belowThan(to);
        /*
         * Although the copyUnaligned methods do not copy backwards, it is safe to call them here
         * because we never let them copy overlapping memory due to how we handle alignment.
         */
        UnsignedWord offset = size;
        UnsignedWord upperSize = from.add(size).and(0x7);
        if (upperSize.aboveThan(0)) {
            if (size.belowThan(upperSize)) {
                upperSize = size.and(upperSize);
            }
            offset = offset.subtract(upperSize);
            copyUnalignedUpper(from.add(offset), to.add(offset), upperSize);
        }

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

        if (offset.aboveThan(0)) {
            copyUnalignedLower(from, to, offset);
        }
    }

    /**
     * Set the bytes of a memory area to a given value.
     */
    @Uninterruptible(reason = "Called from uninterruptible code, but may be inlined.", mayBeInlined = true)
    public static void fill(Pointer to, UnsignedWord size, byte value) {
        // Even though this calls the atomic implementation, this still doesn't guarantee atomicity
        // because this method may be intrinsified.
        JavaMemoryUtil.fill(to, size, value);
    }

    private UnmanagedMemoryUtil() {
    }
}
