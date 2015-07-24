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

import static jdk.internal.jvmci.code.ValueUtil.*;

import java.util.*;

import jdk.internal.jvmci.code.*;
import jdk.internal.jvmci.meta.*;
import jdk.internal.jvmci.options.*;

import com.oracle.graal.compiler.common.alloc.*;
import com.oracle.graal.compiler.common.alloc.TraceBuilder.TraceBuilderResult;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.MoveOp;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.gen.LIRGeneratorTool.SpillMoveFactory;
import com.oracle.graal.lir.phases.*;
import com.oracle.graal.lir.ssa.SSAUtil.PhiValueVisitor;
import com.oracle.graal.lir.ssi.*;

public class TraceRegisterAllocationPhase extends AllocationPhase {
    public static class Options {
        // @formatter:off
        @Option(help = "Use inter-trace register hints.", type = OptionType.Debug)
        public static final OptionValue<Boolean> TraceRAuseInterTraceHints = new OptionValue<>(true);
        // @formatter:on
    }

    static final int TRACE_DUMP_LEVEL = 3;

    @Override
    protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder, SpillMoveFactory spillMoveFactory,
                    RegisterAllocationConfig registerAllocationConfig) {
        LIR lir = lirGenRes.getLIR();
        assert SSIVerifier.verify(lir) : "LIR not in SSI form.";
        B startBlock = linearScanOrder.get(0);
        assert startBlock.equals(lir.getControlFlowGraph().getStartBlock());
        TraceBuilderResult<B> resultTraces = TraceBuilder.computeTraces(startBlock, linearScanOrder);

        Debug.dump(lir, "Before TraceRegisterAllocation");
        int traceNumber = 0;
        for (List<B> trace : resultTraces.getTraces()) {
            try (Indent i = Debug.logAndIndent("Allocating Trace%d: %s", traceNumber, trace); Scope s = Debug.scope("AllocateTrace", trace)) {
                Debug.dump(TRACE_DUMP_LEVEL, trace, "Trace" + traceNumber + ": " + trace);
                TraceLinearScan allocator = new TraceLinearScan(target, lirGenRes, spillMoveFactory, registerAllocationConfig, trace, resultTraces);
                allocator.allocate(target, lirGenRes, codeEmittingOrder, linearScanOrder, spillMoveFactory, registerAllocationConfig);
                Debug.dump(TRACE_DUMP_LEVEL, trace, "After Trace" + traceNumber + ": " + trace);
                traceNumber++;
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
            unnumberInstructions(trace, lir);
        }
        Debug.dump(lir, "After trace allocation");

        resolveGlobalDataFlow(resultTraces, lirGenRes, spillMoveFactory, target.arch);
        Debug.dump(lir, "After global dataflow resolution");

        if (replaceStackToStackMoves(lir, spillMoveFactory)) {
            Debug.dump(lir, "After fixing stack to stack moves");
        }
        /*
         * Incoming Values are needed for the RegisterVerifier, otherwise SIGMAs/PHIs where the Out
         * and In value matches (ie. there is no resolution move) are falsely detected as errors.
         */
        for (AbstractBlockBase<?> toBlock : lir.getControlFlowGraph().getBlocks()) {
            if (toBlock.getPredecessorCount() != 0) {
                SSIUtil.removeIncoming(lir, toBlock);
            } else {
                assert lir.getControlFlowGraph().getStartBlock().equals(toBlock);
            }
            SSIUtil.removeOutgoing(lir, toBlock);
        }
    }

    /**
     * Fixup stack to stack moves introduced by stack arguments.
     *
     * TODO (je) find a better solution.
     */
    private static boolean replaceStackToStackMoves(LIR lir, SpillMoveFactory spillMoveFactory) {
        boolean changed = false;
        for (AbstractBlockBase<?> block : lir.getControlFlowGraph().getBlocks()) {
            List<LIRInstruction> instructions = lir.getLIRforBlock(block);
            for (int i = 0; i < instructions.size(); i++) {
                LIRInstruction inst = instructions.get(i);

                if (inst instanceof MoveOp) {
                    MoveOp move = (MoveOp) inst;
                    if (isStackSlotValue(move.getInput()) && isStackSlotValue(move.getResult())) {
                        instructions.set(i, spillMoveFactory.createStackMove(move.getResult(), move.getInput()));
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private static <B extends AbstractBlockBase<B>> void resolveGlobalDataFlow(TraceBuilderResult<B> resultTraces, LIRGenerationResult lirGenRes, SpillMoveFactory spillMoveFactory, Architecture arch) {
        LIR lir = lirGenRes.getLIR();
        /* Resolve trace global data-flow mismatch. */
        TraceGlobalMoveResolver moveResolver = new TraceGlobalMoveResolver(lirGenRes, spillMoveFactory, arch);
        PhiValueVisitor visitor = (Value phiIn, Value phiOut) -> {
            if (!isIllegal(phiIn) && !TraceGlobalMoveResolver.isMoveToSelf(phiOut, phiIn)) {
                moveResolver.addMapping(phiOut, (AllocatableValue) phiIn);
            }
        };

        try (Indent indent = Debug.logAndIndent("Trace global move resolution")) {
            for (List<B> trace : resultTraces.getTraces()) {
                for (AbstractBlockBase<?> fromBlock : trace) {
                    for (AbstractBlockBase<?> toBlock : fromBlock.getSuccessors()) {
                        if (resultTraces.getTraceForBlock(fromBlock) != resultTraces.getTraceForBlock(toBlock)) {
                            try (Indent indent0 = Debug.logAndIndent("Handle trace edge from %s (Trace%d) to %s (Trace%d)", fromBlock, resultTraces.getTraceForBlock(fromBlock), toBlock,
                                            resultTraces.getTraceForBlock(toBlock))) {

                                final List<LIRInstruction> instructions;
                                final int insertIdx;
                                if (fromBlock.getSuccessorCount() == 1) {
                                    instructions = lir.getLIRforBlock(fromBlock);
                                    insertIdx = instructions.size() - 1;
                                } else {
                                    assert toBlock.getPredecessorCount() == 1;
                                    instructions = lir.getLIRforBlock(toBlock);
                                    insertIdx = 1;
                                }

                                moveResolver.setInsertPosition(instructions, insertIdx);
                                SSIUtil.forEachValuePair(lir, toBlock, fromBlock, visitor);
                                moveResolver.resolveAndAppendMoves();
                            }
                        }
                    }
                }
            }
        }
    }

    private static void unnumberInstructions(List<? extends AbstractBlockBase<?>> trace, LIR lir) {
        trace.stream().flatMap(b -> lir.getLIRforBlock(b).stream()).forEach(op -> op.setId(-1));
    }
}
