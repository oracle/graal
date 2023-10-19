/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.core.phases;

import jdk.compiler.graal.lir.phases.AllocationPhase.AllocationContext;
import jdk.compiler.graal.lir.phases.EconomyAllocationStage;
import jdk.compiler.graal.lir.phases.EconomyFinalCodeAnalysisStage;
import jdk.compiler.graal.lir.phases.EconomyPostAllocationOptimizationStage;
import jdk.compiler.graal.lir.phases.EconomyPreAllocationOptimizationStage;
import jdk.compiler.graal.lir.phases.FinalCodeAnalysisPhase.FinalCodeAnalysisContext;
import jdk.compiler.graal.lir.phases.LIRPhaseSuite;
import jdk.compiler.graal.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import jdk.compiler.graal.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import jdk.compiler.graal.options.OptionValues;
import jdk.compiler.graal.phases.PhaseSuite;
import jdk.compiler.graal.phases.tiers.CompilerConfiguration;
import jdk.compiler.graal.phases.tiers.HighTierContext;
import jdk.compiler.graal.phases.tiers.LowTierContext;
import jdk.compiler.graal.phases.tiers.MidTierContext;

import jdk.vm.ci.code.Architecture;

/**
 * A compiler configuration that performs fewer Graal IR optimizations while using the same backend
 * as the {@link CommunityCompilerConfiguration}.
 */
public class EconomyCompilerConfiguration implements CompilerConfiguration {

    @Override
    public PhaseSuite<HighTierContext> createHighTier(OptionValues options) {
        return new EconomyHighTier();
    }

    @Override
    public PhaseSuite<MidTierContext> createMidTier(OptionValues options) {
        return new EconomyMidTier();
    }

    @Override
    public PhaseSuite<LowTierContext> createLowTier(OptionValues options, Architecture arch) {
        return new EconomyLowTier();
    }

    @Override
    public LIRPhaseSuite<PreAllocationOptimizationContext> createPreAllocationOptimizationStage(OptionValues options) {
        return new EconomyPreAllocationOptimizationStage();
    }

    @Override
    public LIRPhaseSuite<AllocationContext> createAllocationStage(OptionValues options) {
        return new EconomyAllocationStage(options);
    }

    @Override
    public LIRPhaseSuite<PostAllocationOptimizationContext> createPostAllocationOptimizationStage(OptionValues options) {
        return new EconomyPostAllocationOptimizationStage();
    }

    @Override
    public LIRPhaseSuite<FinalCodeAnalysisContext> createFinalCodeAnalysisStage(OptionValues options) {
        return new EconomyFinalCodeAnalysisStage();
    }

}
