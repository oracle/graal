/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * Emits code which compares two arrays of the same length. If the CPU supports any vector
 * instructions specialized code is emitted to leverage these instructions.
 */
@Opcode("ARRAY_EQUALS")
public final class AArch64ArrayEqualsOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64ArrayEqualsOp> TYPE = LIRInstructionClass.create(AArch64ArrayEqualsOp.class);

    private final Stride stride;

    @Def({REG}) protected Value resultValue;
    @Alive({REG}) protected Value array1Value;
    @Alive({REG, ILLEGAL}) protected Value offset1Value;
    @Alive({REG}) protected Value array2Value;
    @Alive({REG, ILLEGAL}) protected Value offset2Value;
    @Alive({REG}) protected Value lengthValue;
    @Temp({REG}) protected Value temp1;
    @Temp({REG}) protected Value temp2;
    @Temp({REG}) protected Value temp3;
    @Temp({REG}) protected Value temp4;

    @Temp({REG}) protected AllocatableValue vectorTemp1;
    @Temp({REG}) protected AllocatableValue vectorTemp2;
    @Temp({REG}) protected AllocatableValue vectorTemp3;
    @Temp({REG}) protected AllocatableValue vectorTemp4;

    public AArch64ArrayEqualsOp(LIRGeneratorTool tool, Stride stride, Value result, Value array1, Value offset1, Value array2, Value offset2, Value length) {
        super(TYPE);
        this.stride = stride;

        assert result.getPlatformKind() == AArch64Kind.DWORD;
        assert array1.getPlatformKind() == AArch64Kind.QWORD && array1.getPlatformKind() == array2.getPlatformKind();
        assert offset1 == null || offset1.getPlatformKind() == AArch64Kind.QWORD;
        assert offset2 == null || offset2.getPlatformKind() == AArch64Kind.QWORD;
        assert length.getPlatformKind() == AArch64Kind.DWORD;

        this.resultValue = result;
        this.array1Value = array1;
        this.offset1Value = offset1 == null ? Value.ILLEGAL : offset1;
        this.array2Value = array2;
        this.offset2Value = offset2 == null ? Value.ILLEGAL : offset2;
        this.lengthValue = length;

        // Allocate some temporaries.
        LIRKind archWordKind = LIRKind.value(tool.target().arch.getWordKind());
        this.temp1 = tool.newVariable(archWordKind);
        this.temp2 = tool.newVariable(archWordKind);
        this.temp3 = tool.newVariable(archWordKind);
        this.temp4 = tool.newVariable(archWordKind);

        LIRKind vectorKind = LIRKind.value(tool.target().arch.getLargestStorableKind(SIMD));
        vectorTemp1 = tool.newVariable(vectorKind);
        vectorTemp2 = tool.newVariable(vectorKind);
        vectorTemp3 = tool.newVariable(vectorKind);
        vectorTemp4 = tool.newVariable(vectorKind);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register byteArrayLength = asRegister(temp1);

        try (ScratchRegister sc1 = masm.getScratchRegister(); ScratchRegister sc2 = masm.getScratchRegister()) {
            Label breakLabel = new Label();
            Label scalarCompare = new Label();
            Register hasMismatch = sc1.getRegister();
            Register scratch = sc2.getRegister();

            // Get array length in bytes and store as a 64-bit value.
            masm.mov(32, byteArrayLength, asRegister(lengthValue));
            masm.lsl(64, byteArrayLength, byteArrayLength, stride.log2);
            masm.compare(32, asRegister(lengthValue), 32 / stride.value);
            masm.branchConditionally(ConditionFlag.LE, scalarCompare);

            emitSIMDCompare(masm, byteArrayLength, hasMismatch, scratch, breakLabel);

            masm.bind(scalarCompare);
            emitScalarCompare(masm, byteArrayLength, hasMismatch, scratch, breakLabel);

            // Return: hasMismatch is non-zero iff the arrays differ
            masm.bind(breakLabel);
            masm.cmp(64, hasMismatch, zr);
            masm.cset(32, asRegister(resultValue), ConditionFlag.EQ);
        }
    }

    private void loadArrayStart(AArch64MacroAssembler masm, Register array1, Register array2) {
        // address start = pointer + offset
        masm.add(64, array1, asRegister(array1Value), asRegister(offset1Value));
        // address start = pointer + offset
        masm.add(64, array2, asRegister(array2Value), asRegister(offset2Value));

    }

    private void emitScalarCompare(AArch64MacroAssembler masm, Register byteArrayLength, Register hasMismatch, Register scratch, Label breakLabel) {
        Register array1 = asRegister(temp2);
        Register array2 = asRegister(temp3);

        // Load array base addresses.
        loadArrayStart(masm, array1, array2);
        masm.mov(64, scratch, byteArrayLength); // copy

        emit8ByteCompare(masm, scratch, array1, array2, byteArrayLength, breakLabel, hasMismatch);
        emitTailCompares(masm, scratch, array1, array2, breakLabel, hasMismatch);
    }

    /**
     * Vector size used in {@link #emit8ByteCompare}.
     */
    private static final int VECTOR_SIZE = 8;

    /**
     * Emits code that uses 8-byte vector compares.
     */
    private void emit8ByteCompare(AArch64MacroAssembler masm, Register result, Register array1, Register array2, Register length, Label breakLabel, Register rscratch1) {
        Label loop = new Label();
        Label compareTail = new Label();

        Register temp = asRegister(temp4);

        masm.and(64, result, result, VECTOR_SIZE - 1); // tail count (in bytes)
        masm.ands(64, length, length, ~(VECTOR_SIZE - 1));  // vector count (in bytes)
        masm.branchConditionally(ConditionFlag.EQ, compareTail);

        masm.add(64, array1, array1, length);
        masm.add(64, array2, array2, length);
        masm.sub(64, length, zr, length);

        // Align the main loop
        masm.align(AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT);
        masm.bind(loop);
        masm.ldr(64, temp, AArch64Address.createRegisterOffsetAddress(64, array1, length, false));
        masm.ldr(64, rscratch1, AArch64Address.createRegisterOffsetAddress(64, array2, length, false));
        masm.eor(64, rscratch1, temp, rscratch1);
        masm.cbnz(64, rscratch1, breakLabel);
        masm.add(64, length, length, VECTOR_SIZE);
        masm.cbnz(64, length, loop);

        masm.cbz(64, result, breakLabel);

        /*
         * Compare the remaining bytes with an unaligned memory load aligned to the end of the
         * array.
         */
        masm.add(64, array1, array1, -VECTOR_SIZE);
        masm.add(64, array2, array2, -VECTOR_SIZE);
        masm.ldr(64, temp, AArch64Address.createRegisterOffsetAddress(64, array1, result, false));
        masm.ldr(64, rscratch1, AArch64Address.createRegisterOffsetAddress(64, array2, result, false));
        masm.eor(64, rscratch1, temp, rscratch1);
        masm.jmp(breakLabel);

        masm.bind(compareTail);
    }

    /**
     * Emits code to compare the remaining 1 to 4 bytes.
     *
     */
    private void emitTailCompares(AArch64MacroAssembler masm, Register result, Register array1, Register array2, Label breakLabel, Register rscratch1) {
        Label compare2Bytes = new Label();
        Label compare1Byte = new Label();
        Label end = new Label();

        Register temp = asRegister(temp4);

        if (stride.value <= 4) {
            // Compare trailing 4 bytes, if any.
            masm.ands(32, zr, result, 4);
            masm.branchConditionally(ConditionFlag.EQ, compare2Bytes);
            masm.ldr(32, temp, AArch64Address.createImmediateAddress(32, AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED, array1, 4));
            masm.ldr(32, rscratch1, AArch64Address.createImmediateAddress(32, AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED, array2, 4));
            masm.eor(32, rscratch1, temp, rscratch1);
            masm.cbnz(32, rscratch1, breakLabel);

            if (stride.value <= 2) {
                // Compare trailing 2 bytes, if any.
                masm.bind(compare2Bytes);
                masm.ands(32, zr, result, 2);
                masm.branchConditionally(ConditionFlag.EQ, compare1Byte);
                masm.ldr(16, temp, AArch64Address.createImmediateAddress(16, AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED, array1, 2));
                masm.ldr(16, rscratch1, AArch64Address.createImmediateAddress(16, AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED, array2, 2));
                masm.eor(32, rscratch1, temp, rscratch1);
                masm.cbnz(32, rscratch1, breakLabel);

                // The one-byte tail compare is only required for boolean and byte arrays.
                if (stride.value <= 1) {
                    // Compare trailing byte, if any.
                    masm.bind(compare1Byte);
                    masm.ands(32, zr, result, 1);
                    masm.branchConditionally(ConditionFlag.EQ, end);
                    masm.ldr(8, temp, AArch64Address.createBaseRegisterOnlyAddress(8, array1));
                    masm.ldr(8, rscratch1, AArch64Address.createBaseRegisterOnlyAddress(8, array2));
                    masm.eor(32, rscratch1, temp, rscratch1);
                    masm.cbnz(32, rscratch1, breakLabel);
                } else {
                    masm.bind(compare1Byte);
                }
            } else {
                masm.bind(compare2Bytes);
            }
        }
        masm.bind(end);
        masm.mov(64, rscratch1, zr);
    }

    /**
     * This implementation is similar to (AArch64ArrayIndexOfOp.emitSIMDCompare). The main
     * difference is that it is only necessary to find any mismatch, not a match. In the case of a
     * mismatch, the loop is exited immediately. To ensure accesses on array1 are aligned, the first
     * loop iteration is peeled.
     *
     * @formatter:off
     *  1. Get the references that point to the first characters of the source and target array.
     *  2. Read and compare array chunk-by-chunk.
     *   2.1 Store end index at the beginning of the last chunk ('refAddress1'). This ensures that we
     *   don't read beyond the array boundary.
     *   2.2 Read a 32-byte chunk from source and target array each in two SIMD registers.
     *  3. Compare the 32-byte chunks from both arrays. Comparison sets all bits of the destination register
     *  when the source and target elements are equal. The result of the comparison is now in two SIMD registers.
     *  4. Detect a mismatch by checking if any element of the two SIMD registers is zero.
     *   4.1 Combine the result of comparison from Step 3 into one SIMD register by performing logical AND.
     *   4.2 Mismatch is detected if any of the bits in the comparison result is unset. Thus, find the minimum
     *   element across the vector. Here, element size doesn't matter as the objective is to detect a mismatch.
     *   4.3 Read the minimum value from Step 4.2 and sign-extend it to 8 bytes. There is a mismatch,
     *   if the result != 0xFFFFFFFFFFFFFFFF or (result+1) != 0.
     *  5. Repeat the process until the end of the arrays.
     * @formatter:on
     */
    private void emitSIMDCompare(AArch64MacroAssembler masm, Register byteArrayLength, Register hasMismatch, Register scratch, Label endLabel) {
        Register array1Address = asRegister(temp2);
        Register array2Address = asRegister(temp3);
        Register refAddress1 = asRegister(temp4);
        Register endOfArray1 = scratch;

        Register array1Part1RegV = asRegister(vectorTemp1);
        Register array1Part2RegV = asRegister(vectorTemp2);
        Register array2Part1RegV = asRegister(vectorTemp3);
        Register array2Part2RegV = asRegister(vectorTemp4);

        Label compareByChunkHead = new Label();
        Label compareByChunkTail = new Label();
        Label processTail = new Label();

        /* 1. Set 'array1Address' and 'array2Address' to point to start of arrays. */
        loadArrayStart(masm, array1Address, array2Address);
        /*
         * 2.1 Set endOfArray1 pointing to byte next to the last valid element in array1 and
         * 'refAddress1' pointing to the beginning of the last chunk.
         */
        masm.add(64, endOfArray1, array1Address, byteArrayLength);
        masm.sub(64, refAddress1, endOfArray1, 32);

        /*
         * **********************************
         *
         * START PEELED FIRST LOOP ITERATION.
         *
         * **********************************
         */

        /* 2.2 Read a 32-byte chunk from source and target array each in two SIMD registers. */
        masm.fldp(128, array1Part1RegV, array1Part2RegV, AArch64Address.createImmediateAddress(128, AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, array1Address, 32));
        masm.fldp(128, array2Part1RegV, array2Part2RegV, AArch64Address.createImmediateAddress(128, AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, array2Address, 32));
        /* 3. Compare arrays in the 32-byte chunk */
        masm.neon.cmeqVVV(AArch64ASIMDAssembler.ASIMDSize.FullReg, AArch64ASIMDAssembler.ElementSize.DoubleWord, array1Part1RegV, array1Part1RegV, array2Part1RegV);
        masm.neon.cmeqVVV(AArch64ASIMDAssembler.ASIMDSize.FullReg, AArch64ASIMDAssembler.ElementSize.DoubleWord, array1Part2RegV, array1Part2RegV, array2Part2RegV);
        /* 4. Determining if they are identical. */
        /* 4.1 Combine two registers into 1 register */
        masm.neon.andVVV(AArch64ASIMDAssembler.ASIMDSize.FullReg, array1Part1RegV, array1Part1RegV, array1Part2RegV);
        /* 4.2 Find the minimum value across the vector */
        masm.neon.uminvSV(AArch64ASIMDAssembler.ASIMDSize.FullReg, AArch64ASIMDAssembler.ElementSize.Word, array1Part1RegV, array1Part1RegV);
        /* 4.3 If hasMismatch + 1 != 0, then there is a mismatch somewhere. */
        masm.neon.moveFromIndex(AArch64ASIMDAssembler.ElementSize.DoubleWord, AArch64ASIMDAssembler.ElementSize.Word, hasMismatch, array1Part1RegV, 0);
        masm.add(64, hasMismatch, hasMismatch, 1);
        /* If there is mismatch, then no more searching is necessary */
        masm.cbnz(64, hasMismatch, endLabel);

        /* 5. No mismatch; proceed to next loop iteration. */
        /*
         * Extra first loop iteration step: align array1 to a 32-byte boundary.
         *
         * Determine how much to subtract from array2Address to match aligned array1Address. Using
         * the result register as a temporary.
         */
        Register array1Alignment = asRegister(resultValue);
        masm.and(64, array1Alignment, array1Address, 31);
        masm.sub(64, array2Address, array2Address, array1Alignment);
        masm.bic(64, array1Address, array1Address, 31);

        /*
         * ********************************
         *
         * END PEELED FIRST LOOP ITERATION.
         *
         * ********************************
         */

        masm.align(AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT);
        masm.bind(compareByChunkHead);
        masm.cmp(64, refAddress1, array1Address);
        masm.branchConditionally(ConditionFlag.LO, processTail);
        masm.bind(compareByChunkTail);

        /* 2.2 Read a 32-byte chunk from source and target array each in two SIMD registers. */
        masm.fldp(128, array1Part1RegV, array1Part2RegV, AArch64Address.createImmediateAddress(128, AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, array1Address, 32));
        masm.fldp(128, array2Part1RegV, array2Part2RegV, AArch64Address.createImmediateAddress(128, AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, array2Address, 32));
        /* 3. Compare arrays in the 32-byte chunk */
        masm.neon.cmeqVVV(AArch64ASIMDAssembler.ASIMDSize.FullReg, AArch64ASIMDAssembler.ElementSize.DoubleWord, array1Part1RegV, array1Part1RegV, array2Part1RegV);
        masm.neon.cmeqVVV(AArch64ASIMDAssembler.ASIMDSize.FullReg, AArch64ASIMDAssembler.ElementSize.DoubleWord, array1Part2RegV, array1Part2RegV, array2Part2RegV);
        /* 4. Determining if they are identical. */
        /* 4.1 Combine two registers into 1 register */
        masm.neon.andVVV(AArch64ASIMDAssembler.ASIMDSize.FullReg, array1Part1RegV, array1Part1RegV, array1Part2RegV);
        /* 4.2 Find the minimum value across the vector */
        masm.neon.uminvSV(AArch64ASIMDAssembler.ASIMDSize.FullReg, AArch64ASIMDAssembler.ElementSize.Word, array1Part1RegV, array1Part1RegV);
        /* 4.3 If hasMismatch + 1 != 0, then there is a mismatch somewhere. */
        masm.neon.moveFromIndex(AArch64ASIMDAssembler.ElementSize.DoubleWord, AArch64ASIMDAssembler.ElementSize.Word, hasMismatch, array1Part1RegV, 0);
        masm.add(64, hasMismatch, hasMismatch, 1);
        /* 5. No mismatch; jump to next loop iteration. */
        masm.cbz(64, hasMismatch, compareByChunkHead);
        /* If there is mismatch, then no more searching is necessary */
        masm.jmp(endLabel);

        masm.align(AArch64MacroAssembler.PREFERRED_BRANCH_TARGET_ALIGNMENT);
        masm.bind(processTail);
        masm.cmp(64, array1Address, endOfArray1);
        masm.branchConditionally(ConditionFlag.HS, endLabel);
        /* Adjust array1Address and array2Address to access last 32 bytes. */
        masm.mov(64, array1Address, refAddress1);
        if (!offset2Value.equals(Value.ILLEGAL)) {
            masm.add(64, array2Address, asRegister(array2Value), asRegister(offset2Value));
            masm.sub(64, array2Address, array2Address, 32);
        } else {
            masm.sub(64, array2Address, asRegister(array2Value), 32);
        }
        masm.add(64, array2Address, array2Address, byteArrayLength);
        masm.jmp(compareByChunkTail);
    }
}
