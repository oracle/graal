/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.api;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.graph.SourceLanguagePositionProvider;
import org.graalvm.compiler.nodes.EncodedGraph;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.LoopExplosionPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.ParameterPlugin;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.PEGraphDecoder;
import org.graalvm.compiler.truffle.compiler.PartialEvaluator;
import org.graalvm.compiler.truffle.compiler.TruffleConstantFieldProvider;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.graal.phases.DeadStoreRemovalPhase;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class SubstratePartialEvaluator extends PartialEvaluator {

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstratePartialEvaluator(Providers providers, GraphBuilderConfiguration configForRoot, SnippetReflectionProvider snippetReflection, Architecture architecture) {
        super(providers, configForRoot, snippetReflection, architecture, new SubstrateKnownTruffleTypes(providers.getMetaAccess()));
    }

    @Override
    protected PEGraphDecoder createGraphDecoder(Request request, LoopExplosionPlugin loopExplosionPlugin,
                    InvocationPlugins invocationPlugins,
                    InlineInvokePlugin[] inlineInvokePlugins, ParameterPlugin parameterPlugin, NodePlugin[] nodePlugins, SourceLanguagePositionProvider sourceLanguagePositionProvider,
                    EconomicMap<ResolvedJavaMethod, EncodedGraph> graphCache) {
        TruffleConstantFieldProvider compilationLocalConstantProvider = new TruffleConstantFieldProvider(providers.getConstantFieldProvider(), providers.getMetaAccess());
        return new SubstratePEGraphDecoder(architecture, request.graph, providers.copyWith(compilationLocalConstantProvider), loopExplosionPlugin, invocationPlugins, inlineInvokePlugins,
                        parameterPlugin, nodePlugins, callInlined, sourceLanguagePositionProvider);
    }

    @Override
    protected StructuredGraph.Builder customizeStructuredGraphBuilder(StructuredGraph.Builder builder) {
        /*
         * Substrate VM does not need a complete list of methods that were inlined during
         * compilation. Therefore, we do not even store this information in encoded graphs that are
         * part of the image heap.
         */
        return super.customizeStructuredGraphBuilder(builder).recordInlinedMethods(false);
    }

    @Override
    public void doGraphPE(Request request, InlineInvokePlugin inlineInvokePlugin, EconomicMap<ResolvedJavaMethod, EncodedGraph> graphCache) {
        super.doGraphPE(request, inlineInvokePlugin, graphCache);
        new DeadStoreRemovalPhase().apply(request.graph);
        new TruffleBoundaryPhase().apply(request.graph);
    }

    @Override
    protected void registerTruffleInvocationPlugins(InvocationPlugins invocationPlugins, boolean canDelayIntrinsification) {
        super.registerTruffleInvocationPlugins(invocationPlugins, canDelayIntrinsification);
        SubstrateTruffleGraphBuilderPlugins.registerCompilationFinalReferencePlugins(invocationPlugins, canDelayIntrinsification, (SubstrateKnownTruffleTypes) getKnownTruffleTypes());
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    protected InvocationPlugins createDecodingInvocationPlugins(Plugins parent) {
        InvocationPlugins decodingInvocationPlugins = new InvocationPlugins();
        registerTruffleInvocationPlugins(decodingInvocationPlugins, false);
        decodingInvocationPlugins.closeRegistration();
        return decodingInvocationPlugins;
    }

    @Override
    protected NodePlugin[] createNodePlugins(Plugins plugins) {
        return null;
    }
}
