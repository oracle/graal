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
 * Test {@code if} expressions.
 */
public class IfTests extends RubyTests {

    @Test
    public void testImmediateIf() {
        assertPrints("1\n", "if true; puts 1 end");
        assertPrints("", "if false; puts 1 end");
    }

    @Test
    public void testImmediateTrailingIf() {
        assertPrints("1\n", "puts 1 if true");
        assertPrints("", "puts 1 if false");
    }

    @Test
    public void testImmediateIfElse() {
        assertPrints("1\n", "if true; puts 1 else puts 2 end");
        assertPrints("2\n", "if false; puts 1 else puts 2 end");
    }

}
