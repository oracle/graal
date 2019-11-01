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

import org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.EngineModeEnum;
import org.graalvm.options.OptionValues;

import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.Mode;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.Splitting;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.SplittingAllowForcedSplits;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.SplittingDumpDecisions;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.SplittingGrowthLimit;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.SplittingMaxCalleeSize;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.SplittingMaxNumberOfSplitNodes;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.SplittingMaxPropagationDepth;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.SplittingTraceEvents;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.TraceSplittingSummary;
import static org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions.getValue;

import java.util.function.Function;

/**
 * Class used to store data used by the compiler in the Engine. Enables "global" compiler state per
 * engine.
 */
final class EngineData {

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
    final boolean splitting;
    final boolean splittingAllowForcedSplits;
    final boolean splittingDumpDecisions;
    final boolean splittingTraceEvents;
    final boolean traceSplittingSummary;
    final int splittingMaxCalleeSize;
    final int splittingMaxPropagationDepth;
    final double splittingGrowthLimit;
    final int splittingMaxNumberOfSplitNodes;

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

        // the reporter requires options to be initialized
        this.reporter = new TruffleSplittingStrategy.SplitStatisticsReporter(this);
    }

}
