/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.cri.intrinsics;

import static com.oracle.max.cri.intrinsics.IntrinsicIDs.*;

import java.math.*;

import com.sun.max.annotate.*;

/**
 * {@link INTRINSIC} method definitions for unsigned comparisons.
 * All methods have correct, but slow, standard Java implementations so that
 * they can be used with compilers not supporting the intrinsics.
 */
public class UnsignedMath {
    private static final long MASK = 0xffffffffL;

    /**
     * @see IntrinsicIDs#UCMP_AT
     */
    @INTRINSIC(UCMP_AT)
    public static boolean aboveThan(int a, int b) {
        return (a & MASK) > (b & MASK);
    }

    /**
     * @see IntrinsicIDs#UCMP_AE
     */
    @INTRINSIC(UCMP_AE)
    public static boolean aboveOrEqual(int a, int b) {
        return (a & MASK) >= (b & MASK);
    }

    /**
     * @see IntrinsicIDs#UCMP_BT
     */
    @INTRINSIC(UCMP_BT)
    public static boolean belowThan(int a, int b) {
        return (a & MASK) < (b & MASK);
    }

    /**
     * @see IntrinsicIDs#UCMP_BE
     */
    @INTRINSIC(UCMP_BE)
    public static boolean belowOrEqual(int a, int b) {
        return (a & MASK) <= (b & MASK);
    }


    /**
     * @see IntrinsicIDs#UCMP_AT
     */
    @INTRINSIC(UCMP_AT)
    public static boolean aboveThan(long a, long b) {
        return (a > b) ^ ((a < 0) != (b < 0));
    }

    /**
     * @see IntrinsicIDs#UCMP_AE
     */
    @INTRINSIC(UCMP_AE)
    public static boolean aboveOrEqual(long a, long b) {
        return (a >= b) ^ ((a < 0) != (b < 0));
    }

    /**
     * @see IntrinsicIDs#UCMP_BT
     */
    @INTRINSIC(UCMP_BT)
    public static boolean belowThan(long a, long b) {
        return (a < b) ^ ((a < 0) != (b < 0));
    }

    /**
     * @see IntrinsicIDs#UCMP_BE
     */
    @INTRINSIC(UCMP_BE)
    public static boolean belowOrEqual(long a, long b) {
        return (a <= b) ^ ((a < 0) != (b < 0));
    }


    /**
     * @see IntrinsicIDs#UDIV
     */
    @INTRINSIC(UDIV)
    public static int divide(int a, int b) {
        return (int) ((a & MASK) / (b & MASK));
    }

    /**
     * @see IntrinsicIDs#UREM
     */
    @INTRINSIC(UREM)
    public static int remainder(int a, int b) {
        return (int) ((a & MASK) % (b & MASK));
    }


    /**
     * @see IntrinsicIDs#UDIV
     */
    @INTRINSIC(UDIV)
    public static long divide(long a, long b) {
        return bi(a).divide(bi(b)).longValue();
    }

    /**
     * @see IntrinsicIDs#UREM
     */
    @INTRINSIC(UREM)
    public static long remainder(long a, long b) {
        return bi(a).remainder(bi(b)).longValue();
    }

    private static BigInteger bi(long unsigned) {
        return unsigned >= 0 ? BigInteger.valueOf(unsigned) : BigInteger.valueOf(unsigned & 0x7fffffffffffffffL).setBit(63);
    }
}

