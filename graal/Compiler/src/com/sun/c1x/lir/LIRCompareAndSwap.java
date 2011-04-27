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

import com.sun.cri.ci.*;

/**
 * The {@code LIRCompareAndSwap} class definition.
 *
 * @author Marcelo Cintra
 *
 */
public class LIRCompareAndSwap extends LIRInstruction {

    /**
     * Constructs a new LIRCompareAndSwap instruction.
     * @param addr
     * @param expectedValue
     * @param newValue
     */
    public LIRCompareAndSwap(LIROpcode opcode, CiValue addr, CiValue expectedValue, CiValue newValue) {
        super(opcode, CiValue.IllegalValue, null, false, 0, 0, addr, expectedValue, newValue);
    }

    /**
     * Gets the address of compare and swap.
     *
     * @return the address
     */
    public CiValue address() {
        return operand(0);
    }

    /**
     * Gets the cmpValue of this class.
     *
     * @return the cmpValue
     */
    public CiValue expectedValue() {
        return operand(1);
    }

    /**
     * Gets the newValue of this class.
     *
     * @return the newValue
     */
    public CiValue newValue() {
        return operand(2);
    }

    /**
     * Emits target assembly code for this instruction.
     *
     * @param masm the target assembler
     */
    @Override
    public void emitCode(LIRAssembler masm) {
        masm.emitCompareAndSwap(this);
    }
}
