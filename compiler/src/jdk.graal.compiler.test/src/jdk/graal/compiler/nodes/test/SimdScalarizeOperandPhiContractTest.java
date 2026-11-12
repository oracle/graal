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

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.vector.nodes.simd.SimdBroadcastNode;
import jdk.graal.compiler.vector.nodes.simd.SimdCutNode;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.JavaKind;

/// Tests scalarization contracts with connected, constructed SIMD graphs.
public class SimdScalarizeOperandPhiContractTest extends GraalCompilerTest {
    /// Supplies a two-predecessor merge for the constructed SIMD phi.
    public static int shape(boolean flag) {
        int value;
        if (flag) {
            value = 0;
        } else {
            value = 1;
        }
        return value;
    }

    private record Scalarized(StructuredGraph graph, ValueNode value) {
    }

    /// Builds and scalarizes arithmetic on a SIMD phi with bounded lane stamps.
    private Scalarized scalarize(int operand, boolean multiply, boolean nested) {
        StructuredGraph graph = parseEager("shape", StructuredGraph.AllowAssumptions.NO);
        ValuePhiNode parsedPhi = graph.getNodes().filter(ValuePhiNode.class).first();
        Assert.assertNotNull("parsed conditional must have a phi", parsedPhi);
        Assert.assertEquals(2, parsedPhi.merge().forwardEndCount());
        ValueNode zero = graph.addOrUnique(new SimdBroadcastNode(ConstantNode.forInt(0, graph), 4));
        ValueNode one = graph.addOrUnique(new SimdBroadcastNode(ConstantNode.forInt(1, graph), 4));
        ValueNode operandVector = graph.addOrUnique(new SimdBroadcastNode(ConstantNode.forInt(operand, graph), 4));
        SimdStamp phiStamp = SimdStamp.broadcast(StampFactory.forInteger(JavaKind.Int, 0, 1), 4);
        ValuePhiNode phi = graph.addWithoutUnique(new ValuePhiNode(phiStamp, parsedPhi.merge()));
        for (ValueNode input : parsedPhi.values()) {
            phi.addInput(input.asJavaConstant().asInt() == 0 ? zero : one);
        }
        ValueNode arithmetic = graph.addOrUnique(multiply ? new MulNode(phi, operandVector) : new AddNode(phi, operandVector));
        if (nested) {
            arithmetic = graph.addOrUnique(new SimdCutNode(arithmetic, 1, 2));
        }
        SimdCutNode cut = graph.addOrUnique(new SimdCutNode(arithmetic, nested ? 1 : 0, 1));
        ReturnNode ret = graph.getNodes().filter(ReturnNode.class).first();
        ret.replaceFirstInput(ret.result(), cut);
        Assert.assertTrue("vector phi verification", phi.verifyNode());
        Assert.assertTrue("graph verification before scalarization", graph.verify());
        ValueNode scalar = cut.tryToScalarize(getMetaAccess());
        Assert.assertTrue("graph verification after scalarization", graph.verify());
        return new Scalarized(graph, scalar);
    }

    /// Checks the neutral-addend control where operand and result bounds coincide.
    @Test
    public void zeroAddendControl() {
        IntegerStamp stamp = (IntegerStamp) scalarize(0, false, false).value().stamp(NodeView.DEFAULT);
        Assert.assertTrue(stamp.contains(0));
        Assert.assertTrue(stamp.contains(1));
    }

    /// Checks operand bounds before downstream stamp inference can repair the scalar phi.
    @Test
    public void operandPhiStampMustContainValues() {
        ValueNode scalar = scalarize(10, false, false).value();
        AddNode add = (AddNode) scalar;
        ValuePhiNode scalarPhi = (ValuePhiNode) add.getX();
        IntegerStamp phiStamp = (IntegerStamp) scalarPhi.stamp(NodeView.DEFAULT);
        IntegerStamp resultStamp = (IntegerStamp) scalar.stamp(NodeView.DEFAULT);
        Assert.assertEquals(StampFactory.forInteger(JavaKind.Int, 0, 1), phiStamp);
        Assert.assertEquals(StampFactory.forInteger(JavaKind.Int, 10, 11), resultStamp);
    }

    /// Checks operand bounds and compiled results for a non-neutral multiplier.
    @Test
    public void multiplyOperandPhiStamp() throws InvalidInstalledCodeException {
        Scalarized witness = scalarize(10, true, false);
        MulNode mul = (MulNode) witness.value();
        Assert.assertEquals(StampFactory.forInteger(JavaKind.Int, 0, 1), mul.getX().stamp(NodeView.DEFAULT));
        InstalledCode code = getCode(getResolvedJavaMethod("shape"), witness.graph(), true, false, getInitialOptions());
        Assert.assertEquals(0, code.executeVarargs(true));
        Assert.assertEquals(10, code.executeVarargs(false));
    }

    /// Checks operand bounds after combining offsets from nested cuts.
    @Test
    public void nestedCutsPreserveOperandStamp() {
        AddNode add = (AddNode) scalarize(10, false, true).value();
        Assert.assertEquals(StampFactory.forInteger(JavaKind.Int, 0, 1), add.getX().stamp(NodeView.DEFAULT));
        Assert.assertEquals(StampFactory.forInteger(JavaKind.Int, 10, 11), add.stamp(NodeView.DEFAULT));
    }

    /// Supplies a connected loop header for a constructed cyclic SIMD phi.
    public static int loopShape(int count) {
        int value = 0;
        for (int i = 0; i < count; i++) {
            value += 2;
        }
        return value;
    }

    /// Checks that recursive phi inputs reuse the cached scalar phi.
    @Test
    public void cyclicPhiTerminates() {
        StructuredGraph graph = parseEager("loopShape", StructuredGraph.AllowAssumptions.NO);
        LoopBeginNode loop = graph.getNodes().filter(LoopBeginNode.class).first();
        SimdStamp stamp = SimdStamp.broadcast(StampFactory.forKind(JavaKind.Int), 4);
        ValuePhiNode phi = graph.addWithoutUnique(new ValuePhiNode(stamp, loop));
        ValueNode initial = graph.addOrUnique(new SimdBroadcastNode(ConstantNode.forInt(0, graph), 4));
        ValueNode step = graph.addOrUnique(new SimdBroadcastNode(ConstantNode.forInt(2, graph), 4));
        phi.addInput(initial);
        phi.addInput(graph.addOrUnique(new AddNode(phi, step)));
        SimdCutNode cut = graph.addOrUnique(new SimdCutNode(phi, 1, 1));
        ReturnNode ret = graph.getNodes().filter(ReturnNode.class).first();
        ret.replaceFirstInput(ret.result(), cut);
        Assert.assertTrue(graph.verify());
        ValuePhiNode scalarPhi = (ValuePhiNode) cut.tryToScalarize(getMetaAccess());
        Assert.assertEquals(stamp.getComponent(1), scalarPhi.stamp(NodeView.DEFAULT));
        AddNode backedge = (AddNode) scalarPhi.valueAt(1);
        Assert.assertSame(scalarPhi, backedge.getX());
        Assert.assertTrue(graph.verify());
    }

    /// Checks that a stamp-consuming comparison accepts both valid phi choices.
    @Test
    public void compiledStampConsumer() throws InvalidInstalledCodeException {
        Scalarized witness = scalarize(10, false, false);
        StructuredGraph graph = witness.graph();
        LogicNode lessThan = graph.addOrUniqueWithInputs(IntegerLessThanNode.create(witness.value(), ConstantNode.forInt(15, graph), NodeView.DEFAULT));
        ValueNode result = graph.addOrUniqueWithInputs(ConditionalNode.create(lessThan, ConstantNode.forInt(1, graph), ConstantNode.forInt(0, graph), NodeView.DEFAULT));
        ReturnNode ret = graph.getNodes().filter(ReturnNode.class).first();
        ret.replaceFirstInput(ret.result(), result);
        Assert.assertTrue("graph verification before compile", graph.verify());
        InstalledCode code = getCode(getResolvedJavaMethod("shape"), graph, true, false, getInitialOptions());
        Assert.assertEquals(1, code.executeVarargs(true));
        Assert.assertEquals(1, code.executeVarargs(false));
    }
}
