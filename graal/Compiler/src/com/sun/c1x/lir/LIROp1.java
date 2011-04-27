/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.c1x.util.*;
import com.sun.cri.ci.*;

/**
 * The {@code LIROp1} class definition. The LIROp1 instruction has only one input operand.
 *
 * @author Marcelo Cintra
 */
public class LIROp1 extends LIRInstruction {

    public enum LIRMoveKind {
        Normal, Volatile, Unaligned
    }

    public final CiKind kind;          // the operand type
    public final LIRMoveKind moveKind; // flag that indicate the kind of move

    /**
     * Constructs a new LIROp1 instruction.
     *
     * @param opcode the instruction's opcode
     * @param opr the first input operand
     * @param result the operand that holds the result of this instruction
     * @param kind the kind of this instruction
     * @param info the object holding information needed to emit debug information
     */
    public LIROp1(LIROpcode opcode, CiValue opr, CiValue result, CiKind kind, LIRDebugInfo info) {
        super(opcode, result, info, false, 0, 0, opr);
        this.kind = kind;
        this.moveKind = LIRMoveKind.Normal;
        assert isInRange(opcode, LIROpcode.BeginOp1, LIROpcode.EndOp1) : "The " + opcode + " is not a valid LIROp1 opcode";
    }

    /**
     * Constructs a new LIROp1 instruction.
     *
     * @param opcode the instruction's opcode
     * @param opr the first input operand
     * @param result the operand that holds the result of this instruction
     * @param kind the kind of this instruction
     */
    public LIROp1(LIROpcode opcode, CiValue opr, CiValue result, CiKind kind) {
        this(opcode, opr, result, kind, null);
    }

    /**
     * Constructs a new LIROp1 instruction.
     *
     * @param opcode the instruction's opcode
     * @param opr the first input operand
     * @param result the operand that holds the result of this instruction
     */
    public LIROp1(LIROpcode opcode, CiValue opr, CiValue result) {
        this(opcode, opr, result, CiKind.Illegal);
    }

    /**
     * Constructs a new LIROp1 instruction.
     *
     * @param opcode the instruction's opcode
     * @param opr the first input operand
     */
    public LIROp1(LIROpcode opcode, CiValue opr) {
        this(opcode, opr, CiValue.IllegalValue);
    }

    /**
     * Constructs a new LIROp1 instruction.
     *
     * @param moveKind the kind of move the instruction represents
     * @param operand the single input operand
     * @param result the operand that holds the result of this instruction
     * @param kind the kind of this instruction
     * @param info the object holding information needed to emit debug information
     */
    public LIROp1(LIRMoveKind moveKind, CiValue operand, CiValue result, CiKind kind, LIRDebugInfo info) {
        super(LIROpcode.Move, result, info, false, 0, 0, operand);
        this.kind = kind;
        this.moveKind = moveKind;
    }

    /**
     * Constructs a new LIROp1 instruction.
     *
     * @param opcode the instruction's opcode
     * @param opr the first input operand
     * @param info the object holding information needed to emit debug information
     */
    public LIROp1(LIROpcode opcode, CiValue opr, LIRDebugInfo info) {
        super(opcode, CiValue.IllegalValue, info, false, 0, 0, opr);
        this.kind = CiKind.Illegal;
        this.moveKind = LIRMoveKind.Normal;
        assert isInRange(opcode, LIROpcode.BeginOp1, LIROpcode.EndOp1) : "The " + opcode + " is not a valid LIROp1 opcode";
    }

    /**
     * Gets the input operand of this instruction.
     *
     * @return opr the input operand.
     */
    public CiValue operand() {
        return operand(0);
    }

    /**
     * Gets the kind of move of this instruction.
     *
     * @return flags the constant that represents the move kind.
     */
    public LIRMoveKind moveKind() {
        assert code == LIROpcode.Move : "The opcode must be of type LIROpcode.Move in LIROp1";
        return moveKind;
    }

    @Override
    public void emitCode(LIRAssembler masm) {
        masm.emitOp1(this);
    }

    @Override
    public String name() {
        if (code == LIROpcode.Move) {
            switch (moveKind()) {
                case Normal:
                    return "move";
                case Unaligned:
                    return "unaligned move";
                case Volatile:
                    return "volatile_move";
                default:
                    throw Util.shouldNotReachHere();
            }
        } else {
            return super.name();
        }
    }

    @Override
    public boolean verify() {
        switch (code) {
            case Move:
                assert (operand().isLegal()) && (result().isLegal()) : "Operand and result must be valid in a LIROp1 move instruction.";
                break;
            case NullCheck:
                assert operand().isVariableOrRegister() : "Operand must be a register in a LIROp1 null check instruction.";
                break;
            case Return:
                assert operand().isVariableOrRegister() || operand().isIllegal() : "Operand must be (register | illegal) in a LIROp1 return instruction.";
                break;
        }
        return true;
    }
}
