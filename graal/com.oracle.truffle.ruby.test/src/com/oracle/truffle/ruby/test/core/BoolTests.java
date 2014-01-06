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
 * Test {@code true} and {@code false}. Note that there is no {@code Bool} class or type. There is
 * {@code TrueClass}, {@code FalseClass}, and instances of them {@code true} and {@code false}.
 */
public class BoolTests extends RubyTests {

    @Test
    public void testImmediate() {
        assertPrints("true\n", "puts true");
        assertPrints("false\n", "puts false");
    }

}
