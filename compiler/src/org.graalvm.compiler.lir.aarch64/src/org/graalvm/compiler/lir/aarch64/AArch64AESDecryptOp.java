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

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED;
import static org.graalvm.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_SIGNED_UNSCALED;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.StubPort;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

// @formatter:off
@StubPort(path      = "src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp",
          lineStart = 2653,
          lineEnd   = 2753,
          commit    = "a97715755d01b88ad9e4cf32f10ca5a3f2fda898",
          sha1      = "1f7de04ab4a673b5406fd7ca747702decc4b7d10")
// @formatter:on
public final class AArch64AESDecryptOp extends AArch64LIRInstruction {

    public static final LIRInstructionClass<AArch64AESDecryptOp> TYPE = LIRInstructionClass.create(AArch64AESDecryptOp.class);

    private final int lengthOffset;

    @Alive({REG}) private Value fromValue;
    @Alive({REG}) private Value toValue;
    @Use({REG}) private Value originalKeyValue;

    @Temp({REG}) private Value keyValue;
    @Temp({REG}) private Value[] temps;

    public AArch64AESDecryptOp(LIRGeneratorTool tool, Value fromValue, Value toValue, Value originalKeyValue, int lengthOffset) {
        super(TYPE);
        this.fromValue = fromValue;
        this.toValue = toValue;
        this.originalKeyValue = originalKeyValue;
        this.keyValue = tool.newVariable(originalKeyValue.getValueKind());
        this.lengthOffset = lengthOffset;
        this.temps = new Value[]{AArch64.v0.asValue(), AArch64.v1.asValue(), AArch64.v2.asValue(), AArch64.v3.asValue(), AArch64.v4.asValue(), AArch64.v5.asValue()};
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Label labelDoLast = new Label();

        Register from = asRegister(fromValue); // source array address
        Register to = asRegister(toValue);     // destination array address
        Register originalKey = asRegister(originalKeyValue);
        Register key = asRegister(keyValue);   // key array address

        if (!originalKey.equals(key)) {
            masm.mov(originalKeyValue.getPlatformKind().getSizeInBytes() * Byte.SIZE, key, originalKey);
        }

        try (ScratchRegister sr = masm.getScratchRegister()) {
            Register keylen = sr.getRegister();
            masm.ldr(32, keylen, AArch64Address.createImmediateAddress(32, IMMEDIATE_SIGNED_UNSCALED, key, lengthOffset));

            masm.fldr(128, AArch64.v0, AArch64Address.createBaseRegisterOnlyAddress(128, from));
            masm.fldr(128, AArch64.v5, AArch64Address.createImmediateAddress(128, IMMEDIATE_POST_INDEXED, key, 16));
            masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, AArch64.v5, AArch64.v5);

            AArch64Address ld1Addr = AArch64Address.createStructureImmediatePostIndexAddress(ASIMDInstruction.LD1_MULTIPLE_4R, ASIMDSize.FullReg, ElementSize.Byte, key, 64);
            masm.neon.ld1MultipleVVVV(ASIMDSize.FullReg, ElementSize.Byte, AArch64.v1, AArch64.v2, AArch64.v3, AArch64.v4, ld1Addr);
            masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, AArch64.v1, AArch64.v1);
            masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, AArch64.v2, AArch64.v2);
            masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, AArch64.v3, AArch64.v3);
            masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, AArch64.v4, AArch64.v4);
            masm.neon.aesd(AArch64.v0, AArch64.v1);
            masm.neon.aesimc(AArch64.v0, AArch64.v0);
            masm.neon.aesd(AArch64.v0, AArch64.v2);
            masm.neon.aesimc(AArch64.v0, AArch64.v0);
            masm.neon.aesd(AArch64.v0, AArch64.v3);
            masm.neon.aesimc(AArch64.v0, AArch64.v0);
            masm.neon.aesd(AArch64.v0, AArch64.v4);
            masm.neon.aesimc(AArch64.v0, AArch64.v0);

            ld1Addr = AArch64Address.createStructureImmediatePostIndexAddress(ASIMDInstruction.LD1_MULTIPLE_4R, ASIMDSize.FullReg, ElementSize.Byte, key, 64);
            masm.neon.ld1MultipleVVVV(ASIMDSize.FullReg, ElementSize.Byte, AArch64.v1, AArch64.v2, AArch64.v3, AArch64.v4, ld1Addr);
            masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, AArch64.v1, AArch64.v1);
            masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, AArch64.v2, AArch64.v2);
            masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, AArch64.v3, AArch64.v3);
            masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, AArch64.v4, AArch64.v4);
            masm.neon.aesd(AArch64.v0, AArch64.v1);
            masm.neon.aesimc(AArch64.v0, AArch64.v0);
            masm.neon.aesd(AArch64.v0, AArch64.v2);
            masm.neon.aesimc(AArch64.v0, AArch64.v0);
            masm.neon.aesd(AArch64.v0, AArch64.v3);
            masm.neon.aesimc(AArch64.v0, AArch64.v0);
            masm.neon.aesd(AArch64.v0, AArch64.v4);
            masm.neon.aesimc(AArch64.v0, AArch64.v0);

            ld1Addr = AArch64Address.createStructureImmediatePostIndexAddress(ASIMDInstruction.LD1_MULTIPLE_2R, ASIMDSize.FullReg, ElementSize.Byte, key, 32);
            masm.neon.ld1MultipleVV(ASIMDSize.FullReg, ElementSize.Byte, AArch64.v1, AArch64.v2, ld1Addr);
            masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, AArch64.v1, AArch64.v1);
            masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, AArch64.v2, AArch64.v2);

            masm.compare(32, keylen, 44);
            masm.branchConditionally(ConditionFlag.EQ, labelDoLast);

            masm.neon.aesd(AArch64.v0, AArch64.v1);
            masm.neon.aesimc(AArch64.v0, AArch64.v0);
            masm.neon.aesd(AArch64.v0, AArch64.v2);
            masm.neon.aesimc(AArch64.v0, AArch64.v0);

            ld1Addr = AArch64Address.createStructureImmediatePostIndexAddress(ASIMDInstruction.LD1_MULTIPLE_2R, ASIMDSize.FullReg, ElementSize.Byte, key, 32);
            masm.neon.ld1MultipleVV(ASIMDSize.FullReg, ElementSize.Byte, AArch64.v1, AArch64.v2, ld1Addr);
            masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, AArch64.v1, AArch64.v1);
            masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, AArch64.v2, AArch64.v2);

            masm.compare(32, keylen, 52);
            masm.branchConditionally(ConditionFlag.EQ, labelDoLast);

            masm.neon.aesd(AArch64.v0, AArch64.v1);
            masm.neon.aesimc(AArch64.v0, AArch64.v0);
            masm.neon.aesd(AArch64.v0, AArch64.v2);
            masm.neon.aesimc(AArch64.v0, AArch64.v0);

            ld1Addr = AArch64Address.createStructureImmediatePostIndexAddress(ASIMDInstruction.LD1_MULTIPLE_2R, ASIMDSize.FullReg, ElementSize.Byte, key, 32);
            masm.neon.ld1MultipleVV(ASIMDSize.FullReg, ElementSize.Byte, AArch64.v1, AArch64.v2, ld1Addr);
            masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, AArch64.v1, AArch64.v1);
            masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, AArch64.v2, AArch64.v2);

            masm.bind(labelDoLast);

            masm.neon.aesd(AArch64.v0, AArch64.v1);
            masm.neon.aesimc(AArch64.v0, AArch64.v0);
            masm.neon.aesd(AArch64.v0, AArch64.v2);

            masm.neon.eorVVV(ASIMDSize.FullReg, AArch64.v0, AArch64.v0, AArch64.v5);

            masm.fstr(128, AArch64.v0, AArch64Address.createBaseRegisterOnlyAddress(128, to));
        }
    }
}
