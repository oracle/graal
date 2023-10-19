/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.core.phases;

import static jdk.compiler.graal.phases.common.DeadCodeEliminationPhase.Optionality.Required;

import jdk.compiler.graal.core.common.GraalOptions;
import jdk.compiler.graal.options.Option;
import jdk.compiler.graal.options.OptionKey;
import jdk.compiler.graal.options.OptionType;
import jdk.compiler.graal.options.OptionValues;
import jdk.compiler.graal.phases.PlaceholderPhase;
import jdk.compiler.graal.phases.common.AddressLoweringPhase;
import jdk.compiler.graal.phases.common.CanonicalizerPhase;
import jdk.compiler.graal.phases.common.DeadCodeEliminationPhase;
import jdk.compiler.graal.phases.common.ExpandLogicPhase;
import jdk.compiler.graal.phases.common.FinalCanonicalizerPhase;
import jdk.compiler.graal.phases.common.FixReadsPhase;
import jdk.compiler.graal.phases.common.LowTierLoweringPhase;
import jdk.compiler.graal.phases.common.OptimizeExtendsPhase;
import jdk.compiler.graal.phases.common.ProfileCompiledMethodsPhase;
import jdk.compiler.graal.phases.common.PropagateDeoptimizeProbabilityPhase;
import jdk.compiler.graal.phases.schedule.SchedulePhase;
import jdk.compiler.graal.phases.schedule.SchedulePhase.SchedulingStrategy;
import jdk.compiler.graal.phases.tiers.LowTierContext;

public class LowTier extends BaseTier<LowTierContext> {

    static class Options {

        // @formatter:off
        @Option(help = "", type = OptionType.Debug)
        public static final OptionKey<Boolean> ProfileCompiledMethods = new OptionKey<>(false);
        // @formatter:on

    }

    private final CanonicalizerPhase canonicalizerWithoutGVN;
    private final CanonicalizerPhase canonicalizerWithGVN;

    @SuppressWarnings("this-escape")
    public LowTier(OptionValues options) {
        this.canonicalizerWithGVN = CanonicalizerPhase.create();
        this.canonicalizerWithoutGVN = canonicalizerWithGVN.copyWithoutGVN();

        if (Options.ProfileCompiledMethods.getValue(options)) {
            appendPhase(new ProfileCompiledMethodsPhase());
        }

        appendPhase(new LowTierLoweringPhase(canonicalizerWithGVN));

        appendPhase(new ExpandLogicPhase(canonicalizerWithGVN));

        appendPhase(new FixReadsPhase(true,
                        new SchedulePhase(GraalOptions.StressTestEarlyReads.getValue(options) ? SchedulingStrategy.EARLIEST : SchedulingStrategy.LATEST_OUT_OF_LOOPS_IMPLICIT_NULL_CHECKS)));

        appendPhase(canonicalizerWithoutGVN);

        /*
         * This placeholder should be replaced by an instance of {@link AddressLoweringPhase}
         * specific to the target architecture for this compilation. This should be done by the
         * backend or the target specific suites provider.
         */
        appendPhase(new PlaceholderPhase<>(AddressLoweringPhase.class));

        appendPhase(FinalCanonicalizerPhase.createFromCanonicalizer(canonicalizerWithoutGVN));

        appendPhase(new DeadCodeEliminationPhase(Required));

        appendPhase(new PropagateDeoptimizeProbabilityPhase());

        appendPhase(new OptimizeExtendsPhase());

        appendPhase(new SchedulePhase(SchedulePhase.SchedulingStrategy.LATEST_OUT_OF_LOOPS));
    }

    public final CanonicalizerPhase getCanonicalizerWithoutGVN() {
        return canonicalizerWithoutGVN;
    }

    public final CanonicalizerPhase getCanonicalizer() {
        return canonicalizerWithGVN;
    }
}
