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

import java.util.Optional;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.GraphState.MandatoryStages;
import org.graalvm.compiler.nodes.GraphState.StageFlag;
import org.graalvm.compiler.phases.BasePhase.NotApplicable;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.LowTierContext;
import org.graalvm.compiler.phases.tiers.MidTierContext;
import org.graalvm.compiler.phases.tiers.Suites;

/**
 * Compilation plan that represents a specific ordering of phases. These phases are grouped into
 * three {@link AbstractTierPlan}s, one for each tier (low, mid and high).
 *
 * See {@code compiler/docs/CompilationPlanFuzzing.md} for more details.
 */
public abstract class AbstractCompilationPlan {
    /**
     * Represents the phase ordering for high tier.
     */
    private final AbstractTierPlan<HighTierContext> highTier;

    /**
     * Represents the phase ordering for mid tier.
     */
    private final AbstractTierPlan<MidTierContext> midTier;

    /**
     * Represents the phase ordering for low tier.
     */
    private final AbstractTierPlan<LowTierContext> lowTier;

    /**
     * Represents stages this compilation plan needs to reach.
     */
    private final GraphState.MandatoryStages mandatoryStages;

    protected AbstractCompilationPlan(AbstractTierPlan<HighTierContext> highTier,
                    AbstractTierPlan<MidTierContext> midTier,
                    AbstractTierPlan<LowTierContext> lowTier,
                    @SuppressWarnings("unused") GraphState graphState,
                    GraphState.MandatoryStages mandatoryStages) {
        this(highTier, midTier, lowTier, mandatoryStages);
        if (!isSchedulePhaseLast()) {
            // This makes sure every node can be scheduled correctly and helps in catching errors
            // early on during the compilation.
            this.lowTier.getPhaseSuite().appendPhase(new SchedulePhase(SchedulePhase.SchedulingStrategy.LATEST_OUT_OF_LOOPS));
        }
    }

    /**
     * This is a copy constructor and should only be used for this purpose.
     */
    protected AbstractCompilationPlan(AbstractTierPlan<HighTierContext> highTier,
                    AbstractTierPlan<MidTierContext> midTier,
                    AbstractTierPlan<LowTierContext> lowTier,
                    GraphState.MandatoryStages mandatoryStages) {
        this.highTier = highTier;
        this.midTier = midTier;
        this.lowTier = lowTier;
        this.mandatoryStages = mandatoryStages;
    }

    /**
     * Verifies this compilation plan with respect to phase invariants.
     */
    public void verifyCompilationPlan(GraphState graphState) {
        GraphState simulationGraphState = graphState.copy();
        addInitialRequiredStages(simulationGraphState);
        Optional<NotApplicable> tierNotApplicable = highTier.getPhaseSuite().notApplicableTo(simulationGraphState);
        GraalError.guarantee(tierNotApplicable.isEmpty(), "Cannot apply the high tier of this compilation plan because %s.%n%s", tierNotApplicable.orElse(null), this);
        highTier.updateGraphState(simulationGraphState);
        tierNotApplicable = midTier.getPhaseSuite().notApplicableTo(simulationGraphState);
        GraalError.guarantee(tierNotApplicable.isEmpty(), "Cannot apply the mid tier of this compilation plan because %s.%n%s", tierNotApplicable.orElse(null), this);
        midTier.updateGraphState(simulationGraphState);
        tierNotApplicable = lowTier.getPhaseSuite().notApplicableTo(simulationGraphState);
        GraalError.guarantee(tierNotApplicable.isEmpty(), "Cannot apply the low tier of this compilation plan because %s.%n%s", tierNotApplicable.orElse(null), this);
        lowTier.updateGraphState(simulationGraphState);
        GraalError.guarantee(simulationGraphState.hasAllMandatoryStages(getMandatoryStages()), "This compilation plan does not apply all mandatory stages.%n%s", this);
        GraalError.guarantee(!simulationGraphState.requiresFutureStages(), "This compilation plan:%n%s%nhas remaining requirements: %s", this, simulationGraphState.getFutureRequiredStages());
        GraalError.guarantee(isSchedulePhaseLast(), "Low tier should end with a %s.%n%s", SchedulePhase.class.getName(), this);
    }

    /**
     * Adds all the {@link GraphState#INITIAL_REQUIRED_STAGES} to the
     * {@link GraphState#getFutureRequiredStages()} of the given {@code graphState}.
     */
    protected static void addInitialRequiredStages(GraphState graphState) {
        for (StageFlag flag : GraphState.INITIAL_REQUIRED_STAGES) {
            graphState.addFutureStageRequirement(flag);
        }
    }

    /**
     * Checks {@link AbstractCompilationPlan#lowTier} ends with a {@link SchedulePhase}.
     */
    private boolean isSchedulePhaseLast() {
        return lowTier.getPhaseSuite().findLastPhase().previous() instanceof SchedulePhase;
    }

    /**
     * @return the {@link AbstractTierPlan} representing the phase ordering for high tier.
     */
    public AbstractTierPlan<HighTierContext> getHighTier() {
        return highTier;
    }

    /**
     * @return the {@link AbstractTierPlan} representing the phase ordering for mid tier.
     */
    public AbstractTierPlan<MidTierContext> getMidTier() {
        return midTier;
    }

    /**
     * @return the {@link AbstractTierPlan} representing the phase ordering for low tier.
     */
    public AbstractTierPlan<LowTierContext> getLowTier() {
        return lowTier;
    }

    /**
     * @return the {@link Suites} constructed from the {@link AbstractTierPlan#getPhaseSuite}s of
     *         high, mid and low tier.
     */
    public Suites getSuites() {
        return new Suites(highTier.getPhaseSuite(), midTier.getPhaseSuite(), lowTier.getPhaseSuite());
    }

    /**
     * @return the {@link MandatoryStages} representing all the stages that need to be applied to
     *         the graph for a complete and correct compilation.
     */
    public GraphState.MandatoryStages getMandatoryStages() {
        return mandatoryStages;
    }

    /**
     * Saves this compilation plan to the file designated by {@code dumpPath}.
     */
    public void saveCompilationPlan(String dumpPath) {
        PhasePlanSerializer.savePhasePlan(dumpPath + ".phaseplan", this.getSuites());
    }

    @Override
    public String toString() {
        return String.format("%s%n%s%n%s", highTier.toString(), midTier.toString(), lowTier.toString());
    }

    /**
     * Helper class providing methods to format compilation plans.
     */
    static class PrintingUtils {
        static final String INDENT = "\t";

        /**
         * Indents all the lines of the given {@link String} with {@link #INDENT}.
         */
        static String indent(String string) {
            return String.format("%s%s%n", INDENT, String.join(System.lineSeparator() + INDENT, string.split(System.lineSeparator())));
        }

        /**
         * @return "failing" if the given {@link String} that represents a compilation plan contains
         *         "FAILURE", returns an empty {@link String} otherwise.
         */
        static String printFailing(String phasePlan) {
            if (phasePlan.contains("FAILURE")) {
                return "failing ";
            }
            return "";
        }
    }
}
