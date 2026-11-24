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
package jdk.graal.compiler.nodes.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.CopySignNode;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;

public class CopySignNodeCanonicalizationTest extends GraalCompilerTest {

    @Test
    public void testEmptySign() {
        for (int bits : new int[]{32, 64}) {
            ParameterNode magnitude = new ParameterNode(0, StampPair.createSingle(FloatStamp.createUnrestricted(bits)));
            ParameterNode sign = new ParameterNode(1, StampPair.createSingle(FloatStamp.createEmpty(bits)));
            CopySignNode copy = new CopySignNode(magnitude, sign);
            Assert.assertTrue(copy.stamp(NodeView.DEFAULT).isEmpty());
            Assert.assertSame(copy, copy.canonical(null, magnitude, sign));
            Assert.assertTrue(copy.stamp(NodeView.DEFAULT).isEmpty());
        }
    }

    @Test
    public void testNonemptySign() {
        for (int bits : new int[]{32, 64}) {
            ConstantNode magnitude = bits == 32 ? ConstantNode.forFloat(-13.0f) : ConstantNode.forDouble(-13.0);
            for (int signValue : new int[]{-1, 1}) {
                FloatStamp signStamp = signValue > 0 ? FloatStamp.create(bits, 1.0, 4.0, true) : FloatStamp.create(bits, -4.0, -1.0, true);
                ParameterNode sign = new ParameterNode(0, StampPair.createSingle(signStamp));
                CopySignNode copy = new CopySignNode(magnitude, sign);
                ConstantNode expected = bits == 32 ? ConstantNode.forFloat(signValue * 13.0f) : ConstantNode.forDouble(signValue * 13.0);
                Assert.assertEquals(expected.asJavaConstant(), ((ConstantNode) copy.canonical(null, magnitude, sign)).asJavaConstant());
            }
            ParameterNode sign = new ParameterNode(0, StampPair.createSingle(FloatStamp.createNaN(bits)));
            CopySignNode copy = new CopySignNode(magnitude, sign);
            Assert.assertSame(copy, copy.canonical(null, magnitude, sign));
        }
    }

    public static float floatSnippet(float magnitude, float sign) {
        return magnitude + sign;
    }

    public static double doubleSnippet(double magnitude, double sign) {
        return magnitude + sign;
    }

    @Test
    public void testCanonicalizer() {
        for (int bits : new int[]{32, 64}) {
            for (FloatStamp stamp : new FloatStamp[]{FloatStamp.createEmpty(bits), FloatStamp.create(bits, 1.0, 4.0, true),
                            FloatStamp.create(bits, -4.0, -1.0, true), FloatStamp.createNaN(bits)}) {
                StructuredGraph graph = parseEager(bits == 32 ? "floatSnippet" : "doubleSnippet", StructuredGraph.AllowAssumptions.NO);
                ValueNode sign = graph.addOrUnique(new PiNode(graph.getParameter(1), stamp));
                CopySignNode copy = graph.addOrUniqueWithInputs(new CopySignNode(graph.getParameter(0), sign));
                ReturnNode ret = graph.getNodes().filter(ReturnNode.class).first();
                ret.replaceFirstInput(ret.result(), copy);
                Assert.assertTrue(graph.verify());
                CanonicalizerPhase.create().apply(graph, getDefaultHighTierContext());
                if (stamp.isEmpty()) {
                    Assert.assertSame(copy, ret.result());
                    Assert.assertTrue(ret.result().stamp(NodeView.DEFAULT).isEmpty());
                }
                Assert.assertTrue(graph.verify());
            }
        }
    }
}
