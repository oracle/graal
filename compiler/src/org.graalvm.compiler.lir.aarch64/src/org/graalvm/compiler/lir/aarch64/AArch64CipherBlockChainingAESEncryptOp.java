/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.aarch64;

import static jdk.vm.ci.aarch64.AArch64.v0;
import static jdk.vm.ci.aarch64.AArch64.v1;
import static jdk.vm.ci.aarch64.AArch64.v17;
import static jdk.vm.ci.aarch64.AArch64.v18;
import static jdk.vm.ci.aarch64.AArch64.v19;
import static jdk.vm.ci.aarch64.AArch64.v20;
import static jdk.vm.ci.aarch64.AArch64.v21;
import static jdk.vm.ci.aarch64.AArch64.v22;
import static jdk.vm.ci.aarch64.AArch64.v23;
import static jdk.vm.ci.aarch64.AArch64.v24;
import static jdk.vm.ci.aarch64.AArch64.v25;
import static jdk.vm.ci.aarch64.AArch64.v26;
import static jdk.vm.ci.aarch64.AArch64.v27;
import static jdk.vm.ci.aarch64.AArch64.v28;
import static jdk.vm.ci.aarch64.AArch64.v29;
import static jdk.vm.ci.aarch64.AArch64.v30;
import static jdk.vm.ci.aarch64.AArch64.v31;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD1_MULTIPLE_2R;
import static org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD1_MULTIPLE_4R;
import static org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static org.graalvm.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED;
import static org.graalvm.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_SIGNED_UNSCALED;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.StubPort;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@StubPort(path      = "src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp",
          lineStart = 2636,
          lineEnd   = 2738,
          commit    = "4a300818fe7a47932c5b762ccd3b948815a31974",
          sha1      = "a42450cd58995c057fe3c5edb2a63d68a154d735")
// @formatter:on
public final class AArch64CipherBlockChainingAESEncryptOp extends AArch64LIRInstruction {

    public static final LIRInstructionClass<AArch64CipherBlockChainingAESEncryptOp> TYPE = LIRInstructionClass.create(AArch64CipherBlockChainingAESEncryptOp.class);

    private final int lengthOffset;

    @Alive({REG}) protected Value fromValue;
    @Alive({REG}) protected Value toValue;
    @Alive({REG}) protected Value keyValue;
    @Alive({REG}) protected Value rvecValue;
    @Alive({REG}) protected Value lenValue;

    @Temp({REG}) protected Value fromTempValue;
    @Temp({REG}) protected Value toTempValue;
    @Temp({REG}) protected Value keyTempValue;

    @Def({REG}) protected Value resultValue;

    @Temp protected Value[] simdTemps;

    public AArch64CipherBlockChainingAESEncryptOp(LIRGeneratorTool tool,
                    AllocatableValue fromValue,
                    AllocatableValue toValue,
                    AllocatableValue keyValue,
                    AllocatableValue rvecValue,
                    AllocatableValue lenValue,
                    AllocatableValue resultValue,
                    int lengthOffset) {
        super(TYPE);

        this.fromValue = fromValue;
        this.toValue = toValue;
        this.keyValue = keyValue;
        this.rvecValue = rvecValue;
        this.lenValue = lenValue;
        this.resultValue = resultValue;

        this.fromTempValue = tool.newVariable(fromValue.getValueKind());
        this.toTempValue = tool.newVariable(toValue.getValueKind());
        this.keyTempValue = tool.newVariable(keyValue.getValueKind());

        this.lengthOffset = lengthOffset;

        this.simdTemps = new Value[]{
                        v0.asValue(),
                        v1.asValue(),
                        v17.asValue(),
                        v18.asValue(),
                        v19.asValue(),
                        v20.asValue(),
                        v21.asValue(),
                        v22.asValue(),
                        v23.asValue(),
                        v24.asValue(),
                        v25.asValue(),
                        v26.asValue(),
                        v27.asValue(),
                        v28.asValue(),
                        v29.asValue(),
                        v30.asValue(),
                        v31.asValue(),
        };
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        GraalError.guarantee(fromValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid fromValue kind: %s", fromValue);
        GraalError.guarantee(toValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid toValue kind: %s", toValue);
        GraalError.guarantee(keyValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid keyValue kind: %s", keyValue);
        GraalError.guarantee(rvecValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid rvecValue kind: %s", rvecValue);
        GraalError.guarantee(lenValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid lenValue kind: %s", lenValue);
        GraalError.guarantee(resultValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid resultValue kind: %s", resultValue);

        try (AArch64MacroAssembler.ScratchRegister sr1 = masm.getScratchRegister();
                        AArch64MacroAssembler.ScratchRegister sr2 = masm.getScratchRegister()) {
            Label labelLoadkeys44 = new Label();
            Label labelLoadkeys52 = new Label();
            Label labelAesLoop = new Label();
            Label labelRounds44 = new Label();
            Label labelRounds52 = new Label();

            Register from = asRegister(fromTempValue);  // source array address
            Register to = asRegister(toTempValue);      // destination array address
            Register key = asRegister(keyTempValue);    // key array address
            Register rvec = asRegister(rvecValue);  // r byte array

            Register keylen = sr1.getRegister();
            Register lenReg = sr2.getRegister();

            masm.mov(64, from, asRegister(fromValue));
            masm.mov(64, to, asRegister(toValue));
            masm.mov(64, key, asRegister(keyValue));
            masm.mov(32, lenReg, asRegister(lenValue));

            masm.ldr(32, keylen, AArch64Address.createImmediateAddress(32, IMMEDIATE_SIGNED_UNSCALED, key, lengthOffset));

            masm.fldr(128, v0, AArch64Address.createBaseRegisterOnlyAddress(128, rvec));

            masm.compare(32, keylen, 52);
            masm.branchConditionally(ConditionFlag.LO, labelLoadkeys44);
            masm.branchConditionally(ConditionFlag.EQ, labelLoadkeys52);

            masm.neon.ld1MultipleVV(FullReg, ElementSize.Byte, v17, v18,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_2R, FullReg, ElementSize.Byte, key, 32));
            masm.neon.rev32VV(FullReg, ElementSize.Byte, v17, v17);
            masm.neon.rev32VV(FullReg, ElementSize.Byte, v18, v18);
            masm.bind(labelLoadkeys52);
            masm.neon.ld1MultipleVV(FullReg, ElementSize.Byte, v19, v20,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_2R, FullReg, ElementSize.Byte, key, 32));
            masm.neon.rev32VV(FullReg, ElementSize.Byte, v19, v19);
            masm.neon.rev32VV(FullReg, ElementSize.Byte, v20, v20);
            masm.bind(labelLoadkeys44);
            masm.neon.ld1MultipleVVVV(FullReg, ElementSize.Byte, v21, v22, v23, v24,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, FullReg, ElementSize.Byte, key, 64));
            masm.neon.rev32VV(FullReg, ElementSize.Byte, v21, v21);
            masm.neon.rev32VV(FullReg, ElementSize.Byte, v22, v22);
            masm.neon.rev32VV(FullReg, ElementSize.Byte, v23, v23);
            masm.neon.rev32VV(FullReg, ElementSize.Byte, v24, v24);
            masm.neon.ld1MultipleVVVV(FullReg, ElementSize.Byte, v25, v26, v27, v28,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, FullReg, ElementSize.Byte, key, 64));
            masm.neon.rev32VV(FullReg, ElementSize.Byte, v25, v25);
            masm.neon.rev32VV(FullReg, ElementSize.Byte, v26, v26);
            masm.neon.rev32VV(FullReg, ElementSize.Byte, v27, v27);
            masm.neon.rev32VV(FullReg, ElementSize.Byte, v28, v28);
            masm.neon.ld1MultipleVVV(FullReg, ElementSize.Byte, v29, v30, v31, AArch64Address.createBaseRegisterOnlyAddress(128, key));
            masm.neon.rev32VV(FullReg, ElementSize.Byte, v29, v29);
            masm.neon.rev32VV(FullReg, ElementSize.Byte, v30, v30);
            masm.neon.rev32VV(FullReg, ElementSize.Byte, v31, v31);

            masm.bind(labelAesLoop);
            masm.fldr(128, v1, AArch64Address.createImmediateAddress(128, IMMEDIATE_POST_INDEXED, from, 16));
            masm.neon.eorVVV(FullReg, v0, v0, v1);

            masm.branchConditionally(ConditionFlag.LO, labelRounds44);
            masm.branchConditionally(ConditionFlag.EQ, labelRounds52);

            masm.neon.aese(v0, v17);
            masm.neon.aesmc(v0, v0);
            masm.neon.aese(v0, v18);
            masm.neon.aesmc(v0, v0);
            masm.bind(labelRounds52);
            masm.neon.aese(v0, v19);
            masm.neon.aesmc(v0, v0);
            masm.neon.aese(v0, v20);
            masm.neon.aesmc(v0, v0);
            masm.bind(labelRounds44);
            masm.neon.aese(v0, v21);
            masm.neon.aesmc(v0, v0);
            masm.neon.aese(v0, v22);
            masm.neon.aesmc(v0, v0);
            masm.neon.aese(v0, v23);
            masm.neon.aesmc(v0, v0);
            masm.neon.aese(v0, v24);
            masm.neon.aesmc(v0, v0);
            masm.neon.aese(v0, v25);
            masm.neon.aesmc(v0, v0);
            masm.neon.aese(v0, v26);
            masm.neon.aesmc(v0, v0);
            masm.neon.aese(v0, v27);
            masm.neon.aesmc(v0, v0);
            masm.neon.aese(v0, v28);
            masm.neon.aesmc(v0, v0);
            masm.neon.aese(v0, v29);
            masm.neon.aesmc(v0, v0);
            masm.neon.aese(v0, v30);
            masm.neon.eorVVV(FullReg, v0, v0, v31);

            masm.fstr(128, v0, AArch64Address.createImmediateAddress(128, IMMEDIATE_POST_INDEXED, to, 16));

            masm.sub(32, lenReg, lenReg, 16);
            masm.cbnz(32, lenReg, labelAesLoop);

            masm.fstr(128, v0, AArch64Address.createBaseRegisterOnlyAddress(128, rvec));

            masm.mov(32, asRegister(resultValue), asRegister(lenValue));
        }
    }
}
