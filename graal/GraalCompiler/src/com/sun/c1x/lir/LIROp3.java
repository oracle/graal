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

import com.sun.cri.ci.*;

/**
 * The {@code LIROp3} class definition.
 *
 * @author Marcelo Cintra
 *
 */
public class LIROp3 extends LIRInstruction {

    /**
     * Creates a new LIROp3 instruction. A LIROp3 instruction represents a LIR instruction
     * that has three input operands.
     *
     * @param opcode the instruction's opcode
     * @param opr1 the first input operand
     * @param opr2 the second input operand
     * @param opr3 the third input operand
     * @param result the result operand
     * @param info the debug information, used for deoptimization, associated to this instruction
     */
    public LIROp3(LIROpcode opcode, CiValue opr1, CiValue opr2, CiValue opr3, CiValue result, LIRDebugInfo info) {
        super(opcode, result, info, false, 1, 1, opr1, opr2, opr3);
        assert isInRange(opcode, LIROpcode.BeginOp3, LIROpcode.EndOp3) : "The " + opcode + " is not a valid LIROp3 opcode";
    }

    /**
     * Gets the opr1 of this class.
     *
     * @return the opr1
     */
    public CiValue opr1() {
        return operand(0);
    }

    /**
     * Gets the opr2 of this class.
     *
     * @return the opr2
     */
    public CiValue opr2() {
        return operand(1);
    }

    /**
     * Emits assembly code for this instruction.
     *
     * @param masm the target assembler
     */
    @Override
    public void emitCode(LIRAssembler masm) {
        masm.emitOp3(this);
    }
}
