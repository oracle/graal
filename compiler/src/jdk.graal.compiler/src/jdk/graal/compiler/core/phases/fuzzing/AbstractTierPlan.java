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

import java.util.ArrayList;
import java.util.List;

import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.PhaseSuite;

/**
 * Plan that represents a specific ordering of phases for a tier.
 *
 * See {@code compiler/docs/CompilationPlanFuzzing.md} for more details.
 */
abstract class AbstractTierPlan<C> {
    /**
     * Contains phases that were given to construct the tier plan and that can be applied at most
     * once since they update the {@link GraphState} and make it progress through the compilation.
     */
    private final List<BasePhase<? super C>> singleApplyPhases;

    /**
     * Contains phases that were given to construct the tier plan and that can be applied multiple
     * times.
     */
    private final List<BasePhase<? super C>> multiApplyPhases;

    /**
     * Represents the phase ordering for this tier plan. This is the {@link PhaseSuite} that will be
     * run to perform the compilation.
     */
    private PhaseSuite<C> phaseSuite;

    /**
     * Represents the name of the tier (e.g. "high tier", "mid tier" or "low tier") and is used for
     * printing.
     */
    private final String tierName;

    protected AbstractTierPlan(List<BasePhase<? super C>> originalPhases, String tierName) {
        this(new ArrayList<>(), new ArrayList<>(), new PhaseSuite<>(), tierName);
        splitPhasesByNumberOfApplications(originalPhases);
    }

    /**
     * This should only be used as a copy or superclass constructor.
     */
    protected AbstractTierPlan(List<BasePhase<? super C>> singleApplyPhases,
                    List<BasePhase<? super C>> multiApplyPhases,
                    PhaseSuite<C> phaseSuite,
                    String tierName) {
        this.singleApplyPhases = singleApplyPhases;
        this.multiApplyPhases = multiApplyPhases;
        this.phaseSuite = phaseSuite;
        this.tierName = tierName;
    }

    /**
     * Inserts phases of {@code originalPhases} into {@link #singleApplyPhases} if they update the
     * {@link GraphState}. Otherwise, inserts them into {@link #multiApplyPhases}.
     */
    private void splitPhasesByNumberOfApplications(List<BasePhase<? super C>> originalPhases) {
        GraphState baseline = GraphState.defaultGraphState();
        for (BasePhase<? super C> phase : originalPhases) {
            GraphState actual = GraphState.defaultGraphState();
            updateGraphState(phase, actual);
            if (actual.getStageFlags().equals(baseline.getStageFlags())) {
                multiApplyPhases.add(phase);
            } else {
                singleApplyPhases.add(phase);
            }
        }
    }

    /**
     * Retrieves phases that update the {@link GraphState} and can be applied at most once.
     */
    protected List<BasePhase<? super C>> getSingleApplyPhases() {
        return singleApplyPhases;
    }

    /**
     * Retrieves phases that do not update the {@link GraphState} and can be applied multiple times.
     */
    protected List<BasePhase<? super C>> getMultiApplyPhases() {
        return multiApplyPhases;
    }

    /**
     * @return the {@link PhaseSuite} containing the current phase ordering.
     */
    public PhaseSuite<C> getPhaseSuite() {
        return phaseSuite;
    }

    /**
     * Sets a new phase ordering.
     */
    protected void setPhaseSuite(PhaseSuite<C> suite) {
        this.phaseSuite = suite;
    }

    /**
     * @return a string indicating the name of this tier.
     */
    protected String getTierName() {
        return tierName;
    }

    @Override
    public String toString() {
        return String.format("%s %s", getTierName(), AbstractCompilationPlan.PrintingUtils.indent(getPhaseSuite().toString()).stripLeading());
    }

    /**
     * Checks if a phase {@link BasePhase#mustApply(GraphState)} after applying this tier plan to
     * the given {@link GraphState}.
     */
    protected boolean mustApplyAfterSuite(BasePhase<? super C> phase, GraphState graphState) {
        GraphState graphStateCopy = graphState.copy();
        updateGraphState(getPhaseSuite(), graphStateCopy);
        return phase.mustApply(graphStateCopy);
    }

    /**
     * Updates the {@link GraphState} with this tier plan.
     */
    protected void updateGraphState(GraphState graphState) {
        updateGraphState(getPhaseSuite(), graphState);
    }

    /**
     * Updates the {@link GraphState} with the phases before {@code index} in this tier plan.
     */
    @SuppressWarnings("unchecked")
    protected void updateGraphStateUntilPhaseIndex(GraphState graphState, int index) {
        int currIndex = 0;
        for (BasePhase<? super C> phase : getPhaseSuite().getPhases()) {
            if (currIndex == index) {
                break;
            }
            updateGraphState(phase, graphState);
            currIndex += 1;
        }
    }

    /**
     * Updates the given {@link GraphState} with the given {@link BasePhase}. If the phase is a
     * {@link PhaseSuite}, the graph state will be updated with all the phases of the phase suite.
     */
    @SuppressWarnings("unchecked")
    protected static <C> void updateGraphState(BasePhase<? super C> phase, GraphState graphState) {
        if (phase instanceof PhaseSuite) {
            for (BasePhase<? super C> innerPhase : ((PhaseSuite<C>) phase).getPhases()) {
                updateGraphState(innerPhase, graphState);
            }
        }
        phase.updateGraphState(graphState);
    }

}
