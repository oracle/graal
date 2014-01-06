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
 * Test blocks.
 */
public class BlockTests extends RubyTests {

    @Test
    public void testSimpleYield() {
        assertPrints("1\n", "def foo; yield; end; foo { puts 1 };");
        assertPrints("1\n", "def foo; yield; end; foo do; puts 1; end;");
    }

    @Test
    public void testYieldOneParameter() {
        assertPrints("1\n", "def foo; yield 1; end; foo { |x| puts x };");
        assertPrints("1\n", "def foo; yield 1; end; foo do |x|; puts x; end;");
    }

    @Test
    public void testYieldTwoParameters() {
        assertPrints("1\n2\n", "def foo; yield 1, 2; end; foo { |x, y| puts x; puts y; };");
        assertPrints("1\n2\n", "def foo; yield 1, 2; end; foo do |x, y|; puts x; puts y; end;");
    }

    @Test
    public void testSelfCapturedInBlock() {
        assertPrints("main\n", "[1].each { |n| puts self.to_s }");
    }

    @Test
    public void testBlockArgumentsDestructure() {
        /*
         * This really subtle. If you pass an array to a block with more than one parameters, it
         * will be destructured. Any other type won't be destructured. There's no annotation to
         * indicate that, like there would be with a method with the * operator.
         */

        assertPrints("1\n", "[1].each { |x| puts x.to_s }");
        assertPrints("[1, 2]\n", "[[1, 2]].each { |xy| puts xy.to_s }");
        assertPrints("1\n2\n", "[[1, 2]].each { |x, y| puts x, y }");
    }

    @Test
    public void testBlocksHaveTheirOwnScopeForAssignment() {
        assertPrints("\n", "1.times { x = 14 }; puts defined? x");
        assertPrints("\n", "1.times do; x = 14; end; puts defined? x");
    }

    @Test
    public void testImplicitForBlocksDoNotHaveTheirOwnScopeForAssignment() {
        assertPrints("local-variable\n", "for n in [1]; x = 14; end; puts defined? x");
    }

}
