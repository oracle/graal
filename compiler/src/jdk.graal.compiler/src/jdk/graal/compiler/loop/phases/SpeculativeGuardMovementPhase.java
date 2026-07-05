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

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
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
import jdk.graal.compiler.nodes.calc.AddNode;
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
import jdk.graal.compiler.nodes.loop.InductionVariableHelper;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.phases.FloatingGuardPhase;
import jdk.graal.compiler.phases.Speculative;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.PostRunCanonicalizationPhase;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;
import jdk.graal.compiler.phases.common.util.LoopUtility;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerAddExactOverflowNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerExactOverflowNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerSubExactOverflowNode;
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
 * Moves guards out of loops when the guard condition can be evaluated before the loop. If the guard
 * is reached through a branch that depends on loop values, moving it before the loop makes it
 * execute even on loop executions that would not take that branch. The phase speculates that this
 * earlier execution will usually avoid repeated checks without causing extra deoptimizations.
 * <p>
 * For example:
 *
 * <pre>{@code
 * public static int sumInts(int[] ints, Integer negAdjust) {
 *     int sum = 0;
 *     for (int i = 0; i < ints.length; i++) {
 *         if (ints[i] < 0) {
 *             sum += negAdjust; // guard: negAdjust != null
 *         }
 *         sum += ints[i];
 *     }
 *     return sum;
 * }
 * }</pre>
 *
 * The null check of {@code negAdjust} is loop invariant, so moving it before the loop can remove a
 * repeated guard. That is speculative because the original program only executes the null check on
 * iterations where {@code ints[i] < 0}. If the moved guard deoptimizes, the speculation log records
 * the failed {@code (loop, deoptimization reason)} pair and a later compilation will keep the guard
 * in the loop.
 * <p>
 * The phase computes the earliest block where each node can be scheduled. For a guard, if its
 * condition can be scheduled before one or more enclosing loops, the phase moves the guard's anchor
 * before the outermost such loop and records a speculation for that loop. This guard-specific part
 * is handled by {@link SpeculativeGuardMovement#earliestBlockForGuard(GuardNode, CFGLoop)}:
 *
 * <pre>{@code
 * for each guard:
 *     conditionEarliest = earliest block for guard.condition
 *     anchorEarliest = earliest block for guard.anchor
 *     while conditionEarliest dominates a loop header above anchorEarliest:
 *         if speculation permits moving this guard above the loop
 *            and moving there is not more frequent than the old anchor:
 *             remember the loop predecessor as a candidate anchor
 *     if a candidate anchor was found:
 *         guard.anchor = candidateAnchor
 *         guard.action = InvalidateRecompile
 *         guard.speculation = speculate(loop, guard.reason)
 * }</pre>
 *
 * Besides loop-invariant conditions, this phase can also make two common guard conditions movable
 * first: an anchored {@link InstanceOfNode} whose value is available before the loop, and an
 * integer comparison between a loop-invariant value and an induction variable. The
 * integer-comparison rewrite is specific to counted loops, other guards can be hoisted out of
 * non-counted loops as well.
 * <p>
 * If the guard condition is an integer comparison against an induction variable, the compare
 * depends on a loop phi. The phase replaces the comparison with tests over the induction variable's
 * initial value and extremum. This rewrite is handled by
 * {@link SpeculativeGuardMovement#tryOptimizeCompare(GuardNode, CompareNode)}. For example, when
 * the IV is the left operand of a signed comparison, the new condition is:
 *
 * <pre>{@code
 * // Original loop-body guard, ignoring whether the guard deopts on true or false:
 * guard(i < bound)
 *
 * // Hoisted guard condition:
 * guard((init(i) < bound) && (extremum(i) < (long) bound))
 * }</pre>
 *
 * Checking the initial value and the extremum is enough to prove that the comparison holds on every
 * iteration. For non-inverted loops, the phase also adds a loop-entry check so that the extremum
 * test is ignored when the loop has zero trips. If the new tests fold to a guard that always
 * deoptimizes, the phase leaves the original guard in the loop.
 * <p>
 * The IV extremum and the overflow conditions needed to compute it come from
 * {@link SpeculativeGuardMovement#buildExtremumComputation(InductionVariable, Stamp, ValueNode)}.
 * This phase adds overflow guards for IV extrema, because the computation must be safe in the IV's
 * original integer width, not just after the result has been widened to {@code long}. IV overflow
 * guards are also protected by speculations.
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
        EconomicSetNodeEventListener change = new EconomicSetNodeEventListener(EnumSet.of(Graph.NodeEvent.INPUT_CHANGED));
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            boolean iterate = false;
            try (NodeEventScope news = graph.trackNodeEvents(change)) {
                if (graph.getDebug().isCountEnabled()) {
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

    /**
     * Materializes a temporary overflow guard for a non-constant extremum overflow condition. This
     * is used when speculative guard movement is invoked from a context that needs to manage such
     * guards specially instead of just adding floating guards to the graph.
     */
    @FunctionalInterface
    public interface OverflowConditionGuardMaterializer {
        /**
         * Materializes a temporary overflow guard for the compare optimization of
         * {@code hoistingCandidateGuard}.
         *
         * @param hoistingCandidateGuard the existing guard currently being considered for hoisting
         *            whose compare rewrite needs additional overflow protection
         * @param overflowCondition the non-constant condition that indicates overflow and must
         *            deoptimize when it becomes true
         */
        GuardNode materializeOverflowGuard(GuardNode hoistingCandidateGuard, LogicNode overflowCondition);
    }

    public static boolean performSpeculativeGuardMovement(MidTierContext context, StructuredGraph graph, LoopsData loops) {
        return performSpeculativeGuardMovement(context, graph, loops, null, false, true, null);
    }

    public static boolean performSpeculativeGuardMovement(MidTierContext context, StructuredGraph graph, LoopsData loops, boolean ignoreFrequency, boolean requireSpeculationLog) {
        return performSpeculativeGuardMovement(context, graph, loops, null, ignoreFrequency, requireSpeculationLog, null);
    }

    public static boolean performSpeculativeGuardMovement(MidTierContext context, StructuredGraph graph, LoopsData loops, boolean ignoreFrequency, boolean requireSpeculationLog,
                    OverflowConditionGuardMaterializer overflowConditionGuardMaterializer) {
        return performSpeculativeGuardMovement(context, graph, loops, null, ignoreFrequency, requireSpeculationLog, overflowConditionGuardMaterializer);
    }

    public static boolean performSpeculativeGuardMovement(MidTierContext context, StructuredGraph graph, LoopsData loops, NodeBitMap toProcess) {
        return performSpeculativeGuardMovement(context, graph, loops, toProcess, false, true, null);
    }

    public static boolean performSpeculativeGuardMovement(MidTierContext context, StructuredGraph graph, LoopsData loops, NodeBitMap toProcess, boolean ignoreFrequency,
                    boolean requireSpeculationLog) {
        return performSpeculativeGuardMovement(context, graph, loops, toProcess, ignoreFrequency, requireSpeculationLog, null);
    }

    public static boolean performSpeculativeGuardMovement(MidTierContext context, StructuredGraph graph, LoopsData loops, NodeBitMap toProcess, boolean ignoreFrequency,
                    boolean requireSpeculationLog, OverflowConditionGuardMaterializer overflowConditionGuardMaterializer) {
        SpeculativeGuardMovement spec = new SpeculativeGuardMovement(loops, graph.createNodeMap(), graph, context.getProfilingInfo(), graph.getSpeculationLog(), toProcess,
                        ignoreFrequency, requireSpeculationLog, overflowConditionGuardMaterializer);
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
        private final OverflowGuardRegistry overflowGuardRegistry;

        SpeculativeGuardMovement(LoopsData loops, NodeMap<HIRBlock> earliestCache, StructuredGraph graph, ProfilingInfo profilingInfo, SpeculationLog speculationLog, NodeBitMap toProcess,
                        boolean ignoreFrequency, boolean requireSpeculationLog, OverflowConditionGuardMaterializer overflowConditionGuardMaterializer) {
            this.loops = loops;
            this.earliestCache = earliestCache;
            this.graph = graph;
            this.profilingInfo = profilingInfo;
            GraalError.guarantee(requireSpeculationLog ? speculationLog != null : true, "Graph has no speculation log attached: %s", graph);
            this.speculationLog = speculationLog;
            this.toProcess = toProcess;
            this.ignoreFrequency = ignoreFrequency;
            this.overflowGuardRegistry = new OverflowGuardRegistry(overflowConditionGuardMaterializer);
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

        /**
         * Tries to rewrite the given comparison against {@code iv} to a loop-invariant form.
         * {@code bound} is the other operand of the comparison. The {@code mirrored} flag is
         * {@code true} if {@code iv} was originally on the right-hand side of the comparison.
         * Returns {@code true} if the rewrite was applied.
         * <p>
         * The overall flow for a guard with an optimizable comparison condition is:
         * <ol>
         * <li>{@link #shouldOptimizeCompare} checks whether the rewrite is allowed and worth doing.
         * As a special case, it may create the counted loop's own overflow guard, but it does not
         * change the candidate guard or create derived IV overflow guards.</li>
         * <li>{@link #computeNewCompareGuards} (called here) creates the new init/extremum tests
         * and any IV overflow guards. It can still return {@code null} if the speculation log says
         * such an overflow guard must not be created.</li>
         * <li>{@link #optimizeCompare} (also called here) changes the original guard to use the new
         * tests.</li>
         * </ol>
         */
        private boolean tryOptimizeCompare(CompareNode compare, InductionVariable iv, ValueNode bound, boolean mirrored, GuardNode guard) {
            if (shouldOptimizeCompare(compare, iv, bound, guard, mirrored)) {
                OptimizedCompareTests materializedTests = computeNewCompareGuards(guard, compare, iv, bound, mirrored);
                if (materializedTests == null) {
                    guard.getDebug().log("tryOptimizeCompare(%s): overflow guards are no longer materializable (speculation not possible anymore)", compare);
                    return false;
                }
                optimizeCompare(compare, iv, guard, materializedTests);
                return true;
            }
            return false;
        }

        /**
         * Changes the graph after {@link #shouldOptimizeCompare} has accepted the rewrite. The
         * original guard condition is replaced with the init/extremum tests. For non-inverted
         * loops, this also adds a loop-entry check that skips the extremum test on zero-trip
         * executions. Usages of the guard are then moved behind a {@link MultiGuardNode} that also
         * keeps them inside the loop body.
         */
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

        /**
         * Builds the init/extremum tests for the moved guard and creates any required IV overflow
         * guards. Returns {@code null} if the extremum computation would always overflow or if the
         * speculation log says a required overflow guard must not be created.
         */
        private OptimizedCompareTests computeNewCompareGuards(GuardNode guard, CompareNode compare, InductionVariable iv, ValueNode bound, boolean mirrored) {
            return computeNewCompareGuards(guard, compare, iv, bound, mirrored, null);
        }

        private OptimizedCompareTests computeNewCompareGuards(GuardNode guard, CompareNode compare, InductionVariable iv, ValueNode bound, boolean mirrored, ValueNode maxTripCountNode) {
            ExtremumOverflowData extremumOverflowData = computeExtremumOverflowData(guard, iv, maxTripCountNode);
            if (extremumOverflowData.alwaysOverflows()) {
                return null;
            }
            ValueNode guardedExtremum = graph.addOrUniqueWithInputs(GuardedValueNode.create(extremumOverflowData.extremum, extremumOverflowData.overflowGuard));
            return computeNewCompareGuards(compare, iv, bound, mirrored, guardedExtremum);
        }

        private OptimizedCompareTests computeNewCompareGuards(CompareNode compare, InductionVariable iv, ValueNode bound, boolean mirrored, ValueNode guardedExtremum) {
            final boolean zeroExtendBound = compare.condition().isUnsigned();
            ValueNode longBound = IntegerConvertNode.convert(bound, StampFactory.forKind(JavaKind.Long), zeroExtendBound, graph, NodeView.DEFAULT);
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
                GraalError.guarantee(compare instanceof IntegerLessThanNode, "Expected signed compare but got %s", compare);
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
         * Captures the extremum used for hoisting together with the overflow guards needed to make
         * that extremum valid.
         */
        private record ExtremumOverflowData(ValueNode extremum, GuardingNode overflowGuard) {
            private boolean alwaysOverflows() {
                return overflowGuard instanceof GuardNode overflowGuardNode && OverflowGuardRegistry.isAlwaysOverflowingGuard(overflowGuardNode);
            }
        }

        /**
         * Computes the extremum used for hoisting and the overflow guards needed to evaluate that
         * extremum safely.
         * <p>
         * Each IV step contributes its raw exact-overflow conditions, such as
         * {@code IntegerAddExactOverflow(base, 27)} for the condition that becomes true iff
         * evaluating {@code base + 27} in the IV arithmetic width overflows. The
         * {@link OverflowGuardRegistry} handles canonicalization and sharing of guards.
         */
        private ExtremumOverflowData computeExtremumOverflowData(GuardNode hoistingCandidateGuard, InductionVariable iv, ValueNode maxTripCountNode) {
            InductionVariable.Extremum extremumComputation = buildExtremumComputation(iv, StampFactory.forKind(JavaKind.Long), maxTripCountNode);
            GuardingNode extremumOverflowGuard = null;
            for (LogicNode overflowCondition : extremumComputation.overflowConditions()) {
                GuardNode ivOverflowGuard = overflowGuardRegistry.createOverflowGuard(hoistingCandidateGuard, iv, overflowCondition);
                if (OverflowGuardRegistry.isAlwaysOverflowingGuard(ivOverflowGuard)) {
                    return new ExtremumOverflowData(extremumComputation.extremum(), ivOverflowGuard);
                }
                extremumOverflowGuard = MultiGuardNode.addGuard(extremumOverflowGuard, ivOverflowGuard);
            }
            return new ExtremumOverflowData(extremumComputation.extremum(), extremumOverflowGuard);
        }

        /**
         * Class representing the result of optimizing a comparison.
         */
        private static class OptimizedCompareTests {
            /**
             * Optimized compare test: init < bound.
             * <p>
             * if mirrored: bound < init
             */
            LogicNode initTest;
            /**
             * Optimized compare test: extremum < bound.
             * <p>
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

        /**
         * Checks whether this compare can be rewritten and moved out of the loop. It checks that
         * the loop is counted, the guard and IV are in that loop, the bound is available before the
         * loop, that moving the guard is frequent enough, the speculation log allows the move,
         * overflow guards can be added, and the new guard condition is not a constant that always
         * deopts.
         * <p>
         * If it returns {@code true}, the caller must call {@link #computeNewCompareGuards} to
         * create the new compare tests and any required derived IV overflow guards before rewriting
         * the graph. This method may create the counted loop's own overflow guard when the loop
         * counter needs one; it must not create derived IV overflow guards or rewrite the candidate
         * guard.
         */
        private boolean shouldOptimizeCompare(CompareNode compare, InductionVariable iv, ValueNode bound, GuardNode guard, boolean mirrored) {
            DebugContext debug = guard.getDebug();
            if (!iv.getLoop().isCounted()) {
                debug.log("shouldOptimizeCompare(%s):not a counted loop", guard);
                return false;
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
                        return false;
                    }
                }
            } else {
                if (!earliestBlock(iv.getLoop().counted().getBody()).dominates(guardAnchorBlock)) {
                    debug.log("shouldOptimizeCompare(%s):guard is not inside loop", guard);
                    return false; // guard must come from inside the loop
                }
            }

            if (!ivLoop.getBlocks().contains(earliestBlock(iv.valueNode()))) {
                debug.log("shouldOptimizeCompare(%s):iv is not inside loop", guard);
                // These strange IVs are created because we don't really know if Guards are inside a
                // loop. See LoopFragment.markFloating
                // Such IVs can not be re-written to anything that can be hoisted.
                return false;
            }

            // Predecessor block IDs are always before successor block IDs
            if (earliestBlock(bound).getId() >= ivLoop.getHeader().getId()) {
                debug.log("shouldOptimizeCompare(%s):bound is not schedulable above the IV loop", guard);
                return false; // the bound must be loop invariant and schedulable above the loop.
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
                    return false;
                }
            }

            CFGLoop<HIRBlock> l = guardAnchorBlock.getLoop();
            if (isInverted(loopEx)) {
                // guard is anchored outside the loop but the condition might still be in the loop
                l = iv.getLoop().getCFGLoop();
            }
            if (l == null) {
                return false;
            }
            assert l != null : "Loop for guard anchor block must not be null:" + guardAnchorBlock.getBeginNode() + " loop " + iv.getLoop() + " inverted?" +
                            isInverted(iv.getLoop());
            do {
                if (!allowsSpeculativeGuardMovement(guard.getReason(), (LoopBeginNode) l.getHeader().getBeginNode(), true)) {
                    debug.log("shouldOptimizeCompare(%s):The guard would not hoist", guard);
                    return false; // the guard would not hoist, don't hoist the compare
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
                    return false;
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
                if (countedLoopInfo.getOverFlowGuard() == null && !countedLoopInfo.counterNeverOverflows()) {
                    if (!graph.getGuardsStage().allowsFloatingGuards() || !countedLoopInfo.canCreateOverflowGuard(null)) {
                        debug.log("shouldOptimizeCompare(%s): abort, cannot create overflow guard", compare);
                        return false;
                    }
                    countedLoopInfo.createOverFlowGuard();
                }
                if (!canComputeOptimizedTests(guard, compare, iv, bound, mirrored, null)) {
                    return false;
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
                        if (!canComputeOptimizedTests(guard, compare, duplicateOriginalLoopIV, bound, mirrored, outerLoopInitBasedMaxTripCount)) {
                            return false;
                        }
                    }
                }
                return true;
            } else {
                debug.log("shouldOptimizeCompare(%s): bound or iv does not fit in int", guard);
                return false; // only ints are supported (so that the overflow fits in longs)
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

        /**
         * Finds a new anchor for the guard and updates the guard when moving it is allowed. A
         * non-null {@code forcedHoisting} means a caller has already handled a compare or
         * {@code instanceof} condition for that loop; in that case, this method starts from the
         * predecessor of the loop header. Otherwise it starts from the earliest block of the guard
         * condition. The anchor is changed only if speculation allows the guard to leave at least
         * one loop and the new anchor is not more frequent than the old one.
         */
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

        /**
         * Builds the IV extremum for use by the hoisted form of the comparison. The value is
         * computed from the counted loop's max trip count and the IV definition. The caller must
         * create IV extremum overflow guards from the overflow conditions returned as part of the
         * result.
         */
        private static InductionVariable.Extremum buildExtremumComputation(InductionVariable iv, Stamp extremumStamp, ValueNode maxTripCountNode) {
            GraalError.guarantee(extremumStamp instanceof IntegerStamp, "Expected integer stamp for %s but got %s", iv, extremumStamp);
            CountedLoopInfo countedLoop = iv.getLoop().counted();
            ValueNode effectiveMaxTripCount = maxTripCountNode == null ? countedLoop.maxTripCountNode(true) : maxTripCountNode;
            InductionVariable limitCheckedIV = countedLoop.getLimitCheckedIV();
            InductionVariable bodyIV = countedLoop.getBodyIVEqualsLimitCheckedIV() ? limitCheckedIV : InductionVariableHelper.previousIteration(limitCheckedIV);
            return iv.extremumComputation(true, extremumStamp, effectiveMaxTripCount, bodyIV, limitCheckedIV);
        }

        /**
         * Determines whether the given guard's comparison can be hoisted out of the loop, including
         * whether the required extremum computations can be guarded against overflow. Hoisting is
         * not considered possible if the hoisted guard would unconditionally deopt.
         * <p>
         * This method does not modify the graph.
         */
        private boolean canComputeOptimizedTests(GuardNode guard, CompareNode compare, InductionVariable iv, ValueNode bound, boolean mirrored, ValueNode maxTripCountNode) {
            InductionVariable.Extremum extremumComputation = buildExtremumComputation(iv, StampFactory.forKind(JavaKind.Long), maxTripCountNode);
            for (LogicNode overflowCondition : extremumComputation.overflowConditions()) {
                if (!overflowGuardRegistry.canGuardOverflowCondition(guard.getReason(), iv, overflowCondition)) {
                    guard.getDebug().log("shouldOptimizeCompare(%s): guard would overflow", compare);
                    return false;
                }
            }
            OptimizedCompareTests optimizedCompareTests = computeNewCompareGuards(compare, iv, bound, mirrored, extremumComputation.extremum());
            /*
             * Determine if, based on loop bounds and guard bounds the moved guard is always false,
             * i.e., deopts unconditionally. In such cases, avoid optimizing the compare.
             *
             * Note: this typically happens with multiple loop exits, i.e., a loop condition that is
             * not visible in the counted condition of the loop.
             */
            if (optimizedCompareUnconditionalDeopt(guard, optimizedCompareTests)) {
                guard.getDebug().log("shouldOptimizeCompare(%s): guard would immediately deopt", compare);
                return false;
            }
            return true;
        }
    }

    /**
     * Manages IV overflow guards, sharing a single guard for related overflow conditions expressed
     * as {@link IntegerExactOverflowNode}s. When two conditions have the same base value and only
     * differ in a constant offset, they are shared. For example, given overflow guards for
     * {@code base + 31} and {@code base + 127}, we only place a single overflow guard for
     * {@code base + 127}. If this guard deoptimizes, the loop will not be entered, and it is
     * irrelevant whether {@code base + 31} would deoptimize before the loop. On the other hand, if
     * {@code base + 127} does not overflow, then neither does {@code base + 31}. In both cases, it
     * is enough to only check the stronger condition (the one with the constant offset with the
     * larger magnitude).
     * <p>
     * Overflow guards are grouped by {@link OverflowGuardGroupKey} consisting of the original loop,
     * the {@linkplain #normalizeOverflowConditionForSharing normalized} base value, and whether the
     * constant is positive or negative. Overflow guards with the same base value but different-sign
     * offsets do not subsume each other.
     */
    private static final class OverflowGuardRegistry {
        private final OverflowConditionGuardMaterializer overflowConditionGuardMaterializer;
        private final EconomicMap<OverflowGuardGroupKey, GuardNode> strongestOverflowGroupGuards = EconomicMap.create();

        private OverflowGuardRegistry(OverflowConditionGuardMaterializer overflowConditionGuardMaterializer) {
            this.overflowConditionGuardMaterializer = overflowConditionGuardMaterializer;
        }

        /**
         * Identifies one group of overflow guards belonging to the same loop, having the same
         * variable base value, and the same sign of the constant offset.
         */
        private record OverflowGuardGroupKey(LoopBeginNode loopBegin, ValueNode baseValue, boolean positive) {
        }

        /**
         * Materializes one exact-overflow guard. This guard is strengthened in place as stronger
         * offsets from the same base value appear.
         *
         * @return {@code null} when no guard is needed, a guard with a nontrivial condition when
         *         overflow is possible and must be checked, or an always-deoptimizing guard that
         *         represents an always-overflowing condition.
         */
        private GuardNode createOverflowGuard(GuardNode hoistingCandidateGuard, InductionVariable iv, LogicNode overflowCondition) {
            if (overflowCondition.isContradiction()) {
                return null;
            }
            if (overflowCondition.isTautology()) {
                return alwaysOverflowingGuard(hoistingCandidateGuard);
            }
            if (overflowCondition instanceof IntegerExactOverflowNode exactOverflow) {
                IntegerExactOverflowNode normalizedOverflowCondition = normalizeOverflowConditionForSharing(exactOverflow);
                if (normalizedOverflowCondition instanceof IntegerAddExactOverflowNode addOverflow && addOverflow.getY().isConstant()) {
                    return createOrReuseOverflowGroupGuard(hoistingCandidateGuard, iv.getLoop().counted(), iv.getLoop().loopBegin(), addOverflow);
                }
            }
            return materializeOverflowGuard(hoistingCandidateGuard, iv.getLoop().counted(), overflowCondition);
        }

        /**
         * Determines whether the given overflow condition can be generated with all the required
         * guards. Returns {@code true} if overflow condition is impossible, the condition is
         * already guarded by a shared group guard, or placing guards is allowed; returns
         * {@code false} otherwise, including if the condition indicates unconditional overflow.
         * <p>
         * This method does not modify the graph.
         */
        private boolean canGuardOverflowCondition(DeoptimizationReason additionalReason, InductionVariable iv, LogicNode overflowCondition) {
            if (overflowCondition.isContradiction()) {
                return true;
            }
            if (overflowCondition.isTautology()) {
                return false;
            }
            if (overflowCondition instanceof IntegerExactOverflowNode exactOverflow) {
                IntegerExactOverflowNode normalizedOverflowCondition = normalizeOverflowConditionForSharing(exactOverflow);
                if (normalizedOverflowCondition instanceof IntegerAddExactOverflowNode addOverflow && addOverflow.getY().isConstant()) {
                    LoopBeginNode loopBegin = iv.getLoop().loopBegin();
                    OverflowGuardGroupKey groupKey = getOverflowGuardGroupKey(addOverflow, loopBegin);
                    GuardNode sharedGuard = strongestOverflowGroupGuards.get(groupKey);
                    if (sharedGuard != null) {
                        /*
                         * A prior compare optimization in this phase has already materialized the
                         * shared group guard for this loop/base/sign combination. We can reuse it.
                         */
                        return true;
                    }
                }
            }
            /* We will need to place a proper guard, check if we can. */
            return overflowConditionGuardMaterializer != null || iv.getLoop().counted().canCreateOverflowGuard(additionalReason);
        }

        private static OverflowGuardGroupKey getOverflowGuardGroupKey(IntegerAddExactOverflowNode addOverflow, LoopBeginNode loopBegin) {
            ValueNode baseValue = addOverflow.getX();
            long constantOffset = addOverflow.getY().asJavaConstant().asLong();
            GraalError.guarantee(constantOffset != 0, "Unexpected zero overflow-group offset for %s, %s", loopBegin, baseValue);
            OverflowGuardGroupKey groupKey = new OverflowGuardGroupKey(loopBegin, baseValue, constantOffset > 0);
            return groupKey;
        }

        /**
         * Creates or reuses the single guard for one overflow guard group, given a new overflow
         * condition with constant offset. If the group already has a guard and the new condition is
         * stronger, that guard's condition is updated in place.
         */
        private GuardNode createOrReuseOverflowGroupGuard(GuardNode hoistingCandidateGuard, CountedLoopInfo countedLoop, LoopBeginNode loopBegin,
                        IntegerAddExactOverflowNode normalizedOverflowCondition) {
            long constantOffset = normalizedOverflowCondition.getY().asJavaConstant().asLong();
            OverflowGuardGroupKey groupKey = getOverflowGuardGroupKey(normalizedOverflowCondition, loopBegin);
            GuardNode sharedGuard = strongestOverflowGroupGuards.get(groupKey);
            if (sharedGuard != null && !isStrongerOffset(constantOffset, overflowOffset(sharedGuard))) {
                return sharedGuard;
            }

            StructuredGraph graph = hoistingCandidateGuard.graph();
            LogicNode groupOverflowCondition = graph.addOrUniqueWithInputs(normalizedOverflowCondition);
            if (sharedGuard == null) {
                GuardNode materializedOverflowGuard = materializeOverflowGuard(hoistingCandidateGuard, countedLoop, groupOverflowCondition);
                if (!isAlwaysOverflowingGuard(materializedOverflowGuard)) {
                    /*
                     * Do not cache the always-overflowing sentinel: it represents refusal to
                     * materialize a speculation-based overflow guard, not a real base + offset
                     * exact-overflow condition that later grouped guards can share or strengthen.
                     */
                    strongestOverflowGroupGuards.put(groupKey, materializedOverflowGuard);
                }
                return materializedOverflowGuard;
            } else {
                sharedGuard.setCondition(groupOverflowCondition, sharedGuard.isNegated());
                return sharedGuard;
            }
        }

        /**
         * Normalize the given {@code overflowCondition} for purposes of grouping conditions with a
         * similar base but different constant offsets. {@code IntegerSubExactOverflow(base, c)}
         * will be transformed to {@code IntegerAddExactOverflow(base, -c)}. This will also
         * transform {@code IntegerAddExactOverflow(Add(base, c1), c2)} to
         * {@code IntegerAddExactOverflow(base, c1 + c2)} when {@code c1} and {@code c2} have the
         * same sign. In both cases, the normalization is only done if the computation of the new
         * constant does not overflow.
         * <p>
         * In some corner cases, the transformed version may indicate overflow more often than the
         * original: The inner non-exact {@code base + c1} might already overflow without indicating
         * this, and the outer exact {@code + c2} might not overflow. The combined version will
         * indicate an overflow. This can make the resulting guard deoptimize more eagerly than the
         * original, so users of the returned condition must use protection against deopt loops.
         *
         * @return an {@link IntegerAddExactOverflowNode} that is not added to the graph when
         *         normalization succeeds; otherwise the original {@code overflowCondition}
         */
        private static IntegerExactOverflowNode normalizeOverflowConditionForSharing(IntegerExactOverflowNode overflowCondition) {
            if (!(overflowCondition instanceof IntegerAddExactOverflowNode || overflowCondition instanceof IntegerSubExactOverflowNode)) {
                return overflowCondition;
            }
            ValueNode offsetNode = overflowCondition.getY();
            if (!offsetNode.isJavaConstant()) {
                return overflowCondition;
            }
            int bits = PrimitiveStamp.getBits(offsetNode.stamp(NodeView.DEFAULT));
            long offset = offsetNode.asJavaConstant().asLong();
            if (overflowCondition instanceof IntegerSubExactOverflowNode) {
                if (offset == NumUtil.minValue(bits)) {
                    return overflowCondition;
                }
                offset = -offset;  // normalize to add
            }

            ValueNode base = overflowCondition.getX();
            if (base instanceof AddNode add && add.getY().isJavaConstant()) {
                long innerY = add.getY().asJavaConstant().asLong();
                /*
                 * AddExact(Add(base, innerY), offset) -> AddExact(base, (innerY + offset))
                 *
                 * Only safe when innerY and offset have the same sign, otherwise they may cancel.
                 * For example, AddExact(Add(INT_MIN, -1), 2) would signal overflow but
                 * AddExact(INT_MIN, (-1 + 2)) would not.
                 */
                if (Long.signum(innerY) == Long.signum(offset)) {
                    try {
                        offset = LoopUtility.addExact(bits, innerY, offset);
                        base = add.getX();
                    } catch (ArithmeticException e) {
                        return overflowCondition;
                    }
                }
            }
            GraalError.guarantee(offset != 0, "offset should have been nonzero originally and must not cancel out: %s", offset);

            LogicNode normalized = IntegerAddExactOverflowNode.create(base, ConstantNode.forIntegerBits(bits, offset));
            if (normalized instanceof IntegerAddExactOverflowNode normalizedAddExact) {
                return normalizedAddExact;
            } else {
                /* The smart constructor folded this to some simpler version. */
                return overflowCondition;
            }
        }

        /**
         * Returns whether {@code candidateOffset} is stronger than {@code currentOffset} within one
         * same-sign group.
         */
        private static boolean isStrongerOffset(long candidateOffset, long currentOffset) {
            GraalError.guarantee(Long.signum(candidateOffset) == Long.signum(currentOffset), "Expected same-sign overflow-group offsets: %s, %s", candidateOffset, currentOffset);
            return candidateOffset > 0 ? candidateOffset > currentOffset : candidateOffset < currentOffset;
        }

        /**
         * Extracts the constant offset represented by a shared exact-overflow guard.
         */
        private static long overflowOffset(GuardNode overflowGuard) {
            GraalError.guarantee(overflowGuard.getCondition() instanceof IntegerAddExactOverflowNode,
                            "Expected exact add-overflow condition in %s", overflowGuard);
            return ((IntegerAddExactOverflowNode) overflowGuard.getCondition()).getY().asJavaConstant().asLong();
        }

        /**
         * Creates one temporary overflow guard for the supplied exact-overflow condition.
         */
        private GuardNode materializeOverflowGuard(GuardNode hoistingCandidateGuard, CountedLoopInfo countedLoop, LogicNode overflowCondition) {
            GuardNode overflowGuard;
            if (overflowConditionGuardMaterializer != null) {
                overflowGuard = overflowConditionGuardMaterializer.materializeOverflowGuard(hoistingCandidateGuard, overflowCondition);
            } else {
                GuardingNode guardingNode = countedLoop.createOverflowGuard(overflowCondition, hoistingCandidateGuard.getReason());
                GraalError.guarantee(guardingNode == null || guardingNode instanceof GuardNode, "Expected GuardNode but got %s", guardingNode);
                overflowGuard = (GuardNode) guardingNode;
            }
            return overflowGuard != null ? overflowGuard : alwaysOverflowingGuard(hoistingCandidateGuard);
        }

        /**
         * Creates a temporary guard that represents an always-overflowing condition and therefore
         * blocks hoisting of a compare. Guard metadata is copied from
         * {@code hoistingCandidateGuard}.
         */
        private static GuardNode alwaysOverflowingGuard(GuardNode hoistingCandidateGuard) {
            return new GuardNode(LogicConstantNode.tautology(), hoistingCandidateGuard.getAnchor(), hoistingCandidateGuard.getReason(), hoistingCandidateGuard.getAction(), true,
                            hoistingCandidateGuard.getSpeculation(), hoistingCandidateGuard.getNoDeoptSuccessorPosition());
        }

        /**
         * Returns whether {@code overflowGuard} is the sentinel guard for an always-overflowing
         * condition.
         */
        private static boolean isAlwaysOverflowingGuard(GuardNode overflowGuard) {
            return overflowGuard != null && overflowGuard.isNegated() && overflowGuard.getCondition().isTautology();
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
