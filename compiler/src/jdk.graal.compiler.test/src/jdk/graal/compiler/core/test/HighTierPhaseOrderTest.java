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

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.phases.HighTier;
import jdk.graal.compiler.loop.phases.InjectLoopCounterStampsPhase;
import jdk.graal.compiler.loop.phases.LoopFullUnrollPhase;
import jdk.graal.compiler.loop.phases.LoopInversionPhase;
import jdk.graal.compiler.loop.phases.LoopPeelingPhase;
import jdk.graal.compiler.loop.phases.LoopRotationPhase;
import jdk.graal.compiler.loop.phases.LoopUnswitchingPhase;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationPhase;

/// Tests ordering constraints among phases in the community high tier.
public class HighTierPhaseOrderTest extends GraalCompilerTest {

    /// Verifies the rotation and counter-stamp order around full unrolling and peeling.
    @Test
    public void loopPhasesWithFullUnrolling() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        GraalOptions.FullUnroll, true,
                        GraalOptions.LoopPeeling, true,
                        GraalOptions.LoopUnswitch, true,
                        LoopRotationPhase.Options.LoopRotation, true,
                        LoopRotationPhase.Options.HighTierLoopRotation, true,
                        InjectLoopCounterStampsPhase.Options.OptLoopPhiStamps, true);
        List<BasePhase<? super HighTierContext>> phases = new HighTier(options).getPhases();

        int rotation = indexOf(phases, LoopRotationPhase.class, 0);
        int fullUnroll = indexOf(phases, LoopFullUnrollPhase.class, rotation + 1);
        int firstStampInjection = indexOf(phases, InjectLoopCounterStampsPhase.class, fullUnroll + 1);
        int peeling = indexOf(phases, LoopPeelingPhase.class, firstStampInjection + 1);
        int secondStampInjection = indexOf(phases, InjectLoopCounterStampsPhase.class, peeling + 1);

        Assert.assertTrue("rotation must precede full unrolling", rotation < fullUnroll);
        Assert.assertTrue("counter stamps must be injected after full unrolling", fullUnroll < firstStampInjection);
        Assert.assertTrue("counter stamps must be reinjected after peeling", peeling < secondStampInjection);
    }

    /// Verifies that rotation follows read elimination when full unrolling is disabled.
    @Test
    public void rotationBeforePeelingWithoutFullUnrolling() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        GraalOptions.FullUnroll, false,
                        GraalOptions.OptReadElimination, true,
                        GraalOptions.LoopPeeling, true,
                        LoopRotationPhase.Options.LoopRotation, true,
                        LoopRotationPhase.Options.HighTierLoopRotation, true);
        List<BasePhase<? super HighTierContext>> phases = new HighTier(options).getPhases();

        int readElimination = indexOf(phases, ReadEliminationPhase.class, 0);
        int rotation = indexOf(phases, LoopRotationPhase.class, readElimination + 1);
        int peeling = indexOf(phases, LoopPeelingPhase.class, rotation + 1);

        Assert.assertTrue("read elimination must precede rotation", readElimination < rotation);
        Assert.assertTrue("rotation must precede peeling", rotation < peeling);
    }

    /// Verifies that high-tier inversion follows unswitching when enabled.
    @Test
    public void inversionFollowsUnswitching() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        GraalOptions.LoopUnswitch, true,
                        LoopInversionPhase.Options.LoopInversion, true,
                        LoopInversionPhase.Options.HighTierInversion, true);
        List<BasePhase<? super HighTierContext>> phases = new HighTier(options).getPhases();

        int unswitching = indexOf(phases, LoopUnswitchingPhase.class, 0);
        int inversion = indexOf(phases, LoopInversionPhase.class, unswitching + 1);
        Assert.assertTrue("inversion must follow unswitching", unswitching < inversion);
    }

    /// Verifies that high-tier rotation needs a full-unrolling or peeling anchor.
    @Test
    public void rotationRequiresLoopOptimizationAnchor() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        GraalOptions.FullUnroll, false,
                        GraalOptions.LoopPeeling, false,
                        LoopRotationPhase.Options.LoopRotation, true,
                        LoopRotationPhase.Options.HighTierLoopRotation, true);
        List<BasePhase<? super HighTierContext>> phases = new HighTier(options).getPhases();
        Assert.assertEquals("rotation must be omitted without a loop optimization anchor", -1, findIndex(phases, LoopRotationPhase.class, 0));
    }

    /// Finds the first phase of type `phaseClass` at or after `startIndex`.
    private static int indexOf(List<BasePhase<? super HighTierContext>> phases, Class<?> phaseClass, int startIndex) {
        int index = findIndex(phases, phaseClass, startIndex);
        Assert.assertNotEquals("Missing phase " + phaseClass.getName(), -1, index);
        return index;
    }

    /// Finds the first phase of type `phaseClass`, or returns `-1` when none exists.
    private static int findIndex(List<BasePhase<? super HighTierContext>> phases, Class<?> phaseClass, int startIndex) {
        for (int i = startIndex; i < phases.size(); i++) {
            if (phaseClass.isInstance(phases.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
