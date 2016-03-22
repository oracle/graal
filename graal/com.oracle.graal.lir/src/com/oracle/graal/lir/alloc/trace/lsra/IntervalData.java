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
import static jdk.vm.ci.code.CodeUtil.isEven;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isLegal;
import static jdk.vm.ci.code.ValueUtil.isRegister;

import java.util.Arrays;
import java.util.List;

import com.oracle.graal.compiler.common.alloc.RegisterAllocationConfig;
import com.oracle.graal.compiler.common.alloc.Trace;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Indent;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.alloc.trace.TraceBuilderPhase;
import com.oracle.graal.lir.alloc.trace.lsra.TraceInterval.RegisterPriority;
import com.oracle.graal.lir.gen.LIRGenerationResult;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterAttributes;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.Value;

public final class IntervalData {

    private static final int SPLIT_INTERVALS_CAPACITY_RIGHT_SHIFT = 1;

    private final LIR ir;
    private final RegisterAttributes[] registerAttributes;
    private final Register[] registers;

    /**
     * List of blocks in linear-scan order. This is only correct as long as the CFG does not change.
     */
    private final List<? extends AbstractBlockBase<?>> sortedBlocks;

    /**
     * The index of the first entry in {@link #intervals} for a
     * {@linkplain #createDerivedInterval(TraceInterval) derived interval}.
     */
    private int firstDerivedIntervalIndex = -1;

    /** @see #fixedIntervals() */
    private final FixedInterval[] fixedIntervals;

    /** @see #intervals() */
    private TraceInterval[] intervals;

    /**
     * The number of valid entries in {@link #intervals}.
     */
    private int intervalsSize;

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

    IntervalData(TargetDescription target, LIRGenerationResult res, RegisterAllocationConfig regAllocConfig, Trace<? extends AbstractBlockBase<?>> trace) {
        this.ir = res.getLIR();
        this.sortedBlocks = trace.getBlocks();
        this.registerAttributes = regAllocConfig.getRegisterConfig().getAttributesMap();

        this.registers = target.arch.getRegisters();
        this.fixedIntervals = new FixedInterval[registers.length];
    }

    private int getFirstLirInstructionId(AbstractBlockBase<?> block) {
        int result = ir.getLIRforBlock(block).get(0).id();
        assert result >= 0;
        return result;
    }

    private int getLastLirInstructionId(AbstractBlockBase<?> block) {
        List<LIRInstruction> instructions = ir.getLIRforBlock(block);
        int result = instructions.get(instructions.size() - 1).id();
        assert result >= 0;
        return result;
    }

    public boolean isProcessed(Value operand) {
        return !isRegister(operand) || attributes(asRegister(operand)).isAllocatable();
    }

    /**
     * Converts an operand (variable or register) to an index in a flat address space covering all
     * the {@linkplain Variable variables} and {@linkplain RegisterValue registers} being processed
     * by this allocator.
     */
    private static int operandNumber(Value operand) {
        assert !isRegister(operand) : "Register do not have operand numbers: " + operand;
        assert isVariable(operand) : "Unsupported Value " + operand;
        return ((Variable) operand).index;
    }

    /**
     * Gets the number of operands. This value will increase by 1 for new variable.
     */
    private int operandSize() {
        return ir.numVariables();
    }

    /**
     * Gets an object describing the attributes of a given register according to this register
     * configuration.
     */
    RegisterAttributes attributes(Register reg) {
        return registerAttributes[reg.number];
    }

    /**
     * Map from {@linkplain #operandNumber(Value) operand numbers} to intervals.
     */
    TraceInterval[] intervals() {
        return intervals;
    }

    /**
     * Map from {@linkplain #operandNumber(Value) operand numbers} to intervals.
     */
    FixedInterval[] fixedIntervals() {
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
    private FixedInterval createFixedInterval(RegisterValue reg) {
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
    private TraceInterval createInterval(AllocatableValue operand) {
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
            intervals = Arrays.copyOf(intervals, intervals.length + (intervals.length >> SPLIT_INTERVALS_CAPACITY_RIGHT_SHIFT) + 1);
        }
        // increments intervalsSize
        Variable variable = createVariable(source.kind());

        assert intervalsSize <= intervals.length;

        TraceInterval interval = createInterval(variable);
        assert intervals[intervalsSize - 1] == interval;
        return interval;
    }

    /**
     * Creates a new variable for a derived interval. Note that the variable is not
     * {@linkplain LIR#nextVariable() managed} so it must not be inserted into the {@link LIR}.
     */
    private Variable createVariable(LIRKind kind) {
        return new Variable(kind, intervalsSize++);
    }

    boolean hasDerivedIntervals() {
        return firstDerivedIntervalIndex != -1;
    }

    int firstDerivedIntervalIndex() {
        return firstDerivedIntervalIndex;
    }

    public int intervalsSize() {
        return intervalsSize;
    }

    // access to block list (sorted in linear scan order)
    private int blockCount() {
        return sortedBlocks.size();
    }

    private AbstractBlockBase<?> blockAt(int index) {
        return sortedBlocks.get(index);
    }

    public List<? extends AbstractBlockBase<?>> getBlocks() {
        return sortedBlocks;
    }

    FixedInterval fixedIntervalFor(RegisterValue reg) {
        return fixedIntervals[reg.getRegister().number];
    }

    FixedInterval getOrCreateFixedInterval(RegisterValue reg) {
        FixedInterval ret = fixedIntervalFor(reg);
        if (ret == null) {
            return createFixedInterval(reg);
        } else {
            return ret;
        }
    }

    TraceInterval intervalFor(Value operand) {
        int operandNumber = operandNumber(operand);
        assert operandNumber < intervalsSize;
        return intervals[operandNumber];
    }

    TraceInterval getOrCreateInterval(AllocatableValue operand) {
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
        assert opIdToBlockMap.length > 0 && opId >= 0 && opId <= maxOpId() + 1 : "opId out of range: " + opId;
        return opIdToBlockMap[opIdToIndex(opId)];
    }

    @SuppressWarnings("try")
    public void printIntervals(String label) {
        if (Debug.isDumpEnabled(TraceBuilderPhase.TRACE_DUMP_LEVEL)) {
            if (Debug.isLogEnabled()) {
                try (Indent indent = Debug.logAndIndent("intervals %s", label)) {
                    for (FixedInterval interval : fixedIntervals) {
                        if (interval != null) {
                            Debug.log("%s", interval.logString());
                        }
                    }

                    for (TraceInterval interval : intervals) {
                        if (interval != null) {
                            Debug.log("%s", interval.logString());
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
            Debug.dump(Debug.INFO_LOG_LEVEL, new TraceIntervalDumper(Arrays.copyOf(fixedIntervals, fixedIntervals.length), Arrays.copyOf(intervals, intervalsSize)), label);
        }
    }

    public static boolean verifyEquals(IntervalData a, IntervalData b) {
        assert compareFixed(a.fixedIntervals(), b.fixedIntervals());
        assert compareIntervals(a.intervals(), b.intervals());
        return true;
    }

    private static boolean compareIntervals(TraceInterval[] a, TraceInterval[] b) {
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            if (i >= a.length) {
                assert b[i] == null : "missing a interval: " + i + " b: " + b[i];
                continue;
            }
            if (i >= b.length) {
                assert a[i] == null : "missing b interval: " + i + " a: " + a[i];
                continue;
            }
            compareInterval(a[i], b[i]);
        }
        return true;
    }

    private static void compareInterval(TraceInterval a, TraceInterval b) {
        if (a == null) {
            assert b == null : "First interval is null but second is: " + b;
            return;
        }
        assert b != null : "Second interval is null but forst is: " + a;
        assert a.operand.equals(b.operand) : "Operand mismatch: " + a + " vs. " + b;
        assert a.from() == b.from() : "From mismatch: " + a + " vs. " + b;
        assert a.to() == b.to() : "To mismatch: " + a + " vs. " + b;
        assert verifyIntervalsEquals(a, b);
    }

    private static boolean verifyIntervalsEquals(TraceInterval a, TraceInterval b) {
        for (int i = 0; i < Math.max(a.usePosList().size(), b.usePosList().size()); i++) {
            assert i < a.usePosList().size() : "missing a usepos: " + i + " b: " + b;
            assert i < b.usePosList().size() : "missing b usepos: " + i + " a: " + a;
            int aPos = a.usePosList().usePos(i);
            int bPos = b.usePosList().usePos(i);
            assert aPos == bPos : "Use Positions differ: " + aPos + " vs. " + bPos;
            RegisterPriority aReg = a.usePosList().registerPriority(i);
            RegisterPriority bReg = b.usePosList().registerPriority(i);
            assert aReg == bReg : "Register priority differ: " + aReg + " vs. " + bReg;
        }
        return true;
    }

    private static boolean compareFixed(FixedInterval[] a, FixedInterval[] b) {
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            if (i >= a.length) {
                assert b[i] == null : "missing a interval: " + i + " b: " + b[i];
                continue;
            }
            if (i >= b.length) {
                assert a[i] == null : "missing b interval: " + i + " a: " + a[i];
                continue;
            }
            compareFixedInterval(a[i], b[i]);
        }
        return true;
    }

    private static void compareFixedInterval(FixedInterval a, FixedInterval b) {
        if (a == null) {
            assert b == null || isEmptyInterval(b) : "First interval is null but second is: " + b;
            return;
        }
        if (b == null) {
            assert isEmptyInterval(a) : "Second interval is null but first is: " + a;
            return;
        }
        assert a.operand.equals(b.operand) : "Operand mismatch: " + a + " vs. " + b;
        assert a.from() == b.from() : "From mismatch: " + a + " vs. " + b;
        assert a.to() == b.to() : "To mismatch: " + a + " vs. " + b;
        assert verifyFixeEquas(a, b);
    }

    private static boolean verifyFixeEquas(FixedInterval a, FixedInterval b) {
        a.rewindRange();
        b.rewindRange();
        while (!a.currentAtEnd()) {
            assert !b.currentAtEnd() : "Fixed range mismatch: " + a + " vs. " + b;
            assert a.currentFrom() == b.currentFrom() : "From range mismatch: " + a + " vs. " + b + " from: " + a.currentFrom() + " vs. " + b.currentFrom();
            assert a.currentTo() == b.currentTo() : "To range mismatch: " + a + " vs. " + b + " from: " + a.currentTo() + " vs. " + b.currentTo();
            a.nextRange();
            b.nextRange();
        }
        assert b.currentAtEnd() : "Fixed range mismatch: " + a + " vs. " + b;
        return true;
    }

    private static boolean isEmptyInterval(FixedInterval fixed) {
        return fixed.from() == -1 && fixed.to() == 0;
    }
}
