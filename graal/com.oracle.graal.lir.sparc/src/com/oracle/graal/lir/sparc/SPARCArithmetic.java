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
import com.oracle.graal.asm.sparc.SPARCAssembler.Add;
import com.oracle.graal.asm.sparc.SPARCAssembler.And;
import com.oracle.graal.asm.sparc.SPARCAssembler.Faddd;
import com.oracle.graal.asm.sparc.SPARCAssembler.Fadds;
import com.oracle.graal.asm.sparc.SPARCAssembler.Fdivd;
import com.oracle.graal.asm.sparc.SPARCAssembler.Fdivs;
import com.oracle.graal.asm.sparc.SPARCAssembler.Fdtoi;
import com.oracle.graal.asm.sparc.SPARCAssembler.Fmuld;
import com.oracle.graal.asm.sparc.SPARCAssembler.Fmuls;
import com.oracle.graal.asm.sparc.SPARCAssembler.Fnegd;
import com.oracle.graal.asm.sparc.SPARCAssembler.Fnegs;
import com.oracle.graal.asm.sparc.SPARCAssembler.Fstoi;
import com.oracle.graal.asm.sparc.SPARCAssembler.Fsubd;
import com.oracle.graal.asm.sparc.SPARCAssembler.Fsubs;
import com.oracle.graal.asm.sparc.SPARCAssembler.Mulx;
import com.oracle.graal.asm.sparc.SPARCAssembler.Or;
import com.oracle.graal.asm.sparc.SPARCAssembler.Sdivx;
import com.oracle.graal.asm.sparc.SPARCAssembler.Sll;
import com.oracle.graal.asm.sparc.SPARCAssembler.Sllx;
import com.oracle.graal.asm.sparc.SPARCAssembler.Sra;
import com.oracle.graal.asm.sparc.SPARCAssembler.Srax;
import com.oracle.graal.asm.sparc.SPARCAssembler.Srl;
import com.oracle.graal.asm.sparc.SPARCAssembler.Srlx;
import com.oracle.graal.asm.sparc.SPARCAssembler.Sub;
import com.oracle.graal.asm.sparc.SPARCAssembler.Xor;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Neg;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Not;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Signx;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.gen.*;

public enum SPARCArithmetic {
    // @formatter:off
    IADD, ISUB, IMUL, IDIV, IREM, IUDIV, IUREM, IAND, IOR, IXOR, ISHL, ISHR, IUSHR,
    LADD, LSUB, LMUL, LDIV, LREM, LUDIV, LUREM, LAND, LOR, LXOR, LSHL, LSHR, LUSHR,
    FADD, FSUB, FMUL, FDIV, FREM, FAND, FOR, FXOR,
    DADD, DSUB, DMUL, DDIV, DREM, DAND, DOR, DXOR,
    INEG, LNEG, FNEG, DNEG, INOT, LNOT,
    I2L, L2I, I2B, I2C, I2S,
    F2D, D2F,
    I2F, I2D, F2I, D2I,
    L2F, L2D, F2L, D2L,
    MOV_I2F, MOV_L2D, MOV_F2I, MOV_D2L;
    // @formatter:on

    /**
     * Unary operation with separate source and destination operand.
     */
    public static class Unary2Op extends SPARCLIRInstruction {

        @Opcode private final SPARCArithmetic opcode;
        @Def({REG}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue x;

        public Unary2Op(SPARCArithmetic opcode, AllocatableValue result, AllocatableValue x) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            emit(crb, masm, opcode, result, x, null);
        }
    }

    public static class Op1Stack extends SPARCLIRInstruction {

        @Opcode private final SPARCArithmetic opcode;
        @Def({REG, HINT}) protected Value result;
        @Use({REG, STACK, CONST}) protected Value x;

        public Op1Stack(SPARCArithmetic opcode, Value result, Value x) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            emit(crb, masm, opcode, result, x, null);
        }
    }

    public static class Op2Stack extends SPARCLIRInstruction {

        @Opcode private final SPARCArithmetic opcode;
        @Def({REG}) protected Value result;
        @Use({REG, CONST}) protected Value x;
        @Alive({REG, CONST}) protected Value y;

        public Op2Stack(SPARCArithmetic opcode, Value result, Value x, Value y) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            emit(crb, masm, opcode, result, x, y, null);
        }

        @Override
        public void verify() {
            super.verify();
            verifyKind(opcode, result, x, y);
        }
    }

    /**
     * Binary operation with two operands. The first source operand is combined with the
     * destination. The second source operand must be a register.
     */
    public static class BinaryRegReg extends SPARCLIRInstruction {

        @Opcode private final SPARCArithmetic opcode;
        @Def({REG}) protected Value result;
        @Use({REG, CONST}) protected Value x;
        @Alive({REG, CONST}) protected Value y;

        public BinaryRegReg(SPARCArithmetic opcode, Value result, Value x, Value y) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            emit(crb, masm, opcode, result, x, y, null);
        }

        @Override
        public void verify() {
            super.verify();
            verifyKind(opcode, result, x, y);
        }
    }

    /**
     * Binary operation with single source/destination operand and one constant.
     */
    public static class BinaryRegConst extends SPARCLIRInstruction {

        @Opcode private final SPARCArithmetic opcode;
        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue x;
        protected Constant y;

        public BinaryRegConst(SPARCArithmetic opcode, AllocatableValue result, AllocatableValue x, Constant y) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            emit(crb, masm, opcode, result, x, y, null);
        }

        @Override
        public void verify() {
            super.verify();
            verifyKind(opcode, result, x, y);
        }
    }

    /**
     * Commutative binary operation with two operands.
     */
    public static class BinaryCommutative extends SPARCLIRInstruction {

        @Opcode private final SPARCArithmetic opcode;
        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue x;
        @Use({REG}) protected AllocatableValue y;

        public BinaryCommutative(SPARCArithmetic opcode, AllocatableValue result, AllocatableValue x, AllocatableValue y) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            emit(crb, masm, opcode, result, x, y, null);
        }

        @Override
        protected void verify() {
            super.verify();
            verifyKind(opcode, result, x, y);
        }
    }

    public static class ShiftOp extends SPARCLIRInstruction {

        @Opcode private final SPARCArithmetic opcode;
        @Def({REG}) protected Value result;
        @Use({REG, CONST}) protected Value x;
        @Alive({REG, CONST}) protected Value y;

        public ShiftOp(SPARCArithmetic opcode, Value result, Value x, Value y) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            assert !(x instanceof SPARCAddressValue);
            emit(crb, masm, opcode, result, x, y, null);
        }

        @Override
        public void verify() {
            super.verify();
            verifyKind(opcode, result, x, x);
            assert y.getKind().getStackKind() == Kind.Int;
        }
    }

    public static class RemOp extends SPARCLIRInstruction {

        @Opcode private final SPARCArithmetic opcode;
        @Def({REG}) protected Value result;
        @Use({REG, CONST}) protected Value x;
        @Alive({REG, CONST}) protected Value y;
        @Def({REG}) protected Value scratch1;
        @Def({REG}) protected Value scratch2;
        @State protected LIRFrameState state;

        public RemOp(SPARCArithmetic opcode, Value result, Value x, Value y, LIRFrameState state, LIRGeneratorTool gen) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
            this.scratch1 = gen.newVariable(x.getKind());
            this.scratch2 = gen.newVariable(x.getKind());
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            emit(crb, masm, opcode, result, x, y, scratch1, scratch2, state);
        }

        @Override
        protected void verify() {
            super.verify();
            verifyKind(opcode, result, x, y);
        }
    }

    public static void emit(CompilationResultBuilder crb, SPARCAssembler masm, SPARCArithmetic opcode, Value dst, Value src1, Value src2, LIRFrameState info) {
        int exceptionOffset = -1;
        if (isConstant(src1)) {
            switch (opcode) {
                case ISUB:
                    assert isSimm13(crb.asIntConst(src1));
                    new Add(asIntReg(src2), -(crb.asIntConst(src1)), asIntReg(dst)).emit(masm);
                    break;
                case IAND:
                    throw GraalInternalError.unimplemented();
                case IDIV:
                    assert isSimm13(crb.asIntConst(src1));
                    throw GraalInternalError.unimplemented();
                    // new Sdivx(masm, asIntReg(src1), asIntReg(src2),
                    // asIntReg(dst));
                case FSUB:
                case FDIV:
                case DSUB:
                case DDIV:
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        } else if (isConstant(src2)) {
            switch (opcode) {
                case IADD:
                    assert isSimm13(crb.asIntConst(src2));
                    new Add(asIntReg(src1), crb.asIntConst(src2), asIntReg(dst)).emit(masm);
                    break;
                case ISUB:
                    assert isSimm13(crb.asIntConst(src2));
                    new Sub(asIntReg(src1), crb.asIntConst(src2), asIntReg(dst)).emit(masm);
                    break;
                case IMUL:
                    assert isSimm13(crb.asIntConst(src2));
                    new Mulx(asIntReg(src1), crb.asIntConst(src2), asIntReg(dst)).emit(masm);
                    break;
                case IDIV:
                    assert isSimm13(crb.asIntConst(src2));
                    new Signx(asIntReg(src1), asIntReg(src1)).emit(masm);
                    new Sdivx(asIntReg(src1), crb.asIntConst(src2), asIntReg(dst)).emit(masm);
                    break;
                case IAND:
                    assert isSimm13(crb.asIntConst(src2));
                    new And(asIntReg(src1), crb.asIntConst(src2), asIntReg(dst)).emit(masm);
                    break;
                case ISHL:
                    assert isSimm13(crb.asIntConst(src2));
                    new Sll(asIntReg(src1), crb.asIntConst(src2), asIntReg(dst)).emit(masm);
                    break;
                case ISHR:
                    assert isSimm13(crb.asIntConst(src2));
                    new Sra(asIntReg(src1), crb.asIntConst(src2), asIntReg(dst)).emit(masm);
                    break;
                case IUSHR:
                    assert isSimm13(crb.asIntConst(src2));
                    new Srl(asIntReg(src1), crb.asIntConst(src2), asIntReg(dst)).emit(masm);
                    break;
                case IOR:
                    assert isSimm13(crb.asIntConst(src2));
                    new Or(asIntReg(src1), crb.asIntConst(src2), asIntReg(dst)).emit(masm);
                    break;
                case IXOR:
                    assert isSimm13(crb.asIntConst(src2));
                    new Xor(asIntReg(src1), crb.asIntConst(src2), asIntReg(dst)).emit(masm);
                    break;
                case LADD:
                    assert isSimm13(crb.asIntConst(src2));
                    new Add(asLongReg(src1), crb.asIntConst(src2), asLongReg(dst)).emit(masm);
                    break;
                case LSUB:
                    assert isSimm13(crb.asIntConst(src2));
                    new Sub(asLongReg(src1), crb.asIntConst(src2), asLongReg(dst)).emit(masm);
                    break;
                case LMUL:
                    assert isSimm13(crb.asIntConst(src2));
                    new Mulx(asLongReg(src1), crb.asIntConst(src2), asLongReg(dst)).emit(masm);
                    break;
                case LDIV:
                    throw GraalInternalError.unimplemented();
                case LUDIV:
                    throw GraalInternalError.unimplemented();
                case LAND:
                    assert isSimm13(crb.asIntConst(src2));
                    new And(asLongReg(src1), crb.asIntConst(src2), asLongReg(dst)).emit(masm);
                    break;
                case LOR:
                    assert isSimm13(crb.asIntConst(src2));
                    new Or(asLongReg(src1), crb.asIntConst(src2), asLongReg(dst)).emit(masm);
                    break;
                case LXOR:
                    assert isSimm13(crb.asIntConst(src2));
                    new Xor(asLongReg(src1), crb.asIntConst(src2), asLongReg(dst)).emit(masm);
                    break;
                case LSHL:
                    assert isSimm13(crb.asIntConst(src2));
                    new Sllx(asLongReg(src1), crb.asIntConst(src2), asLongReg(dst)).emit(masm);
                    break;
                case LSHR:
                    assert isSimm13(crb.asIntConst(src2));
                    new Srax(asLongReg(src1), crb.asIntConst(src2), asLongReg(dst)).emit(masm);
                    break;
                case LUSHR:
                    assert isSimm13(crb.asIntConst(src2));
                    new Srlx(asLongReg(src1), crb.asIntConst(src2), asLongReg(dst)).emit(masm);
                    break;
                case FADD:
                case FMUL:
                case FDIV:
                case DADD:
                case DMUL:
                case DDIV:
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        } else {
            switch (opcode) {
                case IADD:
                    new Add(asIntReg(src1), asIntReg(src2), asIntReg(dst)).emit(masm);
                    break;
                case ISUB:
                    new Sub(asIntReg(src1), asIntReg(src2), asIntReg(dst)).emit(masm);
                    break;
                case IMUL:
                    new Mulx(asIntReg(src1), asIntReg(src2), asIntReg(dst)).emit(masm);
                    break;
                case IDIV:
                    new Signx(asIntReg(src1), asIntReg(src1)).emit(masm);
                    new Signx(asIntReg(src2), asIntReg(src2)).emit(masm);
                    new Sdivx(asIntReg(src1), asIntReg(src2), asIntReg(dst)).emit(masm);
                    break;
                case IAND:
                    new And(asIntReg(src1), asIntReg(src2), asIntReg(dst)).emit(masm);
                    break;
                case IOR:
                    new Or(asIntReg(src1), asIntReg(src2), asIntReg(dst)).emit(masm);
                    break;
                case IXOR:
                    new Xor(asIntReg(src1), asIntReg(src2), asIntReg(dst)).emit(masm);
                    break;
                case ISHL:
                    new Sll(asIntReg(src1), asIntReg(src2), asIntReg(dst)).emit(masm);
                    break;
                case ISHR:
                    new Srl(asIntReg(src1), asIntReg(src2), asIntReg(dst)).emit(masm);
                    break;
                case IUSHR:
                    new Sra(asIntReg(src1), asIntReg(src2), asIntReg(dst)).emit(masm);
                    break;
                case IREM:
                    throw GraalInternalError.unimplemented();
                case LADD:
                    new Add(asLongReg(src1), asLongReg(src2), asLongReg(dst)).emit(masm);
                    break;
                case LSUB:
                    new Sub(asLongReg(src1), asLongReg(src2), asLongReg(dst)).emit(masm);
                    break;
                case LMUL:
                    new Mulx(asLongReg(src1), asLongReg(src2), asLongReg(dst)).emit(masm);
                    break;
                case LDIV:
                    new Sdivx(asLongReg(src1), asLongReg(src2), asLongReg(dst)).emit(masm);
                    break;
                case LUDIV:
                    throw GraalInternalError.unimplemented();
                case LAND:
                    new And(asLongReg(src1), asLongReg(src2), asLongReg(dst)).emit(masm);
                    break;
                case LOR:
                    new Or(asLongReg(src1), asLongReg(src2), asLongReg(dst)).emit(masm);
                    break;
                case LXOR:
                    new Xor(asLongReg(src1), asLongReg(src2), asLongReg(dst)).emit(masm);
                    break;
                case LSHL:
                    new Sllx(asLongReg(src1), asIntReg(src2), asLongReg(dst)).emit(masm);
                    break;
                case LSHR:
                    new Srax(asLongReg(src1), asIntReg(src2), asLongReg(dst)).emit(masm);
                    break;
                case LUSHR:
                    new Srlx(asLongReg(src1), asIntReg(src2), asLongReg(dst)).emit(masm);
                    break;
                case FADD:
                    new Fadds(asFloatReg(src1), asFloatReg(src2), asFloatReg(dst)).emit(masm);
                    break;
                case FSUB:
                    new Fsubs(asFloatReg(src1), asFloatReg(src2), asFloatReg(dst)).emit(masm);
                    break;
                case FMUL:
                    new Fmuls(asFloatReg(src1), asFloatReg(src2), asFloatReg(dst)).emit(masm);
                    break;
                case FDIV:
                    new Fdivs(asFloatReg(src1), asFloatReg(src2), asFloatReg(dst)).emit(masm);
                    break;
                case FREM:
                    throw GraalInternalError.unimplemented();
                case DADD:
                    new Faddd(asDoubleReg(src1), asDoubleReg(src2), asDoubleReg(dst)).emit(masm);
                    break;
                case DSUB:
                    new Fsubd(asDoubleReg(src1), asDoubleReg(src2), asDoubleReg(dst)).emit(masm);
                    break;
                case DMUL:
                    new Fmuld(asDoubleReg(src1), asDoubleReg(src2), asDoubleReg(dst)).emit(masm);
                    break;
                case DDIV:
                    new Fdivd(asDoubleReg(src1), asDoubleReg(src2), asDoubleReg(dst)).emit(masm);
                    break;
                case DREM:
                    throw GraalInternalError.unimplemented();
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }

        if (info != null) {
            assert exceptionOffset != -1;
            crb.recordImplicitException(exceptionOffset, info);
        }
    }

    public static void emit(CompilationResultBuilder crb, SPARCAssembler masm, SPARCArithmetic opcode, Value dst, Value src1, Value src2, Value scratch1, Value scratch2, LIRFrameState info) {
        int exceptionOffset = -1;
        if (isConstant(src1)) {
            switch (opcode) {
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        } else if (isConstant(src2)) {
            switch (opcode) {
                case IREM:
                    assert isSimm13(crb.asIntConst(src2));
                    new Sra(asIntReg(src1), 0, asIntReg(src1)).emit(masm);
                    new Sdivx(asIntReg(src1), crb.asIntConst(src2), asIntReg(scratch1)).emit(masm);
                    new Mulx(asIntReg(scratch1), crb.asIntConst(src2), asIntReg(scratch2)).emit(masm);
                    new Sub(asIntReg(src1), asIntReg(scratch2), asIntReg(dst)).emit(masm);
                    break;
                case LREM:
                    assert isSimm13(crb.asIntConst(src2));
                    new Sdivx(asLongReg(src1), crb.asIntConst(src2), asLongReg(scratch1)).emit(masm);
                    new Mulx(asLongReg(scratch1), crb.asIntConst(src2), asLongReg(scratch2)).emit(masm);
                    new Sub(asLongReg(src1), asLongReg(scratch2), asLongReg(dst)).emit(masm);
                    break;
                case LUREM:
                    throw GraalInternalError.unimplemented();
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        } else {
            switch (opcode) {
                case LREM:
                case LUREM:
                    throw GraalInternalError.unimplemented();
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }

        if (info != null) {
            assert exceptionOffset != -1;
            crb.recordImplicitException(exceptionOffset, info);
        }
    }

    @SuppressWarnings("unused")
    public static void emit(CompilationResultBuilder crb, SPARCAssembler masm, SPARCArithmetic opcode, Value dst, Value src, LIRFrameState info) {
        int exceptionOffset = -1;
        if (isRegister(src)) {
            switch (opcode) {
                case INEG:
                    new Neg(asIntReg(src), asIntReg(dst)).emit(masm);
                    break;
                case INOT:
                    new Not(asIntReg(src), asIntReg(dst)).emit(masm);
                    break;
                case LNOT:
                    new Not(asLongReg(src), asLongReg(dst)).emit(masm);
                    break;
                case I2L:
                    new Signx(asIntReg(src), asLongReg(dst)).emit(masm);
                    break;
                case L2I:
                    new Signx(asLongReg(src), asIntReg(dst)).emit(masm);
                    break;
                case I2B:
                    new Sll(asIntReg(src), 24, asIntReg(dst)).emit(masm);
                    new Srl(asIntReg(dst), 24, asIntReg(dst)).emit(masm);
                    break;
                case I2C:
                    new Sll(asIntReg(src), 16, asIntReg(dst)).emit(masm);
                    new Srl(asIntReg(dst), 16, asIntReg(dst)).emit(masm);
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
                default:
                    throw GraalInternalError.shouldNotReachHere("missing: " + opcode);
            }
        } else if (isConstant(src)) {
            switch (opcode) {
                default:
                    throw GraalInternalError.shouldNotReachHere("missing: " + opcode);
            }
        } else {
            switch (opcode) {
                default:
                    throw GraalInternalError.shouldNotReachHere("missing: " + opcode);
            }
        }

        if (info != null) {
            assert exceptionOffset != -1;
            crb.recordImplicitException(exceptionOffset, info);
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
                assert rk == Kind.Double && xk == Kind.Double && yk == Kind.Double : "opcode=" + opcode + ", result kind=" + rk + ", x kind=" + xk + ", y kind=" + yk;
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("missing: " + opcode);
        }
    }
}
