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
package jdk.graal.compiler.core.phases;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.SpectrePHTMitigations;
import jdk.graal.compiler.loop.phases.LoopFullUnrollPhase;
import jdk.graal.compiler.loop.phases.LoopPartialUnrollPhase;
import jdk.graal.compiler.loop.phases.LoopPredicationPhase;
import jdk.graal.compiler.loop.phases.LoopSafepointEliminationPhase;
import jdk.graal.compiler.loop.phases.SpeculativeGuardMovementPhase;
import jdk.graal.compiler.nodes.loop.DefaultLoopPolicies;
import jdk.graal.compiler.nodes.loop.LoopPolicies;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DeoptimizationGroupingPhase;
import jdk.graal.compiler.phases.common.FloatingReadPhase;
import jdk.graal.compiler.phases.common.FrameStateAssignmentPhase;
import jdk.graal.compiler.phases.common.GuardLoweringPhase;
import jdk.graal.compiler.phases.common.InsertGuardFencesPhase;
import jdk.graal.compiler.phases.common.IterativeConditionalEliminationPhase;
import jdk.graal.compiler.phases.common.LockEliminationPhase;
import jdk.graal.compiler.phases.common.LoopSafepointInsertionPhase;
import jdk.graal.compiler.phases.common.MidTierLoweringPhase;
import jdk.graal.compiler.phases.common.OptimizeDivPhase;
import jdk.graal.compiler.phases.common.ReassociationPhase;
import jdk.graal.compiler.phases.common.RemoveValueProxyPhase;
import jdk.graal.compiler.phases.common.VerifyHeapAtReturnPhase;
import jdk.graal.compiler.phases.common.WriteBarrierAdditionPhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;

public class MidTier extends BaseTier<MidTierContext> {

    @SuppressWarnings("this-escape")
    public MidTier(OptionValues options) {
        CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();

        appendPhase(new LockEliminationPhase());

        if (GraalOptions.OptFloatingReads.getValue(options)) {
            appendPhase(new FloatingReadPhase(canonicalizer));
        }

        if (GraalOptions.ConditionalElimination.getValue(options)) {
            appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, true));
        }

        if (GraalOptions.LoopPredication.getValue(options) && !GraalOptions.SpeculativeGuardMovement.getValue(options)) {
            appendPhase(new LoopPredicationPhase(canonicalizer));
        }

        appendPhase(new LoopSafepointEliminationPhase());

        if (GraalOptions.SpeculativeGuardMovement.getValue(options)) {
            appendPhase(new SpeculativeGuardMovementPhase(canonicalizer));
        }

        appendPhase(new GuardLoweringPhase());

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

        if (GraalOptions.ConditionalElimination.getValue(options)) {
            appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, false));
        }

        if (GraalOptions.OptimizeDiv.getValue(options)) {
            appendPhase(new OptimizeDivPhase(canonicalizer));
        }

        appendPhase(new FrameStateAssignmentPhase());

        if (GraalOptions.PartialUnroll.getValue(options)) {
            LoopPolicies loopPolicies = createLoopPolicies(options);
            appendPhase(new LoopPartialUnrollPhase(loopPolicies, canonicalizer));
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

    @Override
    public LoopPolicies createLoopPolicies(OptionValues options) {
        return new DefaultLoopPolicies();
    }
}
