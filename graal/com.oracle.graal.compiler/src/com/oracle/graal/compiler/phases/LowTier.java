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
import static com.oracle.graal.phases.common.DeadCodeEliminationPhase.Optionality.Required;
import jdk.internal.jvmci.options.Option;
import jdk.internal.jvmci.options.OptionType;
import jdk.internal.jvmci.options.OptionValue;

import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.common.DeadCodeEliminationPhase;
import com.oracle.graal.phases.common.ExpandLogicPhase;
import com.oracle.graal.phases.common.IterativeConditionalEliminationPhase;
import com.oracle.graal.phases.common.LoweringPhase;
import com.oracle.graal.phases.common.ProfileCompiledMethodsPhase;
import com.oracle.graal.phases.common.RemoveValueProxyPhase;
import com.oracle.graal.phases.common.UseTrappingNullChecksPhase;
import com.oracle.graal.phases.tiers.LowTierContext;

public class LowTier extends PhaseSuite<LowTierContext> {

    static class Options {

        // @formatter:off
        @Option(help = "", type = OptionType.Debug)
        public static final OptionValue<Boolean> ProfileCompiledMethods = new OptionValue<>(false);
        // @formatter:on

    }

    public LowTier() {
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        if (ImmutableCode.getValue()) {
            canonicalizer.disableReadCanonicalization();
        }

        if (Options.ProfileCompiledMethods.getValue()) {
            appendPhase(new ProfileCompiledMethodsPhase());
        }

        appendPhase(new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.LOW_TIER));

        appendPhase(new RemoveValueProxyPhase());

        appendPhase(new ExpandLogicPhase());

        /* Cleanup IsNull checks resulting from MID_TIER/LOW_TIER lowering and ExpandLogic phase. */
        if (ConditionalElimination.getValue() && OptCanonicalizer.getValue()) {
            appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, false));
            /* Canonicalizer may create some new ShortCircuitOrNodes so clean them up. */
            appendPhase(new ExpandLogicPhase());
        }

        appendPhase(new UseTrappingNullChecksPhase());

        appendPhase(new DeadCodeEliminationPhase(Required));
    }
}
