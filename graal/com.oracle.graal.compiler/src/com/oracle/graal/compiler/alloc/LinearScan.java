/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.alloc;

import static com.oracle.graal.api.code.CodeUtil.*;
import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.compiler.GraalDebugConfig.*;
import static com.oracle.graal.compiler.common.cfg.AbstractControlFlowGraph.*;
import static com.oracle.graal.lir.LIRValueUtil.*;

import java.util.*;

import com.oracle.graal.alloc.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.alloc.Interval.RegisterBinding;
import com.oracle.graal.compiler.alloc.Interval.RegisterPriority;
import com.oracle.graal.compiler.alloc.Interval.SpillState;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRInstruction.OperandMode;
import com.oracle.graal.lir.StandardOp.MoveOp;
import com.oracle.graal.nodes.*;
import com.oracle.graal.options.*;
import com.oracle.graal.phases.util.*;

/**
 * An implementation of the linear scan register allocator algorithm described in <a
 * href="http://doi.acm.org/10.1145/1064979.1064998"
 * >"Optimized Interval Splitting in a Linear Scan Register Allocator"</a> by Christian Wimmer and
 * Hanspeter Moessenboeck.
 */
public final class LinearScan {

    final TargetDescription target;
    final LIR ir;
    final FrameMap frameMap;
    final RegisterAttributes[] registerAttributes;
    final Register[] registers;

    boolean callKillsRegisters;

    public static final int DOMINATOR_SPILL_MOVE_ID = -2;
    private static final int SPLIT_INTERVALS_CAPACITY_RIGHT_SHIFT = 1;

    public static class Options {
        // @formatter:off
        @Option(help = "Enable spill position optimization")
        public static final OptionValue<Boolean> LSRAOptimizeSpillPosition = new OptionValue<>(true);
        // @formatter:on
    }

    public static class BlockData {

        /**
         * Bit map specifying which operands are live upon entry to this block. These are values
         * used in this block or any of its successors where such value are not defined in this
         * block. The bit index of an operand is its {@linkplain LinearScan#operandNumber(Value)
         * operand number}.
         */
        public BitSet liveIn;

        /**
         * Bit map specifying which operands are live upon exit from this block. These are values
         * used in a successor block that are either defined in this block or were live upon entry
         * to this block. The bit index of an operand is its
         * {@linkplain LinearScan#operandNumber(Value) operand number}.
         */
        public BitSet liveOut;

        /**
         * Bit map specifying which operands are used (before being defined) in this block. That is,
         * these are the values that are live upon entry to the block. The bit index of an operand
         * is its {@linkplain LinearScan#operandNumber(Value) operand number}.
         */
        public BitSet liveGen;

        /**
         * Bit map specifying which operands are defined/overwritten in this block. The bit index of
         * an operand is its {@linkplain LinearScan#operandNumber(Value) operand number}.
         */
        public BitSet liveKill;
    }

    public final BlockMap<BlockData> blockData;

    /**
     * List of blocks in linear-scan order. This is only correct as long as the CFG does not change.
     */
    final List<? extends AbstractBlock<?>> sortedBlocks;

    /**
     * Map from {@linkplain #operandNumber(Value) operand numbers} to intervals.
     */
    Interval[] intervals;

    /**
     * The number of valid entries in {@link #intervals}.
     */
    int intervalsSize;

    /**
     * The index of the first entry in {@link #intervals} for a
     * {@linkplain #createDerivedInterval(Interval) derived interval}.
     */
    int firstDerivedIntervalIndex = -1;

    /**
     * Intervals sorted by {@link Interval#from()}.
     */
    Interval[] sortedIntervals;

    /**
     * Map from an instruction {@linkplain LIRInstruction#id id} to the instruction. Entries should
     * be retrieved with {@link #instructionForId(int)} as the id is not simply an index into this
     * array.
     */
    LIRInstruction[] opIdToInstructionMap;

    /**
     * Map from an instruction {@linkplain LIRInstruction#id id} to the {@linkplain AbstractBlock
     * block} containing the instruction. Entries should be retrieved with {@link #blockForId(int)}
     * as the id is not simply an index into this array.
     */
    AbstractBlock<?>[] opIdToBlockMap;

    /**
     * Bit set for each variable that is contained in each loop.
     */
    BitMap2D intervalInLoop;

    /**
     * The {@linkplain #operandNumber(Value) number} of the first variable operand allocated.
     */
    private final int firstVariableNumber;

    public LinearScan(TargetDescription target, LIR ir, FrameMap frameMap) {
        this.target = target;
        this.ir = ir;
        this.frameMap = frameMap;
        this.sortedBlocks = ir.linearScanOrder();
        this.registerAttributes = frameMap.registerConfig.getAttributesMap();

        this.registers = target.arch.getRegisters();
        this.firstVariableNumber = registers.length;
        this.blockData = new BlockMap<>(ir.getControlFlowGraph());
    }

    public int getFirstLirInstructionId(AbstractBlock<?> block) {
        int result = ir.getLIRforBlock(block).get(0).id();
        assert result >= 0;
        return result;
    }

    public int getLastLirInstructionId(AbstractBlock<?> block) {
        List<LIRInstruction> instructions = ir.getLIRforBlock(block);
        int result = instructions.get(instructions.size() - 1).id();
        assert result >= 0;
        return result;
    }

    public static boolean isVariableOrRegister(Value value) {
        return isVariable(value) || isRegister(value);
    }

    /**
     * Converts an operand (variable or register) to an index in a flat address space covering all
     * the {@linkplain Variable variables} and {@linkplain RegisterValue registers} being processed
     * by this allocator.
     */
    private int operandNumber(Value operand) {
        if (isRegister(operand)) {
            int number = asRegister(operand).number;
            assert number < firstVariableNumber;
            return number;
        }
        assert isVariable(operand) : operand;
        return firstVariableNumber + ((Variable) operand).index;
    }

    /**
     * Gets the number of operands. This value will increase by 1 for new variable.
     */
    private int operandSize() {
        return firstVariableNumber + ir.numVariables();
    }

    /**
     * Gets the highest operand number for a register operand. This value will never change.
     */
    public int maxRegisterNumber() {
        return firstVariableNumber - 1;
    }

    static final IntervalPredicate IS_PRECOLORED_INTERVAL = new IntervalPredicate() {

        @Override
        public boolean apply(Interval i) {
            return isRegister(i.operand);
        }
    };

    static final IntervalPredicate IS_VARIABLE_INTERVAL = new IntervalPredicate() {

        @Override
        public boolean apply(Interval i) {
            return isVariable(i.operand);
        }
    };

    static final IntervalPredicate IS_STACK_INTERVAL = new IntervalPredicate() {

        @Override
        public boolean apply(Interval i) {
            return !isRegister(i.operand);
        }
    };

    /**
     * Gets an object describing the attributes of a given register according to this register
     * configuration.
     */
    RegisterAttributes attributes(Register reg) {
        return registerAttributes[reg.number];
    }

    void assignSpillSlot(Interval interval) {
        // assign the canonical spill slot of the parent (if a part of the interval
        // is already spilled) or allocate a new spill slot
        if (interval.canMaterialize()) {
            interval.assignLocation(Value.ILLEGAL);
        } else if (interval.spillSlot() != null) {
            interval.assignLocation(interval.spillSlot());
        } else {
            StackSlot slot = frameMap.allocateSpillSlot(interval.kind());
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
    Interval createInterval(AllocatableValue operand) {
        assert isLegal(operand);
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
            intervals = Arrays.copyOf(intervals, intervals.length + (intervals.length >> SPLIT_INTERVALS_CAPACITY_RIGHT_SHIFT));
        }
        intervalsSize++;
        Variable variable = new Variable(source.kind(), ir.nextVariable());

        Interval interval = createInterval(variable);
        assert intervals[intervalsSize - 1] == interval;
        return interval;
    }

    // access to block list (sorted in linear scan order)
    int blockCount() {
        return sortedBlocks.size();
    }

    AbstractBlock<?> blockAt(int index) {
        return sortedBlocks.get(index);
    }

    /**
     * Gets the size of the {@link BlockData#liveIn} and {@link BlockData#liveOut} sets for a basic
     * block. These sets do not include any operands allocated as a result of creating
     * {@linkplain #createDerivedInterval(Interval) derived intervals}.
     */
    int liveSetSize() {
        return firstDerivedIntervalIndex == -1 ? operandSize() : firstDerivedIntervalIndex;
    }

    int numLoops() {
        return ir.getControlFlowGraph().getLoops().size();
    }

    boolean isIntervalInLoop(int interval, int loop) {
        return intervalInLoop.at(interval, loop);
    }

    Interval intervalFor(int operandNumber) {
        return intervals[operandNumber];
    }

    Interval intervalFor(Value operand) {
        int operandNumber = operandNumber(operand);
        assert operandNumber < intervalsSize;
        return intervals[operandNumber];
    }

    Interval getOrCreateInterval(AllocatableValue operand) {
        Interval ret = intervalFor(operand);
        if (ret == null) {
            return createInterval(operand);
        } else {
            return ret;
        }
    }

    /**
     * Gets the highest instruction id allocated by this object.
     */
    int maxOpId() {
        assert opIdToInstructionMap.length > 0 : "no operations";
        return (opIdToInstructionMap.length - 1) << 1;
    }

    /**
     * Converts an {@linkplain LIRInstruction#id instruction id} to an instruction index. All LIR
     * instructions in a method have an index one greater than their linear-scan order predecesor
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
        assert instr.id() == opId;
        return instr;
    }

    /**
     * Gets the block containing a given instruction.
     *
     * @param opId an instruction {@linkplain LIRInstruction#id id}
     * @return the block containing the instruction denoted by {@code opId}
     */
    AbstractBlock<?> blockForId(int opId) {
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
     * @return {@code true} if the instruction denoted by {@code id} destroys all caller saved
     *         registers.
     */
    boolean hasCall(int opId) {
        assert isEven(opId) : "opId not even";
        return instructionForId(opId).destroysCallerSavedRegisters();
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
                if (defPos < interval.spillDefinitionPos() - 2) {
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
                throw new BailoutException("other states not allowed at this time");
        }
    }

    // called during register allocation
    void changeSpillState(Interval interval, int spillPos) {
        switch (interval.spillState()) {
            case NoSpillStore: {
                int defLoopDepth = blockForId(interval.spillDefinitionPos()).getLoopDepth();
                int spillLoopDepth = blockForId(spillPos).getLoopDepth();

                if (defLoopDepth < spillLoopDepth) {
                    // the loop depth of the spilling position is higher then the loop depth
                    // at the definition of the interval . move write to memory out of loop.
                    if (Options.LSRAOptimizeSpillPosition.getValue()) {
                        // find best spill position in dominator the tree
                        interval.setSpillState(SpillState.SpillInDominator);
                    } else {
                        // store at definition of the interval
                        interval.setSpillState(SpillState.StoreAtDefinition);
                    }
                } else {
                    // the interval is currently spilled only once, so for now there is no
                    // reason to store the interval at the definition
                    interval.setSpillState(SpillState.OneSpillStore);
                }
                break;
            }

            case OneSpillStore: {
                if (Options.LSRAOptimizeSpillPosition.getValue()) {
                    // the interval is spilled more then once
                    interval.setSpillState(SpillState.SpillInDominator);
                } else {
                    // it is better to store it to
                    // memory at the definition
                    interval.setSpillState(SpillState.StoreAtDefinition);
                }
                break;
            }

            case SpillInDominator:
            case StoreAtDefinition:
            case StartInMemory:
            case NoOptimization:
            case NoDefinitionFound:
                // nothing to do
                break;

            default:
                throw new BailoutException("other states not allowed at this time");
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
        try (Indent indent = Debug.logAndIndent("Eliminating unnecessary spill moves")) {

            // collect all intervals that must be stored after their definition.
            // the list is sorted by Interval.spillDefinitionPos
            Interval interval;
            interval = createUnhandledLists(mustStoreAtDefinition, null).first;
            if (DetailedAsserts.getValue()) {
                checkIntervals(interval);
            }

            LIRInsertionBuffer insertionBuffer = new LIRInsertionBuffer();
            for (AbstractBlock<?> block : sortedBlocks) {
                List<LIRInstruction> instructions = ir.getLIRforBlock(block);
                int numInst = instructions.size();

                // iterate all instructions of the block. skip the first
                // because it is always a label
                for (int j = 1; j < numInst; j++) {
                    LIRInstruction op = instructions.get(j);
                    int opId = op.id();

                    if (opId == -1) {
                        MoveOp move = (MoveOp) op;
                        // remove move from register to stack if the stack slot is guaranteed to be
                        // correct.
                        // only moves that have been inserted by LinearScan can be removed.
                        assert isVariable(move.getResult()) : "LinearScan inserts only moves to variables";

                        Interval curInterval = intervalFor(move.getResult());

                        if (!isRegister(curInterval.location()) && curInterval.alwaysInMemory()) {
                            // move target is a stack slot that is always correct, so eliminate
                            // instruction
                            if (Debug.isLogEnabled()) {
                                Debug.log("eliminating move from interval %d to %d", operandNumber(move.getInput()), operandNumber(move.getResult()));
                            }
                            // null-instructions are deleted by assignRegNum
                            instructions.set(j, null);
                        }

                    } else {
                        // insert move from register to stack just after
                        // the beginning of the interval
                        assert interval == Interval.EndMarker || interval.spillDefinitionPos() >= opId : "invalid order";
                        assert interval == Interval.EndMarker || (interval.isSplitParent() && interval.spillState() == SpillState.StoreAtDefinition) : "invalid interval";

                        while (interval != Interval.EndMarker && interval.spillDefinitionPos() == opId) {
                            if (!interval.canMaterialize()) {
                                if (!insertionBuffer.initialized()) {
                                    // prepare insertion buffer (appended when all instructions in
                                    // the block are processed)
                                    insertionBuffer.init(instructions);
                                }

                                AllocatableValue fromLocation = interval.location();
                                AllocatableValue toLocation = canonicalSpillOpr(interval);

                                assert isRegister(fromLocation) : "from operand must be a register but is: " + fromLocation + " toLocation=" + toLocation + " spillState=" + interval.spillState();
                                assert isStackSlot(toLocation) : "to operand must be a stack slot";

                                insertionBuffer.append(j + 1, ir.getSpillMoveFactory().createMove(toLocation, fromLocation));

                                Debug.log("inserting move after definition of interval %d to stack slot %s at opId %d", interval.operandNumber, interval.spillSlot(), opId);
                            }
                            interval = interval.next;
                        }
                    }
                } // end of instruction iteration

                if (insertionBuffer.initialized()) {
                    insertionBuffer.finish();
                }
            } // end of block iteration

            assert interval == Interval.EndMarker : "missed an interval";
        }
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

            Debug.log("interval %d (from %d to %d) must be stored at %d", temp.operandNumber, temp.from(), temp.to(), temp.spillDefinitionPos());

            prev = temp;
            temp = temp.next;
        }
    }

    /**
     * Numbers all instructions in all blocks. The numbering follows the
     * {@linkplain ComputeBlockOrder linear scan order}.
     */
    void numberInstructions() {

        intervalsSize = operandSize();
        intervals = new Interval[intervalsSize + (intervalsSize >> SPLIT_INTERVALS_CAPACITY_RIGHT_SHIFT)];

        ValueConsumer setVariableConsumer = new ValueConsumer() {

            @Override
            public void visitValue(Value value) {
                if (isVariable(value)) {
                    getOrCreateInterval(asVariable(value));
                }
            }
        };

        // Assign IDs to LIR nodes and build a mapping, lirOps, from ID to LIRInstruction node.
        int numInstructions = 0;
        for (AbstractBlock<?> block : sortedBlocks) {
            numInstructions += ir.getLIRforBlock(block).size();
        }

        // initialize with correct length
        opIdToInstructionMap = new LIRInstruction[numInstructions];
        opIdToBlockMap = new AbstractBlock<?>[numInstructions];

        int opId = 0;
        int index = 0;
        for (AbstractBlock<?> block : sortedBlocks) {
            blockData.put(block, new BlockData());

            List<LIRInstruction> instructions = ir.getLIRforBlock(block);

            int numInst = instructions.size();
            for (int j = 0; j < numInst; j++) {
                LIRInstruction op = instructions.get(j);
                op.setId(opId);

                opIdToInstructionMap[index] = op;
                opIdToBlockMap[index] = block;
                assert instructionForId(opId) == op : "must match";

                op.visitEachTemp(setVariableConsumer);
                op.visitEachOutput(setVariableConsumer);

                index++;
                opId += 2; // numbering of lirOps by two
            }
        }
        assert index == numInstructions : "must match";
        assert (index << 1) == opId : "must match: " + (index << 1);
    }

    /**
     * Computes local live sets (i.e. {@link BlockData#liveGen} and {@link BlockData#liveKill})
     * separately for each block.
     */
    void computeLocalLiveSets() {
        int liveSize = liveSetSize();

        intervalInLoop = new BitMap2D(operandSize(), numLoops());

        // iterate all blocks
        for (final AbstractBlock<?> block : sortedBlocks) {
            try (Indent indent = Debug.logAndIndent("compute local live sets for block %d", block.getId())) {

                final BitSet liveGen = new BitSet(liveSize);
                final BitSet liveKill = new BitSet(liveSize);

                List<LIRInstruction> instructions = ir.getLIRforBlock(block);
                int numInst = instructions.size();

                ValueConsumer useConsumer = new ValueConsumer() {

                    @Override
                    public void visitValue(Value operand) {
                        if (isVariable(operand)) {
                            int operandNum = operandNumber(operand);
                            if (!liveKill.get(operandNum)) {
                                liveGen.set(operandNum);
                                Debug.log("liveGen for operand %d", operandNum);
                            }
                            if (block.getLoop() != null) {
                                intervalInLoop.setBit(operandNum, block.getLoop().getIndex());
                            }
                        }

                        if (DetailedAsserts.getValue()) {
                            verifyInput(block, liveKill, operand);
                        }
                    }
                };
                ValueConsumer stateConsumer = new ValueConsumer() {

                    @Override
                    public void visitValue(Value operand) {
                        int operandNum = operandNumber(operand);
                        if (!liveKill.get(operandNum)) {
                            liveGen.set(operandNum);
                            Debug.log("liveGen in state for operand %d", operandNum);
                        }
                    }
                };
                ValueConsumer defConsumer = new ValueConsumer() {

                    @Override
                    public void visitValue(Value operand) {
                        if (isVariable(operand)) {
                            int varNum = operandNumber(operand);
                            liveKill.set(varNum);
                            Debug.log("liveKill for operand %d", varNum);
                            if (block.getLoop() != null) {
                                intervalInLoop.setBit(varNum, block.getLoop().getIndex());
                            }
                        }

                        if (DetailedAsserts.getValue()) {
                            // fixed intervals are never live at block boundaries, so
                            // they need not be processed in live sets
                            // process them only in debug mode so that this can be checked
                            verifyTemp(liveKill, operand);
                        }
                    }
                };

                // iterate all instructions of the block
                for (int j = 0; j < numInst; j++) {
                    final LIRInstruction op = instructions.get(j);

                    try (Indent indent2 = Debug.logAndIndent("handle op %d", op.id())) {
                        op.visitEachInput(useConsumer);
                        op.visitEachAlive(useConsumer);
                        // Add uses of live locals from interpreter's point of view for proper debug
                        // information generation
                        op.visitEachState(stateConsumer);
                        op.visitEachTemp(defConsumer);
                        op.visitEachOutput(defConsumer);
                    }
                } // end of instruction iteration

                BlockData blockSets = blockData.get(block);
                blockSets.liveGen = liveGen;
                blockSets.liveKill = liveKill;
                blockSets.liveIn = new BitSet(liveSize);
                blockSets.liveOut = new BitSet(liveSize);

                Debug.log("liveGen  B%d %s", block.getId(), blockSets.liveGen);
                Debug.log("liveKill B%d %s", block.getId(), blockSets.liveKill);

            }
        } // end of block iteration
    }

    private void verifyTemp(BitSet liveKill, Value operand) {
        // fixed intervals are never live at block boundaries, so
        // they need not be processed in live sets
        // process them only in debug mode so that this can be checked
        if (isRegister(operand)) {
            if (isProcessed(operand)) {
                liveKill.set(operandNumber(operand));
            }
        }
    }

    private void verifyInput(AbstractBlock<?> block, BitSet liveKill, Value operand) {
        // fixed intervals are never live at block boundaries, so
        // they need not be processed in live sets.
        // this is checked by these assertions to be sure about it.
        // the entry block may have incoming
        // values in registers, which is ok.
        if (isRegister(operand) && block != ir.getControlFlowGraph().getStartBlock()) {
            if (isProcessed(operand)) {
                assert liveKill.get(operandNumber(operand)) : "using fixed register that is not defined in this block";
            }
        }
    }

    /**
     * Performs a backward dataflow analysis to compute global live sets (i.e.
     * {@link BlockData#liveIn} and {@link BlockData#liveOut}) for each block.
     */
    void computeGlobalLiveSets() {
        try (Indent indent = Debug.logAndIndent("compute global live sets")) {
            int numBlocks = blockCount();
            boolean changeOccurred;
            boolean changeOccurredInBlock;
            int iterationCount = 0;
            BitSet liveOut = new BitSet(liveSetSize()); // scratch set for calculations

            // Perform a backward dataflow analysis to compute liveOut and liveIn for each block.
            // The loop is executed until a fixpoint is reached (no changes in an iteration)
            do {
                changeOccurred = false;

                try (Indent indent2 = Debug.logAndIndent("new iteration %d", iterationCount)) {

                    // iterate all blocks in reverse order
                    for (int i = numBlocks - 1; i >= 0; i--) {
                        AbstractBlock<?> block = blockAt(i);
                        BlockData blockSets = blockData.get(block);

                        changeOccurredInBlock = false;

                        // liveOut(block) is the union of liveIn(sux), for successors sux of block
                        int n = block.getSuccessorCount();
                        if (n > 0) {
                            liveOut.clear();
                            // block has successors
                            if (n > 0) {
                                for (AbstractBlock<?> successor : block.getSuccessors()) {
                                    liveOut.or(blockData.get(successor).liveIn);
                                }
                            }

                            if (!blockSets.liveOut.equals(liveOut)) {
                                // A change occurred. Swap the old and new live out
                                // sets to avoid copying.
                                BitSet temp = blockSets.liveOut;
                                blockSets.liveOut = liveOut;
                                liveOut = temp;

                                changeOccurred = true;
                                changeOccurredInBlock = true;
                            }
                        }

                        if (iterationCount == 0 || changeOccurredInBlock) {
                            // liveIn(block) is the union of liveGen(block) with (liveOut(block) &
                            // !liveKill(block))
                            // note: liveIn has to be computed only in first iteration
                            // or if liveOut has changed!
                            BitSet liveIn = blockSets.liveIn;
                            liveIn.clear();
                            liveIn.or(blockSets.liveOut);
                            liveIn.andNot(blockSets.liveKill);
                            liveIn.or(blockSets.liveGen);

                            Debug.log("block %d: livein = %s,  liveout = %s", block.getId(), liveIn, blockSets.liveOut);
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
            AbstractBlock<?> startBlock = ir.getControlFlowGraph().getStartBlock();
            if (blockData.get(startBlock).liveIn.cardinality() != 0) {
                if (DetailedAsserts.getValue()) {
                    reportFailure(numBlocks);
                }
                // bailout if this occurs in product mode.
                throw new GraalInternalError("liveIn set of first block must be empty: " + blockData.get(startBlock).liveIn);
            }
        }
    }

    private static NodeLIRBuilder getNodeLIRGeneratorFromDebugContext() {
        if (Debug.isEnabled()) {
            NodeLIRBuilder lirGen = Debug.contextLookup(NodeLIRBuilder.class);
            assert lirGen != null;
            return lirGen;
        }
        return null;
    }

    private static ValueNode getValueForOperandFromDebugContext(Value value) {
        NodeLIRBuilder gen = getNodeLIRGeneratorFromDebugContext();
        if (gen != null) {
            return gen.valueForOperand(value);
        }
        return null;
    }

    private void reportFailure(int numBlocks) {
        try (Scope s = Debug.forceLog()) {
            try (Indent indent = Debug.logAndIndent("report failure")) {

                BitSet startBlockLiveIn = blockData.get(ir.getControlFlowGraph().getStartBlock()).liveIn;
                try (Indent indent2 = Debug.logAndIndent("Error: liveIn set of first block must be empty (when this fails, variables are used before they are defined):")) {
                    for (int operandNum = startBlockLiveIn.nextSetBit(0); operandNum >= 0; operandNum = startBlockLiveIn.nextSetBit(operandNum + 1)) {
                        Interval interval = intervalFor(operandNum);
                        if (interval != null) {
                            Value operand = interval.operand;
                            Debug.log("var %d; operand=%s; node=%s", operandNum, operand, getValueForOperandFromDebugContext(operand));
                        } else {
                            Debug.log("var %d; missing operand", operandNum);
                        }
                    }
                }

                // print some additional information to simplify debugging
                for (int operandNum = startBlockLiveIn.nextSetBit(0); operandNum >= 0; operandNum = startBlockLiveIn.nextSetBit(operandNum + 1)) {
                    Interval interval = intervalFor(operandNum);
                    Value operand = null;
                    ValueNode valueForOperandFromDebugContext = null;
                    if (interval != null) {
                        operand = interval.operand;
                        valueForOperandFromDebugContext = getValueForOperandFromDebugContext(operand);
                    }
                    try (Indent indent2 = Debug.logAndIndent("---- Detailed information for var %d; operand=%s; node=%s ----", operandNum, operand, valueForOperandFromDebugContext)) {

                        Deque<AbstractBlock<?>> definedIn = new ArrayDeque<>();
                        HashSet<AbstractBlock<?>> usedIn = new HashSet<>();
                        for (AbstractBlock<?> block : sortedBlocks) {
                            if (blockData.get(block).liveGen.get(operandNum)) {
                                usedIn.add(block);
                                try (Indent indent3 = Debug.logAndIndent("used in block B%d", block.getId())) {
                                    for (LIRInstruction ins : ir.getLIRforBlock(block)) {
                                        try (Indent indent4 = Debug.logAndIndent("%d: %s", ins.id(), ins)) {
                                            ins.forEachState(new ValueProcedure() {

                                                @Override
                                                public Value doValue(Value liveStateOperand, OperandMode mode, EnumSet<OperandFlag> flags) {
                                                    Debug.log("operand=%s", liveStateOperand);
                                                    return liveStateOperand;
                                                }
                                            });
                                        }
                                    }
                                }
                            }
                            if (blockData.get(block).liveKill.get(operandNum)) {
                                definedIn.add(block);
                                try (Indent indent3 = Debug.logAndIndent("defined in block B%d", block.getId())) {
                                    for (LIRInstruction ins : ir.getLIRforBlock(block)) {
                                        Debug.log("%d: %s", ins.id(), ins);
                                    }
                                }
                            }
                        }

                        int[] hitCount = new int[numBlocks];

                        while (!definedIn.isEmpty()) {
                            AbstractBlock<?> block = definedIn.removeFirst();
                            usedIn.remove(block);
                            for (AbstractBlock<?> successor : block.getSuccessors()) {
                                if (successor.isLoopHeader()) {
                                    if (!block.isLoopEnd()) {
                                        definedIn.add(successor);
                                    }
                                } else {
                                    if (++hitCount[successor.getId()] == successor.getPredecessorCount()) {
                                        definedIn.add(successor);
                                    }
                                }
                            }
                        }
                        try (Indent indent3 = Debug.logAndIndent("**** offending usages are in: ")) {
                            for (AbstractBlock<?> block : usedIn) {
                                Debug.log("B%d", block.getId());
                            }
                        }
                    }
                }
            }
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    private void verifyLiveness() {
        // check that fixed intervals are not live at block boundaries
        // (live set must be empty at fixed intervals)
        for (AbstractBlock<?> block : sortedBlocks) {
            for (int j = 0; j <= maxRegisterNumber(); j++) {
                assert !blockData.get(block).liveIn.get(j) : "liveIn  set of fixed register must be empty";
                assert !blockData.get(block).liveOut.get(j) : "liveOut set of fixed register must be empty";
                assert !blockData.get(block).liveGen.get(j) : "liveGen set of fixed register must be empty";
            }
        }
    }

    void addUse(AllocatableValue operand, int from, int to, RegisterPriority registerPriority, LIRKind kind) {
        if (!isProcessed(operand)) {
            return;
        }

        Interval interval = getOrCreateInterval(operand);
        if (!kind.equals(LIRKind.Illegal)) {
            interval.setKind(kind);
        }

        interval.addRange(from, to);

        // Register use position at even instruction id.
        interval.addUsePos(to & ~1, registerPriority);

        Debug.log("add use: %s, from %d to %d (%s)", interval, from, to, registerPriority.name());
    }

    void addTemp(AllocatableValue operand, int tempPos, RegisterPriority registerPriority, LIRKind kind) {
        if (!isProcessed(operand)) {
            return;
        }

        Interval interval = getOrCreateInterval(operand);
        if (!kind.equals(LIRKind.Illegal)) {
            interval.setKind(kind);
        }

        interval.addRange(tempPos, tempPos + 1);
        interval.addUsePos(tempPos, registerPriority);
        interval.addMaterializationValue(null);

        Debug.log("add temp: %s tempPos %d (%s)", interval, tempPos, RegisterPriority.MustHaveRegister.name());
    }

    boolean isProcessed(Value operand) {
        return !isRegister(operand) || attributes(asRegister(operand)).isAllocatable();
    }

    void addDef(AllocatableValue operand, LIRInstruction op, RegisterPriority registerPriority, LIRKind kind) {
        if (!isProcessed(operand)) {
            return;
        }
        int defPos = op.id();

        Interval interval = getOrCreateInterval(operand);
        if (!kind.equals(LIRKind.Illegal)) {
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
            Debug.log("Warning: def of operand %s at %d occurs without use", operand, defPos);
        }

        changeSpillDefinitionPos(interval, defPos);
        if (registerPriority == RegisterPriority.None && interval.spillState().ordinal() <= SpillState.StartInMemory.ordinal()) {
            // detection of method-parameters and roundfp-results
            interval.setSpillState(SpillState.StartInMemory);
        }
        interval.addMaterializationValue(LinearScan.getMaterializedValue(op, operand, interval));

        Debug.log("add def: %s defPos %d (%s)", interval, defPos, registerPriority.name());
    }

    /**
     * Determines the register priority for an instruction's output/result operand.
     */
    static RegisterPriority registerPriorityOfOutputOperand(LIRInstruction op) {
        if (op instanceof MoveOp) {
            MoveOp move = (MoveOp) op;
            if (optimizeMethodArgument(move.getInput())) {
                return RegisterPriority.None;
            }
        }

        // all other operands require a register
        return RegisterPriority.MustHaveRegister;
    }

    /**
     * Determines the priority which with an instruction's input operand will be allocated a
     * register.
     */
    static RegisterPriority registerPriorityOfInputOperand(EnumSet<OperandFlag> flags) {
        if (flags.contains(OperandFlag.STACK)) {
            return RegisterPriority.ShouldHaveRegister;
        }
        // all other operands require a register
        return RegisterPriority.MustHaveRegister;
    }

    private static boolean optimizeMethodArgument(Value value) {
        /*
         * Object method arguments that are passed on the stack are currently not optimized because
         * this requires that the runtime visits method arguments during stack walking.
         */
        return isStackSlot(value) && asStackSlot(value).isInCallerFrame() && value.getKind() != Kind.Object;
    }

    /**
     * Optimizes moves related to incoming stack based arguments. The interval for the destination
     * of such moves is assigned the stack slot (which is in the caller's frame) as its spill slot.
     */
    void handleMethodArguments(LIRInstruction op) {
        if (op instanceof MoveOp) {
            MoveOp move = (MoveOp) op;
            if (optimizeMethodArgument(move.getInput())) {
                StackSlot slot = asStackSlot(move.getInput());
                if (DetailedAsserts.getValue()) {
                    assert op.id() > 0 : "invalid id";
                    assert blockForId(op.id()).getPredecessorCount() == 0 : "move from stack must be in first block";
                    assert isVariable(move.getResult()) : "result of move must be a variable";

                    Debug.log("found move from stack slot %s to %s", slot, move.getResult());
                }

                Interval interval = intervalFor(move.getResult());
                interval.setSpillSlot(slot);
                interval.assignLocation(slot);
            }
        }
    }

    void addRegisterHint(final LIRInstruction op, final Value targetValue, OperandMode mode, EnumSet<OperandFlag> flags, final boolean hintAtDef) {
        if (flags.contains(OperandFlag.HINT) && isVariableOrRegister(targetValue)) {

            op.forEachRegisterHint(targetValue, mode, new ValueProcedure() {

                @Override
                public Value doValue(Value registerHint, OperandMode valueMode, EnumSet<OperandFlag> valueFlags) {
                    if (isVariableOrRegister(registerHint)) {
                        Interval from = getOrCreateInterval((AllocatableValue) registerHint);
                        Interval to = getOrCreateInterval((AllocatableValue) targetValue);

                        // hints always point from def to use
                        if (hintAtDef) {
                            to.setLocationHint(from);
                        } else {
                            from.setLocationHint(to);
                        }
                        Debug.log("operation at opId %d: added hint from interval %d to %d", op.id(), from.operandNumber, to.operandNumber);

                        return registerHint;
                    }
                    return null;
                }
            });
        }
    }

    void buildIntervals() {

        try (Indent indent = Debug.logAndIndent("build intervals")) {
            InstructionValueConsumer outputConsumer = new InstructionValueConsumer() {

                @Override
                public void visitValue(LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                    if (isVariableOrRegister(operand)) {
                        addDef((AllocatableValue) operand, op, registerPriorityOfOutputOperand(op), operand.getLIRKind());
                        addRegisterHint(op, operand, mode, flags, true);
                    }
                }
            };

            InstructionValueConsumer tempConsumer = new InstructionValueConsumer() {

                @Override
                public void visitValue(LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                    if (isVariableOrRegister(operand)) {
                        addTemp((AllocatableValue) operand, op.id(), RegisterPriority.MustHaveRegister, operand.getLIRKind());
                        addRegisterHint(op, operand, mode, flags, false);
                    }
                }
            };

            InstructionValueConsumer aliveConsumer = new InstructionValueConsumer() {

                @Override
                public void visitValue(LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                    if (isVariableOrRegister(operand)) {
                        RegisterPriority p = registerPriorityOfInputOperand(flags);
                        final int opId = op.id();
                        final int blockFrom = getFirstLirInstructionId((blockForId(opId)));
                        addUse((AllocatableValue) operand, blockFrom, opId + 1, p, operand.getLIRKind());
                        addRegisterHint(op, operand, mode, flags, false);
                    }
                }
            };

            InstructionValueConsumer inputConsumer = new InstructionValueConsumer() {

                @Override
                public void visitValue(LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                    if (isVariableOrRegister(operand)) {
                        final int opId = op.id();
                        final int blockFrom = getFirstLirInstructionId((blockForId(opId)));
                        RegisterPriority p = registerPriorityOfInputOperand(flags);
                        addUse((AllocatableValue) operand, blockFrom, opId, p, operand.getLIRKind());
                        addRegisterHint(op, operand, mode, flags, false);
                    }
                }
            };

            InstructionValueConsumer stateProc = new InstructionValueConsumer() {

                @Override
                public void visitValue(LIRInstruction op, Value operand) {
                    final int opId = op.id();
                    final int blockFrom = getFirstLirInstructionId((blockForId(opId)));
                    addUse((AllocatableValue) operand, blockFrom, opId + 1, RegisterPriority.None, operand.getLIRKind());
                }
            };

            // create a list with all caller-save registers (cpu, fpu, xmm)
            Register[] callerSaveRegs = frameMap.registerConfig.getCallerSaveRegisters();

            // iterate all blocks in reverse order
            for (int i = blockCount() - 1; i >= 0; i--) {

                AbstractBlock<?> block = blockAt(i);
                try (Indent indent2 = Debug.logAndIndent("handle block %d", block.getId())) {

                    List<LIRInstruction> instructions = ir.getLIRforBlock(block);
                    final int blockFrom = getFirstLirInstructionId(block);
                    int blockTo = getLastLirInstructionId(block);

                    assert blockFrom == instructions.get(0).id();
                    assert blockTo == instructions.get(instructions.size() - 1).id();

                    // Update intervals for operands live at the end of this block;
                    BitSet live = blockData.get(block).liveOut;
                    for (int operandNum = live.nextSetBit(0); operandNum >= 0; operandNum = live.nextSetBit(operandNum + 1)) {
                        assert live.get(operandNum) : "should not stop here otherwise";
                        AllocatableValue operand = intervalFor(operandNum).operand;
                        Debug.log("live in %d: %s", operandNum, operand);

                        addUse(operand, blockFrom, blockTo + 2, RegisterPriority.None, LIRKind.Illegal);

                        // add special use positions for loop-end blocks when the
                        // interval is used anywhere inside this loop. It's possible
                        // that the block was part of a non-natural loop, so it might
                        // have an invalid loop index.
                        if (block.isLoopEnd() && block.getLoop() != null && isIntervalInLoop(operandNum, block.getLoop().getIndex())) {
                            intervalFor(operandNum).addUsePos(blockTo + 1, RegisterPriority.LiveAtLoopEnd);
                        }
                    }

                    // iterate all instructions of the block in reverse order.
                    // definitions of intervals are processed before uses
                    for (int j = instructions.size() - 1; j >= 0; j--) {
                        final LIRInstruction op = instructions.get(j);
                        final int opId = op.id();

                        try (Indent indent3 = Debug.logAndIndent("handle inst %d: %s", opId, op)) {

                            // add a temp range for each register if operation destroys
                            // caller-save registers
                            if (op.destroysCallerSavedRegisters()) {
                                for (Register r : callerSaveRegs) {
                                    if (attributes(r).isAllocatable()) {
                                        addTemp(r.asValue(), opId, RegisterPriority.None, LIRKind.Illegal);
                                    }
                                }
                                Debug.log("operation destroys all caller-save registers");
                            }

                            op.visitEachOutput(outputConsumer);
                            op.visitEachTemp(tempConsumer);
                            op.visitEachAlive(aliveConsumer);
                            op.visitEachInput(inputConsumer);

                            // Add uses of live locals from interpreter's point of view for proper
                            // debug information generation
                            // Treat these operands as temp values (if the live range is extended
                            // to a call site, the value would be in a register at
                            // the call otherwise)
                            op.visitEachState(stateProc);

                            // special steps for some instructions (especially moves)
                            handleMethodArguments(op);

                        }

                    } // end of instruction iteration
                }
            } // end of block iteration

            // add the range [0, 1] to all fixed intervals.
            // the register allocator need not handle unhandled fixed intervals
            for (Interval interval : intervals) {
                if (interval != null && isRegister(interval.operand)) {
                    interval.addRange(0, 1);
                }
            }
        }
    }

    // * Phase 5: actual register allocation

    private static boolean isSorted(Interval[] intervals) {
        int from = -1;
        for (Interval interval : intervals) {
            assert interval != null;
            assert from <= interval.from();
            from = interval.from();
        }
        return true;
    }

    static Interval addToList(Interval first, Interval prev, Interval interval) {
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
        Arrays.sort(newList, (Interval a, Interval b) -> a.from() - b.from());

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

    public void allocateRegisters() {
        try (Indent indent = Debug.logAndIndent("allocate registers")) {
            Interval precoloredIntervals;
            Interval notPrecoloredIntervals;

            Interval.Pair result = createUnhandledLists(IS_PRECOLORED_INTERVAL, IS_VARIABLE_INTERVAL);
            precoloredIntervals = result.first;
            notPrecoloredIntervals = result.second;

            // allocate cpu registers
            LinearScanWalker lsw;
            if (OptimizingLinearScanWalker.Options.LSRAOptimization.getValue()) {
                lsw = new OptimizingLinearScanWalker(this, precoloredIntervals, notPrecoloredIntervals);
            } else {
                lsw = new LinearScanWalker(this, precoloredIntervals, notPrecoloredIntervals);
            }
            lsw.walk();
            lsw.finishAllocation();
        }
    }

    // * Phase 6: resolve data flow
    // (insert moves at edges between blocks if intervals have been split)

    // wrapper for Interval.splitChildAtOpId that performs a bailout in product mode
    // instead of returning null
    Interval splitChildAtOpId(Interval interval, int opId, LIRInstruction.OperandMode mode) {
        Interval result = interval.getSplitChildAtOpId(opId, mode, this);

        if (result != null) {
            Debug.log("Split child at pos %d of interval %s is %s", opId, interval, result);
            return result;
        }

        throw new BailoutException("LinearScan: interval is null");
    }

    Interval intervalAtBlockBegin(AbstractBlock<?> block, int operandNumber) {
        return splitChildAtOpId(intervalFor(operandNumber), getFirstLirInstructionId(block), LIRInstruction.OperandMode.DEF);
    }

    Interval intervalAtBlockEnd(AbstractBlock<?> block, int operandNumber) {
        return splitChildAtOpId(intervalFor(operandNumber), getLastLirInstructionId(block) + 1, LIRInstruction.OperandMode.DEF);
    }

    void resolveCollectMappings(AbstractBlock<?> fromBlock, AbstractBlock<?> toBlock, MoveResolver moveResolver) {
        assert moveResolver.checkEmpty();

        int numOperands = operandSize();
        BitSet liveAtEdge = blockData.get(toBlock).liveIn;

        // visit all variables for which the liveAtEdge bit is set
        for (int operandNum = liveAtEdge.nextSetBit(0); operandNum >= 0; operandNum = liveAtEdge.nextSetBit(operandNum + 1)) {
            assert operandNum < numOperands : "live information set for not exisiting interval";
            assert blockData.get(fromBlock).liveOut.get(operandNum) && blockData.get(toBlock).liveIn.get(operandNum) : "interval not live at this edge";

            Interval fromInterval = intervalAtBlockEnd(fromBlock, operandNum);
            Interval toInterval = intervalAtBlockBegin(toBlock, operandNum);

            if (fromInterval != toInterval && !fromInterval.location().equals(toInterval.location())) {
                // need to insert move instruction
                moveResolver.addMapping(fromInterval, toInterval);
            }
        }
    }

    void resolveFindInsertPos(AbstractBlock<?> fromBlock, AbstractBlock<?> toBlock, MoveResolver moveResolver) {
        if (fromBlock.getSuccessorCount() <= 1) {
            Debug.log("inserting moves at end of fromBlock B%d", fromBlock.getId());

            List<LIRInstruction> instructions = ir.getLIRforBlock(fromBlock);
            LIRInstruction instr = instructions.get(instructions.size() - 1);
            if (instr instanceof StandardOp.JumpOp) {
                // insert moves before branch
                moveResolver.setInsertPosition(instructions, instructions.size() - 1);
            } else {
                moveResolver.setInsertPosition(instructions, instructions.size());
            }

        } else {
            Debug.log("inserting moves at beginning of toBlock B%d", toBlock.getId());

            if (DetailedAsserts.getValue()) {
                assert ir.getLIRforBlock(fromBlock).get(0) instanceof StandardOp.LabelOp : "block does not start with a label";

                // because the number of predecessor edges matches the number of
                // successor edges, blocks which are reached by switch statements
                // may have be more than one predecessor but it will be guaranteed
                // that all predecessors will be the same.
                for (AbstractBlock<?> predecessor : toBlock.getPredecessors()) {
                    assert fromBlock == predecessor : "all critical edges must be broken";
                }
            }

            moveResolver.setInsertPosition(ir.getLIRforBlock(toBlock), 1);
        }
    }

    /**
     * Inserts necessary moves (spilling or reloading) at edges between blocks for intervals that
     * have been split.
     */
    void resolveDataFlow() {
        try (Indent indent = Debug.logAndIndent("resolve data flow")) {

            int numBlocks = blockCount();
            MoveResolver moveResolver = new MoveResolver(this);
            BitSet blockCompleted = new BitSet(numBlocks);
            BitSet alreadyResolved = new BitSet(numBlocks);

            for (AbstractBlock<?> block : sortedBlocks) {

                // check if block has only one predecessor and only one successor
                if (block.getPredecessorCount() == 1 && block.getSuccessorCount() == 1) {
                    List<LIRInstruction> instructions = ir.getLIRforBlock(block);
                    assert instructions.get(0) instanceof StandardOp.LabelOp : "block must start with label";
                    assert instructions.get(instructions.size() - 1) instanceof StandardOp.JumpOp : "block with successor must end with unconditional jump";

                    // check if block is empty (only label and branch)
                    if (instructions.size() == 2) {
                        AbstractBlock<?> pred = block.getPredecessors().iterator().next();
                        AbstractBlock<?> sux = block.getSuccessors().iterator().next();

                        // prevent optimization of two consecutive blocks
                        if (!blockCompleted.get(pred.getLinearScanNumber()) && !blockCompleted.get(sux.getLinearScanNumber())) {
                            Debug.log(" optimizing empty block B%d (pred: B%d, sux: B%d)", block.getId(), pred.getId(), sux.getId());

                            blockCompleted.set(block.getLinearScanNumber());

                            // directly resolve between pred and sux (without looking
                            // at the empty block
                            // between)
                            resolveCollectMappings(pred, sux, moveResolver);
                            if (moveResolver.hasMappings()) {
                                moveResolver.setInsertPosition(instructions, 1);
                                moveResolver.resolveAndAppendMoves();
                            }
                        }
                    }
                }
            }

            for (AbstractBlock<?> fromBlock : sortedBlocks) {
                if (!blockCompleted.get(fromBlock.getLinearScanNumber())) {
                    alreadyResolved.clear();
                    alreadyResolved.or(blockCompleted);

                    for (AbstractBlock<?> toBlock : fromBlock.getSuccessors()) {

                        // check for duplicate edges between the same blocks (can happen with switch
                        // blocks)
                        if (!alreadyResolved.get(toBlock.getLinearScanNumber())) {
                            Debug.log("processing edge between B%d and B%d", fromBlock.getId(), toBlock.getId());

                            alreadyResolved.set(toBlock.getLinearScanNumber());

                            // collect all intervals that have been split between
                            // fromBlock and toBlock
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
    }

    // * Phase 7: assign register numbers back to LIR
    // (includes computation of debug information and oop maps)

    static StackSlot canonicalSpillOpr(Interval interval) {
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
    private Value colorLirOperand(Variable operand, int opId, OperandMode mode) {
        Interval interval = intervalFor(operand);
        assert interval != null : "interval must exist";

        if (opId != -1) {
            if (DetailedAsserts.getValue()) {
                AbstractBlock<?> block = blockForId(opId);
                if (block.getSuccessorCount() <= 1 && opId == getLastLirInstructionId(block)) {
                    // check if spill moves could have been appended at the end of this block, but
                    // before the branch instruction. So the split child information for this branch
                    // would
                    // be incorrect.
                    LIRInstruction instr = ir.getLIRforBlock(block).get(ir.getLIRforBlock(block).size() - 1);
                    if (instr instanceof StandardOp.JumpOp) {
                        if (blockData.get(block).liveOut.get(operandNumber(operand))) {
                            assert false : "can't get split child for the last branch of a block because the information would be incorrect (moves are inserted before the branch in resolveDataFlow)";
                        }
                    }
                }
            }

            // operands are not changed when an interval is split during allocation,
            // so search the right interval here
            interval = splitChildAtOpId(interval, opId, mode);
        }

        if (isIllegal(interval.location()) && interval.canMaterialize()) {
            assert mode != OperandMode.DEF;
            return interval.getMaterializedValue();
        }
        return interval.location();
    }

    private boolean isMaterialized(AllocatableValue operand, int opId, OperandMode mode) {
        Interval interval = intervalFor(operand);
        assert interval != null : "interval must exist";

        if (opId != -1) {
            // operands are not changed when an interval is split during allocation,
            // so search the right interval here
            interval = splitChildAtOpId(interval, opId, mode);
        }

        return isIllegal(interval.location()) && interval.canMaterialize();
    }

    protected IntervalWalker initIntervalWalker(IntervalPredicate predicate) {
        // setup lists of potential oops for walking
        Interval oopIntervals;
        Interval nonOopIntervals;

        oopIntervals = createUnhandledLists(predicate, null).first;

        // intervals that have no oops inside need not to be processed.
        // to ensure a walking until the last instruction id, add a dummy interval
        // with a high operation id
        nonOopIntervals = new Interval(Value.ILLEGAL, -1);
        nonOopIntervals.addRange(Integer.MAX_VALUE - 2, Integer.MAX_VALUE - 1);

        return new IntervalWalker(this, oopIntervals, nonOopIntervals);
    }

    /**
     * Visits all intervals for a frame state. The frame state use this information to build the OOP
     * maps.
     */
    void markFrameLocations(IntervalWalker iw, LIRInstruction op, LIRFrameState info) {
        Debug.log("creating oop map at opId %d", op.id());

        // walk before the current operation . intervals that start at
        // the operation (i.e. output operands of the operation) are not
        // included in the oop map
        iw.walkBefore(op.id());

        // TODO(je) we could pass this as parameter
        AbstractBlock<?> block = blockForId(op.id());

        // Iterate through active intervals
        for (Interval interval = iw.activeLists.get(RegisterBinding.Fixed); interval != Interval.EndMarker; interval = interval.next) {
            Value operand = interval.operand;

            assert interval.currentFrom() <= op.id() && op.id() <= interval.currentTo() : "interval should not be active otherwise";
            assert isVariable(interval.operand) : "fixed interval found";

            // Check if this range covers the instruction. Intervals that
            // start or end at the current operation are not included in the
            // oop map, except in the case of patching moves. For patching
            // moves, any intervals which end at this instruction are included
            // in the oop map since we may safepoint while doing the patch
            // before we've consumed the inputs.
            if (op.id() < interval.currentTo() && !isIllegal(interval.location())) {
                // caller-save registers must not be included into oop-maps at calls
                assert !op.destroysCallerSavedRegisters() || !isRegister(operand) || !isCallerSave(operand) : "interval is in a caller-save register at a call . register will be overwritten";

                info.markLocation(interval.location(), frameMap);

                // Spill optimization: when the stack value is guaranteed to be always correct,
                // then it must be added to the oop map even if the interval is currently in a
                // register
                int spillPos = interval.spillDefinitionPos();
                if (interval.spillState() != SpillState.SpillInDominator) {
                    if (interval.alwaysInMemory() && op.id() > interval.spillDefinitionPos() && !interval.location().equals(interval.spillSlot())) {
                        assert interval.spillDefinitionPos() > 0 : "position not set correctly";
                        assert spillPos > 0 : "position not set correctly";
                        assert interval.spillSlot() != null : "no spill slot assigned";
                        assert !isRegister(interval.operand) : "interval is on stack :  so stack slot is registered twice";
                        info.markLocation(interval.spillSlot(), frameMap);
                    }
                } else {
                    AbstractBlock<?> spillBlock = blockForId(spillPos);
                    if (interval.alwaysInMemory() && !interval.location().equals(interval.spillSlot())) {
                        if ((spillBlock.equals(block) && op.id() > spillPos) || dominates(spillBlock, block)) {
                            assert spillPos > 0 : "position not set correctly";
                            assert interval.spillSlot() != null : "no spill slot assigned";
                            assert !isRegister(interval.operand) : "interval is on stack :  so stack slot is registered twice";
                            info.markLocation(interval.spillSlot(), frameMap);
                        }
                    }
                }
            }
        }
    }

    private boolean isCallerSave(Value operand) {
        return attributes(asRegister(operand)).isCallerSave();
    }

    private InstructionValueProcedure debugInfoProc = new InstructionValueProcedure() {

        @Override
        public Value doValue(LIRInstruction op, Value operand, OperandMode valueMode, EnumSet<OperandFlag> flags) {
            int tempOpId = op.id();
            OperandMode mode = OperandMode.USE;
            AbstractBlock<?> block = blockForId(tempOpId);
            if (block.getSuccessorCount() == 1 && tempOpId == getLastLirInstructionId(block)) {
                // generating debug information for the last instruction of a block.
                // if this instruction is a branch, spill moves are inserted before this branch
                // and so the wrong operand would be returned (spill moves at block boundaries
                // are not
                // considered in the live ranges of intervals)
                // Solution: use the first opId of the branch target block instead.
                final LIRInstruction instr = ir.getLIRforBlock(block).get(ir.getLIRforBlock(block).size() - 1);
                if (instr instanceof StandardOp.JumpOp) {
                    if (blockData.get(block).liveOut.get(operandNumber(operand))) {
                        tempOpId = getFirstLirInstructionId(block.getSuccessors().iterator().next());
                        mode = OperandMode.DEF;
                    }
                }
            }

            // Get current location of operand
            // The operand must be live because debug information is considered when building
            // the intervals
            // if the interval is not live, colorLirOperand will cause an assert on failure
            Value result = colorLirOperand((Variable) operand, tempOpId, mode);
            assert !hasCall(tempOpId) || isStackSlot(result) || isConstant(result) || !isCallerSave(result) : "cannot have caller-save register operands at calls";
            return result;
        }
    };

    private void computeDebugInfo(IntervalWalker iw, final LIRInstruction op, LIRFrameState info) {
        info.initDebugInfo(frameMap, !op.destroysCallerSavedRegisters() || !callKillsRegisters);
        markFrameLocations(iw, op, info);

        info.forEachState(op, debugInfoProc);
        info.finish(op, frameMap);
    }

    private void assignLocations(List<LIRInstruction> instructions, final IntervalWalker iw) {
        int numInst = instructions.size();
        boolean hasDead = false;

        InstructionValueProcedure assignProc = new InstructionValueProcedure() {

            @Override
            public Value doValue(LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                if (isVariable(operand)) {
                    return colorLirOperand((Variable) operand, op.id(), mode);
                }
                return operand;
            }
        };
        InstructionStateProcedure stateProc = new InstructionStateProcedure() {

            @Override
            public void doState(LIRInstruction op, LIRFrameState state) {
                computeDebugInfo(iw, op, state);
            }
        };

        for (int j = 0; j < numInst; j++) {
            final LIRInstruction op = instructions.get(j);
            if (op == null) { // this can happen when spill-moves are removed in eliminateSpillMoves
                hasDead = true;
                continue;
            }

            // remove useless moves
            MoveOp move = null;
            if (op instanceof MoveOp) {
                move = (MoveOp) op;
                AllocatableValue result = move.getResult();
                if (isVariable(result) && isMaterialized(result, op.id(), OperandMode.DEF)) {
                    /*
                     * This happens if a materializable interval is originally not spilled but then
                     * kicked out in LinearScanWalker.splitForSpilling(). When kicking out such an
                     * interval this move operation was already generated.
                     */
                    instructions.set(j, null);
                    hasDead = true;
                    continue;
                }
            }

            op.forEachInput(assignProc);
            op.forEachAlive(assignProc);
            op.forEachTemp(assignProc);
            op.forEachOutput(assignProc);

            // compute reference map and debug information
            op.forEachState(stateProc);

            // remove useless moves
            if (move != null) {
                if (move.getInput().equals(move.getResult())) {
                    instructions.set(j, null);
                    hasDead = true;
                }
            }
        }

        if (hasDead) {
            // Remove null values from the list.
            instructions.removeAll(Collections.singleton(null));
        }
    }

    private void assignLocations() {
        IntervalWalker iw = initIntervalWalker(IS_STACK_INTERVAL);
        try (Indent indent = Debug.logAndIndent("assign locations")) {
            for (AbstractBlock<?> block : sortedBlocks) {
                try (Indent indent2 = Debug.logAndIndent("assign locations in block B%d", block.getId())) {
                    assignLocations(ir.getLIRforBlock(block), iw);
                }
            }
        }
    }

    public static void allocate(TargetDescription target, LIR lir, FrameMap frameMap) {
        new LinearScan(target, lir, frameMap).allocate();
    }

    private void allocate() {

        /*
         * This is the point to enable debug logging for the whole register allocation.
         */
        try (Indent indent = Debug.logAndIndent("LinearScan allocate")) {

            try (Scope s = Debug.scope("LifetimeAnalysis")) {
                numberInstructions();
                printLir("Before register allocation", true);
                computeLocalLiveSets();
                computeGlobalLiveSets();
                buildIntervals();
                sortIntervalsBeforeAllocation();
            } catch (Throwable e) {
                throw Debug.handle(e);
            }

            try (Scope s = Debug.scope("RegisterAllocation")) {
                printIntervals("Before register allocation");
                allocateRegisters();
            } catch (Throwable e) {
                throw Debug.handle(e);
            }

            if (Options.LSRAOptimizeSpillPosition.getValue()) {
                try (Scope s = Debug.scope("OptimizeSpillPosition")) {
                    optimizeSpillPosition();
                } catch (Throwable e) {
                    throw Debug.handle(e);
                }
            }

            try (Scope s = Debug.scope("ResolveDataFlow")) {
                resolveDataFlow();
            } catch (Throwable e) {
                throw Debug.handle(e);
            }

            try (Scope s = Debug.scope("DebugInfo")) {
                frameMap.finish();

                printIntervals("After register allocation");
                printLir("After register allocation", true);

                sortIntervalsAfterAllocation();

                if (DetailedAsserts.getValue()) {
                    verify();
                }

                try (Scope s1 = Debug.scope("EliminateSpillMove")) {
                    eliminateSpillMoves();
                } catch (Throwable e) {
                    throw Debug.handle(e);
                }
                printLir("After spill move elimination", true);

                try (Scope s1 = Debug.scope("AssignLocations")) {
                    assignLocations();
                } catch (Throwable e) {
                    throw Debug.handle(e);
                }

                if (DetailedAsserts.getValue()) {
                    verifyIntervals();
                }
            } catch (Throwable e) {
                throw Debug.handle(e);
            }

            printLir("After register number assignment", true);
        }
    }

    private DebugMetric betterSpillPos = Debug.metric("BetterSpillPosition");
    private DebugMetric betterSpillPosWithLowerProbability = Debug.metric("BetterSpillPositionWithLowerProbability");

    private void optimizeSpillPosition() {
        LIRInsertionBuffer[] insertionBuffers = new LIRInsertionBuffer[ir.linearScanOrder().size()];
        for (Interval interval : intervals) {
            if (interval != null && interval.isSplitParent() && interval.spillState() == SpillState.SpillInDominator) {
                AbstractBlock<?> defBlock = blockForId(interval.spillDefinitionPos());
                AbstractBlock<?> spillBlock = null;
                Interval firstSpillChild = null;
                try (Indent indent = Debug.logAndIndent("interval %s (%s)", interval, defBlock)) {
                    for (Interval splitChild : interval.getSplitChildren()) {
                        if (isStackSlot(splitChild.location())) {
                            if (firstSpillChild == null || splitChild.from() < firstSpillChild.from()) {
                                firstSpillChild = splitChild;
                            } else {
                                assert firstSpillChild.from() < splitChild.from();
                            }
                            // iterate all blocks where the interval has use positions
                            for (AbstractBlock<?> splitBlock : blocksForInterval(splitChild)) {
                                if (dominates(defBlock, splitBlock)) {
                                    Debug.log("Split interval %s, block %s", splitChild, splitBlock);
                                    if (spillBlock == null) {
                                        spillBlock = splitBlock;
                                    } else {
                                        spillBlock = commonDominator(spillBlock, splitBlock);
                                        assert spillBlock != null;
                                    }
                                }
                            }
                        }
                    }
                    if (spillBlock == null) {
                        // no spill interval
                        interval.setSpillState(SpillState.StoreAtDefinition);
                    } else {
                        // move out of loops
                        if (defBlock.getLoopDepth() < spillBlock.getLoopDepth()) {
                            spillBlock = moveSpillOutOfLoop(defBlock, spillBlock);
                        }

                        /*
                         * If the spill block is the begin of the first split child (aka the value
                         * is on the stack) spill in the dominator.
                         */
                        assert firstSpillChild != null;
                        if (!defBlock.equals(spillBlock) && spillBlock.equals(blockForId(firstSpillChild.from()))) {
                            AbstractBlock<?> dom = spillBlock.getDominator();
                            Debug.log("Spill block (%s) is the beginning of a spill child -> use dominator (%s)", spillBlock, dom);
                            spillBlock = dom;
                        }

                        if (!defBlock.equals(spillBlock)) {
                            assert dominates(defBlock, spillBlock);
                            betterSpillPos.increment();
                            Debug.log("Better spill position found (Block %s)", spillBlock);

                            if (defBlock.probability() <= spillBlock.probability()) {
                                // better spill block has the same probability -> do nothing
                                interval.setSpillState(SpillState.StoreAtDefinition);
                            } else {
                                LIRInsertionBuffer insertionBuffer = insertionBuffers[spillBlock.getId()];
                                if (insertionBuffer == null) {
                                    insertionBuffer = new LIRInsertionBuffer();
                                    insertionBuffers[spillBlock.getId()] = insertionBuffer;
                                    insertionBuffer.init(ir.getLIRforBlock(spillBlock));
                                }
                                int spillOpId = getFirstLirInstructionId(spillBlock);
                                // insert spill move
                                AllocatableValue fromLocation = interval.getSplitChildAtOpId(spillOpId, OperandMode.DEF, this).location();
                                AllocatableValue toLocation = canonicalSpillOpr(interval);
                                LIRInstruction move = ir.getSpillMoveFactory().createMove(toLocation, fromLocation);
                                move.setId(DOMINATOR_SPILL_MOVE_ID);
                                /*
                                 * We can use the insertion buffer directly because we always insert
                                 * at position 1.
                                 */
                                insertionBuffer.append(1, move);

                                betterSpillPosWithLowerProbability.increment();
                                interval.setSpillDefinitionPos(spillOpId);
                            }
                        } else {
                            // definition is the best choice
                            interval.setSpillState(SpillState.StoreAtDefinition);
                        }
                    }
                }
            }
        }
        for (LIRInsertionBuffer insertionBuffer : insertionBuffers) {
            if (insertionBuffer != null) {
                assert insertionBuffer.initialized() : "Insertion buffer is nonnull but not initialized!";
                insertionBuffer.finish();
            }
        }
    }

    /**
     * Iterate over all {@link AbstractBlock blocks} of an interval.
     */
    private class IntervalBlockIterator implements Iterator<AbstractBlock<?>> {

        Range range;
        AbstractBlock<?> block;

        public IntervalBlockIterator(Interval interval) {
            range = interval.first();
            block = blockForId(range.from);
        }

        public AbstractBlock<?> next() {
            AbstractBlock<?> currentBlock = block;
            int nextBlockIndex = block.getLinearScanNumber() + 1;
            if (nextBlockIndex < sortedBlocks.size()) {
                block = sortedBlocks.get(nextBlockIndex);
                if (range.to <= getFirstLirInstructionId(block)) {
                    range = range.next;
                    if (range == Range.EndMarker) {
                        block = null;
                    } else {
                        block = blockForId(range.from);
                    }
                }
            } else {
                block = null;
            }
            return currentBlock;
        }

        public boolean hasNext() {
            return block != null;
        }
    }

    private Iterable<AbstractBlock<?>> blocksForInterval(Interval interval) {
        return new Iterable<AbstractBlock<?>>() {
            public Iterator<AbstractBlock<?>> iterator() {
                return new IntervalBlockIterator(interval);
            }
        };
    }

    private static AbstractBlock<?> moveSpillOutOfLoop(AbstractBlock<?> defBlock, AbstractBlock<?> spillBlock) {
        int defLoopDepth = defBlock.getLoopDepth();
        for (AbstractBlock<?> block = spillBlock.getDominator(); !defBlock.equals(block); block = block.getDominator()) {
            assert block != null : "spill block not dominated by definition block?";
            if (block.getLoopDepth() <= defLoopDepth) {
                assert block.getLoopDepth() == defLoopDepth : "Cannot spill an interval outside of the loop where it is defined!";
                return block;
            }
        }
        return defBlock;
    }

    void printIntervals(String label) {
        if (Debug.isLogEnabled()) {
            try (Indent indent = Debug.logAndIndent("intervals %s", label)) {
                for (Interval interval : intervals) {
                    if (interval != null) {
                        Debug.log("%s", interval.logString(this));
                    }
                }

                try (Indent indent2 = Debug.logAndIndent("Basic Blocks")) {
                    for (int i = 0; i < blockCount(); i++) {
                        AbstractBlock<?> block = blockAt(i);
                        Debug.log("B%d [%d, %d, %s] ", block.getId(), getFirstLirInstructionId(block), getLastLirInstructionId(block), block.getLoop());
                    }
                }
            }
        }
        Debug.dump(Arrays.copyOf(intervals, intervalsSize), label);
    }

    void printLir(String label, @SuppressWarnings("unused") boolean hirValid) {
        Debug.dump(ir, label);
    }

    boolean verify() {
        // (check that all intervals have a correct register and that no registers are overwritten)
        verifyIntervals();

        verifyRegisters();

        Debug.log("no errors found");

        return true;
    }

    private void verifyRegisters() {
        // Enable this logging to get output for the verification process.
        try (Indent indent = Debug.logAndIndent("verifying register allocation")) {
            RegisterVerifier verifier = new RegisterVerifier(this);
            verifier.verify(blockAt(0));
        }
    }

    void verifyIntervals() {
        try (Indent indent = Debug.logAndIndent("verifying intervals")) {
            int len = intervalsSize;

            for (int i = 0; i < len; i++) {
                Interval i1 = intervals[i];
                if (i1 == null) {
                    continue;
                }

                i1.checkSplitChildren();

                if (i1.operandNumber != i) {
                    Debug.log("Interval %d is on position %d in list", i1.operandNumber, i);
                    Debug.log(i1.logString(this));
                    throw new GraalInternalError("");
                }

                if (isVariable(i1.operand) && i1.kind().equals(LIRKind.Illegal)) {
                    Debug.log("Interval %d has no type assigned", i1.operandNumber);
                    Debug.log(i1.logString(this));
                    throw new GraalInternalError("");
                }

                if (i1.location() == null) {
                    Debug.log("Interval %d has no register assigned", i1.operandNumber);
                    Debug.log(i1.logString(this));
                    throw new GraalInternalError("");
                }

                if (i1.first() == Range.EndMarker) {
                    Debug.log("Interval %d has no Range", i1.operandNumber);
                    Debug.log(i1.logString(this));
                    throw new GraalInternalError("");
                }

                for (Range r = i1.first(); r != Range.EndMarker; r = r.next) {
                    if (r.from >= r.to) {
                        Debug.log("Interval %d has zero length range", i1.operandNumber);
                        Debug.log(i1.logString(this));
                        throw new GraalInternalError("");
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
                    Value l1 = i1.location();
                    Value l2 = i2.location();
                    if (i1.intersects(i2) && !isIllegal(l1) && (l1.equals(l2))) {
                        if (DetailedAsserts.getValue()) {
                            Debug.log("Intervals %d and %d overlap and have the same register assigned", i1.operandNumber, i2.operandNumber);
                            Debug.log(i1.logString(this));
                            Debug.log(i2.logString(this));
                        }
                        throw new BailoutException("");
                    }
                }
            }
        }
    }

    class CheckConsumer extends ValueConsumer {

        boolean ok;
        Interval curInterval;

        @Override
        public void visitValue(Value operand) {
            if (isRegister(operand)) {
                if (intervalFor(operand) == curInterval) {
                    ok = true;
                }
            }
        }
    }

    void verifyNoOopsInFixedIntervals() {
        try (Indent indent = Debug.logAndIndent("verifying that no oops are in fixed intervals *")) {
            CheckConsumer checkConsumer = new CheckConsumer();

            Interval fixedIntervals;
            Interval otherIntervals;
            fixedIntervals = createUnhandledLists(IS_PRECOLORED_INTERVAL, null).first;
            // to ensure a walking until the last instruction id, add a dummy interval
            // with a high operation id
            otherIntervals = new Interval(Value.ILLEGAL, -1);
            otherIntervals.addRange(Integer.MAX_VALUE - 2, Integer.MAX_VALUE - 1);
            IntervalWalker iw = new IntervalWalker(this, fixedIntervals, otherIntervals);

            for (AbstractBlock<?> block : sortedBlocks) {
                List<LIRInstruction> instructions = ir.getLIRforBlock(block);

                for (int j = 0; j < instructions.size(); j++) {
                    LIRInstruction op = instructions.get(j);

                    if (op.hasState()) {
                        iw.walkBefore(op.id());
                        boolean checkLive = true;

                        // Make sure none of the fixed registers is live across an
                        // oopmap since we can't handle that correctly.
                        if (checkLive) {
                            for (Interval interval = iw.activeLists.get(RegisterBinding.Fixed); interval != Interval.EndMarker; interval = interval.next) {
                                if (interval.currentTo() > op.id() + 1) {
                                    // This interval is live out of this op so make sure
                                    // that this interval represents some value that's
                                    // referenced by this op either as an input or output.
                                    checkConsumer.curInterval = interval;
                                    checkConsumer.ok = false;

                                    op.visitEachInput(checkConsumer);
                                    op.visitEachAlive(checkConsumer);
                                    op.visitEachTemp(checkConsumer);
                                    op.visitEachOutput(checkConsumer);

                                    assert checkConsumer.ok : "fixed intervals should never be live across an oopmap point";
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns a value for a interval definition, which can be used for re-materialization.
     *
     * @param op An instruction which defines a value
     * @param operand The destination operand of the instruction
     * @param interval The interval for this defined value.
     * @return Returns the value which is moved to the instruction and which can be reused at all
     *         reload-locations in case the interval of this instruction is spilled. Currently this
     *         can only be a {@link Constant}.
     */
    public static Constant getMaterializedValue(LIRInstruction op, Value operand, Interval interval) {
        if (op instanceof MoveOp) {
            MoveOp move = (MoveOp) op;
            if (move.getInput() instanceof Constant) {
                /*
                 * Check if the interval has any uses which would accept an stack location (priority
                 * == ShouldHaveRegister). Rematerialization of such intervals can result in a
                 * degradation, because rematerialization always inserts a constant load, even if
                 * the value is not needed in a register.
                 */
                Interval.UsePosList usePosList = interval.usePosList();
                int numUsePos = usePosList.size();
                for (int useIdx = 0; useIdx < numUsePos; useIdx++) {
                    Interval.RegisterPriority priority = usePosList.registerPriority(useIdx);
                    if (priority == Interval.RegisterPriority.ShouldHaveRegister) {
                        return null;
                    }
                }
                return (Constant) move.getInput();
            }
        }
        return null;
    }
}
