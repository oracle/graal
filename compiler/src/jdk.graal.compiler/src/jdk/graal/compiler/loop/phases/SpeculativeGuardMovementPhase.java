/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumSet;
import java.util.Optional;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Graph.NodeEventScope;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.GuardedValueNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ProfileData.BranchProbabilityData;
import jdk.graal.compiler.nodes.ProfileData.ProfileSource;
import jdk.graal.compiler.nodes.ShortCircuitOrNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.IntegerBelowNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.calc.IntegerDivRemNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.extended.AnchoringNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.extended.MultiGuardNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.loop.CountedLoopInfo;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariable.Direction;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.phases.FloatingGuardPhase;
import jdk.graal.compiler.phases.Speculative;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.PostRunCanonicalizationPhase;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.serviceprovider.SpeculationReasonGroup;
import jdk.vm.ci.meta.DefaultProfilingInfo;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;

/**
 * Tries to move guards within a branch inside a loop to a block outside a loop, speculating that
 * there is no correlation between the condition under which that branch is reached and the
 * condition of the guard.
 *
 * This is best explained with an example:
 *
 * <pre>
 * public static int sumInts(int[] ints, Integer negAdjust) {
 *     int sum = 0;
 *     for (int i = 0; i &lt; ints.length; i++) {
 *         if (ints[i] &lt; 0) {
 *             sum += negAdjust; // guard: negAdjust != null
 *         }
 *         sum += ints[i];
 *     }
 *     return sum;
 * }
 * </pre>
 *
 * It is advantageous to hoist the guard that null checks {@code negAdjust} outside the loop since
 * the guard is loop invariant. However, doing so blindly would miss the fact that the null check is
 * only performed when {@code ints} contains a negative number. That is, the execution of the null
 * check is correlated with a condition in the loop (namely {@code ints[i] < 0}). If the guard is
 * hoisted and the method is called with {@code negAdjust == null} and {@code ints} only containing
 * positive numbers, then the method will deoptimize unnecessarily (since an exception will not be
 * thrown when executing in the interpreter). To avoid such unnecessary deoptimizations, a
 * speculation log entry is associated with the hoisted guard such that when it fails, the same
 * guard hoisting will not be performed in a subsequent compilation.
 */
public class SpeculativeGuardMovementPhase extends PostRunCanonicalizationPhase<MidTierContext> implements FloatingGuardPhase, Speculative {

    private final boolean ignoreFrequency;
    private final boolean requireSpeculationLog;

    public SpeculativeGuardMovementPhase(CanonicalizerPhase canonicalizer) {
        super(canonicalizer);
        this.ignoreFrequency = false;
        this.requireSpeculationLog = true;
    }

    public SpeculativeGuardMovementPhase(CanonicalizerPhase canonicalizer, boolean ignoreFrequency, boolean requireSpeculationLog) {
        super(canonicalizer);
        this.ignoreFrequency = ignoreFrequency;
        this.requireSpeculationLog = requireSpeculationLog;
    }

    @Override
    public float codeSizeIncrease() {
        return 2.0f;
    }

    /**
     * Maximum iterations for speculative guard movement. Certain guard patterns may require
     * speculative guard movement to first move guard a to make guard b also loop
     * invariant/participate in an induction variable. This may trigger with pi nodes and guards.
     */
    private static final int MAX_ITERATIONS = 3;

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        super.notApplicableTo(graphState),
                        NotApplicable.ifApplied(this, StageFlag.GUARD_MOVEMENT, graphState),
                        NotApplicable.when(!graphState.getGuardsStage().allowsFloatingGuards(), "Floating guards must be allowed"));
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, MidTierContext context) {
        EconomicSetNodeEventListener change = new EconomicSetNodeEventListener(EnumSet.of(Graph.NodeEvent.INPUT_CHANGED, Graph.NodeEvent.CONTROL_FLOW_CHANGED));
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            boolean iterate = false;
            try (NodeEventScope news = graph.trackNodeEvents(change)) {
                if (graph.getDebug().areCountersEnabled()) {
                    DebugContext.counter("SpeculativeGuardMovement_Iteration" + i).increment(graph.getDebug());
                }
                LoopsData loops = context.getLoopsDataProvider().getLoopsData(graph);
                loops.detectCountedLoops();
                iterate = performSpeculativeGuardMovement(context, graph, loops, ignoreFrequency, requireSpeculationLog);
            }
            if (change.getNodes().isEmpty() || !iterate) {
                break;
            }
            change.getNodes().clear();
        }
    }

    @Override
    public void updateGraphState(GraphState graphState) {
        super.updateGraphState(graphState);
        graphState.setAfterStage(StageFlag.GUARD_MOVEMENT);
    }

    public static boolean performSpeculativeGuardMovement(MidTierContext context, StructuredGraph graph, LoopsData loops) {
        return performSpeculativeGuardMovement(context, graph, loops, null, false, true);
    }

    public static boolean performSpeculativeGuardMovement(MidTierContext context, StructuredGraph graph, LoopsData loops, boolean ignoreFrequency, boolean requireSpeculationLog) {
        return performSpeculativeGuardMovement(context, graph, loops, null, ignoreFrequency, requireSpeculationLog);
    }

    public static boolean performSpeculativeGuardMovement(MidTierContext context, StructuredGraph graph, LoopsData loops, NodeBitMap toProcess) {
        return performSpeculativeGuardMovement(context, graph, loops, toProcess, false, true);
    }

    public static boolean performSpeculativeGuardMovement(MidTierContext context, StructuredGraph graph, LoopsData loops, NodeBitMap toProcess, boolean ignoreFrequency,
                    boolean requireSpeculationLog) {
        SpeculativeGuardMovement spec = new SpeculativeGuardMovement(loops, graph.createNodeMap(), graph, context.getProfilingInfo(), graph.getSpeculationLog(), toProcess,
                        ignoreFrequency, requireSpeculationLog);
        spec.run();
        return spec.iterate;
    }

    private static class SpeculativeGuardMovement implements Runnable {

        private final boolean ignoreFrequency;
        private final LoopsData loops;
        private final NodeMap<HIRBlock> earliestCache;
        private final StructuredGraph graph;
        private final ProfilingInfo profilingInfo;
        private final SpeculationLog speculationLog;
        boolean iterate;
        private final NodeBitMap toProcess;

        SpeculativeGuardMovement(LoopsData loops, NodeMap<HIRBlock> earliestCache, StructuredGraph graph, ProfilingInfo profilingInfo, SpeculationLog speculationLog, NodeBitMap toProcess,
                        boolean ignoreFrequency, boolean requireSpeculationLog) {
            this.loops = loops;
            this.earliestCache = earliestCache;
            this.graph = graph;
            this.profilingInfo = profilingInfo;
            GraalError.guarantee(requireSpeculationLog ? speculationLog != null : true, "Graph has no speculation log attached: %s", graph);
            this.speculationLog = speculationLog;
            this.toProcess = toProcess;
            this.ignoreFrequency = ignoreFrequency;
        }

        @Override
        public void run() {
            for (GuardNode guard : graph.getNodes(GuardNode.TYPE)) {
                if (toProcess == null || (!toProcess.isNew(guard) && toProcess.contains(guard))) {
                    HIRBlock anchorBlock = loops.getCFG().blockFor(guard.getAnchor().asNode());
                    if (exitsLoop(anchorBlock, earliestBlock(guard))) {
                        iterate = true;
                    }
                    graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After processing guard %s", guard);
                }
            }
        }

        private static boolean exitsLoop(HIRBlock earliestOld, HIRBlock earliestNew) {
            if (earliestOld == null) {
                return false;
            }
            return earliestOld.getLoopDepth() > earliestNew.getLoopDepth();
        }

        /**
         * Determines the earliest block in which the given node can be scheduled.
         */
        private HIRBlock earliestBlock(Node node) {
            ControlFlowGraph cfg = loops.getCFG();
            HIRBlock earliest = earliestCache.getAndGrow(node);
            if (earliest != null) {
                return earliest;
            }
            earliest = cfg.getNodeToBlock().isNew(node) ? null : cfg.getNodeToBlock().get(node);
            if (earliest == null) {
                if (node instanceof IntegerDivRemNode) {
                    earliest = earliestBlock(node.predecessor());
                } else if (node instanceof PhiNode) {
                    PhiNode phi = (PhiNode) node;
                    earliest = earliestBlock(phi.merge());
                }
            }
            if (earliest != null) {
                earliestCache.setAndGrow(node, earliest);
                return earliest;
            }

            if (node instanceof GuardNode) {
                GuardNode guard = (GuardNode) node;
                LogicNode condition = guard.getCondition();

                CFGLoop<HIRBlock> forcedHoisting = null;
                if (condition instanceof IntegerLessThanNode || condition instanceof IntegerBelowNode) {
                    forcedHoisting = tryOptimizeCompare(guard, (CompareNode) condition);
                } else if (condition instanceof InstanceOfNode) {
                    forcedHoisting = tryOptimizeInstanceOf(guard, (InstanceOfNode) condition);
                }
                earliest = earliestBlockForGuard(guard, forcedHoisting);
            } else {
                earliest = computeEarliestBlock(node);
            }
            earliestCache.setAndGrow(node, earliest);
            return earliest;
        }

        private HIRBlock computeEarliestBlock(Node node) {
            /*
             * All inputs must be in a dominating block, otherwise the graph cannot be scheduled.
             * This implies that the inputs' blocks have a total ordering via their dominance
             * relation. So in order to find the earliest block placement for this node we need to
             * find the input block that is dominated by all other input blocks.
             *
             * While iterating over the inputs a set of dominator blocks of the current earliest
             * placement is maintained. When the block of an input is not within this set, it
             * becomes the current earliest placement and the list of dominator blocks is updated.
             */
            ControlFlowGraph cfg = loops.getCFG();
            assert node.predecessor() == null;

            HIRBlock earliest = null;
            for (Node input : node.inputs().snapshot()) {
                if (input != null) {
                    assert input instanceof ValueNode : Assertions.errorMessage(input);
                    HIRBlock inputEarliest;
                    if (input instanceof WithExceptionNode) {
                        inputEarliest = cfg.getNodeToBlock().get(((WithExceptionNode) input).next());
                    } else {
                        inputEarliest = earliestBlock(input);
                    }
                    earliest = (earliest == null || earliest.strictlyDominates(inputEarliest)) ? inputEarliest : earliest;
                }
            }
            if (earliest == null) {
                earliest = cfg.getStartBlock();
            }
            return earliest;
        }

        private CFGLoop<HIRBlock> tryOptimizeCompare(GuardNode guard, CompareNode compare) {
            assert compare instanceof IntegerLessThanNode || compare instanceof IntegerBelowNode : Assertions.errorMessage(compare);
            assert !compare.usages().filter(GuardNode.class).isEmpty() : Assertions.errorMessage(compare, compare.usages());
            InductionVariable ivX = loops.getInductionVariable(compare.getX());
            InductionVariable ivY = loops.getInductionVariable(compare.getY());
            if (ivX == null && ivY == null) {
                return null;
            }

            InductionVariable iv;
            InductionVariable otherIV;
            ValueNode bound;
            boolean mirrored;
            if (ivX == null || (ivY != null && ivY.getLoop().getCFGLoop().getDepth() > ivX.getLoop().getCFGLoop().getDepth())) {
                iv = ivY;
                otherIV = ivX;
                bound = compare.getX();
                mirrored = true;
            } else {
                iv = ivX;
                otherIV = ivY;
                bound = compare.getY();
                mirrored = false;
            }

            if (tryOptimizeCompare(compare, iv, bound, mirrored, guard)) {
                return iv.getLoop().getCFGLoop();
            }
            if (otherIV != null) {
                if (tryOptimizeCompare(compare, otherIV, iv.valueNode(), !mirrored, guard)) {
                    return otherIV.getLoop().getCFGLoop();
                }
            }

            return null;
        }

        private boolean tryOptimizeCompare(CompareNode compare, InductionVariable iv, ValueNode bound, boolean mirrored, GuardNode guard) {
            OptimizedCompareTests tests = shouldOptimizeCompare(compare, iv, bound, guard, mirrored);
            if (tests != null) {
                optimizeCompare(compare, iv, guard, tests);
                return true;
            }
            return false;
        }

        @SuppressWarnings("try")
        private void optimizeCompare(CompareNode compare, InductionVariable iv, GuardNode guard, OptimizedCompareTests tests) {
            CountedLoopInfo countedLoop = iv.getLoop().counted();
            try (DebugCloseable position = compare.withNodeSourcePosition()) {
                LogicNode newCompare = ShortCircuitOrNode.and(tests.extremumTest, guard.isNegated(), tests.initTest, guard.isNegated(), BranchProbabilityData.unknown());
                /*
                 * the fact that the guard was negated was integrated in the ShortCircuitOr so it
                 * needs to be reset here
                 */
                if (guard.isNegated()) {
                    guard.negate();
                }

                boolean createLoopEnteredCheck = true;
                if (isInverted(iv.getLoop())) {
                    createLoopEnteredCheck = false;
                }
                if (createLoopEnteredCheck) {
                    newCompare = createLoopEnterCheck(countedLoop, newCompare);
                }

                guard.replaceFirstInput(compare, newCompare);
                GuardingNode loopBodyGuard = MultiGuardNode.combine(guard, countedLoop.getBody());
                for (ValueNode usage : guard.usages().filter(ValueNode.class).snapshot()) {
                    if (usage != loopBodyGuard) {
                        usage.replaceFirstInput(guard, loopBodyGuard.asNode());
                    }
                }
            }
            graph.getOptimizationLog().report(SpeculativeGuardMovementPhase.class, "CompareOptimization", compare);
        }

        private OptimizedCompareTests computeNewCompareGuards(CompareNode compare, InductionVariable iv, ValueNode bound, boolean mirrored, GuardingNode overflowGuard) {
            return computeNewCompareGuards(compare, iv, bound, mirrored, overflowGuard, null);
        }

        private OptimizedCompareTests computeNewCompareGuards(CompareNode compare, InductionVariable iv, ValueNode bound, boolean mirrored, GuardingNode overflowGuard, ValueNode maxTripCountNode) {
            final boolean zeroExtendBound = compare.condition().isUnsigned();
            ValueNode longBound = IntegerConvertNode.convert(bound, StampFactory.forKind(JavaKind.Long), zeroExtendBound, graph, NodeView.DEFAULT);
            ValueNode extremum = maxTripCountNode == null ? iv.extremumNode(true, StampFactory.forKind(JavaKind.Long)) : iv.extremumNode(true, StampFactory.forKind(JavaKind.Long), maxTripCountNode);
            ValueNode guardedExtremum = graph.addOrUniqueWithInputs(GuardedValueNode.create(extremum, overflowGuard));
            // guardedExtremum |<| longBound && iv.initNode() |<| bound
            ValueNode y1 = longBound;
            ValueNode y2 = bound;
            ValueNode x1 = guardedExtremum;
            ValueNode x2 = iv.initNode();
            if (mirrored) {
                // longBound |<| guardedExtremum && bound |<| iv.initNode()
                x1 = longBound;
                y1 = guardedExtremum;
                x2 = bound;
                y2 = iv.initNode();
            }
            LogicNode extremumTest;
            LogicNode initTest;
            if (compare instanceof IntegerBelowNode) {
                extremumTest = graph.addOrUniqueWithInputs(IntegerBelowNode.create(x1, y1, NodeView.DEFAULT));
                initTest = graph.addOrUniqueWithInputs(IntegerBelowNode.create(x2, y2, NodeView.DEFAULT));
            } else {
                assert compare instanceof IntegerLessThanNode : Assertions.errorMessage(compare);
                extremumTest = graph.addOrUniqueWithInputs(IntegerLessThanNode.create(x1, y1, NodeView.DEFAULT));
                initTest = graph.addOrUniqueWithInputs(IntegerLessThanNode.create(x2, y2, NodeView.DEFAULT));
            }
            if (graph.getDebug().isDumpEnabledForMethod()) {
                if (mirrored) {
                    graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Speculative guard movement: longBound(%s) |<| guardedExtremum(%s) && bound(%s) |<| iv.initNode()(%s) =%s && %s", x1,
                                    y1, x2, y2, extremumTest, initTest);
                } else {
                    graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Speculative guard movement: guardedExtremum(%s) |<| longBound(%s) && iv.initNode()(%s) |<| bound(%s)=%s && %s", x1,
                                    y1, x2, y2, extremumTest, initTest);
                }
            }
            return new OptimizedCompareTests(initTest, extremumTest);
        }

        /**
         * Class representing the result of optimizing a comparison.
         */
        private static class OptimizedCompareTests {
            /**
             * Optimized compare test: init < bound.
             *
             * if mirrored: bound < init
             */
            LogicNode initTest;
            /**
             * Optimized compare test: extremum < bound.
             *
             * if mirrored: bound < extremum
             */
            LogicNode extremumTest;

            OptimizedCompareTests(LogicNode initTest, LogicNode extremumTest) {
                this.initTest = initTest;
                this.extremumTest = extremumTest;
            }

            private static boolean isLogicConstant(ValueNode v) {
                return v instanceof LogicConstantNode;
            }

            private boolean constantInitTestOrValue(boolean value) {
                if (initTestIsConstant()) {
                    return initTestAsConstant();
                }
                return value;
            }

            private boolean constantExtremumTestOrValue(boolean value) {
                if (extremumTestIsConstant()) {
                    return extremumTestAsConstant();
                }
                return value;
            }

            private boolean initTestAsConstant() {
                assert isLogicConstant(initTest);
                return ((LogicConstantNode) initTest).getValue();
            }

            private boolean extremumTestAsConstant() {
                assert isLogicConstant(extremumTest);
                return ((LogicConstantNode) extremumTest).getValue();
            }

            private boolean initTestIsConstant() {
                return isLogicConstant(initTest);
            }

            private boolean extremumTestIsConstant() {
                return isLogicConstant(extremumTest);
            }
        }

        private LogicNode createLoopEnterCheck(CountedLoopInfo countedLoop, LogicNode newCompare) {
            ValueNode limit = countedLoop.getLimit();
            ValueNode start = countedLoop.getBodyIVStart();
            Direction direction = countedLoop.getDirection();
            boolean limitIncluded = countedLoop.isLimitIncluded();
            ValueNode x;
            ValueNode y;
            if (limitIncluded) {
                if (direction == Direction.Up) {
                    // limit < start || newCompare
                    x = limit;
                    y = start;
                } else {
                    assert direction == Direction.Down : direction;
                    // start < limit || newCompare
                    x = start;
                    y = limit;
                }
            } else {
                if (direction == Direction.Up) {
                    // limit <= start || newCompare
                    x = start;
                    y = limit;
                } else {
                    assert direction == Direction.Down : direction;
                    // start <= limit || newCompare
                    x = limit;
                    y = start;
                }
            }
            LogicNode compare = countedLoop.getCounterIntegerHelper().createCompareNode(x, y, NodeView.DEFAULT);
            return graph.addOrUniqueWithInputs(ShortCircuitOrNode.create(compare, !limitIncluded, newCompare, false, BranchProbabilityData.unknown()));
        }

        private static boolean shouldHoistBasedOnFrequency(HIRBlock proposedNewAnchor, HIRBlock anchorBlock) {
            return shouldHoistBasedOnFrequency(proposedNewAnchor.getRelativeFrequency(), anchorBlock.getRelativeFrequency());
        }

        private static boolean shouldHoistBasedOnFrequency(double proposedNewAnchorFrequency, double anchorFrequency) {
            return SchedulePhase.Instance.compareRelativeFrequencies(proposedNewAnchorFrequency, anchorFrequency) <= 0;
        }

        private OptimizedCompareTests shouldOptimizeCompare(CompareNode compare, InductionVariable iv, ValueNode bound, GuardNode guard, boolean mirrored) {
            DebugContext debug = guard.getDebug();
            if (!iv.getLoop().isCounted()) {
                debug.log("shouldOptimizeCompare(%s):not a counted loop", guard);
                return null;
            }

            Loop loopEx = iv.getLoop();
            CFGLoop<HIRBlock> ivLoop = loopEx.getCFGLoop();
            HIRBlock guardAnchorBlock = earliestBlock(guard.getAnchor().asNode());
            if (isInverted(iv.getLoop())) {
                /*
                 * <Special case inverted loops>
                 *
                 * With loop inversion it may be very likely that the guard's anchor is already
                 * outside the loop (since there is no dominating condition in the loop when
                 * lowering the original node to a guard). Thus, it can be that the guard anchor
                 * block is outside the loop while the condition is still rooted inside the loop. We
                 * need to account for this case.
                 */
                if (!earliestBlock(iv.getLoop().counted().getBody()).dominates(guardAnchorBlock)) {
                    // determine if the condition is inside the loop
                    if (!iv.getLoop().whole().contains(guard.getCondition())) {
                        return null;
                    }
                }
            } else {
                if (!earliestBlock(iv.getLoop().counted().getBody()).dominates(guardAnchorBlock)) {
                    debug.log("shouldOptimizeCompare(%s):guard is not inside loop", guard);
                    return null; // guard must come from inside the loop
                }
            }

            if (!ivLoop.getBlocks().contains(earliestBlock(iv.valueNode()))) {
                debug.log("shouldOptimizeCompare(%s):iv is not inside loop", guard);
                // These strange IVs are created because we don't really know if Guards are inside a
                // loop. See LoopFragment.markFloating
                // Such IVs can not be re-written to anything that can be hoisted.
                return null;
            }

            // Predecessor block IDs are always before successor block IDs
            if (earliestBlock(bound).getId() >= ivLoop.getHeader().getId()) {
                debug.log("shouldOptimizeCompare(%s):bound is not schedulable above the IV loop", guard);
                return null; // the bound must be loop invariant and schedulable above the loop.
            }

            CountedLoopInfo countedLoop = loopEx.counted();

            if (!(profilingInfo instanceof DefaultProfilingInfo)) {
                double loopFreqThreshold = 1;
                if (!(iv.initNode() instanceof ConstantNode && bound instanceof ConstantNode)) {
                    // additional compare and short-circuit-or introduced in optimizeCompare
                    loopFreqThreshold += 2;
                }
                if (!isInverted(loopEx)) {
                    if (!(countedLoop.getBodyIVStart() instanceof ConstantNode && countedLoop.getLimit() instanceof ConstantNode)) {
                        // additional compare and short-circuit-or for loop enter check
                        loopFreqThreshold++;
                    }
                }
                if (!ignoreFrequency && ProfileSource.isTrusted(loopEx.localFrequencySource()) &&
                                loopEx.localLoopFrequency() < loopFreqThreshold) {
                    debug.log("shouldOptimizeCompare(%s):loop frequency too low.", guard);
                    // loop frequency is too low -- the complexity introduced by hoisting this guard
                    // will not pay off.
                    return null;
                }
            }

            CFGLoop<HIRBlock> l = guardAnchorBlock.getLoop();
            if (isInverted(loopEx)) {
                // guard is anchored outside the loop but the condition might still be in the loop
                l = iv.getLoop().getCFGLoop();
            }
            if (l == null) {
                return null;
            }
            assert l != null : "Loop for guard anchor block must not be null:" + guardAnchorBlock.getBeginNode() + " loop " + iv.getLoop() + " inverted?" +
                            isInverted(iv.getLoop());
            do {
                if (!allowsSpeculativeGuardMovement(guard.getReason(), (LoopBeginNode) l.getHeader().getBeginNode(), true)) {
                    debug.log("shouldOptimizeCompare(%s):The guard would not hoist", guard);
                    return null; // the guard would not hoist, don't hoist the compare
                }
                l = l.getParent();
            } while (l != ivLoop.getParent() && l != null);

            /*
             * See above <Special case inverted loops>
             *
             * If the guard anchor is already outside the loop, the condition may still be inside
             * the loop, thus we still want to try hoisting the guard.
             */
            if (!isInverted(iv.getLoop()) && !guardAnchorBlock.dominates(iv.getLoop().getCFGLoop().getHeader())) {
                if (!ignoreFrequency && !shouldHoistBasedOnFrequency(ivLoop.getHeader().getDominator(), guardAnchorBlock)) {
                    debug.log("hoisting is not beneficial based on frequency", guard);
                    return null;
                }
            }

            Stamp boundStamp = bound.stamp(NodeView.DEFAULT);
            Stamp ivStamp = iv.valueNode().stamp(NodeView.DEFAULT);
            boolean fitsInInt = false;
            if (boundStamp instanceof IntegerStamp && ivStamp instanceof IntegerStamp) {
                IntegerStamp integerBoundStamp = (IntegerStamp) boundStamp;
                IntegerStamp integerIvStamp = (IntegerStamp) ivStamp;
                if (fitsIn32Bit(integerBoundStamp) && fitsIn32Bit(integerIvStamp)) {
                    fitsInInt = true;
                }
            }
            if (fitsInInt) {
                CountedLoopInfo countedLoopInfo = iv.getLoop().counted();
                GuardingNode overflowGuard = countedLoopInfo.getOverFlowGuard();
                if (overflowGuard == null && !countedLoopInfo.counterNeverOverflows()) {
                    if (graph.getGuardsStage().allowsFloatingGuards()) {
                        overflowGuard = iv.getLoop().counted().createOverFlowGuard();
                    } else {
                        debug.log("shouldOptimizeCompare(%s): abort, cannot create overflow guard", compare);
                        return null;
                    }
                }
                OptimizedCompareTests tests = computeNewCompareGuards(compare, iv, bound, mirrored, overflowGuard);
                /**
                 * Determine if, based on loop bounds and guard bounds the moved guard is always
                 * false, i.e., deopts unconditionally. In such cases, avoid optimizing the compare.
                 *
                 * Note: this typically happens with multiple loop exits, i.e., a loop condition
                 * that is not visible in the counted condition of the loop.
                 */
                if (optimizedCompareUnconditionalDeopt(guard, tests)) {
                    debug.log("shouldOptimizeCompare(%s): guard would immediately deopt", compare);
                    return null;
                }
                /**
                 * Special case outer loop phis: for inner loop phis initialized with outer loop
                 * phis we no longer "see" the original phi init value since we only see the outer
                 * loop phi of the current iteration. We want to avoid moving guards that will fail
                 * on the first iteration of the outer loop based on the bound of the guard and the
                 * loop. Thus, we create a new IV for the first iteration of the outer loop's values
                 * in the inner loop and check if we statically fold to a deopting scenario, in
                 * which case the guard would anyway always fail at runtime.
                 */
                if (iv.getLoop().getCFGLoop().getDepth() > 1 && iv.getLoop().loopBegin().loopExits().count() > 1) {
                    InductionVariable currentIv = iv;
                    Loop currentLoop = iv.getLoop();
                    /*
                     * Since we are calculating the inner loops max trip count based on the outer
                     * loop IV we also have to compute a different max trip count node for this
                     * purpose.
                     */
                    InductionVariable countedLoopInitModifiedIV = iv.getLoop().counted().getLimitCheckedIV();
                    boolean initIsParentIV = false;
                    boolean initIsParentPhi = false;
                    ValueNode currentRootInit = currentIv.getRootIV().initNode();
                    while (currentLoop.parent() != null &&
                                    // init is outer IV node
                                    ((initIsParentIV = currentLoop.parent().getInductionVariables().containsKey(currentRootInit)) ||
                                                    // init is outer phi but not IV
                                                    (initIsParentPhi = currentLoop.parent().loopBegin().isPhiAtMerge(currentRootInit)))) {
                        if (initIsParentIV) {
                            InductionVariable parentIv = currentLoop.parent().getInductionVariables().get(currentRootInit);
                            currentIv = currentIv.duplicateWithNewInit(parentIv.entryTripValue());
                        } else if (initIsParentPhi) {
                            currentIv = currentIv.duplicateWithNewInit(((PhiNode) currentRootInit).valueAt(0));
                        } else {
                            throw GraalError.shouldNotReachHere("Must have never entered loop"); // ExcludeFromJacocoGeneratedReport
                        }
                        if (currentLoop.parent().getInductionVariables().containsKey(countedLoopInitModifiedIV.getRootIV().initNode())) {
                            InductionVariable parentIVBodyRef = currentLoop.parent().getInductionVariables().get(countedLoopInitModifiedIV.getRootIV().initNode());
                            countedLoopInitModifiedIV = countedLoopInitModifiedIV.duplicateWithNewInit(parentIVBodyRef.entryTripValue());
                        }
                        currentRootInit = currentIv.getRootIV().initNode();
                        currentLoop = currentLoop.parent();
                    }
                    if (currentLoop != iv.getLoop()) {
                        InductionVariable duplicateOriginalLoopIV = currentIv;
                        ValueNode newBodyIVInit = countedLoopInitModifiedIV.initNode();
                        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "SpeculativeGuardMovement: new if for outer loop check %s %s", duplicateOriginalLoopIV.valueNode(),
                                        duplicateOriginalLoopIV);
                        CountedLoopInfo thisLoopCounted = iv.getLoop().counted();
                        ValueNode outerLoopInitBasedMaxTripCount = thisLoopCounted.maxTripCountNode(true, thisLoopCounted.getCounterIntegerHelper(), newBodyIVInit,
                                        thisLoopCounted.getTripCountLimit());
                        OptimizedCompareTests testStripMinedIV = computeNewCompareGuards(compare, duplicateOriginalLoopIV, bound, mirrored, iv.getLoop().counted().getOverFlowGuard(),
                                        outerLoopInitBasedMaxTripCount);
                        if (optimizedCompareUnconditionalDeopt(guard, testStripMinedIV)) {
                            debug.log("shouldOptimizeCompare(%s): guard would immediately deopt in loop", compare);
                            return null;
                        }
                    }
                }
                return tests;
            } else {
                debug.log("shouldOptimizeCompare(%s): bound or iv does not fit in int", guard);
                return null; // only ints are supported (so that the overflow fits in longs)
            }

        }

        /*
         * We will create a guard test1 && test2, this means if one of the two is a boolean that is
         * negative the result is negative and then, depending on the negated flag the guard will
         * fail or not.
         */
        private static boolean optimizedCompareUnconditionalDeopt(GuardNode guard, OptimizedCompareTests tests) {
            if (tests.extremumTestIsConstant() || tests.initTestIsConstant()) {
                // true is the neutral value of &&
                final boolean t1 = tests.constantExtremumTestOrValue(true);
                final boolean t2 = tests.constantInitTestOrValue(true);
                final boolean result = t1 && t2;
                return result == guard.deoptsOnTrue();
            }
            return false;
        }

        private static boolean fitsIn32Bit(IntegerStamp stamp) {
            return NumUtil.isUInt(stamp.mayBeSet());
        }

        private CFGLoop<HIRBlock> tryOptimizeInstanceOf(GuardNode guard, InstanceOfNode compare) {
            AnchoringNode anchor = compare.getAnchor();
            if (anchor == null) {
                return null;
            }
            HIRBlock anchorBlock = earliestBlock(anchor.asNode());
            if (anchorBlock.getLoop() == null) {
                return null;
            }
            HIRBlock valueBlock = earliestBlock(compare.getValue());
            CFGLoop<HIRBlock> hoistAbove = findInstanceOfLoopHoisting(guard, anchorBlock, valueBlock);
            if (hoistAbove != null) {
                compare.setProfile(compare.profile(), hoistAbove.getHeader().getDominator().getBeginNode());
                graph.getOptimizationLog().report(SpeculativeGuardMovementPhase.class, "InstanceOfOptimization", compare);
                return hoistAbove;
            }
            return null;
        }

        private CFGLoop<HIRBlock> findInstanceOfLoopHoisting(GuardNode guard, HIRBlock anchorBlock, HIRBlock valueBlock) {
            assert anchorBlock.getLoop() != null;
            DebugContext debug = guard.getDebug();
            if (valueBlock.getLoop() == anchorBlock.getLoop()) {
                debug.log("shouldOptimizeInstanceOf(%s): anchor and condition in the same loop", guard);
                return null;
            }
            if (!valueBlock.isInSameOrOuterLoopOf(anchorBlock)) {
                debug.log("shouldOptimizeInstanceOf(%s): condition loop is not a parent of anchor loop", guard);
                return null;
            }
            if (!valueBlock.dominates(anchorBlock)) {
                // this can happen when the value comes from *after* the exit of the anchor loop
                debug.log("shouldOptimizeInstanceOf(%s): value block does not dominate loop header", guard);
                return null;
            }
            if (!allowsSpeculativeGuardMovement(guard.getReason(), (LoopBeginNode) anchorBlock.getLoop().getHeader().getBeginNode(), true)) {
                debug.log("shouldOptimizeInstanceOf(%s): The guard would not hoist", guard);
                return null; // the guard would not hoist, don't hoist the compare
            }

            //@formatter:off
            /*
             * At this point, we know that the guard can be hoisted if the instanceOf gets hoisted.
             * How far both nodes are moved towards the instanceOf value, depends on:
             *  1) if the current loop allows guard movement based on its speculation log
             *  2) if hoisting does not end in a block with larger rel. frequency than before
             *
             * Assume the following loop nest, where "value" is the input to the instanceOf:
             *
             * L0
             *  -- value [fq = 100]
             *   L1
             *     -- (...) [fq = 100k]
             *     L2
             *       -- instanceOf [fq = 10k]
             *         -- guard
             *
             * The goal would be to move the instanceOf and its guard to the header of L1 with frequency 100.
             *
             * First, it is assumed that the guard can be hoisted from its immediate loop L2.
             * Then, all outer loops (dominated by value) are traversed for finding the loop header
             * with the lowest frequency.
             * If one loop does not allow hoisting based on the speculation log, the traversal terminates sooner.
             */
            //@formatter:on
            CFGLoop<HIRBlock> hoistFrom = anchorBlock.getLoop();
            CFGLoop<HIRBlock> curLoop = anchorBlock.getLoop();

            // check hoisting from loop nests
            while (curLoop.getParent() != valueBlock.getLoop()) {
                curLoop = curLoop.getParent();
                if (!allowsSpeculativeGuardMovement(guard.getReason(), (LoopBeginNode) curLoop.getHeader().getBeginNode(), true)) {
                    break;
                } else if (ignoreFrequency || shouldHoistBasedOnFrequency(curLoop.getHeader().getDominator(), hoistFrom.getHeader().getDominator())) {
                    hoistFrom = curLoop;
                }
            }

            // compare lowest rel. frequency of outer loops and initial anchor rel. frequency
            if (!ignoreFrequency && !shouldHoistBasedOnFrequency(hoistFrom.getHeader().getDominator(), anchorBlock)) {
                debug.log("hoisting is not beneficial based on frequency", guard);
                return null;
            }
            return hoistFrom;
        }

        private HIRBlock earliestBlockForGuard(GuardNode guard, CFGLoop<HIRBlock> forcedHoisting) {
            DebugContext debug = guard.getDebug();
            Node anchor = guard.getAnchor().asNode();
            assert guard.inputs().count() == 2 : Assertions.errorMessage(guard.inputs());
            HIRBlock conditionEarliest = earliestBlock(guard.getCondition());

            HIRBlock anchorEarliest = earliestBlock(anchor);
            HIRBlock newAnchorEarliest = null;
            LoopBeginNode outerMostExitedLoop = null;
            HIRBlock b = anchorEarliest;

            if (forcedHoisting != null) {
                newAnchorEarliest = forcedHoisting.getHeader().getDominator();
                if (anchorEarliest.strictlyDominates(newAnchorEarliest)) {
                    /*
                     * Special case strip mined inverted loops: if the original guard of a strip
                     * mined inverted loop is already anchored outside the outer strip mined loop,
                     * no need to try to use the loop header of the outer strip mined loop as the
                     * forced hoisting anchor.
                     */
                    newAnchorEarliest = anchorEarliest;
                }
                outerMostExitedLoop = (LoopBeginNode) forcedHoisting.getHeader().getBeginNode();
                b = newAnchorEarliest;
            }

            debug.log("earliestBlockForGuard(%s) inital anchor : %s, condition : %s condition's earliest %s", guard, anchor, guard.getCondition(), conditionEarliest.getBeginNode());

            double minFrequency = anchorEarliest.getRelativeFrequency();

            while (conditionEarliest.strictlyDominates(b)) {
                HIRBlock candidateAnchor = b.getDominatorSkipLoops();
                assert candidateAnchor.getLoopDepth() <= anchorEarliest.getLoopDepth() : " candidate anchor block at begin node " + candidateAnchor.getBeginNode() + " earliest anchor block " +
                                anchorEarliest.getBeginNode() + " loop depth is not smaller equal for guard " + guard;

                if (b.isLoopHeader() && (newAnchorEarliest == null || candidateAnchor.getLoopDepth() < newAnchorEarliest.getLoopDepth())) {
                    LoopBeginNode loopBegin = (LoopBeginNode) b.getBeginNode();
                    if (!allowsSpeculativeGuardMovement(guard.getReason(), loopBegin, true)) {
                        break;
                    } else {
                        double candidateFrequency = candidateAnchor.getRelativeFrequency();
                        if (ignoreFrequency || shouldHoistBasedOnFrequency(candidateFrequency, minFrequency)) {
                            debug.log("earliestBlockForGuard(%s) hoisting above %s", guard, loopBegin);
                            outerMostExitedLoop = loopBegin;
                            newAnchorEarliest = candidateAnchor;
                            minFrequency = candidateFrequency;
                        } else {
                            debug.log("earliestBlockForGuard(%s) %s not worth it, old relative frequency %f, new relative frequency %f", guard, loopBegin, minFrequency, candidateFrequency);
                        }
                    }
                }
                b = candidateAnchor;
            }

            if (newAnchorEarliest != null && allowsSpeculativeGuardMovement(guard.getReason(), outerMostExitedLoop, false)) {
                AnchoringNode newAnchor = newAnchorEarliest.getBeginNode();
                guard.setAnchor(newAnchor);
                debug.log("New earliest : %s, anchor is %s, update guard", newAnchorEarliest.getBeginNode(), anchor);
                HIRBlock earliest = newAnchorEarliest;
                if (guard.getAction() == DeoptimizationAction.None) {
                    guard.setAction(DeoptimizationAction.InvalidateRecompile);
                }
                guard.setSpeculation(registerSpeculativeGuardMovement(guard.getReason(), outerMostExitedLoop));
                debug.log("Exited %d loops for %s %s in %s", anchorEarliest.getLoopDepth() - earliest.getLoopDepth(), guard, guard.getCondition(), graph.method());
                return earliest;
            } else {
                debug.log("Keep normal anchor edge");
                return conditionEarliest.strictlyDominates(anchorEarliest) ? anchorEarliest : conditionEarliest;
            }
        }

        private boolean allowsSpeculativeGuardMovement(DeoptimizationReason reason, LoopBeginNode loopBeginNode, boolean checkDeoptimizationCount) {
            DebugContext debug = loopBeginNode.getDebug();
            if (speculationLog != null) {
                SpeculationReason speculation = createSpeculation(reason, loopBeginNode);
                if (speculationLog.maySpeculate(speculation)) {
                    return true;
                } else {
                    debug.log("Preventing Speculative Guard Motion because of speculation log: %s", speculation);
                    return false;
                }
            }
            if (checkDeoptimizationCount && profilingInfo != null) {
                if (profilingInfo.getDeoptimizationCount(DeoptimizationReason.LoopLimitCheck) > 1) {
                    debug.log("Preventing Speculative Guard Motion because of failed LoopLimitCheck");
                    return false;
                }
                if (profilingInfo.getDeoptimizationCount(reason) > 2) {
                    debug.log("Preventing Speculative Guard Motion because of deopt count for reason: %s", reason);
                    return false;
                }
            }
            debug.log("Allowing Speculative Guard Motion but we can not speculate: %s", loopBeginNode);
            return true;
        }

        private SpeculationLog.Speculation registerSpeculativeGuardMovement(DeoptimizationReason reason, LoopBeginNode loopBeginNode) {
            assert allowsSpeculativeGuardMovement(reason, loopBeginNode, false);
            if (speculationLog != null) {
                return speculationLog.speculate(createSpeculation(reason, loopBeginNode));
            } else {
                loopBeginNode.getDebug().log("No log or state :(");
                return SpeculationLog.NO_SPECULATION;
            }
        }
    }

    private static final SpeculationReasonGroup GUARD_MOVEMENT_LOOP_SPECULATIONS = new SpeculationReasonGroup("GuardMovement", ResolvedJavaMethod.class, int.class, DeoptimizationReason.class);

    private static SpeculationLog.SpeculationReason createSpeculation(DeoptimizationReason reason, LoopBeginNode loopBeginNode) {
        FrameState loopState = loopBeginNode.stateAfter();
        ResolvedJavaMethod method = null;
        int bci = 0;
        if (loopState != null) {
            method = loopState.getMethod();
            bci = loopState.bci;
        }
        return GUARD_MOVEMENT_LOOP_SPECULATIONS.createSpeculationReason(method, bci, reason);
    }

    private static boolean isInverted(Loop loop) {
        return loop.isCounted() && loop.counted().isInverted();
    }
}
