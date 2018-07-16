/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler;

import static jdk.vm.ci.runtime.JVMCICompiler.INVOCATION_ENTRY_BCI;
import static org.graalvm.compiler.core.CompilationWrapper.ExceptionAction.Diagnose;
import static org.graalvm.compiler.core.GraalCompilerOptions.CompilationBailoutAction;
import static org.graalvm.compiler.core.GraalCompilerOptions.CompilationFailureAction;
import static org.graalvm.compiler.core.common.CompilationRequestIdentifier.asCompilationRequest;
import static org.graalvm.compiler.phases.OptimisticOptimizations.ALL;
import static org.graalvm.compiler.phases.OptimisticOptimizations.Optimization.RemoveNeverExecutedCode;
import static org.graalvm.compiler.phases.OptimisticOptimizations.Optimization.UseExceptionProbability;
import static org.graalvm.compiler.phases.OptimisticOptimizations.Optimization.UseTypeCheckHints;
import static org.graalvm.compiler.phases.OptimisticOptimizations.Optimization.UseTypeCheckedInlining;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleEnableInfopoints;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleExcludeAssertions;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleInstrumentBoundaries;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleInstrumentBranches;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.getValue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.CompilationPrinter;
import org.graalvm.compiler.core.CompilationWrapper;
import org.graalvm.compiler.core.CompilationWrapper.ExceptionAction;
import org.graalvm.compiler.core.GraalCompiler;
import org.graalvm.compiler.core.common.CancellationBailoutException;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.RetryableBailoutException;
import org.graalvm.compiler.core.common.util.CompilationAlarm;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Scope;
import org.graalvm.compiler.debug.DiagnosticsOutputDirectory;
import org.graalvm.compiler.debug.MemUseTrackerKey;
import org.graalvm.compiler.debug.TimerKey;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.nodes.Cancellable;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.BytecodeExceptionMode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.options.EnumOptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.OptimizedAssumptionDependency;
import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener;
import org.graalvm.compiler.truffle.common.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.TruffleDebugJavaMethod;
import org.graalvm.compiler.truffle.common.TruffleInliningPlan;
import org.graalvm.compiler.truffle.compiler.nodes.TruffleAssumption;
import org.graalvm.compiler.truffle.compiler.phases.InstrumentPhase;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.meta.Assumptions.Assumption;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Coordinates partial evaluation of a Truffle AST and subsequent compilation via Graal.
 */
public abstract class TruffleCompilerImpl implements TruffleCompiler {

    /**
     * The Graal-specific Truffle runtime associated with this compiler.
     */

    protected final Providers providers;
    protected final Suites suites;
    protected final GraphBuilderConfiguration config;
    protected final LIRSuites lirSuites;
    protected final PartialEvaluator partialEvaluator;
    protected final Backend backend;
    protected final SnippetReflectionProvider snippetReflection;
    protected final TrufflePostCodeInstallationTaskFactory codeInstallationTaskFactory;

    public static final OptimisticOptimizations Optimizations = ALL.remove(
                    UseExceptionProbability,
                    RemoveNeverExecutedCode,
                    UseTypeCheckedInlining,
                    UseTypeCheckHints);

    public TruffleCompilerImpl(TruffleCompilerRuntime runtime, Plugins plugins, Suites suites, LIRSuites lirSuites, Backend backend, SnippetReflectionProvider snippetReflection) {
        this.backend = backend;
        this.snippetReflection = snippetReflection;
        this.providers = backend.getProviders();
        this.suites = suites;
        this.lirSuites = lirSuites;
        this.codeInstallationTaskFactory = new TrufflePostCodeInstallationTaskFactory();
        backend.addCodeInstallationTask(codeInstallationTaskFactory);

        ResolvedJavaType[] skippedExceptionTypes = getSkippedExceptionTypes(runtime);

        boolean needSourcePositions = TruffleCompilerOptions.getValue(TruffleEnableInfopoints) ||
                        TruffleCompilerOptions.getValue(TruffleInstrumentBranches) || TruffleCompilerOptions.getValue(TruffleInstrumentBoundaries);
        GraphBuilderConfiguration baseConfig = GraphBuilderConfiguration.getDefault(new Plugins(plugins)).withNodeSourcePosition(needSourcePositions);
        this.config = baseConfig.withSkippedExceptionTypes(skippedExceptionTypes).withOmitAssertions(TruffleCompilerOptions.getValue(TruffleExcludeAssertions)).withBytecodeExceptionMode(
                        BytecodeExceptionMode.ExplicitOnly);

        this.partialEvaluator = createPartialEvaluator();
    }

    private ResolvedJavaType[] getSkippedExceptionTypes(TruffleCompilerRuntime runtime) {
        final MetaAccessProvider metaAccess = providers.getMetaAccess();
        ResolvedJavaType[] head = metaAccess.lookupJavaTypes(new Class<?>[]{
                        ArithmeticException.class,
                        IllegalArgumentException.class,
                        VirtualMachineError.class,
                        IndexOutOfBoundsException.class,
                        ClassCastException.class,
                        BufferUnderflowException.class,
                        BufferOverflowException.class,
        });
        ResolvedJavaType[] tail = {
                        runtime.resolveType(metaAccess, "com.oracle.truffle.api.nodes.UnexpectedResultException"),
                        runtime.resolveType(metaAccess, "com.oracle.truffle.api.nodes.SlowPathException")
        };
        ResolvedJavaType[] skippedExceptionTypes = new ResolvedJavaType[head.length + tail.length];
        System.arraycopy(head, 0, skippedExceptionTypes, 0, head.length);
        System.arraycopy(tail, 0, skippedExceptionTypes, head.length, tail.length);
        return skippedExceptionTypes;
    }

    /**
     * Gets the Graal compiler backend used for Truffle compilation.
     */
    public Backend getBackend() {
        return backend;
    }

    /**
     * Creates the partial evaluator used by this compiler.
     */
    protected abstract PartialEvaluator createPartialEvaluator();

    public static final TimerKey PartialEvaluationTime = DebugContext.timer("PartialEvaluationTime");
    public static final TimerKey CompilationTime = DebugContext.timer("CompilationTime");
    public static final TimerKey CodeInstallationTime = DebugContext.timer("CodeInstallation");

    public static final MemUseTrackerKey PartialEvaluationMemUse = DebugContext.memUseTracker("TrufflePartialEvaluationMemUse");
    public static final MemUseTrackerKey CompilationMemUse = DebugContext.memUseTracker("TruffleCompilationMemUse");
    public static final MemUseTrackerKey CodeInstallationMemUse = DebugContext.memUseTracker("TruffleCodeInstallationMemUse");

    @Override
    @SuppressWarnings("try")
    public void doCompile(DebugContext inDebug, CompilationIdentifier inCompilationId, OptionValues options, CompilableTruffleAST compilable, TruffleInliningPlan inliningPlan, Cancellable cancellable,
                    TruffleCompilerListener listener) {
        CompilationIdentifier compilationId = inCompilationId == null ? getCompilationIdentifier(compilable) : inCompilationId;
        DebugContext debug = inDebug == null ? openDebugContext(options, compilationId, compilable) : inDebug;
        try (DebugContext debugToClose = debug == inDebug ? null : debug;
                        DebugContext.Scope s = maybeOpenTruffleScope(compilable, debug)) {
            new TruffleCompilationWrapper(getDebugOutputDirectory(), getCompilationProblemsPerAction(), compilable, cancellable, inliningPlan, compilationId, listener).run(debug);
        } catch (Throwable e) {
            notifyCompilableOfFailure(compilable, e);
        }
    }

    /**
     * Opens a new {@code "Truffle"} scope if that's not the unqualified name of the current scope.
     *
     * @param compilable the context for the {@code "Truffle"} scope
     * @param debug the current debug context
     * @return a new opened {@code "Truffle"} scope or {@code null} if the current (unqualified)
     *         scope is already named {@code "Truffle"}
     */
    private static Scope maybeOpenTruffleScope(CompilableTruffleAST compilable, DebugContext debug) throws Throwable {
        if (debug.getCurrentScopeName().endsWith(".Truffle")) {
            return null;
        }
        return debug.scope("Truffle", new TruffleDebugJavaMethod(compilable));
    }

    private static void notifyCompilableOfFailure(CompilableTruffleAST compilable, Throwable e) {
        BailoutException bailout = e instanceof BailoutException ? (BailoutException) e : null;
        boolean permanentBailout = bailout != null ? bailout.isPermanent() : false;
        final Supplier<String> reasonAndStackTrace = new Supplier<String>() {

            @Override
            public String get() {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                return sw.toString();
            }
        };
        compilable.onCompilationFailed(reasonAndStackTrace, bailout != null, permanentBailout);
    }

    @Override
    public void shutdown() {
        InstrumentPhase.Instrumentation ins = this.partialEvaluator.instrumentation;
        if (ins != null) {
            OptionValues options = TruffleCompilerOptions.getOptions();
            if (getValue(TruffleInstrumentBranches) || getValue(TruffleInstrumentBoundaries)) {
                ins.dumpAccessTable(options);
            }
        }
    }

    protected abstract DiagnosticsOutputDirectory getDebugOutputDirectory();

    /**
     * Gets the map used to count the number of compilation failures or bailouts handled by each
     * action.
     *
     * @see CompilationWrapper#CompilationWrapper(DiagnosticsOutputDirectory, Map)
     */
    protected abstract Map<ExceptionAction, Integer> getCompilationProblemsPerAction();

    static class GraphInfoImpl implements TruffleCompilerListener.GraphInfo {
        private final StructuredGraph graph;

        GraphInfoImpl(StructuredGraph graph) {
            this.graph = graph;
        }

        @Override
        public int getNodeCount() {
            return graph.getNodeCount();
        }

        @Override
        public String[] getNodeTypes(boolean simpleNames) {
            String[] res = new String[graph.getNodeCount()];
            int i = 0;
            for (org.graalvm.compiler.graph.Node node : graph.getNodes()) {
                res[i++] = simpleNames ? node.getClass().getSimpleName() : node.getClass().getName();
            }
            return res;
        }
    }

    static class CompilationResultInfoImpl implements TruffleCompilerListener.CompilationResultInfo {
        private final CompilationResult compResult;

        CompilationResultInfoImpl(CompilationResult compResult) {
            this.compResult = compResult;
        }

        @Override
        public int getTargetCodeSize() {
            return compResult.getTargetCodeSize();
        }

        @Override
        public int getTotalFrameSize() {
            return compResult.getTotalFrameSize();
        }

        @Override
        public int getExceptionHandlersCount() {
            return compResult.getExceptionHandlers().size();
        }

        @Override
        public int getInfopointsCount() {
            return compResult.getInfopoints().size();
        }

        @Override
        public String[] getInfopoints() {
            final List<Infopoint> infopoints = compResult.getInfopoints();
            final String[] res = new String[infopoints.size()];
            int i = 0;
            for (Infopoint infopoint : infopoints) {
                res[i++] = infopoint.reason.toString();
            }
            return res;
        }

        @Override
        public int getMarksCount() {
            return compResult.getMarks().size();
        }

        @Override
        public int getDataPatchesCount() {
            return compResult.getDataPatches().size();
        }
    }

    /**
     * Compiles a Truffle AST. If compilation succeeds, the AST will have compiled code associated
     * with it that can be executed instead of interpreting the AST.
     *
     * @param compilable representation of the AST to be compiled
     * @param inliningPlan
     * @param compilationId identifier to be used for the compilation
     * @param cancellable an object polled during the compilation process to
     *            {@linkplain CancellationBailoutException abort} early if the thread owning the
     *            cancellable requests it
     * @param listener
     */
    @SuppressWarnings("try")
    public void compileAST(DebugContext debug, final CompilableTruffleAST compilable, TruffleInliningPlan inliningPlan, CompilationIdentifier compilationId, Cancellable cancellable,
                    TruffleCompilerListener listener) {
        final CompilationPrinter printer = CompilationPrinter.begin(TruffleCompilerOptions.getOptions(), compilationId, new TruffleDebugJavaMethod(compilable), INVOCATION_ENTRY_BCI);
        StructuredGraph graph = null;

        try (CompilationAlarm alarm = CompilationAlarm.trackCompilationPeriod(TruffleCompilerOptions.getOptions())) {
            PhaseSuite<HighTierContext> graphBuilderSuite = createGraphBuilderSuite();

            // Failed speculations must be collected before any compilation or
            // partial evaluation is performed.
            SpeculationLog speculationLog = compilable.getSpeculationLog();
            if (speculationLog != null) {
                speculationLog.collectFailedSpeculations();
            }

            try (DebugCloseable a = PartialEvaluationTime.start(debug); DebugCloseable c = PartialEvaluationMemUse.start(debug)) {
                graph = partialEvaluator.createGraph(debug, compilable, inliningPlan, AllowAssumptions.YES, compilationId, speculationLog, cancellable);
            }

            // Check if the task has been cancelled
            if (cancellable != null && cancellable.isCancelled()) {
                return;
            }

            if (listener != null) {
                listener.onTruffleTierFinished(compilable, inliningPlan, new GraphInfoImpl(graph));
            }
            CompilationResult compilationResult = compilePEGraph(graph, compilable.toString(), graphBuilderSuite, compilable, asCompilationRequest(compilationId), listener);
            if (listener != null) {
                listener.onSuccess(compilable, inliningPlan, new GraphInfoImpl(graph), new CompilationResultInfoImpl(compilationResult));
            }

            // Partial evaluation and installation are included in
            // compilation time and memory usage reported by printer
            printer.finish(compilationResult);
        } catch (Throwable t) {
            // Note: If the compiler cancels the compilation with a bailout exception, then the
            // graph is null
            if (listener != null) {
                BailoutException bailout = t instanceof BailoutException ? (BailoutException) t : null;
                boolean permanentBailout = bailout != null ? bailout.isPermanent() : false;
                listener.onFailure(compilable, t.toString(), bailout != null, permanentBailout);
            }
            throw t;
        }
    }

    static void notifyListenerOfFailure(final CompilableTruffleAST compilable, TruffleCompilerListener listener, Throwable t) {
        if (listener != null) {
            BailoutException bailout = t instanceof BailoutException ? (BailoutException) t : null;
            boolean permanentBailout = bailout != null ? bailout.isPermanent() : false;
            listener.onFailure(compilable, t.toString(), bailout != null, permanentBailout);
        }
    }

    /**
     * Compiles a graph produced by {@link PartialEvaluator#createGraph partial evaluation}.
     *
     * @param graph a graph resulting from partial evaluation
     * @param name the name to be used for the returned {@link CompilationResult#getName() result}
     * @param graphBuilderSuite phase suite to be used for creating new graphs during inlining
     * @param compilationRequest
     * @param listener
     */
    @SuppressWarnings("try")
    public CompilationResult compilePEGraph(StructuredGraph graph, String name, PhaseSuite<HighTierContext> graphBuilderSuite, CompilableTruffleAST compilable,
                    CompilationRequest compilationRequest, TruffleCompilerListener listener) {
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

            CompilationResult compilationResult = createCompilationResult(name, graph.compilationId());
            result = GraalCompiler.compileGraph(graph, graph.method(), providers, backend, graphBuilderSuite, Optimizations, graph.getProfilingInfo(), suites, lirSuites, compilationResult,
                            CompilationResultBuilderFactory.Default, false);
        } catch (Throwable e) {
            throw debug.handle(e);
        }

        if (listener != null) {
            listener.onGraalTierFinished(compilable, new GraphInfoImpl(graph));
        }

        try (DebugCloseable a = CodeInstallationTime.start(debug); DebugCloseable c = CodeInstallationMemUse.start(debug)) {
            InstalledCode installedCode = createInstalledCode(compilable);
            backend.createInstalledCode(debug, graph.method(), compilationRequest, result, graph.getSpeculationLog(), installedCode, false);
        } catch (Throwable e) {
            throw debug.handle(e);
        }

        return result;
    }

    protected abstract InstalledCode createInstalledCode(CompilableTruffleAST compilable);

    /**
     * @see OptimizedAssumptionDependency#reachabilityDeterminesValidity()
     *
     * @param installedCode
     */
    protected boolean reachabilityDeterminesValidity(InstalledCode installedCode) {
        return true;
    }

    protected CompilationResult createCompilationResult(String name, CompilationIdentifier compilationIdentifier) {
        return new CompilationResult(compilationIdentifier, name);
    }

    public abstract PhaseSuite<HighTierContext> createGraphBuilderSuite();

    public PartialEvaluator getPartialEvaluator() {
        return partialEvaluator;
    }

    public CompilableTruffleAST asCompilableTruffleAST(JavaConstant constant) {
        return snippetReflection.asObject(CompilableTruffleAST.class, constant);
    }

    /**
     * Wrapper for performing a Truffle compilation that can retry upon failure.
     */
    private final class TruffleCompilationWrapper extends CompilationWrapper<Void> {
        private final CompilableTruffleAST compilable;
        private final TruffleInliningPlan inliningPlan;
        private final Cancellable cancellable;
        private final TruffleCompilerListener listener;
        private final CompilationIdentifier compilationId;

        private TruffleCompilationWrapper(DiagnosticsOutputDirectory outputDirectory, Map<ExceptionAction, Integer> problemsHandledPerAction, CompilableTruffleAST optimizedCallTarget,
                        Cancellable cancellable, TruffleInliningPlan inliningPlan, CompilationIdentifier compilationId, TruffleCompilerListener listener) {
            super(outputDirectory, problemsHandledPerAction);
            this.compilable = optimizedCallTarget;
            this.inliningPlan = inliningPlan;
            this.cancellable = cancellable;
            this.listener = listener;
            this.compilationId = compilationId;
        }

        @Override
        public String toString() {
            return compilable.toString();
        }

        @Override
        protected ExceptionAction lookupAction(OptionValues options, EnumOptionKey<ExceptionAction> actionKey, Throwable cause) {
            // Respect current action if it has been explicitly set.
            if (!actionKey.hasBeenSet(options)) {
                if (actionKey == CompilationFailureAction ||
                                (actionKey == CompilationBailoutAction && ((BailoutException) cause).isPermanent())) {
                    if (TruffleCompilerRuntime.areTruffleCompilationExceptionsFatal()) {
                        // Get more info for Truffle compilation exceptions
                        // that will cause the VM to exit.
                        return Diagnose;
                    }
                }
            }
            return super.lookupAction(options, actionKey, cause);
        }

        @Override
        protected DebugContext createRetryDebugContext(OptionValues options) {
            return openDebugContext(options, compilationId, compilable);
        }

        @Override
        protected Void handleException(Throwable t) {
            notifyCompilableOfFailure(compilable, t);
            return null;
        }

        @Override
        protected Void performCompilation(DebugContext debug) {
            compileAST(debug, compilable, inliningPlan, compilationId, cancellable, listener);
            return null;
        }
    }

    /**
     * Encapsulates custom tasks done before and after installing code that may completely or
     * partially be the result of compiling some Truffle AST.
     */
    private final class TruffleCodeInstallationTask extends Backend.CodeInstallationTask {

        /**
         * The list of consumers associated with the {@code OptimizedAssumption}s that must be
         * notified once the code is installed.
         */
        private final List<Consumer<OptimizedAssumptionDependency>> optimizedAssumptions = new ArrayList<>();

        @Override
        public void preProcess(CompilationResult result, InstalledCode predefinedInstalledCode) {
            if (result == null || result.getAssumptions() == null) {
                return;
            }
            TruffleCompilerRuntime runtime = TruffleCompilerRuntime.getRuntime();
            ArrayList<Assumption> newAssumptions = new ArrayList<>();
            for (Assumption assumption : result.getAssumptions()) {
                if (assumption != null && assumption instanceof TruffleAssumption) {
                    TruffleAssumption truffleAssumption = (TruffleAssumption) assumption;
                    Consumer<OptimizedAssumptionDependency> dep = runtime.registerOptimizedAssumptionDependency(truffleAssumption.getAssumption());
                    if (dep == null) {
                        // Before bailing out, notify other assumptions waiting
                        // for the code that it will never be installed
                        notifyAssumptions(null);

                        throw new RetryableBailoutException("Assumption invalidated while compiling code: %s", truffleAssumption);
                    }
                    optimizedAssumptions.add(dep);
                } else {
                    newAssumptions.add(assumption);
                }
            }
            result.setAssumptions(newAssumptions.toArray(new Assumption[newAssumptions.size()]));
        }

        @Override
        public void postProcess(InstalledCode installedCode) {
            afterCodeInstallation(installedCode);
            if (!optimizedAssumptions.isEmpty()) {
                OptimizedAssumptionDependency dependency;
                if (installedCode instanceof OptimizedAssumptionDependency) {
                    dependency = (OptimizedAssumptionDependency) installedCode;
                } else if (installedCode instanceof OptimizedAssumptionDependency.Access) {
                    dependency = ((OptimizedAssumptionDependency.Access) installedCode).getDependency();
                } else {
                    dependency = new OptimizedAssumptionDependency() {
                        @Override
                        public void invalidate() {
                            installedCode.invalidate();
                        }

                        @Override
                        public boolean isValid() {
                            return installedCode.isValid();
                        }

                        @Override
                        public boolean reachabilityDeterminesValidity() {
                            return TruffleCompilerImpl.this.reachabilityDeterminesValidity(installedCode);
                        }

                        @Override
                        public String toString() {
                            return installedCode.toString();
                        }
                    };
                }

                notifyAssumptions(dependency);
            }
        }

        @Override
        public void installFailed(Throwable t) {
            notifyAssumptions(null);
        }

        private void notifyAssumptions(OptimizedAssumptionDependency dependency) {
            List<Throwable> errors = null;
            for (Consumer<OptimizedAssumptionDependency> entry : optimizedAssumptions) {
                try {
                    entry.accept(dependency);
                } catch (Throwable t) {
                    if (errors == null) {
                        errors = new ArrayList<>();
                    }
                    errors.add(t);
                }
            }
            if (errors != null) {
                StringBuilder sb = new StringBuilder("There were errors while notifying assumptions:");
                for (Throwable e : errors) {
                    sb.append(System.lineSeparator()).append("  ").append(e);
                }
                throw new RetryableBailoutException(sb.toString());
            }
        }
    }

    /**
     * Notifies this object once {@code installedCode} has been installed in the code cache.
     *
     * @param installedCode code that has just been installed in the code cache
     */
    protected void afterCodeInstallation(InstalledCode installedCode) {
    }

    private class TrufflePostCodeInstallationTaskFactory extends Backend.CodeInstallationTaskFactory {

        @Override
        public Backend.CodeInstallationTask create() {
            return new TruffleCodeInstallationTask();
        }
    }
}
