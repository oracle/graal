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

public class ConstantTests extends RubyTests {

    @Test
    public void testTopLevelConstants() {
        assertPrints("14\n", "X=14; class Foo; def foo; puts X; end; end; f=Foo.new; f.foo");
    }

    @Test
    public void testNestedConstants() {
        assertPrints("", "module X; class A; end; class B; class C < A; end; end; end");
    }

    @Test
    public void testSearchInParentModules() {
        assertPrints("14\n", "module A; C = 14; module B; def self.test; puts C; end; end; end; A::B.test");
    }

}
