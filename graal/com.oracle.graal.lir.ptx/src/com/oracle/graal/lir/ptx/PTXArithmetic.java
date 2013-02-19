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
package com.oracle.graal.lir.ptx;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.ptx.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;

// @formatter:off
public enum PTXArithmetic {
    IADD, ISUB, IMUL, IDIV, IDIVREM, IREM, IUDIV, IUREM, IAND, IOR, IXOR, ISHL, ISHR, IUSHR,
    LADD, LSUB, LMUL, LDIV, LDIVREM, LREM, LUDIV, LUREM, LAND, LOR, LXOR, LSHL, LSHR, LUSHR,
    FADD, FSUB, FMUL, FDIV, FAND, FOR, FXOR,
    DADD, DSUB, DMUL, DDIV, DAND, DOR, DXOR,
    INEG, LNEG,
    I2L, L2I, I2B, I2C, I2S,
    F2D, D2F,
    I2F, I2D, F2I, D2I,
    L2F, L2D, F2L, D2L,
    MOV_I2F, MOV_L2D, MOV_F2I, MOV_D2L;


    public static class Op1Reg extends PTXLIRInstruction {
        @Opcode private final PTXArithmetic opcode;
        @Def({REG, HINT}) protected Value result;
        @Use({REG}) protected Value x;

        public Op1Reg(PTXArithmetic opcode, Value result, Value x) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
            emit(tasm, masm, opcode, result, x, null);
        }
    }

    public static class Op1Stack extends PTXLIRInstruction {
        @Opcode private final PTXArithmetic opcode;
        @Def({REG, HINT}) protected Value result;
        @Use({REG, STACK, CONST}) protected Value x;

        public Op1Stack(PTXArithmetic opcode, Value result, Value x) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
            emit(tasm, masm, opcode, result, x, null);
        }
    }

    public static class Op2Stack extends PTXLIRInstruction {
        @Opcode private final PTXArithmetic opcode;
        @Def({REG, HINT}) protected Value result;
        @Use({REG, STACK, CONST}) protected Value x;
        @Alive({REG, STACK, CONST}) protected Value y;

        public Op2Stack(PTXArithmetic opcode, Value result, Value x, Value y) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
            emit(tasm, masm, opcode, result, x, y, null);
        }

        @Override
        public void verify() {
            super.verify();
            verifyKind(opcode, result, x, y);
        }
    }

    public static class Op2Reg extends PTXLIRInstruction {
        @Opcode private final PTXArithmetic opcode;
        @Def({REG, HINT}) protected Value result;
        @Use({REG, STACK, CONST}) protected Value x;
        @Alive({REG, CONST}) protected Value y;

        public Op2Reg(PTXArithmetic opcode, Value result, Value x, Value y) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
            emit(tasm, masm, opcode, result, x, y, null);
        }

        @Override
        public void verify() {
            super.verify();
            verifyKind(opcode, result, x, y);
        }
    }

    public static class Op2RegCommutative extends PTXLIRInstruction {
        @Opcode private final PTXArithmetic opcode;
        @Def({REG, HINT}) protected Value result;
        @Use({REG, STACK, CONST}) protected Value x;
        @Use({REG, CONST}) protected Value y;

        public Op2RegCommutative(PTXArithmetic opcode, Value result, Value x, Value y) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
            if (sameRegister(result, y)) {
                emit(tasm, masm, opcode, result, x, null);
            } else {
                PTXMove.move(tasm, masm, result, x);
                emit(tasm, masm, opcode, result, y, null);
            }
        }

        @Override
        protected void verify() {
            super.verify();
            verifyKind(opcode, result, x, y);
        }
    }

    public static class ShiftOp extends PTXLIRInstruction {
        @Opcode private final PTXArithmetic opcode;
        @Def({REG, HINT}) protected Value result;
        @Use({REG, STACK, CONST}) protected Value x;
        @Alive({REG, CONST}) protected Value y;

        public ShiftOp(PTXArithmetic opcode, Value result, Value x, Value y) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
            emit(tasm, masm, opcode, result, x, y, null);
        }

        @Override
        public void verify() {
            super.verify();
            verifyKind(opcode, result, x, x);
            assert y.getKind().getStackKind() == Kind.Int;
        }
    }

    public static class DivOp extends PTXLIRInstruction {
        @Opcode private final PTXArithmetic opcode;
        @Def protected Value result;
        @Use protected Value x;
        @Alive protected Value y;
        @State protected LIRFrameState state;

        public DivOp(PTXArithmetic opcode, Value result, Value x, Value y, LIRFrameState state) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
            this.state = state;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
            emit(tasm, masm, opcode, result, y, state);
        }

        @Override
        protected void verify() {
            super.verify();
            verifyKind(opcode, result, x, y);
        }
    }


    @SuppressWarnings("unused")
    protected static void emit(TargetMethodAssembler tasm, PTXAssembler masm, PTXArithmetic opcode, Value result) {
        switch (opcode) {
            default:   throw GraalInternalError.shouldNotReachHere();
        }
    }

    public static void emit(TargetMethodAssembler tasm, PTXAssembler masm, PTXArithmetic opcode, Value dst, Value src, LIRFrameState info) {
        int exceptionOffset = -1;
        if (isRegister(src)) {
            Register a = asIntReg(src);
            Register d = asIntReg(dst);
            switch (opcode) {
                case INEG: masm.neg_s32(d, a); break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        } else if (isConstant(src)) {
            switch (opcode) {
                case ISUB: masm.sub_s32(asIntReg(dst), asIntReg(dst), tasm.asIntConst(src)); break;
                case IAND: masm.and_b32(asIntReg(dst), asIntReg(dst), tasm.asIntConst(src)); break;
                default:   throw GraalInternalError.shouldNotReachHere();
            }
        } else {
            switch (opcode) {
                default:   throw GraalInternalError.shouldNotReachHere();
            }
        }

        if (info != null) {
            assert exceptionOffset != -1;
            tasm.recordImplicitException(exceptionOffset, info);
        }
    }

    public static void emit(TargetMethodAssembler tasm, PTXAssembler masm, PTXArithmetic opcode, Value dst, Value src1, Value src2, LIRFrameState info) {
        int exceptionOffset = -1;
        if (isConstant(src1)) {
            int      a = tasm.asIntConst(src1);
            Register b = asIntReg(src2);
            Register d = asIntReg(dst);
            switch (opcode) {
            case ISUB:  masm.sub_s32(d, a, b); break;
            case IAND:  masm.and_b32(d, b, a); break;
            default:    throw GraalInternalError.shouldNotReachHere();
            }
        } else if (isConstant(src2)) {
            Register a = asIntReg(src1);
            int      b = tasm.asIntConst(src2);
            Register d = asIntReg(dst);
            switch (opcode) {
            case IADD:  masm.add_s32(d, a, b); break;
            case IAND:  masm.and_b32(d, a, b); break;
            case IUSHR: masm.shr_u32(d, a, b); break;
            default:    throw GraalInternalError.shouldNotReachHere();
            }
        } else {
            Register a = asIntReg(src1);
            Register b = asIntReg(src2);
            Register d = asIntReg(dst);
            switch (opcode) {
            case IADD:  masm.add_s32(d, a, b); break;
            case ISUB:  masm.sub_s32(d, a, b); break;
            case IMUL:  masm.mul_s32(d, a, b); break;
            default:    throw GraalInternalError.shouldNotReachHere();
            }
        }

        if (info != null) {
            assert exceptionOffset != -1;
            tasm.recordImplicitException(exceptionOffset, info);
        }
    }

    private static void verifyKind(PTXArithmetic opcode, Value result, Value x, Value y) {
        assert (opcode.name().startsWith("I") && result.getKind() == Kind.Int && x.getKind().getStackKind() == Kind.Int && y.getKind().getStackKind() == Kind.Int)
            || (opcode.name().startsWith("L") && result.getKind() == Kind.Long && x.getKind() == Kind.Long && y.getKind() == Kind.Long)
            || (opcode.name().startsWith("F") && result.getKind() == Kind.Float && x.getKind() == Kind.Float && y.getKind() == Kind.Float)
            || (opcode.name().startsWith("D") && result.getKind() == Kind.Double && x.getKind() == Kind.Double && y.getKind() == Kind.Double);
    }
}
