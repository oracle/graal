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
package jdk.graal.compiler.duplication.test;

import java.lang.reflect.Field;
import java.util.ArrayDeque;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugDumpScope;
import jdk.graal.compiler.duplication.util.DuplicationUtil;
import jdk.graal.compiler.duplication.util.DuplicationUtil.DuplicationRegion;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GuardPhiNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ProfileData.BranchProbabilityData;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.ValueProxyNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IntegerBelowNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.SignedFloatingIntegerRemNode;
import jdk.graal.compiler.nodes.calc.UnsignedRightShiftNode;
import jdk.graal.compiler.nodes.calc.XorNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.util.GraphOrder;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Manual compiler-only regression for GR-74049.
 *
 * <p>The hosted SVM reproducer in {@code DuplicationRegressionGR74049Test} proves the production
 * failure, but it is too far away from the duplication code to let us shape the exact
 * loop-exit-proxy and guard-phi neighborhood that later becomes unschedulable during duplication.
 * This test therefore hand-builds the graph in Java code instead of deriving it from bytecode.
 * That trade-off is deliberate: the manual graph makes the structural preconditions explicit,
 * keeps the failing duplication region stable across unrelated canonicalization changes, and gives
 * us one fast unit-test entry point for future debugging.
 */
public class GR74049ManualLoopExitProxyTest extends GraalCompilerTest {

    private static final long HOSTED_WIDE_LONG_LOWER_BOUND = Long.MIN_VALUE + 1;
    private static final long HOSTED_WIDE_LONG_UPPER_BOUND = Long.MAX_VALUE;
    private static final long HOSTED_POSITIVE_LONG_LOWER_BOUND = 1L;
    private static final long HOSTED_POSITIVE_LONG_UPPER_BOUND = Long.MAX_VALUE;
    private static final long HOSTED_LONG_PROXY_UPPER_BOUND = 536_870_911L;

    static final class Holder {
        long field;
    }

    @SuppressWarnings("unused")
    private static long graphStub(Holder holder, long dividend, long divisor) {
        return holder.field + dividend + divisor;
    }

    /**
     * Anchors the manually assembled graph plus the fixed nodes that define the duplication region
     * under test.
     */
    private static final class ReproGraph {
        final StructuredGraph graph;
        final MergeNode duplicationMerge;
        final StoreFieldNode regionEnd;
        final EndNode duplicatedEnd;

        ReproGraph(StructuredGraph graph, MergeNode duplicationMerge, StoreFieldNode regionEnd, EndNode duplicatedEnd) {
            this.graph = graph;
            this.duplicationMerge = duplicationMerge;
            this.regionEnd = regionEnd;
            this.duplicatedEnd = duplicatedEnd;
        }
    }

    /**
     * Verifies that the manual GR-74049 graph survives the same pre-duplication rewrite used in
     * production and then reaches duplication with a valid schedule.
     */
    @Test
    public void testManualLoopExitProxyGraphTriggersLoopBeginClassificationHole() throws Exception {
        ReproGraph repro = buildGraph();
        DebugContext debug = repro.graph.getDebug();
        try (DebugContext.Scope _ = debug.scope("GR74049ManualLoopExitProxyTest",
                        new Object[]{new DebugDumpScope("GR74049ManualLoopExitProxyTest", true), repro.graph, repro.duplicationMerge, repro.regionEnd, repro.duplicatedEnd})) {
            CanonicalizerPhase canonicalizer = CanonicalizerPhase.createWithoutCFGSimplification();
            HighTierContext context = getDefaultHighTierContext();
            debug.dump(DebugContext.BASIC_LEVEL, repro.graph, "Hosted-shaped manual repro before pre-dup canonicalization");
            canonicalizer.apply(repro.graph, context);
            Assert.assertTrue("duplication merge survived pre-dup canonicalization", repro.duplicationMerge.isAlive());
            Assert.assertTrue("duplication region end survived pre-dup canonicalization", repro.regionEnd.isAlive());
            Assert.assertTrue("duplicated end survived pre-dup canonicalization", repro.duplicatedEnd.isAlive());
            debug.dump(DebugContext.BASIC_LEVEL, repro.graph, "Hosted-shaped manual repro after pre-dup canonicalization");

            int rewrittenGuardInputs = DuplicationUtil.splitRegularMergeGuardInputs(repro.graph);
            Assert.assertTrue("expected to rewrite at least one guard-phi merge input", rewrittenGuardInputs > 0);
            debug.dump(DebugContext.BASIC_LEVEL, repro.graph, "Hosted-shaped manual repro after splitting guard-phi merge inputs");

            Assert.assertTrue(repro.graph.verify(true));
            ControlFlowGraph cfg = ControlFlowGraph.computeForSchedule(repro.graph);
            SchedulePhase.runWithoutContextOptimizations(repro.graph, SchedulePhase.SchedulingStrategy.EARLIEST_WITH_GUARD_ORDER, cfg, true, true);
            Assert.assertTrue("graph must keep a valid schedule before duplication", GraphOrder.assertSchedulableGraph(repro.graph));
            Assert.assertTrue("graph must keep a cached schedule before duplication", repro.graph.isLastScheduleValid());
            debug.dump(DebugContext.BASIC_LEVEL, repro.graph, "Hosted-shaped manual repro before region creation");

            SimplifierTool simplifierTool = GraphUtil.getDefaultSimplifier(context, canonicalizer.getCanonicalizeReads(), repro.graph.getAssumptions(), repro.graph.getOptions());
            DuplicationUtil duplicationUtil = new DuplicationUtil(repro.graph, simplifierTool);
            DuplicationRegion region = DuplicationUtil.createRegion(new ArrayDeque<>(), repro.duplicationMerge, repro.regionEnd, repro.duplicatedEnd);
            debug.dump(DebugContext.BASIC_LEVEL, repro.graph, "Hosted-shaped manual repro before duplication");
            duplicationUtil.duplicate(repro.duplicationMerge, region, canonicalizer, context);
        } catch (Throwable t) {
            throw debug.handle(t);
        }
    }

    /**
     * Verifies that nested regular-merge guard inputs are rewritten to a fixpoint so replacement
     * guard phis do not keep unreduced regular-merge inputs alive.
     */
    @Test
    public void testSplitRegularMergeGuardInputsReachesFixpointForNestedRegularMerges() throws Exception {
        StructuredGraph graph = buildNestedRegularMergeGuardInputGraph();
        int replacements = DuplicationUtil.splitRegularMergeGuardInputs(graph);
        Assert.assertEquals("expected to rewrite both the outer merge input and the nested predecessor merge input", 2, replacements);
        for (GuardPhiNode guardPhi : graph.getNodes().filter(GuardPhiNode.class)) {
            for (int i = 0; i < guardPhi.valueCount(); i++) {
                ValueNode input = guardPhi.valueAt(i);
                if (input instanceof MergeNode mergeInput && mergeInput != guardPhi.merge()) {
                    Assert.fail("guard phi still contains unreduced regular-merge input " + mergeInput + " at index " + i + " of " + guardPhi);
                }
            }
        }
        Assert.assertTrue(graph.verify(true));
    }

    /**
     * Builds the smallest hand-shaped graph we currently know that preserves the hosted failure
     * ingredients from GR-74049.
     *
     * <p>This is intentionally assembled node-by-node instead of parsed from bytecode: the hosted
     * failure depends on a precise combination of nested loop exits, guard phis, proxies, and a
     * post-merge linear suffix leading into duplication. Expressing that shape directly in graph
     * construction code keeps the unit test honest about which edges matter and avoids accidental
     * reshaping by unrelated front-end or lowering changes before duplication is reached.
     */
    private ReproGraph buildGraph() throws Exception {
        OptionValues options = getInitialOptions();
        ResolvedJavaMethod method = getResolvedJavaMethod(getClass(), "graphStub");
        DebugContext debug = getDebugContext(options, null, method);
        StructuredGraph graph = new StructuredGraph.Builder(options, debug, AllowAssumptions.YES).method(method).build();

        ResolvedJavaType holderType = getMetaAccess().lookupJavaType(Holder.class);
        Field reflectedField = Holder.class.getDeclaredField("field");
        ResolvedJavaField field = getMetaAccess().lookupJavaField(reflectedField);

        ParameterNode holder = graph.addWithoutUnique(new ParameterNode(0, StampFactory.forDeclaredType(graph.getAssumptions(), holderType, true)));
        ParameterNode dividend = graph.addWithoutUnique(new ParameterNode(1, longStamp(HOSTED_WIDE_LONG_LOWER_BOUND, HOSTED_WIDE_LONG_UPPER_BOUND)));
        ParameterNode divisor = graph.addWithoutUnique(new ParameterNode(2, longStamp(HOSTED_POSITIVE_LONG_LOWER_BOUND, HOSTED_POSITIVE_LONG_UPPER_BOUND)));

        BeginNode outerLoopEntryBegin = graph.add(new BeginNode());
        BeginNode bypassBegin = graph.add(new BeginNode());
        IfNode startSplit = graph.add(new IfNode(createLongEqualsCondition(graph, dividend, 17L), outerLoopEntryBegin, bypassBegin, BranchProbabilityData.unknown()));
        graph.start().setNext(startSplit);

        EndNode preheaderEnd296 = graph.add(new EndNode());
        outerLoopEntryBegin.setNext(preheaderEnd296);

        BeginNode bypassPath0True = graph.add(new BeginNode());
        BeginNode bypassPath0False = graph.add(new BeginNode());
        IfNode bypassPath0 = graph.add(new IfNode(createLongEqualsCondition(graph, divisor, 19L), bypassPath0True, bypassPath0False, BranchProbabilityData.unknown()));
        bypassBegin.setNext(bypassPath0);

        EndNode bypassEnd6036 = graph.add(new EndNode());
        bypassPath0True.setNext(bypassEnd6036);

        BeginNode bypassPath1True = graph.add(new BeginNode());
        BeginNode bypassPath1False = graph.add(new BeginNode());
        IfNode bypassPath1 = graph.add(new IfNode(createLongEqualsCondition(graph, dividend, 23L), bypassPath1True, bypassPath1False, BranchProbabilityData.unknown()));
        bypassPath0False.setNext(bypassPath1);

        EndNode bypassEnd6057 = graph.add(new EndNode());
        bypassPath1True.setNext(bypassEnd6057);
        EndNode bypassEnd6134 = graph.add(new EndNode());
        bypassPath1False.setNext(bypassEnd6134);

        LoopBeginNode outerLoopBegin = graph.add(new LoopBeginNode());
        outerLoopBegin.setStateAfter(graph.addWithoutUnique(new FrameState(BytecodeFrame.UNKNOWN_BCI)));
        outerLoopBegin.addForwardEnd(preheaderEnd296);

        ValuePhiNode outerLoopIv299 = graph.addWithoutUnique(new ValuePhiNode(StampFactory.forInteger(JavaKind.Int, 0, 101), outerLoopBegin));
        GuardPhiNode outerGuardPhi5732 = graph.addWithoutUnique(new GuardPhiNode(outerLoopBegin));
        ValuePhiNode outerLoopValuePhi5734 = graph.addWithoutUnique(new ValuePhiNode(StampFactory.forInteger(JavaKind.Int, 0, 1), outerLoopBegin));

        ValueNode shiftSeed343 = graph.addOrUniqueWithInputs(ConditionalNode.create(createIntEqualsCondition(graph, outerLoopValuePhi5734, 0),
                        exactLongConstant(graph, -298L), exactLongConstant(graph, 74L), NodeView.DEFAULT));
        ValueNode narrowShiftSeed344 = graph.addOrUniqueWithInputs(NarrowNode.create(shiftSeed343, 32, NodeView.DEFAULT));
        ValueNode outerShift345 = graph.addOrUniqueWithInputs(UnsignedRightShiftNode.create(exactLongConstant(graph, 4L), narrowShiftSeed344, NodeView.DEFAULT));
        ValueNode remDivisor349 = graph.addOrUniqueWithInputs(ConditionalNode.create(createIntEqualsCondition(graph, outerLoopValuePhi5734, 1),
                        exactLongConstant(graph, 288L), exactLongConstant(graph, -9_223_372_036_854_775_594L), NodeView.DEFAULT));
        ValueNode outerRem350 = graph.addOrUniqueWithInputs(SignedFloatingIntegerRemNode.create(exactLongConstant(graph, 528_482_304L), remDivisor349, NodeView.DEFAULT, outerGuardPhi5732, true));
        ValueNode outerXor351 = graph.addOrUniqueWithInputs(new XorNode(outerShift345, outerRem350));

        BeginNode outerBodyBegin335 = graph.add(new BeginNode());
        LoopExitNode outerLoopExit336 = graph.add(new LoopExitNode(outerLoopBegin));
        outerLoopExit336.setStateAfter(graph.addWithoutUnique(new FrameState(BytecodeFrame.UNKNOWN_BCI)));
        LogicNode outerLoopContinueCondition = graph.addOrUniqueWithInputs(IntegerBelowNode.create(outerLoopIv299, exactIntConstant(graph, 101), NodeView.DEFAULT));
        IfNode outerLoopBranch = graph.add(new IfNode(outerLoopContinueCondition, outerBodyBegin335, outerLoopExit336, BranchProbabilityData.unknown()));
        outerLoopBegin.setNext(outerLoopBranch);

        ValueProxyNode outerExitProxy338 = graph.addWithoutUnique(new ValueProxyNode(outerLoopValuePhi5734, outerLoopExit336));
        ValueProxyNode outerExitProxy352 = graph.addWithoutUnique(new ValueProxyNode(outerXor351, outerLoopExit336));

        BeginNode duplicatedBegin = graph.add(new BeginNode());
        BeginNode mergePeerBegin = graph.add(new BeginNode());
        IfNode duplicationSplit = graph.add(new IfNode(createLongEqualsCondition(graph, dividend, 41L), duplicatedBegin, mergePeerBegin, BranchProbabilityData.unknown()));
        outerBodyBegin335.setNext(duplicationSplit);

        EndNode duplicatedEnd402 = graph.add(new EndNode());
        duplicatedBegin.setNext(duplicatedEnd402);
        EndNode mergePeerEnd384 = graph.add(new EndNode());
        mergePeerBegin.setNext(mergePeerEnd384);

        MergeNode duplicationMerge385 = graph.add(new MergeNode());
        duplicationMerge385.setStateAfter(graph.addWithoutUnique(new FrameState(BytecodeFrame.UNKNOWN_BCI)));
        duplicationMerge385.addForwardEnd(mergePeerEnd384);
        duplicationMerge385.addForwardEnd(duplicatedEnd402);

        ValuePhiNode storedHolderPhi387 = graph.addWithoutUnique(new ValuePhiNode(holder.stamp(NodeView.DEFAULT), duplicationMerge385));
        storedHolderPhi387.addInput(ConstantNode.defaultForKind(JavaKind.Object, graph));
        storedHolderPhi387.addInput(holder);

        FrameState storeState406 = graph.addWithoutUnique(new FrameState(BytecodeFrame.UNKNOWN_BCI));
        StoreFieldNode store405 = graph.add(new StoreFieldNode(storedHolderPhi387, field, outerXor351, storeState406));
        duplicationMerge385.setNext(store405);

        LoopEndNode outerLoopEnd407 = graph.add(new LoopEndNode(outerLoopBegin));
        store405.setNext(outerLoopEnd407);

        EndNode outerExitEnd6035 = graph.add(new EndNode());
        outerLoopExit336.setNext(outerExitEnd6035);

        MergeNode postOuterExitMerge6034 = graph.add(new MergeNode());
        postOuterExitMerge6034.setStateAfter(graph.addWithoutUnique(new FrameState(BytecodeFrame.UNKNOWN_BCI)));
        postOuterExitMerge6034.addForwardEnd(outerExitEnd6035);
        postOuterExitMerge6034.addForwardEnd(bypassEnd6036);
        postOuterExitMerge6034.addForwardEnd(bypassEnd6057);
        postOuterExitMerge6034.addForwardEnd(bypassEnd6134);

        ValuePhiNode postOuterIntPhi6043 = graph.addWithoutUnique(new ValuePhiNode(StampFactory.forInteger(JavaKind.Int, 0, 1), postOuterExitMerge6034));
        postOuterIntPhi6043.addInput(outerExitProxy338);
        postOuterIntPhi6043.addInput(exactIntConstant(graph, 0));
        postOuterIntPhi6043.addInput(exactIntConstant(graph, 1));
        postOuterIntPhi6043.addInput(exactIntConstant(graph, 1));

        ValuePhiNode postOuterLongPhi6044 = graph.addWithoutUnique(new ValuePhiNode(StampFactory.forInteger(JavaKind.Long, 0, HOSTED_LONG_PROXY_UPPER_BOUND), postOuterExitMerge6034));
        postOuterLongPhi6044.addInput(outerExitProxy352);
        postOuterLongPhi6044.addInput(exactLongConstant(graph, 288L));
        postOuterLongPhi6044.addInput(exactLongConstant(graph, 74L));
        postOuterLongPhi6044.addInput(exactLongConstant(graph, 1L));

        EndNode innerEntryEnd337 = graph.add(new EndNode());
        postOuterExitMerge6034.setNext(innerEntryEnd337);

        LoopBeginNode innerLoopBegin408 = graph.add(new LoopBeginNode());
        innerLoopBegin408.setStateAfter(graph.addWithoutUnique(new FrameState(BytecodeFrame.UNKNOWN_BCI)));
        innerLoopBegin408.addForwardEnd(innerEntryEnd337);

        ValuePhiNode innerLoopValuePhi415 = graph.addWithoutUnique(new ValuePhiNode(StampFactory.forInteger(JavaKind.Int, 0, 1), innerLoopBegin408));
        ValuePhiNode innerLoopValuePhi422 = graph.addWithoutUnique(new ValuePhiNode(StampFactory.forInteger(JavaKind.Long, 0, HOSTED_LONG_PROXY_UPPER_BOUND), innerLoopBegin408));

        BeginNode innerLoopContinueBegin = graph.add(new BeginNode());
        BeginNode innerLoopExitBegin = graph.add(new BeginNode());
        IfNode innerLoopBranch = graph.add(new IfNode(createIntEqualsCondition(graph, innerLoopValuePhi415, 1), innerLoopExitBegin, innerLoopContinueBegin, BranchProbabilityData.unknown()));
        innerLoopBegin408.setNext(innerLoopBranch);

        LoopEndNode innerLoopEnd = graph.add(new LoopEndNode(innerLoopBegin408));
        innerLoopContinueBegin.setNext(innerLoopEnd);

        LoopExitNode innerLoopExit460 = graph.add(new LoopExitNode(innerLoopBegin408));
        innerLoopExit460.setStateAfter(graph.addWithoutUnique(new FrameState(BytecodeFrame.UNKNOWN_BCI)));
        innerLoopExitBegin.setNext(innerLoopExit460);

        ValueProxyNode innerExitProxy467 = graph.addWithoutUnique(new ValueProxyNode(innerLoopValuePhi415, innerLoopExit460));
        ValueProxyNode innerExitProxy468 = graph.addWithoutUnique(new ValueProxyNode(innerLoopValuePhi422, innerLoopExit460));

        ValueNode innerExitResult = graph.addOrUniqueWithInputs(ConditionalNode.create(createIntEqualsCondition(graph, innerExitProxy467, 1), innerExitProxy468, exactLongConstant(graph, 0L),
                        NodeView.DEFAULT));
        innerLoopExit460.setNext(graph.add(new ReturnNode(innerExitResult)));

        ValueNode outerLoopIvNext = graph.addOrUniqueWithInputs(AddNode.create(outerLoopIv299, exactIntConstant(graph, 1), NodeView.DEFAULT));
        outerLoopIv299.addInput(exactIntConstant(graph, 0));
        outerLoopIv299.addInput(outerLoopIvNext);

        outerGuardPhi5732.addInput(graph.start());
        outerGuardPhi5732.addInput(duplicationMerge385);

        outerLoopValuePhi5734.addInput(exactIntConstant(graph, 0));
        outerLoopValuePhi5734.addInput(exactIntConstant(graph, 1));

        innerLoopValuePhi415.addInput(postOuterIntPhi6043);
        innerLoopValuePhi415.addInput(exactIntConstant(graph, 1));

        innerLoopValuePhi422.addInput(postOuterLongPhi6044);
        innerLoopValuePhi422.addInput(exactLongConstant(graph, 535L));

        return new ReproGraph(graph, duplicationMerge385, store405, duplicatedEnd402);
    }

    /**
     * Builds a small graph where rewriting one foreign regular-merge input reveals another one in
     * the replacement phi, so the guard-phi normalization must revisit that replacement to reach a
     * fixpoint.
     */
    private StructuredGraph buildNestedRegularMergeGuardInputGraph() throws Exception {
        OptionValues options = getInitialOptions();
        ResolvedJavaMethod method = getResolvedJavaMethod(getClass(), "graphStub");
        DebugContext debug = getDebugContext(options, null, method);
        StructuredGraph graph = new StructuredGraph.Builder(options, debug, AllowAssumptions.YES).method(method).build();

        ParameterNode dividend = graph.addWithoutUnique(new ParameterNode(1, longStamp(HOSTED_WIDE_LONG_LOWER_BOUND, HOSTED_WIDE_LONG_UPPER_BOUND)));

        BeginNode directOwnerPathBegin = graph.add(new BeginNode());
        BeginNode nestedPathBegin = graph.add(new BeginNode());
        IfNode ownerSplit = graph.add(new IfNode(createLongEqualsCondition(graph, dividend, 11L), directOwnerPathBegin, nestedPathBegin, BranchProbabilityData.unknown()));
        graph.start().setNext(ownerSplit);

        EndNode directOwnerPathEnd = graph.add(new EndNode());
        directOwnerPathBegin.setNext(directOwnerPathEnd);

        BeginNode mergeAFirstPathBegin = graph.add(new BeginNode());
        BeginNode mergeASecondSplitBegin = graph.add(new BeginNode());
        IfNode mergeAEntrySplit = graph.add(new IfNode(createLongEqualsCondition(graph, dividend, 13L), mergeAFirstPathBegin, mergeASecondSplitBegin, BranchProbabilityData.unknown()));
        nestedPathBegin.setNext(mergeAEntrySplit);

        EndNode mergeAFirstPathEnd = graph.add(new EndNode());
        mergeAFirstPathBegin.setNext(mergeAFirstPathEnd);

        BeginNode mergeASecondPathBegin = graph.add(new BeginNode());
        BeginNode mergeBPeerPathBegin = graph.add(new BeginNode());
        IfNode mergeASecondSplit = graph.add(new IfNode(createLongEqualsCondition(graph, dividend, 17L), mergeASecondPathBegin, mergeBPeerPathBegin, BranchProbabilityData.unknown()));
        mergeASecondSplitBegin.setNext(mergeASecondSplit);

        EndNode mergeASecondPathEnd = graph.add(new EndNode());
        mergeASecondPathBegin.setNext(mergeASecondPathEnd);

        MergeNode nestedMergeA = graph.add(new MergeNode());
        nestedMergeA.setStateAfter(graph.addWithoutUnique(new FrameState(BytecodeFrame.UNKNOWN_BCI)));
        nestedMergeA.addForwardEnd(mergeAFirstPathEnd);
        nestedMergeA.addForwardEnd(mergeASecondPathEnd);

        EndNode mergeBFromNestedMergeA = graph.add(new EndNode());
        nestedMergeA.setNext(mergeBFromNestedMergeA);

        EndNode mergeBPeerPathEnd = graph.add(new EndNode());
        mergeBPeerPathBegin.setNext(mergeBPeerPathEnd);

        MergeNode nestedMergeB = graph.add(new MergeNode());
        nestedMergeB.setStateAfter(graph.addWithoutUnique(new FrameState(BytecodeFrame.UNKNOWN_BCI)));
        nestedMergeB.addForwardEnd(mergeBFromNestedMergeA);
        nestedMergeB.addForwardEnd(mergeBPeerPathEnd);

        EndNode nestedOwnerPathEnd = graph.add(new EndNode());
        nestedMergeB.setNext(nestedOwnerPathEnd);

        MergeNode ownerMerge = graph.add(new MergeNode());
        ownerMerge.setStateAfter(graph.addWithoutUnique(new FrameState(BytecodeFrame.UNKNOWN_BCI)));
        ownerMerge.addForwardEnd(directOwnerPathEnd);
        ownerMerge.addForwardEnd(nestedOwnerPathEnd);

        GuardPhiNode outerGuardPhi = graph.addWithoutUnique(new GuardPhiNode(ownerMerge));
        outerGuardPhi.addInput(graph.start());
        outerGuardPhi.addInput(nestedMergeB);

        ValueNode guardedResult = graph.addOrUniqueWithInputs(SignedFloatingIntegerRemNode.create(exactLongConstant(graph, 21L), exactLongConstant(graph, 4L), NodeView.DEFAULT, outerGuardPhi, true));
        ownerMerge.setNext(graph.add(new ReturnNode(guardedResult)));

        return graph;
    }

    private static LogicNode createIntEqualsCondition(StructuredGraph graph, ValueNode value, int constant) {
        return graph.addOrUniqueWithInputs(IntegerEqualsNode.create(value, exactIntConstant(graph, constant), NodeView.DEFAULT));
    }

    private static LogicNode createLongEqualsCondition(StructuredGraph graph, ValueNode value, long constant) {
        return graph.addOrUniqueWithInputs(IntegerEqualsNode.create(value, exactLongConstant(graph, constant), NodeView.DEFAULT));
    }

    private static StampPair longStamp(long lowerBound, long upperBound) {
        return StampPair.createSingle(StampFactory.forInteger(JavaKind.Long, lowerBound, upperBound));
    }

    private static ConstantNode exactIntConstant(StructuredGraph graph, int value) {
        return ConstantNode.forPrimitive(StampFactory.forInteger(JavaKind.Int, value, value), JavaConstant.forInt(value), graph);
    }

    private static ConstantNode exactLongConstant(StructuredGraph graph, long value) {
        return ConstantNode.forPrimitive(StampFactory.forInteger(JavaKind.Long, value, value), JavaConstant.forLong(value), graph);
    }
}
