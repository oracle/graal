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

import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isIllegal;
import static org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.PREFERRED_BRANCH_TARGET_ALIGNMENT;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import java.util.Arrays;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Address.AddressingMode;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ShiftType;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.core.common.StrideUtil;
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
 * Emits code which compares two arrays of the same length. If the CPU supports any vector
 * instructions specialized code is emitted to leverage these instructions.
 */
@Opcode("ARRAY_EQUALS")
public final class AArch64ArrayEqualsOp extends AArch64ComplexVectorOp {
    public static final LIRInstructionClass<AArch64ArrayEqualsOp> TYPE = LIRInstructionClass.create(AArch64ArrayEqualsOp.class);

    private final Stride argStride1;
    private final Stride argStride2;
    private final Stride argStrideM;

    @Def({REG}) protected Value resultValue;
    @Alive({REG}) protected Value array1Value;
    @Alive({REG, ILLEGAL}) protected Value offset1Value;
    @Alive({REG}) protected Value array2Value;
    @Alive({REG, ILLEGAL}) protected Value offset2Value;
    @Alive({REG}) protected Value lengthValue;
    @Alive({REG, ILLEGAL}) protected Value arrayMaskValue;
    @Alive({REG, ILLEGAL}) private Value dynamicStridesValue;
    @Temp({REG}) protected AllocatableValue[] temp;
    @Temp({REG}) protected AllocatableValue[] vectorTemp;

    public AArch64ArrayEqualsOp(LIRGeneratorTool tool, Stride stride1, Stride stride2, Stride strideM, Value result, Value array1, Value offset1, Value array2, Value offset2, Value length, Value mask,
                    Value dynamicStrides) {
        super(TYPE);
        this.argStride1 = stride1;
        this.argStride2 = stride2;
        this.argStrideM = strideM;

        GraalError.guarantee(strideM == null || Stride.max(stride1, stride2).value >= strideM.value, "mask array stride must not be greater than other strides");

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
        this.arrayMaskValue = mask == null ? Value.ILLEGAL : mask;
        this.dynamicStridesValue = dynamicStrides == null ? Value.ILLEGAL : dynamicStrides;

        temp = allocateTempRegisters(tool, withMask() ? 5 : 4);
        vectorTemp = allocateVectorRegisters(tool, withMask() ? 6 : 4);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register length = asRegister(temp[0]);
        Register array1 = asRegister(temp[1]);
        Register array2 = asRegister(temp[2]);
        Register tmp = asRegister(temp[3]);
        Register mask = withMask() ? asRegister(temp[4]) : null;
        Label breakLabel = new Label();

        // Load array base addresses.
        masm.add(64, array1, asRegister(array1Value), asRegister(offset1Value));
        masm.add(64, array2, asRegister(array2Value), asRegister(offset2Value));
        if (withMask()) {
            masm.mov(64, mask, asRegister(arrayMaskValue));
        }

        // Get array length and store as a 64-bit value.
        masm.mov(32, length, asRegister(lengthValue));

        try (ScratchRegister sc = masm.getScratchRegister()) {
            Register hasMismatch = sc.getRegister();
            if (withDynamicStrides()) {
                Label[] variants = new Label[9];
                for (int i = 0; i < variants.length; i++) {
                    variants[i] = new Label();
                }
                AArch64ControlFlow.RangeTableSwitchOp.emitJumpTable(crb, masm, tmp, asRegister(dynamicStridesValue), 0, 8, Arrays.stream(variants));

                // use the 1-byte-1-byte stride variant for the 2-2 and 4-4 cases by simply shifting
                // the length
                masm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
                masm.bind(variants[StrideUtil.getDirectStubCallIndex(Stride.S4, Stride.S4)]);
                masm.lsl(64, length, length, 1);
                masm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
                masm.bind(variants[StrideUtil.getDirectStubCallIndex(Stride.S2, Stride.S2)]);
                masm.lsl(64, length, length, 1);
                masm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
                masm.bind(variants[StrideUtil.getDirectStubCallIndex(Stride.S1, Stride.S1)]);
                emitArrayEquals(masm, Stride.S1, Stride.S1, Stride.S1, array1, array2, mask, length, hasMismatch, breakLabel);
                masm.jmp(breakLabel);

                for (Stride stride1 : new Stride[]{Stride.S1, Stride.S2, Stride.S4}) {
                    for (Stride stride2 : new Stride[]{Stride.S1, Stride.S2, Stride.S4}) {
                        if (stride1.log2 <= stride2.log2) {
                            continue;
                        }
                        masm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
                        // use the same implementation for e.g. stride 1-2 and 2-1 by swapping the
                        // arguments in one variant
                        masm.bind(variants[StrideUtil.getDirectStubCallIndex(stride2, stride1)]);
                        masm.mov(64, tmp, array1);
                        masm.mov(64, array1, array2);
                        masm.mov(64, array2, tmp);
                        masm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
                        masm.bind(variants[StrideUtil.getDirectStubCallIndex(stride1, stride2)]);
                        emitArrayEquals(masm, stride1, stride2, stride2, array1, array2, mask, length, hasMismatch, breakLabel);
                        masm.jmp(breakLabel);
                    }
                }
            } else {
                emitArrayEquals(masm, argStride1, argStride2, argStrideM, array1, array2, mask, length, hasMismatch, breakLabel);
            }

            // Return: hasMismatch is non-zero iff the arrays differ
            masm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
            masm.bind(breakLabel);
            masm.cmp(64, hasMismatch, zr);
            masm.cset(32, asRegister(resultValue), ConditionFlag.EQ);
        }
    }

    private boolean withMask() {
        return !isIllegal(arrayMaskValue);
    }

    private boolean withDynamicStrides() {
        return !isIllegal(dynamicStridesValue);
    }

    private void emitArrayEquals(AArch64MacroAssembler masm, Stride stride1, Stride stride2, Stride strideM, Register array1, Register array2, Register mask, Register length, Register hasMismatch,
                    Label breakLabel) {

        try (ScratchRegister sc = masm.getScratchRegister()) {
            Label scalarCompare = new Label();
            Register scratch = sc.getRegister();

            Stride maxStride = Stride.max(stride1, stride2);
            masm.compare(32, length, 32 / maxStride.value);
            masm.branchConditionally(ConditionFlag.LE, scalarCompare);

            emitSIMDCompare(masm, stride1, stride2, strideM, array1, array2, mask, length, hasMismatch, scratch, breakLabel);

            masm.bind(scalarCompare);
            if (stride1 == stride2 && stride1 == strideM) {
                emitSameStrideScalarCompare(masm, stride1, array1, array2, mask, length, hasMismatch, scratch, breakLabel);
            } else {
                emitMixedStrideScalarCompare(masm, stride1, stride2, strideM, array1, array2, mask, length, breakLabel, hasMismatch);
            }
        }
    }

    private void emitSameStrideScalarCompare(AArch64MacroAssembler masm, Stride stride, Register array1, Register array2, Register mask, Register byteArrayLength, Register hasMismatch,
                    Register scratch,
                    Label breakLabel) {
        // convert length to byte-length
        if (stride.log2 > 0) {
            masm.lsl(64, byteArrayLength, byteArrayLength, stride.log2);
        }
        masm.mov(64, scratch, byteArrayLength); // copy

        emit8ByteCompare(masm, scratch, array1, array2, mask, byteArrayLength, breakLabel, hasMismatch);
        emitTailCompares(masm, stride, scratch, array1, array2, mask, breakLabel, hasMismatch);
    }

    /**
     * Vector size used in {@link #emit8ByteCompare}.
     */
    private static final int VECTOR_SIZE = 8;

    /**
     * Emits code that uses 8-byte vector compares.
     */
    private void emit8ByteCompare(AArch64MacroAssembler masm, Register result, Register array1, Register array2, Register mask, Register length, Label breakLabel, Register rscratch1) {
        Label loop = new Label();
        Label compareTail = new Label();

        Register tmp = asRegister(temp[3]);

        masm.and(64, result, result, VECTOR_SIZE - 1); // tail count (in bytes)
        masm.ands(64, length, length, ~(VECTOR_SIZE - 1));  // vector count (in bytes)
        masm.branchConditionally(ConditionFlag.EQ, compareTail);

        masm.add(64, array1, array1, length);
        masm.add(64, array2, array2, length);
        if (withMask()) {
            masm.add(64, mask, mask, length);
        }
        masm.sub(64, length, zr, length);

        // Align the main loop
        masm.align(AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT);
        masm.bind(loop);
        masm.ldr(64, tmp, AArch64Address.createRegisterOffsetAddress(64, array1, length, false));
        if (withMask()) {
            masm.ldr(64, rscratch1, AArch64Address.createRegisterOffsetAddress(64, mask, length, false));
            masm.orr(64, tmp, tmp, rscratch1);
        }
        masm.ldr(64, rscratch1, AArch64Address.createRegisterOffsetAddress(64, array2, length, false));
        masm.eor(64, rscratch1, tmp, rscratch1);
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
        if (withMask()) {
            masm.add(64, mask, mask, -VECTOR_SIZE);
        }
        masm.ldr(64, tmp, AArch64Address.createRegisterOffsetAddress(64, array1, result, false));
        if (withMask()) {
            masm.ldr(64, rscratch1, AArch64Address.createRegisterOffsetAddress(64, mask, result, false));
            masm.orr(64, tmp, tmp, rscratch1);
        }
        masm.ldr(64, rscratch1, AArch64Address.createRegisterOffsetAddress(64, array2, result, false));
        masm.eor(64, rscratch1, tmp, rscratch1);
        masm.jmp(breakLabel);

        masm.bind(compareTail);
    }

    /**
     * Emits code to compare the remaining 1 to 4 bytes.
     *
     */
    private void emitTailCompares(AArch64MacroAssembler masm, Stride stride, Register result, Register array1, Register array2, Register mask, Label breakLabel, Register rscratch1) {
        Label compare2Bytes = new Label();
        Label compare1Byte = new Label();
        Label end = new Label();

        Register tmp = asRegister(temp[3]);

        if (stride.value <= 4) {
            // Compare trailing 4 bytes, if any.
            tailCompare(masm, result, array1, array2, mask, breakLabel, rscratch1, compare2Bytes, tmp, 4);

            masm.bind(compare2Bytes);
            if (stride.value <= 2) {
                // Compare trailing 2 bytes, if any.
                tailCompare(masm, result, array1, array2, mask, breakLabel, rscratch1, compare1Byte, tmp, 2);

                // The one-byte tail compare is only required for boolean and byte arrays.
                masm.bind(compare1Byte);
                if (stride.value <= 1) {
                    // Compare trailing byte, if any.
                    tailCompare(masm, result, array1, array2, mask, breakLabel, rscratch1, end, tmp, 1);
                }
            }
        }
        masm.bind(end);
        masm.mov(64, rscratch1, zr);
    }

    private void tailCompare(AArch64MacroAssembler masm, Register result, Register array1, Register array2, Register mask, Label breakLabel, Register rscratch1, Label nextTail, Register tmp,
                    int nBytes) {
        int srcSize = nBytes * 8;
        masm.ands(32, zr, result, nBytes);
        masm.branchConditionally(ConditionFlag.EQ, nextTail);
        masm.ldr(srcSize, tmp, AArch64Address.createImmediateAddress(srcSize, AddressingMode.IMMEDIATE_POST_INDEXED, array1, nBytes));
        if (withMask()) {
            masm.ldr(srcSize, rscratch1, AArch64Address.createImmediateAddress(srcSize, AddressingMode.IMMEDIATE_POST_INDEXED, mask, nBytes));
            masm.orr(32, tmp, tmp, rscratch1);
        }
        masm.ldr(srcSize, rscratch1, AArch64Address.createImmediateAddress(srcSize, AddressingMode.IMMEDIATE_POST_INDEXED, array2, nBytes));
        masm.eor(32, rscratch1, tmp, rscratch1);
        masm.cbnz(32, rscratch1, breakLabel);
    }

    /**
     * Emits code that uses 8-byte vector compares.
     */
    private void emitMixedStrideScalarCompare(AArch64MacroAssembler masm, Stride stride1, Stride stride2, Stride strideM, Register array1, Register array2, Register mask, Register length,
                    Label breakLabel, Register rscratch1) {
        Label loop = new Label();

        Register tmp = asRegister(temp[3]);

        // check for length == 0
        masm.mov(64, rscratch1, zr);
        masm.cbz(64, length, breakLabel);

        // main loop
        masm.align(AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT);
        masm.bind(loop);
        masm.ldr(stride1.getBitCount(), tmp, AArch64Address.createImmediateAddress(stride1.getBitCount(), AddressingMode.IMMEDIATE_POST_INDEXED, array1, stride1.value));
        if (withMask()) {
            masm.ldr(strideM.getBitCount(), rscratch1, AArch64Address.createImmediateAddress(strideM.getBitCount(), AddressingMode.IMMEDIATE_POST_INDEXED, mask, strideM.value));
            masm.orr(64, tmp, tmp, rscratch1);
        }
        masm.ldr(stride2.getBitCount(), rscratch1, AArch64Address.createImmediateAddress(stride2.getBitCount(), AddressingMode.IMMEDIATE_POST_INDEXED, array2, stride2.value));
        masm.eor(64, rscratch1, tmp, rscratch1);
        masm.cbnz(64, rscratch1, breakLabel);
        masm.sub(64, length, length, 1);
        masm.cbnz(64, length, loop);
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
     *   2.1 Store end index at the beginning of the last chunk ('refAddress'). This ensures that we
     *   don't read beyond the array boundary.
     *   2.2 Read a 32-byte chunk from source and target array each in two SIMD registers.
     *  3. XOR the 32-byte chunks from both arrays. The result of the comparison is now in two SIMD registers.
     *  4. Detect a mismatch by checking if any element of the two SIMD registers is nonzero.
     *   4.1 Combine the result of comparison from Step 3 into one SIMD register by performing logical OR.
     *   4.2 Mismatch is detected if any of the bits in the XOR result is set. Thus, find the maximum
     *   element across the vector. Here, element size doesn't matter as the objective is to detect a mismatch.
     *   4.3 Read the maximum value from Step 4.2 and sign-extend it to 8 bytes. There is a mismatch
     *   if the result != 0.
     *  5. Repeat the process until the end of the arrays.
     * @formatter:on
     */
    private void emitSIMDCompare(AArch64MacroAssembler masm, Stride stride1, Stride stride2, Stride strideM, Register array1, Register array2, Register mask, Register byteArrayLength,
                    Register hasMismatch, Register scratch,
                    Label endLabel) {
        Register refAddress = asRegister(temp[3]);
        Register endOfMaxStrideArray = scratch;

        Label compareByChunkHead = new Label();
        Label compareByChunkTail = new Label();
        Label processTail = new Label();

        Stride maxStride = Stride.max(stride1, stride2);
        Stride minStride = Stride.min(stride1, stride2);
        assert strideM.value <= maxStride.value;
        Register maxStrideArray = stride1.value < stride2.value ? array2 : array1;
        Register minStrideArray = stride1.value < stride2.value ? array1 : array2;

        // convert length to byte-length
        if (maxStride.log2 > 0) {
            masm.lsl(64, byteArrayLength, byteArrayLength, maxStride.log2);
        }

        /*
         * 2.1 Set endOfArray1 pointing to byte next to the last valid element in array1 and
         * 'refAddress1' pointing to the beginning of the last chunk.
         */
        masm.add(64, endOfMaxStrideArray, maxStrideArray, byteArrayLength);
        masm.sub(64, refAddress, endOfMaxStrideArray, 32);

        /*
         * **********************************
         *
         * START PEELED FIRST LOOP ITERATION.
         *
         * **********************************
         */

        simdCompare(masm, stride1, stride2, strideM, hasMismatch, array1, array2, mask);
        /* If there is mismatch, then no more searching is necessary */
        masm.cbnz(64, hasMismatch, endLabel);

        /* 5. No mismatch; proceed to next loop iteration. */
        /*
         * Extra first loop iteration step: align array1 to a 32-byte boundary.
         *
         * Determine how much to subtract from array2 to match aligned array1. Using the result
         * register as a temporary.
         */
        Register arrayAlignment = asRegister(resultValue);
        masm.and(64, arrayAlignment, maxStrideArray, 31);
        masm.sub(64, minStrideArray, minStrideArray, arrayAlignment, ShiftType.LSR, maxStride.log2 - minStride.log2);
        if (withMask()) {
            masm.sub(64, mask, mask, arrayAlignment, ShiftType.LSR, maxStride.log2 - strideM.log2);
        }
        masm.bic(64, maxStrideArray, maxStrideArray, 31);

        /*
         * ********************************
         *
         * END PEELED FIRST LOOP ITERATION.
         *
         * ********************************
         */

        masm.align(AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT);
        masm.bind(compareByChunkHead);
        masm.cmp(64, refAddress, maxStrideArray);
        masm.branchConditionally(ConditionFlag.LO, processTail);
        masm.bind(compareByChunkTail);

        /* 2.2 Read a 32-byte chunk from source and target array each in two SIMD registers. */
        simdCompare(masm, stride1, stride2, strideM, hasMismatch, array1, array2, mask);
        /* 5. No mismatch; jump to next loop iteration. */
        masm.cbz(64, hasMismatch, compareByChunkHead);
        /* If there is mismatch, then no more searching is necessary */
        masm.jmp(endLabel);

        masm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
        masm.bind(processTail);
        masm.cmp(64, maxStrideArray, endOfMaxStrideArray);
        masm.branchConditionally(ConditionFlag.HS, endLabel);
        /* Adjust array1 and array2 to access last 32 bytes. */
        masm.sub(64, arrayAlignment, maxStrideArray, refAddress);
        masm.mov(64, maxStrideArray, refAddress);
        masm.sub(64, minStrideArray, minStrideArray, arrayAlignment, ShiftType.LSR, maxStride.log2 - minStride.log2);
        if (withMask()) {
            masm.sub(64, mask, mask, arrayAlignment, ShiftType.LSR, maxStride.log2 - strideM.log2);
        }
        masm.jmp(compareByChunkTail);
    }

    private void simdCompare(AArch64MacroAssembler masm,
                    Stride stride1,
                    Stride stride2,
                    Stride strideM, Register hasMismatch,
                    Register array1,
                    Register array2, Register mask) {

        Register array1Part1RegV = asRegister(vectorTemp[0]);
        Register array1Part2RegV = asRegister(vectorTemp[1]);
        Register array2Part1RegV = asRegister(vectorTemp[2]);
        Register array2Part2RegV = asRegister(vectorTemp[3]);
        Register maskPart1RegV = withMask() ? asRegister(vectorTemp[4]) : null;
        Register maskPart2RegV = withMask() ? asRegister(vectorTemp[5]) : null;

        Stride strideMax = Stride.max(stride1, stride2);
        /* 2.2 Read a 32-byte chunk from source and target array each in two SIMD registers. */
        masm.loadAndExtend(strideMax, stride1, array1, array1Part1RegV, array1Part2RegV);
        masm.loadAndExtend(strideMax, stride2, array2, array2Part1RegV, array2Part2RegV);
        if (withMask()) {
            masm.loadAndExtend(strideMax, strideM, mask, maskPart1RegV, maskPart2RegV);
            masm.neon.orrVVV(FullReg, array1Part1RegV, array1Part1RegV, maskPart1RegV);
            masm.neon.orrVVV(FullReg, array1Part2RegV, array1Part2RegV, maskPart2RegV);
        }
        /* 3. XOR arrays in the 32-byte chunk */
        masm.neon.eorVVV(FullReg, array1Part1RegV, array1Part1RegV, array2Part1RegV);
        masm.neon.eorVVV(FullReg, array1Part2RegV, array1Part2RegV, array2Part2RegV);
        /* 4. Determining if they are identical. */
        /* 4.1 Combine two registers into 1 register */
        masm.neon.orrVVV(FullReg, array1Part1RegV, array1Part1RegV, array1Part2RegV);
        /* 4.2 Find the maximum value across the vector */
        masm.neon.umaxvSV(FullReg, ElementSize.Word, array1Part1RegV, array1Part1RegV);
        /* 4.3 If result != 0, then there is a mismatch somewhere. */
        masm.neon.moveFromIndex(ElementSize.DoubleWord, ElementSize.Word, hasMismatch, array1Part1RegV, 0);
    }
}
