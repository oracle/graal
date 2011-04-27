/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.alloc;

import static com.sun.cri.ci.CiUtil.*;
import static java.lang.reflect.Modifier.*;

import java.util.*;

import com.sun.c1x.*;
import com.sun.c1x.alloc.Interval.*;
import com.sun.c1x.debug.*;
import com.sun.c1x.gen.*;
import com.sun.c1x.graph.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.ir.BlockBegin.BlockFlag;
import com.sun.c1x.lir.*;
import com.sun.c1x.lir.LIRInstruction.OperandMode;
import com.sun.c1x.observer.*;
import com.sun.c1x.util.*;
import com.sun.c1x.value.*;
import com.sun.c1x.value.FrameState.PhiProcedure;
import com.sun.c1x.value.FrameState.ValueProcedure;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * An implementation of the linear scan register allocator algorithm described
 * in <a href="http://doi.acm.org/10.1145/1064979.1064998">"Optimized Interval Splitting in a Linear Scan Register Allocator"</a>
 * by Christian Wimmer and Hanspeter Moessenboeck.
 *
 * @author Christian Wimmer (original HotSpot implementation)
 * @author Thomas Wuerthinger
 * @author Doug Simon
 */
public final class LinearScan {

    final C1XCompilation compilation;
    final IR ir;
    final LIRGenerator gen;
    final FrameMap frameMap;
    final RiRegisterAttributes[] registerAttributes;
    final CiRegister[] registers;

    private static final int INITIAL_SPLIT_INTERVALS_CAPACITY = 32;

    /**
     * List of blocks in linear-scan order. This is only correct as long as the CFG does not change.
     */
    final BlockBegin[] sortedBlocks;

    final OperandPool operands;

    /**
     * Number of stack slots used for intervals allocated to memory.
     */
    int maxSpills;

    /**
     * Unused spill slot for a single-word value because of alignment of a double-word value.
     */
    CiStackSlot unusedSpillSlot;

    /**
     * Map from {@linkplain #operandNumber(CiValue) operand numbers} to intervals.
     */
    Interval[] intervals;

    /**
     * The number of valid entries in {@link #intervals}.
     */
    int intervalsSize;

    /**
     * The index of the first entry in {@link #intervals} for a {@linkplain #createDerivedInterval(Interval) derived interval}.
     */
    int firstDerivedIntervalIndex = -1;

    /**
     * Intervals sorted by {@link Interval#from()}.
     */
    Interval[] sortedIntervals;

    /**
     * Map from an instruction {@linkplain LIRInstruction#id id} to the instruction.
     * Entries should be retrieved with {@link #instructionForId(int)} as the id is
     * not simply an index into this array.
     */
    LIRInstruction[] opIdToInstructionMap;

    /**
     * Map from an instruction {@linkplain LIRInstruction#id id} to the {@linkplain
     * BlockBegin block} containing the instruction. Entries should be retrieved with
     * {@link #blockForId(int)} as the id is not simply an index into this array.
     */
    BlockBegin[] opIdToBlockMap;

    /**
     * Bit set for each variable that is contained in each loop.
     */
    BitMap2D intervalInLoop;

    public LinearScan(C1XCompilation compilation, IR ir, LIRGenerator gen, FrameMap frameMap) {
        this.compilation = compilation;
        this.ir = ir;
        this.gen = gen;
        this.frameMap = frameMap;
        this.maxSpills = frameMap.initialSpillSlot();
        this.unusedSpillSlot = null;
        this.sortedBlocks = ir.linearScanOrder().toArray(new BlockBegin[ir.linearScanOrder().size()]);
        CiRegister[] allocatableRegisters = compilation.registerConfig.getAllocatableRegisters();
        this.registers = new CiRegister[CiRegister.maxRegisterNumber(allocatableRegisters) + 1];
        for (CiRegister reg : allocatableRegisters) {
            registers[reg.number] = reg;
        }
        this.registerAttributes = compilation.registerConfig.getAttributesMap();
        this.operands = gen.operands;
    }

    /**
     * Converts an operand (variable or register) to an index in a flat address space covering all the
     * {@linkplain CiVariable variables} and {@linkplain CiRegisterValue registers} being processed by this
     * allocator.
     */
    int operandNumber(CiValue operand) {
        return operands.operandNumber(operand);
    }

    static final IntervalPredicate IS_PRECOLORED_INTERVAL = new IntervalPredicate() {
        @Override
        public boolean apply(Interval i) {
            return i.operand.isRegister();
        }
    };

    static final IntervalPredicate IS_VARIABLE_INTERVAL = new IntervalPredicate() {
        @Override
        public boolean apply(Interval i) {
            return i.operand.isVariable();
        }
    };

    static final IntervalPredicate IS_OOP_INTERVAL = new IntervalPredicate() {
        @Override
        public boolean apply(Interval i) {
            return !i.operand.isRegister() && i.kind() == CiKind.Object;
        }
    };

    /**
     * Gets an object describing the attributes of a given register according to this register configuration.
     */
    RiRegisterAttributes attributes(CiRegister reg) {
        return registerAttributes[reg.number];
    }

    /**
     * Allocates the next available spill slot for a value of a given kind.
     */
    CiStackSlot allocateSpillSlot(CiKind kind) {
        CiStackSlot spillSlot;
        if (numberOfSpillSlots(kind) == 2) {
            if (isOdd(maxSpills)) {
                // alignment of double-slot values
                // the hole because of the alignment is filled with the next single-slot value
                assert unusedSpillSlot == null : "wasting a spill slot";
                unusedSpillSlot = CiStackSlot.get(kind, maxSpills);
                maxSpills++;
            }
            spillSlot = CiStackSlot.get(kind, maxSpills);
            maxSpills += 2;
        } else if (unusedSpillSlot != null) {
            // re-use hole that was the result of a previous double-word alignment
            spillSlot = unusedSpillSlot;
            unusedSpillSlot = null;
        } else {
            spillSlot = CiStackSlot.get(kind, maxSpills);
            maxSpills++;
        }

        return spillSlot;
    }

    void assignSpillSlot(Interval interval) {
        // assign the canonical spill slot of the parent (if a part of the interval
        // is already spilled) or allocate a new spill slot
        if (interval.spillSlot() != null) {
            interval.assignLocation(interval.spillSlot());
        } else {
            CiStackSlot slot = allocateSpillSlot(interval.kind());
            interval.setSpillSlot(slot);
            interval.assignLocation(slot);
        }
    }

    /**
     * Creates a new interval.
     *
     * @param operand the operand for the interval
     * @return the created interval
     */
    Interval createInterval(CiValue operand) {
        assert isProcessed(operand);
        assert operand.isLegal();
        int operandNumber = operandNumber(operand);
        Interval interval = new Interval(operand, operandNumber);
        assert operandNumber < intervalsSize;
        assert intervals[operandNumber] == null;
        intervals[operandNumber] = interval;
        return interval;
    }

    /**
     * Creates an interval as a result of splitting or spilling another interval.
     *
     * @param source an interval being split of spilled
     * @return a new interval derived from {@code source}
     */
    Interval createDerivedInterval(Interval source) {
        if (firstDerivedIntervalIndex == -1) {
            firstDerivedIntervalIndex = intervalsSize;
        }
        if (intervalsSize == intervals.length) {
            intervals = Arrays.copyOf(intervals, intervals.length * 2);
        }
        intervalsSize++;
        Interval interval = createInterval(operands.newVariable(source.kind()));
        assert intervals[intervalsSize - 1] == interval;
        return interval;
    }

    // copy the variable flags if an interval is split
    void copyRegisterFlags(Interval from, Interval to) {
        if (operands.mustBeByteRegister(from.operand)) {
            operands.setMustBeByteRegister((CiVariable) to.operand);
        }

        // Note: do not copy the mustStartInMemory flag because it is not necessary for child
        // intervals (only the very beginning of the interval must be in memory)
    }

    // access to block list (sorted in linear scan order)
    int blockCount() {
        assert sortedBlocks.length == ir.linearScanOrder().size() : "invalid cached block list";
        return sortedBlocks.length;
    }

    BlockBegin blockAt(int index) {
        assert sortedBlocks[index] == ir.linearScanOrder().get(index) : "invalid cached block list";
        return sortedBlocks[index];
    }

    /**
     * Gets the size of the {@link LIRBlock#liveIn} and {@link LIRBlock#liveOut} sets for a basic block. These sets do
     * not include any operands allocated as a result of creating {@linkplain #createDerivedInterval(Interval) derived
     * intervals}.
     */
    int liveSetSize() {
        return firstDerivedIntervalIndex == -1 ? operands.size() : firstDerivedIntervalIndex;
    }

    int numLoops() {
        return ir.numLoops();
    }

    boolean isIntervalInLoop(int interval, int loop) {
        return intervalInLoop.at(interval, loop);
    }

    Interval intervalFor(CiValue operand) {
        int operandNumber = operandNumber(operand);
        assert operandNumber < intervalsSize;
        return intervals[operandNumber];
    }

    /**
     * Gets the highest instruction id allocated by this object.
     */
    int maxOpId() {
        assert opIdToInstructionMap.length > 0 : "no operations";
        return (opIdToInstructionMap.length - 1) << 1;
    }

    /**
     * Converts an {@linkplain LIRInstruction#id instruction id} to an instruction index.
     * All LIR instructions in a method have an index one greater than their linear-scan order predecesor
     * with the first instruction having an index of 0.
     */
    static int opIdToIndex(int opId) {
        return opId >> 1;
    }

    /**
     * Retrieves the {@link LIRInstruction} based on its {@linkplain LIRInstruction#id id}.
     *
     * @param opId an instruction {@linkplain LIRInstruction#id id}
     * @return the instruction whose {@linkplain LIRInstruction#id} {@code == id}
     */
    LIRInstruction instructionForId(int opId) {
        assert isEven(opId) : "opId not even";
        LIRInstruction instr = opIdToInstructionMap[opIdToIndex(opId)];
        assert instr.id == opId;
        return instr;
    }

    /**
     * Gets the block containing a given instruction.
     *
     * @param opId an instruction {@linkplain LIRInstruction#id id}
     * @return the block containing the instruction denoted by {@code opId}
     */
    BlockBegin blockForId(int opId) {
        assert opIdToBlockMap.length > 0 && opId >= 0 && opId <= maxOpId() + 1 : "opId out of range";
        return opIdToBlockMap[opIdToIndex(opId)];
    }

    boolean isBlockBegin(int opId) {
        return opId == 0 || blockForId(opId) != blockForId(opId - 1);
    }

    boolean coversBlockBegin(int opId1, int opId2) {
        return blockForId(opId1) != blockForId(opId2);
    }

    /**
     * Determines if an {@link LIRInstruction} destroys all caller saved registers.
     *
     * @param opId an instruction {@linkplain LIRInstruction#id id}
     * @return {@code true} if the instruction denoted by {@code id} destroys all caller saved registers.
     */
    boolean hasCall(int opId) {
        assert isEven(opId) : "opId not even";
        return instructionForId(opId).hasCall;
    }

    /**
     * Eliminates moves from register to stack if the stack slot is known to be correct.
     */
    void changeSpillDefinitionPos(Interval interval, int defPos) {
        assert interval.isSplitParent() : "can only be called for split parents";

        switch (interval.spillState()) {
            case NoDefinitionFound:
                assert interval.spillDefinitionPos() == -1 : "must no be set before";
                interval.setSpillDefinitionPos(defPos);
                interval.setSpillState(SpillState.NoSpillStore);
                break;

            case NoSpillStore:
                assert defPos <= interval.spillDefinitionPos() : "positions are processed in reverse order when intervals are created";
                if (defPos < interval.spillDefinitionPos() - 2 || instructionForId(interval.spillDefinitionPos()).code == LIROpcode.Xir) {
                    // second definition found, so no spill optimization possible for this interval
                    interval.setSpillState(SpillState.NoOptimization);
                } else {
                    // two consecutive definitions (because of two-operand LIR form)
                    assert blockForId(defPos) == blockForId(interval.spillDefinitionPos()) : "block must be equal";
                }
                break;

            case NoOptimization:
                // nothing to do
                break;

            default:
                throw new CiBailout("other states not allowed at this time");
        }
    }

    // called during register allocation
    void changeSpillState(Interval interval, int spillPos) {
        switch (interval.spillState()) {
            case NoSpillStore: {
                int defLoopDepth = blockForId(interval.spillDefinitionPos()).loopDepth();
                int spillLoopDepth = blockForId(spillPos).loopDepth();

                if (defLoopDepth < spillLoopDepth) {
                    // the loop depth of the spilling position is higher then the loop depth
                    // at the definition of the interval . move write to memory out of loop
                    // by storing at definitin of the interval
                    interval.setSpillState(SpillState.StoreAtDefinition);
                } else {
                    // the interval is currently spilled only once, so for now there is no
                    // reason to store the interval at the definition
                    interval.setSpillState(SpillState.OneSpillStore);
                }
                break;
            }

            case OneSpillStore: {
                // the interval is spilled more then once, so it is better to store it to
                // memory at the definition
                interval.setSpillState(SpillState.StoreAtDefinition);
                break;
            }

            case StoreAtDefinition:
            case StartInMemory:
            case NoOptimization:
            case NoDefinitionFound:
                // nothing to do
                break;

            default:
                throw new CiBailout("other states not allowed at this time");
        }
    }

    abstract static class IntervalPredicate {
        abstract boolean apply(Interval i);
    }

    private static final IntervalPredicate mustStoreAtDefinition = new IntervalPredicate() {
        @Override
        public boolean apply(Interval i) {
            return i.isSplitParent() && i.spillState() == SpillState.StoreAtDefinition;
        }
    };

    // called once before assignment of register numbers
    void eliminateSpillMoves() {
        if (C1XOptions.TraceLinearScanLevel >= 3) {
            TTY.println(" Eliminating unnecessary spill moves");
        }

        // collect all intervals that must be stored after their definition.
        // the list is sorted by Interval.spillDefinitionPos
        Interval interval;
        interval = createUnhandledLists(mustStoreAtDefinition, null).first;
        if (C1XOptions.DetailedAsserts) {
            checkIntervals(interval);
        }

        LIRInsertionBuffer insertionBuffer = new LIRInsertionBuffer();
        int numBlocks = blockCount();
        for (int i = 0; i < numBlocks; i++) {
            BlockBegin block = blockAt(i);
            List<LIRInstruction> instructions = block.lir().instructionsList();
            int numInst = instructions.size();
            boolean hasNew = false;

            // iterate all instructions of the block. skip the first because it is always a label
            for (int j = 1; j < numInst; j++) {
                LIRInstruction op = instructions.get(j);
                int opId = op.id;

                if (opId == -1) {
                    CiValue resultOperand = op.result();
                    // remove move from register to stack if the stack slot is guaranteed to be correct.
                    // only moves that have been inserted by LinearScan can be removed.
                    assert op.code == LIROpcode.Move : "only moves can have a opId of -1";
                    assert resultOperand.isVariable() : "LinearScan inserts only moves to variables";

                    LIROp1 op1 = (LIROp1) op;
                    Interval curInterval = intervalFor(resultOperand);

                    if (!curInterval.location().isRegister() && curInterval.alwaysInMemory()) {
                        // move target is a stack slot that is always correct, so eliminate instruction
                        if (C1XOptions.TraceLinearScanLevel >= 4) {
                            TTY.println("eliminating move from interval %d to %d", operandNumber(op1.operand()), operandNumber(op1.result()));
                        }
                        instructions.set(j, null); // null-instructions are deleted by assignRegNum
                    }

                } else {
                    // insert move from register to stack just after the beginning of the interval
                    assert interval == Interval.EndMarker || interval.spillDefinitionPos() >= opId : "invalid order";
                    assert interval == Interval.EndMarker || (interval.isSplitParent() && interval.spillState() == SpillState.StoreAtDefinition) : "invalid interval";

                    while (interval != Interval.EndMarker && interval.spillDefinitionPos() == opId) {
                        if (!hasNew) {
                            // prepare insertion buffer (appended when all instructions of the block are processed)
                            insertionBuffer.init(block.lir());
                            hasNew = true;
                        }

                        CiValue fromLocation = interval.location();
                        CiValue toLocation = canonicalSpillOpr(interval);

                        assert fromLocation.isRegister() : "from operand must be a register but is: " + fromLocation + " toLocation=" + toLocation + " spillState=" + interval.spillState();
                        assert toLocation.isStackSlot() : "to operand must be a stack slot";

                        insertionBuffer.move(j, fromLocation, toLocation, null);

                        if (C1XOptions.TraceLinearScanLevel >= 4) {
                            CiStackSlot slot = interval.spillSlot();
                            TTY.println("inserting move after definition of interval %d to stack slot %d%s at opId %d",
                                            interval.operandNumber, slot.index(), slot.inCallerFrame() ? " in caller frame" : "", opId);
                        }

                        interval = interval.next;
                    }
                }
            } // end of instruction iteration

            if (hasNew) {
                block.lir().append(insertionBuffer);
            }
        } // end of block iteration

        assert interval == Interval.EndMarker : "missed an interval";
    }

    private void checkIntervals(Interval interval) {
        Interval prev = null;
        Interval temp = interval;
        while (temp != Interval.EndMarker) {
            assert temp.spillDefinitionPos() > 0 : "invalid spill definition pos";
            if (prev != null) {
                assert temp.from() >= prev.from() : "intervals not sorted";
                assert temp.spillDefinitionPos() >= prev.spillDefinitionPos() : "when intervals are sorted by from :  then they must also be sorted by spillDefinitionPos";
            }

            assert temp.spillSlot() != null : "interval has no spill slot assigned";
            assert temp.spillDefinitionPos() >= temp.from() : "invalid order";
            assert temp.spillDefinitionPos() <= temp.from() + 2 : "only intervals defined once at their start-pos can be optimized";

            if (C1XOptions.TraceLinearScanLevel >= 4) {
                TTY.println("interval %d (from %d to %d) must be stored at %d", temp.operandNumber, temp.from(), temp.to(), temp.spillDefinitionPos());
            }

            prev = temp;
            temp = temp.next;
        }
    }

    /**
     * Numbers all instructions in all blocks. The numbering follows the {@linkplain ComputeLinearScanOrder linear scan order}.
     */
    void numberInstructions() {
        // Assign IDs to LIR nodes and build a mapping, lirOps, from ID to LIRInstruction node.
        int numBlocks = blockCount();
        int numInstructions = 0;
        for (int i = 0; i < numBlocks; i++) {
            numInstructions += blockAt(i).lir().instructionsList().size();
        }

        // initialize with correct length
        opIdToInstructionMap = new LIRInstruction[numInstructions];
        opIdToBlockMap = new BlockBegin[numInstructions];

        int opId = 0;
        int index = 0;

        for (int i = 0; i < numBlocks; i++) {
            BlockBegin block = blockAt(i);
            block.setFirstLirInstructionId(opId);
            List<LIRInstruction> instructions = block.lir().instructionsList();

            int numInst = instructions.size();
            for (int j = 0; j < numInst; j++) {
                LIRInstruction op = instructions.get(j);
                op.id = opId;

                opIdToInstructionMap[index] = op;
                opIdToBlockMap[index] = block;
                assert instructionForId(opId) == op : "must match";

                index++;
                opId += 2; // numbering of lirOps by two
            }
            block.setLastLirInstructionId(opId - 2);
        }
        assert index == numInstructions : "must match";
        assert (index << 1) == opId : "must match: " + (index << 1);
    }

    /**
     * Computes local live sets (i.e. {@link LIRBlock#liveGen} and {@link LIRBlock#liveKill}) separately for each block.
     */
    void computeLocalLiveSets() {
        int numBlocks = blockCount();
        int liveSize = liveSetSize();

        BitMap2D localIntervalInLoop = new BitMap2D(operands.size(), numLoops());

        // iterate all blocks
        for (int i = 0; i < numBlocks; i++) {
            BlockBegin block = blockAt(i);
            final CiBitMap liveGen = new CiBitMap(liveSize);
            final CiBitMap liveKill = new CiBitMap(liveSize);

            if (block.isExceptionEntry()) {
                // Phi functions at the begin of an exception handler are
                // implicitly defined (= killed) at the beginning of the block.
                block.stateBefore().forEachLivePhi(block, new PhiProcedure() {
                    public boolean doPhi(Phi phi) {
                        liveKill.set(operandNumber(phi.operand()));
                        return true;
                    }
                });
            }

            List<LIRInstruction> instructions = block.lir().instructionsList();
            int numInst = instructions.size();

            // iterate all instructions of the block. skip the first because it is always a label
            assert !instructions.get(0).hasOperands() : "first operation must always be a label";
            for (int j = 1; j < numInst; j++) {
                final LIRInstruction op = instructions.get(j);

                // iterate input operands of instruction
                int n = op.operandCount(LIRInstruction.OperandMode.Input);
                for (int k = 0; k < n; k++) {
                    CiValue operand = op.operandAt(LIRInstruction.OperandMode.Input, k);

                    if (operand.isVariable()) {
                        int operandNum = operandNumber(operand);
                        if (!liveKill.get(operandNum)) {
                            liveGen.set(operandNum);
                            if (C1XOptions.TraceLinearScanLevel >= 4) {
                                TTY.println("  Setting liveGen for operand %d at instruction %d", operandNum, op.id);
                            }
                        }
                        if (block.loopIndex() >= 0) {
                            localIntervalInLoop.setBit(operandNum, block.loopIndex());
                        }
                    }

                    if (C1XOptions.DetailedAsserts) {
                        assert operand.isVariableOrRegister() : "visitor should only return register operands";
                        verifyInput(block, liveKill, operand);
                    }
                }

                // Add uses of live locals from interpreter's point of view for proper debug information generation
                LIRDebugInfo info = op.info;
                if (info != null) {
                    info.state.forEachLiveStateValue(new ValueProcedure() {
                        public void doValue(Value value) {
                            CiValue operand = value.operand();
                            if (operand.isVariable()) {
                                int operandNum = operandNumber(operand);
                                if (!liveKill.get(operandNum)) {
                                    liveGen.set(operandNum);
                                    if (C1XOptions.TraceLinearScanLevel >= 4) {
                                        TTY.println("  Setting liveGen for value %s, LIR opId %d, operand %d", Util.valueString(value), op.id, operandNum);
                                    }
                                }
                            } else if (operand.isRegister()) {
                                assert !isProcessed(operand) && !operand.kind.isObject();
                            } else {
                                assert operand.isConstant() || operand.isIllegal() : "invalid operand for deoptimization value: " + value;
                            }
                        }
                    });
                }

                // iterate temp operands of instruction
                n = op.operandCount(LIRInstruction.OperandMode.Temp);
                for (int k = 0; k < n; k++) {
                    CiValue operand = op.operandAt(LIRInstruction.OperandMode.Temp, k);

                    if (operand.isVariable()) {
                        int varNum = operandNumber(operand);
                        liveKill.set(varNum);
                        if (block.loopIndex() >= 0) {
                            localIntervalInLoop.setBit(varNum, block.loopIndex());
                        }
                    }

                    if (C1XOptions.DetailedAsserts) {
                        assert operand.isVariableOrRegister() : "visitor should only return register operands";
                        verifyTemp(liveKill, operand);
                    }
                }

                // iterate output operands of instruction
                n = op.operandCount(LIRInstruction.OperandMode.Output);
                for (int k = 0; k < n; k++) {
                    CiValue operand = op.operandAt(LIRInstruction.OperandMode.Output, k);

                    if (operand.isVariable()) {
                        int varNum = operandNumber(operand);
                        liveKill.set(varNum);
                        if (block.loopIndex() >= 0) {
                            localIntervalInLoop.setBit(varNum, block.loopIndex());
                        }
                    }

                    if (C1XOptions.DetailedAsserts) {
                        assert operand.isVariableOrRegister() : "visitor should only return register operands";
                        // fixed intervals are never live at block boundaries, so
                        // they need not be processed in live sets
                        // process them only in debug mode so that this can be checked
                        verifyTemp(liveKill, operand);
                    }
                }
            } // end of instruction iteration

            LIRBlock lirBlock = block.lirBlock();
            lirBlock.liveGen = liveGen;
            lirBlock.liveKill = liveKill;
            lirBlock.liveIn = new CiBitMap(liveSize);
            lirBlock.liveOut = new CiBitMap(liveSize);

            if (C1XOptions.TraceLinearScanLevel >= 4) {
                TTY.println("liveGen  B%d %s", block.blockID, block.lirBlock.liveGen);
                TTY.println("liveKill B%d %s", block.blockID, block.lirBlock.liveKill);
            }
        } // end of block iteration

        intervalInLoop = localIntervalInLoop;
    }

    private void verifyTemp(CiBitMap liveKill, CiValue operand) {
        // fixed intervals are never live at block boundaries, so
        // they need not be processed in live sets
        // process them only in debug mode so that this can be checked
        if (!operand.isVariable()) {
            if (isProcessed(operand)) {
                liveKill.set(operandNumber(operand));
            }
        }
    }

    private void verifyInput(BlockBegin block, CiBitMap liveKill, CiValue operand) {
        // fixed intervals are never live at block boundaries, so
        // they need not be processed in live sets.
        // this is checked by these assertions to be sure about it.
        // the entry block may have incoming
        // values in registers, which is ok.
        if (!operand.isVariable() && block != ir.startBlock) {
            if (isProcessed(operand)) {
                assert liveKill.get(operandNumber(operand)) : "using fixed register that is not defined in this block";
            }
        }
    }

    /**
     * Performs a backward dataflow analysis to compute global live sets (i.e. {@link LIRBlock#liveIn} and
     * {@link LIRBlock#liveOut}) for each block.
     */
    void computeGlobalLiveSets() {
        int numBlocks = blockCount();
        boolean changeOccurred;
        boolean changeOccurredInBlock;
        int iterationCount = 0;
        CiBitMap liveOut = new CiBitMap(liveSetSize()); // scratch set for calculations

        // Perform a backward dataflow analysis to compute liveOut and liveIn for each block.
        // The loop is executed until a fixpoint is reached (no changes in an iteration)
        // Exception handlers must be processed because not all live values are
        // present in the state array, e.g. because of global value numbering
        do {
            changeOccurred = false;

            // iterate all blocks in reverse order
            for (int i = numBlocks - 1; i >= 0; i--) {
                BlockBegin block = blockAt(i);
                LIRBlock lirBlock = block.lirBlock();

                changeOccurredInBlock = false;

                // liveOut(block) is the union of liveIn(sux), for successors sux of block
                int n = block.numberOfSux();
                int e = block.numberOfExceptionHandlers();
                if (n + e > 0) {
                    // block has successors
                    if (n > 0) {
                        liveOut.setFrom(block.suxAt(0).lirBlock.liveIn);
                        for (int j = 1; j < n; j++) {
                            liveOut.setUnion(block.suxAt(j).lirBlock.liveIn);
                        }
                    } else {
                        liveOut.clearAll();
                    }
                    for (int j = 0; j < e; j++) {
                        liveOut.setUnion(block.exceptionHandlerAt(j).lirBlock.liveIn);
                    }

                    if (!lirBlock.liveOut.isSame(liveOut)) {
                        // A change occurred. Swap the old and new live out sets to avoid copying.
                        CiBitMap temp = lirBlock.liveOut;
                        lirBlock.liveOut = liveOut;
                        liveOut = temp;

                        changeOccurred = true;
                        changeOccurredInBlock = true;
                    }
                }

                if (iterationCount == 0 || changeOccurredInBlock) {
                    // liveIn(block) is the union of liveGen(block) with (liveOut(block) & !liveKill(block))
                    // note: liveIn has to be computed only in first iteration or if liveOut has changed!
                    CiBitMap liveIn = lirBlock.liveIn;
                    liveIn.setFrom(lirBlock.liveOut);
                    liveIn.setDifference(lirBlock.liveKill);
                    liveIn.setUnion(lirBlock.liveGen);
                }

                if (C1XOptions.TraceLinearScanLevel >= 4) {
                    traceLiveness(changeOccurredInBlock, iterationCount, block);
                }
            }
            iterationCount++;

            if (changeOccurred && iterationCount > 50) {
                throw new CiBailout("too many iterations in computeGlobalLiveSets");
            }
        } while (changeOccurred);

        if (C1XOptions.DetailedAsserts) {
            verifyLiveness(numBlocks);
        }

        // check that the liveIn set of the first block is empty
        CiBitMap liveInArgs = new CiBitMap(ir.startBlock.lirBlock.liveIn.size());
        if (!ir.startBlock.lirBlock.liveIn.isSame(liveInArgs)) {
            if (C1XOptions.DetailedAsserts) {
                reportFailure(numBlocks);
            }

            // bailout of if this occurs in product mode.
            throw new CiBailout("liveIn set of first block must be empty");
        }
    }

    private void reportFailure(int numBlocks) {
        TTY.println("Error: liveIn set of first block must be empty (when this fails, variables are used before they are defined)");
        TTY.print("affected registers:");
        TTY.println(ir.startBlock.lirBlock.liveIn.toString());

        // print some additional information to simplify debugging
        for (int operandNum = 0; operandNum < ir.startBlock.lirBlock.liveIn.size(); operandNum++) {
            if (ir.startBlock.lirBlock.liveIn.get(operandNum)) {
                CiValue operand = operands.operandFor(operandNum);
                Value instr = operand.isVariable() ? gen.operands.instructionForResult(((CiVariable) operand)) : null;
                TTY.println(" var %d (HIR instruction %s)", operandNum, instr == null ? " " : instr.toString());

                for (int j = 0; j < numBlocks; j++) {
                    BlockBegin block = blockAt(j);
                    if (block.lirBlock.liveGen.get(operandNum)) {
                        TTY.println("  used in block B%d", block.blockID);
                    }
                    if (block.lirBlock.liveKill.get(operandNum)) {
                        TTY.println("  defined in block B%d", block.blockID);
                    }
                }
            }
        }
    }

    private void verifyLiveness(int numBlocks) {
        // check that fixed intervals are not live at block boundaries
        // (live set must be empty at fixed intervals)
        for (int i = 0; i < numBlocks; i++) {
            BlockBegin block = blockAt(i);
            for (int j = 0; j <= operands.maxRegisterNumber(); j++) {
                assert !block.lirBlock.liveIn.get(j) : "liveIn  set of fixed register must be empty";
                assert !block.lirBlock.liveOut.get(j) : "liveOut set of fixed register must be empty";
                assert !block.lirBlock.liveGen.get(j) : "liveGen set of fixed register must be empty";
            }
        }
    }

    private void traceLiveness(boolean changeOccurredInBlock, int iterationCount, BlockBegin block) {
        char c = iterationCount == 0 || changeOccurredInBlock ? '*' : ' ';
        TTY.print("(%d) liveIn%c  B%d ", iterationCount, c, block.blockID);
        TTY.println(block.lirBlock.liveIn.toString());
        TTY.print("(%d) liveOut%c B%d ", iterationCount, c, block.blockID);
        TTY.println(block.lirBlock.liveOut.toString());
    }

    Interval addUse(CiValue operand, int from, int to, RegisterPriority registerPriority, CiKind kind) {
        if (!isProcessed(operand)) {
            return null;
        }
        if (C1XOptions.TraceLinearScanLevel >= 2 && kind == null) {
            TTY.println(" use %s from %d to %d (%s)", operand, from, to, registerPriority.name());
        }

        if (kind == null) {
            kind = operand.kind.stackKind();
        }
        Interval interval = intervalFor(operand);
        if (interval == null) {
            interval = createInterval(operand);
        }

        if (kind != CiKind.Illegal) {
            interval.setKind(kind);
        }

        if (operand.isVariable() && gen.operands.mustStayInMemory((CiVariable) operand)) {
            interval.addRange(from, maxOpId());
        } else {
            interval.addRange(from, to);
        }

        interval.addUsePos(to, registerPriority);
        return interval;
    }

    void addTemp(CiValue operand, int tempPos, RegisterPriority registerPriority, CiKind kind) {
        if (!isProcessed(operand)) {
            return;
        }
        Interval interval = intervalFor(operand);
        if (interval == null) {
            interval = createInterval(operand);
        }

        if (kind != CiKind.Illegal) {
            interval.setKind(kind);
        }

        interval.addRange(tempPos, tempPos + 1);
        interval.addUsePos(tempPos, registerPriority);
    }

    boolean isProcessed(CiValue operand) {
        return !operand.isRegister() || attributes(operand.asRegister()).isAllocatable;
    }

    void addDef(CiValue operand, int defPos, RegisterPriority registerPriority, CiKind kind) {
        if (!isProcessed(operand)) {
            return;
        }
        if (C1XOptions.TraceLinearScanLevel >= 2) {
            TTY.println(" def %s defPos %d (%s)", operand, defPos, registerPriority.name());
        }
        Interval interval = intervalFor(operand);
        if (interval != null) {

            if (kind != CiKind.Illegal) {
                interval.setKind(kind);
            }

            Range r = interval.first();
            if (r.from <= defPos) {
                // Update the starting point (when a range is first created for a use, its
                // start is the beginning of the current block until a def is encountered.)
                r.from = defPos;
                interval.addUsePos(defPos, registerPriority);

            } else {
                // Dead value - make vacuous interval
                // also add register priority for dead intervals
                interval.addRange(defPos, defPos + 1);
                interval.addUsePos(defPos, registerPriority);
                if (C1XOptions.TraceLinearScanLevel >= 2) {
                    TTY.println("Warning: def of operand %s at %d occurs without use", operand, defPos);
                }
            }

        } else {
            // Dead value - make vacuous interval
            // also add register priority for dead intervals
            interval = createInterval(operand);
            if (kind != CiKind.Illegal) {
                interval.setKind(kind);
            }

            interval.addRange(defPos, defPos + 1);
            interval.addUsePos(defPos, registerPriority);
            if (C1XOptions.TraceLinearScanLevel >= 2) {
                TTY.println("Warning: dead value %s at %d in live intervals", operand, defPos);
            }
        }

        changeSpillDefinitionPos(interval, defPos);
        if (registerPriority == RegisterPriority.None && interval.spillState().ordinal() <= SpillState.StartInMemory.ordinal()) {
            // detection of method-parameters and roundfp-results
            // TODO: move this directly to position where use-kind is computed
            interval.setSpillState(SpillState.StartInMemory);
        }
    }

    /**
     * Determines the register priority for an instruction's output/result operand.
     */
    RegisterPriority registerPriorityOfOutputOperand(LIRInstruction op, CiValue operand) {
        if (op.code == LIROpcode.Move) {
            LIROp1 move = (LIROp1) op;
            CiValue res = move.result();
            boolean resultInMemory = res.isVariable() && operands.mustStartInMemory((CiVariable) res);

            if (resultInMemory) {
                // Begin of an interval with mustStartInMemory set.
                // This interval will always get a stack slot first, so return noUse.
                return RegisterPriority.None;

            } else if (move.operand().isStackSlot()) {
                // method argument (condition must be equal to handleMethodArguments)
                return RegisterPriority.None;

            } else if (move.operand().isVariableOrRegister() && move.result().isVariableOrRegister()) {
                // Move from register to register
                if (blockForId(op.id).checkBlockFlag(BlockBegin.BlockFlag.OsrEntry)) {
                    // special handling of phi-function moves inside osr-entry blocks
                    // input operand must have a register instead of output operand (leads to better register
                    // allocation)
                    return RegisterPriority.ShouldHaveRegister;
                }
            }
        }

        if (operand.isVariable() && operands.mustStartInMemory((CiVariable) operand)) {
            // result is a stack-slot, so prevent immediate reloading
            return RegisterPriority.None;
        }

        // all other operands require a register
        return RegisterPriority.MustHaveRegister;
    }

    /**
     * Determines the priority which with an instruction's input operand will be allocated a register.
     */
    RegisterPriority registerPriorityOfInputOperand(LIRInstruction op, CiValue operand) {
        if (op.code == LIROpcode.Move) {
            LIROp1 move = (LIROp1) op;
            CiValue res = move.result();
            boolean resultInMemory = res.isVariable() && operands.mustStartInMemory((CiVariable) res);

            if (resultInMemory) {
                // Move to an interval with mustStartInMemory set.
                // To avoid moves from stack to stack (not allowed) force the input operand to a register
                return RegisterPriority.MustHaveRegister;

            } else if (move.operand().isVariableOrRegister() && move.result().isVariableOrRegister()) {
                // Move from register to register
                if (blockForId(op.id).checkBlockFlag(BlockBegin.BlockFlag.OsrEntry)) {
                    // special handling of phi-function moves inside osr-entry blocks
                    // input operand must have a register instead of output operand (leads to better register
                    // allocation)
                    return RegisterPriority.MustHaveRegister;
                }

                // The input operand is not forced to a register (moves from stack to register are allowed),
                // but it is faster if the input operand is in a register
                return RegisterPriority.ShouldHaveRegister;
            }
        }

        if (compilation.target.arch.isX86()) {
            if (op.code == LIROpcode.Cmove) {
                // conditional moves can handle stack operands
                assert op.result().isVariableOrRegister();
                return RegisterPriority.ShouldHaveRegister;
            }

            // optimizations for second input operand of arithmetic operations on Intel
            // this operand is allowed to be on the stack in some cases
            CiKind kind = operand.kind.stackKind();
            if (kind == CiKind.Float || kind == CiKind.Double) {
                // SSE float instruction (CiKind.Double only supported with SSE2)
                switch (op.code) {
                    case Cmp:
                    case Add:
                    case Sub:
                    case Mul:
                    case Div: {
                        LIROp2 op2 = (LIROp2) op;
                        if (op2.operand1() != op2.operand2() && op2.operand2() == operand) {
                            assert (op2.result().isVariableOrRegister() || op.code == LIROpcode.Cmp) && op2.operand1().isVariableOrRegister() : "cannot mark second operand as stack if others are not in register";
                            return RegisterPriority.ShouldHaveRegister;
                        }
                    }
                }
            } else if (kind != CiKind.Long) {
                // integer instruction (note: long operands must always be in register)
                switch (op.code) {
                    case Cmp:
                    case Add:
                    case Sub:
                    case LogicAnd:
                    case LogicOr:
                    case LogicXor: {
                        LIROp2 op2 = (LIROp2) op;
                        if (op2.operand1() != op2.operand2() && op2.operand2() == operand) {
                            assert (op2.result().isVariableOrRegister() || op.code == LIROpcode.Cmp) && op2.operand1().isVariableOrRegister() : "cannot mark second operand as stack if others are not in register";
                            return RegisterPriority.ShouldHaveRegister;
                        }
                    }
                }
            }
        } // X86

        // all other operands require a register
        return RegisterPriority.MustHaveRegister;
    }

    /**
     * Optimizes moves related to incoming stack based arguments.
     * The interval for the destination of such moves is assigned
     * the stack slot (which is in the caller's frame) as its
     * spill slot.
     */
    void handleMethodArguments(LIRInstruction op) {
        if (op.code == LIROpcode.Move) {
            LIROp1 move = (LIROp1) op;

            if (move.operand().isStackSlot()) {
                CiStackSlot slot = (CiStackSlot) move.operand();
                if (C1XOptions.DetailedAsserts) {
                    int argSlots = compilation.method.signature().argumentSlots(!isStatic(compilation.method.accessFlags()));
                    assert slot.index() >= 0 && slot.index() < argSlots;
                    assert move.id > 0 : "invalid id";
                    assert blockForId(move.id).numberOfPreds() == 0 : "move from stack must be in first block";
                    assert move.result().isVariable() : "result of move must be a variable";

                    if (C1XOptions.TraceLinearScanLevel >= 4) {
                        TTY.println("found move from stack slot %s to %s", slot, move.result());
                    }
                }

                Interval interval = intervalFor(move.result());
                CiStackSlot copySlot = slot;
                if (C1XOptions.CopyPointerStackArguments && slot.kind == CiKind.Object) {
                    copySlot = allocateSpillSlot(slot.kind);
                }
                interval.setSpillSlot(copySlot);
                interval.assignLocation(copySlot);
            }
        }
    }

    void addRegisterHints(LIRInstruction op) {
        switch (op.code) {
            case Move: // fall through
            case Convert: {
                LIROp1 move = (LIROp1) op;

                CiValue moveFrom = move.operand();
                CiValue moveTo = move.result();

                if (moveTo.isVariableOrRegister() && moveFrom.isVariableOrRegister()) {
                    Interval from = intervalFor(moveFrom);
                    Interval to = intervalFor(moveTo);
                    if (from != null && to != null) {
                        to.setLocationHint(from);
                        if (C1XOptions.TraceLinearScanLevel >= 4) {
                            TTY.println("operation at opId %d: added hint from interval %d to %d", move.id, from.operandNumber, to.operandNumber);
                        }
                    }
                }
                break;
            }
            case Cmove: {
                LIROp2 cmove = (LIROp2) op;

                CiValue moveFrom = cmove.operand1();
                CiValue moveTo = cmove.result();

                if (moveTo.isVariableOrRegister() && moveFrom.isVariableOrRegister()) {
                    Interval from = intervalFor(moveFrom);
                    Interval to = intervalFor(moveTo);
                    if (from != null && to != null) {
                        to.setLocationHint(from);
                        if (C1XOptions.TraceLinearScanLevel >= 4) {
                            TTY.println("operation at opId %d: added hint from interval %d to %d", cmove.id, from.operandNumber, to.operandNumber);
                        }
                    }
                }
                break;
            }
        }
    }

    void buildIntervals() {
        intervalsSize = operands.size();
        intervals = new Interval[intervalsSize + INITIAL_SPLIT_INTERVALS_CAPACITY];

        // create a list with all caller-save registers (cpu, fpu, xmm)
        RiRegisterConfig registerConfig = compilation.registerConfig;
        CiRegister[] callerSaveRegs = registerConfig.getCallerSaveRegisters();

        // iterate all blocks in reverse order
        for (int i = blockCount() - 1; i >= 0; i--) {
            BlockBegin block = blockAt(i);
            List<LIRInstruction> instructions = block.lir().instructionsList();
            final int blockFrom = block.firstLirInstructionId();
            int blockTo = block.lastLirInstructionId();

            assert blockFrom == instructions.get(0).id;
            assert blockTo == instructions.get(instructions.size() - 1).id;

            // Update intervals for operands live at the end of this block;
            CiBitMap live = block.lirBlock.liveOut;
            for (int operandNum = live.nextSetBit(0); operandNum >= 0; operandNum = live.nextSetBit(operandNum + 1)) {
                assert live.get(operandNum) : "should not stop here otherwise";
                CiValue operand = operands.operandFor(operandNum);
                if (C1XOptions.TraceLinearScanLevel >= 2) {
                    TTY.println("live in %s to %d", operand, blockTo + 2);
                }

                addUse(operand, blockFrom, blockTo + 2, RegisterPriority.None, CiKind.Illegal);

                // add special use positions for loop-end blocks when the
                // interval is used anywhere inside this loop. It's possible
                // that the block was part of a non-natural loop, so it might
                // have an invalid loop index.
                if (block.checkBlockFlag(BlockBegin.BlockFlag.LinearScanLoopEnd) && block.loopIndex() != -1 && isIntervalInLoop(operandNum, block.loopIndex())) {
                    intervalFor(operand).addUsePos(blockTo + 1, RegisterPriority.LiveAtLoopEnd);
                }
            }

            // iterate all instructions of the block in reverse order.
            // skip the first instruction because it is always a label
            // definitions of intervals are processed before uses
            assert !instructions.get(0).hasOperands() : "first operation must always be a label";
            for (int j = instructions.size() - 1; j >= 1; j--) {
                LIRInstruction op = instructions.get(j);
                final int opId = op.id;

                // add a temp range for each register if operation destroys caller-save registers
                if (op.hasCall) {
                    for (CiRegister r : callerSaveRegs) {
                        if (attributes(r).isAllocatable) {
                            addTemp(r.asValue(), opId, RegisterPriority.None, CiKind.Illegal);
                        }
                    }
                    if (C1XOptions.TraceLinearScanLevel >= 4) {
                        TTY.println("operation destroys all caller-save registers");
                    }
                }

                // Add any platform dependent temps
                pdAddTemps(op);

                // visit definitions (output and temp operands)
                int k;
                int n;
                n = op.operandCount(LIRInstruction.OperandMode.Output);
                for (k = 0; k < n; k++) {
                    CiValue operand = op.operandAt(LIRInstruction.OperandMode.Output, k);
                    assert operand.isVariableOrRegister();
                    addDef(operand, opId, registerPriorityOfOutputOperand(op, operand), operand.kind.stackKind());
                }

                n = op.operandCount(LIRInstruction.OperandMode.Temp);
                for (k = 0; k < n; k++) {
                    CiValue operand = op.operandAt(LIRInstruction.OperandMode.Temp, k);
                    assert operand.isVariableOrRegister();
                    if (C1XOptions.TraceLinearScanLevel >= 2) {
                        TTY.println(" temp %s tempPos %d (%s)", operand, opId, RegisterPriority.MustHaveRegister.name());
                    }
                    addTemp(operand, opId, RegisterPriority.MustHaveRegister, operand.kind.stackKind());
                }

                // visit uses (input operands)
                n = op.operandCount(LIRInstruction.OperandMode.Input);
                for (k = 0; k < n; k++) {
                    CiValue operand = op.operandAt(LIRInstruction.OperandMode.Input, k);
                    assert operand.isVariableOrRegister();
                    RegisterPriority p = registerPriorityOfInputOperand(op, operand);
                    Interval interval = addUse(operand, blockFrom, opId, p, null);
                    if (interval != null && op instanceof LIRXirInstruction) {
                        Range range = interval.first();
                        // (tw) Increase range by 1 in order to overlap the input with the temp and the output operand.
                        if (range.to == opId) {
                            range.to++;
                        }
                    }
                }

                // Add uses of live locals from interpreter's point of view for proper
                // debug information generation
                // Treat these operands as temp values (if the live range is extended
                // to a call site, the value would be in a register at the call otherwise)
                LIRDebugInfo info = op.info;
                if (info != null) {
                    info.state.forEachLiveStateValue(new ValueProcedure() {
                        public void doValue(Value value) {
                            CiValue operand = value.operand();
                            if (operand.isVariableOrRegister()) {
                                addUse(operand, blockFrom, (opId + 1), RegisterPriority.None, null);
                            }
                        }
                    });
                }

                // special steps for some instructions (especially moves)
                handleMethodArguments(op);
                addRegisterHints(op);

            } // end of instruction iteration

            // (tw) Make sure that no spill store optimization is applied for phi instructions that flow into exception handlers.
            if (block.isExceptionEntry()) {
                FrameState stateBefore = block.stateBefore();
                stateBefore.forEachLivePhi(block, new PhiProcedure() {
                    @Override
                    public boolean doPhi(Phi phi) {
                        Interval interval = intervalFor(phi.operand());
                        if (interval != null) {
                            interval.setSpillState(SpillState.NoOptimization);
                        }
                        return true;
                    }
                });
            }

        } // end of block iteration

        // add the range [0, 1] to all fixed intervals.
        // the register allocator need not handle unhandled fixed intervals
        for (Interval interval : intervals) {
            if (interval != null && interval.operand.isRegister()) {
                interval.addRange(0, 1);
            }
        }
    }

    // * Phase 5: actual register allocation

    private void pdAddTemps(LIRInstruction op) {
        // TODO Platform dependent!
        assert compilation.target.arch.isX86();

        switch (op.code) {
            case Tan:
            case Sin:
            case Cos: {
                // The slow path for these functions may need to save and
                // restore all live registers but we don't want to save and
                // restore everything all the time, so mark the xmms as being
                // killed. If the slow path were explicit or we could propagate
                // live register masks down to the assembly we could do better
                // but we don't have any easy way to do that right now. We
                // could also consider not killing all xmm registers if we
                // assume that slow paths are uncommon but it's not clear that
                // would be a good idea.
                if (C1XOptions.TraceLinearScanLevel >= 2) {
                    TTY.println("killing XMMs for trig");
                }
                int opId = op.id;

                for (CiRegister r : compilation.registerConfig.getCallerSaveRegisters()) {
                    if (r.isFpu()) {
                        addTemp(r.asValue(), opId, RegisterPriority.None, CiKind.Illegal);
                    }
                }
                break;
            }
        }

    }

    boolean isSorted(Interval[] intervals) {
        int from = -1;
        for (Interval interval : intervals) {
            assert interval != null;
            assert from <= interval.from();
            from = interval.from();

            // XXX: very slow!
            assert Arrays.asList(this.intervals).contains(interval);
        }
        return true;
    }

    Interval addToList(Interval first, Interval prev, Interval interval) {
        Interval newFirst = first;
        if (prev != null) {
            prev.next = interval;
        } else {
            newFirst = interval;
        }
        return newFirst;
    }

    Interval.Pair createUnhandledLists(IntervalPredicate isList1, IntervalPredicate isList2) {
        assert isSorted(sortedIntervals) : "interval list is not sorted";

        Interval list1 = Interval.EndMarker;
        Interval list2 = Interval.EndMarker;

        Interval list1Prev = null;
        Interval list2Prev = null;
        Interval v;

        int n = sortedIntervals.length;
        for (int i = 0; i < n; i++) {
            v = sortedIntervals[i];
            if (v == null) {
                continue;
            }

            if (isList1.apply(v)) {
                list1 = addToList(list1, list1Prev, v);
                list1Prev = v;
            } else if (isList2 == null || isList2.apply(v)) {
                list2 = addToList(list2, list2Prev, v);
                list2Prev = v;
            }
        }

        if (list1Prev != null) {
            list1Prev.next = Interval.EndMarker;
        }
        if (list2Prev != null) {
            list2Prev.next = Interval.EndMarker;
        }

        assert list1Prev == null || list1Prev.next == Interval.EndMarker : "linear list ends not with sentinel";
        assert list2Prev == null || list2Prev.next == Interval.EndMarker : "linear list ends not with sentinel";

        return new Interval.Pair(list1, list2);
    }

    void sortIntervalsBeforeAllocation() {
        int sortedLen = 0;
        for (Interval interval : intervals) {
            if (interval != null) {
                sortedLen++;
            }
        }

        Interval[] sortedList = new Interval[sortedLen];
        int sortedIdx = 0;
        int sortedFromMax = -1;

        // special sorting algorithm: the original interval-list is almost sorted,
        // only some intervals are swapped. So this is much faster than a complete QuickSort
        for (Interval interval : intervals) {
            if (interval != null) {
                int from = interval.from();

                if (sortedFromMax <= from) {
                    sortedList[sortedIdx++] = interval;
                    sortedFromMax = interval.from();
                } else {
                    // the assumption that the intervals are already sorted failed,
                    // so this interval must be sorted in manually
                    int j;
                    for (j = sortedIdx - 1; j >= 0 && from < sortedList[j].from(); j--) {
                        sortedList[j + 1] = sortedList[j];
                    }
                    sortedList[j + 1] = interval;
                    sortedIdx++;
                }
            }
        }
        sortedIntervals = sortedList;
    }

    void sortIntervalsAfterAllocation() {
        if (firstDerivedIntervalIndex == -1) {
            // no intervals have been added during allocation, so sorted list is already up to date
            return;
        }

        Interval[] oldList = sortedIntervals;
        Interval[] newList = Arrays.copyOfRange(intervals, firstDerivedIntervalIndex, intervalsSize);
        int oldLen = oldList.length;
        int newLen = newList.length;

        // conventional sort-algorithm for new intervals
        Arrays.sort(newList, INTERVAL_COMPARATOR);

        // merge old and new list (both already sorted) into one combined list
        Interval[] combinedList = new Interval[oldLen + newLen];
        int oldIdx = 0;
        int newIdx = 0;

        while (oldIdx + newIdx < combinedList.length) {
            if (newIdx >= newLen || (oldIdx < oldLen && oldList[oldIdx].from() <= newList[newIdx].from())) {
                combinedList[oldIdx + newIdx] = oldList[oldIdx];
                oldIdx++;
            } else {
                combinedList[oldIdx + newIdx] = newList[newIdx];
                newIdx++;
            }
        }

        sortedIntervals = combinedList;
    }

    private static final Comparator<Interval> INTERVAL_COMPARATOR = new Comparator<Interval>() {

        public int compare(Interval a, Interval b) {
            if (a != null) {
                if (b != null) {
                    return a.from() - b.from();
                } else {
                    return -1;
                }
            } else {
                if (b != null) {
                    return 1;
                } else {
                    return 0;
                }
            }
        }
    };

    public void allocateRegisters() {
        Interval precoloredIntervals;
        Interval notPrecoloredIntervals;

        Interval.Pair result = createUnhandledLists(IS_PRECOLORED_INTERVAL, IS_VARIABLE_INTERVAL);
        precoloredIntervals = result.first;
        notPrecoloredIntervals = result.second;

        // allocate cpu registers
        LinearScanWalker lsw = new LinearScanWalker(this, precoloredIntervals, notPrecoloredIntervals);
        lsw.walk();
        lsw.finishAllocation();
    }

    // * Phase 6: resolve data flow
    // (insert moves at edges between blocks if intervals have been split)

    // wrapper for Interval.splitChildAtOpId that performs a bailout in product mode
    // instead of returning null
    Interval splitChildAtOpId(Interval interval, int opId, LIRInstruction.OperandMode mode) {
        Interval result = interval.getSplitChildAtOpId(opId, mode, this);

        if (result != null) {
            if (C1XOptions.TraceLinearScanLevel >= 4) {
                TTY.println("Split child at pos " + opId + " of interval " + interval.toString() + " is " + result.toString());
            }
            return result;
        }

        throw new CiBailout("LinearScan: interval is null");
    }

    Interval intervalAtBlockBegin(BlockBegin block, CiValue operand) {
        assert operand.isVariable() : "register number out of bounds";
        assert intervalFor(operand) != null : "no interval found";

        return splitChildAtOpId(intervalFor(operand), block.firstLirInstructionId(), LIRInstruction.OperandMode.Output);
    }

    Interval intervalAtBlockEnd(BlockBegin block, CiValue operand) {
        assert operand.isVariable() : "register number out of bounds";
        assert intervalFor(operand) != null : "no interval found";

        return splitChildAtOpId(intervalFor(operand), block.lastLirInstructionId() + 1, LIRInstruction.OperandMode.Output);
    }

    Interval intervalAtOpId(CiValue operand, int opId) {
        assert operand.isVariable() : "register number out of bounds";
        assert intervalFor(operand) != null : "no interval found";

        return splitChildAtOpId(intervalFor(operand), opId, LIRInstruction.OperandMode.Input);
    }

    void resolveCollectMappings(BlockBegin fromBlock, BlockBegin toBlock, MoveResolver moveResolver) {
        assert moveResolver.checkEmpty();

        int numOperands = operands.size();
        CiBitMap liveAtEdge = toBlock.lirBlock.liveIn;

        // visit all variables for which the liveAtEdge bit is set
        for (int operandNum = liveAtEdge.nextSetBit(0); operandNum >= 0; operandNum = liveAtEdge.nextSetBit(operandNum + 1)) {
            assert operandNum < numOperands : "live information set for not exisiting interval";
            assert fromBlock.lirBlock.liveOut.get(operandNum) && toBlock.lirBlock.liveIn.get(operandNum) : "interval not live at this edge";

            CiValue liveOperand = operands.operandFor(operandNum);
            Interval fromInterval = intervalAtBlockEnd(fromBlock, liveOperand);
            Interval toInterval = intervalAtBlockBegin(toBlock, liveOperand);

            if (fromInterval != toInterval && (fromInterval.location() != toInterval.location())) {
                // need to insert move instruction
                moveResolver.addMapping(fromInterval, toInterval);
            }
        }
    }

    void resolveFindInsertPos(BlockBegin fromBlock, BlockBegin toBlock, MoveResolver moveResolver) {
        if (fromBlock.numberOfSux() <= 1) {
            if (C1XOptions.TraceLinearScanLevel >= 4) {
                TTY.println("inserting moves at end of fromBlock B%d", fromBlock.blockID);
            }

            List<LIRInstruction> instructions = fromBlock.lir().instructionsList();
            LIRInstruction instr = instructions.get(instructions.size() - 1);
            if (instr instanceof LIRBranch) {
                LIRBranch branch = (LIRBranch) instr;
                // insert moves before branch
                assert branch.cond() == Condition.TRUE : "block does not end with an unconditional jump";
                moveResolver.setInsertPosition(fromBlock.lir(), instructions.size() - 2);
            } else {
                moveResolver.setInsertPosition(fromBlock.lir(), instructions.size() - 1);
            }

        } else {
            if (C1XOptions.TraceLinearScanLevel >= 4) {
                TTY.println("inserting moves at beginning of toBlock B%d", toBlock.blockID);
            }

            if (C1XOptions.DetailedAsserts) {
                assert fromBlock.lir().instructionsList().get(0) instanceof LIRLabel : "block does not start with a label";

                // because the number of predecessor edges matches the number of
                // successor edges, blocks which are reached by switch statements
                // may have be more than one predecessor but it will be guaranteed
                // that all predecessors will be the same.
                for (int i = 0; i < toBlock.numberOfPreds(); i++) {
                    assert fromBlock == toBlock.predAt(i) : "all critical edges must be broken";
                }
            }

            moveResolver.setInsertPosition(toBlock.lir(), 0);
        }
    }

    /**
     * Inserts necessary moves (spilling or reloading) at edges between blocks for intervals that
     * have been split.
     */
    void resolveDataFlow() {
        int numBlocks = blockCount();
        MoveResolver moveResolver = new MoveResolver(this);
        CiBitMap blockCompleted = new CiBitMap(numBlocks);
        CiBitMap alreadyResolved = new CiBitMap(numBlocks);

        int i;
        for (i = 0; i < numBlocks; i++) {
            BlockBegin block = blockAt(i);

            // check if block has only one predecessor and only one successor
            if (block.numberOfPreds() == 1 && block.numberOfSux() == 1 && block.numberOfExceptionHandlers() == 0 && !block.isExceptionEntry()) {
                List<LIRInstruction> instructions = block.lir().instructionsList();
                assert instructions.get(0).code == LIROpcode.Label : "block must start with label";
                assert instructions.get(instructions.size() - 1).code == LIROpcode.Branch : "block with successors must end with branch";
                assert ((LIRBranch) instructions.get(instructions.size() - 1)).cond() == Condition.TRUE : "block with successor must end with unconditional branch";

                // check if block is empty (only label and branch)
                if (instructions.size() == 2) {
                    BlockBegin pred = block.predAt(0);
                    BlockBegin sux = block.suxAt(0);

                    // prevent optimization of two consecutive blocks
                    if (!blockCompleted.get(pred.linearScanNumber()) && !blockCompleted.get(sux.linearScanNumber())) {
                        if (C1XOptions.TraceLinearScanLevel >= 3) {
                            TTY.println(" optimizing empty block B%d (pred: B%d, sux: B%d)", block.blockID, pred.blockID, sux.blockID);
                        }
                        blockCompleted.set(block.linearScanNumber());

                        // directly resolve between pred and sux (without looking at the empty block between)
                        resolveCollectMappings(pred, sux, moveResolver);
                        if (moveResolver.hasMappings()) {
                            moveResolver.setInsertPosition(block.lir(), 0);
                            moveResolver.resolveAndAppendMoves();
                        }
                    }
                }
            }
        }

        for (i = 0; i < numBlocks; i++) {
            if (!blockCompleted.get(i)) {
                BlockBegin fromBlock = blockAt(i);
                alreadyResolved.setFrom(blockCompleted);

                int numSux = fromBlock.numberOfSux();
                for (int s = 0; s < numSux; s++) {
                    BlockBegin toBlock = fromBlock.suxAt(s);

                    // check for duplicate edges between the same blocks (can happen with switch blocks)
                    if (!alreadyResolved.get(toBlock.linearScanNumber())) {
                        if (C1XOptions.TraceLinearScanLevel >= 3) {
                            TTY.println(" processing edge between B%d and B%d", fromBlock.blockID, toBlock.blockID);
                        }
                        alreadyResolved.set(toBlock.linearScanNumber());

                        // collect all intervals that have been split between fromBlock and toBlock
                        resolveCollectMappings(fromBlock, toBlock, moveResolver);
                        if (moveResolver.hasMappings()) {
                            resolveFindInsertPos(fromBlock, toBlock, moveResolver);
                            moveResolver.resolveAndAppendMoves();
                        }
                    }
                }
            }
        }
    }

    void resolveExceptionEntry(BlockBegin block, CiValue operand, MoveResolver moveResolver) {
        if (intervalFor(operand) == null) {
            // if a phi function is never used, no interval is created . ignore this
            return;
        }

        Interval interval = intervalAtBlockBegin(block, operand);
        CiValue location = interval.location();

        if (location.isRegister() && interval.alwaysInMemory()) {
            // the interval is split to get a short range that is located on the stack
            // in the following two cases:
            // * the interval started in memory (e.g. method parameter), but is currently in a register
            // this is an optimization for exception handling that reduces the number of moves that
            // are necessary for resolving the states when an exception uses this exception handler
            // * the interval would be on the fpu stack at the begin of the exception handler
            // this is not allowed because of the complicated fpu stack handling on Intel

            // range that will be spilled to memory
            int fromOpId = block.firstLirInstructionId();
            int toOpId = fromOpId + 1; // short live range of length 1
            assert interval.from() <= fromOpId && interval.to() >= toOpId : "no split allowed between exception entry and first instruction";

            if (interval.from() != fromOpId) {
                // the part before fromOpId is unchanged
                interval = interval.split(fromOpId, this);
                interval.assignLocation(location);
            }
            assert interval.from() == fromOpId : "must be true now";

            Interval spilledPart = interval;
            if (interval.to() != toOpId) {
                // the part after toOpId is unchanged
                spilledPart = interval.splitFromStart(toOpId, this);
                moveResolver.addMapping(spilledPart, interval);
            }
            assignSpillSlot(spilledPart);

            assert spilledPart.from() == fromOpId && spilledPart.to() == toOpId : "just checking";
        }
    }

    void resolveExceptionEntry(final BlockBegin block, final MoveResolver moveResolver) {
        assert block.checkBlockFlag(BlockBegin.BlockFlag.ExceptionEntry) : "should not call otherwise";
        assert moveResolver.checkEmpty();

        // visit all registers where the liveIn bit is set
        for (int operandNum = block.lirBlock.liveIn.nextSetBit(0); operandNum >= 0; operandNum = block.lirBlock.liveIn.nextSetBit(operandNum + 1)) {
            resolveExceptionEntry(block, operands.operandFor(operandNum), moveResolver);
        }

        // the liveIn bits are not set for phi functions of the xhandler entry, so iterate them separately
        block.stateBefore().forEachLivePhi(block, new PhiProcedure() {
            public boolean doPhi(Phi phi) {
                resolveExceptionEntry(block, phi.operand(), moveResolver);
                return true;
            }
        });

        if (moveResolver.hasMappings()) {
            // insert moves after first instruction
            moveResolver.setInsertPosition(block.lir(), 0);
            moveResolver.resolveAndAppendMoves();
        }
    }

    void resolveExceptionEdge(ExceptionHandler handler, int throwingOpId, CiValue operand, Phi phi, MoveResolver moveResolver) {
        if (intervalFor(operand) == null) {
            // if a phi function is never used, no interval is created . ignore this
            return;
        }

        // the computation of toInterval is equal to resolveCollectMappings,
        // but fromInterval is more complicated because of phi functions
        BlockBegin toBlock = handler.entryBlock();
        Interval toInterval = intervalAtBlockBegin(toBlock, operand);

        if (phi != null) {
            // phi function of the exception entry block
            // no moves are created for this phi function in the LIRGenerator, so the
            // interval at the throwing instruction must be searched using the operands
            // of the phi function
            Value fromValue = phi.inputAt(handler.phiOperand());
            Constant con = null;
            if (fromValue instanceof Constant) {
                con = (Constant) fromValue;
            }
            if (con != null && (con.operand().isIllegal() || con.operand().isConstant())) {
                // unpinned constants may have no register, so add mapping from constant to interval
                moveResolver.addMapping(con.asConstant(), toInterval);
            } else {
                // search split child at the throwing opId
                Interval fromInterval = intervalAtOpId(fromValue.operand(), throwingOpId);
                if (fromInterval != toInterval) {
                    moveResolver.addMapping(fromInterval, toInterval);
                    // with phi functions it can happen that the same fromValue is used in
                    // multiple mappings, so notify move-resolver that this is allowed
                    moveResolver.setMultipleReadsAllowed();
                }
            }
        } else {
            // no phi function, so use regNum also for fromInterval
            // search split child at the throwing opId
            Interval fromInterval = intervalAtOpId(operand, throwingOpId);
            if (fromInterval != toInterval) {
                // optimization to reduce number of moves: when toInterval is on stack and
                // the stack slot is known to be always correct, then no move is necessary
                if (!fromInterval.alwaysInMemory() || fromInterval.spillSlot() != toInterval.location()) {
                    moveResolver.addMapping(fromInterval, toInterval);
                }
            }
        }
    }

    void resolveExceptionEdge(final ExceptionHandler handler, final int throwingOpId, final MoveResolver moveResolver) {
        if (C1XOptions.TraceLinearScanLevel >= 4) {
            TTY.println("resolving exception handler B%d: throwingOpId=%d", handler.entryBlock().blockID, throwingOpId);
        }

        assert moveResolver.checkEmpty();
        assert handler.lirOpId() == -1 : "already processed this xhandler";
        handler.setLirOpId(throwingOpId);
        assert handler.entryCode() == null : "code already present";

        // visit all registers where the liveIn bit is set
        BlockBegin block = handler.entryBlock();
        for (int operandNum = block.lirBlock.liveIn.nextSetBit(0); operandNum >= 0; operandNum = block.lirBlock.liveIn.nextSetBit(operandNum + 1)) {
            resolveExceptionEdge(handler, throwingOpId, operands.operandFor(operandNum), null, moveResolver);
        }

        // the liveIn bits are not set for phi functions of the xhandler entry, so iterate them separately
        block.stateBefore().forEachLivePhi(block, new PhiProcedure() {
            public boolean doPhi(Phi phi) {
                resolveExceptionEdge(handler, throwingOpId, phi.operand(), phi, moveResolver);
                return true;
            }
        });

        if (moveResolver.hasMappings()) {
            LIRList entryCode = new LIRList(gen);
            moveResolver.setInsertPosition(entryCode, 0);
            moveResolver.resolveAndAppendMoves();

            entryCode.jump(handler.entryBlock());
            handler.setEntryCode(entryCode);
        }
    }

    void resolveExceptionHandlers() {
        MoveResolver moveResolver = new MoveResolver(this);
        //LIRVisitState visitor = new LIRVisitState();
        int numBlocks = blockCount();

        int i;
        for (i = 0; i < numBlocks; i++) {
            BlockBegin block = blockAt(i);
            if (block.checkBlockFlag(BlockFlag.ExceptionEntry)) {
                resolveExceptionEntry(block, moveResolver);
            }
        }

        for (i = 0; i < numBlocks; i++) {
            BlockBegin block = blockAt(i);
            LIRList ops = block.lir();
            int numOps = ops.length();

            // iterate all instructions of the block. skip the first because it is always a label
            assert !ops.at(0).hasOperands() : "first operation must always be a label";
            for (int j = 1; j < numOps; j++) {
                LIRInstruction op = ops.at(j);
                int opId = op.id;

                if (opId != -1 && op.info != null) {
                    // visit operation to collect all operands
                    for (ExceptionHandler handler : op.exceptionEdges()) {
                        resolveExceptionEdge(handler, opId, moveResolver);
                    }

                } else if (C1XOptions.DetailedAsserts) {
                    assert op.exceptionEdges().size() == 0 : "missed exception handler";
                }
            }
        }
    }

    // * Phase 7: assign register numbers back to LIR
    // (includes computation of debug information and oop maps)

    boolean verifyAssignedLocation(Interval interval, CiValue location) {
        CiKind kind = interval.kind();

        assert location.isRegister() || location.isStackSlot();

        if (location.isRegister()) {
            CiRegister reg = location.asRegister();

            // register
            switch (kind) {
                case Byte:
                case Char:
                case Short:
                case Jsr:
                case Word:
                case Object:
                case Int: {
                    assert reg.isCpu() : "not cpu register";
                    break;
                }

                case Long: {
                    assert reg.isCpu() : "not cpu register";
                    break;
                }

                case Float: {
                    assert !compilation.target.arch.isX86() || reg.isFpu() : "not xmm register: " + reg;
                    break;
                }

                case Double: {
                    assert !compilation.target.arch.isX86() || reg.isFpu() : "not xmm register: " + reg;
                    break;
                }

                default: {
                    throw Util.shouldNotReachHere();
                }
            }
        }
        return true;
    }

    CiStackSlot canonicalSpillOpr(Interval interval) {
        assert interval.spillSlot() != null : "canonical spill slot not set";
        return interval.spillSlot();
    }

    /**
     * Assigns the allocated location for an LIR instruction operand back into the instruction.
     *
     * @param operand an LIR instruction operand
     * @param opId the id of the LIR instruction using {@code operand}
     * @param mode the usage mode for {@code operand} by the instruction
     * @return the location assigned for the operand
     */
    private CiValue colorLirOperand(CiVariable operand, int opId, OperandMode mode) {
        Interval interval = intervalFor(operand);
        assert interval != null : "interval must exist";

        if (opId != -1) {
            if (C1XOptions.DetailedAsserts) {
                BlockBegin block = blockForId(opId);
                if (block.numberOfSux() <= 1 && opId == block.lastLirInstructionId()) {
                    // check if spill moves could have been appended at the end of this block, but
                    // before the branch instruction. So the split child information for this branch would
                    // be incorrect.
                    LIRInstruction instr = block.lir().instructionsList().get(block.lir().instructionsList().size() - 1);
                    if (instr instanceof LIRBranch) {
                        LIRBranch branch = (LIRBranch) instr;
                        if (block.lirBlock.liveOut.get(operandNumber(operand))) {
                            assert branch.cond() == Condition.TRUE : "block does not end with an unconditional jump";
                            throw new CiBailout("can't get split child for the last branch of a block because the information would be incorrect (moves are inserted before the branch in resolveDataFlow)");
                        }
                    }
                }
            }

            // operands are not changed when an interval is split during allocation,
            // so search the right interval here
            interval = splitChildAtOpId(interval, opId, mode);
        }

        return interval.location();
    }

    IntervalWalker initComputeOopMaps() {
        // setup lists of potential oops for walking
        Interval oopIntervals;
        Interval nonOopIntervals;

        oopIntervals = createUnhandledLists(IS_OOP_INTERVAL, null).first;

        // intervals that have no oops inside need not to be processed.
        // to ensure a walking until the last instruction id, add a dummy interval
        // with a high operation id
        nonOopIntervals = new Interval(CiValue.IllegalValue, -1);
        nonOopIntervals.addRange(Integer.MAX_VALUE - 2, Integer.MAX_VALUE - 1);

        return new IntervalWalker(this, oopIntervals, nonOopIntervals);
    }

    void computeOopMap(IntervalWalker iw, LIRInstruction op, LIRDebugInfo info, boolean isCallSite, CiBitMap frameRefMap, CiBitMap regRefMap) {
        if (C1XOptions.TraceLinearScanLevel >= 3) {
            TTY.println("creating oop map at opId %d", op.id);
        }

        // walk before the current operation . intervals that start at
        // the operation (i.e. output operands of the operation) are not
        // included in the oop map
        iw.walkBefore(op.id);

        // Iterate through active intervals
        for (Interval interval = iw.activeLists.get(RegisterBinding.Fixed); interval != Interval.EndMarker; interval = interval.next) {
            CiValue operand = interval.operand;

            assert interval.currentFrom() <= op.id && op.id <= interval.currentTo() : "interval should not be active otherwise";
            assert interval.operand.isVariable() : "fixed interval found";

            // Check if this range covers the instruction. Intervals that
            // start or end at the current operation are not included in the
            // oop map, except in the case of patching moves. For patching
            // moves, any intervals which end at this instruction are included
            // in the oop map since we may safepoint while doing the patch
            // before we've consumed the inputs.
            if (op.id < interval.currentTo()) {
                // caller-save registers must not be included into oop-maps at calls
                assert !isCallSite || !operand.isRegister() || !isCallerSave(operand) : "interval is in a caller-save register at a call . register will be overwritten";

                CiValue location = interval.location();
                if (location.isStackSlot()) {
                    location = frameMap.toStackAddress((CiStackSlot) location);
                }
                info.setOop(location, compilation, frameRefMap, regRefMap);

                // Spill optimization: when the stack value is guaranteed to be always correct,
                // then it must be added to the oop map even if the interval is currently in a register
                if (interval.alwaysInMemory() && op.id > interval.spillDefinitionPos() && !interval.location().equals(interval.spillSlot())) {
                    assert interval.spillDefinitionPos() > 0 : "position not set correctly";
                    assert interval.spillSlot() != null : "no spill slot assigned";
                    assert !interval.operand.isRegister() : "interval is on stack :  so stack slot is registered twice";
                    info.setOop(frameMap.toStackAddress(interval.spillSlot()), compilation, frameRefMap, regRefMap);
                }
            }
        }
    }

    private boolean isCallerSave(CiValue operand) {
        return attributes(operand.asRegister()).isCallerSave;
    }

    void computeOopMap(IntervalWalker iw, LIRInstruction op, LIRDebugInfo info, CiBitMap frameRefMap, CiBitMap regRefMap) {
        computeOopMap(iw, op, info, op.hasCall, frameRefMap, regRefMap);
        if (op instanceof LIRCall) {
            List<CiValue> pointerSlots = ((LIRCall) op).pointerSlots;
            if (pointerSlots != null) {
                for (CiValue v : pointerSlots) {
                    info.setOop(v, compilation, frameRefMap, regRefMap);
                }
            }
        } else if (op instanceof LIRXirInstruction) {
            List<CiValue> pointerSlots = ((LIRXirInstruction) op).pointerSlots;
            if (pointerSlots != null) {
                for (CiValue v : pointerSlots) {
                    info.setOop(v, compilation, frameRefMap, regRefMap);
                }
            }
        }
    }

    CiValue toCiValue(int opId, Value value) {
        if (value != null && value.operand() != CiValue.IllegalValue) {
            CiValue operand = value.operand();
            Constant con = null;
            if (value instanceof Constant) {
                con = (Constant) value;
            }

            assert con == null || operand.isVariable() || operand.isConstant() || operand.isIllegal() : "Constant instructions have only constant operands (or illegal if constant is optimized away)";

            if (con != null && !con.isLive() && !operand.isConstant()) {
                // Unpinned constants may have a variable operand for a part of the lifetime
                // or may be illegal when it was optimized away,
                // so always use a constant operand
                operand = con.asConstant();
            }

            if (operand.isVariable()) {
                OperandMode mode = OperandMode.Input;
                BlockBegin block = blockForId(opId);
                if (block.numberOfSux() == 1 && opId == block.lastLirInstructionId()) {
                    // generating debug information for the last instruction of a block.
                    // if this instruction is a branch, spill moves are inserted before this branch
                    // and so the wrong operand would be returned (spill moves at block boundaries are not
                    // considered in the live ranges of intervals)
                    // Solution: use the first opId of the branch target block instead.
                    final LIRInstruction instr = block.lir().instructionsList().get(block.lir().instructionsList().size() - 1);
                    if (instr instanceof LIRBranch) {
                        if (block.lirBlock.liveOut.get(operandNumber(operand))) {
                            opId = block.suxAt(0).firstLirInstructionId();
                            mode = OperandMode.Output;
                        }
                    }
                }

                // Get current location of operand
                // The operand must be live because debug information is considered when building the intervals
                // if the interval is not live, colorLirOperand will cause an assert on failure
                operand = colorLirOperand((CiVariable) operand, opId, mode);
                assert !hasCall(opId) || operand.isStackSlot() || !isCallerSave(operand) : "cannot have caller-save register operands at calls";
                return operand;
            } else if (operand.isRegister()) {
                assert value instanceof LoadRegister;
                return operand;
            } else {
                assert value instanceof Constant;
                assert operand.isConstant() : "operand must be constant";
                return operand;
            }
        } else {
            // return a dummy value because real value not needed
            return CiValue.IllegalValue;
        }
    }

    CiFrame computeFrameForState(int opId, FrameState state, CiBitMap frameRefMap) {
        CiFrame callerFrame = null;

        FrameState callerState = state.callerState();
        if (callerState != null) {
            // process recursively to compute outermost scope first
            callerFrame = computeFrameForState(opId, callerState, frameRefMap);
        }

        CiValue[] values = new CiValue[state.valuesSize() + state.locksSize()];
        int valueIndex = 0;

        for (int i = 0; i < state.valuesSize(); i++) {
            values[valueIndex++] = toCiValue(opId, state.valueAt(i));
        }

        for (int i = 0; i < state.locksSize(); i++) {
            if (compilation.runtime.sizeOfBasicObjectLock() != 0) {
                CiStackSlot monitorAddress = frameMap.toMonitorBaseStackAddress(i);
                values[valueIndex++] = monitorAddress;
                assert frameRefMap != null;
                CiStackSlot objectAddress = frameMap.toMonitorObjectStackAddress(i);
                LIRDebugInfo.setBit(frameRefMap, objectAddress.index());
            } else {
                Value lock = state.lockAt(i);
                if (lock.isConstant() && compilation.runtime.asJavaClass(lock.asConstant()) != null) {
                   // lock on class for synchronized static method
                   values[valueIndex++] = lock.asConstant();
                } else {
                   values[valueIndex++] = toCiValue(opId, lock);
                }
            }
        }

        return new CiFrame(callerFrame, state.scope().method, state.bci, values, state.localsSize(), state.stackSize(), state.locksSize());
    }

    private void computeDebugInfo(IntervalWalker iw, LIRInstruction op) {
        assert iw != null : "interval walker needed for debug information";
        computeDebugInfo(iw, op, op.info);

        if (op instanceof LIRXirInstruction) {
            LIRXirInstruction xir = (LIRXirInstruction) op;
            if (xir.infoAfter != null) {
                computeDebugInfo(iw, op, xir.infoAfter);
            }
        }
    }


    private void computeDebugInfo(IntervalWalker iw, LIRInstruction op, LIRDebugInfo info) {
        if (info != null) {
            if (info.debugInfo == null) {
                int frameSize = compilation.frameMap().frameSize();
                int frameWords = frameSize / compilation.target.spillSlotSize;
                CiBitMap frameRefMap = new CiBitMap(frameWords);
                CiBitMap regRefMap = !op.hasCall ? new CiBitMap(compilation.target.arch.registerReferenceMapBitCount) : null;
                CiFrame frame = compilation.placeholderState != null ? null : computeFrame(info.state, op.id, frameRefMap);
                computeOopMap(iw, op, info, frameRefMap, regRefMap);
                info.debugInfo = new CiDebugInfo(frame, regRefMap, frameRefMap);
            } else if (C1XOptions.DetailedAsserts) {
                assert info.debugInfo.frame().equals(computeFrame(info.state, op.id, new CiBitMap(info.debugInfo.frameRefMap.size())));
            }
        }
    }

    CiFrame computeFrame(FrameState state, int opId, CiBitMap frameRefMap) {
        if (C1XOptions.TraceLinearScanLevel >= 3) {
            TTY.println("creating debug information at opId %d", opId);
        }
        return computeFrameForState(opId, state, frameRefMap);
    }

    private void assignLocations(List<LIRInstruction> instructions, IntervalWalker iw) {
        int numInst = instructions.size();
        boolean hasDead = false;

        for (int j = 0; j < numInst; j++) {
            LIRInstruction op = instructions.get(j);
            if (op == null) { // this can happen when spill-moves are removed in eliminateSpillMoves
                hasDead = true;
                continue;
            }

            // iterate all modes of the visitor and process all virtual operands
            for (LIRInstruction.OperandMode mode : LIRInstruction.OPERAND_MODES) {
                int n = op.operandCount(mode);
                for (int k = 0; k < n; k++) {
                    CiValue operand = op.operandAt(mode, k);
                    if (operand.isVariable()) {
                        op.setOperandAt(mode, k, colorLirOperand((CiVariable) operand, op.id, mode));
                    }
                }
            }

            if (op.info != null) {
                // exception handling
                if (compilation.hasExceptionHandlers()) {
                    for (ExceptionHandler handler : op.exceptionEdges()) {
                        if (handler.entryCode() != null) {
                            assignLocations(handler.entryCode().instructionsList(), null);
                        }
                    }
                }

                // compute reference map and debug information
                computeDebugInfo(iw, op);
            }

            // make sure we haven't made the op invalid.
            assert op.verify();

            // remove useless moves
            if (op.code == LIROpcode.Move) {
                CiValue src = op.operand(0);
                CiValue dst = op.result();
                if (dst == src || src.equals(dst)) {
                    // TODO: what about o.f = o.f and exceptions?
                    instructions.set(j, null);
                    hasDead = true;
                }
            }
        }

        if (hasDead) {
            // iterate all instructions of the block and remove all null-values.
            int insertPoint = 0;
            for (int j = 0; j < numInst; j++) {
                LIRInstruction op = instructions.get(j);
                if (op != null) {
                    if (insertPoint != j) {
                        instructions.set(insertPoint, op);
                    }
                    insertPoint++;
                }
            }
            Util.truncate(instructions, insertPoint);
        }
    }

    private void assignLocations() {
        IntervalWalker iw = initComputeOopMaps();
        for (BlockBegin block : sortedBlocks) {
            assignLocations(block.lir().instructionsList(), iw);
        }
    }

    public void allocate() {
        if (C1XOptions.PrintTimers) {
            C1XTimers.LIFETIME_ANALYSIS.start();
        }

        numberInstructions();

        printLir("Before register allocation", true);

        computeLocalLiveSets();
        computeGlobalLiveSets();

        buildIntervals();
        sortIntervalsBeforeAllocation();

        if (C1XOptions.PrintTimers) {
            C1XTimers.LIFETIME_ANALYSIS.stop();
            C1XTimers.LINEAR_SCAN.start();
        }

        printIntervals("Before register allocation");

        allocateRegisters();

        if (C1XOptions.PrintTimers) {
            C1XTimers.LINEAR_SCAN.stop();
            C1XTimers.RESOLUTION.start();
        }

        resolveDataFlow();
        if (compilation.hasExceptionHandlers()) {
            resolveExceptionHandlers();
        }

        if (C1XOptions.PrintTimers) {
            C1XTimers.RESOLUTION.stop();
            C1XTimers.DEBUG_INFO.start();
        }

        C1XMetrics.LSRASpills += (maxSpills - frameMap.initialSpillSlot());

        // fill in number of spill slots into frameMap
        frameMap.finalizeFrame(maxSpills);

        printIntervals("After register allocation");
        printLir("After register allocation", true);

        sortIntervalsAfterAllocation();

        if (C1XOptions.DetailedAsserts) {
            verify();
        }

        eliminateSpillMoves();
        assignLocations();

        if (C1XOptions.DetailedAsserts) {
            verifyIntervals();
        }

        if (C1XOptions.PrintTimers) {
            C1XTimers.DEBUG_INFO.stop();
            C1XTimers.CODE_CREATE.start();
        }

        printLir("After register number assignment", true);

        EdgeMoveOptimizer.optimize(ir.linearScanOrder());
        if (C1XOptions.OptControlFlow) {
            ControlFlowOptimizer.optimize(ir);
        }

        printLir("After control flow optimization", false);
    }

    void printIntervals(String label) {
        if (C1XOptions.TraceLinearScanLevel >= 1) {
            int i;
            TTY.println();
            TTY.println(label);

            for (Interval interval : intervals) {
                if (interval != null) {
                    TTY.out().println(interval.logString(this));
                }
            }

            TTY.println();
            TTY.println("--- Basic Blocks ---");
            for (i = 0; i < blockCount(); i++) {
                BlockBegin block = blockAt(i);
                TTY.print("B%d [%d, %d, %d, %d] ", block.blockID, block.firstLirInstructionId(), block.lastLirInstructionId(), block.loopIndex(), block.loopDepth());
            }
            TTY.println();
            TTY.println();
        }

        if (compilation.compiler.isObserved()) {
            compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, label, this, intervals, intervalsSize));
        }
    }

    void printLir(String label, boolean hirValid) {
        if (C1XOptions.TraceLinearScanLevel >= 1 && !TTY.isSuppressed()) {
            TTY.println();
            TTY.println(label);
            LIRList.printLIR(ir.linearScanOrder());
            TTY.println();
        }

        if (compilation.compiler.isObserved()) {
            compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, label, compilation.hir().startBlock, hirValid, true));
        }
    }

    boolean verify() {
        // (check that all intervals have a correct register and that no registers are overwritten)
        if (C1XOptions.TraceLinearScanLevel >= 2) {
            TTY.println(" verifying intervals *");
        }
        verifyIntervals();

        if (C1XOptions.TraceLinearScanLevel >= 2) {
            TTY.println(" verifying that no oops are in fixed intervals *");
        }
        //verifyNoOopsInFixedIntervals();

        if (C1XOptions.TraceLinearScanLevel >= 2) {
            TTY.println(" verifying that unpinned constants are not alive across block boundaries");
        }
        verifyConstants();

        if (C1XOptions.TraceLinearScanLevel >= 2) {
            TTY.println(" verifying register allocation *");
        }
        verifyRegisters();

        if (C1XOptions.TraceLinearScanLevel >= 2) {
            TTY.println(" no errors found *");
        }

        return true;
    }

    private void verifyRegisters() {
        RegisterVerifier verifier = new RegisterVerifier(this);
        verifier.verify(blockAt(0));
    }

    void verifyIntervals() {
        int len = intervalsSize;

        for (int i = 0; i < len; i++) {
            Interval i1 = intervals[i];
            if (i1 == null) {
                continue;
            }

            i1.checkSplitChildren();

            if (i1.operandNumber != i) {
                TTY.println("Interval %d is on position %d in list", i1.operandNumber, i);
                TTY.println(i1.logString(this));
                throw new CiBailout("");
            }

            if (i1.operand.isVariable() && i1.kind() == CiKind.Illegal) {
                TTY.println("Interval %d has no type assigned", i1.operandNumber);
                TTY.println(i1.logString(this));
                throw new CiBailout("");
            }

            if (i1.location() == null) {
                TTY.println("Interval %d has no register assigned", i1.operandNumber);
                TTY.println(i1.logString(this));
                throw new CiBailout("");
            }

            if (!isProcessed(i1.location())) {
                TTY.println("Can not have an Interval for an ignored register " + i1.location());
                TTY.println(i1.logString(this));
                throw new CiBailout("");
            }

            if (i1.first() == Range.EndMarker) {
                TTY.println("Interval %d has no Range", i1.operandNumber);
                TTY.println(i1.logString(this));
                throw new CiBailout("");
            }

            for (Range r = i1.first(); r != Range.EndMarker; r = r.next) {
                if (r.from >= r.to) {
                    TTY.println("Interval %d has zero length range", i1.operandNumber);
                    TTY.println(i1.logString(this));
                    throw new CiBailout("");
                }
            }

            for (int j = i + 1; j < len; j++) {
                Interval i2 = intervals[j];
                if (i2 == null) {
                    continue;
                }

                // special intervals that are created in MoveResolver
                // . ignore them because the range information has no meaning there
                if (i1.from() == 1 && i1.to() == 2) {
                    continue;
                }
                if (i2.from() == 1 && i2.to() == 2) {
                    continue;
                }
                CiValue l1 = i1.location();
                CiValue l2 = i2.location();
                if (i1.intersects(i2) && (l1.equals(l2))) {
                    if (C1XOptions.DetailedAsserts) {
                        TTY.println("Intervals %d and %d overlap and have the same register assigned", i1.operandNumber, i2.operandNumber);
                        TTY.println(i1.logString(this));
                        TTY.println(i2.logString(this));
                    }
                    throw new CiBailout("");
                }
            }
        }
    }

    void verifyNoOopsInFixedIntervals() {
        Interval fixedIntervals;
        Interval otherIntervals;
        fixedIntervals = createUnhandledLists(IS_PRECOLORED_INTERVAL, null).first;
        // to ensure a walking until the last instruction id, add a dummy interval
        // with a high operation id
        otherIntervals = new Interval(CiValue.IllegalValue, -1);
        otherIntervals.addRange(Integer.MAX_VALUE - 2, Integer.MAX_VALUE - 1);
        IntervalWalker iw = new IntervalWalker(this, fixedIntervals, otherIntervals);

        for (int i = 0; i < blockCount(); i++) {
            BlockBegin block = blockAt(i);

            List<LIRInstruction> instructions = block.lir().instructionsList();

            for (int j = 0; j < instructions.size(); j++) {
                LIRInstruction op = instructions.get(j);

                if (op.info != null) {
                    iw.walkBefore(op.id);
                    boolean checkLive = true;

                    // Make sure none of the fixed registers is live across an
                    // oopmap since we can't handle that correctly.
                    if (checkLive) {
                        for (Interval interval = iw.activeLists.get(RegisterBinding.Fixed); interval != Interval.EndMarker; interval = interval.next) {
                            if (interval.currentTo() > op.id + 1) {
                                // This interval is live out of this op so make sure
                                // that this interval represents some value that's
                                // referenced by this op either as an input or output.
                                boolean ok = false;
                                for (LIRInstruction.OperandMode mode : LIRInstruction.OPERAND_MODES) {
                                    int n = op.operandCount(mode);
                                    for (int k = 0; k < n; k++) {
                                        CiValue operand = op.operandAt(mode, k);
                                        if (operand.isRegister()) {
                                            if (intervalFor(operand) == interval) {
                                                ok = true;
                                                break;
                                            }
                                        }
                                    }
                                }
                                assert ok : "fixed intervals should never be live across an oopmap point";
                            }
                        }
                    }
                }
            }
        }
    }

    void verifyConstants() {
        int numBlocks = blockCount();

        for (int i = 0; i < numBlocks; i++) {
            BlockBegin block = blockAt(i);
            CiBitMap liveAtEdge = block.lirBlock.liveIn;

            // visit all operands where the liveAtEdge bit is set
            for (int operandNum = liveAtEdge.nextSetBit(0); operandNum >= 0; operandNum = liveAtEdge.nextSetBit(operandNum + 1)) {
                if (C1XOptions.TraceLinearScanLevel >= 4) {
                    TTY.println("checking interval %d of block B%d", operandNum, block.blockID);
                }
                CiValue operand = operands.operandFor(operandNum);
                assert operand.isVariable() : "value must have variable operand";
                Value value = gen.operands.instructionForResult(((CiVariable) operand));
                assert value != null : "all intervals live across block boundaries must have Value";
                // TKR assert value.asConstant() == null || value.isPinned() :
                // "only pinned constants can be alive accross block boundaries";
            }
        }
    }

    public int numberOfSpillSlots(CiKind kind) {
        return compilation.target.spillSlots(kind);
    }
}
