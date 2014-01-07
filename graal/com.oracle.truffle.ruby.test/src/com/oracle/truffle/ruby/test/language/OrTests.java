/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.test.language;

import org.junit.*;

import com.oracle.truffle.ruby.test.*;

/**
 * Test {@code or} expressions, which unusually for Ruby are not methods. This is because with
 * Ruby's eager evaluation there would be no way to implement the short circuit semantics.
 */
public class OrTests extends RubyTests {

    @Test
    public void testImmediate() {
        assertPrints("false\n", "puts false || false");
        assertPrints("true\n", "puts true || false");
        assertPrints("true\n", "puts false || true");
        assertPrints("true\n", "puts true || true");
        assertPrints("false\n", "puts (false or false)");
        assertPrints("true\n", "puts (true or false)");
        assertPrints("true\n", "puts (false or true)");
        assertPrints("true\n", "puts (true or true)");
    }

    @Test
    public void testShortCircuits() {
        assertPrints("false\nfalse\nfalse\n", "x = y = false; puts (x = false) || (y = false); puts x, y");
        assertPrints("true\ntrue\nfalse\n", "x = y = false; puts (x = true) || (y = false); puts x, y");
        assertPrints("true\nfalse\ntrue\n", "x = y = false; puts (x = false) || (y = true); puts x, y");
        assertPrints("true\ntrue\nfalse\n", "x = y = false; puts (x = true) || (y = true); puts x, y");
        assertPrints("false\nfalse\nfalse\n", "x = y = false; puts ((x = false) or (y = false)); puts x, y");
        assertPrints("true\ntrue\nfalse\n", "x = y = false; puts ((x = true) or (y = false)); puts x, y");
        assertPrints("true\nfalse\ntrue\n", "x = y = false; puts ((x = false) or (y = true)); puts x, y");
        assertPrints("true\ntrue\nfalse\n", "x = y = false; puts ((x = true) or (y = true)); puts x, y");
    }

    @Test
    public void testOrAssign() {
        assertPrints("true\n", "def foo; puts 1; false; end; x = true; x ||= foo; puts x");
        assertPrints("1\nfalse\n", "def foo; puts 1; false; end; x = false; x ||= foo; puts x");
    }

}
