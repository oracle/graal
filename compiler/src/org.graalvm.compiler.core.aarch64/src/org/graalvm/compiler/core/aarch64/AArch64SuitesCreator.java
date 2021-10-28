/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.aarch64;

import java.util.List;
import java.util.ListIterator;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.java.DefaultSuitesCreator;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;
import org.graalvm.compiler.phases.tiers.LowTierContext;
import org.graalvm.compiler.phases.tiers.Suites;

public class AArch64SuitesCreator extends DefaultSuitesCreator {
    private final List<Class<? extends BasePhase<? super CoreProviders>>> insertReadReplacementBeforePositions;

    public AArch64SuitesCreator(CompilerConfiguration compilerConfiguration, Plugins plugins, List<Class<? extends BasePhase<? super CoreProviders>>> insertReadReplacementBeforePositions) {
        super(compilerConfiguration, plugins);
        this.insertReadReplacementBeforePositions = insertReadReplacementBeforePositions;
    }

    public AArch64SuitesCreator(CompilerConfiguration compilerConfiguration, List<Class<? extends BasePhase<? super CoreProviders>>> insertReadReplacementBeforePositions) {
        super(compilerConfiguration);
        this.insertReadReplacementBeforePositions = insertReadReplacementBeforePositions;
    }

    @Override
    public Suites createSuites(OptionValues options) {
        Suites suites = super.createSuites(options);
        ListIterator<BasePhase<? super LowTierContext>> findPhase = null;
        for (Class<? extends BasePhase<? super CoreProviders>> phase : insertReadReplacementBeforePositions) {
            findPhase = suites.getLowTier().findPhase(phase);
            if (findPhase != null) {
                // Put AArch64ReadReplacementPhase right before the requested phase
                while (PhaseSuite.findNextPhase(findPhase, phase)) {
                    // Search for last occurrence of SchedulePhase
                }
                findPhase.previous();
                break;
            }
        }
        if (findPhase != null) {
            findPhase.add(new AArch64ReadReplacementPhase());
        } else {
            throw GraalError.shouldNotReachHere("Cannot find phase to insert AArch64ReadReplacementPhase");
        }
        return suites;
    }
}
