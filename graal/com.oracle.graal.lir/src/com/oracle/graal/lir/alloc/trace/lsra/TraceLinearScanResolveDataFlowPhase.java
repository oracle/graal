/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.compiler.common.GraalOptions.DetailedAsserts;
import static com.oracle.graal.lir.LIRValueUtil.asConstant;
import static com.oracle.graal.lir.LIRValueUtil.isConstantValue;
import static com.oracle.graal.lir.LIRValueUtil.isStackSlotValue;
import static com.oracle.graal.lir.LIRValueUtil.isVirtualStackSlot;
import static jdk.vm.ci.code.ValueUtil.isRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import com.oracle.graal.compiler.common.alloc.Trace;
import com.oracle.graal.compiler.common.alloc.TraceBuilderResult;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugCounter;
import com.oracle.graal.debug.Indent;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.StandardOp;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.ssa.SSAUtil.PhiValueVisitor;
import com.oracle.graal.lir.ssi.SSIUtil;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Value;

/**
 * Phase 6: resolve data flow
 *
 * Insert moves at edges between blocks if intervals have been split.
 */
final class TraceLinearScanResolveDataFlowPhase extends TraceLinearScanAllocationPhase {

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, Trace trace, TraceLinearScanAllocationContext context) {
        TraceBuilderResult traceBuilderResult = context.resultTraces;
        TraceLinearScan allocator = context.allocator;
        new Resolver(allocator, traceBuilderResult).resolveDataFlow(allocator.sortedBlocks());
    }

    private static final class Resolver {
        private final TraceLinearScan allocator;
        private final TraceBuilderResult traceBuilderResult;

        private Resolver(TraceLinearScan allocator, TraceBuilderResult traceBuilderResult) {
            this.allocator = allocator;
            this.traceBuilderResult = traceBuilderResult;
        }

        private void resolveFindInsertPos(AbstractBlockBase<?> fromBlock, AbstractBlockBase<?> toBlock, TraceLocalMoveResolver moveResolver) {
            if (fromBlock.getSuccessorCount() <= 1) {
                if (Debug.isLogEnabled()) {
                    Debug.log("inserting moves at end of fromBlock B%d", fromBlock.getId());
                }

                List<LIRInstruction> instructions = allocator.getLIR().getLIRforBlock(fromBlock);
                LIRInstruction instr = instructions.get(instructions.size() - 1);
                if (instr instanceof StandardOp.JumpOp) {
                    // insert moves before branch
                    moveResolver.setInsertPosition(instructions, instructions.size() - 1);
                } else {
                    moveResolver.setInsertPosition(instructions, instructions.size());
                }

            } else {
                if (Debug.isLogEnabled()) {
                    Debug.log("inserting moves at beginning of toBlock B%d", toBlock.getId());
                }

                if (DetailedAsserts.getValue()) {
                    assert allocator.getLIR().getLIRforBlock(fromBlock).get(0) instanceof StandardOp.LabelOp : "block does not start with a label";

                    /*
                     * Because the number of predecessor edges matches the number of successor
                     * edges, blocks which are reached by switch statements may have be more than
                     * one predecessor but it will be guaranteed that all predecessors will be the
                     * same.
                     */
                    for (AbstractBlockBase<?> predecessor : toBlock.getPredecessors()) {
                        assert fromBlock == predecessor : "all critical edges must be broken";
                    }
                }

                moveResolver.setInsertPosition(allocator.getLIR().getLIRforBlock(toBlock), 1);
            }
        }

        /**
         * Inserts necessary moves (spilling or reloading) at edges between blocks for intervals
         * that have been split.
         */
        @SuppressWarnings("try")
        private void resolveDataFlow(ArrayList<? extends AbstractBlockBase<?>> blocks) {
            if (blocks.size() < 2) {
                // no resolution necessary
                return;
            }
            try (Indent indent = Debug.logAndIndent("resolve data flow")) {

                TraceLocalMoveResolver moveResolver = allocator.createMoveResolver();
                ListIterator<? extends AbstractBlockBase<?>> it = blocks.listIterator();
                AbstractBlockBase<?> toBlock = null;
                for (AbstractBlockBase<?> fromBlock = it.next(); it.hasNext(); fromBlock = toBlock) {
                    toBlock = it.next();
                    assert containedInTrace(fromBlock) : "Not in Trace: " + fromBlock;
                    assert containedInTrace(toBlock) : "Not in Trace: " + toBlock;
                    resolveCollectMappings(fromBlock, toBlock, moveResolver);
                }
                assert blocks.get(blocks.size() - 1).equals(toBlock);
                if (toBlock.isLoopEnd()) {
                    assert toBlock.getSuccessorCount() == 1;
                    AbstractBlockBase<?> loopHeader = toBlock.getSuccessors()[0];
                    if (containedInTrace(loopHeader)) {
                        resolveCollectMappings(toBlock, loopHeader, moveResolver);
                    }
                }

            }
        }

        @SuppressWarnings("try")
        private void resolveCollectMappings(AbstractBlockBase<?> fromBlock, AbstractBlockBase<?> toBlock, TraceLocalMoveResolver moveResolver) {
            try (Indent indent0 = Debug.logAndIndent("Edge %s -> %s", fromBlock, toBlock)) {
                // collect all intervals that have been split between
                // fromBlock and toBlock
                SSIUtil.forEachValuePair(allocator.getLIR(), toBlock, fromBlock, new MappingCollector(moveResolver, toBlock, fromBlock));
                if (moveResolver.hasMappings()) {
                    resolveFindInsertPos(fromBlock, toBlock, moveResolver);
                    moveResolver.resolveAndAppendMoves();
                }
            }
        }

        private boolean containedInTrace(AbstractBlockBase<?> block) {
            return currentTrace().getId() == traceBuilderResult.getTraceForBlock(block).getId();
        }

        private Trace currentTrace() {
            return traceBuilderResult.getTraceForBlock(allocator.sortedBlocks().get(0));
        }

        private static final DebugCounter numSSIResolutionMoves = Debug.counter("SSI LSRA[numSSIResolutionMoves]");
        private static final DebugCounter numStackToStackMoves = Debug.counter("SSI LSRA[numStackToStackMoves]");

        private class MappingCollector implements PhiValueVisitor {
            final TraceLocalMoveResolver moveResolver;
            final int toId;
            final int fromId;

            MappingCollector(TraceLocalMoveResolver moveResolver, AbstractBlockBase<?> toBlock, AbstractBlockBase<?> fromBlock) {
                this.moveResolver = moveResolver;
                toId = allocator.getFirstLirInstructionId(toBlock);
                fromId = allocator.getLastLirInstructionId(fromBlock);
                assert fromId >= 0;
            }

            @Override
            public void visit(Value phiIn, Value phiOut) {
                assert !isRegister(phiOut) : "Out is a register: " + phiOut;
                assert !isRegister(phiIn) : "In is a register: " + phiIn;
                if (Value.ILLEGAL.equals(phiIn)) {
                    // The value not needed in this branch.
                    return;
                }
                if (isVirtualStackSlot(phiIn) && isVirtualStackSlot(phiOut) && phiIn.equals(phiOut)) {
                    // no need to handle virtual stack slots
                    return;
                }
                TraceInterval toInterval = allocator.splitChildAtOpId(allocator.intervalFor(phiIn), toId, LIRInstruction.OperandMode.DEF);
                if (isConstantValue(phiOut)) {
                    numSSIResolutionMoves.increment();
                    moveResolver.addMapping(asConstant(phiOut), toInterval);
                } else {
                    TraceInterval fromInterval = allocator.splitChildAtOpId(allocator.intervalFor(phiOut), fromId, LIRInstruction.OperandMode.DEF);
                    if (fromInterval != toInterval) {
                        numSSIResolutionMoves.increment();
                        if (!(isStackSlotValue(toInterval.location()) && isStackSlotValue(fromInterval.location()))) {
                            moveResolver.addMapping(fromInterval, toInterval);
                        } else {
                            numStackToStackMoves.increment();
                            moveResolver.addMapping(fromInterval, toInterval);
                        }
                    }
                }
            }
        }
    }

}
