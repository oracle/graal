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

import com.oracle.graal.loop.phases.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.virtual.phases.ea.*;

public class MidTier extends PhaseSuite<MidTierContext> {

    public MidTier() {
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase(!ImmutableCode.getValue());

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
            if (OptReadElimination.getValue()) {
                appendPhase(new ReadEliminationPhase());
            }
        }
        appendPhase(new RemoveValueProxyPhase());

        if (OptCanonicalizer.getValue()) {
            appendPhase(canonicalizer);
        }

        if (OptEliminatePartiallyRedundantGuards.getValue()) {
            appendPhase(new OptimizeGuardAnchorsPhase());
        }

        if (ConditionalElimination.getValue() && OptCanonicalizer.getValue()) {
            appendPhase(new IterativeConditionalEliminationPhase(canonicalizer));
        }

        if (OptEliminatePartiallyRedundantGuards.getValue()) {
            appendPhase(new OptimizeGuardAnchorsPhase());
        }

        if (OptCanonicalizer.getValue()) {
            appendPhase(canonicalizer);
        }

        appendPhase(new LoopSafepointEliminationPhase());

        appendPhase(new LoopSafepointInsertionPhase());

        appendPhase(new IncrementalCanonicalizerPhase<>(canonicalizer, new GuardLoweringPhase()));

        appendPhase(new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.MID_TIER));

        appendPhase(new FrameStateAssignmentPhase());

        if (OptDeoptimizationGrouping.getValue()) {
            appendPhase(new DeoptimizationGroupingPhase());
        }

        if (OptCanonicalizer.getValue()) {
            appendPhase(canonicalizer);
        }
    }
}
