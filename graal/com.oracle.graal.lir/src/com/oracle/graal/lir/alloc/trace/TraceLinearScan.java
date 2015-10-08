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
package com.oracle.graal.lir.alloc.trace;

import static com.oracle.graal.compiler.common.GraalOptions.DetailedAsserts;
import static com.oracle.graal.lir.LIRValueUtil.isVariable;
import static jdk.vm.ci.code.CodeUtil.isEven;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.asRegisterValue;
import static jdk.vm.ci.code.ValueUtil.isIllegal;
import static jdk.vm.ci.code.ValueUtil.isLegal;
import static jdk.vm.ci.code.ValueUtil.isRegister;

import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterAttributes;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlotValue;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.VirtualStackSlot;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.Value;

import com.oracle.graal.compiler.common.alloc.RegisterAllocationConfig;
import com.oracle.graal.compiler.common.alloc.TraceBuilder.TraceBuilderResult;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.compiler.common.cfg.BlockMap;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.debug.Indent;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRInstruction.OperandMode;
import com.oracle.graal.lir.ValueConsumer;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.alloc.trace.TraceLinearScanAllocationPhase.TraceLinearScanAllocationContext;
import com.oracle.graal.lir.framemap.FrameMapBuilder;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.gen.LIRGeneratorTool.SpillMoveFactory;

/**
 * An implementation of the linear scan register allocator algorithm described in <a
 * href="http://doi.acm.org/10.1145/1064979.1064998"
 * >"Optimized Interval Splitting in a Linear Scan Register Allocator"</a> by Christian Wimmer and
 * Hanspeter Moessenboeck.
 */
final class TraceLinearScan {

    private static final TraceLinearScanRegisterAllocationPhase TRACE_LINEAR_SCAN_REGISTER_ALLOCATION_PHASE = new TraceLinearScanRegisterAllocationPhase();
    private static final TraceLinearScanAssignLocationsPhase TRACE_LINEAR_SCAN_ASSIGN_LOCATIONS_PHASE = new TraceLinearScanAssignLocationsPhase();
    // TODO (je) use!
    @SuppressWarnings("unused") private static final TraceLinearScanEliminateSpillMovePhase TRACE_LINEAR_SCAN_ELIMINATE_SPILL_MOVE_PHASE = new TraceLinearScanEliminateSpillMovePhase();
    private static final TraceLinearScanResolveDataFlowPhase TRACE_LINEAR_SCAN_RESOLVE_DATA_FLOW_PHASE = new TraceLinearScanResolveDataFlowPhase();
    private static final TraceLinearScanLifetimeAnalysisPhase TRACE_LINEAR_SCAN_LIFETIME_ANALYSIS_PHASE = new TraceLinearScanLifetimeAnalysisPhase();

    public static class BlockData {

        /**
         * Bit map specifying which operands are live upon entry to this block. These are values
         * used in this block or any of its successors where such value are not defined in this
         * block. The bit index of an operand is its
         * {@linkplain TraceLinearScan#operandNumber(Value) operand number}.
         */
        public BitSet liveIn;

        /**
         * Bit map specifying which operands are live upon exit from this block. These are values
         * used in a successor block that are either defined in this block or were live upon entry
         * to this block. The bit index of an operand is its
         * {@linkplain TraceLinearScan#operandNumber(Value) operand number}.
         */
        public BitSet liveOut;

        /**
         * Bit map specifying which operands are used (before being defined) in this block. That is,
         * these are the values that are live upon entry to the block. The bit index of an operand
         * is its {@linkplain TraceLinearScan#operandNumber(Value) operand number}.
         */
        public BitSet liveGen;

        /**
         * Bit map specifying which operands are defined/overwritten in this block. The bit index of
         * an operand is its {@linkplain TraceLinearScan#operandNumber(Value) operand number}.
         */
        public BitSet liveKill;
    }

    public static final int DOMINATOR_SPILL_MOVE_ID = -2;
    private static final int SPLIT_INTERVALS_CAPACITY_RIGHT_SHIFT = 1;

    private final LIR ir;
    private final FrameMapBuilder frameMapBuilder;
    private final RegisterAttributes[] registerAttributes;
    private final Register[] registers;
    private final RegisterAllocationConfig regAllocConfig;
    private final SpillMoveFactory moveFactory;

    private final BlockMap<BlockData> blockData;

    /**
     * List of blocks in linear-scan order. This is only correct as long as the CFG does not change.
     */
    private final List<? extends AbstractBlockBase<?>> sortedBlocks;

    /** @see #fixedIntervals() */
    private final FixedInterval[] fixedIntervals;

    /** @see #intervals() */
    private TraceInterval[] intervals;

    /**
     * The number of valid entries in {@link #intervals}.
     */
    private int intervalsSize;

    /**
     * The index of the first entry in {@link #intervals} for a
     * {@linkplain #createDerivedInterval(TraceInterval) derived interval}.
     */
    private int firstDerivedIntervalIndex = -1;

    /**
     * Intervals sorted by {@link TraceInterval#from()}.
     */
    private TraceInterval[] sortedIntervals;

    /**
     * Fixed intervals sorted by {@link FixedInterval#from()}.
     */
    private FixedInterval[] sortedFixedIntervals;

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

    protected final TraceBuilderResult<?> traceBuilderResult;
    private final boolean neverSpillConstants;

    protected TraceLinearScan(TargetDescription target, LIRGenerationResult res, SpillMoveFactory spillMoveFactory, RegisterAllocationConfig regAllocConfig,
                    List<? extends AbstractBlockBase<?>> sortedBlocks, TraceBuilderResult<?> traceBuilderResult, boolean neverSpillConstants) {
        this.ir = res.getLIR();
        this.moveFactory = spillMoveFactory;
        this.frameMapBuilder = res.getFrameMapBuilder();
        this.sortedBlocks = sortedBlocks;
        this.registerAttributes = regAllocConfig.getRegisterConfig().getAttributesMap();
        this.regAllocConfig = regAllocConfig;

        this.registers = target.arch.getRegisters();
        this.fixedIntervals = new FixedInterval[registers.length];
        this.blockData = new BlockMap<>(ir.getControlFlowGraph());
        this.traceBuilderResult = traceBuilderResult;
        this.neverSpillConstants = neverSpillConstants;
    }

    public int getFirstLirInstructionId(AbstractBlockBase<?> block) {
        int result = ir.getLIRforBlock(block).get(0).id();
        assert result >= 0;
        return result;
    }

    public int getLastLirInstructionId(AbstractBlockBase<?> block) {
        List<LIRInstruction> instructions = ir.getLIRforBlock(block);
        int result = instructions.get(instructions.size() - 1).id();
        assert result >= 0;
        return result;
    }

    public SpillMoveFactory getSpillMoveFactory() {
        return moveFactory;
    }

    protected TraceLocalMoveResolver createMoveResolver() {
        TraceLocalMoveResolver moveResolver = new TraceLocalMoveResolver(this);
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
    @SuppressWarnings("static-method")
    int operandNumber(Value operand) {
        assert !isRegister(operand) : "Register do not have operand numbers: " + operand;
        assert isVariable(operand) : "Unsupported Value " + operand;
        return ((Variable) operand).index;
    }

    /**
     * Gets the number of operands. This value will increase by 1 for new variable.
     */
    int operandSize() {
        return ir.numVariables();
    }

    /**
     * Gets the number of registers. This value will never change.
     */
    int numRegisters() {
        return registers.length;
    }

    public BlockData getBlockData(AbstractBlockBase<?> block) {
        return blockData.get(block);
    }

    void initBlockData(AbstractBlockBase<?> block) {
        blockData.put(block, new BlockData());
    }

    static final IntervalPredicate IS_PRECOLORED_INTERVAL = new IntervalPredicate() {

        @Override
        public boolean apply(TraceInterval i) {
            return isRegister(i.operand);
        }
    };

    static final IntervalPredicate IS_VARIABLE_INTERVAL = new IntervalPredicate() {

        @Override
        public boolean apply(TraceInterval i) {
            return isVariable(i.operand);
        }
    };

    static final IntervalPredicate IS_STACK_INTERVAL = new IntervalPredicate() {

        @Override
        public boolean apply(TraceInterval i) {
            return !isRegister(i.operand);
        }
    };

    /**
     * Gets an object describing the attributes of a given register according to this register
     * configuration.
     */
    public RegisterAttributes attributes(Register reg) {
        return registerAttributes[reg.number];
    }

    void assignSpillSlot(TraceInterval interval) {
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
    public TraceInterval[] intervals() {
        return intervals;
    }

    /**
     * Map from {@linkplain #operandNumber(Value) operand numbers} to intervals.
     */
    public FixedInterval[] fixedIntervals() {
        return fixedIntervals;
    }

    void initIntervals() {
        intervalsSize = operandSize();
        intervals = new TraceInterval[intervalsSize + (intervalsSize >> SPLIT_INTERVALS_CAPACITY_RIGHT_SHIFT)];
    }

    /**
     * Creates a new fixed interval.
     *
     * @param reg the operand for the interval
     * @return the created interval
     */
    FixedInterval createFixedInterval(RegisterValue reg) {
        FixedInterval interval = new FixedInterval(reg);
        int operandNumber = reg.getRegister().number;
        assert fixedIntervals[operandNumber] == null;
        fixedIntervals[operandNumber] = interval;
        return interval;
    }

    /**
     * Creates a new interval.
     *
     * @param operand the operand for the interval
     * @return the created interval
     */
    TraceInterval createInterval(AllocatableValue operand) {
        assert isLegal(operand);
        int operandNumber = operandNumber(operand);
        TraceInterval interval = new TraceInterval(operand, operandNumber);
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
    TraceInterval createDerivedInterval(TraceInterval source) {
        if (firstDerivedIntervalIndex == -1) {
            firstDerivedIntervalIndex = intervalsSize;
        }
        if (intervalsSize == intervals.length) {
            intervals = Arrays.copyOf(intervals, intervals.length + (intervals.length >> SPLIT_INTERVALS_CAPACITY_RIGHT_SHIFT));
        }
        intervalsSize++;
        Variable variable = new Variable(source.kind(), ir.nextVariable());

        TraceInterval interval = createInterval(variable);
        assert intervals[intervalsSize - 1] == interval;
        return interval;
    }

    // access to block list (sorted in linear scan order)
    public int blockCount() {
        return sortedBlocks.size();
    }

    public AbstractBlockBase<?> blockAt(int index) {
        return sortedBlocks.get(index);
    }

    /**
     * Gets the size of the {@link BlockData#liveIn} and {@link BlockData#liveOut} sets for a basic
     * block. These sets do not include any operands allocated as a result of creating
     * {@linkplain #createDerivedInterval(TraceInterval) derived intervals}.
     */
    public int liveSetSize() {
        return firstDerivedIntervalIndex == -1 ? operandSize() : firstDerivedIntervalIndex;
    }

    int numLoops() {
        return ir.getControlFlowGraph().getLoops().size();
    }

    public FixedInterval fixedIntervalFor(RegisterValue reg) {
        return fixedIntervals[reg.getRegister().number];
    }

    public FixedInterval getOrCreateFixedInterval(RegisterValue reg) {
        FixedInterval ret = fixedIntervalFor(reg);
        if (ret == null) {
            return createFixedInterval(reg);
        } else {
            return ret;
        }
    }

    TraceInterval intervalFor(int operandNumber) {
        return intervals[operandNumber];
    }

    public TraceInterval intervalFor(Value operand) {
        int operandNumber = operandNumber(operand);
        assert operandNumber < intervalsSize;
        return intervals[operandNumber];
    }

    public TraceInterval getOrCreateInterval(AllocatableValue operand) {
        TraceInterval ret = intervalFor(operand);
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
    public LIRInstruction instructionForId(int opId) {
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
    public AbstractBlockBase<?> blockForId(int opId) {
        assert opIdToBlockMap.length > 0 && opId >= 0 && opId <= maxOpId() + 1 : "opId out of range: " + opId;
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

    abstract static class IntervalPredicate {

        abstract boolean apply(TraceInterval i);
    }

    public boolean isProcessed(Value operand) {
        return !isRegister(operand) || attributes(asRegister(operand)).isAllocatable();
    }

    // * Phase 5: actual register allocation

    private static <T extends IntervalHint> boolean isSorted(T[] intervals) {
        int from = -1;
        for (T interval : intervals) {
            assert interval != null;
            assert from <= interval.from();
            from = interval.from();
        }
        return true;
    }

    private static TraceInterval addToList(TraceInterval first, TraceInterval prev, TraceInterval interval) {
        TraceInterval newFirst = first;
        if (prev != null) {
            prev.next = interval;
        } else {
            newFirst = interval;
        }
        return newFirst;
    }

    TraceInterval createUnhandledList(IntervalPredicate isList1) {
        assert isSorted(sortedIntervals) : "interval list is not sorted";

        TraceInterval list1 = TraceInterval.EndMarker;

        TraceInterval list1Prev = null;
        TraceInterval v;

        int n = sortedIntervals.length;
        for (int i = 0; i < n; i++) {
            v = sortedIntervals[i];
            if (v == null) {
                continue;
            }

            if (isList1.apply(v)) {
                list1 = addToList(list1, list1Prev, v);
                list1Prev = v;
            }
        }

        if (list1Prev != null) {
            list1Prev.next = TraceInterval.EndMarker;
        }

        assert list1Prev == null || list1Prev.next == TraceInterval.EndMarker : "linear list ends not with sentinel";

        return list1;
    }

    private static FixedInterval addToList(FixedInterval first, FixedInterval prev, FixedInterval interval) {
        FixedInterval newFirst = first;
        if (prev != null) {
            prev.next = interval;
        } else {
            newFirst = interval;
        }
        return newFirst;
    }

    FixedInterval createFixedUnhandledList() {
        assert isSorted(sortedFixedIntervals) : "interval list is not sorted";

        FixedInterval list1 = FixedInterval.EndMarker;

        FixedInterval list1Prev = null;
        FixedInterval v;

        int n = sortedFixedIntervals.length;
        for (int i = 0; i < n; i++) {
            v = sortedFixedIntervals[i];
            if (v == null) {
                continue;
            }

            v.rewindRange();
            list1 = addToList(list1, list1Prev, v);
            list1Prev = v;
        }

        if (list1Prev != null) {
            list1Prev.next = FixedInterval.EndMarker;
        }

        assert list1Prev == null || list1Prev.next == FixedInterval.EndMarker : "linear list ends not with sentinel";

        return list1;
    }

    // SORTING

    protected void sortIntervalsBeforeAllocation() {
        int sortedLen = 0;
        for (TraceInterval interval : intervals) {
            if (interval != null) {
                sortedLen++;
            }
        }
        sortedIntervals = sortIntervalsBeforeAllocation(intervals, new TraceInterval[sortedLen]);
    }

    protected void sortFixedIntervalsBeforeAllocation() {
        int sortedLen = 0;
        for (FixedInterval interval : fixedIntervals) {
            if (interval != null) {
                sortedLen++;
            }
        }
        sortedFixedIntervals = sortIntervalsBeforeAllocation(fixedIntervals, new FixedInterval[sortedLen]);
    }

    private static <T extends IntervalHint> T[] sortIntervalsBeforeAllocation(T[] intervals, T[] sortedList) {
        int sortedIdx = 0;
        int sortedFromMax = -1;

        // special sorting algorithm: the original interval-list is almost sorted,
        // only some intervals are swapped. So this is much faster than a complete QuickSort
        for (T interval : intervals) {
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
        return sortedList;
    }

    void sortIntervalsAfterAllocation() {
        if (firstDerivedIntervalIndex == -1) {
            // no intervals have been added during allocation, so sorted list is already up to date
            return;
        }

        TraceInterval[] oldList = sortedIntervals;
        TraceInterval[] newList = Arrays.copyOfRange(intervals, firstDerivedIntervalIndex, intervalsSize);
        int oldLen = oldList.length;
        int newLen = newList.length;

        // conventional sort-algorithm for new intervals
        Arrays.sort(newList, (TraceInterval a, TraceInterval b) -> a.from() - b.from());

        // merge old and new list (both already sorted) into one combined list
        TraceInterval[] combinedList = new TraceInterval[oldLen + newLen];
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

    // wrapper for Interval.splitChildAtOpId that performs a bailout in product mode
    // instead of returning null
    public TraceInterval splitChildAtOpId(TraceInterval interval, int opId, LIRInstruction.OperandMode mode) {
        TraceInterval result = interval.getSplitChildAtOpId(opId, mode, this);

        if (result != null) {
            if (Debug.isLogEnabled()) {
                Debug.log("Split child at pos %d of interval %s is %s", opId, interval, result);
            }
            return result;
        }

        throw new BailoutException("LinearScan: interval is null");
    }

    static StackSlotValue canonicalSpillOpr(TraceInterval interval) {
        assert interval.spillSlot() != null : "canonical spill slot not set";
        return interval.spillSlot();
    }

    boolean isMaterialized(AllocatableValue operand, int opId, OperandMode mode) {
        TraceInterval interval = intervalFor(operand);
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

    @SuppressWarnings("try")
    protected <B extends AbstractBlockBase<B>> void allocate(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder,
                    SpillMoveFactory spillMoveFactory, RegisterAllocationConfig registerAllocationConfig) {

        /*
         * This is the point to enable debug logging for the whole register allocation.
         */
        try (Indent indent = Debug.logAndIndent("LinearScan allocate")) {
            TraceLinearScanAllocationContext context = new TraceLinearScanAllocationContext(spillMoveFactory, registerAllocationConfig, traceBuilderResult, this);

            TRACE_LINEAR_SCAN_LIFETIME_ANALYSIS_PHASE.apply(target, lirGenRes, codeEmittingOrder, linearScanOrder, context, false);

            try (Scope s = Debug.scope("AfterLifetimeAnalysis", (Object) intervals())) {
                sortIntervalsBeforeAllocation();
                sortFixedIntervalsBeforeAllocation();

                TRACE_LINEAR_SCAN_REGISTER_ALLOCATION_PHASE.apply(target, lirGenRes, codeEmittingOrder, linearScanOrder, context, false);

                // resolve intra-trace data-flow
                TRACE_LINEAR_SCAN_RESOLVE_DATA_FLOW_PHASE.apply(target, lirGenRes, codeEmittingOrder, linearScanOrder, context, false);
                Debug.dump(TraceRegisterAllocationPhase.TRACE_DUMP_LEVEL, sortedBlocks(), "%s", TRACE_LINEAR_SCAN_RESOLVE_DATA_FLOW_PHASE.getName());

                TRACE_LINEAR_SCAN_ASSIGN_LOCATIONS_PHASE.apply(target, lirGenRes, codeEmittingOrder, linearScanOrder, context, false);

                if (DetailedAsserts.getValue()) {
                    verifyIntervals();
                }
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
        }
    }

    @SuppressWarnings("try")
    public void printIntervals(String label) {
        if (Debug.isDumpEnabled(TraceRegisterAllocationPhase.TRACE_DUMP_LEVEL)) {
            if (Debug.isLogEnabled()) {
                try (Indent indent = Debug.logAndIndent("intervals %s", label)) {
                    for (FixedInterval interval : fixedIntervals) {
                        if (interval != null) {
                            Debug.log("%s", interval.logString(this));
                        }
                    }

                    for (TraceInterval interval : intervals) {
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
            Debug.dump(new TraceIntervalDumper(Arrays.copyOf(fixedIntervals, fixedIntervals.length), Arrays.copyOf(intervals, intervalsSize)), label);
        }
    }

    public void printLir(String label, @SuppressWarnings("unused") boolean hirValid) {
        if (Debug.isDumpEnabled(TraceRegisterAllocationPhase.TRACE_DUMP_LEVEL)) {
            Debug.dump(TraceRegisterAllocationPhase.TRACE_DUMP_LEVEL, sortedBlocks(), label);
        }
    }

    boolean verify() {
        // (check that all intervals have a correct register and that no registers are overwritten)
        verifyIntervals();

        verifyRegisters();

        Debug.log("no errors found");

        return true;
    }

    @SuppressWarnings("try")
    private void verifyRegisters() {
        // Enable this logging to get output for the verification process.
        try (Indent indent = Debug.logAndIndent("verifying register allocation")) {
            RegisterVerifier verifier = new RegisterVerifier(this);
            verifier.verify(blockAt(0));
        }
    }

    @SuppressWarnings("try")
    protected void verifyIntervals() {
        try (Indent indent = Debug.logAndIndent("verifying intervals")) {
            int len = intervalsSize;

            for (int i = 0; i < len; i++) {
                final TraceInterval i1 = intervals[i];
                if (i1 == null) {
                    continue;
                }

                i1.checkSplitChildren();

                if (i1.operandNumber != i) {
                    Debug.log("Interval %d is on position %d in list", i1.operandNumber, i);
                    Debug.log(i1.logString(this));
                    throw new JVMCIError("");
                }

                if (isVariable(i1.operand) && i1.kind().equals(LIRKind.Illegal)) {
                    Debug.log("Interval %d has no type assigned", i1.operandNumber);
                    Debug.log(i1.logString(this));
                    throw new JVMCIError("");
                }

                if (i1.location() == null) {
                    Debug.log("Interval %d has no register assigned", i1.operandNumber);
                    Debug.log(i1.logString(this));
                    throw new JVMCIError("");
                }

                if (i1.isEmpty()) {
                    Debug.log("Interval %d has no Range", i1.operandNumber);
                    Debug.log(i1.logString(this));
                    throw new JVMCIError("");
                }

                if (i1.from() >= i1.to()) {
                    Debug.log("Interval %d has zero length range", i1.operandNumber);
                    Debug.log(i1.logString(this));
                    throw new JVMCIError("");
                }

                // special intervals that are created in MoveResolver
                // . ignore them because the range information has no meaning there
                if (i1.from() == 1 && i1.to() == 2) {
                    continue;
                }
                // check any intervals
                for (int j = i + 1; j < len; j++) {
                    final TraceInterval i2 = intervals[j];
                    if (i2 == null) {
                        continue;
                    }

                    // special intervals that are created in MoveResolver
                    // . ignore them because the range information has no meaning there
                    if (i2.from() == 1 && i2.to() == 2) {
                        continue;
                    }
                    Value l1 = i1.location();
                    Value l2 = i2.location();
                    boolean intersects = i1.intersects(i2);
                    if (intersects && !isIllegal(l1) && (l1.equals(l2))) {
                        if (DetailedAsserts.getValue()) {
                            Debug.log("Intervals %s and %s overlap and have the same register assigned", i1, i2);
                            Debug.log(i1.logString(this));
                            Debug.log(i2.logString(this));
                        }
                        throw new BailoutException("");
                    }
                }
                // check fixed intervals
                for (FixedInterval i2 : fixedIntervals) {
                    if (i2 == null) {
                        continue;
                    }

                    Value l1 = i1.location();
                    Value l2 = i2.location();
                    boolean intersects = i2.intersects(i1);
                    if (intersects && !isIllegal(l1) && (l1.equals(l2))) {
                        if (DetailedAsserts.getValue()) {
                            Debug.log("Intervals %s and %s overlap and have the same register assigned", i1, i2);
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
        FixedInterval curInterval;

        @Override
        public void visitValue(Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
            if (isRegister(operand)) {
                if (fixedIntervalFor(asRegisterValue(operand)) == curInterval) {
                    ok = true;
                }
            }
        }
    }

    @SuppressWarnings("try")
    void verifyNoOopsInFixedIntervals() {
        try (Indent indent = Debug.logAndIndent("verifying that no oops are in fixed intervals *")) {
            CheckConsumer checkConsumer = new CheckConsumer();

            TraceInterval otherIntervals;
            FixedInterval fixedInts = createFixedUnhandledList();
            // to ensure a walking until the last instruction id, add a dummy interval
            // with a high operation id
            otherIntervals = new TraceInterval(Value.ILLEGAL, -1);
            otherIntervals.addRange(Integer.MAX_VALUE - 2, Integer.MAX_VALUE - 1);
            TraceIntervalWalker iw = new TraceIntervalWalker(this, fixedInts, otherIntervals);

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
                            for (FixedInterval interval = iw.activeFixedList.getFixed(); interval != FixedInterval.EndMarker; interval = interval.next) {
                                if (interval.to() > op.id() + 1) {
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

    public LIR getLIR() {
        return ir;
    }

    public FrameMapBuilder getFrameMapBuilder() {
        return frameMapBuilder;
    }

    public List<? extends AbstractBlockBase<?>> sortedBlocks() {
        return sortedBlocks;
    }

    public Register[] getRegisters() {
        return registers;
    }

    public RegisterAllocationConfig getRegisterAllocationConfig() {
        return regAllocConfig;
    }

    public boolean callKillsRegisters() {
        return regAllocConfig.getRegisterConfig().areAllAllocatableRegistersCallerSaved();
    }

    boolean neverSpillConstants() {
        return neverSpillConstants;
    }

}
