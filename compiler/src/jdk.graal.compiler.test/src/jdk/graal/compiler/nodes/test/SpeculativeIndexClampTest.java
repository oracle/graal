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
package jdk.graal.compiler.nodes.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.type.IntegerStamp;

/**
 * Tests the branchless clamp pattern used to protect array index addresses from speculative
 * out-of-bounds execution. This test validates the arithmetic behavior of the clamp independently
 * of compiler graph construction. {@link SpeculativeIndexClampCompilerTest} covers the compiler
 * lowering level by compiling Java array accesses and checking that the expected IR pattern is
 * produced.
 */
public class SpeculativeIndexClampTest {

    /**
     * Checks that the branchless clamp produces the same result as a conceptual
     * {@code max(min(index, length - 1), 0)} clamp.
     */
    @Test
    public void testBranchlessClampMatchesExpectedClamp() {
        int[] lengths = {0, 1, 2, 42, Integer.MAX_VALUE};
        int[] indexes = {Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -42, -1, 0, 1, 41, 42, Integer.MAX_VALUE - 1, Integer.MAX_VALUE};
        for (int length : lengths) {
            for (int index : indexes) {
                int clamped = branchlessClamp(index, length);
                Assert.assertEquals(formatClamp(index, length, clamped), expectedClamp(index, length), clamped);
            }
        }
    }

    /**
     * Checks that the branchless clamp always produces an in-bounds index for non-empty arrays.
     */
    @Test
    public void testBranchlessClampProducesInBoundsIndex() {
        int[] lengths = {0, 1, 2, 42, Integer.MAX_VALUE};
        int[] indexes = {Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -42, -1, 0, 1, 41, 42, Integer.MAX_VALUE - 1, Integer.MAX_VALUE};
        for (int length : lengths) {
            for (int index : indexes) {
                int clamped = branchlessClamp(index, length);
                if (length == 0) {
                    Assert.assertEquals(formatClamp(index, length, clamped), 0, clamped);
                } else {
                    Assert.assertTrue(formatClamp(index, length, clamped), clamped >= 0);
                    Assert.assertTrue(formatClamp(index, length, clamped), clamped < length);
                }
            }
        }
    }

    private static int branchlessClamp(int index, int length) {
        int nonNegativeIndex = branchlessMaxZero(index);
        int nonNegativeLengthMinusOne = branchlessMaxZero(length - 1);
        return branchlessMinNonNegative(nonNegativeIndex, nonNegativeLengthMinusOne);
    }

    private static void assertSubtractionDoesNotOverflow(int x, int y) {
        Assert.assertFalse(formatBinaryOperation("sub", x, y), IntegerStamp.subtractionOverflows(x, y, Integer.SIZE));
    }

    private static int expectedClamp(int index, int length) {
        return Math.max(Math.min(index, length - 1), 0);
    }

    private static String formatBinaryOperation(String operation, int x, int y) {
        return operation + "(x=" + x + ", y=" + y + ")";
    }

    private static String formatClamp(int index, int length, int clamped) {
        return "index=" + index + ", length=" + length + ", clamped=" + clamped;
    }

    private static int branchlessMaxZero(int x) {
        return x & ~(x >> (Integer.SIZE - 1));
    }

    private static int branchlessMinNonNegative(int x, int y) {
        assertSubtractionDoesNotOverflow(x, y);
        int delta = x - y;
        return y + (delta & (delta >> (Integer.SIZE - 1)));
    }
}
