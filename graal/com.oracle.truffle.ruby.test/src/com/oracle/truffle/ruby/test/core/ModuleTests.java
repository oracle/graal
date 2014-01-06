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
 * Test the {@code Module} class.
 */
public class ModuleTests extends RubyTests {

    @Test
    public void testAttrAccessor() {
        assertPrints("14\n", "class Foo; attr_accessor :x end; foo=Foo.new; foo.x=14; puts foo.x");
        assertPrints("14\n", "class Foo; attr_accessor(:x) end; foo=Foo.new; foo.x=14; puts foo.x");
        assertPrints("16\n", "class Foo; attr_accessor(:x, :y) end; foo=Foo.new; foo.x=14; foo.y=2; puts foo.x + foo.y");
        assertPrints("16\n", "class Foo; attr_accessor :x end; foo=Foo.new; foo.x=2; foo.x+=14; puts foo.x");
    }

    @Test
    public void testDefineMethod() {
        /*
         * We use Object#send instead of calling define_method directly because that method is
         * private.
         */

        assertPrints("14\n", "class Foo; end; Foo.send(:define_method, :foo) do; puts 14; end; Foo.new.foo");
    }

    @Test
    public void testDefinedBeforeScopeRun() {
        assertPrints("Module\n", "module Foo; puts Foo.class; end");
    }

    @Test
    public void testDefinedInRootScopeBeforeScopeRun() {
        assertPrints("Module\n", "module Foo; puts ::Foo.class; end");
    }

    @Test
    public void testModuleEval() {
        assertPrints("14\n", "class Foo; puts 14; end; Foo.module_eval('def foo; end'); Foo.new.foo");
    }

    @Test
    public void testNextInBlock() {
        assertPrints("1\n1\n1\n", "3.times do; puts 1; next; puts 2; end");
    }

}
