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
 * Test the {@code Range} class.
 */
public class RangeTests extends RubyTests {

    @Test
    public void testImmediate() {
        assertPrints("1..2\n", "puts 1..2");
        assertPrints("1..2\n", "puts (1..2)");
        assertPrints("1..2\n", "puts ((1)..2)");
        assertPrints("1...2\n", "puts 1...2");
    }

    @Test
    public void testVariables() {
        assertPrints("1..2\n", "x = 1; puts x..2");
        assertPrints("1..2\n", "y = 2; puts 1..y");
        assertPrints("1..2\n", "x = 1; y = 2; puts x..y");
    }

    @Test
    public void testToArray() {
        assertPrints("0\n1\n2\n", "puts (0..2).to_a");
        assertPrints("1\n2\n", "puts (1..2).to_a");
        assertPrints("1\n2\n", "puts (1...3).to_a");
        assertPrints("1\n2\n3\n", "puts (1..3).to_a");
    }

    @Test
    public void testEach() {
        assertPrints("", "(1...1).each { |n| puts n }");
        assertPrints("1\n", "(1...2).each { |n| puts n }");
        assertPrints("1\n2\n", "(1...3).each { |n| puts n }");
        assertPrints("1\n2\n3\n", "(1...4).each { |n| puts n }");
        assertPrints("1\n", "(1..1).each { |n| puts n }");
        assertPrints("1\n2\n", "(1..2).each { |n| puts n }");
        assertPrints("1\n2\n3\n", "(1..3).each { |n| puts n }");
        assertPrints("1\n2\n3\n4\n", "(1..4).each { |n| puts n }");
        assertPrints("0\n1\n", "(0..1).each { |n| puts n }");
        assertPrints("", "(4..-2).each { |n| puts n }");
        assertPrints("-4\n-3\n-2\n", "(-4..-2).each { |n| puts n }");
        assertPrints("-1\n0\n1\n", "(-1..1).each { |n| puts n }");
    }

    @Test
    public void testMap() {
        assertPrints("2\n4\n6\n", "puts (1..3).map { |n| n*2 }");
    }

}
