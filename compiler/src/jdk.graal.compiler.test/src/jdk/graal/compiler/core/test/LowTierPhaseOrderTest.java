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
package jdk.graal.compiler.core.test;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.phases.LowTier;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.FixReadsPhase;
import jdk.graal.compiler.phases.schedule.PartialRedundancySchedulePhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase;

/// Verifies the community low-tier scheduling policy around partial redundancy scheduling.
public class LowTierPhaseOrderTest extends GraalCompilerTest {

    @Test
    public void partialRedundancySchedulingUsesFixReads() {
        LowTier lowTier = new LowTier(getInitialOptions());
        FixReadsPhase fixReads = findFixReads(lowTier);
        Assert.assertEquals(PartialRedundancySchedulePhase.class, fixReads.getSchedulePhase().getClass());
        Assert.assertEquals(1, countSchedulePhases(lowTier, PartialRedundancySchedulePhase.class));
    }

    @Test
    public void partialRedundancySchedulingCanBeDisabled() {
        OptionValues options = new OptionValues(getInitialOptions(), PartialRedundancySchedulePhase.Options.PartialRedundancyScheduling, false);
        LowTier lowTier = new LowTier(options);
        FixReadsPhase fixReads = findFixReads(lowTier);
        Assert.assertEquals(SchedulePhase.class, fixReads.getSchedulePhase().getClass());
        Assert.assertEquals(0, countSchedulePhases(lowTier, PartialRedundancySchedulePhase.class));
    }

    @Test
    public void partialRedundancySchedulingOverridesStressEarlyReads() {
        OptionValues options = new OptionValues(getInitialOptions(), GraalOptions.StressTestEarlyReads, true);
        LowTier lowTier = new LowTier(options);
        FixReadsPhase fixReads = findFixReads(lowTier);
        Assert.assertEquals(PartialRedundancySchedulePhase.class, fixReads.getSchedulePhase().getClass());
        Assert.assertEquals(1, countSchedulePhases(lowTier, PartialRedundancySchedulePhase.class));
    }

    @Test
    public void disabledPartialRedundancySchedulingPreservesStressEarlyReads() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        PartialRedundancySchedulePhase.Options.PartialRedundancyScheduling, false,
                        GraalOptions.StressTestEarlyReads, true);
        LowTier lowTier = new LowTier(options);
        FixReadsPhase fixReads = findFixReads(lowTier);
        Assert.assertEquals(SchedulePhase.class, fixReads.getSchedulePhase().getClass());
        Assert.assertEquals(1, countSchedulePhases(lowTier, SchedulePhase.class));
    }

    @Test
    public void defaultPhasePlanMatchesPreMoveBaseline() {
        OptionValues baselineOptions = new OptionValues(getInitialOptions(), PartialRedundancySchedulePhase.Options.PartialRedundancyScheduling, false);
        List<String> baseline = phasePlan(new LowTier(baselineOptions));
        List<String> expected = replaceSchedule(baseline, PartialRedundancySchedulePhase.class);
        Assert.assertEquals(expected, phasePlan(new LowTier(getInitialOptions())));
    }

    private static FixReadsPhase findFixReads(LowTier lowTier) {
        for (BasePhase<?> phase : lowTier.getPhases()) {
            if (phase instanceof FixReadsPhase fixReads) {
                return fixReads;
            }
        }
        throw new AssertionError("Missing " + FixReadsPhase.class.getSimpleName());
    }

    private static int countSchedulePhases(LowTier lowTier, Class<?> scheduleClass) {
        int count = 0;
        for (BasePhase<?> phase : lowTier.getPhases()) {
            if (phase instanceof FixReadsPhase fixReads && scheduleClass.isInstance(fixReads.getSchedulePhase())) {
                count++;
            }
        }
        return count;
    }

    private static List<String> phasePlan(LowTier lowTier) {
        List<String> plan = new ArrayList<>();
        for (BasePhase<?> phase : lowTier.getPhases()) {
            if (phase instanceof FixReadsPhase fixReads) {
                plan.add(phase.getClass().getName() + "[" + fixReads.getSchedulePhase().getClass().getName() + "]");
            } else {
                plan.add(phase.getClass().getName());
            }
        }
        return plan;
    }

    private static List<String> replaceSchedule(List<String> plan, Class<?> scheduleClass) {
        List<String> replaced = new ArrayList<>(plan);
        String fixReadsPrefix = FixReadsPhase.class.getName() + "[";
        for (int i = 0; i < replaced.size(); i++) {
            if (replaced.get(i).startsWith(fixReadsPrefix)) {
                replaced.set(i, fixReadsPrefix + scheduleClass.getName() + "]");
            }
        }
        return replaced;
    }
}
