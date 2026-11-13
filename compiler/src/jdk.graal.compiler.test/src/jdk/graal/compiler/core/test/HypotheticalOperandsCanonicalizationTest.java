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

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.calc.UnsignedRightShiftNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.extended.GuardedNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerMulExactNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerSubExactNode;
import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatNormalizeCompareNode;
import jdk.graal.compiler.nodes.calc.IntegerNormalizeCompareNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
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
}
