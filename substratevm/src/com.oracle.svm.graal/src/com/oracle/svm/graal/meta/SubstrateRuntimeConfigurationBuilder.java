/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.meta;

import java.util.function.Function;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.bytecode.BytecodeProvider;
import jdk.graal.compiler.bytecode.ResolvedJavaMethodBytecodeProvider;
import jdk.graal.compiler.core.common.spi.ConstantFieldProvider;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.LoopsDataProvider;
import jdk.graal.compiler.nodes.spi.LoweringProvider;
import jdk.graal.compiler.nodes.spi.PlatformConfigurationProvider;
import jdk.graal.compiler.nodes.spi.Replacements;
import jdk.graal.compiler.nodes.spi.StampProvider;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.ReplacementsImpl;
import jdk.graal.compiler.word.WordTypes;

import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstratePlatformConfigurationProvider;
import com.oracle.svm.core.graal.meta.SharedCodeCacheProvider;
import com.oracle.svm.core.graal.meta.SubstrateLoweringProvider;
import com.oracle.svm.core.graal.meta.SubstrateReplacements;
import com.oracle.svm.core.graal.meta.SubstrateSnippetReflectionProvider;
import com.oracle.svm.graal.hosted.RuntimeCompilationFeature;
import com.oracle.svm.graal.isolated.IsolateAwareCodeCacheProvider;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.ameta.AnalysisConstantFieldProvider;
import com.oracle.svm.hosted.ameta.AnalysisConstantReflectionProvider;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.code.SharedRuntimeConfigurationBuilder;
import com.oracle.svm.hosted.code.SubstrateGraphMaker;
import com.oracle.svm.hosted.code.SubstrateGraphMakerFactory;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class SubstrateRuntimeConfigurationBuilder extends SharedRuntimeConfigurationBuilder {

    private final AnalysisUniverse aUniverse;

    public SubstrateRuntimeConfigurationBuilder(OptionValues options, SVMHost hostVM, AnalysisUniverse aUniverse, UniverseMetaAccess metaAccess,
                    Function<Providers, SubstrateBackend> backendProvider,
                    ClassInitializationSupport classInitializationSupport, LoopsDataProvider loopsDataProvider, SubstratePlatformConfigurationProvider platformConfig) {
        super(options, hostVM, metaAccess, backendProvider, classInitializationSupport, loopsDataProvider, platformConfig);
        this.aUniverse = aUniverse;
    }

    @Override
    protected Providers createProviders(CodeCacheProvider codeCache, ConstantReflectionProvider constantReflection, ConstantFieldProvider constantFieldProvider, ForeignCallsProvider foreignCalls,
                    LoweringProvider lowerer, Replacements replacements, StampProvider stampProvider, SnippetReflectionProvider snippetReflection,
                    PlatformConfigurationProvider platformConfigurationProvider, MetaAccessExtensionProvider metaAccessExtensionProvider, WordTypes wordTypes, LoopsDataProvider loopsDataProvider) {
        return new Providers(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, stampProvider, platformConfigurationProvider,
                        metaAccessExtensionProvider, snippetReflection, wordTypes, loopsDataProvider);
    }

    @Override
    protected ConstantReflectionProvider createConstantReflectionProvider() {
        return new AnalysisConstantReflectionProvider(aUniverse, metaAccess, classInitializationSupport);
    }

    @Override
    protected ConstantFieldProvider createConstantFieldProvider() {
        return new AnalysisConstantFieldProvider(metaAccess, hostVM);
    }

    @Override
    protected SnippetReflectionProvider createSnippetReflectionProvider(WordTypes wordTypes) {
        return new SubstrateSnippetReflectionProvider(wordTypes);
    }

    @Override
    protected LoweringProvider createLoweringProvider(ForeignCallsProvider foreignCalls, MetaAccessExtensionProvider metaAccessExtensionProvider) {
        return SubstrateLoweringProvider.createForRuntime(metaAccess, foreignCalls, platformConfig, metaAccessExtensionProvider);
    }

    @Override
    protected Replacements createReplacements(Providers p, SnippetReflectionProvider snippetReflection) {
        BytecodeProvider bytecodeProvider = new ResolvedJavaMethodBytecodeProvider();
        WordTypes wordTypes = p.getWordTypes();
        return new SubstrateReplacements(p, snippetReflection, bytecodeProvider, ConfigurationValues.getTarget(), wordTypes, new SubstrateRuntimeGraphMakerFactory(wordTypes));
    }

    @Override
    protected SharedCodeCacheProvider createCodeCacheProvider(RegisterConfig registerConfig) {
        if (SubstrateOptions.supportCompileInIsolates()) {
            return new IsolateAwareCodeCacheProvider(ConfigurationValues.getTarget(), registerConfig);
        }
        return new SubstrateCodeCacheProvider(ConfigurationValues.getTarget(), registerConfig);
    }

    static class SubstrateRuntimeGraphMakerFactory extends SubstrateGraphMakerFactory {

        SubstrateRuntimeGraphMakerFactory(WordTypes wordTypes) {
            super(wordTypes);
        }

        @Override
        public ReplacementsImpl.GraphMaker create(MetaAccessProvider metaAccess, ReplacementsImpl replacements, ResolvedJavaMethod substitute, ResolvedJavaMethod substitutedMethod) {
            return new SubstrateRuntimeGraphMaker(metaAccess, replacements, substitute, substitutedMethod, wordTypes);
        }
    }

    static class SubstrateRuntimeGraphMaker extends SubstrateGraphMaker {
        private final MetaAccessProvider metaAccess;

        SubstrateRuntimeGraphMaker(MetaAccessProvider metaAccess, ReplacementsImpl replacements, ResolvedJavaMethod substitute, ResolvedJavaMethod substitutedMethod, WordTypes wordTypes) {
            super(replacements, substitute, substitutedMethod, wordTypes);
            this.metaAccess = metaAccess;
        }

        @Override
        protected void finalizeGraph(StructuredGraph graph) {
            RuntimeCompilationFeature.unwrapImageHeapConstants(graph, metaAccess);
            super.finalizeGraph(graph);
        }
    }
}
