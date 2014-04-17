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
import static com.oracle.graal.lir.LIRValueUtil.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.ptx.*;
import com.oracle.graal.asm.ptx.PTXMacroAssembler.Add;
import com.oracle.graal.asm.ptx.PTXMacroAssembler.And;
import com.oracle.graal.asm.ptx.PTXMacroAssembler.Cvt;
import com.oracle.graal.asm.ptx.PTXMacroAssembler.Div;
import com.oracle.graal.asm.ptx.PTXMacroAssembler.Mul;
import com.oracle.graal.asm.ptx.PTXMacroAssembler.Neg;
import com.oracle.graal.asm.ptx.PTXMacroAssembler.Not;
import com.oracle.graal.asm.ptx.PTXMacroAssembler.Or;
import com.oracle.graal.asm.ptx.PTXMacroAssembler.Rem;
import com.oracle.graal.asm.ptx.PTXMacroAssembler.Shl;
import com.oracle.graal.asm.ptx.PTXMacroAssembler.Shr;
import com.oracle.graal.asm.ptx.PTXMacroAssembler.Sub;
import com.oracle.graal.asm.ptx.PTXMacroAssembler.Ushr;
import com.oracle.graal.asm.ptx.PTXMacroAssembler.Xor;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;

// @formatter:off
public enum PTXArithmetic {
    IADD, ISUB, IMUL, IDIV, IDIVREM, IREM, IUDIV, IUREM, IAND, IOR, IXOR, ISHL, ISHR, IUSHR,
    LADD, LSUB, LMUL, LDIV, LDIVREM, LREM, LUDIV, LUREM, LAND, LOR, LXOR, LSHL, LSHR, LUSHR,
    FADD, FSUB, FMUL, FDIV, FREM, FAND, FOR, FXOR,
    DADD, DSUB, DMUL, DDIV, DREM, DAND, DOR, DXOR,
    INEG, LNEG, FNEG, DNEG, INOT, LNOT;


    public static class ConvertOp extends PTXLIRInstruction {
        private final Kind from;
        private final Kind to;
        @Def({REG}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue x;

        public ConvertOp(AllocatableValue result, AllocatableValue x, Kind to, Kind from) {
            this.from = from;
            this.to = to;
            this.result = result;
            this.x = x;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, PTXMacroAssembler masm) {
            Variable dst = (Variable) result;
            Variable src = (Variable) x;
            if (from == Kind.Long && to == Kind.Int) {
                new And(dst, src, Constant.forLong(0xFFFFFFFF)).emit(masm);
            } else if ((from == Kind.Int || from == Kind.Long) && to == Kind.Short) {
                new And(dst, src, Constant.forInt((short) 0xFFFF)).emit(masm);
            } else {
                new Cvt((Variable) result, (Variable) x, to, from).emit(masm);
            }
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
        public void emitCode(CompilationResultBuilder crb, PTXMacroAssembler masm) {
            emit(crb, masm, opcode, result, x, null);
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
        public void emitCode(CompilationResultBuilder crb, PTXMacroAssembler masm) {
            emit(crb, masm, opcode, result, x, y, null);
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
        public void emitCode(CompilationResultBuilder crb, PTXMacroAssembler masm) {
            emit(crb, masm, opcode, result, x, y, null);
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
        public void emitCode(CompilationResultBuilder crb, PTXMacroAssembler masm) {
            if (sameRegister(result, y)) {
                emit(crb, masm, opcode, result, x, null);
            } else {
                PTXMove.move(crb, masm, result, x);
                emit(crb, masm, opcode, result, y, null);
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
        public void emitCode(CompilationResultBuilder crb, PTXMacroAssembler masm) {
            emit(crb, masm, opcode, result, x, y, null);
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
        public void emitCode(CompilationResultBuilder crb, PTXMacroAssembler masm) {
            emit(crb, masm, opcode, result, y, state);
        }

        @Override
        protected void verify() {
            super.verify();
            verifyKind(opcode, result, x, y);
        }
    }

    public static void emit(CompilationResultBuilder crb, PTXMacroAssembler masm, PTXArithmetic opcode, Value dst, Value src, LIRFrameState info) {
        int exceptionOffset = -1;
        Variable dest = (Variable) dst;

        if (isVariable(src)) {
            Variable source = (Variable) src;
            switch (opcode) {
                case INEG:
                case FNEG:
                case DNEG:
                    new Neg(dest, source).emit(masm);
                    break;
                case INOT:
                case LNOT:
                    new Not(dest, source).emit(masm);
                    break;
                case LSHL:
                    new Shl(dest, dest, src).emit(masm);
                    break;
                case LSHR:
                    new Shr(dest, dest, src).emit(masm);
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere("missing: "  + opcode);
            }
        } else if (isConstant(src)) {
            switch (opcode) {
                case ISUB:
                    new Sub(dest, dest, src).emit(masm);
                    break;
                case IAND:
                    new And(dest, dest, src).emit(masm);
                    break;
                case LSHL:
                    new Shl(dest, dest, src).emit(masm);
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        } else {
            switch (opcode) {
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }

        if (info != null) {
            assert exceptionOffset != -1;
            crb.recordImplicitException(exceptionOffset, info);
        }
    }

    public static void emit(CompilationResultBuilder crb, PTXMacroAssembler masm, PTXArithmetic opcode,
                            Value dst, Value src1, Value src2, LIRFrameState info) {
        int exceptionOffset = -1;
        Variable dest = (Variable) dst;

        switch (opcode) {
            case IADD:
            case LADD:
            case FADD:
            case DADD:
                new Add(dest, src1, src2).emit(masm);
                break;
            case IAND:
            case LAND:
                new And(dest, src1, src2).emit(masm);
                break;
            case ISUB:
            case LSUB:
            case FSUB:
            case DSUB:
                new Sub(dest, src1, src2).emit(masm);
                break;
            case IMUL:
            case LMUL:
            case FMUL:
            case DMUL:
                new Mul(dest, src1, src2).emit(masm);
                break;
            case IDIV:
            case LDIV:
            case FDIV:
            case DDIV:
                new Div(dest, src1, src2).emit(masm);
                break;
            case IOR:
            case LOR:
                new Or(dest, src1, src2).emit(masm);
                break;
            case IXOR:
            case LXOR:
                new Xor(dest, src1, src2).emit(masm);
                break;
            case ISHL:
            case LSHL:
                new Shl(dest, src1, src2).emit(masm);
                break;
            case ISHR:
            case LSHR:
                new Shr(dest, src1, src2).emit(masm);
                break;
            case IUSHR:
            case LUSHR:
                new Ushr(dest, src1, src2).emit(masm);
                break;
            case IREM:
            case LREM:
            case FREM:
            case DREM:
                new Rem(dest, src1, src2).emit(masm);
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("missing: "  + opcode);
        }

        if (info != null) {
            assert exceptionOffset != -1;
            crb.recordImplicitException(exceptionOffset, info);
        }
    }

    private static void verifyKind(PTXArithmetic opcode, Value result, Value x, Value y) {
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
                rk = result.getKind();
                xk = x.getKind();
                yk = y.getKind();
                assert rk == Kind.Long && xk == Kind.Long && yk == Kind.Long;
                break;
            case LSHL:
            case LSHR:
            case LUSHR:
                rk = result.getKind();
                xk = x.getKind();
                yk = y.getKind();
                assert rk == Kind.Long && xk == Kind.Long && (yk == Kind.Int || yk == Kind.Long);
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
                assert rk == Kind.Double && xk == Kind.Double && yk == Kind.Double :
                    "opcode=" + opcode + ", result kind=" + rk + ", x kind=" + xk + ", y kind=" + yk;
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("missing: " + opcode);
        }
    }
}
