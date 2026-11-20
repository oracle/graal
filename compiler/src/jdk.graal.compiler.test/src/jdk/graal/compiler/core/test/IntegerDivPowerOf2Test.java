/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.RightShiftNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.SignedDivNode;
import jdk.graal.compiler.nodes.calc.UnsignedRightShiftNode;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.JavaConstant;

public class IntegerDivPowerOf2Test extends GraalCompilerTest {

    public static int positiveDivByPowerOf2(boolean flag) {
        int val = flag ? 1 : 10;
        GraalDirectives.blackhole(val);
        return val / 8;
    }

    @Test
    public void testPositiveDivByPowerOf2() {
        StructuredGraph graph = parseForCompile(getResolvedJavaMethod("positiveDivByPowerOf2"));
        // We expect no rounding is needed
        assertTrue(countShiftNode(graph) == 1);
    }

    private static int countShiftNode(StructuredGraph graph) {
        return graph.getNodes().filter(node -> node instanceof RightShiftNode || node instanceof UnsignedRightShiftNode).count();
    }

    public static int unknownDivByPowerOf2(boolean flag) {
        int val = flag ? 0x800000F0 : 0x20;
        GraalDirectives.blackhole(val);
        return val / 8;
    }

    @Test
    public void testUnknownDivByPowerOf2() {
        StructuredGraph graph = parseForCompile(getResolvedJavaMethod("unknownDivByPowerOf2"));
        // We expect no rounding is needed
        assertTrue(graph.getNodes().filter(RightShiftNode.class).count() <= 1);
    }

    public static int shape(int value) {
        return value;
    }

    private StructuredGraph makeGraph(int bits, int divisor) {
        StructuredGraph graph = parseEager("shape", AllowAssumptions.YES);
        ValueNode input = bits == 32 ? graph.getParameter(0) : graph.addOrUnique(new NarrowNode(graph.getParameter(0), bits));
        ValueNode quotient = SignedDivNode.canonical(input, divisor, NodeView.DEFAULT);
        Assert.assertNotNull(quotient);
        quotient = graph.addOrUniqueWithInputs(quotient);
        ValueNode result = bits == 32 ? quotient : graph.addOrUnique(new SignExtendNode(quotient, 32));
        ReturnNode ret = graph.getNodes().filter(ReturnNode.class).first();
        ret.replaceFirstInput(ret.result(), result);
        return graph;
    }

    @Test
    public void narrowDivisionRewriteMatchesJava() throws InvalidInstalledCodeException {
        for (int bits : new int[]{8, 16, 32}) {
            for (int divisor : new int[]{2, -2, 4, -4, 8, -8, 64, -64}) {
                InstalledCode code = getCode(getResolvedJavaMethod("shape"), makeGraph(bits, divisor), true, false, getInitialOptions());
                int min = bits == 32 ? Integer.MIN_VALUE : -(1 << (bits - 1));
                int max = bits == 32 ? Integer.MAX_VALUE : (1 << (bits - 1)) - 1;
                for (int input : new int[]{min, min + 1, -8, -7, -4, -3, -1, 0, 1, 3, 4, 7, 8, max - 1, max}) {
                    int actual = ((Number) code.executeVarargs(input)).intValue();
                    Assert.assertEquals("bits=" + bits + " input=" + input + " divisor=" + divisor, input / divisor, actual);
                }
            }
        }
    }

    private static JavaConstant evaluate(ValueNode node, ValueNode input) {
        if (node == input) {
            return JavaConstant.forByte((byte) -7);
        }
        if (node.isJavaConstant()) {
            return node.asJavaConstant();
        }
        Assert.assertTrue("Expected arithmetic rewrite node: " + node, node instanceof BinaryNode);
        BinaryNode binary = (BinaryNode) node;
        JavaConstant a = evaluate(binary.getX(), input);
        JavaConstant b = evaluate(binary.getY(), input);
        if (node instanceof RightShiftNode) {
            return (JavaConstant) IntegerStamp.OPS.getShr().foldConstant(a, b);
        } else if (node instanceof UnsignedRightShiftNode) {
            return (JavaConstant) IntegerStamp.OPS.getUShr().foldConstant(a, b);
        } else if (node instanceof AddNode) {
            return (JavaConstant) IntegerStamp.OPS.getAdd().foldConstant(a, b);
        }
        throw new AssertionError("Unexpected node: " + node);
    }

    @Test
    public void actualRewriteConstantFoldMatchesDivision() {
        StructuredGraph graph = parseEager("shape", AllowAssumptions.YES);
        ValueNode input = graph.addOrUnique(new NarrowNode(graph.getParameter(0), 8));
        ValueNode quotient = SignedDivNode.canonical(input, 4, NodeView.DEFAULT);
        int actual = evaluate(quotient, input).asInt();
        Assert.assertEquals("Actual Graal arithmetic folds must preserve signed division", -1, actual);
    }
}
