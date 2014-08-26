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
package com.oracle.graal.replacements.test;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.inlining.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.replacements.nodes.*;

public class BitOpNodesTest extends GraalCompilerTest {

    private static final int INT_CONSTANT_1 = 0x80100010;
    private static final int INT_CONSTANT_2 = 0x00011110;
    private static final int INT_CONSTANT_3 = 0x00000000;

    private static final long LONG_CONSTANT_1 = 0x8000000000100010L;
    private static final long LONG_CONSTANT_2 = 0x0000000000011110L;
    private static final long LONG_CONSTANT_3 = 0x0000000000000000L;

    public static long dummyField;

    /*
     * Tests for BitCountNode canonicalizations.
     */

    public static int bitCountIntConstantSnippet() {
        return Integer.bitCount(INT_CONSTANT_1) + Integer.bitCount(INT_CONSTANT_2) + Integer.bitCount(INT_CONSTANT_3);
    }

    @Test
    public void testBitCountIntConstant() {
        ValueNode result = parseAndInline("bitCountIntConstantSnippet");
        Assert.assertEquals(7, result.asConstant().asInt());
    }

    public static int bitCountLongConstantSnippet() {
        return Long.bitCount(LONG_CONSTANT_1) + Long.bitCount(LONG_CONSTANT_2) + Long.bitCount(LONG_CONSTANT_3);
    }

    public static int bitCountIntSnippet(int v) {
        return Integer.bitCount(v & 0xFFFFFF | 0xFF);
    }

    @Test
    public void testBitCountInt() {
        ValueNode result = parseAndInline("bitCountIntSnippet");
        Assert.assertEquals(StampFactory.forInteger(Kind.Int, 8, 24), result.stamp());
    }

    public static int bitCountIntEmptySnippet(int v) {
        return Integer.bitCount(v & 0xFFFFFF);
    }

    @Test
    public void testBitCountIntEmpty() {
        ValueNode result = parseAndInline("bitCountIntEmptySnippet");
        Assert.assertEquals(StampFactory.forInteger(Kind.Int, 0, 24), result.stamp());
    }

    @Test
    public void testBitCountLongConstant() {
        ValueNode result = parseAndInline("bitCountLongConstantSnippet");
        Assert.assertEquals(7, result.asConstant().asInt());
    }

    public static int bitCountLongSnippet(long v) {
        return Long.bitCount(v & 0xFFFFFFFFFFL | 0xFFL);
    }

    @Test
    public void testBitCountLong() {
        ValueNode result = parseAndInline("bitCountLongSnippet");
        Assert.assertEquals(StampFactory.forInteger(Kind.Int, 8, 40), result.stamp());
    }

    public static int bitCountLongEmptySnippet(long v) {
        return Long.bitCount(v & 0xFFFFFFFFFFL);
    }

    @Test
    public void testBitCountLongEmpty() {
        ValueNode result = parseAndInline("bitCountLongEmptySnippet");
        Assert.assertEquals(StampFactory.forInteger(Kind.Int, 0, 40), result.stamp());
    }

    /*
     * Tests for BitScanForwardNode
     */

    public static int scanForwardIntConstantSnippet() {
        return Integer.numberOfTrailingZeros(INT_CONSTANT_1) + Integer.numberOfTrailingZeros(INT_CONSTANT_2) + Integer.numberOfTrailingZeros(INT_CONSTANT_3);
    }

    @Test
    public void testScanForwardIntConstant() {
        ValueNode result = parseAndInline("scanForwardIntConstantSnippet");
        Assert.assertEquals(40, result.asConstant().asInt());
    }

    public static int scanForwardIntSnippet(int v) {
        return Integer.numberOfTrailingZeros(v & 0xFFF0 | 0xFF00);
    }

    @Test
    public void testScanForwardInt() {
        ValueNode result = parseAndInline("scanForwardIntSnippet");
        Assert.assertEquals(StampFactory.forInteger(Kind.Int, 4, 8), result.stamp());
    }

    public static int scanForwardLongConstantSnippet() {
        return Long.numberOfTrailingZeros(LONG_CONSTANT_1) + Long.numberOfTrailingZeros(LONG_CONSTANT_2) + Long.numberOfTrailingZeros(LONG_CONSTANT_3);
    }

    @Test
    public void testScanForwardLongConstant() {
        ValueNode result = parseAndInline("scanForwardLongConstantSnippet");
        Assert.assertEquals(72, result.asConstant().asInt());
    }

    public static int scanForwardLongSnippet(long v) {
        return Long.numberOfTrailingZeros(v & 0xFFFF000000L | 0xFF00000000L);
    }

    @Test
    public void testScanForwardLong() {
        ValueNode result = parseAndInline("scanForwardLongSnippet");
        Assert.assertEquals(StampFactory.forInteger(Kind.Int, 24, 32), result.stamp());
    }

    public static int scanForwardLongEmptySnippet(long v) {
        int result = Long.numberOfTrailingZeros(v & 0xFFFF000000L);
        dummyField = result;
        return result;
    }

    @Test
    public void testScanForwardLongEmpty() {
        ValueNode result = parseAndInline("scanForwardLongEmptySnippet");
        Assert.assertEquals(StampFactory.forInteger(Kind.Int, -1, 64), result.stamp());
    }

    /*
     * Tests for BitScanReverseNode
     */

    public static int scanReverseIntConstantSnippet() {
        return Integer.numberOfLeadingZeros(INT_CONSTANT_1) + Integer.numberOfLeadingZeros(INT_CONSTANT_2) + Integer.numberOfLeadingZeros(INT_CONSTANT_3);
    }

    @Test
    public void testScanReverseIntConstant() {
        ValueNode result = parseAndInline("scanReverseIntConstantSnippet");
        Assert.assertEquals(47, result.asConstant().asInt());
    }

    public static int scanReverseIntSnippet(int v) {
        return Integer.numberOfLeadingZeros(v & 0xFFF0 | 0xFF0);
    }

    @Test
    public void testScanReverseInt() {
        /* This test isn't valid unless the BitScanReverseNode intrinsic is used. */
        ValueNode result = parseAndInline("scanReverseIntSnippet", BitScanReverseNode.class);
        if (result != null) {
            Assert.assertEquals(StampFactory.forInteger(Kind.Int, 16, 20), result.stamp());
        }
    }

    public static int scanReverseLongConstantSnippet() {
        return Long.numberOfLeadingZeros(LONG_CONSTANT_1) + Long.numberOfLeadingZeros(LONG_CONSTANT_2) + Long.numberOfLeadingZeros(LONG_CONSTANT_3);
    }

    @Test
    public void testScanReverseLongConstant() {
        ValueNode result = parseAndInline("scanReverseLongConstantSnippet");
        Assert.assertEquals(111, result.asConstant().asInt());
    }

    public static int scanReverseLongSnippet(long v) {
        int result = Long.numberOfLeadingZeros(v & 0xFFF0);
        dummyField = result;
        return result;
    }

    @Test
    public void testScanReverseLong() {
        /* This test isn't valid unless the BitScanReverseNode intrinsic is used. */
        ValueNode result = parseAndInline("scanReverseLongSnippet", BitScanReverseNode.class);
        if (result != null) {
            Assert.assertEquals(StampFactory.forInteger(Kind.Int, 48, 64), result.stamp());
        }
    }

    public static int scanReverseLongEmptySnippet(long v) {
        int result = Long.numberOfLeadingZeros(v & 0xFFFF000000L);
        dummyField = result;
        return result;
    }

    @Test
    public void testScanReverseLongEmpty() {
        /* This test isn't valid unless the BitScanReverseNode intrinsic is used. */
        ValueNode result = parseAndInline("scanReverseLongEmptySnippet", BitScanReverseNode.class);
        if (result != null) {
            Assert.assertEquals(StampFactory.forInteger(Kind.Int, 24, 64), result.stamp());
        }
    }

    private ValueNode parseAndInline(String name) {
        return parseAndInline(name, null);
    }

    /**
     * Parse and optimize {@code name}. If {@code expectedClass} is non-null and a node of that type
     * isn't found simply return null. Otherwise return the node returned by the graph.
     *
     * @param name
     * @param expectedClass
     * @return the returned value or null if {@code expectedClass} is not found in the graph.
     */
    private ValueNode parseAndInline(String name, Class<? extends ValueNode> expectedClass) {
        StructuredGraph graph = parseEager(name);
        HighTierContext context = new HighTierContext(getProviders(), new Assumptions(false), null, getDefaultGraphBuilderSuite(), OptimisticOptimizations.NONE);
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase(true);
        canonicalizer.apply(graph, context);
        new InliningPhase(canonicalizer).apply(graph, context);
        canonicalizer.apply(graph, context);
        Assert.assertEquals(1, graph.getNodes(ReturnNode.class).count());
        if (expectedClass != null) {
            if (graph.getNodes().filter(expectedClass).count() == 0) {
                return null;
            }
        }
        return graph.getNodes(ReturnNode.class).first().result();
    }
}
