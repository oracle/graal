/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.amd64;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.amd64.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.asm.amd64.AMD64Assembler.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;

public enum AMD64Arithmetic {
    IADD, ISUB, IMUL, IDIV, IREM, IUDIV, IUREM, IAND, IOR, IXOR, ISHL, ISHR, IUSHR,
    LADD, LSUB, LMUL, LDIV, LREM, LUDIV, LUREM, LAND, LOR, LXOR, LSHL, LSHR, LUSHR,
    FADD, FSUB, FMUL, FDIV, FAND, FOR, FXOR,
    DADD, DSUB, DMUL, DDIV, DAND, DOR, DXOR,
    INEG, LNEG,
    I2L, L2I, I2B, I2C, I2S,
    F2D, D2F,
    I2F, I2D, F2I, D2I,
    L2F, L2D, F2L, D2L,
    MOV_I2F, MOV_L2D, MOV_F2I, MOV_D2L;


    public static class Op1Reg extends AMD64LIRInstruction {
        @Opcode private final AMD64Arithmetic opcode;
        @Def({REG, HINT}) protected Value result;
        @Use({REG}) protected Value x;

        public Op1Reg(AMD64Arithmetic opcode, Value result, Value x) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            emit(tasm, masm, opcode, result, x, null);
        }
    }

    public static class Op1Stack extends AMD64LIRInstruction {
        @Opcode private final AMD64Arithmetic opcode;
        @Def({REG, HINT}) protected Value result;
        @Use({REG, STACK, CONST}) protected Value x;

        public Op1Stack(AMD64Arithmetic opcode, Value result, Value x) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            AMD64Move.move(tasm, masm, result, x);
            emit(tasm, masm, opcode, result);
        }
    }

    public static class Op2Stack extends AMD64LIRInstruction {
        @Opcode private final AMD64Arithmetic opcode;
        @Def({REG, HINT}) protected Value result;
        @Use({REG, STACK, CONST}) protected Value x;
        @Alive({REG, STACK, CONST}) protected Value y;

        public Op2Stack(AMD64Arithmetic opcode, Value result, Value x, Value y) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            AMD64Move.move(tasm, masm, result, x);
            emit(tasm, masm, opcode, result, y, null);
        }

        @Override
        public void verify() {
            super.verify();
            assert differentRegisters(result, y) || sameRegister(x, y);
            verifyKind(opcode, result, x, y);
        }
    }

    public static class Op2Reg extends AMD64LIRInstruction {
        @Opcode private final AMD64Arithmetic opcode;
        @Def({REG, HINT}) protected Value result;
        @Use({REG, STACK, CONST}) protected Value x;
        @Alive({REG, CONST}) protected Value y;

        public Op2Reg(AMD64Arithmetic opcode, Value result, Value x, Value y) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            AMD64Move.move(tasm, masm, result, x);
            emit(tasm, masm, opcode, result, y, null);
        }

        @Override
        public void verify() {
            super.verify();
            assert differentRegisters(result, y) || sameRegister(x, y);
            verifyKind(opcode, result, x, y);
        }
    }

    public static class Op2RegCommutative extends AMD64LIRInstruction {
        @Opcode private final AMD64Arithmetic opcode;
        @Def({REG, HINT}) protected Value result;
        @Use({REG, STACK, CONST}) protected Value x;
        @Use({REG, CONST}) protected Value y;

        public Op2RegCommutative(AMD64Arithmetic opcode, Value result, Value x, Value y) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            if (sameRegister(result, y)) {
                emit(tasm, masm, opcode, result, x, null);
            } else {
                AMD64Move.move(tasm, masm, result, x);
                emit(tasm, masm, opcode, result, y, null);
            }
        }

        @Override
        protected void verify() {
            super.verify();
            verifyKind(opcode, result, x, y);
        }
    }

    public static class ShiftOp extends AMD64LIRInstruction {
        @Opcode private final AMD64Arithmetic opcode;
        @Def({REG, HINT}) protected Value result;
        @Use({REG, STACK, CONST}) protected Value x;
        @Alive({REG, CONST}) protected Value y;

        public ShiftOp(AMD64Arithmetic opcode, Value result, Value x, Value y) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            AMD64Move.move(tasm, masm, result, x);
            emit(tasm, masm, opcode, result, y, null);
        }

        @Override
        public void verify() {
            super.verify();
            assert isConstant(y) || asRegister(y) == AMD64.rcx;
            assert differentRegisters(result, y) || sameRegister(x, y);
            verifyKind(opcode, result, x, x);
            assert y.getKind().getStackKind() == Kind.Int;
        }
    }

    public static class DivOp extends AMD64LIRInstruction {
        @Opcode private final AMD64Arithmetic opcode;
        @Def protected Value result;
        @Use protected Value x;
        @Alive protected Value y;
        @Temp protected Value temp;
        @State protected LIRFrameState state;

        public DivOp(AMD64Arithmetic opcode, Value result, Value x, Value y, LIRFrameState state) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
            this.temp = asRegister(result) == AMD64.rax ? AMD64.rdx.asValue(result.getKind()) : AMD64.rax.asValue(result.getKind());
            this.state = state;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            emit(tasm, masm, opcode, result, y, state);
        }

        @Override
        protected void verify() {
            super.verify();
            // left input in rax, right input in any register but rax and rdx, result quotient in rax, result remainder in rdx
            assert asRegister(x) == AMD64.rax;
            assert differentRegisters(y, AMD64.rax.asValue(), AMD64.rdx.asValue());
            assert (name().endsWith("DIV") && asRegister(result) == AMD64.rax) || (name().endsWith("REM") && asRegister(result) == AMD64.rdx);
            verifyKind(opcode, result, x, y);
        }
    }


    @SuppressWarnings("unused")
    protected static void emit(TargetMethodAssembler tasm, AMD64MacroAssembler masm, AMD64Arithmetic opcode, Value result) {
        switch (opcode) {
            case INEG: masm.negl(asIntReg(result)); break;
            case LNEG: masm.negq(asLongReg(result)); break;
            case L2I:  masm.andl(asIntReg(result), 0xFFFFFFFF); break;
            case I2B:  masm.signExtendByte(asIntReg(result)); break;
            case I2C:  masm.andl(asIntReg(result), 0xFFFF); break;
            case I2S:  masm.signExtendShort(asIntReg(result)); break;
            default:   throw GraalInternalError.shouldNotReachHere();
        }
    }

    public static void emit(TargetMethodAssembler tasm, AMD64MacroAssembler masm, AMD64Arithmetic opcode, Value dst, Value src, LIRFrameState info) {
        int exceptionOffset = -1;
        if (isRegister(src)) {
            switch (opcode) {
                case IADD: masm.addl(asIntReg(dst),  asIntReg(src)); break;
                case ISUB: masm.subl(asIntReg(dst),  asIntReg(src)); break;
                case IAND: masm.andl(asIntReg(dst),  asIntReg(src)); break;
                case IMUL: masm.imull(asIntReg(dst), asIntReg(src)); break;
                case IOR:  masm.orl(asIntReg(dst),   asIntReg(src)); break;
                case IXOR: masm.xorl(asIntReg(dst),  asIntReg(src)); break;
                case ISHL: masm.shll(asIntReg(dst)); break;
                case ISHR: masm.sarl(asIntReg(dst)); break;
                case IUSHR:masm.shrl(asIntReg(dst)); break;

                case LADD: masm.addq(asLongReg(dst),  asLongReg(src)); break;
                case LSUB: masm.subq(asLongReg(dst),  asLongReg(src)); break;
                case LMUL: masm.imulq(asLongReg(dst), asLongReg(src)); break;
                case LAND: masm.andq(asLongReg(dst),  asLongReg(src)); break;
                case LOR:  masm.orq(asLongReg(dst),   asLongReg(src)); break;
                case LXOR: masm.xorq(asLongReg(dst),  asLongReg(src)); break;
                case LSHL: masm.shlq(asLongReg(dst)); break;
                case LSHR: masm.sarq(asLongReg(dst)); break;
                case LUSHR:masm.shrq(asLongReg(dst)); break;

                case FADD: masm.addss(asFloatReg(dst), asFloatReg(src)); break;
                case FSUB: masm.subss(asFloatReg(dst), asFloatReg(src)); break;
                case FMUL: masm.mulss(asFloatReg(dst), asFloatReg(src)); break;
                case FDIV: masm.divss(asFloatReg(dst), asFloatReg(src)); break;
                case FAND: masm.andps(asFloatReg(dst), asFloatReg(src)); break;
                case FOR:  masm.orps(asFloatReg(dst),  asFloatReg(src)); break;
                case FXOR: masm.xorps(asFloatReg(dst), asFloatReg(src)); break;

                case DADD: masm.addsd(asDoubleReg(dst), asDoubleReg(src)); break;
                case DSUB: masm.subsd(asDoubleReg(dst), asDoubleReg(src)); break;
                case DMUL: masm.mulsd(asDoubleReg(dst), asDoubleReg(src)); break;
                case DDIV: masm.divsd(asDoubleReg(dst), asDoubleReg(src)); break;
                case DAND: masm.andpd(asDoubleReg(dst), asDoubleReg(src)); break;
                case DOR:  masm.orpd(asDoubleReg(dst),  asDoubleReg(src)); break;
                case DXOR: masm.xorpd(asDoubleReg(dst), asDoubleReg(src)); break;

                case I2L: masm.movslq(asLongReg(dst), asIntReg(src)); break;
                case F2D: masm.cvtss2sd(asDoubleReg(dst), asFloatReg(src)); break;
                case D2F: masm.cvtsd2ss(asFloatReg(dst), asDoubleReg(src)); break;
                case I2F: masm.cvtsi2ssl(asFloatReg(dst), asIntReg(src)); break;
                case I2D: masm.cvtsi2sdl(asDoubleReg(dst), asIntReg(src)); break;
                case L2F: masm.cvtsi2ssq(asFloatReg(dst), asLongReg(src)); break;
                case L2D: masm.cvtsi2sdq(asDoubleReg(dst), asLongReg(src)); break;
                case F2I:
                    masm.cvttss2sil(asIntReg(dst), asFloatReg(src));
                    emitConvertFixup(tasm, masm, dst, src);
                    break;
                case D2I:
                    masm.cvttsd2sil(asIntReg(dst), asDoubleReg(src));
                    emitConvertFixup(tasm, masm, dst, src);
                    break;
                case F2L:
                    masm.cvttss2siq(asLongReg(dst), asFloatReg(src));
                    emitConvertFixup(tasm, masm, dst, src);
                    break;
                case D2L:
                    masm.cvttsd2siq(asLongReg(dst), asDoubleReg(src));
                    emitConvertFixup(tasm, masm, dst, src);
                    break;
                case MOV_I2F: masm.movdl(asFloatReg(dst), asIntReg(src)); break;
                case MOV_L2D: masm.movdq(asDoubleReg(dst), asLongReg(src)); break;
                case MOV_F2I: masm.movdl(asIntReg(dst), asFloatReg(src)); break;
                case MOV_D2L: masm.movdq(asLongReg(dst), asDoubleReg(src)); break;

                case IDIV:
                case IREM:
                    masm.cdql();
                    exceptionOffset = masm.codeBuffer.position();
                    masm.idivl(asRegister(src));
                    break;

                case LDIV:
                case LREM:
                    masm.cdqq();
                    exceptionOffset = masm.codeBuffer.position();
                    masm.idivq(asRegister(src));
                    break;

                case IUDIV:
                case IUREM:
                    // Must zero the high 64-bit word (in RDX) of the dividend
                    masm.xorq(AMD64.rdx, AMD64.rdx);
                    exceptionOffset = masm.codeBuffer.position();
                    masm.divl(asRegister(src));
                    break;

                case LUDIV:
                case LUREM:
                    // Must zero the high 64-bit word (in RDX) of the dividend
                    masm.xorq(AMD64.rdx, AMD64.rdx);
                    exceptionOffset = masm.codeBuffer.position();
                    masm.divq(asRegister(src));
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        } else if (isConstant(src)) {
            switch (opcode) {
                case IADD: masm.incrementl(asIntReg(dst), tasm.asIntConst(src)); break;
                case ISUB: masm.decrementl(asIntReg(dst), tasm.asIntConst(src)); break;
                case IMUL: masm.imull(asIntReg(dst), asIntReg(dst), tasm.asIntConst(src)); break;
                case IAND: masm.andl(asIntReg(dst), tasm.asIntConst(src)); break;
                case IOR:  masm.orl(asIntReg(dst),  tasm.asIntConst(src)); break;
                case IXOR: masm.xorl(asIntReg(dst), tasm.asIntConst(src)); break;
                case ISHL: masm.shll(asIntReg(dst), tasm.asIntConst(src) & 31); break;
                case ISHR: masm.sarl(asIntReg(dst), tasm.asIntConst(src) & 31); break;
                case IUSHR:masm.shrl(asIntReg(dst), tasm.asIntConst(src) & 31); break;

                case LADD: masm.addq(asLongReg(dst), tasm.asIntConst(src)); break;
                case LSUB: masm.subq(asLongReg(dst), tasm.asIntConst(src)); break;
                case LMUL: masm.imulq(asLongReg(dst), asLongReg(dst), tasm.asIntConst(src)); break;
                case LAND: masm.andq(asLongReg(dst), tasm.asIntConst(src)); break;
                case LOR:  masm.orq(asLongReg(dst),  tasm.asIntConst(src)); break;
                case LXOR: masm.xorq(asLongReg(dst), tasm.asIntConst(src)); break;
                case LSHL: masm.shlq(asLongReg(dst), tasm.asIntConst(src) & 63); break;
                case LSHR: masm.sarq(asLongReg(dst), tasm.asIntConst(src) & 63); break;
                case LUSHR:masm.shrq(asLongReg(dst), tasm.asIntConst(src) & 63); break;

                case FADD: masm.addss(asFloatReg(dst), tasm.asFloatConstRef(src)); break;
                case FSUB: masm.subss(asFloatReg(dst), tasm.asFloatConstRef(src)); break;
                case FMUL: masm.mulss(asFloatReg(dst), tasm.asFloatConstRef(src)); break;
                case FAND: masm.andps(asFloatReg(dst), tasm.asFloatConstRef(src, 16)); break;
                case FOR:  masm.orps(asFloatReg(dst),  tasm.asFloatConstRef(src, 16)); break;
                case FXOR: masm.xorps(asFloatReg(dst), tasm.asFloatConstRef(src, 16)); break;
                case FDIV: masm.divss(asFloatReg(dst), tasm.asFloatConstRef(src)); break;

                case DADD: masm.addsd(asDoubleReg(dst), tasm.asDoubleConstRef(src)); break;
                case DSUB: masm.subsd(asDoubleReg(dst), tasm.asDoubleConstRef(src)); break;
                case DMUL: masm.mulsd(asDoubleReg(dst), tasm.asDoubleConstRef(src)); break;
                case DDIV: masm.divsd(asDoubleReg(dst), tasm.asDoubleConstRef(src)); break;
                case DAND: masm.andpd(asDoubleReg(dst), tasm.asDoubleConstRef(src, 16)); break;
                case DOR:  masm.orpd(asDoubleReg(dst),  tasm.asDoubleConstRef(src, 16)); break;
                case DXOR: masm.xorpd(asDoubleReg(dst), tasm.asDoubleConstRef(src, 16)); break;
                default:   throw GraalInternalError.shouldNotReachHere();
            }
        } else {
            switch (opcode) {
                case IADD: masm.addl(asIntReg(dst), tasm.asIntAddr(src)); break;
                case ISUB: masm.subl(asIntReg(dst), tasm.asIntAddr(src)); break;
                case IAND: masm.andl(asIntReg(dst), tasm.asIntAddr(src)); break;
                case IOR:  masm.orl(asIntReg(dst),  tasm.asIntAddr(src)); break;
                case IXOR: masm.xorl(asIntReg(dst), tasm.asIntAddr(src)); break;

                case LADD: masm.addq(asLongReg(dst), tasm.asLongAddr(src)); break;
                case LSUB: masm.subq(asLongReg(dst), tasm.asLongAddr(src)); break;
                case LAND: masm.andq(asLongReg(dst), tasm.asLongAddr(src)); break;
                case LOR:  masm.orq(asLongReg(dst),  tasm.asLongAddr(src)); break;
                case LXOR: masm.xorq(asLongReg(dst), tasm.asLongAddr(src)); break;

                case FADD: masm.addss(asFloatReg(dst), tasm.asFloatAddr(src)); break;
                case FSUB: masm.subss(asFloatReg(dst), tasm.asFloatAddr(src)); break;
                case FMUL: masm.mulss(asFloatReg(dst), tasm.asFloatAddr(src)); break;
                case FDIV: masm.divss(asFloatReg(dst), tasm.asFloatAddr(src)); break;

                case DADD: masm.addsd(asDoubleReg(dst), tasm.asDoubleAddr(src)); break;
                case DSUB: masm.subsd(asDoubleReg(dst), tasm.asDoubleAddr(src)); break;
                case DMUL: masm.mulsd(asDoubleReg(dst), tasm.asDoubleAddr(src)); break;
                case DDIV: masm.divsd(asDoubleReg(dst), tasm.asDoubleAddr(src)); break;
                default:   throw GraalInternalError.shouldNotReachHere();
            }
        }

        if (info != null) {
            assert exceptionOffset != -1;
            tasm.recordImplicitException(exceptionOffset, info);
        }
    }

    private static void emitConvertFixup(TargetMethodAssembler tasm, AMD64MacroAssembler masm, Value result, Value x) {
        ConvertSlowPath slowPath = new ConvertSlowPath(result, x);
        tasm.stubs.add(slowPath);
        switch (result.getKind()) {
            case Int:  masm.cmpl(asIntReg(result),  Integer.MIN_VALUE); break;
            case Long: masm.cmpq(asLongReg(result), tasm.asLongConstRef(Constant.forLong(java.lang.Long.MIN_VALUE))); break;
            default:   throw GraalInternalError.shouldNotReachHere();
        }
        masm.jcc(ConditionFlag.equal, slowPath.start);
        masm.bind(slowPath.continuation);
    }

    private static class ConvertSlowPath extends AMD64Code {
        public final Label start = new Label();
        public final Label continuation = new Label();
        private final Value result;
        private final Value x;

        public ConvertSlowPath(Value result, Value x) {
            this.result = result;
            this.x = x;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            masm.bind(start);
            switch (x.getKind()) {
                case Float:  masm.ucomiss(asFloatReg(x),  tasm.asFloatConstRef(Constant.FLOAT_0)); break;
                case Double: masm.ucomisd(asDoubleReg(x), tasm.asDoubleConstRef(Constant.DOUBLE_0)); break;
                default:     throw GraalInternalError.shouldNotReachHere();
            }
            Label nan = new Label();
            masm.jcc(ConditionFlag.parity, nan);
            masm.jcc(ConditionFlag.below, continuation);

            // input is > 0 -> return maxInt
            // result register already contains 0x80000000, so subtracting 1 gives 0x7fffffff
            switch (result.getKind()) {
                case Int:  masm.decrementl(asIntReg(result),  1); break;
                case Long: masm.decrementq(asLongReg(result), 1); break;
                default:   throw GraalInternalError.shouldNotReachHere();
            }
            masm.jmp(continuation);

            // input is NaN -> return 0
            masm.bind(nan);
            masm.xorptr(asRegister(result), asRegister(result));
            masm.jmp(continuation);
        }

        @Override
        public String description() {
            return "convert " + x + " to " + result;
        }
    }


    private static void verifyKind(AMD64Arithmetic opcode, Value result, Value x, Value y) {
        assert (opcode.name().startsWith("I") && result.getKind() == Kind.Int && x.getKind().getStackKind() == Kind.Int && y.getKind().getStackKind() == Kind.Int)
            || (opcode.name().startsWith("L") && result.getKind() == Kind.Long && x.getKind() == Kind.Long && y.getKind() == Kind.Long)
            || (opcode.name().startsWith("F") && result.getKind() == Kind.Float && x.getKind() == Kind.Float && y.getKind() == Kind.Float)
            || (opcode.name().startsWith("D") && result.getKind() == Kind.Double && x.getKind() == Kind.Double && y.getKind() == Kind.Double);
    }
}
