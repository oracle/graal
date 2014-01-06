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
 * Test {@code case} expressions.
 */
public class CaseTests extends RubyTests {

    @Test
    public void testSingle() {
        assertPrints("1\n4\n", "case 'a'; when 'a'; puts 1; when 'b'; puts 2; else; puts 3; end; puts 4");
        assertPrints("2\n4\n", "case 'b'; when 'a'; puts 1; when 'b'; puts 2; else; puts 3; end; puts 4");
        assertPrints("3\n4\n", "case 'c'; when 'a'; puts 1; when 'b'; puts 2; else; puts 3; end; puts 4");
    }

    @Test
    public void testMultiple() {
        assertPrints("1\n4\n", "case 'a'; when 'a', 'b'; puts 1; when 'c'; puts 2; else; puts 3; end; puts 4");
        assertPrints("1\n4\n", "case 'b'; when 'a', 'b'; puts 1; when 'c'; puts 2; else; puts 3; end; puts 4");
        assertPrints("2\n4\n", "case 'c'; when 'a', 'b'; puts 1; when 'c'; puts 2; else; puts 3; end; puts 4");
        assertPrints("3\n4\n", "case 'd'; when 'a', 'b'; puts 1; when 'c'; puts 2; else; puts 3; end; puts 4");
    }

    @Test
    public void testSimpleConditions() {
        assertPrints("1\n", "case; when true; puts 1; when false; puts 2; end");
    }

}
