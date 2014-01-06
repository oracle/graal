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
 * Test {@code for} expressions.
 */
public class ForTests extends RubyTests {

    @Test
    public void testArray() {
        assertPrints("1\n2\n3\n", "for x in [1, 2, 3]; puts x; end");
        assertPrints("1\n2\n3\n", "y = [1, 2, 3]; for x in y; puts x; end");
    }

    @Test
    public void testRange() {
        assertPrints("1\n2\n3\n", "for x in 1..3; puts x; end");
        assertPrints("1\n2\n3\n", "y = 1..3; for x in y; puts x; end");
        assertPrints("1\n2\n", "for x in 1...3; puts x; end");
    }

    @Test
    public void testScopeRO() {
        assertPrints("14\n", "x=14; for n in [1]; puts x; end");
    }

    @Test
    public void testScopeRW() {
        assertPrints("14\n", "x=0; for n in [1]; x=x+14; puts x; end");
    }

    @Test
    public void testNoNewScope() {
        assertPrints("14\n", "for i in [1]; v = 14; end; puts v");
    }

}
