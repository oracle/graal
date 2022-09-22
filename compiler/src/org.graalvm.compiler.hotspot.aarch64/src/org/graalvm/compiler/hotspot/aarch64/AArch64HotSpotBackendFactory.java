/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, Red Hat Inc. All rights reserved.
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
package org.graalvm.compiler.hotspot.aarch64;

import static jdk.vm.ci.aarch64.AArch64.sp;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.core.aarch64.AArch64AddressLoweringByUse;
import org.graalvm.compiler.core.aarch64.AArch64LIRKindTool;
import org.graalvm.compiler.core.aarch64.AArch64SuitesCreator;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotBackendFactory;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.HotSpotReplacementsImpl;
import org.graalvm.compiler.hotspot.meta.AddressLoweringHotSpotSuitesProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotGraphBuilderPlugins;
import org.graalvm.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotLoweringProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotMetaAccessExtensionProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotPlatformConfigurationProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.meta.HotSpotRegisters;
import org.graalvm.compiler.hotspot.meta.HotSpotRegistersProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotSnippetReflectionProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotSuitesProvider;
import org.graalvm.compiler.hotspot.word.HotSpotWordTypes;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.AddressLoweringByUsePhase;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;
import org.graalvm.compiler.replacements.aarch64.AArch64GraphBuilderPlugins;
import org.graalvm.compiler.serviceprovider.ServiceProvider;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.aarch64.AArch64HotSpotRegisterConfig;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.Value;

@ServiceProvider(HotSpotBackendFactory.class)
public class AArch64HotSpotBackendFactory extends HotSpotBackendFactory {

    @Override
    public String getName() {
        return "community";
    }

    @Override
    public Class<? extends Architecture> getArchitecture() {
        return AArch64.class;
    }

    @Override
    protected Plugins createGraphBuilderPlugins(HotSpotGraalRuntimeProvider graalRuntime,
                    CompilerConfiguration compilerConfiguration,
                    GraalHotSpotVMConfig config,
                    TargetDescription target,
                    HotSpotConstantReflectionProvider constantReflection,
                    HotSpotHostForeignCallsProvider foreignCalls,
                    MetaAccessProvider metaAccess,
                    HotSpotSnippetReflectionProvider snippetReflection,
                    HotSpotReplacementsImpl replacements,
                    HotSpotWordTypes wordTypes,
                    OptionValues options) {
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
                        target);
        AArch64GraphBuilderPlugins.register(plugins,
                        replacements,
                        /* registerForeignCallMath */true,
                        options);
        return plugins;
    }

    @Override
    protected AArch64HotSpotBackend createBackend(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, HotSpotProviders providers) {
        return new AArch64HotSpotBackend(config, runtime, providers);
    }

    @Override
    protected HotSpotRegistersProvider createRegisters() {
        return new HotSpotRegisters(AArch64HotSpotRegisterConfig.threadRegister, AArch64HotSpotRegisterConfig.heapBaseRegister, sp);
    }

    @Override
    protected HotSpotHostForeignCallsProvider createForeignCalls(HotSpotJVMCIRuntime jvmciRuntime, HotSpotGraalRuntimeProvider graalRuntime, MetaAccessProvider metaAccess,
                    HotSpotCodeCacheProvider codeCache, HotSpotWordTypes wordTypes, Value[] nativeABICallerSaveRegisters) {
        return new AArch64HotSpotForeignCallsProvider(jvmciRuntime, graalRuntime, metaAccess, codeCache, wordTypes, nativeABICallerSaveRegisters);
    }

    @Override
    protected HotSpotSuitesProvider createSuites(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, CompilerConfiguration compilerConfiguration, Plugins plugins,
                    HotSpotRegistersProvider registers, HotSpotReplacementsImpl replacements, OptionValues options) {
        AArch64SuitesCreator suitesCreator = new AArch64SuitesCreator(compilerConfiguration, plugins);
        BasePhase<CoreProviders> addressLoweringPhase = new AddressLoweringByUsePhase(new AArch64AddressLoweringByUse(new AArch64LIRKindTool(), true));
        return new AddressLoweringHotSpotSuitesProvider(suitesCreator, config, runtime, addressLoweringPhase);
    }

    @Override
    protected HotSpotLoweringProvider createLowerer(HotSpotGraalRuntimeProvider graalRuntime, MetaAccessProvider metaAccess, HotSpotHostForeignCallsProvider foreignCalls,
                    HotSpotRegistersProvider registers, HotSpotConstantReflectionProvider constantReflection, HotSpotPlatformConfigurationProvider platformConfig,
                    HotSpotMetaAccessExtensionProvider metaAccessExtensionProvider, TargetDescription target) {
        return new AArch64HotSpotLoweringProvider(graalRuntime, metaAccess, foreignCalls, registers, constantReflection, platformConfig, metaAccessExtensionProvider, target);
    }

    @Override
    protected Value[] createNativeABICallerSaveRegisters(@SuppressWarnings("unused") GraalHotSpotVMConfig config, RegisterConfig regConfig) {
        List<Register> callerSave = new ArrayList<>(regConfig.getAllocatableRegisters().asList());
        // Removing callee-saved registers.
        /* General Purpose Registers. */
        callerSave.remove(AArch64.r19);
        callerSave.remove(AArch64.r20);
        callerSave.remove(AArch64.r21);
        callerSave.remove(AArch64.r22);
        callerSave.remove(AArch64.r23);
        callerSave.remove(AArch64.r24);
        callerSave.remove(AArch64.r25);
        callerSave.remove(AArch64.r26);
        callerSave.remove(AArch64.r27);
        callerSave.remove(AArch64.r28);
        /* Floating-Point Registers. */
        callerSave.remove(AArch64.v8);
        callerSave.remove(AArch64.v9);
        callerSave.remove(AArch64.v10);
        callerSave.remove(AArch64.v11);
        callerSave.remove(AArch64.v12);
        callerSave.remove(AArch64.v13);
        callerSave.remove(AArch64.v14);
        callerSave.remove(AArch64.v15);
        Value[] nativeABICallerSaveRegisters = new Value[callerSave.size()];
        for (int i = 0; i < callerSave.size(); i++) {
            nativeABICallerSaveRegisters[i] = callerSave.get(i).asValue();
        }
        return nativeABICallerSaveRegisters;
    }

    @Override
    public String toString() {
        return "AArch64";
    }
}
