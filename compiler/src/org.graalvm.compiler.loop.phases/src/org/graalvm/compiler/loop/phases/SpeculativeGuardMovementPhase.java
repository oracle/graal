/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.loop.phases;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.cfg.AbstractControlFlowGraph;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.GuardNode;
import org.graalvm.compiler.nodes.GuardedValueNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ProfileData.BranchProbabilityData;
import org.graalvm.compiler.nodes.ProfileData.ProfileSource;
import org.graalvm.compiler.nodes.ShortCircuitOrNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.StageFlag;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.WithExceptionNode;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.calc.IntegerBelowNode;
import org.graalvm.compiler.nodes.calc.IntegerConvertNode;
import org.graalvm.compiler.nodes.calc.IntegerDivRemNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.extended.AnchoringNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.extended.MultiGuardNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.loop.CountedLoopInfo;
import org.graalvm.compiler.nodes.loop.InductionVariable;
import org.graalvm.compiler.nodes.loop.InductionVariable.Direction;
import org.graalvm.compiler.nodes.loop.LoopEx;
import org.graalvm.compiler.nodes.loop.LoopsData;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.tiers.MidTierContext;
import org.graalvm.compiler.serviceprovider.SpeculationReasonGroup;

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
 *     for (int i = 0; i < ints.length; i++) {
 *         if (ints[i] < 0) {
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
public class SpeculativeGuardMovementPhase extends BasePhase<MidTierContext> {

    @Override
    public float codeSizeIncrease() {
        return 2.0f;
    }

    @Override
    protected void run(StructuredGraph graph, MidTierContext context) {
        try {
            if (!graph.getGuardsStage().allowsFloatingGuards()) {
                return;
            }
            LoopsData loops = context.getLoopsDataProvider().getLoopsData(graph);
            loops.detectedCountedLoops();
            performSpeculativeGuardMovement(context, graph, loops);
        } finally {
            graph.setAfterStage(StageFlag.GUARD_MOVEMENT);
        }
    }

    protected static void performSpeculativeGuardMovement(MidTierContext context, StructuredGraph graph, LoopsData loops) {
        new SpeculativeGuardMovement(loops, graph.createNodeMap(), graph, context.getProfilingInfo(), graph.getSpeculationLog()).run();
    }

    private static class SpeculativeGuardMovement implements Runnable {

        private final LoopsData loops;
        private final NodeMap<Block> earliestCache;
        private final StructuredGraph graph;
        private final ProfilingInfo profilingInfo;
        private final SpeculationLog speculationLog;

        SpeculativeGuardMovement(LoopsData loops, NodeMap<Block> earliestCache, StructuredGraph graph, ProfilingInfo profilingInfo, SpeculationLog speculationLog) {
            this.loops = loops;
            this.earliestCache = earliestCache;
            this.graph = graph;
            this.profilingInfo = profilingInfo;
            this.speculationLog = speculationLog;
        }

        @Override
        public void run() {
            for (GuardNode guard : graph.getNodes(GuardNode.TYPE)) {
                earliestBlock(guard);
                graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After processing guard %s", guard);
            }
        }

        /**
         * Determines the earliest block in which the given node can be scheduled.
         */
        private Block earliestBlock(Node node) {
            ControlFlowGraph cfg = loops.getCFG();
            Block earliest = earliestCache.getAndGrow(node);
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

                Loop<Block> forcedHoisting = null;
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

        private Block computeEarliestBlock(Node node) {
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

            Block earliest = null;
            for (Node input : node.inputs().snapshot()) {
                if (input != null) {
                    assert input instanceof ValueNode;
                    Block inputEarliest;
                    if (input instanceof WithExceptionNode) {
                        inputEarliest = cfg.getNodeToBlock().get(((WithExceptionNode) input).next());
                    } else {
                        inputEarliest = earliestBlock(input);
                    }
                    earliest = (earliest == null || AbstractControlFlowGraph.strictlyDominates(earliest, inputEarliest)) ? inputEarliest : earliest;
                }
            }
            if (earliest == null) {
                earliest = cfg.getStartBlock();
            }
            return earliest;
        }

        private Loop<Block> tryOptimizeCompare(GuardNode guard, CompareNode compare) {
            assert compare instanceof IntegerLessThanNode || compare instanceof IntegerBelowNode;
            assert !compare.usages().filter(GuardNode.class).isEmpty();
            InductionVariable ivX = loops.getInductionVariable(compare.getX());
            InductionVariable ivY = loops.getInductionVariable(compare.getY());
            if (ivX == null && ivY == null) {
                return null;
            }

            InductionVariable iv;
            InductionVariable otherIV;
            ValueNode bound;
            boolean mirrored;
            if (ivX == null || (ivY != null && ivY.getLoop().loop().getDepth() > ivX.getLoop().loop().getDepth())) {
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
                return iv.getLoop().loop();
            }
            if (otherIV != null) {
                if (tryOptimizeCompare(compare, otherIV, iv.valueNode(), !mirrored, guard)) {
                    return otherIV.getLoop().loop();
                }
            }

            return null;
        }

        private boolean tryOptimizeCompare(CompareNode compare, InductionVariable iv, ValueNode bound, boolean mirrored, GuardNode guard) {
            if (shouldOptimizeCompare(iv, bound, guard)) {
                optimizeCompare(compare, iv, bound, mirrored, guard);
                return true;
            }
            return false;
        }

        private void optimizeCompare(CompareNode compare, InductionVariable iv, ValueNode bound, boolean mirrored, GuardNode guard) {
            bound.getDebug().log("optimizeCompare(%s, %s, %s, %b) in %s", compare, iv, bound, mirrored, graph.method());
            CountedLoopInfo countedLoop = iv.getLoop().counted();
            GuardingNode overflowGuard = countedLoop.getOverFlowGuard();
            ValueNode longBound = IntegerConvertNode.convert(bound, StampFactory.forKind(JavaKind.Long), graph, NodeView.DEFAULT);
            LogicNode newCompare;
            ValueNode extremum = iv.extremumNode(true, StampFactory.forKind(JavaKind.Long));
            GuardedValueNode guardedExtremum = graph.unique(new GuardedValueNode(extremum, overflowGuard));
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
            LogicNode test1;
            LogicNode test2;
            if (compare instanceof IntegerBelowNode) {
                test1 = graph.unique(new IntegerBelowNode(x1, y1));
                test2 = graph.unique(new IntegerBelowNode(x2, y2));
            } else {
                assert compare instanceof IntegerLessThanNode;
                test1 = graph.unique(new IntegerLessThanNode(x1, y1));
                test2 = graph.unique(new IntegerLessThanNode(x2, y2));
            }
            newCompare = ShortCircuitOrNode.and(test1, guard.isNegated(), test2, guard.isNegated(), BranchProbabilityData.unknown());

            /*
             * the fact that the guard was negated was integrated in the ShortCircuitOr so it needs
             * to be reset here
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
                    assert direction == Direction.Down;
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
                    assert direction == Direction.Down;
                    // start <= limit || newCompare
                    x = limit;
                    y = start;
                }
            }
            LogicNode compare = countedLoop.getCounterIntegerHelper().createCompareNode(x, y, NodeView.DEFAULT);
            return graph.addOrUniqueWithInputs(new ShortCircuitOrNode(compare, !limitIncluded, newCompare, false, BranchProbabilityData.unknown()));
        }

        private static boolean shouldHoistBasedOnFrequency(Block anchorBlock, Block proposedNewAnchor) {
            return proposedNewAnchor.getRelativeFrequency() <= anchorBlock.getRelativeFrequency();
        }

        private boolean shouldOptimizeCompare(InductionVariable iv, ValueNode bound, GuardNode guard) {
            DebugContext debug = guard.getDebug();
            if (!iv.getLoop().isCounted()) {
                debug.log("shouldOptimizeCompare(%s):not a counted loop", guard);
                return false;
            }

            LoopEx loopEx = iv.getLoop();
            Loop<Block> ivLoop = loopEx.loop();
            Block guardAnchorBlock = earliestBlock(guard.getAnchor().asNode());
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
                if (!AbstractControlFlowGraph.dominates(earliestBlock(iv.getLoop().counted().getBody()), guardAnchorBlock)) {
                    // determine if the condition is inside the loop
                    if (!iv.getLoop().whole().contains(guard.getCondition())) {
                        return false;
                    }
                }
            } else {
                if (!AbstractControlFlowGraph.dominates(earliestBlock(iv.getLoop().counted().getBody()), guardAnchorBlock)) {
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

            if (profilingInfo != null && !(profilingInfo instanceof DefaultProfilingInfo)) {
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
                if (ProfileSource.isTrusted(loopEx.localFrequencySource()) &&
                                loopEx.localLoopFrequency() < loopFreqThreshold) {
                    debug.log("shouldOptimizeCompare(%s):loop frequency too low.", guard);
                    // loop frequency is too low -- the complexity introduced by hoisting this guard
                    // will not pay off.
                    return false;
                }
            }

            Loop<Block> l = guardAnchorBlock.getLoop();
            if (isInverted(loopEx)) {
                // guard is anchored outside the loop but the condition might still be in the loop
                l = iv.getLoop().loop();
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
             * the loop, thus w still want to try hoisting the guard.
             */
            if (!isInverted(iv.getLoop()) && !AbstractControlFlowGraph.dominates(guardAnchorBlock, iv.getLoop().loop().getHeader())) {
                if (!shouldHoistBasedOnFrequency(guardAnchorBlock, ivLoop.getHeader().getDominator())) {
                    debug.log("hoisting is not beneficial based on fequency", guard);
                    return false;
                }
            }

            Stamp boundStamp = bound.stamp(NodeView.DEFAULT);
            Stamp ivStamp = iv.valueNode().stamp(NodeView.DEFAULT);
            if (boundStamp instanceof IntegerStamp && ivStamp instanceof IntegerStamp) {
                IntegerStamp integerBoundStamp = (IntegerStamp) boundStamp;
                IntegerStamp integerIvStamp = (IntegerStamp) ivStamp;
                if (fitsIn32Bit(integerBoundStamp) && fitsIn32Bit(integerIvStamp)) {
                    return true;
                }
            }

            debug.log("shouldOptimizeCompare(%s): bound or iv does not fit in int", guard);
            return false; // only ints are supported (so that the overflow fits in longs)
        }

        private static boolean fitsIn32Bit(IntegerStamp stamp) {
            return NumUtil.isUInt(stamp.upMask());
        }

        private Loop<Block> tryOptimizeInstanceOf(GuardNode guard, InstanceOfNode compare) {
            AnchoringNode anchor = compare.getAnchor();
            if (anchor == null) {
                return null;
            }
            Block anchorBlock = earliestBlock(anchor.asNode());
            if (anchorBlock.getLoop() == null) {
                return null;
            }
            Block valueBlock = earliestBlock(compare.getValue());
            Loop<Block> hoistAbove = findInstanceOfLoopHoisting(guard, anchorBlock, valueBlock);
            if (hoistAbove != null) {
                compare.setProfile(compare.profile(), hoistAbove.getHeader().getDominator().getBeginNode());
                return hoistAbove;
            }
            return null;
        }

        private Loop<Block> findInstanceOfLoopHoisting(GuardNode guard, Block anchorBlock, Block valueBlock) {
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
            if (!AbstractControlFlowGraph.dominates(valueBlock, anchorBlock)) {
                // this can happen when the value comes from *after* the exit of the anchor loop
                debug.log("shouldOptimizeInstanceOf(%s): value block does not dominate loop header", guard);
                return null;
            }
            if (!allowsSpeculativeGuardMovement(guard.getReason(), (LoopBeginNode) anchorBlock.getLoop().getHeader().getBeginNode(), true)) {
                debug.log("shouldOptimizeInstanceOf(%s): The guard would not hoist", guard);
                return null; // the guard would not hoist, don't hoist the compare
            }

            Loop<Block> result = anchorBlock.getLoop();
            while (result.getParent() != valueBlock.getLoop()) {
                result = result.getParent();
            }
            return result;
        }

        private Block earliestBlockForGuard(GuardNode guard, Loop<Block> forcedHoisting) {
            DebugContext debug = guard.getDebug();
            Node anchor = guard.getAnchor().asNode();
            assert guard.inputs().count() == 2;
            Block conditionEarliest = earliestBlock(guard.getCondition());

            Block anchorEarliest = earliestBlock(anchor);
            Block newAnchorEarliest = null;
            LoopBeginNode outerMostExitedLoop = null;
            Block b = anchorEarliest;

            if (forcedHoisting != null) {
                newAnchorEarliest = forcedHoisting.getHeader().getDominator();
                outerMostExitedLoop = (LoopBeginNode) forcedHoisting.getHeader().getBeginNode();
                b = newAnchorEarliest;
            }

            debug.log("earliestBlockForGuard(%s) inital anchor : %s, condition : %s condition's earliest %s", guard, anchor, guard.getCondition(), conditionEarliest.getBeginNode());

            double minFrequency = anchorEarliest.getRelativeFrequency();

            while (AbstractControlFlowGraph.strictlyDominates(conditionEarliest, b)) {
                Block candidateAnchor = b.getDominatorSkipLoops();
                assert candidateAnchor.getLoopDepth() <= anchorEarliest.getLoopDepth() : " candidate anchor block at begin node " + candidateAnchor.getBeginNode() + " earliest anchor block " +
                                anchorEarliest.getBeginNode() + " loop depth is not smaller equal for guard " + guard;

                if (b.isLoopHeader() && (newAnchorEarliest == null || candidateAnchor.getLoopDepth() < newAnchorEarliest.getLoopDepth())) {
                    LoopBeginNode loopBegin = (LoopBeginNode) b.getBeginNode();
                    if (!allowsSpeculativeGuardMovement(guard.getReason(), loopBegin, true)) {
                        break;
                    } else {
                        double relativeFrequency = candidateAnchor.getRelativeFrequency();
                        if (relativeFrequency <= minFrequency) {
                            debug.log("earliestBlockForGuard(%s) hoisting above %s", guard, loopBegin);
                            outerMostExitedLoop = loopBegin;
                            newAnchorEarliest = candidateAnchor;
                            minFrequency = relativeFrequency;
                        } else {
                            debug.log("earliestBlockForGuard(%s) %s not worth it, old relative frequency %f, new relative frequency %f", guard, loopBegin, minFrequency, relativeFrequency);
                        }
                    }
                }
                b = candidateAnchor;
            }

            if (newAnchorEarliest != null && allowsSpeculativeGuardMovement(guard.getReason(), outerMostExitedLoop, false)) {
                AnchoringNode newAnchor = newAnchorEarliest.getBeginNode();
                guard.setAnchor(newAnchor);
                debug.log("New earliest : %s, anchor is %s, update guard", newAnchorEarliest.getBeginNode(), anchor);
                Block earliest = newAnchorEarliest;
                if (guard.getAction() == DeoptimizationAction.None) {
                    guard.setAction(DeoptimizationAction.InvalidateRecompile);
                }
                guard.setSpeculation(registerSpeculativeGuardMovement(guard.getReason(), outerMostExitedLoop));
                debug.log("Exited %d loops for %s %s in %s", anchorEarliest.getLoopDepth() - earliest.getLoopDepth(), guard, guard.getCondition(), graph.method());
                return earliest;
            } else {
                debug.log("Keep normal anchor edge");
                return AbstractControlFlowGraph.strictlyDominates(conditionEarliest, anchorEarliest) ? anchorEarliest : conditionEarliest;
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
            if (profilingInfo == null) {
                return false;
            }
            if (checkDeoptimizationCount) {
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

    private static boolean isInverted(LoopEx loop) {
        return loop.isCounted() && loop.counted().isInverted();
    }
}
