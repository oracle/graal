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

import com.sun.c1x.ir.*;
import com.sun.cri.ci.*;

/**
 * The {@code LIROp2} class represents a LIR instruction that performs an operation on two operands.
 *
 * @author Marcelo Cintra
 * @author Thomas Wuerthinger
 * @author Ben L. Titzer
 */
public class LIROp2 extends LIRInstruction {

    final Condition condition;

    /**
     * Constructs a new LIROp2 instruction.
     *
     * @param opcode the instruction's opcode
     * @param condition the instruction's condition
     * @param opr1 the first input operand
     * @param opr2 the second input operand
     * @param info the object holding information needed to emit debug information
     */
    public LIROp2(LIROpcode opcode, Condition condition, CiValue opr1, CiValue opr2, LIRDebugInfo info) {
        super(opcode, CiValue.IllegalValue, info, false, 0, 0, opr1, opr2);
        this.condition = condition;
        assert opcode == LIROpcode.Cmp : "Instruction opcode should be of type LIROpcode.Cmp";
    }

    /**
     * Constructs a new LIROp2 instruction.
     *
     * @param opcode the instruction's opcode
     * @param condition the instruction's condition
     * @param opr1 the first input operand
     * @param opr2 the second input operand
     * @param result the operand that holds the result of this instruction
     */
    public LIROp2(LIROpcode opcode, Condition condition, CiValue opr1, CiValue opr2, CiValue result) {
        super(opcode, result, null, false, 0, 0, opr1, opr2);
        this.condition = condition;
        assert opcode == LIROpcode.Cmove : "Instruction opcode should be of type LIROpcode.Cmove";
    }

    /**
     * Constructs a new LIROp2 instruction.
     *
     * @param opcode the instruction's opcode
     * @param opr1 the first input operand
     * @param opr2 the second input operand
     * @param result the operand that holds the result of this instruction
     * @param info the object holding information needed to emit debug information
     * @param kind the kind of this instruction
     */
    public LIROp2(LIROpcode opcode, CiValue opr1, CiValue opr2, CiValue result, LIRDebugInfo info, CiKind kind, boolean hasCall) {
        super(opcode, result, info, hasCall, 0, 0, opr1, opr2);
        this.condition = null;
        assert opcode != LIROpcode.Cmp && isInRange(opcode, LIROpcode.BeginOp2, LIROpcode.EndOp2) : "The " + opcode + " is not a valid LIROp2 opcode";
    }

    /**
     * Constructs a new LIROp2 instruction.
     *
     * @param opcode the instruction's opcode
     * @param opr1 the instruction's first operand
     * @param opr2 the instruction's second operand
     * @param result the operand that holds the result of this instruction
     * @param info the object holding information needed to emit debug information
     */
    public LIROp2(LIROpcode opcode, CiValue opr1, CiValue opr2, CiValue result, LIRDebugInfo info) {
        this(opcode, opr1, opr2, result, info, CiKind.Illegal, false);
    }

    /**
     * Constructs a new LIROp2 instruction.
     *
     * @param opcode the instruction's opcode
     * @param opr1 the instruction's first operand
     * @param opr2 the instruction's second operand
     * @param result the operand that holds the result of this instruction
     */
    public LIROp2(LIROpcode opcode, CiValue opr1, CiValue opr2, CiValue result) {
        this(opcode, opr1, opr2, result, (LIRDebugInfo) null);
    }

    /**
     * Constructs a new LIROp2 instruction.
     *
     * @param opcode the instruction's opcode
     * @param opr1 the first input operand
     * @param opr2 the second input operand
     * @param result the operand that holds the result of this instruction
     * @param tmp the temporary operand used by this instruction
     */
    public LIROp2(LIROpcode opcode, CiValue opr1, CiValue opr2, CiValue result, CiValue tmp) {
        super(opcode, result, null, false, 0, 1, opr1, opr2, tmp);
        this.condition = null;
        assert opcode != LIROpcode.Cmp && isInRange(opcode, LIROpcode.BeginOp2, LIROpcode.EndOp2) : "The " + opcode + " is not a valid LIROp2 opcode";
    }

    /**
     * Gets the first input operand.
     *
     * @return opr1 the first input operand
     */
    public CiValue operand1() {
        return operand(0);
    }

    /**
     * Gets the second input operand.
     *
     * @return opr2 the second input operand
     */
    public CiValue operand2() {
        return operand(1);
    }

    /**
     * Gets the temporary operand of this instruction.
     *
     * @return tmp the temporary operand of this instruction
     *
     */
    public CiValue tmp() {
        return operand(2);
    }

    /**
     * Gets the condition of this instruction, if it is a Cmp or Cmove LIR instruction.
     *
     * @return condition the condition of this instruction
     */
    public Condition condition() {
        assert code == LIROpcode.Cmp || code == LIROpcode.Cmove : "Field access only valid for cmp and cmove";
        return condition;
    }

    /**
     * Emit target assembly code for this instruction.
     *
     * @param masm the target assembler
     */
    @Override
    public void emitCode(LIRAssembler masm) {
        masm.emitOp2(this);
    }

    /**
     * Prints this instruction.
     */
    @Override
    public String operationString(OperandFormatter operandFmt) {
        if (code == LIROpcode.Cmove) {
            return condition.toString() + " " + super.operationString(operandFmt);
        }
        return super.operationString(operandFmt);
    }
}

