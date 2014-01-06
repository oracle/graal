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
 * Test the {@code Object} class.
 */
public class ObjectTests extends RubyTests {

    @Test
    public void testARGV() {
        assertPrints("1\n2\n3\n", "puts ARGV", "1", "2", "3");
    }

    @Test
    public void testInstanceVariableDefined() {
        assertPrints("true\n", "class Foo; def initialize; @bar=14; end; end; puts Foo.new.instance_variable_defined?(:@bar)");
        assertPrints("true\n", "class Foo; def initialize; @bar=14; end; end; puts Foo.new.instance_variable_defined?(\"@bar\")");
        assertPrints("true\n", "class Foo; def initialize; instance_variable_set(:@bar, 14); end; end; puts Foo.new.instance_variable_defined?(\"@bar\")");
        assertPrints("false\n", "class Foo; def initialize; @foo=14; end; end; puts Foo.new.instance_variable_defined?(:@bar)");
        assertPrints("false\n", "class Foo; def initialize; @foo=14; end; end; puts Foo.new.instance_variable_defined?(\"@bar\")");
        assertPrints("false\n", "class Foo; def initialize; instance_variable_set(:@foo, 14); end; end; puts Foo.new.instance_variable_defined?(\"@bar\")");
    }

    @Test
    public void testInstanceVariableGet() {
        assertPrints("14\n", "class Foo; def initialize; @bar=14; end; end; puts Foo.new.instance_variable_get(:@bar)");
        assertPrints("14\n", "class Foo; def initialize; @bar=14; end; end; puts Foo.new.instance_variable_get(\"@bar\")");
    }

    @Test
    public void testInstanceVariableSet() {
        assertPrints("14\n", "class Foo; attr_accessor :bar; end; foo = Foo.new; foo.instance_variable_set(:@bar, 14); puts foo.bar");
        assertPrints("14\n", "class Foo; attr_accessor :bar; end; foo = Foo.new; foo.instance_variable_set(\"@bar\", 14); puts foo.bar");
    }

    @Test
    public void testInstanceVariables() {
        assertPrints("a\nb\n", "class Foo; def initialize; @a=2; @b=14; end; end; puts Foo.new.instance_variables");
        assertPrints("a\nb\n", "class Foo; def initialize; @a=2; instance_variable_set(:@b, 14); end; end; puts Foo.new.instance_variables");
    }

    @Test
    public void testSend() {
        assertPrints("14\n", "self.send(:puts, 14)");
        assertPrints("14\n", "self.send(\"puts\", 14)");
    }

    @Test
    public void testExtend() {
        assertPrints("14\n", "class Foo; end; module Bar; def bar; puts 14; end; end; foo = Foo.new; foo.extend(Bar); foo.bar");
    }

    @Test
    public void testSingletonMethods() {
        assertPrints("[:baz]\n", "class Foo; def bar; end; end; foo = Foo.new; def foo.baz; end; puts foo.singleton_methods.to_s");
    }

}
