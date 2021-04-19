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
import static org.graalvm.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_SIGNED_UNSCALED;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.CONST;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;

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
    @Alive({REG, STACK, CONST}) protected Value searchValue;
    @Temp({REG}) protected Value temp1;
    @Temp({REG}) protected Value temp2;
    @Temp({REG}) protected Value temp3;
    @Temp({REG}) protected Value temp4;

    public AArch64ArrayIndexOfOp(int arrayBaseOffset, JavaKind valueKind, LIRGeneratorTool tool, Value result, Value arrayPtr, Value arrayLength, Value fromIndex, Value... searchValues) {
        super(TYPE);
        assert byteMode(valueKind) || charMode(valueKind);
        assert searchValues.length == 1;
        this.arrayBaseOffset = arrayBaseOffset;
        this.isUTF16 = charMode(valueKind);
        resultValue = result;
        arrayPtrValue = arrayPtr;
        arrayLengthValue = arrayLength;
        fromIndexValue = fromIndex;
        searchValue = searchValues[0];
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
        Register curCharsOrTmp1 = asRegister(temp1);
        Register refPosIndex = asRegister(temp2);
        Register refPosAddress = asRegister(temp3);
        Register refCharCountOrNeg = asRegister(temp4);

        Label match = new Label();
        Label chunkedReadLoop = new Label();
        Label charInChunk = new Label();
        Label searchByChunk = new Label();
        Label searchByCharLoop = new Label();
        Label end = new Label();

        final int chunkSize = 8;
        int charSize = 1;
        int shiftSize = 0;
        long bitMask01 = 0x0101010101010101L;
        long bitMask7f = 0x7f7f7f7f7f7f7f7fL;
        if (isUTF16) {
            charSize = 2;
            shiftSize = 1;
            bitMask01 = 0x0001000100010001L;
            bitMask7f = 0x7fff7fff7fff7fffL;
        }

        // Return for empty strings
        masm.mov(result, -1);
        masm.cbz(64, arrayLength, end);

        // Set reference position at the beginning of the string
        masm.loadAddress(refPosAddress, AArch64Address.createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, asRegister(arrayPtrValue), arrayBaseOffset));

        // Search char-by-char for small strings (with size less than 64 bits,
        // i.e., 4 UTF-16 or 8 Latin1 chars) else search chunk-by-chunk
        masm.cmp(64, arrayLength, chunkSize / charSize);
        masm.branchConditionally(ConditionFlag.GE, searchByChunk);

        // Search sequentially for small strings
        masm.sub(64, refCharCountOrNeg, arrayLength, fromIndex);
        masm.mov(64, refPosIndex, arrayLength);
        // Set address of the reference position - at the end of the string, after the last char
        masm.adds(64, refPosAddress, refPosAddress, arrayLength, ShiftType.LSL, shiftSize);
        masm.subs(64, refCharCountOrNeg, zr, refCharCountOrNeg, ShiftType.LSL, shiftSize);
        // Loop doing char-by-char search
        masm.bind(searchByCharLoop);
        masm.ldr(charSize * Byte.SIZE, curCharsOrTmp1, AArch64Address.createRegisterOffsetAddress(refPosAddress, refCharCountOrNeg, false));
        masm.cmp(32, curCharsOrTmp1, searchChar);
        masm.branchConditionally(ConditionFlag.EQ, match);

        masm.adds(64, refCharCountOrNeg, refCharCountOrNeg, charSize);
        masm.branchConditionally(ConditionFlag.LT, searchByCharLoop);
        masm.jmp(end);

        // Search chunk-by-chunk for long strings
        masm.bind(searchByChunk);
        /*
        * @formatter:off
        *  Chunk-based reading uses the following approach (with example for UTF-16):
        *  1. Fill 8 byte chunk with search character ('searchChar'): 0x000000000000char -> 0xcharcharcharchar
        *  2. Read and compare string chunk-by-chunk.
        *   2.1 Store reference position at the beginning of the last chunk ('refPosIndex'). This ensures that we don't
        *   read beyond string boundary.
        *   2.2 Count number of bytes to read ('refCharCount') between 'fromIndex' to 'refPosIndex'.
        *   2.3 Use negative count (-refCharCount/refCharCountNeg) and add chunk-size (8 bytes) after each read until the count is positive.
        *  3. Check each read chunk to see if 'searchChar' is found.
        *   3.1 XOR read chunk with 0xcharcharcharchar. (e.g., 0x----char-------- XOR 0xcharcharcharchar -> 0x----0000--------)
        *   Any matching bytes are now 0 and can be detected by using the steps from 3.2 to 3.5 (based on [1]). Here, we set
        *   the MSB of a UTF-16 char in the chunk that matches 'searchChar'.
        *   3.2 Subtract 1 from all UTF-16 char in 3.1 above (T1). (0x----0000-------- - 0x0001000100010001 -> 0x----FFFF--------)
        *   3.3 Except MSB, mask all bits of UTF-16 char in 3.1 (T2). (0x----0000-------- OR 0x7FFF7FFF7FFF7FFF -> 0x----7FFF--------)
        *   3.4 Calculate T3 = T1 & ~(T2)  (0x----FFFF-------- AND (NOT 0x----7FFF--------) -> 0x0000800000000000, sets Z flag to 0).
        *   3.5 Check if Z flag is zero using NE condition flag.
        *  4. Repeat the above steps until 'refCharCountNeg' is positive. Then reset the positive 'refCharCountNeg' to zero so that
        *   the next read operation would read the last chunk. This avoids reading beyond the string's boundary.
        *  5. If character is found in a chunk then find its position. Calculate number zero chars after the matching
        *     char so that we get the position relative to reference position 'refPosIndex'.
        *   5.1 Reverse the bitstream in T3 (0x0000800000000000 -> 0x0000000000010000)
        *   5.2 Calculate leading zeros in 4.1 and get number of bytes from the end of the chunk
        *       (by dividing the count by 8).
        *   5.3 Retrieve the position of 'searchChar' by combining offset within chunk, chunk offset and string length.
        *
        *  [1] https://graphics.stanford.edu/~seander/bithacks.html#ZeroInWord
        * @formatter:on
        * */

        // 1. Repeat the searchChar
        if (!isUTF16) {
            masm.or(64, searchChar, searchChar, searchChar, ShiftType.LSL, Byte.SIZE);
        }
        masm.or(64, searchChar, searchChar, searchChar, ShiftType.LSL, Byte.SIZE * 2);
        masm.or(64, searchChar, searchChar, searchChar, ShiftType.LSL, Byte.SIZE * 4);

        // 2.1 Set address of the reference position - at the starting position of the last chunk
        masm.sub(64, refPosIndex, arrayLength, chunkSize / charSize);
        masm.shl(64, refCharCountOrNeg, refPosIndex, shiftSize);
        masm.adds(64, refPosAddress, refPosAddress, refCharCountOrNeg);
        masm.sub(64, refCharCountOrNeg, refCharCountOrNeg, fromIndex, ShiftType.LSL, shiftSize);
        masm.subs(64, refCharCountOrNeg, zr, refCharCountOrNeg);

        try (ScratchRegister scratchReg1 = masm.getScratchRegister(); ScratchRegister scratchReg2 = masm.getScratchRegister()) {
            Register bitMask01Reg = scratchReg1.getRegister();
            Register tmp2 = scratchReg2.getRegister();
            masm.mov(bitMask01Reg, bitMask01);

            // 3. Find searchChar in the 8-byte chunk
            masm.bind(chunkedReadLoop);
            masm.ldr(64, curCharsOrTmp1, AArch64Address.createRegisterOffsetAddress(refPosAddress, refCharCountOrNeg, false));
            masm.eor(64, curCharsOrTmp1, searchChar, curCharsOrTmp1);
            masm.or(64, tmp2, curCharsOrTmp1, bitMask7f);
            masm.sub(64, curCharsOrTmp1, curCharsOrTmp1, bitMask01Reg);
            masm.bics(64, curCharsOrTmp1, curCharsOrTmp1, tmp2);
            masm.branchConditionally(ConditionFlag.NE, charInChunk);
            masm.adds(64, refCharCountOrNeg, refCharCountOrNeg, chunkSize);
            masm.branchConditionally(ConditionFlag.LT, chunkedReadLoop);

            // 4. Reset the ref chars count to zero to read the last chunk
            masm.cmp(32, refCharCountOrNeg, chunkSize);
            masm.mov(refCharCountOrNeg, 0);
            masm.branchConditionally(ConditionFlag.LT, chunkedReadLoop);
            masm.jmp(end);

            // 5. The searchChar found in chunk so retrieve its position relative to ref
            masm.bind(charInChunk);
            masm.rev(64, curCharsOrTmp1, curCharsOrTmp1);
            masm.clz(64, curCharsOrTmp1, curCharsOrTmp1);
            // 5.2 Convert number of leading zeros to number zero bytes
            masm.add(64, refCharCountOrNeg, refCharCountOrNeg, curCharsOrTmp1, ShiftType.LSR, 3);
        }

        masm.bind(match);
        if (isUTF16) {
            // Convert byte offset of searchChar to its char index in UTF-16 string
            masm.ashr(64, refCharCountOrNeg, refCharCountOrNeg, 1);
        }
        masm.add(64, result, refPosIndex, refCharCountOrNeg);
        masm.bind(end);
    }
}
