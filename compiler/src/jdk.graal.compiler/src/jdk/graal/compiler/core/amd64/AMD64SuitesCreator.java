/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.amd64;

import java.util.ListIterator;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.java.DefaultSuitesCreator;
import jdk.graal.compiler.lir.amd64.phases.StackMoveOptimizationPhase;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.common.UseTrappingNullChecksPhase;
import jdk.graal.compiler.phases.tiers.CompilerConfiguration;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.vector.phases.amd64.AMD64VectorLoweringPhase;
import jdk.vm.ci.code.Architecture;

public abstract class AMD64SuitesCreator extends DefaultSuitesCreator {

    public AMD64SuitesCreator(CompilerConfiguration compilerConfiguration, Plugins plugins) {
        super(compilerConfiguration, plugins);
    }

    public AMD64SuitesCreator(CompilerConfiguration compilerConfiguration) {
        super(compilerConfiguration);
    }

    @Override
    public Suites createSuites(OptionValues options, Architecture arch) {
        Suites suites = super.createSuites(options, arch);
        ListIterator<BasePhase<? super LowTierContext>> position = suites.getLowTier().findPhase(UseTrappingNullChecksPhase.class);
        if (position != null) {
            position.previous();
            position.add(new UseTrappingDivPhase());
        }
        if (GraalOptions.TargetVectorLowering.getValue(options)) {
            position = suites.getLowTier().findPhase(DeadCodeEliminationPhase.class);
            if (position != null) {
                position.previous();
                position.add(new AMD64VectorLoweringPhase());
            }
        }
        return suites;
    }

    @Override
    public LIRSuites createLIRSuites(OptionValues options) {
        LIRSuites lirSuites = super.createLIRSuites(options);
        if (StackMoveOptimizationPhase.Options.LIROptStackMoveOptimizer.getValue(options)) {
            /* Note: this phase must be inserted <b>after</b> RedundantMoveElimination */
            lirSuites.getPostAllocationOptimizationStage().appendPhase(new StackMoveOptimizationPhase());
        }
        return lirSuites;
    }
}
