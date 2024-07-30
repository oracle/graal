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
package jdk.graal.compiler.lir.aarch64;

import static jdk.vm.ci.aarch64.AArch64.r10;
import static jdk.vm.ci.aarch64.AArch64.r11;
import static jdk.vm.ci.aarch64.AArch64.r12;
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
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.graal.compiler.lir.aarch64.AArch64AESEncryptOp.aesecbEncrypt;
import static jdk.graal.compiler.lir.aarch64.AArch64AESEncryptOp.aesencLoadkeys;
import static jdk.graal.compiler.lir.aarch64.AArch64AESEncryptOp.asFloatRegister;

import java.util.Arrays;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/8032d640c0d34fe507392a1d4faa4ff2005c771d/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L2961-L3241",
          sha1 = "75a3a4dabdc42e5e23bbec0cb448d09fb0d7b129")
// @formatter:on
public final class AArch64CounterModeAESCryptOp extends AArch64LIRInstruction {

    public static final LIRInstructionClass<AArch64CounterModeAESCryptOp> TYPE = LIRInstructionClass.create(AArch64CounterModeAESCryptOp.class);

    private final int lengthOffset;

    @Alive({REG}) private Value inValue;
    @Alive({REG}) private Value outValue;
    @Alive({REG}) private Value keyValue;
    @Alive({REG}) private Value counterValue;
    @Alive({REG}) private Value lenValue;
    @Alive({REG}) private Value encryptedCounterValue;
    @Alive({REG}) private Value usedPtrValue;

    @Def({REG}) protected Value resultValue;

    @Temp protected Value[] gpTemps;
    @Temp protected Value[] simdTemps;

    public AArch64CounterModeAESCryptOp(AllocatableValue inValue,
                    AllocatableValue outValue,
                    AllocatableValue keyValue,
                    AllocatableValue counterValue,
                    AllocatableValue lenValue,
                    AllocatableValue encryptedCounterValue,
                    AllocatableValue usedPtrValue,
                    AllocatableValue resultValue,
                    int lengthOffset) {
        super(TYPE);

        this.inValue = inValue;
        this.outValue = outValue;
        this.keyValue = keyValue;
        this.counterValue = counterValue;
        this.lenValue = lenValue;
        this.encryptedCounterValue = encryptedCounterValue;
        this.usedPtrValue = usedPtrValue;
        this.resultValue = resultValue;

        this.lengthOffset = lengthOffset;

        this.gpTemps = new Value[]{
                        r7.asValue(),
                        r10.asValue(),
                        r11.asValue(),
                        r12.asValue(),
        };
        this.simdTemps = Arrays.stream(AArch64.simdRegisters.toArray()).map(Register::asValue).toArray(Value[]::new);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        GraalError.guarantee(inValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid inValue kind: %s", inValue);
        GraalError.guarantee(outValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid outValue kind: %s", outValue);
        GraalError.guarantee(keyValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid keyValue kind: %s", keyValue);
        GraalError.guarantee(counterValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid counterValue kind: %s", counterValue);
        GraalError.guarantee(lenValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid lenValue kind: %s", lenValue);
        GraalError.guarantee(encryptedCounterValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid encryptedCounterValue kind: %s", encryptedCounterValue);
        GraalError.guarantee(usedPtrValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid usedPtrValue kind: %s", usedPtrValue);
        GraalError.guarantee(resultValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid resultValue kind: %s", resultValue);

        Register in = asRegister(inValue);
        Register out = asRegister(outValue);
        Register key = asRegister(keyValue);
        Register counter = asRegister(counterValue);
        Register savedLen = asRegister(lenValue);
        Register savedEncryptedCtr = asRegister(encryptedCounterValue);
        Register usedPtr = asRegister(usedPtrValue);

        Register len = r10;
        Register used = r12;
        Register offset = r7;
        Register keylen = r11;

        int blockSize = 16;
        int bulkWidth = 4;
        // NB: bulkWidth can be 4 or 8. 8 gives slightly faster
        // performance with larger data sizes, but it also means that the
        // fast path isn't used until you have at least 8 blocks, and up
        // to 127 bytes of data will be executed on the slow path. For
        // that reason, and also so as not to blow away too much icache, 4
        // blocks seems like a sensible compromise.

        // @formatter:off
        // Algorithm:
        //
        //    if (len == 0) {
        //        goto DONE;
        //    }
        //    int result = len;
        //    do {
        //        if (used >= blockSize) {
        //            if (len >= bulk_width * blockSize) {
        //                CTR_large_block();
        //                if (len == 0)
        //                    goto DONE;
        //            }
        //            for (;;) {
        //                16ByteVector v0 = counter;
        //                embeddedCipher.encryptBlock(v0, 0, encryptedCounter, 0);
        //                used = 0;
        //                if (len < blockSize)
        //                    break;    /* goto NEXT */
        //                16ByteVector v1 = load16Bytes(in, offset);
        //                v1 = v1 ^ encryptedCounter;
        //                store16Bytes(out, offset);
        //                used = blockSize;
        //                offset += blockSize;
        //                len -= blockSize;
        //                if (len == 0)
        //                    goto DONE;
        //            }
        //        }
        //      NEXT:
        //        out[outOff++] = (byte)(in[inOff++] ^ encryptedCounter[used++]);
        //        len--;
        //    } while (len != 0);
        //  DONE:
        //    return result;
        //
        // CTR_large_block()
        //    Wide bulk encryption of whole blocks.
        // @formatter:on

        Label labelSkipLargeBlock = new Label();
        Label labelDone = new Label();

        masm.ldr(32, used, AArch64Address.createBaseRegisterOnlyAddress(32, usedPtr));
        masm.cbz(32, savedLen, labelDone);

        masm.mov(32, len, savedLen);
        masm.mov(offset, 0);

        // Compute #rounds for AES based on the length of the key array
        masm.ldr(32, keylen, AArch64Address.createImmediateAddress(32, AArch64Address.AddressingMode.IMMEDIATE_SIGNED_UNSCALED, key, lengthOffset));

        aesencLoadkeys(masm, key, keylen);

        Label labelCTRLoop = new Label();
        Label labelNext = new Label();

        masm.bind(labelCTRLoop);

        masm.compare(32, used, blockSize);
        masm.branchConditionally(ConditionFlag.LO, labelNext);

        // Maybe we have a lot of data
        masm.subs(32, zr, len, bulkWidth * blockSize);
        masm.branchConditionally(ConditionFlag.LO, labelSkipLargeBlock);

        emitCTRLargeBlock(masm, bulkWidth, in, out, counter, usedPtr, len, used, offset, keylen);

        masm.bind(labelSkipLargeBlock);
        masm.cbz(32, len, labelDone);

        // Setup the counter
        masm.neon.moveVI(ASIMDSize.FullReg, ElementSize.Word, v4, 0);
        masm.neon.moveVI(ASIMDSize.FullReg, ElementSize.Word, v5, 1);
        masm.neon.insXX(ElementSize.Word, v4, 2, v5, 2);
        // v4 contains { 0, 1 }

        // 128-bit big-endian increment
        masm.fldr(128, v0, AArch64Address.createBaseRegisterOnlyAddress(128, counter));
        masm.neon.rev64VV(ASIMDSize.FullReg, ElementSize.Byte, v16, v0);
        beAdd128x64(masm, v16, v16, v4, v5);
        masm.neon.rev64VV(ASIMDSize.FullReg, ElementSize.Byte, v16, v16);
        masm.fstr(128, v16, AArch64Address.createBaseRegisterOnlyAddress(128, counter));
        // Previous counter value is in v0
        // v4 contains { 0, 1 }

        // We have fewer than bulk_width blocks of data left. Encrypt
        // them one by one until there is less than a full block
        // remaining, being careful to save both the encrypted counter
        // and the counter.
        Label labelInnerLoop = new Label();
        masm.bind(labelInnerLoop);
        // Counter to encrypt is in v0
        aesecbEncrypt(masm, Register.None, Register.None, keylen, v0, 1);
        masm.fstr(128, v0, AArch64Address.createBaseRegisterOnlyAddress(128, savedEncryptedCtr));

        // Do we have a remaining full block?

        masm.mov(used, 0);
        masm.compare(32, len, blockSize);
        masm.branchConditionally(ConditionFlag.LO, labelNext);

        // Yes, we have a full block
        masm.fldr(128, v1, AArch64Address.createRegisterOffsetAddress(128, in, offset, false));
        masm.neon.eorVVV(ASIMDSize.FullReg, v1, v1, v0);
        masm.fstr(128, v1, AArch64Address.createRegisterOffsetAddress(128, out, offset, false));
        masm.mov(used, blockSize);
        masm.add(64, offset, offset, blockSize);

        masm.sub(32, len, len, blockSize);
        masm.cbz(32, len, labelDone);

        // Increment the counter, store it back
        masm.neon.orrVVV(ASIMDSize.FullReg, v0, v16, v16);
        masm.neon.rev64VV(ASIMDSize.FullReg, ElementSize.Byte, v16, v16);
        beAdd128x64(masm, v16, v16, v4, v5);
        masm.neon.rev64VV(ASIMDSize.FullReg, ElementSize.Byte, v16, v16);
        // Save the incremented counter back
        masm.fstr(128, v16, AArch64Address.createBaseRegisterOnlyAddress(128, counter));

        masm.jmp(labelInnerLoop);

        masm.bind(labelNext);

        try (AArch64MacroAssembler.ScratchRegister sc1 = masm.getScratchRegister();
                        AArch64MacroAssembler.ScratchRegister sc2 = masm.getScratchRegister()) {
            Register rscratch1 = sc1.getRegister();
            Register rscratch2 = sc2.getRegister();

            // Encrypt a single byte, and loop.
            // We expect this to be a rare event.
            masm.ldr(8, rscratch1, AArch64Address.createRegisterOffsetAddress(8, in, offset, false));
            masm.ldr(8, rscratch2, AArch64Address.createRegisterOffsetAddress(8, savedEncryptedCtr, used, false));
            masm.eor(64, rscratch1, rscratch1, rscratch2);
            masm.str(8, rscratch1, AArch64Address.createRegisterOffsetAddress(8, out, offset, false));
            masm.add(64, offset, offset, 1);
            masm.add(64, used, used, 1);
            masm.sub(32, len, len, 1);
            masm.cbnz(32, len, labelCTRLoop);
        }

        masm.bind(labelDone);
        masm.str(32, used, AArch64Address.createBaseRegisterOnlyAddress(32, usedPtr));

        Register result = asRegister(resultValue);
        masm.mov(32, result, savedLen);
    }

    // Big-endian 128-bit + 64-bit -> 128-bit addition.
    // Inputs: 128-bits. in is preserved.
    // The least-significant 64-bit word is in the upper dword of each vector.
    // inc (the 64-bit increment) is preserved. Its lower dword must be zero.
    // Output: result
    private static void beAdd128x64(AArch64MacroAssembler masm, Register result, Register in, Register inc, Register tmp) {
        // Add inc to the least-significant dword of input
        masm.neon.addVVV(ASIMDSize.FullReg, ElementSize.DoubleWord, result, in, inc);
        // Check for result overflowing
        masm.neon.cmhiVVV(ASIMDSize.FullReg, ElementSize.DoubleWord, tmp, inc, result);
        // Swap LSD of comparison result to MSD and MSD == 0 (must be!) to LSD
        masm.neon.extVVV(ASIMDSize.FullReg, tmp, tmp, tmp, 0x08);
        // Subtract -1 from MSD if there was an overflow
        masm.neon.subVVV(ASIMDSize.FullReg, ElementSize.DoubleWord, result, result, tmp);
    }

    private static void emitCTRLargeBlock(AArch64MacroAssembler masm, int bulkWidth, Register in, Register out, Register counter,
                    Register usedPtr, Register len, Register used, Register offset, Register keylen) {
        GraalError.guarantee(bulkWidth == 4 || bulkWidth == 8, "bulk_width must be 4 or 8");

        try (AArch64MacroAssembler.ScratchRegister sc = masm.getScratchRegister()) {
            Register rscratch = sc.getRegister();

            Label labelCTRLoop = new Label();

            if (bulkWidth == 8) {
                masm.sub(64, sp, sp, 4 * 16);
                masm.neon.st1MultipleVVVV(ASIMDSize.FullReg, ElementSize.Byte, v12, v13, v14, v15, AArch64Address.createBaseRegisterOnlyAddress(128, sp));
            }
            masm.sub(64, sp, sp, 4 * 16);
            masm.neon.st1MultipleVVVV(ASIMDSize.FullReg, ElementSize.Byte, v8, v9, v10, v11, AArch64Address.createBaseRegisterOnlyAddress(128, sp));

            masm.mov(32, rscratch, len);

            masm.and(32, len, len, -16 * bulkWidth);  // 8/4 encryptions, 16 bytes per encryption
            masm.add(64, in, in, offset);
            masm.add(64, out, out, offset);

            // Keys should already be loaded into the correct registers
            // v0 contains the first counter
            masm.fldr(128, v0, AArch64Address.createBaseRegisterOnlyAddress(128, counter));
            // v16 contains byte-reversed counter
            masm.neon.rev64VV(ASIMDSize.FullReg, ElementSize.Byte, v16, v0);

            // AES/CTR loop
            masm.bind(labelCTRLoop);

            // Setup the counters
            masm.neon.moveVI(ASIMDSize.FullReg, ElementSize.Word, v8, 0);
            masm.neon.moveVI(ASIMDSize.FullReg, ElementSize.Word, v9, 1);
            masm.neon.insXX(ElementSize.Word, v8, 2, v9, 2);
            // v8 contains { 0, 1 }

            for (int i = 0; i < bulkWidth; i++) {
                masm.neon.rev64VV(ASIMDSize.FullReg, ElementSize.Byte, asFloatRegister(v0, i), v16);
                beAdd128x64(masm, v16, v16, v8, v9);
            }

            masm.neon.ld1MultipleVVVV(ASIMDSize.FullReg, ElementSize.Byte, v8, v9, v10, v11,
                            AArch64Address.createStructureImmediatePostIndexAddress(ASIMDInstruction.LD1_MULTIPLE_4R, ASIMDSize.FullReg, ElementSize.Byte, in, 4 * 16));

            // Encrypt the counters
            aesecbEncrypt(masm, Register.None, Register.None, keylen, v0, bulkWidth);

            if (bulkWidth == 8) {
                masm.neon.ld1MultipleVVVV(ASIMDSize.FullReg, ElementSize.Byte, v12, v13, v14, v15,
                                AArch64Address.createStructureImmediatePostIndexAddress(ASIMDInstruction.LD1_MULTIPLE_4R, ASIMDSize.FullReg, ElementSize.Byte, in, 4 * 16));
            }

            // XOR the encrypted counters with the inputs
            for (int i = 0; i < bulkWidth; i++) {
                masm.neon.eorVVV(ASIMDSize.FullReg, asFloatRegister(v0, i), asFloatRegister(v0, i), asFloatRegister(v8, i));
            }

            // Write the encrypted data
            masm.neon.st1MultipleVVVV(ASIMDSize.FullReg, ElementSize.Byte, v0, v1, v2, v3,
                            AArch64Address.createStructureImmediatePostIndexAddress(ASIMDInstruction.ST1_MULTIPLE_4R, ASIMDSize.FullReg, ElementSize.Byte, out, 4 * 16));
            if (bulkWidth == 8) {
                masm.neon.st1MultipleVVVV(ASIMDSize.FullReg, ElementSize.Byte, v4, v5, v6, v7,
                                AArch64Address.createStructureImmediatePostIndexAddress(ASIMDInstruction.ST1_MULTIPLE_4R, ASIMDSize.FullReg, ElementSize.Byte, out, 4 * 16));
            }

            masm.sub(32, len, len, 16 * bulkWidth);
            masm.cbnz(32, len, labelCTRLoop);

            // Save the counter back where it goes
            masm.neon.rev64VV(ASIMDSize.FullReg, ElementSize.Byte, v16, v16);
            masm.fstr(128, v16, AArch64Address.createBaseRegisterOnlyAddress(128, counter));

            masm.neon.ld1MultipleVVVV(ASIMDSize.FullReg, ElementSize.Byte, v8, v9, v10, v11,
                            AArch64Address.createStructureImmediatePostIndexAddress(ASIMDInstruction.LD1_MULTIPLE_4R, ASIMDSize.FullReg, ElementSize.Byte, sp, 64));
            if (bulkWidth == 8) {
                masm.neon.ld1MultipleVVVV(ASIMDSize.FullReg, ElementSize.Byte, v12, v13, v14, v15,
                                AArch64Address.createStructureImmediatePostIndexAddress(ASIMDInstruction.LD1_MULTIPLE_4R, ASIMDSize.FullReg, ElementSize.Byte, sp, 64));
            }

            masm.mov(32, len, rscratch);
            masm.and(32, rscratch, rscratch, -16 * bulkWidth);
            masm.add(64, offset, offset, rscratch);
            masm.sub(64, in, in, offset);
            masm.sub(64, out, out, offset);
            masm.sub(32, len, len, rscratch);
            masm.mov(used, 16);
            masm.str(32, used, AArch64Address.createBaseRegisterOnlyAddress(32, usedPtr));
        }
    }
}
