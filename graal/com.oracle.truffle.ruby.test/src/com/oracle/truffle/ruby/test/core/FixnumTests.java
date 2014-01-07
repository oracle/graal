/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.test.core;

import org.junit.*;

import com.oracle.truffle.ruby.test.*;

/**
 * Test the {@code Fixnum} class.
 */
public class FixnumTests extends RubyTests {

    @Test
    public void testImmediate() {
        assertPrints("2\n", "puts 2");
        assertPrints("14\n", "puts 14");
        assertPrints("-14\n", "puts -14");
    }

    @Test
    public void testNegate() {
        assertPrints("-1\n", "x = 1; puts -x");
        assertPrints("1\n", "x = -1; puts -x");
    }

    @Test
    public void testAddImmediate() {
        assertPrints("16\n", "puts 14+2");
        assertPrints("14\n", "puts 12 + 2");
        assertPrints("17\n", "puts 9 + (8)");
        assertPrints("10\n", "puts (6) + ((4))");
    }

    @Test
    public void testSubImmediate() {
        assertPrints("12\n", "puts 14-2");
        assertPrints("10\n", "puts 12 - 2");
        assertPrints("1\n", "puts 9 - (8)");
        assertPrints("2\n", "puts (6) - ((4))");
    }

    @Test
    public void testMulImmediate() {
        assertPrints("28\n", "puts 14 * 2");
        assertPrints("20\n", "puts 2 * 10");
        assertPrints("72\n", "puts 9 * (8)");
        assertPrints("24\n", "puts (4) * ((6))");
    }

    @Test
    public void testDivImmediate() {
        assertPrints("7\n", "puts 14 / 2");
        assertPrints("0\n", "puts 2 / 10");
        assertPrints("1\n", "puts 9 / (8)");
        assertPrints("0\n", "puts (4) / ((6))");
    }

    @Test
    public void testEqualImmediate() {
        assertPrints("true\n", "puts 14 == 14");
        assertPrints("true\n", "puts 2 == 2");
        assertPrints("false\n", "puts 9 == (8)");
        assertPrints("false\n", "puts (4) == ((6))");
    }

    @Test
    public void testNotEqualImmediate() {
        assertPrints("false\n", "puts 14 != 14");
        assertPrints("false\n", "puts 2 != 2");
        assertPrints("true\n", "puts 9 != (8)");
        assertPrints("true\n", "puts (4) != ((6))");
    }

    @Test
    public void testLessImmediate() {
        assertPrints("false\n", "puts 14 < 2");
        assertPrints("true\n", "puts 2 < 10");
        assertPrints("false\n", "puts 9 < (8)");
        assertPrints("true\n", "puts (4) < ((6))");
    }

    @Test
    public void testGreaterEqualImmediate() {
        assertPrints("true\n", "puts 14 >= 14");
        assertPrints("true\n", "puts 14 >= 2");
        assertPrints("false\n", "puts 2 >= 10");
        assertPrints("true\n", "puts 9 >= (8)");
        assertPrints("false\n", "puts (4) >= ((6))");
    }

    @Test
    public void testLeftShift() {
        assertPrints("0\n", "puts 0 << 0");
        assertPrints("0\n", "puts 0 << 1");
        assertPrints("1\n", "puts 1 << 0");
        assertPrints("0\n", "puts 1 << -1");
        assertPrints("0\n", "puts 1 << -2");
        assertPrints("28\n", "puts 14 << 1");
        assertPrints("7\n", "puts 14 << -1");
        assertPrints("4294967296\n", "puts 1 << 32");
        assertPrints("340282366920938463463374607431768211456\n", "puts 1 << 128");
        assertPrints("0\n", "puts 1 << -32");
        assertPrints("Fixnum\n", "puts (14 << 1).class");
        assertPrints("Bignum\n", "puts (1 << 32).class");
    }

    @Test
    public void testDivmod() {
        // @formatter:off
        final int[][] tests = new int[][]{
                        new int[]{13,   4,  3,  1},
                        new int[]{13,  -4, -4, -3},
                        new int[]{-13,  4, -4,  3},
                        new int[]{-13, -4,  3, -1}};
        // @formatter:on

        for (int[] test : tests) {
            final int a = test[0];
            final int b = test[1];
            final int q = test[2];
            final int r = test[3];
            assertPrints(String.format("%d\n%d\n", q, r), String.format("puts %d.divmod(%d)", a, b));
        }
    }

}
