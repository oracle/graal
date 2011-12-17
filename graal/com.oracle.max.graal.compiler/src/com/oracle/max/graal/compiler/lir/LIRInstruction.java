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
package com.oracle.max.graal.compiler.lir;

import com.oracle.max.graal.compiler.asm.*;
import com.oracle.max.graal.compiler.lir.LIRDebugInfo.ValueProcedure;
import com.oracle.max.graal.compiler.util.*;
import com.sun.cri.ci.*;
import com.sun.cri.ci.CiValue.Formatter;

/**
 * The {@code LIRInstruction} class definition.
 */
public abstract class LIRInstruction {

    public static final CiValue[] NO_OPERANDS = {};

    public static final OperandMode[] OPERAND_MODES = OperandMode.values();

    /**
     * Constants denoting how a LIR instruction uses an operand.
     */
    public enum OperandMode {
        /**
         * The value must have been defined before. It is alive before the instruction until the beginning of the
         * instruction, but not necessarily throughout the instruction. A register assigned to it can also be assigend
         * to a Temp or Output operand. The value can be used again after the instruction, so the instruction must not
         * modify the register.
         */
        Input,

        /**
         * The value must have been defined before. It is alive before the instruction and throughout the instruction. A
         * register assigned to it cannot be assigned to a Temp or Output operand. The value can be used again after the
         * instruction, so the instruction must not modify the register.
         */
        Alive,

        /**
         * The value must not have been defined before, and must not be used after the instruction. The instruction can
         * do whatever it wants with the register assigned to it (or not use it at all).
         */
        Temp,

        /**
         * The value must not have been defined beforee. The instruction has to assign a value to the register. The
         * value can (and most likely will) be used after the instruction.
         */
        Output,
    }

    /**
     * The opcode of this instruction.
     */
    public final LIROpcode code;

    /**
     * The result operand for this instruction (modified by the register allocator).
     */
    protected CiValue result;

    /**
     * The input operands for this instruction (modified by the register allocator).
     */
    protected final CiValue[] inputs;

    /**
     * The alive operands for this instruction (modified by the register allocator).
     */
    protected final CiValue[] alives;

    /**
     * The temp operands for this instruction (modified by the register allocator).
     */
    protected final CiValue[] temps;

    /**
     * Used to emit debug information.
     */
    public final LIRDebugInfo info;

    /**
     * Instruction id for register allocation.
     */
    private int id;

    /**
     * Constructs a new LIR instruction that has input and temp operands.
     *
     * @param opcode the opcode of the new instruction
     * @param result the operand that holds the operation result of this instruction. This will be
     *            {@link CiValue#IllegalValue} for instructions that do not produce a result.
     * @param info the {@link LIRDebugInfo} info that is to be preserved for the instruction. This will be {@code null} when no debug info is required for the instruction.
     * @param inputs the input operands for the instruction.
     * @param temps the temp operands for the instruction.
     */
    public LIRInstruction(LIROpcode opcode, CiValue result, LIRDebugInfo info, CiValue[] inputs, CiValue[] alives, CiValue[] temps) {
        this.code = opcode;
        this.result = result;
        this.inputs = inputs;
        this.alives = alives;
        this.temps = temps;
        this.info = info;
        this.id = -1;
    }

    public abstract void emitCode(TargetMethodAssembler tasm);


    public final int id() {
        return id;
    }

    public final void setId(int id) {
        this.id = id;
    }

    /**
     * Gets an input operand of this instruction.
     *
     * @param index the index of the operand requested.
     * @return the {@code index}'th input operand.
     */
    public final CiValue input(int index) {
        return inputs[index];
    }

    /**
     * Gets an alive operand of this instruction.
     *
     * @param index the index of the operand requested.
     * @return the {@code index}'th alive operand.
     */
    public final CiValue alive(int index) {
        return alives[index];
    }

    /**
     * Gets a temp operand of this instruction.
     *
     * @param index the index of the operand requested.
     * @return the {@code index}'th temp operand.
     */
    public final CiValue temp(int index) {
        return temps[index];
    }

    /**
     * Gets the result operand for this instruction.
     *
     * @return return the result operand
     */
    public final CiValue result() {
        return result;
    }

    /**
     * Gets the instruction name.
     */
    public String name() {
        return code.toString();
    }

    public boolean hasOperands() {
        return inputs.length > 0 || alives.length > 0 || temps.length > 0 || info != null || hasCall();
    }

    public final int operandCount(OperandMode mode) {
        switch (mode) {
            case Output: return result.isLegal() ? 1 : 0;
            case Input:  return inputs.length;
            case Alive:  return alives.length;
            case Temp:   return temps.length;
            default:     throw Util.shouldNotReachHere();
        }
    }

    public final CiValue operandAt(OperandMode mode, int index) {
        assert index < operandCount(mode);
        switch (mode) {
            case Output: return result;
            case Input:  return inputs[index];
            case Alive:  return alives[index];
            case Temp:   return temps[index];
            default:     throw Util.shouldNotReachHere();
        }
    }

    public final void setOperandAt(OperandMode mode, int index, CiValue location) {
        assert index < operandCount(mode);
        assert location.kind != CiKind.Illegal;
        assert operandAt(mode, index).isVariable() && operandAt(mode, index).kind == location.kind;
        switch (mode) {
            case Output: result = location; break;
            case Input:  inputs[index] = location; break;
            case Alive:  alives[index] = location; break;
            case Temp:   temps[index] = location; break;
            default:     throw Util.shouldNotReachHere();
        }
    }

    public final void forEachOperand(OperandMode mode, ValueProcedure proc) {
        for (int i = 0; i < operandCount(mode); i++) {
            CiValue newValue = proc.doValue(operandAt(mode, i));
            if (newValue != null) {
                setOperandAt(mode, i, newValue);
            }
        }
    }

    /**
     * Returns true when this instruction is a call instruction that destroys all caller-saved registers.
     */
    public boolean hasCall() {
        return false;
    }

    /**
     * Used by the register allocator.  The result operand of this instruction should get
     * the same register assigned as the returned operand.
     * @return The register hint for the output operand, or null if no register hint should be defined.
     */
    public CiValue registerHint() {
        return null;
    }

    /**
     * Used by the register allocator to decide whether an input operand can be assigned a stack slot.
     * Subclasses should override this method when an input can be memory.
     * @param index The index of the operand in {@link #inputs}.
     * @return true if the input operand with the given index can be assigned a stack slot.
     */
    public boolean inputCanBeMemory(int index) {
        return false;
    }


    @Override
    public String toString() {
        return toString(Formatter.DEFAULT);
    }

    public final String toStringWithIdPrefix() {
        if (id != -1) {
            return String.format("%4d %s", id, toString());
        }
        return "     " + toString();
    }

    /**
     * Gets the operation performed by this instruction in terms of its operands as a string.
     */
    public String operationString(Formatter operandFmt) {
        StringBuilder buf = new StringBuilder();
        if (result.isLegal()) {
            buf.append(operandFmt.format(result)).append(" = ");
        }
        if (inputs.length + alives.length > 1) {
            buf.append("(");
        }
        String sep = "";
        for (CiValue input : inputs) {
            buf.append(sep).append(operandFmt.format(input));
            sep = ", ";
        }
        for (CiValue input : alives) {
            buf.append(sep).append(operandFmt.format(input)).append(" ~");
            sep = ", ";
        }
        if (inputs.length + alives.length > 1) {
            buf.append(")");
        }

        if (temps.length > 0) {
            buf.append(" [");
        }
        sep = "";
        for (CiValue temp : temps) {
            buf.append(sep).append(operandFmt.format(temp));
            sep = ", ";
        }
        if (temps.length > 0) {
            buf.append("]");
        }
        return buf.toString();
    }

    protected static String refMapToString(CiDebugInfo debugInfo, Formatter operandFmt) {
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
                buf.append("r").append(reg);
            }
        }
        return buf.toString();
    }

    protected void appendDebugInfo(StringBuilder buf, Formatter operandFmt, LIRDebugInfo info) {
        if (info != null) {
            buf.append(" [bci:").append(info.topFrame.bci);
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

    public String toString(Formatter operandFmt) {
        StringBuilder buf = new StringBuilder(name()).append(' ').append(operationString(operandFmt));
        appendDebugInfo(buf, operandFmt, info);
        return buf.toString();
    }
}
