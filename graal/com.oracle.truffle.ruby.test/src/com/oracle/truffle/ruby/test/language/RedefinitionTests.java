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
 * Test that methods can be redefined.
 */
public class RedefinitionTests extends RubyTests {

    /**
     * Test that a method on Fixnum can be redefined and that we will use the new definition. The
     * call that expects to find the redefinition needs to have already been specialized, which is
     * why do it through a method.
     */
    @Test
    public void testSimpleOperatorRedefinition() {
        assertPrints("3\n-1\n", //
                        "def add(x, y)\n" + //
                                        "    x + y\n" + //
                                        "end\n" + //
                                        "puts add(1, 2)\n" + //
                                        "class Fixnum\n" + //
                                        "    def +(other)\n" + //
                                        "        self - other\n" + //
                                        "    end\n" + //
                                        "end\n" + //
                                        "puts add(1, 2)\n");
    }

    /**
     * This is quite a subtle test. We redefine Float#+. We have a method which calls +. It is
     * initially specialized to Fixnum, but then used with Float. The tricky bit is that Float has
     * not been redefined since the operator has been specialized. The operator thinks it is up to
     * date. When we emit a + node, we need to check if any class that that node could specialize to
     * has been redefined.
     */
    @Test
    public void testSpecialisationConsidersRedefinitionInOtherClasses() {
        assertPrints("16\n12.25\n", //
                        "def add(a, b)\n" + //
                                        "    a + b\n" + //
                                        "end\n" + //
                                        "class Float\n" + //
                                        "    def +(other)\n" + //
                                        "        self - other\n" + //
                                        "    end\n" + //
                                        "end\n" + //
                                        "puts add(14, 2)\n" + //
                                        "puts add(14.5, 2.25)");
    }

}
