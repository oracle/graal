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
        assertEquals(new IntegerStamp(32, false, 1, 1, 0x1, 0x1), ConstantNode.forBoolean(true, graph).stamp());
        assertEquals(new IntegerStamp(32, false, 0, 0, 0x0, 0x0), ConstantNode.forBoolean(false, graph).stamp());
    }

    @Test
    public void testByteConstant() {
        assertEquals(new IntegerStamp(32, false, 0, 0, 0x0, 0x0), ConstantNode.forByte((byte) 0, graph).stamp());
        assertEquals(new IntegerStamp(32, false, 16, 16, 0x10, 0x10), ConstantNode.forByte((byte) 16, graph).stamp());
        assertEquals(new IntegerStamp(32, false, -16, -16, 0xfffffff0L, 0xfffffff0L), ConstantNode.forByte((byte) -16, graph).stamp());
        assertEquals(new IntegerStamp(32, false, 127, 127, 0x7f, 0x7f), ConstantNode.forByte((byte) 127, graph).stamp());
        assertEquals(new IntegerStamp(32, false, -128, -128, 0xffffff80L, 0xffffff80L), ConstantNode.forByte((byte) -128, graph).stamp());
    }

    @Test
    public void testShortConstant() {
        assertEquals(new IntegerStamp(32, false, 0, 0, 0x0, 0x0), ConstantNode.forShort((short) 0, graph).stamp());
        assertEquals(new IntegerStamp(32, false, 128, 128, 0x80, 0x80), ConstantNode.forShort((short) 128, graph).stamp());
        assertEquals(new IntegerStamp(32, false, -128, -128, 0xffffff80L, 0xffffff80L), ConstantNode.forShort((short) -128, graph).stamp());
        assertEquals(new IntegerStamp(32, false, 32767, 32767, 0x7fff, 0x7fff), ConstantNode.forShort((short) 32767, graph).stamp());
        assertEquals(new IntegerStamp(32, false, -32768, -32768, 0xffff8000L, 0xffff8000L), ConstantNode.forShort((short) -32768, graph).stamp());
    }

    @Test
    public void testCharConstant() {
        assertEquals(new IntegerStamp(32, false, 0, 0, 0x0, 0x0), ConstantNode.forChar((char) 0, graph).stamp());
        assertEquals(new IntegerStamp(32, false, 'A', 'A', 'A', 'A'), ConstantNode.forChar('A', graph).stamp());
        assertEquals(new IntegerStamp(32, false, 128, 128, 0x80, 0x80), ConstantNode.forChar((char) 128, graph).stamp());
        assertEquals(new IntegerStamp(32, false, 65535, 65535, 0xffff, 0xffff), ConstantNode.forChar((char) 65535, graph).stamp());
    }

    @Test
    public void testIntConstant() {
        assertEquals(new IntegerStamp(32, false, 0, 0, 0x0, 0x0), ConstantNode.forInt(0, graph).stamp());
        assertEquals(new IntegerStamp(32, false, 128, 128, 0x80, 0x80), ConstantNode.forInt(128, graph).stamp());
        assertEquals(new IntegerStamp(32, false, -128, -128, 0xffffff80L, 0xffffff80L), ConstantNode.forInt(-128, graph).stamp());
        assertEquals(new IntegerStamp(32, false, Integer.MAX_VALUE, Integer.MAX_VALUE, 0x7fffffff, 0x7fffffff), ConstantNode.forInt(Integer.MAX_VALUE, graph).stamp());
        assertEquals(new IntegerStamp(32, false, Integer.MIN_VALUE, Integer.MIN_VALUE, 0x80000000L, 0x80000000L), ConstantNode.forInt(Integer.MIN_VALUE, graph).stamp());
    }

    @Test
    public void testLongConstant() {
        assertEquals(new IntegerStamp(64, false, 0, 0, 0x0, 0x0), ConstantNode.forLong(0, graph).stamp());
        assertEquals(new IntegerStamp(64, false, 128, 128, 0x80, 0x80), ConstantNode.forLong(128, graph).stamp());
        assertEquals(new IntegerStamp(64, false, -128, -128, 0xffffffffffffff80L, 0xffffffffffffff80L), ConstantNode.forLong(-128, graph).stamp());
        assertEquals(new IntegerStamp(64, false, Long.MAX_VALUE, Long.MAX_VALUE, 0x7fffffffffffffffL, 0x7fffffffffffffffL), ConstantNode.forLong(Long.MAX_VALUE, graph).stamp());
        assertEquals(new IntegerStamp(64, false, Long.MIN_VALUE, Long.MIN_VALUE, 0x8000000000000000L, 0x8000000000000000L), ConstantNode.forLong(Long.MIN_VALUE, graph).stamp());
    }

    @Test
    public void testPositiveRanges() {
        assertEquals(new IntegerStamp(32, false, 0, 0, 0, 0), StampFactory.forInteger(Kind.Int, 0, 0));
        assertEquals(new IntegerStamp(32, false, 0, 1, 0, 1), StampFactory.forInteger(Kind.Int, 0, 1));
        assertEquals(new IntegerStamp(32, false, 0, 0x123, 0, 0x1ff), StampFactory.forInteger(Kind.Int, 0, 0x123));
        assertEquals(new IntegerStamp(32, false, 0x120, 0x123, 0x120, 0x123), StampFactory.forInteger(Kind.Int, 0x120, 0x123));
        assertEquals(new IntegerStamp(32, false, 10000, 15000, 0x2000, 0x3fff), StampFactory.forInteger(Kind.Int, 10000, 15000));
        assertEquals(new IntegerStamp(64, false, 0, 1, 0, 1), StampFactory.forInteger(Kind.Long, 0, 1));
        assertEquals(new IntegerStamp(64, false, 10000, 15000, 0x2000, 0x3fff), StampFactory.forInteger(Kind.Long, 10000, 15000));
        assertEquals(new IntegerStamp(64, false, 140000000000L, 150000000000L, 0x2000000000L, 0x23ffffffffL), StampFactory.forInteger(Kind.Long, 140000000000L, 150000000000L));
    }

    @Test
    public void testNegativeRanges() {
        assertEquals(new IntegerStamp(32, false, -2, -1, 0xfffffffeL, 0xffffffffL), StampFactory.forInteger(Kind.Int, -2, -1));
        assertEquals(new IntegerStamp(32, false, -20, -10, 0xffffffe0L, 0xffffffffL), StampFactory.forInteger(Kind.Int, -20, -10));
        assertEquals(new IntegerStamp(32, false, -10000, 0, 0, 0xffffffffL), StampFactory.forInteger(Kind.Int, -10000, 0));
        assertEquals(new IntegerStamp(32, false, -10000, -1, 0xffffc000L, 0xffffffffL), StampFactory.forInteger(Kind.Int, -10000, -1));
        assertEquals(new IntegerStamp(32, false, -10010, -10000, 0xffffd8e0L, 0xffffd8ffL), StampFactory.forInteger(Kind.Int, -10010, -10000));
        assertEquals(new IntegerStamp(64, false, -2, -1, 0xfffffffffffffffeL, 0xffffffffffffffffL), StampFactory.forInteger(Kind.Long, -2, -1));
        assertEquals(new IntegerStamp(64, false, -10010, -10000, 0xffffffffffffd8e0L, 0xffffffffffffd8ffL), StampFactory.forInteger(Kind.Long, -10010, -10000));
        assertEquals(new IntegerStamp(64, false, -150000000000L, -140000000000L, 0xffffffdc00000000L, 0xffffffdfffffffffL), StampFactory.forInteger(Kind.Long, -150000000000L, -140000000000L));
    }

    @Test
    public void testMixedRanges() {
        assertEquals(new IntegerStamp(32, false, -1, 0, 0, 0xffffffffL), StampFactory.forInteger(Kind.Int, -1, 0));
        assertEquals(new IntegerStamp(32, false, -10000, 1000, 0, 0xffffffffL), StampFactory.forInteger(Kind.Int, -10000, 1000));
        assertEquals(new IntegerStamp(64, false, -10000, 1000, 0, 0xffffffffffffffffL), StampFactory.forInteger(Kind.Long, -10000, 1000));
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
        assertEquals(new IntegerStamp(32, false, 0, 0xff, 0, 0xff), StampTool.xor(new IntegerStamp(32, false, 0, 0, 0, 0), new IntegerStamp(32, false, 0, 0xff, 0, 0xff)));
        assertEquals(new IntegerStamp(32, false, 0x10, 0x1f, 0x10, 0x1f), StampTool.xor(new IntegerStamp(32, false, 0, 0, 0, 0), new IntegerStamp(32, false, 0x10, 0x1f, 0x10, 0x1f)));
        assertEquals(new IntegerStamp(32, false, 0x0, 0xf, 0x0, 0xf), StampTool.xor(new IntegerStamp(32, false, 0x10, 0x10, 0x10, 0x10), new IntegerStamp(32, false, 0x10, 0x1f, 0x10, 0x1f)));
        assertEquals(new IntegerStamp(32, false, 0x10, 0x1f, 0x10, 0x1f), StampTool.xor(new IntegerStamp(32, false, 0x10, 0x10, 0x10, 0x10), new IntegerStamp(32, false, 0x0, 0xf, 0x0, 0xf)));
    }

    @Test
    public void testNot() {
        assertEquals(new IntegerStamp(32, false, -11, -1, 0xffff_fff0L, 0xffff_ffffL), StampTool.not(new IntegerStamp(32, false, 0, 10, 0, 0xf)));
    }

    @Test
    public void testAddIntSimple() {
        assertEquals(StampFactory.forInteger(Kind.Int, 0, 30, 0, 31), StampTool.add(StampFactory.forInteger(Kind.Int, 0, 10), StampFactory.forInteger(Kind.Int, 0, 20)));
    }

    @Test
    public void testAddNegativeOverFlowInt1() {
        assertEquals(StampFactory.forInteger(Kind.Int, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 0xffff_ffffL),
                        StampTool.add(StampFactory.forInteger(Kind.Int, Integer.MIN_VALUE, 0), StampFactory.forInteger(Kind.Int, -1, 0)));
    }

    @Test
    public void testAddNegativeOverFlowInt2() {
        assertEquals(StampFactory.forInteger(Kind.Int, Integer.MAX_VALUE - 2, Integer.MAX_VALUE, 0x7fff_fffcL, 0x7fff_ffffL),
                        StampTool.add(StampFactory.forInteger(Kind.Int, Integer.MIN_VALUE, Integer.MIN_VALUE + 1), StampFactory.forInteger(Kind.Int, -3, -2)));
    }

    @Test
    public void testAddPositiveOverFlowInt1() {
        assertEquals(StampFactory.forKind(Kind.Int), StampTool.add(StampFactory.forInteger(Kind.Int, 0, 1), StampFactory.forInteger(Kind.Int, 0, Integer.MAX_VALUE)));
    }

    @Test
    public void testAddPositiveOverFlowInt2() {
        assertEquals(StampFactory.forInteger(Kind.Int, Integer.MIN_VALUE, Integer.MIN_VALUE + 2),
                        StampTool.add(StampFactory.forInteger(Kind.Int, Integer.MAX_VALUE - 1, Integer.MAX_VALUE), StampFactory.forInteger(Kind.Int, 2, 3)));
    }

    @Test
    public void testAddOverFlowsInt() {
        assertEquals(StampFactory.forKind(Kind.Int), StampTool.add(StampFactory.forInteger(Kind.Int, -1, 1), StampFactory.forInteger(Kind.Int, Integer.MIN_VALUE, Integer.MAX_VALUE)));
    }

    @Test
    public void testAddLongSimple() {
        assertEquals(StampFactory.forInteger(Kind.Long, 0, 30, 0, 31), StampTool.add(StampFactory.forInteger(Kind.Long, 0, 10), StampFactory.forInteger(Kind.Long, 0, 20)));
    }

    @Test
    public void testAddNegativOverFlowLong1() {
        assertEquals(StampFactory.forInteger(Kind.Long, Long.MIN_VALUE, Long.MAX_VALUE, 0, 0xffff_ffff_ffff_ffffL),
                        StampTool.add(StampFactory.forInteger(Kind.Long, Long.MIN_VALUE, Long.MIN_VALUE + 1), StampFactory.forInteger(Kind.Long, Integer.MIN_VALUE, Integer.MAX_VALUE)));
    }

    @Test
    public void testAddNegativeOverFlowLong2() {
        assertEquals(StampFactory.forInteger(Kind.Long, Long.MAX_VALUE - 2, Long.MAX_VALUE),
                        StampTool.add(StampFactory.forInteger(Kind.Long, Long.MIN_VALUE, Long.MIN_VALUE + 1), StampFactory.forInteger(Kind.Long, -3, -2)));
    }

    @Test
    public void testAddPositiveOverFlowLong1() {
        assertEquals(StampFactory.forKind(Kind.Long), StampTool.add(StampFactory.forInteger(Kind.Long, 0, 1), StampFactory.forInteger(Kind.Long, 0, Long.MAX_VALUE)));
    }

    @Test
    public void testAddPositiveOverFlowLong2() {
        assertEquals(StampFactory.forInteger(Kind.Long, Long.MIN_VALUE, Long.MIN_VALUE + 2),
                        StampTool.add(StampFactory.forInteger(Kind.Long, Long.MAX_VALUE - 1, Long.MAX_VALUE), StampFactory.forInteger(Kind.Long, 2, 3)));
    }

    @Test
    public void testAddOverFlowsLong() {
        assertEquals(StampFactory.forKind(Kind.Long), StampTool.add(StampFactory.forInteger(Kind.Long, -1, 1), StampFactory.forInteger(Kind.Long, Long.MIN_VALUE, Long.MAX_VALUE)));
    }

    @Test
    public void testAdd1() {
        assertEquals(StampFactory.forInteger(Kind.Int, Integer.MIN_VALUE + 1, 31 + (Integer.MIN_VALUE + 1)),
                        StampTool.add(StampFactory.forInteger(Kind.Int, 0, 31), StampFactory.forInteger(Kind.Int, Integer.MIN_VALUE + 1, Integer.MIN_VALUE + 1)));
    }

    @Test
    public void testAdd2() {
        assertEquals(StampFactory.forInteger(Kind.Int, 0x8000_007e, 0x8000_007f, 0x8000_007eL, 0x8000_007fL),
                        StampTool.add(StampFactory.forInteger(Kind.Int, 0x7fff_fffe, 0x7fff_ffff, 0x7fff_fffeL, 0x7ffff_fffL), StampFactory.forInteger(Kind.Int, 128, 128)));
    }

    @Test
    public void testAdd3() {
        assertEquals(StampFactory.forInteger(Kind.Long, Long.MIN_VALUE, Long.MAX_VALUE - 1, 0, 0xffff_ffff_ffff_fffeL),
                        StampTool.add(StampFactory.forInteger(Kind.Long, Long.MIN_VALUE, Long.MAX_VALUE - 1, 0, 0xffff_ffff_ffff_fffeL),
                                        StampFactory.forInteger(Kind.Long, Long.MIN_VALUE, Long.MAX_VALUE - 1, 0, 0xffff_ffff_ffff_fffeL)));

    }

    @Test
    public void testAnd() {
        assertEquals(new IntegerStamp(32, false, Integer.MIN_VALUE, 0x40000000L, 0, 0xc0000000L), StampTool.and(StampFactory.forKind(Kind.Int), StampFactory.forConstant(Constant.forInt(0xc0000000))));
    }

    private static void testSignExtendShort(long lower, long upper) {
        Stamp shortStamp = StampFactory.forInteger(16, false, lower, upper);
        Stamp intStamp = StampTool.signExtend(shortStamp, 32);
        assertEquals(StampFactory.forInteger(32, false, lower, upper), intStamp);
    }

    @Test
    public void testSignExtend() {
        testSignExtendShort(5, 7);
        testSignExtendShort(0, 42);
        testSignExtendShort(-42, -1);
        testSignExtendShort(-42, 0);
        testSignExtendShort(-1, 1);
        testSignExtendShort(Short.MIN_VALUE, Short.MAX_VALUE);
    }

    private static void testZeroExtendShort(long lower, long upper, long newLower, long newUpper) {
        Stamp shortStamp = StampFactory.forInteger(16, false, lower, upper);
        Stamp intStamp = StampTool.zeroExtend(shortStamp, 32);
        assertEquals(StampFactory.forInteger(32, false, newLower, newUpper), intStamp);
    }

    @Test
    public void testZeroExtend() {
        testZeroExtendShort(5, 7, 5, 7);
        testZeroExtendShort(0, 42, 0, 42);
        testZeroExtendShort(-42, -1, 0xFFFF - 41, 0xFFFF);
        testZeroExtendShort(-42, 0, 0, 0xFFFF);
        testZeroExtendShort(-1, 1, 0, 0xFFFF);
        testZeroExtendShort(Short.MIN_VALUE, Short.MAX_VALUE, 0, 0xFFFF);
    }

    private static void testSignExtendChar(long lower, long upper, long newLower, long newUpper) {
        Stamp charStamp = StampFactory.forInteger(16, true, lower, upper);
        Stamp uintStamp = StampTool.signExtend(charStamp, 32);
        assertEquals(StampFactory.forInteger(32, true, newLower, newUpper), uintStamp);
    }

    @Test
    public void testSignExtendUnsigned() {
        testSignExtendChar(5, 7, 5, 7);
        testSignExtendChar(0, 42, 0, 42);
        testSignExtendChar(5, 0xF000, 5, 0xFFFFF000L);
        testSignExtendChar(0, 0xF000, 0, 0xFFFFF000L);
        testSignExtendChar(0xF000, Character.MAX_VALUE, 0xFFFFF000L, 0xFFFFFFFFL);
        testSignExtendChar(Character.MIN_VALUE, Character.MAX_VALUE, 0, 0xFFFFFFFFL);
    }

    private static void testZeroExtendChar(long lower, long upper) {
        Stamp charStamp = StampFactory.forInteger(16, true, lower, upper);
        Stamp uintStamp = StampTool.zeroExtend(charStamp, 32);
        assertEquals(StampFactory.forInteger(32, true, lower, upper), uintStamp);
    }

    @Test
    public void testZeroExtendUnsigned() {
        testZeroExtendChar(5, 7);
        testZeroExtendChar(0, 42);
        testZeroExtendChar(5, 0xF000);
        testZeroExtendChar(0, 0xF000);
        testZeroExtendChar(0xF000, Character.MAX_VALUE);
        testZeroExtendChar(Character.MIN_VALUE, Character.MAX_VALUE);
    }
}
