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
import static com.oracle.graal.compiler.common.GraalOptions.ImmutableCode;
import static com.oracle.graal.compiler.common.GraalOptions.OptCanonicalizer;
import static com.oracle.graal.compiler.common.GraalOptions.OptDeoptimizationGrouping;
import static com.oracle.graal.compiler.common.GraalOptions.OptEliminatePartiallyRedundantGuards;
import static com.oracle.graal.compiler.common.GraalOptions.OptFloatingReads;
import static com.oracle.graal.compiler.common.GraalOptions.OptPushThroughPi;
import static com.oracle.graal.compiler.common.GraalOptions.OptReadElimination;
import static com.oracle.graal.compiler.common.GraalOptions.ReassociateInvariants;
import static com.oracle.graal.compiler.common.GraalOptions.VerifyHeapAtReturn;

import com.oracle.graal.loop.phases.LoopSafepointEliminationPhase;
import com.oracle.graal.loop.phases.ReassociateInvariantPhase;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.common.DeoptimizationGroupingPhase;
import com.oracle.graal.phases.common.FloatingReadPhase;
import com.oracle.graal.phases.common.FrameStateAssignmentPhase;
import com.oracle.graal.phases.common.GuardLoweringPhase;
import com.oracle.graal.phases.common.IncrementalCanonicalizerPhase;
import com.oracle.graal.phases.common.IterativeConditionalEliminationPhase;
import com.oracle.graal.phases.common.LockEliminationPhase;
import com.oracle.graal.phases.common.LoopSafepointInsertionPhase;
import com.oracle.graal.phases.common.LoweringPhase;
import com.oracle.graal.phases.common.OptimizeGuardAnchorsPhase;
import com.oracle.graal.phases.common.PushThroughPiPhase;
import com.oracle.graal.phases.common.RemoveValueProxyPhase;
import com.oracle.graal.phases.common.ValueAnchorCleanupPhase;
import com.oracle.graal.phases.common.VerifyHeapAtReturnPhase;
import com.oracle.graal.phases.tiers.MidTierContext;
import com.oracle.graal.virtual.phases.ea.EarlyReadEliminationPhase;

public class MidTier extends PhaseSuite<MidTierContext> {

    public MidTier() {
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        if (ImmutableCode.getValue()) {
            canonicalizer.disableReadCanonicalization();
        }

        if (OptPushThroughPi.getValue()) {
            appendPhase(new PushThroughPiPhase());
        }
        if (OptCanonicalizer.getValue()) {
            appendPhase(canonicalizer);
        }

        appendPhase(new ValueAnchorCleanupPhase());
        appendPhase(new LockEliminationPhase());

        if (OptReadElimination.getValue()) {
            appendPhase(new EarlyReadEliminationPhase(canonicalizer));
        }

        if (OptFloatingReads.getValue()) {
            appendPhase(new IncrementalCanonicalizerPhase<>(canonicalizer, new FloatingReadPhase()));
        }
        appendPhase(new RemoveValueProxyPhase());

        if (OptCanonicalizer.getValue()) {
            appendPhase(canonicalizer);
        }

        if (OptEliminatePartiallyRedundantGuards.getValue()) {
            appendPhase(new OptimizeGuardAnchorsPhase());
        }

        if (ConditionalElimination.getValue() && OptCanonicalizer.getValue()) {
            appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, true));
        }

        if (OptEliminatePartiallyRedundantGuards.getValue()) {
            appendPhase(new OptimizeGuardAnchorsPhase());
        }

        if (OptCanonicalizer.getValue()) {
            appendPhase(canonicalizer);
        }

        appendPhase(new IncrementalCanonicalizerPhase<>(canonicalizer, new LoopSafepointEliminationPhase()));

        appendPhase(new LoopSafepointInsertionPhase());

        appendPhase(new IncrementalCanonicalizerPhase<>(canonicalizer, new GuardLoweringPhase()));

        if (VerifyHeapAtReturn.getValue()) {
            appendPhase(new VerifyHeapAtReturnPhase());
        }

        appendPhase(new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.MID_TIER));

        appendPhase(new FrameStateAssignmentPhase());

        if (ReassociateInvariants.getValue()) {
            appendPhase(new ReassociateInvariantPhase());
        }

        if (OptDeoptimizationGrouping.getValue()) {
            appendPhase(new DeoptimizationGroupingPhase());
        }

        if (OptCanonicalizer.getValue()) {
            appendPhase(canonicalizer);
        }
    }
}
