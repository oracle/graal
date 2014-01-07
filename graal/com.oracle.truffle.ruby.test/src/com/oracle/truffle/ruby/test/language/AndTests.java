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
 * Test {@code and} expressions, which unusually for Ruby are not methods. This is because with
 * Ruby's eager evaluation there would be no way to implement the short circuit semantics.
 */
public class AndTests extends RubyTests {

    @Test
    public void testImmediate() {
        assertPrints("false\n", "puts false && false");
        assertPrints("false\n", "puts true && false");
        assertPrints("false\n", "puts false && true");
        assertPrints("true\n", "puts true && true");
        assertPrints("false\n", "puts (false and false)");
        assertPrints("false\n", "puts (true and false)");
        assertPrints("false\n", "puts (false and true)");
        assertPrints("true\n", "puts (true and true)");
    }

    @Test
    public void testShortCircuits() {
        assertPrints("false\nfalse\nfalse\n", "x = y = false; puts (x = false) && (y = false); puts x, y");
        assertPrints("false\ntrue\nfalse\n", "x = y = false; puts (x = true) && (y = false); puts x, y");
        assertPrints("false\nfalse\nfalse\n", "x = y = false; puts (x = false) && (y = true); puts x, y");
        assertPrints("true\ntrue\ntrue\n", "x = y = false; puts (x = true) && (y = true); puts x, y");
        assertPrints("false\nfalse\nfalse\n", "x = y = false; puts ((x = false) and (y = false)); puts x, y");
        assertPrints("false\ntrue\nfalse\n", "x = y = false; puts ((x = true) and (y = false)); puts x, y");
        assertPrints("false\nfalse\nfalse\n", "x = y = false; puts ((x = false) and (y = true)); puts x, y");
        assertPrints("true\ntrue\ntrue\n", "x = y = false; puts ((x = true) and (y = true)); puts x, y");
    }

}
