/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IntegerBelowNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.NegateNode;
import jdk.graal.compiler.nodes.calc.RightShiftNode;
import jdk.graal.compiler.nodes.calc.UnsignedRightShiftNode;
import jdk.graal.compiler.nodes.loop.DerivedScaledInductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;

/// Tests analyses of shifts before their constant distances have been canonicalized.
public class ShiftDistanceAnalysisTest extends GraalCompilerTest {

    /// Checks that comparison cancellation preserves Java results for raw shift constants.
    private void checkComparison(int bits, int amount, boolean unsigned, long xValue, long yValue) {
        StructuredGraph graph = new StructuredGraph.Builder(getInitialOptions(), getDebugContext()).build();
        IntegerStamp stamp = IntegerStamp.create(bits, 0, 3);
        ParameterNode x = graph.addWithoutUnique(new ParameterNode(0, StampPair.createSingle(stamp)));
        ParameterNode y = graph.addWithoutUnique(new ParameterNode(1, StampPair.createSingle(stamp)));
        ConstantNode distance = ConstantNode.forInt(amount, graph);
        // Retains the raw constant so comparison analysis runs before shift canonicalization.
        LeftShiftNode shiftX = graph.unique(new LeftShiftNode(x, distance));
        LeftShiftNode shiftY = graph.unique(new LeftShiftNode(y, distance));
        LogicNode comparison = unsigned
                        ? IntegerBelowNode.create(getConstantReflection(), getMetaAccess(), graph.getOptions(), null, shiftX, shiftY, NodeView.DEFAULT)
                        : IntegerLessThanNode.create(getConstantReflection(), getMetaAccess(), graph.getOptions(), null, shiftX, shiftY, NodeView.DEFAULT);
        comparison = graph.addOrUniqueWithInputs(comparison);
        ValueNode result = graph.addOrUniqueWithInputs(ConditionalNode.create(comparison, NodeView.DEFAULT));
        ReturnNode returnNode = graph.add(new ReturnNode(result));
        graph.start().setNext(returnNode);

        x.replaceAtUsages(ConstantNode.forIntegerBits(bits, xValue, graph));
        y.replaceAtUsages(ConstantNode.forIntegerBits(bits, yValue, graph));
        createCanonicalizerPhase().apply(graph, getDefaultHighTierContext());

        boolean expected;
        if (bits == Integer.SIZE) {
            int shiftedX = (int) xValue << amount;
            int shiftedY = (int) yValue << amount;
            expected = unsigned ? Integer.compareUnsigned(shiftedX, shiftedY) < 0 : shiftedX < shiftedY;
        } else {
            long shiftedX = xValue << amount;
            long shiftedY = yValue << amount;
            expected = unsigned ? Long.compareUnsigned(shiftedX, shiftedY) < 0 : shiftedX < shiftedY;
        }
        Assert.assertEquals(expected ? 1 : 0, returnNode.result().asJavaConstant().asInt());
    }

    /// Covers both comparison orders and distances on either side of each Java shift mask.
    @Test
    public void testComparisonCancellation() {
        for (int bits : new int[]{Integer.SIZE, Long.SIZE}) {
            for (int amount : new int[]{-1, -2, -bits, 0, 1, bits - 1, bits, bits + 1}) {
                for (boolean unsigned : new boolean[]{false, true}) {
                    for (long x = 0; x <= 3; x++) {
                        for (long y = 0; y <= 3; y++) {
                            checkComparison(bits, amount, unsigned, x, y);
                        }
                    }
                }
            }
        }
    }

    /// Supplies an int induction variable with a shift that survives parsing.
    public static int intLoop(int limit, int amount) {
        int result = 0;
        for (int i = 0; i < limit; i++) {
            result += i << amount;
        }
        return result;
    }

    /// Supplies a long induction variable with a shift that survives parsing.
    public static long longLoop(long limit, int amount) {
        long result = 0;
        for (long i = 0; i < limit; i++) {
            result += i << amount;
        }
        return result;
    }

    /// Checks the scale inferred from the original shift width rather than the host long width.
    private void checkLoopScale(String snippet, int amount) {
        StructuredGraph graph = parseEager(snippet, StructuredGraph.AllowAssumptions.NO);
        LeftShiftNode shift = graph.getNodes().filter(LeftShiftNode.class).first();
        graph.getParameter(1).replaceAtUsages(ConstantNode.forInt(amount, graph));
        // Queries IV metadata before canonicalization can normalize the injected distance.
        LoopsData loops = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph);
        Assert.assertEquals(1, loops.loops().size());
        Loop loop = loops.loops().get(0);
        InductionVariable iv = loop.getInductionVariables().get(shift);
        assertTrue(iv instanceof DerivedScaledInductionVariable);
        long expected = snippet.equals("intLoop") ? 1 << amount : 1L << amount;
        Assert.assertEquals(expected, ((DerivedScaledInductionVariable) iv).getScale().asJavaConstant().asLong());
    }

    /// Covers int and long scales for negative, masked, and out-of-range distances.
    @Test
    public void testLoopScale() {
        for (int amount : new int[]{-65, -33, -32, -1, 0, 1, 31, 32, 33, 63, 64, 65}) {
            checkLoopScale("intLoop", amount);
            checkLoopScale("longLoop", amount);
        }
    }

    /// Checks multiplication conversion using raw distances and the original operand width.
    @Test
    public void testEquivalentMultiplication() {
        for (int bits : new int[]{Integer.SIZE, Long.SIZE}) {
            ParameterNode x = new ParameterNode(0, StampPair.createSingle(IntegerStamp.create(bits)));
            for (int amount : new int[]{Integer.MIN_VALUE, -65, -33, -32, -1, 0, 1, 31, 32, 33, 63, 64, 65, Integer.MAX_VALUE}) {
                LeftShiftNode shift = new LeftShiftNode(x, ConstantNode.forInt(amount));
                MulNode mul = shift.getEquivalentMulNode();
                Assert.assertNotNull(mul);
                long expected = bits == Integer.SIZE ? 1 << amount : 1L << amount;
                Assert.assertEquals(expected, mul.getY().asJavaConstant().asLong());
            }
        }
    }

    /// Checks that negation recognizes a sign shift before its distance is canonicalized.
    @Test
    public void testSignShiftNegation() {
        for (int bits : new int[]{Integer.SIZE, Long.SIZE}) {
            ParameterNode x = new ParameterNode(0, StampPair.createSingle(IntegerStamp.create(bits)));
            for (int amount : new int[]{-1, bits - 1, 2 * bits - 1, Integer.MAX_VALUE}) {
                RightShiftNode shift = new RightShiftNode(x, ConstantNode.forInt(amount));
                ValueNode negated = NegateNode.create(shift, NodeView.DEFAULT);
                assertTrue(negated instanceof UnsignedRightShiftNode);
                Assert.assertSame(x, ((UnsignedRightShiftNode) negated).getX());
            }
        }
    }
}
