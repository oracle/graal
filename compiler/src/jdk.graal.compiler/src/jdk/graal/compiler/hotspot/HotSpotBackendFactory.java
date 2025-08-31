/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot;

import static jdk.graal.compiler.core.common.NativeImageSupport.inRuntimeCode;
import static jdk.vm.ci.common.InitTimer.timer;

import jdk.graal.compiler.bytecode.BytecodeProvider;
import jdk.graal.compiler.core.ArchitectureSpecific;
import jdk.graal.compiler.core.common.LibGraalSupport;
import jdk.graal.compiler.core.common.spi.ConstantFieldProvider;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.hotspot.meta.HotSpotGraalConstantFieldProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotIdentityHashCodeProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotLoweringProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotMetaAccessExtensionProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotPlatformConfigurationProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.meta.HotSpotRegistersProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotSnippetReflectionProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotStampProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotSuitesProvider;
import jdk.graal.compiler.hotspot.nodes.HotSpotCompressionNode;
import jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import jdk.graal.compiler.hotspot.word.HotSpotWordTypes;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.ArrayRangeWrite;
import jdk.graal.compiler.nodes.gc.BarrierSet;
import jdk.graal.compiler.nodes.gc.CardTableBarrierSet;
import jdk.graal.compiler.nodes.gc.G1BarrierSet;
import jdk.graal.compiler.nodes.gc.NoBarrierSet;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.java.AbstractNewObjectNode;
import jdk.graal.compiler.nodes.loop.LoopsDataProviderImpl;
import jdk.graal.compiler.nodes.memory.FixedAccessNode;
import jdk.graal.compiler.nodes.spi.IdentityHashCodeProvider;
import jdk.graal.compiler.nodes.spi.LoopsDataProvider;
import jdk.graal.compiler.nodes.spi.Replacements;
import jdk.graal.compiler.nodes.type.NarrowOopStamp;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.tiers.CompilerConfiguration;
import jdk.graal.compiler.replacements.classfile.ClassfileBytecodeProvider;
import jdk.graal.compiler.serviceprovider.LibGraalService;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.runtime.JVMCIBackend;

@LibGraalService
public abstract class HotSpotBackendFactory implements ArchitectureSpecific {
    /**
     * Creates a meta access provider based on a JVMCI backend.
     *
     * @param jvmci the JVMCI backend
     * @return a meta access provider
     */
    protected MetaAccessProvider createMetaAccessProvider(JVMCIBackend jvmci) {
        return new HotSpotSnippetMetaAccessProvider(jvmci.getMetaAccess());
    }

    /**
     * Creates a constant reflection provider based on a JVMCI backend.
     *
     * @param jvmci the JVMCI backend
     * @return a constant reflection provider
     */
    protected HotSpotConstantReflectionProvider createConstantReflectionProvider(JVMCIBackend jvmci) {
        return (HotSpotConstantReflectionProvider) jvmci.getConstantReflection();
    }

    /**
     * Creates a code cache provider based on a JVMCI backend.
     *
     * @param jvmci the JVMCI backend
     * @return a code cache provider.
     */
    protected HotSpotCodeCacheProvider createCodeCacheProvider(JVMCIBackend jvmci) {
        return (HotSpotCodeCacheProvider) jvmci.getCodeCache();
    }

    protected HotSpotGraalConstantFieldProvider createConstantFieldProvider(GraalHotSpotVMConfig config, MetaAccessProvider metaAccess) {
        return new HotSpotGraalConstantFieldProvider(config, metaAccess);
    }

    protected HotSpotWordTypes createWordTypes(MetaAccessProvider metaAccess, TargetDescription target) {
        return new HotSpotWordTypes(HotSpotReplacementsImpl.noticeTypes(metaAccess), target.wordJavaKind);
    }

    protected HotSpotStampProvider createStampProvider() {
        return new HotSpotStampProvider();
    }

    protected HotSpotPlatformConfigurationProvider createConfigInfoProvider(GraalHotSpotVMConfig config, BarrierSet barrierSet) {
        return new HotSpotPlatformConfigurationProvider(config, barrierSet);
    }

    protected HotSpotMetaAccessExtensionProvider createMetaAccessExtensionProvider(ConstantReflectionProvider constantReflection) {
        return new HotSpotMetaAccessExtensionProvider(constantReflection);
    }

    protected HotSpotReplacementsImpl createReplacements(TargetDescription target, HotSpotProviders p, BytecodeProvider bytecodeProvider) {
        return new HotSpotReplacementsImpl(p, bytecodeProvider, target);
    }

    protected ClassfileBytecodeProvider createBytecodeProvider(MetaAccessProvider metaAccess, HotSpotSnippetReflectionProvider snippetReflection) {
        return inRuntimeCode() ? null : new ClassfileBytecodeProvider(metaAccess, snippetReflection);
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

    protected LoopsDataProvider createLoopsDataProvider() {
        return new LoopsDataProviderImpl();
    }

    /**
     * Hook method called after all JVMCI providers have been created (meta access, code cache,
     * constant reflection).
     */
    protected void afterJVMCIProvidersCreated() {
    }

    @SuppressWarnings("try")
    public final HotSpotBackend createBackend(HotSpotGraalRuntimeProvider graalRuntime, CompilerConfiguration compilerConfiguration, HotSpotJVMCIRuntime jvmciRuntime, HotSpotBackend host) {
        assert host == null;

        OptionValues options = graalRuntime.getOptions();
        JVMCIBackend jvmci = jvmciRuntime.getHostJVMCIBackend();
        GraalHotSpotVMConfig config = graalRuntime.getVMConfig();
        MetaAccessProvider metaAccess = createMetaAccessProvider(jvmci);
        HotSpotCodeCacheProvider codeCache = createCodeCacheProvider(jvmci);
        HotSpotConstantReflectionProvider constantReflection = createConstantReflectionProvider(jvmci);
        afterJVMCIProvidersCreated();
        TargetDescription target = codeCache.getTarget();
        SnippetSignature.initPrimitiveKindCache(metaAccess);
        ConstantFieldProvider constantFieldProvider = new HotSpotGraalConstantFieldProvider(config, metaAccess);
        HotSpotProviders providers;
        HotSpotReplacementsImpl replacements;
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
            BarrierSet barrierSet = createBarrierSet(config, metaAccess);
            HotSpotPlatformConfigurationProvider platformConfigurationProvider;
            try (InitTimer rt = timer("create platform configuration provider")) {
                platformConfigurationProvider = createConfigInfoProvider(config, barrierSet);
            }
            HotSpotMetaAccessExtensionProvider metaAccessExtensionProvider;
            try (InitTimer rt = timer("create MetaAccessExtensionProvider")) {
                metaAccessExtensionProvider = createMetaAccessExtensionProvider(constantReflection);
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
            IdentityHashCodeProvider identityHashCodeProvider;
            try (InitTimer rt = timer("create IdentityHashCode provider")) {
                identityHashCodeProvider = createIdentityHashCodeProvider();
            }
            providers = new HotSpotProviders(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, null, null, registers,
                            snippetReflection, wordTypes, stampProvider, platformConfigurationProvider, metaAccessExtensionProvider, loopsDataProvider, config, identityHashCodeProvider,
                            graalRuntime.getReplayCompilationSupport());

            try (InitTimer rt = timer("create Replacements provider")) {
                replacements = createReplacements(target, providers, bytecodeProvider);
                providers = replacements.getProviders();
                if (!LibGraalSupport.inLibGraalRuntime()) {
                    replacements.maybeInitializeEncoder();
                }
            }
            GraphBuilderConfiguration.Plugins plugins;
            try (InitTimer rt = timer("create GraphBuilderPhase plugins")) {
                plugins = createGraphBuilderPlugins(graalRuntime, compilerConfiguration, config, target, constantReflection, foreignCalls, metaAccess, snippetReflection, replacements, wordTypes,
                                options, barrierSet);
                replacements.setGraphBuilderPlugins(plugins);
            }
            try (InitTimer rt = timer("create Suites provider")) {
                HotSpotSuitesProvider suites = createSuites(config, graalRuntime, compilerConfiguration, plugins, registers, options);
                providers.setSuites(suites);
            }
            Replacements replacements2 = replacements.getProviders().getReplacements();
            assert replacements == replacements2 : Assertions.errorMessageContext("replacements", replacements, "replacements2", replacements2);
            Plugins plugins2 = providers.getGraphBuilderPlugins();
            assert plugins2 == plugins : Assertions.errorMessageContext("plugins", plugins, "plugins2", plugins2);
        }
        try (InitTimer rt = timer("instantiate backend")) {
            return createBackend(config, graalRuntime, providers);
        }
    }

    protected IdentityHashCodeProvider createIdentityHashCodeProvider() {
        return new HotSpotIdentityHashCodeProvider();
    }

    protected abstract HotSpotBackend createBackend(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider graalRuntime, HotSpotProviders providers);

    protected abstract Value[] createNativeABICallerSaveRegisters(GraalHotSpotVMConfig config, RegisterConfig registerConfig);

    protected abstract GraphBuilderConfiguration.Plugins createGraphBuilderPlugins(HotSpotGraalRuntimeProvider graalRuntime, CompilerConfiguration compilerConfiguration, GraalHotSpotVMConfig config,
                    TargetDescription target, HotSpotConstantReflectionProvider constantReflection, HotSpotHostForeignCallsProvider foreignCalls, MetaAccessProvider metaAccess,
                    HotSpotSnippetReflectionProvider snippetReflection, HotSpotReplacementsImpl replacements, HotSpotWordTypes wordTypes, OptionValues options, BarrierSet barrierSet);

    protected abstract HotSpotSuitesProvider createSuites(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, CompilerConfiguration compilerConfiguration,
                    Plugins plugins, HotSpotRegistersProvider registers, OptionValues options);

    protected abstract HotSpotRegistersProvider createRegisters();

    protected abstract HotSpotLoweringProvider createLowerer(HotSpotGraalRuntimeProvider graalRuntime, MetaAccessProvider metaAccess, HotSpotHostForeignCallsProvider foreignCalls,
                    HotSpotRegistersProvider registers, HotSpotConstantReflectionProvider constantReflection, HotSpotPlatformConfigurationProvider platformConfig,
                    HotSpotMetaAccessExtensionProvider metaAccessExtensionProvider, TargetDescription target);

    protected abstract HotSpotHostForeignCallsProvider createForeignCalls(HotSpotJVMCIRuntime jvmciRuntime, HotSpotGraalRuntimeProvider graalRuntime, MetaAccessProvider metaAccess,
                    HotSpotCodeCacheProvider codeCache, HotSpotWordTypes wordTypes, Value[] nativeABICallerSaveRegisters);

    private BarrierSet createBarrierSet(GraalHotSpotVMConfig config, MetaAccessProvider metaAccess) {
        boolean useDeferredInitBarriers = config.useDeferredInitBarriers;
        ResolvedJavaType objectArrayType = metaAccess.lookupJavaType(Object[].class);
        ResolvedJavaField referentField = HotSpotReplacementsUtil.referentField(metaAccess);
        if (config.gc == HotSpotGraalRuntime.HotSpotGC.Z) {
            return new HotSpotZBarrierSet(objectArrayType, referentField);
        } else if (config.gc == HotSpotGraalRuntime.HotSpotGC.Epsilon) {
            return new NoBarrierSet();
        } else if (config.useG1GC()) {
            return new G1BarrierSet(objectArrayType, referentField) {
                @Override
                protected boolean writeRequiresPostBarrier(FixedAccessNode node, ValueNode writtenValue) {
                    if (!super.writeRequiresPostBarrier(node, writtenValue)) {
                        return false;
                    }
                    return !useDeferredInitBarriers || !isWriteToNewObject(node);
                }

                @Override
                protected boolean arrayRangeWriteRequiresPostBarrier(ArrayRangeWrite write) {
                    if (!super.arrayRangeWriteRequiresPostBarrier(write)) {
                        return false;
                    }
                    return !useDeferredInitBarriers || !isWriteToNewObject(write.asFixedWithNextNode(), write.getAddress().getBase());
                }

                @Override
                protected ValueNode maybeUncompressExpectedValue(ValueNode value) {
                    if (value != null && (value.stamp(NodeView.DEFAULT) instanceof NarrowOopStamp)) {
                        return HotSpotCompressionNode.uncompress(value.graph(), value, config.getOopEncoding());
                    }
                    return value;
                }
            };
        } else {
            return new CardTableBarrierSet(objectArrayType) {
                @Override
                protected boolean writeRequiresBarrier(FixedAccessNode node, ValueNode writtenValue) {
                    if (!super.writeRequiresBarrier(node, writtenValue)) {
                        return false;
                    }
                    return !useDeferredInitBarriers || !isWriteToNewObject(node);
                }

                @Override
                protected boolean arrayRangeWriteRequiresBarrier(ArrayRangeWrite write) {
                    if (!super.arrayRangeWriteRequiresBarrier(write)) {
                        return false;
                    }
                    return !useDeferredInitBarriers || !isWriteToNewObject(write.asFixedWithNextNode(), write.getAddress().getBase());
                }
            };
        }
    }

    /**
     * For initializing writes, the last allocation executed by the JVM is guaranteed to be
     * automatically card marked so it's safe to skip the card mark in the emitted code.
     */
    protected boolean isWriteToNewObject(FixedAccessNode node) {
        if (!node.getLocationIdentity().isInit()) {
            return false;
        }
        // This is only allowed for the last allocation in sequence
        return isWriteToNewObject(node, node.getAddress().getBase());
    }

    protected boolean isWriteToNewObject(FixedWithNextNode node, ValueNode base) {
        if (base instanceof AbstractNewObjectNode) {
            Node pred = node.predecessor();
            while (pred != null) {
                if (pred == base) {
                    node.getDebug().log(DebugContext.INFO_LEVEL, "Deferred barrier for %s with base %s", node, base);
                    return true;
                }
                if (pred instanceof AbstractNewObjectNode) {
                    node.getDebug().log(DebugContext.INFO_LEVEL, "Disallowed deferred barrier for %s because %s was last allocation instead of %s", node, pred, base);
                    return false;
                }
                pred = pred.predecessor();
            }
        }
        node.getDebug().log(DebugContext.INFO_LEVEL, "Unable to find allocation for deferred barrier for %s with base %s", node, base);
        return false;
    }
}
