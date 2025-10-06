/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.riscv64;

import java.util.ArrayList;
import java.util.List;

import jdk.graal.compiler.core.common.alloc.RegisterAllocationConfig;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotBackend;
import jdk.graal.compiler.hotspot.HotSpotBackendFactory;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntimeProvider;
import jdk.graal.compiler.hotspot.HotSpotReplacementsImpl;
import jdk.graal.compiler.hotspot.meta.AddressLoweringHotSpotSuitesProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotGraphBuilderPlugins;
import jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotLoweringProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotMetaAccessExtensionProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotPlatformConfigurationProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.meta.HotSpotRegisters;
import jdk.graal.compiler.hotspot.meta.HotSpotRegistersProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotSnippetReflectionProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotSuitesProvider;
import jdk.graal.compiler.hotspot.word.HotSpotWordTypes;
import jdk.graal.compiler.java.DefaultSuitesCreator;
import jdk.graal.compiler.lir.framemap.ReferenceMapBuilder;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.gc.BarrierSet;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.AddressLoweringPhase;
import jdk.graal.compiler.phases.tiers.CompilerConfiguration;
import jdk.graal.compiler.serviceprovider.ServiceProvider;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.riscv64.RISCV64HotSpotRegisterConfig;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.riscv64.RISCV64;

@ServiceProvider(HotSpotBackendFactory.class)
public class RISCV64HotSpotBackendFactory extends HotSpotBackendFactory {
    @Override
    public String getName() {
        return "community";
    }

    @Override
    public String getArchitecture() {
        return "riscv64";
    }

    @Override
    protected Plugins createGraphBuilderPlugins(HotSpotGraalRuntimeProvider graalRuntime, CompilerConfiguration compilerConfiguration, GraalHotSpotVMConfig config, TargetDescription target,
                    HotSpotConstantReflectionProvider constantReflection, HotSpotHostForeignCallsProvider foreignCalls, MetaAccessProvider metaAccess,
                    HotSpotSnippetReflectionProvider snippetReflection, HotSpotReplacementsImpl replacements, HotSpotWordTypes wordTypes, OptionValues options, BarrierSet barrierSet) {
        Plugins plugins = HotSpotGraphBuilderPlugins.create(graalRuntime,
                        compilerConfiguration,
                        config,
                        wordTypes,
                        metaAccess,
                        constantReflection,
                        snippetReflection,
                        foreignCalls,
                        replacements,
                        options,
                        target,
                        barrierSet);
        return plugins;
    }

    @Override
    protected HotSpotBackend createBackend(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, HotSpotProviders providers) {
        return new HotSpotBackend(runtime, providers) {
            @Override
            public RegisterAllocationConfig newRegisterAllocationConfig(RegisterConfig registerConfig, String[] allocationRestrictedTo, Object stub) {
                throw GraalError.unimplementedOverride();
            }

            @Override
            public ReferenceMapBuilder newReferenceMapBuilder(int totalFrameSize) {
                throw GraalError.unimplementedOverride();
            }
        };
    }

    @Override
    protected HotSpotRegistersProvider createRegisters() {
        return new HotSpotRegisters(RISCV64HotSpotRegisterConfig.threadRegister, RISCV64.x27, RISCV64HotSpotRegisterConfig.sp);
    }

    @Override
    protected HotSpotHostForeignCallsProvider createForeignCalls(HotSpotJVMCIRuntime jvmciRuntime, HotSpotGraalRuntimeProvider graalRuntime, MetaAccessProvider metaAccess,
                    HotSpotCodeCacheProvider codeCache, HotSpotWordTypes wordTypes, Value[] nativeABICallerSaveRegisters) {
        return new RISCV64HotSpotForeignCallsProvider(jvmciRuntime, graalRuntime, metaAccess, codeCache, wordTypes, nativeABICallerSaveRegisters);
    }

    public static class EmptyAddressLoweringPhase extends AddressLoweringPhase {

        @Override
        protected void run(StructuredGraph graph, CoreProviders context) {
            // Do nothing
        }
    }

    @Override
    protected HotSpotSuitesProvider createSuites(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, CompilerConfiguration compilerConfiguration, Plugins plugins,
                    HotSpotRegistersProvider registers, OptionValues options) {
        DefaultSuitesCreator suitesCreator = new RISCV64HotSpotSuitesCreator(compilerConfiguration, plugins);
        BasePhase<CoreProviders> addressLoweringPhase = new EmptyAddressLoweringPhase();
        return new AddressLoweringHotSpotSuitesProvider(suitesCreator, config, runtime, addressLoweringPhase);
    }

    @Override
    protected HotSpotLoweringProvider createLowerer(HotSpotGraalRuntimeProvider graalRuntime, MetaAccessProvider metaAccess, HotSpotHostForeignCallsProvider foreignCalls,
                    HotSpotRegistersProvider registers, HotSpotConstantReflectionProvider constantReflection, HotSpotPlatformConfigurationProvider platformConfig,
                    HotSpotMetaAccessExtensionProvider metaAccessExtensionProvider, TargetDescription target) {
        return new RISCV64HotSpotLoweringProvider(graalRuntime, metaAccess, foreignCalls, registers, constantReflection, platformConfig, metaAccessExtensionProvider, target);
    }

    @Override
    protected Value[] createNativeABICallerSaveRegisters(@SuppressWarnings("unused") GraalHotSpotVMConfig config, RegisterConfig regConfig) {
        List<Register> callerSave = new ArrayList<>(regConfig.getAllocatableRegisters());
        // Removing callee-saved registers.
        /* General Purpose Registers. */
        callerSave.remove(RISCV64.x2);
        callerSave.remove(RISCV64.x8);
        callerSave.remove(RISCV64.x9);
        callerSave.remove(RISCV64.x18);
        callerSave.remove(RISCV64.x19);
        callerSave.remove(RISCV64.x20);
        callerSave.remove(RISCV64.x21);
        callerSave.remove(RISCV64.x22);
        callerSave.remove(RISCV64.x23);
        callerSave.remove(RISCV64.x24);
        callerSave.remove(RISCV64.x25);
        callerSave.remove(RISCV64.x26);
        callerSave.remove(RISCV64.x27);
        /* Floating-Point Registers. */
        callerSave.remove(RISCV64.f8);
        callerSave.remove(RISCV64.f9);
        callerSave.remove(RISCV64.f18);
        callerSave.remove(RISCV64.f19);
        callerSave.remove(RISCV64.f20);
        callerSave.remove(RISCV64.f21);
        callerSave.remove(RISCV64.f22);
        callerSave.remove(RISCV64.f23);
        callerSave.remove(RISCV64.f24);
        callerSave.remove(RISCV64.f25);
        callerSave.remove(RISCV64.f26);
        callerSave.remove(RISCV64.f27);

        Value[] nativeABICallerSaveRegisters = new Value[callerSave.size()];
        for (int i = 0; i < callerSave.size(); i++) {
            nativeABICallerSaveRegisters[i] = callerSave.get(i).asValue();
        }
        return nativeABICallerSaveRegisters;
    }

    @Override
    public String toString() {
        return "RISCV64";
    }
}
