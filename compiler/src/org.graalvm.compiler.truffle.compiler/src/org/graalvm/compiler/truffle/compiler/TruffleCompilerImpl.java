/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.core.common.CompilationRequestIdentifier.asCompilationRequest;
import static org.graalvm.compiler.phases.OptimisticOptimizations.ALL;
import static org.graalvm.compiler.phases.OptimisticOptimizations.Optimization.RemoveNeverExecutedCode;
import static org.graalvm.compiler.phases.OptimisticOptimizations.Optimization.UseExceptionProbability;
import static org.graalvm.compiler.phases.OptimisticOptimizations.Optimization.UseTypeCheckHints;
import static org.graalvm.compiler.phases.OptimisticOptimizations.Optimization.UseTypeCheckedInlining;
import static org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions.TruffleEnableInfopoints;
import static org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions.TruffleExcludeAssertions;
import static org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions.TruffleInstrumentBoundaries;
import static org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions.TruffleInstrumentBranches;
import static org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions.getValue;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import org.graalvm.compiler.core.common.CompilationIdentifier.Verbosity;
import org.graalvm.compiler.core.common.RetryableBailoutException;
import org.graalvm.compiler.core.common.util.CompilationAlarm;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Scope;
import org.graalvm.compiler.debug.DebugHandlersFactory;
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
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.OptimizedAssumptionDependency;
import org.graalvm.compiler.truffle.common.TruffleCompilation;
import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.TruffleDebugContext;
import org.graalvm.compiler.truffle.common.TruffleDebugJavaMethod;
import org.graalvm.compiler.truffle.common.TruffleInliningPlan;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions.TruffleOptionsOverrideScope;
import org.graalvm.compiler.truffle.compiler.debug.TraceCompilationFailureListener;
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

    protected final Providers lastTierProviders;
    protected final Suites lastTierSuites;
    protected final GraphBuilderConfiguration config;
    protected final LIRSuites lastTierLirSuites;
    protected final Providers firstTierProviders;
    protected final Suites firstTierSuites;
    protected final LIRSuites firstTierLirSuites;
    protected final PartialEvaluator partialEvaluator;
    protected final Backend backend;
    protected final SnippetReflectionProvider snippetReflection;
    protected final TrufflePostCodeInstallationTaskFactory codeInstallationTaskFactory;

    public static final OptimisticOptimizations Optimizations = ALL.remove(
                    UseExceptionProbability,
                    RemoveNeverExecutedCode,
                    UseTypeCheckedInlining,
                    UseTypeCheckHints);

    public TruffleCompilerImpl(TruffleCompilerRuntime runtime,
                    Plugins plugins,
                    Suites lastTierSuites,
                    LIRSuites lastTierLirSuites,
                    Backend backend,
                    Suites firstTierSuites,
                    LIRSuites firstTierLirSuites,
                    Providers firstTierProviders,
                    SnippetReflectionProvider snippetReflection) {
        this.backend = backend;
        this.snippetReflection = snippetReflection;
        this.lastTierProviders = backend.getProviders();
        this.lastTierSuites = lastTierSuites;
        this.lastTierLirSuites = lastTierLirSuites;
        this.codeInstallationTaskFactory = new TrufflePostCodeInstallationTaskFactory();
        backend.addCodeInstallationTask(codeInstallationTaskFactory);

        ResolvedJavaType[] skippedExceptionTypes = getSkippedExceptionTypes(runtime);

        boolean needSourcePositions = TruffleCompilerOptions.getValue(TruffleEnableInfopoints) ||
                        TruffleCompilerOptions.getValue(TruffleInstrumentBranches) || TruffleCompilerOptions.getValue(TruffleInstrumentBoundaries);
        GraphBuilderConfiguration baseConfig = GraphBuilderConfiguration.getDefault(new Plugins(plugins)).withNodeSourcePosition(needSourcePositions);
        this.config = baseConfig.withSkippedExceptionTypes(skippedExceptionTypes).withOmitAssertions(TruffleCompilerOptions.getValue(TruffleExcludeAssertions)).withBytecodeExceptionMode(
                        BytecodeExceptionMode.ExplicitOnly);

        this.partialEvaluator = createPartialEvaluator();
        this.firstTierProviders = firstTierProviders;
        this.firstTierSuites = firstTierSuites;
        this.firstTierLirSuites = firstTierLirSuites;
    }

    private ResolvedJavaType[] getSkippedExceptionTypes(TruffleCompilerRuntime runtime) {
        final MetaAccessProvider metaAccess = lastTierProviders.getMetaAccess();
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
     * Gets the compiler backend used for Truffle compilation.
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

    /**
     * Creates a new {@link CompilationIdentifier} for {@code compilable}.
     *
     * Implementations of this method must guarantee that the {@link Verbosity#ID} for each returned
     * value is unique.
     */
    public abstract TruffleCompilationIdentifier createCompilationIdentifier(CompilableTruffleAST compilable);

    protected abstract DebugContext createDebugContext(OptionValues options, CompilationIdentifier compilationId, CompilableTruffleAST compilable, PrintStream logStream);

    @Override
    public final TruffleCompilation openCompilation(CompilableTruffleAST compilable) {
        return createCompilationIdentifier(compilable);
    }

    @SuppressWarnings("try")
    @Override
    public final TruffleDebugContext openDebugContext(Map<String, Object> options, TruffleCompilation compilation) {
        try (TruffleOptionsOverrideScope s = withOptions(options)) {
            DebugContext debugContext;
            if (compilation == null) {
                debugContext = DebugContext.create(TruffleCompilerOptions.getOptions(), DebugHandlersFactory.LOADER);
            } else {
                TruffleCompilationIdentifier ident = asTruffleCompilationIdentifier(compilation);
                CompilableTruffleAST compilable = ident.getCompilable();
                debugContext = createDebugContext(TruffleCompilerOptions.getOptions(), ident, compilable, DebugContext.DEFAULT_LOG_STREAM);
            }
            return new TruffleDebugContextImpl(debugContext);
        }
    }

    private static TruffleCompilationIdentifier asTruffleCompilationIdentifier(TruffleCompilation compilation) {
        if (compilation == null || compilation instanceof TruffleCompilationIdentifier) {
            return (TruffleCompilationIdentifier) compilation;
        } else {
            throw new IllegalArgumentException("The compilation must be instanceof " + TruffleCompilationIdentifier.class.getSimpleName() + ", got: " + compilation.getClass());
        }
    }

    /**
     * Opens a scope that overrides the default Truffle compiler options with values from
     * {@code optionsMap} if it is not empty. Otherwise no scope is opened and {@code null} is
     * returned.
     */
    private static TruffleOptionsOverrideScope withOptions(final Map<String, Object> optionsMap) {
        return optionsMap.isEmpty() ? null : TruffleCompilerOptions.overrideOptions(optionsMap);
    }

    @Override
    @SuppressWarnings("try")
    public final void doCompile(TruffleDebugContext truffleDebug,
                    TruffleCompilation compilation,
                    Map<String, Object> optionsMap,
                    TruffleInliningPlan inliningPlan,
                    TruffleCompilationTask task,
                    TruffleCompilerListener inListener) {
        Objects.requireNonNull(compilation, "Compilation must be non null.");
        try (TruffleOptionsOverrideScope optionsScope = withOptions(optionsMap)) {
            TruffleCompilationIdentifier compilationId = asTruffleCompilationIdentifier(compilation);
            CompilableTruffleAST compilable = compilationId.getCompilable();
            final OptionValues optionValues = TruffleCompilerOptions.getOptions();
            boolean usingCallersDebug = truffleDebug instanceof TruffleDebugContextImpl;
            final DebugContext graalDebug = usingCallersDebug ? ((TruffleDebugContextImpl) truffleDebug).debugContext
                            : createDebugContext(optionValues, compilationId, compilable, DebugContext.DEFAULT_LOG_STREAM);
            try (DebugContext debugToClose = usingCallersDebug ? null : graalDebug;
                            DebugContext.Scope s = maybeOpenTruffleScope(compilable, graalDebug)) {
                final TruffleCompilerListener listener = inListener == null ? null : new TruffleCompilerListenerPair(new TraceCompilationFailureListener(), inListener);
                new TruffleCompilationWrapper(
                                getDebugOutputDirectory(),
                                getCompilationProblemsPerAction(),
                                compilable,
                                task == null ? null : new CancellableTruffleCompilationTask(task),
                                inliningPlan,
                                compilationId,
                                listener).run(graalDebug);
            } catch (Throwable e) {
                notifyCompilableOfFailure(compilable, e);
            }
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
     * @param task an object polled during the compilation process to
     *            {@linkplain CancellationBailoutException abort} early if the thread owning the
     *            task requests it
     * @param listener
     */
    @SuppressWarnings("try")
    public void compileAST(DebugContext debug,
                    final CompilableTruffleAST compilable,
                    TruffleInliningPlan inliningPlan,
                    CompilationIdentifier compilationId,
                    CancellableTruffleCompilationTask task,
                    TruffleCompilerListener listener) {
        final CompilationPrinter printer = CompilationPrinter.begin(TruffleCompilerOptions.getOptions(), compilationId, new TruffleDebugJavaMethod(compilable), INVOCATION_ENTRY_BCI);
        StructuredGraph graph = null;

        try (CompilationAlarm alarm = CompilationAlarm.trackCompilationPeriod(TruffleCompilerOptions.getOptions())) {
            PhaseSuite<HighTierContext> graphBuilderSuite = createGraphBuilderSuite();

            SpeculationLog speculationLog = compilable.getCompilationSpeculationLog();
            if (speculationLog != null) {
                speculationLog.collectFailedSpeculations();
            }

            try (DebugCloseable a = PartialEvaluationTime.start(debug); DebugCloseable c = PartialEvaluationMemUse.start(debug)) {
                graph = partialEvaluator.createGraph(debug, compilable, inliningPlan, AllowAssumptions.YES, compilationId, speculationLog, task);
            }

            // Check if the task has been cancelled
            if (task != null && task.isCancelled()) {
                return;
            }

            if (listener != null) {
                listener.onTruffleTierFinished(compilable, inliningPlan, new GraphInfoImpl(graph));
            }
            // The Truffle compiler owns the last 2 characters of the compilation name, and uses
            // them to encode the compilation tier, so escaping the target name is not necessary.
            String compilationName = compilable.toString() + (task != null && task.isFirstTier() ? TruffleCompiler.FIRST_TIER_COMPILATION_SUFFIX : TruffleCompiler.SECOND_TIER_COMPILATION_SUFFIX);
            CompilationResult compilationResult = compilePEGraph(graph, compilationName, graphBuilderSuite, compilable, asCompilationRequest(compilationId), listener, task);
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
    public CompilationResult compilePEGraph(StructuredGraph graph,
                    String name,
                    PhaseSuite<HighTierContext> graphBuilderSuite,
                    CompilableTruffleAST compilable,
                    CompilationRequest compilationRequest,
                    TruffleCompilerListener listener,
                    TruffleCompilationTask task) {
        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope s = debug.scope("TruffleFinal")) {
            debug.dump(DebugContext.BASIC_LEVEL, graph, "After TruffleTier");
        } catch (Throwable e) {
            throw debug.handle(e);
        }

        CompilationResult result = null;

        try (DebugCloseable a = CompilationTime.start(debug);
                        DebugContext.Scope s = debug.scope("TruffleGraal.GraalCompiler", graph, lastTierProviders.getCodeCache());
                        DebugCloseable c = CompilationMemUse.start(debug)) {
            Suites selectedSuites = lastTierSuites;
            LIRSuites selectedLirSuites = lastTierLirSuites;
            Providers selectedProviders = lastTierProviders;
            if (task != null && !task.isLastTier()) {
                selectedSuites = firstTierSuites;
                selectedLirSuites = firstTierLirSuites;
                selectedProviders = firstTierProviders;
            }
            CompilationResult compilationResult = createCompilationResult(name, graph.compilationId(), compilable);
            result = GraalCompiler.compileGraph(graph, graph.method(), selectedProviders, backend, graphBuilderSuite, Optimizations, graph.getProfilingInfo(), selectedSuites, selectedLirSuites,
                            compilationResult, CompilationResultBuilderFactory.Default, false);
        } catch (Throwable e) {
            throw debug.handle(e);
        }

        if (listener != null) {
            listener.onGraalTierFinished(compilable, new GraphInfoImpl(graph));
        }

        try (DebugCloseable a = CodeInstallationTime.start(debug); DebugCloseable c = CodeInstallationMemUse.start(debug)) {
            InstalledCode installedCode = createInstalledCode(compilable);
            assert graph.getSpeculationLog() == result.getSpeculationLog();
            backend.createInstalledCode(debug, graph.method(), compilationRequest, result, installedCode, false);
        } catch (Throwable e) {
            throw debug.handle(e);
        }

        return result;
    }

    protected abstract InstalledCode createInstalledCode(CompilableTruffleAST compilable);

    /**
     * @see OptimizedAssumptionDependency#soleExecutionEntryPoint()
     *
     * @param installedCode
     */
    protected boolean soleExecutionEntryPoint(InstalledCode installedCode) {
        return true;
    }

    /**
     * Calls {@link System#exit(int)} in the runtime embedding the Graal compiler. This will be a
     * different runtime than Graal's runtime in the case of libgraal.
     */
    protected void exitHostVM(int status) {
        System.exit(status);
    }

    /**
     * Creates the {@link CompilationResult} to be used for a Truffle compilation.
     */
    protected abstract CompilationResult createCompilationResult(String name, CompilationIdentifier compilationIdentifier, CompilableTruffleAST compilable);

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
        private final CancellableTruffleCompilationTask task;
        private final TruffleCompilerListener listener;
        private final CompilationIdentifier compilationId;

        private TruffleCompilationWrapper(DiagnosticsOutputDirectory outputDirectory,
                        Map<ExceptionAction, Integer> problemsHandledPerAction,
                        CompilableTruffleAST optimizedCallTarget,
                        CancellableTruffleCompilationTask task,
                        TruffleInliningPlan inliningPlan,
                        CompilationIdentifier compilationId,
                        TruffleCompilerListener listener) {
            super(outputDirectory, problemsHandledPerAction);
            this.compilable = optimizedCallTarget;
            this.inliningPlan = inliningPlan;
            this.task = task;
            this.listener = listener;
            this.compilationId = compilationId;
        }

        @Override
        public String toString() {
            return compilable.toString();
        }

        @Override
        protected ExceptionAction lookupAction(OptionValues options, Throwable cause) {
            // Respect current action if it has been explicitly set.
            if (!(cause instanceof BailoutException) || ((BailoutException) cause).isPermanent()) {
                if (TruffleCompilerOptions.areTruffleCompilationExceptionsFatal()) {
                    // Get more info for Truffle compilation exceptions
                    // that will cause the VM to exit.
                    return Diagnose;
                }
            }
            return super.lookupAction(options, cause);
        }

        @Override
        protected DebugContext createRetryDebugContext(DebugContext initialDebug, OptionValues options, PrintStream logStream) {
            return createDebugContext(options, compilationId, compilable, logStream);
        }

        @Override
        protected void exitHostVM(int status) {
            TruffleCompilerImpl.this.exitHostVM(status);
        }

        @Override
        protected Void handleException(Throwable t) {
            notifyCompilableOfFailure(compilable, t);
            return null;
        }

        @Override
        protected Void performCompilation(DebugContext debug) {
            compileAST(debug, compilable, inliningPlan, compilationId, task, listener);
            return null;
        }
    }

    /**
     * Gets the {@link CompilableTruffleAST} associated with {@code result}.
     *
     * @param result a {@link CompilationResult} that may have a non-null
     *            {@link CompilableTruffleAST} associated with it
     */
    protected CompilableTruffleAST getCompilable(CompilationResult result) {
        return null;
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
        public void preProcess(CompilationResult result) {
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
        public void postProcess(CompilationResult compilationResult, InstalledCode installedCode) {
            afterCodeInstallation(compilationResult, installedCode);
            if (!optimizedAssumptions.isEmpty()) {
                OptimizedAssumptionDependency dependency;
                if (installedCode instanceof OptimizedAssumptionDependency) {
                    dependency = (OptimizedAssumptionDependency) installedCode;
                } else if (installedCode instanceof OptimizedAssumptionDependency.Access) {
                    dependency = ((OptimizedAssumptionDependency.Access) installedCode).getDependency();
                } else {
                    CompilableTruffleAST compilable = getCompilable(compilationResult);
                    if (compilable instanceof OptimizedAssumptionDependency) {
                        dependency = (OptimizedAssumptionDependency) compilable;
                    } else {
                        // This handles the case where a normal Graal compilation
                        // inlines a call to a compile-time constant Truffle node.
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
                            public boolean soleExecutionEntryPoint() {
                                return TruffleCompilerImpl.this.soleExecutionEntryPoint(installedCode);
                            }

                            @Override
                            public String toString() {
                                return installedCode.toString();
                            }
                        };
                    }
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
     * @param result the {@link CompilationResult result} of compilation
     * @param installedCode code that has just been installed in the code cache
     */
    protected void afterCodeInstallation(CompilationResult result, InstalledCode installedCode) {
    }

    public final SnippetReflectionProvider getSnippetReflection() {
        return snippetReflection;
    }

    private class TrufflePostCodeInstallationTaskFactory extends Backend.CodeInstallationTaskFactory {

        @Override
        public Backend.CodeInstallationTask create() {
            return new TruffleCodeInstallationTask();
        }
    }

    static final class CancellableTruffleCompilationTask implements TruffleCompilationTask, Cancellable {
        private final TruffleCompilationTask delegate;

        CancellableTruffleCompilationTask(TruffleCompilationTask delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isCancelled() {
            return delegate.isCancelled();
        }

        @Override
        public boolean isLastTier() {
            return delegate.isLastTier();
        }
    }

    private static final class TruffleCompilerListenerPair implements TruffleCompilerListener {

        private final TruffleCompilerListener first;
        private final TruffleCompilerListener second;

        TruffleCompilerListenerPair(TruffleCompilerListener first, TruffleCompilerListener second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void onGraalTierFinished(CompilableTruffleAST compilable, GraphInfo graph) {
            first.onGraalTierFinished(compilable, graph);
            second.onGraalTierFinished(compilable, graph);
        }

        @Override
        public void onTruffleTierFinished(CompilableTruffleAST compilable, TruffleInliningPlan inliningPlan, GraphInfo graph) {
            first.onTruffleTierFinished(compilable, inliningPlan, graph);
            second.onTruffleTierFinished(compilable, inliningPlan, graph);
        }

        @Override
        public void onSuccess(CompilableTruffleAST compilable, TruffleInliningPlan inliningPlan, GraphInfo graphInfo, CompilationResultInfo compilationResultInfo) {
            first.onSuccess(compilable, inliningPlan, graphInfo, compilationResultInfo);
            second.onSuccess(compilable, inliningPlan, graphInfo, compilationResultInfo);
        }

        @Override
        public void onFailure(CompilableTruffleAST compilable, String reason, boolean bailout, boolean permanentBailout) {
            first.onFailure(compilable, reason, bailout, permanentBailout);
            second.onFailure(compilable, reason, bailout, permanentBailout);
        }
    }
}
