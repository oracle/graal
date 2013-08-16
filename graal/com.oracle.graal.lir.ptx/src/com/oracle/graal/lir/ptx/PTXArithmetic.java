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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.ptx.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;

// @formatter:off
public enum PTXArithmetic {
    IADD, ISUB, IMUL, IDIV, IDIVREM, IREM, IUDIV, IUREM, IAND, IOR, IXOR, ISHL, ISHR, IUSHR,
    LADD, LSUB, LMUL, LDIV, LDIVREM, LREM, LUDIV, LUREM, LAND, LOR, LXOR, LSHL, LSHR, LUSHR,
    FADD, FSUB, FMUL, FDIV, FREM, FAND, FOR, FXOR,
    DADD, DSUB, DMUL, DDIV, DREM, DAND, DOR, DXOR,
    INEG, LNEG, FNEG, DNEG, INOT, LNOT,
    I2L, L2I, I2B, I2C, I2S,
    F2D, D2F,
    I2F, I2D, F2I, D2I,
    L2F, L2D, F2L, D2L,
    MOV_I2F, MOV_L2D, MOV_F2I, MOV_D2L;


    /**
     * Unary operation with separate source and destination operand. 
     */
    public static class Unary2Op extends PTXLIRInstruction {
        @Opcode private final PTXArithmetic opcode;
        @Def({REG}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue x;

        public Unary2Op(PTXArithmetic opcode, AllocatableValue result, AllocatableValue x) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
            PTXMove.move(tasm, masm, result, x);
            emit(tasm, masm, opcode, result, x, null);
        }
    }

    /**
     * Unary operation with single operand for source and destination. 
     */
    public static class Unary1Op extends PTXLIRInstruction {
        @Opcode private final PTXArithmetic opcode;
        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue x;

        public Unary1Op(PTXArithmetic opcode, AllocatableValue result, AllocatableValue x) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
            emit(tasm, masm, opcode, result);
        }
    }

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

    protected static void emit(@SuppressWarnings("unused") TargetMethodAssembler tasm,
                               PTXAssembler masm, PTXArithmetic opcode, Value result) {
        switch (opcode) {
        case L2I:  masm.and_b32(asIntReg(result), asIntReg(result), 0xFFFFFFFF); break;
        case I2C:  masm.and_b16(asIntReg(result), asIntReg(result), (short) 0xFFFF); break;
            default:
                throw GraalInternalError.shouldNotReachHere("missing: "  + opcode);
        }
    }

    public static void emit(TargetMethodAssembler tasm, PTXAssembler masm, PTXArithmetic opcode, Value dst, Value src, LIRFrameState info) {
        int exceptionOffset = -1;
        if (isRegister(src)) {
            switch (opcode) {
                case INEG:
                    masm.neg_s32(asIntReg(dst), asIntReg(src));
                    break;
                case INOT:
                    masm.not_s32(asIntReg(dst), asIntReg(src));
                    break;
                case LNOT:
                    masm.not_s64(asLongReg(dst), asLongReg(src));
                    break;
                case I2L:
                    masm.cvt_s64_s32(asLongReg(dst), asIntReg(src));
                    break;
                case I2C:
                    masm.cvt_b16_s32(asIntReg(dst), asIntReg(src));
                    break;
                case I2B:
                    masm.cvt_s8_s32(asIntReg(dst), asIntReg(src));
                    break;
                case I2F:
                    masm.cvt_f32_s32(asFloatReg(dst), asIntReg(src));
                    break;
                case I2D:
                    masm.cvt_f64_s32(asDoubleReg(dst), asIntReg(src));
                    break;
                case FNEG:
                    masm.neg_f32(asFloatReg(dst), asFloatReg(src));
                    break;
                case DNEG:
                    masm.neg_f64(asDoubleReg(dst), asDoubleReg(src));
                    break;
                case F2I:
                    masm.cvt_s32_f32(asIntReg(dst), asFloatReg(src));
                    break;
                case F2L:
                    masm.cvt_s64_f32(asLongReg(dst), asFloatReg(src));
                    break;
                case F2D:
                    masm.cvt_f64_f32(asDoubleReg(dst), asFloatReg(src));
                    break;
                case D2I:
                    masm.cvt_s32_f64(asIntReg(dst), asDoubleReg(src));
                    break;
                case D2L:
                    masm.cvt_s64_f64(asLongReg(dst), asDoubleReg(src));
                    break;
                case D2F:
                    masm.cvt_f32_f64(asFloatReg(dst), asDoubleReg(src));
                    break;
                case LSHL:
                    masm.shl_s64(asLongReg(dst), asLongReg(dst), asIntReg(src));
                    break;
                case LSHR:
                    masm.shr_s64(asLongReg(dst), asLongReg(dst), asIntReg(src));
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere("missing: "  + opcode);
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
            switch (opcode) {
            case ISUB:  masm.sub_s32(asIntReg(dst),    tasm.asIntConst(src1),    asIntReg(src2));         break;
            case IAND:  masm.and_b32(asIntReg(dst),    asIntReg(src2),           tasm.asIntConst(src1));  break;
            case IDIV:  masm.div_s32(asIntReg(dst),    tasm.asIntConst(src1),    asIntReg(src2));         break;
            case FSUB:  masm.sub_f32(asFloatReg(dst),  tasm.asFloatConst(src1),  asFloatReg(src2));       break;
            case FDIV:  masm.div_f32(asFloatReg(dst),  tasm.asFloatConst(src1),  asFloatReg(src2));       break;
            case DSUB:  masm.sub_f64(asDoubleReg(dst), tasm.asDoubleConst(src1), asDoubleReg(src2));      break;
            case DDIV:  masm.div_f64(asDoubleReg(dst), tasm.asDoubleConst(src1), asDoubleReg(src2));      break;
            default:
                throw GraalInternalError.shouldNotReachHere();
            }
        } else if (isConstant(src2)) {
            switch (opcode) {
            case IADD:  masm.add_s32(asIntReg(dst),    asIntReg(src1),    tasm.asIntConst(src2));    break;
            case ISUB:  masm.sub_s32(asIntReg(dst),    asIntReg(src1),    tasm.asIntConst(src2));    break;
            case IMUL:  masm.mul_s32(asIntReg(dst),    asIntReg(src1),    tasm.asIntConst(src2));    break;
            case IAND:  masm.and_b32(asIntReg(dst),    asIntReg(src1),    tasm.asIntConst(src2));    break;
            case ISHL:  masm.shl_s32(asIntReg(dst),    asIntReg(src1),    tasm.asIntConst(src2));    break;
            case ISHR:  masm.shr_s32(asIntReg(dst),    asIntReg(src1),    tasm.asIntConst(src2));    break;
            case IUSHR: masm.shr_u32(asIntReg(dst),    asIntReg(src1),    tasm.asIntConst(src2));    break;
            case IXOR:  masm.xor_b32(asIntReg(dst),    asIntReg(src1),    tasm.asIntConst(src2));    break;
            case LXOR:  masm.xor_b64(asLongReg(dst),   asLongReg(src1),   tasm.asLongConst(src2));   break;
            case LUSHR: masm.shr_u64(asLongReg(dst),   asLongReg(src1),   tasm.asLongConst(src2));   break;
            case FADD:  masm.add_f32(asFloatReg(dst),  asFloatReg(src1),  tasm.asFloatConst(src2));  break;
            case FMUL:  masm.mul_f32(asFloatReg(dst),  asFloatReg(src1),  tasm.asFloatConst(src2));  break;
            case FDIV:  masm.div_f32(asFloatReg(dst),  asFloatReg(src1),  tasm.asFloatConst(src2));  break;
            case DADD:  masm.add_f64(asDoubleReg(dst), asDoubleReg(src1), tasm.asDoubleConst(src2)); break;
            case DMUL:  masm.mul_f64(asDoubleReg(dst), asDoubleReg(src1), tasm.asDoubleConst(src2)); break;
            case DDIV:  masm.div_f64(asDoubleReg(dst), asDoubleReg(src1), tasm.asDoubleConst(src2)); break;
            default:
                throw GraalInternalError.shouldNotReachHere();
            }
        } else {
            switch (opcode) {
            // case A:  new Add(Int, dst, src1, src2);
            // case S:  new Sub(Int, dst, src1, src2);
            // case U:  new Shl(UnsignedInt, dst, src1, src2);
            // case L:  new Shl(UnsignedLong, dst, src1, src2);
            // case F:  new Add(Float, dst, src1, src2);
            // case D:  new Mul(Double, dst, src1, src2);
            case IADD:  masm.add_s32(asIntReg(dst),    asIntReg(src1),    asIntReg(src2));    break;
            case ISUB:  masm.sub_s32(asIntReg(dst),    asIntReg(src1),    asIntReg(src2));    break;
            case IMUL:  masm.mul_s32(asIntReg(dst),    asIntReg(src1),    asIntReg(src2));    break;
            case IDIV:  masm.div_s32(asIntReg(dst),    asIntReg(src1),    asIntReg(src2));    break;
            case IAND:  masm.and_b32(asIntReg(dst),    asIntReg(src1),    asIntReg(src2));    break;
            case IOR:    masm.or_b32(asIntReg(dst),    asIntReg(src1),    asIntReg(src2));    break;
            case IXOR:  masm.xor_b32(asIntReg(dst),    asIntReg(src1),    asIntReg(src2));    break;
            case ISHL:  masm.shl_s32(asIntReg(dst),    asIntReg(src1),    asIntReg(src2));    break;
            case ISHR:  masm.shr_s32(asIntReg(dst),    asIntReg(src1),    asIntReg(src2));    break;
            case IUSHR: masm.shr_u32(asIntReg(dst),    asIntReg(src1),    asIntReg(src2));    break;
            case IREM:  masm.rem_s32(asIntReg(dst),    asIntReg(src1),    asIntReg(src2));    break;
            case LADD:  masm.add_s64(asLongReg(dst),   asLongReg(src1),   asLongReg(src2));   break;
            case LSUB:  masm.sub_s64(asLongReg(dst),   asLongReg(src1),   asLongReg(src2));   break;
            case LMUL:  masm.mul_s64(asLongReg(dst),   asLongReg(src1),   asLongReg(src2));   break;
            case LDIV:  masm.div_s64(asLongReg(dst),   asLongReg(src1),   asLongReg(src2));   break;
            case LAND:  masm.and_b64(asLongReg(dst),   asLongReg(src1),   asLongReg(src2));   break;
            case LOR:    masm.or_b64(asLongReg(dst),   asLongReg(src1),   asLongReg(src2));   break;
            case LXOR:  masm.xor_b64(asLongReg(dst),   asLongReg(src1),   asLongReg(src2));   break;
            case LSHL:  masm.shl_s64(asLongReg(dst),   asLongReg(src1),   asLongReg(src2));   break;
            case LSHR:  masm.shr_s64(asLongReg(dst),   asLongReg(src1),   asLongReg(src2));   break;
            case LUSHR: masm.shr_u64(asLongReg(dst),   asLongReg(src1),   asIntReg(src2));    break;
            case LREM:  masm.rem_s64(asLongReg(dst),   asLongReg(src1),   asLongReg(src2));   break;
            case FADD:  masm.add_f32(asFloatReg(dst),  asFloatReg(src1),  asFloatReg(src2));  break;
            case FSUB:  masm.sub_f32(asFloatReg(dst),  asFloatReg(src1),  asFloatReg(src2));  break;
            case FMUL:  masm.mul_f32(asFloatReg(dst),  asFloatReg(src1),  asFloatReg(src2));  break;
            case FDIV:  masm.div_f32(asFloatReg(dst),  asFloatReg(src1),  asFloatReg(src2));  break;
            case FREM:  masm.div_f32(asFloatReg(dst),  asFloatReg(src1),  asFloatReg(src2));  break;
            case DADD:  masm.add_f64(asDoubleReg(dst), asDoubleReg(src1), asDoubleReg(src2)); break;
            case DSUB:  masm.sub_f64(asDoubleReg(dst), asDoubleReg(src1), asDoubleReg(src2)); break;
            case DMUL:  masm.mul_f64(asDoubleReg(dst), asDoubleReg(src1), asDoubleReg(src2)); break;
            case DDIV:  masm.div_f64(asDoubleReg(dst), asDoubleReg(src1), asDoubleReg(src2)); break;
            case DREM:  masm.div_f64(asDoubleReg(dst), asDoubleReg(src1), asDoubleReg(src2)); break;
            default:
                throw GraalInternalError.shouldNotReachHere("missing: "  + opcode);
            }
        }

        if (info != null) {
            assert exceptionOffset != -1;
            tasm.recordImplicitException(exceptionOffset, info);
        }
    }

    private static void verifyKind(PTXArithmetic opcode, Value result, Value x, Value y) {
        if (((opcode.name().startsWith("I") && result.getKind() == Kind.Int && x.getKind().getStackKind() == Kind.Int && y.getKind().getStackKind() == Kind.Int)
            || (opcode.name().startsWith("L") && result.getKind() == Kind.Long && x.getKind() == Kind.Long && y.getKind() == Kind.Long)
            || (opcode.name().startsWith("F") && result.getKind() == Kind.Float && x.getKind() == Kind.Float && y.getKind() == Kind.Float)
            || (opcode.name().startsWith("D") && result.getKind() == Kind.Double && x.getKind() == Kind.Double && y.getKind() == Kind.Double)) == false) {
                throw GraalInternalError.shouldNotReachHere("opcode: "  + opcode.name() + " x: " + x.getKind() + " y: " + y.getKind());
        }
    }
}
