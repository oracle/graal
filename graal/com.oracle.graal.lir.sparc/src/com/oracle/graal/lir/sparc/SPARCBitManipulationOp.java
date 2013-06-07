/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.sparc;

import static com.oracle.graal.asm.sparc.SPARCAssembler.Popc;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Srl;
import static com.oracle.graal.asm.sparc.SPARCAssembler.isSimm13;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.sparc.SPARC;

public class SPARCBitManipulationOp extends SPARCLIRInstruction {

    public enum IntrinsicOpcode {
        IPOPCNT, LPOPCNT, IBSR, LBSR, BSF;
    }

    @Opcode private final IntrinsicOpcode opcode;
    @Def protected AllocatableValue result;
    @Use({OperandFlag.REG, OperandFlag.STACK}) protected AllocatableValue input;

    public SPARCBitManipulationOp(IntrinsicOpcode opcode, AllocatableValue result, AllocatableValue input) {
        this.opcode = opcode;
        this.result = result;
        this.input = input;
    }

    @Override
    @SuppressWarnings("unused")
    public void emitCode(TargetMethodAssembler tasm, SPARCAssembler masm) {
        Register dst = ValueUtil.asIntReg(result);
        if (ValueUtil.isRegister(input)) {
            Register src = ValueUtil.asRegister(input);
            switch (opcode) {
                case IPOPCNT:
                    // clear upper word for 64 bit POPC
                    new Srl(masm, src, SPARC.g0, dst);
                    new Popc(masm, src, dst);
                    break;
                case LPOPCNT:
                    new Popc(masm, src, dst);
                    break;
                case BSF:  // masm.bsfq(dst, src);
                case IBSR:  // masm.bsrl(dst, src);
                case LBSR:  // masm.bsrq(dst, src);
                default:
                    throw GraalInternalError.shouldNotReachHere("missing: " + opcode);

            }
        } else if (ValueUtil.isConstant(input) && isSimm13(tasm.asIntConst(input))) {
            switch (opcode) {
                case IPOPCNT:
                    new Popc(masm, tasm.asIntConst(input), dst);
                    break;
                case LPOPCNT:
                    new Popc(masm, tasm.asIntConst(input), dst);
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere("missing: " + opcode);
            }
        } else {
            SPARCAddress src = (SPARCAddress) tasm.asAddress(input);
            switch (opcode) {
                case IPOPCNT:
                    // masm.popcntl(dst, src);
                    break;
                case LPOPCNT:
                    // masm.popcntq(dst, src);
                    break;
                case BSF:
                    // masm.bsfq(dst, src);
                    break;
                case IBSR:
                    // masm.bsrl(dst, src);
                    break;
                case LBSR:
                    // masm.bsrq(dst, src);
                    break;
            }
        }
    }

}
