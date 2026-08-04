/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.duplication.phases.simulation;

import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.TimerKey;

class DuplicationDebugUtil {

    public static final CounterKey counterBadDuplicationsIgnored = DebugContext.counter("SimulationBasedDuplication_BadDuplicationsIgnored");
    public static final CounterKey counterBudgetExceededIgnored = DebugContext.counter("SimulationBasedDuplication_BudgetExceededIgnored");
    public static final CounterKey counterDuplicationBudgetExceeded = DebugContext.counter("SimulationBasedDuplication_BudgetExceeded");
    public static final CounterKey counterSimulationBasedDuplicationSplitRegions = DebugContext.counter("SimulationBasedDuplication_SplitRegions");
    public static final CounterKey counterSimulationBasedDuplicationRegionSinking = DebugContext.counter("SimulationBasedDuplication_Sinkings");
    public static final CounterKey counterDuplicatedNodes = DebugContext.counter("SimulationBasedDuplication_DuplicatedNodes");
    public static final CounterKey counterCyclesSaved = DebugContext.counter("SimulationBasedDuplication_Cycles_Cycles_Saved");
    public static final CounterKey counterCodeSizeDuplicated = DebugContext.counter("SimulationBasedDuplication_Size_CodeSizeDuplicated");
    public static final CounterKey counterBenefitConditionDominatedByEnd = DebugContext.counter("SimulationBasedDuplication_Benefit_ConditionDominatedByEnd");
    public static final CounterKey counterMergeRemovedSplit = DebugContext.counter("SimulationBasedDuplication_Benefit_MergeRemovedSplit");
    public static final CounterKey counterReducedRegion = DebugContext.counter("SimulationBasedDuplication_ReducedRegionCandidates");
    public static final CounterKey counterReducedRegionShrinked = DebugContext.counter("SimulationBasedDuplication_ReducedRegion_Shrinkings");
    public static final CounterKey counterPhiEstimationCutOff = DebugContext.counter("SimulationBasedDuplication_PhiRatioEstimationCutOff");
    public static final CounterKey counterNotDuplicatedVectorizable = DebugContext.counter("SimulationBasedDuplication_NotDuplicated_Vectorizable");

    public static final CounterKey nodeCountBudgetReached = DebugContext.counter("SimulationBasedDuplication_MaxGraphSizeReached");

    public static final TimerKey timerBenefitComputation = DebugContext.timer("SimulationBasedDuplication_Time_BenefitComputation");
    public static final TimerKey timerRegionComputation = DebugContext.timer("SimulationBasedDuplication_Time_RegionIteration");
    public static final TimerKey timerDuplication = DebugContext.timer("SimulationBasedDuplication_Time_Duplication");
    public static final TimerKey timerPhaseIteration = DebugContext.timer("SimulationBasedDuplication_Time_PhaseIteration");

    public static final TimerKey simulateTime = DebugContext.timer("DuplicationPhase_SimulationTime");
    public static final TimerKey duplicateTime = DebugContext.timer("DuplicationPhase_DuplicationTime");
    public static final TimerKey cleanUpRETime = DebugContext.timer("DuplicationPhase_CleanupTime_RE");
    public static final TimerKey cleanUpCETime = DebugContext.timer("DuplicationPhase_CleanupTime_CE");

    public static final TimerKey phiUsagesCostTimer = DebugContext.timer("Duplication_PhiUsageDetectionTime");

}
