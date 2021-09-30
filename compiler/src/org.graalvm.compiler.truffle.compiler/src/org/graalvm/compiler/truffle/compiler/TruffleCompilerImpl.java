/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationExceptionsAreFatal;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationFailureAction;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.ExcludeAssertions;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.FirstTierUseEconomy;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.PerformanceWarningsAreFatal;

import java.io.PrintStream;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ReadOnlyBufferException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.CompilationPrinter;
import org.graalvm.compiler.core.CompilationWrapper;
import org.graalvm.compiler.core.CompilationWrapper.ExceptionAction;
import org.graalvm.compiler.core.GraalCompiler;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.CompilationIdentifier.Verbosity;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.RetryableBailoutException;
import org.graalvm.compiler.core.common.util.CompilationAlarm;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Builder;
import org.graalvm.compiler.debug.DebugContext.Scope;
import org.graalvm.compiler.debug.DiagnosticsOutputDirectory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.LogStream;
import org.graalvm.compiler.debug.MemUseTrackerKey;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.debug.TimerKey;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.nodes.Cancellable;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.BytecodeExceptionMode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.OptimizedAssumptionDependency;
import org.graalvm.compiler.truffle.common.TruffleCompilation;
import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.TruffleDebugContext;
import org.graalvm.compiler.truffle.common.TruffleDebugJavaMethod;
import org.graalvm.compiler.truffle.common.TruffleInliningData;
import org.graalvm.compiler.truffle.compiler.nodes.TruffleAssumption;
import org.graalvm.compiler.truffle.compiler.phases.InstrumentPhase;
import org.graalvm.compiler.truffle.options.OptionValuesImpl;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;

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
public abstract class TruffleCompilerImpl implements TruffleCompilerBase {

    protected TruffleCompilerConfiguration config;
    protected final GraphBuilderConfiguration builderConfig;
    protected final PartialEvaluator partialEvaluator;
    protected final TrufflePostCodeInstallationTaskFactory codeInstallationTaskFactory;
    private volatile ExpansionStatistics expansionStatistics;
    private volatile boolean expansionStatisticsInitialized;
    private volatile boolean initialized;

    public static final OptimisticOptimizations Optimizations = ALL.remove(
                    UseExceptionProbability,
                    RemoveNeverExecutedCode,
                    UseTypeCheckedInlining,
                    UseTypeCheckHints);

    public TruffleCompilerImpl(TruffleCompilerConfiguration config) {
        this.config = config;
        this.codeInstallationTaskFactory = new TrufflePostCodeInstallationTaskFactory();
        for (Backend backend : config.backends()) {
            backend.addCodeInstallationTask(codeInstallationTaskFactory);
        }

        ResolvedJavaType[] skippedExceptionTypes = getSkippedExceptionTypes(this.config.runtime());

        GraphBuilderConfiguration baseConfig = GraphBuilderConfiguration.getDefault(new Plugins(this.config.plugins()));
        this.builderConfig = baseConfig //
                        .withSkippedExceptionTypes(skippedExceptionTypes) //
                        .withOmitAssertions(ExcludeAssertions.getDefaultValue()) //
                        .withBytecodeExceptionMode(BytecodeExceptionMode.ExplicitOnly) //
                        // Avoid deopt loops caused by unresolved constant pool entries when parsed
                        // graphs are cached across compilations.
                        .withEagerResolving(true);

        this.partialEvaluator = createPartialEvaluator(config);
    }

    private ResolvedJavaType[] getSkippedExceptionTypes(TruffleCompilerRuntime runtime) {
        final MetaAccessProvider metaAccess = this.config.lastTier().providers().getMetaAccess();
        ResolvedJavaType[] head = metaAccess.lookupJavaTypes(new Class<?>[]{
                        ArithmeticException.class,
                        IllegalArgumentException.class,
                        IllegalStateException.class,
                        VirtualMachineError.class,
                        IndexOutOfBoundsException.class,
                        ClassCastException.class,
                        BufferUnderflowException.class,
                        BufferOverflowException.class,
                        ReadOnlyBufferException.class,
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

    public TruffleCompilerConfiguration getConfig() {
        return config;
    }

    /**
     * Creates the partial evaluator used by this compiler.
     */
    protected abstract PartialEvaluator createPartialEvaluator(TruffleCompilerConfiguration configuration);

    public static final TimerKey PartialEvaluationTime = DebugContext.timer("PartialEvaluationTime").doc("Total time spent in the Truffle tier.");
    public static final TimerKey CompilationTime = DebugContext.timer("CompilationTime");
    public static final TimerKey CodeInstallationTime = DebugContext.timer("CodeInstallation");
    public static final TimerKey EncodedGraphCacheEvictionTime = DebugContext.timer("EncodedGraphCacheEvictionTime");

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
    @SuppressWarnings("try")
    public final TruffleCompilation openCompilation(CompilableTruffleAST compilable) {
        TTY.Filter ttyFilter = new TTY.Filter(new LogStream(new TTYToPolyglotLoggerBridge(compilable)));
        try {
            TruffleCompilationIdentifier id = createCompilationIdentifier(compilable);
            return new TruffleCompilationImpl(id, ttyFilter);
        } catch (Throwable t) {
            if (ttyFilter != null) {
                ttyFilter.close();
            }
            throw t;
        }
    }

    @Override
    public final TruffleDebugContext openDebugContext(Map<String, Object> options, TruffleCompilation compilation) {
        OptionValues graalOptions = TruffleCompilerRuntime.getRuntime().getGraalOptions(OptionValues.class);
        final DebugContext debugContext;
        if (compilation == null) {
            debugContext = new Builder(graalOptions).build();
        } else {
            TruffleCompilationIdentifier ident = asTruffleCompilationIdentifier(compilation);
            CompilableTruffleAST compilable = ident.getCompilable();
            org.graalvm.options.OptionValues truffleOptions = getOptionsForCompiler(options);
            if (ExpansionStatistics.isEnabled(truffleOptions)) {
                graalOptions = enableNodeSourcePositions(graalOptions);
            }
            debugContext = createDebugContext(graalOptions, ident, compilable, DebugContext.getDefaultLogStream());
        }
        return new TruffleDebugContextImpl(debugContext);
    }

    public static PartialEvaluatorConfiguration createPartialEvaluatorConfiguration(String name) {
        for (PartialEvaluatorConfiguration candidate : GraalServices.load(PartialEvaluatorConfiguration.class)) {
            if (candidate.name().equals(name)) {
                return candidate;
            }
        }
        throw new GraalError("Cannot find partial evaluation configuration: %s", name);
    }

    private static OptionValues enableNodeSourcePositions(OptionValues values) {
        if (GraalOptions.TrackNodeSourcePosition.getValue(values)) {
            // already enabled nothing to do
            return values;
        } else {
            return new OptionValues(values, GraalOptions.TrackNodeSourcePosition, Boolean.TRUE);
        }
    }

    private static TruffleCompilationIdentifier asTruffleCompilationIdentifier(TruffleCompilation compilation) {
        if (compilation == null) {
            return null;
        } else if (compilation instanceof TruffleCompilationImpl) {
            return ((TruffleCompilationImpl) compilation).delegate;
        } else if (compilation instanceof TruffleCompilationIdentifier) {
            return (TruffleCompilationIdentifier) compilation;
        } else {
            throw new IllegalArgumentException("The compilation must be instanceof " + TruffleCompilationIdentifier.class.getSimpleName() + ", got: " + compilation.getClass());
        }
    }

    @Override
    @SuppressWarnings("try")
    public final void doCompile(TruffleDebugContext truffleDebug,
                    TruffleCompilation compilation,
                    Map<String, Object> optionsMap,
                    TruffleCompilationTask task,
                    TruffleCompilerListener inListener) {
        Objects.requireNonNull(compilation, "Compilation must be non null.");
        org.graalvm.options.OptionValues options = getOptionsForCompiler(optionsMap);
        TruffleCompilationIdentifier compilationId = asTruffleCompilationIdentifier(compilation);
        CompilableTruffleAST compilable = compilationId.getCompilable();
        boolean usingCallersDebug = truffleDebug instanceof TruffleDebugContextImpl;
        if (usingCallersDebug) {
            final DebugContext callerDebug = ((TruffleDebugContextImpl) truffleDebug).debugContext;
            try (DebugContext.Scope s = maybeOpenTruffleScope(compilable, callerDebug)) {
                actuallyCompile(options, task, inListener, compilationId, compilable, callerDebug);
            } catch (Throwable e) {
                notifyCompilableOfFailure(compilable, e, isSuppressedFailure(compilable, e));
            }
        } else {
            OptionValues debugContextOptionValues = TruffleCompilerRuntime.getRuntime().getGraalOptions(OptionValues.class);
            try (DebugContext graalDebug = createDebugContext(debugContextOptionValues, compilationId, compilable, DebugContext.getDefaultLogStream());
                            DebugContext.Scope s = maybeOpenTruffleScope(compilable, graalDebug)) {
                actuallyCompile(options, task, inListener, compilationId, compilable, graalDebug);
            } catch (Throwable e) {
                notifyCompilableOfFailure(compilable, e, isSuppressedFailure(compilable, e));
            }
        }
    }

    @Override
    @SuppressWarnings("try")
    public void initialize(Map<String, Object> optionsMap, CompilableTruffleAST compilable, boolean firstInitialization) {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    try (TTY.Filter ttyFilter = new TTY.Filter(new LogStream(new TTYToPolyglotLoggerBridge(compilable)))) {
                        final org.graalvm.options.OptionValues options = getOptionsForCompiler(optionsMap);
                        partialEvaluator.initialize(options);
                        initialized = true;

                        if (!FirstTierUseEconomy.getValue(options)) {
                            config = config.withFirstTier(config.lastTier());
                        }
                    }
                }
            }
        }
    }

    private void actuallyCompile(org.graalvm.options.OptionValues options, TruffleCompilationTask task, TruffleCompilerListener listener,
                    TruffleCompilationIdentifier compilationId,
                    CompilableTruffleAST compilable, DebugContext graalDebug) {
        final TruffleCompilationWrapper truffleCompilationWrapper = new TruffleCompilationWrapper(
                        options,
                        getDebugOutputDirectory(),
                        getCompilationProblemsPerAction(),
                        compilable,
                        new CancellableTruffleCompilationTask(task),
                        compilationId,
                        listener);
        truffleCompilationWrapper.run(graalDebug);
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

    private static void notifyCompilableOfFailure(CompilableTruffleAST compilable, Throwable e, boolean silent) {
        Throwable error = e;
        boolean graphTooBig = false;
        if (error instanceof GraphTooBigBailoutException) {
            error = error.getCause();
            graphTooBig = true;
        }
        BailoutException bailout = error instanceof BailoutException ? (BailoutException) error : null;
        boolean permanentBailout = bailout != null ? bailout.isPermanent() : false;
        Throwable finalError = error;
        compilable.onCompilationFailed(() -> CompilableTruffleAST.serializeException(finalError), silent, bailout != null, permanentBailout, graphTooBig);
    }

    @Override
    public void shutdown() {
        InstrumentPhase.InstrumentationConfiguration cfg = partialEvaluator.instrumentationCfg;
        if (cfg != null && (cfg.instrumentBoundaries || cfg.instrumentBranches)) {
            InstrumentPhase.Instrumentation ins = this.partialEvaluator.instrumentation;
            if (ins != null) {
                ins.dumpAccessTable();
            }
        }
        ExpansionStatistics histogram = this.expansionStatistics;
        if (histogram != null) {
            histogram.onShutdown();
            this.expansionStatistics = null;
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

    final ExpansionStatistics getExpansionHistogram(org.graalvm.options.OptionValues options) {
        ExpansionStatistics local = expansionStatistics;
        if (local == null && !expansionStatisticsInitialized) {
            synchronized (this) {
                local = expansionStatistics;
                if (local == null) {
                    this.expansionStatistics = local = ExpansionStatistics.create(options);
                    this.expansionStatisticsInitialized = true;
                }
            }
        }
        return local;
    }

    /**
     * Compiles a Truffle AST. If compilation succeeds, the AST will have compiled code associated
     * with it that can be executed instead of interpreting the AST.
     *
     * @param options the values of {@link PolyglotCompilerOptions}.
     * @param compilable representation of the AST to be compiled
     * @param compilationId identifier to be used for the compilation
     * @param task an object that holds information about the compilation process itself (e.g. which
     *            tier, was the compilation canceled)
     * @param listener
     */
    @SuppressWarnings("try")
    public void compileAST(org.graalvm.options.OptionValues options,
                    DebugContext debug,
                    final CompilableTruffleAST compilable,
                    CompilationIdentifier compilationId,
                    CancellableTruffleCompilationTask task,
                    TruffleCompilerListener listener) {
        final CompilationPrinter printer = CompilationPrinter.begin(debug.getOptions(), compilationId, new TruffleDebugJavaMethod(compilable), INVOCATION_ENTRY_BCI);
        StructuredGraph graph = null;

        try (CompilationAlarm alarm = CompilationAlarm.trackCompilationPeriod(debug.getOptions())) {
            PhaseSuite<HighTierContext> graphBuilderSuite = createGraphBuilderSuite(task.isFirstTier() ? config.firstTier() : config.lastTier());

            ExpansionStatistics statistics = getExpansionHistogram(options);
            SpeculationLog speculationLog = compilable.getCompilationSpeculationLog();
            if (speculationLog != null) {
                speculationLog.collectFailedSpeculations();
            }

            try (DebugCloseable a = PartialEvaluationTime.start(debug); DebugCloseable c = PartialEvaluationMemUse.start(debug)) {
                PartialEvaluator.Request request = partialEvaluator.new Request(options, debug, compilable, partialEvaluator.rootForCallTarget(compilable),
                                compilationId, speculationLog, task);
                graph = partialEvaluator.evaluate(request);
                if (statistics != null) {
                    statistics.afterPartialEvaluation(request.compilable, request.graph);
                }
            }

            // Check if the task has been cancelled
            if (task.isCancelled()) {
                return;
            }
            if (statistics != null) {
                statistics.afterTruffleTier(compilable, graph);
            }
            if (listener != null) {
                listener.onTruffleTierFinished(compilable, task.inliningData(), new GraphInfoImpl(graph));
            }
            // The Truffle compiler owns the last 2 characters of the compilation name, and uses
            // them to encode the compilation tier, so escaping the target name is not necessary.
            String compilationName = compilable.toString() + (task.isFirstTier() ? TruffleCompiler.FIRST_TIER_COMPILATION_SUFFIX : TruffleCompiler.SECOND_TIER_COMPILATION_SUFFIX);
            CompilationResult compilationResult = compilePEGraph(graph, compilationName, graphBuilderSuite, compilable, asCompilationRequest(compilationId), listener, task);
            if (statistics != null) {
                statistics.afterLowTier(compilable, graph);
            }
            if (listener != null) {
                listener.onSuccess(compilable, task.inliningData(), new GraphInfoImpl(graph), new CompilationResultInfoImpl(compilationResult), task.tier());
            }

            // Partial evaluation and installation are included in
            // compilation time and memory usage reported by printer
            printer.finish(compilationResult);
        } catch (Throwable t) {
            if (t instanceof BailoutException) {
                handleBailout(debug, graph, (BailoutException) t);
            }
            // Note: If the compiler cancels the compilation with a bailout exception, then the
            // graph is null
            if (listener != null) {
                BailoutException bailout = t instanceof BailoutException ? (BailoutException) t : null;
                boolean permanentBailout = bailout != null ? bailout.isPermanent() : false;
                listener.onFailure(compilable, t.toString(), bailout != null, permanentBailout, task.tier());
            }
            throw t;
        }
    }

    /**
     * Hook for processing bailout exceptions.
     *
     * @param graph graph producing the bailout, can be null
     * @param bailout {@link BailoutException to process}
     */
    @SuppressWarnings("unused")
    protected void handleBailout(DebugContext debug, StructuredGraph graph, BailoutException bailout) {
        // nop
    }

    /**
     * Compiles a graph produced by {@link PartialEvaluator#evaluate partial evaluation}.
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

        final TruffleTierConfiguration tier = task.isFirstTier() ? config.firstTier() : config.lastTier();
        try (DebugCloseable a = CompilationTime.start(debug);
                        DebugContext.Scope s = debug.scope("TruffleGraal.GraalCompiler", graph, tier.providers().getCodeCache());
                        DebugCloseable c = CompilationMemUse.start(debug)) {
            Suites selectedSuites = tier.suites();
            LIRSuites selectedLirSuites = tier.lirSuites();
            Providers selectedProviders = tier.providers();
            CompilationResult compilationResult = createCompilationResult(name, graph.compilationId(), compilable);
            result = GraalCompiler.compileGraph(graph, graph.method(), selectedProviders, tier.backend(), graphBuilderSuite, Optimizations, graph.getProfilingInfo(), selectedSuites,
                            selectedLirSuites, compilationResult, CompilationResultBuilderFactory.Default, false);
        } catch (Throwable e) {
            throw debug.handle(e);
        }

        if (listener != null) {
            listener.onGraalTierFinished(compilable, new GraphInfoImpl(graph));
        }

        try (DebugCloseable a = CodeInstallationTime.start(debug); DebugCloseable c = CodeInstallationMemUse.start(debug)) {
            InstalledCode installedCode = createInstalledCode(compilable);
            assert graph.getSpeculationLog() == result.getSpeculationLog();
            tier.backend().createInstalledCode(debug, graph.method(), compilationRequest, result, installedCode, false);
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

    public abstract PhaseSuite<HighTierContext> createGraphBuilderSuite(TruffleTierConfiguration tier);

    @Override
    public PartialEvaluator getPartialEvaluator() {
        return partialEvaluator;
    }

    public CompilableTruffleAST asCompilableTruffleAST(JavaConstant constant) {
        return config.snippetReflection().asObject(CompilableTruffleAST.class, constant);
    }

    /**
     * Wrapper for performing a Truffle compilation that can retry upon failure.
     */
    private final class TruffleCompilationWrapper extends CompilationWrapper<Void> {
        private final CompilableTruffleAST compilable;
        private final CancellableTruffleCompilationTask task;
        private final TruffleCompilerListener listener;
        private final CompilationIdentifier compilationId;
        private final org.graalvm.options.OptionValues options;
        private boolean silent;

        private TruffleCompilationWrapper(
                        org.graalvm.options.OptionValues options,
                        DiagnosticsOutputDirectory outputDirectory,
                        Map<ExceptionAction, Integer> problemsHandledPerAction,
                        CompilableTruffleAST optimizedCallTarget,
                        CancellableTruffleCompilationTask task,
                        CompilationIdentifier compilationId,
                        TruffleCompilerListener listener) {
            super(outputDirectory, problemsHandledPerAction);
            this.options = options;
            this.compilable = optimizedCallTarget;
            this.task = task;
            this.listener = listener;
            this.compilationId = compilationId;
        }

        @Override
        public String toString() {
            return compilable.toString();
        }

        @Override
        protected ExceptionAction lookupAction(OptionValues compilerOptions, Throwable cause) {
            // Respect current action if it has been explicitly set.
            if (!(cause instanceof BailoutException) || ((BailoutException) cause).isPermanent()) {
                if (areTruffleCompilationExceptionsFatal(options) ||
                                options.get(CompilationFailureAction) == PolyglotCompilerOptions.ExceptionAction.Diagnose) {
                    // Get more info for Truffle compilation exceptions
                    // that will cause the VM to exit.
                    return Diagnose;
                }
            }
            return super.lookupAction(compilerOptions, cause);
        }

        @Override
        protected DebugContext createRetryDebugContext(DebugContext initialDebug, OptionValues compilerOptions, PrintStream logStream) {
            listener.onCompilationRetry(compilable, task);
            return createDebugContext(compilerOptions, compilationId, compilable, logStream);
        }

        @Override
        protected void exitHostVM(int status) {
            TruffleCompilerImpl.this.exitHostVM(status);
        }

        @Override
        protected Void handleException(Throwable t) {
            notifyCompilableOfFailure(compilable, t, silent);
            return null;
        }

        @Override
        protected Void performCompilation(DebugContext debug) {
            compileAST(options, debug, compilable, compilationId, task, listener);
            return null;
        }

        /**
         * Determines whether an exception during a Truffle compilation should result in calling
         * {@link System#exit(int)}.
         */
        private boolean areTruffleCompilationExceptionsFatal(org.graalvm.options.OptionValues optionValues) {
            /*
             * This is duplicated in TruffleRuntimeOptions#areTruffleCompilationExceptionsFatal.
             */
            boolean compilationExceptionsAreFatal = optionValues.get(CompilationExceptionsAreFatal);
            boolean performanceWarningsAreFatal = !optionValues.get(PerformanceWarningsAreFatal).isEmpty();
            boolean exitVM = optionValues.get(CompilationFailureAction) == PolyglotCompilerOptions.ExceptionAction.ExitVM;
            return compilationExceptionsAreFatal || performanceWarningsAreFatal || exitVM;
        }

        @Override
        protected Void onCompilationFailure(CompilationWrapper<Void>.Failure failure) {
            silent = isSuppressedFailure(compilable, failure.cause);
            failure.handle(silent);
            return null;
        }
    }

    /**
     * Handles a Truffle compilation failure by calling {@code failure.handle()}.
     */
    private static boolean isSuppressedFailure(CompilableTruffleAST compilable, Throwable cause) {
        return TruffleCompilerRuntime.getRuntime().isSuppressedFailure(
                        compilable, () -> CompilableTruffleAST.serializeException(cause));
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
                            public void onAssumptionInvalidated(Object source, CharSequence reason) {
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

    @Override
    public final SnippetReflectionProvider getSnippetReflection() {
        return config.snippetReflection();
    }

    /**
     * Converts the values of {@link PolyglotCompilerOptions} passed to the
     * {@link TruffleCompiler#doCompile} as a {@link Map} into
     * {@link org.graalvm.options.OptionValues}.
     */
    public static org.graalvm.options.OptionValues getOptionsForCompiler(Map<String, Object> options) {
        EconomicMap<org.graalvm.options.OptionKey<?>, Object> parsedOptions = EconomicMap.create(Equivalence.IDENTITY);
        OptionDescriptors descriptors = PolyglotCompilerOptions.getDescriptors();
        for (Map.Entry<String, Object> e : options.entrySet()) {
            final OptionDescriptor descriptor = descriptors.get(e.getKey());
            final org.graalvm.options.OptionKey<?> k = descriptor != null ? descriptor.getKey() : null;
            if (k != null) {
                Object value = e.getValue();
                if (value.getClass() == String.class) {
                    value = descriptor.getKey().getType().convert((String) e.getValue());
                }
                parsedOptions.put(k, value);
            }
        }
        return new OptionValuesImpl(descriptors, parsedOptions);
    }

    private class TrufflePostCodeInstallationTaskFactory extends Backend.CodeInstallationTaskFactory {

        @Override
        public Backend.CodeInstallationTask create() {
            return new TruffleCodeInstallationTask();
        }
    }

    /**
     * Wrapper around a {@link TruffleCompilationTask} which also implements {@link Cancellable} to
     * allow co-operative canceling of truffle compilations.
     */
    public static final class CancellableTruffleCompilationTask implements TruffleCompilationTask, Cancellable {
        private final TruffleCompilationTask delegate;

        public CancellableTruffleCompilationTask(TruffleCompilationTask delegate) {
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

        @Override
        public TruffleInliningData inliningData() {
            return delegate.inliningData();
        }

        @Override
        public boolean hasNextTier() {
            return delegate.hasNextTier();
        }

        @Override
        public String toString() {
            return delegate.toString();
        }

        public TruffleCompilationTask getDelegate() {
            return delegate;
        }
    }

    private static final class TTYToPolyglotLoggerBridge implements Consumer<String> {

        private final CompilableTruffleAST compilable;

        TTYToPolyglotLoggerBridge(CompilableTruffleAST compilable) {
            this.compilable = compilable;
        }

        @Override
        public void accept(String message) {
            TruffleCompilerRuntime.getRuntime().log("graal", compilable, message);
        }
    }

    private static final class TruffleCompilationImpl implements TruffleCompilationIdentifier {

        final TruffleCompilationIdentifier delegate;
        private final TTY.Filter ttyFilter;

        TruffleCompilationImpl(TruffleCompilationIdentifier delegate, TTY.Filter ttyFilter) {
            this.delegate = delegate;
            this.ttyFilter = ttyFilter;
        }

        @Override
        public CompilableTruffleAST getCompilable() {
            return delegate.getCompilable();
        }

        @Override
        public String toString(Verbosity verbosity) {
            return delegate.toString(verbosity);
        }

        @Override
        public void close() {
            try {
                delegate.close();
            } finally {
                ttyFilter.close();
            }
        }
    }
}
