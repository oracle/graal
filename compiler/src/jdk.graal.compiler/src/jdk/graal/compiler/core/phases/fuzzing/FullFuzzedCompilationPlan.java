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

import java.util.stream.Collectors;

import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.phases.tiers.MidTierContext;

/**
 * Compilation plan that inserts optional phases in a {@link MinimalFuzzedCompilationPlan}.
 *
 * See {@code compiler/docs/CompilationPlanFuzzing.md} for more details.
 */
public final class FullFuzzedCompilationPlan extends MinimalFuzzedCompilationPlan {
    /**
     * Defines the default {@link FullFuzzedTierPlan#getPhaseSkipOdds()} used to create
     * {@link FullFuzzedTierPlan}.
     */
    private static final int DEFAULT_PHASE_SKIP_ODDS = 10;

    /**
     * Represents the {@link MinimalFuzzedCompilationPlan} in which optional phases have been
     * inserted to create this full fuzzed compilation plan.
     */
    private final MinimalFuzzedCompilationPlan minimalFuzzedCompilationPlan;

    private FullFuzzedCompilationPlan(MinimalFuzzedCompilationPlan minimalFuzzedCompilationPlan,
                    FullFuzzedTierPlan<HighTierContext> highTier,
                    FullFuzzedTierPlan<MidTierContext> midTier,
                    FullFuzzedTierPlan<LowTierContext> lowTier,
                    GraphState graphState,
                    GraphState.MandatoryStages mandatoryStages,
                    long randomSeed) {
        super(highTier, midTier, lowTier, graphState, mandatoryStages, randomSeed);
        this.minimalFuzzedCompilationPlan = minimalFuzzedCompilationPlan;
    }

    @Override
    public String toString() {
        String phasePlan = super.toString().lines().skip(1).collect(Collectors.joining(System.lineSeparator()));
        return String.format("%s%nThe %sfull fuzzed compilation plan is:%n%s", minimalFuzzedCompilationPlan, PrintingUtils.printFailing(phasePlan), phasePlan);
    }

    /**
     * Creates a {@link FullFuzzedCompilationPlan} with {@link #DEFAULT_PHASE_SKIP_ODDS} as
     * {@link FullFuzzedTierPlan#getPhaseSkipOdds()} for each tier. See
     * {@link #createFullFuzzedCompilationPlan(MinimalFuzzedCompilationPlan, GraphState, int, int, int)}.
     */
    public static FullFuzzedCompilationPlan createFullFuzzedCompilationPlan(MinimalFuzzedCompilationPlan minimalFuzzedCompilationPlan, GraphState graphState) {
        return createFullFuzzedCompilationPlan(minimalFuzzedCompilationPlan, graphState, DEFAULT_PHASE_SKIP_ODDS, DEFAULT_PHASE_SKIP_ODDS, DEFAULT_PHASE_SKIP_ODDS);
    }

    /**
     * Creates a {@link FullFuzzedCompilationPlan} by inserting phases in the
     * {@link MinimalFuzzedCompilationPlan#getSuites()}. The phases inserted respect the phase
     * ordering constraints derived from the {@link GraphState}. Phases are inserted in high tier,
     * mid tier and low tier with their respective probability: 1/{@code phaseSkipOddsHighTier},
     * 1/{@code phaseSkipOddsMidTier} and 1/{@code phaseSkipOddsLowTier} (see
     * {@link FullFuzzedTierPlan#getPhaseSkipOdds()}).
     */
    public static FullFuzzedCompilationPlan createFullFuzzedCompilationPlan(MinimalFuzzedCompilationPlan minimalFuzzedCompilationPlan, GraphState graphState, int phaseSkipOddsHighTier,
                    int phaseSkipOddsMidTier, int phaseSkipOddsLowTier) {
        GraphState graphStateCopy = graphState.copy();
        FullFuzzedTierPlan<HighTierContext> highTier = FullFuzzedTierPlan.create((MinimalFuzzedTierPlan<HighTierContext>) minimalFuzzedCompilationPlan.getHighTier(), graphStateCopy,
                        minimalFuzzedCompilationPlan.getRandomSeed(), phaseSkipOddsHighTier, "High tier");
        highTier.updateGraphState(graphStateCopy);
        FullFuzzedTierPlan<MidTierContext> midTier = FullFuzzedTierPlan.create((MinimalFuzzedTierPlan<MidTierContext>) minimalFuzzedCompilationPlan.getMidTier(), graphStateCopy,
                        minimalFuzzedCompilationPlan.getRandomSeed(), phaseSkipOddsMidTier, "Mid tier");
        midTier.updateGraphState(graphStateCopy);
        FullFuzzedTierPlan<LowTierContext> lowTier = FullFuzzedTierPlan.create((MinimalFuzzedTierPlan<LowTierContext>) minimalFuzzedCompilationPlan.getLowTier(), graphStateCopy,
                        minimalFuzzedCompilationPlan.getRandomSeed(), phaseSkipOddsLowTier, "Low tier");
        return new FullFuzzedCompilationPlan(minimalFuzzedCompilationPlan.copy(), highTier, midTier, lowTier, graphState, minimalFuzzedCompilationPlan.getMandatoryStages(),
                        minimalFuzzedCompilationPlan.getRandomSeed());
    }

    /**
     * Saves this compilation plan as well as {@link #minimalFuzzedCompilationPlan} to the file
     * designated by @param dumpPath.
     */
    @Override
    public void saveCompilationPlan(String dumpPath) {
        minimalFuzzedCompilationPlan.saveCompilationPlan(dumpPath + "_minimal");
        super.saveCompilationPlan(dumpPath);
    }
}
