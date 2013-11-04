/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.graal.nodes.test;

import static org.junit.Assert.*;

import org.junit.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.type.*;

/**
 * This class tests that integer stamps are created correctly for constants.
 */
public class IntegerStampTest {

    private StructuredGraph graph;

    @Before
    public void before() {
        graph = new StructuredGraph();
    }

    @Test
    public void testBooleanConstant() {
        assertEquals(new IntegerStamp(Kind.Int, 1, 1, 0x1, 0x1), ConstantNode.forBoolean(true, graph).stamp());
        assertEquals(new IntegerStamp(Kind.Int, 0, 0, 0x0, 0x0), ConstantNode.forBoolean(false, graph).stamp());
    }

    @Test
    public void testByteConstant() {
        assertEquals(new IntegerStamp(Kind.Int, 0, 0, 0x0, 0x0), ConstantNode.forByte((byte) 0, graph).stamp());
        assertEquals(new IntegerStamp(Kind.Int, 16, 16, 0x10, 0x10), ConstantNode.forByte((byte) 16, graph).stamp());
        assertEquals(new IntegerStamp(Kind.Int, -16, -16, 0xfffffff0L, 0xfffffff0L), ConstantNode.forByte((byte) -16, graph).stamp());
        assertEquals(new IntegerStamp(Kind.Int, 127, 127, 0x7f, 0x7f), ConstantNode.forByte((byte) 127, graph).stamp());
        assertEquals(new IntegerStamp(Kind.Int, -128, -128, 0xffffff80L, 0xffffff80L), ConstantNode.forByte((byte) -128, graph).stamp());
    }

    @Test
    public void testShortConstant() {
        assertEquals(new IntegerStamp(Kind.Int, 0, 0, 0x0, 0x0), ConstantNode.forShort((short) 0, graph).stamp());
        assertEquals(new IntegerStamp(Kind.Int, 128, 128, 0x80, 0x80), ConstantNode.forShort((short) 128, graph).stamp());
        assertEquals(new IntegerStamp(Kind.Int, -128, -128, 0xffffff80L, 0xffffff80L), ConstantNode.forShort((short) -128, graph).stamp());
        assertEquals(new IntegerStamp(Kind.Int, 32767, 32767, 0x7fff, 0x7fff), ConstantNode.forShort((short) 32767, graph).stamp());
        assertEquals(new IntegerStamp(Kind.Int, -32768, -32768, 0xffff8000L, 0xffff8000L), ConstantNode.forShort((short) -32768, graph).stamp());
    }

    @Test
    public void testCharConstant() {
        assertEquals(new IntegerStamp(Kind.Int, 0, 0, 0x0, 0x0), ConstantNode.forChar((char) 0, graph).stamp());
        assertEquals(new IntegerStamp(Kind.Int, 'A', 'A', 'A', 'A'), ConstantNode.forChar('A', graph).stamp());
        assertEquals(new IntegerStamp(Kind.Int, 128, 128, 0x80, 0x80), ConstantNode.forChar((char) 128, graph).stamp());
        assertEquals(new IntegerStamp(Kind.Int, 65535, 65535, 0xffff, 0xffff), ConstantNode.forChar((char) 65535, graph).stamp());
    }

    @Test
    public void testIntConstant() {
        assertEquals(new IntegerStamp(Kind.Int, 0, 0, 0x0, 0x0), ConstantNode.forInt(0, graph).stamp());
        assertEquals(new IntegerStamp(Kind.Int, 128, 128, 0x80, 0x80), ConstantNode.forInt(128, graph).stamp());
        assertEquals(new IntegerStamp(Kind.Int, -128, -128, 0xffffff80L, 0xffffff80L), ConstantNode.forInt(-128, graph).stamp());
        assertEquals(new IntegerStamp(Kind.Int, Integer.MAX_VALUE, Integer.MAX_VALUE, 0x7fffffff, 0x7fffffff), ConstantNode.forInt(Integer.MAX_VALUE, graph).stamp());
        assertEquals(new IntegerStamp(Kind.Int, Integer.MIN_VALUE, Integer.MIN_VALUE, 0x80000000L, 0x80000000L), ConstantNode.forInt(Integer.MIN_VALUE, graph).stamp());
    }

    @Test
    public void testLongConstant() {
        assertEquals(new IntegerStamp(Kind.Long, 0, 0, 0x0, 0x0), ConstantNode.forLong(0, graph).stamp());
        assertEquals(new IntegerStamp(Kind.Long, 128, 128, 0x80, 0x80), ConstantNode.forLong(128, graph).stamp());
        assertEquals(new IntegerStamp(Kind.Long, -128, -128, 0xffffffffffffff80L, 0xffffffffffffff80L), ConstantNode.forLong(-128, graph).stamp());
        assertEquals(new IntegerStamp(Kind.Long, Long.MAX_VALUE, Long.MAX_VALUE, 0x7fffffffffffffffL, 0x7fffffffffffffffL), ConstantNode.forLong(Long.MAX_VALUE, graph).stamp());
        assertEquals(new IntegerStamp(Kind.Long, Long.MIN_VALUE, Long.MIN_VALUE, 0x8000000000000000L, 0x8000000000000000L), ConstantNode.forLong(Long.MIN_VALUE, graph).stamp());
    }

    @Test
    public void testPositiveRanges() {
        assertEquals(new IntegerStamp(Kind.Int, 0, 0, 0, 0), StampFactory.forInteger(Kind.Int, 0, 0));
        assertEquals(new IntegerStamp(Kind.Int, 0, 1, 0, 1), StampFactory.forInteger(Kind.Int, 0, 1));
        assertEquals(new IntegerStamp(Kind.Int, 0, 0x123, 0, 0x1ff), StampFactory.forInteger(Kind.Int, 0, 0x123));
        assertEquals(new IntegerStamp(Kind.Int, 0x120, 0x123, 0x120, 0x123), StampFactory.forInteger(Kind.Int, 0x120, 0x123));
        assertEquals(new IntegerStamp(Kind.Int, 10000, 15000, 0x2000, 0x3fff), StampFactory.forInteger(Kind.Int, 10000, 15000));
        assertEquals(new IntegerStamp(Kind.Long, 0, 1, 0, 1), StampFactory.forInteger(Kind.Long, 0, 1));
        assertEquals(new IntegerStamp(Kind.Long, 10000, 15000, 0x2000, 0x3fff), StampFactory.forInteger(Kind.Long, 10000, 15000));
        assertEquals(new IntegerStamp(Kind.Long, 140000000000L, 150000000000L, 0x2000000000L, 0x23ffffffffL), StampFactory.forInteger(Kind.Long, 140000000000L, 150000000000L));
    }

    @Test
    public void testNegativeRanges() {
        assertEquals(new IntegerStamp(Kind.Int, -2, -1, 0xfffffffeL, 0xffffffffL), StampFactory.forInteger(Kind.Int, -2, -1));
        assertEquals(new IntegerStamp(Kind.Int, -20, -10, 0xffffffe0L, 0xffffffffL), StampFactory.forInteger(Kind.Int, -20, -10));
        assertEquals(new IntegerStamp(Kind.Int, -10000, 0, 0, 0xffffffffL), StampFactory.forInteger(Kind.Int, -10000, 0));
        assertEquals(new IntegerStamp(Kind.Int, -10000, -1, 0xffffc000L, 0xffffffffL), StampFactory.forInteger(Kind.Int, -10000, -1));
        assertEquals(new IntegerStamp(Kind.Int, -10010, -10000, 0xffffd8e0L, 0xffffd8ffL), StampFactory.forInteger(Kind.Int, -10010, -10000));
        assertEquals(new IntegerStamp(Kind.Long, -2, -1, 0xfffffffffffffffeL, 0xffffffffffffffffL), StampFactory.forInteger(Kind.Long, -2, -1));
        assertEquals(new IntegerStamp(Kind.Long, -10010, -10000, 0xffffffffffffd8e0L, 0xffffffffffffd8ffL), StampFactory.forInteger(Kind.Long, -10010, -10000));
        assertEquals(new IntegerStamp(Kind.Long, -150000000000L, -140000000000L, 0xffffffdc00000000L, 0xffffffdfffffffffL), StampFactory.forInteger(Kind.Long, -150000000000L, -140000000000L));
    }

    @Test
    public void testMixedRanges() {
        assertEquals(new IntegerStamp(Kind.Int, -1, 0, 0, 0xffffffffL), StampFactory.forInteger(Kind.Int, -1, 0));
        assertEquals(new IntegerStamp(Kind.Int, -10000, 1000, 0, 0xffffffffL), StampFactory.forInteger(Kind.Int, -10000, 1000));
        assertEquals(new IntegerStamp(Kind.Long, -10000, 1000, 0, 0xffffffffffffffffL), StampFactory.forInteger(Kind.Long, -10000, 1000));
    }

    @Test
    public void testNarrowingConversions() {
        // byte cases
        assertEquals(StampFactory.forInteger(Kind.Int, 0, 0), StampTool.narrowingKindConversion(StampFactory.forInteger(Kind.Int, 0, 0), Kind.Byte));
        assertEquals(StampFactory.forInteger(Kind.Int, 0, 10), StampTool.narrowingKindConversion(StampFactory.forInteger(Kind.Int, 0, 10), Kind.Byte));
        assertEquals(StampFactory.forInteger(Kind.Int, 10, 20), StampTool.narrowingKindConversion(StampFactory.forInteger(Kind.Int, 10, 20), Kind.Byte));
        assertEquals(StampFactory.forInteger(Kind.Int, -10, 0), StampTool.narrowingKindConversion(StampFactory.forInteger(Kind.Int, -10, 0), Kind.Byte));
        assertEquals(StampFactory.forInteger(Kind.Int, -20, -10), StampTool.narrowingKindConversion(StampFactory.forInteger(Kind.Int, -20, -10), Kind.Byte));
        assertEquals(StampFactory.forInteger(Kind.Int, Byte.MIN_VALUE, Byte.MAX_VALUE), StampTool.narrowingKindConversion(StampFactory.forInteger(Kind.Int, 100, 200), Kind.Byte));
        assertEquals(StampFactory.forInteger(Kind.Int, Byte.MIN_VALUE, Byte.MAX_VALUE), StampTool.narrowingKindConversion(StampFactory.forInteger(Kind.Int, -100, 200), Kind.Byte));
        assertEquals(StampFactory.forInteger(Kind.Int, Byte.MIN_VALUE, Byte.MAX_VALUE), StampTool.narrowingKindConversion(StampFactory.forInteger(Kind.Int, -200, -100), Kind.Byte));
        // char cases
        assertEquals(StampFactory.forInteger(Kind.Int, 0, 10), StampTool.narrowingKindConversion(StampFactory.forInteger(Kind.Int, 0, 10), Kind.Char));
        assertEquals(StampFactory.forInteger(Kind.Int, 10, 20), StampTool.narrowingKindConversion(StampFactory.forInteger(Kind.Int, 10, 20), Kind.Char));
        assertEquals(StampFactory.forInteger(Kind.Int, Character.MIN_VALUE, Character.MAX_VALUE), StampTool.narrowingKindConversion(StampFactory.forInteger(Kind.Int, 20000, 80000), Kind.Char));
        assertEquals(StampFactory.forInteger(Kind.Int, Character.MIN_VALUE, Character.MAX_VALUE), StampTool.narrowingKindConversion(StampFactory.forInteger(Kind.Int, -10000, 40000), Kind.Char));
        assertEquals(StampFactory.forInteger(Kind.Int, Character.MIN_VALUE, Character.MAX_VALUE), StampTool.narrowingKindConversion(StampFactory.forInteger(Kind.Int, -40000, -10000), Kind.Char));
        // short cases
        assertEquals(StampFactory.forInteger(Kind.Int, 0, 10), StampTool.narrowingKindConversion(StampFactory.forInteger(Kind.Int, 0, 10), Kind.Short));
        assertEquals(StampFactory.forInteger(Kind.Int, 10, 20), StampTool.narrowingKindConversion(StampFactory.forInteger(Kind.Int, 10, 20), Kind.Short));
        assertEquals(StampFactory.forInteger(Kind.Int, Short.MIN_VALUE, Short.MAX_VALUE), StampTool.narrowingKindConversion(StampFactory.forInteger(Kind.Int, 20000, 40000), Kind.Short));
        assertEquals(StampFactory.forInteger(Kind.Int, Short.MIN_VALUE, Short.MAX_VALUE), StampTool.narrowingKindConversion(StampFactory.forInteger(Kind.Int, -10000, 40000), Kind.Short));
        assertEquals(StampFactory.forInteger(Kind.Int, Short.MIN_VALUE, Short.MAX_VALUE), StampTool.narrowingKindConversion(StampFactory.forInteger(Kind.Int, -40000, -10000), Kind.Short));
        // int cases
        assertEquals(StampFactory.forInteger(Kind.Int, 0, 10), StampTool.narrowingKindConversion(StampFactory.forInteger(Kind.Long, 0, 10), Kind.Int));
        assertEquals(StampFactory.forInteger(Kind.Int, 10, 20), StampTool.narrowingKindConversion(StampFactory.forInteger(Kind.Long, 10, 20), Kind.Int));
        assertEquals(StampFactory.forInteger(Kind.Int, Integer.MIN_VALUE, Integer.MAX_VALUE),
                        StampTool.narrowingKindConversion(StampFactory.forInteger(Kind.Long, 20000000000L, 40000000000L), Kind.Int));
        assertEquals(StampFactory.forInteger(Kind.Int, Integer.MIN_VALUE, Integer.MAX_VALUE),
                        StampTool.narrowingKindConversion(StampFactory.forInteger(Kind.Long, -10000000000L, 40000000000L), Kind.Int));
        assertEquals(StampFactory.forInteger(Kind.Int, Integer.MIN_VALUE, Integer.MAX_VALUE),
                        StampTool.narrowingKindConversion(StampFactory.forInteger(Kind.Long, -40000000000L, -10000000000L), Kind.Int));
    }

    @Test
    public void testXor() {
        assertEquals(new IntegerStamp(Kind.Int, 0, 0xff, 0, 0xff), StampTool.xor(new IntegerStamp(Kind.Int, 0, 0, 0, 0), new IntegerStamp(Kind.Int, 0, 0xff, 0, 0xff)));
        assertEquals(new IntegerStamp(Kind.Int, 0x10, 0x1f, 0x10, 0x1f), StampTool.xor(new IntegerStamp(Kind.Int, 0, 0, 0, 0), new IntegerStamp(Kind.Int, 0x10, 0x1f, 0x10, 0x1f)));
        assertEquals(new IntegerStamp(Kind.Int, 0x0, 0xf, 0x0, 0xf), StampTool.xor(new IntegerStamp(Kind.Int, 0x10, 0x10, 0x10, 0x10), new IntegerStamp(Kind.Int, 0x10, 0x1f, 0x10, 0x1f)));
        assertEquals(new IntegerStamp(Kind.Int, 0x10, 0x1f, 0x10, 0x1f), StampTool.xor(new IntegerStamp(Kind.Int, 0x10, 0x10, 0x10, 0x10), new IntegerStamp(Kind.Int, 0x0, 0xf, 0x0, 0xf)));
    }

    @Test
    public void testNot() {
        assertEquals(new IntegerStamp(Kind.Int, -11, -1, 0xfffffff0L, 0xffffffffL), StampTool.not(new IntegerStamp(Kind.Int, 0, 10, 0, 0xf)));
    }

    @Test
    public void testAdd() {
        assertEquals(StampFactory.forInteger(Kind.Int, 0, 30), StampTool.add(StampFactory.forInteger(Kind.Int, 0, 10), StampFactory.forInteger(Kind.Int, 0, 20)));
        assertEquals(StampFactory.forKind(Kind.Long),
                        StampTool.add(StampFactory.forInteger(Kind.Long, Long.MIN_VALUE, Long.MIN_VALUE + 1), StampFactory.forInteger(Kind.Long, Integer.MIN_VALUE, Integer.MAX_VALUE)));
        assertEquals(StampFactory.forInteger(Kind.Int, -2147483647, 31 - 2147483647),
                        StampTool.add(StampFactory.forInteger(Kind.Int, 0, 31), StampFactory.forInteger(Kind.Int, -2147483647, -2147483647)));

        assertEquals(StampFactory.forInteger(Kind.Int, 0x8000007e, 0x8000007f, 0x8000007eL, 0x8000007fL),
                        StampTool.add(StampFactory.forInteger(Kind.Int, 0x7ffffffe, 0x7fffffff, 0x7ffffffeL, 0x7fffffffL), StampFactory.forInteger(Kind.Int, 128, 128)));

        assertEquals(StampFactory.forInteger(Kind.Long, -9223372036854775808L, 9223372036854775806L, 0, 0xfffffffffffffffeL),
                        StampTool.add(StampFactory.forInteger(Kind.Long, -9223372036854775808L, 9223372036854775806L, 0, 0xfffffffffffffffeL),
                                        StampFactory.forInteger(Kind.Long, -9223372036854775808L, 9223372036854775806L, 0, 0xfffffffffffffffeL)));
    }

    @Test
    public void testAnd() {
        assertEquals(new IntegerStamp(Kind.Int, Integer.MIN_VALUE, 0x40000000L, 0, 0xc0000000L), StampTool.and(StampFactory.forKind(Kind.Int), StampFactory.forConstant(Constant.forInt(0xc0000000))));
    }
}
