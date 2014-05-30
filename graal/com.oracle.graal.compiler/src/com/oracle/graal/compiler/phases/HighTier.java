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

import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.compiler.phases.HighTier.Options.*;
import com.oracle.graal.loop.phases.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.options.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.cfs.IterativeFlowSensitiveReductionPhase;
import com.oracle.graal.phases.common.inlining.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.virtual.phases.ea.*;

public class HighTier extends PhaseSuite<HighTierContext> {

    public static class Options {

        // @formatter:off
        @Option(help = "Enable inlining")
        public static final OptionValue<Boolean> Inline = new OptionValue<>(true);
        // @formatter:on
    }

    public HighTier() {
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase(!ImmutableCode.getValue());

        if (OptCanonicalizer.getValue()) {
            appendPhase(canonicalizer);
        }

        if (Inline.getValue()) {
            if (IterativeInlining.getValue()) {
                appendPhase(new IterativeInliningPhase(canonicalizer));
            } else {
                appendPhase(new InliningPhase(canonicalizer));
                appendPhase(new DeadCodeEliminationPhase());

                boolean reduceOrEliminate = FlowSensitiveReduction.getValue() || ConditionalElimination.getValue();
                if (reduceOrEliminate && OptCanonicalizer.getValue()) {
                    appendPhase(canonicalizer);
                    if (FlowSensitiveReduction.getValue()) {
                        appendPhase(new IterativeFlowSensitiveReductionPhase(canonicalizer));
                    } else {
                        appendPhase(new IterativeConditionalEliminationPhase(canonicalizer));
                    }
                }
            }
        }

        appendPhase(new CleanTypeProfileProxyPhase());

        if (FullUnroll.getValue()) {
            appendPhase(new LoopFullUnrollPhase(canonicalizer));
        }

        if (OptTailDuplication.getValue()) {
            appendPhase(new TailDuplicationPhase(canonicalizer));
        }

        if (PartialEscapeAnalysis.getValue()) {
            appendPhase(new PartialEscapePhase(true, canonicalizer));
        }

        if (OptConvertDeoptsToGuards.getValue()) {
            appendPhase(new ConvertDeoptimizeToGuardPhase());
        }

        if (OptLoopTransform.getValue()) {
            appendPhase(new LoopTransformHighPhase());
            appendPhase(new LoopTransformLowPhase());
        }
        appendPhase(new RemoveValueProxyPhase());

        if (OptCanonicalizer.getValue()) {
            appendPhase(canonicalizer);
        }

        appendPhase(new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER));
    }
}
