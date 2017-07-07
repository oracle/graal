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

import static jdk.vm.ci.runtime.JVMCICompiler.INVOCATION_ENTRY_BCI;
import static org.graalvm.compiler.core.GraalCompiler.compileGraph;
import static org.graalvm.compiler.core.common.CompilationRequestIdentifier.asCompilationRequest;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleEnableInfopoints;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleExcludeAssertions;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleInstrumentBoundaries;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleInstrumentBranches;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.CompilationPrinter;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.util.CompilationAlarm;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.MemUseTrackerKey;
import org.graalvm.compiler.debug.TimerKey;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
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
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.Assumptions.Assumption;
import jdk.vm.ci.meta.JavaConstant;
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

        graalTruffleRuntime.reinstallStubs();
    }

    public GraphBuilderConfiguration getGraphBuilderConfiguration() {
        return config;
    }

    public Backend getBackend() {
        return backend;
    }

    protected abstract PartialEvaluator createPartialEvaluator();

    public static ResolvedJavaType[] getSkippedExceptionTypes(MetaAccessProvider metaAccess) {
        ResolvedJavaType[] skippedExceptionTypes = new ResolvedJavaType[SKIPPED_EXCEPTION_CLASSES.length];
        for (int i = 0; i < SKIPPED_EXCEPTION_CLASSES.length; i++) {
            skippedExceptionTypes[i] = metaAccess.lookupJavaType(SKIPPED_EXCEPTION_CLASSES[i]);
        }
        return skippedExceptionTypes;
    }

    public static final TimerKey PartialEvaluationTime = DebugContext.timer("PartialEvaluationTime");
    public static final TimerKey CompilationTime = DebugContext.timer("CompilationTime");
    public static final TimerKey CodeInstallationTime = DebugContext.timer("CodeInstallation");

    public static final MemUseTrackerKey PartialEvaluationMemUse = DebugContext.memUseTracker("TrufflePartialEvaluationMemUse");
    public static final MemUseTrackerKey CompilationMemUse = DebugContext.memUseTracker("TruffleCompilationMemUse");
    public static final MemUseTrackerKey CodeInstallationMemUse = DebugContext.memUseTracker("TruffleCodeInstallationMemUse");

    public void compileMethod(DebugContext debug, final OptimizedCallTarget compilable, GraalTruffleRuntime runtime) {
        ResolvedJavaMethod rootMethod = partialEvaluator.rootForCallTarget(compilable);
        CompilationIdentifier compilationId = runtime.getCompilationIdentifier(compilable, rootMethod, backend);
        compileMethod(debug, compilable, rootMethod, compilationId, null);
    }

    @SuppressWarnings("try")
    public void compileMethod(DebugContext debug, final OptimizedCallTarget compilable, ResolvedJavaMethod rootMethod, CompilationIdentifier compilationId, CancellableCompileTask task) {
        final CompilationPrinter printer = CompilationPrinter.begin(TruffleCompilerOptions.getOptions(), compilationId, new TruffleDebugJavaMethod(compilable), INVOCATION_ENTRY_BCI);
        StructuredGraph graph = null;
        compilationNotify.notifyCompilationStarted(compilable);

        try (CompilationAlarm alarm = CompilationAlarm.trackCompilationPeriod(TruffleCompilerOptions.getOptions())) {
            TruffleInlining inliningDecision = new TruffleInlining(compilable, new DefaultInliningPolicy());
            PhaseSuite<HighTierContext> graphBuilderSuite = createGraphBuilderSuite();
            try (DebugCloseable a = PartialEvaluationTime.start(debug); DebugCloseable c = PartialEvaluationMemUse.start(debug)) {
                graph = partialEvaluator.createGraph(debug, compilable, inliningDecision, rootMethod, AllowAssumptions.YES, compilationId, task);
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

            // Partial evaluation and installation are included in
            // compilation time and memory usage reported by printer
            printer.finish(compilationResult);

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
        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope s = debug.scope("TruffleFinal")) {
            debug.dump(DebugContext.BASIC_LEVEL, graph, "After TruffleTier");
        } catch (Throwable e) {
            throw debug.handle(e);
        }

        CompilationResult result = null;

        try (DebugCloseable a = CompilationTime.start(debug);
                        DebugContext.Scope s = debug.scope("TruffleGraal.GraalCompiler", graph, providers.getCodeCache());
                        DebugCloseable c = CompilationMemUse.start(debug)) {
            SpeculationLog speculationLog = graph.getSpeculationLog();
            if (speculationLog != null) {
                speculationLog.collectFailedSpeculations();
            }

            CompilationResult compilationResult = createCompilationResult(name, graph.compilationId());
            result = compileGraph(graph, graph.method(), providers, backend, graphBuilderSuite, Optimizations, graph.getProfilingInfo(), suites, lirSuites, compilationResult,
                            CompilationResultBuilderFactory.Default);
        } catch (Throwable e) {
            throw debug.handle(e);
        }

        compilationNotify.notifyCompilationGraalTierFinished(predefinedInstalledCode, graph);

        try (DebugCloseable a = CodeInstallationTime.start(debug); DebugCloseable c = CodeInstallationMemUse.start(debug)) {
            backend.createInstalledCode(debug, graph.method(), compilationRequest, result, graph.getSpeculationLog(), predefinedInstalledCode, false);
        } catch (Throwable e) {
            throw debug.handle(e);
        }

        return result;
    }

    protected CompilationResult createCompilationResult(String name, CompilationIdentifier compilationIdentifier) {
        return new CompilationResult(compilationIdentifier, name);
    }

    protected abstract PhaseSuite<HighTierContext> createGraphBuilderSuite();

    public PartialEvaluator getPartialEvaluator() {
        return partialEvaluator;
    }

    public OptimizedCallTarget asOptimizedCallTarget(JavaConstant constant) {
        return snippetReflection.asObject(OptimizedCallTarget.class, constant);
    }

    private class TruffleCodeInstallationTask extends Backend.CodeInstallationTask {
        private List<AssumptionValidAssumption> validAssumptions = new ArrayList<>();

        @Override
        public void preProcess(CompilationResult result) {
            if (result == null || result.getAssumptions() == null) {
                return;
            }
            ArrayList<Assumption> newAssumptions = new ArrayList<>();
            for (Assumption assumption : result.getAssumptions()) {
                if (assumption != null && assumption instanceof AssumptionValidAssumption) {
                    AssumptionValidAssumption assumptionValidAssumption = (AssumptionValidAssumption) assumption;
                    validAssumptions.add(assumptionValidAssumption);
                } else {
                    newAssumptions.add(assumption);
                }
            }
            result.setAssumptions(newAssumptions.toArray(new Assumption[newAssumptions.size()]));
        }

        @Override
        public void postProcess(InstalledCode installedCode) {
            if (installedCode instanceof OptimizedCallTarget) {
                for (AssumptionValidAssumption assumption : validAssumptions) {
                    snippetReflection.asObject(OptimizedAssumption.class, assumption.getAssumption()).registerInstalledCode(installedCode);
                }
            }
        }

        @Override
        public void releaseInstallation(InstalledCode installedCode) {
            if (installedCode instanceof OptimizedCallTarget) {
                ((OptimizedCallTarget) installedCode).releaseEntryPoint();
            }
        }
    }

    private class TrufflePostCodeInstallationTaskFactory extends Backend.CodeInstallationTaskFactory {
        @Override
        public Backend.CodeInstallationTask create() {
            return new TruffleCodeInstallationTask();
        }
    }
}
