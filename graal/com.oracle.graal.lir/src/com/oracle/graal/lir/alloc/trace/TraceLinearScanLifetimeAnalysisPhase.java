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

import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.lir.LIRValueUtil.*;
import static com.oracle.graal.lir.alloc.trace.TraceRegisterAllocationPhase.Options.*;
import static jdk.internal.jvmci.code.ValueUtil.*;

import java.util.*;

import jdk.internal.jvmci.code.*;
import jdk.internal.jvmci.common.*;
import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.alloc.TraceBuilder.TraceBuilderResult;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.BlockEndOp;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.lir.StandardOp.MoveOp;
import com.oracle.graal.lir.alloc.lsra.*;
import com.oracle.graal.lir.alloc.lsra.Interval.RegisterPriority;
import com.oracle.graal.lir.alloc.lsra.Interval.SpillState;
import com.oracle.graal.lir.alloc.lsra.LinearScan.BlockData;
import com.oracle.graal.lir.ssi.*;

public class TraceLinearScanLifetimeAnalysisPhase extends LinearScanLifetimeAnalysisPhase {

    private final TraceBuilderResult<?> traceBuilderResult;

    public TraceLinearScanLifetimeAnalysisPhase(LinearScan linearScan, TraceBuilderResult<?> traceBuilderResult) {
        super(linearScan);
        this.traceBuilderResult = traceBuilderResult;
    }

    private boolean sameTrace(AbstractBlockBase<?> a, AbstractBlockBase<?> b) {
        return traceBuilderResult.getTraceForBlock(b) == traceBuilderResult.getTraceForBlock(a);
    }

    private boolean isAllocatedOrCurrent(AbstractBlockBase<?> currentBlock, AbstractBlockBase<?> other) {
        return traceBuilderResult.getTraceForBlock(other) <= traceBuilderResult.getTraceForBlock(currentBlock);
    }

    static void setHint(final LIRInstruction op, Interval target, Interval source) {
        Interval currentHint = target.locationHint(false);
        if (currentHint == null || currentHint.from() > target.from()) {
            /*
             * Update hint if there was none or if the hint interval starts after the hinted
             * interval.
             */
            target.setLocationHint(source);
            if (Debug.isLogEnabled()) {
                Debug.log("operation at opId %d: added hint from interval %d (%s) to %d (%s)", op.id(), source.operandNumber, source, target.operandNumber, target);
            }
        }
    }

    @Override
    protected void changeSpillDefinitionPos(LIRInstruction op, AllocatableValue operand, Interval interval, int defPos) {
        assert interval.isSplitParent() : "can only be called for split parents";

        switch (interval.spillState()) {
            case NoDefinitionFound:
                // assert interval.spillDefinitionPos() == -1 : "must no be set before";
                interval.setSpillDefinitionPos(defPos);
                if (!(op instanceof LabelOp)) {
                    // Do not update state for labels. This will be done afterwards.
                    interval.setSpillState(SpillState.NoSpillStore);
                }
                break;

            case NoSpillStore:
                assert defPos <= interval.spillDefinitionPos() : "positions are processed in reverse order when intervals are created";
                if (defPos < interval.spillDefinitionPos() - 2) {
                    // second definition found, so no spill optimization possible for this interval
                    interval.setSpillState(SpillState.NoOptimization);
                } else {
                    // two consecutive definitions (because of two-operand LIR form)
                    assert allocator.blockForId(defPos) == allocator.blockForId(interval.spillDefinitionPos()) : "block must be equal";
                }
                break;

            case NoOptimization:
                // nothing to do
                break;

            default:
                throw new BailoutException("other states not allowed at this time");
        }
    }

    @Override
    protected void buildIntervals() {
        super.buildIntervals();
        // fix spill state for phi/sigma intervals
        for (Interval interval : allocator.intervals()) {
            if (interval != null && interval.spillState().equals(SpillState.NoDefinitionFound) && interval.spillDefinitionPos() != -1) {
                // there was a definition in a phi/sigma
                interval.setSpillState(SpillState.NoSpillStore);
            }
        }
        if (TraceRAuseInterTraceHints.getValue()) {
            addInterTraceHints();
        }
    }

    private void addInterTraceHints() {
        // set hints for phi/sigma intervals
        LIR lir = allocator.getLIR();
        for (AbstractBlockBase<?> block : allocator.sortedBlocks()) {
            LabelOp label = SSIUtils.incoming(lir, block);
            for (AbstractBlockBase<?> pred : block.getPredecessors()) {
                if (isAllocatedOrCurrent(block, pred)) {
                    BlockEndOp outgoing = SSIUtils.outgoing(lir, pred);
                    for (int i = 0; i < outgoing.getOutgoingSize(); i++) {
                        Value toValue = label.getIncomingValue(i);
                        if (!isIllegal(toValue)) {
                            Value fromValue = outgoing.getOutgoingValue(i);
                            assert sameTrace(block, pred) || !isVariable(fromValue) : "Unallocated variable: " + fromValue;

                            if (isStackSlotValue(fromValue)) {

                            } else if (isConstant(fromValue)) {
                            } else {
                                Interval from = allocator.getOrCreateInterval((AllocatableValue) fromValue);
                                Interval to = allocator.getOrCreateInterval((AllocatableValue) toValue);
                                setHint(label, to, from);
                            }
                        }
                    }
                }
            }
        }
        /*
         * Set the first range for fixed intervals to [-1, 0] to avoid intersection with incoming
         * values.
         */
        for (Interval interval : allocator.intervals()) {
            if (interval != null && isRegister(interval.operand)) {
                Range range = interval.first();
                if (range == Range.EndMarker) {
                    interval.addRange(-1, 0);
                } else if (range.from == 0 && range.to == 1) {
                    range.from = -1;
                    range.to = 0;
                }
            }
        }
    }

    @Override
    protected RegisterPriority registerPriorityOfOutputOperand(LIRInstruction op) {
        if (op instanceof LabelOp) {
            // skip method header
            return RegisterPriority.None;
        }
        return super.registerPriorityOfOutputOperand(op);
    }

    @Override
    protected void handleMethodArguments(LIRInstruction op) {
        if (op instanceof MoveOp) {
            // do not optimize method arguments
            return;
        }
        super.handleMethodArguments(op);
    }

    /**
     * Performs a backward dataflow analysis to compute global live sets (i.e.
     * {@link BlockData#liveIn} and {@link BlockData#liveOut}) for each block.
     */
    @Override
    protected void computeGlobalLiveSets() {
        try (Indent indent = Debug.logAndIndent("compute global live sets")) {
            int numBlocks = allocator.blockCount();
            boolean changeOccurred;
            boolean changeOccurredInBlock;
            int iterationCount = 0;
            BitSet liveOut = new BitSet(allocator.liveSetSize()); // scratch set for calculations

            /*
             * Perform a backward dataflow analysis to compute liveOut and liveIn for each block.
             * The loop is executed until a fixpoint is reached (no changes in an iteration).
             */
            do {
                changeOccurred = false;

                try (Indent indent2 = Debug.logAndIndent("new iteration %d", iterationCount)) {

                    // iterate all blocks in reverse order
                    for (int i = numBlocks - 1; i >= 0; i--) {
                        AbstractBlockBase<?> block = allocator.blockAt(i);
                        BlockData blockSets = allocator.getBlockData(block);

                        changeOccurredInBlock = false;

                        /* liveOut(block) is the union of liveIn(sux), for successors sux of block. */
                        int n = block.getSuccessorCount();
                        if (n > 0) {
                            liveOut.clear();
                            // block has successors
                            if (n > 0) {
                                for (AbstractBlockBase<?> successor : block.getSuccessors()) {
                                    if (allocator.sortedBlocks().contains(successor)) {
                                        liveOut.or(allocator.getBlockData(successor).liveIn);
                                    }
                                }
                            }

                            if (!blockSets.liveOut.equals(liveOut)) {
                                /*
                                 * A change occurred. Swap the old and new live out sets to avoid
                                 * copying.
                                 */
                                BitSet temp = blockSets.liveOut;
                                blockSets.liveOut = liveOut;
                                liveOut = temp;

                                changeOccurred = true;
                                changeOccurredInBlock = true;
                            }
                        }

                        if (iterationCount == 0 || changeOccurredInBlock) {
                            /*
                             * liveIn(block) is the union of liveGen(block) with (liveOut(block) &
                             * !liveKill(block)).
                             *
                             * Note: liveIn has to be computed only in first iteration or if liveOut
                             * has changed!
                             */
                            BitSet liveIn = blockSets.liveIn;
                            liveIn.clear();
                            liveIn.or(blockSets.liveOut);
                            liveIn.andNot(blockSets.liveKill);
                            liveIn.or(blockSets.liveGen);

                            if (Debug.isLogEnabled()) {
                                Debug.log("block %d: livein = %s,  liveout = %s", block.getId(), liveIn, blockSets.liveOut);
                            }
                        }
                    }
                    iterationCount++;

                    if (changeOccurred && iterationCount > 50) {
                        throw new BailoutException("too many iterations in computeGlobalLiveSets");
                    }
                }
            } while (changeOccurred);

            if (DetailedAsserts.getValue()) {
                verifyLiveness();
            }

            // check that the liveIn set of the first block is empty
            AbstractBlockBase<?> startBlock = allocator.blockAt(0);
            if (allocator.getBlockData(startBlock).liveIn.cardinality() != 0) {
                if (DetailedAsserts.getValue()) {
                    reportFailure(numBlocks);
                }
                // bailout if this occurs in product mode.
                throw new JVMCIError("liveIn set of first block must be empty: " + allocator.getBlockData(startBlock).liveIn);
            }
        }
    }
}
