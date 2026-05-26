/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD1_MULTIPLE_4R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.ST1_MULTIPLE_4R;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PRE_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_SIGNED_UNSCALED;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.graal.compiler.lir.aarch64.AArch64AESEncryptOp.aesecbEncrypt;
import static jdk.graal.compiler.lir.aarch64.AArch64AESEncryptOp.aesencLoadkeys;
import static jdk.graal.compiler.lir.aarch64.AArch64AESEncryptOp.asFloatRegister;
import static jdk.graal.compiler.lir.aarch64.AArch64GHASHProcessBlocksOp.ghashProcessBlocksWide;
import static jdk.vm.ci.aarch64.AArch64.r0;
import static jdk.vm.ci.aarch64.AArch64.r1;
import static jdk.vm.ci.aarch64.AArch64.r11;
import static jdk.vm.ci.aarch64.AArch64.r2;
import static jdk.vm.ci.aarch64.AArch64.r3;
import static jdk.vm.ci.aarch64.AArch64.r4;
import static jdk.vm.ci.aarch64.AArch64.r5;
import static jdk.vm.ci.aarch64.AArch64.r6;
import static jdk.vm.ci.aarch64.AArch64.r7;
import static jdk.vm.ci.aarch64.AArch64.sp;
import static jdk.vm.ci.aarch64.AArch64.v0;
import static jdk.vm.ci.aarch64.AArch64.v1;
import static jdk.vm.ci.aarch64.AArch64.v10;
import static jdk.vm.ci.aarch64.AArch64.v11;
import static jdk.vm.ci.aarch64.AArch64.v12;
import static jdk.vm.ci.aarch64.AArch64.v13;
import static jdk.vm.ci.aarch64.AArch64.v14;
import static jdk.vm.ci.aarch64.AArch64.v15;
import static jdk.vm.ci.aarch64.AArch64.v16;
import static jdk.vm.ci.aarch64.AArch64.v2;
import static jdk.vm.ci.aarch64.AArch64.v3;
import static jdk.vm.ci.aarch64.AArch64.v4;
import static jdk.vm.ci.aarch64.AArch64.v5;
import static jdk.vm.ci.aarch64.AArch64.v6;
import static jdk.vm.ci.aarch64.AArch64.v7;
import static jdk.vm.ci.aarch64.AArch64.v8;
import static jdk.vm.ci.aarch64.AArch64.v9;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64.CPUFeature;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fe611a2a3386d097f636c15bd4d396a82dc695e/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L3390-L3515",
          sha1 = "b8afae639fa123ddfce7de7e051d6ca0bcc4cfad")
// @formatter:on
public final class AArch64GaloisCounterModeAESCryptOp extends AArch64LIRInstruction {

    public static final LIRInstructionClass<AArch64GaloisCounterModeAESCryptOp> TYPE = LIRInstructionClass.create(AArch64GaloisCounterModeAESCryptOp.class);

    private final int lengthOffset;

    @Use({REG}) private Value inValue;
    @Use({REG}) private Value lenValue;
    @Use({REG}) private Value ctValue;
    @Use({REG}) private Value outValue;
    @Use({REG}) private Value keyValue;
    @Use({REG}) private Value stateValue;
    @Use({REG}) private Value subkeyHtblValue;
    @Use({REG}) private Value counterValue;

    @Def({REG}) private Value resultValue;

    @Temp({REG}) private Value[] gpTemps;
    @Temp({REG}) private Value[] simdTemps;

    public AArch64GaloisCounterModeAESCryptOp(AllocatableValue inValue,
                    AllocatableValue lenValue,
                    AllocatableValue ctValue,
                    AllocatableValue outValue,
                    AllocatableValue keyValue,
                    AllocatableValue stateValue,
                    AllocatableValue subkeyHtblValue,
                    AllocatableValue counterValue,
                    AllocatableValue resultValue,
                    int lengthOffset) {
        super(TYPE);

        this.inValue = inValue;
        this.lenValue = lenValue;
        this.ctValue = ctValue;
        this.outValue = outValue;
        this.keyValue = keyValue;
        this.stateValue = stateValue;
        this.subkeyHtblValue = subkeyHtblValue;
        this.counterValue = counterValue;
        this.resultValue = resultValue;
        this.lengthOffset = lengthOffset;

        GraalError.guarantee(inValue instanceof RegisterValue inValueReg && r0.equals(inValueReg.getRegister()), "inValue should be fixed to r0, but is %s", inValue);
        GraalError.guarantee(lenValue instanceof RegisterValue lenValueReg && r1.equals(lenValueReg.getRegister()), "lenValue should be fixed to r1, but is %s", lenValue);
        GraalError.guarantee(ctValue instanceof RegisterValue ctValueReg && r2.equals(ctValueReg.getRegister()), "ctValue should be fixed to r2, but is %s", ctValue);
        GraalError.guarantee(outValue instanceof RegisterValue outValueReg && r3.equals(outValueReg.getRegister()), "outValue should be fixed to r3, but is %s", outValue);
        GraalError.guarantee(keyValue instanceof RegisterValue keyValueReg && r4.equals(keyValueReg.getRegister()), "keyValue should be fixed to r4, but is %s", keyValue);
        GraalError.guarantee(stateValue instanceof RegisterValue stateValueReg && r5.equals(stateValueReg.getRegister()), "stateValue should be fixed to r5, but is %s", stateValue);
        GraalError.guarantee(subkeyHtblValue instanceof RegisterValue subkeyHtblValueReg && r6.equals(subkeyHtblValueReg.getRegister()), "subkeyHtblValue should be fixed to r6, but is %s",
                        subkeyHtblValue);
        GraalError.guarantee(counterValue instanceof RegisterValue counterValueReg && r7.equals(counterValueReg.getRegister()), "counterValue should be fixed to r7, but is %s", counterValue);
        GraalError.guarantee(resultValue instanceof RegisterValue resultValueReg && r0.equals(resultValueReg.getRegister()), "resultValue should be fixed to r0, but is %s", resultValue);

        this.gpTemps = new Value[]{
                        r1.asValue(),
                        r2.asValue(),
                        r3.asValue(),
                        r11.asValue(),
        };
        this.simdTemps = AArch64.simdRegisters.stream().map(Register::asValue).toArray(Value[]::new);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        GraalError.guarantee(masm.supports(CPUFeature.AES) && masm.supports(CPUFeature.PMULL), "GCM AES requires AES and PMULL support");
        GraalError.guarantee(inValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid inValue kind: %s", inValue);
        GraalError.guarantee(lenValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid lenValue kind: %s", lenValue);
        GraalError.guarantee(ctValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid ctValue kind: %s", ctValue);
        GraalError.guarantee(outValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid outValue kind: %s", outValue);
        GraalError.guarantee(keyValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid keyValue kind: %s", keyValue);
        GraalError.guarantee(stateValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid stateValue kind: %s", stateValue);
        GraalError.guarantee(subkeyHtblValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid subkeyHtblValue kind: %s", subkeyHtblValue);
        GraalError.guarantee(counterValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid counterValue kind: %s", counterValue);
        GraalError.guarantee(resultValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid resultValue kind: %s", resultValue);

        // Vector AES Galois Counter Mode implementation. Parameters:
        //
        // in = c_rarg0
        // len = c_rarg1
        // ct = c_rarg2 - ciphertext that ghash will read (in for encrypt, out for decrypt)
        // out = c_rarg3
        // key = c_rarg4
        // state = c_rarg5 - GHASH.state
        // subkeyHtbl = c_rarg6 - powers of H
        // counter = c_rarg7 - 16 bytes of CTR
        // return - number of processed bytes
        Register in = asRegister(inValue);
        Register len = asRegister(lenValue);
        Register ct = asRegister(ctValue);
        Register out = asRegister(outValue);
        // and updated with the incremented counter in the end

        Register key = asRegister(keyValue);
        Register state = asRegister(stateValue);

        Register subkeyHtbl = asRegister(subkeyHtblValue);

        Register counter = asRegister(counterValue);
        Register keylen = r11;
        Register result = asRegister(resultValue);

        int wordSize = crb.target.wordSize;

        // Save state before entering routine
        masm.sub(64, sp, sp, 4 * 16);
        masm.neon.st1MultipleVVVV(ASIMDSize.FullReg, ElementSize.Byte, v12, v13, v14, v15, AArch64Address.createBaseRegisterOnlyAddress(128, sp));
        masm.sub(64, sp, sp, 4 * 16);
        masm.neon.st1MultipleVVVV(ASIMDSize.FullReg, ElementSize.Byte, v8, v9, v10, v11, AArch64Address.createBaseRegisterOnlyAddress(128, sp));

        // masm.and(len, len, -512);
        masm.and(64, len, len, -16 * 8);  // 8 encryptions, 16 bytes per encryption
        masm.str(64, len, AArch64Address.createImmediateAddress(64, IMMEDIATE_PRE_INDEXED, sp, -2 * wordSize));

        Label done = new Label();
        masm.cbz(64, len, done);

        // Compute #rounds for AES based on the length of the key array
        masm.ldr(32, keylen, AArch64Address.createImmediateAddress(32, IMMEDIATE_SIGNED_UNSCALED, key, lengthOffset));

        aesencLoadkeys(masm, key, keylen);
        masm.neon.ld1MultipleV(ASIMDSize.FullReg, ElementSize.Byte, v0, AArch64Address.createStructureNoOffsetAddress(counter)); // v0 contains the first counter
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v16, v0); // v16 contains byte-reversed counter

        // AES/CTR loop
        Label labelCTRLoop = new Label();
        masm.bind(labelCTRLoop);

        // Setup the counters
        masm.neon.moveVI(ASIMDSize.FullReg, ElementSize.Word, v8, 0);
        masm.neon.moveVI(ASIMDSize.FullReg, ElementSize.Word, v9, 1);
        masm.neon.insXX(ElementSize.Word, v8, 3, v9, 3); // v8 contains { 0, 0, 0, 1 }

        assert v0.encoding < v8.encoding : "counter register range";
        for (int i = v0.encoding; i < v8.encoding; i++) {
            Register f = AArch64.simdRegisters.get(i);
            masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, f, v16);
            masm.neon.addVVV(ASIMDSize.FullReg, ElementSize.Word, v16, v16, v8);
        }

        masm.neon.ld1MultipleVVVV(ASIMDSize.FullReg, ElementSize.Byte, v8, v9, v10, v11,
                        AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, ASIMDSize.FullReg, ElementSize.Byte, in, 4 * 16));

        // Encrypt the counters
        aesecbEncrypt(masm, Register.None, Register.None, keylen, v0, 8);

        masm.neon.ld1MultipleVVVV(ASIMDSize.FullReg, ElementSize.Byte, v12, v13, v14, v15,
                        AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, ASIMDSize.FullReg, ElementSize.Byte, in, 4 * 16));

        // XOR the encrypted counters with the inputs
        for (int i = 0; i < 8; i++) {
            Register v0Ofs = asFloatRegister(v0, i);
            Register v8Ofs = asFloatRegister(v8, i);
            masm.neon.eorVVV(ASIMDSize.FullReg, v0Ofs, v0Ofs, v8Ofs);
        }
        masm.neon.st1MultipleVVVV(ASIMDSize.FullReg, ElementSize.Byte, v0, v1, v2, v3,
                        AArch64Address.createStructureImmediatePostIndexAddress(ST1_MULTIPLE_4R, ASIMDSize.FullReg, ElementSize.Byte, out, 4 * 16));
        masm.neon.st1MultipleVVVV(ASIMDSize.FullReg, ElementSize.Byte, v4, v5, v6, v7,
                        AArch64Address.createStructureImmediatePostIndexAddress(ST1_MULTIPLE_4R, ASIMDSize.FullReg, ElementSize.Byte, out, 4 * 16));

        masm.sub(32, len, len, 16 * 8);
        masm.cbnz(32, len, labelCTRLoop);

        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v16, v16);
        masm.neon.st1MultipleV(ASIMDSize.FullReg, ElementSize.Byte, v16, AArch64Address.createStructureNoOffsetAddress(counter));

        masm.ldr(64, len, AArch64Address.createBaseRegisterOnlyAddress(64, sp));
        masm.lsr(64, len, len, 4);  // We want the count of blocks

        // GHASH/CTR loop
        ghashProcessBlocksWide(masm, state, subkeyHtbl, ct, len, 4);

        masm.bind(done);
        // Return the number of bytes processed
        masm.ldr(64, result, AArch64Address.createImmediateAddress(64, IMMEDIATE_POST_INDEXED, sp, 2 * wordSize));

        masm.neon.ld1MultipleVVVV(ASIMDSize.FullReg, ElementSize.Byte, v8, v9, v10, v11,
                        AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, ASIMDSize.FullReg, ElementSize.Byte, sp, 4 * 16));
        masm.neon.ld1MultipleVVVV(ASIMDSize.FullReg, ElementSize.Byte, v12, v13, v14, v15,
                        AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, ASIMDSize.FullReg, ElementSize.Byte, sp, 4 * 16));
    }
}
