/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.phases;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.ExpandLogicPhase;
import org.graalvm.compiler.phases.common.FixReadsPhase;
import org.graalvm.compiler.phases.common.vectorization.AutovectorizationPolicies;
import org.graalvm.compiler.phases.common.vectorization.DefaultAutovectorizationPolicies;
import org.graalvm.compiler.phases.common.vectorization.IsomorphicPackingPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.common.ProfileCompiledMethodsPhase;
import org.graalvm.compiler.phases.common.PropagateDeoptimizeProbabilityPhase;
import org.graalvm.compiler.phases.common.UseTrappingNullChecksPhase;
import org.graalvm.compiler.phases.common.vectorization.MethodList;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;
import org.graalvm.compiler.phases.tiers.LowTierContext;

import static org.graalvm.compiler.core.common.GraalOptions.ImmutableCode;
import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Required;

public class LowTier extends PhaseSuite<LowTierContext> {

    static class Options {

        // @formatter:off
        @Option(help = "", type = OptionType.Debug)
        public static final OptionKey<Boolean> ProfileCompiledMethods = new OptionKey<>(false);

        @Option(help = "Enable autovectorization", type = OptionType.Expert)
        public static final OptionKey<Boolean> Autovectorize = new OptionKey<>(true);

        @Option(help = "Substring of methods and/or classes that should be ex/included", type = OptionType.Debug)
        public static final OptionKey<String> AVList = new OptionKey<>("");

        @Option(help = "Whether AVList should be a blacklist or a whitelist", type = OptionType.Debug)
        public static final OptionKey<Boolean> AVWhitelist = new OptionKey<>(false); // Blacklist by default
        // @formatter:on

    }

    public LowTier(OptionValues options) {
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        CanonicalizerPhase canonicalizerWithoutGVN = new CanonicalizerPhase();
        canonicalizerWithoutGVN.disableGVN();
        if (ImmutableCode.getValue(options)) {
            canonicalizer.disableReadCanonicalization();
            canonicalizerWithoutGVN.disableReadCanonicalization();
        }

        if (Options.ProfileCompiledMethods.getValue(options)) {
            appendPhase(new ProfileCompiledMethodsPhase());
        }

        appendPhase(new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.LOW_TIER));

        appendPhase(new ExpandLogicPhase());

        appendPhase(new FixReadsPhase(true,
                        new SchedulePhase(GraalOptions.StressTestEarlyReads.getValue(options) ? SchedulingStrategy.EARLIEST : SchedulingStrategy.LATEST_OUT_OF_LOOPS_IMPLICIT_NULL_CHECKS)));

        appendPhase(canonicalizerWithoutGVN);

        appendPhase(new UseTrappingNullChecksPhase());

        if (Options.Autovectorize.getValue(options)) {
            final String listValue = Options.AVList.getValue(options).trim();
            List<String> list = new ArrayList<>();
            if (!listValue.isEmpty()) {
                list = Arrays.asList(listValue.split(","));
            }

            appendPhase(new IsomorphicPackingPhase(
                    new SchedulePhase(SchedulingStrategy.EARLIEST),
                    createAutovectorizationPolicies(),
                    new MethodList(list, Options.AVWhitelist.getValue(options)),
                    NodeView.DEFAULT));
        }

        appendPhase(canonicalizer);

        appendPhase(new DeadCodeEliminationPhase(Required));

        appendPhase(new PropagateDeoptimizeProbabilityPhase());

        appendPhase(new SchedulePhase(SchedulePhase.SchedulingStrategy.LATEST_OUT_OF_LOOPS));
    }

    public AutovectorizationPolicies createAutovectorizationPolicies() {
        return new DefaultAutovectorizationPolicies();
    }
}
