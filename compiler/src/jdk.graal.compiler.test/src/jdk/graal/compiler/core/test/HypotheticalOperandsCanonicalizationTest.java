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

import java.util.List;
import java.util.function.BiFunction;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.AndNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.FloatNormalizeCompareNode;
import jdk.graal.compiler.nodes.calc.IntegerNormalizeCompareNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.OrNode;
import jdk.graal.compiler.nodes.calc.SignedDivNode;
import jdk.graal.compiler.nodes.calc.SignedRemNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.calc.UnsignedRightShiftNode;
import jdk.graal.compiler.nodes.calc.XorNode;
import jdk.graal.compiler.nodes.extended.GuardedNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerAddExactNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerMulExactNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerSubExactNode;

import jdk.vm.ci.meta.JavaKind;

/// Checks that binary canonicalization uses the supplied operands without changing the original node.
public class HypotheticalOperandsCanonicalizationTest extends GraalCompilerTest {

    /// Creates a tool for querying canonicalization without applying its result to a graph.
    private CanonicalizerTool canonicalizerTool() {
        return GraphUtil.getDefaultSimplifier(getProviders(), false, null, getInitialOptions());
    }

    /// Checks signed and unsigned integer comparisons with unchanged and replacement operands.
    @Test
    public void testIntegerNormalizeCompare() {
        CanonicalizerTool tool = canonicalizerTool();
        for (JavaKind kind : new JavaKind[]{JavaKind.Int, JavaKind.Long}) {
            for (boolean unsigned : new boolean[]{false, true}) {
                ValueNode one = ConstantNode.forIntegerKind(kind, 1);
                ValueNode two = ConstantNode.forIntegerKind(kind, 2);
                IntegerNormalizeCompareNode compare = new IntegerNormalizeCompareNode(one, two, JavaKind.Int, unsigned);
                Assert.assertEquals(-1, compare.canonical(tool, one, two).asJavaConstant().asInt());
                Assert.assertEquals(1, compare.canonical(tool, two, one).asJavaConstant().asInt());
                Assert.assertEquals(0, compare.canonical(tool, two, two).asJavaConstant().asInt());
                Assert.assertSame(one, compare.getX());
                Assert.assertSame(two, compare.getY());
            }
        }
    }

    /// Checks floating comparisons, including both unordered results for replacement NaN operands.
    @Test
    public void testFloatNormalizeCompare() {
        CanonicalizerTool tool = canonicalizerTool();
        StructuredGraph graph = new StructuredGraph.Builder(getInitialOptions(), getDebugContext()).build();
        for (JavaKind kind : new JavaKind[]{JavaKind.Float, JavaKind.Double}) {
            for (boolean unorderedLess : new boolean[]{false, true}) {
                ValueNode one = ConstantNode.forFloatingKind(kind, 1, graph);
                ValueNode two = ConstantNode.forFloatingKind(kind, 2, graph);
                ValueNode nan = ConstantNode.forFloatingKind(kind, Double.NaN, graph);
                FloatNormalizeCompareNode compare = new FloatNormalizeCompareNode(one, two, JavaKind.Int, unorderedLess);
                Assert.assertEquals(-1, compare.canonical(tool, one, two).asJavaConstant().asInt());
                Assert.assertEquals(1, compare.canonical(tool, two, one).asJavaConstant().asInt());
                Assert.assertEquals(0, compare.canonical(tool, two, two).asJavaConstant().asInt());
                Assert.assertEquals(unorderedLess ? -1 : 1, compare.canonical(tool, nan, two).asJavaConstant().asInt());
                Assert.assertSame(one, compare.getX());
                Assert.assertSame(two, compare.getY());
            }
        }
    }

    /// Creates a nonconstant operand with a chosen stamp for hypothetical canonicalization.
    private static ParameterNode parameter(int index, IntegerStamp stamp) {
        return new ParameterNode(index, StampPair.createSingle(stamp));
    }

    /// Checks that replacing either shift operand cannot reuse the original result's constant stamp.
    @Test
    public void testUnsignedRightShift() {
        CanonicalizerTool tool = canonicalizerTool();
        for (int bits : new int[]{32, 64}) {
            ValueNode zero = parameter(0, IntegerStamp.create(bits, 0, 0));
            ValueNode unrestricted = parameter(1, IntegerStamp.create(bits));
            ValueNode twoOrThree = parameter(2, IntegerStamp.create(bits, 2, 3));
            ValueNode one = ConstantNode.forInt(1);
            UnsignedRightShiftNode shift = new UnsignedRightShiftNode(zero, one);
            Assert.assertEquals(0, shift.canonical(tool, zero, one).asJavaConstant().asLong());
            Assert.assertSame(shift, shift.canonical(tool, unrestricted, one));
            Assert.assertEquals(1, shift.canonical(tool, twoOrThree, one).asJavaConstant().asLong());
            Assert.assertSame(unrestricted, shift.canonical(tool, unrestricted, ConstantNode.forInt(0)));
            UnsignedRightShiftNode largeShift = new UnsignedRightShiftNode(unrestricted, ConstantNode.forInt(bits - 1));
            ValueNode nonnegative = parameter(3, IntegerStamp.create(bits, 0, 3));
            Assert.assertEquals(0, largeShift.canonical(tool, nonnegative, largeShift.getY()).asJavaConstant().asLong());
            Assert.assertSame(largeShift, largeShift.canonical(tool, nonnegative, one));
            Assert.assertSame(zero, shift.getX());
            Assert.assertSame(one, shift.getY());
        }
    }

    /// Checks both replacement operands and their overflow bounds before dropping exact arithmetic.
    @Test
    public void testExactArithmetic() {
        CanonicalizerTool tool = canonicalizerTool();
        for (int bits : new int[]{32, 64}) {
            ValueNode x = parameter(0, IntegerStamp.create(bits, -10, 10));
            ValueNode y = parameter(1, IntegerStamp.create(bits, -10, 10));
            ValueNode u = parameter(2, IntegerStamp.create(bits, -20, 20));
            ValueNode v = parameter(3, IntegerStamp.create(bits, -20, 20));
            ValueNode unrestricted = parameter(4, IntegerStamp.create(bits));
            ValueNode unrestrictedY = parameter(5, IntegerStamp.create(bits));
            BeginNode guard = new BeginNode();
            for (boolean multiply : new boolean[]{false, true}) {
                BinaryArithmeticNode<?> exact = multiply ? new IntegerMulExactNode(x, y, guard) : new IntegerSubExactNode(x, y, guard);
                BinaryArithmeticNode<?> unchanged = (BinaryArithmeticNode<?>) exact.canonical(tool, x, y);
                Assert.assertSame(x, unchanged.getX());
                Assert.assertSame(y, unchanged.getY());
                BinaryArithmeticNode<?> replacement = (BinaryArithmeticNode<?>) exact.canonical(tool, u, v);
                Assert.assertEquals(multiply ? MulNode.class : SubNode.class, replacement.getClass());
                Assert.assertSame(u, replacement.getX());
                Assert.assertSame(v, replacement.getY());
                Assert.assertSame(exact, exact.canonical(tool, unrestricted, unrestrictedY));
                Assert.assertSame(guard, ((GuardedNode) exact).getGuard());
                BinaryArithmeticNode<?> broad = multiply ? new IntegerMulExactNode(unrestricted, unrestricted, guard) : new IntegerSubExactNode(unrestricted, y, guard);
                Assert.assertEquals(multiply ? MulNode.class : SubNode.class, broad.canonical(tool, u, v).getClass());
                Assert.assertSame(x, exact.getX());
                Assert.assertSame(y, exact.getY());
            }
        }
    }

    /// Checks the shared reassociation matcher with replacement expressions and absent matches.
    @Test
    public void testReassociation() {
        List<BiFunction<ValueNode, ValueNode, BinaryArithmeticNode<?>>> operations = List.of(AddNode::new, SubNode::new, MulNode::new, AndNode::new, OrNode::new, XorNode::new);
        long[] expected = {9, -9, 20, 4, 5, 1};
        for (int bits : new int[]{32, 64}) {
            JavaKind kind = bits == 32 ? JavaKind.Int : JavaKind.Long;
            ValueNode x = parameter(0, IntegerStamp.create(bits));
            ValueNode y = parameter(1, IntegerStamp.create(bits));
            ValueNode z = parameter(2, IntegerStamp.create(bits));
            for (int i = 0; i < operations.size(); i++) {
                BiFunction<ValueNode, ValueNode, BinaryArithmeticNode<?>> operation = operations.get(i);
                BinaryArithmeticNode<?> originalX = operation.apply(x, ConstantNode.forIntegerKind(kind, 2));
                BinaryArithmeticNode<?> original = operation.apply(originalX, ConstantNode.forIntegerKind(kind, 3));
                BinaryArithmeticNode<?> suppliedX = operation.apply(y, ConstantNode.forIntegerKind(kind, 4));
                ValueNode suppliedY = ConstantNode.forIntegerKind(kind, 5);
                BinaryArithmeticNode<?> unchanged = (BinaryArithmeticNode<?>) BinaryArithmeticNode.reassociateMatchedValues(original, ValueNode.isConstantPredicate(), originalX,
                                original.getY(), NodeView.DEFAULT);
                Assert.assertSame(x, unchanged.getX());
                BinaryArithmeticNode<?> replacement = (BinaryArithmeticNode<?>) BinaryArithmeticNode.reassociateMatchedValues(original, ValueNode.isConstantPredicate(), suppliedX,
                                suppliedY, NodeView.DEFAULT);
                Assert.assertEquals(i == 1 ? AddNode.class : original.getClass(), replacement.getClass());
                Assert.assertSame(y, replacement.getX());
                Assert.assertEquals(expected[i], replacement.getY().asJavaConstant().asLong());
                Assert.assertSame(original, BinaryArithmeticNode.reassociateMatchedValues(original, ValueNode.isConstantPredicate(), y, z, NodeView.DEFAULT));
                Assert.assertSame(originalX, original.getX());
                // The helper must retain exact operations even when supplied constants can combine.
                IntegerAddExactNode exact = new IntegerAddExactNode(originalX, original.getY(), new BeginNode());
                Assert.assertSame(exact, BinaryArithmeticNode.reassociateMatchedValues(exact, ValueNode.isConstantPredicate(), suppliedX, suppliedY, NodeView.DEFAULT));
            }
        }
    }

    /// Checks adjacent division reuse only for the operands shared with the following division.
    @Test
    public void testAdjacentSignedDivision() {
        OptionValues options = new OptionValues(getInitialOptions(), GraalOptions.FloatingDivNodes, false);
        CanonicalizerTool tool = GraphUtil.getDefaultSimplifier(getProviders(), false, null, options);
        for (int bits : new int[]{32, 64}) {
            StructuredGraph graph = new StructuredGraph.Builder(options, getDebugContext()).build();
            ValueNode x = graph.addWithoutUnique(parameter(0, IntegerStamp.create(bits, 20, 20)));
            ValueNode y = graph.addWithoutUnique(parameter(1, IntegerStamp.create(bits, 4, 4)));
            ValueNode u = graph.addWithoutUnique(parameter(2, IntegerStamp.create(bits, 18, 18)));
            ValueNode v = graph.addWithoutUnique(parameter(3, IntegerStamp.create(bits, 6, 6)));
            SignedDivNode first = graph.add(new SignedDivNode(x, y, null));
            SignedDivNode second = graph.add(new SignedDivNode(x, y, null));
            graph.start().setNext(first);
            first.setNext(second);
            second.setNext(graph.add(new ReturnNode(second)));
            Assert.assertSame(second, first.canonical(tool, x, y));
            Assert.assertSame(first, first.canonical(tool, u, v));
            Assert.assertSame(first, first.canonical(tool, u, y));
            Assert.assertSame(first, first.canonical(tool, x, v));
            Assert.assertSame(x, first.getX());
            Assert.assertSame(y, first.getY());
            second.setZeroGuard(graph.start());
            Assert.assertSame(first, first.canonical(tool, x, y));
        }
    }

    /// Checks that removing an unused remainder preserves traps for the supplied divisor.
    @Test
    public void testAdjacentSignedRemainder() {
        OptionValues options = new OptionValues(getInitialOptions(), GraalOptions.FloatingDivNodes, false);
        CanonicalizerTool tool = GraphUtil.getDefaultSimplifier(getProviders(), false, null, options);
        for (int bits : new int[]{32, 64}) {
            StructuredGraph graph = new StructuredGraph.Builder(options, getDebugContext()).build();
            ValueNode x = graph.addWithoutUnique(parameter(0, IntegerStamp.create(bits)));
            ValueNode y = graph.addWithoutUnique(parameter(1, IntegerStamp.create(bits, 3, 3)));
            ValueNode divisor = graph.addWithoutUnique(parameter(2, IntegerStamp.create(bits)));
            SignedRemNode rem = graph.add(new SignedRemNode(x, y, null));
            SignedDivNode div = graph.add(new SignedDivNode(x, y, null));
            graph.start().setNext(rem);
            rem.setNext(div);
            div.setNext(graph.add(new ReturnNode(div)));
            Assert.assertNull(rem.canonical(tool, x, y));
            ValueNode replacement = rem.canonical(tool, x, divisor);
            Assert.assertTrue(replacement instanceof SignedRemNode);
            Assert.assertSame(x, ((SignedRemNode) replacement).getX());
            Assert.assertSame(divisor, ((SignedRemNode) replacement).getY());
            JavaKind kind = bits == 32 ? JavaKind.Int : JavaKind.Long;
            Assert.assertNotNull(rem.canonical(tool, x, ConstantNode.forIntegerKind(kind, 0)));
            div.setZeroGuard(graph.start());
            Assert.assertNotNull(rem.canonical(tool, x, y));
            Assert.assertSame(x, rem.getX());
            Assert.assertSame(y, rem.getY());
        }
    }
}
