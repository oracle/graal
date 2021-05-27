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
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDMacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ShiftType;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

@Opcode("AArch64_ARRAY_INDEX_OF")
public final class AArch64ArrayIndexOfOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64ArrayIndexOfOp> TYPE = LIRInstructionClass.create(AArch64ArrayIndexOfOp.class);

    private final boolean isUTF16;
    private final boolean findTwoConsecutive;
    private final int arrayBaseOffset;

    @Def({REG}) protected Value resultValue;
    @Alive({REG}) protected Value arrayPtrValue;
    @Alive({REG}) protected Value arrayLengthValue;
    @Alive({REG}) protected Value fromIndexValue;
    @Alive({REG}) protected Value searchValue;
    @Temp({REG}) protected Value temp1;
    @Temp({REG}) protected Value temp2;
    @Temp({REG}) protected Value temp3;
    @Temp({REG}) protected Value temp4;
    @Temp({REG}) protected Value temp5;
    @Temp({REG}) protected Value vectorTemp1;
    @Temp({REG}) protected Value vectorTemp2;
    @Temp({REG}) protected Value vectorTemp3;
    @Temp({REG}) protected Value vectorTemp4;
    @Temp({REG}) protected Value vectorTemp5;
    @Temp({REG}) protected Value vectorTemp6;
    @Temp({REG}) protected Value vectorTemp7;

    public AArch64ArrayIndexOfOp(int arrayBaseOffset, JavaKind valueKind, boolean findTwoConsecutive, LIRGeneratorTool tool, Value result, Value arrayPtr, Value arrayLength, Value fromIndex,
                    Value searchValue) {
        super(TYPE);
        assert byteMode(valueKind) || charMode(valueKind);
        this.arrayBaseOffset = arrayBaseOffset;
        this.isUTF16 = charMode(valueKind);
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
        vectorTemp5 = tool.newVariable(vectorKind);
        vectorTemp6 = tool.newVariable(vectorKind);
        vectorTemp7 = tool.newVariable(vectorKind);
    }

    private static boolean byteMode(JavaKind kind) {
        return kind == JavaKind.Byte;
    }

    private static boolean charMode(JavaKind kind) {
        return kind == JavaKind.Char;
    }

    private void emitScalarCode(AArch64MacroAssembler masm, Register curIndex, Register baseAddress) {
        Register result = asRegister(resultValue);
        Register arrayLength = asRegister(arrayLengthValue);
        Register searchChar = asRegister(searchValue);
        Register curChar = asRegister(temp3);
        Register endIndex = asRegister(temp4);

        Label match = new Label();
        Label searchByCharLoop = new Label();
        Label end = new Label();

        final int charsToRead = findTwoConsecutive ? 2 : 1;
        int charSize;
        int shiftSize;
        if (!isUTF16) {
            /* 1-byte characters */
            charSize = 1;
            shiftSize = 0;
        } else {
            /* 2-byte characters */
            charSize = 2;
            shiftSize = 1;
        }

        /*
         * While searching for one character (not two consecutive), search sequentially if the
         * target string is small. Note that all indexing is done via a negative offset from the
         * first position beyond the end of the array.
         */
        masm.mov(64, endIndex, arrayLength);
        /* Set the base address to be at the end of the string, after the last char */
        masm.add(64, baseAddress, baseAddress, arrayLength, ShiftType.LSL, shiftSize);
        /* Initial index is (fromIndex - arrayLength) << shift size */
        masm.sub(64, curIndex, zr, curIndex, ShiftType.LSL, shiftSize);
        /* Loop doing char-by-char search */
        masm.bind(searchByCharLoop);
        final int charBitSize = charsToRead * charSize * Byte.SIZE;
        masm.ldr(charBitSize, curChar, AArch64Address.createRegisterOffsetAddress(charBitSize, baseAddress, curIndex, false));
        masm.cmp(32, curChar, searchChar);
        masm.branchConditionally(ConditionFlag.EQ, match);

        /*
         * Add charSize to the curIndex and retry if the end of the array has not yet been reached,
         * i.e., the curIndex is still < 0. While searching for two consecutive chars, stop a char
         * earlier to avoid reading past the array, i.e, retry as long as curIndex < -charSize.
         */
        if (findTwoConsecutive) {
            masm.add(64, curIndex, curIndex, charSize);
            masm.compare(64, curIndex, -charSize);
            masm.branchConditionally(ConditionFlag.LT, searchByCharLoop);
        } else {
            masm.adds(64, curIndex, curIndex, charSize);
            masm.branchConditionally(ConditionFlag.MI, searchByCharLoop);
        }
        masm.jmp(end);

        masm.bind(match);
        if (isUTF16) {
            /* Convert byte offset of searchChar to its char index in UTF-16 string */
            masm.asr(64, curIndex, curIndex, 1);
        }
        masm.add(64, result, endIndex, curIndex);

        masm.bind(end);
    }

    private void emitSIMDCode(AArch64MacroAssembler masm, Register baseAddress) {
        /*
         * @formatter:off
         *  Chunk-based reading uses the following approach (with example for UTF-16) inspired from [1]. For SIMD implementation, the steps
         *  represent computation applied to one lane of 8 bytes. SIMD version replicates the following steps to all 4 lanes of 8 bytes each.
         *  1. Fill 8-byte chunk with search character ('searchChar'): 0x000000000000char -> 0xcharcharcharchar
         *  2. Read and compare string chunk-by-chunk.
         *   2.1 Store end index at the beginning of the last chunk ('refAddress'). This ensures that we don't read beyond string boundary.
         *   2.2 String is processed in chunks: the first unaligned chunk (head) -> all 32-byte aligned chunks -> the last unaligned chunk
         *   of 32 bytes (tail). Transitions between processing an unaligned and aligned chunk repeats char search on overlapping part.
         *   However, overhead gets smaller with longer strings and compensated by the gains from using larger (32-byte sized) chunks.
         *   2.3 After processing each chunk, adjust the address 4-byte aligned to read the next chunk. Repeat this step until the last
         *   chunk is reached (address of the next chunk >= 'refAddress').
         *   2.4 On reaching the last chunk, reset the address to read the last chunk.
         *  3. Check each read chunk to see if 'searchChar' is found.
         *   3.1 XOR read chunk with 0xcharcharcharchar. (e.g., 0x----char-------- XOR 0xcharcharcharchar -> 0x----0000--------)
         *   Any matching bytes are now 0 and can be detected by using the steps from 3.2 to 3.5 (based on [2]). Here, we set
         *   the MSB of a UTF-16 char in the chunk that matches 'searchChar'.
         *   3.2 Subtract 1 from all UTF-16 char in 3.1 above (T1). (0x----0000-------- - 0x0001000100010001 -> 0x----FFFF--------)
         *   3.3 Except MSB, mask all bits of UTF-16 char in 3.1 (T2). (0x----0000-------- OR 0x7FFF7FFF7FFF7FFF -> 0x----7FFF--------)
         *   3.4 Calculate T3 = T1 & ~(T2)  (0x----FFFF-------- AND (NOT 0x----7FFF--------) -> 0x0000800000000000, sets Z flag to 0).
         *   3.5 Steps 3.1 to 3.4 would detect a match in a SIMD lane (of 8 bytes). All the lanes are OR-ed to detect a match in the chunk
         *   of 32 bytes.
         *  4. If character is found in a 32-byte chunk then find its position. Use 2-bit representation for each byte to detect the index
         *     of matching char. Set those bits to '11' if the match is found. Then calculate number zero chars after the matching char to
         *     get the position in the chunk.
         *   4.1 For each lane, set all the bits where the char is found using T4 = cmhi(T3) (0x0000800000000000 -> 0x0000FFFF00000000).
         *   4.2 AND T4 with magic constant to set 2 bits based on the position of the char in the chunk.
         *   T5 = T4 & 0xc000_0300_00c0_0003L (0x0000FFFF00000000 -> 0x0000030000000000)
         *   4.3 Perform pairwise addition on adjecent elements/chars twice to convert 32-byte to 8-byte representation where each pair of
         *    bits represents its corresponding char in a chunk. Magic constant sets different bits for each position that ensures the
         *    pairwise addition would not overflow when consecutive chars are matched as well as preserve the position of matching char.
         *   4.4 Reverse the bitstream in T5 (0x0000030000000000 -> 0x0000000000c00000)
         *   4.5 Calculate leading zeros in T5 and divide the count by 2 to get the offset of matching char in a chunk
         *   4.6 Retrieve the position of 'searchChar' by adding offset within chunk to the index of the chunk.
         *
         *  [1] https://github.com/ARM-software/optimized-routines/blob/master/string/aarch64/strchr.S
         *  [2] https://graphics.stanford.edu/~seander/bithacks.html#ZeroInWord
         * @formatter:on
         * */

        Register result = asRegister(resultValue);
        Register arrayLength = asRegister(arrayLengthValue);
        Register fromIndex = asRegister(fromIndexValue);
        Register searchChar = asRegister(searchValue);
        Register currOffset = asRegister(temp2);
        Register endOfString = asRegister(temp3);
        Register refAddress = asRegister(temp4);
        Register chunkToReadAddress = asRegister(temp5);
        Register bitMask01RegV = asRegister(vectorTemp1);
        Register bitMask7fRegV = asRegister(vectorTemp2);
        Register searchCharRegV = asRegister(vectorTemp3);
        Register chunkPart1RegV = asRegister(vectorTemp4);
        Register chunkPart2RegV = asRegister(vectorTemp5);
        Register tmpRegV1 = asRegister(vectorTemp6);
        Register tmpRegV2 = asRegister(vectorTemp7);

        /* Register aliases */
        Register magicConstantRegV = tmpRegV1;
        Register pairwiseSumRegV = tmpRegV1;

        Label matchInChunk = new Label();
        Label searchByChunkLoopHead = new Label();
        Label searchByChunkLoopTail = new Label();
        Label processTail = new Label();
        Label end = new Label();

        int bitMask01;
        int bitMask7f;
        ElementSize elementSize;
        /*
         * Magic constant is used to detect position of the search character in the matching chunk
         */
        long magicConstant;
        if (!isUTF16) {
            /* 1-byte characters */
            bitMask01 = 0x0101;
            bitMask7f = 0x7f7f;
            elementSize = ElementSize.Byte;
            magicConstant = 0xc030_0c03_c030_0c03L;
        } else {
            /* 2-byte characters */
            bitMask01 = 0x0001;
            bitMask7f = 0x7fff;
            elementSize = ElementSize.HalfWord;
            magicConstant = 0xc000_0300_00c0_0003L;
        }

        AArch64ASIMDMacroAssembler simdMasm = new AArch64ASIMDMacroAssembler(masm);
        try (ScratchRegister scratchReg1 = masm.getScratchRegister(); ScratchRegister scratchReg2 = masm.getScratchRegister()) {
            Register bitMask01Reg = scratchReg1.getRegister();
            Register bitMask7fReg = scratchReg2.getRegister();

            masm.mov(bitMask01Reg, bitMask01);
            masm.mov(bitMask7fReg, bitMask7f);
            simdMasm.dupVG(ASIMDSize.FullReg, elementSize, bitMask01RegV, bitMask01Reg);
            simdMasm.dupVG(ASIMDSize.FullReg, elementSize, bitMask7fRegV, bitMask7fReg);
        }
        /* 1. Duplicate the searchChar to 32-bytes */
        simdMasm.dupVG(ASIMDSize.FullReg, elementSize, searchCharRegV, searchChar);
        /*
         * 2.1 Set endOfString pointing to byte next to the last valid char in the string and
         * 'refAddress' pointing to the beginning of the last chunk.
         */
        masm.add(64, endOfString, baseAddress, arrayLength, ShiftType.LSL, isUTF16 ? 1 : 0);
        masm.bic(64, refAddress, endOfString, 31L);
        /* Set 'chunkToReadAddress' pointing to the chunk from where the search begins. */
        if (isUTF16) {
            masm.add(64, chunkToReadAddress, baseAddress, fromIndex, ShiftType.LSL, 1);
        } else {
            masm.add(64, chunkToReadAddress, baseAddress, fromIndex);
        }

        masm.bind(searchByChunkLoopHead);
        masm.cmp(64, refAddress, chunkToReadAddress);
        masm.branchConditionally(ConditionFlag.LE, processTail);
        masm.bind(searchByChunkLoopTail);
        masm.sub(64, currOffset, chunkToReadAddress, baseAddress);

        /*
         * TODO: Combine the following 4 instructions to load a 32-byte chunk into 1 instruction
         * equivalent to 'LD1 {chunkPart1RegV.16b, chunkPart2RegV.16b}, [chunkToReadAddress], #32'
         */
        simdMasm.loadV1(ASIMDSize.FullReg, chunkPart1RegV, chunkToReadAddress, true);
        simdMasm.loadV1(ASIMDSize.FullReg, chunkPart2RegV, chunkToReadAddress, true);
        /* 3. Find searchChar in the 32-byte chunk */
        simdMasm.eorVVV(ASIMDSize.FullReg, chunkPart1RegV, chunkPart1RegV, searchCharRegV);
        simdMasm.eorVVV(ASIMDSize.FullReg, chunkPart2RegV, chunkPart2RegV, searchCharRegV);
        simdMasm.orrVVV(ASIMDSize.FullReg, tmpRegV1, chunkPart1RegV, bitMask7fRegV);
        simdMasm.orrVVV(ASIMDSize.FullReg, tmpRegV2, chunkPart2RegV, bitMask7fRegV);
        simdMasm.subVVV(ASIMDSize.FullReg, elementSize, chunkPart1RegV, chunkPart1RegV, bitMask01RegV);
        simdMasm.subVVV(ASIMDSize.FullReg, elementSize, chunkPart2RegV, chunkPart2RegV, bitMask01RegV);
        simdMasm.bicVVV(ASIMDSize.FullReg, chunkPart1RegV, chunkPart1RegV, tmpRegV1);
        simdMasm.bicVVV(ASIMDSize.FullReg, chunkPart2RegV, chunkPart2RegV, tmpRegV2);

        try (ScratchRegister scratchReg1 = masm.getScratchRegister(); ScratchRegister scratchReg2 = masm.getScratchRegister()) {
            Register pairwiseSumPart1 = scratchReg1.getRegister();
            Register pairwiseSumPart2 = scratchReg2.getRegister();
            simdMasm.orrVVV(ASIMDSize.FullReg, pairwiseSumRegV, chunkPart1RegV, chunkPart2RegV);
            simdMasm.moveFromIndex(ElementSize.DoubleWord, ElementSize.DoubleWord, pairwiseSumPart1, pairwiseSumRegV, 0);
            simdMasm.moveFromIndex(ElementSize.DoubleWord, ElementSize.DoubleWord, pairwiseSumPart2, pairwiseSumRegV, 1);
            masm.orr(64, pairwiseSumPart1, pairwiseSumPart1, pairwiseSumPart2);
            masm.cbnz(64, pairwiseSumPart1, matchInChunk);
            masm.bic(64, chunkToReadAddress, chunkToReadAddress, 31);
            masm.jmp(searchByChunkLoopHead);
        }

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

        /* 4. If the character is found in a 32-byte chunk then find its position. */
        masm.bind(matchInChunk);
        /*
         * 4.1 Set all the bits for the matching char. Currently, the MSB is set for the matching
         * positions. Thus, the element at matching position is negative and checking if element < 0
         * would set all the corresponding bits.
         */
        simdMasm.cmltZeroVV(ASIMDSize.FullReg, elementSize, chunkPart1RegV, chunkPart1RegV);
        simdMasm.cmltZeroVV(ASIMDSize.FullReg, elementSize, chunkPart2RegV, chunkPart2RegV);
        /*
         * 4.2 Using the magic constant set 2 bits in the matching byte that represent its position
         * in the chunk
         */
        try (ScratchRegister scratchReg = masm.getScratchRegister()) {
            Register magicConstantReg = scratchReg.getRegister();
            masm.mov(magicConstantReg, magicConstant);
            simdMasm.dupVG(ASIMDSize.FullReg, ElementSize.DoubleWord, magicConstantRegV, magicConstantReg);
        }
        simdMasm.andVVV(ASIMDSize.FullReg, chunkPart1RegV, chunkPart1RegV, magicConstantRegV);
        simdMasm.andVVV(ASIMDSize.FullReg, chunkPart2RegV, chunkPart2RegV, magicConstantRegV);
        /* 4.3 Convert 32-byte to 8-byte representation, 2 bits per byte. */
        simdMasm.addpVV(ASIMDSize.FullReg, elementSize, chunkPart1RegV, chunkPart1RegV, chunkPart2RegV);
        simdMasm.addpVV(ASIMDSize.FullReg, elementSize, chunkPart1RegV, chunkPart1RegV, chunkPart2RegV);

        try (ScratchRegister scratchReg = masm.getScratchRegister()) {
            Register matchPositionReg = scratchReg.getRegister();
            /* Detect the position of matching char in the chunk */
            simdMasm.moveFromIndex(ElementSize.DoubleWord, ElementSize.DoubleWord, matchPositionReg, chunkPart1RegV, 0);
            masm.rbit(64, matchPositionReg, matchPositionReg);
            masm.clz(64, matchPositionReg, matchPositionReg);
            masm.add(64, result, currOffset, matchPositionReg, ShiftType.ASR, 1);
        }
        if (isUTF16) {
            /* Convert byte offset of searchChar to its char index in UTF-16 string */
            masm.asr(64, result, result, 1);
        }

        masm.bind(end);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register result = asRegister(resultValue);
        Register arrayLength = asRegister(arrayLengthValue);
        Register fromIndex = asRegister(fromIndexValue);
        Register baseAddress = asRegister(temp1);
        Register curIndex = asRegister(temp2);

        Label searchByChunk = new Label();
        Label ret = new Label();

        final int chunkSize = 48;
        int charSize = isUTF16 ? 2 : 1;

        /*
         * @formatter:off
         * The arguments satisfy the following constraints:
         *  1. 0 <= fromIndex <= arrayLength - 1 (or 2 when findTwoConsecutive is true)
         *  2. number of characters in searchChar is 1 (or 2 when findTwoConsecutive is true)
         *  3. arrayLength - fromIndex >= 2 when findTwoConsecutive is true
         * @formatter:on
        */
        masm.mov(result, -1);   // Return for empty strings
        masm.cbz(64, arrayLength, ret);

        /* Load address of first array element */
        masm.add(64, baseAddress, asRegister(arrayPtrValue), arrayBaseOffset);

        /*
         * Search char-by-char for small strings (with search space size of less than 64 bits, i.e.,
         * 4 UTF-16 or 8 Latin1 chars) else search chunk-by-chunk.
         */
        masm.sub(64, curIndex, arrayLength, fromIndex);
        if (!findTwoConsecutive) {
            masm.compare(64, curIndex, chunkSize / charSize);
            masm.branchConditionally(ConditionFlag.GE, searchByChunk);
        }
        emitScalarCode(masm, curIndex, baseAddress);
        masm.jmp(ret);

        /* Search chunk-by-chunk for long strings */
        masm.bind(searchByChunk);
        emitSIMDCode(masm, baseAddress);

        masm.bind(ret);
    }
}
