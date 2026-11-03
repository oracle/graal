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

import org.junit.Test;
import org.junit.Assert;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ReadArgumentNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.phases.common.OptimizeExtendsPhase.ZeroExtendAnalysis;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.ChainedPhiValueSplitNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.extended.OpaqueValueNode;
import jdk.graal.compiler.replacements.nodes.LateLoweredNode;
import jdk.vm.ci.meta.JavaKind;

/** Checks explicit zero-extension decisions for phis and their inputs. */
public class ZeroExtendAnalysisTest extends GraalCompilerTest {
    private static ValuePhiNode createPhi(StructuredGraph graph, ValueNode first, ValueNode second) {
        return graph.addWithoutUnique(new ValuePhiNode(StampFactory.forKind(JavaKind.Int), graph.add(new MergeNode()), first, second));
    }

    public static void empty() {
    }

    /** Checks phi cycles with a narrowed long input and an independent cycle of int sums. */
    @Test
    public void testCycles() {
        StructuredGraph graph = parseEager("empty", StructuredGraph.AllowAssumptions.NO);
        ParameterNode input = graph.addWithoutUnique(new ParameterNode(0, StampPair.createSingle(StampFactory.forKind(JavaKind.Long))));
        ValueNode narrowed = graph.addWithoutUnique(new NarrowNode(input, 32));
        ParameterNode intInput = graph.addWithoutUnique(new ParameterNode(1, StampPair.createSingle(StampFactory.forKind(JavaKind.Int))));
        ValueNode sum = graph.addWithoutUnique(new AddNode(intInput, intInput));
        ValuePhiNode narrowedFirst = createPhi(graph, sum, narrowed);
        ValuePhiNode narrowedSecond = createPhi(graph, narrowedFirst, sum);
        narrowedFirst.setValueAt(0, narrowedSecond);
        ValuePhiNode sumFirst = createPhi(graph, sum, sum);
        ValuePhiNode sumSecond = createPhi(graph, sumFirst, sum);
        sumFirst.setValueAt(0, sumSecond);
        ZeroExtendNode sumFirstExtend = graph.addWithoutUnique(new ZeroExtendNode(sumFirst, 64));
        ZeroExtendNode sumSecondExtend = graph.addWithoutUnique(new ZeroExtendNode(sumSecond, 64));
        ZeroExtendNode narrowedFirstExtend = graph.addWithoutUnique(new ZeroExtendNode(narrowedFirst, 64));
        ZeroExtendNode narrowedSecondExtend = graph.addWithoutUnique(new ZeroExtendNode(narrowedSecond, 64));
        ZeroExtendAnalysis.computeRequiresExplicitZeroExtend(graph);
        Assert.assertFalse(sumFirstExtend.requiresExplicitZeroExtend());
        Assert.assertFalse(sumSecondExtend.requiresExplicitZeroExtend());
        Assert.assertTrue(narrowedFirstExtend.requiresExplicitZeroExtend());
        Assert.assertTrue(narrowedSecondExtend.requiresExplicitZeroExtend());
    }

    /** Splits must be unwrapped both at the conversion and among phi inputs. */
    @Test
    public void testSplitInputs() {
        StructuredGraph graph = parseEager("empty", StructuredGraph.AllowAssumptions.NO);
        ParameterNode input = graph.addWithoutUnique(new ParameterNode(0, StampPair.createSingle(StampFactory.forKind(JavaKind.Long))));
        ValueNode narrow = graph.addWithoutUnique(new NarrowNode(input, 32));
        ChainedPhiValueSplitNode split = graph.addWithoutUnique(new ChainedPhiValueSplitNode(narrow));
        ValuePhiNode phi = createPhi(graph, split, split);
        ZeroExtendNode splitExtend = graph.addWithoutUnique(new ZeroExtendNode(split, 64));
        ZeroExtendNode phiExtend = graph.addWithoutUnique(new ZeroExtendNode(phi, 64));
        ZeroExtendAnalysis.computeRequiresExplicitZeroExtend(graph);
        Assert.assertTrue(splitExtend.requiresExplicitZeroExtend());
        Assert.assertTrue(phiExtend.requiresExplicitZeroExtend());
    }

    /** Primary and additional foreign-call returns may have nonzero upper register bits. */
    @Test
    public void testForeignReturns() {
        StructuredGraph graph = parseEager("empty", StructuredGraph.AllowAssumptions.NO);
        ForeignCallDescriptor descriptor = new ForeignCallDescriptor("intReturn", int.class, new Class<?>[0],
                        ForeignCallDescriptor.CallSideEffect.NO_SIDE_EFFECT, new LocationIdentity[0], false, false);
        ForeignCallNode call = graph.add(new ForeignCallNode(descriptor));
        ReadArgumentNode additionalReturn = graph.addWithoutUnique(new ReadArgumentNode(call, JavaKind.Int, 0));
        ValueNode sum = graph.addWithoutUnique(new AddNode(ConstantNode.forInt(1, graph), ConstantNode.forInt(2, graph)));
        ValuePhiNode primaryPhi = createPhi(graph, sum, call);
        ValuePhiNode additionalPhi = createPhi(graph, sum, additionalReturn);
        ZeroExtendNode primaryExtend = graph.addWithoutUnique(new ZeroExtendNode(call, 64));
        ZeroExtendNode additionalExtend = graph.addWithoutUnique(new ZeroExtendNode(additionalReturn, 64));
        ZeroExtendNode primaryPhiExtend = graph.addWithoutUnique(new ZeroExtendNode(primaryPhi, 64));
        ZeroExtendNode additionalPhiExtend = graph.addWithoutUnique(new ZeroExtendNode(additionalPhi, 64));
        ZeroExtendAnalysis.computeRequiresExplicitZeroExtend(graph);
        Assert.assertTrue("Primary foreign-call returns need clearing", primaryExtend.requiresExplicitZeroExtend());
        Assert.assertTrue("Additional returns need clearing", additionalExtend.requiresExplicitZeroExtend());
        Assert.assertTrue("Phis with primary returns need explicit zero extension", primaryPhiExtend.requiresExplicitZeroExtend());
        Assert.assertTrue("Phis with additional returns need explicit zero extension", additionalPhiExtend.requiresExplicitZeroExtend());
    }

    /** Checks default flags and the restriction of ZeroExtendAnalysis to 32-to-64 extensions. */
    @Test
    public void testDefaultsAndWidths() {
        StructuredGraph graph = parseEager("empty", StructuredGraph.AllowAssumptions.NO);
        ValueNode sum = graph.addWithoutUnique(new AddNode(ConstantNode.forInt(1, graph), ConstantNode.forInt(2, graph)));
        ZeroExtendNode extend = graph.addWithoutUnique(new ZeroExtendNode(sum, 64));
        ValueNode byteValue = graph.addWithoutUnique(new NarrowNode(sum, 8));
        ZeroExtendNode otherWidth = graph.addWithoutUnique(new ZeroExtendNode(byteValue, 8, 32));
        Assert.assertTrue(extend.requiresExplicitZeroExtend());
        Assert.assertTrue(otherWidth.requiresExplicitZeroExtend());
        ZeroExtendAnalysis.computeRequiresExplicitZeroExtend(graph);
        Assert.assertFalse(extend.requiresExplicitZeroExtend());
        Assert.assertTrue(otherWidth.requiresExplicitZeroExtend());
        ZeroExtendNode later = graph.addWithoutUnique(new ZeroExtendNode(sum, 64));
        Assert.assertTrue(later.requiresExplicitZeroExtend());
    }

    /** The explicit zero extension flag must not change from false to true. */
    @Test
    public void testExplicitZeroExtendCannotBeReenabled() {
        StructuredGraph graph = parseEager("empty", StructuredGraph.AllowAssumptions.NO);
        ZeroExtendNode extend = graph.addWithoutUnique(new ZeroExtendNode(ConstantNode.forInt(1, graph), 64));
        extend.setRequiresExplicitZeroExtend(true);
        extend.setRequiresExplicitZeroExtend(false);
        extend.setRequiresExplicitZeroExtend(false);
        Assert.assertThrows(GraalError.class, () -> extend.setRequiresExplicitZeroExtend(true));
        Assert.assertFalse(extend.requiresExplicitZeroExtend());
    }

    /** Opaque inputs require explicit zero extension, both directly and through phis. */
    @Test
    public void testOpaqueReplacement() {
        StructuredGraph graph = parseEager("empty", StructuredGraph.AllowAssumptions.NO);
        ParameterNode input = graph.addWithoutUnique(new ParameterNode(0, StampPair.createSingle(StampFactory.forKind(JavaKind.Long))));
        ValueNode narrowed = graph.addWithoutUnique(new NarrowNode(input, 32));
        ValueNode sum = graph.addWithoutUnique(new AddNode(ConstantNode.forInt(1, graph), ConstantNode.forInt(2, graph)));
        OpaqueValueNode narrowedOpaque = graph.addWithoutUnique(new OpaqueValueNode(narrowed));
        OpaqueValueNode sumOpaque = graph.addWithoutUnique(new OpaqueValueNode(sum));
        ZeroExtendNode narrowedExtend = graph.addWithoutUnique(new ZeroExtendNode(narrowedOpaque, 64));
        ZeroExtendNode sumExtend = graph.addWithoutUnique(new ZeroExtendNode(sumOpaque, 64));
        ValuePhiNode sumPhi = createPhi(graph, sumOpaque, sum);
        ZeroExtendNode sumPhiExtend = graph.addWithoutUnique(new ZeroExtendNode(sumPhi, 64));
        ZeroExtendAnalysis.computeRequiresExplicitZeroExtend(graph);
        narrowedOpaque.replaceAtUsagesAndDelete(narrowed);
        sumOpaque.replaceAtUsagesAndDelete(sum);
        Assert.assertTrue(narrowedExtend.requiresExplicitZeroExtend());
        Assert.assertTrue(sumExtend.requiresExplicitZeroExtend());
        Assert.assertTrue(sumPhiExtend.requiresExplicitZeroExtend());
    }

    /** Late calls can acquire results with nonzero upper register bits during transplantation. */
    @Test
    public void testLateCallReplacement() {
        StructuredGraph graph = parseEager("empty", StructuredGraph.AllowAssumptions.NO);
        LateLoweredNode call = graph.addWithoutUnique(new LateLoweredNode(getResolvedJavaMethod("empty"), StampFactory.forKind(JavaKind.Int), new ValueNode[0], null));
        ValueNode sum = graph.addWithoutUnique(new AddNode(ConstantNode.forInt(1, graph), ConstantNode.forInt(2, graph)));
        ValuePhiNode phi = createPhi(graph, sum, call);
        ZeroExtendNode direct = graph.addWithoutUnique(new ZeroExtendNode(call, 64));
        ZeroExtendNode throughPhi = graph.addWithoutUnique(new ZeroExtendNode(phi, 64));
        ZeroExtendAnalysis.computeRequiresExplicitZeroExtend(graph);
        ParameterNode input = graph.addWithoutUnique(new ParameterNode(0, StampPair.createSingle(StampFactory.forKind(JavaKind.Long))));
        ValueNode narrowed = graph.addWithoutUnique(new NarrowNode(input, 32));
        call.replaceAtUsagesAndDelete(narrowed);
        Assert.assertTrue(direct.requiresExplicitZeroExtend());
        Assert.assertTrue(throughPhi.requiresExplicitZeroExtend());
    }
}
