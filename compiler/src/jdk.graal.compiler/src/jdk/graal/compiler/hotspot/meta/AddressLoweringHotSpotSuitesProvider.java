/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2017, Red Hat Inc. All rights reserved.
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
package jdk.graal.compiler.hotspot.meta;

import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntimeProvider;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.Speculative;
import jdk.graal.compiler.phases.common.AddressLoweringPhase;
import jdk.graal.compiler.phases.common.TransplantGraphsPhase;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.tiers.SuitesCreator;
import jdk.vm.ci.code.Architecture;

/**
 * Subclass to factor out management of address lowering.
 */
public class AddressLoweringHotSpotSuitesProvider extends HotSpotSuitesProvider {

    private final BasePhase<CoreProviders> addressLowering;

    public AddressLoweringHotSpotSuitesProvider(SuitesCreator defaultSuitesCreator, GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime,
                    BasePhase<CoreProviders> addressLowering) {
        super(defaultSuitesCreator, config, runtime);
        this.addressLowering = addressLowering;
    }

    @Override
    public Suites createSuites(OptionValues options, Architecture arch) {
        Suites suites = super.createSuites(options, arch);
        suites.getLowTier().replacePlaceholder(AddressLoweringPhase.class, addressLowering);
        suites.getLowTier().replacePlaceholder(TransplantGraphsPhase.class, new TransplantGraphsPhase(createSuitesForLateSnippetTemplate(suites)));
        return suites;
    }

    private static Suites createSuitesForLateSnippetTemplate(Suites regularCompileSuites) {

        Suites s = regularCompileSuites.copy();

        // massage the phase plan for the low tier snippets
        s.getHighTier().removeSubTypePhases(Speculative.class);
        s.getMidTier().removeSubTypePhases(Speculative.class);
        s.getLowTier().removeSubTypePhases(Speculative.class);

        s.getLowTier().removeAllPlaceHolderOfType(TransplantGraphsPhase.class);

        return s;
    }
}
