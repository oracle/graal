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
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ShiftType;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;

@Opcode("AArch64_ARRAY_INDEX_OF")
public final class AArch64ArrayIndexOfOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64ArrayIndexOfOp> TYPE = LIRInstructionClass.create(AArch64ArrayIndexOfOp.class);

    private final boolean findTwoConsecutive;
    private final int arrayBaseOffset;
    private final int elementByteSize;

    @Def({REG}) protected AllocatableValue resultValue;
    @Alive({REG}) protected AllocatableValue arrayPtrValue;
    @Alive({REG}) protected AllocatableValue arrayLengthValue;
    @Alive({REG}) protected AllocatableValue fromIndexValue;
    @Alive({REG}) protected AllocatableValue searchValue;
    @Temp({REG}) protected AllocatableValue temp1;
    @Temp({REG}) protected AllocatableValue temp2;
    @Temp({REG}) protected AllocatableValue temp3;
    @Temp({REG}) protected AllocatableValue temp4;
    @Temp({REG}) protected AllocatableValue temp5;
    @Temp({REG}) protected AllocatableValue vectorTemp1;
    @Temp({REG}) protected AllocatableValue vectorTemp2;
    @Temp({REG}) protected AllocatableValue vectorTemp3;
    @Temp({REG}) protected AllocatableValue vectorTemp4;

    public AArch64ArrayIndexOfOp(int arrayBaseOffset, JavaKind valueKind, boolean findTwoConsecutive, LIRGeneratorTool tool, AllocatableValue result, AllocatableValue arrayPtr,
                    AllocatableValue arrayLength, AllocatableValue fromIndex, AllocatableValue searchValue) {
        super(TYPE);
        this.arrayBaseOffset = arrayBaseOffset;
        this.elementByteSize = getElementByteSize(valueKind);
        this.findTwoConsecutive = findTwoConsecutive;
        resultValue = result;
        arrayPtrValue = arrayPtr;
        arrayLengthValue = arrayLength;
        fromIndexValue = fromIndex;
        this.searchValue = searchValue;
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
    }

    private static int getElementByteSize(JavaKind kind) {
        switch (kind) {
            case Byte:
                return 1;
            case Char:
                return 2;
            case Int:
                return 4;
            default:
                throw GraalError.shouldNotReachHere("Unexpected JavaKind");
        }
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

    private void emitScalarCode(AArch64MacroAssembler masm, Register searchLength, Register baseAddress) {
        Register result = asRegister(resultValue);
        Register arrayLength = asRegister(arrayLengthValue);
        Register curValue = asRegister(temp3);

        Label match = new Label();
        Label searchByElementLoop = new Label();
        Label end = new Label();

        int shiftSize = getShiftSize();

        /*
         * AArch64 comparisons are at minimum 32 bits; since we are comparing against a
         * zero-extended value, the searchValue must also be zero-extended.
         */
        final int memAccessSize = (findTwoConsecutive ? 2 : 1) * elementByteSize * Byte.SIZE;
        Register searchValueReg;
        int compareSize;
        if (memAccessSize < 32) {
            compareSize = 32;
            searchValueReg = asRegister(temp4);
            masm.and(32, searchValueReg, asRegister(searchValue), NumUtil.getNbitNumberLong(memAccessSize));
        } else {
            compareSize = memAccessSize;
            searchValueReg = asRegister(searchValue);
        }

        /*
         * Searching the array sequentially for the target value. Note that all indexing is done via
         * a negative offset from the first position beyond the end of search region.
         */
        Register endIndex;
        if (findTwoConsecutive) {
            /* The end of the region is the second to last element in the array */
            endIndex = asRegister(temp5);
            masm.sub(64, endIndex, arrayLength, 1);
            /* Adjust search length accordingly */
            masm.sub(64, searchLength, searchLength, 1);
        } else {
            /* The end of the region is the last element in the array */
            endIndex = arrayLength;
        }
        /*
         * Set the base address to be at the end of the region baseAddress + (endIndex << shift
         * size).
         */
        masm.add(64, baseAddress, baseAddress, endIndex, ShiftType.LSL, shiftSize);
        /* Initial index is -searchLength << shift size */
        Register curIndex = searchLength;
        masm.sub(64, curIndex, zr, curIndex, ShiftType.LSL, shiftSize);
        /* Loop doing element-by-element search */
        masm.align(16);
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
        masm.jmp(end);

        masm.align(16);
        masm.bind(match);
        /* index = endIndex + (curIndex >> shiftSize) */
        masm.add(64, result, endIndex, curIndex, ShiftType.ASR, shiftSize);

        masm.bind(end);
    }

    private void emitSIMDCode(AArch64MacroAssembler masm, Register baseAddress) {
        /*
         * @formatter:off
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
         *  [1] https://github.com/ARM-software/optimized-routines/blob/master/string/aarch64/strchr.S
         * @formatter:on
         * */

        Register result = asRegister(resultValue);
        Register arrayLength = asRegister(arrayLengthValue);
        Register fromIndex = asRegister(fromIndexValue);
        Register searchElement = asRegister(searchValue);
        Register currOffset = asRegister(temp2);
        Register endOfString = asRegister(temp3);
        Register refAddress = asRegister(temp4);
        Register chunkToReadAddress = asRegister(temp5);
        Register searchElementRegV = asRegister(vectorTemp1);
        Register chunkPart1RegV = asRegister(vectorTemp2);
        Register chunkPart2RegV = asRegister(vectorTemp3);
        Register tmpRegV1 = asRegister(vectorTemp4);

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

        /* 1. Duplicate the searchElement to 16-bytes */
        masm.neon.dupVG(ASIMDSize.FullReg, eSize, searchElementRegV, searchElement);
        /*
         * 2.1 Set endOfString pointing to byte next to the last valid element in the string and
         * 'refAddress' pointing to the beginning of the last chunk.
         */
        masm.add(64, endOfString, baseAddress, arrayLength, ShiftType.LSL, shiftSize);
        masm.bic(64, refAddress, endOfString, 31L);
        /* Set 'chunkToReadAddress' pointing to the chunk from where the search begins. */
        masm.add(64, chunkToReadAddress, baseAddress, fromIndex, ShiftType.LSL, shiftSize);

        masm.align(16);
        masm.bind(searchByChunkLoopHead);
        masm.cmp(64, refAddress, chunkToReadAddress);
        masm.branchConditionally(ConditionFlag.LE, processTail);
        masm.bind(searchByChunkLoopTail);
        masm.sub(64, currOffset, chunkToReadAddress, baseAddress);

        masm.fldp(128, chunkPart1RegV, chunkPart2RegV, AArch64Address.createImmediateAddress(128, AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, chunkToReadAddress, 32));
        /* 3. Find searchElement in the 32-byte chunk */
        masm.neon.cmeqVVV(ASIMDSize.FullReg, eSize, chunkPart1RegV, chunkPart1RegV, searchElementRegV);
        masm.neon.cmeqVVV(ASIMDSize.FullReg, eSize, chunkPart2RegV, chunkPart2RegV, searchElementRegV);
        /* Determining if there was a match. */
        /* Combine two registers into 1 register */
        masm.neon.orrVVV(ASIMDSize.FullReg, tmpRegV1, chunkPart1RegV, chunkPart2RegV);
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
        masm.bic(64, chunkToReadAddress, chunkToReadAddress, 31);
        masm.jmp(searchByChunkLoopHead);

        masm.align(16);
        masm.bind(processTail);
        masm.cmp(64, chunkToReadAddress, endOfString);
        masm.branchConditionally(ConditionFlag.GE, end);
        masm.sub(64, chunkToReadAddress, endOfString, 32);
        /*
         * Move back the 'endOfString' by 32-bytes because at the end of 'searchByChunkLoopTail',
         * the 'chunkToRead' would be reset to 32-byte aligned addressed. Thus, the
         * searchByChunkLoopHead would never using 'chunkToReadAddress' >= 'endOfString' condition.
         */
        masm.mov(64, endOfString, chunkToReadAddress);
        masm.jmp(searchByChunkLoopTail);

        /* 4. If the element is found in a 32-byte chunk then find its position. */
        masm.align(16);
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
        masm.neon.andVVV(ASIMDSize.FullReg, chunkPart1RegV, chunkPart1RegV, tmpRegV1);
        masm.neon.andVVV(ASIMDSize.FullReg, chunkPart2RegV, chunkPart2RegV, tmpRegV1);
        /* 4.2 Convert 32-byte to 8-byte representation, 2 bits per byte. */
        /* Reduce from 256 -> 128 bits. */
        masm.neon.addpVVV(ASIMDSize.FullReg, ElementSize.Byte, chunkPart1RegV, chunkPart1RegV, chunkPart2RegV);
        /*
         * Reduce from 128 -> 64 bits. Note tmpRegV1's value doesn't matter; only care about
         * chunkPart1RegV.
         */
        masm.neon.addpVVV(ASIMDSize.FullReg, ElementSize.Byte, chunkPart1RegV, chunkPart1RegV, tmpRegV1);

        try (ScratchRegister scratchReg = masm.getScratchRegister()) {
            Register matchPositionReg = scratchReg.getRegister();
            /* Detect the byte starting position of matching element in the chunk. */
            masm.neon.moveFromIndex(ElementSize.DoubleWord, ElementSize.DoubleWord, matchPositionReg, chunkPart1RegV, 0);
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

        Label ret = new Label();

        /*
         * @formatter:off
         * The arguments satisfy the following constraints:
         *  1. 0 <= fromIndex <= arrayLength - 1 (or 2 when findTwoConsecutive is true)
         *  2. number of characters in searchChar is 1 (or 2 when findTwoConsecutive is true)
         * @formatter:on
        */
        masm.mov(result, -1);   // Return for empty strings
        masm.cbz(64, arrayLength, ret);

        /* Load address of first array element */
        masm.add(64, baseAddress, asRegister(arrayPtrValue), arrayBaseOffset);

        /*
         * Search element-by-element for small arrays (with search space size of less than 32 bytes,
         * i.e., 4 UTF-16 or 8 Latin1 elements) else search chunk-by-chunk.
         */
        masm.sub(64, searchLength, arrayLength, fromIndex);
        if (findTwoConsecutive) {
            /* Currently only sequential search is supported while searching for two elements. */
            emitScalarCode(masm, searchLength, baseAddress);
        } else {
            Label searchByChunk = new Label();
            int chunkByteSize = 32;
            masm.compare(64, searchLength, chunkByteSize / elementByteSize);
            masm.branchConditionally(ConditionFlag.GE, searchByChunk);

            /* Search sequentially for short arrays */
            emitScalarCode(masm, searchLength, baseAddress);
            masm.jmp(ret);

            /* Search chunk-by-chunk for long arrays */
            masm.align(16);
            masm.bind(searchByChunk);
            emitSIMDCode(masm, baseAddress);
        }
        masm.bind(ret);
    }
}
