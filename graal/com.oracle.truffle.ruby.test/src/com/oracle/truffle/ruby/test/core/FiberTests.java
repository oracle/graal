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
 * Test the {@code Fiber} class.
 */
public class FiberTests extends RubyTests {

    @Test
    public void testResume() {
        assertPrints("14\n", "f = Fiber.new { |x| puts x }; f.resume 14");
    }

    @Test
    public void testYield() {
        assertPrints("14\n", "f = Fiber.new { |x| Fiber.yield x }; puts f.resume(14)");
    }

    @Test
    public void testCountdown() {
        assertPrints("", "f = Fiber.new do |n|\n" + //
                        "    loop do\n" + //
                        "        n = Fiber.yield n - 1\n" + //
                        "    end\n" + //
                        "end\n" + //
                        "\n" + //
                        "n = 1000\n" + //
                        "\n" + //
                        "while n > 0\n" + //
                        "    n = f.resume n\n" + //
                        "end\n");
    }

}
