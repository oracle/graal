/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.compiler.phases;

import com.oracle.graal.loop.DefaultLoopPolicies;
import com.oracle.graal.loop.LoopPolicies;
import com.oracle.graal.loop.phases.LoopFullUnrollPhase;
import com.oracle.graal.loop.phases.LoopPeelingPhase;
import com.oracle.graal.loop.phases.LoopUnswitchingPhase;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.options.Option;
import com.oracle.graal.options.OptionType;
import com.oracle.graal.options.OptionValue;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.inlining.InliningPhase;
import com.oracle.graal.phases.common.instrumentation.HighTierReconcileInstrumentationPhase;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.virtual.phases.ea.PartialEscapePhase;

import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

public class HighTier extends PhaseSuite<HighTierContext> {

    public static class Options {

        // @formatter:off
        @Option(help = "Enable inlining", type = OptionType.Expert)
        public static final OptionValue<Boolean> Inline = new OptionValue<>(true);
        // @formatter:on
    }

    public HighTier() {
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        if (ImmutableCode.getValue()) {
            canonicalizer.disableReadCanonicalization();
        }

        if (OptCanonicalizer.getValue()) {
            appendPhase(canonicalizer);
        }

        if (Options.Inline.getValue()) {
            appendPhase(new InliningPhase(canonicalizer));
            appendPhase(new DeadCodeEliminationPhase(Optional));

            if (ConditionalElimination.getValue() && OptCanonicalizer.getValue()) {
                appendPhase(canonicalizer);
                appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, false));
            }
        }

        if (OptConvertDeoptsToGuards.getValue()) {
            appendPhase(new ConvertDeoptimizeToGuardPhase());
        }

        LoopPolicies loopPolicies = createLoopPolicies();
        if (FullUnroll.getValue()) {
            appendPhase(new LoopFullUnrollPhase(canonicalizer, loopPolicies));
        }

        if (OptLoopTransform.getValue()) {
            if (LoopPeeling.getValue()) {
                appendPhase(new LoopPeelingPhase(loopPolicies));
            }
            if (LoopUnswitch.getValue()) {
                appendPhase(new LoopUnswitchingPhase(loopPolicies));
            }
        }

        if (OptCanonicalizer.getValue()) {
            appendPhase(canonicalizer);
        }

        if (PartialEscapeAnalysis.getValue()) {
            appendPhase(new PartialEscapePhase(true, canonicalizer));
        }
        appendPhase(new RemoveValueProxyPhase());

        appendPhase(new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER));
        if (UseGraalInstrumentation.getValue()) {
            appendPhase(new HighTierReconcileInstrumentationPhase());
        }
    }

    public LoopPolicies createLoopPolicies() {
        return new DefaultLoopPolicies();
    }
}
