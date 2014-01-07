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
 * Test {@code until} expressions.
 */
public class UntilTests extends RubyTests {

    @Test
    public void testSimpleWhile() {
        assertPrints("0\n", "x = 10; until x == 0; x -= 1; end; puts x");
        assertPrints("10\n", "x = 0; until x == 10; x += 1; end; puts x");
    }

}
