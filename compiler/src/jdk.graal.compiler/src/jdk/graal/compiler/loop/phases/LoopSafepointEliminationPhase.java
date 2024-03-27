/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.extended.ForeignCall;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.LoopEx;
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

    private static final long IntegerRangeDistance = Math.abs((long) Integer.MAX_VALUE - (long) Integer.MIN_VALUE);

    /**
     * To be implemented by subclasses to perform additional checks. Returns <code>true</code> if
     * the safepoint was also disabled in subclasses and we therefore don't need to continue
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
    protected void onSafepointDisabledLoopBegin(LoopEx loop) {
    }

    /**
     * Determines whether guest safepoints should be allowed at all. To be implemented by
     * subclasses. The default implementation returns <code>false</code>, leading to guest
     * safepoints being disabled for all loops in the graph.
     */
    protected boolean allowGuestSafepoints() {
        return false;
    }

    public static boolean loopIsIn32BitRange(LoopEx loop) {
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
                final long startToLimitDistance = Math.abs(upperBoundLimit - lowerBoundStart);

                /*
                 * Divide the distance by the absolute value of the stride. For non-constant strides
                 * assume a worst case stride of 1 since a stride of 0 isn't recognized as an
                 * induction variable.
                 */
                final InductionVariable counter = loop.counted().getLimitCheckedIV();
                final long stride = counter.isConstantStride() ? Math.abs(counter.constantStride()) : 1;
                final long strideRelativeStartToLimitDistance = startToLimitDistance / stride;
                return strideRelativeStartToLimitDistance <= IntegerRangeDistance;
            }
        }
        return false;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected final void run(StructuredGraph graph, MidTierContext context) {
        LoopsData loops = context.getLoopsDataProvider().getLoopsData(graph);
        if (Options.RemoveLoopSafepoints.getValue(graph.getOptions())) {
            loops.detectCountedLoops();
            for (LoopEx loop : loops.countedLoops()) {
                if (loop.loop().getChildren().isEmpty() && (loop.loopBegin().isPreLoop() || loop.loopBegin().isPostLoop() || loopIsIn32BitRange(loop) || loop.loopBegin().isStripMinedInner())) {
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
                                // Cannot disable this safepoint, because the loop could overflow.
                                continue;
                            }
                        }
                        loop.loopBegin().disableSafepoint();
                        if (loop.loopBegin().isStripMinedInner()) {
                            // graal strip mined this loop, trust the heuristics and remove the
                            // inner
                            // loop safepoint
                            loop.loopBegin().disableGuestSafepoint();
                        } else {
                            // let the shape of the loop decide whether a guest safepoint is needed
                            onSafepointDisabledLoopBegin(loop);
                        }
                        graph.getOptimizationLog().report(LoopSafepointEliminationPhase.class, "SafepointElimination", loop.loopBegin());
                    }
                }
            }
        }
        for (LoopEx loop : loops.loops()) {
            if (!allowGuestSafepoints()) {
                loop.loopBegin().disableGuestSafepoint();
            }
            for (LoopEndNode loopEnd : loop.loopBegin().loopEnds()) {
                HIRBlock b = loops.getCFG().blockFor(loopEnd);
                blocks: while (b != loop.loop().getHeader()) {
                    assert b != null;
                    for (FixedNode node : b.getNodes()) {
                        boolean canDisableSafepoint = canDisableSafepoint(node, context);
                        boolean disabledInSubclass = onCallInLoop(loopEnd, node);
                        if (canDisableSafepoint) {
                            loopEnd.disableSafepoint();
                            graph.getOptimizationLog().report(LoopSafepointEliminationPhase.class, "SafepointElimination", loop.loopBegin());

                            // we can only stop if subclasses also say we can stop iterating blocks
                            if (disabledInSubclass) {
                                break blocks;
                            }
                        }
                    }
                    b = b.getDominator();
                }
            }
        }
        loops.deleteUnusedNodes();
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
