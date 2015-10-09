/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.compiler.common.GraalOptions.BootstrapReplacements;
import static com.oracle.graal.compiler.common.GraalOptions.Intrinsify;
import static jdk.vm.ci.inittimer.InitTimer.timer;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotVMConfig;
import jdk.vm.ci.inittimer.InitTimer;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.service.Services;

import com.oracle.graal.compiler.common.spi.ForeignCallDescriptor;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.debug.DebugDumpScope;
import com.oracle.graal.hotspot.meta.HotSpotHostForeignCallsProvider;
import com.oracle.graal.hotspot.meta.HotSpotLoweringProvider;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.hotspot.stubs.DeoptimizationStub;
import com.oracle.graal.hotspot.stubs.UncommonTrapStub;
import com.oracle.graal.nodes.spi.ReplacementsProvider;

/**
 * Common functionality of HotSpot host backends.
 */
public abstract class HotSpotHostBackend extends HotSpotBackend {

    /**
     * Descriptor for {@code SharedRuntime::deopt_blob()->unpack()} or
     * {@link DeoptimizationStub#deoptimizationHandler} depending on
     * {@link HotSpotBackend.Options#PreferGraalStubs}.
     */
    public static final ForeignCallDescriptor DEOPTIMIZATION_HANDLER = new ForeignCallDescriptor("deoptHandler", void.class);

    /**
     * Descriptor for {@code SharedRuntime::deopt_blob()->uncommon_trap()} or
     * {@link UncommonTrapStub#uncommonTrapHandler} depending on
     * {@link HotSpotBackend.Options#PreferGraalStubs}.
     */
    public static final ForeignCallDescriptor UNCOMMON_TRAP_HANDLER = new ForeignCallDescriptor("uncommonTrapHandler", void.class);

    /**
     * This will be 0 if stack banging is disabled.
     */
    protected final int pagesToBang;

    public HotSpotHostBackend(HotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, HotSpotProviders providers) {
        super(runtime, providers);
        this.pagesToBang = config.useStackBanging ? config.stackShadowPages : 0;
    }

    @Override
    @SuppressWarnings("try")
    public void completeInitialization(HotSpotJVMCIRuntime jvmciRuntime) {
        final HotSpotProviders providers = getProviders();
        HotSpotHostForeignCallsProvider foreignCalls = (HotSpotHostForeignCallsProvider) providers.getForeignCalls();
        final HotSpotLoweringProvider lowerer = (HotSpotLoweringProvider) providers.getLowerer();
        HotSpotReplacementsImpl replacements = (HotSpotReplacementsImpl) providers.getReplacements();

        try (InitTimer st = timer("foreignCalls.initialize")) {
            foreignCalls.initialize(providers);
        }
        try (InitTimer st = timer("lowerer.initialize")) {
            lowerer.initialize(providers, jvmciRuntime.getConfig());
        }

        // Install intrinsics.
        if (Intrinsify.getValue()) {
            try (Scope s = Debug.scope("RegisterReplacements", new DebugDumpScope("RegisterReplacements"))) {
                try (InitTimer st = timer("replacementsProviders.registerReplacements")) {
                    Iterable<ReplacementsProvider> sl = Services.load(ReplacementsProvider.class);
                    for (ReplacementsProvider replacementsProvider : sl) {
                        replacementsProvider.registerReplacements(providers.getMetaAccess(), lowerer, providers.getSnippetReflection(), replacements, providers.getCodeCache().getTarget());
                    }
                }
                if (BootstrapReplacements.getValue()) {
                    for (ResolvedJavaMethod method : replacements.getAllReplacements()) {
                        replacements.getSubstitution(method, -1);
                    }
                }
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
        }
    }
}
