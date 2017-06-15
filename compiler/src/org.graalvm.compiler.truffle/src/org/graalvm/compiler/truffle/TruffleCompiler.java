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
package org.graalvm.compiler.truffle;

import static org.graalvm.compiler.core.GraalCompiler.compileGraph;
import static org.graalvm.compiler.core.common.CompilationRequestIdentifier.asCompilationRequest;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleEnableInfopoints;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleExcludeAssertions;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleInstrumentBoundaries;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleInstrumentBranches;

import java.util.ArrayList;
import java.util.List;

import jdk.vm.ci.code.InstalledCode;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.util.CompilationAlarm;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.Debug.Scope;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugEnvironment;
import org.graalvm.compiler.debug.DebugMemUseTracker;
import org.graalvm.compiler.debug.DebugTimer;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.BytecodeExceptionMode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.truffle.nodes.AssumptionValidAssumption;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.SlowPathException;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;

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
    protected final Backend.CodeInstallationTaskFactory codeInstallationTaskFactory;

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
        this.providers = backend.getProviders();
        this.suites = suites;
        this.lirSuites = lirSuites;
        this.codeInstallationTaskFactory = new TrufflePostCodeInstallationTaskFactory();
        backend.addCodeInstallationTask(codeInstallationTaskFactory);

        ResolvedJavaType[] skippedExceptionTypes = getSkippedExceptionTypes(providers.getMetaAccess());

        boolean needSourcePositions = graalTruffleRuntime.enableInfopoints() || TruffleCompilerOptions.getValue(TruffleEnableInfopoints) ||
                        TruffleCompilerOptions.getValue(TruffleInstrumentBranches) || TruffleCompilerOptions.getValue(TruffleInstrumentBoundaries);
        GraphBuilderConfiguration baseConfig = GraphBuilderConfiguration.getDefault(new Plugins(plugins)).withNodeSourcePosition(needSourcePositions);
        this.config = baseConfig.withSkippedExceptionTypes(skippedExceptionTypes).withOmitAssertions(TruffleCompilerOptions.getValue(TruffleExcludeAssertions)).withBytecodeExceptionMode(
                        BytecodeExceptionMode.ExplicitOnly);

        this.partialEvaluator = createPartialEvaluator();

        if (Debug.isEnabled()) {
            DebugEnvironment.ensureInitialized(TruffleCompilerOptions.getOptions(), graalTruffleRuntime.getRequiredGraalCapability(SnippetReflectionProvider.class));
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

    public void compileMethod(final OptimizedCallTarget compilable, GraalTruffleRuntime runtime) {
        compileMethod(compilable, runtime, null);
    }

    @SuppressWarnings("try")
    public void compileMethod(final OptimizedCallTarget compilable, GraalTruffleRuntime runtime, CancellableCompileTask task) {
        StructuredGraph graph = null;
        compilationNotify.notifyCompilationStarted(compilable);

        try (CompilationAlarm alarm = CompilationAlarm.trackCompilationPeriod(TruffleCompilerOptions.getOptions())) {
            ResolvedJavaMethod rootMethod = partialEvaluator.rootForCallTarget(compilable);
            TruffleInlining inliningDecision = new TruffleInlining(compilable, new DefaultInliningPolicy());
            CompilationIdentifier compilationId = runtime.getCompilationIdentifier(compilable, rootMethod, backend);
            PhaseSuite<HighTierContext> graphBuilderSuite = createGraphBuilderSuite();
            try (DebugCloseable a = PartialEvaluationTime.start(); DebugCloseable c = PartialEvaluationMemUse.start()) {
                graph = partialEvaluator.createGraph(compilable, inliningDecision, rootMethod, AllowAssumptions.YES, compilationId, task);
            }

            // check if the task was cancelled in the time frame between [after PE: before
            // compilation]
            if (task != null && task.isCancelled()) {
                return;
            }

            dequeueInlinedCallSites(inliningDecision, compilable);

            compilationNotify.notifyCompilationTruffleTierFinished(compilable, inliningDecision, graph);
            CompilationResult compilationResult = compileMethodHelper(graph, compilable.toString(), graphBuilderSuite, compilable, asCompilationRequest(compilationId));
            compilationNotify.notifyCompilationSuccess(compilable, inliningDecision, graph, compilationResult);
            dequeueInlinedCallSites(inliningDecision, compilable);
        } catch (Throwable t) {
            // Note: If the compiler cancels the compilation with a bailout exception, then the
            // graph is null
            compilationNotify.notifyCompilationFailed(compilable, graph, t);
            throw t;
        }
    }

    private static void dequeueInlinedCallSites(TruffleInlining inliningDecision, OptimizedCallTarget compilable) {
        if (inliningDecision != null) {
            for (TruffleInliningDecision decision : inliningDecision) {
                if (decision.isInline()) {
                    OptimizedCallTarget target = decision.getTarget();
                    if (target != compilable) {
                        target.cancelInstalledTask(decision.getProfile().getCallNode(), "Inlining caller compiled.");
                    }
                    dequeueInlinedCallSites(decision, compilable);
                }
            }
        }
    }

    @SuppressWarnings("try")
    public CompilationResult compileMethodHelper(StructuredGraph graph, String name, PhaseSuite<HighTierContext> graphBuilderSuite, OptimizedCallTarget predefinedInstalledCode,
                    CompilationRequest compilationRequest) {
        try (Scope s = Debug.scope("TruffleFinal")) {
            Debug.dump(Debug.BASIC_LEVEL, graph, "After TruffleTier");
        } catch (Throwable e) {
            throw Debug.handle(e);
        }

        CompilationResult result = null;
        List<AssumptionValidAssumption> validAssumptions = new ArrayList<>();

        TruffleCompilationResultBuilderFactory factory = new TruffleCompilationResultBuilderFactory(graph, validAssumptions);
        try (DebugCloseable a = CompilationTime.start();
                        Scope s = Debug.scope("TruffleGraal.GraalCompiler", graph, providers.getCodeCache());
                        DebugCloseable c = CompilationMemUse.start()) {
            SpeculationLog speculationLog = graph.getSpeculationLog();
            if (speculationLog != null) {
                speculationLog.collectFailedSpeculations();
            }

            CompilationResult compilationResult = createCompilationResult(name, graph.compilationId());
            result = compileGraph(graph, graph.method(), providers, backend, graphBuilderSuite, Optimizations, graph.getProfilingInfo(), suites, lirSuites, compilationResult, factory);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }

        compilationNotify.notifyCompilationGraalTierFinished(predefinedInstalledCode, graph);

        OptimizedCallTarget installedCode;
        try (DebugCloseable a = CodeInstallationTime.start(); DebugCloseable c = CodeInstallationMemUse.start()) {
            installedCode = (OptimizedCallTarget) backend.createInstalledCode(graph.method(), compilationRequest, result, graph.getSpeculationLog(), predefinedInstalledCode, false);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }

        for (AssumptionValidAssumption a : validAssumptions) {
            a.getAssumption().registerInstalledCode(installedCode);
        }

        if (!providers.getCodeCache().getTarget().arch.getName().equals("aarch64")) {
            installedCode.releaseEntryPoint();
        }

        return result;
    }

    /**
     * @param compilationIdentifier
     */
    protected CompilationResult createCompilationResult(String name, CompilationIdentifier compilationIdentifier) {
        return new CompilationResult(name);
    }

    protected abstract PhaseSuite<HighTierContext> createGraphBuilderSuite();

    public PartialEvaluator getPartialEvaluator() {
        return partialEvaluator;
    }

    private class TrufflePostCodeInstallationTask extends Backend.CodeInstallationTask {
        private List<AssumptionValidAssumption> validAssumptions = new ArrayList<>();

        @Override
        public void preProcess(CompilationResult result) {
        }

        @Override
        public void postProcess(InstalledCode installedCode) {
        }

        @Override
        public void releaseInstallation(InstalledCode installedCode) {
        }
    }

    private class TrufflePostCodeInstallationTaskFactory extends Backend.CodeInstallationTaskFactory {
        @Override
        public Backend.CodeInstallationTask create() {
            return new TrufflePostCodeInstallationTask();
        }
    }
}
