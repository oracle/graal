/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.sun.max.lang;

import java.util.*;

/**
 * A word width value describes how many bits there are in a machine word.
 */
public enum WordWidth {

    BITS_8(8, byte.class, Byte.MIN_VALUE, Byte.MAX_VALUE, 3),
    BITS_16(16, short.class, Short.MIN_VALUE, Short.MAX_VALUE, 4),
    BITS_32(32, int.class, Integer.MIN_VALUE, Integer.MAX_VALUE, 5),
    BITS_64(64, long.class, Long.MIN_VALUE, Long.MAX_VALUE, 6);

    public static final List<WordWidth> VALUES = java.util.Arrays.asList(values());

    /**
     * Number of bits in a Word.
     * This must be a positive power of two.
     */
    public final int numberOfBits;

    /**
     * Log2 of the number of bits.
     */
    public final int log2numberOfBits;

    /**
     * Log2 of the number of bytes.
     */
    public final int log2numberOfBytes;

    /**
     * Number of bytes in a Word.
     * This must be a positive power of two.
     */
    public final int numberOfBytes;

    public final Class canonicalPrimitiveType;
    public final long min;
    public final long max;

    private WordWidth(int numberOfBits, Class canonicalPrimitiveType, long min, long max, int log2numberOfBits) {
        this.numberOfBits = numberOfBits;
        this.numberOfBytes = numberOfBits / 8;
        this.canonicalPrimitiveType = canonicalPrimitiveType;
        this.min = min;
        this.max = max;
        this.log2numberOfBits = log2numberOfBits;
        this.log2numberOfBytes = log2numberOfBits - 3;
    }

    public boolean lessThan(WordWidth other) {
        return numberOfBits < other.numberOfBits;
    }

    public boolean lessEqual(WordWidth other) {
        return numberOfBits <= other.numberOfBits;
    }

    public boolean greaterThan(WordWidth other) {
        return numberOfBits > other.numberOfBits;
    }

    public boolean greaterEqual(WordWidth other) {
        return numberOfBits >= other.numberOfBits;
    }

    @Override
    public String toString() {
        return Integer.toString(numberOfBits);
    }

    public static WordWidth fromInt(int wordWidth) {
        if (wordWidth <= 8) {
            return WordWidth.BITS_8;
        }
        if (wordWidth <= 16) {
            return WordWidth.BITS_16;
        }
        if (wordWidth <= 32) {
            return WordWidth.BITS_32;
        }
        return WordWidth.BITS_64;
    }

    /**
     * @return which word width is minimally required to represent all the non-one bits in the signed argument, and a sign bit
     */
    public static WordWidth signedEffective(int signed) {
        return fromInt(Ints.numberOfEffectiveSignedBits(signed));
    }

    /**
     * @return which word width is minimally required to represent all the non-zero bits in the unsigned argument
     */
    public static WordWidth unsignedEffective(int unsigned) {
        return fromInt(Ints.numberOfEffectiveUnsignedBits(unsigned));
    }

    /**
     * @return which word width is minimally required to represent all the non-one bits in the signed argument, and a sign bit
     */
    public static WordWidth signedEffective(long signed) {
        return fromInt(Longs.numberOfEffectiveSignedBits(signed));
    }

    /**
     * @return which word width is minimally required to represent all the non-zero bits in the unsigned argument
     */
    public static WordWidth unsignedEffective(long unsigned) {
        return fromInt(Longs.numberOfEffectiveUnsignedBits(unsigned));
    }
}
