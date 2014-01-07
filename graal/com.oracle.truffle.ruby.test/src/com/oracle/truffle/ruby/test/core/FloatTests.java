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
 * Test the {@code Float} class.
 */
public class FloatTests extends RubyTests {

    @Test
    public void testImmediate() {
        assertPrints("2.5\n", "puts 2.5");
        assertPrints("14.33\n", "puts 14.33");
    }

    @Test
    public void testAddImmediate() {
        assertPrints("16.2\n", "puts 14.1+2.1");
        assertPrints("14.2\n", "puts 12.1 + 2.1");
        assertPrints("17.2\n", "puts 9.1 + (8.1)");
        assertPrints("10.2\n", "puts (6.1) + ((4.1))");
    }

    @Test
    public void testSubImmediate() {
        assertPrints("12.1\n", "puts 14.2-2.1");
        assertPrints("10.1\n", "puts 12.2 - 2.1");
        assertPrints("1.0999999999999996\n", "puts 9.2 - (8.1)");
        assertPrints("2.1000000000000005\n", "puts (6.2) - ((4.1))");
    }

    @Test
    public void testMulImmediate() {
        assertPrints("29.82\n", "puts 14.2*2.1");
        assertPrints("25.62\n", "puts 12.2 * 2.1");
        assertPrints("74.52\n", "puts 9.2 * (8.1)");
        assertPrints("25.419999999999998\n", "puts (6.2) * ((4.1))");
        assertPrints("7.5\n", "puts 2.5 * 3");
        assertPrints("7.5\n", "puts 3 * 2.5");
    }

    @Test
    public void testDivImmediate() {
        assertPrints("6.761904761904761\n", "puts 14.2/2.1");
        assertPrints("5.809523809523809\n", "puts 12.2 / 2.1");
        assertPrints("1.1358024691358024\n", "puts 9.2 / (8.1)");
        assertPrints("1.5121951219512197\n", "puts (6.2) / ((4.1))");
        assertPrints("0.8333333333333334\n", "puts 2.5 / 3");
        assertPrints("1.2\n", "puts 3 / 2.5");
    }

    @Test
    public void testLessImmediate() {
        assertPrints("false\n", "puts 14.2<2.2");
        assertPrints("true\n", "puts 2.2 < 10.2");
        assertPrints("false\n", "puts 9.2 < (8.2)");
        assertPrints("true\n", "puts (4.2) < ((6.2))");
    }

}
