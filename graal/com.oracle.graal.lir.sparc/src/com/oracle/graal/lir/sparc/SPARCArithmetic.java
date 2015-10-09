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
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.sparc.SPARC.g0;
import static jdk.vm.ci.sparc.SPARCKind.DOUBLE;
import static jdk.vm.ci.sparc.SPARCKind.DWORD;
import static jdk.vm.ci.sparc.SPARCKind.SINGLE;
import static jdk.vm.ci.sparc.SPARCKind.WORD;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.sparc.SPARC;

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

public class SPARCArithmetic {
    public static final class FloatConvertOp extends SPARCLIRInstruction {
        public static final LIRInstructionClass<FloatConvertOp> TYPE = LIRInstructionClass.create(FloatConvertOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(5);

        @Opcode private final FloatConvert opcode;
        @Def({REG, HINT}) protected Value result;
        @Use({REG}) protected Value x;

        public enum FloatConvert {
            F2I,
            D2I,
            F2L,
            D2L
        }

        public FloatConvertOp(FloatConvert opcode, Value x, Value result) {
            super(TYPE, SIZE);
            this.opcode = opcode;
            this.x = x;
            this.result = result;
        }

        @Override
        protected void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            Label notOrdered = new Label();
            switch (opcode) {
                case F2L:
                    masm.fcmp(Fcc0, Fcmps, asRegister(x, SINGLE), asRegister(x, SINGLE));
                    masm.fbpcc(F_Ordered, ANNUL, notOrdered, Fcc0, PREDICT_TAKEN);
                    masm.fstox(asRegister(x, SINGLE), asRegister(result, DOUBLE));
                    masm.fxtod(asRegister(result), asRegister(result));
                    masm.fsubd(asRegister(result, DOUBLE), asRegister(result, DOUBLE), asRegister(result, DOUBLE));
                    masm.bind(notOrdered);
                    break;
                case F2I:
                    masm.fcmp(Fcc0, Fcmps, asRegister(x, SINGLE), asRegister(x, SINGLE));
                    masm.fbpcc(F_Ordered, ANNUL, notOrdered, Fcc0, PREDICT_TAKEN);
                    masm.fstoi(asRegister(x, SINGLE), asRegister(result, SINGLE));
                    masm.fitos(asRegister(result, SINGLE), asRegister(result, SINGLE));
                    masm.fsubs(asRegister(result, SINGLE), asRegister(result, SINGLE), asRegister(result, SINGLE));
                    masm.bind(notOrdered);
                    break;
                case D2L:
                    masm.fcmp(Fcc0, Fcmpd, asRegister(x, DOUBLE), asRegister(x, DOUBLE));
                    masm.fbpcc(F_Ordered, ANNUL, notOrdered, Fcc0, PREDICT_TAKEN);
                    masm.fdtox(asRegister(x, DOUBLE), asRegister(result, DOUBLE));
                    masm.fxtod(asRegister(result, DOUBLE), asRegister(result, DOUBLE));
                    masm.fsubd(asRegister(result, DOUBLE), asRegister(result, DOUBLE), asRegister(result, DOUBLE));
                    masm.bind(notOrdered);
                    break;
                case D2I:
                    masm.fcmp(Fcc0, Fcmpd, asRegister(x, DOUBLE), asRegister(x, DOUBLE));
                    masm.fbpcc(F_Ordered, ANNUL, notOrdered, Fcc0, PREDICT_TAKEN);
                    masm.fdtoi(asRegister(x, DOUBLE), asRegister(result, SINGLE));
                    masm.fitos(asRegister(result, SINGLE), asRegister(result, SINGLE));
                    masm.fsubs(asRegister(result, SINGLE), asRegister(result, SINGLE), asRegister(result, SINGLE));
                    masm.bind(notOrdered);
                    break;
                default:
                    throw JVMCIError.shouldNotReachHere("missing: " + opcode);
            }
        }
    }

    /**
     * Special LIR instruction as it requires a bunch of scratch registers.
     */
    public static final class RemOp extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<RemOp> TYPE = LIRInstructionClass.create(RemOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(4);

        @Opcode private final Rem opcode;
        @Def({REG}) protected Value result;
        @Alive({REG, CONST}) protected Value x;
        @Alive({REG, CONST}) protected Value y;
        @Temp({REG}) protected Value scratch1;
        @Temp({REG}) protected Value scratch2;
        @State protected LIRFrameState state;

        public enum Rem {
            IUREM,
            LUREM
        }

        public RemOp(Rem opcode, Value result, Value x, Value y, Value scratch1, Value scratch2, LIRFrameState state) {
            super(TYPE, SIZE);
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
            this.scratch1 = scratch1;
            this.scratch2 = scratch2;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            if (!isJavaConstant(x) && isJavaConstant(y)) {
                assert isSimm13(crb.asIntConst(y));
                assert !x.equals(scratch1);
                assert !x.equals(scratch2);
                assert !y.equals(scratch1);
                switch (opcode) {
                    case LUREM:
                        crb.recordImplicitException(masm.position(), state);
                        masm.udivx(asRegister(x, DWORD), crb.asIntConst(y), asRegister(scratch1, DWORD));
                        masm.mulx(asRegister(scratch1, DWORD), crb.asIntConst(y), asRegister(scratch2, DWORD));
                        getDelayedControlTransfer().emitControlTransfer(crb, masm);
                        masm.sub(asRegister(x, DWORD), asRegister(scratch2, DWORD), asRegister(result, DWORD));
                        break;
                    case IUREM:
                        JVMCIError.unimplemented();
                        break;
                    default:
                        throw JVMCIError.shouldNotReachHere();
                }
            } else if (isRegister(x) && isRegister(y)) {
                Value xLeft = x;
                switch (opcode) {
                    case LUREM:
                        if (isJavaConstant(x)) {
                            new Setx(crb.asLongConst(x), asRegister(scratch2, DWORD), false).emit(masm);
                            xLeft = scratch2;
                        }
                        assert !asRegister(xLeft, DWORD).equals(asRegister(scratch1, DWORD));
                        assert !asRegister(y, DWORD).equals(asRegister(scratch1, DWORD));
                        crb.recordImplicitException(masm.position(), state);
                        masm.udivx(asRegister(xLeft, DWORD), asRegister(y, DWORD), asRegister(scratch1, DWORD));
                        masm.mulx(asRegister(scratch1, DWORD), asRegister(y, DWORD), asRegister(scratch1, DWORD));
                        getDelayedControlTransfer().emitControlTransfer(crb, masm);
                        masm.sub(asRegister(xLeft, DWORD), asRegister(scratch1, DWORD), asRegister(result, DWORD));
                        break;
                    case IUREM:
                        assert !asRegister(result, WORD).equals(asRegister(scratch1, WORD));
                        assert !asRegister(result, WORD).equals(asRegister(scratch2, WORD));
                        masm.srl(asRegister(x, WORD), 0, asRegister(scratch1, WORD));
                        masm.srl(asRegister(y, WORD), 0, asRegister(result, WORD));
                        crb.recordImplicitException(masm.position(), state);
                        masm.udivx(asRegister(scratch1, WORD), asRegister(result, WORD), asRegister(scratch2, WORD));
                        masm.mulx(asRegister(scratch2, WORD), asRegister(result, WORD), asRegister(result, WORD));
                        getDelayedControlTransfer().emitControlTransfer(crb, masm);
                        masm.sub(asRegister(scratch1, WORD), asRegister(result, WORD), asRegister(result, WORD));
                        break;
                    default:
                        throw JVMCIError.shouldNotReachHere();
                }
            } else {
                throw JVMCIError.shouldNotReachHere();
            }
        }
    }

    public static final class SPARCIMulccOp extends SPARCLIRInstruction {
        public static final LIRInstructionClass<SPARCIMulccOp> TYPE = LIRInstructionClass.create(SPARCIMulccOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(10);
        @Def({REG}) protected Value result;
        @Alive({REG}) protected Value x;
        @Alive({REG}) protected Value y;

        public SPARCIMulccOp(Value result, Value x, Value y) {
            super(TYPE, SIZE);
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        protected void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            try (ScratchRegister tmpScratch = masm.getScratchRegister()) {
                Register tmp = tmpScratch.getRegister();
                masm.mulx(asRegister(x, WORD), asRegister(y, WORD), asRegister(result, WORD));
                Label noOverflow = new Label();
                masm.sra(asRegister(result, WORD), 0, tmp);
                masm.xorcc(SPARC.g0, SPARC.g0, SPARC.g0);
                masm.compareBranch(tmp, asRegister(result), Equal, Xcc, noOverflow, PREDICT_TAKEN, null);
                masm.wrccr(SPARC.g0, 1 << (SPARCAssembler.CCR_ICC_SHIFT + SPARCAssembler.CCR_V_SHIFT));
                masm.bind(noOverflow);
            }
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
            masm.mulx(asRegister(x, DWORD), asRegister(y, DWORD), asRegister(result, DWORD));

            // Calculate the upper 64 bit signed := (umulxhi product - (x{63}&y + y{63}&x))
            masm.umulxhi(asRegister(x, DWORD), asRegister(y, DWORD), asRegister(scratch1, DWORD));
            masm.srax(asRegister(x, DWORD), 63, asRegister(scratch2, DWORD));
            masm.and(asRegister(scratch2, DWORD), asRegister(y, DWORD), asRegister(scratch2, DWORD));
            masm.sub(asRegister(scratch1, DWORD), asRegister(scratch2, DWORD), asRegister(scratch1, DWORD));

            masm.srax(asRegister(y, DWORD), 63, asRegister(scratch2, DWORD));
            masm.and(asRegister(scratch2, DWORD), asRegister(x, DWORD), asRegister(scratch2, DWORD));
            masm.sub(asRegister(scratch1, DWORD), asRegister(scratch2, DWORD), asRegister(scratch1, DWORD));

            // Now construct the lower half and compare
            masm.srax(asRegister(result, DWORD), 63, asRegister(scratch2, DWORD));
            masm.cmp(asRegister(scratch1, DWORD), asRegister(scratch2, DWORD));
            masm.bpcc(Equal, NOT_ANNUL, noOverflow, Xcc, PREDICT_TAKEN);
            masm.nop();
            masm.wrccr(g0, 1 << (CCR_XCC_SHIFT + CCR_V_SHIFT));
            masm.bind(noOverflow);
        }
    }

    public static final class MulHighOp extends SPARCLIRInstruction {
        public static final LIRInstructionClass<MulHighOp> TYPE = LIRInstructionClass.create(MulHighOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(4);

        @Opcode private final MulHigh opcode;
        @Def({REG}) public AllocatableValue result;
        @Alive({REG}) public AllocatableValue x;
        @Alive({REG}) public AllocatableValue y;
        @Temp({REG}) public AllocatableValue scratch;

        public enum MulHigh {
            IMUL,
            LMUL
        }

        public MulHighOp(MulHigh opcode, AllocatableValue x, AllocatableValue y, AllocatableValue result, AllocatableValue scratch) {
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
                    masm.mulx(asRegister(x, WORD), asRegister(y, WORD), asRegister(result, WORD));
                    masm.srax(asRegister(result, WORD), 32, asRegister(result, WORD));
                    break;
                case LMUL:
                    assert !asRegister(scratch, DWORD).equals(asRegister(result, DWORD));
                    masm.umulxhi(asRegister(x, DWORD), asRegister(y, DWORD), asRegister(result, DWORD));

                    masm.srlx(asRegister(x, DWORD), 63, asRegister(scratch, DWORD));
                    masm.mulx(asRegister(scratch, DWORD), asRegister(y, DWORD), asRegister(scratch, DWORD));
                    masm.sub(asRegister(result, DWORD), asRegister(scratch, DWORD), asRegister(result, DWORD));

                    masm.srlx(asRegister(y, DWORD), 63, asRegister(scratch, DWORD));
                    masm.mulx(asRegister(scratch, DWORD), asRegister(x, DWORD), asRegister(scratch, DWORD));
                    masm.sub(asRegister(result, DWORD), asRegister(scratch, DWORD), asRegister(result, DWORD));
                    break;
                default:
                    throw JVMCIError.shouldNotReachHere();
            }
        }
    }
}
