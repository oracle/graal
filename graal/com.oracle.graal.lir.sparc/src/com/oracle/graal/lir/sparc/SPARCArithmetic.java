/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.asm.sparc.SPARCAssembler.Annul.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.BranchPredict.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.CC.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.ConditionFlag.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;
import static com.oracle.graal.sparc.SPARC.*;
import static com.oracle.graal.sparc.SPARC.CPUFeature.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.sparc.*;

public enum SPARCArithmetic {
    // @formatter:off
    IADD, ISUB, IMUL, IUMUL, IDIV, IREM, IUDIV, IUREM, IAND, IOR, IXOR, ISHL, ISHR, IUSHR,
    LADD, LSUB, LMUL, LUMUL, LDIV, LREM, LUDIV, LUREM, LAND, LOR, LXOR, LSHL, LSHR, LUSHR,
    IADDCC, ISUBCC, IMULCC,
    LADDCC, LSUBCC, LMULCC,
    FADD, FSUB, FMUL, FDIV, FREM, FAND, FOR, FXOR,
    DADD, DSUB, DMUL, DDIV, DREM, DAND, DOR, DXOR,
    INEG, LNEG, FNEG, DNEG, INOT, LNOT,
    L2I, B2I, S2I, B2L, S2L, I2L,
    F2D, D2F,
    I2F, I2D, F2I, D2I,
    L2F, L2D, F2L, D2L;
    // @formatter:on

    /**
     * Unary operation with separate source and destination operand.
     */
    public static final class Unary2Op extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<Unary2Op> TYPE = LIRInstructionClass.create(Unary2Op.class);

        @Opcode private final SPARCArithmetic opcode;
        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue x;

        public Unary2Op(SPARCArithmetic opcode, AllocatableValue result, AllocatableValue x) {
            super(TYPE);
            this.opcode = opcode;
            this.result = result;
            this.x = x;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            emitUnary(crb, masm, opcode, result, x, null, delayedControlTransfer);
        }
    }

    /**
     * Binary operation with two operands. The first source operand is combined with the
     * destination. The second source operand must be a register.
     */
    public static final class BinaryRegReg extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<BinaryRegReg> TYPE = LIRInstructionClass.create(BinaryRegReg.class);

        @Opcode private final SPARCArithmetic opcode;
        @Def({REG, HINT}) protected Value result;
        @Use({REG}) protected Value x;
        @Alive({REG}) protected Value y;
        @State LIRFrameState state;

        public BinaryRegReg(SPARCArithmetic opcode, Value result, Value x, Value y) {
            this(opcode, result, x, y, null);
        }

        public BinaryRegReg(SPARCArithmetic opcode, Value result, Value x, Value y, LIRFrameState state) {
            super(TYPE);
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            emitRegReg(crb, masm, opcode, result, x, y, state, delayedControlTransfer);
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
    public static final class BinaryRegConst extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<BinaryRegConst> TYPE = LIRInstructionClass.create(BinaryRegConst.class);

        @Opcode private final SPARCArithmetic opcode;
        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG}) protected Value x;
        @State protected LIRFrameState state;
        protected JavaConstant y;

        public BinaryRegConst(SPARCArithmetic opcode, AllocatableValue result, Value x, JavaConstant y) {
            this(opcode, result, x, y, null);
        }

        public BinaryRegConst(SPARCArithmetic opcode, AllocatableValue result, Value x, JavaConstant y, LIRFrameState state) {
            super(TYPE);
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            emitRegConstant(crb, masm, opcode, result, x, y, null, delayedControlTransfer);
        }

        @Override
        public void verify() {
            super.verify();
            verifyKind(opcode, result, x, y);
        }
    }

    /**
     * Special LIR instruction as it requires a bunch of scratch registers.
     */
    public static final class RemOp extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<RemOp> TYPE = LIRInstructionClass.create(RemOp.class);

        @Opcode private final SPARCArithmetic opcode;
        @Def({REG}) protected Value result;
        @Alive({REG, CONST}) protected Value x;
        @Alive({REG, CONST}) protected Value y;
        @Temp({REG}) protected Value scratch1;
        @Temp({REG}) protected Value scratch2;
        @State protected LIRFrameState state;

        public RemOp(SPARCArithmetic opcode, Value result, Value x, Value y, LIRFrameState state, LIRGeneratorTool gen) {
            super(TYPE);
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
            this.scratch1 = gen.newVariable(LIRKind.derive(x, y));
            this.scratch2 = gen.newVariable(LIRKind.derive(x, y));
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            emitRem(crb, masm, opcode, result, x, y, scratch1, scratch2, state, delayedControlTransfer);
        }

        @Override
        public void verify() {
            super.verify();
            verifyKind(opcode, result, x, y);
        }
    }

    /**
     * Calculates the product and condition code for long multiplication of long values.
     */
    public static final class SPARCLMulccOp extends SPARCLIRInstruction {
        public static final LIRInstructionClass<SPARCLMulccOp> TYPE = LIRInstructionClass.create(SPARCLMulccOp.class);
        @Def({REG}) protected Value result;
        @Alive({REG}) protected Value x;
        @Alive({REG}) protected Value y;
        @Temp({REG}) protected Value scratch1;
        @Temp({REG}) protected Value scratch2;

        public SPARCLMulccOp(Value result, Value x, Value y, LIRGeneratorTool gen) {
            super(TYPE);
            this.result = result;
            this.x = x;
            this.y = y;
            this.scratch1 = gen.newVariable(LIRKind.derive(x, y));
            this.scratch2 = gen.newVariable(LIRKind.derive(x, y));
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            Label noOverflow = new Label();
            masm.mulx(asLongReg(x), asLongReg(y), asLongReg(result));

            // Calculate the upper 64 bit signed := (umulxhi product - (x{63}&y + y{63}&x))
            masm.umulxhi(asLongReg(x), asLongReg(y), asLongReg(scratch1));
            masm.srax(asLongReg(x), 63, asLongReg(scratch2));
            masm.and(asLongReg(scratch2), asLongReg(y), asLongReg(scratch2));
            masm.sub(asLongReg(scratch1), asLongReg(scratch2), asLongReg(scratch1));

            masm.srax(asLongReg(y), 63, asLongReg(scratch2));
            masm.and(asLongReg(scratch2), asLongReg(x), asLongReg(scratch2));
            masm.sub(asLongReg(scratch1), asLongReg(scratch2), asLongReg(scratch1));

            // Now construct the lower half and compare
            masm.srax(asLongReg(result), 63, asLongReg(scratch2));
            masm.cmp(asLongReg(scratch1), asLongReg(scratch2));
            masm.bpcc(Equal, NOT_ANNUL, noOverflow, Xcc, PREDICT_TAKEN);
            masm.nop();
            masm.wrccr(g0, 1 << (CCR_XCC_SHIFT + CCR_V_SHIFT));
            masm.bind(noOverflow);
        }
    }

    private static void emitRegConstant(CompilationResultBuilder crb, SPARCMacroAssembler masm, SPARCArithmetic opcode, Value dst, Value src1, JavaConstant src2, LIRFrameState info,
                    SPARCDelayedControlTransfer delaySlotLir) {
        assert isSimm13(crb.asIntConst(src2)) : src2;
        int constant = crb.asIntConst(src2);
        int exceptionOffset = -1;
        delaySlotLir.emitControlTransfer(crb, masm);
        switch (opcode) {
            case IADD:
                masm.add(asIntReg(src1), constant, asIntReg(dst));
                break;
            case IADDCC:
                masm.addcc(asIntReg(src1), constant, asIntReg(dst));
                break;
            case ISUB:
                masm.sub(asIntReg(src1), constant, asIntReg(dst));
                break;
            case ISUBCC:
                masm.subcc(asIntReg(src1), constant, asIntReg(dst));
                break;
            case IMUL:
                masm.mulx(asIntReg(src1), constant, asIntReg(dst));
                break;
            case IMULCC:
                throw GraalInternalError.unimplemented();
            case IDIV:
                masm.sdivx(asIntReg(src1), constant, asIntReg(dst));
                break;
            case IUDIV:
                masm.udivx(asIntReg(src1), constant, asIntReg(dst));
                break;
            case IAND:
                masm.and(asIntReg(src1), constant, asIntReg(dst));
                break;
            case ISHL:
                masm.sll(asIntReg(src1), constant, asIntReg(dst));
                break;
            case ISHR:
                masm.sra(asIntReg(src1), constant, asIntReg(dst));
                break;
            case IUSHR:
                masm.srl(asIntReg(src1), constant, asIntReg(dst));
                break;
            case IOR:
                masm.or(asIntReg(src1), constant, asIntReg(dst));
                break;
            case IXOR:
                masm.xor(asIntReg(src1), constant, asIntReg(dst));
                break;
            case LADD:
                masm.add(asLongReg(src1), constant, asLongReg(dst));
                break;
            case LADDCC:
                masm.addcc(asLongReg(src1), constant, asLongReg(dst));
                break;
            case LSUB:
                masm.sub(asLongReg(src1), constant, asLongReg(dst));
                break;
            case LSUBCC:
                masm.subcc(asLongReg(src1), constant, asLongReg(dst));
                break;
            case LMUL:
                masm.mulx(asLongReg(src1), constant, asLongReg(dst));
                break;
            case LDIV:
                exceptionOffset = masm.position();
                masm.sdivx(asLongReg(src1), constant, asLongReg(dst));
                break;
            case LUDIV:
                exceptionOffset = masm.position();
                masm.udivx(asLongReg(src1), constant, asLongReg(dst));
                break;
            case LAND:
                masm.and(asLongReg(src1), constant, asLongReg(dst));
                break;
            case LOR:
                masm.or(asLongReg(src1), constant, asLongReg(dst));
                break;
            case LXOR:
                masm.xor(asLongReg(src1), constant, asLongReg(dst));
                break;
            case LSHL:
                masm.sllx(asLongReg(src1), constant, asLongReg(dst));
                break;
            case LSHR:
                masm.srax(asLongReg(src1), constant, asLongReg(dst));
                break;
            case LUSHR:
                masm.srlx(asLongReg(src1), constant, asLongReg(dst));
                break;
            case DAND: // Has no constant implementation in SPARC
            case FADD:
            case FMUL:
            case FDIV:
            case DADD:
            case DMUL:
            case DDIV:
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        if (info != null) {
            assert exceptionOffset != -1;
            crb.recordImplicitException(exceptionOffset, info);
        }
    }

    public static void emitRegReg(CompilationResultBuilder crb, SPARCMacroAssembler masm, SPARCArithmetic opcode, Value dst, Value src1, Value src2, LIRFrameState info,
                    SPARCDelayedControlTransfer delaySlotLir) {
        int exceptionOffset = -1;
        assert !isConstant(src1) : src1;
        assert !isConstant(src2) : src2;
        switch (opcode) {
            case IADD:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.add(asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case IADDCC:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.addcc(asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case ISUB:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.sub(asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case ISUBCC:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.subcc(asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case IMUL:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.mulx(asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case IMULCC:
                try (ScratchRegister tmpScratch = masm.getScratchRegister()) {
                    Register tmp = tmpScratch.getRegister();
                    masm.mulx(asIntReg(src1), asIntReg(src2), asIntReg(dst));
                    Label noOverflow = new Label();
                    masm.sra(asIntReg(dst), 0, tmp);
                    masm.xorcc(SPARC.g0, SPARC.g0, SPARC.g0);
                    if (masm.hasFeature(CBCOND)) {
                        masm.cbcondx(Equal, tmp, asIntReg(dst), noOverflow);
                        // Is necessary, otherwise we will have a penalty of 5 cycles in S3
                        masm.nop();
                    } else {
                        masm.cmp(tmp, asIntReg(dst));
                        masm.bpcc(Equal, NOT_ANNUL, noOverflow, Xcc, PREDICT_TAKEN);
                        masm.nop();
                    }
                    masm.wrccr(SPARC.g0, 1 << (SPARCAssembler.CCR_ICC_SHIFT + SPARCAssembler.CCR_V_SHIFT));
                    masm.bind(noOverflow);
                }
                break;
            case IDIV:
                masm.signx(asIntReg(src1), asIntReg(src1));
                masm.signx(asIntReg(src2), asIntReg(src2));
                delaySlotLir.emitControlTransfer(crb, masm);
                exceptionOffset = masm.position();
                masm.sdivx(asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case IUDIV:
                masm.signx(asIntReg(src1), asIntReg(src1));
                masm.signx(asIntReg(src2), asIntReg(src2));
                delaySlotLir.emitControlTransfer(crb, masm);
                exceptionOffset = masm.position();
                masm.udivx(asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case IAND:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.and(asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case IOR:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.or(asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case IXOR:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.xor(asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case ISHL:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.sll(asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case ISHR:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.sra(asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case IUSHR:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.srl(asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case IREM:
                throw GraalInternalError.unimplemented();
            case LADD:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.add(asLongReg(src1), asLongReg(src2), asLongReg(dst));
                break;
            case LADDCC:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.addcc(asLongReg(src1), asLongReg(src2), asLongReg(dst));
                break;
            case LSUB:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.sub(asLongReg(src1), asLongReg(src2), asLongReg(dst));
                break;
            case LSUBCC:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.subcc(asLongReg(src1), asLongReg(src2), asLongReg(dst));
                break;
            case LMUL:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.mulx(asLongReg(src1), asLongReg(src2), asLongReg(dst));
                break;
            case LMULCC:
                throw GraalInternalError.unimplemented();
            case LDIV:
                delaySlotLir.emitControlTransfer(crb, masm);
                exceptionOffset = masm.position();
                masm.sdivx(asLongReg(src1), asLongReg(src2), asLongReg(dst));
                break;
            case LUDIV:
                delaySlotLir.emitControlTransfer(crb, masm);
                exceptionOffset = masm.position();
                masm.udivx(asLongReg(src1), asLongReg(src2), asLongReg(dst));
                break;
            case LAND:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.and(asLongReg(src1), asLongReg(src2), asLongReg(dst));
                break;
            case LOR:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.or(asLongReg(src1), asLongReg(src2), asLongReg(dst));
                break;
            case LXOR:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.xor(asLongReg(src1), asLongReg(src2), asLongReg(dst));
                break;
            case LSHL:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.sllx(asLongReg(src1), asIntReg(src2), asLongReg(dst));
                break;
            case LSHR:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.srax(asLongReg(src1), asIntReg(src2), asLongReg(dst));
                break;
            case LUSHR:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.srlx(asLongReg(src1), asIntReg(src2), asLongReg(dst));
                break;
            case FADD:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.fadds(asFloatReg(src1), asFloatReg(src2), asFloatReg(dst));
                break;
            case FSUB:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.fsubs(asFloatReg(src1), asFloatReg(src2), asFloatReg(dst));
                break;
            case FMUL:
                delaySlotLir.emitControlTransfer(crb, masm);
                if (dst.getPlatformKind() == Kind.Double) {
                    masm.fsmuld(asFloatReg(src1), asFloatReg(src2), asDoubleReg(dst));
                } else if (dst.getPlatformKind() == Kind.Float) {
                    masm.fmuls(asFloatReg(src1), asFloatReg(src2), asFloatReg(dst));
                }
                break;
            case FDIV:
                delaySlotLir.emitControlTransfer(crb, masm);
                exceptionOffset = masm.position();
                masm.fdivs(asFloatReg(src1), asFloatReg(src2), asFloatReg(dst));
                break;
            case FREM:
                throw GraalInternalError.unimplemented();
            case DADD:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.faddd(asDoubleReg(src1), asDoubleReg(src2), asDoubleReg(dst));
                break;
            case DSUB:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.fsubd(asDoubleReg(src1), asDoubleReg(src2), asDoubleReg(dst));
                break;
            case DMUL:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.fmuld(asDoubleReg(src1), asDoubleReg(src2), asDoubleReg(dst));
                break;
            case DDIV:
                delaySlotLir.emitControlTransfer(crb, masm);
                exceptionOffset = masm.position();
                masm.fdivd(asDoubleReg(src1), asDoubleReg(src2), asDoubleReg(dst));
                break;
            case DREM:
                throw GraalInternalError.unimplemented();
            case DAND:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.fandd(asDoubleReg(src1), asDoubleReg(src2), asDoubleReg(dst));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        if (info != null) {
            assert exceptionOffset != -1;
            crb.recordImplicitException(exceptionOffset, info);
        }
    }

    public static void emitRem(CompilationResultBuilder crb, SPARCMacroAssembler masm, SPARCArithmetic opcode, Value dst, Value src1, Value src2, Value scratch1, Value scratch2, LIRFrameState info,
                    SPARCDelayedControlTransfer delaySlotLir) {
        int exceptionOffset = -1;
        if (!isConstant(src1) && isConstant(src2)) {
            assert isSimm13(crb.asIntConst(src2));
            assert !src1.equals(scratch1);
            assert !src1.equals(scratch2);
            assert !src2.equals(scratch1);
            switch (opcode) {
                case IREM:
                    masm.sra(asIntReg(src1), 0, asIntReg(dst));
                    exceptionOffset = masm.position();
                    masm.sdivx(asIntReg(dst), crb.asIntConst(src2), asIntReg(scratch1));
                    masm.mulx(asIntReg(scratch1), crb.asIntConst(src2), asIntReg(scratch2));
                    delaySlotLir.emitControlTransfer(crb, masm);
                    masm.sub(asIntReg(dst), asIntReg(scratch2), asIntReg(dst));
                    break;
                case LREM:
                    exceptionOffset = masm.position();
                    masm.sdivx(asLongReg(src1), crb.asIntConst(src2), asLongReg(scratch1));
                    masm.mulx(asLongReg(scratch1), crb.asIntConst(src2), asLongReg(scratch2));
                    delaySlotLir.emitControlTransfer(crb, masm);
                    masm.sub(asLongReg(src1), asLongReg(scratch2), asLongReg(dst));
                    break;
                case LUREM:
                    exceptionOffset = masm.position();
                    masm.udivx(asLongReg(src1), crb.asIntConst(src2), asLongReg(scratch1));
                    masm.mulx(asLongReg(scratch1), crb.asIntConst(src2), asLongReg(scratch2));
                    delaySlotLir.emitControlTransfer(crb, masm);
                    masm.sub(asLongReg(src1), asLongReg(scratch2), asLongReg(dst));
                    break;
                case IUREM:
                    GraalInternalError.unimplemented();
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        } else if (isRegister(src1) && isRegister(src2)) {
            Value srcLeft = src1;
            switch (opcode) {
                case LREM:
                    if (isConstant(src1)) {
                        new Setx(crb.asLongConst(src1), asLongReg(scratch2), false).emit(masm);
                        srcLeft = scratch2;
                    }
                    assert !asLongReg(srcLeft).equals(asLongReg(scratch1));
                    assert !asLongReg(src2).equals(asLongReg(scratch1));
                    // But src2 can be scratch2
                    exceptionOffset = masm.position();
                    masm.sdivx(asLongReg(srcLeft), asLongReg(src2), asLongReg(scratch1));
                    masm.mulx(asLongReg(scratch1), asLongReg(src2), asLongReg(scratch1));
                    delaySlotLir.emitControlTransfer(crb, masm);
                    masm.sub(asLongReg(srcLeft), asLongReg(scratch1), asLongReg(dst));
                    break;
                case LUREM:
                    if (isConstant(src1)) {
                        new Setx(crb.asLongConst(src1), asLongReg(scratch2), false).emit(masm);
                        srcLeft = scratch2;
                    }
                    assert !asLongReg(srcLeft).equals(asLongReg(scratch1));
                    assert !asLongReg(src2).equals(asLongReg(scratch1));
                    exceptionOffset = masm.position();
                    masm.udivx(asLongReg(srcLeft), asLongReg(src2), asLongReg(scratch1));
                    masm.mulx(asLongReg(scratch1), asLongReg(src2), asLongReg(scratch1));
                    delaySlotLir.emitControlTransfer(crb, masm);
                    masm.sub(asLongReg(srcLeft), asLongReg(scratch1), asLongReg(dst));
                    break;
                case IREM:
                    if (isConstant(src1)) {
                        new Setx(crb.asIntConst(src1), asIntReg(scratch2), false).emit(masm);
                        srcLeft = scratch2;
                    }
                    assert !asIntReg(srcLeft).equals(asIntReg(scratch1));
                    assert !asIntReg(src2).equals(asIntReg(scratch1));
                    masm.sra(asIntReg(src1), 0, asIntReg(scratch1));
                    masm.sra(asIntReg(src2), 0, asIntReg(scratch2));
                    exceptionOffset = masm.position();
                    masm.sdivx(asIntReg(scratch1), asIntReg(scratch2), asIntReg(dst));
                    masm.mulx(asIntReg(dst), asIntReg(scratch2), asIntReg(dst));
                    delaySlotLir.emitControlTransfer(crb, masm);
                    masm.sub(asIntReg(scratch1), asIntReg(dst), asIntReg(dst));
                    break;
                case IUREM:
                    assert !asIntReg(dst).equals(asIntReg(scratch1));
                    assert !asIntReg(dst).equals(asIntReg(scratch2));
                    masm.srl(asIntReg(src1), 0, asIntReg(scratch1));
                    masm.srl(asIntReg(src2), 0, asIntReg(dst));
                    exceptionOffset = masm.position();
                    masm.udivx(asIntReg(scratch1), asIntReg(dst), asIntReg(scratch2));
                    masm.mulx(asIntReg(scratch2), asIntReg(dst), asIntReg(dst));
                    delaySlotLir.emitControlTransfer(crb, masm);
                    masm.sub(asIntReg(scratch1), asIntReg(dst), asIntReg(dst));
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
        if (info != null) {
            assert exceptionOffset != -1;
            crb.recordImplicitException(exceptionOffset, info);
        }
    }

    public static void emitUnary(CompilationResultBuilder crb, SPARCMacroAssembler masm, SPARCArithmetic opcode, Value dst, Value src, LIRFrameState info, SPARCDelayedControlTransfer delaySlotLir) {
        int exceptionOffset = -1;
        Label notOrdered = new Label();
        switch (opcode) {
            case INEG:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.neg(asIntReg(src), asIntReg(dst));
                break;
            case LNEG:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.neg(asLongReg(src), asLongReg(dst));
                break;
            case INOT:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.not(asIntReg(src), asIntReg(dst));
                break;
            case LNOT:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.not(asLongReg(src), asLongReg(dst));
                break;
            case D2F:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.fdtos(asDoubleReg(src), asFloatReg(dst));
                break;
            case L2D:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.fxtod(asDoubleReg(src), asDoubleReg(dst));
                break;
            case L2F:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.fxtos(asDoubleReg(src), asFloatReg(dst));
                break;
            case I2D:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.fitod(asFloatReg(src), asDoubleReg(dst));
                break;
            case I2L:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.signx(asIntReg(src), asLongReg(dst));
                break;
            case L2I:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.signx(asLongReg(src), asIntReg(dst));
                break;
            case B2L:
                masm.sll(asIntReg(src), 24, asLongReg(dst));
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.sra(asLongReg(dst), 24, asLongReg(dst));
                break;
            case B2I:
                masm.sll(asIntReg(src), 24, asIntReg(dst));
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.sra(asIntReg(dst), 24, asIntReg(dst));
                break;
            case S2L:
                masm.sll(asIntReg(src), 16, asLongReg(dst));
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.sra(asLongReg(dst), 16, asLongReg(dst));
                break;
            case S2I:
                masm.sll(asIntReg(src), 16, asIntReg(dst));
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.sra(asIntReg(dst), 16, asIntReg(dst));
                break;
            case I2F:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.fitos(asFloatReg(src), asFloatReg(dst));
                break;
            case F2D:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.fstod(asFloatReg(src), asDoubleReg(dst));
                break;
            case F2L:
                masm.fcmp(Fcc0, Fcmps, asFloatReg(src), asFloatReg(src));
                masm.fbpcc(F_Ordered, ANNUL, notOrdered, Fcc0, PREDICT_TAKEN);
                masm.fstox(asFloatReg(src), asDoubleReg(dst));
                masm.fsubd(asDoubleReg(dst), asDoubleReg(dst), asDoubleReg(dst));
                masm.bind(notOrdered);
                break;
            case F2I:
                masm.fcmp(Fcc0, Fcmps, asFloatReg(src), asFloatReg(src));
                masm.fbpcc(F_Ordered, ANNUL, notOrdered, Fcc0, PREDICT_TAKEN);
                masm.fstoi(asFloatReg(src), asFloatReg(dst));
                masm.fitos(asFloatReg(dst), asFloatReg(dst));
                masm.fsubs(asFloatReg(dst), asFloatReg(dst), asFloatReg(dst));
                masm.bind(notOrdered);
                break;
            case D2L:
                masm.fcmp(Fcc0, Fcmpd, asDoubleReg(src), asDoubleReg(src));
                masm.fbpcc(F_Ordered, ANNUL, notOrdered, Fcc0, PREDICT_TAKEN);
                masm.fdtox(asDoubleReg(src), asDoubleReg(dst));
                masm.fxtod(asDoubleReg(dst), asDoubleReg(dst));
                masm.fsubd(asDoubleReg(dst), asDoubleReg(dst), asDoubleReg(dst));
                masm.bind(notOrdered);
                break;
            case D2I:
                masm.fcmp(Fcc0, Fcmpd, asDoubleReg(src), asDoubleReg(src));
                masm.fbpcc(F_Ordered, ANNUL, notOrdered, Fcc0, PREDICT_TAKEN);
                masm.fdtoi(asDoubleReg(src), asFloatReg(dst));
                masm.fsubs(asFloatReg(dst), asFloatReg(dst), asFloatReg(dst));
                masm.fstoi(asFloatReg(dst), asFloatReg(dst));
                masm.bind(notOrdered);
                break;
            case FNEG:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.fnegs(asFloatReg(src), asFloatReg(dst));
                break;
            case DNEG:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.fnegd(asDoubleReg(src), asDoubleReg(dst));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("missing: " + opcode);
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
            case IADDCC:
            case ISUB:
            case ISUBCC:
            case IMUL:
            case IMULCC:
            case IDIV:
            case IREM:
            case IAND:
            case IOR:
            case IXOR:
            case ISHL:
            case ISHR:
            case IUSHR:
            case IUDIV:
            case IUREM:
                rk = result.getKind().getStackKind();
                xsk = x.getKind().getStackKind();
                ysk = y.getKind().getStackKind();
                boolean valid = false;
                for (Kind k : new Kind[]{Kind.Int, Kind.Short, Kind.Byte, Kind.Char}) {
                    valid |= rk == k && xsk == k && ysk == k;
                }
                assert valid : "rk: " + rk + " xsk: " + xsk + " ysk: " + ysk;
                break;
            case LADD:
            case LADDCC:
            case LSUB:
            case LSUBCC:
            case LMUL:
            case LMULCC:
            case LDIV:
            case LREM:
            case LAND:
            case LOR:
            case LXOR:
            case LUDIV:
            case LUREM:
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
                assert (rk == Kind.Float || rk == Kind.Double) && xk == Kind.Float && yk == Kind.Float;
                break;
            case DAND:
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

    public static final class MulHighOp extends SPARCLIRInstruction {
        public static final LIRInstructionClass<MulHighOp> TYPE = LIRInstructionClass.create(MulHighOp.class);

        @Opcode private final SPARCArithmetic opcode;
        @Def({REG}) public AllocatableValue result;
        @Alive({REG}) public AllocatableValue x;
        @Alive({REG}) public AllocatableValue y;
        @Temp({REG}) public AllocatableValue scratch;

        public MulHighOp(SPARCArithmetic opcode, AllocatableValue x, AllocatableValue y, AllocatableValue result, AllocatableValue scratch) {
            super(TYPE);
            this.opcode = opcode;
            this.x = x;
            this.y = y;
            this.scratch = scratch;
            this.result = result;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            assert isRegister(x) && isRegister(y) && isRegister(result) && isRegister(scratch);
            switch (opcode) {
                case IMUL:
                    masm.mulx(asIntReg(x), asIntReg(y), asIntReg(result));
                    masm.srax(asIntReg(result), 32, asIntReg(result));
                    break;
                case IUMUL:
                    assert !asIntReg(scratch).equals(asIntReg(result));
                    masm.srl(asIntReg(x), 0, asIntReg(scratch));
                    masm.srl(asIntReg(y), 0, asIntReg(result));
                    masm.mulx(asIntReg(result), asIntReg(scratch), asIntReg(result));
                    masm.srlx(asIntReg(result), 32, asIntReg(result));
                    break;
                case LMUL:
                    assert !asLongReg(scratch).equals(asLongReg(result));
                    masm.umulxhi(asLongReg(x), asLongReg(y), asLongReg(result));

                    masm.srlx(asLongReg(x), 63, asLongReg(scratch));
                    masm.mulx(asLongReg(scratch), asLongReg(y), asLongReg(scratch));
                    masm.sub(asLongReg(result), asLongReg(scratch), asLongReg(result));

                    masm.srlx(asLongReg(y), 63, asLongReg(scratch));
                    masm.mulx(asLongReg(scratch), asLongReg(x), asLongReg(scratch));
                    masm.sub(asLongReg(result), asLongReg(scratch), asLongReg(result));
                    break;
                case LUMUL:
                    masm.umulxhi(asLongReg(x), asLongReg(y), asLongReg(result));
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }
    }
}
