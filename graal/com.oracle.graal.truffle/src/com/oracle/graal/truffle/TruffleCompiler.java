/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.compiler.GraalCompiler.*;
import static jdk.internal.jvmci.code.CodeUtil.*;

import java.util.*;

import jdk.internal.jvmci.code.*;
import jdk.internal.jvmci.code.CallingConvention.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.*;
import jdk.internal.jvmci.meta.*;
import jdk.internal.jvmci.meta.Assumptions.Assumption;

import com.oracle.graal.compiler.target.*;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.phases.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.truffle.nodes.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.*;

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
    protected final GraalTruffleCompilationListener compilationNotify;

    // @formatter:off
    private static final Class<?>[] SKIPPED_EXCEPTION_CLASSES = new Class[]{
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

    public TruffleCompiler(Plugins plugins, Suites suites, LIRSuites lirSuites, Backend backend) {
        GraalTruffleRuntime graalTruffleRuntime = ((GraalTruffleRuntime) Truffle.getRuntime());
        this.compilationNotify = graalTruffleRuntime.getCompilationNotify();
        this.backend = backend;
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
            System.out.println("compilation failed!?");
            compilationNotify.notifyCompilationFailed(compilable, graph, t);
            throw t;
        }
    }

    public CompilationResult compileMethodHelper(StructuredGraph graph, String name, PhaseSuite<HighTierContext> graphBuilderSuite, InstalledCode predefinedInstalledCode) {
        try (Scope s = Debug.scope("TruffleFinal")) {
            Debug.dump(1, graph, "After TruffleTier");
        } catch (Throwable e) {
            throw Debug.handle(e);
        }

        CompilationResult result = null;
        try (DebugCloseable a = CompilationTime.start(); Scope s = Debug.scope("TruffleGraal.GraalCompiler", graph, providers.getCodeCache()); DebugCloseable c = CompilationMemUse.start()) {
            SpeculationLog speculationLog = graph.getSpeculationLog();
            if (speculationLog != null) {
                speculationLog.collectFailedSpeculations();
            }

            CodeCacheProvider codeCache = providers.getCodeCache();
            CallingConvention cc = getCallingConvention(codeCache, Type.JavaCallee, graph.method(), false);
            CompilationResult compilationResult = new CompilationResult(name);
            result = compileGraph(graph, cc, graph.method(), providers, backend, codeCache.getTarget(), graphBuilderSuite, Optimizations, getProfilingInfo(graph), suites, lirSuites,
                            compilationResult, CompilationResultBuilderFactory.Default);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }

        compilationNotify.notifyCompilationGraalTierFinished((OptimizedCallTarget) predefinedInstalledCode, graph);

        if (graph.isInlinedMethodRecordingEnabled()) {
            result.setMethods(graph.method(), graph.getInlinedMethods());
            result.setBytecodeSize(graph.getBytecodeSize());
        } else {
            assert result.getMethods() == null;
        }

        List<AssumptionValidAssumption> validAssumptions = new ArrayList<>();
        Set<Assumption> newAssumptions = new HashSet<>();
        for (Assumption assumption : graph.getAssumptions()) {
            processAssumption(newAssumptions, assumption, validAssumptions);
        }

        if (result.getAssumptions() != null) {
            for (Assumption assumption : result.getAssumptions()) {
                processAssumption(newAssumptions, assumption, validAssumptions);
            }
        }

        result.setAssumptions(newAssumptions.toArray(new Assumption[newAssumptions.size()]));

        InstalledCode installedCode;
        try (Scope s = Debug.scope("CodeInstall", providers.getCodeCache()); DebugCloseable a = CodeInstallationTime.start(); DebugCloseable c = CodeInstallationMemUse.start()) {
            installedCode = providers.getCodeCache().addMethod(graph.method(), result, graph.getSpeculationLog(), predefinedInstalledCode);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }

        for (AssumptionValidAssumption a : validAssumptions) {
            a.getAssumption().registerInstalledCode(installedCode);
        }

        return result;
    }

    protected abstract PhaseSuite<HighTierContext> createGraphBuilderSuite();

    public void processAssumption(Set<Assumption> newAssumptions, Assumption assumption, List<AssumptionValidAssumption> manual) {
        if (assumption != null) {
            if (assumption instanceof AssumptionValidAssumption) {
                AssumptionValidAssumption assumptionValidAssumption = (AssumptionValidAssumption) assumption;
                manual.add(assumptionValidAssumption);
            } else {
                newAssumptions.add(assumption);
            }
        }
    }

    public PartialEvaluator getPartialEvaluator() {
        return partialEvaluator;
    }
}
