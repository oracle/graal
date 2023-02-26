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
package org.graalvm.compiler.core.phases.fuzzing;

import java.util.Random;

import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.GraphState.MandatoryStages;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.LowTierContext;
import org.graalvm.compiler.phases.tiers.MidTierContext;
import org.graalvm.compiler.phases.tiers.Suites;

/**
 * {@link AbstractCompilationPlan} created by fuzzing and only contains the phases required to
 * satisfy {@link GraphState#hasAllMandatoryStages}.
 *
 * See {@code compiler/docs/CompilationPlanFuzzing.md} for more details.
 */
public class MinimalFuzzedCompilationPlan extends AbstractCompilationPlan {
    /**
     * Represents the seed used for initializing {@link Random} instances and which allows to
     * reproduce this compilation plan.
     */
    private final long randomSeed;

    protected MinimalFuzzedCompilationPlan(MinimalFuzzedTierPlan<HighTierContext> minimalHighTier,
                    MinimalFuzzedTierPlan<MidTierContext> minimalMidTier,
                    MinimalFuzzedTierPlan<LowTierContext> minimalLowTier,
                    GraphState graphState,
                    GraphState.MandatoryStages mandatoryStages,
                    long randomSeed) {
        super(minimalHighTier, minimalMidTier, minimalLowTier, graphState, mandatoryStages);
        this.randomSeed = randomSeed;
        verifyCompilationPlan(graphState);
    }

    /**
     * This is a copy constructor and should only be used for this purpose.
     */
    private MinimalFuzzedCompilationPlan(MinimalFuzzedTierPlan<HighTierContext> minimalHighTier,
                    MinimalFuzzedTierPlan<MidTierContext> minimalMidTier,
                    MinimalFuzzedTierPlan<LowTierContext> minimalLowTier,
                    GraphState.MandatoryStages mandatoryStages,
                    long randomSeed) {
        super(minimalHighTier, minimalMidTier, minimalLowTier, mandatoryStages);
        this.randomSeed = randomSeed;
    }

    /**
     * Creates a compilation plan by fuzzing the phases of the given {@link Suites}. The phases
     * retained are the ones that apply one of the {@link MandatoryStages}. This compilation plan
     * respect the invariants of the {@link GraphState}. The {@code seed} is used to initialize the
     * {@link Random} generator.
     */
    public static MinimalFuzzedCompilationPlan createMinimalFuzzedCompilationPlan(Suites originalSuites, GraphState graphState, GraphState.MandatoryStages mandatoryStages, long randomSeed) {
        GraphState graphStateCopy = graphState.copy();
        addInitialRequiredStages(graphStateCopy);
        MinimalFuzzedTierPlan<HighTierContext> highTier = MinimalFuzzedTierPlan.create(originalSuites.getHighTier().getPhases(), graphStateCopy, mandatoryStages.getHighTier(), randomSeed,
                        "High tier");
        highTier.updateGraphState(graphStateCopy);
        MinimalFuzzedTierPlan<MidTierContext> midTier = MinimalFuzzedTierPlan.create(originalSuites.getMidTier().getPhases(), graphStateCopy, mandatoryStages.getMidTier(), randomSeed, "Mid tier");
        midTier.updateGraphState(graphStateCopy);
        MinimalFuzzedTierPlan<LowTierContext> lowTier = MinimalFuzzedTierPlan.create(originalSuites.getLowTier().getPhases(), graphStateCopy, mandatoryStages.getLowTier(), randomSeed, "Low tier");
        return new MinimalFuzzedCompilationPlan(highTier, midTier, lowTier, graphState, mandatoryStages, randomSeed);
    }

    /**
     * @return the seed used to initialize {@link Random} instances.
     */
    public long getRandomSeed() {
        return randomSeed;
    }

    @Override
    public String toString() {
        String phasePlan = super.toString();
        return String.format("The %sminimal fuzzed compilation plan based on random seed %s (-Dtest.graal.compilationplan.fuzzing.seed=%s) is:%n%s",
                        PrintingUtils.printFailing(phasePlan), getRandomSeed(), getRandomSeed(), PrintingUtils.indent(phasePlan));
    }

    /**
     * Creates a deep copy of this compilation plan.
     */
    public MinimalFuzzedCompilationPlan copy() {
        return new MinimalFuzzedCompilationPlan(((MinimalFuzzedTierPlan<HighTierContext>) this.getHighTier()).copy(), ((MinimalFuzzedTierPlan<MidTierContext>) this.getMidTier()).copy(),
                        ((MinimalFuzzedTierPlan<LowTierContext>) this.getLowTier()).copy(), getMandatoryStages(), getRandomSeed());
    }

}
