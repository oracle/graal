/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.ArgumentTypeSpeculation;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.BackgroundCompilation;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.Compilation;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationExceptionsAreFatal;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationExceptionsArePrinted;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationExceptionsAreThrown;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationFailureAction;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationStatisticDetails;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationStatistics;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationThreshold;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompileImmediately;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompileOnly;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.FirstTierCompilationThreshold;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.FirstTierMinInvokeThreshold;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.Inlining;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.MinInvokeThreshold;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.Mode;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.MultiTier;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.PerformanceWarningsAreFatal;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.Profiling;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.ReturnTypeSpeculation;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.Splitting;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.SplittingAllowForcedSplits;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.SplittingDumpDecisions;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.SplittingGrowthLimit;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.SplittingMaxCalleeSize;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.SplittingMaxNumberOfSplitNodes;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.SplittingMaxPropagationDepth;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.SplittingTraceEvents;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceCompilation;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceCompilationDetails;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceSplitting;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceSplittingSummary;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceTransferToInterpreter;
import static org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions.getPolyglotOptionValue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.EngineModeEnum;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.ExceptionAction;
import org.graalvm.compiler.truffle.runtime.debug.StatisticsListener;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Class used to store data used by the compiler in the Engine. Enables "global" compiler state per
 * engine.
 */
public final class EngineData {

    static final BiFunction<OptionValues, Supplier<TruffleLogger>, EngineData> ENGINE_DATA_SUPPLIER = new BiFunction<OptionValues, Supplier<TruffleLogger>, EngineData>() {
        @Override
        public EngineData apply(OptionValues engineOptions, Supplier<TruffleLogger> loggerFactory) {
            return new EngineData(engineOptions, loggerFactory);
        }
    };

    private static final AtomicLong engineCounter = new AtomicLong();

    int splitLimit;
    int splitCount;
    public final long id;
    private final Supplier<TruffleLogger> loggerFactory;
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
    @CompilationFinal public int splittingMaxNumberOfSplitNodes;

    // inlining options
    @CompilationFinal public boolean inlining;

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

    // computed fields.
    @CompilationFinal public int firstTierCallThreshold;
    @CompilationFinal public int firstTierCallAndLoopThreshold;
    @CompilationFinal public int lastTierCallThreshold;

    // Cached logger
    private volatile TruffleLogger logger;

    // Cached parsed CompileOnly includes and excludes
    private volatile Pair<List<String>, List<String>> parsedCompileOnly;

    EngineData(OptionValues options, Supplier<TruffleLogger> loggerFactory) {
        this.id = engineCounter.incrementAndGet();
        this.loggerFactory = loggerFactory;
        loadOptions(options);

        // the splittingStatistics requires options to be initialized
        this.splittingStatistics = new TruffleSplittingStrategy.SplitStatisticsData();
    }

    public OptionValues getEngineOptions() {
        return engineOptions;
    }

    void loadOptions(OptionValues options) {
        this.engineOptions = options;

        // splitting options
        this.splitting = getPolyglotOptionValue(options, Splitting) &&
                        getPolyglotOptionValue(options, Mode) != EngineModeEnum.LATENCY;
        this.splittingAllowForcedSplits = getPolyglotOptionValue(options, SplittingAllowForcedSplits);
        this.splittingDumpDecisions = getPolyglotOptionValue(options, SplittingDumpDecisions);
        this.splittingMaxCalleeSize = getPolyglotOptionValue(options, SplittingMaxCalleeSize);
        this.splittingMaxPropagationDepth = getPolyglotOptionValue(options, SplittingMaxPropagationDepth);
        this.splittingTraceEvents = getPolyglotOptionValue(options, SplittingTraceEvents);
        this.traceSplittingSummary = getPolyglotOptionValue(options, TraceSplittingSummary);
        this.traceSplits = getPolyglotOptionValue(options, TraceSplitting);
        this.splittingGrowthLimit = getPolyglotOptionValue(options, SplittingGrowthLimit);
        this.splittingMaxNumberOfSplitNodes = getPolyglotOptionValue(options, SplittingMaxNumberOfSplitNodes);

        // inlining options
        this.inlining = getPolyglotOptionValue(options, Inlining) &&
                        getPolyglotOptionValue(options, Mode) != EngineModeEnum.LATENCY;

        // compilation options
        this.compilation = getPolyglotOptionValue(options, Compilation);
        this.compileOnly = getPolyglotOptionValue(options, CompileOnly);
        this.compileImmediately = getPolyglotOptionValue(options, CompileImmediately);
        this.multiTier = getPolyglotOptionValue(options, MultiTier);

        this.returnTypeSpeculation = getPolyglotOptionValue(options, ReturnTypeSpeculation);
        this.argumentTypeSpeculation = getPolyglotOptionValue(options, ArgumentTypeSpeculation);
        this.traceCompilation = getPolyglotOptionValue(options, TraceCompilation);
        this.traceCompilationDetails = getPolyglotOptionValue(options, TraceCompilationDetails);
        this.backgroundCompilation = getPolyglotOptionValue(options, BackgroundCompilation);
        this.firstTierCallThreshold = computeFirstTierCallThreshold(options);
        this.firstTierCallAndLoopThreshold = computeFirstTierCallAndLoopThreshold(options);
        this.lastTierCallThreshold = firstTierCallAndLoopThreshold;
        this.callTargetStatisticDetails = getPolyglotOptionValue(options, CompilationStatisticDetails);
        this.callTargetStatistics = getPolyglotOptionValue(options, CompilationStatistics) || this.callTargetStatisticDetails;
        this.statisticsListener = this.callTargetStatistics ? StatisticsListener.createEngineListener(GraalTruffleRuntime.getRuntime()) : null;
        this.profilingEnabled = getPolyglotOptionValue(options, Profiling);
        this.traceTransferToInterpreter = getPolyglotOptionValue(options, TraceTransferToInterpreter);
        this.compilationFailureAction = computeCompilationFailureAction(options);
        validateOptions();
        parsedCompileOnly = null;
    }

    /**
     * Checks if the {@link OptimizedCallTarget} for the given {@link RootNode} should be compiled.
     * The {@link PolyglotCompilerOptions#Compilation Compilation} and
     * {@link PolyglotCompilerOptions#CompileOnly CompileOnly} options are used to determine if the
     * calltarget should be compiled.
     */
    boolean acceptForCompilation(RootNode rootNode) {
        if (!compilation) {
            return false;
        }
        Pair<List<String>, List<String>> value = getCompileOnly();
        if (value != null) {
            String name = rootNode.getName();
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
     * Returns the include and exclude sets for the {@link PolyglotCompilerOptions#CompileOnly
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

    private static ExceptionAction computeCompilationFailureAction(OptionValues options) {
        ExceptionAction action = getPolyglotOptionValue(options, CompilationFailureAction);
        if (action.ordinal() < ExceptionAction.Print.ordinal() && getPolyglotOptionValue(options, CompilationExceptionsArePrinted)) {
            action = ExceptionAction.Print;
        }
        if (action.ordinal() < ExceptionAction.Throw.ordinal() && getPolyglotOptionValue(options, CompilationExceptionsAreThrown)) {
            action = ExceptionAction.Throw;
        }
        if (action.ordinal() < ExceptionAction.ExitVM.ordinal() && getPolyglotOptionValue(options, CompilationExceptionsAreFatal)) {
            action = ExceptionAction.ExitVM;
        }
        if (action.ordinal() < ExceptionAction.ExitVM.ordinal() && !getPolyglotOptionValue(options, PerformanceWarningsAreFatal).isEmpty()) {
            action = ExceptionAction.ExitVM;
        }
        return action;
    }

    private void validateOptions() {
        if (compilationFailureAction == ExceptionAction.Throw && backgroundCompilation) {
            getLogger().log(Level.WARNING, "The 'Throw' value of the 'engine.CompilationFailureAction' option requires the 'engine.BackgroundCompilation' option to be set to 'false'.");
        }
        for (OptionDescriptor descriptor : PolyglotCompilerOptions.getDescriptors()) {
            if (descriptor.isDeprecated() && engineOptions.hasBeenSet(descriptor.getKey())) {
                String optionName = descriptor.getName();
                String deprecationMessage = descriptor.getDeprecationMessage();
                if (deprecationMessage.isEmpty()) {
                    deprecationMessage = "Will be removed with no replacement.";
                }
                getLogger().log(Level.WARNING, String.format("The option '%s' is deprecated.%n%s", optionName, deprecationMessage));
            }
        }
    }

    private int computeFirstTierCallThreshold(OptionValues options) {
        if (compileImmediately) {
            return 0;
        }
        if (multiTier) {
            return Math.min(getPolyglotOptionValue(options, FirstTierMinInvokeThreshold), getPolyglotOptionValue(options, FirstTierCompilationThreshold));
        } else {
            return Math.min(getPolyglotOptionValue(options, MinInvokeThreshold), getPolyglotOptionValue(options, CompilationThreshold));
        }
    }

    private int computeFirstTierCallAndLoopThreshold(OptionValues options) {
        if (compileImmediately) {
            return 0;
        }
        if (multiTier) {
            return getPolyglotOptionValue(options, FirstTierCompilationThreshold);
        } else {
            return getPolyglotOptionValue(options, CompilationThreshold);
        }
    }

    public TruffleLogger getLogger() {
        TruffleLogger result = logger;
        if (result == null) {
            result = loggerFactory.get();
            logger = result;
        }
        return result;
    }

}
