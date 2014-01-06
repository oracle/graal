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
 * Test multiple assignment, which is sort of like pattern matching for arrays. The arrays can be
 * implicit on the RHS.
 */
public class MultipleAssignmentTests extends RubyTests {

    @Test
    public void testToLocal() {
        assertPrints("1\n2\n", "x, y = [1, 2]; puts x, y");
        assertPrints("1\n2\n", "x, y = 1, 2; puts x, y");
        assertPrints("1\n2\n", "x = [1, 2]; y, z = x; puts y, z");
    }

    @Test
    public void testToArrayIndex() {
        assertPrints("1\n2\n", "x = []; x[0], x[1] = 1, 2; puts x");
    }

    @Test
    public void testSwap() {
        assertPrints("2\n1\n", "a = [1]; b = [2]; a[0], b[0] = b[0], a[0]; puts a, b");
    }

    @Test
    public void testProducesArray() {
        assertPrints("1\n2\n", "puts((a, b = 1, 2))");
    }

    @Test
    public void testWithSplat() {
        assertPrints("1\n[2, 3]\n", "a, *b = [1, 2, 3]; puts a; puts b.to_s");
    }

    @Test
    public void testWithSplatEmpty() {
        assertPrints("1\n[]\n", "a, *b = [1]; puts a.to_s; puts b.to_s");
    }

    @Test
    public void testWithSingleValueRHS() {
        assertPrints("14\n\n\n", "a, b, c = 14; puts a; puts b; puts c");
        assertPrints("\n\n\n", "a, b, c = nil; puts a; puts b; puts c");
    }

    @Test
    public void testWithSingleSplatLHSAndSingleValueRHS() {
        assertPrints("[14]\n", "a = *14; puts a.to_s");
    }

}
