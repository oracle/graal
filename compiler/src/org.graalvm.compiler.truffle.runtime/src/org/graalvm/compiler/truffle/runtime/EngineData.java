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

import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.ArgumentTypeSpeculation;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.BackgroundCompilation;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.Compilation;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.CompilationExceptionsAreThrown;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.CompilationThreshold;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.CompileImmediately;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.CompileOnly;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.FirstTierCompilationThreshold;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.FirstTierMinInvokeThreshold;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.InvalidationReprofileCount;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.MinInvokeThreshold;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.Mode;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.MultiTier;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.PerformanceWarningsAreFatal;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.ReplaceReprofileCount;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.ReturnTypeSpeculation;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.Splitting;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.SplittingAllowForcedSplits;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.SplittingDumpDecisions;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.SplittingGrowthLimit;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.SplittingMaxCalleeSize;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.SplittingMaxNumberOfSplitNodes;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.SplittingMaxPropagationDepth;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.SplittingTraceEvents;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.TraceCompilation;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.TraceCompilationDetails;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.TraceSplittingSummary;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.getValue;
import static org.graalvm.compiler.truffle.runtime.SharedTruffleRuntimeOptions.TruffleCompilationStatisticDetails;
import static org.graalvm.compiler.truffle.runtime.SharedTruffleRuntimeOptions.TruffleCompilationStatistics;

import java.util.function.Function;

import org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.EngineModeEnum;
import org.graalvm.options.OptionValues;

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

    int splitLimit;
    int splitCount;
    final OptionValues engineOptions;
    final TruffleSplittingStrategy.SplitStatisticsReporter reporter;

    // splitting options
    public final boolean splitting;
    public final boolean splittingAllowForcedSplits;
    public final boolean splittingDumpDecisions;
    public final boolean splittingTraceEvents;
    public final boolean traceSplittingSummary;
    public final int splittingMaxCalleeSize;
    public final int splittingMaxPropagationDepth;
    public final double splittingGrowthLimit;
    public final int splittingMaxNumberOfSplitNodes;

    // compilation options
    public final boolean compilation;
    public final boolean compileImmediately;
    public final int minInvokeThreshold;
    public final int compilationThreshold;
    public final boolean multiTier;
    public final int firstTierMinInvokeThreshold;
    public final int firstTierCompilationThreshold;
    public final boolean returnTypeSpeculation;
    public final int invalidationReprofileCount;
    public final int replaceReprofileCount;
    public final boolean argumentTypeSpeculation;
    public final boolean traceCompilation;
    public final boolean traceCompilationDetails;
    public final boolean backgroundCompilation;
    public final boolean compilationExceptionsAreThrown;
    public final boolean performanceWarningsAreFatal;
    public final String compileOnly;

    public final boolean callTargetStatistics;

    EngineData(OptionValues options) {
        this.engineOptions = options;
        // splitting options
        this.splitting = getValue(options, Splitting) &&
                        getValue(options, Mode) != EngineModeEnum.LATENCY;
        this.splittingAllowForcedSplits = getValue(options, SplittingAllowForcedSplits);
        this.splittingDumpDecisions = getValue(options, SplittingDumpDecisions);
        this.splittingMaxCalleeSize = getValue(options, SplittingMaxCalleeSize);
        this.splittingMaxPropagationDepth = getValue(options, SplittingMaxPropagationDepth);
        this.splittingTraceEvents = getValue(options, SplittingTraceEvents);
        this.traceSplittingSummary = getValue(options, TraceSplittingSummary);
        this.splittingGrowthLimit = getValue(options, SplittingGrowthLimit);
        this.splittingMaxNumberOfSplitNodes = getValue(options, SplittingMaxNumberOfSplitNodes);

        // compilation options
        this.compilation = getValue(options, Compilation);
        this.compileOnly = getValue(options, CompileOnly);
        this.compileImmediately = getValue(options, CompileImmediately);
        this.minInvokeThreshold = getValue(options, MinInvokeThreshold);
        this.compilationThreshold = getValue(options, CompilationThreshold);
        this.multiTier = getValue(options, MultiTier);
        if (multiTier) {
            firstTierMinInvokeThreshold = getValue(options, FirstTierMinInvokeThreshold);
            firstTierCompilationThreshold = getValue(options, FirstTierCompilationThreshold);
        } else {
            firstTierMinInvokeThreshold = Integer.MAX_VALUE;
            firstTierCompilationThreshold = Integer.MAX_VALUE;
        }
        this.returnTypeSpeculation = getValue(options, ReturnTypeSpeculation);
        this.invalidationReprofileCount = getValue(options, InvalidationReprofileCount);
        this.replaceReprofileCount = getValue(options, ReplaceReprofileCount);
        this.argumentTypeSpeculation = getValue(options, ArgumentTypeSpeculation);
        this.traceCompilation = getValue(options, TraceCompilation);
        this.traceCompilationDetails = getValue(options, TraceCompilationDetails);
        this.backgroundCompilation = getValue(options, BackgroundCompilation);
        this.compilationExceptionsAreThrown = getValue(options, CompilationExceptionsAreThrown);
        this.performanceWarningsAreFatal = getValue(options, PerformanceWarningsAreFatal);

        this.callTargetStatistics = TruffleRuntimeOptions.getValue(TruffleCompilationStatistics) ||
                        TruffleRuntimeOptions.getValue(TruffleCompilationStatisticDetails);

        // the reporter requires options to be initialized
        this.reporter = new TruffleSplittingStrategy.SplitStatisticsReporter(this);
    }

}
