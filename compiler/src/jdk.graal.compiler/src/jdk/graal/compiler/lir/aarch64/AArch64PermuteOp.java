/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.lir.aarch64;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;

import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;

import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * This enum encapsulates AArch64 instructions which perform permutations.
 */
public enum AArch64PermuteOp {
    TBL,
    ZIPLOW,
    ZIPHIGH,
    ZIPEVEN,
    ZIPODD,
    UNZIPEVEN,
    UNZIPODD;

    public static class ASIMDBinaryOp extends AArch64LIRInstruction {
        private static final LIRInstructionClass<ASIMDBinaryOp> TYPE = LIRInstructionClass.create(ASIMDBinaryOp.class);

        @Opcode private final AArch64PermuteOp op;
        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue a;
        @Use({REG}) protected AllocatableValue b;

        public ASIMDBinaryOp(AArch64PermuteOp op, AllocatableValue result, AllocatableValue a, AllocatableValue b) {
            super(TYPE);
            this.op = op;
            this.result = result;
            this.a = a;
            this.b = b;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            ASIMDSize size = ASIMDSize.fromVectorKind(result.getPlatformKind());
            ElementSize eSize = ElementSize.fromKind(result.getPlatformKind());
            Register resultReg = asRegister(result);
            Register aReg = asRegister(a);
            Register bReg = asRegister(b);

            switch (op) {
                case TBL:
                    masm.neon.tblVVV(size, resultReg, aReg, bReg);
                    break;
                case ZIPLOW:
                    masm.neon.zip1VVV(size, eSize, resultReg, aReg, bReg);
                    break;
                case ZIPHIGH:
                    masm.neon.zip2VVV(size, eSize, resultReg, aReg, bReg);
                    break;
                case ZIPEVEN:
                    masm.neon.trn1VVV(size, eSize, resultReg, aReg, bReg);
                    break;
                case ZIPODD:
                    masm.neon.trn2VVV(size, eSize, resultReg, aReg, bReg);
                    break;
                case UNZIPEVEN:
                    masm.neon.uzp1VVV(size, eSize, resultReg, aReg, bReg);
                    break;
                case UNZIPODD:
                    masm.neon.uzp2VVV(size, eSize, resultReg, aReg, bReg);
                    break;
                default:
                    throw GraalError.shouldNotReachHereUnexpectedValue(op); // ExcludeFromJacocoGeneratedReport
            }

        }
    }

    public static class ASIMDPermuteOp extends AArch64LIRInstruction {
        private static final LIRInstructionClass<ASIMDPermuteOp> TYPE = LIRInstructionClass.create(ASIMDPermuteOp.class);

        @Def protected AllocatableValue result;
        @Alive protected AllocatableValue source;
        @Use protected AllocatableValue indices;
        @Temp({OperandFlag.REG, OperandFlag.ILLEGAL}) protected AllocatableValue xtmp1;
        @Temp({OperandFlag.REG, OperandFlag.ILLEGAL}) protected AllocatableValue xtmp2;

        public ASIMDPermuteOp(LIRGeneratorTool tool, AllocatableValue result, AllocatableValue source, AllocatableValue indices) {
            super(TYPE);
            this.result = result;
            this.source = source;
            this.indices = indices;
            AArch64Kind eKind = ((AArch64Kind) result.getPlatformKind()).getScalar();
            this.xtmp1 = eKind == AArch64Kind.BYTE ? Value.ILLEGAL : tool.newVariable(LIRKind.value(AArch64Kind.V128_BYTE));
            this.xtmp2 = eKind == AArch64Kind.BYTE ? Value.ILLEGAL : tool.newVariable(LIRKind.value(AArch64Kind.V128_BYTE));
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            AArch64Kind vKind = (AArch64Kind) result.getPlatformKind();
            AArch64Kind eKind = vKind.getScalar();
            ASIMDSize vSize = ASIMDSize.fromVectorKind(vKind);
            Register xtmp1Reg = xtmp1.equals(Value.ILLEGAL) ? Register.None : asRegister(xtmp1);
            Register xtmp2Reg = xtmp2.equals(Value.ILLEGAL) ? Register.None : asRegister(xtmp2);
            Register currentIdxReg = asRegister(indices);
            // Since NEON only supports byte look up, we repeatedly convert a 2W-bit look up into
            // W-bit look up by transforming a 2W-bit index with value v into a pair of W-bit
            // indices v * 2, v * 2 + 1 until we reach the element width equal to Byte.SIZE
            if (eKind.getSizeInBytes() == AArch64Kind.QWORD.getSizeInBytes()) {
                masm.neon.shlVVI(vSize, ElementSize.DoubleWord, xtmp1Reg, currentIdxReg, 1);
                masm.neon.moveVV(vSize, xtmp2Reg, xtmp1Reg);
                masm.neon.orrVI(vSize, ElementSize.Word, xtmp2Reg, 1);
                masm.neon.shlVVI(vSize, ElementSize.DoubleWord, xtmp2Reg, xtmp2Reg, Integer.SIZE);
                masm.neon.orrVVV(vSize, xtmp1Reg, xtmp1Reg, xtmp2Reg);
                currentIdxReg = xtmp1Reg;
                eKind = AArch64Kind.DWORD;
            }
            if (eKind.getSizeInBytes() == AArch64Kind.DWORD.getSizeInBytes()) {
                masm.neon.shlVVI(vSize, ElementSize.Word, xtmp1Reg, currentIdxReg, 1);
                masm.neon.shlVVI(vSize, ElementSize.Word, xtmp2Reg, xtmp1Reg, Short.SIZE);
                masm.neon.orrVVV(vSize, xtmp1Reg, xtmp1Reg, xtmp2Reg);
                masm.neon.orrVI(vSize, ElementSize.Word, xtmp1Reg, 1 << Short.SIZE);
                currentIdxReg = xtmp1Reg;
                eKind = AArch64Kind.WORD;
            }
            if (eKind.getSizeInBytes() == AArch64Kind.WORD.getSizeInBytes()) {
                masm.neon.shlVVI(vSize, ElementSize.HalfWord, xtmp1Reg, currentIdxReg, 1);
                masm.neon.shlVVI(vSize, ElementSize.HalfWord, xtmp2Reg, xtmp1Reg, Byte.SIZE);
                masm.neon.orrVVV(vSize, xtmp1Reg, xtmp1Reg, xtmp2Reg);
                masm.neon.orrVI(vSize, ElementSize.HalfWord, xtmp1Reg, 1 << Byte.SIZE);
                currentIdxReg = xtmp1Reg;
            }
            masm.neon.tblVVV(vSize, asRegister(result), asRegister(source), currentIdxReg);
        }
    }
}
