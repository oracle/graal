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

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * Emits code which compares two arrays lexicographically. If the CPU supports any vector
 * instructions specialized code is emitted to leverage these instructions.
 *
 * <p>
 * <b>IMPORTANT:</b> If both array kinds are not the same (i.e., one is a Char array and another is
 * a Byte array), then byte array's value and length will be the first passed value and the char
 * array's value and length will be the second passed value; however, even if the inputs are
 * swapped, kind1 and kind2 will be in the original order. See {@code AArch64GraphBuilderPlugins}
 * registerStringLatin1Plugins and registerStringUTF16Plugins for the arrangement of values.
 */
@Opcode("ARRAY_COMPARE_TO")
public final class AArch64ArrayCompareToOp extends AArch64ComplexVectorOp {
    public static final LIRInstructionClass<AArch64ArrayCompareToOp> TYPE = LIRInstructionClass.create(AArch64ArrayCompareToOp.class);

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

    @Temp({REG}) protected Value[] temp;
    @Temp({REG}) protected AllocatableValue[] vectorTemp;

    public AArch64ArrayCompareToOp(LIRGeneratorTool tool, Stride strideA, Stride strideB, Value result,
                    Value arrayA, Value lengthA, Value arrayB, Value lengthB) {
        super(TYPE);

        GraalError.guarantee(arrayA.getPlatformKind() == AArch64Kind.QWORD && arrayA.getPlatformKind() == arrayB.getPlatformKind(), "pointer value expected");
        GraalError.guarantee(lengthA.getPlatformKind() == AArch64Kind.DWORD && lengthA.getPlatformKind() == lengthB.getPlatformKind(), "int value expected");
        GraalError.guarantee(result.getPlatformKind() == AArch64Kind.DWORD, "int value expected");

        GraalError.guarantee(strideA == Stride.S1 || strideA == Stride.S2, "strideA must be S1 or S2");
        GraalError.guarantee(strideB == Stride.S1 || strideB == Stride.S2, "strideB must be S1 or S2");

        this.isLL = (strideA == strideB && strideA == Stride.S1);
        this.isUU = (strideA == strideB && strideA == Stride.S2);
        this.isLU = (strideA != strideB && strideA == Stride.S1);
        this.isUL = (strideA != strideB && strideA == Stride.S2);

        this.resultValue = result;
        this.array1Value = arrayA;
        this.array2Value = arrayB;

        /*
         * The length values are inputs but are also killed like temporaries so need both Use and
         * Temp annotations, which will only work with fixed registers.
         */

        this.length1Value = length1ValueTemp = lengthA;
        this.length2Value = length2ValueTemp = lengthB;

        temp = allocateTempRegisters(tool, 6);
        vectorTemp = allocateVectorRegisters(tool, 5);
    }

    @Override
    protected void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register result = asRegister(resultValue);
        Register length1 = asRegister(length1Value);
        Register length2 = asRegister(length2Value);

        Register array1 = asRegister(temp[0]);
        Register array2 = asRegister(temp[1]);
        Register length = asRegister(temp[2]);

        final Label done = new Label();
        final Label stringsEqualUptoLength = new Label();
        final Label simdImpl = new Label();

        /* Load array base addresses. */
        masm.mov(64, array1, asRegister(array1Value));
        masm.mov(64, array2, asRegister(array2Value));
        masm.prfm(AArch64Address.createBaseRegisterOnlyAddress(64, array1), AArch64Assembler.PrefetchMode.PLDL1STRM);
        masm.prfm(AArch64Address.createBaseRegisterOnlyAddress(64, array2), AArch64Assembler.PrefetchMode.PLDL1STRM);

        /*
         * Originally length1 and length2 are byte lengths. Calculate minimal element length for
         * different kind cases. Conditions could be squashed but let's keep it readable.
         */
        if (!isLL) {
            // length2 must be a UTF16
            masm.lsr(64, length2, length2, 1);
        }

        if (isUU) {
            // length1 is also a UTF16
            masm.lsr(64, length1, length1, 1);
        }

        /* Get length of the smaller string */
        masm.cmp(32, length1, length2);
        masm.csel(32, length, length1, length2, ConditionFlag.LT);

        /* One of strings is empty */
        masm.cbz(64, length, stringsEqualUptoLength);

        /*
         * Go back to minimal byte length.
         */
        if (!isLL) {
            masm.lsl(64, length, length, 1);
        }

        int simdByteThreshold = 32;
        masm.compare(64, length, simdByteThreshold);
        masm.branchConditionally(ConditionFlag.GE, simdImpl);

        /* Scalar Implementation */
        emitScalarCode(masm, stringsEqualUptoLength, done);
        masm.jmp(done);

        /* SIMD Implementation */
        masm.bind(simdImpl);
        emitSIMDCode(masm, stringsEqualUptoLength);
        masm.jmp(done);

        /* Strings are equal up to length, return length difference in chars. */
        masm.bind(stringsEqualUptoLength);
        if (isUL) {
            /* The inputs have been swapped. */
            masm.sub(32, result, length2, length1);
        } else {
            masm.sub(32, result, length1, length2);
        }

        masm.bind(done);
    }

    private void emitScalarCode(AArch64MacroAssembler masm, Label stringsEqualUptoLength, Label done) {
        /* Retrieve registers pre-populated in emitCode() */
        Register result = asRegister(resultValue);
        Register array1 = asRegister(temp[0]);
        Register array2 = asRegister(temp[1]);
        Register byteLength = asRegister(temp[2]);
        /* Allocate new temporary registers */
        Register tmp = asRegister(temp[3]);
        Register remainingBytes = asRegister(temp[4]);

        final Label charSearchLoop = new Label();

        final int elementByteSize = isLL ? 1 : 2;
        final int elementBitSize = elementByteSize * Byte.SIZE;

        masm.mov(64, remainingBytes, byteLength);

        masm.align(AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT);
        masm.bind(charSearchLoop);
        if (isLU || isUL) {
            // first input is a byte array
            masm.ldr(Byte.SIZE, tmp, AArch64Address.createImmediateAddress(Byte.SIZE, AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED, array1, Byte.BYTES));
        } else {
            masm.ldr(elementBitSize, tmp, AArch64Address.createImmediateAddress(elementBitSize, AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED, array1, elementByteSize));
        }
        masm.ldr(elementBitSize, result, AArch64Address.createImmediateAddress(elementBitSize, AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED, array2, elementByteSize));
        if (isUL) {
            /* The inputs have been swapped. */
            masm.subs(32, result, result, tmp);
        } else {
            masm.subs(32, result, tmp, result);
        }
        masm.branchConditionally(ConditionFlag.NE, done);
        masm.subs(64, remainingBytes, remainingBytes, elementByteSize);
        masm.branchConditionally(ConditionFlag.EQ, stringsEqualUptoLength);
        masm.jmp(charSearchLoop);
    }

    private void emitSIMDCode(AArch64MacroAssembler masm, Label stringsEqualUptoLength) {
        /*
         * @formatter:off
         * There are two parts to this implementation:
         * a. Compare two char arrays if equal, similar to (AArch64ArrayIndexOfOp.emitSIMDCode)
         * b. Detect the position of mismatch if arrays are not equal
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
         *  3. Compare the 32-byte chunks from both arrays and detect a mismatch.
         *   3.1 Perform an element-wise compare equal comparison
         *   3.2 Mismatch is detected if any of the bits are zero
         *   3.3 If no mismatch is found go to Step 6 else go to step 4.
         *  4. Detect the position of mismatch. First, prepare a 64-bit representation of the comparison. Use 2-bits
         *   per byte to represent whether the chars matched (set to 0) or mismatched (set to 1). Second, detect the
         *   mismatching position by calculating the number of trailing zeros in the bit pattern.
         *   4.1 Use the result of XOR from step 3 and set all bits to 1 where characters match.
         *   4.2 Perform AND between the magic constant 0xc030_0c03 and negated result from step 5.1.
         *   4.3 Perform pairwise addition on the two SIMD chunks of 16-bytes (64-bits) each to collapse to a single
         *    8-byte (64-bit) value.
         *   4.4 Reverse the 8-byte result from step 5.3 and count leading zeros that gives the index of the
         *    mismatching byte on dividing it by 2 (as there are 2-bits per byte).
         *  5. When a mismatch is found, we need to return the difference between the mismatching chars.
         *   5.1a For the same encoding, load the chars from the mismatching position and return the result.
         *   5.1b.1 For the mixed encoding, step 2.2b doubled the width of each char in the first array. Thus read the
         *    first char at 'mismatchPos >> 1' while the second char at mismatchPos from their respective arrays.
         *   5.1b.2 If the inputs are swapped (UL comparison) then negate the result from the previous step and return.
         *  6. Repeat the process until the end of the arrays.
         * @formatter:on
         */

        /* Retrieve registers pre-populated in emitCode() */
        Register result = asRegister(resultValue);
        Register array1 = asRegister(temp[0]);
        Register array2 = asRegister(temp[1]);
        Register byteLength = asRegister(temp[2]);
        /* Allocate new temporary registers */
        Register endOfComparison = asRegister(temp[3]);
        Register lastChunkAddress1 = asRegister(temp[4]);
        Register lastChunkAddress2 = asRegister(temp[5]);

        Register array1LowV = asRegister(vectorTemp[0]);
        Register array1HighV = asRegister(vectorTemp[1]);
        Register array2LowV = asRegister(vectorTemp[2]);
        Register array2HighV = asRegister(vectorTemp[3]);
        Register tmpRegV1 = asRegister(vectorTemp[4]);

        final Label simdLoop = new Label();
        final Label mismatchInChunk = new Label();

        final boolean isSameEncoding = isLL || isUU;
        final int elementBitSize = isLL ? Byte.SIZE : Character.SIZE;
        final int chunkByteSize = 32;

        final AArch64ASIMDAssembler.ElementSize eSize = AArch64ASIMDAssembler.ElementSize.fromSize(elementBitSize);
        /*
         * Calculate the addresses of the last chunk, start and end of comparison. Here, length
         * contains number of bytes to compare from the second string.
         */
        if (isSameEncoding) {
            masm.add(64, endOfComparison, array1, byteLength);
            masm.sub(64, byteLength, byteLength, chunkByteSize);
            masm.add(64, lastChunkAddress1, array1, byteLength);
            masm.add(64, lastChunkAddress2, array2, byteLength);
        } else {
            /*
             * For mixed (LU/UL) comparison, array1 has byte-sized elements and array2 has
             * char-sized elements. Therefore, we need to halve the length while calculating the
             * reference addresses for the first string.
             */
            masm.add(64, endOfComparison, array1, byteLength, AArch64Assembler.ShiftType.LSR, 1);
            masm.sub(64, byteLength, byteLength, chunkByteSize);
            masm.add(64, lastChunkAddress1, array1, byteLength, AArch64Assembler.ShiftType.LSR, 1);
            masm.add(64, lastChunkAddress2, array2, byteLength);
        }

        masm.align(AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT);
        masm.bind(simdLoop);
        /* 2. Read arrays chunk-by-chunk */
        // load elements from array1
        if (isSameEncoding) {
            masm.fldp(128, array1LowV, array1HighV, AArch64Address.createImmediateAddress(128, AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, array1, chunkByteSize));
        } else {
            /*
             * For mixed comparison, double the width of each char read from the first string. Read
             * 16 bytes chunk from the first string, take its respective halves, double the width of
             * each char and then store them in two 16 byte vector registers
             */
            masm.fldr(128, array1LowV, AArch64Address.createImmediateAddress(128, AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED, array1, chunkByteSize / 2));
            masm.neon.uxtl2VV(AArch64ASIMDAssembler.ElementSize.Byte, array1HighV, array1LowV);
            masm.neon.uxtlVV(AArch64ASIMDAssembler.ElementSize.Byte, array1LowV, array1LowV);
        }
        // load elements from array2
        masm.fldp(128, array2LowV, array2HighV, AArch64Address.createImmediateAddress(128, AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, array2, chunkByteSize));

        /* 3. Compare the 32 byte wide chunks from both strings and detect a mismatch */
        masm.neon.cmeqVVV(AArch64ASIMDAssembler.ASIMDSize.FullReg, eSize, array1LowV, array1LowV, array2LowV);
        masm.neon.cmeqVVV(AArch64ASIMDAssembler.ASIMDSize.FullReg, eSize, array1HighV, array1HighV, array2HighV);
        masm.neon.andVVV(AArch64ASIMDAssembler.ASIMDSize.FullReg, tmpRegV1, array1LowV, array1HighV);
        masm.neon.uminvSV(AArch64ASIMDAssembler.ASIMDSize.FullReg, eSize, tmpRegV1, tmpRegV1);
        masm.fcmpZero(64, tmpRegV1);
        masm.branchConditionally(ConditionFlag.EQ, mismatchInChunk);
        masm.cmp(64, array1, lastChunkAddress1);
        masm.branchConditionally(ConditionFlag.LO, simdLoop);

        masm.cmp(64, array1, endOfComparison);
        masm.branchConditionally(ConditionFlag.HS, stringsEqualUptoLength);
        masm.mov(64, array1, lastChunkAddress1);
        masm.mov(64, array2, lastChunkAddress2);
        masm.jmp(simdLoop);

        /*
         * 4. Detect the mismatch using the mechanism similar to
         * (AArch64ArrayIndexOfOp.emitSIMDCode)
         */
        masm.bind(mismatchInChunk);
        try (AArch64MacroAssembler.ScratchRegister scratchReg = masm.getScratchRegister()) {
            Register tmp = scratchReg.getRegister();
            initCalcIndexOfFirstMatchMask(masm, tmpRegV1, tmp);
            calcIndexOfFirstMatch(masm, result, array1LowV, array1HighV, tmpRegV1, true);
        }
        masm.asr(64, result, result, 1);
        // subtract chunkSizeBytes to account for the post index of each of the array addresses
        masm.sub(64, result, result, chunkByteSize);
        // now result holds the first index of mismatch
        // load address1
        if (isSameEncoding) {
            masm.ldr(elementBitSize, lastChunkAddress1, AArch64Address.createRegisterOffsetAddress(elementBitSize, array1, result, false));
        } else {
            // the index needs to be halved for the byte array
            try (AArch64MacroAssembler.ScratchRegister scratchReg = masm.getScratchRegister()) {
                Register tmpReg = scratchReg.getRegister();
                masm.asr(64, tmpReg, result, 1);
                masm.ldr(Byte.SIZE, lastChunkAddress1, AArch64Address.createRegisterOffsetAddress(Byte.SIZE, array1, tmpReg, false));
            }
        }
        // load address2
        masm.ldr(elementBitSize, lastChunkAddress2, AArch64Address.createRegisterOffsetAddress(elementBitSize, array2, result, false));
        if (isUL) {
            masm.sub(32, result, lastChunkAddress2, lastChunkAddress1);
        } else {
            masm.sub(32, result, lastChunkAddress1, lastChunkAddress2);
        }
    }
}
