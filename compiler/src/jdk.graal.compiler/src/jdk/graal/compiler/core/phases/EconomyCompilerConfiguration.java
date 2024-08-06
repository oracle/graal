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
package jdk.graal.compiler.core.phases;

import jdk.graal.compiler.lir.phases.AllocationPhase.AllocationContext;
import jdk.graal.compiler.lir.phases.EconomyAllocationStage;
import jdk.graal.compiler.lir.phases.EconomyFinalCodeAnalysisStage;
import jdk.graal.compiler.lir.phases.EconomyPostAllocationOptimizationStage;
import jdk.graal.compiler.lir.phases.EconomyPreAllocationOptimizationStage;
import jdk.graal.compiler.lir.phases.FinalCodeAnalysisPhase.FinalCodeAnalysisContext;
import jdk.graal.compiler.lir.phases.LIRPhaseSuite;
import jdk.graal.compiler.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import jdk.graal.compiler.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.tiers.CompilerConfiguration;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.phases.tiers.MidTierContext;

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
