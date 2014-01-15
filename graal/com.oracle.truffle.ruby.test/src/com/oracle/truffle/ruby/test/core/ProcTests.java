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
 * Test the {@code Proc} class.
 */
public class ProcTests extends RubyTests {

    @Test
    public void testKernelProc() {
        assertPrints("1\n", "x = proc { puts 1 }; x.call");
        assertPrints("1\n", "x = proc { 1 }; puts x.call");
    }

    @Test
    public void testKernelLambda() {
        assertPrints("1\n", "x = lambda { puts 1 }; x.call");
        assertPrints("1\n", "x = lambda { 1 }; puts x.call");
    }

    @Test
    public void testKernelProcNew() {
        assertPrints("1\n", "x = Proc.new { puts 1 }; x.call");
        assertPrints("1\n", "x = Proc.new { 1 }; puts x.call");
    }

    @Test
    public void testProcReturn() {
        assertPrints("1\n", "def foo; x = proc { return 1 }; x.call; return 2; end; puts foo");
    }

    @Test
    public void testLambdaReturn() {
        assertPrints("2\n", "def foo; x = lambda { return 1 }; x.call; return 2; end; puts foo");
    }

}
