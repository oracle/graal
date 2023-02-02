/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, Arm Limited. All rights reserved.
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
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import java.util.Arrays;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ShiftType;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

@Opcode("AArch64_ARRAY_INDEX_OF")
public final class AArch64ArrayIndexOfOp extends AArch64ComplexVectorOp {
    public static final LIRInstructionClass<AArch64ArrayIndexOfOp> TYPE = LIRInstructionClass.create(AArch64ArrayIndexOfOp.class);

    private final boolean findTwoConsecutive;
    private final boolean withMask;
    private final Stride stride;

    @Def({REG}) protected AllocatableValue resultValue;
    @Alive({REG}) protected AllocatableValue arrayPtrValue;
    @Alive({REG}) protected AllocatableValue arrayOffsetValue;
    @Alive({REG}) protected AllocatableValue arrayLengthValue;
    @Alive({REG}) protected AllocatableValue fromIndexValue;
    @Alive({REG}) protected AllocatableValue[] searchValues;

    @Temp({REG}) protected AllocatableValue[] temp;
    @Temp({REG}) protected AllocatableValue[] vectorTemp;

    public AArch64ArrayIndexOfOp(Stride stride, boolean findTwoConsecutive, boolean withMask, LIRGeneratorTool tool,
                    AllocatableValue result, AllocatableValue arrayPtr, AllocatableValue arrayOffset, AllocatableValue arrayLength, AllocatableValue fromIndex,
                    AllocatableValue[] searchValues) {
        super(TYPE);

        int nValues = searchValues.length;
        GraalError.guarantee(0 < nValues && nValues <= 4, "only 1 - 4 values supported");
        GraalError.guarantee(stride.value <= 4, "supported strides are 1, 2 and 4 bytes");
        GraalError.guarantee(!(!withMask && findTwoConsecutive) || nValues == 2, "findTwoConsecutive mode requires exactly 2 search values");
        GraalError.guarantee(!(withMask && findTwoConsecutive) || nValues == 4, "findTwoConsecutive mode with mask requires exactly 4 search values");
        GraalError.guarantee(!(withMask && !findTwoConsecutive) || nValues == 2, "with mask mode requires exactly 2 search values");
        GraalError.guarantee(result.getPlatformKind() == AArch64Kind.DWORD, "int value expected");
        GraalError.guarantee(arrayPtr.getPlatformKind() == AArch64Kind.QWORD, "pointer value expected");
        GraalError.guarantee(arrayOffset.getPlatformKind() == AArch64Kind.QWORD, "long value expected");
        GraalError.guarantee(arrayLength.getPlatformKind() == AArch64Kind.DWORD, "int value expected");
        GraalError.guarantee(fromIndex.getPlatformKind() == AArch64Kind.DWORD, "int value expected");
        GraalError.guarantee(Arrays.stream(searchValues).allMatch(sv -> sv.getPlatformKind() == AArch64Kind.DWORD), "int values expected");

        this.stride = stride;
        this.findTwoConsecutive = findTwoConsecutive;
        this.withMask = withMask;
        resultValue = result;
        arrayPtrValue = arrayPtr;
        arrayOffsetValue = arrayOffset;
        arrayLengthValue = arrayLength;
        fromIndexValue = fromIndex;
        this.searchValues = searchValues;

        temp = allocateTempRegisters(tool, 5);
        vectorTemp = allocateVectorRegisters(tool, nValues + (findTwoConsecutive ? 4 : !withMask && nValues > 1 ? 6 : 3));
    }

    private void emitScalarCode(AArch64MacroAssembler masm, Register baseAddress, Register searchLength) {
        Register result = asRegister(resultValue);
        Register arrayLength = asRegister(arrayLengthValue);
        Register curValue = asRegister(temp[2]);

        Label match = new Label();
        Label searchByElementLoop = new Label();
        Label done = new Label();

        /*
         * AArch64 comparisons are at minimum 32 bits; since we are comparing against a
         * zero-extended value, the searchValue must also be zero-extended. This is already done by
         * the caller.
         */
        final int memAccessSize = (findTwoConsecutive ? 2 : 1) * stride.value * Byte.SIZE;
        Register searchValueReg;
        Register maskReg;
        int compareSize = Math.max(32, memAccessSize);
        if (findTwoConsecutive) {
            searchValueReg = asRegister(temp[3]);
            masm.lsl(compareSize, searchValueReg, asRegister(searchValues[1]), (long) stride.value * Byte.SIZE);
            masm.orr(compareSize, searchValueReg, searchValueReg, asRegister(searchValues[0]));
            if (withMask) {
                maskReg = asRegister(temp[4]);
                masm.lsl(compareSize, maskReg, asRegister(searchValues[3]), (long) stride.value * Byte.SIZE);
                masm.orr(compareSize, maskReg, maskReg, asRegister(searchValues[2]));
            } else {
                maskReg = null;
            }
        } else {
            searchValueReg = asRegister(searchValues[0]);
            if (withMask) {
                maskReg = asRegister(searchValues[1]);
            } else {
                maskReg = null;
            }
        }

        /*
         * Searching the array sequentially for the target value. Note that all indexing is done via
         * a negative offset from the first position beyond the end of search region.
         */
        Register endIndex = arrayLength;
        if (findTwoConsecutive) {
            /* The end of the region is the second to last element in the array */
            masm.sub(32, endIndex, endIndex, 1);
            masm.cbz(32, endIndex, done);
        }
        /*
         * Set the base address to be at the end of the region baseAddress + (endIndex << shift
         * size).
         */
        masm.add(64, baseAddress, baseAddress, endIndex, AArch64Assembler.ExtendType.SXTW, stride.log2);
        /* Initial index is -searchLength << shift size */
        Register curIndex = searchLength;
        masm.sub(64, curIndex, zr, curIndex, ShiftType.LSL, stride.log2);
        /* Loop doing element-by-element search */
        masm.align(AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT);
        masm.bind(searchByElementLoop);
        masm.ldr(memAccessSize, curValue, AArch64Address.createRegisterOffsetAddress(memAccessSize, baseAddress, curIndex, false));
        if (withMask) {
            masm.orr(compareSize, curValue, curValue, maskReg);
        }
        if (findTwoConsecutive || withMask) {
            masm.cmp(compareSize, searchValueReg, curValue);
            masm.branchConditionally(ConditionFlag.EQ, match);
        } else {
            for (AllocatableValue searchValue : searchValues) {
                masm.cmp(compareSize, asRegister(searchValue), curValue);
                masm.branchConditionally(ConditionFlag.EQ, match);
            }
        }

        /*
         * Add elementSize to the curIndex and retry if the end of the search region has not yet
         * been reached, i.e., the curIndex is still < 0.
         */
        masm.adds(64, curIndex, curIndex, stride.value);
        masm.branchConditionally(ConditionFlag.MI, searchByElementLoop);
        masm.jmp(done);

        masm.align(AArch64MacroAssembler.PREFERRED_BRANCH_TARGET_ALIGNMENT);
        masm.bind(match);
        /* index = endIndex + (curIndex >> shiftSize) */
        masm.add(32, result, endIndex, curIndex, ShiftType.ASR, stride.log2);
        masm.bind(done);
        if (findTwoConsecutive) {
            /* restore arrayLength since it is marked as @Alive */
            masm.add(32, endIndex, endIndex, 1);
        }
    }

    private void emitSIMDCode(AArch64MacroAssembler masm, Register baseAddress) {
        /*
         * @formatter:off
         *  Find a single char in a chunk:
         *  Chunk-based reading uses the following approach (with example for UTF-16) inspired from [1]. For SIMD implementation, the steps
         *  represent computation applied to one lane of 8 bytes. SIMD version replicates the following steps to all 4 lanes of 8 bytes each.
         *  1. Fill 8-byte chunk with search element ('searchElement'): 0x000000000000elem -> 0xelemelemelemelem
         *  2. Read and compare array chunk-by-chunk.
         *   2.1 Store end index at the beginning of the last chunk ('refAddress'). This ensures that we don't read beyond array boundary.
         *   2.2 Array is processed in chunks: the first unaligned chunk (head) -> all 32-byte aligned chunks -> the last unaligned chunk
         *   of 32 bytes (tail). Transitions between processing an unaligned and aligned chunk repeats element search on overlapping part.
         *   However, overhead gets smaller with longer strings and compensated by the gains from using larger (32-byte sized) chunks.
         *   2.3 After processing each chunk, adjust the address 32-byte aligned to read the next chunk. Repeat this step until the last
         *   chunk is reached (address of the next chunk >= 'refAddress').
         *   2.4 On reaching the last chunk, reset the address to read the last chunk.
         *  3. Check each read chunk to see if 'searchElement' is found.
         *     T4 = comparison result, where for each matching lane all bits are set, and for mismatches all bits are cleared.
         *  4. If element is found in a 32-byte chunk then find its position. Use 2-bit representation for each byte to detect the starting byte offset
         *     of matching element. Set those bits to '11' if the match is found. Then calculate number zero element after the matching element to
         *     get the position in the chunk.
         *   4.1 AND T4 with magic constant to set 2 bits based on the byte position of the element in the chunk.
         *   T5 = T4 & 0xc000_0300_00c0_0003L (0x0000FFFF00000000 -> 0x0000030000000000)
         *   4.2 Perform pairwise addition on adjacent byte lanes twice to convert 32-byte to 8-byte representation where each pair of
         *    bits represents its corresponding byte element in a chunk. Magic constant sets different bits for each position that ensures the
         *    pairwise addition would not overflow when consecutive elements are matched as well as preserve the position of matching element.
         *   4.3 Reverse the bitstream in T5 (0x0000030000000000 -> 0x0000000000c00000)
         *   4.4 Calculate leading zeros in T5 and divide the count by 2 to get the byte offset of matching element in a chunk.
         *   4.5 Retrieve the position of 'searchElement' by adding offset within chunk to the index of the chunk.
         *
         *  Find two consecutive chars in a chunk:
         *  The findTwoConsecutive case uses the same steps as finding a single character in a chunk but for two characters separately.
         *  To look for two consecutive characters 'c1c2', we search 'c1' in a first chunk [0..n] and 'c2' in the second chunk at [1..n+1].
         *  Consequently, if the matching sequence is present in a chunk, it will be found at the same position in their respective chunks.
         *  The following list highlights the differences compared to the steps to search a single character in a chunk.
         *   1a. Use two registers, each repeating one of the two consecutive characters to search for.
         *   2a. Read the first chunk starting from the 32 byte aligned ref position. Read the second chunk starting at the next character
         *  from the ref position and ending with the first char from the next 32 byte aligned chunk.
         *   3a. Compare a chunk for presence of the corresponding char.
         *    3a.1 The first chunk is compared with the register repeating the first char and the same for the second chunk.
         *    3a.2 Perform logical AND on the outcome of comparisons for the first and second char.
         *   4a. As the second chunk starts at a char offset from the first chunk, the result of AND from 3a.2 gives a register with all the
         *  bits set at the position where the match is found. The steps to find the position of the match in the searchString remain unchanged.
         *
         *  [1] https://github.com/ARM-software/optimized-routines/blob/master/string/aarch64/strchr.S
         * @formatter:on
         * */

        Register result = asRegister(resultValue);
        Register arrayLength = asRegister(arrayLengthValue);
        Register fromIndex = asRegister(fromIndexValue);
        Register currOffset = asRegister(temp[1]);
        Register searchEnd = asRegister(temp[2]);
        Register refAddress = asRegister(temp[3]);
        Register chunkToReadAddress = asRegister(temp[4]);

        Register[] searchValuesRegV = new Register[searchValues.length];
        for (int i = 0; i < searchValues.length; i++) {
            searchValuesRegV[i] = asRegister(vectorTemp[i]);
        }
        Register firstChunkPart1RegV = asRegister(vectorTemp[searchValues.length]);
        Register firstChunkPart2RegV = asRegister(vectorTemp[searchValues.length + 1]);
        Register tmpRegV1 = asRegister(vectorTemp[searchValues.length + 2]);
        Register tmpRegV2 = findTwoConsecutive || !withMask && searchValues.length > 1 ? asRegister(vectorTemp[searchValues.length + 3]) : null;

        Label matchInChunk = new Label();
        Label searchByChunkLoopHead = new Label();
        Label searchByChunkLoopTail = new Label();
        Label processTail = new Label();
        Label end = new Label();

        ElementSize eSize = ElementSize.fromSize(stride.value * Byte.SIZE);
        /* 1. Duplicate the searchElement(s) to 16-bytes */
        for (int i = 0; i < searchValues.length; i++) {
            masm.neon.dupVG(ASIMDSize.FullReg, eSize, searchValuesRegV[i], asRegister(searchValues[i]));
        }
        /*
         * 2.1 Set searchEnd pointing to byte after the last valid element in the array and
         * 'refAddress' pointing to the beginning of the last chunk.
         */
        if (findTwoConsecutive) {
            // search ends one element early
            masm.sub(32, searchEnd, arrayLength, 1);
            masm.add(64, searchEnd, baseAddress, searchEnd, AArch64Assembler.ExtendType.SXTW, stride.log2);
        } else {
            masm.add(64, searchEnd, baseAddress, arrayLength, AArch64Assembler.ExtendType.SXTW, stride.log2);
        }
        masm.sub(64, refAddress, searchEnd, 32);
        /* Set 'chunkToReadAddress' pointing to the chunk from where the search begins. */
        masm.add(64, chunkToReadAddress, baseAddress, fromIndex, AArch64Assembler.ExtendType.SXTW, stride.log2);

        masm.align(AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT);
        masm.bind(searchByChunkLoopHead);
        masm.cmp(64, refAddress, chunkToReadAddress);
        masm.branchConditionally(ConditionFlag.LS, processTail);
        masm.bind(searchByChunkLoopTail);
        masm.sub(64, currOffset, chunkToReadAddress, baseAddress);

        masm.fldp(128, firstChunkPart1RegV, firstChunkPart2RegV, AArch64Address.createImmediateAddress(128, AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, chunkToReadAddress, 32));
        if (findTwoConsecutive) {
            /*
             * In the findTwoConsecutive case, the second search element is compared against
             * elements [1...n+1] (i.e., the SecondChunk). tmpRegV1 holds values
             * firstChunkPart1[1:n]:firstChunkPart2[0] and tmpRegV2 holds values
             * firstChunkPart2[1:n]:tailValue.
             */
            try (ScratchRegister scratchRegister = masm.getScratchRegister()) {
                Register tailValue = scratchRegister.getRegister();
                masm.ldr(eSize.bits(), tailValue, AArch64Address.createBaseRegisterOnlyAddress(eSize.bits(), chunkToReadAddress));
                // setting firstChunkPart2[1:n] within tempRegV2
                masm.neon.elementRor(ASIMDSize.FullReg, eSize, tmpRegV2, firstChunkPart2RegV, 1);
                // setting tmpRegV1 = firstChunkPart1[1:n]:firstChunkPart2[0]
                masm.neon.extVVV(ASIMDSize.FullReg, tmpRegV1, firstChunkPart1RegV, firstChunkPart2RegV, stride.value);
                // tailValue becomes the last value within tmpRegV2
                int tailValueIndex = (ASIMDSize.FullReg.bytes() / stride.value) - 1;
                masm.neon.insXG(eSize, tmpRegV2, tailValueIndex, tailValue);
            }
        }
        /* 3. Find searchElement in the 32-byte chunk */
        if (withMask) {
            masm.neon.orrVVV(ASIMDSize.FullReg, firstChunkPart1RegV, firstChunkPart1RegV, searchValuesRegV[findTwoConsecutive ? 2 : 1]);
            masm.neon.orrVVV(ASIMDSize.FullReg, firstChunkPart2RegV, firstChunkPart2RegV, searchValuesRegV[findTwoConsecutive ? 2 : 1]);
        }
        if (findTwoConsecutive || withMask || searchValuesRegV.length == 1) {
            masm.neon.cmeqVVV(ASIMDSize.FullReg, eSize, firstChunkPart1RegV, firstChunkPart1RegV, searchValuesRegV[0]);
            masm.neon.cmeqVVV(ASIMDSize.FullReg, eSize, firstChunkPart2RegV, firstChunkPart2RegV, searchValuesRegV[0]);
            if (findTwoConsecutive) {
                // comparing second element against the chunks
                if (withMask) {
                    masm.neon.orrVVV(ASIMDSize.FullReg, tmpRegV1, tmpRegV1, searchValuesRegV[3]);
                    masm.neon.orrVVV(ASIMDSize.FullReg, tmpRegV2, tmpRegV2, searchValuesRegV[3]);
                }
                masm.neon.cmeqVVV(ASIMDSize.FullReg, eSize, tmpRegV1, tmpRegV1, searchValuesRegV[1]);
                masm.neon.cmeqVVV(ASIMDSize.FullReg, eSize, tmpRegV2, tmpRegV2, searchValuesRegV[1]);
                /*
                 * A match exists if there is a match in the same index in both the first and second
                 * chunk.
                 */
                masm.neon.andVVV(ASIMDSize.FullReg, firstChunkPart1RegV, firstChunkPart1RegV, tmpRegV1);
                masm.neon.andVVV(ASIMDSize.FullReg, firstChunkPart2RegV, firstChunkPart2RegV, tmpRegV2);
            }
        } else {
            int nValues = searchValuesRegV.length;
            Register tmpRegV3 = asRegister(vectorTemp[nValues + 4]);
            Register tmpRegV4 = asRegister(vectorTemp[nValues + 5]);
            masm.neon.cmeqVVV(ASIMDSize.FullReg, eSize, tmpRegV1, firstChunkPart1RegV, searchValuesRegV[0]);
            masm.neon.cmeqVVV(ASIMDSize.FullReg, eSize, tmpRegV2, firstChunkPart2RegV, searchValuesRegV[0]);
            for (int i = 1; i < nValues; i++) {
                masm.neon.cmeqVVV(ASIMDSize.FullReg, eSize, tmpRegV3, firstChunkPart1RegV, searchValuesRegV[i]);
                masm.neon.cmeqVVV(ASIMDSize.FullReg, eSize, tmpRegV4, firstChunkPart2RegV, searchValuesRegV[i]);
                masm.neon.orrVVV(ASIMDSize.FullReg, i == nValues - 1 ? firstChunkPart1RegV : tmpRegV1, tmpRegV1, tmpRegV3);
                masm.neon.orrVVV(ASIMDSize.FullReg, i == nValues - 1 ? firstChunkPart2RegV : tmpRegV2, tmpRegV2, tmpRegV4);
            }
            /* Determining if there was a match: combine two registers into 1 register */
            masm.neon.orrVVV(ASIMDSize.FullReg, tmpRegV1, tmpRegV1, tmpRegV2);
        }
        /* Determining if there was a match: combine two registers into 1 register */
        masm.neon.orrVVV(ASIMDSize.FullReg, tmpRegV1, firstChunkPart1RegV, firstChunkPart2RegV);
        /* If value != 0, then there was a match somewhere. */
        vectorCheckZero(masm, ElementSize.fromStride(stride), tmpRegV1, tmpRegV1, true);
        masm.branchConditionally(ConditionFlag.NE, matchInChunk);
        /* No match; jump to next loop iteration. */
        // align address to 32-byte boundary
        masm.bic(64, chunkToReadAddress, chunkToReadAddress, 31);
        masm.jmp(searchByChunkLoopHead);

        masm.align(AArch64MacroAssembler.PREFERRED_BRANCH_TARGET_ALIGNMENT);
        masm.bind(processTail);
        masm.cmp(64, chunkToReadAddress, searchEnd);
        masm.branchConditionally(ConditionFlag.HS, end);
        masm.sub(64, chunkToReadAddress, searchEnd, 32);
        /*
         * Set 'searchEnd' to zero because at the end of 'searchByChunkLoopTail', the
         * 'chunkToReadAddress' will be rolled back to a 32-byte aligned addressed. Thus, unless
         * 'searchEnd' is adjusted, the 'processTail' comparison condition 'chunkToReadAddress' >=
         * 'searchEnd' may never be true.
         */
        masm.mov(64, searchEnd, zr);
        masm.jmp(searchByChunkLoopTail);

        /* 4. If the element is found in a 32-byte chunk then find its position. */
        masm.align(AArch64MacroAssembler.PREFERRED_BRANCH_TARGET_ALIGNMENT);
        masm.bind(matchInChunk);
        try (ScratchRegister scratchReg = masm.getScratchRegister()) {
            Register tmp = scratchReg.getRegister();
            initCalcIndexOfFirstMatchMask(masm, tmpRegV1, tmp);
            calcIndexOfFirstMatch(masm, tmp, firstChunkPart1RegV, firstChunkPart2RegV, tmpRegV1, false);
            masm.add(64, result, currOffset, tmp, ShiftType.ASR, 1);
        }
        if (stride.log2 != 0) {
            /* Convert byte offset of searchElement to its array index */
            masm.asr(64, result, result, stride.log2);
        }
        masm.bind(end);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register result = asRegister(resultValue);
        Register arrayLength = asRegister(arrayLengthValue);
        Register fromIndex = asRegister(fromIndexValue);
        Register baseAddress = asRegister(temp[0]);
        Register searchLength = asRegister(temp[1]);

        Label done = new Label();

        /*
         * @formatter:off
         * The arguments satisfy the following constraints:
         *  1. 0 <= fromIndex <= arrayLength - 1 (or 2 when findTwoConsecutive is true)
         *  2. number of characters in searchChar is 1 (or 2 when findTwoConsecutive is true)
         * @formatter:on
        */
        masm.mov(result, -1);   // Return for empty strings
        masm.cbz(32, arrayLength, done);

        /* Load address of first array element */
        masm.add(64, baseAddress, asRegister(arrayPtrValue), asRegister(arrayOffsetValue));

        /*
         * Search element-by-element for small arrays (with search space size of less than 32 bytes,
         * i.e., 4 UTF-16 or 8 Latin1 elements) else search chunk-by-chunk.
         */
        masm.sub(32, searchLength, arrayLength, fromIndex);
        if (findTwoConsecutive) {
            /*
             * Because one is trying to find two consecutive elements, the search length is in
             * effect one less
             */
            masm.sub(32, searchLength, searchLength, 1);
        }

        Label searchByChunk = new Label();
        int chunkByteSize = 32;
        masm.compare(32, searchLength, chunkByteSize / stride.value);
        masm.branchConditionally(ConditionFlag.GE, searchByChunk);

        /* Search sequentially for short arrays */
        emitScalarCode(masm, baseAddress, searchLength);
        masm.jmp(done);

        /* Search chunk-by-chunk for long arrays */
        masm.bind(searchByChunk);
        emitSIMDCode(masm, baseAddress);

        masm.bind(done);
    }
}
