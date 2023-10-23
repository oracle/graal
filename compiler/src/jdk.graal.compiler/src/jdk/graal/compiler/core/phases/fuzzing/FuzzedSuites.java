/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.phases.fuzzing;

import java.util.Random;

import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.MandatoryStages;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.tiers.Suites;

/**
 * {@link Suites} created by fuzzing the order of the phases in each tier.
 *
 * See {@code compiler/docs/CompilationPlanFuzzing.md} for more details.
 */
public final class FuzzedSuites extends Suites {
    /**
     * Represents the ordering of phases resulting from fuzzing.
     */
    private final FullFuzzedCompilationPlan fullFuzzedCompilationPlan;

    private FuzzedSuites(FullFuzzedCompilationPlan fullFuzzedCompilationPlan) {
        super(fullFuzzedCompilationPlan.getSuites().getHighTier(), fullFuzzedCompilationPlan.getSuites().getMidTier(), fullFuzzedCompilationPlan.getSuites().getLowTier());
        this.fullFuzzedCompilationPlan = fullFuzzedCompilationPlan;
    }

    /**
     * Creates a {@link FuzzedSuites} by fuzzing the phases of the given {@link Suites}. This fuzzed
     * suites is created by inserting phases in the {@link MinimalFuzzedCompilationPlan} that
     * contains only the phases that allow to reach all the given {@link MandatoryStages}. The
     * phases retained respect the invariants of the {@link GraphState}. The {@code seed} is used to
     * initialize the {@link Random} generator.
     */
    public static FuzzedSuites createFuzzedSuites(Suites originalSuites, GraphState graphState, MandatoryStages mandatoryStages, long seed) {
        MinimalFuzzedCompilationPlan minimalFuzzedCompilationPlan = MinimalFuzzedCompilationPlan.createMinimalFuzzedCompilationPlan(originalSuites, graphState, mandatoryStages, seed);
        FullFuzzedCompilationPlan fullFuzzedCompilationPlan = FullFuzzedCompilationPlan.createFullFuzzedCompilationPlan(minimalFuzzedCompilationPlan, graphState);
        return new FuzzedSuites(fullFuzzedCompilationPlan);
    }

    /**
     * Saves this {@link #fullFuzzedCompilationPlan} as well as its minimal version (that contains
     * only the required phases to pass all the {@link MandatoryStages}) to the file designated
     * by @param dumpPath.
     */
    public void saveFuzzedSuites(String dumpPath) {
        fullFuzzedCompilationPlan.saveCompilationPlan(dumpPath);
    }

    /**
     * Adjusts the {@code baseOptions} with extra compiler options to be used when running with a
     * fuzzed phase plan. This can be used to relax some strict verification conditions that should
     * always hold during normal compilations but are not strictly required for the correctness of
     * fuzzed compilations.
     *
     * @return a new {@link OptionValues} instance containing extra compiler options
     */
    public static OptionValues fuzzingOptions(OptionValues baseOptions) {
        return new OptionValues(baseOptions,
                        /*
                         * Allow GVN even in a graph state where we would no longer allow GVN in
                         * normal compilations.
                         */
                        CanonicalizerPhase.Options.CanonicalizerVerifyGVNAllowed, false);
    }

    @Override
    public String toString() {
        return fullFuzzedCompilationPlan.toString();
    }
}
