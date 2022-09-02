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
package org.graalvm.compiler.core.phases;

import static org.graalvm.compiler.core.common.GraalOptions.ConditionalElimination;
import static org.graalvm.compiler.core.common.GraalOptions.EarlyGVN;
import static org.graalvm.compiler.core.common.GraalOptions.LoopPeeling;
import static org.graalvm.compiler.core.common.GraalOptions.LoopUnswitch;
import static org.graalvm.compiler.core.common.GraalOptions.OptConvertDeoptsToGuards;
import static org.graalvm.compiler.core.common.GraalOptions.OptReadElimination;
import static org.graalvm.compiler.core.common.GraalOptions.PartialEscapeAnalysis;
import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

import org.graalvm.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.loop.phases.LoopFullUnrollPhase;
import org.graalvm.compiler.loop.phases.LoopPeelingPhase;
import org.graalvm.compiler.loop.phases.LoopUnswitchingPhase;
import org.graalvm.compiler.nodes.loop.DefaultLoopPolicies;
import org.graalvm.compiler.nodes.loop.LoopPolicies;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.BoxNodeIdentityPhase;
import org.graalvm.compiler.phases.common.BoxNodeOptimizationPhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.DisableOverflownCountedLoopsPhase;
import org.graalvm.compiler.phases.common.DominatorBasedGlobalValueNumberingPhase;
import org.graalvm.compiler.phases.common.HighTierLoweringPhase;
import org.graalvm.compiler.phases.common.IterativeConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.NodeCounterPhase;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;
import org.graalvm.compiler.phases.common.inlining.policy.GreedyInliningPolicy;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.virtual.phases.ea.FinalPartialEscapePhase;
import org.graalvm.compiler.virtual.phases.ea.ReadEliminationPhase;

public class HighTier extends BaseTier<HighTierContext> {

    public static class Options {

        // @formatter:off
        @Option(help = "Enable inlining", type = OptionType.Expert)
        public static final OptionKey<Boolean> Inline = new OptionKey<>(true);
        // @formatter:on
    }

    public HighTier(OptionValues options) {
        CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();
        appendPhase(canonicalizer);

        if (NodeCounterPhase.Options.NodeCounters.getValue(options)) {
            appendPhase(new NodeCounterPhase(NodeCounterPhase.Stage.INIT));
        }

        if (Options.Inline.getValue(options)) {
            appendPhase(new InliningPhase(new GreedyInliningPolicy(null), canonicalizer));
            appendPhase(new DeadCodeEliminationPhase(Optional));
        }

        appendPhase(new DisableOverflownCountedLoopsPhase());

        if (NodeCounterPhase.Options.NodeCounters.getValue(options)) {
            appendPhase(new NodeCounterPhase(NodeCounterPhase.Stage.EARLY));
        }

        if (OptConvertDeoptsToGuards.getValue(options)) {
            appendPhase(new ConvertDeoptimizeToGuardPhase(canonicalizer));
        }

        if (ConditionalElimination.getValue(options)) {
            appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, false));
        }

        if (EarlyGVN.getValue(options)) {
            appendPhase(new DominatorBasedGlobalValueNumberingPhase(canonicalizer));
        }

        LoopPolicies loopPolicies = createLoopPolicies(options);
        appendPhase(new LoopFullUnrollPhase(canonicalizer, loopPolicies));

        if (LoopPeeling.getValue(options)) {
            appendPhase(new LoopPeelingPhase(loopPolicies, canonicalizer));
        }

        if (LoopUnswitch.getValue(options)) {
            appendPhase(new LoopUnswitchingPhase(loopPolicies, canonicalizer));
        }

        // Must precede all phases that otherwise ignore the identity of boxes (e.g.
        // PartialEscapePhase and BoxNodeOptimizationPhase).
        appendPhase(new BoxNodeIdentityPhase());

        if (PartialEscapeAnalysis.getValue(options)) {
            appendPhase(new FinalPartialEscapePhase(true, canonicalizer, null, options));
        }

        if (OptReadElimination.getValue(options)) {
            appendPhase(new ReadEliminationPhase(canonicalizer));
        }

        if (NodeCounterPhase.Options.NodeCounters.getValue(options)) {
            appendPhase(new NodeCounterPhase(NodeCounterPhase.Stage.LATE));
        }

        appendPhase(new BoxNodeOptimizationPhase(canonicalizer));
        appendPhase(new HighTierLoweringPhase(canonicalizer, true));
    }

    @Override
    public LoopPolicies createLoopPolicies(OptionValues options) {
        return new DefaultLoopPolicies();
    }
}
