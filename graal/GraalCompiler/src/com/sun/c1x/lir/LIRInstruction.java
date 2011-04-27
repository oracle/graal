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
package com.sun.c1x.lir;

import static com.sun.c1x.C1XCompilation.*;

import java.util.*;

import com.sun.c1x.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.lir.LIROperand.LIRAddressOperand;
import com.sun.c1x.lir.LIROperand.LIRVariableOperand;
import com.sun.cri.ci.*;

/**
 * The {@code LIRInstruction} class definition.
 *
 * @author Marcelo Cintra
 * @author Thomas Wuerthinger
 */
public abstract class LIRInstruction {

    private static final LIROperand ILLEGAL_SLOT = new LIROperand(CiValue.IllegalValue);

    private static final CiValue[] NO_OPERANDS = {};

    public static final OperandMode[] OPERAND_MODES = OperandMode.values();

    /**
     * Constants denoting how a LIR instruction uses an operand. Any combination of these modes
     * can be applied to an operand as long as every operand has at least one mode applied to it.
     */
    public enum OperandMode {
        /**
         * An operand that is defined by a LIR instruction and is live after the code emitted for a LIR instruction.
         */
        Output,

        /**
         * An operand that is used by a LIR instruction and is live before the code emitted for a LIR instruction.
         * Unless such an operand is also an output or temp operand, it must not be modified by a LIR instruction.
         */
        Input,

        /**
         * An operand that is both modified and used by a LIR instruction.
         */
        Temp
    }

    /**
     * The opcode of this instruction.
     */
    public final LIROpcode code;

    /**
     * The result operand for this instruction.
     */
    private final LIROperand result;

    /**
     * The input and temporary operands of this instruction.
     */
    protected final LIROperand[] operands;

    /**
     * Used to emit debug information.
     */
    public final LIRDebugInfo info;

    /**
     * Value id for register allocation.
     */
    public int id;

    /**
     * Determines if all caller-saved registers are destroyed by this instruction.
     */
    public final boolean hasCall;

    /**
     * The number of variable or register output operands for this instruction.
     * These operands are at indexes {@code [0 .. allocatorOutputCount-1]} in {@link #allocatorOperands}.
     *
     * @see OperandMode#Output
     */
    private byte allocatorOutputCount;

    /**
     * The number of variable or register input operands for this instruction.
     * These operands are at indexes {@code [allocatorOutputCount .. (allocatorInputCount+allocatorOutputCount-1)]} in {@link #allocatorOperands}.
     *
     * @see OperandMode#Input
     */
    private byte allocatorInputCount;

    /**
     * The number of variable or register temp operands for this instruction.
     * These operands are at indexes {@code [allocatorInputCount+allocatorOutputCount .. (allocatorTempCount+allocatorInputCount+allocatorOutputCount-1)]} in {@link #allocatorOperands}.
     *
     * @see OperandMode#Temp
     */
    private byte allocatorTempCount;

    /**
     * The number of variable or register input or temp operands for this instruction.
     */
    private byte allocatorTempInputCount;

    /**
     * The set of operands that must be known to the register allocator either to bind a register
     * or stack slot to a {@linkplain CiVariable variable} or to inform the allocator about operands
     * that are already fixed to a specific register.
     * This set excludes all constant operands as well as operands that are bound to
     * a stack slot in the {@linkplain CiStackSlot#inCallerFrame() caller's frame}.
     * This array is partitioned as follows.
     * <pre>
     *
     *   <-- allocatorOutputCount --> <-- allocatorInputCount --> <-- allocatorTempCount -->
     *  +----------------------------+---------------------------+--------------------------+
     *  |       output operands      |       input operands      |      temp operands       |
     *  +----------------------------+---------------------------+--------------------------+
     *
     * </pre>
     */
    final List<CiValue> allocatorOperands;

    /**
     * Constructs a new LIR instruction that has no input or temp operands.
     *
     * @param opcode the opcode of the new instruction
     * @param result the operand that holds the operation result of this instruction. This will be
     *            {@link CiValue#IllegalValue} for instructions that do not produce a result.
     * @param info the {@link LIRDebugInfo} info that is to be preserved for the instruction. This will be {@code null} when no debug info is required for the instruction.
     * @param hasCall specifies if all caller-saved registers are destroyed by this instruction
     */
    public LIRInstruction(LIROpcode opcode, CiValue result, LIRDebugInfo info, boolean hasCall) {
        this(opcode, result, info, hasCall, 0, 0, NO_OPERANDS);
    }

    /**
     * Constructs a new LIR instruction. The {@code operands} array is partitioned as follows:
     * <pre>
     *
     *                              <------- tempInput -------> <--------- temp --------->
     *  +--------------------------+---------------------------+--------------------------+
     *  |       input operands     |   input+temp operands     |      temp operands       |
     *  +--------------------------+---------------------------+--------------------------+
     *
     * </pre>
     *
     * @param opcode the opcode of the new instruction
     * @param result the operand that holds the operation result of this instruction. This will be
     *            {@link CiValue#IllegalValue} for instructions that do not produce a result.
     * @param info the {@link LIRDebugInfo} that is to be preserved for the instruction. This will be {@code null} when no debug info is required for the instruction.
     * @param hasCall specifies if all caller-saved registers are destroyed by this instruction
     * @param tempInput the number of operands that are both {@linkplain OperandMode#Input input} and {@link OperandMode#Temp temp} operands for this instruction
     * @param temp the number of operands that are {@link OperandMode#Temp temp} operands for this instruction
     * @param operands the input and temp operands for the instruction
     */
    public LIRInstruction(LIROpcode opcode, CiValue result, LIRDebugInfo info, boolean hasCall, int tempInput, int temp, CiValue... operands) {
        this.code = opcode;
        this.info = info;
        this.hasCall = hasCall;

        assert opcode != LIROpcode.Move || result != CiValue.IllegalValue;
        allocatorOperands = new ArrayList<CiValue>(operands.length + 3);
        this.result = initOutput(result);

        C1XMetrics.LIRInstructions++;

        if (opcode == LIROpcode.Move) {
            C1XMetrics.LIRMoveInstructions++;
        }
        id = -1;
        this.operands = new LIROperand[operands.length];
        initInputsAndTemps(tempInput, temp, operands);

        assert verifyOperands();
    }

    private LIROperand initOutput(CiValue output) {
        assert output != null;
        if (output != CiValue.IllegalValue) {
            if (output.isAddress()) {
                return addAddress((CiAddress) output);
            }
            if (output.isStackSlot()) {
                return new LIROperand(output);
            }

            assert allocatorOperands.size() == allocatorOutputCount;
            allocatorOperands.add(output);
            allocatorOutputCount++;
            return new LIRVariableOperand(allocatorOperands.size() - 1);
        } else {
            return ILLEGAL_SLOT;
        }
    }

    /**
     * Adds a {@linkplain CiValue#isLegal() legal} value that is part of an address to
     * the list of {@linkplain #allocatorOperands register allocator operands}. If
     * the value is {@linkplain CiVariable variable}, then its index into the list
     * of register allocator operands is returned. Otherwise, {@code -1} is returned.
     */
    private int addAddressPart(CiValue part) {
        if (part.isRegister()) {
            allocatorInputCount++;
            allocatorOperands.add(part);
            return -1;
        }
        if (part.isVariable()) {
            allocatorInputCount++;
            allocatorOperands.add(part);
            return allocatorOperands.size() - 1;
        }
        assert part.isIllegal();
        return -1;
    }

    private LIROperand addAddress(CiAddress address) {
        assert address.base.isVariableOrRegister();

        int base = addAddressPart(address.base);
        int index = addAddressPart(address.index);

        if (base != -1 || index != -1) {
            return new LIRAddressOperand(base, index, address);
        }

        assert address.base.isRegister() && (address.index.isIllegal() || address.index.isRegister());
        return new LIROperand(address);
    }

    private LIROperand addOperand(CiValue operand, boolean isInput, boolean isTemp) {
        assert operand != null;
        if (operand != CiValue.IllegalValue) {
            assert !(operand.isAddress());
            if (operand.isStackSlot()) {
                // no variables to add
                return new LIROperand(operand);
            } else if (operand.isConstant()) {
                // no variables to add
                return new LIROperand(operand);
            } else {
                assert allocatorOperands.size() == allocatorOutputCount + allocatorInputCount + allocatorTempInputCount + allocatorTempCount;
                allocatorOperands.add(operand);

                if (isInput && isTemp) {
                    allocatorTempInputCount++;
                } else if (isInput) {
                    allocatorInputCount++;
                } else {
                    assert isTemp;
                    allocatorTempCount++;
                }

                return new LIRVariableOperand(allocatorOperands.size() - 1);
            }
        } else {
            return ILLEGAL_SLOT;
        }
    }

    /**
     * Gets an input or temp operand of this instruction.
     *
     * @param index the index of the operand requested
     * @return the {@code index}'th operand
     */
    public final CiValue operand(int index) {
        if (index >= operands.length) {
            return CiValue.IllegalValue;
        }

        return operands[index].value(this);
    }

    private void initInputsAndTemps(int tempInputCount, int tempCount, CiValue[] operands) {

        // Addresses in instruction
        for (int i = 0; i < operands.length; i++) {
            CiValue op = operands[i];
            if (op.isAddress()) {
                this.operands[i] = addAddress((CiAddress) op);
            }
        }

        int z = 0;
        // Input-only operands
        for (int i = 0; i < operands.length - tempInputCount - tempCount; i++) {
            if (this.operands[z] == null) {
                this.operands[z] = addOperand(operands[z], true, false);
            }
            z++;
        }

        // Operands that are both inputs and temps
        for (int i = 0; i < tempInputCount; i++) {
            if (this.operands[z] == null) {
                this.operands[z] = addOperand(operands[z], true, true);
            }
            z++;
        }

        // Temp-only operands
        for (int i = 0; i < tempCount; i++) {
            if (this.operands[z] == null) {
                this.operands[z] = addOperand(operands[z], false, true);
            }
            z++;
        }
    }

    private boolean verifyOperands() {
        for (LIROperand operandSlot : operands) {
            assert operandSlot != null;
        }

        for (CiValue operand : this.allocatorOperands) {
            assert operand != null;
            assert operand.isVariableOrRegister() : "LIR operands can only be variables and registers initially, not " + operand.getClass().getSimpleName();
        }
        return true;
    }

    /**
     * Gets the result operand for this instruction.
     *
     * @return return the result operand
     */
    public final CiValue result() {
        return result.value(this);
    }

    /**
     * Gets the instruction name.
     *
     * @return the name of the enum constant that represents the instruction opcode, exactly as declared in the enum
     *         LIROpcode declaration.
     */
    public String name() {
        return code.name();
    }

    /**
     * Abstract method to be used to emit target code for this instruction.
     *
     * @param masm the target assembler.
     */
    public abstract void emitCode(LIRAssembler masm);

    /**
     * Utility for specializing how a {@linkplain CiValue LIR operand} is formatted to a string.
     * The {@linkplain OperandFormatter#DEFAULT default formatter} returns the value of
     * {@link CiValue#toString()}.
     */
    public static class OperandFormatter {
        public static final OperandFormatter DEFAULT = new OperandFormatter();

        /**
         * Formats a given operand as a string.
         *
         * @param operand the operand to format
         * @return {@code operand} as a string
         */
        public String format(CiValue operand) {
            return operand.toString();
        }
    }

    /**
     * Gets the operation performed by this instruction in terms of its operands as a string.
     */
    public String operationString(OperandFormatter operandFmt) {
        StringBuilder buf = new StringBuilder();
        if (result != ILLEGAL_SLOT) {
            buf.append(operandFmt.format(result.value(this))).append(" = ");
        }
        if (operands.length > 1) {
            buf.append("(");
        }
        boolean first = true;
        for (LIROperand operandSlot : operands) {
            String operand = operandFmt.format(operandSlot.value(this));
            if (!operand.isEmpty()) {
                if (!first) {
                    buf.append(", ");
                } else {
                    first = false;
                }
                buf.append(operand);
            }
        }
        if (operands.length > 1) {
            buf.append(")");
        }
        return buf.toString();
    }

    public boolean verify() {
        return true;
    }

    /**
     * Determines if a given opcode is in a given range of valid opcodes.
     *
     * @param opcode the opcode to be tested.
     * @param start the lower bound range limit of valid opcodes
     * @param end the upper bound range limit of valid opcodes
     */
    protected static boolean isInRange(LIROpcode opcode, LIROpcode start, LIROpcode end) {
        return start.ordinal() < opcode.ordinal() && opcode.ordinal() < end.ordinal();
    }

    public boolean hasOperands() {
        if (info != null || hasCall) {
            return true;
        }
        return allocatorOperands.size() > 0;
    }

    public final int operandCount(OperandMode mode) {
        if (mode == OperandMode.Output) {
            return allocatorOutputCount;
        } else if (mode == OperandMode.Input) {
            return allocatorInputCount + allocatorTempInputCount;
        } else {
            assert mode == OperandMode.Temp;
            return allocatorTempInputCount + allocatorTempCount;
        }
    }

    public final CiValue operandAt(OperandMode mode, int index) {
        if (mode == OperandMode.Output) {
            assert index < allocatorOutputCount;
            return allocatorOperands.get(index);
        } else if (mode == OperandMode.Input) {
            assert index < allocatorInputCount + allocatorTempInputCount;
            return allocatorOperands.get(index + allocatorOutputCount);
        } else {
            assert mode == OperandMode.Temp;
            assert index < allocatorTempInputCount + allocatorTempCount;
            return allocatorOperands.get(index + allocatorOutputCount + allocatorInputCount);
        }
    }

    public final void setOperandAt(OperandMode mode, int index, CiValue location) {
        assert index < operandCount(mode);
        assert location.kind != CiKind.Illegal;
        assert operandAt(mode, index).isVariable();
        if (mode == OperandMode.Output) {
            assert index < allocatorOutputCount;
            allocatorOperands.set(index, location);
        } else if (mode == OperandMode.Input) {
            assert index < allocatorInputCount + allocatorTempInputCount;
            allocatorOperands.set(index + allocatorOutputCount, location);
        } else {
            assert mode == OperandMode.Temp;
            assert index < allocatorTempInputCount + allocatorTempCount;
            allocatorOperands.set(index + allocatorOutputCount + allocatorInputCount, location);
        }
    }

    public final List<ExceptionHandler> exceptionEdges() {
        if (info != null && info.exceptionHandlers != null) {
            return info.exceptionHandlers;
        }

        return Collections.emptyList();
    }

    @Override
    public String toString() {
        return toString(OperandFormatter.DEFAULT);
    }

    public final String toStringWithIdPrefix() {
        if (id != -1) {
            return String.format("%4d %s", id, toString());
        }
        return "     " + toString();
    }

    protected static String refMapToString(CiDebugInfo debugInfo, OperandFormatter operandFmt) {
        StringBuilder buf = new StringBuilder();
        if (debugInfo.hasStackRefMap()) {
            CiBitMap bm = debugInfo.frameRefMap;
            for (int slot = bm.nextSetBit(0); slot >= 0; slot = bm.nextSetBit(slot + 1)) {
                if (buf.length() != 0) {
                    buf.append(", ");
                }
                buf.append(operandFmt.format(CiStackSlot.get(CiKind.Object, slot)));
            }
        }
        if (debugInfo.hasRegisterRefMap()) {
            CiBitMap bm = debugInfo.registerRefMap;
            for (int reg = bm.nextSetBit(0); reg >= 0; reg = bm.nextSetBit(reg + 1)) {
                if (buf.length() != 0) {
                    buf.append(", ");
                }
                CiRegisterValue register = compilation().target.arch.registers[reg].asValue(CiKind.Object);
                buf.append(operandFmt.format(register));
            }
        }
        return buf.toString();
    }

    protected void appendDebugInfo(StringBuilder buf, OperandFormatter operandFmt, LIRDebugInfo info) {
        if (info != null) {
            buf.append(" [bci:").append(info.state.bci);
            if (info.hasDebugInfo()) {
                CiDebugInfo debugInfo = info.debugInfo();
                String refmap = refMapToString(debugInfo, operandFmt);
                if (refmap.length() != 0) {
                    buf.append(", refmap(").append(refmap.trim()).append(')');
                }
            }
            buf.append(']');
        }
    }

    public String toString(OperandFormatter operandFmt) {
        StringBuilder buf = new StringBuilder(name()).append(' ').append(operationString(operandFmt));
        appendDebugInfo(buf, operandFmt, info);
        return buf.toString();
    }
}
