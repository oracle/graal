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
import static jdk.vm.ci.aarch64.AArch64.v2;
import static jdk.vm.ci.aarch64.AArch64.v3;
import static jdk.vm.ci.aarch64.AArch64.v4;
import static jdk.vm.ci.aarch64.AArch64.v5;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED;
import static org.graalvm.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_SIGNED_UNSCALED;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.SyncPort;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/12358e6c94bc96e618efc3ec5299a2cfe1b4669d/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L2702-L2733",
          sha1 = "69b7e01dbc601afd660d5dcef88917a43613e00c")
@SyncPort(from = "https://github.com/openjdk/jdk/blob/12358e6c94bc96e618efc3ec5299a2cfe1b4669d/src/hotspot/cpu/aarch64/macroAssembler_aarch64_aes.cpp#L34-L110",
          sha1 = "4916141cba98c26e4d98edb457161f88a8c66ffa")
// @formatter:on
public final class AArch64AESDecryptOp extends AArch64LIRInstruction {

    public static final LIRInstructionClass<AArch64AESDecryptOp> TYPE = LIRInstructionClass.create(AArch64AESDecryptOp.class);

    private final int lengthOffset;

    @Alive({REG}) private Value fromValue;
    @Alive({REG}) private Value toValue;
    @Alive({REG}) private Value keyValue;

    @Temp({REG}) private Value[] temps;

    public AArch64AESDecryptOp(Value fromValue, Value toValue, Value keyValue, int lengthOffset) {
        super(TYPE);
        this.fromValue = fromValue;
        this.toValue = toValue;
        this.keyValue = keyValue;
        this.lengthOffset = lengthOffset;
        this.temps = new Value[]{
                        v0.asValue(),
                        v1.asValue(),
                        v2.asValue(),
                        v3.asValue(),
                        v4.asValue(),
                        v5.asValue(),
        };
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        GraalError.guarantee(fromValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid fromValue kind: %s", fromValue);
        GraalError.guarantee(toValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid toValue kind: %s", toValue);
        GraalError.guarantee(keyValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid keyValue kind: %s", keyValue);

        Register from = asRegister(fromValue); // source array address
        Register to = asRegister(toValue);     // destination array address
        Register key = asRegister(keyValue);   // key array address

        try (ScratchRegister sr = masm.getScratchRegister()) {
            Register keylen = sr.getRegister();
            masm.ldr(32, keylen, AArch64Address.createImmediateAddress(32, IMMEDIATE_SIGNED_UNSCALED, key, lengthOffset));
            aesecbDecrypt(masm, from, to, key, keylen);
        }
    }

    private static void aesecbDecrypt(AArch64MacroAssembler masm, Register from, Register to, Register key, Register keylen) {
        Label labelDoLast = new Label();

        masm.fldr(128, v0, AArch64Address.createBaseRegisterOnlyAddress(128, from));

        masm.fldr(128, v5, AArch64Address.createImmediateAddress(128, IMMEDIATE_POST_INDEXED, key, 16));
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v5, v5);

        AArch64Address ld1Addr = AArch64Address.createStructureImmediatePostIndexAddress(ASIMDInstruction.LD1_MULTIPLE_4R, ASIMDSize.FullReg, ElementSize.Byte, key, 64);
        masm.neon.ld1MultipleVVVV(ASIMDSize.FullReg, ElementSize.Byte, v1, v2, v3, v4, ld1Addr);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v1, v1);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v2, v2);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v3, v3);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v4, v4);
        masm.neon.aesd(v0, v1);
        masm.neon.aesimc(v0, v0);
        masm.neon.aesd(v0, v2);
        masm.neon.aesimc(v0, v0);
        masm.neon.aesd(v0, v3);
        masm.neon.aesimc(v0, v0);
        masm.neon.aesd(v0, v4);
        masm.neon.aesimc(v0, v0);

        ld1Addr = AArch64Address.createStructureImmediatePostIndexAddress(ASIMDInstruction.LD1_MULTIPLE_4R, ASIMDSize.FullReg, ElementSize.Byte, key, 64);
        masm.neon.ld1MultipleVVVV(ASIMDSize.FullReg, ElementSize.Byte, v1, v2, v3, v4, ld1Addr);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v1, v1);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v2, v2);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v3, v3);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v4, v4);
        masm.neon.aesd(v0, v1);
        masm.neon.aesimc(v0, v0);
        masm.neon.aesd(v0, v2);
        masm.neon.aesimc(v0, v0);
        masm.neon.aesd(v0, v3);
        masm.neon.aesimc(v0, v0);
        masm.neon.aesd(v0, v4);
        masm.neon.aesimc(v0, v0);

        ld1Addr = AArch64Address.createStructureImmediatePostIndexAddress(ASIMDInstruction.LD1_MULTIPLE_2R, ASIMDSize.FullReg, ElementSize.Byte, key, 32);
        masm.neon.ld1MultipleVV(ASIMDSize.FullReg, ElementSize.Byte, v1, v2, ld1Addr);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v1, v1);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v2, v2);

        masm.compare(32, keylen, 44);
        masm.branchConditionally(ConditionFlag.EQ, labelDoLast);

        masm.neon.aesd(v0, v1);
        masm.neon.aesimc(v0, v0);
        masm.neon.aesd(v0, v2);
        masm.neon.aesimc(v0, v0);

        ld1Addr = AArch64Address.createStructureImmediatePostIndexAddress(ASIMDInstruction.LD1_MULTIPLE_2R, ASIMDSize.FullReg, ElementSize.Byte, key, 32);
        masm.neon.ld1MultipleVV(ASIMDSize.FullReg, ElementSize.Byte, v1, v2, ld1Addr);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v1, v1);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v2, v2);

        masm.compare(32, keylen, 52);
        masm.branchConditionally(ConditionFlag.EQ, labelDoLast);

        masm.neon.aesd(v0, v1);
        masm.neon.aesimc(v0, v0);
        masm.neon.aesd(v0, v2);
        masm.neon.aesimc(v0, v0);

        ld1Addr = AArch64Address.createStructureImmediatePostIndexAddress(ASIMDInstruction.LD1_MULTIPLE_2R, ASIMDSize.FullReg, ElementSize.Byte, key, 32);
        masm.neon.ld1MultipleVV(ASIMDSize.FullReg, ElementSize.Byte, v1, v2, ld1Addr);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v1, v1);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v2, v2);

        masm.bind(labelDoLast);

        masm.neon.aesd(v0, v1);
        masm.neon.aesimc(v0, v0);
        masm.neon.aesd(v0, v2);

        masm.neon.eorVVV(ASIMDSize.FullReg, v0, v0, v5);

        masm.fstr(128, v0, AArch64Address.createBaseRegisterOnlyAddress(128, to));

        // Preserve the address of the start of the key
        masm.sub(64, key, key, keylen, AArch64Assembler.ShiftType.LSL, CodeUtil.log2(JavaKind.Int.getByteCount()));
    }
}
