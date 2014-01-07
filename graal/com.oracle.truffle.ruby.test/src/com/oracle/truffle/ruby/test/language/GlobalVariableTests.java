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
 * Test global variables.
 */
public class GlobalVariableTests extends RubyTests {

    @Test
    public void testMinimal() {
        assertPrints("14\n", "$foo = 14; puts $foo");
    }

    @Test
    public void testScope() {
        assertPrints("14\n", "def foo; $bar = 14; end; foo; puts $bar");
        assertPrints("14\n", "$bar = 14; def foo; puts $bar; end; foo");
    }

}
