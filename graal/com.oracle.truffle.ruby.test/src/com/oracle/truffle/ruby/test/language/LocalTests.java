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
 * Test local variables.
 */
public class LocalTests extends RubyTests {

    @Test
    public void testAssignmentTopLevel() {
        assertPrints("1\n", "x = 1; puts x");
    }

    @Test
    public void testAssignmentWithinMethod() {
        assertPrints("1\n", "def foo; x = 1; puts x; end; foo");
    }

}
