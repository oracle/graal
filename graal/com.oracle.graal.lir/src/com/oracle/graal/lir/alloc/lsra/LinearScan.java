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
package com.oracle.graal.lir.alloc.lsra;

import static com.oracle.graal.api.code.CodeUtil.*;
import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.lir.LIRValueUtil.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.alloc.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRInstruction.OperandMode;
import com.oracle.graal.lir.StandardOp.MoveOp;
import com.oracle.graal.lir.alloc.lsra.Interval.RegisterBinding;
import com.oracle.graal.lir.alloc.lsra.Interval.SpillState;
import com.oracle.graal.lir.framemap.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.gen.LIRGeneratorTool.SpillMoveFactory;
import com.oracle.graal.lir.phases.*;
import com.oracle.graal.lir.phases.AllocationPhase.AllocationContext;
import com.oracle.graal.options.*;

/**
 * An implementation of the linear scan register allocator algorithm described in <a
 * href="http://doi.acm.org/10.1145/1064979.1064998"
 * >"Optimized Interval Splitting in a Linear Scan Register Allocator"</a> by Christian Wimmer and
 * Hanspeter Moessenboeck.
 */
class LinearScan {

    final LIRGenerationResult res;
    final LIR ir;
    final FrameMapBuilder frameMapBuilder;
    final RegisterAttributes[] registerAttributes;
    final Register[] registers;
    final RegisterAllocationConfig regAllocConfig;
    private final SpillMoveFactory moveFactory;

    final boolean callKillsRegisters;

    public static final int DOMINATOR_SPILL_MOVE_ID = -2;
    static final int SPLIT_INTERVALS_CAPACITY_RIGHT_SHIFT = 1;

    public static class Options {
        // @formatter:off
        @Option(help = "Enable spill position optimization", type = OptionType.Debug)
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

    private final BlockMap<BlockData> blockData;

    /**
     * List of blocks in linear-scan order. This is only correct as long as the CFG does not change.
     */
    final List<? extends AbstractBlockBase<?>> sortedBlocks;

    /** @see #intervals() */
    private Interval[] intervals;

    /**
     * The number of valid entries in {@link #intervals}.
     */
    private int intervalsSize;

    /**
     * The index of the first entry in {@link #intervals} for a
     * {@linkplain #createDerivedInterval(Interval) derived interval}.
     */
    private int firstDerivedIntervalIndex = -1;

    /**
     * Intervals sorted by {@link Interval#from()}.
     */
    private Interval[] sortedIntervals;

    /**
     * Map from an instruction {@linkplain LIRInstruction#id id} to the instruction. Entries should
     * be retrieved with {@link #instructionForId(int)} as the id is not simply an index into this
     * array.
     */
    private LIRInstruction[] opIdToInstructionMap;

    /**
     * Map from an instruction {@linkplain LIRInstruction#id id} to the
     * {@linkplain AbstractBlockBase block} containing the instruction. Entries should be retrieved
     * with {@link #blockForId(int)} as the id is not simply an index into this array.
     */
    private AbstractBlockBase<?>[] opIdToBlockMap;

    /**
     * The {@linkplain #operandNumber(Value) number} of the first variable operand allocated.
     */
    private final int firstVariableNumber;

    LinearScan(TargetDescription target, LIRGenerationResult res, SpillMoveFactory spillMoveFactory, RegisterAllocationConfig regAllocConfig) {
        this.res = res;
        this.ir = res.getLIR();
        this.moveFactory = spillMoveFactory;
        this.frameMapBuilder = res.getFrameMapBuilder();
        this.sortedBlocks = ir.linearScanOrder();
        this.registerAttributes = regAllocConfig.getRegisterConfig().getAttributesMap();
        this.regAllocConfig = regAllocConfig;

        this.registers = target.arch.getRegisters();
        this.firstVariableNumber = registers.length;
        this.blockData = new BlockMap<>(ir.getControlFlowGraph());

        /*
         * If all allocatable registers are caller saved, then no registers are live across a call
         * site. The register allocator can save time not trying to find a register at a call site.
         */
        this.callKillsRegisters = regAllocConfig.getRegisterConfig().areAllAllocatableRegistersCallerSaved();
    }

    int getFirstLirInstructionId(AbstractBlockBase<?> block) {
        int result = ir.getLIRforBlock(block).get(0).id();
        assert result >= 0;
        return result;
    }

    int getLastLirInstructionId(AbstractBlockBase<?> block) {
        List<LIRInstruction> instructions = ir.getLIRforBlock(block);
        int result = instructions.get(instructions.size() - 1).id();
        assert result >= 0;
        return result;
    }

    SpillMoveFactory getSpillMoveFactory() {
        return moveFactory;
    }

    protected MoveResolver createMoveResolver() {
        MoveResolver moveResolver = new MoveResolver(this);
        assert moveResolver.checkEmpty();
        return moveResolver;
    }

    public static boolean isVariableOrRegister(Value value) {
        return isVariable(value) || isRegister(value);
    }

    /**
     * Converts an operand (variable or register) to an index in a flat address space covering all
     * the {@linkplain Variable variables} and {@linkplain RegisterValue registers} being processed
     * by this allocator.
     */
    int operandNumber(Value operand) {
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
    int operandSize() {
        return firstVariableNumber + ir.numVariables();
    }

    /**
     * Gets the highest operand number for a register operand. This value will never change.
     */
    int maxRegisterNumber() {
        return firstVariableNumber - 1;
    }

    BlockData getBlockData(AbstractBlockBase<?> block) {
        return blockData.get(block);
    }

    void initBlockData(AbstractBlockBase<?> block) {
        blockData.put(block, new BlockData());
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
        /*
         * Assign the canonical spill slot of the parent (if a part of the interval is already
         * spilled) or allocate a new spill slot.
         */
        if (interval.canMaterialize()) {
            interval.assignLocation(Value.ILLEGAL);
        } else if (interval.spillSlot() != null) {
            interval.assignLocation(interval.spillSlot());
        } else {
            VirtualStackSlot slot = frameMapBuilder.allocateSpillSlot(interval.kind());
            interval.setSpillSlot(slot);
            interval.assignLocation(slot);
        }
    }

    /**
     * Map from {@linkplain #operandNumber(Value) operand numbers} to intervals.
     */
    Interval[] intervals() {
        return intervals;
    }

    void initIntervals() {
        intervalsSize = operandSize();
        intervals = new Interval[intervalsSize + (intervalsSize >> SPLIT_INTERVALS_CAPACITY_RIGHT_SHIFT)];
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

    AbstractBlockBase<?> blockAt(int index) {
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

    void initOpIdMaps(int numInstructions) {
        opIdToInstructionMap = new LIRInstruction[numInstructions];
        opIdToBlockMap = new AbstractBlockBase<?>[numInstructions];
    }

    void putOpIdMaps(int index, LIRInstruction op, AbstractBlockBase<?> block) {
        opIdToInstructionMap[index] = op;
        opIdToBlockMap[index] = block;
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
     * instructions in a method have an index one greater than their linear-scan order predecessor
     * with the first instruction having an index of 0.
     */
    private static int opIdToIndex(int opId) {
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
    AbstractBlockBase<?> blockForId(int opId) {
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

    // called during register allocation
    void changeSpillState(Interval interval, int spillPos) {
        switch (interval.spillState()) {
            case NoSpillStore: {
                int defLoopDepth = blockForId(interval.spillDefinitionPos()).getLoopDepth();
                int spillLoopDepth = blockForId(spillPos).getLoopDepth();

                if (defLoopDepth < spillLoopDepth) {
                    /*
                     * The loop depth of the spilling position is higher then the loop depth at the
                     * definition of the interval. Move write to memory out of loop.
                     */
                    if (LinearScan.Options.LSRAOptimizeSpillPosition.getValue()) {
                        // find best spill position in dominator the tree
                        interval.setSpillState(SpillState.SpillInDominator);
                    } else {
                        // store at definition of the interval
                        interval.setSpillState(SpillState.StoreAtDefinition);
                    }
                } else {
                    /*
                     * The interval is currently spilled only once, so for now there is no reason to
                     * store the interval at the definition.
                     */
                    interval.setSpillState(SpillState.OneSpillStore);
                }
                break;
            }

            case OneSpillStore: {
                if (LinearScan.Options.LSRAOptimizeSpillPosition.getValue()) {
                    // the interval is spilled more then once
                    interval.setSpillState(SpillState.SpillInDominator);
                } else {
                    // It is better to store it to memory at the definition.
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
            interval = createUnhandledLists(mustStoreAtDefinition, null).first;
            if (DetailedAsserts.getValue()) {
                checkIntervals(interval);
            }

            LIRInsertionBuffer insertionBuffer = new LIRInsertionBuffer();
            for (AbstractBlockBase<?> block : sortedBlocks) {
                try (Indent indent1 = Debug.logAndIndent("Handle %s", block)) {
                    List<LIRInstruction> instructions = ir.getLIRforBlock(block);
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
                                    Debug.log("eliminating move from interval %d (%s) to %d (%s) in block %s", operandNumber(move.getInput()), move.getInput(), operandNumber(move.getResult()),
                                                    move.getResult(), block);
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
                                    AllocatableValue toLocation = canonicalSpillOpr(interval);
                                    if (!fromLocation.equals(toLocation)) {

                                        assert isRegister(fromLocation) : "from operand must be a register but is: " + fromLocation + " toLocation=" + toLocation + " spillState=" +
                                                        interval.spillState();
                                        assert isStackSlotValue(toLocation) : "to operand must be a stack slot";

                                        LIRInstruction move = getSpillMoveFactory().createMove(toLocation, fromLocation);
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

        Interval curInterval = intervalFor(move.getResult());

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

    boolean isProcessed(Value operand) {
        return !isRegister(operand) || attributes(asRegister(operand)).isAllocatable();
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

    void allocateRegisters() {
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
            if (Debug.isLogEnabled()) {
                Debug.log("Split child at pos %d of interval %s is %s", opId, interval, result);
            }
            return result;
        }

        throw new BailoutException("LinearScan: interval is null");
    }

    void resolveCollectMappings(AbstractBlockBase<?> fromBlock, AbstractBlockBase<?> toBlock, AbstractBlockBase<?> midBlock, MoveResolver moveResolver) {
        assert moveResolver.checkEmpty();
        assert midBlock == null ||
                        (midBlock.getPredecessorCount() == 1 && midBlock.getSuccessorCount() == 1 && midBlock.getPredecessors().get(0).equals(fromBlock) && midBlock.getSuccessors().get(0).equals(
                                        toBlock));

        int toBlockFirstInstructionId = getFirstLirInstructionId(toBlock);
        int fromBlockLastInstructionId = getLastLirInstructionId(fromBlock) + 1;
        int numOperands = operandSize();
        BitSet liveAtEdge = getBlockData(toBlock).liveIn;

        // visit all variables for which the liveAtEdge bit is set
        for (int operandNum = liveAtEdge.nextSetBit(0); operandNum >= 0; operandNum = liveAtEdge.nextSetBit(operandNum + 1)) {
            assert operandNum < numOperands : "live information set for not exisiting interval";
            assert getBlockData(fromBlock).liveOut.get(operandNum) && getBlockData(toBlock).liveIn.get(operandNum) : "interval not live at this edge";

            Interval fromInterval = splitChildAtOpId(intervalFor(operandNum), fromBlockLastInstructionId, LIRInstruction.OperandMode.DEF);
            Interval toInterval = splitChildAtOpId(intervalFor(operandNum), toBlockFirstInstructionId, LIRInstruction.OperandMode.DEF);

            if (fromInterval != toInterval && !fromInterval.location().equals(toInterval.location())) {
                // need to insert move instruction
                moveResolver.addMapping(fromInterval, toInterval);
            }
        }
    }

    void resolveFindInsertPos(AbstractBlockBase<?> fromBlock, AbstractBlockBase<?> toBlock, MoveResolver moveResolver) {
        if (fromBlock.getSuccessorCount() <= 1) {
            if (Debug.isLogEnabled()) {
                Debug.log("inserting moves at end of fromBlock B%d", fromBlock.getId());
            }

            List<LIRInstruction> instructions = ir.getLIRforBlock(fromBlock);
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
                assert ir.getLIRforBlock(fromBlock).get(0) instanceof StandardOp.LabelOp : "block does not start with a label";

                /*
                 * Because the number of predecessor edges matches the number of successor edges,
                 * blocks which are reached by switch statements may have be more than one
                 * predecessor but it will be guaranteed that all predecessors will be the same.
                 */
                for (AbstractBlockBase<?> predecessor : toBlock.getPredecessors()) {
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
            MoveResolver moveResolver = createMoveResolver();
            BitSet blockCompleted = new BitSet(numBlocks);
            BitSet alreadyResolved = new BitSet(numBlocks);

            for (AbstractBlockBase<?> block : sortedBlocks) {

                // check if block has only one predecessor and only one successor
                if (block.getPredecessorCount() == 1 && block.getSuccessorCount() == 1) {
                    List<LIRInstruction> instructions = ir.getLIRforBlock(block);
                    assert instructions.get(0) instanceof StandardOp.LabelOp : "block must start with label";
                    assert instructions.get(instructions.size() - 1) instanceof StandardOp.JumpOp : "block with successor must end with unconditional jump";

                    // check if block is empty (only label and branch)
                    if (instructions.size() == 2) {
                        AbstractBlockBase<?> pred = block.getPredecessors().iterator().next();
                        AbstractBlockBase<?> sux = block.getSuccessors().iterator().next();

                        // prevent optimization of two consecutive blocks
                        if (!blockCompleted.get(pred.getLinearScanNumber()) && !blockCompleted.get(sux.getLinearScanNumber())) {
                            if (Debug.isLogEnabled()) {
                                Debug.log(" optimizing empty block B%d (pred: B%d, sux: B%d)", block.getId(), pred.getId(), sux.getId());
                            }

                            blockCompleted.set(block.getLinearScanNumber());

                            /*
                             * Directly resolve between pred and sux (without looking at the empty
                             * block between).
                             */
                            resolveCollectMappings(pred, sux, block, moveResolver);
                            if (moveResolver.hasMappings()) {
                                moveResolver.setInsertPosition(instructions, 1);
                                moveResolver.resolveAndAppendMoves();
                            }
                        }
                    }
                }
            }

            for (AbstractBlockBase<?> fromBlock : sortedBlocks) {
                if (!blockCompleted.get(fromBlock.getLinearScanNumber())) {
                    alreadyResolved.clear();
                    alreadyResolved.or(blockCompleted);

                    for (AbstractBlockBase<?> toBlock : fromBlock.getSuccessors()) {

                        /*
                         * Check for duplicate edges between the same blocks (can happen with switch
                         * blocks).
                         */
                        if (!alreadyResolved.get(toBlock.getLinearScanNumber())) {
                            if (Debug.isLogEnabled()) {
                                Debug.log("processing edge between B%d and B%d", fromBlock.getId(), toBlock.getId());
                            }

                            alreadyResolved.set(toBlock.getLinearScanNumber());

                            // collect all intervals that have been split between
                            // fromBlock and toBlock
                            resolveCollectMappings(fromBlock, toBlock, null, moveResolver);
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

    static StackSlotValue canonicalSpillOpr(Interval interval) {
        assert interval.spillSlot() != null : "canonical spill slot not set";
        return interval.spillSlot();
    }

    boolean isMaterialized(AllocatableValue operand, int opId, OperandMode mode) {
        Interval interval = intervalFor(operand);
        assert interval != null : "interval must exist";

        if (opId != -1) {
            /*
             * Operands are not changed when an interval is split during allocation, so search the
             * right interval here.
             */
            interval = splitChildAtOpId(interval, opId, mode);
        }

        return isIllegal(interval.location()) && interval.canMaterialize();
    }

    boolean isCallerSave(Value operand) {
        return attributes(asRegister(operand)).isCallerSave();
    }

    <B extends AbstractBlockBase<B>> void allocate(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder, SpillMoveFactory spillMoveFactory) {

        /*
         * This is the point to enable debug logging for the whole register allocation.
         */
        try (Indent indent = Debug.logAndIndent("LinearScan allocate")) {
            AllocationContext context = new AllocationContext(spillMoveFactory);

            createLifetimeAnalysisPhase().apply(target, lirGenRes, codeEmittingOrder, linearScanOrder, context, false);

            sortIntervalsBeforeAllocation();

            createRegisterAllocationPhase().apply(target, lirGenRes, codeEmittingOrder, linearScanOrder, context, false);

            if (LinearScan.Options.LSRAOptimizeSpillPosition.getValue()) {
                createOptimizeSpillPositionPhase().apply(target, lirGenRes, codeEmittingOrder, linearScanOrder, context, false);
            }
            createResolveDataFlowPhase().apply(target, lirGenRes, codeEmittingOrder, linearScanOrder, context);

            sortIntervalsAfterAllocation();

            if (DetailedAsserts.getValue()) {
                verify();
            }

            createSpillMoveEliminationPhase().apply(target, lirGenRes, codeEmittingOrder, linearScanOrder, context);
            createAssignLocationsPhase().apply(target, lirGenRes, codeEmittingOrder, linearScanOrder, context);

            if (DetailedAsserts.getValue()) {
                verifyIntervals();
            }
        }
    }

    protected LifetimeAnalysis createLifetimeAnalysisPhase() {
        return new LifetimeAnalysis(this);
    }

    protected RegisterAllocation createRegisterAllocationPhase() {
        return new RegisterAllocation();
    }

    protected OptimizeSpillPosition createOptimizeSpillPositionPhase() {
        return new OptimizeSpillPosition(this);
    }

    protected ResolveDataFlow createResolveDataFlowPhase() {
        return new ResolveDataFlow();
    }

    protected EliminateSpillMove createSpillMoveEliminationPhase() {
        return new EliminateSpillMove();
    }

    protected AssignLocations createAssignLocationsPhase() {
        return new AssignLocations(this);
    }

    private final class RegisterAllocation extends AllocationPhase {

        @Override
        protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder,
                        SpillMoveFactory spillMoveFactory) {
            printIntervals("Before register allocation");
            allocateRegisters();
            printIntervals("After register allocation");
        }

    }

    private final class ResolveDataFlow extends AllocationPhase {

        @Override
        protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder,
                        SpillMoveFactory spillMoveFactory) {
            resolveDataFlow();
        }

    }

    private final class EliminateSpillMove extends AllocationPhase {

        @Override
        protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder,
                        SpillMoveFactory spillMoveFactory) {
            beforeSpillMoveElimination();
            eliminateSpillMoves();
        }

    }

    protected void beforeSpillMoveElimination() {
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
                        AbstractBlockBase<?> block = blockAt(i);
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

    class CheckConsumer implements ValueConsumer {

        boolean ok;
        Interval curInterval;

        @Override
        public void visitValue(Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
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

            for (AbstractBlockBase<?> block : sortedBlocks) {
                List<LIRInstruction> instructions = ir.getLIRforBlock(block);

                for (int j = 0; j < instructions.size(); j++) {
                    LIRInstruction op = instructions.get(j);

                    if (op.hasState()) {
                        iw.walkBefore(op.id());
                        boolean checkLive = true;

                        /*
                         * Make sure none of the fixed registers is live across an oopmap since we
                         * can't handle that correctly.
                         */
                        if (checkLive) {
                            for (Interval interval = iw.activeLists.get(RegisterBinding.Fixed); interval != Interval.EndMarker; interval = interval.next) {
                                if (interval.currentTo() > op.id() + 1) {
                                    /*
                                     * This interval is live out of this op so make sure that this
                                     * interval represents some value that's referenced by this op
                                     * either as an input or output.
                                     */
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

}
