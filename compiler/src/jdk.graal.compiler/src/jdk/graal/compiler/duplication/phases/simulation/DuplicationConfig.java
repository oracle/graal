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

import static jdk.graal.compiler.duplication.phases.simulation.DuplicationDebugUtil.counterBenefitConditionDominatedByEnd;
import static jdk.graal.compiler.duplication.phases.simulation.DuplicationDebugUtil.counterMergeRemovedSplit;
import static jdk.graal.compiler.duplication.util.DuplicationUtil.assertNotNegative;
import static jdk.graal.compiler.duplication.util.DuplicationUtil.conditionDominatesEnd;

import jdk.graal.compiler.nodes.FixedNode;

import jdk.graal.compiler.duplication.phases.simulation.opportunity.PEAOpportunity;
import jdk.graal.compiler.duplication.phases.simulation.opportunity.ReadEliminationOpportunity;

/**
 * Class to specify the impact of different optimization opportunities on the benefit calculation of
 * the duplication phase.
 */
class DuplicationConfig {
    private final int guardKillEnhance;
    private final int splitKillEnhance;
    private final int mergeRemovedEnhanceSink;
    private final int mergeRemovedEnhanceSplit;
    private final boolean considerReadEliminations;
    private final boolean considerPEAOpportunities;
    private final int conditionDominatedByEndEnhace;

    DuplicationConfig(int splitKillEnhance, int guardKillEnhance, int mergeRemovedEnhanceSink, int mergeRemovedEnhanceSplit, boolean considerReadEliminations, boolean considerPEAOpportunities,
                    int conditionDominatedByEndEnhace) {
        this.splitKillEnhance = splitKillEnhance;
        this.guardKillEnhance = guardKillEnhance;
        this.mergeRemovedEnhanceSink = mergeRemovedEnhanceSink;
        this.mergeRemovedEnhanceSplit = mergeRemovedEnhanceSplit;
        this.considerReadEliminations = considerReadEliminations;
        this.considerPEAOpportunities = considerPEAOpportunities;
        this.conditionDominatedByEndEnhace = conditionDominatedByEndEnhace;
    }

    int getGuardKillEnhance() {
        return guardKillEnhance;
    }

    int getSplitKillEnhance() {
        return splitKillEnhance;
    }

    int benefitMergeRemovedSplit(SimulationEndInfo s) {
        if (mergeRemovedEnhanceSplit == 0) {
            return 0;
        }
        if (s.stateKillsMerge()) {
            if (s.splits()) {
                int l = assertNotNegative(mergeRemovedEnhanceSplit);
                counterMergeRemovedSplit.add(s.end.getDebug(), l);
                return l;
            }
        }
        return 0;
    }

    int benefitMergeRemovedSink(SimulationEndInfo s) {
        if (mergeRemovedEnhanceSink == 0) {
            return 0;
        }
        if (!s.splits()) {
            // the jump is only gone if the split is gone
            return mergeRemovedEnhanceSink;
        } else {
            if (s.killsBranches()) {
                return mergeRemovedEnhanceSplit;
            }
        }

        return 0;
    }

    PEAOpportunity benefitEscapingPhis(SimulationEndInfo s, FixedNode regionEnd) {
        if (!considerPEAOpportunities) {
            return PEAOpportunity.DEFAULT_ESCAPING_OPPORTUNITY;
        }
        return PEAOpportunity.getPEAOpportunity(s.getEnd(), s.getOriginalMerge(), regionEnd);
    }

    ReadEliminationOpportunity benefitReadEliminations(SimulationEndInfo s) {
        if (!considerReadEliminations) {
            return ReadEliminationOpportunity.DEFAULT_RE_OPPORTUNITY;
        }
        return ReadEliminationOpportunity.getReadEliminationOpportunity(s.getOriginalMerge(), s.getEnd());
    }

    int benefitConditionDominatedByEnd(SimulationEndInfo s, FixedNode regionEnd) {
        if (conditionDominatedByEndEnhace == 0) {
            return 0;
        }
        int benefit = assertNotNegative(conditionDominatesEnd(regionEnd, s.getEnd(), s.getOriginalMerge()) ? conditionDominatedByEndEnhace : 0);
        counterBenefitConditionDominatedByEnd.add(s.end.getDebug(), benefit);
        return benefit;
    }

    boolean isConsiderReadEliminations() {
        return considerReadEliminations;
    }

}
