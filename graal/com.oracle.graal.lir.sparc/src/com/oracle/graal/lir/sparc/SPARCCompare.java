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
package com.oracle.graal.lir.sparc;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Cmp;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;

public enum SPARCCompare {
    ICMP,
    LCMP,
    ACMP,
    FCMP,
    DCMP;

    public static class CompareOp extends SPARCLIRInstruction {

        @Opcode private final SPARCCompare opcode;
        @Use({REG}) protected Value x;
        @Use({REG, CONST}) protected Value y;

        public CompareOp(SPARCCompare opcode, Value x, Value y) {
            this.opcode = opcode;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            emit(crb, masm, opcode, x, y);
        }

        @Override
        protected void verify() {
            super.verify();
            // @formatter:off
            assert  (name().startsWith("I") &&
                            (!(x.getKind() == Kind.Int) || y.getKind().getStackKind() == Kind.Int) &&
                            (!(x.getKind() == Kind.Short) || y.getKind().getStackKind() == Kind.Int) &&
                            (!(x.getKind() == Kind.Byte) || y.getKind().getStackKind() == Kind.Int)) ||
                    (name().startsWith("L") && x.getKind() == Kind.Long && y.getKind() == Kind.Long) ||
                    (name().startsWith("A") && x.getKind() == Kind.Object && y.getKind() == Kind.Object) ||
                    (name().startsWith("F") && x.getKind() == Kind.Float && y.getKind() == Kind.Float) ||
                    (name().startsWith("D") && x.getKind() == Kind.Double && y.getKind() == Kind.Double)
                    : "Name; " + name() + " x: " + x + " y: " + y;

            // @formatter:on
        }
    }

    public static void emit(CompilationResultBuilder crb, SPARCMacroAssembler masm, SPARCCompare opcode, Value x, Value y) {
        if (isRegister(y)) {
            switch (opcode) {
                case ICMP:
                    new Cmp(asIntReg(x), asIntReg(y)).emit(masm);
                    break;
                case LCMP:
                    new Cmp(asLongReg(x), asLongReg(y)).emit(masm);
                    break;
                case ACMP:
                    new Cmp(asObjectReg(x), asObjectReg(y)).emit(masm);
                    break;
                case FCMP:
                    new Fcmp(CC.Fcc0, Opfs.Fcmps, asFloatReg(x), asFloatReg(y)).emit(masm);
                    break;
                case DCMP:
                    new Fcmp(CC.Fcc0, Opfs.Fcmpd, asDoubleReg(x), asDoubleReg(y)).emit(masm);
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        } else {
            assert isConstant(y);
            switch (opcode) {
                case ICMP:
                    assert isSimm13(crb.asIntConst(y));
                    new Cmp(asIntReg(x), crb.asIntConst(y)).emit(masm);
                    break;
                case LCMP:
                    assert isSimm13(crb.asIntConst(y));
                    new Cmp(asLongReg(x), crb.asIntConst(y)).emit(masm);
                    break;
                case ACMP:
                    if (((Constant) y).isNull()) {
                        new Cmp(asObjectReg(x), 0).emit(masm);
                        break;
                    } else {
                        throw GraalInternalError.shouldNotReachHere("Only null object constants are allowed in comparisons");
                    }
                case FCMP:
                    new Fcmp(CC.Fcc0, Opfs.Fcmps, asFloatReg(x), asFloatReg(y)).emit(masm);
                    break;
                case DCMP:
                    new Fcmp(CC.Fcc0, Opfs.Fcmpd, asDoubleReg(x), asDoubleReg(y)).emit(masm);
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }
    }
}
