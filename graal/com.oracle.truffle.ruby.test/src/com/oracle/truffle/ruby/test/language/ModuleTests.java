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
 * Test {@code module} expressions.
 */
public class ModuleTests extends RubyTests {

    @Test
    public void testDefine() {
        assertPrints("Foo\n", "module Foo; end; puts Foo");
    }

    @Test
    public void testWithConstantDefinition() {
        assertPrints("14\n", "module Foo; FOO=14; end; puts Foo::FOO");
    }

    @Test
    public void testWithConstantDefinitionAndAccessInDeclaration() {
        assertPrints("14\n", "module Foo; FOO=14; puts FOO; end");
    }

    @Test
    public void testCanAccessObjectConstantsInModuleDefinition() {
        assertPrints("", "class Foo; end; module Bar; foo = Foo.new; end");
    }

    @Test
    public void testInclude() {
        assertPrints("14\n", "module Foo; FOO=14; end; module Bar; include Foo; end; puts Bar::FOO");
    }

    @Test
    public void testModuleFunction() {
        assertPrints("1\n", "module Foo; def bar; puts 1; end; module_function :bar; end; Foo::bar");
    }

    @Test
    public void testDistinctModule() {
        assertPrints("true\n", "module A; module X; end; end; module B; module X; end; end; puts A::X.object_id != B::X.object_id");
    }

}
