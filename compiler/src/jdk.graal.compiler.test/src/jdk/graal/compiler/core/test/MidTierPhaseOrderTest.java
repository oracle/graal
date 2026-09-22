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
import jdk.graal.compiler.core.phases.MidTier;
import jdk.graal.compiler.loop.phases.CountedStripMiningPhase;
import jdk.graal.compiler.loop.phases.CountedStripMiningReassociationPhase;
import jdk.graal.compiler.loop.phases.InjectLoopCounterStampsPhase;
import jdk.graal.compiler.loop.phases.LoopInversionPhase;
import jdk.graal.compiler.loop.phases.LoopPeelingPhase;
import jdk.graal.compiler.loop.phases.LoopRotationPhase;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.FloatingReadPhase;
import jdk.graal.compiler.phases.common.MidTierLoweringPhase;
import jdk.graal.compiler.phases.common.OptimizeExactArithmeticPhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.guards.optimistic.memory.OptimisticAliasingAnalysisPhase;
import jdk.graal.compiler.guards.optimistic.memory.OptimisticGuardsPhase;
import jdk.graal.compiler.vector.phases.OptimizeAddressesInLoopsPhase;
import jdk.graal.compiler.vector.phases.RemoveEmptyLoopsPhase;
import jdk.graal.compiler.vector.replacements.VectorIntrinsics;

/// Tests ordering constraints among phases in the community mid tier.
public class MidTierPhaseOrderTest extends GraalCompilerTest {

    /// Verifies that early rotation precedes all counted strip-mining preparation when non-counted
    /// strip mining is disabled.
    @Test
    public void earlyRotationPrecedesCountedStripMiningPreparation() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        MidTier.Options.StripMineNonCountedLoops, false,
                        MidTier.Options.StripMineCountedLoops, true,
                        MidTier.Options.StripMiningPreparationPhases, true,
                        MidTier.Options.OptExactArithmetic, true,
                        LoopRotationPhase.Options.LoopRotation, true,
                        LoopRotationPhase.Options.EarlyMidTierLoopRotation, true,
                        VectorIntrinsics.Options.Vectorization, true,
                        GraalOptions.LoopPeeling, true);
        List<BasePhase<? super MidTierContext>> phases = new MidTier(options).getPhases();

        int rotation = indexOf(phases, LoopRotationPhase.class, 0);
        int removeEmptyLoops = indexOf(phases, RemoveEmptyLoopsPhase.class, rotation + 1);
        int exactArithmetic = indexOf(phases, OptimizeExactArithmeticPhase.class, removeEmptyLoops + 1);
        int peeling = indexOf(phases, LoopPeelingPhase.class, exactArithmetic + 1);
        int stripMining = indexOf(phases, CountedStripMiningPhase.class, peeling + 1);

        Assert.assertTrue("early rotation must precede counted strip-mining preparation", rotation < removeEmptyLoops);
        Assert.assertTrue("empty-loop removal must precede exact arithmetic", removeEmptyLoops < exactArithmetic);
        Assert.assertTrue("exact arithmetic must precede loop peeling", exactArithmetic < peeling);
        Assert.assertTrue("loop peeling must precede counted strip mining", peeling < stripMining);
    }

    /// Verifies that disabling optional strip-mining preparation does not disable loop peeling.
    @Test
    public void peelingPrecedesCountedStripMiningWithoutPreparation() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        MidTier.Options.StripMineNonCountedLoops, false,
                        MidTier.Options.StripMineCountedLoops, true,
                        MidTier.Options.StripMiningPreparationPhases, false,
                        LoopRotationPhase.Options.LoopRotation, true,
                        LoopRotationPhase.Options.EarlyMidTierLoopRotation, true,
                        GraalOptions.LoopPeeling, true);
        List<BasePhase<? super MidTierContext>> phases = new MidTier(options).getPhases();

        int rotation = indexOf(phases, LoopRotationPhase.class, 0);
        int peeling = indexOf(phases, LoopPeelingPhase.class, rotation + 1);
        int stripMining = indexOf(phases, CountedStripMiningPhase.class, peeling + 1);

        Assert.assertTrue("early rotation must precede loop peeling", rotation < peeling);
        Assert.assertTrue("loop peeling must precede counted strip mining", peeling < stripMining);
    }

    /// Verifies standalone counter-stamp injection after floating reads and mid-tier lowering.
    @Test
    public void counterStampInjectionFollowsGraphChanges() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        GraalOptions.OptFloatingReads, true,
                        InjectLoopCounterStampsPhase.Options.OptLoopPhiStamps, true);
        List<BasePhase<? super MidTierContext>> phases = new MidTier(options).getPhases();

        int floatingReads = indexOf(phases, FloatingReadPhase.class, 0);
        int firstStampInjection = indexOf(phases, InjectLoopCounterStampsPhase.class, floatingReads + 1);
        int lowering = indexOf(phases, MidTierLoweringPhase.class, firstStampInjection + 1);
        int secondStampInjection = indexOf(phases, InjectLoopCounterStampsPhase.class, lowering + 1);

        Assert.assertEquals("counter stamps must be injected immediately after floating reads", floatingReads + 1, firstStampInjection);
        Assert.assertEquals("counter stamps must be injected immediately after lowering", lowering + 1, secondStampInjection);
    }

    /// Verifies that alias information is available to inversion and cleaned before reanalysis.
    @Test
    public void aliasAnalysisPrecedesInversion() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        MidTier.Options.OptimisticAliasingAnalysis, true,
                        LoopInversionPhase.Options.LoopInversion, true,
                        LoopInversionPhase.Options.MidTierInversion, true,
                        VectorIntrinsics.Options.Vectorization, true);
        List<BasePhase<? super MidTierContext>> phases = new MidTier(options).getPhases();

        int firstAddressOptimization = indexOf(phases, OptimizeAddressesInLoopsPhase.class, 0);
        int firstAliasAnalysis = indexOf(phases, OptimisticAliasingAnalysisPhase.class, firstAddressOptimization + 1);
        int inversion = indexOf(phases, LoopInversionPhase.class, firstAliasAnalysis + 1);
        int secondAddressOptimization = indexOf(phases, OptimizeAddressesInLoopsPhase.class, inversion + 1);
        int guardCleanup = indexOf(phases, OptimisticGuardsPhase.class, secondAddressOptimization + 1);
        int secondAliasAnalysis = indexOf(phases, OptimisticAliasingAnalysisPhase.class, guardCleanup + 1);

        Assert.assertTrue("alias analysis must precede inversion", firstAliasAnalysis < inversion);
        Assert.assertTrue("optimistic guards must be cleaned before alias reanalysis", guardCleanup < secondAliasAnalysis);
    }

    /// Verifies that pre-inversion alias guards are cleaned when vectorization is disabled.
    @Test
    public void aliasGuardsAreCleanedWithoutVectorization() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        MidTier.Options.OptimisticAliasingAnalysis, true,
                        LoopInversionPhase.Options.LoopInversion, true,
                        LoopInversionPhase.Options.MidTierInversion, true,
                        VectorIntrinsics.Options.Vectorization, false);
        List<BasePhase<? super MidTierContext>> phases = new MidTier(options).getPhases();

        int aliasAnalysis = indexOf(phases, OptimisticAliasingAnalysisPhase.class, 0);
        int inversion = indexOf(phases, LoopInversionPhase.class, aliasAnalysis + 1);
        int guardCleanup = indexOf(phases, OptimisticGuardsPhase.class, inversion + 1);

        Assert.assertTrue("optimistic guards must be cleaned after inversion", inversion < guardCleanup);
    }

    /// Verifies that strip-mining reassociation is tied to partial unrolling.
    @Test
    public void reassociationRequiresPartialUnrolling() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        GraalOptions.PartialUnroll, false,
                        MidTier.Options.StripMineCountedLoops, true,
                        CountedStripMiningReassociationPhase.Options.StripMiningReassociation, true);
        List<BasePhase<? super MidTierContext>> phases = new MidTier(options).getPhases();
        Assert.assertEquals("strip-mining reassociation must be omitted without partial unrolling", -1,
                        findIndex(phases, CountedStripMiningReassociationPhase.class, 0));
    }

    /// Finds the first phase of type `phaseClass` at or after `startIndex`.
    private static int indexOf(List<BasePhase<? super MidTierContext>> phases, Class<?> phaseClass, int startIndex) {
        int index = findIndex(phases, phaseClass, startIndex);
        Assert.assertNotEquals("Missing phase " + phaseClass.getName(), -1, index);
        return index;
    }

    /// Finds the first phase of type `phaseClass`, or returns `-1` when none exists.
    private static int findIndex(List<BasePhase<? super MidTierContext>> phases, Class<?> phaseClass, int startIndex) {
        for (int i = startIndex; i < phases.size(); i++) {
            if (phaseClass.isInstance(phases.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
