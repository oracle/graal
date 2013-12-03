/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.hsail;

import com.oracle.graal.lir.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.hsail.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.asm.*;

/**
 * Defines bit manipulation operations.
 */
public class HSAILBitManipulationOp extends HSAILLIRInstruction {

    public enum IntrinsicOpcode {
        IPOPCNT, LPOPCNT, IBSR, LBSR, BSF;
    }

    @Opcode private final IntrinsicOpcode opcode;
    @Def protected Value result;
    @Use({OperandFlag.REG}) protected Value input;

    public HSAILBitManipulationOp(IntrinsicOpcode opcode, Value result, Value input) {
        this.opcode = opcode;
        this.result = result;
        this.input = input;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, HSAILAssembler masm) {
        switch (opcode) {
            case IPOPCNT:
                throw GraalInternalError.shouldNotReachHere();
            case LPOPCNT:
                throw GraalInternalError.shouldNotReachHere();
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }
}
