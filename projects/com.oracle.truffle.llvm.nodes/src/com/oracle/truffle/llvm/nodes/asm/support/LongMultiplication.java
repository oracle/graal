/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.nodes.asm.support;

/*
 * This functionality is in the standard class library since Java 9:
 * https://bugs.openjdk.java.net/browse/JDK-5100935
 */
public class LongMultiplication {
    private static final long MASK32 = 0xFFFFFFFFL;
    private static final int SHIFT32 = 32;

    /**
     * Returns as a {@code long} the most significant 64 bits of the 128-bit product of two 64-bit
     * factors.
     *
     * @param x the first value
     * @param y the second value
     * @return the result
     */
    public static long multiplyHigh(long x, long y) {
        if (x < 0 || y < 0) {
            // Use technique from section 8-2 of Henry S. Warren, Jr.,
            // Hacker's Delight (2nd ed.) (Addison Wesley, 2013), 173-174.
            long x1 = x >> SHIFT32;
            long x2 = x & MASK32;
            long y1 = y >> SHIFT32;
            long y2 = y & MASK32;
            long z2 = x2 * y2;
            long t = x1 * y2 + (z2 >>> SHIFT32);
            long z1 = t & MASK32;
            long z0 = t >> SHIFT32;
            z1 += x2 * y1;
            return x1 * y1 + z0 + (z1 >> SHIFT32);
        } else {
            // Use Karatsuba technique with two base 2^32 digits.
            long x1 = x >>> SHIFT32;
            long y1 = y >>> SHIFT32;
            long x2 = x & MASK32;
            long y2 = y & MASK32;
            long a = x1 * y1;
            long b = x2 * y2;
            long c = (x1 + x2) * (y1 + y2);
            long k = c - a - b;
            return (((b >>> SHIFT32) + k) >>> SHIFT32) + a;
        }
    }

    public static long multiplyHighUnsigned(long x, long y) {
        long high = multiplyHigh(x, y);
        return high + (((x < 0) ? y : 0) + ((y < 0) ? x : 0));
    }
}
