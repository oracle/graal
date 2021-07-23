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

import org.graalvm.compiler.asm.Label;
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

    public AArch64ArrayIndexOfOp(int arrayBaseOffset, JavaKind valueKind, LIRGeneratorTool tool, Value result, Value arrayPtr, Value arrayLength, Value fromIndex, Value searchValue) {
        super(TYPE);
        assert byteMode(valueKind) || charMode(valueKind);
        this.arrayBaseOffset = arrayBaseOffset;
        this.isUTF16 = charMode(valueKind);
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
    }

    private static boolean byteMode(JavaKind kind) {
        return kind == JavaKind.Byte;
    }

    private static boolean charMode(JavaKind kind) {
        return kind == JavaKind.Char;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register result = asRegister(resultValue);
        Register arrayLength = asRegister(arrayLengthValue);
        Register fromIndex = asRegister(fromIndexValue);
        Register searchChar = asRegister(searchValue);
        Register curChar = asRegister(temp1);
        Register endIndex = asRegister(temp2);
        Register baseAddress = asRegister(temp3);
        Register curIndex = asRegister(temp4);

        Label match = new Label();
        Label chunkedReadLoop = new Label();
        Label charInChunk = new Label();
        Label searchByChunk = new Label();
        Label searchByCharLoop = new Label();
        Label end = new Label();

        final int chunkSize = 8;
        int charSize;
        int shiftSize;
        long bitMask01;
        long bitMask7f;
        if (!isUTF16) {
            // 1-byte characters
            charSize = 1;
            shiftSize = 0;
            bitMask01 = 0x0101010101010101L;
            bitMask7f = 0x7f7f7f7f7f7f7f7fL;
        } else {
            // 2-byte characters
            charSize = 2;
            shiftSize = 1;
            bitMask01 = 0x0001000100010001L;
            bitMask7f = 0x7fff7fff7fff7fffL;
        }

        // Return for empty strings
        masm.mov(result, -1);
        masm.cbz(64, arrayLength, end);

        // Load address of first array element
        masm.add(64, baseAddress, asRegister(arrayPtrValue), arrayBaseOffset);

        /*
         * Search char-by-char for small strings (with search space size of less than 64 bits, i.e.,
         * 4 UTF-16 or 8 Latin1 chars) else search chunk-by-chunk.
         */
        masm.sub(64, curIndex, arrayLength, fromIndex);
        masm.cmp(64, curIndex, chunkSize / charSize);
        masm.branchConditionally(ConditionFlag.GE, searchByChunk);

        /*
         * Search sequentially for small strings. Note that all indexing is done via a negative
         * offset from the first position beyond the end of the array.
         */
        masm.mov(64, endIndex, arrayLength);
        // Set the base address to be at the end of the string, after the last char
        masm.add(64, baseAddress, baseAddress, arrayLength, ShiftType.LSL, shiftSize);
        // Initial index is (fromIndex - arrayLength) << shift size
        masm.sub(64, curIndex, zr, curIndex, ShiftType.LSL, shiftSize);
        // Loop doing char-by-char search
        masm.bind(searchByCharLoop);
        int charBitSize = charSize * Byte.SIZE;
        masm.ldr(charBitSize, curChar, AArch64Address.createRegisterOffsetAddress(charBitSize, baseAddress, curIndex, false));
        masm.cmp(32, curChar, searchChar);
        masm.branchConditionally(ConditionFlag.EQ, match);

        /*
         * Add charSize to the curIndex, and retry if the end of the array has not yet been reached
         * (i.e. the curIndex is still < 0).
         */
        masm.adds(64, curIndex, curIndex, charSize);
        masm.branchConditionally(ConditionFlag.MI, searchByCharLoop);
        masm.jmp(end);

        // Search chunk-by-chunk for long strings
        masm.bind(searchByChunk);
        /*
        * @formatter:off
        *  Chunk-based reading uses the following approach (with example for UTF-16):
        *  1. Fill 8 byte chunk with search character ('searchChar'): 0x000000000000char -> 0xcharcharcharchar
        *  2. Read and compare string chunk-by-chunk.
        *   2.1 Store end index at the beginning of the last chunk ('endIndex'). This ensures that we don't
        *   read beyond string boundary.
        *   2.2 Use non-positive index ('curIndex') and add chunk-size (8 bytes) after each read until the count is positive.
        *  3. Check each read chunk to see if 'searchChar' is found.
        *   3.1 XOR read chunk with 0xcharcharcharchar. (e.g., 0x----char-------- XOR 0xcharcharcharchar -> 0x----0000--------)
        *   Any matching bytes are now 0 and can be detected by using the steps from 3.2 to 3.5 (based on [1]). Here, we set
        *   the MSB of a UTF-16 char in the chunk that matches 'searchChar'.
        *   3.2 Subtract 1 from all UTF-16 char in 3.1 above (T1). (0x----0000-------- - 0x0001000100010001 -> 0x----FFFF--------)
        *   3.3 Except MSB, mask all bits of UTF-16 char in 3.1 (T2). (0x----0000-------- OR 0x7FFF7FFF7FFF7FFF -> 0x----7FFF--------)
        *   3.4 Calculate T3 = T1 & ~(T2)  (0x----FFFF-------- AND (NOT 0x----7FFF--------) -> 0x0000800000000000, sets Z flag to 0).
        *   3.5 Check if Z flag is zero using NE condition flag.
        *  4. Repeat the above steps until 'curIndex' is positive. Then reset the positive 'curIndex' to zero so that
        *   the next read operation would read the last chunk. This avoids reading beyond the string's boundary.
        *  5. If character is found in a chunk then find its position. Calculate number zero chars after the matching
        *     char so that we get the position relative to reference position 'endIndex'.
        *   5.1 Reverse the bitstream in T3 (0x0000800000000000 -> 0x0000000000010000)
        *   5.2 Calculate leading zeros in 4.1 and get number of bytes from the end of the chunk
        *       (by dividing the count by 8).
        *   5.3 Retrieve the position of 'searchChar' by adding offset within chunk to the current index.
        *
        *  [1] https://graphics.stanford.edu/~seander/bithacks.html#ZeroInWord
        * @formatter:on
        * */

        // 1. Duplicate the searchChar to 64-bits
        if (!isUTF16) {
            masm.orr(64, searchChar, searchChar, searchChar, ShiftType.LSL, Byte.SIZE);
        }
        masm.orr(64, searchChar, searchChar, searchChar, ShiftType.LSL, Byte.SIZE * 2);
        masm.orr(64, searchChar, searchChar, searchChar, ShiftType.LSL, Byte.SIZE * 4);

        // 2.1 Set end index at the starting position of the last chunk
        masm.sub(64, endIndex, arrayLength, chunkSize / charSize);
        masm.add(64, baseAddress, baseAddress, endIndex, ShiftType.LSL, shiftSize);
        /*
         * Set the current index (will be <= 0). Note that curIndex is not aligned to the chunk
         * size.
         */
        masm.sub(64, curIndex, fromIndex, endIndex);
        if (isUTF16) {
            masm.lsl(64, curIndex, curIndex, 1);
        }

        try (ScratchRegister scratchReg1 = masm.getScratchRegister(); ScratchRegister scratchReg2 = masm.getScratchRegister()) {
            Register bitMask01Reg = scratchReg1.getRegister();
            Register tmp2 = scratchReg2.getRegister();
            masm.mov(bitMask01Reg, bitMask01);

            // 3. Find searchChar in the 8-byte chunk
            masm.bind(chunkedReadLoop);
            masm.ldr(64, curChar, AArch64Address.createRegisterOffsetAddress(64, baseAddress, curIndex, false));
            masm.eor(64, curChar, searchChar, curChar);
            masm.orr(64, tmp2, curChar, bitMask7f);
            masm.sub(64, curChar, curChar, bitMask01Reg);
            masm.bics(64, curChar, curChar, tmp2);
            masm.branchConditionally(ConditionFlag.NE, charInChunk);
            // move on to next chunk if no match found and new curIndex is < 0
            masm.adds(64, curIndex, curIndex, chunkSize);
            masm.branchConditionally(ConditionFlag.MI, chunkedReadLoop);

            /*
             * 4. Reset the ref chars count to zero to read the last chunk, only if the chunk was
             * not already read (i.e. the curIndex is not the chunk size). Because curIndex is not
             * aligned to the chunk size, it likely that some indexes searched again; however, this
             * is not a correctness problem and will not result in more than one iteration of
             * chunkedReadLoop being executed.
             */
            masm.cmp(32, curIndex, chunkSize);
            masm.branchConditionally(ConditionFlag.EQ, end);
            masm.mov(curIndex, 0);
            masm.jmp(chunkedReadLoop);

            /*
             * 5. The searchChar found in chunk so retrieve its position relative to the current
             * index.
             */
            masm.bind(charInChunk);
            masm.rev(64, curChar, curChar);
            masm.clz(64, curChar, curChar);
            /*
             * 5.3 Convert number of leading zeros to number of zero bytes and add to current index.
             */
            masm.add(64, curIndex, curIndex, curChar, ShiftType.LSR, 3);
        }

        masm.bind(match);
        if (isUTF16) {
            // Convert byte offset of searchChar to its char index in UTF-16 string
            masm.asr(64, curIndex, curIndex, 1);
        }
        masm.add(64, result, endIndex, curIndex);
        masm.bind(end);
    }
}
