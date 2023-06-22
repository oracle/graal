/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import static org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime.getRuntime;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.ArgumentTypeSpeculation;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.BackgroundCompilation;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.Compilation;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.CompilationFailureAction;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.CompilationStatisticDetails;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.CompilationStatistics;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.CompileAOTOnCreate;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.CompileImmediately;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.CompileOnly;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.FirstTierCompilationThreshold;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.FirstTierMinInvokeThreshold;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.LastTierCompilationThreshold;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.MinInvokeThreshold;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.Mode;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.MultiTier;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.PriorityQueue;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.Profiling;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.PropagateLoopCountToLexicalSingleCaller;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.PropagateLoopCountToLexicalSingleCallerMaxDepth;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.ReturnTypeSpeculation;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.SingleTierCompilationThreshold;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.Splitting;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.SplittingAllowForcedSplits;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.SplittingDumpDecisions;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.SplittingGrowthLimit;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.SplittingMaxCalleeSize;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.SplittingMaxPropagationDepth;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.SplittingTraceEvents;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.TraceCompilation;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.TraceCompilationDetails;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.TraceDeoptimizeFrame;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.TraceSplitting;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.TraceSplittingSummary;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.TraceTransferToInterpreter;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.TraversingQueueFirstTierBonus;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.TraversingQueueFirstTierPriority;
import static org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.TraversingQueueWeightingBothTiers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.logging.Level;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.EngineModeEnum;
import org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions.ExceptionAction;
import org.graalvm.compiler.truffle.runtime.debug.StatisticsListener;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

/**
 * Class used to store data used by the compiler in the Engine. Enables "global" compiler state per
 * engine. One-to-one relationship with a polyglot Engine instance.
 */
public final class EngineData {

    private static final AtomicLong engineCounter = new AtomicLong();

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

    EngineData(Object polyglotEngine, OptionValues runtimeOptions, Function<String, TruffleLogger> loggerFactory) {
        Objects.requireNonNull(polyglotEngine);
        Objects.requireNonNull(runtimeOptions);
        this.polyglotEngine = polyglotEngine;
        this.id = engineCounter.incrementAndGet();
        this.loggerFactory = loggerFactory;
        this.loadOptions(runtimeOptions);

        // the splittingStatistics requires options to be initialized
        this.splittingStatistics = new TruffleSplittingStrategy.SplitStatisticsData();
    }

    public void preinitializeContext() {
        GraalRuntimeAccessor.ENGINE.preinitializeContext(this.polyglotEngine);
    }

    public void finalizeStore() {
        GraalRuntimeAccessor.ENGINE.finalizeStore(this.polyglotEngine);
    }

    public Object getEngineLock() {
        return GraalRuntimeAccessor.ENGINE.getEngineLock(this.polyglotEngine);
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

    void onEnginePatch(OptionValues newRuntimeOptions, Function<String, TruffleLogger> newLoggerFactory) {
        this.loggerFactory = newLoggerFactory;
        loadOptions(newRuntimeOptions);
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

    private void loadOptions(OptionValues options) {
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
        this.statisticsListener = this.callTargetStatistics ? StatisticsListener.createEngineListener(GraalTruffleRuntime.getRuntime()) : null;
        this.profilingEnabled = options.get(Profiling);
        this.traceTransferToInterpreter = options.get(TraceTransferToInterpreter);
        this.traceDeoptimizeFrame = options.get(TraceDeoptimizeFrame);
        this.compilationFailureAction = options.get(CompilationFailureAction);
        validateOptions();
        parsedCompileOnly = null;

        Map<String, String> compilerOptionValues = GraalTruffleRuntime.CompilerOptionsDescriptors.extractOptions(engineOptions);
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
        return (Collection<OptimizedCallTarget>) GraalRuntimeAccessor.ENGINE.findCallTargets(polyglotEngine);
    }

    private void validateOptions() {
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
        GraalRuntimeAccessor.SOURCE.mergeLoadedSources(sources);
    }

    @SuppressWarnings("static-method")
    public Object enterLanguage(TruffleLanguage<?> language) {
        return GraalRuntimeAccessor.ENGINE.enterLanguageFromRuntime(language);
    }

    @SuppressWarnings("static-method")
    public void leaveLanguage(TruffleLanguage<?> language, Object prev) {
        GraalRuntimeAccessor.ENGINE.leaveLanguageFromRuntime(language, prev);
    }

    @SuppressWarnings("static-method")
    public TruffleLanguage<?> getLanguage(OptimizedCallTarget target) {
        RootNode root = target.getRootNode();
        return GraalRuntimeAccessor.NODES.getLanguage(root);
    }

}
