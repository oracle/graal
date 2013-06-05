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

import static com.oracle.graal.phases.GraalOptions.*;

import com.oracle.graal.loop.phases.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;

public class MidTier extends PhaseSuite<MidTierContext> {

    public MidTier() {
        if (OptPushThroughPi.getValue()) {
            addPhase(new PushThroughPiPhase());
            if (OptCanonicalizer.getValue()) {
                addPhase(new CanonicalizerPhase());
            }
        }

        if (OptFloatingReads.getValue()) {
            IncrementalCanonicalizerPhase<MidTierContext> canonicalizer = new IncrementalCanonicalizerPhase<>();
            canonicalizer.addPhase(new FloatingReadPhase());
            addPhase(canonicalizer);
            if (OptReadElimination.getValue()) {
                addPhase(new ReadEliminationPhase());
            }
        }
        addPhase(new RemoveValueProxyPhase());

        if (OptCanonicalizer.getValue()) {
            addPhase(new CanonicalizerPhase());
        }

        if (OptEliminatePartiallyRedundantGuards.getValue()) {
            addPhase(new EliminatePartiallyRedundantGuardsPhase(false, true));
        }

        if (ConditionalElimination.getValue() && OptCanonicalizer.getValue()) {
            addPhase(new IterativeConditionalEliminationPhase());
        }

        if (OptEliminatePartiallyRedundantGuards.getValue()) {
            addPhase(new EliminatePartiallyRedundantGuardsPhase(true, true));
        }

        if (OptCanonicalizer.getValue()) {
            addPhase(new CanonicalizerPhase());
        }

        addPhase(new LoopSafepointEliminationPhase());

        addPhase(new SafepointInsertionPhase());

        addPhase(new GuardLoweringPhase());

        if (OptCanonicalizer.getValue()) {
            addPhase(new CanonicalizerPhase());
        }
    }
}
