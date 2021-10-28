/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.OptimizedAssumptionDependency;
import org.graalvm.compiler.truffle.compiler.EconomyPartialEvaluatorConfiguration;
import org.graalvm.compiler.truffle.compiler.PartialEvaluator;
import org.graalvm.compiler.truffle.compiler.PartialEvaluatorConfiguration;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerConfiguration;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerImpl;
import org.graalvm.compiler.truffle.compiler.TruffleTierConfiguration;
import org.graalvm.compiler.truffle.runtime.BackgroundCompileQueue;
import org.graalvm.compiler.truffle.runtime.OptimizedAssumption;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.OptimizedDirectCallNode;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.graal.GraalSupport;
import com.oracle.svm.graal.hosted.GraalFeature;
import com.oracle.svm.truffle.api.SubstrateOptimizedCallTarget;
import com.oracle.svm.truffle.api.SubstratePartialEvaluator;
import com.oracle.svm.truffle.api.SubstrateTruffleCompiler;
import com.oracle.svm.truffle.api.SubstrateTruffleCompilerImpl;
import com.oracle.svm.truffle.api.SubstrateTruffleRuntime;
import com.oracle.svm.truffle.isolated.IsolateAwareTruffleCompiler;
import com.oracle.svm.truffle.isolated.IsolatedTruffleRuntimeSupport;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.utilities.TriState;

import jdk.vm.ci.meta.JavaConstant;

public class TruffleSupport {

    public static TruffleSupport singleton() {
        return ImageSingletons.lookup(TruffleSupport.class);
    }

    public SubstrateOptimizedCallTarget createOptimizedCallTarget(OptimizedCallTarget sourceCallTarget, RootNode rootNode) {
        return new SubstrateOptimizedCallTarget(sourceCallTarget, rootNode);
    }

    public SubstratePartialEvaluator createPartialEvaluator(TruffleCompilerConfiguration config, GraphBuilderConfiguration graphBuilderConfigForRoot) {
        return new SubstratePartialEvaluator(config, graphBuilderConfigForRoot);
    }

    @SuppressWarnings("unused")
    public void registerInterpreterEntryMethodsAsCompiled(PartialEvaluator partialEvaluator, Feature.BeforeAnalysisAccess access) {
    }

    public SubstrateTruffleCompiler createTruffleCompiler(SubstrateTruffleRuntime runtime) {
        SubstrateTruffleCompiler compiler = createSubstrateTruffleCompilerImpl(runtime, "community");
        if (SubstrateOptions.supportCompileInIsolates()) {
            compiler = new IsolateAwareTruffleCompiler(compiler);
        }
        return compiler;
    }

    protected static SubstrateTruffleCompiler createSubstrateTruffleCompilerImpl(SubstrateTruffleRuntime runtime, String compilerConfigurationName) {
        GraalFeature graalFeature = ImageSingletons.lookup(GraalFeature.class);
        SnippetReflectionProvider snippetReflectionProvider = graalFeature.getHostedProviders().getSnippetReflection();
        final GraphBuilderConfiguration.Plugins graphBuilderPlugins = graalFeature.getHostedProviders().getGraphBuilderPlugins();
        final TruffleTierConfiguration firstTier = new TruffleTierConfiguration(new EconomyPartialEvaluatorConfiguration(), GraalSupport.getRuntimeConfig().getBackendForNormalMethod(),
                        GraalSupport.getFirstTierProviders(), GraalSupport.getFirstTierSuites(), GraalSupport.getFirstTierLirSuites());

        PartialEvaluatorConfiguration peConfig = TruffleCompilerImpl.createPartialEvaluatorConfiguration(compilerConfigurationName);
        final TruffleTierConfiguration lastTier = new TruffleTierConfiguration(peConfig, GraalSupport.getRuntimeConfig().getBackendForNormalMethod(),
                        GraalSupport.getRuntimeConfig().getProviders(), GraalSupport.getSuites(), GraalSupport.getLIRSuites());
        final TruffleCompilerConfiguration truffleCompilerConfig = new TruffleCompilerConfiguration(runtime, graphBuilderPlugins, snippetReflectionProvider, firstTier, lastTier);

        return new SubstrateTruffleCompilerImpl(truffleCompilerConfig);
    }

    public static boolean isIsolatedCompilation() {
        return !SubstrateUtil.HOSTED && SubstrateOptions.shouldCompileInIsolates();
    }

    public Consumer<OptimizedAssumptionDependency> registerOptimizedAssumptionDependency(JavaConstant optimizedAssumptionConstant) {
        if (isIsolatedCompilation()) {
            return IsolatedTruffleRuntimeSupport.registerOptimizedAssumptionDependency(optimizedAssumptionConstant);
        }
        Object target = SubstrateObjectConstant.asObject(optimizedAssumptionConstant);
        OptimizedAssumption assumption = (OptimizedAssumption) target;
        return assumption.registerDependency();
    }

    public JavaConstant getCallTargetForCallNode(JavaConstant callNodeConstant) {
        if (isIsolatedCompilation()) {
            return IsolatedTruffleRuntimeSupport.getCallTargetForCallNode(callNodeConstant);
        }
        Object target = SubstrateObjectConstant.asObject(callNodeConstant);
        OptimizedDirectCallNode callNode = (OptimizedDirectCallNode) target;
        OptimizedCallTarget callTarget = callNode.getCallTarget();
        return SubstrateObjectConstant.forObject(callTarget);
    }

    public BackgroundCompileQueue createBackgroundCompileQueue(@SuppressWarnings("unused") SubstrateTruffleRuntime runtime) {
        return new BackgroundCompileQueue(runtime);
    }

    public CompilableTruffleAST asCompilableTruffleAST(JavaConstant constant) {
        if (isIsolatedCompilation()) {
            return IsolatedTruffleRuntimeSupport.asCompilableTruffleAST(constant);
        }
        return SubstrateObjectConstant.asObject(OptimizedCallTarget.class, constant);
    }

    @SuppressWarnings("unused")
    public boolean tryLog(SubstrateTruffleRuntime runtime, String loggerId, CompilableTruffleAST compilable, String message) {
        if (isIsolatedCompilation()) {
            return IsolatedTruffleRuntimeSupport.tryLog(loggerId, compilable, message);
        }
        return false;
    }

    public TriState tryIsSuppressedFailure(CompilableTruffleAST compilable, Supplier<String> serializedException) {
        if (isIsolatedCompilation()) {
            return IsolatedTruffleRuntimeSupport.tryIsSuppressedFailure(compilable, serializedException);
        }
        return TriState.UNDEFINED;
    }
}
