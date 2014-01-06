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

import com.oracle.truffle.ruby.runtime.control.*;
import com.oracle.truffle.ruby.test.*;

/**
 * Test {@code raise} and {@code rescue}.
 */
public class RaiseRescueTests extends RubyTests {

    @Test(expected = RaiseException.class)
    public void testZeroDivisionError() {
        assertPrints("", "1/0");
    }

    @Test
    public void testBeginRescue() {
        assertPrints("", "begin; rescue; end");
        assertPrints("", "begin; rescue => e; end");
        assertPrints("", "begin; rescue ZeroDivisionError => e; end");
        assertPrints("", "begin; rescue; ensure; end");
        assertPrints("", "begin; rescue => e; ensure; end");
        assertPrints("", "begin; rescue ZeroDivisionError => e; ensure; end");
        assertPrints("", "begin; rescue; else; end");
        assertPrints("", "begin; rescue => e; else; end");
        assertPrints("", "begin; rescue ZeroDivisionError => e; else; end");
        assertPrints("", "def foo; rescue; end");
        assertPrints("", "def foo; rescue => e; end");
        assertPrints("", "def foo; rescue ZeroDivisionError => e; end");
        assertPrints("", "def foo; rescue; ensure; end");
        assertPrints("", "def foo; rescue => e; ensure; end");
        assertPrints("", "def foo; rescue ZeroDivisionError => e; ensure; end");
        assertPrints("", "def foo; rescue; else; end");
        assertPrints("", "def foo; rescue => e; else; end");
        assertPrints("", "def foo; rescue ZeroDivisionError => e; else; end");
    }

    @Test
    public void testRescueZeroDivisionError() {
        assertPrints("divided by 0\n3\n4\n", "begin; 1/0; puts 1; rescue => e; puts e; else puts 2; ensure puts 3; end; puts 4");
        assertPrints("divided by 0\n3\n4\n", "begin; 1/0; puts 1; rescue ZeroDivisionError => e; puts e; else puts 2; ensure puts 3; end; puts 4");
        assertPrints("1\n2\n3\n4\n", "begin; 1/1; puts 1; rescue ZeroDivisionError => e; puts e; else puts 2; ensure puts 3; end; puts 4");
        assertPrints("1\n2\n3\n4\n", "begin; 1/1; puts 1; rescue ZeroDivisionError => e; puts e; rescue NameError => e; puts e; else puts 2; ensure puts 3; end; puts 4");
    }

    @Test
    public void testSplatRescue() {
        assertPrints("2\n", "ERRORS=[ZeroDivisionError]; begin; 1/0; puts 1; rescue *ERRORS; puts 2; end");
    }

    @Test
    public void testRetry() {
        assertPrints("1\n3\n1\n2\n5\n", "x=0; begin; puts 1; 1/x; puts 2; rescue ZeroDivisionError; puts 3; x=1; retry; puts 4; end; puts 5");
    }

}
