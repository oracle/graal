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
package jdk.graal.compiler.lir.aarch64;

import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD2_MULTIPLE_2R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD4_MULTIPLE_4R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createStructureImmediatePostIndexAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import java.util.Arrays;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ExtendType;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ShiftType;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool.ArrayIndexOfVariant;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

@Opcode("AArch64_ARRAY_INDEX_OF")
public final class AArch64ArrayIndexOfOp extends AArch64ComplexVectorOp {
    public static final LIRInstructionClass<AArch64ArrayIndexOfOp> TYPE = LIRInstructionClass.create(AArch64ArrayIndexOfOp.class);

    private final ArrayIndexOfVariant variant;
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
    @Temp({REG}) protected Value[] vectorTemp;

    public AArch64ArrayIndexOfOp(Stride stride, ArrayIndexOfVariant variant, LIRGeneratorTool tool,
                    AllocatableValue result, AllocatableValue arrayPtr, AllocatableValue arrayOffset, AllocatableValue arrayLength, AllocatableValue fromIndex,
                    AllocatableValue[] searchValues) {
        super(TYPE);

        int nValues = searchValues.length;
        GraalError.guarantee(result.getPlatformKind() == AArch64Kind.DWORD, "int value expected");
        GraalError.guarantee(arrayPtr.getPlatformKind() == AArch64Kind.QWORD, "pointer value expected");
        GraalError.guarantee(arrayOffset.getPlatformKind() == AArch64Kind.QWORD, "long value expected");
        GraalError.guarantee(arrayLength.getPlatformKind() == AArch64Kind.DWORD, "int value expected");
        GraalError.guarantee(fromIndex.getPlatformKind() == AArch64Kind.DWORD, "int value expected");
        if (variant == ArrayIndexOfVariant.Table) {
            GraalError.guarantee(searchValues.length == 1 && searchValues[0].getPlatformKind() == AArch64Kind.QWORD, "single pointer value expected");
        } else {
            GraalError.guarantee(Arrays.stream(searchValues).allMatch(sv -> sv.getPlatformKind() == AArch64Kind.DWORD), "int values expected");
        }

        this.stride = stride;
        this.variant = variant;
        this.findTwoConsecutive = variant == ArrayIndexOfVariant.FindTwoConsecutive || variant == ArrayIndexOfVariant.FindTwoConsecutiveWithMask;
        this.withMask = variant == ArrayIndexOfVariant.WithMask || variant == ArrayIndexOfVariant.FindTwoConsecutiveWithMask;
        resultValue = result;
        arrayPtrValue = arrayPtr;
        arrayOffsetValue = arrayOffset;
        arrayLengthValue = arrayLength;
        fromIndexValue = fromIndex;
        this.searchValues = searchValues;

        temp = allocateTempRegisters(tool, 5);
        vectorTemp = allocateVectorRegisters(tool, getNumberOfRequiredVectorRegisters(variant, stride, nValues), variant == ArrayIndexOfVariant.Table);
    }

    private static int getNumberOfRequiredVectorRegisters(ArrayIndexOfVariant variant, Stride stride, int nValues) {
        // Checkstyle: stop FallThrough
        switch (variant) {
            case MatchAny:
                return nValues + (nValues > 1 ? 6 : 3);
            case MatchRange:
                return nValues + 6;
            case WithMask:
                return nValues + 3;
            case FindTwoConsecutive:
            case FindTwoConsecutiveWithMask:
                return nValues + 5;
            case Table:
                switch (stride) {
                    case S1:
                        return 7;
                    case S2:
                        return 9;
                    case S4:
                        return 11;
                    default:
                        throw GraalError.shouldNotReachHereUnexpectedValue(stride); // ExcludeFromJacocoGeneratedReport
                }
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(variant); // ExcludeFromJacocoGeneratedReport
        }
        // Checkstyle: resume FallThrough
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
        masm.add(64, baseAddress, baseAddress, endIndex, ExtendType.SXTW, stride.log2);
        /* Initial index is -searchLength << shift size */
        Register curIndex = searchLength;
        masm.sub(64, curIndex, zr, curIndex, ShiftType.LSL, stride.log2);
        /* Loop doing element-by-element search */
        masm.align(AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT);
        masm.bind(searchByElementLoop);
        masm.ldr(memAccessSize, curValue, AArch64Address.createRegisterOffsetAddress(memAccessSize, baseAddress, curIndex, false));
        switch (variant) {
            case MatchAny:
                for (AllocatableValue searchValue : searchValues) {
                    masm.cmp(compareSize, asRegister(searchValue), curValue);
                    masm.branchConditionally(ConditionFlag.EQ, match);
                }
                break;
            case MatchRange:
                for (int i = 0; i < searchValues.length; i += 2) {
                    Label noMatch = new Label();
                    masm.cmp(compareSize, curValue, asRegister(searchValues[i]));
                    masm.branchConditionally(ConditionFlag.LO, noMatch);
                    masm.cmp(compareSize, curValue, asRegister(searchValues[i + 1]));
                    masm.branchConditionally(ConditionFlag.LS, match);
                    masm.bind(noMatch);
                }
                break;
            case WithMask:
            case FindTwoConsecutiveWithMask:
                masm.orr(compareSize, curValue, curValue, maskReg);
                masm.cmp(compareSize, searchValueReg, curValue);
                masm.branchConditionally(ConditionFlag.EQ, match);
                break;
            case FindTwoConsecutive:
                masm.cmp(compareSize, searchValueReg, curValue);
                masm.branchConditionally(ConditionFlag.EQ, match);
                break;
            case Table:
                Label greaterThan0xff = new Label();
                try (ScratchRegister sc = masm.getScratchRegister()) {
                    Register tmp = sc.getRegister();
                    if (stride.value > 1) {
                        masm.compare(compareSize, curValue, 0xff);
                        masm.branchConditionally(ConditionFlag.HI, greaterThan0xff);
                    }
                    // get lower 4 bits
                    masm.and(compareSize, tmp, curValue, 0xf);
                    // get upper 4 bits
                    masm.asr(compareSize, curValue, curValue, 4);
                    // add offset to second lookup table
                    masm.add(compareSize, tmp, tmp, 16);
                    // load lookup table entries
                    masm.ldr(8, curValue, AArch64Address.createRegisterOffsetAddress(8, asRegister(searchValues[0]), curValue, false));
                    masm.ldr(8, tmp, AArch64Address.createRegisterOffsetAddress(8, asRegister(searchValues[0]), tmp, false));
                    // AND results
                    masm.tst(compareSize, curValue, tmp);
                    // if result is non-zero, a match was found
                    masm.branchConditionally(ConditionFlag.NE, match);
                    masm.bind(greaterThan0xff);
                }
                break;
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
         *  3. Check each read chunk to see if 'searchElement' is found, and if so, calculate the match index with calcIndexOfFirstMatch.
         *
         *  Find two consecutive chars in a chunk:
         *  The findTwoConsecutive case uses the same steps as finding a single character in a chunk but for two characters separately.
         *  To look for two consecutive characters 'c1c2', we search 'c1' in a first chunk [-1..n-1] and 'c2' in the second chunk at [0..n].
         *  Consequently, if the matching sequence is present in a chunk, it will be found at the same position in their respective chunks.
         *  The following list highlights the differences compared to the steps to search a single character in a chunk.
         *   1a. Use two registers, each repeating one of the two consecutive characters to search for.
         *   2a. Read the second chunk starting from the 32 byte aligned ref position. Read the first chunk by concatenating the last element 
         *       of the last iteration's second chunk and the current second chunk.
         *   3a. Compare a chunk for presence of the corresponding char.
         *    3a.1 The first chunk is compared with the register repeating the first char and the same for the second chunk.
         *    3a.2 Perform logical AND on the outcome of comparisons for the first and second char.
         *   4a. As the second chunk starts at a char offset from the first chunk, the result of AND from 3a.2 gives a register with all the
         *  bits set at the position where the match is found. The steps to find the position of the match in the searchString remain unchanged.
         *
         * Other variants are described in their implementation below.
         *
         *  [1] https://github.com/ARM-software/optimized-routines/blob/master/string/aarch64/strchr.S
         * @formatter:on
         * */

        int chunkSize = getSIMDLoopChunkSize();

        Register result = asRegister(resultValue);
        Register arrayLength = asRegister(arrayLengthValue);
        Register fromIndex = asRegister(fromIndexValue);
        Register currOffset = asRegister(temp[1]);
        Register searchEnd = asRegister(temp[2]);
        Register refAddress = asRegister(temp[3]);
        Register array = asRegister(temp[4]);

        Register[] vecSearchValues = new Register[searchValues.length];
        Register[] vecTmp = new Register[vectorTemp.length - (searchValues.length + 2)];
        final Register vecArray1;
        final Register vecArray2;
        Register vecMask0x0f;
        Register vecTableHi;
        Register vecTableLo;

        if (variant == ArrayIndexOfVariant.Table) {
            vecMask0x0f = asRegister(vectorTemp[0]);
            vecTableHi = asRegister(vectorTemp[1]);
            vecTableLo = asRegister(vectorTemp[2]);
            switch (stride) {
                case S1:
                    vecArray1 = asRegister(vectorTemp[3]);
                    vecArray2 = asRegister(vectorTemp[4]);
                    vecTmp[0] = asRegister(vectorTemp[5]);
                    vecTmp[1] = asRegister(vectorTemp[6]);
                    break;
                case S2:
                    // consecutive register ordering required for LD2 instructions
                    vecArray1 = asRegister(vectorTemp[3]);
                    vecTmp[0] = asRegister(vectorTemp[4]);
                    vecArray2 = asRegister(vectorTemp[5]);
                    vecTmp[1] = asRegister(vectorTemp[6]);
                    vecTmp[2] = asRegister(vectorTemp[7]);
                    vecTmp[3] = asRegister(vectorTemp[8]);
                    break;
                case S4:
                    // consecutive register ordering required for LD4 instructions
                    vecArray1 = asRegister(vectorTemp[3]);
                    vecTmp[0] = asRegister(vectorTemp[4]);
                    vecTmp[1] = asRegister(vectorTemp[5]);
                    vecTmp[2] = asRegister(vectorTemp[6]);
                    vecArray2 = asRegister(vectorTemp[7]);
                    vecTmp[3] = asRegister(vectorTemp[8]);
                    vecTmp[4] = asRegister(vectorTemp[9]);
                    vecTmp[5] = asRegister(vectorTemp[10]);
                    break;
                default:
                    throw GraalError.shouldNotReachHereUnexpectedValue(stride); // ExcludeFromJacocoGeneratedReport
            }
        } else {
            vecArray1 = asRegister(vectorTemp[0]);
            vecArray2 = asRegister(vectorTemp[1]);
            for (int i = 0; i < searchValues.length; i++) {
                vecSearchValues[i] = asRegister(vectorTemp[i + 2]);
            }
            for (int i = searchValues.length + 2; i < vectorTemp.length - (findTwoConsecutive ? 1 : 0); i++) {
                vecTmp[i - (searchValues.length + 2)] = asRegister(vectorTemp[i]);
            }
            vecMask0x0f = null;
            vecTableHi = null;
            vecTableLo = null;
        }
        Register vecLastArray2 = findTwoConsecutive ? asRegister(vectorTemp[vectorTemp.length - 1]) : null;

        Label matchInChunk = new Label();
        Label searchByChunkLoopHead = new Label();
        Label searchByChunkLoopTail = new Label();
        Label processTail = new Label();
        Label end = new Label();

        ElementSize eSize = ElementSize.fromStride(stride);
        if (variant == ArrayIndexOfVariant.Table) {
            // in the table variant, searchValue0 is actually a pointer to a 32-byte array
            masm.fldp(128, vecTableHi, vecTableLo, AArch64Address.createPairBaseRegisterOnlyAddress(128, asRegister(searchValues[0])));
            masm.neon.moveVI(FullReg, ElementSize.Byte, vecMask0x0f, 0x0f);
        } else {
            /* 1. Duplicate the searchElement(s) to 16-bytes */
            for (int i = 0; i < searchValues.length; i++) {
                masm.neon.dupVG(FullReg, eSize, vecSearchValues[i], asRegister(searchValues[i]));
            }
        }
        /*
         * 2.1 Set searchEnd pointing to byte after the last valid element in the array and
         * 'refAddress' pointing to the beginning of the last chunk.
         */
        masm.add(64, searchEnd, baseAddress, arrayLength, ExtendType.SXTW, stride.log2);
        masm.sub(64, refAddress, searchEnd, chunkSize);
        /* Set 'array' pointing to the chunk from where the search begins. */
        masm.add(64, array, baseAddress, fromIndex, ExtendType.SXTW, stride.log2);

        if (findTwoConsecutive) {
            // initialize previous chunk tail vector to a value that can't match the first search
            // value.
            masm.neon.notVV(FullReg, vecLastArray2, vecSearchValues[0]);
            // peel first loop iteration because in this variant the loop body depends on the last
            // iteration's state, and we still want to align memory accesses
            masm.cmp(64, refAddress, array);
            masm.branchConditionally(ConditionFlag.LS, processTail);
            masm.sub(64, currOffset, array, baseAddress);
            masm.fldp(128, vecArray1, vecArray2, AArch64Address.createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, array, 32));
            emitSIMDMatch(masm, eSize, array, vecSearchValues, vecTmp, vecArray1, vecArray2, vecLastArray2, vecMask0x0f, vecTableHi, vecTableLo, matchInChunk);
            // align address to 32-byte boundary
            masm.bic(64, array, array, chunkSize - 1);
            // fix vecLastArray2: load from array - 1 and move the resulting vector's first element
            // to the last
            masm.fldr(128, vecArray2, AArch64Address.createImmediateAddress(128, AddressingMode.IMMEDIATE_SIGNED_UNSCALED, array, -stride.value));
            masm.neon.insXX(eSize, vecLastArray2, (FullReg.bytes() / stride.value) - 1, vecArray2, 0);
        }

        masm.align(AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT);
        masm.bind(searchByChunkLoopHead);
        masm.cmp(64, refAddress, array);
        masm.branchConditionally(ConditionFlag.LS, processTail);
        masm.bind(searchByChunkLoopTail);
        masm.sub(64, currOffset, array, baseAddress);

        if (variant != ArrayIndexOfVariant.Table) {
            masm.fldp(128, vecArray1, vecArray2, AArch64Address.createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, array, 32));
        }
        emitSIMDMatch(masm, eSize, array, vecSearchValues, vecTmp, vecArray1, vecArray2, vecLastArray2, vecMask0x0f, vecTableHi, vecTableLo, matchInChunk);
        /* No match; jump to next loop iteration. */
        if (!findTwoConsecutive) {
            // align address to 32-byte boundary
            masm.bic(64, array, array, chunkSize - 1);
        }
        masm.jmp(searchByChunkLoopHead);

        masm.align(AArch64MacroAssembler.PREFERRED_BRANCH_TARGET_ALIGNMENT);
        masm.bind(processTail);
        masm.cmp(64, array, searchEnd);
        masm.branchConditionally(ConditionFlag.HS, end);
        if (findTwoConsecutive) {
            masm.sub(64, array, searchEnd, chunkSize + stride.value);
            // fix vecLastArray2 for last iteration: load from last chunk index - 1 and move the
            // resulting vector's first element to the last
            masm.fldr(128, vecArray2, AArch64Address.createImmediateAddress(128, AddressingMode.IMMEDIATE_POST_INDEXED, array, stride.value));
            masm.neon.insXX(eSize, vecLastArray2, (FullReg.bytes() / stride.value) - 1, vecArray2, 0);
        } else {
            masm.sub(64, array, searchEnd, chunkSize);
        }
        /*
         * Set 'searchEnd' to zero because at the end of 'searchByChunkLoopTail', the 'array' will
         * be rolled back to a 32-byte aligned addressed. Thus, unless 'searchEnd' is adjusted, the
         * 'processTail' comparison condition 'array' >= 'searchEnd' may never be true.
         */
        masm.mov(64, searchEnd, zr);
        masm.jmp(searchByChunkLoopTail);

        /* 4. If the element is found in a 32-byte chunk then find its position. */
        masm.align(AArch64MacroAssembler.PREFERRED_BRANCH_TARGET_ALIGNMENT);
        masm.bind(matchInChunk);
        if (variant == ArrayIndexOfVariant.Table) {
            // convert matching bytes to 0xff
            masm.neon.cmtstVVV(FullReg, ElementSize.Byte, vecArray1, vecArray1, vecArray1);
            masm.neon.cmtstVVV(FullReg, ElementSize.Byte, vecArray2, vecArray2, vecArray2);
        }
        try (ScratchRegister scratchReg = masm.getScratchRegister()) {
            Register tmp = scratchReg.getRegister();
            initCalcIndexOfFirstMatchMask(masm, vecTmp[0], tmp);
            calcIndexOfFirstMatch(masm, tmp, vecArray1, vecArray2, vecTmp[0], false);
            if (variant == ArrayIndexOfVariant.Table) {
                masm.asr(64, currOffset, currOffset, stride.log2);
            }
            masm.add(64, result, currOffset, tmp, ShiftType.ASR, 1);
            if (findTwoConsecutive) {
                masm.sub(64, result, result, 1);
            }
        }
        if (getMatchResultStride().log2 != 0) {
            /* Convert byte offset of searchElement to its array index */
            masm.asr(64, result, result, getMatchResultStride().log2);
        }
        masm.bind(end);
    }

    private void emitSIMDMatch(AArch64MacroAssembler masm,
                    ElementSize eSize,
                    Register array,
                    Register[] vecSearchValues,
                    Register[] vecTmp,
                    Register vecArray1,
                    Register vecArray2,
                    Register vecLastArray2,
                    Register vecMask0x0f,
                    Register vecTableHi,
                    Register vecTableLo,
                    Label matchInChunk) {
        if (findTwoConsecutive) {
            /*
             * In the findTwoConsecutive case, the first search element is compared the current
             * chunk offset by -1. vecTmp[0] holds values vecLastArray2[15]:vecArray1[0:15] and
             * vecTmp[1] holds values vecArray1[15]:vecArray2[0:15].
             */
            // setting vecTmp[0] = vecLastArray2[15]:vecArray1[0:15]
            masm.neon.extVVV(FullReg, vecTmp[0], vecLastArray2, vecArray1, FullReg.bytes() - stride.value);
            // setting vecTmp[1] = vecArray1[15]:vecArray2[0:15]
            masm.neon.extVVV(FullReg, vecTmp[1], vecArray1, vecArray2, FullReg.bytes() - stride.value);
            // save vecArray2 for next iteration
            masm.neon.moveVV(FullReg, vecLastArray2, vecArray2);
        }
        switch (variant) {
            case MatchAny:
                int nValues = vecSearchValues.length;
                if (nValues == 1) {
                    masm.neon.cmeqVVV(FullReg, eSize, vecArray1, vecArray1, vecSearchValues[0]);
                    masm.neon.cmeqVVV(FullReg, eSize, vecArray2, vecArray2, vecSearchValues[0]);
                } else {
                    masm.neon.cmeqVVV(FullReg, eSize, vecTmp[0], vecArray1, vecSearchValues[0]);
                    masm.neon.cmeqVVV(FullReg, eSize, vecTmp[1], vecArray2, vecSearchValues[0]);
                    for (int i = 1; i < nValues; i++) {
                        masm.neon.cmeqVVV(FullReg, eSize, vecTmp[2], vecArray1, vecSearchValues[i]);
                        masm.neon.cmeqVVV(FullReg, eSize, vecTmp[3], vecArray2, vecSearchValues[i]);
                        masm.neon.orrVVV(FullReg, i == nValues - 1 ? vecArray1 : vecTmp[0], vecTmp[0], vecTmp[2]);
                        masm.neon.orrVVV(FullReg, i == nValues - 1 ? vecArray2 : vecTmp[1], vecTmp[1], vecTmp[3]);
                    }
                }
                break;
            case MatchRange:
                // match first range
                // check if array elements are greater or equal to lower bound
                masm.neon.cmhsVVV(FullReg, eSize, vecTmp[0], vecArray1, vecSearchValues[0]);
                masm.neon.cmhsVVV(FullReg, eSize, vecTmp[1], vecArray2, vecSearchValues[0]);
                // check if upper bound is greater or equal to array elements
                masm.neon.cmhsVVV(FullReg, eSize, vecTmp[2], vecSearchValues[1], vecArray1);
                masm.neon.cmhsVVV(FullReg, eSize, vecTmp[3], vecSearchValues[1], vecArray2);
                if (searchValues.length == 4) {
                    // merge results of first range comparisons
                    masm.neon.andVVV(FullReg, vecTmp[0], vecTmp[0], vecTmp[2]);
                    masm.neon.andVVV(FullReg, vecTmp[1], vecTmp[1], vecTmp[3]);

                    // match second range
                    // check if array elements are greater or equal to lower bound
                    masm.neon.cmhsVVV(FullReg, eSize, vecTmp[2], vecArray1, vecSearchValues[2]);
                    masm.neon.cmhsVVV(FullReg, eSize, vecTmp[3], vecArray2, vecSearchValues[2]);
                    // check if upper bound is greater or equal to array elements
                    masm.neon.cmhsVVV(FullReg, eSize, vecArray1, vecSearchValues[3], vecArray1);
                    masm.neon.cmhsVVV(FullReg, eSize, vecArray2, vecSearchValues[3], vecArray2);
                    // merge results of second range comparisons
                    masm.neon.andVVV(FullReg, vecTmp[2], vecTmp[2], vecArray1);
                    masm.neon.andVVV(FullReg, vecTmp[3], vecTmp[3], vecArray2);

                    // merge results of both range comparisons
                    masm.neon.orrVVV(FullReg, vecArray1, vecTmp[0], vecTmp[2]);
                    masm.neon.orrVVV(FullReg, vecArray2, vecTmp[1], vecTmp[3]);
                } else {
                    // merge results of first range comparisons
                    masm.neon.andVVV(FullReg, vecArray1, vecTmp[0], vecTmp[2]);
                    masm.neon.andVVV(FullReg, vecArray2, vecTmp[1], vecTmp[3]);
                }
                break;
            case WithMask:
                masm.neon.orrVVV(FullReg, vecArray1, vecArray1, vecSearchValues[1]);
                masm.neon.orrVVV(FullReg, vecArray2, vecArray2, vecSearchValues[1]);
                masm.neon.cmeqVVV(FullReg, eSize, vecArray1, vecArray1, vecSearchValues[0]);
                masm.neon.cmeqVVV(FullReg, eSize, vecArray2, vecArray2, vecSearchValues[0]);
                break;
            case FindTwoConsecutive:
                masm.neon.cmeqVVV(FullReg, eSize, vecTmp[0], vecTmp[0], vecSearchValues[0]);
                masm.neon.cmeqVVV(FullReg, eSize, vecTmp[1], vecTmp[1], vecSearchValues[0]);
                masm.neon.cmeqVVV(FullReg, eSize, vecArray1, vecArray1, vecSearchValues[1]);
                masm.neon.cmeqVVV(FullReg, eSize, vecArray2, vecArray2, vecSearchValues[1]);
                masm.neon.andVVV(FullReg, vecArray1, vecArray1, vecTmp[0]);
                masm.neon.andVVV(FullReg, vecArray2, vecArray2, vecTmp[1]);
                break;
            case FindTwoConsecutiveWithMask:
                masm.neon.orrVVV(FullReg, vecTmp[0], vecTmp[0], vecSearchValues[2]);
                masm.neon.orrVVV(FullReg, vecTmp[1], vecTmp[1], vecSearchValues[2]);
                masm.neon.orrVVV(FullReg, vecArray1, vecArray1, vecSearchValues[3]);
                masm.neon.orrVVV(FullReg, vecArray2, vecArray2, vecSearchValues[3]);
                masm.neon.cmeqVVV(FullReg, eSize, vecTmp[0], vecTmp[0], vecSearchValues[0]);
                masm.neon.cmeqVVV(FullReg, eSize, vecTmp[1], vecTmp[1], vecSearchValues[0]);
                masm.neon.cmeqVVV(FullReg, eSize, vecArray1, vecArray1, vecSearchValues[1]);
                masm.neon.cmeqVVV(FullReg, eSize, vecArray2, vecArray2, vecSearchValues[1]);
                masm.neon.andVVV(FullReg, vecArray1, vecArray1, vecTmp[0]);
                masm.neon.andVVV(FullReg, vecArray2, vecArray2, vecTmp[1]);
                break;
            case Table:
                switch (stride) {
                    case S1:
                        masm.fldp(128, vecArray1, vecArray2, AArch64Address.createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, array, 32));
                        performTableLookup(masm, vecMask0x0f, vecTableHi, vecTableLo, vecArray1, vecArray2, vecTmp[0], vecTmp[1]);
                        break;
                    case S2:
                        // de-structuring load: get array element's upper and lower bytes into
                        // separate vectors
                        masm.neon.ld2MultipleVV(FullReg, ElementSize.Byte, vecArray1, vecTmp[0], createStructureImmediatePostIndexAddress(LD2_MULTIPLE_2R, FullReg, eSize, array, 32));
                        masm.neon.ld2MultipleVV(FullReg, ElementSize.Byte, vecArray2, vecTmp[1], createStructureImmediatePostIndexAddress(LD2_MULTIPLE_2R, FullReg, eSize, array, 32));
                        // compare upper bytes to zero
                        masm.neon.moveVI(FullReg, ElementSize.Byte, vecTmp[2], 0);
                        masm.neon.cmeqVVV(FullReg, ElementSize.Byte, vecTmp[0], vecTmp[0], vecTmp[2]);
                        masm.neon.cmeqVVV(FullReg, ElementSize.Byte, vecTmp[1], vecTmp[1], vecTmp[2]);
                        // perform table lookup on lower bytes
                        performTableLookup(masm, vecMask0x0f, vecTableHi, vecTableLo, vecArray1, vecArray2, vecTmp[2], vecTmp[3]);
                        // eliminate all matches where the corresponding high byte is not zero
                        masm.neon.andVVV(FullReg, vecArray1, vecArray1, vecTmp[0]);
                        masm.neon.andVVV(FullReg, vecArray2, vecArray2, vecTmp[1]);
                        break;
                    case S4:
                        // de-structuring load: get array element's upper and lower bytes into
                        // separate vectors
                        masm.neon.ld4MultipleVVVV(FullReg, ElementSize.Byte, vecArray1, vecTmp[0], vecTmp[1], vecTmp[2],
                                        createStructureImmediatePostIndexAddress(LD4_MULTIPLE_4R, FullReg, eSize, array, 64));
                        masm.neon.ld4MultipleVVVV(FullReg, ElementSize.Byte, vecArray2, vecTmp[3], vecTmp[4], vecTmp[5],
                                        createStructureImmediatePostIndexAddress(LD4_MULTIPLE_4R, FullReg, eSize, array, 64));
                        // merge upper bytes
                        masm.neon.orrVVV(FullReg, vecTmp[0], vecTmp[0], vecTmp[1]);
                        masm.neon.orrVVV(FullReg, vecTmp[3], vecTmp[3], vecTmp[4]);
                        masm.neon.orrVVV(FullReg, vecTmp[0], vecTmp[0], vecTmp[2]);
                        masm.neon.orrVVV(FullReg, vecTmp[1], vecTmp[3], vecTmp[5]);
                        // compare upper bytes to zero
                        masm.neon.moveVI(FullReg, ElementSize.Byte, vecTmp[2], 0);
                        masm.neon.cmeqVVV(FullReg, ElementSize.Byte, vecTmp[0], vecTmp[0], vecTmp[2]);
                        masm.neon.cmeqVVV(FullReg, ElementSize.Byte, vecTmp[1], vecTmp[1], vecTmp[2]);
                        // perform table lookup on lower bytes
                        performTableLookup(masm, vecMask0x0f, vecTableHi, vecTableLo, vecArray1, vecArray2, vecTmp[2], vecTmp[3]);
                        // eliminate all matches where the corresponding upper bytes are not zero
                        masm.neon.andVVV(FullReg, vecArray1, vecArray1, vecTmp[0]);
                        masm.neon.andVVV(FullReg, vecArray2, vecArray2, vecTmp[1]);
                        break;
                    default:
                        throw GraalError.shouldNotReachHereUnexpectedValue(stride); // ExcludeFromJacocoGeneratedReport
                }
                break;
        }
        masm.neon.orrVVV(FullReg, vecTmp[0], vecArray1, vecArray2);
        try (ScratchRegister sc = masm.getScratchRegister()) {
            Register tmp = sc.getRegister();
            /* If value != 0, then there was a match somewhere. */
            cbnzVector(masm, ElementSize.fromStride(getMatchResultStride()), vecTmp[0], vecTmp[0], tmp, variant != ArrayIndexOfVariant.Table, matchInChunk);
        }
    }

    private Stride getMatchResultStride() {
        return variant == ArrayIndexOfVariant.Table ? Stride.S1 : stride;
    }

    private int getSIMDLoopChunkSize() {
        return variant == ArrayIndexOfVariant.Table ? 32 * stride.value : 32;
    }

    private static void performTableLookup(AArch64MacroAssembler masm,
                    Register vecMask0xf,
                    Register vecTableHi,
                    Register vecTableLo,
                    Register vecArray1,
                    Register vecArray2,
                    Register vecTmp1,
                    Register vecTmp2) {
        // split bytes into low and high nibbles (4-bit values)
        masm.neon.ushrVVI(FullReg, ElementSize.Byte, vecTmp1, vecArray1, 4);
        masm.neon.ushrVVI(FullReg, ElementSize.Byte, vecTmp2, vecArray2, 4);
        masm.neon.andVVV(FullReg, vecArray1, vecArray1, vecMask0xf);
        masm.neon.andVVV(FullReg, vecArray2, vecArray2, vecMask0xf);
        // perform table lookup using nibbles as indices
        masm.neon.tblVVV(FullReg, vecTmp1, vecTableHi, vecTmp1);
        masm.neon.tblVVV(FullReg, vecTmp2, vecTableHi, vecTmp2);
        masm.neon.tblVVV(FullReg, vecArray1, vecTableLo, vecArray1);
        masm.neon.tblVVV(FullReg, vecArray2, vecTableLo, vecArray2);
        // AND lookup results. If the result is non-zero, a match was found
        masm.neon.andVVV(FullReg, vecArray1, vecArray1, vecTmp1);
        masm.neon.andVVV(FullReg, vecArray2, vecArray2, vecTmp2);
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
        masm.subs(32, searchLength, arrayLength, fromIndex);
        if (findTwoConsecutive) {
            /*
             * Because one is trying to find two consecutive elements, the search length is in
             * effect one less
             */
            masm.subs(32, searchLength, searchLength, 1);
        }
        masm.branchConditionally(ConditionFlag.LE, done);

        /* Load address of first array element */
        masm.add(64, baseAddress, asRegister(arrayPtrValue), asRegister(arrayOffsetValue));

        /*
         * Search element-by-element for small arrays (with search space size of less than 32 bytes,
         * i.e., 4 UTF-16 or 8 Latin1 elements) else search chunk-by-chunk.
         */
        Label searchByChunk = new Label();
        int chunkByteSize = getSIMDLoopChunkSize();
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
