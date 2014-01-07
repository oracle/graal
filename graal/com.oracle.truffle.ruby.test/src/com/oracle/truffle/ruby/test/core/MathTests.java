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
 * Test the {@code Math} class.
 */
public class MathTests extends RubyTests {

    @Test
    public void testPI() {
        assertPrints("3.141592653589793\n", "puts Math::PI");
    }

    @Test
    public void testSqrt() {
        assertPrints("1.0\n", "puts Math.sqrt(1)");
        assertPrints("1.0\n", "puts Math::sqrt(1)");
        assertPrints("3.7416573867739413\n", "puts Math::sqrt(14)");
    }

}
