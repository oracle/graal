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
package com.oracle.graal.lir.alloc.trace.lsra;

import static com.oracle.graal.lir.LIRValueUtil.isVariable;
import static com.oracle.graal.lir.alloc.trace.lsra.IntervalBuilderUtil.visitCallerSavedRegisters;
import static jdk.vm.ci.code.ValueUtil.isRegister;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import com.oracle.graal.compiler.common.alloc.RegisterAllocationConfig;
import com.oracle.graal.compiler.common.alloc.Trace;
import com.oracle.graal.compiler.common.alloc.TraceBuilderResult;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRInstruction.OperandMode;
import com.oracle.graal.lir.StandardOp.AbstractBlockEndOp;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.lir.alloc.trace.TraceRegisterAllocationPhase.Options;
import com.oracle.graal.lir.alloc.trace.TraceUtil;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.gen.LIRGeneratorTool.MoveFactory;
import com.oracle.graal.lir.ssi.SSIBuilder;
import com.oracle.graal.lir.ssi.SSIUtil;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Value;

/**
 * Creates {@linkplain SSIUtil SSI form} and {@linkplain IntervalData lifetime intervals} for
 * {@link TraceLinearScan}.
 */
final class SSIAndIntervalBuilder extends SSIBuilder {

    private static final int INTERVAL_DUMP_LEVEL = 3;
    private final TraceIntervalMap traceMap;
    private final TraceBuilderResult<?> traceBuilderResult;
    private final TargetDescription target;
    private final LIRGenerationResult res;
    private final RegisterAllocationConfig regAllocConfig;
    private final boolean neverSpillConstants;
    private final MoveFactory moveFactory;

    SSIAndIntervalBuilder(LIR lir, TraceBuilderResult<?> traceBuilderResult, TargetDescription target, LIRGenerationResult res, RegisterAllocationConfig regAllocConfig,
                    boolean neverSpillConstants, MoveFactory moveFactory) {
        super(lir);
        this.traceMap = new TraceIntervalMap(traceBuilderResult);
        this.traceBuilderResult = traceBuilderResult;
        this.target = target;
        this.res = res;
        this.regAllocConfig = regAllocConfig;
        this.neverSpillConstants = neverSpillConstants;
        this.moveFactory = moveFactory;
    }

    public TraceIntervalMap buildSSIAndIntervals() {
        numberTraces();
        super.build();
        finalizeFixedIntervals();
        if (Debug.isDumpEnabled(INTERVAL_DUMP_LEVEL)) {
            dumpIntervals();
        }
        return traceMap;
    }

    private void finalizeFixedIntervals() {
        for (Trace<?> trace : traceBuilderResult.getTraces()) {
            if (process(trace)) {
                IntervalData intervalData = getIntervalData(trace);
                IntervalBuilderUtil.finalizeFixedIntervals(intervalData);
            }
        }
    }

    private void dumpIntervals() {
        for (Trace<?> trace : traceBuilderResult.getTraces()) {
            if (process(trace)) {
                IntervalData intervalData = getIntervalData(trace);
                Debug.dump(INTERVAL_DUMP_LEVEL, intervalData.getBlocks(), "SSI building Trace%d %s", trace.getId(), trace);
                FixedInterval[] fixedIntervals = intervalData.fixedIntervals();
                TraceInterval[] intervals = intervalData.intervals();
                Debug.dump(INTERVAL_DUMP_LEVEL, new TraceIntervalDumper(Arrays.copyOf(fixedIntervals, fixedIntervals.length), Arrays.copyOf(intervals, intervals.length)), "SSI building Trace%d %s",
                                trace.getId(), trace);
            }
        }
    }

    private void numberTraces() {
        for (Trace<?> trace : traceBuilderResult.getTraces()) {
            if (process(trace)) {
                IntervalData intervalData = new IntervalData(target, res, regAllocConfig, trace);
                traceMap.put(trace, intervalData);
                numberInstructions(intervalData);
            }
        }

    }

    /**
     * Count instructions in all blocks. The numbering follows the
     * {@linkplain TraceLinearScan#sortedBlocks() register allocation order}.
     */
    private void numberInstructions(IntervalData intervalData) {
        List<? extends AbstractBlockBase<?>> blocks = intervalData.getBlocks();

        intervalData.initIntervals();

        int numberInstructions = 0;
        for (AbstractBlockBase<?> block : blocks) {
            numberInstructions += getLIR().getLIRforBlock(block).size();
        }
        // initialize with correct length
        intervalData.initOpIdMaps(numberInstructions);

        int opIndex = 0;
        for (AbstractBlockBase<?> block : blocks) {
            for (LIRInstruction op : getLIR().getLIRforBlock(block)) {
                IntervalBuilderUtil.numberInstruction(intervalData, block, op, opIndex);
                opIndex++;
            }
        }
        assert numberInstructions == opIndex;
    }

    private IntervalData getIntervalData(Trace<?> trace) {
        IntervalData intervalData = traceMap.get(trace);
        assert intervalData != null : "not initialized: " + trace;
        return intervalData;
    }

    private boolean process(Trace<?> trace) {
        return !Options.TraceRAtrivialBlockAllocator.getValue() || !TraceUtil.isTrivialTrace(getLIR(), trace);

    }

    @Override
    protected void visitUse(AbstractBlockBase<?> block, LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
        Trace<?> trace = traceBuilderResult.traceForBlock(block);
        if (process(trace)) {
            IntervalData intervalData = getIntervalData(trace);
            IntervalBuilderUtil.visitInput(intervalData, op, operand, mode, flags);
        }
    }

    @Override
    protected void visitAlive(AbstractBlockBase<?> block, LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
        Trace<?> trace = traceBuilderResult.traceForBlock(block);
        if (process(trace)) {
            IntervalData intervalData = getIntervalData(trace);
            IntervalBuilderUtil.visitAlive(intervalData, op, operand, mode, flags);
        }
    }

    @Override
    protected void visitDef(AbstractBlockBase<?> block, LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
        Trace<?> trace = traceBuilderResult.traceForBlock(block);
        if (process(trace)) {
            IntervalData intervalData = getIntervalData(trace);
            IntervalBuilderUtil.visitOutput(intervalData, op, operand, mode, flags, neverSpillConstants, moveFactory);
        }
    }

    @Override
    protected void visitTemp(AbstractBlockBase<?> block, LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
        Trace<?> trace = traceBuilderResult.traceForBlock(block);
        if (process(trace)) {
            IntervalData intervalData = getIntervalData(trace);
            IntervalBuilderUtil.visitTemp(intervalData, op, operand, mode, flags);
        }
    }

    @Override
    protected void visitState(AbstractBlockBase<?> block, LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
        Trace<?> trace = traceBuilderResult.traceForBlock(block);
        if (process(trace)) {
            IntervalData intervalData = getIntervalData(trace);
            IntervalBuilderUtil.visitState(intervalData, op, operand);
        }
    }

    @Override
    protected void visitInstruction(AbstractBlockBase<?> block, LIRInstruction op) {
        /* Add a temp range for each register if operation destroys caller-save registers. */
        if (op.destroysCallerSavedRegisters()) {
            Trace<?> trace = traceBuilderResult.traceForBlock(block);
            if (process(trace)) {
                visitCallerSavedRegisters(getIntervalData(trace), regAllocConfig.getRegisterConfig().getCallerSaveRegisters(), op.id());
            }
        }

    }

    @Override
    protected void visitIncoming(AbstractBlockBase<?> block, LIRInstruction op, Value[] incoming) {
        Trace<?> trace = traceBuilderResult.traceForBlock(block);
        if (process(trace)) {
            for (Value var : incoming) {
                if (isRegister(var)) {
                    visitDef(block, op, var, OperandMode.DEF, LabelOp.incomingFlags);
                } else if (isVariable(var)) {
                    IntervalData intervalData = getIntervalData(trace);
                    TraceInterval interval = intervalData.intervalFor(var);
                    if (interval != null) {
                        int blockFrom = op.id();
                        if (interval.from() > blockFrom) {
                            interval.setFrom(blockFrom);
                        }
                    } else {
                        visitDef(block, op, var, OperandMode.DEF, LabelOp.incomingFlags);
                    }
                }
            }
        }
    }

    @Override
    protected void visitOutgoing(AbstractBlockBase<?> block, LIRInstruction op, Value[] outgoing) {
        Trace<?> trace = traceBuilderResult.traceForBlock(block);
        if (process(trace)) {
            int blockTo = op.id() + 1;
            for (Value var : outgoing) {
                if (isVariable(var)) {
                    IntervalData intervalData = getIntervalData(trace);
                    TraceInterval interval = intervalData.intervalFor(var);
                    if (interval != null) {
                        if (interval.to() < blockTo) {
                            interval.setTo(blockTo);
                        }
                    } else {
                        visitAlive(block, op, var, OperandMode.ALIVE, AbstractBlockEndOp.outgoingFlags);
                    }
                }
            }
        }
    }
}
