/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot;

import static jdk.vm.ci.common.InitTimer.timer;
import static jdk.vm.ci.services.Services.IS_BUILDING_NATIVE_IMAGE;
import static jdk.vm.ci.services.Services.IS_IN_NATIVE_IMAGE;

import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotGraalConstantFieldProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotLoweringProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotMetaAccessExtensionProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotPlatformConfigurationProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.meta.HotSpotRegistersProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotSnippetReflectionProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotStampProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotSuitesProvider;
import org.graalvm.compiler.hotspot.word.HotSpotWordTypes;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.loop.LoopsDataProviderImpl;
import org.graalvm.compiler.nodes.spi.LoopsDataProvider;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;
import org.graalvm.compiler.replacements.classfile.ClassfileBytecodeProvider;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.runtime.JVMCIBackend;

public abstract class HotSpotBackendFactory {

    protected HotSpotGraalConstantFieldProvider createConstantFieldProvider(GraalHotSpotVMConfig config, MetaAccessProvider metaAccess) {
        return new HotSpotGraalConstantFieldProvider(config, metaAccess);
    }

    protected HotSpotWordTypes createWordTypes(MetaAccessProvider metaAccess, TargetDescription target) {
        return new HotSpotWordTypes(HotSpotReplacementsImpl.noticeTypes(metaAccess), target.wordJavaKind);
    }

    protected HotSpotStampProvider createStampProvider() {
        return new HotSpotStampProvider();
    }

    protected HotSpotPlatformConfigurationProvider createConfigInfoProvider(GraalHotSpotVMConfig config, MetaAccessProvider metaAccess) {
        return new HotSpotPlatformConfigurationProvider(config, metaAccess);
    }

    protected HotSpotMetaAccessExtensionProvider createMetaAccessExtensionProvider() {
        return new HotSpotMetaAccessExtensionProvider();
    }

    protected HotSpotReplacementsImpl createReplacements(TargetDescription target, HotSpotProviders p, HotSpotSnippetReflectionProvider snippetReflection, BytecodeProvider bytecodeProvider) {
        return new HotSpotReplacementsImpl(p, snippetReflection, bytecodeProvider, target);
    }

    protected ClassfileBytecodeProvider createBytecodeProvider(MetaAccessProvider metaAccess, HotSpotSnippetReflectionProvider snippetReflection) {
        return new ClassfileBytecodeProvider(metaAccess, snippetReflection);
    }

    protected HotSpotSnippetReflectionProvider createSnippetReflection(HotSpotGraalRuntimeProvider runtime, HotSpotConstantReflectionProvider constantReflection, HotSpotWordTypes wordTypes) {
        return new HotSpotSnippetReflectionProvider(runtime, constantReflection, wordTypes);
    }

    /**
     * Gets the name of this backend factory. This should not include the {@link #getArchitecture()
     * architecture}. The {@link CompilerConfigurationFactory} can select alternative backends based
     * on this name.
     */
    public abstract String getName();

    /**
     * Gets the class describing the architecture the backend created by this factory is associated
     * with.
     */
    public abstract Class<? extends Architecture> getArchitecture();

    protected LoopsDataProvider createLoopsDataProvider() {
        return new LoopsDataProviderImpl();
    }

    @SuppressWarnings("try")
    public final HotSpotBackend createBackend(HotSpotGraalRuntimeProvider graalRuntime, CompilerConfiguration compilerConfiguration, HotSpotJVMCIRuntime jvmciRuntime, HotSpotBackend host) {
        assert host == null;

        OptionValues options = graalRuntime.getOptions();
        JVMCIBackend jvmci = jvmciRuntime.getHostJVMCIBackend();
        GraalHotSpotVMConfig config = graalRuntime.getVMConfig();
        if (IS_BUILDING_NATIVE_IMAGE || IS_IN_NATIVE_IMAGE) {
            SnippetSignature.initPrimitiveKindCache(jvmci.getMetaAccess());
        }
        HotSpotCodeCacheProvider codeCache = (HotSpotCodeCacheProvider) jvmci.getCodeCache();
        TargetDescription target = codeCache.getTarget();
        MetaAccessProvider metaAccess = new HotSpotSnippetMetaAccessProvider(jvmci.getMetaAccess());
        HotSpotConstantReflectionProvider constantReflection = (HotSpotConstantReflectionProvider) jvmci.getConstantReflection();
        ConstantFieldProvider constantFieldProvider = new HotSpotGraalConstantFieldProvider(config, metaAccess);
        HotSpotProviders providers;
        try (InitTimer t = timer("create providers")) {
            HotSpotRegistersProvider registers;
            try (InitTimer rt = timer("create HotSpotRegisters provider")) {
                registers = createRegisters();
            }
            Value[] nativeABICallerSaveRegisters;
            try (InitTimer rt = timer("create NativeABICallerSaveRegisters")) {
                nativeABICallerSaveRegisters = createNativeABICallerSaveRegisters(config, codeCache.getRegisterConfig());
            }
            HotSpotWordTypes wordTypes;
            try (InitTimer rt = timer("create WordTypes")) {
                wordTypes = createWordTypes(metaAccess, target);
            }
            HotSpotHostForeignCallsProvider foreignCalls;
            try (InitTimer rt = timer("create ForeignCalls provider")) {
                foreignCalls = createForeignCalls(jvmciRuntime, graalRuntime, metaAccess, codeCache, wordTypes, nativeABICallerSaveRegisters);
            }
            HotSpotPlatformConfigurationProvider platformConfigurationProvider;
            try (InitTimer rt = timer("create platform configuration provider")) {
                platformConfigurationProvider = createConfigInfoProvider(config, metaAccess);
            }
            HotSpotMetaAccessExtensionProvider metaAccessExtensionProvider;
            try (InitTimer rt = timer("create MetaAccessExtensionProvider")) {
                metaAccessExtensionProvider = createMetaAccessExtensionProvider();
            }
            HotSpotStampProvider stampProvider;
            try (InitTimer rt = timer("create stamp provider")) {
                stampProvider = createStampProvider();
            }
            HotSpotLoweringProvider lowerer;
            try (InitTimer rt = timer("create Lowerer provider")) {
                lowerer = createLowerer(graalRuntime, metaAccess, foreignCalls, registers, constantReflection, platformConfigurationProvider, metaAccessExtensionProvider, target);
            }
            LoopsDataProvider loopsDataProvider;
            try (InitTimer rt = timer("create loopsdata provider")) {
                loopsDataProvider = createLoopsDataProvider();
            }

            HotSpotSnippetReflectionProvider snippetReflection;
            try (InitTimer rt = timer("create SnippetReflection provider")) {
                snippetReflection = createSnippetReflection(graalRuntime, constantReflection, wordTypes);
            }
            BytecodeProvider bytecodeProvider;
            try (InitTimer rt = timer("create Bytecode provider")) {
                bytecodeProvider = createBytecodeProvider(metaAccess, snippetReflection);
            }

            providers = new HotSpotProviders(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, null, null, registers,
                            snippetReflection, wordTypes, stampProvider, platformConfigurationProvider, metaAccessExtensionProvider, loopsDataProvider, config);
            HotSpotReplacementsImpl replacements;
            try (InitTimer rt = timer("create Replacements provider")) {
                replacements = createReplacements(target, providers, snippetReflection, bytecodeProvider);
                providers = replacements.getProviders();
                replacements.maybeInitializeEncoder();
            }
            GraphBuilderConfiguration.Plugins plugins;
            try (InitTimer rt = timer("create GraphBuilderPhase plugins")) {
                plugins = createGraphBuilderPlugins(graalRuntime, compilerConfiguration, config, target, constantReflection, foreignCalls, metaAccess, snippetReflection, replacements, wordTypes,
                                options);
                replacements.setGraphBuilderPlugins(plugins);
            }
            try (InitTimer rt = timer("create Suites provider")) {
                HotSpotSuitesProvider suites = createSuites(config, graalRuntime, compilerConfiguration, plugins, registers, replacements, options);
                providers.setSuites(suites);
            }
            assert replacements == replacements.getProviders().getReplacements();
            assert providers.getGraphBuilderPlugins() == plugins;
        }
        try (InitTimer rt = timer("instantiate backend")) {
            return createBackend(config, graalRuntime, providers);
        }
    }

    protected abstract HotSpotBackend createBackend(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider graalRuntime, HotSpotProviders providers);

    protected abstract Value[] createNativeABICallerSaveRegisters(GraalHotSpotVMConfig config, RegisterConfig registerConfig);

    protected abstract GraphBuilderConfiguration.Plugins createGraphBuilderPlugins(HotSpotGraalRuntimeProvider graalRuntime, CompilerConfiguration compilerConfiguration, GraalHotSpotVMConfig config,
                    TargetDescription target, HotSpotConstantReflectionProvider constantReflection, HotSpotHostForeignCallsProvider foreignCalls, MetaAccessProvider metaAccess,
                    HotSpotSnippetReflectionProvider snippetReflection, HotSpotReplacementsImpl replacements, HotSpotWordTypes wordTypes, OptionValues options);

    protected abstract HotSpotSuitesProvider createSuites(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, CompilerConfiguration compilerConfiguration,
                    GraphBuilderConfiguration.Plugins plugins,
                    HotSpotRegistersProvider registers, HotSpotReplacementsImpl replacements, OptionValues options);

    protected abstract HotSpotRegistersProvider createRegisters();

    protected abstract HotSpotLoweringProvider createLowerer(HotSpotGraalRuntimeProvider graalRuntime, MetaAccessProvider metaAccess, HotSpotHostForeignCallsProvider foreignCalls,
                    HotSpotRegistersProvider registers, HotSpotConstantReflectionProvider constantReflection, HotSpotPlatformConfigurationProvider platformConfig,
                    HotSpotMetaAccessExtensionProvider metaAccessExtensionProvider, TargetDescription target);

    protected abstract HotSpotHostForeignCallsProvider createForeignCalls(HotSpotJVMCIRuntime jvmciRuntime, HotSpotGraalRuntimeProvider graalRuntime, MetaAccessProvider metaAccess,
                    HotSpotCodeCacheProvider codeCache, HotSpotWordTypes wordTypes, Value[] nativeABICallerSaveRegisters);

}
