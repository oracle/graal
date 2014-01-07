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
 * Test {@code while} expressions.
 */
public class WhileTests extends RubyTests {

    @Test
    public void testSimpleWhile() {
        assertPrints("0\n", "x = 10; while x > 0; x -= 1; end; puts x");
        assertPrints("10\n", "x = 0; while x < 10; x += 1; end; puts x");
    }

    @Test
    public void testBreak() {
        assertPrints("1\n", "x = 0; while x < 10; x += 1; break; end; puts x");
    }

    @Test
    public void testNext() {
        assertPrints("10\n", "x = 0; while x < 10; x += 1; next; end; puts x");
    }

}
