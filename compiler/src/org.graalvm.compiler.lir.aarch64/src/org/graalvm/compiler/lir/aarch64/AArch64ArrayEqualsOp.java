/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

/**
 * Emits code which compares two arrays of the same length. If the CPU supports any vector
 * instructions specialized code is emitted to leverage these instructions.
 */
@Opcode("ARRAY_EQUALS")
public final class AArch64ArrayEqualsOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64ArrayEqualsOp> TYPE = LIRInstructionClass.create(AArch64ArrayEqualsOp.class);

    private final JavaKind kind;
    private final int array1BaseOffset;
    private final int array2BaseOffset;
    private final int arrayIndexScale;

    @Def({REG}) protected Value resultValue;
    @Alive({REG}) protected Value array1Value;
    @Alive({REG}) protected Value array2Value;
    @Alive({REG}) protected Value lengthValue;
    @Temp({REG}) protected Value temp1;
    @Temp({REG}) protected Value temp2;
    @Temp({REG}) protected Value temp3;
    @Temp({REG}) protected Value temp4;

    @Temp({REG}) protected AllocatableValue vectorTemp1;
    @Temp({REG}) protected AllocatableValue vectorTemp2;
    @Temp({REG}) protected AllocatableValue vectorTemp3;
    @Temp({REG}) protected AllocatableValue vectorTemp4;

    public AArch64ArrayEqualsOp(LIRGeneratorTool tool, JavaKind kind, int array1BaseOffset, int array2BaseOffset, Value result, Value array1, Value array2, Value length, boolean directPointers) {
        super(TYPE);

        assert !kind.isNumericFloat() : "Float arrays comparison (bitwise_equal || both_NaN) isn't supported";
        this.kind = kind;

        /*
         * The arrays are expected to have the same kind and thus the same index scale. For
         * primitive arrays, this will mean the same array base offset as well; but if we compare a
         * regular array with a hybrid object, they may have two different offsets.
         */
        this.array1BaseOffset = directPointers ? 0 : array1BaseOffset;
        this.array2BaseOffset = directPointers ? 0 : array2BaseOffset;
        this.arrayIndexScale = tool.getProviders().getMetaAccess().getArrayIndexScale(kind);

        this.resultValue = result;
        this.array1Value = array1;
        this.array2Value = array2;
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

            // Get array length in bytes.
            int shiftAmt = NumUtil.log2Ceil(arrayIndexScale);
            masm.lsl(64, byteArrayLength, asRegister(lengthValue), shiftAmt);
            masm.compare(32, asRegister(lengthValue), 32 / arrayIndexScale);
            masm.branchConditionally(ConditionFlag.LS, scalarCompare);

            emitSimdCompare(masm, byteArrayLength, hasMismatch, scratch, breakLabel);

            masm.bind(scalarCompare);
            emitScalarCompare(masm, byteArrayLength, hasMismatch, scratch, breakLabel);

            // Return: hasMismatch is non-zero iff the arrays differ
            masm.bind(breakLabel);
            masm.cmp(64, hasMismatch, zr);
            masm.cset(resultValue.getPlatformKind().getSizeInBytes() * Byte.SIZE, asRegister(resultValue), ConditionFlag.EQ);
        }
    }

    private void emitScalarCompare(AArch64MacroAssembler masm, Register byteArrayLength, Register hasMismatch, Register scratch, Label breakLabel) {
        Register array1 = asRegister(temp2);
        Register array2 = asRegister(temp3);

        // Load array base addresses.
        masm.add(64, array1, asRegister(array1Value), array1BaseOffset);
        masm.add(64, array2, asRegister(array2Value), array2BaseOffset);
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
     *
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
        masm.align(16);
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

        if (kind.getByteCount() <= 4) {
            // Compare trailing 4 bytes, if any.
            masm.ands(32, zr, result, 4);
            masm.branchConditionally(ConditionFlag.EQ, compare2Bytes);
            masm.ldr(32, temp, AArch64Address.createImmediateAddress(32, AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED, array1, 4));
            masm.ldr(32, rscratch1, AArch64Address.createImmediateAddress(32, AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED, array2, 4));
            masm.eor(32, rscratch1, temp, rscratch1);
            masm.cbnz(32, rscratch1, breakLabel);

            if (kind.getByteCount() <= 2) {
                // Compare trailing 2 bytes, if any.
                masm.bind(compare2Bytes);
                masm.ands(32, zr, result, 2);
                masm.branchConditionally(ConditionFlag.EQ, compare1Byte);
                masm.ldr(16, temp, AArch64Address.createImmediateAddress(16, AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED, array1, 2));
                masm.ldr(16, rscratch1, AArch64Address.createImmediateAddress(16, AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED, array2, 2));
                masm.eor(32, rscratch1, temp, rscratch1);
                masm.cbnz(32, rscratch1, breakLabel);

                // The one-byte tail compare is only required for boolean and byte arrays.
                if (kind.getByteCount() <= 1) {
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

    private void emitSimdCompare(AArch64MacroAssembler masm, Register byteArrayLength, Register hasMismatch, Register scratch, Label endLabel) {
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

        /* Set 'array1Address' and 'array2Address' to point to start of arrays. */
        masm.add(64, array1Address, asRegister(array1Value), array1BaseOffset);
        masm.add(64, array2Address, asRegister(array2Value), array2BaseOffset);
        /*
         * 2.1 Set endOfArray1 pointing to byte next to the last valid element in array1 and
         * 'refAddress1' pointing to the beginning of the last chunk.
         */
        masm.add(64, endOfArray1, array1Address, byteArrayLength);
        masm.bic(64, refAddress1, endOfArray1, 31L);

        masm.align(16);
        masm.bind(compareByChunkHead);
        masm.cmp(64, refAddress1, array1Address);
        masm.branchConditionally(ConditionFlag.LE, processTail);
        masm.bind(compareByChunkTail);

        masm.fldp(128, array1Part1RegV, array1Part2RegV, AArch64Address.createImmediateAddress(128, AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, array1Address, 32));
        masm.fldp(128, array2Part1RegV, array2Part2RegV, AArch64Address.createImmediateAddress(128, AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, array2Address, 32));
        /* 3. Compare arrays in the 32-byte chunk */
        masm.neon.cmeqVVV(AArch64ASIMDAssembler.ASIMDSize.FullReg, AArch64ASIMDAssembler.ElementSize.DoubleWord, array1Part1RegV, array1Part1RegV, array2Part1RegV);
        masm.neon.cmeqVVV(AArch64ASIMDAssembler.ASIMDSize.FullReg, AArch64ASIMDAssembler.ElementSize.DoubleWord, array1Part2RegV, array1Part2RegV, array2Part2RegV);
        /* Determining if they are identical. */
        /* Combine two registers into 1 register */
        masm.neon.andVVV(AArch64ASIMDAssembler.ASIMDSize.FullReg, array1Part1RegV, array1Part1RegV, array1Part2RegV);
        /* Reduce 128-bit value to 64-bit value */
        masm.neon.xtnVV(AArch64ASIMDAssembler.ElementSize.Word, array1Part1RegV, array1Part1RegV);
        /* If ~value != 0, then there is a mismatch somewhere. */
        masm.neon.moveFromIndex(AArch64ASIMDAssembler.ElementSize.DoubleWord, AArch64ASIMDAssembler.ElementSize.DoubleWord, hasMismatch, array1Part1RegV, 0);
        masm.neg(64, hasMismatch, hasMismatch);
        /* if there is mismatch, then no more searching is necessary */
        masm.cbnz(64, hasMismatch, endLabel);

        /* No mismatch; jump to next loop iteration. */
        masm.bic(64, array1Address, array1Address, 31);
        masm.bic(64, array2Address, array2Address, 31);
        masm.jmp(compareByChunkHead);

        masm.align(16);
        masm.bind(processTail);
        masm.cmp(64, array1Address, endOfArray1);
        masm.branchConditionally(ConditionFlag.GE, endLabel);
        /* adjust array1Address and array2Address to access last 32 bytes. */
        masm.sub(64, array1Address, endOfArray1, 32);
        masm.add(64, array2Address, asRegister(array2Value), array2BaseOffset - 32);
        masm.add(64, array2Address, array2Address, byteArrayLength);
        /*
         * Move back the 'endOfArray1' by 32-bytes because at the end of 'compareByChunkLoopTail',
         * the 'chunkToRead' would be reset to 32-byte aligned addressed. Thus, the
         * compareByChunkLoopHead would never using 'array1Address' >= 'endOfArray1' condition.
         */
        masm.mov(64, endOfArray1, array1Address);
        masm.jmp(compareByChunkTail);
    }
}
