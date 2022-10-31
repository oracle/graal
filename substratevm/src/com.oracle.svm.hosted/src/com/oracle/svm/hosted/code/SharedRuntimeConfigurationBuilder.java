/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import java.util.EnumMap;
import java.util.function.Function;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;
import org.graalvm.compiler.nodes.spi.LoopsDataProvider;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.PlatformConfigurationProvider;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.nodes.spi.StampProvider;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.word.WordTypes;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.GraalConfiguration;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstratePlatformConfigurationProvider;
import com.oracle.svm.core.graal.code.SubstrateRegisterConfigFactory;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.meta.SubstrateLoweringProvider;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig.ConfigKind;
import com.oracle.svm.core.graal.meta.SubstrateSnippetReflectionProvider;
import com.oracle.svm.core.graal.meta.SubstrateStampProvider;
import com.oracle.svm.core.graal.word.SubstrateWordTypes;
import com.oracle.svm.hosted.HostedConfiguration;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.meta.ConstantReflectionProvider;

public abstract class SharedRuntimeConfigurationBuilder {

    protected final OptionValues options;
    protected final SVMHost hostVM;
    protected final UniverseMetaAccess metaAccess;
    protected RuntimeConfiguration runtimeConfig;
    protected WordTypes wordTypes;
    protected final Function<Providers, SubstrateBackend> backendProvider;
    protected final NativeLibraries nativeLibraries;
    protected final ClassInitializationSupport classInitializationSupport;
    protected final LoopsDataProvider originalLoopsDataProvider;
    protected final SubstratePlatformConfigurationProvider platformConfig;

    public SharedRuntimeConfigurationBuilder(OptionValues options, SVMHost hostVM, UniverseMetaAccess metaAccess, Function<Providers, SubstrateBackend> backendProvider,
                    NativeLibraries nativeLibraries, ClassInitializationSupport classInitializationSupport, LoopsDataProvider originalLoopsDataProvider,
                    SubstratePlatformConfigurationProvider platformConfig) {
        this.options = options;
        this.hostVM = hostVM;
        this.metaAccess = metaAccess;
        this.backendProvider = backendProvider;
        this.nativeLibraries = nativeLibraries;
        this.classInitializationSupport = classInitializationSupport;
        this.originalLoopsDataProvider = originalLoopsDataProvider;
        this.platformConfig = platformConfig;
    }

    public NativeLibraries getNativeLibraries() {
        return nativeLibraries;
    }

    public SharedRuntimeConfigurationBuilder build() {
        EnumMap<ConfigKind, RegisterConfig> registerConfigs = new EnumMap<>(ConfigKind.class);
        for (ConfigKind config : ConfigKind.values()) {
            registerConfigs.put(config, ImageSingletons.lookup(SubstrateRegisterConfigFactory.class).newRegisterFactory(config, metaAccess, ConfigurationValues.getTarget(),
                            SubstrateOptions.PreserveFramePointer.getValue()));
        }

        wordTypes = new SubstrateWordTypes(metaAccess, FrameAccess.getWordKind());
        Providers p = createProviders(null, null, null, null, null, null, null, null, null, null, null);
        StampProvider stampProvider = createStampProvider(p);
        p = createProviders(null, null, null, null, null, null, stampProvider, null, null, null, null);
        ConstantReflectionProvider constantReflection = createConstantReflectionProvider(p);
        p = createProviders(null, constantReflection, null, null, null, null, stampProvider, null, null, null, null);
        ConstantFieldProvider constantFieldProvider = createConstantFieldProvider(p);
        SnippetReflectionProvider snippetReflection = createSnippetReflectionProvider();
        ForeignCallsProvider foreignCalls = createForeignCallsProvider(registerConfigs.get(ConfigKind.NORMAL));
        p = createProviders(null, constantReflection, constantFieldProvider, foreignCalls, null, null, stampProvider, snippetReflection, null, null, null);
        MetaAccessExtensionProvider metaAccessExtensionProvider = HostedConfiguration.instance().createCompilationMetaAccessExtensionProvider(metaAccess);
        p = createProviders(null, constantReflection, constantFieldProvider, foreignCalls, null, null, stampProvider, snippetReflection, platformConfig, metaAccessExtensionProvider, null);
        LoweringProvider lowerer = createLoweringProvider(p);
        p = createProviders(null, constantReflection, constantFieldProvider, foreignCalls, lowerer, null, stampProvider, snippetReflection, platformConfig, metaAccessExtensionProvider, null);
        Replacements replacements = createReplacements(p, snippetReflection);
        p = createProviders(null, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, stampProvider, snippetReflection, platformConfig, metaAccessExtensionProvider, null);
        LoopsDataProvider loopsDataProvider = originalLoopsDataProvider;
        p = createProviders(null, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, stampProvider, snippetReflection, platformConfig, metaAccessExtensionProvider,
                        loopsDataProvider);

        EnumMap<ConfigKind, SubstrateBackend> backends = new EnumMap<>(ConfigKind.class);
        for (ConfigKind config : ConfigKind.values()) {
            CodeCacheProvider codeCacheProvider = createCodeCacheProvider(registerConfigs.get(config));

            Providers newProviders = createProviders(codeCacheProvider, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, stampProvider,
                            snippetReflection, platformConfig, metaAccessExtensionProvider, loopsDataProvider);
            backends.put(config, GraalConfiguration.runtimeInstance().createBackend(newProviders));
        }

        runtimeConfig = new RuntimeConfiguration(p, snippetReflection, backends, wordTypes);
        return this;
    }

    public WordTypes getWordTypes() {
        return wordTypes;
    }

    protected Providers createProviders(CodeCacheProvider codeCache, ConstantReflectionProvider constantReflection, ConstantFieldProvider constantFieldProvider, ForeignCallsProvider foreignCalls,
                    LoweringProvider lowerer, Replacements replacements, StampProvider stampProvider, @SuppressWarnings("unused") SnippetReflectionProvider snippetReflection,
                    PlatformConfigurationProvider platformConfigurationProvider, MetaAccessExtensionProvider metaAccessExtensionProvider, LoopsDataProvider loopsDataProvider) {
        return new Providers(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, stampProvider, platformConfigurationProvider,
                        metaAccessExtensionProvider, snippetReflection, wordTypes, loopsDataProvider);
    }

    public RuntimeConfiguration getRuntimeConfig() {
        return runtimeConfig;
    }

    protected StampProvider createStampProvider(Providers p) {
        return new SubstrateStampProvider(p.getMetaAccess());
    }

    protected abstract ConstantReflectionProvider createConstantReflectionProvider(Providers p);

    protected abstract ConstantFieldProvider createConstantFieldProvider(Providers p);

    protected SnippetReflectionProvider createSnippetReflectionProvider() {
        return new SubstrateSnippetReflectionProvider(getWordTypes());
    }

    protected ForeignCallsProvider createForeignCallsProvider(RegisterConfig registerConfig) {
        return new SubstrateForeignCallsProvider(metaAccess, registerConfig);
    }

    protected LoweringProvider createLoweringProvider(Providers p) {
        return SubstrateLoweringProvider.createForRuntime(p.getMetaAccess(), p.getForeignCalls(), p.getPlatformConfigurationProvider(), p.getMetaAccessExtensionProvider());
    }

    protected abstract Replacements createReplacements(Providers p, SnippetReflectionProvider snippetReflection);

    protected abstract CodeCacheProvider createCodeCacheProvider(RegisterConfig registerConfig);
}
