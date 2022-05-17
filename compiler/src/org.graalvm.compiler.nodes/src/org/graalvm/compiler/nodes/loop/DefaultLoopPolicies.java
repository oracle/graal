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

import java.util.List;

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
        @Option(help = "", type = OptionType.Expert) public static final OptionKey<Integer> LoopUnswitchMaxIncrease = new OptionKey<>(500);
        @Option(help = "", type = OptionType.Expert) public static final OptionKey<Integer> LoopUnswitchTrivial = new OptionKey<>(10);
        @Option(help = "", type = OptionType.Expert) public static final OptionKey<Double> LoopUnswitchFrequencyBoost = new OptionKey<>(10.0);

        @Option(help = "", type = OptionType.Expert) public static final OptionKey<Integer> FullUnrollMaxNodes = new OptionKey<>(400);
        @Option(help = "", type = OptionType.Expert) public static final OptionKey<Integer> FullUnrollConstantCompareBoost = new OptionKey<>(15);
        @Option(help = "", type = OptionType.Expert) public static final OptionKey<Integer> FullUnrollMaxIterations = new OptionKey<>(600);
        @Option(help = "", type = OptionType.Expert) public static final OptionKey<Integer> ExactFullUnrollMaxNodes = new OptionKey<>(800);
        @Option(help = "", type = OptionType.Expert) public static final OptionKey<Integer> ExactPartialUnrollMaxNodes = new OptionKey<>(200);

        @Option(help = "", type = OptionType.Expert) public static final OptionKey<Integer> UnrollMaxIterations = new OptionKey<>(16);
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
        if (maxTrips.isGreaterThan(Options.FullUnrollMaxIterations.getValue(options))) {
            return FullUnrollability.TOO_MANY_ITERATIONS;
        }
        int globalMax = MaximumDesiredSize.getValue(options) - loop.loopBegin().graph().getNodeCount();
        if (globalMax <= 0) {
            return FullUnrollability.TOO_LARGE;
        }
        int maxNodes = counted.isExactTripCount() ? Options.ExactFullUnrollMaxNodes.getValue(options) : Options.FullUnrollMaxNodes.getValue(options);
        for (Node usage : counted.getLimitCheckedIV().valueNode().usages()) {
            if (usage instanceof CompareNode) {
                CompareNode compare = (CompareNode) usage;
                if (compare.getY().isConstant()) {
                    maxNodes += Options.FullUnrollConstantCompareBoost.getValue(options);
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
        int maxNodes = Options.ExactPartialUnrollMaxNodes.getValue(options);
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
        int maxUnroll = Options.UnrollMaxIterations.getValue(options);
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
     * @return the approximate difference.
     */
    private static int approxDiff(LoopEx loop, List<ControlSplitNode> controlSplits) {
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
     * Compute the sum of the local frequencies of the control split nodes. If a control split node
     * is within an inner loop then its frequency is divided by the local frequencies of the inner
     * loops.
     *
     * The result should be between 0 and {@code controlSplits.size()} times the local loop
     * frequency.
     */
    private static double localFrequency(LoopEx loop, List<ControlSplitNode> controlSplits) {
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

    private static int maxDiff(LoopEx loop) {
        StructuredGraph graph = loop.loopBegin().graph();

        double loopFrequency = loop.localLoopFrequency();
        OptionValues options = loop.loopBegin().getOptions();
        /* When a loop has a greater local loop frequency we allow a bigger change in code size */
        int maxDiff = Options.LoopUnswitchTrivial.getValue(options) + (int) (Options.LoopUnswitchFrequencyBoost.getValue(options) * (loopFrequency - 1.0));

        maxDiff = Math.min(maxDiff, Options.LoopUnswitchMaxIncrease.getValue(options));
        int remainingGraphSpace = MaximumDesiredSize.getValue(options) - graph.getNodeCount();
        return Math.min(maxDiff, remainingGraphSpace);
    }

    /**
     * We want to prioritizes invariant that are computed frequently and that have a lower code size
     * change.
     */
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
        int maxDiff = maxDiff(loop);

        // We prioritize invariant with the highest frequency
        List<ControlSplitNode> maxSplit = null;
        double maxSplitFrequency = 0.0;
        int maxApproxDiff = 0;
        for (List<ControlSplitNode> split : controlSplits) {
            int approxDiff = approxDiff(loop, split);
            if (approxDiff > maxDiff) {
                continue;
            }

            double splitFrequency = localFrequency(loop, split);
            if (splitFrequency < 1) {
                /*
                 * When a invariant is unswitched then it is computed each time the loop is executed
                 * so we only want to unswitch conditions that are on average executed at least once
                 * par execution of the whole loop.
                 */
                continue;
            }

            if (splitFrequency > maxSplitFrequency) {
                maxSplitFrequency = splitFrequency;
                maxSplit = split;
                maxApproxDiff = approxDiff;
            }
        }

        if (maxSplit != null) {
            debug.log("shouldUnswitch(%s, %s) : best=%s, delta=%d, max=%d, f=%.2f, invariant f=%.2f", loop, controlSplits, maxSplit, maxApproxDiff, maxDiff,
                            loop.localLoopFrequency(), maxSplitFrequency);

            return UnswitchingDecision.yes(maxSplit);
        } else {
            return UnswitchingDecision.NO;
        }
    }
}
