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
import com.oracle.svm.core.util.VMError;

public final class MemoryUtil {

    @Uninterruptible(reason = "Arguments may be managed objects")
    public static void copyConjointMemoryAtomic(Pointer from, Pointer to, UnsignedWord size) {
        UnsignedWord bits = from.or(to).or(size);

        // (Note: We could improve performance by ignoring the low bits of size,
        // and putting a short cleanup loop after each bulk copy loop.
        // There are plenty of other ways to make this faster also,
        // and it's a slippery slope. For now, let's keep this code simple
        // since the simplicity helps clarify the atomicity semantics of
        // this operation. There are also CPU-specific assembly versions
        // which may or may not want to include such optimizations.)

        if (bits.unsignedRemainder(8).equal(0)) {
            copyConjointLongsAtomic(from, to, size);
        } else if (bits.unsignedRemainder(4).equal(0)) {
            copyConjointIntsAtomic(from, to, size);
        } else if (bits.unsignedRemainder(2).equal(0)) {
            copyConjointShortsAtomic(from, to, size);
        } else {
            copyConjointBytesAtomic(from, to, size);
        }
    }

    @Uninterruptible(reason = "Arguments may be managed objects")
    private static void copyConjointBytesAtomic(Pointer from, Pointer to, UnsignedWord size) {
        if (from.aboveThan(to)) {
            for (UnsignedWord offset = WordFactory.zero(); offset.belowThan(size); offset = offset.add(1)) {
                to.writeByte(offset, from.readByte(offset));
            }
        } else if (from.belowThan(to)) {
            for (UnsignedWord offset = size; offset.aboveThan(0); offset = offset.subtract(1)) {
                to.writeByte(offset.subtract(1), from.readByte(offset.subtract(1)));
            }
        }
    }

    @Uninterruptible(reason = "Arguments may be managed objects")
    private static void copyConjointShortsAtomic(Pointer from, Pointer to, UnsignedWord size) {
        if (from.aboveThan(to)) {
            for (UnsignedWord offset = WordFactory.zero(); offset.belowThan(size); offset = offset.add(2)) {
                to.writeShort(offset, from.readShort(offset));
            }
        } else if (from.belowThan(to)) {
            for (UnsignedWord offset = size; offset.aboveThan(0); offset = offset.subtract(2)) {
                to.writeShort(offset.subtract(2), from.readShort(offset.subtract(2)));
            }
        }
    }

    @Uninterruptible(reason = "Arguments may be managed objects")
    private static void copyConjointIntsAtomic(Pointer from, Pointer to, UnsignedWord size) {
        if (from.aboveThan(to)) {
            for (UnsignedWord offset = WordFactory.zero(); offset.belowThan(size); offset = offset.add(4)) {
                to.writeInt(offset, from.readInt(offset));
            }
        } else if (from.belowThan(to)) {
            for (UnsignedWord offset = size; offset.aboveThan(0); offset = offset.subtract(4)) {
                to.writeInt(offset.subtract(4), from.readInt(offset.subtract(4)));
            }
        }
    }

    @Uninterruptible(reason = "Arguments may be managed objects")
    private static void copyConjointLongsAtomic(Pointer from, Pointer to, UnsignedWord size) {
        if (from.aboveThan(to)) {
            for (UnsignedWord offset = WordFactory.zero(); offset.belowThan(size); offset = offset.add(8)) {
                to.writeLong(offset, from.readLong(offset));
            }
        } else if (from.belowThan(to)) {
            for (UnsignedWord offset = size; offset.aboveThan(0); offset = offset.subtract(8)) {
                to.writeLong(offset.subtract(8), from.readLong(offset.subtract(8)));
            }
        }
    }

    @Uninterruptible(reason = "Arguments may be managed objects")
    public static void fillToMemoryAtomic(Pointer to, UnsignedWord size, byte value) {
        UnsignedWord bits = to.or(size);
        if (bits.unsignedRemainder(8).equal(0)) {
            long fill = value & 0xffL; // zero-extend
            if (fill != 0) {
                fill += fill << 8;
                fill += fill << 16;
                fill += fill << 32;
            }
            for (UnsignedWord offset = WordFactory.zero(); offset.belowThan(size); offset = offset.add(8)) {
                to.writeLong(offset, fill);
            }

        } else if (bits.unsignedRemainder(4).equal(0)) {
            int fill = value & 0xff; // zero-extend
            if (fill != 0) {
                fill += fill << 8;
                fill += fill << 16;
            }
            for (UnsignedWord offset = WordFactory.zero(); offset.belowThan(size); offset = offset.add(4)) {
                to.writeInt(offset, fill);
            }

        } else if (bits.unsignedRemainder(2).equal(0)) {
            short fill = (short) (value & 0xff); // zero-extend
            if (fill != 0) {
                fill += fill << 8;
            }
            for (UnsignedWord offset = WordFactory.zero(); offset.belowThan(size); offset = offset.add(2)) {
                to.writeShort(offset, fill);
            }

        } else {
            byte fill = value;
            for (UnsignedWord offset = WordFactory.zero(); offset.belowThan(size); offset = offset.add(1)) {
                to.writeByte(offset, fill);
            }
        }
    }

    /**
     * Copy memory and unconditionally reverse bytes based on the element size.
     */
    @Uninterruptible(reason = "Arguments may be managed objects")
    public static void copyConjointSwap(Pointer from, Pointer to, UnsignedWord size, UnsignedWord elementSize) {
        assert from.isNonNull() : "address must not be NULL";
        assert to.isNonNull() : "address must not be NULL";
        assert size.unsignedRemainder(elementSize).equal(0) : "byte count must be multiple of element size";

        if (elementSize.equal(2)) {
            copyConjointSwap2(from, to, size);
        } else if (elementSize.equal(4)) {
            copyConjointSwap4(from, to, size);
        } else if (elementSize.equal(8)) {
            copyConjointSwap8(from, to, size);
        } else {
            throw VMError.shouldNotReachHere("incorrect element size");
        }
    }

    @Uninterruptible(reason = "Arguments may be managed objects")
    private static void copyConjointSwap2(Pointer from, Pointer to, UnsignedWord size) {
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

    @Uninterruptible(reason = "Arguments may be managed objects")
    private static void copyConjointSwap4(Pointer from, Pointer to, UnsignedWord size) {
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

    @Uninterruptible(reason = "Arguments may be managed objects")
    private static void copyConjointSwap8(Pointer from, Pointer to, UnsignedWord size) {
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
}
