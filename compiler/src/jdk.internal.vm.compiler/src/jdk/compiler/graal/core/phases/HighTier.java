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
package jdk.compiler.graal.core.phases;

import static jdk.compiler.graal.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

import jdk.compiler.graal.core.common.GraalOptions;
import jdk.compiler.graal.loop.phases.ConvertDeoptimizeToGuardPhase;
import jdk.compiler.graal.loop.phases.LoopFullUnrollPhase;
import jdk.compiler.graal.loop.phases.LoopPeelingPhase;
import jdk.compiler.graal.loop.phases.LoopUnswitchingPhase;
import jdk.compiler.graal.nodes.loop.DefaultLoopPolicies;
import jdk.compiler.graal.nodes.loop.LoopPolicies;
import jdk.compiler.graal.options.Option;
import jdk.compiler.graal.options.OptionKey;
import jdk.compiler.graal.options.OptionType;
import jdk.compiler.graal.options.OptionValues;
import jdk.compiler.graal.phases.common.BoxNodeIdentityPhase;
import jdk.compiler.graal.phases.common.BoxNodeOptimizationPhase;
import jdk.compiler.graal.phases.common.CanonicalizerPhase;
import jdk.compiler.graal.phases.common.DeadCodeEliminationPhase;
import jdk.compiler.graal.phases.common.DisableOverflownCountedLoopsPhase;
import jdk.compiler.graal.phases.common.DominatorBasedGlobalValueNumberingPhase;
import jdk.compiler.graal.phases.common.HighTierLoweringPhase;
import jdk.compiler.graal.phases.common.IterativeConditionalEliminationPhase;
import jdk.compiler.graal.phases.common.inlining.InliningPhase;
import jdk.compiler.graal.phases.common.inlining.policy.GreedyInliningPolicy;
import jdk.compiler.graal.phases.tiers.HighTierContext;
import jdk.compiler.graal.virtual.phases.ea.FinalPartialEscapePhase;
import jdk.compiler.graal.virtual.phases.ea.ReadEliminationPhase;

public class HighTier extends BaseTier<HighTierContext> {

    public static class Options {

        // @formatter:off
        @Option(help = "Enable inlining", type = OptionType.Expert)
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
        appendPhase(new LoopFullUnrollPhase(canonicalizer, loopPolicies));

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
