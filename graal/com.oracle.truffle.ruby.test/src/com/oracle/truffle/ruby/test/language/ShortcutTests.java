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
 * Test shortcut expressions (syntactic sugar) such as {@code +=} and {@code |=}.
 */
public class ShortcutTests extends RubyTests {

    @Test
    public void testShortcutAddAssign() {
        assertPrints("2\n", "x = 1; x += 1; puts x");
    }

    @Test
    public void testShortcutSubAssign() {
        assertPrints("0\n", "x = 1; x -= 1; puts x");
    }

}
