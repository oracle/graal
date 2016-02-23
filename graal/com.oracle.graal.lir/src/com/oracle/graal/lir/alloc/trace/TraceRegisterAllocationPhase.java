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

import java.util.Collection;
import java.util.List;

import com.oracle.graal.compiler.common.alloc.RegisterAllocationConfig;
import com.oracle.graal.compiler.common.alloc.Trace;
import com.oracle.graal.compiler.common.alloc.TraceBuilderResult;
import com.oracle.graal.compiler.common.alloc.TraceStatisticsPrinter;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.debug.DebugMetric;
import com.oracle.graal.debug.Indent;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.StandardOp.JumpOp;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.lir.alloc.trace.TraceAllocationPhase.TraceAllocationContext;
import com.oracle.graal.lir.alloc.trace.TraceBuilderPhase.TraceBuilderContext;
import com.oracle.graal.lir.alloc.trace.lsra.TraceLinearScan;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.gen.LIRGeneratorTool.MoveFactory;
import com.oracle.graal.lir.phases.AllocationPhase;
import com.oracle.graal.lir.ssi.SSIUtil;
import com.oracle.graal.lir.ssi.SSIVerifier;
import com.oracle.graal.options.Option;
import com.oracle.graal.options.OptionType;
import com.oracle.graal.options.OptionValue;

import jdk.vm.ci.code.TargetDescription;

/**
 * An implementation of a Trace Register Allocator as described in <a
 * href="http://dx.doi.org/10.1145/2814189.2814199">"Trace Register Allocation"</a> by Josef Eisl.
 */
public final class TraceRegisterAllocationPhase extends AllocationPhase {

    public static class Options {
        // @formatter:off
        @Option(help = "Use inter-trace register hints.", type = OptionType.Debug)
        public static final OptionValue<Boolean> TraceRAuseInterTraceHints = new OptionValue<>(true);
        @Option(help = "Use special allocator for trivial blocks.", type = OptionType.Debug)
        public static final OptionValue<Boolean> TraceRAtrivialBlockAllocator = new OptionValue<>(true);
        @Option(help = "Share information about spilled values to other traces.", type = OptionType.Debug)
        public static final OptionValue<Boolean> TraceRAshareSpillInformation = new OptionValue<>(true);
        @Option(help = "Reuse spill slots for global move resolution cycle breaking.", type = OptionType.Debug)
        public static final OptionValue<Boolean> TraceRAreuseStackSlotsForMoveResolutionCycleBreaking = new OptionValue<>(true);
        // @formatter:on
    }

    private static final TraceGlobalMoveResolutionPhase TRACE_GLOBAL_MOVE_RESOLUTION_PHASE = new TraceGlobalMoveResolutionPhase();
    private static final TraceTrivialAllocator TRACE_TRIVIAL_ALLOCATOR = new TraceTrivialAllocator();
    private static final TraceBuilderPhase TRACE_BUILDER_PHASE = new TraceBuilderPhase();

    public static final int TRACE_DUMP_LEVEL = 3;
    private static final int TRACE_LOG_LEVEL = 1;

    private static final DebugMetric trivialTracesMetric = Debug.metric("TraceRA[trivialTraces]");
    private static final DebugMetric tracesMetric = Debug.metric("TraceRA[traces]");

    @Override
    @SuppressWarnings("try")
    protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder, AllocationContext context) {
        MoveFactory spillMoveFactory = context.spillMoveFactory;
        RegisterAllocationConfig registerAllocationConfig = context.registerAllocationConfig;
        LIR lir = lirGenRes.getLIR();
        assert SSIVerifier.verify(lir) : "LIR not in SSI form.";
        TraceBuilderResult<B> resultTraces = builtTraces(target, lirGenRes, codeEmittingOrder, linearScanOrder);

        TraceAllocationContext traceContext = new TraceAllocationContext(spillMoveFactory, registerAllocationConfig, resultTraces);

        Debug.dump(lir, "Before TraceRegisterAllocation");
        try (Scope s0 = Debug.scope("AllocateTraces", resultTraces)) {
            for (Trace<B> trace : resultTraces.getTraces()) {
                try (Indent i = Debug.logAndIndent("Allocating Trace%d: %s", trace.getId(), trace); Scope s = Debug.scope("AllocateTrace", trace)) {
                    tracesMetric.increment();
                    if (trivialTracesMetric.isEnabled() && isTrivialTrace(lir, trace)) {
                        trivialTracesMetric.increment();
                    }
                    Debug.dump(TRACE_DUMP_LEVEL, trace, "Trace" + trace.getId() + ": " + trace);
                    if (Options.TraceRAtrivialBlockAllocator.getValue() && isTrivialTrace(lir, trace)) {
                        TRACE_TRIVIAL_ALLOCATOR.apply(target, lirGenRes, codeEmittingOrder, trace, traceContext, false);
                    } else {
                        TraceLinearScan allocator = new TraceLinearScan(target, lirGenRes, spillMoveFactory, registerAllocationConfig, trace, resultTraces, false);
                        allocator.allocate(target, lirGenRes, codeEmittingOrder, linearScanOrder, spillMoveFactory, registerAllocationConfig);
                    }
                    Debug.dump(TRACE_DUMP_LEVEL, trace, "After Trace" + trace.getId() + ": " + trace);
                }
                unnumberInstructions(trace.getBlocks(), lir);
            }
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
        Debug.dump(lir, "After trace allocation");

        TRACE_GLOBAL_MOVE_RESOLUTION_PHASE.apply(target, lirGenRes, codeEmittingOrder, linearScanOrder, traceContext);
        deconstructSSIForm(lir);
    }

    @SuppressWarnings("try")
    private static <B extends AbstractBlockBase<B>> TraceBuilderResult<B> builtTraces(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder) {
        try (Scope s = Debug.scope("TraceBuilding")) {
            TraceBuilderContext traceBuilderContext = new TraceBuilderPhase.TraceBuilderContext();
            TRACE_BUILDER_PHASE.apply(target, lirGenRes, codeEmittingOrder, linearScanOrder, traceBuilderContext, false);

            @SuppressWarnings("unchecked")
            TraceBuilderResult<B> resultTraces = (TraceBuilderResult<B>) traceBuilderContext.traceBuilderResult;

            assert TraceBuilderResult.verify(resultTraces, lirGenRes.getLIR().getControlFlowGraph().getBlocks().size());
            if (Debug.isLogEnabled(TRACE_LOG_LEVEL)) {
                List<Trace<B>> traces = resultTraces.getTraces();
                for (int i = 0; i < traces.size(); i++) {
                    Trace<B> trace = traces.get(i);
                    Debug.log(TRACE_LOG_LEVEL, "Trace %5d: %s%s", i, trace, isTrivialTrace(lirGenRes.getLIR(), trace) ? " (trivial)" : "");
                }
            }
            TraceStatisticsPrinter.printTraceStatistics(resultTraces, lirGenRes.getCompilationUnitName());
            Debug.dump(TRACE_DUMP_LEVEL, resultTraces, "After TraceBuilding");
            return resultTraces;
        }
    }

    /**
     * Remove Phi/Sigma In/Out.
     *
     * Note: Incoming Values are needed for the RegisterVerifier, otherwise SIGMAs/PHIs where the
     * Out and In value matches (ie. there is no resolution move) are falsely detected as errors.
     */
    @SuppressWarnings("try")
    private static void deconstructSSIForm(LIR lir) {
        for (AbstractBlockBase<?> block : lir.getControlFlowGraph().getBlocks()) {
            try (Indent i = Debug.logAndIndent("Fixup Block %s", block)) {
                if (block.getPredecessorCount() != 0) {
                    SSIUtil.removeIncoming(lir, block);
                } else {
                    assert lir.getControlFlowGraph().getStartBlock().equals(block);
                }
                SSIUtil.removeOutgoing(lir, block);
            }
        }
    }

    static boolean isTrivialTrace(LIR lir, Trace<? extends AbstractBlockBase<?>> trace) {
        if (trace.size() != 1) {
            return false;
        }
        List<LIRInstruction> instructions = lir.getLIRforBlock(trace.getBlocks().iterator().next());
        if (instructions.size() != 2) {
            return false;
        }
        assert instructions.get(0) instanceof LabelOp : "First instruction not a LabelOp: " + instructions.get(0);
        /*
         * Now we need to check if the BlockEndOp has no special operand requirements (i.e.
         * stack-slot, register). For now we just check for JumpOp because we know that it doesn't.
         */
        return instructions.get(1) instanceof JumpOp;
    }

    private static void unnumberInstructions(Collection<? extends AbstractBlockBase<?>> trace, LIR lir) {
        for (AbstractBlockBase<?> block : trace) {
            for (LIRInstruction op : lir.getLIRforBlock(block)) {
                op.setId(-1);
            }
        }
    }
}
