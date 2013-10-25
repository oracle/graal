/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot;

import static com.oracle.graal.phases.GraalOptions.*;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Common functionality of HotSpot host backends.
 */
public abstract class HotSpotHostBackend extends HotSpotBackend {

    public HotSpotHostBackend(HotSpotGraalRuntime runtime, HotSpotProviders providers) {
        super(runtime, providers);
    }

    @Override
    public void completeInitialization() {
        final HotSpotProviders providers = getProviders();
        HotSpotVMConfig config = getRuntime().getConfig();
        HotSpotHostForeignCallsProvider foreignCalls = (HotSpotHostForeignCallsProvider) providers.getForeignCalls();
        final HotSpotHostLoweringProvider lowerer = (HotSpotHostLoweringProvider) providers.getLowerer();
        foreignCalls.initialize(providers, config);
        lowerer.initialize(providers, config);

        // Install intrinsics.
        if (Intrinsify.getValue()) {
            Debug.scope("RegisterReplacements", new Object[]{new DebugDumpScope("RegisterReplacements")}, new Runnable() {

                @Override
                public void run() {

                    Replacements replacements = providers.getReplacements();
                    ServiceLoader<ReplacementsProvider> sl = ServiceLoader.loadInstalled(ReplacementsProvider.class);
                    for (ReplacementsProvider replacementsProvider : sl) {
                        replacementsProvider.registerReplacements(providers.getMetaAccess(), lowerer, replacements, providers.getCodeCache().getTarget());
                    }
                    if (BootstrapReplacements.getValue()) {
                        for (ResolvedJavaMethod method : replacements.getAllReplacements()) {
                            replacements.getMacroSubstitution(method);
                            replacements.getMethodSubstitution(method);
                        }
                    }
                }
            });
        }
    }
}
