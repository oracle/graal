/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests {@link System#arraycopy} on primitive arrays against a per-element reference copy: every
 * length from 0 to a few hundred bytes (the window and library paths of byte copies), every
 * alignment of source and destination, overlapping ranges in both directions, and a few large
 * copies.
 */
public class PrimitiveArrayCopyTest {
    private static final int BUFFER = 4096;
    private static final int MAX_SHORT_LENGTH = 300;
    private static final int[] LARGE_LENGTHS = {511, 512, 513, 1000, 2047, 2048, 3000};

    private static byte[] pattern(int length, int seed) {
        byte[] result = new byte[length];
        for (int i = 0; i < length; i++) {
            result[i] = (byte) (seed * 31 + i * 7);
        }
        return result;
    }

    private static byte[] expectedCopy(byte[] src, int srcPos, byte[] dst, int dstPos, int length) {
        byte[] expected = dst.clone();
        byte[] saved = Arrays.copyOfRange(src, srcPos, srcPos + length); // memmove semantics
        System.arraycopy(saved, 0, expected, dstPos, 0); // keep the array type for the loop below
        for (int i = 0; i < length; i++) {
            expected[dstPos + i] = saved[i];
        }
        return expected;
    }

    private static void check(byte[] src, int srcPos, byte[] dst, int dstPos, int length) {
        byte[] expected = expectedCopy(src, srcPos, dst, dstPos, length);
        System.arraycopy(src, srcPos, dst, dstPos, length);
        Assert.assertArrayEquals("length " + length + " from " + srcPos + " to " + dstPos + (src == dst ? " (same array)" : ""), expected, dst);
    }

    @Test
    public void disjointArraysAllShortLengthsAndAlignments() {
        for (int length = 0; length <= MAX_SHORT_LENGTH; length++) {
            for (int srcPos = 0; srcPos < 16; srcPos++) {
                for (int dstPos = 0; dstPos < 16; dstPos += 3) {
                    byte[] src = pattern(BUFFER, length);
                    byte[] dst = pattern(BUFFER, length + 1);
                    check(src, srcPos, dst, dstPos, length);
                }
            }
        }
    }

    @Test
    public void sameArrayOverlapInBothDirections() {
        for (int length = 0; length <= MAX_SHORT_LENGTH; length++) {
            for (int distance = 1; distance <= 70; distance += (distance < 20 ? 1 : 7)) {
                byte[] a = pattern(BUFFER, length);
                check(a, 100, a, 100 + distance, length); // destination after the source
                byte[] b = pattern(BUFFER, length);
                check(b, 100 + distance, b, 100, length); // destination before the source
            }
        }
    }

    @Test
    public void sameArrayNoOverlap() {
        for (int length = 0; length <= MAX_SHORT_LENGTH; length += 7) {
            byte[] a = pattern(BUFFER, length);
            check(a, 3, a, 3 + length + 5, length);
            check(a, 3 + length + 5, a, 3, length);
        }
    }

    @Test
    public void largeCopies() {
        for (int length : LARGE_LENGTHS) {
            for (int srcPos = 0; srcPos < 8; srcPos++) {
                byte[] src = pattern(BUFFER, length);
                byte[] dst = pattern(BUFFER, length + 1);
                check(src, srcPos, dst, 5, length);
                byte[] same = pattern(BUFFER, length);
                check(same, srcPos, same, srcPos + 1, length);
                check(same, srcPos + 1, same, srcPos, length);
            }
        }
    }

    @Test
    public void booleanAndWiderElementsStayCorrect() {
        boolean[] flags = new boolean[200];
        for (int i = 0; i < flags.length; i++) {
            flags[i] = (i % 3) == 0;
        }
        boolean[] expected = flags.clone();
        for (int i = 0; i < 77; i++) {
            expected[20 + i] = flags[3 + i];
        }
        System.arraycopy(flags, 3, flags, 20, 77);
        Assert.assertArrayEquals(expected, flags);

        int[] ints = new int[300];
        for (int i = 0; i < ints.length; i++) {
            ints[i] = i * 1_000_003;
        }
        int[] expectedInts = ints.clone();
        for (int i = 0; i < 123; i++) {
            expectedInts[7 + i] = ints[5 + i];
        }
        System.arraycopy(ints, 5, ints, 7, 123);
        Assert.assertArrayEquals(expectedInts, ints);

        long[] longs = new long[150];
        for (int i = 0; i < longs.length; i++) {
            longs[i] = i * 0x9E3779B97F4A7C15L;
        }
        long[] expectedLongs = longs.clone();
        for (int i = 0; i < 61; i++) {
            expectedLongs[2 + i] = longs[9 + i];
        }
        System.arraycopy(longs, 9, longs, 2, 61);
        Assert.assertArrayEquals(expectedLongs, longs);
    }
}
