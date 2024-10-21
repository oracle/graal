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

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.Invoke;
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

    private static final long IntegerRangeDistance = NumUtil.unsafeAbs((long) Integer.MAX_VALUE - (long) Integer.MIN_VALUE);

    public static boolean iterationRangeIsIn32Bit(Loop loop) {
        if (loop.counted().getStamp().getBits() <= 32) {
            return true;
        }
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
                    return strideRelativeStartToLimitDistance <= IntegerRangeDistance;
                } catch (ArithmeticException e) {
                    return false;
                }
            }
        }
        return false;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.unlessRunAfter(this, GraphState.StageFlag.LOOP_OVERFLOWS_CHECKED, graphState);
    }

    @Override
    protected void run(StructuredGraph graph, MidTierContext context) {
        new Instance(graph, context).optimizeSafepoints();
    }

    protected static class Instance {
        private final StructuredGraph graph;
        private final MidTierContext context;

        protected Instance(StructuredGraph graph, MidTierContext context) {
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
        private int disableSafepointsByBodyNodes(Loop loop, ControlFlowGraph cfg) {
            int loopEndSafepointsDisabled = 0;
            for (LoopEndNode loopEnd : loop.loopBegin().loopEnds()) {
                HIRBlock b = cfg.blockFor(loopEnd);
                blocks: while (b != loop.getCFGLoop().getHeader()) {
                    assert b != null;
                    for (FixedNode node : b.getNodes()) {
                        boolean canDisableSafepoint = canDisableSafepoint(node, context);
                        boolean disabledInSubclass = onCallInLoop(loopEnd, node);
                        if (canDisableSafepoint) {
                            loopEnd.disableSafepoint();
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

        public void optimizeSafepoints() {
            final boolean optimisticallyRemoveLoopSafepoints = Options.RemoveLoopSafepoints.getValue(graph.getOptions());

            LoopsData loops = context.getLoopsDataProvider().getLoopsData(graph);
            loops.detectCountedLoops();

            for (Loop loop : loops.loops()) {
                if (!allowGuestSafepoints()) {
                    loop.loopBegin().disableGuestSafepoint(SafepointState.MUST_NEVER_SAFEPOINT);
                }
                int loopEndSafepointsDisabled = disableSafepointsByBodyNodes(loop, loops.getCFG());
                final boolean allLoopEndSafepointsDisabled = loopEndSafepointsDisabled == loop.loopBegin().getLoopEndCount();
                if (!allLoopEndSafepointsDisabled && optimisticallyRemoveLoopSafepoints) {
                    if (optimizeSafepointsForCountedLoop(loop)) {
                        /*
                         * We removed all loop end safepoints if we do it optimistically for the
                         * entire loop.
                         */
                        loopEndSafepointsDisabled = loop.loopBegin().getLoopEndCount();
                    }
                }
                final boolean allLoopEndSafepointsEnabled = loopEndSafepointsDisabled == 0;
                if (allLoopEndSafepointsEnabled) {
                    /*
                     * Only if ALL paths through the loop are guaranteed to safepoint we can drop
                     * the exit safepoint. If there is any path left that does not safepoint we
                     * could be only executing that path and then we need an exit safepoint.
                     */
                    loop.loopBegin().disableLoopExitSafepoint(SafepointState.OPTIMIZER_DISABLED);
                }

            }
            loops.deleteUnusedNodes();
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
        protected boolean onCallInLoop(LoopEndNode loopEnd, FixedNode currentCallNode) {
            return true;
        }

        /**
         * To be implemented by subclasses to compute additional fields.
         */
        @SuppressWarnings("unused")
        protected void onSafepointDisabledLoopBegin(Loop loop) {
        }

        /**
         * Tries to optimize away safepoints for the given counted loop completely. We have not been
         * able to remove safepoints from the loop ends of the given loop yet. However, the
         * optimizer may believe this loop is short running enough to remove safepoints.
         */
        private boolean optimizeSafepointsForCountedLoop(Loop loop) {
            if (loop.isCounted()) {
                if (loop.getCFGLoop().getChildren().isEmpty() &&
                                (loop.loopBegin().isPreLoop() || loop.loopBegin().isPostLoop() || loopIsIn32BitRange(loop) ||
                                                loop.loopBegin().isStripMinedInner())) {
                    boolean hasSafepoint = false;
                    for (LoopEndNode loopEnd : loop.loopBegin().loopEnds()) {
                        hasSafepoint |= loopEnd.canSafepoint();
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
                        loop.loopBegin().disableSafepoint(SafepointState.OPTIMIZER_DISABLED);
                        if (loop.loopBegin().isStripMinedInner()) {
                            /*
                             * graal strip mined this loop, trust the heuristics and remove the
                             * inner loop safepoint
                             */
                            loop.loopBegin().disableGuestSafepoint(SafepointState.OPTIMIZER_DISABLED);
                        } else {
                            /*
                             * let the shape of the loop decide whether a guest safepoint is needed
                             */
                            onSafepointDisabledLoopBegin(loop);
                        }
                        graph.getOptimizationLog().report(LoopSafepointEliminationPhase.class, "SafepointElimination", loop.loopBegin());
                        return true;
                    }
                }
            }
            return false;
        }

        public boolean loopIsIn32BitRange(Loop loop) {
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
