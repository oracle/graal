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
 * Test the {@code Regexp} class.
 */
public class RegexpTests extends RubyTests {

    @Test
    public void testLiteral() {
        assertPrints("Regexp\n", "puts /foo/.class");
    }

    @Test
    public void testInterpolatedLiteral() {
        assertPrints("Regexp\n", "puts /foo#{1}/.class");
    }

    @Test
    public void testMatch() {
        assertPrints("0\n", "puts(/foo/ =~ \"foo\")");
        assertPrints("0\n", "puts(\"foo\" =~ /foo/)");
        assertPrints("3\n", "puts(/foo/ =~ \"abcfoo\")");
        assertPrints("3\n", "puts(\"abcfoo\" =~ /foo/)");
        assertPrints("\n", "puts(/foo/ =~ \"abc\")");
        assertPrints("\n", "puts(\"abc\" =~ /foo/)");
    }

    @Test
    public void testFrameLocalVariableResults() {
        assertPrints("foo\nbar\nbaz\n", "/(foo)(bar)(baz)/ =~ 'foobarbaz'; puts $1; puts $2; puts $3");
    }

}
