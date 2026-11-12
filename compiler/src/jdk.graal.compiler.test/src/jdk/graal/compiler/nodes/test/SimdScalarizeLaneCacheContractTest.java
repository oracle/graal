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

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.graal.compiler.vector.nodes.simd.SimdCutNode;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/// Tests scalarization of different lanes of a shared, connected SIMD phi.
public class SimdScalarizeLaneCacheContractTest extends GraalCompilerTest {
    /// Supplies a two-predecessor merge for the constructed SIMD phi.
    public static int shape(boolean flag) {
        int v;
        if (flag) {
            v = 0;
        } else {
            v = 1;
        }
        return v;
    }

    /// Creates a SIMD constant with distinct values in each lane.
    private ValueNode vector(StructuredGraph graph, int a, int b, int c) {
        JavaConstant[] values = {JavaConstant.forInt(a), JavaConstant.forInt(b), JavaConstant.forInt(c)};
        return graph.addOrUnique(SimdConstant.constantNodeForConstants(values));
    }

    /// Explicitly scalarizes nested cuts to exercise the phi cache independently of vectorization.
    private InstalledCode compileScalarized(boolean differentLane, int offset) {
        StructuredGraph graph = parseEager("shape", StructuredGraph.AllowAssumptions.NO);
        ValuePhiNode parsed = graph.getNodes().filter(ValuePhiNode.class).first();
        Assert.assertNotNull(parsed);
        Assert.assertEquals(2, parsed.merge().forwardEndCount());
        ValueNode[] arms = new ValueNode[2];
        for (int i = 0; i < arms.length; i++) {
            if (parsed.valueAt(i).asJavaConstant().asInt() == 0) {
                arms[i] = vector(graph, 1, 2, 3);
            } else {
                arms[i] = vector(graph, 4, 5, 6);
            }
        }
        SimdStamp stamp = SimdStamp.broadcast(StampFactory.forKind(JavaKind.Int), 3);
        ValuePhiNode phi = graph.addWithoutUnique(new ValuePhiNode(stamp, parsed.merge(), arms));
        ValueNode first = graph.addOrUnique(new SimdCutNode(phi, 0, 2));
        ValueNode second = graph.addOrUnique(new SimdCutNode(phi, differentLane ? 1 : 0, 2));
        ValueNode add = graph.addOrUnique(new AddNode(first, second));
        SimdCutNode rootCut = graph.addOrUnique(new SimdCutNode(add, offset, 1));
        ReturnNode ret = graph.getNodes().filter(ReturnNode.class).first();
        ret.replaceFirstInput(ret.result(), rootCut);
        Assert.assertTrue("connected graph before scalarization", graph.verify());
        AddNode scalar = (AddNode) rootCut.tryToScalarize(getMetaAccess());
        if (differentLane) {
            Assert.assertNotSame(scalar.getX(), scalar.getY());
        } else {
            Assert.assertSame(scalar.getX(), scalar.getY());
        }
        Assert.assertTrue("connected graph after scalarization", graph.verify());
        return getCode(getResolvedJavaMethod("shape"), graph, true, false, getInitialOptions());
    }

    /// Checks that repeated visits to the same lane share one scalar phi.
    @Test
    public void sameLaneControl() throws InvalidInstalledCodeException {
        InstalledCode code = compileScalarized(false, 0);
        Assert.assertEquals(2, code.executeVarargs(true));
        Assert.assertEquals(8, code.executeVarargs(false));
    }

    /// Checks distinct cached phis and compiled results for the reported lane pair.
    @Test
    public void distinctLanesMustStayDistinct() throws InvalidInstalledCodeException {
        InstalledCode code = compileScalarized(true, 0);
        Assert.assertEquals(3, code.executeVarargs(true));
        Assert.assertEquals(9, code.executeVarargs(false));
    }

    /// Checks that the outer cut offset combines with each operand cut offset.
    @Test
    public void nestedCutOffsetsAccumulate() throws InvalidInstalledCodeException {
        InstalledCode code = compileScalarized(true, 1);
        Assert.assertEquals(5, code.executeVarargs(true));
        Assert.assertEquals(11, code.executeVarargs(false));
    }

    /// Supplies a connected loop header for cyclic SIMD lanes.
    public static int loopShape(int count) {
        int value = 0;
        for (int i = 0; i < count; i++) {
            value += 2;
        }
        return value;
    }

    /// Checks that each cyclic lane reuses its own scalar phi on the backedge.
    @Test
    public void distinctCyclicLanes() {
        StructuredGraph graph = parseEager("loopShape", StructuredGraph.AllowAssumptions.NO);
        LoopBeginNode loop = graph.getNodes().filter(LoopBeginNode.class).first();
        SimdStamp stamp = SimdStamp.broadcast(StampFactory.forKind(JavaKind.Int), 3);
        ValuePhiNode phi = graph.addWithoutUnique(new ValuePhiNode(stamp, loop));
        ValueNode initial = vector(graph, 1, 2, 3);
        phi.addInput(initial);
        phi.addInput(graph.addOrUnique(new AddNode(phi, initial)));
        ValueNode first = graph.addOrUnique(new SimdCutNode(phi, 0, 2));
        ValueNode second = graph.addOrUnique(new SimdCutNode(phi, 1, 2));
        ValueNode add = graph.addOrUnique(new AddNode(first, second));
        SimdCutNode cut = graph.addOrUnique(new SimdCutNode(add, 0, 1));
        ReturnNode ret = graph.getNodes().filter(ReturnNode.class).first();
        ret.replaceFirstInput(ret.result(), cut);
        Assert.assertTrue(graph.verify());
        AddNode scalar = (AddNode) cut.tryToScalarize(getMetaAccess());
        ValuePhiNode firstPhi = (ValuePhiNode) scalar.getX();
        ValuePhiNode secondPhi = (ValuePhiNode) scalar.getY();
        Assert.assertNotSame(firstPhi, secondPhi);
        Assert.assertEquals(1, firstPhi.valueAt(0).asJavaConstant().asInt());
        Assert.assertEquals(2, secondPhi.valueAt(0).asJavaConstant().asInt());
        Assert.assertSame(firstPhi, ((AddNode) firstPhi.valueAt(1)).getX());
        Assert.assertSame(secondPhi, ((AddNode) secondPhi.valueAt(1)).getX());
        Assert.assertEquals(stamp.getComponent(0), firstPhi.stamp(NodeView.DEFAULT));
        Assert.assertEquals(stamp.getComponent(1), secondPhi.stamp(NodeView.DEFAULT));
        Assert.assertTrue(graph.verify());
    }
}
