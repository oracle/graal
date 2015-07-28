/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.lir.alloc.trace.TraceLinearScan.Options.*;
import static com.oracle.graal.compiler.common.GraalOptions.*;

import java.util.*;

import jdk.internal.jvmci.code.*;
import jdk.internal.jvmci.options.*;

import com.oracle.graal.compiler.common.alloc.*;
import com.oracle.graal.compiler.common.alloc.TraceBuilder.TraceBuilderResult;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.lir.alloc.lsra.*;
import com.oracle.graal.lir.alloc.lsra.ssa.*;
import com.oracle.graal.lir.alloc.lsra.ssi.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.gen.LIRGeneratorTool.SpillMoveFactory;
import com.oracle.graal.lir.phases.AllocationPhase.AllocationContext;

public final class TraceLinearScan extends LinearScan {

    public static class Options {
        // @formatter:off
        @Option(help = "", type = OptionType.Debug)
        public static final OptionValue<Boolean> TraceRAsimpleLifetimeAnalysis = new OptionValue<>(true);
        // @formatter:on
    }

    private final TraceBuilderResult<?> traceBuilderResult;

    public TraceLinearScan(TargetDescription target, LIRGenerationResult res, SpillMoveFactory spillMoveFactory, RegisterAllocationConfig regAllocConfig,
                    List<? extends AbstractBlockBase<?>> sortedBlocks, TraceBuilderResult<?> traceBuilderResult) {
        super(target, res, spillMoveFactory, regAllocConfig, sortedBlocks);
        this.traceBuilderResult = traceBuilderResult;
    }

    @Override
    protected <B extends AbstractBlockBase<B>> void allocate(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder,
                    SpillMoveFactory spillMoveFactory, RegisterAllocationConfig registerAllocationConfig) {

        /*
         * This is the point to enable debug logging for the whole register allocation.
         */
        try (Indent indent = Debug.logAndIndent("LinearScan allocate")) {
            AllocationContext context = new AllocationContext(spillMoveFactory, registerAllocationConfig);

            createLifetimeAnalysisPhase().apply(target, lirGenRes, codeEmittingOrder, linearScanOrder, context, false);

            try (Scope s = Debug.scope("AfterLifetimeAnalysis", (Object) intervals())) {
                sortIntervalsBeforeAllocation();

                createRegisterAllocationPhase().apply(target, lirGenRes, codeEmittingOrder, linearScanOrder, context, false);

                if (LinearScan.Options.LIROptLSRAOptimizeSpillPosition.getValue()) {
                    createOptimizeSpillPositionPhase().apply(target, lirGenRes, codeEmittingOrder, linearScanOrder, context, false);
                }
                // resolve intra-trace data-flow
                LinearScanResolveDataFlowPhase dataFlowPhase = createResolveDataFlowPhase();
                dataFlowPhase.apply(target, lirGenRes, codeEmittingOrder, linearScanOrder, context, false);
                Debug.dump(TraceRegisterAllocationPhase.TRACE_DUMP_LEVEL, sortedBlocks(), "%s", dataFlowPhase.getName());

                LinearScanAssignLocationsPhase assignPhase = createAssignLocationsPhase();
                assignPhase.apply(target, lirGenRes, codeEmittingOrder, linearScanOrder, context, false);

                if (DetailedAsserts.getValue()) {
                    verifyIntervals();
                }
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
        }
    }

    @Override
    protected MoveResolver createMoveResolver() {
        SSAMoveResolver moveResolver = new SSAMoveResolver(this);
        assert moveResolver.checkEmpty();
        return moveResolver;
    }

    @Override
    protected LinearScanLifetimeAnalysisPhase createLifetimeAnalysisPhase() {
        if (TraceRAsimpleLifetimeAnalysis.getValue()) {
            return new TraceSimpleLifetimeAnalysisPhase(this, traceBuilderResult);
        }
        return new TraceLinearScanLifetimeAnalysisPhase(this, traceBuilderResult);
    }

    @Override
    protected LinearScanResolveDataFlowPhase createResolveDataFlowPhase() {
        return new TraceLinearScanResolveDataFlowPhase(this);
    }

    @Override
    protected LinearScanEliminateSpillMovePhase createSpillMoveEliminationPhase() {
        return new SSILinearScanEliminateSpillMovePhase(this);
    }

    @Override
    public void printIntervals(String label) {
        if (Debug.isDumpEnabled(TraceRegisterAllocationPhase.TRACE_DUMP_LEVEL)) {
            super.printIntervals(label);
        }
    }

    @Override
    public void printLir(String label, boolean hirValid) {
        if (Debug.isDumpEnabled(TraceRegisterAllocationPhase.TRACE_DUMP_LEVEL)) {
            Debug.dump(TraceRegisterAllocationPhase.TRACE_DUMP_LEVEL, sortedBlocks(), label);
        }
    }

}
