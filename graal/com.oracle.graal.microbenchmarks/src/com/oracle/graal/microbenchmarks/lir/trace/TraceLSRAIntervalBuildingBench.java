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
package com.oracle.graal.microbenchmarks.lir.trace;

import java.util.List;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;

import com.oracle.graal.compiler.common.alloc.RegisterAllocationConfig;
import com.oracle.graal.compiler.common.alloc.Trace;
import com.oracle.graal.compiler.common.alloc.TraceBuilderResult;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.lir.alloc.trace.TraceBuilderPhase;
import com.oracle.graal.lir.alloc.trace.lsra.IntervalData;
import com.oracle.graal.lir.alloc.trace.lsra.TraceLinearScanLifetimeAnalysisPhase;
import com.oracle.graal.lir.alloc.trace.lsra.TraceLinearScanLifetimeAnalysisPhase.Analyser;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.gen.LIRGeneratorTool.MoveFactory;
import com.oracle.graal.lir.phases.AllocationPhase;
import com.oracle.graal.lir.phases.AllocationPhase.AllocationContext;
import com.oracle.graal.lir.phases.LIRPhaseSuite;
import com.oracle.graal.lir.phases.LIRSuites;
import com.oracle.graal.lir.ssi.SSIConstructionPhase;
import com.oracle.graal.microbenchmarks.graal.GraalBenchmark;
import com.oracle.graal.microbenchmarks.lir.GraalCompilerState;

import jdk.vm.ci.code.TargetDescription;

/**
 * Benchmarks {@link TraceLinearScan TraceRA} {@link TraceLinearScanLifetimeAnalysisPhase lifetime
 * analysis phase}.
 */
public class TraceLSRAIntervalBuildingBench extends GraalBenchmark {

    private static class DummyTraceAllocatorPhase extends AllocationPhase {
        private IntervalData intervalData = null;

        @Override
        @SuppressWarnings("try")
        protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder,
                        AllocationContext context) {
            MoveFactory spillMoveFactory = context.spillMoveFactory;
            RegisterAllocationConfig registerAllocationConfig = context.registerAllocationConfig;
            TraceBuilderResult<B> resultTraces = getTraces(context);

            for (Trace<B> trace : resultTraces.getTraces()) {
                intervalData = new IntervalData(target, lirGenRes, registerAllocationConfig, trace);
                Analyser a = new TraceLinearScanLifetimeAnalysisPhase.Analyser(intervalData, resultTraces, trace.getBlocks(), lirGenRes.getLIR(), true, spillMoveFactory,
                                registerAllocationConfig.getRegisterConfig().getCallerSaveRegisters());
                a.analyze();
            }
        }

        @SuppressWarnings("unchecked")
        private static <B extends AbstractBlockBase<B>> TraceBuilderResult<B> getTraces(AllocationContext context) {
            return context.contextLookup(TraceBuilderResult.class);
        }
    }

    public abstract static class AllocationState extends GraalCompilerState {

        private static final DummyTraceAllocatorPhase LTA_PHASE = new DummyTraceAllocatorPhase();
        private static final SSIConstructionPhase SSI_CONSTRUCTION_PHASE = new SSIConstructionPhase();
        private static final TraceBuilderPhase TRACE_BUILDER_PHASE = new TraceBuilderPhase();

        private AllocationContext allocationContext;

        @Override
        protected LIRSuites getLIRSuites() {
            LIRSuites ls = super.getLIRSuites();
            LIRPhaseSuite<AllocationContext> allocationStage = new LIRPhaseSuite<>();
            allocationStage.appendPhase(TRACE_BUILDER_PHASE);
            allocationStage.appendPhase(SSI_CONSTRUCTION_PHASE);
            return new LIRSuites(ls.getPreAllocationOptimizationStage(), allocationStage, ls.getPostAllocationOptimizationStage());
        }

        @Setup(Level.Trial)
        public void setup() {
            initializeMethod();
            prepareRequest();
            emitFrontEnd();
            generateLIR();
            preAllocationStage();
            // context for all allocation phases
            allocationContext = createAllocationContext();
            applyLIRPhase(TRACE_BUILDER_PHASE, allocationContext);
            applyLIRPhase(SSI_CONSTRUCTION_PHASE, allocationContext);
        }

        public IntervalData compile() {
            applyLIRPhase(LTA_PHASE, allocationContext);
            return LTA_PHASE.intervalData;
        }

    }

    public static class State extends AllocationState {
        @MethodDescString @Param({
                        "java.lang.String#equals",
                        "java.util.HashMap#computeIfAbsent"
        }) public String method;
    }

    @Benchmark
    public IntervalData buildIntervals(State s) {
        return s.compile();
    }
}
