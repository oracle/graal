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
 * Test the {@code Thread} class.
 */
public class ThreadTests extends RubyTests {

    @Test
    public void testCreateJoin() {
        assertPrints("1\n2\n", "t = Thread.new { puts 1 }; t.join; puts 2");
    }

}
