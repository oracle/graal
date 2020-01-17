/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationExceptionsAreThrown;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationStatistics;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationStatisticDetails;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationThreshold;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompileImmediately;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompileOnly;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.FirstTierCompilationThreshold;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.FirstTierMinInvokeThreshold;
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
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceSplittingSummary;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceTransferToInterpreter;
import static org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions.getPolyglotOptionValue;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.EngineModeEnum;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import org.graalvm.compiler.truffle.runtime.debug.StatisticsListener;

/**
 * Class used to store data used by the compiler in the Engine. Enables "global" compiler state per
 * engine.
 */
public final class EngineData {

    static final Function<OptionValues, EngineData> ENGINE_DATA_SUPPLIER = new Function<OptionValues, EngineData>() {
        @Override
        public EngineData apply(OptionValues engineOptions) {
            return new EngineData(engineOptions);
        }
    };

    private static final AtomicLong engineCounter = new AtomicLong();

    int splitLimit;
    int splitCount;
    public final long id;
    @CompilationFinal OptionValues engineOptions;
    final TruffleSplittingStrategy.SplitStatisticsReporter reporter;
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
    @CompilationFinal public int splittingMaxCalleeSize;
    @CompilationFinal public int splittingMaxPropagationDepth;
    @CompilationFinal public double splittingGrowthLimit;
    @CompilationFinal public int splittingMaxNumberOfSplitNodes;

    // compilation options
    @CompilationFinal public boolean compilation;
    @CompilationFinal public boolean compileImmediately;
    @CompilationFinal public boolean multiTier;
    @CompilationFinal public boolean returnTypeSpeculation;
    @CompilationFinal public boolean argumentTypeSpeculation;
    @CompilationFinal public boolean traceCompilation;
    @CompilationFinal public boolean traceCompilationDetails;
    @CompilationFinal public boolean backgroundCompilation;
    @CompilationFinal public boolean compilationExceptionsAreThrown;
    @CompilationFinal public boolean performanceWarningsAreFatal;
    @CompilationFinal public String compileOnly;
    @CompilationFinal public boolean callTargetStatistics;
    @CompilationFinal public boolean callTargetStatisticDetails;
    @CompilationFinal public boolean profilingEnabled;
    @CompilationFinal public boolean traceTransferToInterpreter;

    // computed fields.
    @CompilationFinal public int firstTierCallThreshold;
    @CompilationFinal public int firstTierCallAndLoopThreshold;
    @CompilationFinal public int lastTierCallThreshold;

    EngineData(OptionValues options) {
        this.id = engineCounter.incrementAndGet();
        // splitting options
        loadOptions(options);

        // the reporter requires options to be initialized
        this.reporter = new TruffleSplittingStrategy.SplitStatisticsReporter(this);
    }

    void loadOptions(OptionValues options) {
        this.engineOptions = options;
        this.splitting = getPolyglotOptionValue(options, Splitting) &&
                        getPolyglotOptionValue(options, Mode) != EngineModeEnum.LATENCY;
        this.splittingAllowForcedSplits = getPolyglotOptionValue(options, SplittingAllowForcedSplits);
        this.splittingDumpDecisions = getPolyglotOptionValue(options, SplittingDumpDecisions);
        this.splittingMaxCalleeSize = getPolyglotOptionValue(options, SplittingMaxCalleeSize);
        this.splittingMaxPropagationDepth = getPolyglotOptionValue(options, SplittingMaxPropagationDepth);
        this.splittingTraceEvents = getPolyglotOptionValue(options, SplittingTraceEvents);
        this.traceSplittingSummary = getPolyglotOptionValue(options, TraceSplittingSummary);
        this.splittingGrowthLimit = getPolyglotOptionValue(options, SplittingGrowthLimit);
        this.splittingMaxNumberOfSplitNodes = getPolyglotOptionValue(options, SplittingMaxNumberOfSplitNodes);

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
        this.compilationExceptionsAreThrown = getPolyglotOptionValue(options, CompilationExceptionsAreThrown);
        this.performanceWarningsAreFatal = getPolyglotOptionValue(options, PerformanceWarningsAreFatal);
        this.firstTierCallThreshold = computeFirstTierCallThreshold(options);
        this.firstTierCallAndLoopThreshold = computeFirstTierCallAndLoopThreshold(options);
        this.lastTierCallThreshold = firstTierCallAndLoopThreshold;
        this.callTargetStatisticDetails = getPolyglotOptionValue(options, CompilationStatisticDetails);
        this.callTargetStatistics = getPolyglotOptionValue(options, CompilationStatistics) || this.callTargetStatisticDetails;
        this.statisticsListener = this.callTargetStatistics ? StatisticsListener.createEngineListener(GraalTruffleRuntime.getRuntime()) : null;
        this.profilingEnabled = getPolyglotOptionValue(options, Profiling);
        this.traceTransferToInterpreter = getPolyglotOptionValue(options, TraceTransferToInterpreter);
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

}
