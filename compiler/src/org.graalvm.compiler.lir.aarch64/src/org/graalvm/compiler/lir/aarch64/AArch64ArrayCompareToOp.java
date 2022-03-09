/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.aarch64.AArch64.SIMD;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import jdk.vm.ci.meta.AllocatableValue;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

/**
 * Emits code which compares two arrays lexicographically. If the CPU supports any vector
 * instructions specialized code is emitted to leverage these instructions.
 */
@Opcode("ARRAY_COMPARE_TO")
public final class AArch64ArrayCompareToOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64ArrayCompareToOp> TYPE = LIRInstructionClass.create(AArch64ArrayCompareToOp.class);

    private final int array1BaseOffset;
    private final int array2BaseOffset;

    private final boolean isLL;
    private final boolean isUU;
    private final boolean isLU;
    private final boolean isUL;

    @Def({REG}) protected Value resultValue;

    @Alive({REG}) protected Value array1Value;
    @Alive({REG}) protected Value array2Value;
    @Use({REG}) protected Value length1Value;
    @Use({REG}) protected Value length2Value;
    @Temp({REG}) protected Value length1ValueTemp;
    @Temp({REG}) protected Value length2ValueTemp;

    @Temp({REG}) protected Value temp1;
    @Temp({REG}) protected Value temp2;
    @Temp({REG}) protected Value temp3;
    @Temp({REG}) protected Value temp4;
    @Temp({REG}) protected Value temp5;
    @Temp({REG}) protected Value temp6;

    @Temp({REG}) protected AllocatableValue vectorTemp1;
    @Temp({REG}) protected AllocatableValue vectorTemp2;
    @Temp({REG}) protected AllocatableValue vectorTemp3;
    @Temp({REG}) protected AllocatableValue vectorTemp4;
    @Temp({REG}) protected AllocatableValue vectorTemp5;
    @Temp({REG}) protected AllocatableValue vectorTemp6;

    public AArch64ArrayCompareToOp(LIRGeneratorTool tool, JavaKind kind1, JavaKind kind2, int array1BaseOffset, int array2BaseOffset, Value result, Value array1, Value array2, Value length1,
                    Value length2) {
        super(TYPE);

        this.isLL = (kind1 == kind2 && kind1 == JavaKind.Byte);
        this.isUU = (kind1 == kind2 && kind1 == JavaKind.Char);
        this.isLU = (kind1 != kind2 && kind1 == JavaKind.Byte);
        this.isUL = (kind1 != kind2 && kind1 == JavaKind.Char);

        this.array1BaseOffset = array1BaseOffset;
        this.array2BaseOffset = array2BaseOffset;
        this.resultValue = result;
        this.array1Value = array1;
        this.array2Value = array2;

        /*
         * The length values are inputs but are also killed like temporaries so need both Use and
         * Temp annotations, which will only work with fixed registers.
         */

        this.length1Value = length1;
        this.length2Value = length2;
        this.length1ValueTemp = length1;
        this.length2ValueTemp = length2;

        LIRKind archWordKind = LIRKind.unknownReference(tool.target().arch.getWordKind());
        this.temp1 = tool.newVariable(archWordKind);
        this.temp2 = tool.newVariable(archWordKind);
        this.temp3 = tool.newVariable(archWordKind);
        this.temp4 = tool.newVariable(archWordKind);
        this.temp5 = tool.newVariable(archWordKind);
        this.temp6 = tool.newVariable(archWordKind);
        LIRKind vectorKind = LIRKind.value(tool.target().arch.getLargestStorableKind(SIMD));
        vectorTemp1 = tool.newVariable(vectorKind);
        vectorTemp2 = tool.newVariable(vectorKind);
        vectorTemp3 = tool.newVariable(vectorKind);
        vectorTemp4 = tool.newVariable(vectorKind);
        vectorTemp5 = tool.newVariable(vectorKind);
        vectorTemp6 = tool.newVariable(vectorKind);
    }

    @Override
    protected void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        /*
         * Note: AArch64StringUTF16Substitutions.compareToLatin1 and
         * AArch64StringUTF16Substitutions.compareToLatin1 swap input to array1=byte[] and
         * array2=char[] but kind1==Char and kind2==Byte remains
         */
        Register result = asRegister(resultValue);
        Register length1 = asRegister(length1Value);
        Register length2 = asRegister(length2Value);

        Register array1 = asRegister(temp1);
        Register array2 = asRegister(temp2);
        Register length = asRegister(temp3);

        final Label breakLabel = new Label();
        final Label stringsEqualUptoLength = new Label();
        final Label simdImpl = new Label();

        final int simdThreshold = 32;

        /* Load array base addresses. */
        masm.add(64, array1, asRegister(array1Value), array1BaseOffset);
        masm.add(64, array2, asRegister(array2Value), array2BaseOffset);
        masm.prfm(AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_SIGNED_UNSCALED, array1, 0), AArch64Assembler.PrefetchMode.PLDL1STRM);
        masm.prfm(AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_SIGNED_UNSCALED, array2, 0), AArch64Assembler.PrefetchMode.PLDL1STRM);

        /*
         * Calculate minimal length in chars for different kind cases. Conditions could be squashed
         * but let's keep it readable.
         */
        if (!isLL) {
            masm.lsr(64, length2, length2, 1);
        }

        if (isUU) {
            masm.lsr(64, length1, length1, 1);
        }

        /* Get length of the smaller string */
        masm.cmp(64, length1, length2);
        masm.csel(64, length, length1, length2, ConditionFlag.LT);

        /* One of strings is empty */
        masm.cbz(64, length, stringsEqualUptoLength);

        /*
         * Go back to bytes for not LL cases, because following tail and length calculation is done
         * in byte.
         */
        if (!isLL) {
            masm.lsl(64, length, length, 1);
        }

        masm.compare(64, length, simdThreshold);
        masm.branchConditionally(ConditionFlag.GE, simdImpl);

        /* Scalar Implementation */
        emitScalarCode(masm, stringsEqualUptoLength, breakLabel);
        masm.jmp(breakLabel);

        /* SIMD Implementation */
        masm.bind(simdImpl);
        emitSIMDCode(masm, stringsEqualUptoLength);
        masm.jmp(breakLabel);

        /* Strings are equal up to length, return length difference in chars. */
        masm.bind(stringsEqualUptoLength);
        if (isUL) {
            /* UL's input has been swapped in AArch64StringUTF16Substitutions.compareToLatin1. */
            masm.sub(64, result, length2, length1);
        } else {
            masm.sub(64, result, length1, length2);
        }

        masm.bind(breakLabel);
    }

    private void emitScalarCode(AArch64MacroAssembler masm, Label stringsEqualUptoLength, Label breakLabel) {
        /* Retrieve registers pre-populated in emitCode() */
        Register result = asRegister(resultValue);
        Register array1 = asRegister(temp1);
        Register array2 = asRegister(temp2);
        Register length = asRegister(temp3);
        /* Allocate new temporary registers */
        Register temp = asRegister(temp4);
        Register tailCount = asRegister(temp5);
        Register vecCount = asRegister(temp6);

        final Label stringDifferLabel = new Label();
        final Label charSearchLoop = new Label();

        int charSizeBytes = 1;
        final int vectorSizeBytes = 32;

        /* Byte is expanded to short if we compare non-LL strings. */
        if (!isLL) {
            charSizeBytes = 2;
        }

        masm.mov(64, vecCount, zr);
        /* tail count (in bytes) */
        masm.and(64, tailCount, length, vectorSizeBytes - 1);

        masm.align(16);
        masm.bind(charSearchLoop);
        int transferSize = 8 * charSizeBytes;
        if (isLU || isUL) {
            masm.ldr(8, temp, AArch64Address.createImmediateAddress(8, AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED, array1, 1));
        } else {
            masm.ldr(transferSize, temp, AArch64Address.createImmediateAddress(transferSize, AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED, array1, charSizeBytes));
        }
        masm.ldr(transferSize, result, AArch64Address.createImmediateAddress(transferSize, AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED, array2, charSizeBytes));
        if (isUL) {
            /* UL's input has been swapped in AArch64StringUTF16Substitutions.compareToLatin1. */
            masm.subs(64, result, result, temp);
        } else {
            masm.subs(64, result, temp, result);
        }
        masm.branchConditionally(ConditionFlag.NE, breakLabel);
        masm.subs(64, tailCount, tailCount, charSizeBytes);
        masm.branchConditionally(ConditionFlag.EQ, stringsEqualUptoLength);
        masm.jmp(charSearchLoop);

        /* Extract exact value of a difference. */
        masm.bind(stringDifferLabel);
        masm.rbit(64, tailCount, result);
        masm.clz(64, vecCount, tailCount);
        /* Round to byte or short */
        masm.and(64, vecCount, vecCount, ~((8 * charSizeBytes) - 1));

        masm.eor(64, result, temp, result);
        masm.asr(64, result, result, vecCount);
        masm.asr(64, temp, temp, vecCount);

        /* 0xFF or 0xFFFF */
        masm.and(64, result, result, 0xFFFF >>> (16 - (8 * charSizeBytes)));
        masm.and(64, temp, temp, 0xFFFF >>> (16 - (8 * charSizeBytes)));

        if (isUL) {
            /* UL's input has been swapped in AArch64StringUTF16Substitutions.compareToLatin1. */
            masm.sub(64, result, result, temp);
        } else {
            masm.sub(64, result, temp, result);
        }
    }

    private void emitSIMDCode(AArch64MacroAssembler masm, Label stringsEqualUptoLength) {
        /**
         * @formatter:off
         * There are two parts to this implementation:
         * a. Compare two char arrays if equal, similar to (AArch64ArrayIndexOfOp.emitSIMDCode)
         * b. Detect the position of mismatch if arrays are not equal, similar to (AArch64ArrayEqualsOp.emitSIMDCompare)
         * The key difference is that the comparison can include arrays of different encoding, i.e., Latin1 and UTF16.
         * The mixed encoding requires expanding Latin1 chars (1 byte) to UTF16 chars (2 bytes) before comparison. Consequently,
         * we need to take a 16-byte chunk from the Latin1 array and compare it with a 32-byte chunk of the UTF16 array.
         * To simplify the comparison of mixed encoding, the array with Latin1 encoding is always passed in 'array1'.
         * Thus, the order of input arrays is swapped while comparing a UTF16 array to a Latin1. The array-kind parameters
         * maintain the original order of comparison. The comparison order is required to calculate the result value on
         * mismatch. The rest of the steps to compare and detect mismatch are common for comparing arrays of both mixed
         * and same encoding.
         *
         *  1. Get the references that point to the first characters of the first and second array.
         *  2. Read arrays chunk-by-chunk.
         *   2.1 Store end index at the beginning of the last chunk ('lastChunkAddress1'). This ensures that we
         *    don't read beyond the array boundary.
         *   2.2a For the same encoding, read a 32-byte chunk from the first and second array each in two SIMD registers.
         *   2.2b.1 For mixed encoding, read a 16-byte chunk from the first and a 32-byte chunk from the second array.
         *   2.2b.2 Double the width of each char in the first chunk, from 1 to 2 bytes, to get a 32-byte chunk for it.
         *  3. Compare the 32-byte chunks from both arrays.
         *   The XOR between 32-byte chunks sets all bits of the destination register to zero when the source and target
         *   elements are equal. The result of the comparison is now in two SIMD registers.
         *  4. Detect a mismatch by checking if any element of the two SIMD registers is non-zero.
         *   4.1 Combine the result of comparison from Step 3 into one SIMD register by performing logical OR.
         *   4.2 Mismatch is detected if any of the bits in the comparison result is set to non-zero. Thus, find the sum
         *    of all the vector elements and check if it's non-zero. Here, element size doesn't matter as the objective
         *    is to detect a mismatch.
         *   4.3 If no mismatch is found go to Step 7 else go to step 5.
         *  5. Detect the position of mismatch. First, prepare a 64-bit representation of the comparison. Use 2-bits
         *   per byte to represent whether the chars matched (set to 0) or mismatched (set to 1). Second, detect the
         *   mismatching position by calculating the number of trailing zeros in the bit pattern.
         *   5.1 Use the result of XOR from step 3 and set all bits to 1 where characters match.
         *   5.2 Perform AND between the magic constant 0xc030_0c03 and negated result from step 5.1.
         *   5.3 Perform pairwise addition on the two SIMD chunks of 16-bytes (64-bits) each to collapse to a single
         *    8-byte (64-bit) value.
         *   5.4 Reverse the 8-byte result from step 5.3 and count leading zeros that gives the index of the
         *    mismatching byte on dividing it by 2 (as there are 2-bits per byte).
         *  6. When a mismatch is found, we need to return the difference between the mismatching chars.
         *   6.1a For the same encoding, load the chars from the mismatching position and return the result.
         *   6.1b.1 For the mixed encoding, step 2.2b doubled the width of each char in the first array. Thus read the
         *    first char at 'mismatchPos >> 1' while the second char at mismatchPos from their respective arrays.
         *   6.1b.2 If the inputs are swapped (UL comparison) then negate the result from the previous step and return.
         *  7. Repeat the process until the end of the arrays.
         * @formatter:on
         */

        /* Retrieve registers pre-populated in emitCode() */
        Register result = asRegister(resultValue);
        Register array1 = asRegister(temp1);
        Register array2 = asRegister(temp2);
        Register length = asRegister(temp3);
        /* Allocate new temporary registers */
        Register endOfComparison = asRegister(temp4);
        Register lastChunkAddress1 = asRegister(temp5);
        Register lastChunkAddress2 = asRegister(temp6);

        Register array1LowV = asRegister(vectorTemp1);
        Register array1HighV = asRegister(vectorTemp2);
        Register array2LowV = asRegister(vectorTemp3);
        Register array2HighV = asRegister(vectorTemp4);
        Register tmpRegV1 = asRegister(vectorTemp5);
        Register tmpRegV2 = asRegister(vectorTemp6);

        final Label simdLoop = new Label();
        final Label misMatchInChunk = new Label();

        final boolean isSameEncoding = isLL || isUU;
        final int charSizeBytes = isLL ? 1 : 2;
        final int chunkSizeBytes = 32;

        final AArch64ASIMDAssembler.ElementSize eSize = AArch64ASIMDAssembler.ElementSize.fromSize(charSizeBytes * Byte.SIZE);
        /*
         * Calculate the addresses of the last chunk, start and end of comparison. Here, length
         * contains number of bytes to compare from the second string.
         */
        if (isSameEncoding) {
            masm.add(64, endOfComparison, array1, length);
            masm.sub(64, length, length, chunkSizeBytes);
            masm.add(64, lastChunkAddress1, array1, length);
        } else {
            /*
             * For mixed (LU/UL) comparison, chars in the second string are two bytes long compared
             * to a byte long chars in the first string. Therefore, we need to halve the length
             * while calculating the reference addresses for the first string.
             */
            masm.add(64, endOfComparison, array1, length, AArch64Assembler.ShiftType.LSR, 1);
            masm.sub(64, length, length, chunkSizeBytes);
            masm.add(64, lastChunkAddress1, array1, length, AArch64Assembler.ShiftType.LSR, 1);
        }
        masm.add(64, lastChunkAddress2, array2, length);

        masm.align(16);
        masm.bind(simdLoop);
        if (isSameEncoding) {
            masm.fldp(128, array1LowV, array1HighV, AArch64Address.createImmediateAddress(128, AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, array1, 32));
        } else {
            /*
             * For mixed comparison, double the width of each char read from the first string. Read
             * 16 bytes chunk from the first string, take its respective halves, double the width of
             * each char and then store them in two 16 byte vector registers
             */
            masm.fldr(128, array1LowV, AArch64Address.createImmediateAddress(128, AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED, array1, 16));
            masm.neon.uxtl2VV(AArch64ASIMDAssembler.ElementSize.Byte, array1HighV, array1LowV);
            masm.neon.uxtlVV(AArch64ASIMDAssembler.ElementSize.Byte, array1LowV, array1LowV);
        }
        masm.fldp(128, array2LowV, array2HighV, AArch64Address.createImmediateAddress(128, AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, array2, 32));

        /* Compare the 32 byte wide chunks from both strings and detect a mismatch */
        masm.neon.cmeqVVV(AArch64ASIMDAssembler.ASIMDSize.FullReg, eSize, array1LowV, array1LowV, array2LowV);
        masm.neon.cmeqVVV(AArch64ASIMDAssembler.ASIMDSize.FullReg, eSize, array1HighV, array1HighV, array2HighV);
        masm.neon.andVVV(AArch64ASIMDAssembler.ASIMDSize.FullReg, tmpRegV1, array1LowV, array1HighV);
        masm.neon.uminvSV(AArch64ASIMDAssembler.ASIMDSize.FullReg, eSize, tmpRegV1, tmpRegV1);
        masm.fcmpZero(64, tmpRegV1);
        masm.branchConditionally(ConditionFlag.EQ, misMatchInChunk);
        masm.cmp(64, array1, lastChunkAddress1);
        masm.branchConditionally(ConditionFlag.LT, simdLoop);

        masm.cmp(64, array1, endOfComparison);
        masm.branchConditionally(ConditionFlag.GE, stringsEqualUptoLength);
        masm.mov(64, array1, lastChunkAddress1);
        masm.mov(64, array2, lastChunkAddress2);
        masm.jmp(simdLoop);

        /*
         * Detect the mismatch using the mechanism similar to (AArch64ArrayIndexOfOp.emitSIMDCode)
         */
        masm.bind(misMatchInChunk);
        try (AArch64MacroAssembler.ScratchRegister scratchReg = masm.getScratchRegister()) {
            int magicConstant = 0xc030_0c03;
            Register magicConstantReg = scratchReg.getRegister();
            masm.mov(magicConstantReg, magicConstant);
            masm.neon.dupVG(AArch64ASIMDAssembler.ASIMDSize.FullReg, AArch64ASIMDAssembler.ElementSize.Word, tmpRegV2, magicConstantReg);
        }
        masm.neon.bicVVV(AArch64ASIMDAssembler.ASIMDSize.FullReg, array1LowV, tmpRegV2, array1LowV);
        masm.neon.bicVVV(AArch64ASIMDAssembler.ASIMDSize.FullReg, array1HighV, tmpRegV2, array1HighV);
        /* Reduce from 256 -> 128 bits. */
        masm.neon.addpVVV(AArch64ASIMDAssembler.ASIMDSize.FullReg, AArch64ASIMDAssembler.ElementSize.Byte, array1LowV, array1LowV, array1HighV);
        /* Reduce from 128 -> 64 bits. */
        masm.neon.addpVVV(AArch64ASIMDAssembler.ASIMDSize.FullReg, AArch64ASIMDAssembler.ElementSize.Byte, array1LowV, array1LowV, array1HighV);
        masm.neon.moveFromIndex(AArch64ASIMDAssembler.ElementSize.DoubleWord, AArch64ASIMDAssembler.ElementSize.DoubleWord, result, array1LowV, 0);
        masm.rbit(64, result, result);
        masm.clz(64, result, result);
        masm.asr(64, result, result, 1);
        masm.sub(64, result, result, 32);
        if (isSameEncoding) {
            masm.ldr(8 * charSizeBytes, lastChunkAddress1, AArch64Address.createRegisterOffsetAddress(8 * charSizeBytes, array1, result, false));
        } else {
            try (AArch64MacroAssembler.ScratchRegister scratchReg = masm.getScratchRegister()) {
                Register tmpReg = scratchReg.getRegister();
                masm.asr(64, tmpReg, result, 1);
                masm.ldr(8, lastChunkAddress1, AArch64Address.createRegisterOffsetAddress(8, array1, tmpReg, false));
            }
        }
        masm.ldr(8 * charSizeBytes, lastChunkAddress2, AArch64Address.createRegisterOffsetAddress(8 * charSizeBytes, array2, result, false));
        if (isUL) {
            masm.sub(32, result, lastChunkAddress2, lastChunkAddress1);
        } else {
            masm.sub(32, result, lastChunkAddress1, lastChunkAddress2);
        }
    }
}
