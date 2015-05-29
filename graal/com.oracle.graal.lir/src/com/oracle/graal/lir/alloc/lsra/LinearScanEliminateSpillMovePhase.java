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
package com.oracle.graal.lir.alloc.lsra;

import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.lir.LIRValueUtil.*;
import static com.oracle.jvmci.code.ValueUtil.*;

import java.util.*;

import com.oracle.graal.compiler.common.alloc.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.MoveOp;
import com.oracle.graal.lir.alloc.lsra.Interval.SpillState;
import com.oracle.graal.lir.alloc.lsra.LinearScan.IntervalPredicate;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.gen.LIRGeneratorTool.SpillMoveFactory;
import com.oracle.graal.lir.phases.*;
import com.oracle.jvmci.code.*;
import com.oracle.jvmci.debug.*;
import com.oracle.jvmci.meta.*;

class LinearScanEliminateSpillMovePhase extends AllocationPhase {

    private static final IntervalPredicate mustStoreAtDefinition = new LinearScan.IntervalPredicate() {

        @Override
        public boolean apply(Interval i) {
            return i.isSplitParent() && i.spillState() == SpillState.StoreAtDefinition;
        }
    };

    protected final LinearScan allocator;

    LinearScanEliminateSpillMovePhase(LinearScan allocator) {
        this.allocator = allocator;
    }

    @Override
    protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder, SpillMoveFactory spillMoveFactory,
                    RegisterAllocationConfig registerAllocationConfig) {
        eliminateSpillMoves();
    }

    /**
     * @return the index of the first instruction that is of interest for
     *         {@link #eliminateSpillMoves()}
     */
    protected int firstInstructionOfInterest() {
        // skip the first because it is always a label
        return 1;
    }

    // called once before assignment of register numbers
    void eliminateSpillMoves() {
        try (Indent indent = Debug.logAndIndent("Eliminating unnecessary spill moves")) {

            /*
             * collect all intervals that must be stored after their definition. The list is sorted
             * by Interval.spillDefinitionPos.
             */
            Interval interval;
            interval = allocator.createUnhandledLists(mustStoreAtDefinition, null).first;
            if (DetailedAsserts.getValue()) {
                checkIntervals(interval);
            }

            LIRInsertionBuffer insertionBuffer = new LIRInsertionBuffer();
            for (AbstractBlockBase<?> block : allocator.sortedBlocks) {
                try (Indent indent1 = Debug.logAndIndent("Handle %s", block)) {
                    List<LIRInstruction> instructions = allocator.ir.getLIRforBlock(block);
                    int numInst = instructions.size();

                    // iterate all instructions of the block.
                    for (int j = firstInstructionOfInterest(); j < numInst; j++) {
                        LIRInstruction op = instructions.get(j);
                        int opId = op.id();

                        if (opId == -1) {
                            MoveOp move = (MoveOp) op;
                            /*
                             * Remove move from register to stack if the stack slot is guaranteed to
                             * be correct. Only moves that have been inserted by LinearScan can be
                             * removed.
                             */
                            if (canEliminateSpillMove(block, move)) {
                                /*
                                 * Move target is a stack slot that is always correct, so eliminate
                                 * instruction.
                                 */
                                if (Debug.isLogEnabled()) {
                                    Debug.log("eliminating move from interval %d (%s) to %d (%s) in block %s", allocator.operandNumber(move.getInput()), move.getInput(),
                                                    allocator.operandNumber(move.getResult()), move.getResult(), block);
                                }

                                // null-instructions are deleted by assignRegNum
                                instructions.set(j, null);
                            }

                        } else {
                            /*
                             * Insert move from register to stack just after the beginning of the
                             * interval.
                             */
                            assert interval == Interval.EndMarker || interval.spillDefinitionPos() >= opId : "invalid order";
                            assert interval == Interval.EndMarker || (interval.isSplitParent() && interval.spillState() == SpillState.StoreAtDefinition) : "invalid interval";

                            while (interval != Interval.EndMarker && interval.spillDefinitionPos() == opId) {
                                if (!interval.canMaterialize()) {
                                    if (!insertionBuffer.initialized()) {
                                        /*
                                         * prepare insertion buffer (appended when all instructions
                                         * in the block are processed)
                                         */
                                        insertionBuffer.init(instructions);
                                    }

                                    AllocatableValue fromLocation = interval.location();
                                    AllocatableValue toLocation = LinearScan.canonicalSpillOpr(interval);
                                    if (!fromLocation.equals(toLocation)) {

                                        assert isRegister(fromLocation) : "from operand must be a register but is: " + fromLocation + " toLocation=" + toLocation + " spillState=" +
                                                        interval.spillState();
                                        assert isStackSlotValue(toLocation) : "to operand must be a stack slot";

                                        LIRInstruction move = allocator.getSpillMoveFactory().createMove(toLocation, fromLocation);
                                        insertionBuffer.append(j + 1, move);

                                        if (Debug.isLogEnabled()) {
                                            Debug.log("inserting move after definition of interval %d to stack slot %s at opId %d", interval.operandNumber, interval.spillSlot(), opId);
                                        }
                                    }
                                }
                                interval = interval.next;
                            }
                        }
                    } // end of instruction iteration

                    if (insertionBuffer.initialized()) {
                        insertionBuffer.finish();
                    }
                }
            } // end of block iteration

            assert interval == Interval.EndMarker : "missed an interval";
        }
    }

    /**
     * @param block The block {@code move} is located in.
     * @param move Spill move.
     */
    protected boolean canEliminateSpillMove(AbstractBlockBase<?> block, MoveOp move) {
        assert isVariable(move.getResult()) : "LinearScan inserts only moves to variables: " + move;

        Interval curInterval = allocator.intervalFor(move.getResult());

        if (!isRegister(curInterval.location()) && curInterval.alwaysInMemory()) {
            assert isStackSlotValue(curInterval.location()) : "Not a stack slot: " + curInterval.location();
            return true;
        }
        return false;
    }

    private static void checkIntervals(Interval interval) {
        Interval prev = null;
        Interval temp = interval;
        while (temp != Interval.EndMarker) {
            assert temp.spillDefinitionPos() > 0 : "invalid spill definition pos";
            if (prev != null) {
                assert temp.from() >= prev.from() : "intervals not sorted";
                assert temp.spillDefinitionPos() >= prev.spillDefinitionPos() : "when intervals are sorted by from :  then they must also be sorted by spillDefinitionPos";
            }

            assert temp.spillSlot() != null || temp.canMaterialize() : "interval has no spill slot assigned";
            assert temp.spillDefinitionPos() >= temp.from() : "invalid order";
            assert temp.spillDefinitionPos() <= temp.from() + 2 : "only intervals defined once at their start-pos can be optimized";

            if (Debug.isLogEnabled()) {
                Debug.log("interval %d (from %d to %d) must be stored at %d", temp.operandNumber, temp.from(), temp.to(), temp.spillDefinitionPos());
            }

            prev = temp;
            temp = temp.next;
        }
    }

}
