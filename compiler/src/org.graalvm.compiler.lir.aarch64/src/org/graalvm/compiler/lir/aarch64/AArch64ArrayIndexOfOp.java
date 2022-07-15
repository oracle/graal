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

import static jdk.vm.ci.aarch64.AArch64.SIMD;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
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
import org.graalvm.compiler.core.common.LIRKind;
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

@Opcode("AArch64_ARRAY_INDEX_OF")
public final class AArch64ArrayIndexOfOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64ArrayIndexOfOp> TYPE = LIRInstructionClass.create(AArch64ArrayIndexOfOp.class);

    private final boolean findTwoConsecutive;
    private final int elementByteSize;

    @Def({REG}) protected AllocatableValue resultValue;
    @Alive({REG}) protected AllocatableValue arrayPtrValue;
    @Alive({REG}) protected AllocatableValue arrayOffsetValue;
    @Alive({REG}) protected AllocatableValue arrayLengthValue;
    @Alive({REG}) protected AllocatableValue fromIndexValue;
    @Alive({REG}) protected AllocatableValue[] searchValues;
    @Temp({REG}) protected AllocatableValue temp1;
    @Temp({REG}) protected AllocatableValue temp2;
    @Temp({REG}) protected AllocatableValue temp3;
    @Temp({REG}) protected AllocatableValue temp4;
    @Temp({REG}) protected AllocatableValue temp5;
    @Temp({REG}) protected AllocatableValue vectorTemp1;
    @Temp({REG}) protected AllocatableValue vectorTemp2;
    @Temp({REG}) protected AllocatableValue vectorTemp3;
    @Temp({REG}) protected AllocatableValue vectorTemp4;
    /* These two vectors are only needed when trying to match two consecutive elements. */
    @Temp({REG, ILLEGAL}) protected AllocatableValue vectorTemp5;
    @Temp({REG, ILLEGAL}) protected AllocatableValue vectorTemp6;

    public AArch64ArrayIndexOfOp(Stride stride, boolean findTwoConsecutive, LIRGeneratorTool tool,
                    AllocatableValue result, AllocatableValue arrayPtr, AllocatableValue arrayOffset, AllocatableValue arrayLength, AllocatableValue fromIndex,
                    AllocatableValue[] searchValues) {
        super(TYPE);

        assert result.getPlatformKind() == AArch64Kind.DWORD;
        assert arrayPtr.getPlatformKind() == AArch64Kind.QWORD;
        assert arrayOffset.getPlatformKind() == AArch64Kind.QWORD;
        assert arrayLength.getPlatformKind() == AArch64Kind.DWORD;
        assert fromIndex.getPlatformKind() == AArch64Kind.DWORD;
        assert Arrays.stream(searchValues).allMatch(sv -> sv.getPlatformKind() == AArch64Kind.DWORD);

        this.elementByteSize = stride.value;
        this.findTwoConsecutive = findTwoConsecutive;
        resultValue = result;
        arrayPtrValue = arrayPtr;
        arrayOffsetValue = arrayOffset;
        arrayLengthValue = arrayLength;
        fromIndexValue = fromIndex;
        this.searchValues = searchValues;
        LIRKind archWordKind = LIRKind.value(tool.target().arch.getWordKind());
        temp1 = tool.newVariable(archWordKind);
        temp2 = tool.newVariable(archWordKind);
        temp3 = tool.newVariable(archWordKind);
        temp4 = tool.newVariable(archWordKind);
        temp5 = tool.newVariable(archWordKind);

        LIRKind vectorKind = LIRKind.value(tool.target().arch.getLargestStorableKind(SIMD));
        vectorTemp1 = tool.newVariable(vectorKind);
        vectorTemp2 = tool.newVariable(vectorKind);
        vectorTemp3 = tool.newVariable(vectorKind);
        vectorTemp4 = tool.newVariable(vectorKind);
        vectorTemp5 = findTwoConsecutive ? tool.newVariable(vectorKind) : Value.ILLEGAL;
        vectorTemp6 = findTwoConsecutive ? tool.newVariable(vectorKind) : Value.ILLEGAL;
    }

    private int getShiftSize() {
        switch (elementByteSize) {
            case 1:
                return 0;
            case 2:
                return 1;
            case 4:
                return 2;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    private void emitScalarCode(AArch64MacroAssembler masm, Register baseAddress, Register searchLength) {
        Register result = asRegister(resultValue);
        Register arrayLength = asRegister(arrayLengthValue);
        Register curValue = asRegister(temp3);

        Label match = new Label();
        Label searchByElementLoop = new Label();
        Label done = new Label();

        int shiftSize = getShiftSize();

        /*
         * AArch64 comparisons are at minimum 32 bits; since we are comparing against a
         * zero-extended value, the searchValue must also be zero-extended. This is already done by
         * the caller.
         */
        final int memAccessSize = (findTwoConsecutive ? 2 : 1) * elementByteSize * Byte.SIZE;
        Register searchValueReg;
        int compareSize = Math.max(32, memAccessSize);
        if (findTwoConsecutive) {
            searchValueReg = asRegister(temp4);
            masm.lsl(compareSize, searchValueReg, asRegister(searchValues[1]), (long) elementByteSize * Byte.SIZE);
            masm.orr(compareSize, searchValueReg, searchValueReg, asRegister(searchValues[0]));
        } else {
            searchValueReg = asRegister(searchValues[0]);
        }

        /*
         * Searching the array sequentially for the target value. Note that all indexing is done via
         * a negative offset from the first position beyond the end of search region.
         */
        Register endIndex;
        if (findTwoConsecutive) {
            /* The end of the region is the second to last element in the array */
            endIndex = asRegister(temp5);
            masm.sub(32, endIndex, arrayLength, 1);
        } else {
            /* The end of the region is the last element in the array */
            endIndex = arrayLength;
        }
        /*
         * Set the base address to be at the end of the region baseAddress + (endIndex << shift
         * size).
         */
        masm.add(64, baseAddress, baseAddress, endIndex, AArch64Assembler.ExtendType.SXTW, shiftSize);
        /* Initial index is -searchLength << shift size */
        Register curIndex = searchLength;
        masm.sub(64, curIndex, zr, curIndex, ShiftType.LSL, shiftSize);
        /* Loop doing element-by-element search */
        masm.align(AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT);
        masm.bind(searchByElementLoop);
        masm.ldr(memAccessSize, curValue, AArch64Address.createRegisterOffsetAddress(memAccessSize, baseAddress, curIndex, false));
        masm.cmp(compareSize, searchValueReg, curValue);
        masm.branchConditionally(ConditionFlag.EQ, match);

        /*
         * Add elementSize to the curIndex and retry if the end of the search region has not yet
         * been reached, i.e., the curIndex is still < 0.
         */
        masm.adds(64, curIndex, curIndex, elementByteSize);
        masm.branchConditionally(ConditionFlag.MI, searchByElementLoop);
        masm.jmp(done);

        masm.align(AArch64MacroAssembler.PREFERRED_BRANCH_TARGET_ALIGNMENT);
        masm.bind(match);
        /* index = endIndex + (curIndex >> shiftSize) */
        masm.add(32, result, endIndex, curIndex, ShiftType.ASR, shiftSize);

        masm.bind(done);
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
        Register currOffset = asRegister(temp2);
        Register searchEnd = asRegister(temp3);
        Register refAddress = asRegister(temp4);
        Register chunkToReadAddress = asRegister(temp5);
        Register firstSearchElementRegV = asRegister(vectorTemp1);
        Register firstChunkPart1RegV = asRegister(vectorTemp2);
        Register firstChunkPart2RegV = asRegister(vectorTemp3);
        Register tmpRegV1 = asRegister(vectorTemp4);
        Register secondSearchElementRegV = findTwoConsecutive ? asRegister(vectorTemp5) : null;
        Register tmpRegV2 = findTwoConsecutive ? asRegister(vectorTemp6) : null;

        Label matchInChunk = new Label();
        Label searchByChunkLoopHead = new Label();
        Label searchByChunkLoopTail = new Label();
        Label processTail = new Label();
        Label end = new Label();

        int shiftSize = getShiftSize();
        ElementSize eSize = ElementSize.fromSize(elementByteSize * Byte.SIZE);
        /*
         * Magic constant is used to detect byte offset of the starting position of the search
         * element in the matching chunk.
         */
        long magicConstant = 0xc030_0c03_c030_0c03L;

        /* 1. Duplicate the searchElement(s) to 16-bytes */
        masm.neon.dupVG(ASIMDSize.FullReg, eSize, firstSearchElementRegV, asRegister(searchValues[0]));
        if (findTwoConsecutive) {
            masm.neon.dupVG(ASIMDSize.FullReg, eSize, secondSearchElementRegV, asRegister(searchValues[1]));
        }
        /*
         * 2.1 Set searchEnd pointing to byte after the last valid element in the array and
         * 'refAddress' pointing to the beginning of the last chunk.
         */
        if (findTwoConsecutive) {
            // search ends one element early
            masm.sub(32, searchEnd, arrayLength, 1);
            masm.add(64, searchEnd, baseAddress, searchEnd, AArch64Assembler.ExtendType.SXTW, shiftSize);
        } else {
            masm.add(64, searchEnd, baseAddress, arrayLength, AArch64Assembler.ExtendType.SXTW, shiftSize);
        }
        masm.sub(64, refAddress, searchEnd, 32);
        /* Set 'chunkToReadAddress' pointing to the chunk from where the search begins. */
        masm.add(64, chunkToReadAddress, baseAddress, fromIndex, AArch64Assembler.ExtendType.SXTW, shiftSize);

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
                masm.neon.extVVV(ASIMDSize.FullReg, tmpRegV1, firstChunkPart1RegV, firstChunkPart2RegV, elementByteSize);
                // tailValue becomes the last value within tmpRegV2
                int tailValueIndex = (ASIMDSize.FullReg.bytes() / elementByteSize) - 1;
                masm.neon.insXG(eSize, tmpRegV2, tailValueIndex, tailValue);
            }
        }
        /* 3. Find searchElement in the 32-byte chunk */
        masm.neon.cmeqVVV(ASIMDSize.FullReg, eSize, firstChunkPart1RegV, firstChunkPart1RegV, firstSearchElementRegV);
        masm.neon.cmeqVVV(ASIMDSize.FullReg, eSize, firstChunkPart2RegV, firstChunkPart2RegV, firstSearchElementRegV);
        if (findTwoConsecutive) {
            // comparing second element against the chunks
            masm.neon.cmeqVVV(ASIMDSize.FullReg, eSize, tmpRegV1, tmpRegV1, secondSearchElementRegV);
            masm.neon.cmeqVVV(ASIMDSize.FullReg, eSize, tmpRegV2, tmpRegV2, secondSearchElementRegV);
            /*
             * A match exists if there is a match in the same index in both the first and second
             * chunk.
             */
            masm.neon.andVVV(ASIMDSize.FullReg, firstChunkPart1RegV, firstChunkPart1RegV, tmpRegV1);
            masm.neon.andVVV(ASIMDSize.FullReg, firstChunkPart2RegV, firstChunkPart2RegV, tmpRegV2);
        }
        /* Determining if there was a match: combine two registers into 1 register */
        masm.neon.orrVVV(ASIMDSize.FullReg, tmpRegV1, firstChunkPart1RegV, firstChunkPart2RegV);
        /* Reduce 128-bit value to 32/64-bit value */
        if (elementByteSize == 1) {
            masm.neon.umaxvSV(ASIMDSize.FullReg, ElementSize.Word, tmpRegV1, tmpRegV1);
        } else {
            masm.neon.xtnVV(eSize.narrow(), tmpRegV1, tmpRegV1);
        }
        /* If value != 0, then there was a match somewhere. */
        try (ScratchRegister scratchReg = masm.getScratchRegister()) {
            Register matchReg = scratchReg.getRegister();
            masm.neon.moveFromIndex(ElementSize.DoubleWord, ElementSize.DoubleWord, matchReg, tmpRegV1, 0);
            masm.cbnz(64, matchReg, matchInChunk);
        }
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
        /*
         * 4.1 Using the magic constant set 2 bits in the matching byte(s) that represent its
         * position in the chunk
         */
        try (ScratchRegister scratchReg = masm.getScratchRegister()) {
            Register magicConstantReg = scratchReg.getRegister();
            masm.mov(magicConstantReg, magicConstant);
            masm.neon.dupVG(ASIMDSize.FullReg, ElementSize.DoubleWord, tmpRegV1, magicConstantReg);
        }
        masm.neon.andVVV(ASIMDSize.FullReg, firstChunkPart1RegV, firstChunkPart1RegV, tmpRegV1);
        masm.neon.andVVV(ASIMDSize.FullReg, firstChunkPart2RegV, firstChunkPart2RegV, tmpRegV1);
        /* 4.2 Convert 32-byte to 8-byte representation, 2 bits per byte. */
        /* Reduce from 256 -> 128 bits. */
        masm.neon.addpVVV(ASIMDSize.FullReg, ElementSize.Byte, firstChunkPart1RegV, firstChunkPart1RegV, firstChunkPart2RegV);
        /*
         * Reduce from 128 -> 64 bits. Note tmpRegV1's value doesn't matter; only care about
         * chunkPart1RegV.
         */
        masm.neon.addpVVV(ASIMDSize.FullReg, ElementSize.Byte, firstChunkPart1RegV, firstChunkPart1RegV, tmpRegV1);

        try (ScratchRegister scratchReg = masm.getScratchRegister()) {
            Register matchPositionReg = scratchReg.getRegister();
            /* Detect the byte starting position of matching element in the chunk. */
            masm.neon.moveFromIndex(ElementSize.DoubleWord, ElementSize.DoubleWord, matchPositionReg, firstChunkPart1RegV, 0);
            masm.rbit(64, matchPositionReg, matchPositionReg);
            masm.clz(64, matchPositionReg, matchPositionReg);
            masm.add(64, result, currOffset, matchPositionReg, ShiftType.ASR, 1);
        }
        if (shiftSize != 0) {
            /* Convert byte offset of searchElement to its array index */
            masm.asr(64, result, result, shiftSize);
        }

        masm.bind(end);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register result = asRegister(resultValue);
        Register arrayLength = asRegister(arrayLengthValue);
        Register fromIndex = asRegister(fromIndexValue);
        Register baseAddress = asRegister(temp1);
        Register searchLength = asRegister(temp2);

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
        masm.compare(64, searchLength, chunkByteSize / elementByteSize);
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
