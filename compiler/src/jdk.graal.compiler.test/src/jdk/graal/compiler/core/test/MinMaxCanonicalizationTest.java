/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.graal.compiler.core.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.MaxNode;
import jdk.graal.compiler.nodes.calc.MinNode;
import jdk.graal.compiler.nodes.calc.UnsignedMaxNode;
import jdk.graal.compiler.nodes.calc.UnsignedMinNode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class MinMaxCanonicalizationTest extends GraalCompilerTest {

    record ByteHolder(byte b) {

    }

    public static int intParametersSnippet(int a, int b) {
        return a + b;
    }

    public static float floatParametersSnippet(float a, float b) {
        return a + b;
    }

    public static float floatMinSelfSnippet(float x) {
        return Math.min(x, x);
    }

    public static float floatMaxSelfSnippet(float x) {
        return Math.max(x, x);
    }

    public static double doubleMinSelfSnippet(double x) {
        return Math.min(x, x);
    }

    public static double doubleMaxSelfSnippet(double x) {
        return Math.max(x, x);
    }

    @FunctionalInterface
    private interface MinMaxFactory {
        ValueNode create(ValueNode x, ValueNode y);
    }

    @Test
    public void testSameInputIntegerMinMaxCreateCanonicalizesToInput() {
        StructuredGraph graph = parseEager("intParametersSnippet", StructuredGraph.AllowAssumptions.YES);
        ParameterNode x = graph.getParameter(0);
        assertNotNull(x);

        assertSame(x, MinNode.create(x, x, NodeView.DEFAULT));
        assertSame(x, MaxNode.create(x, x, NodeView.DEFAULT));
        assertSame(x, UnsignedMinNode.create(x, x, NodeView.DEFAULT));
        assertSame(x, UnsignedMaxNode.create(x, x, NodeView.DEFAULT));
    }

    @Test
    public void testSameInputIntegerMinMaxCanonicalizesAfterInputCollapse() {
        assertSameInputIntegerMinMaxCanonicalizesAfterInputCollapse((x, y) -> MinNode.create(x, y, NodeView.DEFAULT));
        assertSameInputIntegerMinMaxCanonicalizesAfterInputCollapse((x, y) -> MaxNode.create(x, y, NodeView.DEFAULT));
        assertSameInputIntegerMinMaxCanonicalizesAfterInputCollapse((x, y) -> UnsignedMinNode.create(x, y, NodeView.DEFAULT));
        assertSameInputIntegerMinMaxCanonicalizesAfterInputCollapse((x, y) -> UnsignedMaxNode.create(x, y, NodeView.DEFAULT));
    }

    @Test
    public void testSameInputFloatingMinMaxCreateCanonicalizesToInput() {
        StructuredGraph graph = parseEager("floatParametersSnippet", StructuredGraph.AllowAssumptions.YES);
        ParameterNode x = graph.getParameter(0);
        assertNotNull(x);

        assertSame(x, MinNode.create(x, x, NodeView.DEFAULT));
        assertSame(x, MaxNode.create(x, x, NodeView.DEFAULT));
    }

    @Test
    public void testFloatSameInputMinMaxSemanticBoundaries() {
        // Raw-bit inputs exercise NaN payload and signed-zero preservation.
        int[] rawValues = {
                        0x00000000, // +0.0f
                        0x80000000, // -0.0f
                        0x7fc12345, // NaN payload
                        0xffc12345, // NaN payload with sign bit set
        };
        for (int raw : rawValues) {
            float value = Float.intBitsToFloat(raw);
            test("floatMinSelfSnippet", value);
            test("floatMaxSelfSnippet", value);
        }
    }

    @Test
    public void testDoubleSameInputMinMaxSemanticBoundaries() {
        // Raw-bit inputs exercise NaN payload and signed-zero preservation.
        long[] rawValues = {
                        0x0000000000000000L, // +0.0d
                        0x8000000000000000L, // -0.0d
                        0x7ff8000000001234L, // NaN payload
                        0xfff8000000001234L, // NaN payload with sign bit set
        };
        for (long raw : rawValues) {
            double value = Double.longBitsToDouble(raw);
            test("doubleMinSelfSnippet", value);
            test("doubleMaxSelfSnippet", value);
        }
    }

    private void assertSameInputIntegerMinMaxCanonicalizesAfterInputCollapse(MinMaxFactory factory) {
        StructuredGraph graph = parseEager("intParametersSnippet", StructuredGraph.AllowAssumptions.YES);
        ParameterNode x = graph.getParameter(0);
        assertNotNull(x);

        ValueNode addZero = graph.addOrUniqueWithInputs(new AddNode(x, ConstantNode.forInt(0, graph)));
        ValueNode minMax = graph.addOrUniqueWithInputs(factory.create(x, addZero));
        ReturnNode returnNode = graph.getNodes(ReturnNode.TYPE).first();
        assertNotNull(returnNode);
        returnNode.result().replaceAtUsages(minMax, n -> n instanceof ReturnNode);

        createCanonicalizerPhase().apply(graph, getProviders());

        assertSame(x, returnNode.result());
    }

    @Override
    protected void assertDeepEquals(String message, Object expected, Object actual, double delta) {
        if (expected instanceof Float && actual instanceof Float) {
            int expectedBits = Float.floatToRawIntBits((Float) expected);
            int actualBits = Float.floatToRawIntBits((Float) actual);
            if (actualBits != expectedBits) {
                Assert.fail((message == null ? "" : message) + "raw float bits not equal " + actualBits + " != " + expectedBits);
            }
        } else if (expected instanceof Double && actual instanceof Double) {
            long expectedBits = Double.doubleToRawLongBits((Double) expected);
            long actualBits = Double.doubleToRawLongBits((Double) actual);
            if (actualBits != expectedBits) {
                Assert.fail((message == null ? "" : message) + "raw double bits not equal " + actualBits + " != " + expectedBits);
            }
        } else {
            super.assertDeepEquals(message, expected, actual, delta);
        }
    }

    public static boolean byteMinSnippet(ByteHolder holder) {
        byte b = holder.b;
        /* This tests a case in IntegerEqualsOp#canonical where we need constants in the graph. */
        return (b < 8 ? b : 8) == 7;
    }

    public static boolean byteMinReference(ByteHolder holder) {
        return holder.b == 7;
    }

    @Test
    public void testByteMin() {
        testByteHolderMethod("byteMinSnippet", "byteMinReference");
    }

    private void testByteHolderMethod(String testSnippetName, String referenceSnippetName) {
        StructuredGraph graph = parseEager(testSnippetName, StructuredGraph.AllowAssumptions.YES);
        createInliningPhase().apply(graph, getDefaultHighTierContext());
        createCanonicalizerPhase().apply(graph, getProviders());

        StructuredGraph referenceGraph = parseEager(referenceSnippetName, StructuredGraph.AllowAssumptions.YES);
        assertEquals(referenceGraph, graph);

        ResolvedJavaMethod referenceMethod = referenceGraph.method();
        for (byte b = 7; b <= 9; b++) {
            testAgainstExpected(graph.method(), executeExpected(referenceMethod, null, new ByteHolder(b)), null, new ByteHolder(b));
        }
    }

    public static boolean byteMaxEqualsToLessThanSnippet(ByteHolder holder) {
        return Math.max(7, holder.b) == 7;
    }

    public static boolean byteMaxEqualsToLessThanReference(ByteHolder holder) {
        return holder.b <= 7;
    }

    @Test
    public void testByteMaxEqualsToLessThan() {
        testByteHolderMethod("byteMaxEqualsToLessThanSnippet", "byteMaxEqualsToLessThanReference");
    }

    private static volatile byte symbolic = 7;

    public static boolean byteMaxEqualsToLessThanSymbolicSnippet(ByteHolder holder) {
        byte other = symbolic;
        /*
         * Many of these tests test a case where we don't need constants in the graph. The tests
         * look cleaner with constants, but here is a demonstration that fully symbolic reasoning
         * works too.
         */
        return Math.max(other, holder.b) == other;
    }

    public static boolean byteMaxEqualsToLessThanSymbolicReference(ByteHolder holder) {
        byte other = symbolic;
        return holder.b <= other;
    }

    @Test
    public void testByteMaxEqualsToLessThanSymbolic() {
        testByteHolderMethod("byteMaxEqualsToLessThanSymbolicSnippet", "byteMaxEqualsToLessThanSymbolicReference");
    }

    public static boolean byteUMaxEqualsToLessThanSnippet(ByteHolder holder) {
        byte b = holder.b;
        int result = Integer.compareUnsigned(b & 0xff, 7) < 0 ? 7 : b & 0xff;
        return result == 7;
    }

    public static boolean byteUMaxEqualsToLessThanReference(ByteHolder holder) {
        return Byte.compareUnsigned(holder.b, (byte) 7) <= 0;
    }

    @Test
    public void testByteUMaxEqualsToLessThan() {
        testByteHolderMethod("byteUMaxEqualsToLessThanSnippet", "byteUMaxEqualsToLessThanReference");
    }

    public static boolean byteMinEqualsToLessThanSnippet(ByteHolder holder) {
        return Math.min(7, holder.b) == 7;
    }

    public static boolean byteMinEqualsToLessThanReference(ByteHolder holder) {
        return holder.b >= 7;
    }

    @Test
    public void testByteMinEqualsToLessThan() {
        testByteHolderMethod("byteMinEqualsToLessThanSnippet", "byteMinEqualsToLessThanReference");
    }

    public static boolean byteUMinEqualsToLessThanSnippet(ByteHolder holder) {
        byte b = holder.b;
        int result = Integer.compareUnsigned(b & 0xff, 7) > 0 ? 7 : b & 0xff;
        return result == 7;
    }

    public static boolean byteUMinEqualsToLessThanReference(ByteHolder holder) {
        return Byte.compareUnsigned(holder.b, (byte) 7) >= 0;
    }

    @Test
    public void testByteUMinEqualsToLessThan() {
        testByteHolderMethod("byteUMinEqualsToLessThanSnippet", "byteUMinEqualsToLessThanReference");
    }
}
