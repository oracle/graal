/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.util.EnumMap;
import java.util.function.Function;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.nodes.spi.StampProvider;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.word.WordTypes;

import com.oracle.svm.core.amd64.FrameAccess;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.GraalConfiguration;
import com.oracle.svm.core.graal.code.amd64.SubstrateAMD64RegisterConfig;
import com.oracle.svm.core.graal.code.amd64.SubstrateAMD64RegisterConfig.ConfigKind;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateCodeCacheProvider;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.meta.SubstrateLoweringProvider;
import com.oracle.svm.core.graal.meta.SubstrateSnippetReflectionProvider;
import com.oracle.svm.core.graal.meta.SubstrateStampProvider;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.config.HybridLayout;
import com.oracle.svm.hosted.meta.HostedMetaAccess;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;

public abstract class SharedRuntimeConfigurationBuilder {

    protected final OptionValues options;
    protected final SVMHost hostVM;
    protected final MetaAccessProvider metaAccess;
    protected RuntimeConfiguration runtimeConfig;
    protected WordTypes wordTypes;
    protected Function<Providers, Backend> backendProvider;

    public SharedRuntimeConfigurationBuilder(OptionValues options, SVMHost hostVM, MetaAccessProvider metaAccess, Function<Providers, Backend> backendProvider) {
        this.options = options;
        this.hostVM = hostVM;
        this.metaAccess = metaAccess;
        this.backendProvider = backendProvider;
    }

    public SharedRuntimeConfigurationBuilder build() {
        wordTypes = new WordTypes(metaAccess, FrameAccess.getWordKind());
        Providers p = createProviders(null, null, null, null, null, null, null, null);
        StampProvider stampProvider = createStampProvider(p);
        p = createProviders(null, null, null, null, null, null, stampProvider, null);
        ConstantReflectionProvider constantReflection = createConstantReflectionProvider(p);
        p = createProviders(null, constantReflection, null, null, null, null, stampProvider, null);
        ConstantFieldProvider constantFieldProvider = createConstantFieldProvider(p);
        SnippetReflectionProvider snippetReflection = createSnippetReflectionProvider();
        ForeignCallsProvider foreignCalls = createForeignCallsProvider();
        p = createProviders(null, constantReflection, constantFieldProvider, foreignCalls, null, null, stampProvider, snippetReflection);
        LoweringProvider lowerer = createLoweringProvider(p);
        p = createProviders(null, constantReflection, constantFieldProvider, foreignCalls, lowerer, null, stampProvider, snippetReflection);
        Replacements replacements = createReplacements(p, snippetReflection);
        p = createProviders(null, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, stampProvider, snippetReflection);

        EnumMap<ConfigKind, Backend> backends = new EnumMap<>(ConfigKind.class);
        for (ConfigKind config : ConfigKind.values()) {
            RegisterConfig registerConfig = new SubstrateAMD64RegisterConfig(config, metaAccess, ConfigurationValues.getTarget());
            CodeCacheProvider codeCacheProvider = createCodeCacheProvider(registerConfig);

            Providers newProviders = createProviders(codeCacheProvider, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, stampProvider,
                            snippetReflection);
            backends.put(config, GraalConfiguration.instance().createBackend(newProviders));
        }

        runtimeConfig = new RuntimeConfiguration(p, snippetReflection, backends, wordTypes);
        return this;
    }

    public WordTypes getWordTypes() {
        return wordTypes;
    }

    protected Providers createProviders(CodeCacheProvider codeCache, ConstantReflectionProvider constantReflection, ConstantFieldProvider constantFieldProvider, ForeignCallsProvider foreignCalls,
                    LoweringProvider lowerer, Replacements replacements, StampProvider stampProvider, @SuppressWarnings("unused") SnippetReflectionProvider snippetReflection) {
        return new Providers(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, stampProvider);
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
        return new SubstrateSnippetReflectionProvider();
    }

    protected ForeignCallsProvider createForeignCallsProvider() {
        return new SubstrateForeignCallsProvider();
    }

    protected LoweringProvider createLoweringProvider(Providers p) {
        return SubstrateLoweringProvider.create(p.getMetaAccess(), p.getForeignCalls());
    }

    protected abstract Replacements createReplacements(Providers p, SnippetReflectionProvider snippetReflection);

    protected SubstrateCodeCacheProvider createCodeCacheProvider(RegisterConfig registerConfig) {
        return new SubstrateCodeCacheProvider(ConfigurationValues.getTarget(), registerConfig);
    }

    public void updateLazyState(HostedMetaAccess hMetaAccess) {
        HybridLayout<DynamicHub> hubLayout = new HybridLayout<>(DynamicHub.class, ConfigurationValues.getObjectLayout(), hMetaAccess);
        int vtableBaseOffset = hubLayout.getArrayBaseOffset();
        int vtableEntrySize = ConfigurationValues.getObjectLayout().sizeInBytes(hubLayout.getArrayElementStorageKind());
        int instanceOfBitsOffset = hubLayout.getBitFieldOffset();

        int componentHubOffset;
        try {
            componentHubOffset = hMetaAccess.lookupJavaField(DynamicHub.class.getDeclaredField("componentHub")).getLocation();
        } catch (NoSuchFieldException ex) {
            throw shouldNotReachHere(ex);
        }

        runtimeConfig.setLazyState(vtableBaseOffset, vtableEntrySize, instanceOfBitsOffset, componentHubOffset);
    }
}
