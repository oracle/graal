/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package jdk.graal.compiler.loop.phases;

import static jdk.graal.compiler.core.common.GraalOptions.LoopPredicationMainPath;
import static jdk.graal.compiler.core.common.calc.Condition.EQ;
import static jdk.graal.compiler.core.common.calc.Condition.NE;

import java.util.Optional;

import org.graalvm.collections.EconomicMap;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.cfg.AbstractControlFlowGraph;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.GuardedValueNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.IntegerBelowNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.extended.AnchoringNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.extended.MultiGuardNode;
import jdk.graal.compiler.nodes.loop.CountedLoopInfo;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.loop.MathUtil;
import jdk.graal.compiler.phases.Speculative;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.PostRunCanonicalizationPhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.serviceprovider.SpeculationReasonGroup;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.SpeculationLog;

public class LoopPredicationPhase extends PostRunCanonicalizationPhase<MidTierContext> implements Speculative {
    private static final SpeculationReasonGroup LOOP_PREDICATION = new SpeculationReasonGroup("Loop Predication", BytecodePosition.class);

    public LoopPredicationPhase(CanonicalizerPhase canonicalizer) {
        super(canonicalizer);
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        super.notApplicableTo(graphState),
                        NotApplicable.withoutSpeculationLog(this, graphState),
                        NotApplicable.when(!graphState.getGuardsStage().allowsFloatingGuards(), "Floating guards must be allowed."));
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, MidTierContext context) {
        DebugContext debug = graph.getDebug();
        final SpeculationLog speculationLog = graph.getSpeculationLog();
        if (graph.hasLoops() && context.getOptimisticOptimizations().useLoopLimitChecks(graph.getOptions())) {
            LoopsData data = context.getLoopsDataProvider().getLoopsData(graph);
            final ControlFlowGraph cfg = data.getCFG();
            try (DebugContext.Scope s = debug.scope("predication", cfg)) {
                for (Loop loop : data.loops()) {
                    // Only inner most loops.
                    if (!loop.loop().getChildren().isEmpty()) {
                        continue;
                    }
                    if (!loop.detectCounted()) {
                        continue;
                    }
                    final FrameState state = loop.loopBegin().stateAfter();
                    final BytecodePosition pos = new BytecodePosition(null, state.getMethod(), state.bci);
                    SpeculationLog.SpeculationReason reason = LOOP_PREDICATION.createSpeculationReason(pos);
                    if (speculationLog.maySpeculate(reason)) {
                        final CountedLoopInfo counted = loop.counted();
                        final InductionVariable counter = counted.getLimitCheckedIV();
                        final Condition condition = ((CompareNode) counted.getLimitTest().condition()).condition().asCondition();
                        final boolean inverted = loop.counted().isInverted();
                        if ((((IntegerStamp) counter.valueNode().stamp(NodeView.DEFAULT)).getBits() == 32) && !counted.isUnsignedCheck() &&
                                        ((condition != NE && condition != EQ) || (counter.isConstantStride() && Loop.absStrideIsOne(counter))) &&
                                        (loop.loopBegin().isMainLoop() || loop.loopBegin().isSimpleLoop())) {
                            NodeIterable<GuardNode> guards = loop.whole().nodes().filter(GuardNode.class);
                            if (LoopPredicationMainPath.getValue(graph.getOptions())) {
                                // C2 only applies loop predication to guards dominating the
                                // backedge.
                                // The following logic emulates that behavior.
                                final NodeIterable<LoopEndNode> loopEndNodes = loop.loopBegin().loopEnds();
                                final HIRBlock end = data.getCFG().commonDominatorFor(loopEndNodes);
                                guards = guards.filter(guard -> {
                                    final ValueNode anchor = ((GuardNode) guard).getAnchor().asNode();
                                    final HIRBlock anchorBlock = data.getCFG().getNodeToBlock().get(anchor);
                                    return AbstractControlFlowGraph.dominates(anchorBlock, end);
                                });
                            }
                            final AbstractBeginNode body = loop.counted().getBody();
                            final HIRBlock bodyBlock = cfg.getNodeToBlock().get(body);

                            for (GuardNode guard : guards) {
                                final AnchoringNode anchor = guard.getAnchor();
                                final HIRBlock anchorBlock = cfg.getNodeToBlock().get(anchor.asNode());
                                // for inverted loop the anchor can dominate the body
                                if (!inverted) {
                                    if (!AbstractControlFlowGraph.dominates(bodyBlock, anchorBlock)) {
                                        continue;
                                    }
                                }
                                processGuard(loop, guard);
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                throw debug.handle(t);
            }
        }
    }

    // Create a range check predicate for
    //
    // for (i = init; i < limit; i += stride) {
    // a[scale*i+offset]
    // }
    private static void processGuard(Loop loop, GuardNode guard) {
        final LogicNode condition = guard.getCondition();
        if (!(condition instanceof IntegerBelowNode) || guard.isNegated()) {
            return;
        }

        final IntegerBelowNode rangeCheck = (IntegerBelowNode) condition;
        final ValueNode range = rangeCheck.getY();
        if (!loop.isOutsideLoop(range) || ((IntegerStamp) range.stamp(NodeView.DEFAULT)).lowerBound() < 0) {
            return;
        }

        final EconomicMap<Node, InductionVariable> inductionVariables = loop.getInductionVariables();

        final ValueNode x = rangeCheck.getX();
        if (!inductionVariables.containsKey(x)) {
            return;
        }

        final StructuredGraph graph = guard.graph();

        final InductionVariable iv = inductionVariables.get(x);

        Long scale = null;

        final InductionVariable counter = loop.counted().getLimitCheckedIV();
        if (iv.isConstantScale(counter)) {
            scale = iv.constantScale(counter);
        }

        ValueNode offset = null;

        if (iv.offsetIsZero(counter)) {
            offset = graph.unique(ConstantNode.forInt(0));
        } else {
            offset = iv.offsetNode(counter);
        }

        if (offset == null || scale == null || !loop.isOutsideLoop(offset)) {
            return;
        }

        long scaleCon = scale;

        replaceGuardNode(loop, guard, range, graph, scaleCon, offset);
    }

    private static void replaceGuardNode(Loop loop, GuardNode guard, ValueNode range, StructuredGraph graph, long scaleCon, ValueNode offset) {
        final InductionVariable counter = loop.counted().getLimitCheckedIV();
        ValueNode rangeLong = IntegerConvertNode.convert(range, IntegerStamp.create(64), graph, NodeView.DEFAULT);

        final GuardingNode overFlowGuard = loop.counted().createOverFlowGuard();
        ValueNode extremumNode = counter.extremumNode(false, IntegerStamp.create(64));
        assert overFlowGuard != null || loop.counted().counterNeverOverflows();
        if (overFlowGuard != null) {
            extremumNode = graph.unique(new GuardedValueNode(extremumNode, overFlowGuard));
        }
        final ValueNode upperNode = MathUtil.add(graph, MathUtil.mul(graph, extremumNode, ConstantNode.forLong(scaleCon, graph)),
                        IntegerConvertNode.convert(offset, IntegerStamp.create(64), graph, NodeView.DEFAULT));
        final LogicNode upperCond = IntegerBelowNode.create(upperNode, rangeLong, NodeView.DEFAULT);

        final ValueNode initNode = IntegerConvertNode.convert(loop.counted().getBodyIVStart(), IntegerStamp.create(64), graph, NodeView.DEFAULT);
        final ValueNode lowerNode = MathUtil.add(graph, MathUtil.mul(graph, initNode, ConstantNode.forLong(scaleCon, graph)),
                        IntegerConvertNode.convert(offset, IntegerStamp.create(64), graph, NodeView.DEFAULT));
        final LogicNode lowerCond = IntegerBelowNode.create(lowerNode, rangeLong, NodeView.DEFAULT);

        final FrameState state = loop.loopBegin().stateAfter();
        final BytecodePosition pos = new BytecodePosition(null, state.getMethod(), state.bci);
        SpeculationLog.SpeculationReason reason = LOOP_PREDICATION.createSpeculationReason(pos);
        SpeculationLog.Speculation speculation = graph.getSpeculationLog().speculate(reason);
        final AbstractBeginNode anchor = AbstractBeginNode.prevBegin(loop.entryPoint());

        final GuardNode upperGuard = graph.addOrUniqueWithInputs(new GuardNode(upperCond, anchor, guard.getReason(), guard.getAction(), guard.isNegated(), speculation, null));
        final GuardNode lowerGuard = graph.addOrUniqueWithInputs(new GuardNode(lowerCond, anchor, guard.getReason(), guard.getAction(), guard.isNegated(), speculation, null));

        final GuardingNode combinedGuard = MultiGuardNode.combine(lowerGuard, upperGuard);
        guard.replaceAtUsagesAndDelete(combinedGuard.asNode());
        graph.getOptimizationLog().report(LoopPredicationPhase.class, "GuardReplacement", guard);
    }

    @Override
    public float codeSizeIncrease() {
        return 2;
    }
}
