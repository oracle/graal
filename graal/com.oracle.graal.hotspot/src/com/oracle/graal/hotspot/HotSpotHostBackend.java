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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.hotspot.HotSpotReplacementsImpl.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Common functionality of HotSpot host backends.
 */
public abstract class HotSpotHostBackend extends HotSpotBackend implements HostBackend {

    /**
     * This will be 0 if stack banging is disabled.
     */
    protected final int pagesToBang;

    public HotSpotHostBackend(HotSpotGraalRuntime runtime, HotSpotProviders providers) {
        super(runtime, providers);
        this.pagesToBang = runtime.getConfig().useStackBanging ? runtime.getConfig().stackShadowPages : 0;
    }

    @Override
    public void completeInitialization() {
        final HotSpotProviders providers = getProviders();
        HotSpotVMConfig config = getRuntime().getConfig();
        HotSpotHostForeignCallsProvider foreignCalls = (HotSpotHostForeignCallsProvider) providers.getForeignCalls();
        final HotSpotLoweringProvider lowerer = (HotSpotLoweringProvider) providers.getLowerer();
        foreignCalls.initialize(providers, config);
        lowerer.initialize(providers, config);
        HotSpotReplacementsImpl replacements = (HotSpotReplacementsImpl) providers.getReplacements();

        replacements.registerGraphProducers(getNonHostGraphProducers());

        // Install intrinsics.
        if (Intrinsify.getValue()) {
            try (Scope s = Debug.scope("RegisterReplacements", new DebugDumpScope("RegisterReplacements"))) {
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
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
        }

    }

    /**
     * Gets the {@link GraphProducer}s from the non-host backends. These allow a GPU backend (for
     * example) to offload compilation and execution of certain methods to a GPU.
     * <p>
     * Note that is is a very rough initial attempt at providing a hook for a GPU backend to
     * intercept a compilation (or method inlining) for the purpose of routing execution to the GPU.
     * Expect it to be extensively refined as experimentation with GPU offload proceeds.
     */
    protected GraphProducer[] getNonHostGraphProducers() {
        List<GraphProducer> list = new ArrayList<>();
        for (Map.Entry<Class<? extends Architecture>, HotSpotBackend> e : getRuntime().getBackends().entrySet()) {
            HotSpotBackend value = e.getValue();
            if (value != this) {
                GraphProducer gp = value.getGraphProducer();
                if (gp != null) {
                    list.add(gp);
                }
            }
        }
        return list.toArray(new GraphProducer[list.size()]);
    }
}
