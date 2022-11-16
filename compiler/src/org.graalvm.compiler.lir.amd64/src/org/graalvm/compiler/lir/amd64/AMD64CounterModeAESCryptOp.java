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
package org.graalvm.compiler.lir.amd64;

import static jdk.vm.ci.amd64.AMD64.r11;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rbx;
import static jdk.vm.ci.amd64.AMD64.xmm0;
import static jdk.vm.ci.amd64.AMD64.xmm1;
import static jdk.vm.ci.amd64.AMD64.xmm10;
import static jdk.vm.ci.amd64.AMD64.xmm11;
import static jdk.vm.ci.amd64.AMD64.xmm12;
import static jdk.vm.ci.amd64.AMD64.xmm13;
import static jdk.vm.ci.amd64.AMD64.xmm14;
import static jdk.vm.ci.amd64.AMD64.xmm2;
import static jdk.vm.ci.amd64.AMD64.xmm3;
import static jdk.vm.ci.amd64.AMD64.xmm4;
import static jdk.vm.ci.amd64.AMD64.xmm5;
import static jdk.vm.ci.amd64.AMD64.xmm6;
import static jdk.vm.ci.amd64.AMD64.xmm7;
import static jdk.vm.ci.amd64.AMD64.xmm8;
import static jdk.vm.ci.amd64.AMD64.xmm9;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexMRIOp.VPEXTRB;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexMRIOp.VPEXTRD;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexMRIOp.VPEXTRQ;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexMRIOp.VPEXTRW;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.VPINSRB;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.VPINSRD;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.VPINSRQ;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.VPINSRW;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.amd64.AMD64AESEncryptOp.AES_BLOCK_SIZE;
import static org.graalvm.compiler.lir.amd64.AMD64AESEncryptOp.loadKey;
import static org.graalvm.compiler.lir.amd64.AMD64HotSpotHelper.pointerConstant;
import static org.graalvm.compiler.lir.amd64.AMD64HotSpotHelper.recordExternalAddress;

import java.util.function.BiConsumer;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.asm.amd64.AVXKind.AVXSize;
import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.StubPort;
import org.graalvm.compiler.lir.asm.ArrayDataPointerConstant;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@StubPort(path      = "src/hotspot/cpu/x86/stubGenerator_x86_64_aes.cpp",
          lineStart = 323,
          lineEnd   = 630,
          commit    = "090cdfc7a2e280c620a0926512fb67f0ce7f3c21",
          sha1      = "15d222b1d71c2bf1284277ca93b3c3e5c3dc6f05")
// @formatter:on
public final class AMD64CounterModeAESCryptOp extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64CounterModeAESCryptOp> TYPE = LIRInstructionClass.create(AMD64CounterModeAESCryptOp.class);

    private final int lengthOffset;

    @Alive({REG}) private Value inValue;
    @Alive({REG}) private Value outValue;
    @Alive({REG}) private Value keyValue;
    @Alive({REG}) private Value counterValue;
    @Alive({REG}) private Value lenValue;
    @Alive({REG}) private Value encryptedCounterValue;
    @Alive({REG}) private Value usedPtrValue;

    @Def({REG}) protected Value resultValue;

    @Temp protected Value[] temps;

    public AMD64CounterModeAESCryptOp(AllocatableValue inValue,
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

        temps = new Value[]{
                        r11.asValue(),
                        rax.asValue(),
                        rbx.asValue(),
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
        };
    }

    static Label[] newLabels(int len) {
        Label[] labels = new Label[len];
        for (int i = 0; i < len; i++) {
            labels[i] = new Label();
        }
        return labels;
    }

    private static Label[][] newLabels(int lenDimension1, int lenDimension2) {
        Label[][] labels = new Label[lenDimension1][lenDimension2];
        for (int i = 0; i < lenDimension1; i++) {
            labels[i] = new Label[lenDimension2];
            for (int j = 0; j < lenDimension2; j++) {
                labels[i][j] = new Label();
            }
        }
        return labels;
    }

    private static final int PARALLEL_FACTOR = 6;

    private ArrayDataPointerConstant keyShuffleMask = pointerConstant(16, new int[]{
            // @formatter:off
            0x00010203, 0x04050607, 0x08090a0b, 0x0c0d0e0f
            // @formatter:on
    });

    private ArrayDataPointerConstant counterShuffleMask = pointerConstant(16, new int[]{
            // @formatter:off
            0x0c0d0e0f, 0x08090a0b, 0x04050607, 0x00010203,
            // @formatter:on
    });

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        GraalError.guarantee(inValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid inValue kind: %s", inValue);
        GraalError.guarantee(outValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid outValue kind: %s", outValue);
        GraalError.guarantee(keyValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid keyValue kind: %s", keyValue);
        GraalError.guarantee(counterValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid counterValue kind: %s", counterValue);
        GraalError.guarantee(lenValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid lenValue kind: %s", lenValue);
        GraalError.guarantee(encryptedCounterValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid encryptedCounterValue kind: %s", encryptedCounterValue);
        GraalError.guarantee(usedPtrValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid usedPtrValue kind: %s", usedPtrValue);
        GraalError.guarantee(resultValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid resultValue kind: %s", resultValue);

        Register from = asRegister(inValue);
        Register to = asRegister(outValue);
        Register key = asRegister(keyValue);
        Register counter = asRegister(counterValue);
        Register lenReg = asRegister(resultValue);
        Register savedEncCounterStart = asRegister(encryptedCounterValue);
        Register usedAddr = asRegister(usedPtrValue);
        Register used = r11;
        Register pos = rax;

        Register xmmCounterShufMask = xmm0;
        // used temporarily to swap key bytes up front
        Register xmmKeyShufMask = xmm1;
        Register xmmCurrCounter = xmm2;

        Register xmmKeyTmp0 = xmm3;
        Register xmmKeyTmp1 = xmm4;

        // registers holding the four results in the parallelized loop
        Register xmmResult0 = xmm5;
        Register xmmResult1 = xmm6;
        Register xmmResult2 = xmm7;
        Register xmmResult3 = xmm8;
        Register xmmResult4 = xmm9;
        Register xmmResult5 = xmm10;

        Register xmmFrom0 = xmm11;
        Register xmmFrom1 = xmm12;
        Register xmmFrom2 = xmm13;
        Register xmmFrom3 = xmm14;
        // reuse xmm3~4. Because xmmKeyTmp0~1 are useless when loading input text
        Register xmmFrom4 = xmm3;
        Register xmmFrom5 = xmm4;

        // for key_128, key_192, key_256
        int[] rounds = new int[]{10, 12, 14};
        Label labelExitPreLoop = new Label();
        Label labelPreLoopStart = new Label();
        Label[] labelMultiBlockLoopTop = newLabels(3);
        Label[] labelSingleBlockLoopTop = newLabels(3);
        // for 6 blocks
        Label[][] labelIncCounter = newLabels(3, 6);
        // for single block, key128, key192, key256
        Label[] labelIncCounterSingle = newLabels(3);
        Label[] labelProcessTailInsr = newLabels(3);
        Label[] labelProcessTail4Insr = newLabels(3);
        Label[] labelProcessTail2Insr = newLabels(3);
        Label[] labelProcessTail1Insr = newLabels(3);
        Label[] labelProcessTailExitInsr = newLabels(3);
        Label[] labelProcessTail4Extr = newLabels(3);
        Label[] labelProcessTail2Extr = newLabels(3);
        Label[] labelProcessTail1Extr = newLabels(3);
        Label[] labelProcessTailExitExtr = newLabels(3);

        Label labelExit = new Label();

        masm.movl(lenReg, asRegister(lenValue));
        masm.movl(used, new AMD64Address(usedAddr));

        // initialize counter with initial counter
        masm.movdqu(xmmCurrCounter, new AMD64Address(counter));
        // pos as scratch
        masm.movdqu(xmmCounterShufMask, recordExternalAddress(crb, counterShuffleMask));
        // counter is shuffled
        masm.vpshufb(xmmCurrCounter, xmmCurrCounter, xmmCounterShufMask, AVXSize.XMM);
        masm.movq(pos, 0);

        // Use the partially used encrpyted counter from last invocation
        masm.bind(labelPreLoopStart);
        masm.cmplAndJcc(used, 16, ConditionFlag.AboveEqual, labelExitPreLoop, false);
        masm.cmplAndJcc(lenReg, 0, ConditionFlag.LessEqual, labelExitPreLoop, false);
        masm.movb(rbx, new AMD64Address(savedEncCounterStart, used, Stride.S1));
        masm.xorb(rbx, new AMD64Address(from, pos, Stride.S1));
        masm.movb(new AMD64Address(to, pos, Stride.S1), rbx);
        masm.addq(pos, 1);
        masm.addl(used, 1);
        masm.subl(lenReg, 1);

        masm.jmp(labelPreLoopStart);

        masm.bind(labelExitPreLoop);
        masm.movl(new AMD64Address(usedAddr), used);

        // key length could be only {11, 13, 15} * 4 = {44, 52, 60}
        masm.movdqu(xmmKeyShufMask, recordExternalAddress(crb, keyShuffleMask));
        masm.movl(rbx, new AMD64Address(key, lengthOffset));
        masm.cmplAndJcc(rbx, 52, ConditionFlag.Equal, labelMultiBlockLoopTop[1], false);
        masm.cmplAndJcc(rbx, 60, ConditionFlag.Equal, labelMultiBlockLoopTop[2], false);

        // k == 0 : generate code for key_128
        // k == 1 : generate code for key_192
        // k == 2 : generate code for key_256
        for (int k = 0; k < 3; k++) {
            // multi blocks starts here
            masm.align(preferredLoopAlignment(crb));
            masm.bind(labelMultiBlockLoopTop[k]);
            // see if at least PARALLEL_FACTOR blocks left
            masm.cmplAndJcc(lenReg, PARALLEL_FACTOR * AES_BLOCK_SIZE, ConditionFlag.LessEqual, labelSingleBlockLoopTop[k], false);
            loadKey(masm, xmmKeyTmp0, key, 0x00, xmmKeyShufMask);

            // load, then increase counters
            applyCTRDoSix(masm::movdqa, xmmCurrCounter);
            incCounter(masm, rbx, xmmResult1, 0x01, labelIncCounter[k][0]);
            incCounter(masm, rbx, xmmResult2, 0x02, labelIncCounter[k][1]);
            incCounter(masm, rbx, xmmResult3, 0x03, labelIncCounter[k][2]);
            incCounter(masm, rbx, xmmResult4, 0x04, labelIncCounter[k][3]);
            incCounter(masm, rbx, xmmResult5, 0x05, labelIncCounter[k][4]);
            incCounter(masm, rbx, xmmCurrCounter, 0x06, labelIncCounter[k][5]);
            // after increased, shuffled counters back for PXOR
            applyCTRDoSix((dst, src) -> masm.vpshufb(dst, dst, src, AVXSize.XMM), xmmCounterShufMask);
            // PXOR with Round 0 key
            applyCTRDoSix(masm::pxor, xmmKeyTmp0);

            // load two ROUND_KEYs at a time
            for (int i = 1; i < rounds[k];) {
                loadKey(masm, xmmKeyTmp1, key, i * 0x10, xmmKeyShufMask);
                loadKey(masm, xmmKeyTmp0, key, (i + 1) * 0x10, xmmKeyShufMask);
                applyCTRDoSix(masm::aesenc, xmmKeyTmp1);
                i++;
                if (i != rounds[k]) {
                    applyCTRDoSix(masm::aesenc, xmmKeyTmp0);
                } else {
                    applyCTRDoSix(masm::aesenclast, xmmKeyTmp0);
                }
                i++;
            }

            // get next PARALLEL_FACTOR blocks into xmmResult registers
            masm.movdqu(xmmFrom0, new AMD64Address(from, pos, Stride.S1, 0 * AES_BLOCK_SIZE));
            masm.movdqu(xmmFrom1, new AMD64Address(from, pos, Stride.S1, 1 * AES_BLOCK_SIZE));
            masm.movdqu(xmmFrom2, new AMD64Address(from, pos, Stride.S1, 2 * AES_BLOCK_SIZE));
            masm.movdqu(xmmFrom3, new AMD64Address(from, pos, Stride.S1, 3 * AES_BLOCK_SIZE));
            masm.movdqu(xmmFrom4, new AMD64Address(from, pos, Stride.S1, 4 * AES_BLOCK_SIZE));
            masm.movdqu(xmmFrom5, new AMD64Address(from, pos, Stride.S1, 5 * AES_BLOCK_SIZE));

            masm.pxor(xmmResult0, xmmFrom0);
            masm.pxor(xmmResult1, xmmFrom1);
            masm.pxor(xmmResult2, xmmFrom2);
            masm.pxor(xmmResult3, xmmFrom3);
            masm.pxor(xmmResult4, xmmFrom4);
            masm.pxor(xmmResult5, xmmFrom5);

            // store 6 results into the next 64 bytes of output
            masm.movdqu(new AMD64Address(to, pos, Stride.S1, 0 * AES_BLOCK_SIZE), xmmResult0);
            masm.movdqu(new AMD64Address(to, pos, Stride.S1, 1 * AES_BLOCK_SIZE), xmmResult1);
            masm.movdqu(new AMD64Address(to, pos, Stride.S1, 2 * AES_BLOCK_SIZE), xmmResult2);
            masm.movdqu(new AMD64Address(to, pos, Stride.S1, 3 * AES_BLOCK_SIZE), xmmResult3);
            masm.movdqu(new AMD64Address(to, pos, Stride.S1, 4 * AES_BLOCK_SIZE), xmmResult4);
            masm.movdqu(new AMD64Address(to, pos, Stride.S1, 5 * AES_BLOCK_SIZE), xmmResult5);

            // increase the length of crypt text
            masm.addq(pos, PARALLEL_FACTOR * AES_BLOCK_SIZE);
            // decrease the remaining length
            masm.subl(lenReg, PARALLEL_FACTOR * AES_BLOCK_SIZE);
            masm.jmp(labelMultiBlockLoopTop[k]);

            // singleBlock starts here
            masm.align(preferredLoopAlignment(crb));
            masm.bind(labelSingleBlockLoopTop[k]);
            masm.cmplAndJcc(lenReg, 0, ConditionFlag.LessEqual, labelExit, false);
            loadKey(masm, xmmKeyTmp0, key, 0x00, xmmKeyShufMask);
            masm.movdqa(xmmResult0, xmmCurrCounter);
            incCounter(masm, rbx, xmmCurrCounter, 0x01, labelIncCounterSingle[k]);
            masm.vpshufb(xmmResult0, xmmResult0, xmmCounterShufMask, AVXSize.XMM);
            masm.pxor(xmmResult0, xmmKeyTmp0);
            for (int i = 1; i < rounds[k]; i++) {
                loadKey(masm, xmmKeyTmp0, key, i * 0x10, xmmKeyShufMask);
                masm.aesenc(xmmResult0, xmmKeyTmp0);
            }
            loadKey(masm, xmmKeyTmp0, key, rounds[k] * 0x10, xmmKeyShufMask);
            masm.aesenclast(xmmResult0, xmmKeyTmp0);
            masm.cmplAndJcc(lenReg, AES_BLOCK_SIZE, ConditionFlag.Less, labelProcessTailInsr[k], false);
            masm.movdqu(xmmFrom0, new AMD64Address(from, pos, Stride.S1, 0 * AES_BLOCK_SIZE));
            masm.pxor(xmmResult0, xmmFrom0);
            masm.movdqu(new AMD64Address(to, pos, Stride.S1, 0 * AES_BLOCK_SIZE), xmmResult0);
            masm.addq(pos, AES_BLOCK_SIZE);
            masm.subl(lenReg, AES_BLOCK_SIZE);
            masm.jmp(labelSingleBlockLoopTop[k]);

            // Process the tail part of the input array
            masm.bind(labelProcessTailInsr[k]);
            // 1. Insert bytes from src array into xmmFrom0 register
            masm.addq(pos, lenReg);
            masm.testlAndJcc(lenReg, 8, ConditionFlag.Zero, labelProcessTail4Insr[k], false);
            masm.subq(pos, 8);

            VPINSRQ.emit(masm, AVXSize.XMM, xmmFrom0, xmmFrom0, new AMD64Address(from, pos, Stride.S1), 0);
            masm.bind(labelProcessTail4Insr[k]);
            masm.testlAndJcc(lenReg, 4, ConditionFlag.Zero, labelProcessTail2Insr[k], false);
            masm.subq(pos, 4);
            masm.pslldq(xmmFrom0, 4);
            VPINSRD.emit(masm, AVXSize.XMM, xmmFrom0, xmmFrom0, new AMD64Address(from, pos, Stride.S1), 0);
            masm.bind(labelProcessTail2Insr[k]);
            masm.testlAndJcc(lenReg, 2, ConditionFlag.Zero, labelProcessTail1Insr[k], false);
            masm.subq(pos, 2);
            masm.pslldq(xmmFrom0, 2);
            VPINSRW.emit(masm, AVXSize.XMM, xmmFrom0, xmmFrom0, new AMD64Address(from, pos, Stride.S1), 0);
            masm.bind(labelProcessTail1Insr[k]);
            masm.testlAndJcc(lenReg, 1, ConditionFlag.Zero, labelProcessTailExitInsr[k], false);
            masm.subq(pos, 1);
            masm.pslldq(xmmFrom0, 1);
            VPINSRB.emit(masm, AVXSize.XMM, xmmFrom0, xmmFrom0, new AMD64Address(from, pos, Stride.S1), 0);
            masm.bind(labelProcessTailExitInsr[k]);
            // 2. Perform pxor of the encrypted counter and plaintext Bytes.
            // Also the encrypted counter is saved for next invocation.
            masm.movdqu(new AMD64Address(savedEncCounterStart), xmmResult0);
            masm.pxor(xmmResult0, xmmFrom0);
            // 3. Extract bytes from xmmResult0 into the dest. array
            masm.testlAndJcc(lenReg, 8, ConditionFlag.Zero, labelProcessTail4Extr[k], false);
            VPEXTRQ.emit(masm, AVXSize.XMM, new AMD64Address(to, pos, Stride.S1), xmmResult0, 0);
            masm.psrldq(xmmResult0, 8);
            masm.addq(pos, 8);
            masm.bind(labelProcessTail4Extr[k]);
            masm.testlAndJcc(lenReg, 4, ConditionFlag.Zero, labelProcessTail2Extr[k], false);
            VPEXTRD.emit(masm, AVXSize.XMM, new AMD64Address(to, pos, Stride.S1), xmmResult0, 0);
            masm.psrldq(xmmResult0, 4);
            masm.addq(pos, 4);
            masm.bind(labelProcessTail2Extr[k]);
            masm.testlAndJcc(lenReg, 2, ConditionFlag.Zero, labelProcessTail1Extr[k], false);
            VPEXTRW.emit(masm, AVXSize.XMM, new AMD64Address(to, pos, Stride.S1), xmmResult0, 0);
            masm.psrldq(xmmResult0, 2);
            masm.addq(pos, 2);
            masm.bind(labelProcessTail1Extr[k]);
            masm.testlAndJcc(lenReg, 1, ConditionFlag.Zero, labelProcessTailExitExtr[k], false);
            VPEXTRB.emit(masm, AVXSize.XMM, new AMD64Address(to, pos, Stride.S1), xmmResult0, 0);

            masm.bind(labelProcessTailExitExtr[k]);
            masm.movl(new AMD64Address(usedAddr), lenReg);
            masm.jmp(labelExit);
        }

        masm.bind(labelExit);
        // counter is shuffled back.
        masm.vpshufb(xmmCurrCounter, xmmCurrCounter, xmmCounterShufMask, AVXSize.XMM);
        masm.movdqu(new AMD64Address(counter), xmmCurrCounter); // save counter back
        masm.movl(asRegister(resultValue), asRegister(lenValue));
    }

    private static void incCounter(AMD64MacroAssembler masm, Register reg, Register xmmdst, int incDelta, Label nextBlock) {
        VPEXTRQ.emit(masm, AVXSize.XMM, reg, xmmdst, 0x00);
        masm.addq(reg, incDelta);
        VPINSRQ.emit(masm, AVXSize.XMM, xmmdst, xmmdst, reg, 0x00);
        masm.jcc(ConditionFlag.CarryClear, nextBlock); // jump if no carry
        VPEXTRQ.emit(masm, AVXSize.XMM, reg, xmmdst, 0x01); // Carry-> D1
        masm.addq(reg, 0x01);
        VPINSRQ.emit(masm, AVXSize.XMM, xmmdst, xmmdst, reg, 0x01);
        masm.bind(nextBlock); // next instruction
    }

    private static void applyCTRDoSix(BiConsumer<Register, Register> op, Register src) {
        Register xmmResult0 = xmm5;
        Register xmmResult1 = xmm6;
        Register xmmResult2 = xmm7;
        Register xmmResult3 = xmm8;
        Register xmmResult4 = xmm9;
        Register xmmResult5 = xmm10;

        op.accept(xmmResult0, src);
        op.accept(xmmResult1, src);
        op.accept(xmmResult2, src);
        op.accept(xmmResult3, src);
        op.accept(xmmResult4, src);
        op.accept(xmmResult5, src);
    }
}
