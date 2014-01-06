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
 * Test the {@code String} class.
 */
public class StringTests extends RubyTests {

    @Test
    public void testToI() {
        assertPrints("2\n", "puts \"2\".to_i");
        assertPrints("-2\n", "puts \"-2\".to_i");
        assertPrints("123456789123456789\n", "puts \"123456789123456789\".to_i");
    }

    @Test
    public void testFormat() {
        assertPrints("a\n", "puts \"a\"");
        assertPrints("1\n", "puts \"%d\" % 1");
        assertPrints("a1b\n", "puts \"a%db\" % 1");
        assertPrints("a1.00000b\n", "puts \"a%fb\" % 1");
        assertPrints("a2.50000b\n", "puts \"a%fb\" % 2.5");
        assertPrints("3.400000000\n", "puts \"%.9f\" % 3.4");
        assertPrints("3.400000000\n", "puts \"%0.9f\" % 3.4");
    }

    @Test
    public void testThreequal() {
        assertPrints("false\n", "puts \"a\" === \"b\"");
        assertPrints("true\n", "puts \"a\" === \"a\"");
    }

    @Test
    public void testIndex() {
        assertPrints("a\n", "puts 'a'[0]");
        assertPrints("config\n", "puts 'foo.config'[-6..-1]");
    }

}
