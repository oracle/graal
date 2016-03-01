/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.truffle;

import static com.oracle.graal.compiler.GraalCompiler.compileGraph;

import java.util.ArrayList;
import java.util.List;

import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;

import com.oracle.graal.api.replacements.SnippetReflectionProvider;
import com.oracle.graal.code.CompilationResult;
import com.oracle.graal.compiler.target.Backend;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.lir.phases.LIRSuites;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.tiers.Suites;
import com.oracle.graal.phases.util.Providers;
import com.oracle.graal.truffle.nodes.AssumptionValidAssumption;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.SlowPathException;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

/**
 * Implementation of the Truffle compiler using Graal.
 */
public abstract class TruffleCompiler {

    protected final Providers providers;

    protected final Suites suites;
    protected final GraphBuilderConfiguration config;
    protected final LIRSuites lirSuites;
    protected final PartialEvaluator partialEvaluator;
    protected final Backend backend;
    protected final SnippetReflectionProvider snippetReflection;
    protected final GraalTruffleCompilationListener compilationNotify;

    // @formatter:off
    private static final Class<?>[] SKIPPED_EXCEPTION_CLASSES = new Class<?>[]{
        UnexpectedResultException.class,
        SlowPathException.class,
        ArithmeticException.class,
        IllegalArgumentException.class,
        VirtualMachineError.class,
        StringIndexOutOfBoundsException.class,
        ClassCastException.class
    };
    // @formatter:on

    public static final OptimisticOptimizations Optimizations = OptimisticOptimizations.ALL.remove(OptimisticOptimizations.Optimization.UseExceptionProbability,
                    OptimisticOptimizations.Optimization.RemoveNeverExecutedCode, OptimisticOptimizations.Optimization.UseTypeCheckedInlining, OptimisticOptimizations.Optimization.UseTypeCheckHints);

    public TruffleCompiler(Plugins plugins, Suites suites, LIRSuites lirSuites, Backend backend, SnippetReflectionProvider snippetReflection) {
        GraalTruffleRuntime graalTruffleRuntime = ((GraalTruffleRuntime) Truffle.getRuntime());
        this.compilationNotify = graalTruffleRuntime.getCompilationNotify();
        this.backend = backend;
        this.snippetReflection = snippetReflection;
        Providers backendProviders = backend.getProviders();
        ConstantReflectionProvider constantReflection = new TruffleConstantReflectionProvider(backendProviders.getConstantReflection(), backendProviders.getMetaAccess());
        this.providers = backendProviders.copyWith(constantReflection);
        this.suites = suites;
        this.lirSuites = lirSuites;

        ResolvedJavaType[] skippedExceptionTypes = getSkippedExceptionTypes(providers.getMetaAccess());

        GraphBuilderConfiguration baseConfig = graalTruffleRuntime.enableInfopoints() ? GraphBuilderConfiguration.getInfopointDefault(new Plugins(plugins))
                        : GraphBuilderConfiguration.getDefault(new Plugins(plugins));
        this.config = baseConfig.withSkippedExceptionTypes(skippedExceptionTypes).withOmitAssertions(TruffleCompilerOptions.TruffleExcludeAssertions.getValue());

        this.partialEvaluator = createPartialEvaluator();

        if (Debug.isEnabled()) {
            DebugEnvironment.initialize(System.out);
        }

        graalTruffleRuntime.reinstallStubs();
    }

    public GraphBuilderConfiguration getGraphBuilderConfiguration() {
        return config;
    }

    protected abstract PartialEvaluator createPartialEvaluator();

    public static ResolvedJavaType[] getSkippedExceptionTypes(MetaAccessProvider metaAccess) {
        ResolvedJavaType[] skippedExceptionTypes = new ResolvedJavaType[SKIPPED_EXCEPTION_CLASSES.length];
        for (int i = 0; i < SKIPPED_EXCEPTION_CLASSES.length; i++) {
            skippedExceptionTypes[i] = metaAccess.lookupJavaType(SKIPPED_EXCEPTION_CLASSES[i]);
        }
        return skippedExceptionTypes;
    }

    public static final DebugTimer PartialEvaluationTime = Debug.timer("PartialEvaluationTime");
    public static final DebugTimer CompilationTime = Debug.timer("CompilationTime");
    public static final DebugTimer CodeInstallationTime = Debug.timer("CodeInstallation");

    public static final DebugMemUseTracker PartialEvaluationMemUse = Debug.memUseTracker("TrufflePartialEvaluationMemUse");
    public static final DebugMemUseTracker CompilationMemUse = Debug.memUseTracker("TruffleCompilationMemUse");
    public static final DebugMemUseTracker CodeInstallationMemUse = Debug.memUseTracker("TruffleCodeInstallationMemUse");

    @SuppressWarnings("try")
    public void compileMethod(final OptimizedCallTarget compilable) {
        StructuredGraph graph = null;

        compilationNotify.notifyCompilationStarted(compilable);

        try {
            PhaseSuite<HighTierContext> graphBuilderSuite = createGraphBuilderSuite();

            try (DebugCloseable a = PartialEvaluationTime.start(); DebugCloseable c = PartialEvaluationMemUse.start()) {
                graph = partialEvaluator.createGraph(compilable, AllowAssumptions.YES);
            }

            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            compilationNotify.notifyCompilationTruffleTierFinished(compilable, graph);
            CompilationResult compilationResult = compileMethodHelper(graph, compilable.toString(), graphBuilderSuite, compilable);
            compilationNotify.notifyCompilationSuccess(compilable, graph, compilationResult);
        } catch (Throwable t) {
            compilationNotify.notifyCompilationFailed(compilable, graph, t);
            throw t;
        }
    }

    @SuppressWarnings("try")
    public CompilationResult compileMethodHelper(StructuredGraph graph, String name, PhaseSuite<HighTierContext> graphBuilderSuite, InstalledCode predefinedInstalledCode) {
        try (Scope s = Debug.scope("TruffleFinal")) {
            Debug.dump(1, graph, "After TruffleTier");
        } catch (Throwable e) {
            throw Debug.handle(e);
        }

        CompilationResult result = null;
        List<AssumptionValidAssumption> validAssumptions = new ArrayList<>();
        TruffleCompilationResultBuilderFactory factory = new TruffleCompilationResultBuilderFactory(graph, validAssumptions);
        try (DebugCloseable a = CompilationTime.start(); Scope s = Debug.scope("TruffleGraal.GraalCompiler", graph, providers.getCodeCache()); DebugCloseable c = CompilationMemUse.start()) {
            SpeculationLog speculationLog = graph.getSpeculationLog();
            if (speculationLog != null) {
                speculationLog.collectFailedSpeculations();
            }

            CompilationResult compilationResult = new CompilationResult(name);
            result = compileGraph(graph, graph.method(), providers, backend, graphBuilderSuite, Optimizations, graph.getProfilingInfo(), suites, lirSuites, compilationResult, factory);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }

        compilationNotify.notifyCompilationGraalTierFinished((OptimizedCallTarget) predefinedInstalledCode, graph);

        InstalledCode installedCode;
        try (Scope s = Debug.scope("CodeInstall", providers.getCodeCache()); DebugCloseable a = CodeInstallationTime.start(); DebugCloseable c = CodeInstallationMemUse.start()) {
            CompiledCode compiledCode = backend.createCompiledCode(graph.method(), result);
            installedCode = providers.getCodeCache().addCode(graph.method(), compiledCode, graph.getSpeculationLog(), predefinedInstalledCode);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }

        for (AssumptionValidAssumption a : validAssumptions) {
            a.getAssumption().registerInstalledCode(installedCode);
        }

        return result;
    }

    protected abstract PhaseSuite<HighTierContext> createGraphBuilderSuite();

    public PartialEvaluator getPartialEvaluator() {
        return partialEvaluator;
    }
}
