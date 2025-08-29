/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.bytecode.BytecodeProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotGraalConstantFieldProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotLoweringProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotMetaAccessExtensionProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotPlatformConfigurationProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.meta.HotSpotRegistersProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotSnippetReflectionProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotStampProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotSuitesProvider;
import jdk.graal.compiler.hotspot.word.HotSpotWordTypes;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.gc.BarrierSet;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.memory.FixedAccessNode;
import jdk.graal.compiler.nodes.spi.IdentityHashCodeProvider;
import jdk.graal.compiler.nodes.spi.LoopsDataProvider;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.tiers.CompilerConfiguration;
import jdk.graal.compiler.replacements.classfile.ClassfileBytecodeProvider;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.runtime.JVMCIBackend;

/**
 * A backend factory that creates providers via a delegate factory and decorates them.
 */
public class HotSpotDecoratedBackendFactory extends HotSpotBackendFactory {
    /**
     * The delegate backend factory.
     */
    private final HotSpotBackendFactory delegate;

    /**
     * The decorators to apply to the providers created by the delegate factory.
     */
    private final HotSpotBackendFactoryDecorators decorators;

    /**
     * Constructs a new instance of a decorated backend factory.
     *
     * @param delegate the delegate backend factory
     * @param decorators the decorators to apply to the providers created by the delegate factory
     */
    public HotSpotDecoratedBackendFactory(HotSpotBackendFactory delegate, HotSpotBackendFactoryDecorators decorators) {
        this.delegate = delegate;
        this.decorators = decorators;
    }

    @Override
    protected void afterJVMCIProvidersCreated() {
        decorators.afterJVMCIProvidersCreated();
        delegate.afterJVMCIProvidersCreated();
    }

    @Override
    protected HotSpotGraalConstantFieldProvider createConstantFieldProvider(GraalHotSpotVMConfig config, MetaAccessProvider metaAccess) {
        return delegate.createConstantFieldProvider(config, metaAccess);
    }

    @Override
    protected HotSpotWordTypes createWordTypes(MetaAccessProvider metaAccess, TargetDescription target) {
        return delegate.createWordTypes(metaAccess, target);
    }

    @Override
    protected HotSpotStampProvider createStampProvider() {
        return delegate.createStampProvider();
    }

    @Override
    protected HotSpotPlatformConfigurationProvider createConfigInfoProvider(GraalHotSpotVMConfig config, BarrierSet barrierSet) {
        return delegate.createConfigInfoProvider(config, barrierSet);
    }

    @Override
    protected HotSpotReplacementsImpl createReplacements(TargetDescription target, HotSpotProviders p, BytecodeProvider bytecodeProvider) {
        return delegate.createReplacements(target, p, bytecodeProvider);
    }

    @Override
    protected ClassfileBytecodeProvider createBytecodeProvider(MetaAccessProvider metaAccess, HotSpotSnippetReflectionProvider snippetReflection) {
        return delegate.createBytecodeProvider(metaAccess, snippetReflection);
    }

    @Override
    protected HotSpotSnippetReflectionProvider createSnippetReflection(HotSpotGraalRuntimeProvider runtime, HotSpotConstantReflectionProvider constantReflection, HotSpotWordTypes wordTypes) {
        return delegate.createSnippetReflection(runtime, constantReflection, wordTypes);
    }

    @Override
    protected IdentityHashCodeProvider createIdentityHashCodeProvider() {
        return delegate.createIdentityHashCodeProvider();
    }

    @Override
    protected boolean isWriteToNewObject(FixedAccessNode node) {
        return delegate.isWriteToNewObject(node);
    }

    @Override
    protected boolean isWriteToNewObject(FixedWithNextNode node, ValueNode base) {
        return delegate.isWriteToNewObject(node, base);
    }

    @Override
    protected LoopsDataProvider createLoopsDataProvider() {
        return delegate.createLoopsDataProvider();
    }

    @Override
    protected MetaAccessProvider createMetaAccessProvider(JVMCIBackend jvmci) {
        return decorators.decorateMetaAccessProvider(jvmci.getMetaAccess());
    }

    @Override
    protected HotSpotConstantReflectionProvider createConstantReflectionProvider(JVMCIBackend jvmci) {
        return decorators.decorateConstantReflectionProvider(delegate.createConstantReflectionProvider(jvmci));
    }

    @Override
    protected HotSpotCodeCacheProvider createCodeCacheProvider(JVMCIBackend jvmci) {
        return decorators.decorateCodeCacheProvider(delegate.createCodeCacheProvider(jvmci));
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    protected HotSpotBackend createBackend(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider graalRuntime, HotSpotProviders providers) {
        return delegate.createBackend(config, graalRuntime, providers);
    }

    @Override
    protected Value[] createNativeABICallerSaveRegisters(GraalHotSpotVMConfig config, RegisterConfig registerConfig) {
        return delegate.createNativeABICallerSaveRegisters(config, registerConfig);
    }

    @Override
    protected GraphBuilderConfiguration.Plugins createGraphBuilderPlugins(HotSpotGraalRuntimeProvider graalRuntime,
                    CompilerConfiguration compilerConfiguration, GraalHotSpotVMConfig config, TargetDescription target, HotSpotConstantReflectionProvider constantReflection,
                    HotSpotHostForeignCallsProvider foreignCalls, MetaAccessProvider metaAccess, HotSpotSnippetReflectionProvider snippetReflection, HotSpotReplacementsImpl replacements,
                    HotSpotWordTypes wordTypes, OptionValues options, BarrierSet barrierSet) {
        return delegate.createGraphBuilderPlugins(graalRuntime, compilerConfiguration, config, target, constantReflection,
                        foreignCalls, metaAccess, snippetReflection, replacements, wordTypes, options, barrierSet);
    }

    @Override
    protected HotSpotSuitesProvider createSuites(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, CompilerConfiguration compilerConfiguration,
                    GraphBuilderConfiguration.Plugins plugins, HotSpotRegistersProvider registers, OptionValues options) {
        return delegate.createSuites(config, runtime, compilerConfiguration, plugins, registers, options);
    }

    @Override
    protected HotSpotRegistersProvider createRegisters() {
        return delegate.createRegisters();
    }

    @Override
    protected HotSpotLoweringProvider createLowerer(HotSpotGraalRuntimeProvider graalRuntime, MetaAccessProvider metaAccess, HotSpotHostForeignCallsProvider foreignCalls,
                    HotSpotRegistersProvider registers, HotSpotConstantReflectionProvider constantReflection, HotSpotPlatformConfigurationProvider platformConfig,
                    HotSpotMetaAccessExtensionProvider metaAccessExtensionProvider, TargetDescription target) {
        return delegate.createLowerer(graalRuntime, metaAccess, foreignCalls, registers, constantReflection, platformConfig,
                        metaAccessExtensionProvider, target);
    }

    @Override
    protected HotSpotHostForeignCallsProvider createForeignCalls(HotSpotJVMCIRuntime jvmciRuntime, HotSpotGraalRuntimeProvider graalRuntime, MetaAccessProvider metaAccess,
                    HotSpotCodeCacheProvider codeCache, HotSpotWordTypes wordTypes, Value[] nativeABICallerSaveRegisters) {
        return decorators.decorateForeignCallsProvider(delegate.createForeignCalls(jvmciRuntime, graalRuntime, metaAccess, codeCache, wordTypes, nativeABICallerSaveRegisters));
    }

    @Override
    public String getArchitecture() {
        return delegate.getArchitecture();
    }
}
