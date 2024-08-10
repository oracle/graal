/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.runtime;

import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.ArgumentTypeSpeculation;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.BackgroundCompilation;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.Compilation;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.CompilationFailureAction;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.CompilationStatisticDetails;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.CompilationStatistics;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.CompileAOTOnCreate;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.CompileImmediately;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.CompileOnly;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.FirstTierCompilationThreshold;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.FirstTierMinInvokeThreshold;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.LastTierCompilationThreshold;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.MinInvokeThreshold;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.Mode;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.MultiTier;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.PriorityQueue;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.Profiling;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.PropagateLoopCountToLexicalSingleCaller;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.PropagateLoopCountToLexicalSingleCallerMaxDepth;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.ReturnTypeSpeculation;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.SingleTierCompilationThreshold;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.Splitting;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.SplittingAllowForcedSplits;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.SplittingDumpDecisions;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.SplittingGrowthLimit;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.SplittingMaxCalleeSize;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.SplittingMaxPropagationDepth;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.SplittingTraceEvents;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.TraceCompilation;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.TraceCompilationDetails;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.TraceDeoptimizeFrame;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.TraceSplitting;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.TraceSplittingSummary;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.TraceTransferToInterpreter;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.TraversingQueueFirstTierBonus;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.TraversingQueueFirstTierPriority;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.TraversingQueueWeightingBothTiers;
import static com.oracle.truffle.runtime.OptimizedTruffleRuntime.getRuntime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;

import org.graalvm.collections.Pair;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.SandboxPolicy;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.runtime.OptimizedRuntimeOptions.EngineModeEnum;
import com.oracle.truffle.runtime.OptimizedRuntimeOptions.ExceptionAction;
import com.oracle.truffle.runtime.debug.StatisticsListener;

/**
 * Class used to store data used by the compiler in the Engine. Enables "global" compiler state per
 * engine. One-to-one relationship with a polyglot Engine instance.
 */
public final class EngineData {

    int splitLimit;
    int splitCount;
    public final long id;
    private Function<String, TruffleLogger> loggerFactory;
    @CompilationFinal OptionValues engineOptions;
    final TruffleSplittingStrategy.SplitStatisticsData splittingStatistics;
    @CompilationFinal public StatisticsListener statisticsListener;

    /*
     * Important while visible, options must not be modified except in loadOptions.
     */
    // splitting options
    @CompilationFinal public boolean splitting;
    @CompilationFinal public boolean splittingAllowForcedSplits;
    @CompilationFinal public boolean splittingDumpDecisions;
    @CompilationFinal public boolean splittingTraceEvents;
    @CompilationFinal public boolean traceSplittingSummary;
    @CompilationFinal public boolean traceSplits;
    @CompilationFinal public int splittingMaxCalleeSize;
    @CompilationFinal public int splittingMaxPropagationDepth;
    @CompilationFinal public double splittingGrowthLimit;

    // compilation options
    @CompilationFinal public boolean compilation;
    @CompilationFinal public boolean compileImmediately;
    @CompilationFinal public boolean multiTier;
    @CompilationFinal public boolean returnTypeSpeculation;
    @CompilationFinal public boolean argumentTypeSpeculation;
    @CompilationFinal public boolean traceCompilation;
    @CompilationFinal public boolean traceCompilationDetails;
    @CompilationFinal public boolean backgroundCompilation;
    @CompilationFinal public ExceptionAction compilationFailureAction;
    @CompilationFinal public String compileOnly;
    @CompilationFinal public boolean callTargetStatistics;
    @CompilationFinal public boolean callTargetStatisticDetails;
    @CompilationFinal public boolean profilingEnabled;
    @CompilationFinal public boolean traceTransferToInterpreter;
    @CompilationFinal public boolean traceDeoptimizeFrame;
    @CompilationFinal public boolean compileAOTOnCreate;
    @CompilationFinal public boolean firstTierOnly;

    // compilation queue options
    @CompilationFinal public boolean priorityQueue;
    @CompilationFinal public boolean weightingBothTiers;
    @CompilationFinal public boolean traversingFirstTierPriority;
    @CompilationFinal public double traversingFirstTierBonus;
    @CompilationFinal public boolean propagateCallAndLoopCount;
    @CompilationFinal public int propagateCallAndLoopCountMaxDepth;

    // computed fields.
    @CompilationFinal public int callThresholdInInterpreter;
    @CompilationFinal public int callAndLoopThresholdInInterpreter;
    @CompilationFinal public int callThresholdInFirstTier;
    @CompilationFinal public int callAndLoopThresholdInFirstTier;

    // Cached parsed CompileOnly includes and excludes
    private volatile Pair<List<String>, List<String>> parsedCompileOnly;
    private Map<String, String> compilerOptions;

    private volatile Object polyglotEngine;

    /*
     * Extension data for dynamically bound engine extensions.
     */
    private volatile Map<Class<?>, Object> engineLocals;

    EngineData(Object polyglotEngine, OptionValues runtimeOptions, Function<String, TruffleLogger> loggerFactory, SandboxPolicy sandboxPolicy) {
        Objects.requireNonNull(polyglotEngine);
        Objects.requireNonNull(runtimeOptions);
        this.polyglotEngine = polyglotEngine;
        this.id = OptimizedRuntimeAccessor.ENGINE.getEngineId(polyglotEngine);
        this.loggerFactory = loggerFactory;
        this.loadOptions(runtimeOptions, sandboxPolicy);

        // the splittingStatistics requires options to be initialized
        this.splittingStatistics = new TruffleSplittingStrategy.SplitStatisticsData();
    }

    public static IllegalArgumentException sandboxPolicyException(SandboxPolicy sandboxPolicy, String reason, String fix) {
        Objects.requireNonNull(sandboxPolicy);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(fix);
        String spawnIsolateHelp;
        if (sandboxPolicy.isStricterOrEqual(SandboxPolicy.ISOLATED)) {
            spawnIsolateHelp = " If you switch to a less strict sandbox policy you can still spawn an isolate with an isolated heap using Builder.option(\"engine.SpawnIsolate\",\"true\").";
        } else {
            spawnIsolateHelp = "";
        }
        String message = String.format("The validation for the given sandbox policy %s failed. %s " +
                        "In order to resolve this %s or switch to a less strict sandbox policy using Builder.sandbox(SandboxPolicy).%s", sandboxPolicy, reason, fix, spawnIsolateHelp);
        return new IllegalArgumentException(message);
    }

    public void preinitializeContext() {
        OptimizedRuntimeAccessor.ENGINE.preinitializeContext(this.polyglotEngine);
    }

    public void finalizeStore() {
        OptimizedRuntimeAccessor.ENGINE.finalizeStore(this.polyglotEngine);
    }

    public Object getEngineLock() {
        return OptimizedRuntimeAccessor.ENGINE.getEngineLock(this.polyglotEngine);
    }

    @SuppressWarnings("unchecked")
    public <T> T getEngineLocal(Class<T> symbol) {
        Map<Class<?>, Object> data = this.engineLocals;
        if (data == null) {
            return null;
        }
        return (T) data.get(symbol);
    }

    public void clearEngineLocal(Class<?> symbol) {
        Map<Class<?>, Object> data = this.engineLocals;
        if (data == null) {
            return;
        }
        data.remove(symbol);
    }

    public <T> void putEngineLocal(Class<T> symbol, T value) {
        Map<Class<?>, Object> data = this.engineLocals;
        if (data == null) {
            synchronized (this) {
                data = this.engineLocals;
                if (data == null) {
                    this.engineLocals = data = new ConcurrentHashMap<>();
                }
            }
        }
        Object prev = data.putIfAbsent(symbol, symbol.cast(value));
        if (prev != null) {
            throw new IllegalArgumentException("Cannot set engine local. Key " + symbol + " is already defined.");
        }
    }

    void onEngineCreated(Object engine) {
        assert this.polyglotEngine == engine;
        getRuntime().getEngineCacheSupport().onEngineCreated(this);
    }

    void onEnginePatch(OptionValues newRuntimeOptions, Function<String, TruffleLogger> newLoggerFactory, SandboxPolicy sandboxPolicy) {
        this.loggerFactory = newLoggerFactory;
        loadOptions(newRuntimeOptions, sandboxPolicy);
        getRuntime().getEngineCacheSupport().onEnginePatch(this);
    }

    public Object getPolyglotEngine() {
        return polyglotEngine;
    }

    boolean onEngineClosing() {
        return getRuntime().getEngineCacheSupport().onEngineClosing(this);
    }

    void onEngineClosed() {
        getRuntime().getListener().onEngineClosed(this);
        getRuntime().getEngineCacheSupport().onEngineClosed(this);
        /*
         * The PolyglotEngine must be reset only after listeners are closed. The PolyglotEngine
         * reset disables logging.
         */
        this.polyglotEngine = null;
    }

    private void loadOptions(OptionValues options, SandboxPolicy sandboxPolicy) {
        this.engineOptions = options;

        // splitting options
        this.splitting = options.get(Splitting) && options.get(Mode) != EngineModeEnum.LATENCY;
        this.splittingAllowForcedSplits = options.get(SplittingAllowForcedSplits);
        this.splittingDumpDecisions = options.get(SplittingDumpDecisions);
        this.splittingMaxCalleeSize = options.get(SplittingMaxCalleeSize);
        this.splittingMaxPropagationDepth = options.get(SplittingMaxPropagationDepth);
        this.splittingTraceEvents = options.get(SplittingTraceEvents);
        this.traceSplittingSummary = options.get(TraceSplittingSummary);
        this.traceSplits = options.get(TraceSplitting);
        this.splittingGrowthLimit = options.get(SplittingGrowthLimit);

        // compilation options
        this.compilation = options.get(Compilation);
        this.compileOnly = options.get(CompileOnly);
        this.compileImmediately = options.get(CompileImmediately);
        this.multiTier = !compileImmediately && options.get(MultiTier);
        this.compileAOTOnCreate = options.get(CompileAOTOnCreate);
        this.firstTierOnly = options.get(Mode) == EngineModeEnum.LATENCY;
        this.propagateCallAndLoopCount = options.get(PropagateLoopCountToLexicalSingleCaller);
        this.propagateCallAndLoopCountMaxDepth = options.get(PropagateLoopCountToLexicalSingleCallerMaxDepth);

        // compilation queue options
        priorityQueue = options.get(PriorityQueue);
        weightingBothTiers = options.get(TraversingQueueWeightingBothTiers);
        traversingFirstTierPriority = options.get(TraversingQueueFirstTierPriority);
        // See usage of traversingFirstTierBonus for explanation of this formula.
        traversingFirstTierBonus = options.get(TraversingQueueFirstTierBonus) * options.get(LastTierCompilationThreshold) / options.get(FirstTierCompilationThreshold);

        this.returnTypeSpeculation = options.get(ReturnTypeSpeculation);
        this.argumentTypeSpeculation = options.get(ArgumentTypeSpeculation);
        this.traceCompilation = options.get(TraceCompilation);
        this.traceCompilationDetails = options.get(TraceCompilationDetails);
        this.backgroundCompilation = options.get(BackgroundCompilation) && !compileAOTOnCreate;
        this.callThresholdInInterpreter = computeCallThresholdInInterpreter(options);
        this.callAndLoopThresholdInInterpreter = computeCallAndLoopThresholdInInterpreter(options);
        this.callThresholdInFirstTier = computeCallThresholdInFirstTier(options);
        this.callAndLoopThresholdInFirstTier = computeCallAndLoopThresholdInFirstTier(options);
        this.callTargetStatisticDetails = options.get(CompilationStatisticDetails);
        this.callTargetStatistics = options.get(CompilationStatistics) || this.callTargetStatisticDetails;
        this.statisticsListener = this.callTargetStatistics ? StatisticsListener.createEngineListener(OptimizedTruffleRuntime.getRuntime()) : null;
        this.profilingEnabled = options.get(Profiling);
        this.traceTransferToInterpreter = options.get(TraceTransferToInterpreter);
        this.traceDeoptimizeFrame = options.get(TraceDeoptimizeFrame);
        this.compilationFailureAction = options.get(CompilationFailureAction);
        validateOptions(sandboxPolicy);
        parsedCompileOnly = null;

        Map<String, String> compilerOptionValues = OptimizedTruffleRuntime.CompilerOptionsDescriptors.extractOptions(engineOptions);
        updateCompilerOptions(compilerOptionValues);
        this.compilerOptions = compilerOptionValues;
    }

    /**
     * Update compiler options based on runtime options. Note there is no support for compiler
     * options yet.
     */
    private void updateCompilerOptions(Map<String, String> options) {
        if (compilationFailureAction == ExceptionAction.ExitVM) {
            options.put("compiler.DiagnoseFailure", "true");
        } else if (compilationFailureAction == ExceptionAction.Diagnose) {
            options.put("compiler.DiagnoseFailure", "true");
        }
        if (TruffleOptions.AOT && traceTransferToInterpreter) {
            options.put("compiler.NodeSourcePositions", "true");
        }
        if (callTargetStatistics || callTargetStatisticDetails) {
            options.put("compiler.LogInlinedTargets", "true");
        }
    }

    public Map<String, String> getCompilerOptions() {
        return compilerOptions;
    }

    /**
     * Checks if a {@link OptimizedCallTarget} should be compiled. The
     * {@link OptimizedRuntimeOptions#Compilation Compilation} and
     * {@link OptimizedRuntimeOptions#CompileOnly CompileOnly} options are used to determine if the
     * calltarget should be compiled.
     */
    boolean acceptForCompilation(OptimizedCallTarget target) {
        if (!compilation) {
            return false;
        }
        Pair<List<String>, List<String>> value = getCompileOnly();
        if (value != null) {
            String name = target.getName();
            List<String> includes = value.getLeft();
            boolean included = includes.isEmpty();
            if (name != null) {
                for (int i = 0; !included && i < includes.size(); i++) {
                    if (name.contains(includes.get(i))) {
                        included = true;
                    }
                }
            }
            if (!included) {
                return false;
            }
            if (name != null) {
                for (String exclude : value.getRight()) {
                    if (name.contains(exclude)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Returns the include and exclude sets for the {@link OptimizedRuntimeOptions#CompileOnly
     * CompileOnly} option. The returned value is {@code null} if the {@code CompileOnly} option is
     * not specified. Otherwise the {@link Pair#getLeft() left} value is the include set and the
     * {@link Pair#getRight() right} value is the exclude set.
     */
    private Pair<List<String>, List<String>> getCompileOnly() {
        if (compileOnly == null) {
            return null;
        }
        Pair<List<String>, List<String>> result = parsedCompileOnly;
        if (result == null) {
            List<String> includesList = new ArrayList<>();
            List<String> excludesList = new ArrayList<>();
            String[] items = compileOnly.split(",");
            for (String item : items) {
                if (item.startsWith("~")) {
                    excludesList.add(item.substring(1));
                } else {
                    includesList.add(item);
                }
            }
            result = Pair.create(includesList, excludesList);
            parsedCompileOnly = result;
        }
        return result;
    }

    public OptionValues getEngineOptions() {
        return engineOptions;
    }

    @SuppressWarnings({"static-method", "unchecked"})
    public Collection<OptimizedCallTarget> getCallTargets() {
        if (polyglotEngine == null) {
            throw new IllegalStateException("The polyglot engine is closed.");
        }
        return (Collection<OptimizedCallTarget>) OptimizedRuntimeAccessor.ENGINE.findCallTargets(polyglotEngine);
    }

    private void validateOptions(SandboxPolicy sandboxPolicy) {
        if (sandboxPolicy.isStricterOrEqual(SandboxPolicy.CONSTRAINED) && compilationFailureAction != ExceptionAction.Silent && compilationFailureAction != ExceptionAction.Print) {
            throw OptimizedRuntimeAccessor.ENGINE.createPolyglotEngineException(
                            sandboxPolicyException(sandboxPolicy, "The engine.CompilationFailureAction option is set to " + compilationFailureAction.name() + ", but must be set to Silent or Print.",
                                            "use the default value (Silent) by removing Builder.option(\"engine.CompilationFailureAction\", ...) or set it to Print"));
        }
        if (compilationFailureAction == ExceptionAction.Throw && backgroundCompilation) {
            getEngineLogger().log(Level.WARNING, "The 'Throw' value of the 'engine.CompilationFailureAction' option requires the 'engine.BackgroundCompilation' option to be set to 'false'.");
        }
    }

    private int computeCallThresholdInInterpreter(OptionValues options) {
        if (compileImmediately) {
            return 0;
        }
        if (multiTier) {
            return Math.min(options.get(FirstTierMinInvokeThreshold), options.get(FirstTierCompilationThreshold));
        } else {
            return Math.min(options.get(MinInvokeThreshold), options.get(SingleTierCompilationThreshold));
        }
    }

    private int computeCallAndLoopThresholdInInterpreter(OptionValues options) {
        if (compileImmediately) {
            return 0;
        }
        if (multiTier) {
            return options.get(FirstTierCompilationThreshold);
        } else {
            return options.get(SingleTierCompilationThreshold);
        }
    }

    private int computeCallThresholdInFirstTier(OptionValues options) {
        if (compileImmediately) {
            return 0;
        }
        return Math.min(options.get(MinInvokeThreshold), options.get(LastTierCompilationThreshold));
    }

    private int computeCallAndLoopThresholdInFirstTier(OptionValues options) {
        if (compileImmediately) {
            return 0;
        }
        return options.get(LastTierCompilationThreshold);
    }

    public TruffleLogger getEngineLogger() {
        return getLogger("engine");
    }

    public TruffleLogger getLogger(String loggerId) {
        return polyglotEngine != null ? loggerFactory.apply(loggerId) : null;
    }

    @SuppressWarnings("static-method")
    public void mergeLoadedSources(Source[] sources) {
        OptimizedRuntimeAccessor.SOURCE.mergeLoadedSources(sources);
    }

    @SuppressWarnings("static-method")
    public Object enterLanguage(TruffleLanguage<?> language) {
        return OptimizedRuntimeAccessor.ENGINE.enterLanguageFromRuntime(language);
    }

    @SuppressWarnings("static-method")
    public void leaveLanguage(TruffleLanguage<?> language, Object prev) {
        OptimizedRuntimeAccessor.ENGINE.leaveLanguageFromRuntime(language, prev);
    }

    @SuppressWarnings("static-method")
    public TruffleLanguage<?> getLanguage(OptimizedCallTarget target) {
        RootNode root = target.getRootNode();
        return OptimizedRuntimeAccessor.NODES.getLanguage(root);
    }

}
