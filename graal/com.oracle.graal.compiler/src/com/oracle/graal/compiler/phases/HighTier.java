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

import static com.oracle.graal.compiler.common.GraalOptions.ConditionalElimination;
import static com.oracle.graal.compiler.common.GraalOptions.FullUnroll;
import static com.oracle.graal.compiler.common.GraalOptions.ImmutableCode;
import static com.oracle.graal.compiler.common.GraalOptions.LoopPeeling;
import static com.oracle.graal.compiler.common.GraalOptions.LoopUnswitch;
import static com.oracle.graal.compiler.common.GraalOptions.OptCanonicalizer;
import static com.oracle.graal.compiler.common.GraalOptions.OptConvertDeoptsToGuards;
import static com.oracle.graal.compiler.common.GraalOptions.OptLoopTransform;
import static com.oracle.graal.compiler.common.GraalOptions.PartialEscapeAnalysis;
import static com.oracle.graal.compiler.common.GraalOptions.UseGraalQueries;
import static com.oracle.graal.compiler.phases.HighTier.Options.Inline;
import static com.oracle.graal.phases.common.DeadCodeEliminationPhase.Optionality.Optional;
import jdk.internal.jvmci.options.Option;
import jdk.internal.jvmci.options.OptionType;
import jdk.internal.jvmci.options.OptionValue;

import com.oracle.graal.loop.phases.LoopFullUnrollPhase;
import com.oracle.graal.loop.phases.LoopPeelingPhase;
import com.oracle.graal.loop.phases.LoopUnswitchingPhase;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.common.ConvertDeoptimizeToGuardPhase;
import com.oracle.graal.phases.common.DeadCodeEliminationPhase;
import com.oracle.graal.phases.common.IterativeConditionalEliminationPhase;
import com.oracle.graal.phases.common.LoweringPhase;
import com.oracle.graal.phases.common.RemoveValueProxyPhase;
import com.oracle.graal.phases.common.inlining.InliningPhase;
import com.oracle.graal.phases.common.query.HighTierReconcileICGPhase;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.virtual.phases.ea.PartialEscapePhase;

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

        if (Inline.getValue()) {
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

        if (FullUnroll.getValue()) {
            appendPhase(new LoopFullUnrollPhase(canonicalizer));
        }

        if (OptLoopTransform.getValue()) {
            if (LoopPeeling.getValue()) {
                appendPhase(new LoopPeelingPhase());
            }
            if (LoopUnswitch.getValue()) {
                appendPhase(new LoopUnswitchingPhase());
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
        if (UseGraalQueries.getValue()) {
            appendPhase(new HighTierReconcileICGPhase());
        }
    }
}
