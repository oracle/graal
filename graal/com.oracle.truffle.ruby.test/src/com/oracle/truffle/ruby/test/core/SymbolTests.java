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
 * Test the {@code Symbol} class.
 */
public class SymbolTests extends RubyTests {

    @Test
    public void testParses() {
        assertPrints("", ":foo");
        assertPrints("", ":\"foo\"");
    }

    @Test
    public void testPuts() {
        assertPrints("foo\n", "puts :foo");
    }

    @Test
    public void testInterpolated() {
        assertPrints("foo123\n", "puts :\"foo1#{2}3\"");
    }

}
