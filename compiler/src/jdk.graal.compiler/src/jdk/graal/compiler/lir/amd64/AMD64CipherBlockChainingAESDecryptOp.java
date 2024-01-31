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
package jdk.graal.compiler.lir.amd64;

import static jdk.vm.ci.amd64.AMD64.r11;
import static jdk.vm.ci.amd64.AMD64.rbx;
import static jdk.vm.ci.amd64.AMD64.rsp;
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
import static jdk.graal.compiler.lir.amd64.AMD64CounterModeAESCryptOp.newLabels;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;

import java.util.function.BiConsumer;

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
@SyncPort(from = "https://github.com/openjdk/jdk/blob/ce8399fd6071766114f5f201b6e44a7abdba9f5a/src/hotspot/cpu/x86/stubGenerator_x86_64_aes.cpp#L1363-L1619",
          sha1 = "e3481678a0bdb4d66c9b3641ba44a3559979aff5")
// @formatter:on
public final class AMD64CipherBlockChainingAESDecryptOp extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64CipherBlockChainingAESDecryptOp> TYPE = LIRInstructionClass.create(AMD64CipherBlockChainingAESDecryptOp.class);

    private final int lengthOffset;

    @Alive({OperandFlag.REG}) protected Value fromValue;
    @Alive({OperandFlag.REG}) protected Value toValue;
    @Alive({OperandFlag.REG}) protected Value keyValue;
    @Alive({OperandFlag.REG}) protected Value rvecValue;
    @Alive({OperandFlag.REG}) protected Value lenValue;

    @Def({OperandFlag.REG}) protected Value resultValue;

    @Temp protected Value[] temps;

    public AMD64CipherBlockChainingAESDecryptOp(AllocatableValue fromValue,
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
                        rbx.asValue(),
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

    private static final int XMM_REG_NUM_KEY_FIRST = 5;
    private static final int XMM_REG_NUM_KEY_LAST = 15;

    private static final int PARALLEL_FACTOR = 4;
    // aes rounds for key128, key192, key256
    private static final int[] ROUNDS = new int[]{10, 12, 14};

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

        Label labelExit = new Label();
        Label[] labelSingleBlockLoopTopHead = newLabels(3);  // 128, 192, 256
        Label[] labelSingleBlockLoopTopHead2 = newLabels(3); // 128, 192, 256
        Label[] labelSingleBlockLoopTop = newLabels(3);      // 128, 192, 256
        Label[] labelMultiBlockLoopTopHead = newLabels(3);   // 128, 192, 256
        Label[] labelMultiBlockLoopTop = newLabels(3);       // 128, 192, 256

        // keys 0-10 preloaded into xmm5-xmm15
        Register xmmKeyFirst = asXMMRegister(XMM_REG_NUM_KEY_FIRST);
        Register xmmKeyLast = asXMMRegister(XMM_REG_NUM_KEY_LAST);

        int wordSize = crb.target.wordSize;

        // the java expanded key ordering is rotated one position from what we want
        // so we start from 0x10 here and hit 0x00 last
        Register xmmKeyShufMask = xmm1;  // used temporarily to swap key bytes up front
        masm.movdqu(xmmKeyShufMask, recordExternalAddress(crb, keyShuffleMask));
        // load up xmm regs 5 thru 15 with key 0x10 - 0xa0 - 0x00
        for (int rnum = XMM_REG_NUM_KEY_FIRST, offset = 0x10; rnum < XMM_REG_NUM_KEY_LAST; rnum++) {
            loadKey(masm, asXMMRegister(rnum), key, offset, xmmKeyShufMask);
            offset += 0x10;
        }
        loadKey(masm, xmmKeyLast, key, 0x00, xmmKeyShufMask);

        Register xmmPrevBlockCipher = xmm1;  // holds cipher of previous block

        // registers holding the four results in the parallelized loop
        Register xmmResult0 = xmm0;
        Register xmmResult1 = xmm2;
        Register xmmResult2 = xmm3;
        Register xmmResult3 = xmm4;
        // initialize with initial rvec
        masm.movdqu(xmmPrevBlockCipher, new AMD64Address(rvec, 0x00));

        masm.xorq(pos, pos);

        // now split to different paths depending on the keylen
        // (len in ints of AESCrypt.KLE array (52=192, or 60=256))
        masm.movl(rbx, new AMD64Address(key, lengthOffset));
        masm.cmplAndJcc(rbx, 52, ConditionFlag.Equal, labelMultiBlockLoopTopHead[1], false);
        masm.cmplAndJcc(rbx, 60, ConditionFlag.Equal, labelMultiBlockLoopTopHead[2], false);

        for (int k = 0; k < 3; ++k) {
            masm.bind(labelMultiBlockLoopTopHead[k]);
            if (k != 0) {
                // see if at least 4 blocks left
                masm.cmpqAndJcc(lenReg, PARALLEL_FACTOR * AES_BLOCK_SIZE, ConditionFlag.Less, labelSingleBlockLoopTopHead2[k], false);
            }
            if (k == 1) {
                masm.subq(rsp, 6 * wordSize);
                // save last_key from xmm15
                masm.movdqu(new AMD64Address(rsp, 0), xmm15);
                // 192-bit key goes up to 0xc0
                loadKey(masm, xmm15, key, 0xb0, crb); // 0xb0;
                masm.movdqu(new AMD64Address(rsp, 2 * wordSize), xmm15);
                loadKey(masm, xmm1, key, 0xc0, crb);  // 0xc0;
                masm.movdqu(new AMD64Address(rsp, 4 * wordSize), xmm1);
            } else if (k == 2) {
                masm.subq(rsp, 10 * wordSize);
                // save last_key from xmm15
                masm.movdqu(new AMD64Address(rsp, 0), xmm15);
                // 256-bit key goes up to 0xe0
                loadKey(masm, xmm15, key, 0xd0, crb); // 0xd0;
                masm.movdqu(new AMD64Address(rsp, 6 * wordSize), xmm15);
                loadKey(masm, xmm1, key, 0xe0, crb);  // 0xe0;
                masm.movdqu(new AMD64Address(rsp, 8 * wordSize), xmm1);
                loadKey(masm, xmm15, key, 0xb0, crb); // 0xb0;
                masm.movdqu(new AMD64Address(rsp, 2 * wordSize), xmm15);
                loadKey(masm, xmm1, key, 0xc0, crb);  // 0xc0;
                masm.movdqu(new AMD64Address(rsp, 4 * wordSize), xmm1);
            }
            masm.align(preferredLoopAlignment(crb));
            masm.bind(labelMultiBlockLoopTop[k]);
            masm.cmpq(lenReg, PARALLEL_FACTOR * AES_BLOCK_SIZE); // see if at least 4 blocks left
            masm.jcc(ConditionFlag.Less, labelSingleBlockLoopTopHead[k]);

            if (k != 0) {
                masm.movdqu(xmm15, new AMD64Address(rsp, 2 * wordSize));
                masm.movdqu(xmm1, new AMD64Address(rsp, 4 * wordSize));
            }

            // get next 4 blocks into xmmresult registers
            masm.movdqu(xmmResult0, new AMD64Address(from, pos, Stride.S1, 0 * AES_BLOCK_SIZE));
            masm.movdqu(xmmResult1, new AMD64Address(from, pos, Stride.S1, 1 * AES_BLOCK_SIZE));
            masm.movdqu(xmmResult2, new AMD64Address(from, pos, Stride.S1, 2 * AES_BLOCK_SIZE));
            masm.movdqu(xmmResult3, new AMD64Address(from, pos, Stride.S1, 3 * AES_BLOCK_SIZE));

            doFour(masm::pxor, xmmKeyFirst);
            if (k == 0) {
                for (int rnum = 1; rnum < ROUNDS[k]; rnum++) {
                    doFour(masm::aesdec, asXMMRegister(rnum + XMM_REG_NUM_KEY_FIRST));
                }
                doFour(masm::aesdeclast, xmmKeyLast);
            } else if (k == 1) {
                for (int rnum = 1; rnum <= ROUNDS[k] - 2; rnum++) {
                    doFour(masm::aesdec, asXMMRegister(rnum + XMM_REG_NUM_KEY_FIRST));
                }
                // xmm15 needs to be loaded again
                masm.movdqu(xmmKeyLast, new AMD64Address(rsp, 0));
                doFour(masm::aesdec, xmm1);  // key : 0xc0
                // xmm1 needs to be loaded again
                masm.movdqu(xmmPrevBlockCipher, new AMD64Address(rvec, 0x00));
                doFour(masm::aesdeclast, xmmKeyLast);
            } else if (k == 2) {
                for (int rnum = 1; rnum <= ROUNDS[k] - 4; rnum++) {
                    doFour(masm::aesdec, asXMMRegister(rnum + XMM_REG_NUM_KEY_FIRST));
                }
                doFour(masm::aesdec, xmm1);  // key : 0xc0
                masm.movdqu(xmm15, new AMD64Address(rsp, 6 * wordSize));
                masm.movdqu(xmm1, new AMD64Address(rsp, 8 * wordSize));
                doFour(masm::aesdec, xmm15);  // key : 0xd0
                // xmm15 needs to be loaded again
                masm.movdqu(xmmKeyLast, new AMD64Address(rsp, 0));
                doFour(masm::aesdec, xmm1);  // key : 0xe0
                // xmm1 needs to be loaded again
                masm.movdqu(xmmPrevBlockCipher, new AMD64Address(rvec, 0x00));
                doFour(masm::aesdeclast, xmmKeyLast);
            }

            // for each result, xor with the r vector of previous cipher block
            masm.pxor(xmmResult0, xmmPrevBlockCipher);
            masm.movdqu(xmmPrevBlockCipher, new AMD64Address(from, pos, Stride.S1, 0 * AES_BLOCK_SIZE));
            masm.pxor(xmmResult1, xmmPrevBlockCipher);
            masm.movdqu(xmmPrevBlockCipher, new AMD64Address(from, pos, Stride.S1, 1 * AES_BLOCK_SIZE));
            masm.pxor(xmmResult2, xmmPrevBlockCipher);
            masm.movdqu(xmmPrevBlockCipher, new AMD64Address(from, pos, Stride.S1, 2 * AES_BLOCK_SIZE));
            masm.pxor(xmmResult3, xmmPrevBlockCipher);
            // this will carry over to next set of blocks
            masm.movdqu(xmmPrevBlockCipher, new AMD64Address(from, pos, Stride.S1, 3 * AES_BLOCK_SIZE));
            if (k != 0) {
                masm.movdqu(new AMD64Address(rvec, 0x00), xmmPrevBlockCipher);
            }

            // store 4 results into the next 64 bytes of output
            masm.movdqu(new AMD64Address(to, pos, Stride.S1, 0 * AES_BLOCK_SIZE), xmmResult0);
            masm.movdqu(new AMD64Address(to, pos, Stride.S1, 1 * AES_BLOCK_SIZE), xmmResult1);
            masm.movdqu(new AMD64Address(to, pos, Stride.S1, 2 * AES_BLOCK_SIZE), xmmResult2);
            masm.movdqu(new AMD64Address(to, pos, Stride.S1, 3 * AES_BLOCK_SIZE), xmmResult3);

            masm.addq(pos, PARALLEL_FACTOR * AES_BLOCK_SIZE);
            masm.subq(lenReg, PARALLEL_FACTOR * AES_BLOCK_SIZE);
            masm.jmp(labelMultiBlockLoopTop[k]);

            // registers used in the non-parallelized loops
            // xmm register assignments for the loops below
            Register xmmResult = xmm0;
            Register xmmPrevBlockCipherSave = xmm2;
            Register xmmKey11 = xmm3;
            Register xmmKey12 = xmm4;
            Register keyTmp = xmm4;

            masm.bind(labelSingleBlockLoopTopHead[k]);
            if (k == 1) {
                masm.addq(rsp, 6 * wordSize);
            } else if (k == 2) {
                masm.addq(rsp, 10 * wordSize);
            }
            masm.cmpq(lenReg, 0); // any blocks left??
            masm.jcc(ConditionFlag.Equal, labelExit);
            masm.bind(labelSingleBlockLoopTopHead2[k]);
            if (k == 1) {
                loadKey(masm, xmmKey11, key, 0xb0, crb); // 0xb0;
                // 192-bit key goes up to 0xc0
                loadKey(masm, xmmKey12, key, 0xc0, crb); // 0xc0;
            }
            if (k == 2) {
                // 256-bit key goes up to 0xe0
                loadKey(masm, xmmKey11, key, 0xb0, crb); // 0xb0;
            }
            masm.align(preferredLoopAlignment(crb));
            masm.bind(labelSingleBlockLoopTop[k]);
            // get next 16 bytes of cipher input
            masm.movdqu(xmmResult, new AMD64Address(from, pos, Stride.S1, 0));
            masm.movdqa(xmmPrevBlockCipherSave, xmmResult); // save for next r vector
            masm.pxor(xmmResult, xmmKeyFirst); // do the aes dec rounds
            for (int rnum = 1; rnum <= 9; rnum++) {
                masm.aesdec(xmmResult, asXMMRegister(rnum + XMM_REG_NUM_KEY_FIRST));
            }
            if (k == 1) {
                masm.aesdec(xmmResult, xmmKey11);
                masm.aesdec(xmmResult, xmmKey12);
            }
            if (k == 2) {
                masm.aesdec(xmmResult, xmmKey11);
                loadKey(masm, keyTmp, key, 0xc0, crb);
                masm.aesdec(xmmResult, keyTmp);
                loadKey(masm, keyTmp, key, 0xd0, crb);
                masm.aesdec(xmmResult, keyTmp);
                loadKey(masm, keyTmp, key, 0xe0, crb);
                masm.aesdec(xmmResult, keyTmp);
            }

            masm.aesdeclast(xmmResult, xmmKeyLast); // xmm15 always came from key+0
            masm.pxor(xmmResult, xmmPrevBlockCipher); // xor with the current r vector
            // store into the next 16 bytes of output
            masm.movdqu(new AMD64Address(to, pos, Stride.S1, 0), xmmResult);
            // no need to store r to memory until we exit
            // set up next r vector with cipher input from this block
            masm.movdqa(xmmPrevBlockCipher, xmmPrevBlockCipherSave);
            masm.addq(pos, AES_BLOCK_SIZE);
            masm.subq(lenReg, AES_BLOCK_SIZE);
            masm.jcc(ConditionFlag.NotEqual, labelSingleBlockLoopTop[k]);
            if (k != 2) {
                masm.jmp(labelExit);
            }
        } // for 128/192/256

        masm.bind(labelExit);
        // final value of r stored in rvec of CipherBlockChaining object
        masm.movdqu(new AMD64Address(rvec, 0), xmmPrevBlockCipher);
        masm.movl(asRegister(resultValue), asRegister(lenValue));
    }

    private static void doFour(BiConsumer<Register, Register> op, Register src) {
        Register xmmResult0 = xmm0;
        Register xmmResult1 = xmm2;
        Register xmmResult2 = xmm3;
        Register xmmResult3 = xmm4;

        op.accept(xmmResult0, src);
        op.accept(xmmResult1, src);
        op.accept(xmmResult2, src);
        op.accept(xmmResult3, src);
    }
}
