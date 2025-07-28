/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.amd64;

import java.util.ArrayList;
import java.util.List;

import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
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
import jdk.graal.compiler.nodes.gc.BarrierSet;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.AddressLoweringByNodePhase;
import jdk.graal.compiler.phases.tiers.CompilerConfiguration;
import jdk.graal.compiler.replacements.amd64.AMD64GraphBuilderPlugins;
import jdk.graal.compiler.serviceprovider.ServiceProvider;
import jdk.graal.compiler.vector.architecture.amd64.VectorAMD64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.Value;

@ServiceProvider(HotSpotBackendFactory.class)
public class AMD64HotSpotBackendFactory extends HotSpotBackendFactory {

    @Override
    public String getName() {
        return "community";
    }

    @Override
    public String getArchitecture() {
        return "AMD64";
    }

    @Override
    protected Plugins createGraphBuilderPlugins(HotSpotGraalRuntimeProvider graalRuntime, CompilerConfiguration compilerConfiguration, GraalHotSpotVMConfig config, TargetDescription target,
                    HotSpotConstantReflectionProvider constantReflection, HotSpotHostForeignCallsProvider foreignCalls, MetaAccessProvider metaAccess,
                    HotSpotSnippetReflectionProvider snippetReflection, HotSpotReplacementsImpl replacements, HotSpotWordTypes wordTypes, OptionValues options, BarrierSet barrierSet) {
        Plugins plugins = HotSpotGraphBuilderPlugins.create(
                        graalRuntime,
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
        AMD64GraphBuilderPlugins.register(plugins, options);
        return plugins;
    }

    @Override
    protected AMD64HotSpotBackend createBackend(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, HotSpotProviders providers) {
        return new AMD64HotSpotBackend(config, runtime, providers);
    }

    @Override
    protected HotSpotRegistersProvider createRegisters() {
        return new HotSpotRegisters(AMD64.r15, AMD64.r12, AMD64.rsp);
    }

    @Override
    protected HotSpotHostForeignCallsProvider createForeignCalls(HotSpotJVMCIRuntime runtime, HotSpotGraalRuntimeProvider graalRuntime, MetaAccessProvider metaAccess,
                    HotSpotCodeCacheProvider codeCache, HotSpotWordTypes wordTypes, Value[] nativeABICallerSaveRegisters) {
        return new AMD64HotSpotForeignCallsProvider(runtime, graalRuntime, metaAccess, codeCache, wordTypes, nativeABICallerSaveRegisters);
    }

    @Override
    protected HotSpotSuitesProvider createSuites(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, CompilerConfiguration compilerConfiguration, Plugins plugins,
                    HotSpotRegistersProvider registers, OptionValues options) {
        return new AddressLoweringHotSpotSuitesProvider(new AMD64HotSpotSuitesCreator(compilerConfiguration, plugins), config, runtime,
                        new AddressLoweringByNodePhase(new AMD64HotSpotAddressLowering(config, registers.getHeapBaseRegister())));
    }

    @Override
    protected HotSpotLoweringProvider createLowerer(HotSpotGraalRuntimeProvider runtime, MetaAccessProvider metaAccess, HotSpotHostForeignCallsProvider foreignCalls,
                    HotSpotRegistersProvider registers, HotSpotConstantReflectionProvider constantReflection, HotSpotPlatformConfigurationProvider platformConfig,
                    HotSpotMetaAccessExtensionProvider metaAccessExtensionProvider, TargetDescription target) {
        boolean enableObjectVectorization = true;
        VectorAMD64 varch = new VectorAMD64((AMD64) target.arch, metaAccess.getArrayIndexScale(JavaKind.Object), runtime.getVMConfig().useCompressedOops, runtime.getVMConfig().objectAlignment,
                        runtime.getVMConfig().maxVectorSize, enableObjectVectorization);
        return new AMD64HotSpotLoweringProvider(runtime, metaAccess, foreignCalls, registers, constantReflection, platformConfig, metaAccessExtensionProvider, target, varch);
    }

    @Override
    protected Value[] createNativeABICallerSaveRegisters(GraalHotSpotVMConfig config, RegisterConfig regConfig) {
        List<Register> callerSave = new ArrayList<>(regConfig.getAllocatableRegisters());
        if (config.osName.equals("windows")) {
            // http://msdn.microsoft.com/en-us/library/9z1stfyw.aspx
            callerSave.remove(AMD64.rdi);
            callerSave.remove(AMD64.rsi);
            callerSave.remove(AMD64.rbx);
            callerSave.remove(AMD64.rbp);
            callerSave.remove(AMD64.rsp);
            callerSave.remove(AMD64.r12);
            callerSave.remove(AMD64.r13);
            callerSave.remove(AMD64.r14);
            callerSave.remove(AMD64.r15);
            callerSave.remove(AMD64.xmm6);
            callerSave.remove(AMD64.xmm7);
            callerSave.remove(AMD64.xmm8);
            callerSave.remove(AMD64.xmm9);
            callerSave.remove(AMD64.xmm10);
            callerSave.remove(AMD64.xmm11);
            callerSave.remove(AMD64.xmm12);
            callerSave.remove(AMD64.xmm13);
            callerSave.remove(AMD64.xmm14);
            callerSave.remove(AMD64.xmm15);
        } else {
            /*
             * System V Application Binary Interface, AMD64 Architecture Processor Supplement
             *
             * Draft Version 0.96
             *
             * http://www.uclibc.org/docs/psABI-x86_64.pdf
             *
             * 3.2.1
             *
             * ...
             *
             * This subsection discusses usage of each register. Registers %rbp, %rbx and %r12
             * through %r15 "belong" to the calling function and the called function is required to
             * preserve their values. In other words, a called function must preserve these
             * registers' values for its caller. Remaining registers "belong" to the called
             * function. If a calling function wants to preserve such a register value across a
             * function call, it must save the value in its local stack frame.
             */
            callerSave.remove(AMD64.rbp);
            callerSave.remove(AMD64.rbx);
            callerSave.remove(AMD64.r12);
            callerSave.remove(AMD64.r13);
            callerSave.remove(AMD64.r14);
            callerSave.remove(AMD64.r15);
        }
        Value[] nativeABICallerSaveRegisters = new Value[callerSave.size()];
        for (int i = 0; i < callerSave.size(); i++) {
            nativeABICallerSaveRegisters[i] = callerSave.get(i).asValue();
        }
        return nativeABICallerSaveRegisters;
    }

    @Override
    public String toString() {
        return "AMD64";
    }
}
