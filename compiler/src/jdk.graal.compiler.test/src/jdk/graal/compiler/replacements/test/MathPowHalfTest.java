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
package jdk.graal.compiler.replacements.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.SqrtNode;
import jdk.graal.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import jdk.graal.compiler.replacements.nodes.BinaryMathIntrinsicNode.BinaryOperation;
import jdk.vm.ci.code.InstalledCode;

public class MathPowHalfTest extends GraalCompilerTest {

    public static double clampedPower(double x) {
        return Math.pow(Math.max(x, -0.0D), 0.5D);
    }

    private static void checkCanonicalization(FloatStamp stamp, boolean expectSqrt) {
        ParameterNode x = new ParameterNode(0, StampPair.createSingle(stamp));
        ValueNode half = ConstantNode.forDouble(0.5D);
        BinaryMathIntrinsicNode pow = (BinaryMathIntrinsicNode) BinaryMathIntrinsicNode.create(x, half, BinaryOperation.POW);
        ValueNode canonical = pow.canonical(null, x, half);
        if (expectSqrt) {
            Assert.assertTrue(canonical instanceof SqrtNode);
            Assert.assertSame(x, ((SqrtNode) canonical).getValue());
        } else {
            Assert.assertSame(pow, canonical);
        }
    }

    @Test
    public void testEmptyStamp() {
        checkCanonicalization(FloatStamp.createEmpty(64), false);
    }

    @Test
    public void testPositiveStamp() {
        checkCanonicalization(FloatStamp.create(64, 1.0D, 4.0D, true), true);
    }

    @Test
    public void testNegativeStamp() {
        checkCanonicalization(FloatStamp.create(64, -4.0D, -1.0D, true), false);
    }

    @Test
    public void testNaNStamp() {
        checkCanonicalization(FloatStamp.create(64, Double.NaN, Double.NaN, false), false);
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        Assert.assertEquals(1, graph.getNodes().filter(SqrtNode.class).count());
    }

    @Test
    public void testClampedPower() throws Throwable {
        InstalledCode code = getCode(getResolvedJavaMethod("clampedPower"));
        double[] inputs = {-1.0D, Double.NEGATIVE_INFINITY, -0.0D, 0.0D, 4.0D, Double.POSITIVE_INFINITY, Double.NaN,
                        -Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_NORMAL, Double.MAX_VALUE};
        for (double x : inputs) {
            double expected = StrictMath.pow(Math.max(x, -0.0D), 0.5D);
            double actual = (double) code.executeVarargs(x);
            // Compare bits so that the sign of zero is checked, while allowing any NaN encoding.
            Assert.assertEquals("input: " + x, Double.doubleToLongBits(expected), Double.doubleToLongBits(actual));
        }
    }
}
