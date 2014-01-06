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
 * Test the {@code Array} class.
 */
public class ArrayTests extends RubyTests {

    @Test
    public void testLiteral() {
        assertPrints("", "puts []");
        assertPrints("1\n", "puts [1]");
        assertPrints("1\n2\n", "puts [1, 2]");
        assertPrints("1\n2\n3\n", "puts [1, 2, 3]");
        assertPrints("1\n2\n3\n", "puts [(1), 2, ((3))]");
    }

    @Test
    public void testReadIndexSimple() {
        assertPrints("2\n", "puts [1, 2, 3][1]");
        assertPrints("2\n", "x = [1, 2, 3]; puts x[1]");
        assertPrints("\n", "puts [1, 2, 3][10]");
    }

    @Test
    public void testReadIndexRange() {
        assertPrints("2\n3\n", "puts [1, 2, 3][1..2]");
        assertPrints("2\n3\n", "puts [1, 2, 3][1..-1]");
        assertPrints("1\n2\n3\n", "puts [1, 2, 3][0..-1]");
    }

    @Test
    public void testWriteIndexSimple() {
        assertPrints("1\n0\n3\n", "x = [1, 2, 3]; x[1] = 0; puts x");
        assertPrints("0\n", "x = [1, 2, 3]; puts x[1] = 0");
        assertPrints("1\n\n3\n", "x = [1]; x[2] = 3; puts x");
    }

    @Test
    public void testWriteIndexRangeSingle() {
        assertPrints("1\n0\n", "x = [1, 2, 3]; x[1..2] = 0; puts x");
        assertPrints("1\n0\n", "x = [1, 2, 3]; x[1..3] = 0; puts x");
        assertPrints("1\n0\n", "x = [1, 2, 3]; x[1..-1] = 0; puts x");
        assertPrints("0\n", "x = [1, 2, 3]; x[0..-1] = 0; puts x");
    }

    @Test
    public void testWriteIndexRangeArray() {
        assertPrints("1\n4\n5\n", "x = [1, 2, 3]; x[1..2] = [4, 5]; puts x");
        assertPrints("1\n4\n5\n", "x = [1, 2, 3]; x[1..-1] = [4, 5]; puts x");
        assertPrints("4\n5\n6\n", "x = [1, 2, 3]; x[0..-1] = [4, 5, 6]; puts x");
    }

    @Test
    public void testInsert() {
        assertPrints("1\n2\n3\n", "x = [1, 2]; x.insert(2, 3); puts x");
        assertPrints("1\n2\n3\n", "x = [1, 3]; x.insert(1, 2); puts x");
        assertPrints("1\n\n3\n", "x = [1]; x.insert(2, 3); puts x");
    }

    @Test
    public void testPush() {
        assertPrints("1\n2\n3\n", "x = [1, 2]; x.push(3); puts x");
        assertPrints("1\n2\n3\n", "x = [1, 2]; x << 3; puts x");
        assertPrints("1\n2\n3\n4\n", "x = [1, 2]; x << 3 << 4; puts x");
    }

    @Test
    public void testDeleteAt() {
        assertPrints("1\n3\n", "x = [1, 2, 3]; x.delete_at(1); puts x");
    }

    @Test
    public void testDup() {
        assertPrints("1\n2\n3\n", "x = [1, 2, 3]; y = x.dup; x.delete_at(1); puts y;");
    }

    @Test
    public void testOpAssign() {
        assertPrints("1\n", "x = [0]; x[0] += 1; puts x");
    }

    @Test
    public void testSize() {
        assertPrints("0\n", "puts [].size");
        assertPrints("1\n", "puts [1].size");
        assertPrints("2\n", "puts [1, 2].size");
    }

    @Test
    public void testEach() {
        assertPrints("", "[].each { |n| puts n }");
        assertPrints("1\n", "[1].each { |n| puts n }");
        assertPrints("1\n2\n3\n", "[1, 2, 3].each { |n| puts n }");
    }

    @Test
    public void testMap() {
        assertPrints("2\n4\n6\n", "puts [1, 2, 3].map { |n| n*2 }");
    }

    @Test
    public void testZip() {
        assertPrints("1\n4\n2\n5\n3\n6\n", "puts [1, 2, 3].zip([4, 5, 6])");
        assertPrints("1\n4\n2\n5\n", "puts [1, 2].zip([4, 5, 6])");
        assertPrints("1\n4\n2\n5\n3\n\n", "puts [1, 2, 3].zip([4, 5])");
        assertPrints("1\n3\n5\n2\n4\n6\n", "puts [1, 2].zip([3, 4], [5, 6])");
    }

    @Test
    public void testProduct() {
        assertPrints("[[1, 3], [1, 4], [2, 3], [2, 4]]\n", "puts [1, 2].product([3, 4]).to_s");
    }

    @Test
    public void testInject() {
        assertPrints("10\n", "puts [2, 3, 4].inject(1) { |a, n| a + n }");
    }

    @Test
    public void testLiteralSplat() {
        assertPrints("[1, 2, 3, 4]\n", "a = [3, 4]; puts [1, 2, *a].to_s");
        assertPrints("[1, 2, 3, 4, 5, 6]\n", "a = [3, 4]; puts [1, 2, *a, 5, 6].to_s");
    }

    @Test
    public void testSplatCast() {
        assertPrints("[14]\n", "def foo; value = 14; return *value; end; puts foo.to_s");
    }

    @Test
    public void testSelect() {
        assertPrints("[3, 4]\n", "foo = [1, 2, 3, 4]; bar = foo.select { |x| x > 2 }; puts bar.to_s");
    }

    @Test
    public void testInclude() {
        assertPrints("true\nfalse\n", "foo = [1, 2, 3, 4]; puts foo.include? 2; puts foo.include? 5");
    }

    @Test
    public void testSub() {
        assertPrints("[1, 4]\n", "puts ([1, 2, 2, 3, 4] - [2, 3]).to_s");
    }

    @Test
    public void testJoin() {
        assertPrints("1.2.3\n", "puts [1, 2, 3].join('.')");
    }

    @Test
    public void testShift() {
        assertPrints("1\n2\n3\n", "a = [1, 2, 3]; while b = a.shift; puts b; end");
    }

}
