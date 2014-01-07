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
 * Test characteristics of polymorphism.
 */
public class PolymorphismTests extends RubyTests {

    /**
     * Test that a polymorphic method that will specialize to double will then not treat integers as
     * doubles.
     */
    @Test
    public void testSimpleOperatorRedefinition() {
        assertPrints("16.759999999999998\n16\n", //
                        "def add(x, y)\n" + //
                                        "    x + y\n" + //
                                        "end\n" + //
                                        "puts add(14.5, 2.26)\n" + //
                                        "puts add(14, 2)\n");
    }

}
