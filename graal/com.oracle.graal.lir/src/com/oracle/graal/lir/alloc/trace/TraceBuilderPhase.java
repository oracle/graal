/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.lir.alloc.trace;

import static com.oracle.graal.lir.alloc.trace.TraceUtil.isTrivialTrace;

import java.util.List;

import com.oracle.graal.compiler.common.alloc.BiDirectionalTraceBuilder;
import com.oracle.graal.compiler.common.alloc.Trace;
import com.oracle.graal.compiler.common.alloc.TraceBuilderResult;
import com.oracle.graal.compiler.common.alloc.TraceBuilderResult.TrivialTracePredicate;
import com.oracle.graal.compiler.common.alloc.TraceStatisticsPrinter;
import com.oracle.graal.compiler.common.alloc.UniDirectionalTraceBuilder;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.phases.AllocationPhase;
import com.oracle.graal.options.Option;
import com.oracle.graal.options.OptionType;
import com.oracle.graal.options.OptionValue;

import jdk.vm.ci.code.TargetDescription;

public class TraceBuilderPhase extends AllocationPhase {

    public static class Options {
        // @formatter:off
        @Option(help = "Use bidirectional trace builder.", type = OptionType.Debug)
        public static final OptionValue<Boolean> TraceRAbiDirectionalTraceBuilder = new OptionValue<>(false);
        @Option(help = "Schedule trivial traces as early as possible.", type = OptionType.Debug)
        public static final OptionValue<Boolean> TraceRAScheduleTrivialTracesEarly = new OptionValue<>(true);
        // @formatter:on
    }

    private static final int TRACE_LOG_LEVEL = 1;
    public static final int TRACE_DUMP_LEVEL = 3;

    @Override
    protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder, AllocationContext context) {
        B startBlock = linearScanOrder.get(0);
        LIR lir = lirGenRes.getLIR();
        assert startBlock.equals(lir.getControlFlowGraph().getStartBlock());

        final TraceBuilderResult<B> traceBuilderResult = getTraceBuilderResult(lir, startBlock, linearScanOrder);

        if (Debug.isLogEnabled(TRACE_LOG_LEVEL)) {
            List<Trace<B>> traces = traceBuilderResult.getTraces();
            for (int i = 0; i < traces.size(); i++) {
                Trace<B> trace = traces.get(i);
                Debug.log(TRACE_LOG_LEVEL, "Trace %5d: %s%s", i, trace, isTrivialTrace(lirGenRes.getLIR(), trace) ? " (trivial)" : "");
            }
        }
        TraceStatisticsPrinter.printTraceStatistics(traceBuilderResult, lirGenRes.getCompilationUnitName());
        Debug.dump(TRACE_DUMP_LEVEL, traceBuilderResult, "After TraceBuilding");
        context.contextAdd(traceBuilderResult);
    }

    private static <B extends AbstractBlockBase<B>> TraceBuilderResult<B> getTraceBuilderResult(LIR lir, B startBlock, List<B> blocks) {
        TraceBuilderResult.TrivialTracePredicate pred = !Options.TraceRAScheduleTrivialTracesEarly.getValue() ? null : new TrivialTracePredicate() {
            @Override
            public <T extends AbstractBlockBase<T>> boolean isTrivialTrace(Trace<T> trace) {
                return TraceUtil.isTrivialTrace(lir, trace);
            }
        };

        if (Options.TraceRAbiDirectionalTraceBuilder.getValue()) {
            return BiDirectionalTraceBuilder.computeTraces(startBlock, blocks, pred);
        }
        return UniDirectionalTraceBuilder.computeTraces(startBlock, blocks, pred);
    }
}
