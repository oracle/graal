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
 * Test @@ class variables. There is a lot of counter-intuitive behavior here - they aren't instance
 * variables in class objects. The books describe them as being in a 'class hierarchy', rather than
 * in a class. Also, the object they are defined it is not consistent - sometimes it is the class of
 * self, sometimes it's just self.
 */
public class ClassLocalTests extends RubyTests {

    @Test
    public void testBasic() {
        assertPrints("14\n", "@@x = 14; puts @@x");
    }

    /*
     * Test that they are defined for a hierarchy, not a class.
     */

    @Test
    public void testHeirarchyNotClassVariable() {
        assertPrints("2\n", "class Foo; @@value = 14; end; class Bar < Foo; @@value = 2; end; class Foo; puts @@value; end");
    }

    /*
     * Test that they take the correct class at different times. In a method, they use the class of
     * self. In a class definition they use that class, not the class of self, which is Class.
     */

    @Test
    public void testCorrectClass() {
        assertPrints("1\n", "class Foo; @@x = 1; def foo; puts @@x; end; end; Foo.new.foo");
    }

    @Test
    public void testWithinSelfMethod() {
        assertPrints("14\n", "class Foo; @@foo = 14; def self.foo; @@foo; end; end; puts Foo.foo");
    }

}
