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
import static com.oracle.graal.asm.sparc.SPARCAssembler.Add;
import static com.oracle.graal.asm.sparc.SPARCAssembler.And;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Fadds;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Faddd;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Fdivs;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Fdivd;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Fdtoi;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Fmuls;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Fmuld;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Fnegs;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Fnegd;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Fstoi;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Fsubs;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Fsubd;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Mulx;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Or;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Sdivx;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Sll;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Sllx;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Srl;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Srlx;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Sra;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Sub;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Xor;
import static com.oracle.graal.asm.sparc.SPARCAssembler.isSimm13;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.CONST;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.HINT;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.REG;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.STACK;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.sparc.SPARCAssembler;
import com.oracle.graal.graph.GraalInternalError;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.asm.TargetMethodAssembler;

//@formatter:off
public enum SPARCArithmetic {
    // @formatter:off
    IADD, ISUB, IMUL, IDIV, IDIVREM, IREM, IUDIV, IUREM, IAND, IOR, IXOR, ISHL, ISHR, IUSHR,
    LADD, LSUB, LMUL, LDIV, LDIVREM, LREM, LUDIV, LUREM, LAND, LOR, LXOR, LSHL, LSHR, LUSHR,
    FADD, FSUB, FMUL, FDIV, FREM, FAND, FOR, FXOR,
    DADD, DSUB, DMUL, DDIV, DREM, DAND, DOR, DXOR,
    INEG, LNEG, FNEG, DNEG,
    I2L, L2I, I2B, I2C, I2S,
    F2D, D2F,
    I2F, I2D, F2I, D2I,
    L2F, L2D, F2L, D2L,
    MOV_I2F, MOV_L2D, MOV_F2I, MOV_D2L;

    /**
     * Binary operation with single source/destination operand and one constant.
     */
    public static class BinaryRegConst extends SPARCLIRInstruction {
        @Opcode private final SPARCArithmetic opcode;
        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue x;
        protected Constant y;

        public BinaryRegConst(SPARCArithmetic opcode, AllocatableValue result, AllocatableValue x, Constant y) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, SPARCAssembler masm) {
            SPARCMove.move(tasm, masm, result, x);
            emit(tasm, masm, opcode, result, y, null);
        }

        @Override
        public void verify() {
            super.verify();
            verifyKind(opcode, result, x, y);
        }
    }

    /**
     * Unary operation with separate source and destination operand.
     */
    public static class Unary2Op extends SPARCLIRInstruction {

        @Opcode
        private final SPARCArithmetic opcode;
        @Def({ REG })
        protected AllocatableValue result;
        @Use({ REG, STACK })
        protected AllocatableValue x;

        public Unary2Op(SPARCArithmetic opcode, AllocatableValue result, AllocatableValue x) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, SPARCAssembler masm) {
            SPARCMove.move(tasm, masm, result, x);
            emit(tasm, masm, opcode, result, x, null);
        }
    }

    /**
     * Unary operation with single operand for source and destination.
     */
    public static class Unary1Op extends SPARCLIRInstruction {

        @Opcode
        private final SPARCArithmetic opcode;
        @Def({ REG, HINT })
        protected AllocatableValue result;
        @Use({ REG, STACK })
        protected AllocatableValue x;

        public Unary1Op(SPARCArithmetic opcode, AllocatableValue result, AllocatableValue x) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, SPARCAssembler masm) {
            emit(masm, opcode, result);
        }
    }

    public static class Op1Stack extends SPARCLIRInstruction {

        @Opcode
        private final SPARCArithmetic opcode;
        @Def({ REG, HINT })
        protected Value result;
        @Use({ REG, STACK, CONST })
        protected Value x;

        public Op1Stack(SPARCArithmetic opcode, Value result, Value x) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, SPARCAssembler masm) {
            emit(tasm, masm, opcode, result, x, null);
        }
    }

    public static class Op2Stack extends SPARCLIRInstruction {

        @Opcode
        private final SPARCArithmetic opcode;
        @Def({ REG, HINT })
        protected Value result;
        @Use({ REG, STACK, CONST })
        protected Value x;
        @Alive({ REG, STACK, CONST })
        protected Value y;

        public Op2Stack(SPARCArithmetic opcode, Value result, Value x, Value y) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, SPARCAssembler masm) {
            emit(tasm, masm, opcode, result, x, y, null);
        }

        @Override
        public void verify() {
            super.verify();
            verifyKind(opcode, result, x, y);
        }
    }

    public static class Op2Reg extends SPARCLIRInstruction {

        @Opcode
        private final SPARCArithmetic opcode;
        @Def({ REG, HINT })
        protected Value result;
        @Use({ REG, STACK, CONST })
        protected Value x;
        @Alive({ REG, CONST })
        protected Value y;

        public Op2Reg(SPARCArithmetic opcode, Value result, Value x, Value y) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, SPARCAssembler masm) {
            emit(tasm, masm, opcode, result, x, y, null);
        }

        @Override
        public void verify() {
            super.verify();
            verifyKind(opcode, result, x, y);
        }
    }

    public static class ShiftOp extends SPARCLIRInstruction {

        @Opcode
        private final SPARCArithmetic opcode;
        @Def({ REG, HINT })
        protected Value result;
        @Use({ REG, STACK, CONST })
        protected Value x;
        @Alive({ REG, CONST })
        protected Value y;

        public ShiftOp(SPARCArithmetic opcode, Value result, Value x, Value y) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, SPARCAssembler masm) {
            emit(tasm, masm, opcode, result, x, y, null);
        }

        @Override
        public void verify() {
            super.verify();
            verifyKind(opcode, result, x, x);
            assert y.getKind().getStackKind() == Kind.Int;
        }
    }

    @SuppressWarnings("unused")
    protected static void emit(SPARCAssembler masm, SPARCArithmetic opcode, Value result) {
        switch (opcode) {
        case L2I:
            new And(masm, asIntReg(result), -1, asIntReg(result));
            break;
        case I2C:
            new Sll(masm, asIntReg(result), 16, asIntReg(result));
            new Srl(masm, asIntReg(result), 16, asIntReg(result));
            break;
        default:
            throw GraalInternalError.shouldNotReachHere("missing: " + opcode);
        }
    }

    @SuppressWarnings("unused")
    public static void emit(TargetMethodAssembler tasm, SPARCAssembler masm,
            SPARCArithmetic opcode, Value dst, Value src1, Value src2, LIRFrameState info) {
        int exceptionOffset = -1;
        if (isConstant(src1)) {
            switch (opcode) {
            case ISUB:
                assert isSimm13(tasm.asIntConst(src1));
                new Add(masm, asIntReg(src2), -(tasm.asIntConst(src1)), asIntReg(dst));
                break;
            case IAND:
                throw new InternalError("NYI");
            case IDIV:
                assert isSimm13(tasm.asIntConst(src1));
                throw new InternalError("NYI");
                // new Sdivx(masm, asIntReg(src1), asIntReg(src2),
                // asIntReg(dst));
            case FSUB:
                throw new InternalError("NYI");
            case FDIV:
                throw new InternalError("NYI");
            case DSUB:
                throw new InternalError("NYI");
            case DDIV:
                throw new InternalError("NYI");
            default:
                throw GraalInternalError.shouldNotReachHere();
            }
        } else if (isConstant(src2)) {
            switch (opcode) {
            case IADD:
                assert isSimm13(tasm.asIntConst(src2));
                new Add(masm, asIntReg(src1), tasm.asIntConst(src2), asIntReg(dst));
                break;
            case ISUB:
                assert isSimm13(tasm.asIntConst(src2));
                new Sub(masm, asIntReg(src1), tasm.asIntConst(src2), asIntReg(dst));
                break;
            case IMUL:
                assert isSimm13(tasm.asIntConst(src2));
                new Mulx(masm, asIntReg(src1), tasm.asIntConst(src2), asIntReg(dst));
                break;
            case IAND:
                assert isSimm13(tasm.asIntConst(src2));
                new And(masm, asIntReg(src1), tasm.asIntConst(src2), asIntReg(dst));
                break;
            case ISHL:
                assert isSimm13(tasm.asIntConst(src2));
                new Sll(masm, asIntReg(src1), tasm.asIntConst(src2), asIntReg(dst));
                break;
            case ISHR:
                assert isSimm13(tasm.asIntConst(src2));
                new Srl(masm, asIntReg(src1), tasm.asIntConst(src2), asIntReg(dst));
                break;
            case IUSHR:
                assert isSimm13(tasm.asIntConst(src2));
                new Sra(masm, asIntReg(src1), tasm.asIntConst(src2), asIntReg(dst));
                break;
            case IXOR:
                assert isSimm13(tasm.asIntConst(src2));
                new Xor(masm, asIntReg(src1), tasm.asIntConst(src2), asIntReg(dst));
                break;
            case LXOR:
                assert isSimm13(tasm.asIntConst(src2));
                new Add(masm, asLongReg(src1), tasm.asIntConst(src2), asLongReg(dst));
                break;
            case LUSHR:
                throw new InternalError("NYI");
            case FADD:
                throw new InternalError("NYI");
            case FMUL:
                throw new InternalError("NYI");
            case FDIV:
                throw new InternalError("NYI");
            case DADD:
                throw new InternalError("NYI");
            case DMUL:
                throw new InternalError("NYI");
            case DDIV:
                throw new InternalError("NYI");
            default:
                throw GraalInternalError.shouldNotReachHere();
            }
        } else {
            switch (opcode) {
            case IADD:
                new Add(masm, asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case ISUB:
                new Sub(masm, asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case IMUL:
                new Mulx(masm, asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case IDIV:
                new Sdivx(masm, asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case IAND:
                new And(masm, asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case IOR:
                new Or(masm, asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case IXOR:
                new Xor(masm, asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case ISHL:
                new Sll(masm, asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case ISHR:
                new Srl(masm, asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case IUSHR:
                new Sra(masm, asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case IREM:
                throw new InternalError("NYI");
            case LADD:
                new Add(masm, asLongReg(src1), asLongReg(src2), asLongReg(dst));
                break;
            case LSUB:
                new Sub(masm, asLongReg(src1), asLongReg(src2), asLongReg(dst));
                break;
            case LMUL:
                new Mulx(masm, asLongReg(src1), asLongReg(src2), asLongReg(dst));
                break;
            case LDIV:
                new Sdivx(masm, asLongReg(src1), asLongReg(src2),
                        asLongReg(dst));
                break;
            case LAND:
                new And(masm, asLongReg(src1), asLongReg(src2), asLongReg(dst));
                break;
            case LOR:
                new Or(masm, asLongReg(src1), asLongReg(src2), asLongReg(dst));
                break;
            case LXOR:
                new Xor(masm, asLongReg(src1), asLongReg(src2), asLongReg(dst));
                break;
            case LSHL:
                new Sll(masm, asLongReg(src1), asLongReg(src2), asLongReg(dst));
                break;
            case LSHR:
                new Srl(masm, asLongReg(src1), asLongReg(src2), asLongReg(dst));
                break;
            case LUSHR:
                new Sra(masm, asLongReg(src1), asIntReg(src2), asLongReg(dst));
                break;
            case LDIVREM:
            case LUDIV:
            case LUREM:
            case LREM:
                throw new InternalError("NYI");
            case FADD:
                new Fadds(masm, asFloatReg(src1), asFloatReg(src2),
                        asFloatReg(dst));
                break;
            case FSUB:
                new Fsubs(masm, asFloatReg(src1), asFloatReg(src2),
                        asFloatReg(dst));
                break;
            case FMUL:
                new Fmuls(masm, asFloatReg(src1), asFloatReg(src2),
                        asFloatReg(dst));
                break;
            case FDIV:
                new Fdivs(masm, asFloatReg(src1), asFloatReg(src2),
                        asFloatReg(dst));
                break;
            case FREM:
                throw new InternalError("NYI");
            case DADD:
                new Faddd(masm, asDoubleReg(src1), asDoubleReg(src2),
                        asDoubleReg(dst));
                break;
            case DSUB:
                new Fsubd(masm, asDoubleReg(src1), asDoubleReg(src2),
                        asDoubleReg(dst));
                break;
            case DMUL:
                new Fmuld(masm, asDoubleReg(src1), asDoubleReg(src2),
                        asDoubleReg(dst));
                break;
            case DDIV:
                new Fdivd(masm, asDoubleReg(src1), asDoubleReg(src2),
                        asDoubleReg(dst));
                break;
            case DREM:
                throw new InternalError("NYI");
            default:
                throw GraalInternalError.shouldNotReachHere("missing: "
                        + opcode);
            }
        }

        if (info != null) {
            assert exceptionOffset != -1;
            tasm.recordImplicitException(exceptionOffset, info);
        }
    }

    @SuppressWarnings("unused")
    public static void emit(TargetMethodAssembler tasm, SPARCAssembler masm,
            SPARCArithmetic opcode, Value dst, Value src, LIRFrameState info) {
        int exceptionOffset = -1;
        if (isRegister(src)) {
            switch (opcode) {
            case I2L:
                new Sra(masm, asIntReg(src), 0, asLongReg(dst));
                break;
            case I2B:
                new Sll(masm, asIntReg(src), 24, asIntReg(src));
                new Srl(masm, asIntReg(dst), 24, asIntReg(src));
                break;
            case I2F:
                new Fstoi(masm, asIntReg(src), asFloatReg(dst));
                break;
            case I2D:
                new Fdtoi(masm, asIntReg(src), asDoubleReg(dst));
                break;
            case FNEG:
                new Fnegs(masm, asFloatReg(src), asFloatReg(dst));
                break;
            case DNEG:
                new Fnegd(masm, asDoubleReg(src), asDoubleReg(dst));
                break;
            case LSHL:
                new Sllx(masm, asLongReg(dst), asIntReg(src), asLongReg(dst));
                break;
            case LSHR:
                new Srlx(masm, asLongReg(dst), asIntReg(src), asLongReg(dst));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("missing: "
                        + opcode);
            }
        } else if (isConstant(src)) {
            switch (opcode) {
            default:
                throw GraalInternalError.shouldNotReachHere("missing: "
                        + opcode);
            }
        } else {
            switch (opcode) {
            default:
                throw GraalInternalError.shouldNotReachHere("missing: "
                        + opcode);
            }
        }

        if (info != null) {
            assert exceptionOffset != -1;
            tasm.recordImplicitException(exceptionOffset, info);
        }
    }

    private static void verifyKind(SPARCArithmetic opcode, Value result, Value x, Value y) {
        Kind rk;
        Kind xk;
        Kind yk;
        Kind xsk;
        Kind ysk;

        switch (opcode) {
        case IADD:
        case ISUB:
        case IMUL:
        case IDIV:
        case IREM:
        case IAND:
        case IOR:
        case IXOR:
        case ISHL:
        case ISHR:
        case IUSHR:
            rk = result.getKind();
            xsk = x.getKind().getStackKind();
            ysk = y.getKind().getStackKind();

            assert rk == Kind.Int && xsk == Kind.Int && ysk == Kind.Int;
            break;
        case LADD:
        case LSUB:
        case LMUL:
        case LDIV:
        case LREM:
        case LAND:
        case LOR:
        case LXOR:
        case LSHL:
        case LSHR:
        case LUSHR:
            rk = result.getKind();
            xk = x.getKind();
            yk = y.getKind();

            assert rk == Kind.Long && xk == Kind.Long && yk == Kind.Long;
            break;
        case FADD:
        case FSUB:
        case FMUL:
        case FDIV:
        case FREM:
            rk = result.getKind();
            xk = x.getKind();
            yk = y.getKind();

            assert rk == Kind.Float && xk == Kind.Float && yk == Kind.Float;
            break;
        case DADD:
        case DSUB:
        case DMUL:
        case DDIV:
        case DREM:
            rk = result.getKind();
            xk = x.getKind();
            yk = y.getKind();

            assert rk == Kind.Double && xk == Kind.Double && yk == Kind.Double;
            break;
        default:
            throw new InternalError("NYI: " + opcode);
        }
    }
}
