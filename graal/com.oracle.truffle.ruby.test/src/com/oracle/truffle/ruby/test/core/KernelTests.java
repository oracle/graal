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

import com.oracle.truffle.ruby.runtime.configuration.*;
import com.oracle.truffle.ruby.test.*;

/**
 * Test {@code Kernel}.
 */
public class KernelTests extends RubyTests {

    @Test
    public void testPutsEmpty() {
        assertPrints("\n", "puts");
    }

    @Test
    public void testPutsString() {
        assertPrints("1\n", "puts 1");
    }

    @Test
    public void testPrintfNoFormatting() {
        assertPrints("", "printf");
        assertPrints("foo", "printf \"foo\"");
        assertPrints("foo\n", "printf \"foo\\n\"");
    }

    @Test
    public void testPrintfDecimal() {
        assertPrints("foo14bar", "printf \"foo%dbar\", 14");
    }

    @Test
    public void testGets() {
        assertPrintsWithInput("test\n", "puts gets", "test\n");
    }

    @Test
    public void testInteger() {
        assertPrints("14\n", "puts Integer(\"14\")");
    }

    @Test
    public void testEval() {
        assertPrints("16\n", "puts eval(\"14 + 2\")");
    }

    @Test
    public void testBindingLocalVariables() {
        // Use the current binding for eval
        assertPrints("16\n", "x = 14; y = 2; puts eval(\"x + y\", binding)");

        // Use the binding returned from a method for eval
        assertPrints("16\n", "def foo; x = 14; y = 2; binding; end; puts eval(\"x + y\", foo)");
    }

    @Test
    public void testBindingInstanceVariables() {
        // Use the binding returned from a method in an object for eval
        assertPrints("16\n", "class Foo; def foo; @x = 14; @y = 2; binding; end; end; puts eval(\"@x + @y\", Foo.new.foo)");
    }

    @Ignore
    @Test
    public void testSetTraceFuncLine() {
        final ConfigurationBuilder configuration = new ConfigurationBuilder();
        configuration.setTrace(true);

        final String code = "def foo\n" + //
                        "    a = 14\n" + //
                        "    b = 2\n" + //
                        "    a + b\n" + //
                        "end\n" + //
                        "\n" + //
                        "set_trace_func proc { |event, file, line, id, binding, classname|\n" + //
                        "    if event == \"line\"\n" + //
                        "        puts file + \":\" + line.to_s\n" + //
                        "    end\n" + //
                        "}\n" + //
                        "\n" + //
                        "foo";
        final String input = "";
        final String expected = "(test):13\n(test):2\n(test):3\n(test):4\n";
        assertPrints(new Configuration(configuration), expected, "(test)", code, input);
    }

    @Test
    public void testBlockGiven() {
        assertPrints("false\n", "def foo; puts block_given?; end; foo");
        assertPrints("true\n", "def foo; puts block_given?; end; foo do; end");
        assertPrints("true\n", "def foo; puts block_given?; end; foo {}");
        assertPrints("true\n", "def foo; puts block_given?; end; foo &:+");
    }

    @Test
    public void testLoop() {
        assertPrints("14\n", "loop do; break; end; puts 14");
    }

}
