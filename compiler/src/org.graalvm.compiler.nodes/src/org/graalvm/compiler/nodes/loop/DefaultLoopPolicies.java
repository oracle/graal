/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.loop;

import static org.graalvm.compiler.core.common.GraalOptions.LoopMaxUnswitch;
import static org.graalvm.compiler.core.common.GraalOptions.MaximumDesiredSize;
import static org.graalvm.compiler.core.common.GraalOptions.MinimumPeelFrequency;
import static org.graalvm.compiler.nodes.loop.DefaultLoopPolicies.Options.ExactFullUnrollMaxNodes;
import static org.graalvm.compiler.nodes.loop.DefaultLoopPolicies.Options.ExactPartialUnrollMaxNodes;
import static org.graalvm.compiler.nodes.loop.DefaultLoopPolicies.Options.FullUnrollConstantCompareBoost;
import static org.graalvm.compiler.nodes.loop.DefaultLoopPolicies.Options.FullUnrollMaxIterations;
import static org.graalvm.compiler.nodes.loop.DefaultLoopPolicies.Options.FullUnrollMaxNodes;
import static org.graalvm.compiler.nodes.loop.DefaultLoopPolicies.Options.LoopUnswitchFrequencyBoost;
import static org.graalvm.compiler.nodes.loop.DefaultLoopPolicies.Options.LoopUnswitchFrequencyMaxFactor;
import static org.graalvm.compiler.nodes.loop.DefaultLoopPolicies.Options.LoopUnswitchFrequencyMinFactor;
import static org.graalvm.compiler.nodes.loop.DefaultLoopPolicies.Options.LoopUnswitchMaxIncrease;
import static org.graalvm.compiler.nodes.loop.DefaultLoopPolicies.Options.LoopUnswitchMinSplitFrequency;
import static org.graalvm.compiler.nodes.loop.DefaultLoopPolicies.Options.LoopUnswitchTrivial;
import static org.graalvm.compiler.nodes.loop.DefaultLoopPolicies.Options.UnrollMaxIterations;

import java.util.List;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.core.common.util.UnsignedLong;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.ProfileData.ProfileSource;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.debug.ControlFlowAnchorNode;
import org.graalvm.compiler.nodes.extended.ForeignCall;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;

public class DefaultLoopPolicies implements LoopPolicies {

    public static class Options {
        // @formatter:off
        @Option(help = "Maximum loop unswiching code size increase in nodes", type = OptionType.Expert)
        public static final OptionKey<Integer> LoopUnswitchMaxIncrease = new OptionKey<>(2000);
        @Option(help = "Number of nodes allowed for a loop unswitching regardless of the loop frequency", type = OptionType.Expert)
        public static final OptionKey<Integer> LoopUnswitchTrivial = new OptionKey<>(10);
        @Option(help = "Number of nodes allowed for a loop unswitching per the loop frequency", type = OptionType.Expert)
        public static final OptionKey<Double> LoopUnswitchFrequencyBoost = new OptionKey<>(10.0);
        @Option(help = "Minimum value for the frequency factor of an invariant", type = OptionType.Expert)
        public static final OptionKey<Double> LoopUnswitchFrequencyMinFactor = new OptionKey<>(0.05);
        @Option(help = "Maximun value for the frequency factor of an invariant", type = OptionType.Expert)
        public static final OptionKey<Double> LoopUnswitchFrequencyMaxFactor = new OptionKey<>(0.95);
        @Option(help = "Lower bound for the minimun frequency of an invariant condition to be unswitched", type = OptionType.Expert)
        public static final OptionKey<Double> LoopUnswitchMinSplitFrequency = new OptionKey<>(1.0);

        @Option(help = "", type = OptionType.Expert) public static final OptionKey<Integer> FullUnrollMaxNodes = new OptionKey<>(400);
        @Option(help = "", type = OptionType.Expert) public static final OptionKey<Integer> FullUnrollConstantCompareBoost = new OptionKey<>(15);
        @Option(help = "", type = OptionType.Expert) public static final OptionKey<Integer> FullUnrollMaxIterations = new OptionKey<>(600);
        @Option(help = "", type = OptionType.Expert) public static final OptionKey<Integer> ExactFullUnrollMaxNodes = new OptionKey<>(800);
        @Option(help = "", type = OptionType.Expert) public static final OptionKey<Integer> ExactPartialUnrollMaxNodes = new OptionKey<>(200);

        @Option(help = "", type = OptionType.Expert) public static final OptionKey<Integer> UnrollMaxIterations = new OptionKey<>(16);
        // @formatter:on
    }

    @Override
    public boolean shouldPeel(LoopEx loop, ControlFlowGraph cfg, CoreProviders providers, int peelingIteration) {
        if (peelingIteration > 0) {
            // Do not do iterative peeling by default.
            return false;
        }
        LoopBeginNode loopBegin = loop.loopBegin();
        double entryProbability = cfg.blockFor(loopBegin.forwardEnd()).getRelativeFrequency();
        StructuredGraph graph = cfg.graph;
        OptionValues options = graph.getOptions();

        if (entryProbability < MinimumPeelFrequency.getValue(options)) {
            return false;
        }

        if (loop.parent() != null) {
            if (loop.size() > loop.parent().size() >> 1) {
                // This loops make up more than half of the parent loop in terms of number of nodes.
                // There is a risk that this loop unproportionally increases parent loop body size.
                return false;
            }
        }

        if (loop.loop().getChildren().size() > 0) {
            // This loop has child loops. Loop peeling could explode graph size.
            return false;
        }

        if (loop.size() + graph.getNodeCount() > MaximumDesiredSize.getValue(options)) {
            // We are out of budget for peeling.
            return false;
        }

        return true;
    }

    /**
     * This enum represents the result of analyzing a loop for full unrolling. It is used by
     * {@link #canFullUnroll} to communicate to its caller whether a loop should be fully unrolled,
     * and if not, why not.
     */
    public enum FullUnrollability {
        /** We can and should fully unroll this loop. */
        SHOULD_FULL_UNROLL,
        /**
         * We cannot fully unroll this loop because it is not a counted loop with a constant max
         * trip count, or the counter might overflow.
         */
        NOT_COUNTED,
        /** We cannot fully unroll this loop because we cannot duplicate it. */
        MUST_NOT_DUPLICATE,
        /**
         * We could fully unroll this loop, but we should not because it has more iterations than
         * allowed by the full unrolling options.
         */
        TOO_MANY_ITERATIONS,
        /**
         * We could fully unroll this loop, but we should not because this would exceed a global or
         * per-loop size limit.
         */
        TOO_LARGE,
    }

    /**
     * Determine whether the loop should be fully unrolled. Returns
     * {@link FullUnrollability#SHOULD_FULL_UNROLL} if the loop should be fully unrolled. Otherwise,
     * returns some other member of {@link FullUnrollability} describing why the loop cannot or
     * should not be fully unrolled.
     */
    public FullUnrollability canFullUnroll(LoopEx loop) {
        if (!loop.isCounted() || !loop.counted().isConstantMaxTripCount() || !loop.counted().counterNeverOverflows()) {
            return FullUnrollability.NOT_COUNTED;
        }
        if (!loop.canDuplicateLoop()) {
            return FullUnrollability.MUST_NOT_DUPLICATE;
        }
        OptionValues options = loop.entryPoint().getOptions();
        CountedLoopInfo counted = loop.counted();
        UnsignedLong maxTrips = counted.constantMaxTripCount();
        if (maxTrips.equals(0)) {
            return FullUnrollability.SHOULD_FULL_UNROLL;
        }
        if (maxTrips.isGreaterThan(FullUnrollMaxIterations.getValue(options))) {
            return FullUnrollability.TOO_MANY_ITERATIONS;
        }
        int globalMax = MaximumDesiredSize.getValue(options) - loop.loopBegin().graph().getNodeCount();
        if (globalMax <= 0) {
            return FullUnrollability.TOO_LARGE;
        }
        int maxNodes = counted.isExactTripCount() ? ExactFullUnrollMaxNodes.getValue(options) : FullUnrollMaxNodes.getValue(options);
        for (Node usage : counted.getLimitCheckedIV().valueNode().usages()) {
            if (usage instanceof CompareNode) {
                CompareNode compare = (CompareNode) usage;
                if (compare.getY().isConstant()) {
                    maxNodes += FullUnrollConstantCompareBoost.getValue(options);
                }
            }
        }
        maxNodes = Math.min(maxNodes, globalMax);
        int size = loop.inside().nodes().count();
        size -= 2; // remove the counted if and its non-exit begin
        size -= loop.loopBegin().loopEnds().count();
        GraalError.guarantee(size >= 0, "Wrong size");
        /* @formatter:off
         * The check below should not throw ArithmeticException because:
         * maxTrips is guaranteed to be >= 1 by the check above
         * - maxTrips * size can not overfow because:
         *   - maxTrips <= FullUnrollMaxIterations <= Integer.MAX_VALUE
         *   - 1 <= size <= Integer.MAX_VALUE
         * @formatter:on
         */
        if (maxTrips.minus(1).times(size).isLessOrEqualTo(maxNodes)) {
            // check whether we're allowed to unroll this loop
            return FullUnrollability.SHOULD_FULL_UNROLL;
        } else {
            return FullUnrollability.TOO_LARGE;
        }
    }

    @Override
    public boolean shouldFullUnroll(LoopEx loop) {
        return canFullUnroll(loop) == FullUnrollability.SHOULD_FULL_UNROLL;
    }

    @Override
    public boolean shouldPartiallyUnroll(LoopEx loop, CoreProviders providers) {
        LoopBeginNode loopBegin = loop.loopBegin();
        if (!loop.isCounted()) {
            loopBegin.getDebug().log(DebugContext.VERBOSE_LEVEL, "shouldPartiallyUnroll %s isn't counted", loopBegin);
            return false;
        }
        OptionValues options = loop.entryPoint().getOptions();
        int maxNodes = ExactPartialUnrollMaxNodes.getValue(options);
        maxNodes = Math.min(maxNodes, Math.max(0, MaximumDesiredSize.getValue(options) - loop.loopBegin().graph().getNodeCount()));
        int size = Math.max(1, loop.size() - 1 - loop.loopBegin().phis().count());
        int unrollFactor = loopBegin.getUnrollFactor();
        if (unrollFactor == 1) {
            double loopFrequency = loop.localLoopFrequency();
            if (loopBegin.isSimpleLoop() && loopFrequency < 5.0) {
                loopBegin.getDebug().log(DebugContext.VERBOSE_LEVEL, "shouldPartiallyUnroll %s frequency too low %s ", loopBegin, loopFrequency);
                return false;
            }
            loopBegin.setLoopOrigFrequency(loopFrequency);
        }
        int maxUnroll = UnrollMaxIterations.getValue(options);
        // Now correct size for the next unroll. UnrollMaxIterations == 1 means perform the
        // pre/main/post transformation but don't actually unroll the main loop.
        size += size;
        if (maxUnroll == 1 && loopBegin.isSimpleLoop() || size <= maxNodes && unrollFactor < maxUnroll) {
            // Will the next unroll fit?
            if ((int) loopBegin.loopOrigFrequency() < (unrollFactor * 2)) {
                return false;
            }
            // Check whether we're allowed to unroll this loop
            for (Node node : loop.inside().nodes()) {
                if (node instanceof ControlFlowAnchorNode) {
                    return false;
                }
                if (node instanceof Invoke || node instanceof ForeignCall) {
                    return false;
                }
            }
            return true;
        } else {
            loopBegin.getDebug().log(DebugContext.VERBOSE_LEVEL, "shouldPartiallyUnroll %s unrolled loop is too large %s ", loopBegin, size);
            return false;
        }
    }

    @Override
    public boolean shouldTryUnswitch(LoopEx loop) {
        LoopBeginNode loopBegin = loop.loopBegin();
        double loopFrequency = loop.localLoopFrequency();
        if (loopFrequency <= 1.0) {
            return false;
        }
        OptionValues options = loop.entryPoint().getOptions();
        return loopBegin.unswitches() < LoopMaxUnswitch.getValue(options);
    }

    /**
     * Compute an approximation of the change in the number of nodes of the graph if the given
     * control split nodes are unswitched.
     *
     * @param loop the loop to unswitch.
     * @param controlSplits the control split nodes to consider.
     * @return the approximate code size change in nodes.
     */
    private static int approxCodeSizeChange(LoopEx loop, List<ControlSplitNode> controlSplits) {
        StructuredGraph graph = loop.loopBegin().graph();
        NodeBitMap branchNodes = graph.createNodeBitMap();
        for (ControlSplitNode controlSplit : controlSplits) {
            for (Node successor : controlSplit.successors()) {
                AbstractBeginNode branch = (AbstractBeginNode) successor;
                // this may count twice because of fall-through in switches
                loop.nodesInLoopBranch(branchNodes, branch);
            }
        }
        int inBranchTotal = branchNodes.count();

        int loopTotal = loop.size();
        int diff = (loopTotal - inBranchTotal);

        ControlSplitNode firstSplit = controlSplits.get(0);
        int copies = firstSplit.successors().count() - 1;
        diff = diff * copies;

        return diff;
    }

    /**
     * Compute the sum of the local frequencies ({@link AbstractBlockBase#getRelativeFrequency()})
     * of the control split nodes. If a control split node is within an inner loop then its
     * frequency is divided by the local loop frequencies of the inner loops.
     *
     * The result should be between 0 and {@code controlSplits.size()} times the local loop
     * frequency.
     *
     * Lets take the following code :
     *
     * <pre>
     *  for (int i = 0; i < 1000; ++i) {
     *      // Loop1, loop local frequency = 1000
     *      if (i % 2 == 0) {
     *          // relative frequency = 500
     *          if (invariant (1) ) { [...] } else { [...] }
     *      } else {
     *          // relative frequency = 500
     *          if (i % 4 == 1) {
     *              // relative frequency = 250
     *              for (int j = 0; j < 10; ++j) {
     *                  // Loop2, loop local frequency = 10, relative frequency = 2500
     *                  if (invariant (2) ) { [...] } else { [...] }
     *              }
     *          } else { [...] }
     *      }
     *  }
     * </pre>
     *
     * Loop1 has a local loop frequency of 1000, invariant (1) is reached 500 times, invariant (2)
     * 2500 times as loop2 has a local loop frequency of 10. Invariant (2) could be unswitched from
     * loop2 so we only count a frequency of 250 for it. Finally the result is the sum of the two
     * frequencies which is 750. This mean that on average each time the loop is executed, the
     * invariant is computed 750 times.
     */
    private static double splitLocalLoopFrequency(LoopEx loop, List<ControlSplitNode> controlSplits) {
        int loopDepth = loop.loop().getDepth();
        double loopLocalFrequency = loop.localLoopFrequency();
        ControlFlowGraph cfg = loop.loopsData().getCFG();
        double loopRelativeFrequency = cfg.blockFor(loop.loopBegin()).getRelativeFrequency();

        double freq = 0.0;
        for (ControlSplitNode node : controlSplits) {
            Block b = cfg.blockFor(node);
            double f = b.getRelativeFrequency();
            for (Loop<Block> l = b.getLoop(); l.getDepth() > loopDepth; l = l.getParent()) {
                f /= loop.loopsData().loop(l).localLoopFrequency();
            }

            freq += f;
        }

        return freq / loopRelativeFrequency * loopLocalFrequency;
    }

    /**
     * Compute the maximum code size change in the number of nodes for the given loop. It is used by
     * the unswitching heuristic to prevent the CFG to become too big.
     *
     * @param loop the loop to unswitch
     * @return the maximum code size change (in the number of nodes)
     */
    private static int loopMaxCodeSizeChange(LoopEx loop) {
        StructuredGraph graph = loop.loopBegin().graph();

        double loopFrequency = loop.localLoopFrequency();
        OptionValues options = loop.loopBegin().getOptions();
        /* When a loop has a greater local loop frequency we allow a bigger change in code size */
        int maxDiff = LoopUnswitchTrivial.getValue(options) + (int) (LoopUnswitchFrequencyBoost.getValue(options) * (loopFrequency - 1.0));

        maxDiff = Math.min(maxDiff, LoopUnswitchMaxIncrease.getValue(options));
        int remainingGraphSpace = MaximumDesiredSize.getValue(options) - graph.getNodeCount();
        return Math.min(maxDiff, remainingGraphSpace);
    }

    @Override
    public UnswitchingDecision shouldUnswitch(LoopEx loop, List<List<ControlSplitNode>> controlSplits) {
        if (loop.loopBegin().unswitches() >= LoopMaxUnswitch.getValue(loop.loopBegin().graph().getOptions())) {
            return UnswitchingDecision.NO;
        }
        // check whether we're allowed to unswitch this loop
        if (!loop.canDuplicateLoop()) {
            return UnswitchingDecision.NO;
        }

        DebugContext debug = loop.loopBegin().getDebug();
        OptionValues options = debug.getOptions();
        double localLoopFrequency = loop.localLoopFrequency();
        int loopSize = loop.size();
        int loopMaxCodeSizeChange = loopMaxCodeSizeChange(loop);

        // We prioritize invariant conditions with the highest frequency
        List<ControlSplitNode> bestSplit = null;
        double bestSplitFrequency = 0.0;
        int bestCodeSizeChange = 0;
        double bestFactor = 0.0;
        for (List<ControlSplitNode> split : controlSplits) {
            boolean isTrusted = true;
            for (ControlSplitNode node : split) {
                isTrusted = isTrusted && ProfileSource.isTrusted(node.getProfileData().getProfileSource());
            }
            if (!isTrusted) {
                /**
                 * If any of the nodes has an untrusted profile source, we don't unswitch it as its
                 * probabilities are unknown.
                 */
                debug.log("control split %s discarded because unknown profile data", split);
            }

            int approxCodeSizeChange = approxCodeSizeChange(loop, split);
            if (approxCodeSizeChange > loopMaxCodeSizeChange) {
                /*
                 * To prevent the code to become too big, invariant that generate big code size
                 * changes are discarded.
                 */
                debug.log("control split %s discarded because the code size difference is too big, loop size=%d, loop f=%.2f, max diff=%d, diff=%d, relative code size diff=%d%%", split, loopSize,
                                localLoopFrequency, loopMaxCodeSizeChange, approxCodeSizeChange, (int) (100.0 * approxCodeSizeChange / loopSize));
                continue;
            }

            double splitFrequency = splitLocalLoopFrequency(loop, split);
            if (splitFrequency < LoopUnswitchMinSplitFrequency.getValue(options)) {
                /*
                 * When a invariant is unswitched then it is computed each time the loop is executed
                 * so we only want to unswitch conditions that are on average executed at least once
                 * par execution of the whole loop.
                 */
                debug.log("control split %s discarded because infrequent, f=%.2f", split, splitFrequency);
                continue;
            }

            /**
             * Guards should only be unswitched if they are executed very often because otherwise it
             * might change the hottest path. However if the successor probabilities of the control
             * split nodes are evenly distributed then it is good to unswitch them.
             *
             * {@link factor} is 0 for guards and 1 for evenly distributed splits.
             *
             * The idea behind this heuristic is that when a guard is executed often, unswitching it
             * will not change the hottest path. For example:
             *
             * <pre>
             *  Object o = [...];
             *  for (int i = 0; i < 1000; ++i) {
             *      if (very_likely(i)) {
             *          if (o == null) { Deop } else { [.1.] }
             *      } else { [.2.] }
             *  }
             * </pre>
             *
             * The hottest path goes trough the block 1 and as the invariant is computed frequently
             * we can be confident that it is most of the time true. Thus after unswitching it:
             *
             * <pre>
             *  Object o = [...];
             *  if (o == null) {
             *      for (int i = 0; i < 1000; ++i) {
             *          if (very_likely(i)) { Deop } else { [.2.] }
             *      }
             *  } else {
             *      for (int i = 0; i < 1000; ++i) {
             *          if (very_likely(i)) { [.1.] } else { [.2.] }
             *      }
             *  }
             * </pre>
             *
             * The hottest path still goes through block [.1.].
             *
             * On the other hand when an invariant is not frequently computed, it is possible that
             * it is not independent from outer conditions. This can specially happens for guards,
             * as usually programmers avoid null pointer exceptions but the compiler cannot check
             * that it will not happen. Thus their profile data might not be correct after
             * unswitching and so it might change the hottest path.
             *
             * The initial value of factor is computed such that if all the successor probabilities
             * are equals (i.e. 1 / successorCount) then its final value is 1 and the its inverted
             * and capped value is close to 0 meaning that the invariant can have any frequency and
             * it will be unswitched.
             *
             * Let's consider the previous program with a probability of 0.1 for the null check. The
             * initial value of factor is 2^(2*1) = 4 and its final value is 4 * 0.9 * 0.1 = 0.36.
             * Then its inverted and capped value is 1 - 0.36 = 0.64 meaning that for this invariant
             * to be unswitched is must be computed during more that 64% of the loop's iterations.
             */
            double factor = Math.pow(split.get(0).getSuccessorCount(), split.get(0).getSuccessorCount() * split.size());
            for (ControlSplitNode s : split) {
                for (double p : s.successorProbabilities()) {
                    factor *= p;
                }
            }
            assert 0 <= factor && factor <= 1 : "factor shold be between 0 and 1, but is : " + factor;

            // We cap the factor and we invert it to make guards' range narrow.
            double cappedFactor = 1 - Math.min(Math.max(factor, LoopUnswitchFrequencyMinFactor.getValue(options)), LoopUnswitchFrequencyMaxFactor.getValue(options));

            if (splitFrequency < cappedFactor * localLoopFrequency) {
                /*
                 * Invariants that are executed not often with respect to their successor
                 * probabilities are discarded as they might change the hottest path.
                 */
                debug.log("control split %s not frequenct enough with respect to factor, factor=%.2f, split f=%.2f, loop f=%.2f", split, cappedFactor, splitFrequency, localLoopFrequency);
                continue;
            }

            if (splitFrequency > bestSplitFrequency) {
                bestSplitFrequency = splitFrequency;
                bestSplit = split;
                bestCodeSizeChange = approxCodeSizeChange;
                bestFactor = cappedFactor;
            }
        }

        if (bestSplit != null) {
            debug.log("shouldUnswitch(%s, %s) : best=%s, loop size=%d, f=%.2f, max=%d, delta=%d, invariant f=%.2f, factor=%.2f", loop, controlSplits, bestSplit, loopSize, localLoopFrequency,
                            loopMaxCodeSizeChange,
                            bestCodeSizeChange,
                            bestSplitFrequency, bestFactor);

            return UnswitchingDecision.yes(bestSplit);
        } else {
            return UnswitchingDecision.NO;
        }
    }
}
