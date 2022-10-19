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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Formatter;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

import org.graalvm.compiler.core.phases.fuzzing.AbstractCompilationPlan.PrintingUtils;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.GraphState.StageFlag;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.BasePhase.NotApplicable;
import org.graalvm.compiler.phases.PhaseSuite;

/**
 * {@link AbstractTierPlan} that only contains the phases required to satisfy a given set of
 * mandatory stages.
 *
 * See {@code compiler/docs/CompilationPlanFuzzing.md} for more details.
 */
class MinimalFuzzedTierPlan<C> extends AbstractTierPlan<C> {
    /**
     * Contains phases that were given to construct the fuzzed tier plan but were ignored by the
     * fuzzer and are not in the resulting tier plan.
     */
    private final Set<BasePhase<? super C>> ignoredPhases;

    /**
     * Represents the seed used for initializing {@link Random} instances and which allows to
     * reproduce this tier plan.
     */
    private final long randomSeed;

    private MinimalFuzzedTierPlan(List<BasePhase<? super C>> originalPhases,
                    GraphState graphState,
                    EnumSet<StageFlag> mandatoryStages,
                    long randomSeed,
                    String tierName) {
        super(originalPhases, tierName);
        this.ignoredPhases = new HashSet<>(originalPhases);
        this.randomSeed = randomSeed;
        computeMinimalFuzzedTierPlan(graphState, mandatoryStages);
    }

    /**
     * This should only be used as a copy or superclass constructor.
     */
    protected MinimalFuzzedTierPlan(List<BasePhase<? super C>> singleApplyPhases,
                    List<BasePhase<? super C>> multiApplyPhases,
                    Set<BasePhase<? super C>> ignoredPhases,
                    PhaseSuite<C> minimalPhaseSuite,
                    long randomSeed,
                    String tierName) {
        super(singleApplyPhases, multiApplyPhases, minimalPhaseSuite, tierName);
        this.ignoredPhases = ignoredPhases;
        this.randomSeed = randomSeed;
    }

    /**
     * Creates and computes a new minimal fuzzed tier plan that is a permutation and/or subset of
     * the original phases.
     */
    protected static <C> MinimalFuzzedTierPlan<C> create(List<BasePhase<? super C>> originalPhases,
                    GraphState graphState,
                    EnumSet<StageFlag> mandatoryStages,
                    long seed,
                    String tierName) {
        return new MinimalFuzzedTierPlan<>(originalPhases, graphState, mandatoryStages, seed, tierName);
    }

    /**
     * Computes the minimal fuzzed tier plan that applies all the {@link StageFlag stage} in
     * {@code mandatoryStages} in three steps:
     * <ol>
     * <li>Add to the fuzzed tier plan all the phases that {@link BasePhase#mustApply} with the
     * given {@link GraphState}. See {@link MinimalFuzzedTierPlan#insertPhasesThatMustApply}.</li>
     *
     * <li>Loop over the phases that modify the {@link GraphState} (see
     * {@link #getSingleApplyPhases()}) by applying a mandatory stage and insert them in the tier
     * plan if the resulting {@link MinimalFuzzedTierPlan#getPhaseSuite} can be applied (see
     * {@link PhaseSuite#notApplicableTo(GraphState)}). <br>
     * This is done until all the {@code mandatoryStages} are applied by one of the phase in the
     * tier plan or the maximum number of attempts has been reached. <br>
     * </li>
     *
     * <li>Insert all the phases that {@link BasePhase#mustApply} after applying the tier plan
     * resulting from step 2. See {@link MinimalFuzzedTierPlan#insertPhasesThatMustApply}.</li>
     * </ol>
     */
    private void computeMinimalFuzzedTierPlan(GraphState graphState, EnumSet<StageFlag> mandatoryStages) {
        Random random = new Random(getRandomSeed());
        /**
         * 1. Insert phases that must apply to resolve the initial
         * {@link GraphState#getFutureRequiredStages()}.
         */
        insertPhasesThatMustApply(graphState);
        /**
         * 2. Insert phases to create a tier plan that applies all the {@code mandatoryStages}.
         */
        GraphState graphStateCopy = graphState.copy();
        updateGraphState(getPhaseSuite(), graphStateCopy);
        // Filter the phases to keep only the phases that apply one of the mandatory stages.
        List<BasePhase<? super C>> minimalPhases = new ArrayList<>(getSingleApplyPhases());
        minimalPhases.removeIf(phase -> {
            GraphState graphStateWithThisPhase = graphState.copy();
            updateGraphState(phase, graphStateWithThisPhase);
            return graphStateWithThisPhase.countMissingStages(mandatoryStages) >= graphState.countMissingStages(mandatoryStages);
        });
        Collections.shuffle(minimalPhases, random);
        ListIterator<BasePhase<? super C>> phasesIterator = minimalPhases.listIterator();
        // The the maximum number of attempts is determined with this logic:
        // If we have minimalPhases = [phaseA, phaseB, phaseC]
        // During attempt 0, in the worst case, we can only insert phaseC because the others phases
        // need to be after phaseC.
        // During attempt 1, in the worst case, we can only insert phaseB because phaseA needs to be
        // after phaseB.
        // During attempt 2, we can insert phaseA.
        // An attempt corresponds to processing all the remaining minimal phases.
        int maxAttempts = minimalPhases.size();
        int currAttempt = 0;
        while (!graphStateCopy.isAfterStages(mandatoryStages)) {
            if (!phasesIterator.hasNext()) {
                currAttempt += 1;
                if (currAttempt > maxAttempts) {
                    Formatter errorMsg = new Formatter();
                    errorMsg.format("The given phases cannot fulfill the requirements.%n");
                    errorMsg.format("Current random seed:%s%n", getRandomSeed());
                    errorMsg.format("Current plan:%n");
                    errorMsg.format("%s%n", this);
                    errorMsg.format("%s%n", ignoredPhasesToString());
                    errorMsg.format("Given mandatory stages:%n");
                    errorMsg.format("%s%n", mandatoryStages);
                    errorMsg.format("Stages that can be reached with the current plan:%n");
                    errorMsg.format("%s%n", graphStateCopy.getStageFlags());
                    GraalError.shouldNotReachHere(errorMsg.toString());
                }
                Collections.shuffle(minimalPhases, random);
                phasesIterator = minimalPhases.listIterator();
                continue;
            }
            // Pick a phase and try to insert it in the tier plan.
            BasePhase<? super C> phase = phasesIterator.next();
            for (int i = 0; i <= getPhaseSuite().getPhases().size(); i++) {
                if (insertPhaseAtIndex(phase, i, graphState)) {
                    phasesIterator.remove();
                    GraphState newGraphState = graphState.copy();
                    updateGraphState(newGraphState);
                    graphStateCopy = newGraphState;
                    break;
                }
            }
        }
        /**
         * 3. Insert phases to fulfill the last {@link GraphState#getFutureRequiredStages()}.
         */
        insertPhasesThatMustApply(graphState);
    }

    /**
     * Inserts phases that return {@code true} for {@link BasePhase#mustApply} after running the
     * current suite.
     *
     * The insertion follow this procedure:
     * <ol>
     * <li>Create a queue with all the phases</li>
     * <li>While there is a phase that must apply and we did not try more than a fixed number of
     * attempts
     *
     * <ol>
     * <li>Pick a phase that must apply after applying the current plan.</li>
     * <li>Try to insert it in the plan, starting from then end:
     * <ul>
     * <li>If the phase is not needed anymore, got to 3.</li>
     * <li>If the phase can be inserted, go to 3.</li>
     * </ul>
     * </li>
     * <li>Put the phase at the end of the queue and go to 1. If the phase was not yet inserted, it
     * means another phase needs to be inserted before this phase.</li>
     * </ol>
     *
     * </li>
     * </ol>
     */
    protected void insertPhasesThatMustApply(GraphState graphState) {
        Deque<BasePhase<? super C>> phases = new ArrayDeque<>(getSingleApplyPhases());
        phases.addAll(getMultiApplyPhases());
        Supplier<BasePhase<? super C>> phaseThatMustApplySupplier = () -> phases.stream().filter(p -> mustApplyAfterSuite(p, graphState)).findFirst().orElse(null);
        BasePhase<? super C> phaseThatMustApply = phaseThatMustApplySupplier.get();
        // The the maximum number of attempts is determined with this logic:
        // In the worst case, all phases must apply and
        // phaseThatMustApplyQueue = [phaseA, phaseB, phaseC]
        // Attempt 0: We try to insert phaseA but cannot since it needs to run after phaseB so we
        // put phaseA at the end of the queue.
        // Attempt 1: We try to insert phaseB but cannot since it needs to run after phaseC so we
        // put phaseB at the end of the queue.
        // Attempt 2: We insert phaseC.
        // Attempt 3: Cannot insert phaseA.
        // Attempt 4: Insert phaseB.
        // Attempt 5: Insert phaseA.
        // The maximum number of attempts is defined by n + (n -1) + (n -2) + ... = n * (n+1) / 2
        // where n represents the total number of phases.
        long maxAttempts = phases.size();
        maxAttempts = maxAttempts > Math.sqrt(Long.MAX_VALUE) ? Long.MAX_VALUE : (maxAttempts * (maxAttempts + 1)) / 2;
        int currAttempt = 0;
        while (phaseThatMustApply != null && currAttempt < maxAttempts) {
            // Insert the phase where possible, starting from the end.
            for (int i = getPhaseSuite().getPhases().size(); i >= 0; i--) {
                GraphState currGraphState = graphState.copy();
                updateGraphStateUntilPhaseIndex(currGraphState, i);
                if (!phaseThatMustApply.mustApply(currGraphState)) {
                    // The phase is not needed anymore, try again later.
                    break;
                }
                if (insertPhaseAtIndex(phaseThatMustApply, i, graphState)) {
                    break;
                }
            }
            phases.remove(phaseThatMustApply);
            phases.add(phaseThatMustApply);
            phaseThatMustApply = phaseThatMustApplySupplier.get();
            currAttempt += 1;
        }
    }

    /**
     * Inserts a phase at the given index in the tier plan if it results in a correct plan (see
     * {@link BasePhase#notApplicableTo(GraphState)}).
     *
     * @return {@code true} if the phase was inserted successfully, {@code false} otherwise.
     */
    protected boolean insertPhaseAtIndex(BasePhase<? super C> phase, int index, GraphState graphState) {
        PhaseSuite<C> newFuzzedPhaseSuite = getPhaseSuite().copy();
        newFuzzedPhaseSuite.insertAtIndex(index, phase);
        Optional<NotApplicable> suiteNotApplicable = newFuzzedPhaseSuite.notApplicableTo(graphState.copy());
        if (suiteNotApplicable.isEmpty()) {
            getIgnoredPhases().remove(phase);
            setPhaseSuite(newFuzzedPhaseSuite);
            return true;
        }
        return false;
    }

    /**
     * Retrieves phases that were given to the constructor but were ignored by the fuzzer and are
     * not in the resulting tier plan.
     */
    protected Set<BasePhase<? super C>> getIgnoredPhases() {
        return ignoredPhases;
    }

    /**
     * @return the seed used to initialize {@link Random} instances.
     */
    public long getRandomSeed() {
        return randomSeed;
    }

    /**
     * Creates a string with all the phases ignored by the fuzzer.
     */
    protected String ignoredPhasesToString() {
        if (getIgnoredPhases().isEmpty()) {
            return "Every phase that was given is in the resulting tier plan.";
        }
        Formatter formatter = new Formatter();
        formatter.format("Phase%s in %s ignored by the fuzzer:%n", getIgnoredPhases().size() > 1 ? "s" : "", getTierName().toLowerCase());
        for (BasePhase<? super C> phase : getIgnoredPhases()) {
            if (phase instanceof PhaseSuite) {
                formatter.format("%s", PrintingUtils.indent(phase.toString()));
            } else {
                formatter.format("%s", PrintingUtils.indent(phase.contractorName()));
            }
        }
        return formatter.toString();
    }

    public MinimalFuzzedTierPlan<C> copy() {
        return new MinimalFuzzedTierPlan<>(new ArrayList<>(getSingleApplyPhases()), new ArrayList<>(getMultiApplyPhases()), new HashSet<>(getIgnoredPhases()), getPhaseSuite().copy(),
                        getRandomSeed(), getTierName());
    }

}
