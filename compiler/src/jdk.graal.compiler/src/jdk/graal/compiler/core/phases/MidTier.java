/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.phases;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.SpectrePHTMitigations;
import jdk.graal.compiler.duplication.phases.DeDuplicationPhase;
import jdk.graal.compiler.duplication.phases.PullThroughPhiPhase;
import jdk.graal.compiler.guards.GuardRangeGroupingPhase;
import jdk.graal.compiler.loop.phases.CountedStripMiningPhase;
import jdk.graal.compiler.loop.phases.CountedStripMiningReassociationPhase;
import jdk.graal.compiler.loop.phases.InjectLoopCounterStampsPhase;
import jdk.graal.compiler.loop.phases.LoopFullUnrollPhase;
import jdk.graal.compiler.loop.phases.LoopInversionPhase;
import jdk.graal.compiler.loop.phases.LoopPartialUnrollPhase;
import jdk.graal.compiler.loop.phases.LoopPeelingPhase;
import jdk.graal.compiler.loop.phases.LoopRotationPhase;
import jdk.graal.compiler.loop.phases.LoopPredicationPhase;
import jdk.graal.compiler.loop.phases.LoopSafepointEliminationPhase;
import jdk.graal.compiler.loop.phases.NonCountedStripMiningPhase;
import jdk.graal.compiler.loop.phases.OptimizeLoopAccessesPhase;
import jdk.graal.compiler.loop.phases.SpeculativeGuardMovementPhase;
import jdk.graal.compiler.guards.optimistic.memory.OptimisticAliasingAnalysisPhase;
import jdk.graal.compiler.guards.optimistic.memory.OptimisticGuardsPhase;
import jdk.graal.compiler.nodes.loop.DefaultLoopPolicies;
import jdk.graal.compiler.nodes.loop.LoopPolicies;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DeoptimizationGroupingPhase;
import jdk.graal.compiler.phases.common.FloatingReadPhase;
import jdk.graal.compiler.phases.common.FrameStateAssignmentPhase;
import jdk.graal.compiler.phases.common.GuardLoweringPhase;
import jdk.graal.compiler.phases.common.InsertGuardFencesPhase;
import jdk.graal.compiler.phases.common.IterativeConditionalEliminationPhase;
import jdk.graal.compiler.phases.common.LateLockEliminationPhase;
import jdk.graal.compiler.phases.common.LockEliminationPhase;
import jdk.graal.compiler.phases.common.LoopSafepointInsertionPhase;
import jdk.graal.compiler.phases.common.MidTierLoweringPhase;
import jdk.graal.compiler.phases.common.OptimizeDivPhase;
import jdk.graal.compiler.phases.common.OptimizeExactArithmeticPhase;
import jdk.graal.compiler.phases.common.ReassociationPhase;
import jdk.graal.compiler.phases.common.RemoveValueProxyPhase;
import jdk.graal.compiler.phases.common.VerifyHeapAtReturnPhase;
import jdk.graal.compiler.phases.common.WriteBarrierAdditionPhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.vector.phases.ConditionalMoveOptimizationPhase;
import jdk.graal.compiler.vector.phases.LoopVectorizationPhase;
import jdk.graal.compiler.vector.phases.NodeVectorizationPhase;
import jdk.graal.compiler.vector.phases.OptimizeAddressesInLoopsPhase;
import jdk.graal.compiler.vector.phases.RemoveEmptyLoopsPhase;
import jdk.graal.compiler.vector.phases.VectorMaterializationPhaseSuite;
import jdk.graal.compiler.vector.replacements.VectorIntrinsics;

public class MidTier extends BaseTier<MidTierContext> {

    public static class Options {

        //@formatter:off
        @Option(help = "Performs aliasing analysis on arrays to determine which memory " +
                       "does not alias and enables more optimizations to be performed.", type = OptionType.Expert)
        public static final OptionKey<Boolean> OptimisticAliasingAnalysis = new OptionKey<>(true);
        /// Controls whether integer range guards with the same anchor are combined.
        @Option(help = "Combines integer range guards that have the same anchor.", type = OptionType.Debug)
        public static final OptionKey<Boolean> OptGuardRangeGrouping = new OptionKey<>(true);

        /// Controls whether eligible loop reads are replaced with loop-carried value phis.
        @Option(help = "Enables access node optimizations for loops. " +
                       "This can reduce the number of memory operations executed in the body of a loop.", type = OptionType.Expert)
        public static final OptionKey<Boolean> OptimizeLoopAccesses = new OptionKey<>(true);

        @Option(help = "Enables strip mining for non-counted loops.", type = OptionType.Expert)
        public static final OptionKey<Boolean> StripMineNonCountedLoops = new OptionKey<>(true);
        @Option(help = "Enables strip mining for counted loops.", type = OptionType.Expert)
        public static final OptionKey<Boolean> StripMineCountedLoops = new OptionKey<>(true);
        @Option(help = "Enables preparation phases used by counted strip mining.", type = OptionType.Expert)
        public static final OptionKey<Boolean> StripMiningPreparationPhases = new OptionKey<>(true);
        @Option(help = "Optimizes exact arithmetic where possible by rewriting it " +
                       "to non-exit counterparts iff provably no overflow is possible.", type = OptionType.Expert)
        public static final OptionKey<Boolean> OptExactArithmetic = new OptionKey<>(true);
        //@formatter:on
    }

    @SuppressWarnings("this-escape")
    public MidTier(OptionValues options) {
        CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();

        appendPhase(new LockEliminationPhase());

        if (GraalOptions.OptFloatingReads.getValue(options)) {
            appendPhase(new FloatingReadPhase(canonicalizer));
            if (InjectLoopCounterStampsPhase.Options.OptLoopPhiStamps.getValue(options)) {
                // Floating reads can expose counted loops whose counter stamps can be refined.
                appendPhase(new InjectLoopCounterStampsPhase());
            }
        }

        if (GraalOptions.ConditionalElimination.getValue(options)) {
            appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, true));
        }

        if (GraalOptions.LoopPredication.getValue(options) && !GraalOptions.SpeculativeGuardMovement.getValue(options)) {
            appendPhase(new LoopPredicationPhase(canonicalizer));
        }

        if (Options.OptimizeLoopAccesses.getValue(options)) {
            // Expose value phis before later loop optimizations inspect induction variables.
            appendPhase(new OptimizeLoopAccessesPhase());
        }

        if (LoopRotationPhase.Options.LoopRotation.getValue(options) && LoopRotationPhase.Options.EarlyMidTierLoopRotation.getValue(options)) {
            // before inversion, strip mining and EPU
            appendPhase(new LoopRotationPhase<>(canonicalizer));
        }

        if (Options.StripMineNonCountedLoops.getValue(options)) {
            appendPhase(new NonCountedStripMiningPhase(canonicalizer));
        }

        if (Options.StripMineCountedLoops.getValue(options)) {
            if (Options.StripMiningPreparationPhases.getValue(options)) {
                if (VectorIntrinsics.Options.Vectorization.getValue(options)) {
                    // dont strip mine loops that are no loops
                    appendPhase(new RemoveEmptyLoopsPhase(canonicalizer));
                }
                if (Options.OptExactArithmetic.getValue(options)) {
                    /*
                     * Run before strip mining to piggy back on the overflow before we rewrite
                     * the loop.
                     */
                    appendPhase(new OptimizeExactArithmeticPhase(canonicalizer));
                }
            }
            if (GraalOptions.LoopPeeling.getValue(options)) {
                /**
                 *
                 * Strip mining can influence loop peeling decisions because it can hinder
                 * constant folding. Consider a loop like this
                 *
                 * <pre>
                 * int phi = 0;
                 * int res = 0;
                 * while (condition) {
                 *     res += phi / 32;
                 *     phi++;
                 * }
                 * </pre>
                 *
                 * if this loop is peeled the peeled fraction
                 *
                 * <pre>
                 * int phi = 0;
                 * int res = 0;
                 * res += 0 / 32; // == 0
                 * phi++;
                 * while (condition) {
                 *     res += phi / 32;
                 *     phi++;
                 * }
                 * </pre>
                 *
                 * can be optimized to a noop. If we strip mine this loop first the IV offset
                 * done by strip mining can hinder constant folding. If we peeled the not strip
                 * mined version iteration =0 means all IVs have their entry value, for the
                 * peeled version in the strip mined loop this is not the case because it is
                 * still wrapped by the outer loop.
                 */
                appendPhase(new LoopPeelingPhase(createLoopPolicies(options), canonicalizer));
            }
            appendPhase(new CountedStripMiningPhase(canonicalizer));
        } else if (Options.StripMiningPreparationPhases.getValue(options) && Options.OptExactArithmetic.getValue(options)) {
            // Keep exact arithmetic available when counted strip mining is disabled.
            appendPhase(new OptimizeExactArithmeticPhase(canonicalizer));
        }

        boolean runLoopInversion = LoopInversionPhase.Options.LoopInversion.getValue(options) && LoopInversionPhase.Options.MidTierInversion.getValue(options);
        boolean aliasAnalysisBeforeInversion = runLoopInversion && Options.OptimisticAliasingAnalysis.getValue(options);
        if (aliasAnalysisBeforeInversion) {
            // Inversion uses alias information to avoid transforming loops that can be vectorized.
            appendPhase(new OptimizeAddressesInLoopsPhase());
            appendPhase(new OptimisticAliasingAnalysisPhase(canonicalizer));
        }
        if (runLoopInversion) {
            appendPhase(new LoopInversionPhase(createLoopPolicies(options), canonicalizer));
        }

        appendPhase(new LoopSafepointEliminationPhase());

        if (Options.OptGuardRangeGrouping.getValue(options)) {
            appendPhase(new GuardRangeGroupingPhase());
        }

        if (GraalOptions.SpeculativeGuardMovement.getValue(options)) {
            appendPhase(new SpeculativeGuardMovementPhase(canonicalizer));
        }

        appendPhase(new GuardLoweringPhase());

        /*
         * We apply loop rotation a second time after guard lowering for a special scenario: If a
         * non-counted loop has guards anchored in the loop body (because speculative guard movement
         * on non-counted loops does not work) we rotate the actual if(nonCounterCondition){break}
         * at the end of the loop and use the hopefully counted guard check as the condition. We can
         * then apply full counted loop optimizations on the non-counted loop as if it was counted
         * because the guard should anyway never trigger.
         */
        if (LoopRotationPhase.Options.LoopRotation.getValue(options) && LoopRotationPhase.Options.LateMidTierLoopRotation.getValue(options)) {
            appendPhase(new LoopRotationPhase<>(canonicalizer, createLoopRotationCleanup(options, canonicalizer)));
        }

        if (SpectrePHTMitigations.Options.SpectrePHTBarriers.getValue(options) == SpectrePHTMitigations.GuardTargets ||
                        SpectrePHTMitigations.Options.SpectrePHTBarriers.getValue(options) == SpectrePHTMitigations.NonDeoptGuardTargets) {
            appendPhase(new InsertGuardFencesPhase());
        }

        if (GraalOptions.VerifyHeapAtReturn.getValue(options)) {
            appendPhase(new VerifyHeapAtReturnPhase());
        }

        if (GraalOptions.FullUnroll.getValue(options)) {
            appendPhase(new LoopFullUnrollPhase(canonicalizer, createLoopPolicies(options)));
        }

        appendPhase(new RemoveValueProxyPhase(canonicalizer));

        appendPhase(new LoopSafepointInsertionPhase());

        appendPhase(new MidTierLoweringPhase(canonicalizer));
        if (InjectLoopCounterStampsPhase.Options.OptLoopPhiStamps.getValue(options)) {
            // Lowering can inline snippet loops whose counter stamps can then be refined.
            appendPhase(new InjectLoopCounterStampsPhase());
        }

        if (GraalOptions.ConditionalElimination.getValue(options)) {
            appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, false));
        }

        if (GraalOptions.OptimizeDiv.getValue(options)) {
            appendPhase(new OptimizeDivPhase(canonicalizer));
        }

        if (VectorIntrinsics.Options.Vectorization.getValue(options) && Options.OptimisticAliasingAnalysis.getValue(options)) {
            appendPhase(new OptimizeAddressesInLoopsPhase());
            if (aliasAnalysisBeforeInversion) {
                // Clean up the guards from the earlier analysis before creating another set.
                appendPhase(new OptimisticGuardsPhase(canonicalizer));
            }
            appendPhase(new OptimisticAliasingAnalysisPhase(canonicalizer));
        }

        appendPhase(new FrameStateAssignmentPhase());

        if (DeDuplicationPhase.Options.OptDeDuplication.getValue(options)) {
            appendPhase(new DeDuplicationPhase(canonicalizer));
        }

        // Frame states enable nested elimination and lock coarsening across control flow.
        appendPhase(new LateLockEliminationPhase());

        if (PullThroughPhiPhase.Options.OptPullThroughPhi.getValue(options)) {
            appendPhase(new PullThroughPhiPhase(canonicalizer));
        }

        if (VectorIntrinsics.Options.Vectorization.getValue(options)) {
            appendPhase(new NodeVectorizationPhase(canonicalizer));
            if (ConditionalMoveOptimizationPhase.Options.OptConditionalMoves.getValue(options)) {
                appendPhase(new ConditionalMoveOptimizationPhase(canonicalizer));
            }
            if (LoopVectorizationPhase.Options.VectorizeLoops.getValue(options)) {
                appendPhase(new LoopVectorizationPhase(LoopVectorizationPhase.Options.VectorizeDeopts.getValue(options), Options.OptimisticAliasingAnalysis.getValue(options), canonicalizer));
            }
            appendPhase(new RemoveEmptyLoopsPhase(canonicalizer));
            appendPhase(new VectorMaterializationPhaseSuite(canonicalizer));
            appendPhase(new OptimisticGuardsPhase(canonicalizer));
        } else if (aliasAnalysisBeforeInversion) {
            // Resolve guards created before inversion when the vector phases do not provide cleanup.
            appendPhase(new OptimisticGuardsPhase(canonicalizer));
        }

        if (GraalOptions.PartialUnroll.getValue(options)) {
            LoopPolicies loopPolicies = createLoopPolicies(options);
            appendPhase(new LoopPartialUnrollPhase(loopPolicies, canonicalizer));
        }

        if (GraalOptions.PartialUnroll.getValue(options) && Options.StripMineCountedLoops.getValue(options) &&
                        CountedStripMiningReassociationPhase.Options.StripMiningReassociation.getValue(options)) {
            /*
             * Strip mining potentially produces complex IVs that cannot easily reassociate
             * and fold because of the rewrite from regular IVs to outer offset IVs. Thus,
             * we try to "clean" them up if unrolling produced such offset patterns.
             */
            appendPhase(new CountedStripMiningReassociationPhase(canonicalizer));
        }

        if (GraalOptions.ReassociateExpressions.getValue(options)) {
            appendPhase(new ReassociationPhase(canonicalizer));
        }

        if (GraalOptions.OptDeoptimizationGrouping.getValue(options)) {
            appendPhase(new DeoptimizationGroupingPhase());
        }

        appendPhase(canonicalizer);

        appendPhase(new WriteBarrierAdditionPhase());

    }

    /// Creates the cleanup suite that follows mid-tier loop rotation.
    protected PhaseSuite<MidTierContext> createLoopRotationCleanup(OptionValues options, CanonicalizerPhase canonicalizer) {
        PhaseSuite<MidTierContext> cleanup = new PhaseSuite<>();
        cleanup.appendPhase(new LoopSafepointEliminationPhase());
        if (LoopInversionPhase.Options.LoopInversion.getValue(options) && LoopInversionPhase.Options.MidTierInversion.getValue(options)) {
            cleanup.appendPhase(new LoopInversionPhase(createLoopPolicies(options), canonicalizer));
        }
        return cleanup;
    }

    @Override
    public LoopPolicies createLoopPolicies(OptionValues options) {
        return new DefaultLoopPolicies();
    }
}
