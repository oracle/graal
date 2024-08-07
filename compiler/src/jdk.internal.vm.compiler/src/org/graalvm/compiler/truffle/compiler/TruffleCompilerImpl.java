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
import static org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions.ExcludeAssertions;
import static org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions.IterativePartialEscape;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.CompilationPrinter;
import org.graalvm.compiler.core.CompilationWatchDog;
import org.graalvm.compiler.core.CompilationWrapper;
import org.graalvm.compiler.core.CompilationWrapper.ExceptionAction;
import org.graalvm.compiler.core.GraalCompiler;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.CompilationIdentifier.Verbosity;
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
import org.graalvm.compiler.options.OptionDescriptor;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.options.OptionsParser;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.truffle.compiler.TruffleCompilation.TTYToPolyglotLoggerBridge;
import org.graalvm.compiler.truffle.compiler.nodes.AnyExtendNode;
import org.graalvm.compiler.truffle.compiler.nodes.TruffleAssumption;
import org.graalvm.compiler.truffle.compiler.phases.InstrumentPhase;
import org.graalvm.compiler.truffle.compiler.phases.InstrumentationSuite;
import org.graalvm.compiler.truffle.compiler.phases.TruffleTier;

import com.oracle.truffle.compiler.OptimizedAssumptionDependency;
import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilationTask;
import com.oracle.truffle.compiler.TruffleCompiler;
import com.oracle.truffle.compiler.TruffleCompilerAssumptionDependency;
import com.oracle.truffle.compiler.TruffleCompilerListener;
import com.oracle.truffle.compiler.TruffleCompilerRuntime;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.meta.Assumptions.Assumption;
import jdk.vm.ci.meta.JavaConstant;

/**
 * Coordinates partial evaluation of a Truffle AST and subsequent compilation via Graal.
 */
public abstract class TruffleCompilerImpl implements TruffleCompiler, CompilationWatchDog.EventHandler {

    public static final int FIRST_TIER_INDEX = 1;
    public static final int LAST_TIER_INDEX = 2;

    static final int NUMBER_OF_CACHED_OPTIONS = 128;
    static final TruffleCompilerOptionsOptionDescriptors OPTION_DESCRIPTORS = new TruffleCompilerOptionsOptionDescriptors();

    protected TruffleCompilerConfiguration config;
    protected final GraphBuilderConfiguration builderConfig;
    protected final PartialEvaluator partialEvaluator;
    protected final TrufflePostCodeInstallationTaskFactory codeInstallationTaskFactory;
    private volatile ExpansionStatistics expansionStatistics;
    private volatile boolean expansionStatisticsInitialized;
    private volatile boolean initialized;
    // Effectively final, but initialized in #initialize
    private TruffleTier truffleTier;

    @SuppressWarnings("serial") private static final Map<Long, OptionValues> cachedOptions = Collections.synchronizedMap(new LRUCache<>(NUMBER_OF_CACHED_OPTIONS));

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
            initializeBackend(backend);
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

    public void initializeBackend(Backend backend) {
        backend.addCodeInstallationTask(codeInstallationTaskFactory);
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
    public abstract TruffleCompilationIdentifier createCompilationIdentifier(TruffleCompilationTask task, TruffleCompilable compilable);

    protected abstract DebugContext createDebugContext(OptionValues options, CompilationIdentifier compilationId, TruffleCompilable compilable, PrintStream logStream);

    public static PartialEvaluatorConfiguration createPartialEvaluatorConfiguration(String name) {
        for (PartialEvaluatorConfiguration candidate : GraalServices.load(PartialEvaluatorConfiguration.class)) {
            if (candidate.name().equals(name)) {
                return candidate;
            }
        }
        throw new GraalError("Cannot find partial evaluation configuration: %s", name);
    }

    @Override
    @SuppressWarnings("try")
    public final void doCompile(TruffleCompilationTask task, TruffleCompilable compilable, TruffleCompilerListener listener) {
        try (TruffleCompilation compilation = openCompilation(task, compilable)) {
            doCompile(compilation, listener);
        }
    }

    /**
     * General options configured for the compiler. E.g. default values set with
     * -Dgraal.OptionKey=value.
     */
    protected abstract OptionValues getGraalOptions();

    @SuppressWarnings("try")
    public final void doCompile(TruffleCompilation compilation, TruffleCompilerListener listener) {
        TruffleCompilable compilable = compilation.getCompilable();
        TruffleCompilationTask task = compilation.getTask();
        OptionValues compilerOptions = getOrCreateCompilerOptions(compilation.getCompilable());

        try (DebugContext debugContext = openDebugContext(compilerOptions, compilation)) {
            try (DebugContext.Scope s = maybeOpenTruffleScope(task, compilable, debugContext)) {
                final TruffleCompilationWrapper truffleCompilationWrapper = new TruffleCompilationWrapper(
                                compilerOptions,
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

    public OptionValues getOrCreateCompilerOptions(TruffleCompilable compilable) throws IllegalArgumentException {
        // Options are guaranteed to be unchanged per engine.
        Long engineId = compilable.engineId();
        return cachedOptions.computeIfAbsent(engineId, (id) -> {
            OptionValues graalOptions = getGraalOptions();
            Map<String, String> options = compilable.getCompilerOptions();
            EconomicMap<OptionKey<?>, Object> map = parseOptions(options);
            map.putAll(graalOptions.getMap());
            TruffleCompilerOptions.updateValues(graalOptions);
            return new OptionValues(map);
        });
    }

    private static EconomicMap<OptionKey<?>, Object> parseOptions(Map<String, String> options) {
        EconomicMap<OptionKey<?>, Object> map = EconomicMap.create();
        for (var entry : options.entrySet()) {
            String key = entry.getKey();
            String uncheckedValue = entry.getValue();
            OptionDescriptor descriptor = OPTION_DESCRIPTORS.get(key);
            if (descriptor == null) {
                throw new IllegalArgumentException("Invalid option " + key);
            }
            Object value = TruffleCompilerOptions.parseCustom(descriptor, uncheckedValue);
            if (value == null) {
                value = OptionsParser.parseOptionValue(descriptor, uncheckedValue);
            }
            map.put(descriptor.getOptionKey(), value);
        }
        return map;
    }

    @SuppressWarnings("static-method")
    public final TruffleCompilation openCompilation(TruffleCompilationTask task, TruffleCompilable compilable) {
        TruffleCompilation compilation = new TruffleCompilation(config.runtime(), task, compilable);
        compilation.setCompilationId(createCompilationIdentifier(task, compilable));
        return compilation;
    }

    private DebugContext openDebugContext(OptionValues compilerOptions, TruffleCompilation compilation) {
        final DebugContext debugContext;
        if (compilation == null) {
            debugContext = new Builder(compilerOptions).build();
        } else {
            TruffleCompilable compilable = compilation.getCompilable();
            debugContext = createDebugContext(compilerOptions, compilation.getCompilationId(), compilable, DebugContext.getDefaultLogStream());
        }
        return debugContext;
    }

    @Override
    @SuppressWarnings("try")
    public void initialize(TruffleCompilable compilable, boolean firstInitialization) {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    try (TTY.Filter ttyFilter = new TTY.Filter(new LogStream(new TTYToPolyglotLoggerBridge(config.runtime(), compilable)))) {
                        OptionValues compilerOptions = getOrCreateCompilerOptions(compilable);

                        partialEvaluator.initialize(compilerOptions);
                        truffleTier = newTruffleTier(compilerOptions);
                        initialized = true;

                        if (!TruffleCompilerOptions.FirstTierUseEconomy.getValue(compilerOptions)) {
                            config = config.withFirstTier(config.lastTier());
                        }
                    }
                }
            }
        }
    }

    // Hook for SVM
    protected TruffleTier newTruffleTier(OptionValues options) {
        return new TruffleTier(options, partialEvaluator,
                        new InstrumentationSuite(partialEvaluator.instrumentationCfg, config.snippetReflection(), partialEvaluator.getInstrumentation()),
                        new PostPartialEvaluationSuite(options, IterativePartialEscape.getValue(options)));
    }

    /**
     * Opens a new {@code "Truffle"} scope if that's not the unqualified name of the current scope.
     *
     * @param compilable the context for the {@code "Truffle"} scope
     * @param debug the current debug context
     * @return a new opened {@code "Truffle"} scope or {@code null} if the current (unqualified)
     *         scope is already named {@code "Truffle"}
     */
    private static Scope maybeOpenTruffleScope(TruffleCompilationTask task, TruffleCompilable compilable, DebugContext debug) throws Throwable {
        if (debug.getCurrentScopeName().endsWith(".Truffle")) {
            return null;
        }
        return debug.scope("Truffle", new TruffleDebugJavaMethod(task, compilable));
    }

    private static void notifyCompilableOfFailure(TruffleCompilable compilable, Throwable e, boolean silent) {
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
        compilable.onCompilationFailed(() -> TruffleCompilable.serializeException(finalError), silent, bailout != null, permanentBailout, graphTooBig);
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

    final ExpansionStatistics getExpansionHistogram(OptionValues options) {
        ExpansionStatistics local = expansionStatistics;
        if (local == null && !expansionStatisticsInitialized) {
            synchronized (this) {
                local = expansionStatistics;
                if (local == null) {
                    this.expansionStatistics = local = ExpansionStatistics.create(partialEvaluator, options);
                    this.expansionStatisticsInitialized = true;
                }
            }
        }
        return local;
    }

    // Used for tests and should be removed
    public void compileAST(
                    DebugContext debug,
                    final TruffleCompilable compilable,
                    CompilationIdentifier compilationId,
                    TruffleCompilationTask task,
                    TruffleCompilerListener listener) {
        compileAST(new TruffleCompilationWrapper(getOrCreateCompilerOptions(compilable), getDebugOutputDirectory(), Collections.emptyMap(), compilable, task, compilationId, listener), debug);
    }

    /**
     * Compiles a Truffle AST. If compilation succeeds, the AST will have compiled code associated
     * with it that can be executed instead of interpreting the AST.
     */
    @SuppressWarnings("try")
    private void compileAST(TruffleCompilationWrapper wrapper, DebugContext debug) {
        TruffleCompilationTask task = wrapper.task;
        TruffleCompilable compilable = wrapper.compilable;

        final CompilationPrinter printer = CompilationPrinter.begin(debug.getOptions(), wrapper.compilationId, new TruffleDebugJavaMethod(task, compilable), INVOCATION_ENTRY_BCI);
        StructuredGraph graph = null;

        try (CompilationAlarm alarm = CompilationAlarm.trackCompilationPeriod(debug.getOptions());
                        TruffleInliningScope inlining = TruffleInliningScope.open(debug)) {
            graph = truffleTier(wrapper, debug);

            graph.checkCancellation();
            // The Truffle compiler owns the last 2 characters of the compilation name, and uses
            // them to encode the compilation tier, so escaping the target name is not
            // necessary.
            String compilationName = wrapper.compilable.toString() + (task.isFirstTier() ? FIRST_TIER_COMPILATION_SUFFIX : SECOND_TIER_COMPILATION_SUFFIX);
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
                wrapper.listener.onFailure(compilable, t.toString(), bailout != null, permanentBailout, task.tier(), bailout != null ? null : () -> TruffleCompilable.serializeException(t));
            }
            throw t;
        }
    }

    @SuppressWarnings("try")
    private StructuredGraph truffleTier(TruffleCompilationWrapper wrapper, DebugContext debug) {
        StructuredGraph graph;
        try (DebugCloseable a = PartialEvaluationTime.start(debug);
                        DebugCloseable c = PartialEvaluationMemUse.start(debug);
                        PerformanceInformationHandler handler = PerformanceInformationHandler.install(config.runtime(), wrapper.compilerOptions)) {

            /*
             * TODO GR-37097 Merge TruffleTierConfiguration and TruffleCompilationWrapper so that
             * there is one place where compilation data lives
             */
            TruffleTierContext context = new TruffleTierContext(partialEvaluator,
                            wrapper.compilerOptions,
                            debug,
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
                    TruffleCompilable compilable,
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
            debug.dump(DebugContext.BASIC_LEVEL, TruffleAST.create(partialEvaluator, task, compilable, inlining != null ? inlining.getCallTree() : null), "After TruffleTier");
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

    protected abstract InstalledCode createInstalledCode(TruffleCompilable compilable);

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
    protected abstract CompilationResult createCompilationResult(String name, CompilationIdentifier compilationIdentifier, TruffleCompilable compilable);

    public abstract PhaseSuite<HighTierContext> createGraphBuilderSuite(TruffleTierConfiguration tier);

    public PartialEvaluator getPartialEvaluator() {
        return partialEvaluator;
    }

    public TruffleTier getTruffleTier() {
        return truffleTier;
    }

    public TruffleCompilable asCompilableTruffleAST(JavaConstant constant) {
        return config.snippetReflection().asObject(TruffleCompilable.class, constant);
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
        final TruffleCompilable compilable;
        final TruffleCompilationTask task;
        final TruffleCompilerListener listener;
        final CompilationIdentifier compilationId;
        final ExpansionStatistics statistics;
        final OptionValues compilerOptions;
        boolean silent;

        private TruffleCompilationWrapper(
                        OptionValues compilerOptions,
                        DiagnosticsOutputDirectory outputDirectory,
                        Map<ExceptionAction, Integer> problemsHandledPerAction,
                        TruffleCompilable optimizedCallTarget,
                        TruffleCompilationTask task,
                        CompilationIdentifier compilationId,
                        TruffleCompilerListener listener) {
            super(outputDirectory, problemsHandledPerAction);
            this.compilerOptions = compilerOptions;
            this.compilable = optimizedCallTarget;
            this.task = task;
            this.listener = listener;
            this.compilationId = compilationId;
            this.statistics = getExpansionHistogram(compilerOptions);
        }

        @Override
        public String toString() {
            return compilable.toString();
        }

        @Override
        protected ExceptionAction lookupAction(OptionValues options, Throwable cause) {
            // Respect current action if it has been explicitly set.
            if (!(cause instanceof BailoutException) || ((BailoutException) cause).isPermanent()) {
                if (TruffleCompilerOptions.DiagnoseFailure.getValue(compilerOptions)) {
                    // Get more info for Truffle compilation exceptions
                    // that will cause the VM to exit.
                    return Diagnose;
                }
            }
            return super.lookupAction(options, cause);
        }

        @Override
        protected DebugContext createRetryDebugContext(DebugContext initialDebug, OptionValues options, PrintStream logStream) {
            listener.onCompilationRetry(compilable, task);
            return createDebugContext(options, compilationId, compilable, logStream);
        }

        @Override
        protected void exitHostVM(int status) {
            // TODO throw an assertion here
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
    private boolean isSuppressedFailure(TruffleCompilable compilable, Throwable cause) {
        return config.runtime().isSuppressedFailure(
                        compilable, () -> TruffleCompilable.serializeException(cause));
    }

    /**
     * Gets the {@link TruffleCompilable} associated with {@code result}.
     *
     * @param result a {@link CompilationResult} that may have a non-null {@link TruffleCompilable}
     *            associated with it
     */
    protected TruffleCompilable getCompilable(CompilationResult result) {
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
            TruffleCompilerRuntime runtime = config.runtime();
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
                    TruffleCompilable compilable = getCompilable(compilationResult);
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
                    e.printStackTrace();
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
        return config.snippetReflection();
    }

    private class TrufflePostCodeInstallationTaskFactory extends Backend.CodeInstallationTaskFactory {

        @Override
        public Backend.CodeInstallationTask create() {
            return new TruffleCodeInstallationTask();
        }
    }

    private static final class LRUCache<K, V> extends LinkedHashMap<K, V> {

        private static final long serialVersionUID = 7813848977534444613L;
        private final int maxCacheSize;

        LRUCache(int maxCacheSize) {
            this(maxCacheSize, 16);
        }

        LRUCache(int maxCacheSize, int initialCapacity) {
            super(initialCapacity, 0.75f, true);
            this.maxCacheSize = maxCacheSize;
        }

        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<K, V> eldest) {
            return size() > maxCacheSize;
        }

    }

}
