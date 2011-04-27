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
 * The {@code LIROp0} class definition.
 *
 * @author Marcelo Cintra
 *
 */
public class LIROp0 extends LIRInstruction {

    /**
     * Creates a LIROp0 instruction.
     *
     * @param opcode the opcode of the new instruction
     */
    public LIROp0(LIROpcode opcode) {
        this(opcode, CiValue.IllegalValue);
    }

    /**
     * Creates a LIROp0 instruction.
     *
     * @param opcode the opcode of the new instruction
     * @param result the result operand to the new instruction
     */
    public LIROp0(LIROpcode opcode, CiValue result) {
        this(opcode, result, null);
    }

    /**
     * Creates a LIROp0 instruction.
     *
     * @param opcode the opcode of the new instruction
     * @param result the result operand to the new instruction
     * @param info used to emit debug information associated to this instruction
     */
    public LIROp0(LIROpcode opcode, CiValue result, LIRDebugInfo info) {
        super(opcode, result, info, false);
        assert isInRange(opcode, LIROpcode.BeginOp0, LIROpcode.EndOp0) : "Opcode " + opcode + " is invalid for a LIROP0 instruction";
    }

    /**
     * Emit assembly code for this instruction.
     * @param masm the target assembler
     */
    @Override
    public void emitCode(LIRAssembler masm) {
        masm.emitOp0(this);
    }
}
