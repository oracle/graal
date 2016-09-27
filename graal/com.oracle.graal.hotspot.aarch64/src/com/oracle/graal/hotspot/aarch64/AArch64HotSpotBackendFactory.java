/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.aarch64;

import static jdk.vm.ci.aarch64.AArch64.sp;
import static jdk.vm.ci.common.InitTimer.timer;

import com.oracle.graal.api.replacements.SnippetReflectionProvider;
import com.oracle.graal.compiler.aarch64.AArch64AddressLowering;
import com.oracle.graal.compiler.aarch64.AArch64SuitesProvider;
import com.oracle.graal.hotspot.GraalHotSpotVMConfig;
import com.oracle.graal.hotspot.HotSpotBackend;
import com.oracle.graal.hotspot.HotSpotBackendFactory;
import com.oracle.graal.hotspot.HotSpotGraalRuntimeProvider;
import com.oracle.graal.hotspot.HotSpotReplacementsImpl;
import com.oracle.graal.hotspot.meta.HotSpotConstantFieldProvider;
import com.oracle.graal.hotspot.meta.HotSpotForeignCallsProvider;
import com.oracle.graal.hotspot.meta.HotSpotGraalConstantFieldProvider;
import com.oracle.graal.hotspot.meta.HotSpotGraphBuilderPlugins;
import com.oracle.graal.hotspot.meta.HotSpotHostForeignCallsProvider;
import com.oracle.graal.hotspot.meta.HotSpotLoweringProvider;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.hotspot.meta.HotSpotRegisters;
import com.oracle.graal.hotspot.meta.HotSpotRegistersProvider;
import com.oracle.graal.hotspot.meta.HotSpotSnippetReflectionProvider;
import com.oracle.graal.hotspot.meta.HotSpotStampProvider;
import com.oracle.graal.hotspot.meta.HotSpotSuitesProvider;
import com.oracle.graal.hotspot.nodes.HotSpotNodeCostProvider;
import com.oracle.graal.hotspot.word.HotSpotWordTypes;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.nodes.spi.NodeCostProvider;
import com.oracle.graal.phases.tiers.CompilerConfiguration;
import com.oracle.graal.phases.util.Providers;
import com.oracle.graal.replacements.aarch64.AArch64GraphBuilderPlugins;
import com.oracle.graal.serviceprovider.ServiceProvider;
import com.oracle.graal.word.WordTypes;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.RegisterArray;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider;
import jdk.vm.ci.hotspot.HotSpotMetaAccessProvider;
import jdk.vm.ci.hotspot.aarch64.AArch64HotSpotRegisterConfig;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.runtime.JVMCIBackend;

@ServiceProvider(HotSpotBackendFactory.class)
public class AArch64HotSpotBackendFactory implements HotSpotBackendFactory {

    @Override
    public String getName() {
        return "core";
    }

    @Override
    public Class<? extends Architecture> getArchitecture() {
        return AArch64.class;
    }

    @Override
    @SuppressWarnings("try")
    public HotSpotBackend createBackend(HotSpotGraalRuntimeProvider graalRuntime, CompilerConfiguration compilerConfiguration, HotSpotJVMCIRuntimeProvider jvmciRuntime, HotSpotBackend host) {
        assert host == null;

        JVMCIBackend jvmci = jvmciRuntime.getHostJVMCIBackend();
        GraalHotSpotVMConfig config = graalRuntime.getVMConfig();
        HotSpotProviders providers;
        HotSpotRegistersProvider registers;
        HotSpotCodeCacheProvider codeCache = (HotSpotCodeCacheProvider) jvmci.getCodeCache();
        TargetDescription target = codeCache.getTarget();
        HotSpotHostForeignCallsProvider foreignCalls;
        Value[] nativeABICallerSaveRegisters;
        HotSpotMetaAccessProvider metaAccess = (HotSpotMetaAccessProvider) jvmci.getMetaAccess();
        HotSpotConstantReflectionProvider constantReflection = (HotSpotConstantReflectionProvider) jvmci.getConstantReflection();
        HotSpotConstantFieldProvider constantFieldProvider = new HotSpotGraalConstantFieldProvider(config, metaAccess);
        HotSpotLoweringProvider lowerer;
        HotSpotSnippetReflectionProvider snippetReflection;
        HotSpotReplacementsImpl replacements;
        HotSpotSuitesProvider suites;
        HotSpotWordTypes wordTypes;
        Plugins plugins;
        NodeCostProvider nodeCostProvider;
        try (InitTimer t = timer("create providers")) {
            try (InitTimer rt = timer("create HotSpotRegisters provider")) {
                registers = createRegisters();
            }
            try (InitTimer rt = timer("create NativeABICallerSaveRegisters")) {
                nativeABICallerSaveRegisters = createNativeABICallerSaveRegisters(config, codeCache.getRegisterConfig());
            }
            try (InitTimer rt = timer("create WordTypes")) {
                wordTypes = new HotSpotWordTypes(metaAccess, target.wordJavaKind);
            }
            try (InitTimer rt = timer("create ForeignCalls provider")) {
                foreignCalls = createForeignCalls(jvmciRuntime, graalRuntime, metaAccess, codeCache, wordTypes, nativeABICallerSaveRegisters);
            }
            try (InitTimer rt = timer("create Lowerer provider")) {
                lowerer = createLowerer(graalRuntime, metaAccess, foreignCalls, registers, constantReflection, target);
            }
            try (InitTimer rt = timer("create NodeCost provider")) {
                nodeCostProvider = createNodeCostProvider();
            }
            HotSpotStampProvider stampProvider = new HotSpotStampProvider();
            Providers p = new Providers(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, null, stampProvider, nodeCostProvider);

            try (InitTimer rt = timer("create SnippetReflection provider")) {
                snippetReflection = createSnippetReflection(graalRuntime, constantReflection, wordTypes);
            }
            try (InitTimer rt = timer("create Replacements provider")) {
                replacements = createReplacements(p, snippetReflection);
            }
            try (InitTimer rt = timer("create GraphBuilderPhase plugins")) {
                plugins = createGraphBuilderPlugins(config, constantReflection, foreignCalls, metaAccess, snippetReflection, replacements, wordTypes, stampProvider);
                replacements.setGraphBuilderPlugins(plugins);
            }
            try (InitTimer rt = timer("create Suites provider")) {
                suites = createSuites(config, graalRuntime, compilerConfiguration, plugins);
            }
            providers = new HotSpotProviders(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, nodeCostProvider, suites, registers,
                            snippetReflection, wordTypes,
                            plugins);
        }
        try (InitTimer rt = timer("instantiate backend")) {
            return createBackend(config, graalRuntime, providers);
        }
    }

    protected Plugins createGraphBuilderPlugins(GraalHotSpotVMConfig config, HotSpotConstantReflectionProvider constantReflection, HotSpotHostForeignCallsProvider foreignCalls,
                    HotSpotMetaAccessProvider metaAccess, HotSpotSnippetReflectionProvider snippetReflection, HotSpotReplacementsImpl replacements, HotSpotWordTypes wordTypes,
                    HotSpotStampProvider stampProvider) {
        Plugins plugins = HotSpotGraphBuilderPlugins.create(config, wordTypes, metaAccess, constantReflection, snippetReflection, foreignCalls, stampProvider, replacements);
        AArch64GraphBuilderPlugins.register(plugins, foreignCalls);
        return plugins;
    }

    protected AArch64HotSpotBackend createBackend(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, HotSpotProviders providers) {
        return new AArch64HotSpotBackend(config, runtime, providers);
    }

    protected HotSpotRegistersProvider createRegisters() {
        return new HotSpotRegisters(AArch64HotSpotRegisterConfig.threadRegister, AArch64HotSpotRegisterConfig.heapBaseRegister, sp);
    }

    protected HotSpotReplacementsImpl createReplacements(Providers p, SnippetReflectionProvider snippetReflection) {
        return new HotSpotReplacementsImpl(p, snippetReflection, p.getCodeCache().getTarget());
    }

    protected HotSpotHostForeignCallsProvider createForeignCalls(HotSpotJVMCIRuntimeProvider jvmciRuntime, HotSpotGraalRuntimeProvider runtime, HotSpotMetaAccessProvider metaAccess,
                    HotSpotCodeCacheProvider codeCache, WordTypes wordTypes, Value[] nativeABICallerSaveRegisters) {
        return new AArch64HotSpotForeignCallsProvider(jvmciRuntime, runtime, metaAccess, codeCache, wordTypes, nativeABICallerSaveRegisters);
    }

    protected HotSpotSuitesProvider createSuites(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, CompilerConfiguration compilerConfiguration, Plugins plugins) {
        return new HotSpotSuitesProvider(new AArch64SuitesProvider(compilerConfiguration, plugins), config, runtime, new AArch64AddressLowering());
    }

    protected HotSpotSnippetReflectionProvider createSnippetReflection(HotSpotGraalRuntimeProvider runtime, HotSpotConstantReflectionProvider constantReflection, WordTypes wordTypes) {
        return new HotSpotSnippetReflectionProvider(runtime, constantReflection, wordTypes);
    }

    protected HotSpotLoweringProvider createLowerer(HotSpotGraalRuntimeProvider runtime, HotSpotMetaAccessProvider metaAccess, HotSpotForeignCallsProvider foreignCalls,
                    HotSpotRegistersProvider registers, HotSpotConstantReflectionProvider constantReflection, TargetDescription target) {
        return new AArch64HotSpotLoweringProvider(runtime, metaAccess, foreignCalls, registers, constantReflection, target);
    }

    protected HotSpotNodeCostProvider createNodeCostProvider() {
        return new AArchHotSpotNodeCostProvider();
    }

    protected static Value[] createNativeABICallerSaveRegisters(@SuppressWarnings("unused") GraalHotSpotVMConfig config, RegisterConfig regConfig) {
        AArch64HotSpotRegisterConfig conf = (AArch64HotSpotRegisterConfig) regConfig;
        RegisterArray callerSavedRegisters = conf.getCallerSaveRegisters();
        int size = callerSavedRegisters.size();
        Value[] nativeABICallerSaveRegisters = new Value[size];
        for (int i = 0; i < size; i++) {
            nativeABICallerSaveRegisters[i] = callerSavedRegisters.get(i).asValue();
        }
        return nativeABICallerSaveRegisters;
    }

    @Override
    public String toString() {
        return "AArch64";
    }
}
