/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.test;

import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;

public class IntegerBitsTest extends GraalCompilerTest {

    public static int integerNumberOfLeadingZeros(int value) {
        return Integer.numberOfLeadingZeros(value);
    }

    public static int integerNumberOfTrailingZeros(int value) {
        return Integer.numberOfTrailingZeros(value);
    }

    public static int integerBitCount(int value) {
        return Integer.bitCount(value);
    }

    public static final int[] INT_TEST_CASES = new int[]{0, 1, -1, 0xFFFF0000, 0x0000FFFF, 0x0F0F0F0F, 0xF0F0F0F0};

    @Test
    public void testIntegerNumberOfLeadingZeros() {
        for (int i : INT_TEST_CASES) {
            test("integerNumberOfLeadingZeros", i);
        }
    }

    @Test
    public void testIntegerNumberOfTrailingZeros() {
        for (int i : INT_TEST_CASES) {
            test("integerNumberOfTrailingZeros", i);
        }
    }

    @Test
    public void testIntegerBitCount() {
        for (int i : INT_TEST_CASES) {
            test("integerBitCount", i);
        }
    }

    public static final long[] LONG_TEST_CASES = new long[]{0, 1L, -1L, 0xFFFF_FFFF_0000_0000L, 0x0000_0000_FFFF_FFFFL, 0x0F0F_0F0F_0F0F_0F0FL, 0xF0F0_F0F0_F0F0_F0F0L};

    public static int longNumberOfLeadingZeros(long value) {
        return Long.numberOfLeadingZeros(value);
    }

    public static int longNumberOfTrailingZeros(long value) {
        return Long.numberOfTrailingZeros(value);
    }

    public static int longBitCount(long value) {
        return Long.bitCount(value);
    }

    @Test
    public void testLongNumberOfLeadingZeros() {
        for (long l : LONG_TEST_CASES) {
            test("longNumberOfLeadingZeros", l);
        }
    }

    @Test
    public void testLongNumberOfTrailingZeros() {
        for (long l : LONG_TEST_CASES) {
            test("longNumberOfTrailingZeros", l);
        }
    }

    @Test
    public void testLongBitCount() {
        for (long l : LONG_TEST_CASES) {
            test("longBitCount", l);
        }
    }
}
