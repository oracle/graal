/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.amd64;

import static jdk.vm.ci.amd64.AMD64.r11;
import static jdk.vm.ci.amd64.AMD64.xmm0;
import static jdk.vm.ci.amd64.AMD64.xmm1;
import static jdk.vm.ci.amd64.AMD64.xmm10;
import static jdk.vm.ci.amd64.AMD64.xmm11;
import static jdk.vm.ci.amd64.AMD64.xmm12;
import static jdk.vm.ci.amd64.AMD64.xmm13;
import static jdk.vm.ci.amd64.AMD64.xmm14;
import static jdk.vm.ci.amd64.AMD64.xmm15;
import static jdk.vm.ci.amd64.AMD64.xmm2;
import static jdk.vm.ci.amd64.AMD64.xmm3;
import static jdk.vm.ci.amd64.AMD64.xmm4;
import static jdk.vm.ci.amd64.AMD64.xmm5;
import static jdk.vm.ci.amd64.AMD64.xmm6;
import static jdk.vm.ci.amd64.AMD64.xmm7;
import static jdk.vm.ci.amd64.AMD64.xmm8;
import static jdk.vm.ci.amd64.AMD64.xmm9;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.graal.compiler.lir.amd64.AMD64AESEncryptOp.AES_BLOCK_SIZE;
import static jdk.graal.compiler.lir.amd64.AMD64AESEncryptOp.asXMMRegister;
import static jdk.graal.compiler.lir.amd64.AMD64AESEncryptOp.keyShuffleMask;
import static jdk.graal.compiler.lir.amd64.AMD64AESEncryptOp.loadKey;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/4994bd594299e91e804438692e068b1c5dd5cc02/src/hotspot/cpu/x86/stubGenerator_x86_64_aes.cpp#L1253-L1400",
          sha1 = "84d9fe0404167447e2d6b3d0cf9a07f752c474d5")
// @formatter:on
public final class AMD64CipherBlockChainingAESEncryptOp extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64CipherBlockChainingAESEncryptOp> TYPE = LIRInstructionClass.create(AMD64CipherBlockChainingAESEncryptOp.class);

    private final int lengthOffset;

    @Alive({OperandFlag.REG}) protected Value fromValue;
    @Alive({OperandFlag.REG}) protected Value toValue;
    @Alive({OperandFlag.REG}) protected Value keyValue;
    @Alive({OperandFlag.REG}) protected Value rvecValue;
    @Alive({OperandFlag.REG}) protected Value lenValue;

    @Def({OperandFlag.REG}) protected Value resultValue;

    @Temp protected Value[] temps;

    public AMD64CipherBlockChainingAESEncryptOp(AllocatableValue fromValue,
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

        this.lengthOffset = lengthOffset;

        temps = new Value[]{
                        r11.asValue(),
                        xmm0.asValue(),
                        xmm1.asValue(),
                        xmm2.asValue(),
                        xmm3.asValue(),
                        xmm4.asValue(),
                        xmm5.asValue(),
                        xmm6.asValue(),
                        xmm7.asValue(),
                        xmm8.asValue(),
                        xmm9.asValue(),
                        xmm10.asValue(),
                        xmm11.asValue(),
                        xmm12.asValue(),
                        xmm13.asValue(),
                        xmm14.asValue(),
                        xmm15.asValue(),
        };
    }

    private static final int XMM_REG_NUM_KEY_FIRST = 2;

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        GraalError.guarantee(fromValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid fromValue kind: %s", fromValue);
        GraalError.guarantee(toValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid toValue kind: %s", toValue);
        GraalError.guarantee(keyValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid keyValue kind: %s", keyValue);
        GraalError.guarantee(rvecValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid rvecValue kind: %s", rvecValue);
        GraalError.guarantee(lenValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid lenValue kind: %s", lenValue);
        GraalError.guarantee(resultValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid resultValue kind: %s", resultValue);

        Register from = asRegister(fromValue);  // source array address
        Register to = asRegister(toValue);      // destination array address
        Register key = asRegister(keyValue);    // key array address
        Register rvec = asRegister(rvecValue);  // r byte array

        Register lenReg = r11;
        // keep lenValue alive
        masm.movq(lenReg, asRegister(lenValue));

        // use resultValue as temp register
        Register pos = asRegister(resultValue);

        // xmm register assignments for the loops below
        Register xmmResult = xmm0;
        Register xmmTemp = xmm1;
        // keys 0-10 preloaded into xmm2-xmm12
        Register xmmKey0 = asXMMRegister(XMM_REG_NUM_KEY_FIRST);
        Register xmmKey10 = asXMMRegister(XMM_REG_NUM_KEY_FIRST + 10);
        Register xmmKey11 = asXMMRegister(XMM_REG_NUM_KEY_FIRST + 11);
        Register xmmKey12 = asXMMRegister(XMM_REG_NUM_KEY_FIRST + 12);
        Register xmmKey13 = asXMMRegister(XMM_REG_NUM_KEY_FIRST + 13);

        Label labelExit = new Label();
        Label labelKey192or256 = new Label();
        Label labelKey256 = new Label();
        Label labelLoopTop128 = new Label();
        Label labelLoopTop192 = new Label();
        Label labelLoopTop256 = new Label();

        Register xmmKeyShufMask = xmmTemp; // used temporarily to swap key bytes up front
        masm.movdqu(xmmKeyShufMask, recordExternalAddress(crb, keyShuffleMask));
        // load up xmm regs xmm2 thru xmm12 with key 0x00 - 0xa0
        for (int rnum = XMM_REG_NUM_KEY_FIRST, offset = 0x00; rnum <= XMM_REG_NUM_KEY_FIRST + 10; rnum++) {
            loadKey(masm, asXMMRegister(rnum), key, offset, xmmKeyShufMask);
            offset += 0x10;
        }
        // initialize xmmResult with r vec
        masm.movdqu(xmmResult, new AMD64Address(rvec, 0x00));

        // now split to different paths depending on the keylen (len in ints of AESCrypt.KLE array
        // (52=192, or 60=256))
        masm.movl(pos, new AMD64Address(key, lengthOffset));
        masm.cmplAndJcc(pos, 44, ConditionFlag.NotEqual, labelKey192or256, false);

        // 128 bit code follows here
        masm.movq(pos, 0);
        masm.align(preferredLoopAlignment(crb));

        masm.bind(labelLoopTop128);
        // get next 16 bytes of input
        masm.movdqu(xmmTemp, new AMD64Address(from, pos, Stride.S1, 0));
        masm.pxor(xmmResult, xmmTemp); // xor with the current r vector
        masm.pxor(xmmResult, xmmKey0); // do the aes rounds
        for (int rnum = XMM_REG_NUM_KEY_FIRST + 1; rnum <= XMM_REG_NUM_KEY_FIRST + 9; rnum++) {
            masm.aesenc(xmmResult, asXMMRegister(rnum));
        }
        masm.aesenclast(xmmResult, xmmKey10);
        // store into the next 16 bytes of output
        masm.movdqu(new AMD64Address(to, pos, Stride.S1, 0), xmmResult);
        // no need to store r to memory until we exit
        masm.addq(pos, AES_BLOCK_SIZE);
        masm.subqAndJcc(lenReg, AES_BLOCK_SIZE, ConditionFlag.NotEqual, labelLoopTop128, false);
        masm.jmp(labelExit);

        masm.bind(labelKey192or256);
        // here pos = len in ints of AESCrypt.KLE array (52=192, or 60=256)
        loadKey(masm, xmmKey11, key, 0xb0, xmmKeyShufMask);
        loadKey(masm, xmmKey12, key, 0xc0, xmmKeyShufMask);
        masm.cmplAndJcc(pos, 52, ConditionFlag.NotEqual, labelKey256, false);

        // 192-bit code follows here (could be changed to use more xmm registers)
        masm.movq(pos, 0);
        masm.align(preferredLoopAlignment(crb));

        masm.bind(labelLoopTop192);
        // get next 16 bytes of input
        masm.movdqu(xmmTemp, new AMD64Address(from, pos, Stride.S1, 0));
        masm.pxor(xmmResult, xmmTemp); // xor with the current r vector
        masm.pxor(xmmResult, xmmKey0); // do the aes rounds
        for (int rnum = XMM_REG_NUM_KEY_FIRST + 1; rnum <= XMM_REG_NUM_KEY_FIRST + 11; rnum++) {
            masm.aesenc(xmmResult, asXMMRegister(rnum));
        }
        masm.aesenclast(xmmResult, xmmKey12);
        // store into the next 16 bytes of output
        masm.movdqu(new AMD64Address(to, pos, Stride.S1, 0), xmmResult);
        // no need to store r to memory until we exit
        masm.addq(pos, AES_BLOCK_SIZE);
        masm.subqAndJcc(lenReg, AES_BLOCK_SIZE, ConditionFlag.NotEqual, labelLoopTop192, false);
        masm.jmp(labelExit);

        masm.bind(labelKey256);
        // 256-bit code follows here (could be changed to use more xmm registers)
        loadKey(masm, xmmKey13, key, 0xd0, xmmKeyShufMask);
        masm.movq(pos, 0);
        masm.align(preferredLoopAlignment(crb));

        masm.bind(labelLoopTop256);
        // get next 16 bytes of input
        masm.movdqu(xmmTemp, new AMD64Address(from, pos, Stride.S1, 0));
        masm.pxor(xmmResult, xmmTemp); // xor with the current r vector
        masm.pxor(xmmResult, xmmKey0); // do the aes rounds
        for (int rnum = XMM_REG_NUM_KEY_FIRST + 1; rnum <= XMM_REG_NUM_KEY_FIRST + 13; rnum++) {
            masm.aesenc(xmmResult, asXMMRegister(rnum));
        }
        loadKey(masm, xmmTemp, key, 0xe0, crb);
        masm.aesenclast(xmmResult, xmmTemp);
        // store into the next 16 bytes of output
        masm.movdqu(new AMD64Address(to, pos, Stride.S1, 0), xmmResult);
        // no need to store r to memory until we exit
        masm.addq(pos, AES_BLOCK_SIZE);
        masm.subqAndJcc(lenReg, AES_BLOCK_SIZE, ConditionFlag.NotEqual, labelLoopTop256, false);

        masm.bind(labelExit);
        // final value of r stored in rvec of CipherBlockChaining object
        masm.movdqu(new AMD64Address(rvec, 0), xmmResult);
        masm.movl(asRegister(resultValue), asRegister(lenValue));
    }
}
