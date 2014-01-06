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
 * Test the {@code Integer} class.
 */
public class IntegerTests extends RubyTests {

    @Test
    public void testTimes() {
        assertPrints("0\n", "1.times { |i| puts i }");
        assertPrints("0\n1\n2\n", "3.times { |i| puts i }");
    }

}
