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

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.*;
import static com.oracle.graal.asm.sparc.SPARCMacroAssembler.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;
import static com.oracle.graal.sparc.SPARC.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.spi.*;

public class SPARCBitManipulationOp extends SPARCLIRInstruction {

    public enum IntrinsicOpcode {
        IPOPCNT, LPOPCNT, IBSR, LBSR, BSF;
    }

    @Opcode private final IntrinsicOpcode opcode;
    @Def protected AllocatableValue result;
    @Use({REG}) protected AllocatableValue input;
    @Def({REG}) protected Value scratch;

    public SPARCBitManipulationOp(IntrinsicOpcode opcode, AllocatableValue result, AllocatableValue input, LIRGeneratorTool gen) {
        this.opcode = opcode;
        this.result = result;
        this.input = input;
        if (opcode == IntrinsicOpcode.IBSR || opcode == IntrinsicOpcode.LBSR) {
            scratch = gen.newVariable(input.getKind());
        }
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        Register dst = asIntReg(result);
        if (isRegister(input)) {
            Register src = asRegister(input);
            switch (opcode) {
                case IPOPCNT:
                    // clear upper word for 64 bit POPC
                    new Srl(src, g0, dst).emit(masm);
                    new Popc(src, dst).emit(masm);
                    break;
                case LPOPCNT:
                    new Popc(src, dst).emit(masm);
                    break;
                case BSF:
                    Kind tkind = input.getKind();
                    if (tkind == Kind.Int) {
                        new Sub(src, 1, dst).emit(masm);
                        new Andn(dst, src, dst).emit(masm);
                        new Srl(dst, g0, dst).emit(masm);
                        new Popc(dst, dst).emit(masm);
                    } else if (tkind == Kind.Long) {
                        new Sub(src, 1, dst).emit(masm);
                        new Andn(dst, src, dst).emit(masm);
                        new Popc(dst, dst).emit(masm);
                    } else {
                        throw GraalInternalError.shouldNotReachHere("missing: " + tkind);
                    }
                    break;
                case IBSR: {
                    Kind ikind = input.getKind();
                    assert ikind == Kind.Int;
                    Register tmp = asRegister(scratch);
                    new Srl(src, 1, tmp).emit(masm);
                    new Srl(src, 0, dst).emit(masm);
                    new Or(src, tmp, dst).emit(masm);
                    new Srl(dst, 2, tmp).emit(masm);
                    new Or(dst, tmp, dst).emit(masm);
                    new Srl(dst, 4, tmp).emit(masm);
                    new Or(dst, tmp, dst).emit(masm);
                    new Srl(dst, 8, tmp).emit(masm);
                    new Or(dst, tmp, dst).emit(masm);
                    new Srl(dst, 16, tmp).emit(masm);
                    new Or(dst, tmp, dst).emit(masm);
                    new Popc(dst, dst).emit(masm);
                    new Mov(ikind.getBitCount(), tmp).emit(masm);
                    new Sub(tmp, dst, dst).emit(masm);
                    break;
                }
                case LBSR: {
                    Kind lkind = input.getKind();
                    assert lkind == Kind.Int;
                    Register tmp = asRegister(scratch);
                    new Srlx(src, 1, tmp).emit(masm);
                    new Or(src, tmp, dst).emit(masm);
                    new Srlx(dst, 2, tmp).emit(masm);
                    new Or(dst, tmp, dst).emit(masm);
                    new Srlx(dst, 4, tmp).emit(masm);
                    new Or(dst, tmp, dst).emit(masm);
                    new Srlx(dst, 8, tmp).emit(masm);
                    new Or(dst, tmp, dst).emit(masm);
                    new Srlx(dst, 16, tmp).emit(masm);
                    new Or(dst, tmp, dst).emit(masm);
                    new Srlx(dst, 32, tmp).emit(masm);
                    new Or(dst, tmp, dst).emit(masm);
                    new Popc(dst, dst).emit(masm);
                    new Mov(lkind.getBitCount(), tmp).emit(masm);
                    new Sub(tmp, dst, dst).emit(masm);
                    break;
                }
                default:
                    throw GraalInternalError.shouldNotReachHere();

            }
        } else if (isConstant(input) && isSimm13(crb.asIntConst(input))) {
            switch (opcode) {
                case IPOPCNT:
                    new Popc(crb.asIntConst(input), dst).emit(masm);
                    break;
                case LPOPCNT:
                    new Popc(crb.asIntConst(input), dst).emit(masm);
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        } else {
            throw GraalInternalError.shouldNotReachHere();
            // SPARCAddress src = (SPARCAddress) crb.asAddress(input);
            // switch (opcode) {
            // case IPOPCNT:
            // new Ldsw(src, tmp).emit(masm);
            // // clear upper word for 64 bit POPC
            // new Srl(tmp, g0, dst).emit(masm);
            // new Popc(tmp, dst).emit(masm);
            // break;
            // case LPOPCNT:
            // new Ldx(src, tmp).emit(masm);
            // new Popc(tmp, dst).emit(masm);
            // break;
            // case BSF:
            // assert input.getKind() == Kind.Int;
            // new Ldsw(src, tmp).emit(masm);
            // new Srl(tmp, 1, tmp).emit(masm);
            // new Srl(tmp, 0, dst).emit(masm);
            // new Or(tmp, tmp, dst).emit(masm);
            // new Srl(dst, 2, tmp).emit(masm);
            // new Or(dst, tmp, dst).emit(masm);
            // new Srl(dst, 4, tmp).emit(masm);
            // new Or(dst, tmp, dst).emit(masm);
            // new Srl(dst, 8, tmp).emit(masm);
            // new Or(dst, tmp, dst).emit(masm);
            // new Srl(dst, 16, tmp).emit(masm);
            // new Or(dst, tmp, dst).emit(masm);
            // new Popc(dst, dst).emit(masm);
            // new Mov(Kind.Int.getBitCount(), tmp).emit(masm);
            // new Sub(tmp, dst, dst).emit(masm);
            // break;
            // case IBSR:
            // // masm.bsrl(dst, src);
            // // countLeadingZerosI_bsr masm.bsrq(dst, src);
            // // masm.bsrl(dst, src);
            // case LBSR:
            // // masm.bsrq(dst, src);
            // default:
            // throw GraalInternalError.shouldNotReachHere("missing: " + opcode);
            // }
        }
    }

}
