/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Optional;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopBeginNode.SafepointState;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.extended.ForeignCall;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class LoopSafepointEliminationPhase extends BasePhase<MidTierContext> {
    public static class Options {
        //@formatter:off
        @Option(help = "Removes safepoints on counted loop ends.", type = OptionType.Expert)
        public static final OptionKey<Boolean> RemoveLoopSafepoints = new OptionKey<>(true);
        //@formatter:on
    }

    /**
     * Models SafepointState transforms that can be applied to all loops in a graph.
     */
    public static class LoopSafepointPlan {
        private final EconomicMap<LoopBeginNode, SafepointStateEffect> loopStates;

        public LoopSafepointPlan(StructuredGraph g) {
            loopStates = EconomicMap.create();
            for (LoopBeginNode lb : g.getNodes(LoopBeginNode.TYPE)) {
                loopStates.put(lb, new SafepointStateEffect(lb));
            }
        }

        /**
         * Apply all safepoint effects on all loops, that is, enable/disable loop safepoint on loop
         * ends and exits.
         */
        public void apply() {
            for (SafepointStateEffect loopPlan : loopStates.getValues()) {
                loopPlan.apply();
            }
        }

        public void setGuestEndStateAllEnds(Loop loop, SafepointState state) {
            loopStates.get(loop.loopBegin()).setGuestEndStateAllEnds(state);
        }

        public void setGuestEndState(LoopEndNode loopEnd, SafepointState state) {
            loopStates.get(loopEnd.loopBegin()).setGuestEndState(loopEnd, state);
        }

        public void setEndState(LoopEndNode loopEnd, SafepointState state) {
            LoopBeginNode lb = loopEnd.loopBegin();
            loopStates.get(lb).setEndState(loopEnd, state);
        }

        public void setEndStateAllEnds(Loop loop, SafepointState state) {
            loopStates.get(loop.loopBegin()).setEndStateAllEnds(state);
        }

        public void setExitState(Loop loop, SafepointState state) {
            loopStates.get(loop.loopBegin()).setExitState(state);
        }

        public boolean canSafepoint(LoopEndNode len) {
            return loopStates.get(len.loopBegin()).getEndStates().get(len).canSafepoint();
        }

    }

    /**
     * Models SafepointState transforms that can be {@linkplain #apply applied} to a single loop.
     */
    public static class SafepointStateEffect {
        /**
         * The safepoint state that should be assigned to the loop exit nodes of this loop.
         */
        private SafepointState exitState;
        /**
         * The safepoint state assigned to every loop end of this loop.
         */
        private final EconomicMap<LoopEndNode, SafepointState> endStates;
        /**
         * The guest safepoint state assigned to every loop end of this loop.
         */
        private final EconomicMap<LoopEndNode, SafepointState> guestEndStates;
        /**
         * The corresponding loop begin node.
         */
        private final LoopBeginNode loopBegin;

        public SafepointStateEffect(LoopBeginNode lb) {
            this.loopBegin = lb;
            endStates = EconomicMap.create();
            guestEndStates = EconomicMap.create();
            // Start with the states before the elimination
            for (LoopEndNode len : lb.loopEnds()) {
                endStates.put(len, len.getSafepointState());
                guestEndStates.put(len, len.getGuestSafepointState());
            }
            exitState = lb.getLoopExitsSafepointState();
        }

        public SafepointState getExitState() {
            return exitState;
        }

        public void setExitState(SafepointState exitState) {
            this.exitState = exitState;
        }

        public EconomicMap<LoopEndNode, SafepointState> getEndStates() {
            return endStates;
        }

        public EconomicMap<LoopEndNode, SafepointState> getGuestEndStates() {
            return guestEndStates;
        }

        public void setEndState(LoopEndNode len, SafepointState newState) {
            endStates.put(len, newState);
        }

        public void setEndStateAllEnds(SafepointState newState) {
            for (LoopEndNode len : loopBegin.loopEnds()) {
                endStates.put(len, newState);
            }
        }

        public void setGuestEndStateAllEnds(SafepointState newState) {
            for (LoopEndNode len : loopBegin.loopEnds()) {
                guestEndStates.put(len, newState);
            }
        }

        public void setGuestEndState(LoopEndNode len, SafepointState newState) {
            guestEndStates.put(len, newState);
        }

        public void apply() {
            for (LoopEndNode len : loopBegin.loopEnds()) {
                len.setSafepointState(endStates.get(len));
                len.setGuestSafepointState(guestEndStates.get(len));
            }
            loopBegin.setLoopExitSafepoint(exitState);
        }

    }

    private static final long IntegerRangeDistance = NumUtil.unsafeAbs((long) Integer.MAX_VALUE - (long) Integer.MIN_VALUE);

    public static boolean loopIsInIterationRange(Loop loop, long distance) {
        final Stamp limitStamp = loop.counted().getTripCountLimit().stamp(NodeView.DEFAULT);
        if (limitStamp instanceof IntegerStamp) {
            final IntegerStamp limitIStamp = (IntegerStamp) limitStamp;
            final long upperBoundLimit = limitIStamp.upperBound();
            final Stamp startStamp = loop.counted().getBodyIVStart().stamp(NodeView.DEFAULT);
            if (startStamp instanceof IntegerStamp) {
                final IntegerStamp startIStamp = (IntegerStamp) startStamp;
                final long lowerBoundStart = startIStamp.lowerBound();
                if (IntegerStamp.subtractionOverflows(upperBoundLimit, lowerBoundStart, 64)) {
                    return false;
                }
                try {
                    final long startToLimitDistance = NumUtil.safeAbs(upperBoundLimit - lowerBoundStart);

                    /*
                     * Divide the distance by the absolute value of the stride. For non-constant
                     * strides assume a worst case stride of 1 since a stride of 0 isn't recognized
                     * as an induction variable.
                     */
                    final InductionVariable counter = loop.counted().getLimitCheckedIV();
                    final long stride = counter.isConstantStride() ? NumUtil.safeAbs(counter.constantStride()) : 1;
                    final long strideRelativeStartToLimitDistance = startToLimitDistance / stride;
                    return strideRelativeStartToLimitDistance <= distance;
                } catch (ArithmeticException e) {
                    return false;
                }
            }
        }
        return false;
    }

    public static boolean iterationRangeIsIn32Bit(Loop loop) {
        if (loop.counted().getStamp().getBits() <= 32) {
            return true;
        }
        return loopIsInIterationRange(loop, IntegerRangeDistance);
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.unlessRunAfter(this, GraphState.StageFlag.LOOP_OVERFLOWS_CHECKED, graphState);
    }

    @Override
    protected void run(StructuredGraph graph, MidTierContext context) {
        new SafepointOptimizer(graph, context).optimizeSafepoints().apply();
    }

    public static class SafepointOptimizer {
        private final StructuredGraph graph;
        private final MidTierContext context;

        public SafepointOptimizer(StructuredGraph graph, MidTierContext context) {
            this.graph = graph;
            this.context = context;
        }

        /**
         * Disable safepoints on loop ends by body nodes. If there is a node that is a guaranteed
         * safepoint (like a call) we can disable the safepoint on the dominated loop end because
         * there will be a safepoint poll done already on every iteration of the loop.
         *
         * So for a pattern
         *
         * <pre>
         * while (header) {
         *     if (someCondition1) {
         *         call1();
         *         continue;
         *     }
         *     code();
         *     if (someCondition2) {
         *         call2();
         *         continue;
         *     }
         *     restOfBody();
         * }
         * </pre>
         *
         * we know that both {@code continue} backedges are dominated by a call. {@code call1} and
         * {@code call2} dominate the backedges. We can remove safepoint polls from both of them
         * because the call will be a guaranteed safepoint.
         */
        private int disableSafepointsByBodyNodes(LoopSafepointPlan safepointPlan, Loop loop, ControlFlowGraph cfg) {
            int loopEndSafepointsDisabled = 0;
            for (LoopEndNode loopEnd : loop.loopBegin().loopEnds()) {
                HIRBlock b = cfg.blockFor(loopEnd);
                blocks: while (b != loop.getCFGLoop().getHeader()) {
                    assert b != null;
                    for (FixedNode node : b.getNodes()) {
                        boolean canDisableSafepoint = canDisableSafepoint(node, context);
                        boolean disabledInSubclass = onCallInLoop(safepointPlan, loopEnd, node);
                        if (canDisableSafepoint) {
                            safepointPlan.setEndState(loopEnd, SafepointState.OPTIMIZER_DISABLED);
                            graph.getOptimizationLog().report(LoopSafepointEliminationPhase.class, "SafepointElimination", loop.loopBegin());
                            loopEndSafepointsDisabled++;
                            /*
                             * we can only stop if subclasses also say we can stop iterating blocks
                             */
                            if (disabledInSubclass) {
                                break blocks;
                            }
                        }
                    }
                    b = b.getDominator();
                }
            }
            return loopEndSafepointsDisabled;
        }

        public LoopSafepointPlan optimizeSafepoints() {
            LoopsData loops = context.getLoopsDataProvider().getLoopsData(graph);
            loops.detectCountedLoops();
            return optimizeSafepoints(loops);
        }

        public LoopSafepointPlan optimizeSafepoints(LoopsData loops) {
            LoopSafepointPlan graphWidePlan = new LoopSafepointPlan(graph);

            final boolean optimisticallyRemoveLoopSafepoints = Options.RemoveLoopSafepoints.getValue(graph.getOptions());

            for (Loop loop : loops.loops()) {
                if (!allowGuestSafepoints()) {
                    graphWidePlan.setGuestEndStateAllEnds(loop, SafepointState.MUST_NEVER_SAFEPOINT);
                }
                if (!loop.loopBegin().canEndsSafepoint() && !loop.loopBegin().canEndsGuestSafepoint()) {
                    /*
                     * There are no safepoints at the loop ends. Therefore, we want safepoints at
                     * loop exits. Skip optimization of loop exit safepoints.
                     */
                    continue;
                }

                int loopEndSafepointsDisabled = disableSafepointsByBodyNodes(graphWidePlan, loop, loops.getCFG());
                final boolean allLoopEndSafepointsDisabledByBodyNodes = loopEndSafepointsDisabled == loop.loopBegin().getLoopEndCount();
                if (!allLoopEndSafepointsDisabledByBodyNodes && optimisticallyRemoveLoopSafepoints) {
                    if (optimizeSafepointsForCountedLoop(graphWidePlan, loop)) {
                        /*
                         * We removed all loop end safepoints if we do it optimistically for the
                         * entire loop.
                         */
                        loopEndSafepointsDisabled = loop.loopBegin().getLoopEndCount();
                    }
                }
                if (!loop.loopBegin().canExitsSafepoint()) {
                    continue;
                }
                final boolean allLoopEndSafepointsEnabled = loopEndSafepointsDisabled == 0;
                // strip mined outer is never counted
                final boolean stripMinedOuter = loop.loopBegin().isAnyStripMinedOuter();
                // retain the exit safepoint for all non-counted loops
                if (loop.isCounted() || stripMinedOuter) {
                    if (allLoopEndSafepointsEnabled || allLoopEndSafepointsDisabledByBodyNodes) {
                        /**
                         * If all paths inside the loop are guaranteed to trigger a safepoint
                         * explicitly via SafepointNodes, or implicitly via other nodes (e.g.,
                         * InvokeNode), we can drop the exit safepoint.
                         */
                        graphWidePlan.setExitState(loop, SafepointState.OPTIMIZER_DISABLED);
                    }
                }

            }
            loops.deleteUnusedNodes();

            return graphWidePlan;
        }

        /**
         * Determines whether guest safepoints should be allowed at all. To be implemented by
         * subclasses. The default implementation returns <code>false</code>, leading to guest
         * safepoints being disabled for all loops in the graph.
         */
        protected boolean allowGuestSafepoints() {
            return false;
        }

        /**
         * To be implemented by subclasses to perform additional checks. Returns <code>true</code>
         * if the safepoint was also disabled in subclasses and we therefore don't need to continue
         * traversing.
         */
        @SuppressWarnings("unused")
        protected boolean onCallInLoop(LoopSafepointPlan safepointPlan, LoopEndNode loopEnd, FixedNode currentCallNode) {
            return true;
        }

        /**
         * To be implemented by subclasses to compute additional fields.
         */
        @SuppressWarnings("unused")
        protected void onSafepointDisabledLoopBegin(LoopSafepointPlan safepointPlan, Loop loop) {
        }

        /**
         * Tries to optimize away safepoints for the given counted loop completely. We have not been
         * able to remove safepoints from the loop ends of the given loop yet. However, the
         * optimizer may believe this loop is short running enough to remove safepoints.
         */
        private boolean optimizeSafepointsForCountedLoop(LoopSafepointPlan safepointPlan, Loop loop) {
            if (loop.isCounted()) {
                final boolean leafLoop = loop.getCFGLoop().getChildren().isEmpty();
                final boolean preLoop = loop.loopBegin().isPreLoop();
                final boolean postLoop = loop.loopBegin().isPostLoop();
                final boolean briefLoop = loopIsInBriefRange(loop);
                final boolean stripMinedInner = loop.loopBegin().isAnyStripMinedInner();
                if (leafLoop && (preLoop || postLoop || briefLoop || stripMinedInner)) {
                    boolean hasSafepoint = false;
                    for (LoopEndNode loopEnd : loop.loopBegin().loopEnds()) {
                        hasSafepoint |= loopEnd.getSafepointState().canSafepoint();
                    }
                    if (hasSafepoint) {
                        if (!loop.counted().counterNeverOverflows()) {
                            // Counter can overflow, need to create a guard.
                            boolean allowsLoopLimitChecks = context.getOptimisticOptimizations().useLoopLimitChecks(graph.getOptions());
                            boolean allowsFloatingGuards = graph.getGuardsStage().allowsFloatingGuards();
                            if (allowsLoopLimitChecks && allowsFloatingGuards) {
                                loop.counted().createOverFlowGuard();
                            } else {
                                /*
                                 * Cannot disable this safepoint, because the loop could overflow.
                                 */
                                return false;
                            }
                        }
                        safepointPlan.setEndStateAllEnds(loop, SafepointState.OPTIMIZER_DISABLED);
                        if (stripMinedInner) {
                            /*
                             * graal strip mined this loop, trust the heuristics and remove the
                             * inner loop safepoint
                             */
                            safepointPlan.setGuestEndStateAllEnds(loop, SafepointState.OPTIMIZER_DISABLED);
                        } else {
                            /*
                             * let the shape of the loop decide whether a guest safepoint is needed
                             */
                            onSafepointDisabledLoopBegin(safepointPlan, loop);
                        }
                        graph.getOptimizationLog().report(LoopSafepointEliminationPhase.class, "SafepointElimination", loop.loopBegin());
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Determine if this loop is a brief range loop. A brief range loop is one where we assume
         * that we can drop the safepoint because the body of the loop executes quickly enough.
         */
        public boolean loopIsInBriefRange(Loop loop) {
            return iterationRangeIsIn32Bit(loop);
        }

    }

    public static boolean canDisableSafepoint(FixedNode node, CoreProviders context) {
        if (node instanceof Invoke) {
            Invoke invoke = (Invoke) node;
            ResolvedJavaMethod method = invoke.getTargetMethod();
            return context.getMetaAccessExtensionProvider().isGuaranteedSafepoint(method, invoke.getInvokeKind().isDirect());
        } else if (node instanceof ForeignCall) {
            return ((ForeignCall) node).isGuaranteedSafepoint();
        }
        return false;
    }

}
