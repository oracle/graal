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
 * Test the {@code Continuation} class.
 */
public class ContinuationTests extends RubyTests {

    @Test
    public void testRequired18() {
        assertPrints(RubyVersion.RUBY_18, "", "callcc { |c| c.call }");
    }

    @Test
    public void testRequired19() {
        assertPrints(RubyVersion.RUBY_19, "", "require \"continuation\"; callcc { |c| c.call }");
    }

    @Test
    public void testRequired20() {
        assertPrints(RubyVersion.RUBY_20, "", "require \"continuation\"; callcc { |c| c.call }");
    }

    @Test
    public void testOneShotGoingUpCallstack() {
        assertPrints(RubyVersion.RUBY_18, "1\n3\n", "callcc { |c| puts 1; c.call; puts 2 }; puts 3");
    }

    @Test
    public void testOneShotGoingUpCallstackReturnValue() {
        assertPrints(RubyVersion.RUBY_18, "14\n", "puts callcc { |c| c.call 14 }");
    }

    @Test
    public void testNestedOneShotGoingUpCallstack() {
        assertPrints(RubyVersion.RUBY_18, "1\n2\n4\n5\n", "callcc { |c1| puts 1; callcc { |c2| puts 2; c2.call; puts 3 }; puts 4 }; puts 5");
        assertPrints(RubyVersion.RUBY_18, "1\n2\n5\n", "callcc { |c1| puts 1; callcc { |c2| puts 2; c1.call; puts 3 }; puts 4 }; puts 5");
    }

}
