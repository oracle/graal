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
 * Test {@code class} expressions.
 */
public class ClassTests extends RubyTests {

    @Test
    public void testDefine() {
        assertPrints("Foo\n", "class Foo; end; puts Foo");
    }

    @Test
    public void testMethods() {
        assertPrints("14\n", "class Foo; def test; return 14; end; end; foo=Foo.new; puts foo.test");
        assertPrints("14\n", "class Foo; def test(x); return x; end; end; foo=Foo.new; puts foo.test(14)");
    }

    @Test
    public void testDefineHasScope() {
        assertPrints("14\n", "x=14; class Foo; x=0; end; puts x");
        assertPrints("14\n", "class Foo; x=14; puts x; end");
    }

    @Test
    public void testInitialise() {
        assertPrints("14\n", "class Foo; def initialize; puts 14; end; end; Foo.new");
        assertPrints("14\n", "class Foo; def initialize(x); puts x; end; end; Foo.new 14");
        assertPrints("14\n", "class Foo; def initialize(x); puts x; end; end; Foo.new(14)");
    }

    @Test
    public void testInstanceVariables() {
        assertPrints("14\n", "class Foo; def a; @x=14; end; def b; @x; end; end; foo=Foo.new; foo.a; puts foo.b");
    }

    @Test
    public void testMissingVariables() {
        assertPrints("NilClass\n", "class Foo; def a; @x; end; end; foo=Foo.new; puts foo.a.class");
    }

    @Test
    public void testClassConstants() {
        assertPrints("14\n", "class Foo; X=14; def foo; puts X; end; end; foo=Foo.new; foo.foo");
    }

    @Test
    public void testReopeningSingletonClass() {
        assertPrints("1\n", "foo = Object.new; class << foo; def bar; puts 1; end; end; foo.bar");
    }

    @Test
    public void testInheritance() {
        assertPrints("14\n", "class Foo; def foo; puts 14; end; end; class Bar < Foo; end; Bar.new.foo");
    }

    @Test
    public void testSingletonInheritance() {
        assertPrints("14\n", "class Foo; def self.foo; puts 14; end; end; class Bar < Foo; end; Bar.foo");
    }

    @Test
    public void testNestedClass() {
        assertPrints("14\n", "class Foo; class Bar; def bar; 14; end; end; def foo; Bar.new; end; end; puts Foo.new.foo.bar");
    }

}
