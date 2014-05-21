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
import com.oracle.graal.asm.amd64.AMD64Assembler.ConditionFlag;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.AMD64Move.MemOp;
import com.oracle.graal.lir.asm.*;

public enum AMD64Arithmetic {

    // @formatter:off

    IADD, ISUB, IMUL, IUMUL, IDIV, IDIVREM, IREM, IUDIV, IUREM, IAND, IOR, IXOR, ISHL, ISHR, IUSHR, IROL, IROR,
    LADD, LSUB, LMUL, LUMUL, LDIV, LDIVREM, LREM, LUDIV, LUREM, LAND, LOR, LXOR, LSHL, LSHR, LUSHR, LROL, LROR,
    FADD, FSUB, FMUL, FDIV, FREM, FAND, FOR, FXOR,
    DADD, DSUB, DMUL, DDIV, DREM, DAND, DOR, DXOR,
    INEG, LNEG, INOT, LNOT,
    SQRT,
    L2I, B2I, S2I, B2L, S2L, I2L,
    F2D, D2F,
    I2F, I2D,
    L2F, L2D,
    MOV_I2F, MOV_L2D, MOV_F2I, MOV_D2L,
    MOV_B2UI, MOV_B2UL, // Zero extending byte loads

    /*
     * Converts a float/double to an int/long. The result of the conversion does not comply with Java semantics
     * when the input is a NaN, infinity or the conversion result is greater than Integer.MAX_VALUE/Long.MAX_VALUE.
     */
    F2I, D2I, F2L, D2L;

    // @formatter:on

    /**
     * Unary operation with separate source and destination operand.
     */
    public static class Unary2Op extends AMD64LIRInstruction {

        @Opcode private final AMD64Arithmetic opcode;
        @Def({REG}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue x;

        public Unary2Op(AMD64Arithmetic opcode, AllocatableValue result, AllocatableValue x) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            emit(crb, masm, opcode, result, x, null);
        }
    }

    /**
     * Unary operation with separate source and destination operand but register only.
     */
    public static class Unary2RegOp extends AMD64LIRInstruction {

        @Opcode private final AMD64Arithmetic opcode;
        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue x;

        public Unary2RegOp(AMD64Arithmetic opcode, AllocatableValue result, AllocatableValue x) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            emit(crb, masm, opcode, result, x, null);
        }
    }

    /**
     * Unary operation with single operand for source and destination.
     */
    public static class Unary1Op extends AMD64LIRInstruction {

        @Opcode private final AMD64Arithmetic opcode;
        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue x;

        public Unary1Op(AMD64Arithmetic opcode, AllocatableValue result, AllocatableValue x) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Move.move(crb, masm, result, x);
            emit(crb, masm, opcode, result);
        }
    }

    /**
     * Unary operation with separate memory source and destination operand.
     */
    public static class Unary2MemoryOp extends MemOp {

        @Opcode private final AMD64Arithmetic opcode;
        @Def({REG}) protected AllocatableValue result;

        public Unary2MemoryOp(AMD64Arithmetic opcode, AllocatableValue result, Kind kind, AMD64AddressValue address, LIRFrameState state) {
            super(kind, address, state);
            this.opcode = opcode;
            this.result = result;
        }

        @Override
        public void emitMemAccess(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            emit(crb, masm, opcode, result, address, null);
        }
    }

    /**
     * Binary operation with two operands. The first source operand is combined with the
     * destination. The second source operand may be a stack slot.
     */
    public static class BinaryRegStack extends AMD64LIRInstruction {

        @Opcode private final AMD64Arithmetic opcode;
        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue x;
        @Alive({REG, STACK}) protected AllocatableValue y;

        public BinaryRegStack(AMD64Arithmetic opcode, AllocatableValue result, AllocatableValue x, AllocatableValue y) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Move.move(crb, masm, result, x);
            emit(crb, masm, opcode, result, y, null);
        }

        @Override
        public void verify() {
            super.verify();
            assert differentRegisters(result, y) || sameRegister(x, y);
            verifyKind(opcode, result, x, y);
        }
    }

    /**
     * Binary operation with two operands. The first source operand is combined with the
     * destination. The second source operand may be a stack slot.
     */
    public static class BinaryMemory extends AMD64LIRInstruction {

        @Opcode private final AMD64Arithmetic opcode;
        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue x;
        protected final Kind kind;
        @Alive({COMPOSITE}) protected AMD64AddressValue location;
        @State protected LIRFrameState state;

        public BinaryMemory(AMD64Arithmetic opcode, Kind kind, AllocatableValue result, AllocatableValue x, AMD64AddressValue location, LIRFrameState state) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.location = location;
            this.kind = kind;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Move.move(crb, masm, result, x);
            if (state != null) {
                crb.recordImplicitException(masm.position(), state);
            }
            emit(crb, masm, opcode, result, location, null);
        }

        @Override
        public void verify() {
            super.verify();
            assert differentRegisters(result, location) || sameRegister(x, location);
            // verifyKind(opcode, result, x, location);
        }
    }

    /**
     * Binary operation with two operands. The first source operand is combined with the
     * destination. The second source operand must be a register.
     */
    public static class BinaryRegReg extends AMD64LIRInstruction {

        @Opcode private final AMD64Arithmetic opcode;
        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue x;
        @Alive({REG}) protected AllocatableValue y;

        public BinaryRegReg(AMD64Arithmetic opcode, AllocatableValue result, AllocatableValue x, AllocatableValue y) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Move.move(crb, masm, result, x);
            emit(crb, masm, opcode, result, y, null);
        }

        @Override
        public void verify() {
            super.verify();
            assert differentRegisters(result, y) || sameRegister(x, y);
            verifyKind(opcode, result, x, y);
        }
    }

    /**
     * Binary operation with single source/destination operand and one constant.
     */
    public static class BinaryRegConst extends AMD64LIRInstruction {

        @Opcode private final AMD64Arithmetic opcode;
        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue x;
        protected Constant y;

        public BinaryRegConst(AMD64Arithmetic opcode, AllocatableValue result, AllocatableValue x, Constant y) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Move.move(crb, masm, result, x);
            emit(crb, masm, opcode, result, y, null);
        }

        @Override
        public void verify() {
            super.verify();
            verifyKind(opcode, result, x, y);
        }
    }

    /**
     * Commutative binary operation with two operands. One of the operands is combined with the
     * result.
     */
    public static class BinaryCommutative extends AMD64LIRInstruction {

        @Opcode private final AMD64Arithmetic opcode;
        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue x;
        @Use({REG, STACK}) protected AllocatableValue y;

        public BinaryCommutative(AMD64Arithmetic opcode, AllocatableValue result, AllocatableValue x, AllocatableValue y) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (sameRegister(result, y)) {
                emit(crb, masm, opcode, result, x, null);
            } else {
                AMD64Move.move(crb, masm, result, x);
                emit(crb, masm, opcode, result, y, null);
            }
        }

        @Override
        protected void verify() {
            super.verify();
            verifyKind(opcode, result, x, y);
        }
    }

    /**
     * Binary operation with separate source and destination and one constant operand.
     */
    public static class BinaryRegStackConst extends AMD64LIRInstruction {

        @Opcode private final AMD64Arithmetic opcode;
        @Def({REG}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue x;
        protected Constant y;

        public BinaryRegStackConst(AMD64Arithmetic opcode, AllocatableValue result, AllocatableValue x, Constant y) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Move.move(crb, masm, result, x);
            emit(crb, masm, opcode, result, y, null);
        }

        @Override
        public void verify() {
            super.verify();
            verifyKind(opcode, result, x, y);
        }
    }

    public static class MulHighOp extends AMD64LIRInstruction {

        @Opcode private final AMD64Arithmetic opcode;
        @Def({REG}) public AllocatableValue lowResult;
        @Def({REG}) public AllocatableValue highResult;
        @Use({REG}) public AllocatableValue x;
        @Use({REG, STACK}) public AllocatableValue y;

        public MulHighOp(AMD64Arithmetic opcode, AllocatableValue y) {
            PlatformKind kind = y.getPlatformKind();

            this.opcode = opcode;
            this.x = AMD64.rax.asValue(kind);
            this.y = y;
            this.lowResult = AMD64.rax.asValue(kind);
            this.highResult = AMD64.rdx.asValue(kind);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(y)) {
                switch (opcode) {
                    case IMUL:
                        masm.imull(asRegister(y));
                        break;
                    case IUMUL:
                        masm.mull(asRegister(y));
                        break;
                    case LMUL:
                        masm.imulq(asRegister(y));
                        break;
                    case LUMUL:
                        masm.mulq(asRegister(y));
                        break;
                    default:
                        throw GraalInternalError.shouldNotReachHere();
                }
            } else {
                switch (opcode) {
                    case IMUL:
                        masm.imull((AMD64Address) crb.asAddress(y));
                        break;
                    case IUMUL:
                        masm.mull((AMD64Address) crb.asAddress(y));
                        break;
                    case LMUL:
                        masm.imulq((AMD64Address) crb.asAddress(y));
                        break;
                    case LUMUL:
                        masm.mulq((AMD64Address) crb.asAddress(y));
                        break;
                    default:
                        throw GraalInternalError.shouldNotReachHere();
                }
            }
        }
    }

    public static class DivRemOp extends AMD64LIRInstruction {

        @Opcode private final AMD64Arithmetic opcode;
        @Def protected AllocatableValue divResult;
        @Def protected AllocatableValue remResult;
        @Use protected AllocatableValue x;
        @Alive protected AllocatableValue y;
        @State protected LIRFrameState state;

        public DivRemOp(AMD64Arithmetic opcode, AllocatableValue x, AllocatableValue y, LIRFrameState state) {
            this.opcode = opcode;
            this.divResult = AMD64.rax.asValue(x.getPlatformKind());
            this.remResult = AMD64.rdx.asValue(x.getPlatformKind());
            this.x = x;
            this.y = y;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            emit(crb, masm, opcode, null, y, state);
        }

        @Override
        protected void verify() {
            super.verify();
            // left input in rax, right input in any register but rax and rdx, result quotient in
            // rax, result remainder in rdx
            assert asRegister(x).equals(AMD64.rax);
            assert differentRegisters(y, AMD64.rax.asValue(), AMD64.rdx.asValue());
            verifyKind(opcode, divResult, x, y);
            verifyKind(opcode, remResult, x, y);
        }
    }

    public static class FPDivRemOp extends AMD64LIRInstruction {

        @Opcode private final AMD64Arithmetic opcode;
        @Def protected AllocatableValue result;
        @Use protected AllocatableValue x;
        @Use protected AllocatableValue y;
        @Temp protected AllocatableValue raxTemp;

        public FPDivRemOp(AMD64Arithmetic opcode, AllocatableValue result, AllocatableValue x, AllocatableValue y) {
            this.opcode = opcode;
            this.result = result;
            this.raxTemp = AMD64.rax.asValue(Kind.Int);
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Address tmp = new AMD64Address(AMD64.rsp);
            masm.subq(AMD64.rsp, 8);
            if (opcode == FREM) {
                masm.movflt(tmp, asRegister(y));
                masm.flds(tmp);
                masm.movflt(tmp, asRegister(x));
                masm.flds(tmp);
            } else {
                assert opcode == DREM;
                masm.movdbl(tmp, asRegister(y));
                masm.fldd(tmp);
                masm.movdbl(tmp, asRegister(x));
                masm.fldd(tmp);
            }

            Label label = new Label();
            masm.bind(label);
            masm.fprem();
            masm.fwait();
            masm.fnstswAX();
            masm.testl(AMD64.rax, 0x400);
            masm.jcc(ConditionFlag.NotZero, label);
            masm.fxch(1);
            masm.fpop();

            if (opcode == FREM) {
                masm.fstps(tmp);
                masm.movflt(asRegister(result), tmp);
            } else {
                masm.fstpd(tmp);
                masm.movdbl(asRegister(result), tmp);
            }
            masm.addq(AMD64.rsp, 8);
        }

        @Override
        protected void verify() {
            super.verify();
            verifyKind(opcode, result, x, y);
        }
    }

    @SuppressWarnings("unused")
    protected static void emit(CompilationResultBuilder crb, AMD64MacroAssembler masm, AMD64Arithmetic opcode, AllocatableValue result) {
        switch (opcode) {
            case INEG:
                masm.negl(asIntReg(result));
                break;
            case LNEG:
                masm.negq(asLongReg(result));
                break;
            case INOT:
                masm.notl(asIntReg(result));
                break;
            case LNOT:
                masm.notq(asLongReg(result));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    public static void emit(CompilationResultBuilder crb, AMD64MacroAssembler masm, AMD64Arithmetic opcode, Value dst, Value src, LIRFrameState info) {
        int exceptionOffset = -1;
        if (isRegister(src)) {
            switch (opcode) {
                case IADD:
                    masm.addl(asIntReg(dst), asIntReg(src));
                    break;
                case ISUB:
                    masm.subl(asIntReg(dst), asIntReg(src));
                    break;
                case IAND:
                    masm.andl(asIntReg(dst), asIntReg(src));
                    break;
                case IMUL:
                    masm.imull(asIntReg(dst), asIntReg(src));
                    break;
                case IOR:
                    masm.orl(asIntReg(dst), asIntReg(src));
                    break;
                case IXOR:
                    masm.xorl(asIntReg(dst), asIntReg(src));
                    break;
                case ISHL:
                    assert asIntReg(src).equals(AMD64.rcx);
                    masm.shll(asIntReg(dst));
                    break;
                case ISHR:
                    assert asIntReg(src).equals(AMD64.rcx);
                    masm.sarl(asIntReg(dst));
                    break;
                case IUSHR:
                    assert asIntReg(src).equals(AMD64.rcx);
                    masm.shrl(asIntReg(dst));
                    break;
                case IROL:
                    assert asIntReg(src).equals(AMD64.rcx);
                    masm.roll(asIntReg(dst));
                    break;
                case IROR:
                    assert asIntReg(src).equals(AMD64.rcx);
                    masm.rorl(asIntReg(dst));
                    break;

                case LADD:
                    masm.addq(asLongReg(dst), asLongReg(src));
                    break;
                case LSUB:
                    masm.subq(asLongReg(dst), asLongReg(src));
                    break;
                case LMUL:
                    masm.imulq(asLongReg(dst), asLongReg(src));
                    break;
                case LAND:
                    masm.andq(asLongReg(dst), asLongReg(src));
                    break;
                case LOR:
                    masm.orq(asLongReg(dst), asLongReg(src));
                    break;
                case LXOR:
                    masm.xorq(asLongReg(dst), asLongReg(src));
                    break;
                case LSHL:
                    assert asIntReg(src).equals(AMD64.rcx);
                    masm.shlq(asLongReg(dst));
                    break;
                case LSHR:
                    assert asIntReg(src).equals(AMD64.rcx);
                    masm.sarq(asLongReg(dst));
                    break;
                case LUSHR:
                    assert asIntReg(src).equals(AMD64.rcx);
                    masm.shrq(asLongReg(dst));
                    break;
                case LROL:
                    assert asIntReg(src).equals(AMD64.rcx);
                    masm.rolq(asLongReg(dst));
                    break;
                case LROR:
                    assert asIntReg(src).equals(AMD64.rcx);
                    masm.rorq(asLongReg(dst));
                    break;

                case FADD:
                    masm.addss(asFloatReg(dst), asFloatReg(src));
                    break;
                case FSUB:
                    masm.subss(asFloatReg(dst), asFloatReg(src));
                    break;
                case FMUL:
                    masm.mulss(asFloatReg(dst), asFloatReg(src));
                    break;
                case FDIV:
                    masm.divss(asFloatReg(dst), asFloatReg(src));
                    break;
                case FAND:
                    masm.andps(asFloatReg(dst), asFloatReg(src));
                    break;
                case FOR:
                    masm.orps(asFloatReg(dst), asFloatReg(src));
                    break;
                case FXOR:
                    masm.xorps(asFloatReg(dst), asFloatReg(src));
                    break;

                case DADD:
                    masm.addsd(asDoubleReg(dst), asDoubleReg(src));
                    break;
                case DSUB:
                    masm.subsd(asDoubleReg(dst), asDoubleReg(src));
                    break;
                case DMUL:
                    masm.mulsd(asDoubleReg(dst), asDoubleReg(src));
                    break;
                case DDIV:
                    masm.divsd(asDoubleReg(dst), asDoubleReg(src));
                    break;
                case DAND:
                    masm.andpd(asDoubleReg(dst), asDoubleReg(src));
                    break;
                case DOR:
                    masm.orpd(asDoubleReg(dst), asDoubleReg(src));
                    break;
                case DXOR:
                    masm.xorpd(asDoubleReg(dst), asDoubleReg(src));
                    break;

                case SQRT:
                    masm.sqrtsd(asDoubleReg(dst), asDoubleReg(src));
                    break;

                case B2I:
                    masm.movsbl(asIntReg(dst), asIntReg(src));
                    break;
                case S2I:
                    masm.movswl(asIntReg(dst), asIntReg(src));
                    break;
                case B2L:
                    masm.movsbq(asLongReg(dst), asIntReg(src));
                    break;
                case S2L:
                    masm.movswq(asLongReg(dst), asIntReg(src));
                    break;
                case I2L:
                    masm.movslq(asLongReg(dst), asIntReg(src));
                    break;
                case L2I:
                    masm.movl(asIntReg(dst), asLongReg(src));
                    break;
                case F2D:
                    masm.cvtss2sd(asDoubleReg(dst), asFloatReg(src));
                    break;
                case D2F:
                    masm.cvtsd2ss(asFloatReg(dst), asDoubleReg(src));
                    break;
                case I2F:
                    masm.cvtsi2ssl(asFloatReg(dst), asIntReg(src));
                    break;
                case I2D:
                    masm.cvtsi2sdl(asDoubleReg(dst), asIntReg(src));
                    break;
                case L2F:
                    masm.cvtsi2ssq(asFloatReg(dst), asLongReg(src));
                    break;
                case L2D:
                    masm.cvtsi2sdq(asDoubleReg(dst), asLongReg(src));
                    break;
                case F2I:
                    masm.cvttss2sil(asIntReg(dst), asFloatReg(src));
                    break;
                case D2I:
                    masm.cvttsd2sil(asIntReg(dst), asDoubleReg(src));
                    break;
                case F2L:
                    masm.cvttss2siq(asLongReg(dst), asFloatReg(src));
                    break;
                case D2L:
                    masm.cvttsd2siq(asLongReg(dst), asDoubleReg(src));
                    break;
                case MOV_I2F:
                    masm.movdl(asFloatReg(dst), asIntReg(src));
                    break;
                case MOV_L2D:
                    masm.movdq(asDoubleReg(dst), asLongReg(src));
                    break;
                case MOV_F2I:
                    masm.movdl(asIntReg(dst), asFloatReg(src));
                    break;
                case MOV_D2L:
                    masm.movdq(asLongReg(dst), asDoubleReg(src));
                    break;

                case IDIVREM:
                case IDIV:
                case IREM:
                    masm.cdql();
                    exceptionOffset = masm.position();
                    masm.idivl(asRegister(src));
                    break;

                case LDIVREM:
                case LDIV:
                case LREM:
                    masm.cdqq();
                    exceptionOffset = masm.position();
                    masm.idivq(asRegister(src));
                    break;

                case IUDIV:
                case IUREM:
                    // Must zero the high 64-bit word (in RDX) of the dividend
                    masm.xorq(AMD64.rdx, AMD64.rdx);
                    exceptionOffset = masm.position();
                    masm.divl(asRegister(src));
                    break;

                case LUDIV:
                case LUREM:
                    // Must zero the high 64-bit word (in RDX) of the dividend
                    masm.xorq(AMD64.rdx, AMD64.rdx);
                    exceptionOffset = masm.position();
                    masm.divq(asRegister(src));
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        } else if (isConstant(src)) {
            switch (opcode) {
                case IADD:
                    masm.incrementl(asIntReg(dst), crb.asIntConst(src));
                    break;
                case ISUB:
                    masm.decrementl(asIntReg(dst), crb.asIntConst(src));
                    break;
                case IMUL:
                    masm.imull(asIntReg(dst), asIntReg(dst), crb.asIntConst(src));
                    break;
                case IAND:
                    masm.andl(asIntReg(dst), crb.asIntConst(src));
                    break;
                case IOR:
                    masm.orl(asIntReg(dst), crb.asIntConst(src));
                    break;
                case IXOR:
                    masm.xorl(asIntReg(dst), crb.asIntConst(src));
                    break;
                case ISHL:
                    masm.shll(asIntReg(dst), crb.asIntConst(src) & 31);
                    break;
                case ISHR:
                    masm.sarl(asIntReg(dst), crb.asIntConst(src) & 31);
                    break;
                case IUSHR:
                    masm.shrl(asIntReg(dst), crb.asIntConst(src) & 31);
                    break;
                case IROL:
                    masm.roll(asIntReg(dst), crb.asIntConst(src) & 31);
                    break;
                case IROR:
                    masm.rorl(asIntReg(dst), crb.asIntConst(src) & 31);
                    break;

                case LADD:
                    masm.addq(asLongReg(dst), crb.asIntConst(src));
                    break;
                case LSUB:
                    masm.subq(asLongReg(dst), crb.asIntConst(src));
                    break;
                case LMUL:
                    masm.imulq(asLongReg(dst), asLongReg(dst), crb.asIntConst(src));
                    break;
                case LAND:
                    masm.andq(asLongReg(dst), crb.asIntConst(src));
                    break;
                case LOR:
                    masm.orq(asLongReg(dst), crb.asIntConst(src));
                    break;
                case LXOR:
                    masm.xorq(asLongReg(dst), crb.asIntConst(src));
                    break;
                case LSHL:
                    masm.shlq(asLongReg(dst), crb.asIntConst(src) & 63);
                    break;
                case LSHR:
                    masm.sarq(asLongReg(dst), crb.asIntConst(src) & 63);
                    break;
                case LUSHR:
                    masm.shrq(asLongReg(dst), crb.asIntConst(src) & 63);
                    break;
                case LROL:
                    masm.rolq(asLongReg(dst), crb.asIntConst(src) & 31);
                    break;
                case LROR:
                    masm.rorq(asLongReg(dst), crb.asIntConst(src) & 31);
                    break;

                case FADD:
                    masm.addss(asFloatReg(dst), (AMD64Address) crb.asFloatConstRef(src));
                    break;
                case FSUB:
                    masm.subss(asFloatReg(dst), (AMD64Address) crb.asFloatConstRef(src));
                    break;
                case FMUL:
                    masm.mulss(asFloatReg(dst), (AMD64Address) crb.asFloatConstRef(src));
                    break;
                case FAND:
                    masm.andps(asFloatReg(dst), (AMD64Address) crb.asFloatConstRef(src, 16));
                    break;
                case FOR:
                    masm.orps(asFloatReg(dst), (AMD64Address) crb.asFloatConstRef(src, 16));
                    break;
                case FXOR:
                    masm.xorps(asFloatReg(dst), (AMD64Address) crb.asFloatConstRef(src, 16));
                    break;
                case FDIV:
                    masm.divss(asFloatReg(dst), (AMD64Address) crb.asFloatConstRef(src));
                    break;

                case DADD:
                    masm.addsd(asDoubleReg(dst), (AMD64Address) crb.asDoubleConstRef(src));
                    break;
                case DSUB:
                    masm.subsd(asDoubleReg(dst), (AMD64Address) crb.asDoubleConstRef(src));
                    break;
                case DMUL:
                    masm.mulsd(asDoubleReg(dst), (AMD64Address) crb.asDoubleConstRef(src));
                    break;
                case DDIV:
                    masm.divsd(asDoubleReg(dst), (AMD64Address) crb.asDoubleConstRef(src));
                    break;
                case DAND:
                    masm.andpd(asDoubleReg(dst), (AMD64Address) crb.asDoubleConstRef(src, 16));
                    break;
                case DOR:
                    masm.orpd(asDoubleReg(dst), (AMD64Address) crb.asDoubleConstRef(src, 16));
                    break;
                case DXOR:
                    masm.xorpd(asDoubleReg(dst), (AMD64Address) crb.asDoubleConstRef(src, 16));
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        } else if (isStackSlot(src)) {

            switch (opcode) {
                case IADD:
                    masm.addl(asIntReg(dst), (AMD64Address) crb.asIntAddr(src));
                    break;
                case ISUB:
                    masm.subl(asIntReg(dst), (AMD64Address) crb.asIntAddr(src));
                    break;
                case IAND:
                    masm.andl(asIntReg(dst), (AMD64Address) crb.asIntAddr(src));
                    break;
                case IMUL:
                    masm.imull(asIntReg(dst), (AMD64Address) crb.asIntAddr(src));
                    break;
                case IOR:
                    masm.orl(asIntReg(dst), (AMD64Address) crb.asIntAddr(src));
                    break;
                case IXOR:
                    masm.xorl(asIntReg(dst), (AMD64Address) crb.asIntAddr(src));
                    break;

                case LADD:
                    masm.addq(asLongReg(dst), (AMD64Address) crb.asLongAddr(src));
                    break;
                case LSUB:
                    masm.subq(asLongReg(dst), (AMD64Address) crb.asLongAddr(src));
                    break;
                case LMUL:
                    masm.imulq(asLongReg(dst), (AMD64Address) crb.asLongAddr(src));
                    break;
                case LAND:
                    masm.andq(asLongReg(dst), (AMD64Address) crb.asLongAddr(src));
                    break;
                case LOR:
                    masm.orq(asLongReg(dst), (AMD64Address) crb.asLongAddr(src));
                    break;
                case LXOR:
                    masm.xorq(asLongReg(dst), (AMD64Address) crb.asLongAddr(src));
                    break;

                case FADD:
                    masm.addss(asFloatReg(dst), (AMD64Address) crb.asFloatAddr(src));
                    break;
                case FSUB:
                    masm.subss(asFloatReg(dst), (AMD64Address) crb.asFloatAddr(src));
                    break;
                case FMUL:
                    masm.mulss(asFloatReg(dst), (AMD64Address) crb.asFloatAddr(src));
                    break;
                case FDIV:
                    masm.divss(asFloatReg(dst), (AMD64Address) crb.asFloatAddr(src));
                    break;

                case DADD:
                    masm.addsd(asDoubleReg(dst), (AMD64Address) crb.asDoubleAddr(src));
                    break;
                case DSUB:
                    masm.subsd(asDoubleReg(dst), (AMD64Address) crb.asDoubleAddr(src));
                    break;
                case DMUL:
                    masm.mulsd(asDoubleReg(dst), (AMD64Address) crb.asDoubleAddr(src));
                    break;
                case DDIV:
                    masm.divsd(asDoubleReg(dst), (AMD64Address) crb.asDoubleAddr(src));
                    break;

                case SQRT:
                    masm.sqrtsd(asDoubleReg(dst), (AMD64Address) crb.asDoubleAddr(src));
                    break;

                case B2I:
                    masm.movsbl(asIntReg(dst), (AMD64Address) crb.asIntAddr(src));
                    break;
                case S2I:
                    masm.movswl(asIntReg(dst), (AMD64Address) crb.asIntAddr(src));
                    break;
                case B2L:
                    masm.movsbq(asLongReg(dst), (AMD64Address) crb.asIntAddr(src));
                    break;
                case S2L:
                    masm.movswq(asLongReg(dst), (AMD64Address) crb.asIntAddr(src));
                    break;
                case I2L:
                    masm.movslq(asLongReg(dst), (AMD64Address) crb.asIntAddr(src));
                    break;
                case F2D:
                    masm.cvtss2sd(asDoubleReg(dst), (AMD64Address) crb.asFloatAddr(src));
                    break;
                case D2F:
                    masm.cvtsd2ss(asFloatReg(dst), (AMD64Address) crb.asDoubleAddr(src));
                    break;
                case I2F:
                    masm.cvtsi2ssl(asFloatReg(dst), (AMD64Address) crb.asIntAddr(src));
                    break;
                case I2D:
                    masm.cvtsi2sdl(asDoubleReg(dst), (AMD64Address) crb.asIntAddr(src));
                    break;
                case L2F:
                    masm.cvtsi2ssq(asFloatReg(dst), (AMD64Address) crb.asLongAddr(src));
                    break;
                case L2D:
                    masm.cvtsi2sdq(asDoubleReg(dst), (AMD64Address) crb.asLongAddr(src));
                    break;
                case F2I:
                    masm.cvttss2sil(asIntReg(dst), (AMD64Address) crb.asFloatAddr(src));
                    break;
                case D2I:
                    masm.cvttsd2sil(asIntReg(dst), (AMD64Address) crb.asDoubleAddr(src));
                    break;
                case F2L:
                    masm.cvttss2siq(asLongReg(dst), (AMD64Address) crb.asFloatAddr(src));
                    break;
                case D2L:
                    masm.cvttsd2siq(asLongReg(dst), (AMD64Address) crb.asDoubleAddr(src));
                    break;
                case MOV_I2F:
                    masm.movss(asFloatReg(dst), (AMD64Address) crb.asIntAddr(src));
                    break;
                case MOV_L2D:
                    masm.movsd(asDoubleReg(dst), (AMD64Address) crb.asLongAddr(src));
                    break;
                case MOV_F2I:
                    masm.movl(asIntReg(dst), (AMD64Address) crb.asFloatAddr(src));
                    break;
                case MOV_D2L:
                    masm.movq(asLongReg(dst), (AMD64Address) crb.asDoubleAddr(src));
                    break;

                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        } else {
            switch (opcode) {
                case IADD:
                    masm.addl(asIntReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case ISUB:
                    masm.subl(asIntReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case IAND:
                    masm.andl(asIntReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case IMUL:
                    masm.imull(asIntReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case IOR:
                    masm.orl(asIntReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case IXOR:
                    masm.xorl(asIntReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;

                case LADD:
                    masm.addq(asLongReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case LSUB:
                    masm.subq(asLongReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case LMUL:
                    masm.imulq(asLongReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case LAND:
                    masm.andq(asLongReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case LOR:
                    masm.orq(asLongReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case LXOR:
                    masm.xorq(asLongReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;

                case FADD:
                    masm.addss(asFloatReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case FSUB:
                    masm.subss(asFloatReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case FMUL:
                    masm.mulss(asFloatReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case FDIV:
                    masm.divss(asFloatReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;

                case DADD:
                    masm.addsd(asDoubleReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case DSUB:
                    masm.subsd(asDoubleReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case DMUL:
                    masm.mulsd(asDoubleReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case DDIV:
                    masm.divsd(asDoubleReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;

                case SQRT:
                    masm.sqrtsd(asDoubleReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;

                case B2I:
                    masm.movsbl(asIntReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case S2I:
                    masm.movswl(asIntReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case B2L:
                    masm.movsbq(asLongReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case S2L:
                    masm.movswq(asLongReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case I2L:
                    masm.movslq(asLongReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case F2D:
                    masm.cvtss2sd(asDoubleReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case D2F:
                    masm.cvtsd2ss(asFloatReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case I2F:
                    masm.cvtsi2ssl(asFloatReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case I2D:
                    masm.cvtsi2sdl(asDoubleReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case L2F:
                    masm.cvtsi2ssq(asFloatReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case L2D:
                    masm.cvtsi2sdq(asDoubleReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case F2I:
                    masm.cvttss2sil(asIntReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case D2I:
                    masm.cvttsd2sil(asIntReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case F2L:
                    masm.cvttss2siq(asLongReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case D2L:
                    masm.cvttsd2siq(asLongReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case MOV_I2F:
                    masm.movss(asFloatReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case MOV_L2D:
                    masm.movsd(asDoubleReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case MOV_F2I:
                    masm.movl(asIntReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case MOV_D2L:
                    masm.movq(asLongReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case MOV_B2UI:
                    masm.movzbl(asIntReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;
                case MOV_B2UL:
                    masm.movzbl(asLongReg(dst), ((AMD64AddressValue) src).toAddress());
                    break;

                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }

        if (info != null) {
            assert exceptionOffset != -1;
            crb.recordImplicitException(exceptionOffset, info);
        }
    }

    private static void verifyKind(AMD64Arithmetic opcode, Value result, Value x, Value y) {
        assert (opcode.name().startsWith("I") && result.getKind().getStackKind() == Kind.Int && x.getKind().getStackKind() == Kind.Int && y.getKind().getStackKind() == Kind.Int) ||
                        (opcode.name().startsWith("L") && result.getKind() == Kind.Long && x.getKind() == Kind.Long && y.getKind() == Kind.Long) ||
                        (opcode.name().startsWith("F") && result.getKind() == Kind.Float && x.getKind() == Kind.Float && y.getKind() == Kind.Float) ||
                        (opcode.name().startsWith("D") && result.getKind() == Kind.Double && x.getKind() == Kind.Double && y.getKind() == Kind.Double) ||
                        (opcode.name().matches(".U?SH.") && result.getKind() == x.getKind() && y.getKind() == Kind.Int && (isConstant(y) || asRegister(y).equals(AMD64.rcx)));
    }
}
