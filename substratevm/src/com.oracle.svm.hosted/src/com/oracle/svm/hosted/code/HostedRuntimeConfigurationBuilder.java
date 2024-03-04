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

import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.code.SubstrateBackendFactory;
import com.oracle.svm.core.graal.code.SubstratePlatformConfigurationProvider;
import com.oracle.svm.core.graal.meta.SubstrateLoweringProvider;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.meta.HostedConstantFieldProvider;
import com.oracle.svm.hosted.meta.HostedConstantReflectionProvider;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedUniverse;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.bytecode.BytecodeProvider;
import jdk.graal.compiler.bytecode.ResolvedJavaMethodBytecodeProvider;
import jdk.graal.compiler.core.common.spi.ConstantFieldProvider;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.nodes.spi.IdentityHashCodeProvider;
import jdk.graal.compiler.nodes.spi.LoopsDataProvider;
import jdk.graal.compiler.nodes.spi.LoweringProvider;
import jdk.graal.compiler.nodes.spi.PlatformConfigurationProvider;
import jdk.graal.compiler.nodes.spi.Replacements;
import jdk.graal.compiler.nodes.spi.StampProvider;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.meta.ConstantReflectionProvider;

public class HostedRuntimeConfigurationBuilder extends SharedRuntimeConfigurationBuilder {

    private final HostedUniverse universe;
    private final HostedProviders analysisProviders;

    public HostedRuntimeConfigurationBuilder(OptionValues options, SVMHost hostVM, HostedUniverse universe, HostedMetaAccess metaAccess,
                    HostedProviders analysisProviders, ClassInitializationSupport classInitializationSupport, LoopsDataProvider originalLoopsDataProvider,
                    SubstratePlatformConfigurationProvider platformConfig, SnippetReflectionProvider snippetReflection) {
        super(options, hostVM, metaAccess, SubstrateBackendFactory.get()::newBackend, classInitializationSupport, originalLoopsDataProvider, platformConfig, snippetReflection);
        this.universe = universe;
        this.analysisProviders = analysisProviders;
    }

    @Override
    protected Providers createProviders(CodeCacheProvider codeCache, ConstantReflectionProvider constantReflection, ConstantFieldProvider constantFieldProvider, ForeignCallsProvider foreignCalls,
                    LoweringProvider lowerer, Replacements replacements, StampProvider stampProvider, SnippetReflectionProvider reflectionProvider,
                    PlatformConfigurationProvider platformConfigurationProvider, MetaAccessExtensionProvider metaAccessExtensionProvider, WordTypes wordTypes, LoopsDataProvider loopsDataProvider,
                    IdentityHashCodeProvider identityHashCodeProvider) {
        return new HostedProviders(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, stampProvider, reflectionProvider,
                        wordTypes, platformConfigurationProvider, metaAccessExtensionProvider, loopsDataProvider, identityHashCodeProvider);
    }

    @Override
    protected ConstantReflectionProvider createConstantReflectionProvider() {
        return new HostedConstantReflectionProvider(universe, (HostedMetaAccess) metaAccess);
    }

    @Override
    protected ConstantFieldProvider createConstantFieldProvider() {
        return new HostedConstantFieldProvider(metaAccess, universe.hostVM());
    }

    @Override
    protected LoweringProvider createLoweringProvider(ForeignCallsProvider foreignCalls, MetaAccessExtensionProvider metaAccessExtensionProvider) {
        return SubstrateLoweringProvider.createForHosted(metaAccess, foreignCalls, platformConfig, metaAccessExtensionProvider);
    }

    @Override
    protected Replacements createReplacements(Providers p) {
        BytecodeProvider bytecodeProvider = new ResolvedJavaMethodBytecodeProvider();
        return new HostedReplacements(universe, p, ConfigurationValues.getTarget(), analysisProviders, bytecodeProvider);
    }

    @Override
    protected CodeCacheProvider createCodeCacheProvider(RegisterConfig registerConfig) {
        return new HostedCodeCacheProvider(ConfigurationValues.getTarget(), registerConfig);
    }
}
