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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.PhaseSuite;

/**
 * Tier plan that inserts optional phases in a {@link MinimalFuzzedTierPlan}.
 *
 * See {@code compiler/docs/CompilationPlanFuzzing.md} for more details.
 */
public final class FullFuzzedTierPlan<C> extends MinimalFuzzedTierPlan<C> {
    /**
     * Represents the {@link MinimalFuzzedTierPlan} in which optional phases will be inserted to
     * create this full fuzzed tier plan.
     */
    private final MinimalFuzzedTierPlan<C> minimalFuzzedTierPlan;

    /**
     * Determines the probability that a phase has to be inserted at each position in the resulting
     * fuzzed tier plan. When such a phase is chosen from the bag of available phases, the fuzzer
     * will try to insert it at each position of the current {@link #getPhaseSuite} with probability
     * 1/{@link #phaseSkipOdds}.
     */
    private final int phaseSkipOdds;

    private FullFuzzedTierPlan(MinimalFuzzedTierPlan<C> minimalTierPlan,
                    GraphState graphState,
                    long randomSeed,
                    int phaseSkipOdds,
                    String tierName) {
        this(minimalTierPlan.getSingleApplyPhases(), minimalTierPlan.getMultiApplyPhases(), minimalTierPlan.getIgnoredPhases(), new PhaseSuite<C>(), minimalTierPlan, randomSeed,
                        phaseSkipOdds, tierName);
        computeFullFuzzedTierPlan(graphState);
    }

    /**
     * This should only be used as a copy constructor.
     */
    private FullFuzzedTierPlan(List<BasePhase<? super C>> singleApplyPhases,
                    List<BasePhase<? super C>> multiApplyPhases,
                    Set<BasePhase<? super C>> unusedPhases,
                    PhaseSuite<C> phaseSuite,
                    MinimalFuzzedTierPlan<C> minimalFuzzedTierPlan,
                    long randomSeed,
                    int phaseSkipOdds,
                    String tierName) {
        super(singleApplyPhases, multiApplyPhases, unusedPhases, phaseSuite, randomSeed, tierName);
        this.minimalFuzzedTierPlan = minimalFuzzedTierPlan;
        this.phaseSkipOdds = phaseSkipOdds;
    }

    /**
     * Creates and computes a new full fuzzed tier plan by inserting optional phases in the given
     * minimal tier plan.
     */
    protected static <C> FullFuzzedTierPlan<C> create(MinimalFuzzedTierPlan<C> minimalTierPlan,
                    GraphState graphState,
                    long randomSeed,
                    int phaseSkipOdds,
                    String tierName) {
        return new FullFuzzedTierPlan<>(minimalTierPlan, graphState, randomSeed, phaseSkipOdds, tierName);
    }

    /**
     * Computes the full fuzzed tier plan with the following steps:
     * <ol>
     * <li>Initialize this {@link #getPhaseSuite()} to correspond to {@link #minimalFuzzedTierPlan}
     * {@link #getPhaseSuite()}.</li>
     *
     * <li>Insert into the fuzzed tier plan all the phases that {@link BasePhase#mustApply} with the
     * given {@link GraphState}. See {@link MinimalFuzzedTierPlan#insertPhasesThatMustApply}.</li>
     *
     * <li>Insert optional phases that can be applied at most once (see
     * {@link #getSingleApplyPhases()}). Each phase is inserted by following the logic described by
     * this pseudo-code:
     *
     * <pre>
     * phase = pickRandomPhase()
     * for randomPosition in positionsInThePlan {
     *      if(skipped){
     *          continue;
     *      }
     *      newSuite = getPhaseSuite().insertAtIndex(randomPosition, phase);
     *      if(newSuite canBeApplied){
     *          setPhaseSuite(newSuite);
     *          break;
     *      }
     * }
     * </pre>
     *
     * The probability of skipping insertion at any point is equal to 1/{@link #phaseSkipOdds}.</li>
     *
     * <li>Insert optional phases that can be applied multiple times (see
     * {@link #getMultiApplyPhases()}). Each phase is inserted by following the logic described by
     * this pseudo-code:
     *
     * <pre>
     * phase = pickRandomPhase()
     * for position in positionsInThePlan {
     *      if(skipped){
     *          continue;
     *      }
     *      newSuite = getPhaseSuite().insertAtIndex(position, phase);
     *      if(newSuite canBeApplied){
     *          setPhaseSuite(newSuite);
     *      }
     * }
     * </pre>
     *
     * The probability of skipping insertion at any point is equal to 1/{@link #phaseSkipOdds}.</li>
     *
     * <li>Insert all the phases that {@link BasePhase#mustApply} after applying the tier plan
     * resulting from the last step. See
     * {@link MinimalFuzzedTierPlan#insertPhasesThatMustApply}.</li>
     * </ol>
     */
    private void computeFullFuzzedTierPlan(GraphState graphState) {
        Random random = new Random(getRandomSeed());
        /**
         * 1. Initialize the tier plan to correspond to the {@link #minimalFuzzedTierPlan}.
         */
        setPhaseSuite(minimalFuzzedTierPlan.getPhaseSuite().copy());
        /**
         * 2. Insert phases to create a tier plan that applies all the {@code mandatoryStages}.
         */
        insertPhasesThatMustApply(graphState);
        /**
         * 3. Insert phases that can be applied at most once.
         */
        // Randomize the positions of insertion since the phase can only be inserted once.
        int suiteSize = getPhaseSuite().getPhases().size();
        List<Integer> indices = new ArrayList<>(suiteSize);
        for (int index = 0; index < suiteSize; index++) {
            indices.add(index);
        }
        Collections.shuffle(indices, random);
        List<BasePhase<? super C>> availablePhases = new ArrayList<>(getSingleApplyPhases());
        Collections.shuffle(availablePhases, random);
        for (BasePhase<? super C> phase : availablePhases) {
            for (Integer positionInSuite : indices) {
                if (random.nextInt(phaseSkipOdds) > 0) {
                    continue;
                }
                if (insertPhaseAtIndex(phase, positionInSuite, graphState)) {
                    suiteSize += 1;
                    indices.add(suiteSize);
                    Collections.shuffle(indices, random);
                    break;
                }
            }
        }
        /**
         * 4. Insert phases that can be applied multiple times.
         */
        availablePhases = new ArrayList<>(getMultiApplyPhases());
        Collections.shuffle(availablePhases, random);
        for (BasePhase<? super C> phase : availablePhases) {
            for (int positionInSuite = 0; positionInSuite <= getPhaseSuite().getPhases().size(); positionInSuite++) {
                if (random.nextInt(phaseSkipOdds) > 0) {
                    continue;
                }
                insertPhaseAtIndex(phase, positionInSuite, graphState);
            }
        }
        /**
         * 5. Insert phases to resolve the last {@link GraphState#getStageDependencies()}.
         */
        insertPhasesThatMustApply(graphState);
    }

    /**
     * @return the odds to skip the insertion of a phase at each position of the fuzzed tier plan.
     */
    public int getPhaseSkipOdds() {
        return phaseSkipOdds;
    }

    @Override
    public String toString() {
        return String.format("%s%n%s%nProbability of inserting a phase: 1/%s (-Dtest.graal.skip.phase.insertion.odds.%s=%s)%n",
                        super.toString(), ignoredPhasesToString(), phaseSkipOdds, String.join(".", getTierName().toLowerCase().split(" ")), phaseSkipOdds);
    }

    @Override
    public FullFuzzedTierPlan<C> copy() {
        return new FullFuzzedTierPlan<>(new ArrayList<>(getSingleApplyPhases()), new ArrayList<>(getMultiApplyPhases()), new HashSet<>(getIgnoredPhases()), getPhaseSuite().copy(),
                        minimalFuzzedTierPlan.copy(), getRandomSeed(), phaseSkipOdds, getTierName());
    }
}
