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
 * Test the {@code Bignum} class.
 */
public class BignumTests extends RubyTests {

    @Test
    public void testImmediate() {
        assertPrints("123456789123456789\n", "puts 123456789123456789");
        assertPrints("574658776654483828\n", "puts 574658776654483828");
    }

    @Test
    public void testAddImmediate() {
        assertPrints("821572354901397406\n", "puts 123456789123456789+698115565777940617");
        assertPrints("7675735362615108\n", "puts 867676857675 + 7674867685757433");
        assertPrints("8792416214481\n", "puts 8785647643454 + (6768571027)");
        assertPrints("8089240320234\n", "puts (7132953783486) + ((956286536748))");
    }

    @Test
    public void testSubImmediate() {
        assertPrints("574658776654483828\n", "puts 698115565777940617-123456789123456789");
        assertPrints("7674000008899758\n", "puts 7674867685757433 - 867676857675");
        assertPrints("8778879072427\n", "puts 8785647643454 - (6768571027)");
        assertPrints("6176667246738\n", "puts (7132953783486) - ((956286536748))");
    }

    @Test
    public void testLessImmediate() {
        assertPrints("false\n", "puts 698115565777940617 < 123456789123456789");
        assertPrints("true\n", "puts 867676857675 < 7674867685757433");
        assertPrints("false\n", "puts 8785647643454 < (6768571027)");
        assertPrints("true\n", "puts (956286536748) < ((7132953783486))");
    }

    @Test
    public void testDivmod() {
        assertPrints("100\n2342\n", "puts 1000000000000000000000002342.divmod(10000000000000000000000000)");
        assertPrints("Fixnum\n", "puts 1000000000000000000000002342.divmod(10000000000000000000000000)[0].class");
        assertPrints("Fixnum\n", "puts 1000000000000000000000002342.divmod(10000000000000000000000000)[1].class");
    }
}
