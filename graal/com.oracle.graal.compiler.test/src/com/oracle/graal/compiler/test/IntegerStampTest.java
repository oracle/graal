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
package com.oracle.graal.compiler.test;

import org.junit.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.type.*;

/**
 * This class tests that integer stamps are created correctly for constants.
 */
public class IntegerStampTest extends GraalCompilerTest {

    private StructuredGraph graph;

    @Before
    public void before() {
        graph = new StructuredGraph();
    }

    @Test
    public void testBooleanConstant() {
        assertEquals(new IntegerStamp(Kind.Int, 1, 1, 0x1), ConstantNode.forBoolean(true, graph).integerStamp());
        assertEquals(new IntegerStamp(Kind.Int, 0, 0, 0x0), ConstantNode.forBoolean(false, graph).integerStamp());
    }

    @Test
    public void testByteConstant() {
        assertEquals(new IntegerStamp(Kind.Int, 0, 0, 0x0), ConstantNode.forByte((byte) 0, graph).integerStamp());
        assertEquals(new IntegerStamp(Kind.Int, 16, 16, 0x10), ConstantNode.forByte((byte) 16, graph).integerStamp());
        assertEquals(new IntegerStamp(Kind.Int, -16, -16, 0xf0), ConstantNode.forByte((byte) -16, graph).integerStamp());
        assertEquals(new IntegerStamp(Kind.Int, 127, 127, 0x7f), ConstantNode.forByte((byte) 127, graph).integerStamp());
        assertEquals(new IntegerStamp(Kind.Int, -128, -128, 0x80), ConstantNode.forByte((byte) -128, graph).integerStamp());
    }

    @Test
    public void testShortConstant() {
        assertEquals(new IntegerStamp(Kind.Int, 0, 0, 0x0), ConstantNode.forShort((short) 0, graph).integerStamp());
        assertEquals(new IntegerStamp(Kind.Int, 128, 128, 0x80), ConstantNode.forShort((short) 128, graph).integerStamp());
        assertEquals(new IntegerStamp(Kind.Int, -128, -128, 0xff80), ConstantNode.forShort((short) -128, graph).integerStamp());
        assertEquals(new IntegerStamp(Kind.Int, 32767, 32767, 0x7fff), ConstantNode.forShort((short) 32767, graph).integerStamp());
        assertEquals(new IntegerStamp(Kind.Int, -32768, -32768, 0x8000), ConstantNode.forShort((short) -32768, graph).integerStamp());
    }

    @Test
    public void testCharConstant() {
        assertEquals(new IntegerStamp(Kind.Int, 0, 0, 0x0), ConstantNode.forChar((char) 0, graph).integerStamp());
        assertEquals(new IntegerStamp(Kind.Int, 'A', 'A', 'A'), ConstantNode.forChar('A', graph).integerStamp());
        assertEquals(new IntegerStamp(Kind.Int, 128, 128, 0x80), ConstantNode.forChar((char) 128, graph).integerStamp());
        assertEquals(new IntegerStamp(Kind.Int, 65535, 65535, 0xffff), ConstantNode.forChar((char) 65535, graph).integerStamp());
    }

    @Test
    public void testIntConstant() {
        assertEquals(new IntegerStamp(Kind.Int, 0, 0, 0x0), ConstantNode.forInt(0, graph).integerStamp());
        assertEquals(new IntegerStamp(Kind.Int, 128, 128, 0x80), ConstantNode.forInt(128, graph).integerStamp());
        assertEquals(new IntegerStamp(Kind.Int, -128, -128, 0xffffff80L), ConstantNode.forInt(-128, graph).integerStamp());
        assertEquals(new IntegerStamp(Kind.Int, Integer.MAX_VALUE, Integer.MAX_VALUE, 0x7fffffff), ConstantNode.forInt(Integer.MAX_VALUE, graph).integerStamp());
        assertEquals(new IntegerStamp(Kind.Int, Integer.MIN_VALUE, Integer.MIN_VALUE, 0x80000000L), ConstantNode.forInt(Integer.MIN_VALUE, graph).integerStamp());
    }

    @Test
    public void testLongConstant() {
        assertEquals(new IntegerStamp(Kind.Long, 0, 0, 0x0), ConstantNode.forLong(0, graph).integerStamp());
        assertEquals(new IntegerStamp(Kind.Long, 128, 128, 0x80), ConstantNode.forLong(128, graph).integerStamp());
        assertEquals(new IntegerStamp(Kind.Long, -128, -128, 0xffffffffffffff80L), ConstantNode.forLong(-128, graph).integerStamp());
        assertEquals(new IntegerStamp(Kind.Long, Long.MAX_VALUE, Long.MAX_VALUE, 0x7fffffffffffffffL), ConstantNode.forLong(Long.MAX_VALUE, graph).integerStamp());
        assertEquals(new IntegerStamp(Kind.Long, Long.MIN_VALUE, Long.MIN_VALUE, 0x8000000000000000L), ConstantNode.forLong(Long.MIN_VALUE, graph).integerStamp());
    }
}
