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

import com.sun.c1x.globalstub.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;

/**
 * The {@code LIRConvert} class definition.
 *
 * @author Marcelo Cintra
 *
 */
public class LIRConvert extends LIROp1 {

    public final int bytecode;
    public GlobalStub globalStub;

    /**
     * Constructs a new instruction LIRConvert for a given operand.
     *
     * @param bytecode the opcode of the bytecode for this conversion
     * @param operand the input operand for this instruction
     * @param result the result operand for this instruction
     */
    public LIRConvert(int bytecode, CiValue operand, CiValue result) {
        super(LIROpcode.Convert, operand, result);
        this.bytecode = bytecode;
    }

    /**
     * Emits target assembly code for this LIRConvert instruction.
     *
     * @param masm the LIRAssembler
     */
    @Override
    public void emitCode(LIRAssembler masm) {
        masm.emitConvert(this);
    }

    /**
     * Prints this instruction to a LogStream.
     */
    @Override
    public String operationString(OperandFormatter operandFmt) {
        return "[" + Bytecodes.nameOf(bytecode) + "] " + super.operationString(operandFmt);
    }
}
