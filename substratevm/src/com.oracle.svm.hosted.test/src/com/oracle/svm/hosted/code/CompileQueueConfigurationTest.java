/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.core.SubstrateOptions;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.phases.EconomyHighTier;
import jdk.graal.compiler.core.phases.EconomyLowTier;
import jdk.graal.compiler.core.phases.EconomyMarkFixReadsPhase;
import jdk.graal.compiler.core.phases.EconomyMidTier;
import jdk.graal.compiler.core.phases.MidTier;
import jdk.graal.compiler.loop.phases.LoopPartialUnrollPhase;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.FixReadsPhase;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.vector.phases.ConditionalMoveOptimizationPhase;
import jdk.graal.compiler.vector.phases.LoopVectorizationPhase;
import jdk.graal.compiler.vector.phases.VectorLoweringPhaseSuite;
import jdk.graal.compiler.vector.replacements.VectorIntrinsics;

/// Verifies CE compile-queue suite and code-size configuration policies.
public class CompileQueueConfigurationTest {

    @Test
    public void regularSuitesHonorOptimizationLevelAndExplicitOverrides() {
        OptionValues defaults = new OptionValues(OptionValues.newOptionMap());
        Suites original = createRegularTestSuites(defaults);
        Suites tuned = CompileQueue.applyRegularSuiteTuning(original, defaults, false);

        Assert.assertNotSame(original, tuned);
        Assert.assertNull(tuned.getMidTier().findPhase(LoopPartialUnrollPhase.class));
        Assert.assertNull(tuned.getMidTier().findPhase(LoopVectorizationPhase.class));
        Assert.assertNotNull(original.getMidTier().findPhase(LoopPartialUnrollPhase.class));
        Assert.assertNotNull(original.getMidTier().findPhase(LoopVectorizationPhase.class));

        EconomicMap<OptionKey<?>, Object> optionsMap = OptionValues.newOptionMap();
        optionsMap.put(GraalOptions.PartialUnroll, true);
        optionsMap.put(LoopVectorizationPhase.Options.VectorizeLoops, true);
        OptionValues explicit = new OptionValues(optionsMap);
        Suites explicitSuites = CompileQueue.applyRegularSuiteTuning(createRegularTestSuites(explicit), explicit, false);
        Assert.assertNotNull(explicitSuites.getMidTier().findPhase(LoopPartialUnrollPhase.class));
        Assert.assertNotNull(explicitSuites.getMidTier().findPhase(LoopVectorizationPhase.class));

        Suites maximumSuites = createRegularTestSuites(defaults);
        Assert.assertSame(maximumSuites, CompileQueue.applyRegularSuiteTuning(maximumSuites, defaults, true));
    }

    @Test
    public void sizePolicyConfiguresCommunityOptions() {
        EconomicMap<OptionKey<?>, Object> map = OptionValues.newOptionMap();
        SubstrateOptions.configureOptimizeForCodeSize(map, true, true, true);
        OptionValues options = new OptionValues(map);

        Assert.assertFalse(VectorIntrinsics.Options.Vectorization.getValue(options));
        Assert.assertFalse(LoopVectorizationPhase.Options.VectorizeLoops.getValue(options));
        Assert.assertFalse(MidTier.Options.OptimisticAliasingAnalysis.getValue(options));
        Assert.assertTrue(ConditionalMoveOptimizationPhase.Options.CMoveALot.getValue(options));

        map = OptionValues.newOptionMap();
        SubstrateOptions.configureOptimizeForCodeSize(map, false, true, true);
        options = new OptionValues(map);
        Assert.assertTrue(VectorIntrinsics.Options.Vectorization.getValue(options));
        Assert.assertFalse(LoopVectorizationPhase.Options.VectorizeLoops.getValue(options));
        Assert.assertFalse(MidTier.Options.OptimisticAliasingAnalysis.getValue(options));
        Assert.assertTrue(ConditionalMoveOptimizationPhase.Options.CMoveALot.getValue(options));

        map = OptionValues.newOptionMap();
        SubstrateOptions.configureOptimizeForCodeSize(map, false, false, false);
        options = new OptionValues(map);
        Assert.assertTrue(VectorIntrinsics.Options.Vectorization.getValue(options));
        Assert.assertTrue(LoopVectorizationPhase.Options.VectorizeLoops.getValue(options));
        Assert.assertTrue(MidTier.Options.OptimisticAliasingAnalysis.getValue(options));
        Assert.assertTrue(ConditionalMoveOptimizationPhase.Options.CMoveALot.getValue(options));
    }

    @Test
    public void fallbackSuitesAddVectorLoweringAndFixReads() {
        OptionValues defaults = new OptionValues(OptionValues.newOptionMap());
        Suites original = createFallbackTestSuites(defaults);
        Suites tuned = CompileQueue.applyFallbackSuiteTuning(original, defaults);

        Assert.assertNotSame(original, tuned);
        Assert.assertNull(tuned.getHighTier().findPhase(EconomyMarkFixReadsPhase.class));
        Assert.assertNotNull(tuned.getLowTier().findPhase(VectorLoweringPhaseSuite.class));
        Assert.assertNotNull(tuned.getLowTier().findPhase(FixReadsPhase.class));
        Assert.assertTrue(phaseIndex(tuned.getLowTier(), VectorLoweringPhaseSuite.class) < phaseIndex(tuned.getLowTier(), FixReadsPhase.class));
        Assert.assertNotNull(original.getHighTier().findPhase(EconomyMarkFixReadsPhase.class));
    }

    @Test
    public void fallbackSuitesStayUnchangedWhenVectorizationIsDisabled() {
        EconomicMap<OptionKey<?>, Object> disabledMap = OptionValues.newOptionMap();
        disabledMap.put(VectorIntrinsics.Options.Vectorization, false);
        OptionValues disabled = new OptionValues(disabledMap);
        Suites disabledSuites = createFallbackTestSuites(disabled);
        Assert.assertSame(disabledSuites, CompileQueue.applyFallbackSuiteTuning(disabledSuites, disabled));
        Assert.assertNotNull(disabledSuites.getHighTier().findPhase(EconomyMarkFixReadsPhase.class));
        Assert.assertNull(disabledSuites.getLowTier().findPhase(VectorLoweringPhaseSuite.class));
    }

    private static Suites createRegularTestSuites(OptionValues options) {
        return new Suites(new PhaseSuite<>(), new MidTier(options), new PhaseSuite<>());
    }

    private static Suites createFallbackTestSuites(OptionValues options) {
        return new Suites(new EconomyHighTier(), new EconomyMidTier(), new EconomyLowTier(options));
    }

    private static int phaseIndex(PhaseSuite<?> suite, Class<?> phaseClass) {
        List<?> phases = suite.getPhases();
        for (int i = 0; i < phases.size(); i++) {
            if (phaseClass.isInstance(phases.get(i))) {
                return i;
            }
        }
        return -1;
    }

}
