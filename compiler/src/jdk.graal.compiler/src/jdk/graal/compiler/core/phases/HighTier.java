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

import static jdk.graal.compiler.duplication.phases.simulation.DuplicationOptions.EarlySimulationDepth;
import static jdk.graal.compiler.duplication.phases.simulation.DuplicationPhase.FACTORS_INCLUDING_PEA;
import static jdk.graal.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

import java.util.ListIterator;
import java.util.function.Consumer;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.NativeImageSupport;
import jdk.graal.compiler.duplication.phases.MethodDuplicationPhase;
import jdk.graal.compiler.duplication.phases.PullThroughPhiPhase;
import jdk.graal.compiler.duplication.phases.simulation.DuplicationPhase;
import jdk.graal.compiler.duplication.phases.simulation.FixedDuplicationSimulationConfig;
import jdk.graal.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import jdk.graal.compiler.loop.phases.LoopFullUnrollPhase;
import jdk.graal.compiler.loop.phases.LoopPeelingPhase;
import jdk.graal.compiler.loop.phases.LoopUnswitchingPhase;
import jdk.graal.compiler.nodes.loop.DefaultLoopPolicies;
import jdk.graal.compiler.nodes.loop.LoopPolicies;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.BoxNodeIdentityPhase;
import jdk.graal.compiler.phases.common.BoxNodeOptimizationPhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.common.DisableOverflownCountedLoopsPhase;
import jdk.graal.compiler.phases.common.DominatorBasedGlobalValueNumberingPhase;
import jdk.graal.compiler.phases.common.HighTierLoweringPhase;
import jdk.graal.compiler.phases.common.IterativeConditionalEliminationPhase;
import jdk.graal.compiler.phases.common.inlining.InliningPhase;
import jdk.graal.compiler.phases.common.inlining.policy.GreedyInliningPolicy;
import jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIExpansionPhase;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIIntrinsics;
import jdk.graal.compiler.virtual.phases.ea.FinalPartialEscapePhase;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationPhase;

public class HighTier extends BaseTier<HighTierContext> {

    public static class Options {

        // @formatter:off
        @Option(help = "Performs inlining optimization. " +
                       "This can improve performance because callees are specialized to the types and values of callers.", type = OptionType.Expert)
        public static final OptionKey<Boolean> Inline = new OptionKey<>(true);
        // @formatter:on
    }

    @SuppressWarnings("this-escape")
    public HighTier(OptionValues options) {
        CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();
        appendPhase(canonicalizer);

        boolean boxNodeIdentityPhaseAdded = false;
        if (Options.Inline.getValue(options)) {
            boolean usePriorityInlining = PriorityInliningPhase.Options.UsePriorityInlining.getValue(options);
            if (NativeImageSupport.inBuildtimeCode()) {
                // GR-78137: priority inliner does not yet work with Native Image runtime compilation
                usePriorityInlining = false;
            }
            if (usePriorityInlining) {
                appendPhase(new BoxNodeIdentityPhase());
                appendPhase(new PriorityInliningPhase(canonicalizer, options));
                boxNodeIdentityPhaseAdded = true;
            } else {
                appendPhase(new InliningPhase(new GreedyInliningPolicy(null), canonicalizer, options));
            }
            // Method duplication specializes the graph produced by the selected inliner.
            if (MethodDuplicationPhase.Options.OptMethodDuplication.getValue(options)) {
                appendPhase(new MethodDuplicationPhase(canonicalizer));
            }
            appendPhase(new DeadCodeEliminationPhase(Optional));
        }

        appendPhase(new DisableOverflownCountedLoopsPhase());

        if (GraalOptions.OptConvertDeoptsToGuards.getValue(options)) {
            appendPhase(new ConvertDeoptimizeToGuardPhase(canonicalizer));
        }

        if (GraalOptions.ConditionalElimination.getValue(options)) {
            appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, false));
        }

        if (GraalOptions.EarlyGVN.getValue(options)) {
            appendPhase(new DominatorBasedGlobalValueNumberingPhase(canonicalizer));
        }

        LoopPolicies loopPolicies = createLoopPolicies(options);

        if (GraalOptions.FullUnroll.getValue(options)) {
            appendPhase(new LoopFullUnrollPhase(canonicalizer, loopPolicies));
        }

        if (GraalOptions.LoopPeeling.getValue(options)) {
            appendPhase(new LoopPeelingPhase(loopPolicies, canonicalizer));
        }

        if (GraalOptions.LoopUnswitch.getValue(options)) {
            appendPhase(new LoopUnswitchingPhase(loopPolicies, canonicalizer));
        }

        // Must precede all phases that otherwise ignore the identity of boxes (e.g.
        // PartialEscapePhase and BoxNodeOptimizationPhase).
        if (!boxNodeIdentityPhaseAdded) {
            appendPhase(new BoxNodeIdentityPhase());
        }

        this.<HighTierContext> appendControlFlowDuplicationPhases(this::appendPhase, options, canonicalizer);

        if (GraalOptions.PartialEscapeAnalysis.getValue(options)) {
            PhaseSuite<CoreProviders> cleanup = createFinalPEACleanup(options, canonicalizer);
            appendPhase(new FinalPartialEscapePhase(true, canonicalizer, cleanup, options));
        }

        if (VectorAPIIntrinsics.intrinsificationSupported(options)) {
            appendPhase(new VectorAPIExpansionPhase(canonicalizer));
        }

        if (GraalOptions.OptReadElimination.getValue(options)) {
            appendPhase(new ReadEliminationPhase(canonicalizer));
        }

        appendPhase(new BoxNodeOptimizationPhase(canonicalizer));
        appendPhase(new HighTierLoweringPhase(canonicalizer));
    }

    /// Determines whether at least one control flow duplication phase is enabled in `options`.
    protected static boolean isControlFlowDuplicationEnabled(OptionValues options) {
        return PullThroughPhiPhase.Options.OptPullThroughPhi.getValue(options) || GraalOptions.OptDuplication.getValue(options);
    }

    /// Adds the enabled control flow duplication phases through `phaseConsumer`.
    protected final <C extends CoreProviders> void appendControlFlowDuplicationPhases(Consumer<BasePhase<? super C>> phaseConsumer, OptionValues options,
                    CanonicalizerPhase canonicalizer) {
        if (PullThroughPhiPhase.Options.OptPullThroughPhi.getValue(options)) {
            phaseConsumer.accept(new PullThroughPhiPhase(canonicalizer));
        }
        if (GraalOptions.OptDuplication.getValue(options)) {
            // Dead code elimination reduces the graph considered by duplication simulation.
            phaseConsumer.accept(new DeadCodeEliminationPhase());
            phaseConsumer.accept(new DuplicationPhase(FixedDuplicationSimulationConfig.defaultForDepth(EarlySimulationDepth), true, true,
                            FACTORS_INCLUDING_PEA, canonicalizer, getDuplicationVectorizationCheck(), options));
        }
    }

    /// Removes the top-level control flow duplication phases so a subclass can reposition them.
    protected final void removeControlFlowDuplicationPhases() {
        removePhase(PullThroughPhiPhase.class);
        ListIterator<BasePhase<? super HighTierContext>> duplicationPosition = findPhase(DuplicationPhase.class);
        if (duplicationPosition != null) {
            duplicationPosition.previous();
            duplicationPosition.remove();
            BasePhase<? super HighTierContext> precedingPhase = duplicationPosition.previous();
            assert precedingPhase instanceof DeadCodeEliminationPhase : precedingPhase;
            duplicationPosition.remove();
        }
    }

    /// Creates the control flow duplication phases that clean up and iteratively expose escape
    /// analysis opportunities.
    protected final PhaseSuite<CoreProviders> createFinalPEACleanup(OptionValues options, CanonicalizerPhase canonicalizer) {
        if (!isControlFlowDuplicationEnabled(options)) {
            return null;
        }
        PhaseSuite<CoreProviders> cleanup = new PhaseSuite<>();
        this.<CoreProviders> appendControlFlowDuplicationPhases(cleanup::appendPhase, options, canonicalizer);
        return cleanup;
    }

    /// Gets the policy used to prevent duplication from harming later vectorization.
    protected DuplicationPhase.VectorizationCheck getDuplicationVectorizationCheck() {
        return (loop, graph, providers) -> false;
    }

    @Override
    public LoopPolicies createLoopPolicies(OptionValues options) {
        return new DefaultLoopPolicies();
    }
}
