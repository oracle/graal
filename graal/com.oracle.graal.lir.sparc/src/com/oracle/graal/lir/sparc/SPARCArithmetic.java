/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.asm.sparc.SPARCAssembler.CCR_V_SHIFT;
import static com.oracle.graal.asm.sparc.SPARCAssembler.CCR_XCC_SHIFT;
import static com.oracle.graal.asm.sparc.SPARCAssembler.isSimm13;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Annul.ANNUL;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Annul.NOT_ANNUL;
import static com.oracle.graal.asm.sparc.SPARCAssembler.BranchPredict.PREDICT_TAKEN;
import static com.oracle.graal.asm.sparc.SPARCAssembler.CC.Fcc0;
import static com.oracle.graal.asm.sparc.SPARCAssembler.CC.Xcc;
import static com.oracle.graal.asm.sparc.SPARCAssembler.ConditionFlag.Equal;
import static com.oracle.graal.asm.sparc.SPARCAssembler.ConditionFlag.F_Ordered;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fcmpd;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fcmps;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.CONST;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.HINT;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.REG;
import static com.oracle.graal.lir.LIRValueUtil.isJavaConstant;
import static jdk.internal.jvmci.code.ValueUtil.asRegister;
import static jdk.internal.jvmci.code.ValueUtil.isRegister;
import static jdk.internal.jvmci.sparc.SPARC.g0;
import jdk.internal.jvmci.code.Register;
import jdk.internal.jvmci.common.JVMCIError;
import jdk.internal.jvmci.meta.AllocatableValue;
import jdk.internal.jvmci.meta.JavaConstant;
import jdk.internal.jvmci.meta.JavaKind;
import jdk.internal.jvmci.meta.LIRKind;
import jdk.internal.jvmci.meta.PlatformKind;
import jdk.internal.jvmci.meta.Value;
import jdk.internal.jvmci.sparc.SPARC;

import com.oracle.graal.asm.Label;
import com.oracle.graal.asm.sparc.SPARCAssembler;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.ScratchRegister;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Setx;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.Opcode;
import com.oracle.graal.lir.asm.CompilationResultBuilder;
import com.oracle.graal.lir.gen.LIRGeneratorTool;

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
        public static final SizeEstimate SIZE_1 = SizeEstimate.create(1);
        public static final SizeEstimate SIZE_5 = SizeEstimate.create(5);

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
            emitUnary(crb, masm, opcode, result, x, null, getDelayedControlTransfer());
        }

        @Override
        public SizeEstimate estimateSize() {
            switch (opcode) {
                case F2L:
                case F2I:
                case D2L:
                case D2I:
                    return SIZE_5;
                default:
                    return SIZE_1;
            }
        }
    }

    /**
     * Binary operation with two operands. The first source operand is combined with the
     * destination. The second source operand must be a register.
     */
    public static final class BinaryRegReg extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<BinaryRegReg> TYPE = LIRInstructionClass.create(BinaryRegReg.class);
        public static final SizeEstimate SIZE_1 = SizeEstimate.create(1);
        public static final SizeEstimate SIZE_3 = SizeEstimate.create(3);
        public static final SizeEstimate SIZE_7 = SizeEstimate.create(7);

        @Opcode private final SPARCArithmetic opcode;
        @Def({REG}) protected Value result;
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
            emitRegReg(crb, masm, opcode, result, x, y, state, getDelayedControlTransfer());
        }

        @Override
        public void verify() {
            super.verify();
            verifyKind(opcode, result.getPlatformKind(), x.getPlatformKind(), y.getPlatformKind());
        }

        @Override
        public SizeEstimate estimateSize() {
            switch (opcode) {
                case IMULCC:
                    return SIZE_7;
                case IUDIV:
                case IDIV:
                    return SIZE_3;
                default:
                    return SIZE_1;
            }
        }
    }

    /**
     * Binary operation with single source/destination operand and one constant.
     */
    public static final class BinaryRegConst extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<BinaryRegConst> TYPE = LIRInstructionClass.create(BinaryRegConst.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(1);

        @Opcode private final SPARCArithmetic opcode;
        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected Value x;
        @State protected LIRFrameState state;
        protected JavaConstant y;

        public BinaryRegConst(SPARCArithmetic opcode, AllocatableValue result, Value x, JavaConstant y) {
            this(opcode, result, x, y, null);
        }

        public BinaryRegConst(SPARCArithmetic opcode, AllocatableValue result, Value x, JavaConstant y, LIRFrameState state) {
            super(TYPE, SIZE);
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            emitRegConstant(crb, masm, opcode, result, x, y, null, getDelayedControlTransfer());
        }

        @Override
        public void verify() {
            super.verify();
            verifyKind(opcode, result.getPlatformKind(), x.getPlatformKind(), y.getJavaKind());
        }
    }

    /**
     * Special LIR instruction as it requires a bunch of scratch registers.
     */
    public static final class RemOp extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<RemOp> TYPE = LIRInstructionClass.create(RemOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(4);

        @Opcode private final SPARCArithmetic opcode;
        @Def({REG}) protected Value result;
        @Alive({REG, CONST}) protected Value x;
        @Alive({REG, CONST}) protected Value y;
        @Temp({REG}) protected Value scratch1;
        @Temp({REG}) protected Value scratch2;
        @State protected LIRFrameState state;

        public RemOp(SPARCArithmetic opcode, Value result, Value x, Value y, LIRFrameState state, LIRGeneratorTool gen) {
            super(TYPE, SIZE);
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
            this.scratch1 = gen.newVariable(LIRKind.combine(x, y));
            this.scratch2 = gen.newVariable(LIRKind.combine(x, y));
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            emitRem(crb, masm, opcode, result, x, y, scratch1, scratch2, state, getDelayedControlTransfer());
        }

        @Override
        public void verify() {
            super.verify();
            verifyKind(opcode, result.getPlatformKind(), x.getPlatformKind(), y.getPlatformKind());
        }
    }

    /**
     * Calculates the product and condition code for long multiplication of long values.
     */
    public static final class SPARCLMulccOp extends SPARCLIRInstruction {
        public static final LIRInstructionClass<SPARCLMulccOp> TYPE = LIRInstructionClass.create(SPARCLMulccOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(13);

        @Def({REG}) protected Value result;
        @Alive({REG}) protected Value x;
        @Alive({REG}) protected Value y;
        @Temp({REG}) protected Value scratch1;
        @Temp({REG}) protected Value scratch2;

        public SPARCLMulccOp(Value result, Value x, Value y, LIRGeneratorTool gen) {
            super(TYPE, SIZE);
            this.result = result;
            this.x = x;
            this.y = y;
            this.scratch1 = gen.newVariable(LIRKind.combine(x, y));
            this.scratch2 = gen.newVariable(LIRKind.combine(x, y));
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            Label noOverflow = new Label();
            masm.mulx(asRegister(x, JavaKind.Long), asRegister(y, JavaKind.Long), asRegister(result, JavaKind.Long));

            // Calculate the upper 64 bit signed := (umulxhi product - (x{63}&y + y{63}&x))
            masm.umulxhi(asRegister(x, JavaKind.Long), asRegister(y, JavaKind.Long), asRegister(scratch1, JavaKind.Long));
            masm.srax(asRegister(x, JavaKind.Long), 63, asRegister(scratch2, JavaKind.Long));
            masm.and(asRegister(scratch2, JavaKind.Long), asRegister(y, JavaKind.Long), asRegister(scratch2, JavaKind.Long));
            masm.sub(asRegister(scratch1, JavaKind.Long), asRegister(scratch2, JavaKind.Long), asRegister(scratch1, JavaKind.Long));

            masm.srax(asRegister(y, JavaKind.Long), 63, asRegister(scratch2, JavaKind.Long));
            masm.and(asRegister(scratch2, JavaKind.Long), asRegister(x, JavaKind.Long), asRegister(scratch2, JavaKind.Long));
            masm.sub(asRegister(scratch1, JavaKind.Long), asRegister(scratch2, JavaKind.Long), asRegister(scratch1, JavaKind.Long));

            // Now construct the lower half and compare
            masm.srax(asRegister(result, JavaKind.Long), 63, asRegister(scratch2, JavaKind.Long));
            masm.cmp(asRegister(scratch1, JavaKind.Long), asRegister(scratch2, JavaKind.Long));
            masm.bpcc(Equal, NOT_ANNUL, noOverflow, Xcc, PREDICT_TAKEN);
            masm.nop();
            masm.wrccr(g0, 1 << (CCR_XCC_SHIFT + CCR_V_SHIFT));
            masm.bind(noOverflow);
        }
    }

    private static void emitRegConstant(CompilationResultBuilder crb, SPARCMacroAssembler masm, SPARCArithmetic opcode, Value dst, Value src1, JavaConstant src2, LIRFrameState info,
                    SPARCDelayedControlTransfer delaySlotLir) {
        assert isSimm13(src2.asLong()) : src2;
        int constant = (int) src2.asLong();
        int exceptionOffset = -1;
        delaySlotLir.emitControlTransfer(crb, masm);
        switch (opcode) {
            case IADD:
                masm.add(asRegister(src1, JavaKind.Int), constant, asRegister(dst, JavaKind.Int));
                break;
            case IADDCC:
                masm.addcc(asRegister(src1, JavaKind.Int), constant, asRegister(dst, JavaKind.Int));
                break;
            case ISUB:
                masm.sub(asRegister(src1, JavaKind.Int), constant, asRegister(dst, JavaKind.Int));
                break;
            case ISUBCC:
                masm.subcc(asRegister(src1, JavaKind.Int), constant, asRegister(dst, JavaKind.Int));
                break;
            case IMUL:
                masm.mulx(asRegister(src1, JavaKind.Int), constant, asRegister(dst, JavaKind.Int));
                break;
            case IMULCC:
                throw JVMCIError.unimplemented();
            case IDIV:
                masm.sra(asRegister(src1), 0, asRegister(src1));
                masm.sdivx(asRegister(src1, JavaKind.Int), constant, asRegister(dst, JavaKind.Int));
                break;
            case IUDIV:
                masm.srl(asRegister(src1), 0, asRegister(src1));
                masm.udivx(asRegister(src1, JavaKind.Int), constant, asRegister(dst, JavaKind.Int));
                break;
            case IAND:
                masm.and(asRegister(src1, JavaKind.Int), constant, asRegister(dst, JavaKind.Int));
                break;
            case ISHL:
                masm.sll(asRegister(src1, JavaKind.Int), constant, asRegister(dst, JavaKind.Int));
                break;
            case ISHR:
                masm.sra(asRegister(src1, JavaKind.Int), constant, asRegister(dst, JavaKind.Int));
                break;
            case IUSHR:
                masm.srl(asRegister(src1, JavaKind.Int), constant, asRegister(dst, JavaKind.Int));
                break;
            case IOR:
                masm.or(asRegister(src1, JavaKind.Int), constant, asRegister(dst, JavaKind.Int));
                break;
            case IXOR:
                masm.xor(asRegister(src1, JavaKind.Int), constant, asRegister(dst, JavaKind.Int));
                break;
            case LADD:
                masm.add(asRegister(src1, JavaKind.Long), constant, asRegister(dst, JavaKind.Long));
                break;
            case LADDCC:
                masm.addcc(asRegister(src1, JavaKind.Long), constant, asRegister(dst, JavaKind.Long));
                break;
            case LSUB:
                masm.sub(asRegister(src1, JavaKind.Long), constant, asRegister(dst, JavaKind.Long));
                break;
            case LSUBCC:
                masm.subcc(asRegister(src1, JavaKind.Long), constant, asRegister(dst, JavaKind.Long));
                break;
            case LMUL:
                masm.mulx(asRegister(src1, JavaKind.Long), constant, asRegister(dst, JavaKind.Long));
                break;
            case LDIV:
                exceptionOffset = masm.position();
                masm.sdivx(asRegister(src1, JavaKind.Long), constant, asRegister(dst, JavaKind.Long));
                break;
            case LUDIV:
                exceptionOffset = masm.position();
                masm.udivx(asRegister(src1, JavaKind.Long), constant, asRegister(dst, JavaKind.Long));
                break;
            case LAND:
                masm.and(asRegister(src1, JavaKind.Long), constant, asRegister(dst, JavaKind.Long));
                break;
            case LOR:
                masm.or(asRegister(src1, JavaKind.Long), constant, asRegister(dst, JavaKind.Long));
                break;
            case LXOR:
                masm.xor(asRegister(src1, JavaKind.Long), constant, asRegister(dst, JavaKind.Long));
                break;
            case LSHL:
                masm.sllx(asRegister(src1, JavaKind.Long), constant, asRegister(dst, JavaKind.Long));
                break;
            case LSHR:
                masm.srax(asRegister(src1, JavaKind.Long), constant, asRegister(dst, JavaKind.Long));
                break;
            case LUSHR:
                masm.srlx(asRegister(src1, JavaKind.Long), constant, asRegister(dst, JavaKind.Long));
                break;
            case DAND: // Has no constant implementation in SPARC
            case FADD:
            case FMUL:
            case FDIV:
            case DADD:
            case DMUL:
            case DDIV:
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        if (info != null) {
            assert exceptionOffset != -1;
            crb.recordImplicitException(exceptionOffset, info);
        }
    }

    public static void emitRegReg(CompilationResultBuilder crb, SPARCMacroAssembler masm, SPARCArithmetic opcode, Value dst, Value src1, Value src2, LIRFrameState info,
                    SPARCDelayedControlTransfer delaySlotLir) {
        int exceptionOffset = -1;
        assert !isJavaConstant(src1) : src1;
        assert !isJavaConstant(src2) : src2;
        switch (opcode) {
            case IADD:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.add(asRegister(src1, JavaKind.Int), asRegister(src2, JavaKind.Int), asRegister(dst, JavaKind.Int));
                break;
            case IADDCC:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.addcc(asRegister(src1, JavaKind.Int), asRegister(src2, JavaKind.Int), asRegister(dst, JavaKind.Int));
                break;
            case ISUB:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.sub(asRegister(src1, JavaKind.Int), asRegister(src2, JavaKind.Int), asRegister(dst, JavaKind.Int));
                break;
            case ISUBCC:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.subcc(asRegister(src1, JavaKind.Int), asRegister(src2, JavaKind.Int), asRegister(dst, JavaKind.Int));
                break;
            case IMUL:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.mulx(asRegister(src1, JavaKind.Int), asRegister(src2, JavaKind.Int), asRegister(dst, JavaKind.Int));
                break;
            case IMULCC:
                try (ScratchRegister tmpScratch = masm.getScratchRegister()) {
                    Register tmp = tmpScratch.getRegister();
                    masm.mulx(asRegister(src1, JavaKind.Int), asRegister(src2, JavaKind.Int), asRegister(dst, JavaKind.Int));
                    Label noOverflow = new Label();
                    masm.sra(asRegister(dst, JavaKind.Int), 0, tmp);
                    masm.xorcc(SPARC.g0, SPARC.g0, SPARC.g0);
                    masm.compareBranch(tmp, asRegister(dst), Equal, Xcc, noOverflow, PREDICT_TAKEN, null);
                    masm.wrccr(SPARC.g0, 1 << (SPARCAssembler.CCR_ICC_SHIFT + SPARCAssembler.CCR_V_SHIFT));
                    masm.bind(noOverflow);
                }
                break;
            case IDIV:
                masm.signx(asRegister(src1, JavaKind.Int), asRegister(src1, JavaKind.Int));
                masm.signx(asRegister(src2, JavaKind.Int), asRegister(src2, JavaKind.Int));
                delaySlotLir.emitControlTransfer(crb, masm);
                exceptionOffset = masm.position();
                masm.sdivx(asRegister(src1, JavaKind.Int), asRegister(src2, JavaKind.Int), asRegister(dst, JavaKind.Int));
                break;
            case IUDIV:
                masm.srl(asRegister(src1, JavaKind.Int), 0, asRegister(src1, JavaKind.Int));
                masm.srl(asRegister(src2, JavaKind.Int), 0, asRegister(src2, JavaKind.Int));
                delaySlotLir.emitControlTransfer(crb, masm);
                exceptionOffset = masm.position();
                masm.udivx(asRegister(src1, JavaKind.Int), asRegister(src2, JavaKind.Int), asRegister(dst, JavaKind.Int));
                break;
            case IAND:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.and(asRegister(src1, JavaKind.Int), asRegister(src2, JavaKind.Int), asRegister(dst, JavaKind.Int));
                break;
            case IOR:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.or(asRegister(src1, JavaKind.Int), asRegister(src2, JavaKind.Int), asRegister(dst, JavaKind.Int));
                break;
            case IXOR:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.xor(asRegister(src1, JavaKind.Int), asRegister(src2, JavaKind.Int), asRegister(dst, JavaKind.Int));
                break;
            case ISHL:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.sll(asRegister(src1, JavaKind.Int), asRegister(src2, JavaKind.Int), asRegister(dst, JavaKind.Int));
                break;
            case ISHR:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.sra(asRegister(src1, JavaKind.Int), asRegister(src2, JavaKind.Int), asRegister(dst, JavaKind.Int));
                break;
            case IUSHR:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.srl(asRegister(src1, JavaKind.Int), asRegister(src2, JavaKind.Int), asRegister(dst, JavaKind.Int));
                break;
            case IREM:
                throw JVMCIError.unimplemented();
            case LADD:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.add(asRegister(src1, JavaKind.Long), asRegister(src2, JavaKind.Long), asRegister(dst, JavaKind.Long));
                break;
            case LADDCC:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.addcc(asRegister(src1, JavaKind.Long), asRegister(src2, JavaKind.Long), asRegister(dst, JavaKind.Long));
                break;
            case LSUB:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.sub(asRegister(src1, JavaKind.Long), asRegister(src2, JavaKind.Long), asRegister(dst, JavaKind.Long));
                break;
            case LSUBCC:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.subcc(asRegister(src1, JavaKind.Long), asRegister(src2, JavaKind.Long), asRegister(dst, JavaKind.Long));
                break;
            case LMUL:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.mulx(asRegister(src1, JavaKind.Long), asRegister(src2, JavaKind.Long), asRegister(dst, JavaKind.Long));
                break;
            case LMULCC:
                throw JVMCIError.unimplemented();
            case LDIV:
                delaySlotLir.emitControlTransfer(crb, masm);
                exceptionOffset = masm.position();
                masm.sdivx(asRegister(src1, JavaKind.Long), asRegister(src2, JavaKind.Long), asRegister(dst, JavaKind.Long));
                break;
            case LUDIV:
                delaySlotLir.emitControlTransfer(crb, masm);
                exceptionOffset = masm.position();
                masm.udivx(asRegister(src1, JavaKind.Long), asRegister(src2, JavaKind.Long), asRegister(dst, JavaKind.Long));
                break;
            case LAND:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.and(asRegister(src1, JavaKind.Long), asRegister(src2, JavaKind.Long), asRegister(dst, JavaKind.Long));
                break;
            case LOR:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.or(asRegister(src1, JavaKind.Long), asRegister(src2, JavaKind.Long), asRegister(dst, JavaKind.Long));
                break;
            case LXOR:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.xor(asRegister(src1, JavaKind.Long), asRegister(src2, JavaKind.Long), asRegister(dst, JavaKind.Long));
                break;
            case LSHL:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.sllx(asRegister(src1, JavaKind.Long), asRegister(src2, JavaKind.Int), asRegister(dst, JavaKind.Long));
                break;
            case LSHR:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.srax(asRegister(src1, JavaKind.Long), asRegister(src2, JavaKind.Int), asRegister(dst, JavaKind.Long));
                break;
            case LUSHR:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.srlx(asRegister(src1, JavaKind.Long), asRegister(src2, JavaKind.Int), asRegister(dst, JavaKind.Long));
                break;
            case FADD:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.fadds(asRegister(src1, JavaKind.Float), asRegister(src2, JavaKind.Float), asRegister(dst, JavaKind.Float));
                break;
            case FSUB:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.fsubs(asRegister(src1, JavaKind.Float), asRegister(src2, JavaKind.Float), asRegister(dst, JavaKind.Float));
                break;
            case FMUL:
                delaySlotLir.emitControlTransfer(crb, masm);
                if (dst.getPlatformKind() == JavaKind.Double) {
                    masm.fsmuld(asRegister(src1, JavaKind.Float), asRegister(src2, JavaKind.Float), asRegister(dst, JavaKind.Double));
                } else if (dst.getPlatformKind() == JavaKind.Float) {
                    masm.fmuls(asRegister(src1, JavaKind.Float), asRegister(src2, JavaKind.Float), asRegister(dst, JavaKind.Float));
                }
                break;
            case FDIV:
                delaySlotLir.emitControlTransfer(crb, masm);
                exceptionOffset = masm.position();
                masm.fdivs(asRegister(src1, JavaKind.Float), asRegister(src2, JavaKind.Float), asRegister(dst, JavaKind.Float));
                break;
            case FREM:
                throw JVMCIError.unimplemented();
            case DADD:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.faddd(asRegister(src1, JavaKind.Double), asRegister(src2, JavaKind.Double), asRegister(dst, JavaKind.Double));
                break;
            case DSUB:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.fsubd(asRegister(src1, JavaKind.Double), asRegister(src2, JavaKind.Double), asRegister(dst, JavaKind.Double));
                break;
            case DMUL:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.fmuld(asRegister(src1, JavaKind.Double), asRegister(src2, JavaKind.Double), asRegister(dst, JavaKind.Double));
                break;
            case DDIV:
                delaySlotLir.emitControlTransfer(crb, masm);
                exceptionOffset = masm.position();
                masm.fdivd(asRegister(src1, JavaKind.Double), asRegister(src2, JavaKind.Double), asRegister(dst, JavaKind.Double));
                break;
            case DREM:
                throw JVMCIError.unimplemented();
            case DAND:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.fandd(asRegister(src1, JavaKind.Double), asRegister(src2, JavaKind.Double), asRegister(dst, JavaKind.Double));
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        if (info != null) {
            assert exceptionOffset != -1;
            crb.recordImplicitException(exceptionOffset, info);
        }
    }

    public static void emitRem(CompilationResultBuilder crb, SPARCMacroAssembler masm, SPARCArithmetic opcode, Value dst, Value src1, Value src2, Value scratch1, Value scratch2, LIRFrameState info,
                    SPARCDelayedControlTransfer delaySlotLir) {
        int exceptionOffset = -1;
        if (!isJavaConstant(src1) && isJavaConstant(src2)) {
            assert isSimm13(crb.asIntConst(src2));
            assert !src1.equals(scratch1);
            assert !src1.equals(scratch2);
            assert !src2.equals(scratch1);
            switch (opcode) {
                case IREM:
                    masm.sra(asRegister(src1, JavaKind.Int), 0, asRegister(dst, JavaKind.Int));
                    exceptionOffset = masm.position();
                    masm.sdivx(asRegister(dst, JavaKind.Int), crb.asIntConst(src2), asRegister(scratch1, JavaKind.Int));
                    masm.mulx(asRegister(scratch1, JavaKind.Int), crb.asIntConst(src2), asRegister(scratch2, JavaKind.Int));
                    delaySlotLir.emitControlTransfer(crb, masm);
                    masm.sub(asRegister(dst, JavaKind.Int), asRegister(scratch2, JavaKind.Int), asRegister(dst, JavaKind.Int));
                    break;
                case LREM:
                    exceptionOffset = masm.position();
                    masm.sdivx(asRegister(src1, JavaKind.Long), crb.asIntConst(src2), asRegister(scratch1, JavaKind.Long));
                    masm.mulx(asRegister(scratch1, JavaKind.Long), crb.asIntConst(src2), asRegister(scratch2, JavaKind.Long));
                    delaySlotLir.emitControlTransfer(crb, masm);
                    masm.sub(asRegister(src1, JavaKind.Long), asRegister(scratch2, JavaKind.Long), asRegister(dst, JavaKind.Long));
                    break;
                case LUREM:
                    exceptionOffset = masm.position();
                    masm.udivx(asRegister(src1, JavaKind.Long), crb.asIntConst(src2), asRegister(scratch1, JavaKind.Long));
                    masm.mulx(asRegister(scratch1, JavaKind.Long), crb.asIntConst(src2), asRegister(scratch2, JavaKind.Long));
                    delaySlotLir.emitControlTransfer(crb, masm);
                    masm.sub(asRegister(src1, JavaKind.Long), asRegister(scratch2, JavaKind.Long), asRegister(dst, JavaKind.Long));
                    break;
                case IUREM:
                    JVMCIError.unimplemented();
                    break;
                default:
                    throw JVMCIError.shouldNotReachHere();
            }
        } else if (isRegister(src1) && isRegister(src2)) {
            Value srcLeft = src1;
            switch (opcode) {
                case LREM:
                    if (isJavaConstant(src1)) {
                        new Setx(crb.asLongConst(src1), asRegister(scratch2, JavaKind.Long), false).emit(masm);
                        srcLeft = scratch2;
                    }
                    assert !asRegister(srcLeft, JavaKind.Long).equals(asRegister(scratch1, JavaKind.Long));
                    assert !asRegister(src2, JavaKind.Long).equals(asRegister(scratch1, JavaKind.Long));
                    // But src2 can be scratch2
                    exceptionOffset = masm.position();
                    masm.sdivx(asRegister(srcLeft, JavaKind.Long), asRegister(src2, JavaKind.Long), asRegister(scratch1, JavaKind.Long));
                    masm.mulx(asRegister(scratch1, JavaKind.Long), asRegister(src2, JavaKind.Long), asRegister(scratch1, JavaKind.Long));
                    delaySlotLir.emitControlTransfer(crb, masm);
                    masm.sub(asRegister(srcLeft, JavaKind.Long), asRegister(scratch1, JavaKind.Long), asRegister(dst, JavaKind.Long));
                    break;
                case LUREM:
                    if (isJavaConstant(src1)) {
                        new Setx(crb.asLongConst(src1), asRegister(scratch2, JavaKind.Long), false).emit(masm);
                        srcLeft = scratch2;
                    }
                    assert !asRegister(srcLeft, JavaKind.Long).equals(asRegister(scratch1, JavaKind.Long));
                    assert !asRegister(src2, JavaKind.Long).equals(asRegister(scratch1, JavaKind.Long));
                    exceptionOffset = masm.position();
                    masm.udivx(asRegister(srcLeft, JavaKind.Long), asRegister(src2, JavaKind.Long), asRegister(scratch1, JavaKind.Long));
                    masm.mulx(asRegister(scratch1, JavaKind.Long), asRegister(src2, JavaKind.Long), asRegister(scratch1, JavaKind.Long));
                    delaySlotLir.emitControlTransfer(crb, masm);
                    masm.sub(asRegister(srcLeft, JavaKind.Long), asRegister(scratch1, JavaKind.Long), asRegister(dst, JavaKind.Long));
                    break;
                case IREM:
                    if (isJavaConstant(src1)) {
                        new Setx(crb.asIntConst(src1), asRegister(scratch2, JavaKind.Int), false).emit(masm);
                        srcLeft = scratch2;
                    }
                    assert !asRegister(srcLeft, JavaKind.Int).equals(asRegister(scratch1, JavaKind.Int));
                    assert !asRegister(src2, JavaKind.Int).equals(asRegister(scratch1, JavaKind.Int));
                    masm.sra(asRegister(src1, JavaKind.Int), 0, asRegister(scratch1, JavaKind.Int));
                    masm.sra(asRegister(src2, JavaKind.Int), 0, asRegister(scratch2, JavaKind.Int));
                    exceptionOffset = masm.position();
                    masm.sdivx(asRegister(scratch1, JavaKind.Int), asRegister(scratch2, JavaKind.Int), asRegister(dst, JavaKind.Int));
                    masm.mulx(asRegister(dst, JavaKind.Int), asRegister(scratch2, JavaKind.Int), asRegister(dst, JavaKind.Int));
                    delaySlotLir.emitControlTransfer(crb, masm);
                    masm.sub(asRegister(scratch1, JavaKind.Int), asRegister(dst, JavaKind.Int), asRegister(dst, JavaKind.Int));
                    break;
                case IUREM:
                    assert !asRegister(dst, JavaKind.Int).equals(asRegister(scratch1, JavaKind.Int));
                    assert !asRegister(dst, JavaKind.Int).equals(asRegister(scratch2, JavaKind.Int));
                    masm.srl(asRegister(src1, JavaKind.Int), 0, asRegister(scratch1, JavaKind.Int));
                    masm.srl(asRegister(src2, JavaKind.Int), 0, asRegister(dst, JavaKind.Int));
                    exceptionOffset = masm.position();
                    masm.udivx(asRegister(scratch1, JavaKind.Int), asRegister(dst, JavaKind.Int), asRegister(scratch2, JavaKind.Int));
                    masm.mulx(asRegister(scratch2, JavaKind.Int), asRegister(dst, JavaKind.Int), asRegister(dst, JavaKind.Int));
                    delaySlotLir.emitControlTransfer(crb, masm);
                    masm.sub(asRegister(scratch1, JavaKind.Int), asRegister(dst, JavaKind.Int), asRegister(dst, JavaKind.Int));
                    break;
                default:
                    throw JVMCIError.shouldNotReachHere();
            }
        } else {
            throw JVMCIError.shouldNotReachHere();
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
                masm.neg(asRegister(src, JavaKind.Int), asRegister(dst, JavaKind.Int));
                break;
            case LNEG:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.neg(asRegister(src, JavaKind.Long), asRegister(dst, JavaKind.Long));
                break;
            case INOT:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.not(asRegister(src, JavaKind.Int), asRegister(dst, JavaKind.Int));
                break;
            case LNOT:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.not(asRegister(src, JavaKind.Long), asRegister(dst, JavaKind.Long));
                break;
            case D2F:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.fdtos(asRegister(src, JavaKind.Double), asRegister(dst, JavaKind.Float));
                break;
            case L2D:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.fxtod(asRegister(src, JavaKind.Double), asRegister(dst, JavaKind.Double));
                break;
            case L2F:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.fxtos(asRegister(src, JavaKind.Double), asRegister(dst, JavaKind.Float));
                break;
            case I2D:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.fitod(asRegister(src, JavaKind.Float), asRegister(dst, JavaKind.Double));
                break;
            case I2L:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.signx(asRegister(src, JavaKind.Int), asRegister(dst, JavaKind.Long));
                break;
            case L2I:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.signx(asRegister(src, JavaKind.Long), asRegister(dst, JavaKind.Int));
                break;
            case B2L:
                masm.sll(asRegister(src), 24, asRegister(dst, JavaKind.Long));
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.sra(asRegister(dst, JavaKind.Long), 24, asRegister(dst, JavaKind.Long));
                break;
            case B2I:
                masm.sll(asRegister(src), 24, asRegister(dst, JavaKind.Int));
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.sra(asRegister(dst, JavaKind.Int), 24, asRegister(dst, JavaKind.Int));
                break;
            case S2L:
                masm.sll(asRegister(src), 16, asRegister(dst, JavaKind.Long));
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.sra(asRegister(dst, JavaKind.Long), 16, asRegister(dst, JavaKind.Long));
                break;
            case S2I:
                masm.sll(asRegister(src), 16, asRegister(dst, JavaKind.Int));
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.sra(asRegister(dst, JavaKind.Int), 16, asRegister(dst, JavaKind.Int));
                break;
            case I2F:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.fitos(asRegister(src, JavaKind.Float), asRegister(dst, JavaKind.Float));
                break;
            case F2D:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.fstod(asRegister(src, JavaKind.Float), asRegister(dst, JavaKind.Double));
                break;
            case F2L:
                masm.fcmp(Fcc0, Fcmps, asRegister(src, JavaKind.Float), asRegister(src, JavaKind.Float));
                masm.fbpcc(F_Ordered, ANNUL, notOrdered, Fcc0, PREDICT_TAKEN);
                masm.fstox(asRegister(src, JavaKind.Float), asRegister(dst, JavaKind.Double));
                masm.fxtod(asRegister(dst), asRegister(dst));
                masm.fsubd(asRegister(dst, JavaKind.Double), asRegister(dst, JavaKind.Double), asRegister(dst, JavaKind.Double));
                masm.bind(notOrdered);
                break;
            case F2I:
                masm.fcmp(Fcc0, Fcmps, asRegister(src, JavaKind.Float), asRegister(src, JavaKind.Float));
                masm.fbpcc(F_Ordered, ANNUL, notOrdered, Fcc0, PREDICT_TAKEN);
                masm.fstoi(asRegister(src, JavaKind.Float), asRegister(dst, JavaKind.Float));
                masm.fitos(asRegister(dst, JavaKind.Float), asRegister(dst, JavaKind.Float));
                masm.fsubs(asRegister(dst, JavaKind.Float), asRegister(dst, JavaKind.Float), asRegister(dst, JavaKind.Float));
                masm.bind(notOrdered);
                break;
            case D2L:
                masm.fcmp(Fcc0, Fcmpd, asRegister(src, JavaKind.Double), asRegister(src, JavaKind.Double));
                masm.fbpcc(F_Ordered, ANNUL, notOrdered, Fcc0, PREDICT_TAKEN);
                masm.fdtox(asRegister(src, JavaKind.Double), asRegister(dst, JavaKind.Double));
                masm.fxtod(asRegister(dst, JavaKind.Double), asRegister(dst, JavaKind.Double));
                masm.fsubd(asRegister(dst, JavaKind.Double), asRegister(dst, JavaKind.Double), asRegister(dst, JavaKind.Double));
                masm.bind(notOrdered);
                break;
            case D2I:
                masm.fcmp(Fcc0, Fcmpd, asRegister(src, JavaKind.Double), asRegister(src, JavaKind.Double));
                masm.fbpcc(F_Ordered, ANNUL, notOrdered, Fcc0, PREDICT_TAKEN);
                masm.fdtoi(asRegister(src, JavaKind.Double), asRegister(dst, JavaKind.Float));
                masm.fitos(asRegister(dst, JavaKind.Float), asRegister(dst, JavaKind.Float));
                masm.fsubs(asRegister(dst, JavaKind.Float), asRegister(dst, JavaKind.Float), asRegister(dst, JavaKind.Float));
                masm.bind(notOrdered);
                break;
            case FNEG:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.fnegs(asRegister(src, JavaKind.Float), asRegister(dst, JavaKind.Float));
                break;
            case DNEG:
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.fnegd(asRegister(src, JavaKind.Double), asRegister(dst, JavaKind.Double));
                break;
            default:
                throw JVMCIError.shouldNotReachHere("missing: " + opcode);
        }
        if (info != null) {
            assert exceptionOffset != -1;
            crb.recordImplicitException(exceptionOffset, info);
        }
    }

    private static void verifyKind(SPARCArithmetic opcode, PlatformKind result, PlatformKind x, PlatformKind y) {
        JavaKind rk;
        JavaKind xk;
        JavaKind yk;
        JavaKind xsk;
        JavaKind ysk;

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
                rk = ((JavaKind) result).getStackKind();
                xsk = ((JavaKind) x).getStackKind();
                ysk = ((JavaKind) y).getStackKind();
                boolean valid = false;
                for (JavaKind k : new JavaKind[]{JavaKind.Int, JavaKind.Short, JavaKind.Byte, JavaKind.Char}) {
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
                rk = (JavaKind) result;
                xk = (JavaKind) x;
                yk = (JavaKind) y;
                assert rk == JavaKind.Long && xk == JavaKind.Long && yk == JavaKind.Long;
                break;
            case LSHL:
            case LSHR:
            case LUSHR:
                rk = (JavaKind) result;
                xk = (JavaKind) x;
                yk = (JavaKind) y;
                assert rk == JavaKind.Long && xk == JavaKind.Long && (yk == JavaKind.Int || yk == JavaKind.Long);
                break;
            case FADD:
            case FSUB:
            case FMUL:
            case FDIV:
            case FREM:
                rk = (JavaKind) result;
                xk = (JavaKind) x;
                yk = (JavaKind) y;
                assert (rk == JavaKind.Float || rk == JavaKind.Double) && xk == JavaKind.Float && yk == JavaKind.Float;
                break;
            case DAND:
            case DADD:
            case DSUB:
            case DMUL:
            case DDIV:
            case DREM:
                rk = (JavaKind) result;
                xk = (JavaKind) x;
                yk = (JavaKind) y;
                assert rk == JavaKind.Double && xk == JavaKind.Double && yk == JavaKind.Double : "opcode=" + opcode + ", result kind=" + rk + ", x kind=" + xk + ", y kind=" + yk;
                break;
            default:
                throw JVMCIError.shouldNotReachHere("missing: " + opcode);
        }
    }

    public static final class MulHighOp extends SPARCLIRInstruction {
        public static final LIRInstructionClass<MulHighOp> TYPE = LIRInstructionClass.create(MulHighOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(4);

        @Opcode private final SPARCArithmetic opcode;
        @Def({REG}) public AllocatableValue result;
        @Alive({REG}) public AllocatableValue x;
        @Alive({REG}) public AllocatableValue y;
        @Temp({REG}) public AllocatableValue scratch;

        public MulHighOp(SPARCArithmetic opcode, AllocatableValue x, AllocatableValue y, AllocatableValue result, AllocatableValue scratch) {
            super(TYPE, SIZE);
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
                    masm.sra(asRegister(x), 0, asRegister(x));
                    masm.sra(asRegister(y), 0, asRegister(y));
                    masm.mulx(asRegister(x, JavaKind.Int), asRegister(y, JavaKind.Int), asRegister(result, JavaKind.Int));
                    masm.srax(asRegister(result, JavaKind.Int), 32, asRegister(result, JavaKind.Int));
                    break;
                case IUMUL:
                    assert !asRegister(scratch, JavaKind.Int).equals(asRegister(result, JavaKind.Int));
                    masm.srl(asRegister(x, JavaKind.Int), 0, asRegister(scratch, JavaKind.Int));
                    masm.srl(asRegister(y, JavaKind.Int), 0, asRegister(result, JavaKind.Int));
                    masm.mulx(asRegister(result, JavaKind.Int), asRegister(scratch, JavaKind.Int), asRegister(result, JavaKind.Int));
                    masm.srlx(asRegister(result, JavaKind.Int), 32, asRegister(result, JavaKind.Int));
                    break;
                case LMUL:
                    assert !asRegister(scratch, JavaKind.Long).equals(asRegister(result, JavaKind.Long));
                    masm.umulxhi(asRegister(x, JavaKind.Long), asRegister(y, JavaKind.Long), asRegister(result, JavaKind.Long));

                    masm.srlx(asRegister(x, JavaKind.Long), 63, asRegister(scratch, JavaKind.Long));
                    masm.mulx(asRegister(scratch, JavaKind.Long), asRegister(y, JavaKind.Long), asRegister(scratch, JavaKind.Long));
                    masm.sub(asRegister(result, JavaKind.Long), asRegister(scratch, JavaKind.Long), asRegister(result, JavaKind.Long));

                    masm.srlx(asRegister(y, JavaKind.Long), 63, asRegister(scratch, JavaKind.Long));
                    masm.mulx(asRegister(scratch, JavaKind.Long), asRegister(x, JavaKind.Long), asRegister(scratch, JavaKind.Long));
                    masm.sub(asRegister(result, JavaKind.Long), asRegister(scratch, JavaKind.Long), asRegister(result, JavaKind.Long));
                    break;
                case LUMUL:
                    masm.umulxhi(asRegister(x, JavaKind.Long), asRegister(y, JavaKind.Long), asRegister(result, JavaKind.Long));
                    break;
                default:
                    throw JVMCIError.shouldNotReachHere();
            }
        }
    }
}
