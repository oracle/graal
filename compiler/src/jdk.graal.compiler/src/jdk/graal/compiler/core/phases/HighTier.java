/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import jdk.graal.compiler.loop.phases.LoopFullUnrollPhase;
import jdk.graal.compiler.loop.phases.LoopPeelingPhase;
import jdk.graal.compiler.loop.phases.LoopUnswitchingPhase;
import jdk.graal.compiler.nodes.loop.DefaultLoopPolicies;
import jdk.graal.compiler.nodes.loop.LoopPolicies;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
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

        if (Options.Inline.getValue(options)) {
            appendPhase(new InliningPhase(new GreedyInliningPolicy(null), canonicalizer));
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
        appendPhase(new BoxNodeIdentityPhase());

        if (GraalOptions.PartialEscapeAnalysis.getValue(options)) {
            appendPhase(new FinalPartialEscapePhase(true, canonicalizer, null, options));
        }

        if (VectorAPIIntrinsics.intrinsificationSupported(options)) {
            appendPhase(new VectorAPIExpansionPhase(canonicalizer));
        }

        if (GraalOptions.OptReadElimination.getValue(options)) {
            appendPhase(new ReadEliminationPhase(canonicalizer));
        }

        appendPhase(new BoxNodeOptimizationPhase(canonicalizer));
        appendPhase(new HighTierLoweringPhase(canonicalizer, true));
    }

    @Override
    public LoopPolicies createLoopPolicies(OptionValues options) {
        return new DefaultLoopPolicies();
    }
}
