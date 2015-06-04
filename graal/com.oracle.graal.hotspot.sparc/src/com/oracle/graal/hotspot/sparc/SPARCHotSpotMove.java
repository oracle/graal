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

import com.oracle.jvmci.code.StackSlot;
import com.oracle.jvmci.code.Register;
import com.oracle.jvmci.meta.JavaConstant;
import com.oracle.jvmci.meta.Kind;
import com.oracle.jvmci.meta.Value;
import com.oracle.jvmci.meta.AllocatableValue;
import com.oracle.jvmci.sparc.*;

import static com.oracle.jvmci.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.asm.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCAssembler.*;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.MoveOp;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.sparc.*;
import com.oracle.jvmci.common.*;
import com.oracle.jvmci.hotspot.*;
import com.oracle.jvmci.hotspot.HotSpotVMConfig.CompressEncoding;

public class SPARCHotSpotMove {

    public static final class HotSpotLoadConstantOp extends SPARCLIRInstruction implements MoveOp {
        public static final LIRInstructionClass<HotSpotLoadConstantOp> TYPE = LIRInstructionClass.create(HotSpotLoadConstantOp.class);

        @Def({REG, STACK}) private AllocatableValue result;
        private final JavaConstant input;

        public HotSpotLoadConstantOp(AllocatableValue result, JavaConstant input) {
            super(TYPE);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            if (isStackSlot(result)) {
                StackSlot ss = asStackSlot(result);
                try (ScratchRegister s1 = masm.getScratchRegister()) {
                    Register sr1 = s1.getRegister();
                    loadToRegister(crb, masm, sr1.asValue(), input);
                    try (ScratchRegister s2 = masm.getScratchRegister()) {
                        Register sr2 = s2.getRegister();
                        int stackBias = HotSpotGraalRuntime.runtime().getConfig().stackBias;
                        int offset = crb.frameMap.offsetForStackSlot(ss);
                        new SPARCMacroAssembler.Setx(offset + stackBias, sr2).emit(masm);
                        SPARCAddress addr = new SPARCAddress(SPARC.sp, sr2);
                        Kind resultKind = (Kind) result.getPlatformKind();
                        switch (resultKind) {
                            case Int:
                                masm.stw(sr1, addr);
                                break;
                            case Long:
                            case Object:
                                masm.stx(sr1, addr);
                                break;
                            default:
                                throw JVMCIError.shouldNotReachHere();
                        }
                    }
                }
            } else {
                loadToRegister(crb, masm, result, input);
            }
        }

        private static void loadToRegister(CompilationResultBuilder crb, SPARCMacroAssembler masm, AllocatableValue dest, JavaConstant constant) {
            assert isRegister(dest);
            if (HotSpotCompressedNullConstant.COMPRESSED_NULL.equals(constant)) {
                masm.mov(0, asRegister(dest));
            } else if (constant instanceof HotSpotObjectConstant) {
                boolean compressed = ((HotSpotObjectConstant) constant).isCompressed();
                if (crb.target.inlineObjects) {
                    crb.recordInlineDataInCode(constant);
                    if (compressed) {
                        masm.sethi(0xDEADDEAD >>> 10, asRegister(dest));
                        masm.add(asRegister(dest), 0xAD & 0x3F, asRegister(dest));
                    } else {
                        new SPARCMacroAssembler.Setx(0xDEADDEADDEADDEADL, asRegister(dest), true).emit(masm);
                    }
                } else {
                    JVMCIError.unimplemented();
                }
            } else if (constant instanceof HotSpotMetaspaceConstant) {
                assert constant.getKind() == Kind.Int || constant.getKind() == Kind.Long;
                boolean compressed = constant.getKind() == Kind.Int;
                boolean isImmutable = GraalOptions.ImmutableCode.getValue();
                boolean generatePIC = GraalOptions.GeneratePIC.getValue();
                crb.recordInlineDataInCode(constant);
                if (compressed) {
                    if (isImmutable && generatePIC) {
                        JVMCIError.unimplemented();
                    } else {
                        new SPARCMacroAssembler.Setx(constant.asInt(), asRegister(dest), true).emit(masm);
                    }
                } else {
                    if (isImmutable && generatePIC) {
                        JVMCIError.unimplemented();
                    } else {
                        new SPARCMacroAssembler.Setx(constant.asLong(), asRegister(dest), true).emit(masm);
                    }
                }
            } else {
                SPARCMove.move(crb, masm, dest, constant, SPARCDelayedControlTransfer.DUMMY);
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
                masm.sll(resReg, encoding.shift, resReg);
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
