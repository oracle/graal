/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.meta;

import java.util.ListIterator;

import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.UseTrappingNullChecksPhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.tiers.LowTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.tiers.SuitesCreator;

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
    public Suites createSuites(OptionValues options) {
        Suites suites = super.createSuites(options);

        ListIterator<BasePhase<? super LowTierContext>> findPhase = suites.getLowTier().findPhase(UseTrappingNullChecksPhase.class);
        if (findPhase != null) {
            findPhase.add(addressLowering);
        } else {
            findPhase = suites.getLowTier().findPhase(SchedulePhase.class);
            findPhase.previous();
            findPhase.add(addressLowering);
        }

        return suites;
    }
}
