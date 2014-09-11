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
import static com.oracle.graal.phases.common.DeadCodeEliminationPhase.Optionality.*;

import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.options.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;

public class LowTier extends PhaseSuite<LowTierContext> {

    static class Options {

        // @formatter:off
        @Option(help = "")
        public static final OptionValue<Boolean> ProfileCompiledMethods = new OptionValue<>(false);
        // @formatter:on

    }

    public LowTier() {
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase(!ImmutableCode.getValue());

        if (Options.ProfileCompiledMethods.getValue()) {
            appendPhase(new ProfileCompiledMethodsPhase());
        }

        appendPhase(new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.LOW_TIER));

        appendPhase(new RemoveValueProxyPhase());

        appendPhase(new ExpandLogicPhase());

        /* Cleanup IsNull checks resulting from MID_TIER/LOW_TIER lowering and ExpandLogic phase. */
        if (ConditionalElimination.getValue() && OptCanonicalizer.getValue()) {
            appendPhase(new IterativeConditionalEliminationPhase(canonicalizer));
            /* Canonicalizer may create some new ShortCircuitOrNodes so clean them up. */
            appendPhase(new ExpandLogicPhase());
        }

        appendPhase(new UseTrappingNullChecksPhase());

        appendPhase(new DeadCodeEliminationPhase(Required));
    }
}
