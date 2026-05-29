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

import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD4R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.ST4;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_SIGNED_UNSCALED;
import static jdk.vm.ci.aarch64.AArch64.r0;
import static jdk.vm.ci.aarch64.AArch64.r1;
import static jdk.vm.ci.aarch64.AArch64.r11;
import static jdk.vm.ci.aarch64.AArch64.r20;
import static jdk.vm.ci.aarch64.AArch64.v0;
import static jdk.vm.ci.aarch64.AArch64.v1;
import static jdk.vm.ci.aarch64.AArch64.v16;
import static jdk.vm.ci.aarch64.AArch64.v17;
import static jdk.vm.ci.aarch64.AArch64.v18;
import static jdk.vm.ci.aarch64.AArch64.v19;
import static jdk.vm.ci.aarch64.AArch64.v2;
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
import static jdk.vm.ci.aarch64.AArch64.v3;
import static jdk.vm.ci.aarch64.AArch64.v4;
import static jdk.vm.ci.aarch64.AArch64.v5;
import static jdk.vm.ci.aarch64.AArch64.v6;
import static jdk.vm.ci.aarch64.AArch64.v7;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/c59e44a7aa2aeff0823830b698d524523b996650/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L4528-L4717",
          sha1 = "4d4610150b13b2f6287d3546fcaf2c2629455b59")
// @formatter:on
public final class AArch64ChaCha20BlockOp extends AArch64LIRInstruction {

    public static final LIRInstructionClass<AArch64ChaCha20BlockOp> TYPE = LIRInstructionClass.create(AArch64ChaCha20BlockOp.class);

    @Use({OperandFlag.REG}) private Value stateValue;
    @Use({OperandFlag.REG}) private Value resultValue;
    @Def({OperandFlag.REG}) private Value outputLengthValue;

    @Temp({OperandFlag.REG}) private Value[] temps;

    private static final ArrayDataPointerConstant CC20_CONST = AArch64LIRHelper.pointerConstant(16, new long[]{
                    0x0000000100000000L,
                    0x0000000300000002L,
                    0x0605040702010003L,
                    0x0E0D0C0F0A09080BL,
    });

    public AArch64ChaCha20BlockOp(AllocatableValue state, AllocatableValue result, AllocatableValue outputLength) {
        super(TYPE);

        GraalError.guarantee(asRegister(state).equals(r0), "expect stateValue at r0, but was %s", state);
        GraalError.guarantee(asRegister(result).equals(r1), "expect resultValue at r1, but was %s", result);

        this.stateValue = state;
        this.resultValue = result;
        this.outputLengthValue = outputLength;
        this.temps = new Value[]{
                        r1.asValue(),
                        r11.asValue(),
                        r20.asValue(),
                        v0.asValue(),
                        v1.asValue(),
                        v2.asValue(),
                        v3.asValue(),
                        v4.asValue(),
                        v5.asValue(),
                        v6.asValue(),
                        v7.asValue(),
                        v16.asValue(),
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
        };
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        GraalError.guarantee(stateValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid stateValue kind: %s", stateValue);
        GraalError.guarantee(resultValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid resultValue kind: %s", resultValue);
        GraalError.guarantee(outputLengthValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid outputLengthValue kind: %s", outputLengthValue);

        Register state = asRegister(stateValue);
        Register keystream = asRegister(resultValue);
        Register outLength = asRegister(outputLengthValue);

        // Use r20 instead of r10 here, since SVM treats r10 as a scratch register
        Register loopCtr = r20;
        Register tmpAddr = r11;

        Register ctrAddOverlay = v28;
        Register lrot8Tbl = v29;

        Register[] workSt = {
                        v4, v5, v6, v7,
                        v16, v17, v18, v19,
                        v20, v21, v22, v23,
                        v24, v25, v26, v27,
        };
        Register[] aSet = {v4, v5, v6, v7};
        Register[] bSet = new Register[4];
        Register[] cSet = new Register[4];
        Register[] dSet = new Register[4];
        Register[] scratch = {v0, v1, v2, v3};

        Label twoRounds = new Label();

        // Pull in constant data. The first 16 bytes are the add overlay
        // which is applied to the vector holding the counter (state[12]).
        // The second 16 bytes is the index register for the 8-bit left
        // rotation tbl instruction.
        crb.recordDataReferenceInCode(CC20_CONST);
        masm.adrpAdd(tmpAddr);
        masm.fldr(128, ctrAddOverlay, AArch64Address.createImmediateAddress(128, IMMEDIATE_SIGNED_UNSCALED, tmpAddr, 0));
        masm.fldr(128, lrot8Tbl, AArch64Address.createImmediateAddress(128, IMMEDIATE_SIGNED_UNSCALED, tmpAddr, 16));

        // @formatter:off
        // Load from memory and interlace across 16 SIMD registers,
        // With each word from memory being broadcast to all lanes of
        // each successive SIMD register.
        //      Addr(0) -> All lanes in workSt[i]
        //      Addr(4) -> All lanes workSt[i + 1], etc.
        // @formatter:on
        masm.mov(64, tmpAddr, state);
        for (int i = 0; i < 16; i += 4) {
            masm.neon.ld4rVVVV(FullReg, ElementSize.Word,
                            workSt[i], workSt[i + 1], workSt[i + 2], workSt[i + 3],
                            AArch64Address.createStructureImmediatePostIndexAddress(LD4R, FullReg, ElementSize.Word, tmpAddr, 16));
        }
        // Add ctr overlay
        masm.neon.addVVV(FullReg, ElementSize.Word, workSt[12], workSt[12], ctrAddOverlay);

        // Before entering the loop, create 5 4-register arrays. These
        // will hold the 4 registers that represent the a/b/c/d fields
        // in the quarter round operation. For instance the "b" field
        // for the first 4 quarter round operations is the set of v16/v17/v18/v19,
        // but in the second 4 quarter rounds it gets adjusted to v17/v18/v19/v16
        // since it is part of a diagonal organization. The aSet and scratch
        // register sets are defined at declaration time because they do not change
        // organization at any point during the 20-round processing.

        // Set up the 10 iteration loop and perform all 8 quarter round ops
        masm.mov(loopCtr, 10L);
        masm.bind(twoRounds);

        // Set to columnar organization and do the following 4 quarter-rounds:
        // QUARTERROUND(0, 4, 8, 12)
        // QUARTERROUND(1, 5, 9, 13)
        // QUARTERROUND(2, 6, 10, 14)
        // QUARTERROUND(3, 7, 11, 15)
        setQuarterRoundRegisters(bSet, workSt, 4, 5, 6, 7);
        setQuarterRoundRegisters(cSet, workSt, 8, 9, 10, 11);
        setQuarterRoundRegisters(dSet, workSt, 12, 13, 14, 15);

        // a += b
        qrAdd4(masm, aSet, bSet);
        // d ^= a
        qrXor4(masm, dSet, aSet, dSet);
        // d <<<= 16
        qrLrot4(masm, dSet, dSet, 16, lrot8Tbl);

        // c += d
        qrAdd4(masm, cSet, dSet);
        // b ^= c (scratch)
        qrXor4(masm, bSet, cSet, scratch);
        // b <<<= 12
        qrLrot4(masm, scratch, bSet, 12, lrot8Tbl);

        // a += b
        qrAdd4(masm, aSet, bSet);
        // d ^= a
        qrXor4(masm, dSet, aSet, dSet);
        // d <<<= 8
        qrLrot4(masm, dSet, dSet, 8, lrot8Tbl);

        // c += d
        qrAdd4(masm, cSet, dSet);
        // b ^= c (scratch)
        qrXor4(masm, bSet, cSet, scratch);
        // b <<<= 7
        qrLrot4(masm, scratch, bSet, 7, lrot8Tbl);

        // Set to diagonal organization and do the next 4 quarter-rounds:
        // QUARTERROUND(0, 5, 10, 15)
        // QUARTERROUND(1, 6, 11, 12)
        // QUARTERROUND(2, 7, 8, 13)
        // QUARTERROUND(3, 4, 9, 14)
        setQuarterRoundRegisters(bSet, workSt, 5, 6, 7, 4);
        setQuarterRoundRegisters(cSet, workSt, 10, 11, 8, 9);
        setQuarterRoundRegisters(dSet, workSt, 15, 12, 13, 14);

        // a += b
        qrAdd4(masm, aSet, bSet);
        // d ^= a
        qrXor4(masm, dSet, aSet, dSet);
        // d <<<= 16
        qrLrot4(masm, dSet, dSet, 16, lrot8Tbl);

        // c += d
        qrAdd4(masm, cSet, dSet);
        // b ^= c (scratch)
        qrXor4(masm, bSet, cSet, scratch);
        // b <<<= 12
        qrLrot4(masm, scratch, bSet, 12, lrot8Tbl);

        // a += b
        qrAdd4(masm, aSet, bSet);
        // d ^= a
        qrXor4(masm, dSet, aSet, dSet);
        // d <<<= 8
        qrLrot4(masm, dSet, dSet, 8, lrot8Tbl);

        // c += d
        qrAdd4(masm, cSet, dSet);
        // b ^= c (scratch)
        qrXor4(masm, bSet, cSet, scratch);
        // b <<<= 7
        qrLrot4(masm, scratch, bSet, 7, lrot8Tbl);

        // Decrement and iterate
        masm.sub(64, loopCtr, loopCtr, 1);
        masm.cbnz(64, loopCtr, twoRounds);

        // Add the starting state back to the post-loop keystream
        // state. We read/interlace the state array from memory into
        // 4 registers similar to what we did in the beginning. Then
        // add the counter overlay onto workSt[12] at the end.
        masm.mov(64, tmpAddr, state);
        for (int i = 0; i < 16; i += 4) {
            masm.neon.ld4rVVVV(FullReg, ElementSize.Word,
                            v0, v1, v2, v3,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD4R, FullReg, ElementSize.Word, tmpAddr, 16));
            masm.neon.addVVV(FullReg, ElementSize.Word, workSt[i], workSt[i], v0);
            masm.neon.addVVV(FullReg, ElementSize.Word, workSt[i + 1], workSt[i + 1], v1);
            masm.neon.addVVV(FullReg, ElementSize.Word, workSt[i + 2], workSt[i + 2], v2);
            masm.neon.addVVV(FullReg, ElementSize.Word, workSt[i + 3], workSt[i + 3], v3);
        }
        // Add ctr overlay
        masm.neon.addVVV(FullReg, ElementSize.Word, workSt[12], workSt[12], ctrAddOverlay);

        // Write working state into the keystream buffer. This is accomplished
        // by taking the lane "i" from each of the four vectors and writing
        // it to consecutive 4-byte offsets, then post-incrementing by 16 and
        // repeating with the next 4 vectors until all 16 vectors have been used.
        // Then move to the next lane and repeat the process until all lanes have
        // been written.

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 16; j += 4) {
                storeKeystreamLaneGroup(masm, workSt[j], workSt[j + 1], workSt[j + 2], workSt[j + 3], keystream, i);
            }
        }

        // Return length of output keystream
        masm.orr(64, outLength, zr, 0x100);
    }

    private static void setQuarterRoundRegisters(Register[] registers, Register[] workSt, int r0, int r1, int r2, int r3) {
        registers[0] = workSt[r0];
        registers[1] = workSt[r1];
        registers[2] = workSt[r2];
        registers[3] = workSt[r3];
    }

    private static void qrAdd4(AArch64MacroAssembler masm, Register[] a, Register[] b) {
        for (int i = 0; i < 4; i++) {
            masm.neon.addVVV(FullReg, ElementSize.Word, a[i], a[i], b[i]);
        }
    }

    private static void qrXor4(AArch64MacroAssembler masm, Register[] firstElem, Register[] secondElem, Register[] result) {
        for (int i = 0; i < 4; i++) {
            masm.neon.eorVVV(FullReg, result[i], firstElem[i], secondElem[i]);
        }
    }

    private static void qrLrot4(AArch64MacroAssembler masm, Register[] src, Register[] dst, int shift, Register lrot8Tbl) {
        if (shift == 16) {
            for (int i = 0; i < 4; i++) {
                masm.neon.rev32VV(FullReg, ElementSize.HalfWord, dst[i], src[i]);
            }
        } else if (shift == 8) {
            for (int i = 0; i < 4; i++) {
                masm.neon.tblVVV(FullReg, dst[i], src[i], lrot8Tbl);
            }
        } else {
            for (int i = 0; i < 4; i++) {
                masm.neon.ushrVVI(FullReg, ElementSize.Word, dst[i], src[i], 32 - shift);
            }
            for (int i = 0; i < 4; i++) {
                masm.neon.sliVVI(FullReg, ElementSize.Word, dst[i], src[i], shift);
            }
        }
    }

    private static void storeKeystreamLaneGroup(AArch64MacroAssembler masm, Register src0, Register src1, Register src2, Register src3, Register keystream, int lane) {
        masm.neon.st4SingleVVVV(ElementSize.Word, src0, src1, src2, src3, lane,
                        AArch64Address.createStructureImmediatePostIndexAddress(ST4, FullReg, ElementSize.Word, keystream, 16));
    }
}
