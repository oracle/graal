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
package com.oracle.graal.hotspot.sparc;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.sparc.SPARCAssembler.Annul;
import com.oracle.graal.asm.sparc.SPARCAssembler.BranchPredict;
import com.oracle.graal.asm.sparc.SPARCAssembler.CC;
import com.oracle.graal.asm.sparc.SPARCAssembler.ConditionFlag;
import com.oracle.graal.asm.sparc.SPARCAssembler.RCondition;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.hotspot.HotSpotVMConfig.CompressEncoding;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.MoveOp;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.sparc.*;

public class SPARCHotSpotMove {

    public static final class HotSpotLoadConstantOp extends SPARCLIRInstruction implements MoveOp {
        public static final LIRInstructionClass<HotSpotLoadConstantOp> TYPE = LIRInstructionClass.create(HotSpotLoadConstantOp.class);

        @Def({REG}) private AllocatableValue result;
        private final JavaConstant input;

        public HotSpotLoadConstantOp(AllocatableValue result, JavaConstant input) {
            super(TYPE);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            assert isRegister(result);
            if (HotSpotCompressedNullConstant.COMPRESSED_NULL.equals(input)) {
                masm.mov(0, asRegister(result));
            } else if (input instanceof HotSpotObjectConstant) {
                boolean compressed = ((HotSpotObjectConstant) input).isCompressed();
                if (crb.target.inlineObjects) {
                    crb.recordInlineDataInCode(input);
                    if (compressed) {
                        masm.sethi(0xDEADDEAD >>> 10, asRegister(result));
                        masm.add(asRegister(result), 0xAD & 0x3F, asRegister(result));
                    } else {
                        new SPARCMacroAssembler.Setx(0xDEADDEADDEADDEADL, asRegister(result), true).emit(masm);
                    }
                } else {
                    GraalInternalError.unimplemented();
                }
            } else if (input instanceof HotSpotMetaspaceConstant) {
                assert input.getKind() == Kind.Int || input.getKind() == Kind.Long;
                boolean compressed = input.getKind() == Kind.Int;
                boolean isImmutable = GraalOptions.ImmutableCode.getValue();
                boolean generatePIC = GraalOptions.GeneratePIC.getValue();
                crb.recordInlineDataInCode(input);
                if (compressed) {
                    if (isImmutable && generatePIC) {
                        GraalInternalError.unimplemented();
                    } else {
                        new SPARCMacroAssembler.Setx(input.asInt(), asRegister(result), true).emit(masm);
                    }
                } else {
                    if (isImmutable && generatePIC) {
                        GraalInternalError.unimplemented();
                    } else {
                        new SPARCMacroAssembler.Setx(input.asLong(), asRegister(result), true).emit(masm);
                    }
                }
            } else {
                SPARCMove.move(crb, masm, result, input, SPARCDelayedControlTransfer.DUMMY);
            }
        }

        public Value getInput() {
            return input;
        }

        public AllocatableValue getResult() {
            return result;
        }
    }

    public static final class CompressPointer extends SPARCLIRInstruction {
        public static final LIRInstructionClass<CompressPointer> TYPE = LIRInstructionClass.create(CompressPointer.class);

        private final CompressEncoding encoding;
        private final boolean nonNull;

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue input;
        @Alive({REG, ILLEGAL}) protected AllocatableValue baseRegister;

        public CompressPointer(AllocatableValue result, AllocatableValue input, AllocatableValue baseRegister, CompressEncoding encoding, boolean nonNull) {
            super(TYPE);
            this.result = result;
            this.input = input;
            this.baseRegister = baseRegister;
            this.encoding = encoding;
            this.nonNull = nonNull;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            SPARCMove.move(crb, masm, result, input, SPARCDelayedControlTransfer.DUMMY);

            Register resReg = asRegister(result);
            if (encoding.base != 0) {
                Register baseReg = asRegister(baseRegister);
                if (!nonNull) {
                    masm.cmp(resReg, baseReg);
                    masm.movcc(ConditionFlag.Equal, CC.Xcc, baseReg, resReg);
                }
                masm.sub(resReg, baseReg, resReg);
            }

            if (encoding.shift != 0) {
                masm.srlx(resReg, encoding.shift, resReg);
            }
        }
    }

    public static final class UncompressPointer extends SPARCLIRInstruction {
        public static final LIRInstructionClass<UncompressPointer> TYPE = LIRInstructionClass.create(UncompressPointer.class);

        private final CompressEncoding encoding;
        private final boolean nonNull;

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue input;
        @Alive({REG, ILLEGAL}) protected AllocatableValue baseRegister;

        public UncompressPointer(AllocatableValue result, AllocatableValue input, AllocatableValue baseRegister, CompressEncoding encoding, boolean nonNull) {
            super(TYPE);
            this.result = result;
            this.input = input;
            this.baseRegister = baseRegister;
            this.encoding = encoding;
            this.nonNull = nonNull;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            SPARCMove.move(crb, masm, result, input, SPARCDelayedControlTransfer.DUMMY);

            Register resReg = asRegister(result);
            if (encoding.shift != 0) {
                masm.sllx(resReg, 32, resReg);
                masm.srlx(resReg, 32 - encoding.shift, resReg);
            }

            if (encoding.base != 0) {
                if (nonNull) {
                    masm.add(resReg, asRegister(baseRegister), resReg);
                } else {
                    masm.cmp(resReg, resReg);

                    Label done = new Label();
                    masm.bpr(RCondition.Rc_nz, Annul.ANNUL, done, BranchPredict.PREDICT_TAKEN, resReg);
                    masm.add(asRegister(baseRegister), resReg, resReg);
                    masm.bind(done);
                }
            }
        }
    }

}
