/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.IterativePartialEscape;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.PerformanceWarningsAreFatal;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.CompilationPrinter;
import org.graalvm.compiler.core.CompilationWatchDog;
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
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.debug.LogStream;
import org.graalvm.compiler.debug.MemUseTrackerKey;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.debug.TimerKey;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
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
import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.compiler.truffle.common.TruffleCompilerAssumptionDependency;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.compiler.TruffleCompilation.TTYToPolyglotLoggerBridge;
import org.graalvm.compiler.truffle.compiler.nodes.AnyExtendNode;
import org.graalvm.compiler.truffle.compiler.nodes.TruffleAssumption;
import org.graalvm.compiler.truffle.compiler.phases.InstrumentPhase;
import org.graalvm.compiler.truffle.compiler.phases.InstrumentationSuite;
import org.graalvm.compiler.truffle.compiler.phases.TruffleTier;
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

/**
 * Coordinates partial evaluation of a Truffle AST and subsequent compilation via Graal.
 */
public abstract class TruffleCompilerImpl implements TruffleCompilerBase, CompilationWatchDog.EventHandler {

    protected TruffleCompilerConfiguration config;
    protected final GraphBuilderConfiguration builderConfig;
    protected final PartialEvaluator partialEvaluator;
    protected final TrufflePostCodeInstallationTaskFactory codeInstallationTaskFactory;
    private volatile ExpansionStatistics expansionStatistics;
    private volatile boolean expansionStatisticsInitialized;
    private volatile boolean initialized;
    // Effectively final, but initialized in #initialize
    private TruffleTier truffleTier;

    public static final OptimisticOptimizations Optimizations = ALL.remove(
                    UseExceptionProbability,
                    RemoveNeverExecutedCode,
                    UseTypeCheckedInlining,
                    UseTypeCheckHints);

    @SuppressWarnings("this-escape")
    public TruffleCompilerImpl(TruffleCompilerConfiguration config) {
        this.config = config;
        this.codeInstallationTaskFactory = new TrufflePostCodeInstallationTaskFactory();
        for (Backend backend : config.backends()) {
            backend.addCodeInstallationTask(codeInstallationTaskFactory);
        }

        GraphBuilderConfiguration baseConfig = GraphBuilderConfiguration.getDefault(new Plugins(this.config.plugins()));
        this.builderConfig = baseConfig //
                        .withSkippedExceptionTypes(config.types().skippedExceptionTypes) //
                        .withOmitAssertions(ExcludeAssertions.getDefaultValue()) //
                        .withBytecodeExceptionMode(BytecodeExceptionMode.ExplicitOnly) //
                        // Avoid deopt loops caused by unresolved constant pool entries when parsed
                        // graphs are cached across compilations.
                        .withEagerResolving(true);

        this.partialEvaluator = createPartialEvaluator(config);
    }

    @Override
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
    public abstract TruffleCompilationIdentifier createCompilationIdentifier(TruffleCompilationTask task, CompilableTruffleAST compilable);

    protected abstract DebugContext createDebugContext(OptionValues options, CompilationIdentifier compilationId, CompilableTruffleAST compilable, PrintStream logStream);

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

    @Override
    @SuppressWarnings("try")
    public final void doCompile(TruffleCompilationTask task, CompilableTruffleAST compilable, Map<String, Object> optionsMap, TruffleCompilerListener listener) {
        try (TruffleCompilation compilation = openCompilation(task, compilable)) {
            doCompile(compilation, optionsMap, listener);
        }
    }

    @SuppressWarnings("try")
    public final void doCompile(TruffleCompilation compilation, Map<String, Object> optionsMap, TruffleCompilerListener listener) {
        CompilableTruffleAST compilable = compilation.getCompilable();
        TruffleCompilationTask task = compilation.getTask();
        org.graalvm.options.OptionValues options = getOptionsForCompiler(optionsMap);
        try (DebugContext debugContext = openDebugContext(optionsMap, compilation)) {
            try (DebugContext.Scope s = maybeOpenTruffleScope(task, compilable, debugContext)) {
                final TruffleCompilationWrapper truffleCompilationWrapper = new TruffleCompilationWrapper(
                                options,
                                getDebugOutputDirectory(),
                                getCompilationProblemsPerAction(),
                                compilable,
                                task,
                                compilation.getCompilationId(),
                                listener);
                truffleCompilationWrapper.run(debugContext);
            } catch (Throwable e) {
                notifyCompilableOfFailure(compilable, e, isSuppressedFailure(compilable, e));
            }
        }
    }

    @SuppressWarnings("static-method")
    public final TruffleCompilation openCompilation(TruffleCompilationTask task, CompilableTruffleAST compilable) {
        TruffleCompilation compilation = new TruffleCompilation(task, compilable);
        compilation.setCompilationId(createCompilationIdentifier(task, compilable));
        return compilation;
    }

    private DebugContext openDebugContext(Map<String, Object> options, TruffleCompilation compilation) {
        OptionValues graalOptions = TruffleCompilerEnvironment.get().runtime().getGraalOptions(OptionValues.class);
        final DebugContext debugContext;
        if (compilation == null) {
            debugContext = new Builder(graalOptions).build();
        } else {
            CompilableTruffleAST compilable = compilation.getCompilable();
            org.graalvm.options.OptionValues truffleOptions = getOptionsForCompiler(options);
            if (ExpansionStatistics.isEnabled(truffleOptions)) {
                graalOptions = enableNodeSourcePositions(graalOptions);
            }
            debugContext = createDebugContext(graalOptions, compilation.getCompilationId(), compilable, DebugContext.getDefaultLogStream());
        }
        return debugContext;
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
                        truffleTier = newTruffleTier(options);
                        initialized = true;
                        if (!FirstTierUseEconomy.getValue(options)) {
                            config = config.withFirstTier(config.lastTier());
                        }
                    }
                }
            }
        }
    }

    // Hook for SVM
    protected TruffleTier newTruffleTier(org.graalvm.options.OptionValues options) {
        return new TruffleTier(options, partialEvaluator,
                        new InstrumentationSuite(partialEvaluator.instrumentationCfg, config.snippetReflection(), partialEvaluator.getInstrumentation()),
                        new PostPartialEvaluationSuite(options.get(IterativePartialEscape)));
    }

    /**
     * Opens a new {@code "Truffle"} scope if that's not the unqualified name of the current scope.
     *
     * @param compilable the context for the {@code "Truffle"} scope
     * @param debug the current debug context
     * @return a new opened {@code "Truffle"} scope or {@code null} if the current (unqualified)
     *         scope is already named {@code "Truffle"}
     */
    private static Scope maybeOpenTruffleScope(TruffleCompilationTask task, CompilableTruffleAST compilable, DebugContext debug) throws Throwable {
        if (debug.getCurrentScopeName().endsWith(".Truffle")) {
            return null;
        }
        return debug.scope("Truffle", new TruffleDebugJavaMethod(task, compilable));
    }

    private static void notifyCompilableOfFailure(CompilableTruffleAST compilable, Throwable e, boolean silent) {
        Throwable error = e;
        boolean graphTooBig = false;
        if (error instanceof GraphTooBigBailoutException) {
            Throwable cause = error.getCause();
            if (cause != null) {
                error = cause;
            }
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

    // Used for tests and should be removed
    public void compileAST(
                    org.graalvm.options.OptionValues options,
                    DebugContext debug,
                    final CompilableTruffleAST compilable,
                    CompilationIdentifier compilationId,
                    TruffleCompilationTask task,
                    TruffleCompilerListener listener) {
        compileAST(new TruffleCompilationWrapper(options, getDebugOutputDirectory(), Collections.emptyMap(), compilable, task, compilationId, listener), debug);
    }

    /**
     * Compiles a Truffle AST. If compilation succeeds, the AST will have compiled code associated
     * with it that can be executed instead of interpreting the AST.
     */
    @SuppressWarnings("try")
    private void compileAST(TruffleCompilationWrapper wrapper, DebugContext debug) {
        TruffleCompilationTask task = wrapper.task;
        CompilableTruffleAST compilable = wrapper.compilable;

        final CompilationPrinter printer = CompilationPrinter.begin(debug.getOptions(), wrapper.compilationId, new TruffleDebugJavaMethod(task, compilable), INVOCATION_ENTRY_BCI);
        StructuredGraph graph = null;

        try (CompilationAlarm alarm = CompilationAlarm.trackCompilationPeriod(debug.getOptions());
                        TruffleInliningScope inlining = TruffleInliningScope.open(debug)) {
            graph = truffleTier(wrapper, debug);

            graph.checkCancellation();
            // The Truffle compiler owns the last 2 characters of the compilation name, and uses
            // them to encode the compilation tier, so escaping the target name is not
            // necessary.
            String compilationName = wrapper.compilable.toString() + (task.isFirstTier() ? TruffleCompiler.FIRST_TIER_COMPILATION_SUFFIX : TruffleCompiler.SECOND_TIER_COMPILATION_SUFFIX);
            PhaseSuite<HighTierContext> graphBuilderSuite = createGraphBuilderSuite(task.isFirstTier() ? config.firstTier() : config.lastTier());
            InstalledCode[] installedCode = {null};
            CompilationResult compilationResult = compilePEGraph(graph,
                            compilationName,
                            graphBuilderSuite,
                            wrapper.compilable,
                            asCompilationRequest(wrapper.compilationId),
                            wrapper.listener,
                            task,
                            installedCode,
                            inlining);

            if (wrapper.statistics != null) {
                wrapper.statistics.afterLowTier(wrapper.compilable, graph);
            }
            if (wrapper.listener != null) {
                wrapper.listener.onSuccess(wrapper.compilable, task, new GraphInfoImpl(graph), new CompilationResultInfoImpl(compilationResult), task.tier());
            }

            // Partial evaluation and installation are included in
            // compilation time and memory usage reported by printer
            printer.finish(compilationResult, installedCode[0]);
        } catch (Throwable t) {
            // Note: If the compiler cancels the compilation with a bailout exception, then the
            // graph is null
            if (wrapper.listener != null) {
                BailoutException bailout = t instanceof BailoutException ? (BailoutException) t : null;
                boolean permanentBailout = bailout != null ? bailout.isPermanent() : false;
                wrapper.listener.onFailure(compilable, t.toString(), bailout != null, permanentBailout, task.tier());
            }
            throw t;
        }
    }

    @SuppressWarnings("try")
    private StructuredGraph truffleTier(TruffleCompilationWrapper wrapper, DebugContext debug) {
        StructuredGraph graph;
        try (DebugCloseable a = PartialEvaluationTime.start(debug);
                        DebugCloseable c = PartialEvaluationMemUse.start(debug);
                        PerformanceInformationHandler handler = PerformanceInformationHandler.install(wrapper.options)) {

            /*
             * TODO GR-37097 Merge TruffleTierConfiguration and TruffleCompilationWrapper so that
             * there is one place where compilation data lives
             */
            TruffleTierContext context = new TruffleTierContext(partialEvaluator,
                            wrapper.options, debug,
                            wrapper.compilable,
                            partialEvaluator.rootForCallTarget(wrapper.compilable),
                            wrapper.compilationId, TruffleTierContext.getSpeculationLog(wrapper), wrapper.task,
                            handler);

            try (Scope s = context.debug.scope("CreateGraph", context.graph);
                            Indent indent = context.debug.logAndIndent("evaluate %s", context.graph);) {
                truffleTier.apply(context.graph, context);
                graph = context.graph;
            } catch (Throwable e) {
                throw context.debug.handle(e);
            }
        }
        if (wrapper.statistics != null) {
            wrapper.statistics.afterTruffleTier(wrapper.compilable, graph);
        }
        if (wrapper.listener != null) {
            wrapper.listener.onTruffleTierFinished(wrapper.compilable, wrapper.task, new GraphInfoImpl(graph));
        }
        return graph;
    }

    private static void replaceAnyExtendNodes(StructuredGraph graph) {
        // replace all AnyExtendNodes with ZeroExtendNodes
        for (AnyExtendNode node : graph.getNodes(AnyExtendNode.TYPE)) {
            node.replaceAndDelete(graph.addOrUnique(ZeroExtendNode.create(node.getValue(), 64, NodeView.DEFAULT)));
        }
    }

    /**
     * Compiles a graph produced by {@link TruffleTier}, i.e.
     * {@link #truffleTier(TruffleCompilationWrapper, DebugContext)}.
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
                    TruffleCompilationTask task,
                    InstalledCode[] outInstalledCode,
                    TruffleInliningScope inlining) {

        replaceAnyExtendNodes(graph);
        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope s = debug.scope("TruffleFinal")) {
            debug.dump(DebugContext.BASIC_LEVEL, graph, "After TruffleTier");
        } catch (Throwable e) {
            throw debug.handle(e);
        }
        if (debug.isDumpEnabled(DebugContext.BASIC_LEVEL)) {
            debug.dump(DebugContext.BASIC_LEVEL, TruffleAST.create(config, task, compilable, inlining != null ? inlining.getCallTree() : null), "After TruffleTier");
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
            if (outInstalledCode != null) {
                outInstalledCode[0] = installedCode;
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }

        return result;
    }

    protected abstract InstalledCode createInstalledCode(CompilableTruffleAST compilable);

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

    @Override
    public TruffleTier getTruffleTier() {
        return truffleTier;
    }

    public CompilableTruffleAST asCompilableTruffleAST(JavaConstant constant) {
        return config.snippetReflection().asObject(CompilableTruffleAST.class, constant);
    }

    @Override
    public void onStuckCompilation(CompilationWatchDog watchDog, Thread watched, CompilationIdentifier compilation, StackTraceElement[] stackTrace, int stuckTime) {
        CompilationWatchDog.EventHandler.super.onStuckCompilation(watchDog, watched, compilation, stackTrace, stuckTime);
        TTY.println("Compilation %s on %s appears stuck - exiting VM", compilation, watched);
        exitHostVM(STUCK_COMPILATION_EXIT_CODE);
    }

    /**
     * Wrapper for performing a Truffle compilation that can retry upon failure.
     */
    final class TruffleCompilationWrapper extends CompilationWrapper<Void> {
        final CompilableTruffleAST compilable;
        final TruffleCompilationTask task;
        final TruffleCompilerListener listener;
        final CompilationIdentifier compilationId;
        final org.graalvm.options.OptionValues options;
        final ExpansionStatistics statistics;
        boolean silent;

        private TruffleCompilationWrapper(
                        org.graalvm.options.OptionValues options,
                        DiagnosticsOutputDirectory outputDirectory,
                        Map<ExceptionAction, Integer> problemsHandledPerAction,
                        CompilableTruffleAST optimizedCallTarget,
                        TruffleCompilationTask task,
                        CompilationIdentifier compilationId,
                        TruffleCompilerListener listener) {
            super(outputDirectory, problemsHandledPerAction);
            this.options = options;
            this.compilable = optimizedCallTarget;
            this.task = task;
            this.listener = listener;
            this.compilationId = compilationId;
            this.statistics = getExpansionHistogram(options);
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

        @SuppressWarnings("try")
        @Override
        protected Void performCompilation(DebugContext debug) {
            try (CompilationWatchDog watch = CompilationWatchDog.watch(compilationId, debug.getOptions(), false, TruffleCompilerImpl.this)) {
                compileAST(this, debug);
                return null;
            }
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
        return TruffleCompilerEnvironment.get().runtime().isSuppressedFailure(
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
            TruffleCompilerRuntime runtime = TruffleCompilerEnvironment.get().runtime();
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
                    /*
                     * On SVM the installed code can be an assumption dependency. On HotSpot we
                     * cannot subclass HotSpotNmethod therefore that is not an option.
                     */
                    dependency = (OptimizedAssumptionDependency) installedCode;
                } else {
                    CompilableTruffleAST compilable = getCompilable(compilationResult);
                    dependency = new TruffleCompilerAssumptionDependency(compilable, installedCode);
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

}
