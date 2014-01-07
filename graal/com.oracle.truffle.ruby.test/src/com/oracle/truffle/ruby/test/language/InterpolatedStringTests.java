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
 * Test interpolated strings - that is string literals with #{...} sections in them. Within those
 * you can have arbitrary Ruby expressions.
 */
public class InterpolatedStringTests extends RubyTests {

    @Test
    public void testBasic() {
        assertPrints("123\n", "puts \"1#{2}3\"");
    }

    @Test
    public void testMethodCall() {
        assertPrints("123\n", "def foo; 2; end; puts \"1#{foo}3\"");
    }

}
