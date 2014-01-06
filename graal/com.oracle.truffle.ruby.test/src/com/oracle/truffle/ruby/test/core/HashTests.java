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
 * Test the {@code Hash} class.
 */
public class HashTests extends RubyTests {

    @Test
    public void testLiteral() {
        assertPrints("Hash\n", "puts ({}).class");
        assertPrints("Hash\n", "puts ({1 => 2, 3 => 4}).class");
        assertPrints("Hash\n", "puts ({key1: 2, key3: 4}).class");
    }

    @Test
    public void testIndex() {
        assertPrints("4\n", "foo = {1 => 2, 3 => 4}; puts foo[3]");
    }

    @Test
    public void testIndexSet() {
        assertPrints("6\n", "foo = {1 => 2, 3 => 4}; foo[5] = 6; puts foo[5]");
    }

    @Test
    public void testKeys() {
        assertPrints("[1, 3]\n", "foo = {1 => 2, 3 => 4}; puts foo.keys.to_s");
    }

}
