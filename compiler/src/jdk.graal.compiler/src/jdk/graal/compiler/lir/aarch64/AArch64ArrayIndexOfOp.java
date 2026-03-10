/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD1_MULTIPLE_2R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD1_MULTIPLE_4R;
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
import jdk.graal.compiler.code.DataSection;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.debug.Assertions;
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
        GraalError.guarantee(result.getPlatformKind() == (variant.returnsLong() ? AArch64Kind.QWORD : AArch64Kind.DWORD), "%s value expected", variant.returnsLong() ? "long" : "int");
        GraalError.guarantee(arrayPtr.getPlatformKind() == AArch64Kind.QWORD, "pointer value expected");
        GraalError.guarantee(arrayOffset.getPlatformKind() == AArch64Kind.QWORD, "long value expected");
        GraalError.guarantee(arrayLength.getPlatformKind() == AArch64Kind.DWORD, "int value expected");
        GraalError.guarantee(fromIndex.getPlatformKind() == AArch64Kind.DWORD, "int value expected");
        if (variant.isTable()) {
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
        vectorTemp = allocateVectorRegisters(tool, getNumberOfRequiredVectorRegisters(variant, stride, nValues), variant.isTable());
    }

    private static int getNumberOfRequiredVectorRegisters(ArrayIndexOfVariant variant, Stride stride, int nValues) {
        if (variant.returnsLong()) {
            int tableCount = variant.tableCount();
            return switch (stride) {
                case S1 -> (3 * tableCount) + 9;
                case S2 -> (3 * tableCount) + 11;
                case S4 -> (3 * tableCount) + 15;
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(stride); // ExcludeFromJacocoGeneratedReport
            };
        }
        // Checkstyle: stop FallThrough
        switch (variant) {
            case MatchAny:
                return nValues + (nValues > 1 ? 6 : 3);
            case MatchRange, MatchRangeForeignEndian:
                return nValues + 6;
            case WithMask:
                return nValues + 3;
            case FindTwoConsecutive:
            case FindTwoConsecutiveWithMask:
                return nValues + 5;
            case Table, TableForeignEndian:
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

    private boolean isConsecutiveTableVariant() {
        return variant.returnsLong();
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
        if (variant == ArrayIndexOfVariant.MatchRangeForeignEndian || variant == ArrayIndexOfVariant.TableForeignEndian) {
            switch (stride) {
                case S2 -> masm.rev16(32, curValue, curValue);
                case S4, S8 -> masm.rev(stride.getBitCount(), curValue, curValue);
            }
        }
        switch (variant) {
            case MatchAny:
                for (AllocatableValue searchValue : searchValues) {
                    masm.cmp(compareSize, asRegister(searchValue), curValue);
                    masm.branchConditionally(ConditionFlag.EQ, match);
                }
                break;
            case MatchRange, MatchRangeForeignEndian:
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
            case Table, TableForeignEndian:
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

        if (variant.isTable()) {
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
        if (variant.isTable()) {
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

        if (!variant.isTable()) {
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
        if (variant.isTable()) {
            // convert matching bytes to 0xff
            masm.neon.cmtstVVV(FullReg, ElementSize.Byte, vecArray1, vecArray1, vecArray1);
            masm.neon.cmtstVVV(FullReg, ElementSize.Byte, vecArray2, vecArray2, vecArray2);
        }
        try (ScratchRegister scratchReg = masm.getScratchRegister()) {
            Register tmp = scratchReg.getRegister();
            initCalcIndexOfFirstMatchMask(masm, vecTmp[0], tmp);
            calcIndexOfFirstMatch(masm, tmp, vecArray1, vecArray2, vecTmp[0], false);
            if (variant.isTable()) {
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

    @SuppressWarnings("fallthrough")
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
            case MatchRangeForeignEndian:
                masm.neon.revVV(FullReg, eSize, vecArray1, vecArray1);
                masm.neon.revVV(FullReg, eSize, vecArray2, vecArray2);
                // fallthrough
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
            case Table, TableForeignEndian:
                switch (stride) {
                    case S1 -> {
                        masm.fldp(128, vecArray1, vecArray2, AArch64Address.createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, array, 32));
                        performTableLookup(masm, vecMask0x0f, vecTableHi, vecTableLo, vecArray1, vecArray2, vecTmp[0], vecTmp[1]);
                    }
                    case S2 -> {
                        // de-structuring load: get array element's upper and lower bytes into
                        // separate vectors
                        masm.neon.ld2MultipleVV(FullReg, ElementSize.Byte, vecArray1, vecTmp[0], createStructureImmediatePostIndexAddress(LD2_MULTIPLE_2R, FullReg, eSize, array, 32));
                        masm.neon.ld2MultipleVV(FullReg, ElementSize.Byte, vecArray2, vecTmp[1], createStructureImmediatePostIndexAddress(LD2_MULTIPLE_2R, FullReg, eSize, array, 32));
                        Register vecArray1B0 = vecArray1;
                        Register vecArray1B1 = vecTmp[0];
                        Register vecArray2B0 = vecArray2;
                        Register vecArray2B1 = vecTmp[1];
                        if (variant.isForeignEndian()) {
                            vecArray1B0 = vecTmp[0];
                            vecArray1B1 = vecArray1;
                            vecArray2B0 = vecTmp[1];
                            vecArray2B1 = vecArray2;
                        }
                        // compare upper bytes to zero
                        masm.neon.moveVI(FullReg, ElementSize.Byte, vecTmp[2], 0);
                        masm.neon.cmeqVVV(FullReg, ElementSize.Byte, vecArray1B1, vecArray1B1, vecTmp[2]);
                        masm.neon.cmeqVVV(FullReg, ElementSize.Byte, vecArray2B1, vecArray2B1, vecTmp[2]);
                        // perform table lookup on lower bytes
                        performTableLookup(masm, vecMask0x0f, vecTableHi, vecTableLo, vecArray1B0, vecArray2B0, vecTmp[2], vecTmp[3]);
                        // eliminate all matches where the corresponding high byte is not zero
                        masm.neon.andVVV(FullReg, vecArray1, vecArray1B0, vecArray1B1);
                        masm.neon.andVVV(FullReg, vecArray2, vecArray2B0, vecArray2B1);
                    }
                    case S4 -> {
                        // de-structuring load: get array element's upper and lower bytes into
                        // separate vectors
                        masm.neon.ld4MultipleVVVV(FullReg, ElementSize.Byte, vecArray1, vecTmp[0], vecTmp[1], vecTmp[2],
                                        createStructureImmediatePostIndexAddress(LD4_MULTIPLE_4R, FullReg, eSize, array, 64));
                        masm.neon.ld4MultipleVVVV(FullReg, ElementSize.Byte, vecArray2, vecTmp[3], vecTmp[4], vecTmp[5],
                                        createStructureImmediatePostIndexAddress(LD4_MULTIPLE_4R, FullReg, eSize, array, 64));
                        Register vecArray1B0 = vecArray1;
                        Register vecArray1B1 = vecTmp[0];
                        Register vecArray1B2 = vecTmp[1];
                        Register vecArray1B3 = vecTmp[2];
                        Register vecArray2B0 = vecArray2;
                        Register vecArray2B1 = vecTmp[3];
                        Register vecArray2B2 = vecTmp[4];
                        Register vecArray2B3 = vecTmp[5];
                        if (variant.isForeignEndian()) {
                            vecArray1B0 = vecTmp[2];
                            vecArray1B1 = vecTmp[1];
                            vecArray1B2 = vecTmp[0];
                            vecArray1B3 = vecArray1;
                            vecArray2B0 = vecTmp[5];
                            vecArray2B1 = vecTmp[4];
                            vecArray2B2 = vecTmp[3];
                            vecArray2B3 = vecArray2;
                        }
                        // merge upper bytes
                        masm.neon.orrVVV(FullReg, vecArray1B1, vecArray1B1, vecArray1B2);
                        masm.neon.orrVVV(FullReg, vecArray2B1, vecArray2B1, vecArray2B2);
                        masm.neon.orrVVV(FullReg, vecArray1B1, vecArray1B1, vecArray1B3);
                        masm.neon.orrVVV(FullReg, vecArray2B1, vecArray2B1, vecArray2B3);
                        // compare upper bytes to zero
                        masm.neon.moveVI(FullReg, ElementSize.Byte, vecArray1B3, 0);
                        masm.neon.cmeqVVV(FullReg, ElementSize.Byte, vecArray1B1, vecArray1B1, vecArray1B3);
                        masm.neon.cmeqVVV(FullReg, ElementSize.Byte, vecArray2B1, vecArray2B1, vecArray1B3);
                        // perform table lookup on lower bytes
                        performTableLookup(masm, vecMask0x0f, vecTableHi, vecTableLo, vecArray1B0, vecArray2B0, vecArray1B3, vecArray2B3);
                        // eliminate all matches where the corresponding upper bytes are not zero
                        masm.neon.andVVV(FullReg, vecArray1, vecArray1B0, vecArray1B1);
                        masm.neon.andVVV(FullReg, vecArray2, vecArray2B0, vecArray2B1);
                    }
                    default -> throw GraalError.shouldNotReachHereUnexpectedValue(stride); // ExcludeFromJacocoGeneratedReport
                }
                break;
        }
        masm.neon.orrVVV(FullReg, vecTmp[0], vecArray1, vecArray2);
        try (ScratchRegister sc = masm.getScratchRegister()) {
            Register tmp = sc.getRegister();
            /* If value != 0, then there was a match somewhere. */
            cbnzVector(masm, ElementSize.fromStride(getMatchResultStride()), vecTmp[0], vecTmp[0], tmp, !variant.isTable(), matchInChunk);
        }
    }

    private Stride getMatchResultStride() {
        return variant.isTable() ? Stride.S1 : stride;
    }

    private int getSIMDLoopChunkSize() {
        return variant.isTable() ? 32 * stride.value : 32;
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
        if (isConsecutiveTableVariant()) {
            // Consecutive-table variants are only emitted for search windows of at least 16
            // elements, so they can always start in the dedicated vectorized path.
            emitFindConsecutiveTablesCode(crb, masm);
            return;
        }

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

    private void emitFindConsecutiveTablesCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        int tableCount = variant.tableCount();
        Register result = asRegister(resultValue);
        Register arrayLength = asRegister(arrayLengthValue);
        Register fromIndex = asRegister(fromIndexValue);
        Register baseAddress = asRegister(temp[0]);
        Register searchLength = asRegister(temp[1]);
        Register endAddress = asRegister(temp[2]);
        Register array = asRegister(temp[3]);
        Register tmp = asRegister(temp[4]);

        Register[] v = new Register[vectorTemp.length];
        for (int i = 0; i < vectorTemp.length; i++) {
            v[i] = asRegister(vectorTemp[i]);
        }
        Register[] vLut = Arrays.copyOfRange(v, 0, tableCount * 2);
        Register vMask0x0f = v[tableCount * 2];
        int base = (tableCount * 2) + 1;
        Register[] vPrevCandidate;
        Register vReduce;
        Register vCandidate1;
        Register vCandidate2;
        Register[] vScratch;
        Register vBytes1;
        Register vBytes2;
        Register vValid1;
        Register vValid2;

        Register[] s2Load = null;
        Register[] s4Load = null;

        final boolean fe = variant.isForeignEndian();
        switch (stride) {
            case S1 -> {
                vBytes1 = v[base];
                vBytes2 = v[base + 1];
                vScratch = Arrays.copyOfRange(v, base + 2, base + 7);
                vCandidate1 = v[base + 7];
                vCandidate2 = v[base + 8];
                vPrevCandidate = Arrays.copyOfRange(v, base + 9, base + 9 + (tableCount - 1));
                vValid1 = null;
                vValid2 = null;
            }
            case S2 -> {
                s2Load = Arrays.copyOfRange(v, base, base + 4);
                vScratch = Arrays.copyOfRange(v, base + 4, base + 9);
                vCandidate1 = v[base + 9];
                vCandidate2 = v[base + 10];
                vPrevCandidate = Arrays.copyOfRange(v, base + 11, base + 11 + (tableCount - 1));
                vBytes1 = s2Load[fe ? 1 : 0];
                vValid1 = s2Load[fe ? 0 : 1];
                vBytes2 = s2Load[fe ? 3 : 2];
                vValid2 = s2Load[fe ? 2 : 3];
            }
            case S4 -> {
                s4Load = Arrays.copyOfRange(v, base, base + 8);
                vScratch = Arrays.copyOfRange(v, base + 8, base + 13);
                vCandidate1 = v[base + 13];
                vCandidate2 = v[base + 14];
                vPrevCandidate = Arrays.copyOfRange(v, base + 15, base + 15 + (tableCount - 1));
                vBytes1 = s4Load[fe ? 3 : 0];
                vValid1 = s4Load[fe ? 2 : 1];
                vBytes2 = s4Load[fe ? 7 : 4];
                vValid2 = s4Load[fe ? 6 : 5];
            }
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(stride); // ExcludeFromJacocoGeneratedReport
        }
        vReduce = vScratch[4];

        Label loop32 = new Label();
        Label tail = new Label();
        Label tailGreater16 = new Label();
        Label tailLessThan16 = new Label();
        Label found32 = new Label();
        Label found16 = new Label();
        Label done = new Label();
        DataSection.Data extractCandidateMask = writeToDataSection(crb, createCTExtractCandidateMask());

        // searchLength counts elements, not bytes. Consecutive-table variants are only emitted for
        // windows of at least 16 elements, so they can always start in the vectorized path.
        masm.sub(32, searchLength, arrayLength, fromIndex);
        masm.add(64, baseAddress, asRegister(arrayPtrValue), asRegister(arrayOffsetValue));

        // Load the hi/lo lookup tables once and clear the carry vectors used for cross-iteration
        // matches.
        emitCTLoadLUT(masm, asRegister(searchValues[0]), vLut);
        masm.neon.moveVI(FullReg, ElementSize.Byte, vMask0x0f, 0x0f);
        for (Register vPrev : vPrevCandidate) {
            masm.neon.moveVI(FullReg, ElementSize.Byte, vPrev, 0);
        }

        // array points at the first searchable element and endAddress points one element past the
        // end of the search window.
        masm.add(64, endAddress, baseAddress, arrayLength, ExtendType.SXTW, stride.log2);
        masm.add(64, array, baseAddress, fromIndex, ExtendType.SXTW, stride.log2);

        masm.compare(32, searchLength, 32);
        masm.branchConditionally(ConditionFlag.LO, tail);

        // Main 32-byte loop. The dedicated 32-byte matcher evaluates two 16-byte halves in parallel
        // and keeps one previous-candidate vector per non-final table.
        masm.sub(64, searchLength, endAddress, 32 * stride.value);
        masm.align(AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT);
        masm.bind(loop32);
        switch (stride) {
            case S1 -> masm.fldp(128, vBytes1, vBytes2, AArch64Address.createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, array, 32));
            case S2 -> emitCTNarrow32S2(masm, array, s2Load);
            case S4 -> emitCTNarrow32S4(masm, array, s4Load);
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(stride); // ExcludeFromJacocoGeneratedReport
        }
        emitCTMatch32(masm, vLut, vMask0x0f, vBytes1, vBytes2, vValid1, vValid2, vScratch[0], vScratch[1], vScratch[2], vScratch[3], vScratch[4], vCandidate1, vCandidate2, vPrevCandidate);
        masm.neon.orrVVV(FullReg, vReduce, vCandidate1, vCandidate2);
        cbnzVector(masm, ElementSize.Byte, vReduce, vReduce, tmp, false, found32);
        masm.cmp(64, array, searchLength);
        masm.branchConditionally(ConditionFlag.LS, loop32);

        // Tail handling: use a dedicated 16-byte matcher for 16..31-byte search windows and for the
        // tail after the 32-byte loop. If less than 16 bytes remain, rewind to the overlapping
        // final 16-byte window and adjust the carry state first.
        masm.bind(tail);
        masm.sub(64, tmp, endAddress, 16 * stride.value);
        masm.cmp(64, array, tmp);
        masm.branchConditionally(ConditionFlag.LO, tailGreater16);
        masm.bind(tailLessThan16);
        masm.sub(64, tmp, endAddress, array);
        if (stride.log2 != 0) {
            masm.asr(64, tmp, tmp, stride.log2);
        }
        emitCTAdjustTailCarry16(crb, masm, tmp, vPrevCandidate, vScratch[4]);
        masm.sub(64, array, endAddress, 16 * stride.value);
        masm.bind(tailGreater16);
        switch (stride) {
            case S1 -> masm.fldr(128, vBytes1, AArch64Address.createImmediateAddress(128, AddressingMode.IMMEDIATE_POST_INDEXED, array, 16));
            case S2 -> emitCTNarrow16S2(masm, array, s2Load);
            case S4 -> emitCTNarrow16S4(masm, array, s4Load);
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(stride); // ExcludeFromJacocoGeneratedReport
        }
        emitCTMatch16(masm, vLut, vMask0x0f, vBytes1, vValid1, vScratch[0], vScratch[1], vScratch[2], vCandidate1, vPrevCandidate);
        masm.neon.moveVV(FullReg, vReduce, vCandidate1);
        cbnzVector(masm, ElementSize.Byte, vReduce, vReduce, tmp, false, found16);

        masm.cmp(64, array, endAddress);
        masm.mov(result, -1); // no match
        masm.branchConditionally(ConditionFlag.HS, done);
        masm.jmp(tailLessThan16);

        masm.bind(found32);
        // Extract the first matching byte from the 32-byte candidate pair and pack the result into
        // [high 32 bits = byte, low 32 bits = index].
        emitCTExtractFirstMatchAndPackResult32(crb, masm, baseAddress, array, result, vCandidate1, vCandidate2, vScratch[0], vScratch[1], vScratch[2], vScratch[3], extractCandidateMask);
        masm.jmp(done);

        masm.bind(found16);
        // Same extraction logic for the single-vector tail path.
        emitCTExtractFirstMatchAndPackResult16(crb, masm, baseAddress, array, result, vCandidate1, vScratch[0], vScratch[1], vScratch[2], vScratch[3], extractCandidateMask);

        masm.bind(done);
    }

    private void emitCTLoadLUT(AArch64MacroAssembler masm, Register tablePtr, Register[] vLut) {
        int tableCount = variant.tableCount();
        try (ScratchRegister sc = masm.getScratchRegister()) {
            Register ptr = sc.getRegister();
            masm.mov(64, ptr, tablePtr);
            // Load one hi/lo lookup-table pair per logical table. Keeping the LUT vectors
            // consecutive lets us use LD1-multiple loads for both the 2-table and 4-table cases.
            masm.neon.ld1MultipleVVVV(FullReg, ElementSize.Byte, vLut[0], vLut[1], vLut[2], vLut[3], createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, FullReg, ElementSize.Byte, ptr, 64));
            if (tableCount == 3) {
                masm.neon.ld1MultipleVV(FullReg, ElementSize.Byte, vLut[4], vLut[5], createStructureImmediatePostIndexAddress(LD1_MULTIPLE_2R, FullReg, ElementSize.Byte, ptr, 32));
            } else if (tableCount == 4) {
                masm.neon.ld1MultipleVVVV(FullReg, ElementSize.Byte, vLut[4], vLut[5], vLut[6], vLut[7], createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, FullReg, ElementSize.Byte, ptr, 64));
            }
        }
    }

    private void emitCTNarrow32S2(AArch64MacroAssembler masm, Register array, Register[] vLoad) {
        boolean fe = variant.isForeignEndian();
        // Destructure 16-bit input into low-byte/high-byte vectors. The low-byte vectors are the
        // narrowed input, while the high-byte vectors become invalid-byte masks after CMTST.
        masm.neon.ld2MultipleVV(FullReg, ElementSize.Byte, vLoad[0], vLoad[1], createStructureImmediatePostIndexAddress(LD2_MULTIPLE_2R, FullReg, ElementSize.Byte, array, 32));
        masm.neon.ld2MultipleVV(FullReg, ElementSize.Byte, vLoad[2], vLoad[3], createStructureImmediatePostIndexAddress(LD2_MULTIPLE_2R, FullReg, ElementSize.Byte, array, 32));
        Register vValid1 = vLoad[fe ? 0 : 1];
        Register vValid2 = vLoad[fe ? 2 : 3];
        // CMTST turns all non-zero upper bytes into 0xff, which lets the lookup stage clear
        // matches whose original 16-bit value was > 0xff via BIC.
        masm.neon.cmtstVVV(FullReg, ElementSize.Byte, vValid1, vValid1, vValid1);
        masm.neon.cmtstVVV(FullReg, ElementSize.Byte, vValid2, vValid2, vValid2);
    }

    private void emitCTNarrow16S2(AArch64MacroAssembler masm, Register array, Register[] vLoad) {
        boolean fe = variant.isForeignEndian();
        // Half variant of emitCTNarrow32S2, used by the 16-byte tail.
        masm.neon.ld2MultipleVV(FullReg, ElementSize.Byte, vLoad[0], vLoad[1], createStructureImmediatePostIndexAddress(LD2_MULTIPLE_2R, FullReg, ElementSize.Byte, array, 32));
        Register vValid = vLoad[fe ? 0 : 1];
        masm.neon.cmtstVVV(FullReg, ElementSize.Byte, vValid, vValid, vValid);
    }

    private void emitCTNarrow32S4(AArch64MacroAssembler masm, Register array, Register[] vLoad) {
        boolean fe = variant.isForeignEndian();
        // Destructure 32-bit input into four byte vectors. The selected byte-0 vectors are the
        // narrowed input. OR the remaining bytes together and turn them into 0xff masks so the
        // lookup stage can eliminate values above 0xff.
        masm.neon.ld4MultipleVVVV(FullReg, ElementSize.Byte, vLoad[0], vLoad[1], vLoad[2], vLoad[3], createStructureImmediatePostIndexAddress(LD4_MULTIPLE_4R, FullReg, ElementSize.Byte, array, 64));
        masm.neon.ld4MultipleVVVV(FullReg, ElementSize.Byte, vLoad[4], vLoad[5], vLoad[6], vLoad[7], createStructureImmediatePostIndexAddress(LD4_MULTIPLE_4R, FullReg, ElementSize.Byte, array, 64));
        Register vValid1 = vLoad[fe ? 2 : 1];
        Register vValid2 = vLoad[fe ? 6 : 5];
        masm.neon.orrVVV(FullReg, vValid1, vValid1, vLoad[fe ? 1 : 2]);
        masm.neon.orrVVV(FullReg, vValid1, vValid1, vLoad[fe ? 0 : 3]);
        masm.neon.orrVVV(FullReg, vValid2, vValid2, vLoad[fe ? 5 : 6]);
        masm.neon.orrVVV(FullReg, vValid2, vValid2, vLoad[fe ? 4 : 7]);
        masm.neon.cmtstVVV(FullReg, ElementSize.Byte, vValid1, vValid1, vValid1);
        masm.neon.cmtstVVV(FullReg, ElementSize.Byte, vValid2, vValid2, vValid2);
    }

    private void emitCTNarrow16S4(AArch64MacroAssembler masm, Register array, Register[] vLoad) {
        boolean fe = variant.isForeignEndian();
        // Half variant of emitCTNarrow32S4, used by the 16-byte tail.
        masm.neon.ld4MultipleVVVV(FullReg, ElementSize.Byte, vLoad[0], vLoad[1], vLoad[2], vLoad[3], createStructureImmediatePostIndexAddress(LD4_MULTIPLE_4R, FullReg, ElementSize.Byte, array, 64));
        Register vValid = vLoad[fe ? 2 : 1];
        masm.neon.orrVVV(FullReg, vValid, vValid, vLoad[fe ? 1 : 2]);
        masm.neon.orrVVV(FullReg, vValid, vValid, vLoad[fe ? 0 : 3]);
        masm.neon.cmtstVVV(FullReg, ElementSize.Byte, vValid, vValid, vValid);
    }

    private static void emitCTSplitIntoNibbles(AArch64MacroAssembler masm, Register vMask0x0f, Register vBytes, Register vHiNibble) {
        // Split the byte input into hi/lo nibble indices for table lookup.
        masm.neon.ushrVVI(FullReg, ElementSize.Byte, vHiNibble, vBytes, 4);
        masm.neon.andVVV(FullReg, vBytes, vBytes, vMask0x0f);
    }

    private static void emitCTLookup(AArch64MacroAssembler masm,
                    Register[] vLut,
                    int lutIndex,
                    Register vHiNibble,
                    Register vLoNibble,
                    Register vCandidateOut,
                    Register vScratch,
                    Register vValidByteMask) {
        // Look up the hi- and low-nibble masks and intersect them.
        masm.neon.tblVVV(FullReg, vCandidateOut, vLut[lutIndex * 2], vHiNibble);
        masm.neon.tblVVV(FullReg, vScratch, vLut[(lutIndex * 2) + 1], vLoNibble);
        masm.neon.andVVV(FullReg, vCandidateOut, vCandidateOut, vScratch);
        if (vValidByteMask != null) {
            // Clear matches of narrowed elements whose original value was > 0xff.
            masm.neon.bicVVV(FullReg, vCandidateOut, vCandidateOut, vValidByteMask);
        }
    }

    private void emitCTMatch32(AArch64MacroAssembler masm,
                    Register[] vLut,
                    Register vMask0x0f,
                    Register vBytes1,
                    Register vBytes2,
                    Register vValid1,
                    Register vValid2,
                    Register vNibbleHi1,
                    Register vNibbleHi2,
                    Register vCurCandidate1,
                    Register vCurCandidate2,
                    Register vScratch,
                    Register vCandidate1,
                    Register vCandidate2,
                    Register[] vPrevCandidate) {
        int tableCount = variant.tableCount();
        // Split both 16-byte vectors once, then evaluate one logical table at a time.
        // vPrevCandidate carries the previous iteration's second-half lookup result so the first
        // half can still match sequences crossing the 32-byte loop boundary.
        emitCTSplitIntoNibbles(masm, vMask0x0f, vBytes1, vNibbleHi1);
        emitCTSplitIntoNibbles(masm, vMask0x0f, vBytes2, vNibbleHi2);
        for (int i = 0; i < tableCount; i++) {
            int shiftCount = tableCount - (i + 1);
            emitCTLookup(masm, vLut, i, vNibbleHi1, vBytes1, vCurCandidate1, vScratch, vValid1);
            emitCTLookup(masm, vLut, i, vNibbleHi2, vBytes2, vCurCandidate2, vScratch, vValid2);
            if (i == 0) {
                assert (shiftCount != 0) : Assertions.errorMessage(shiftCount);
                // The first table initializes both accumulated candidate vectors by shifting in the
                // previous chunk's carry for the first half and the current first half for the
                // second half.
                masm.neon.extVVV(FullReg, vCandidate1, vPrevCandidate[i], vCurCandidate1, FullReg.bytes() - shiftCount);
                masm.neon.extVVV(FullReg, vCandidate2, vCurCandidate1, vCurCandidate2, FullReg.bytes() - shiftCount);
                masm.neon.moveVV(FullReg, vPrevCandidate[i], vCurCandidate2);
            } else if (i < tableCount - 1) {
                // Intermediate tables contribute one shifted candidate stream per half and update
                // their carry register for the next 32-byte iteration.
                masm.neon.extVVV(FullReg, vScratch, vPrevCandidate[i], vCurCandidate1, FullReg.bytes() - shiftCount);
                masm.neon.andVVV(FullReg, vCandidate1, vCandidate1, vScratch);
                masm.neon.extVVV(FullReg, vScratch, vCurCandidate1, vCurCandidate2, FullReg.bytes() - shiftCount);
                masm.neon.andVVV(FullReg, vCandidate2, vCandidate2, vScratch);
                masm.neon.moveVV(FullReg, vPrevCandidate[i], vCurCandidate2);
            } else {
                // The last table already lines up with the sequence end positions, so it can be
                // ANDed into both accumulated candidate vectors directly.
                masm.neon.andVVV(FullReg, vCandidate1, vCandidate1, vCurCandidate1);
                masm.neon.andVVV(FullReg, vCandidate2, vCandidate2, vCurCandidate2);
            }
        }
    }

    private void emitCTMatch16(AArch64MacroAssembler masm,
                    Register[] vLut,
                    Register vMask0x0f,
                    Register vBytes,
                    Register vValid,
                    Register vNibbleHi,
                    Register vCurCandidate,
                    Register vScratch,
                    Register vCandidate,
                    Register[] vPrevCandidate) {
        int tableCount = variant.tableCount();
        // Half variant of emitCTMatch32. This is used for 16..31 byte windows and for the
        // post-loop tail handling.
        emitCTSplitIntoNibbles(masm, vMask0x0f, vBytes, vNibbleHi);
        for (int i = 0; i < tableCount; i++) {
            int shiftCount = tableCount - (i + 1);
            emitCTLookup(masm, vLut, i, vNibbleHi, vBytes, vCurCandidate, vScratch, vValid);
            if (i == 0) {
                assert (shiftCount != 0) : Assertions.errorMessage(shiftCount);
                // The first table initializes the accumulated candidate by shifting in the previous
                // iteration's carry.
                masm.neon.extVVV(FullReg, vCandidate, vPrevCandidate[i], vCurCandidate, FullReg.bytes() - shiftCount);
                masm.neon.moveVV(FullReg, vPrevCandidate[i], vCurCandidate);
            } else if (i < tableCount - 1) {
                // Intermediate tables shift in their previous carry, accumulate into vCandidate,
                // and store the current lookup result for the next 16-byte iteration.
                masm.neon.extVVV(FullReg, vScratch, vPrevCandidate[i], vCurCandidate, FullReg.bytes() - shiftCount);
                masm.neon.andVVV(FullReg, vCandidate, vCandidate, vScratch);
                masm.neon.moveVV(FullReg, vPrevCandidate[i], vCurCandidate);
            } else {
                // The last table doesn't need any carry-over from the previous iteration.
                masm.neon.andVVV(FullReg, vCandidate, vCandidate, vCurCandidate);
            }
        }
    }

    private void emitCTAdjustTailCarry16(CompilationResultBuilder crb,
                    AArch64MacroAssembler masm,
                    Register tailLength,
                    Register[] vPrevCandidate,
                    Register vMask) {
        if (variant.tableCount() == 2) {
            // In two-table mode, the overlapping 16-byte tail always re-checks the only possible
            // cross-boundary match, so no carry needs to be preserved.
            masm.neon.moveVI(FullReg, ElementSize.Byte, vPrevCandidate[0], 0);
            return;
        }
        try (ScratchRegister sc = masm.getScratchRegister()) {
            Register tmp = sc.getRegister();
            // Keep only the last three candidate bytes. At most three start positions can still
            // cross into the final overlapping 16-byte iteration because we handle up to four
            // consecutive tables.
            loadDataSectionAddress(crb, masm, tmp, writeToDataSection(crb, createCTTailPrevShuffleMask()));
            masm.fldr(128, vMask, AArch64Address.createRegisterOffsetAddress(128, tmp, tailLength, false));
            for (Register vPrev : vPrevCandidate) {
                masm.neon.tblVVV(FullReg, vPrev, vPrev, vMask);
            }
        }
    }

    private void emitCTExtractFirstMatchAndPackResult32(CompilationResultBuilder crb,
                    AArch64MacroAssembler masm,
                    Register baseAddress,
                    Register array,
                    Register result,
                    Register vCandidate1,
                    Register vCandidate2,
                    Register vMaskVec,
                    Register vCanon1,
                    Register vCanon2,
                    Register vExtract,
                    DataSection.Data extractCandidateMask) {
        Register matchIndex = asRegister(temp[1]);
        Register tmp = asRegister(temp[2]);
        // vCandidate1/2 contain non-zero bytes at every end position of a matching sequence.
        // Canonicalize those bytes to 0xff so calcIndexOfFirstMatch can locate the first one.
        masm.neon.moveVV(FullReg, vCanon1, vCandidate1);
        masm.neon.moveVV(FullReg, vCanon2, vCandidate2);
        masm.neon.cmtstVVV(FullReg, ElementSize.Byte, vCanon1, vCanon1, vCanon1);
        masm.neon.cmtstVVV(FullReg, ElementSize.Byte, vCanon2, vCanon2, vCanon2);
        initCalcIndexOfFirstMatchMask(masm, vMaskVec, tmp);
        calcIndexOfFirstMatch(masm, matchIndex, vCanon1, vCanon2, vMaskVec, false);
        masm.asr(64, matchIndex, matchIndex, 1);
        // Extract the matching byte by loading a TBL index vector offset by the first-match index.
        loadDataSectionAddress(crb, masm, tmp, extractCandidateMask);
        masm.fldr(128, vMaskVec, AArch64Address.createRegisterOffsetAddress(128, tmp, matchIndex, false));
        masm.neon.tblVVVV(FullReg, vExtract, vCandidate1, vCandidate2, vMaskVec);
        masm.neon.umovGX(ElementSize.Byte, tmp, vExtract, 0);
        masm.and(64, tmp, tmp, 0xff);
        // Convert the byte offset back to an array index and adjust to the start of the sequence.
        masm.sub(64, result, array, baseAddress);
        if (stride.log2 != 0) {
            masm.asr(64, result, result, stride.log2);
        }
        masm.sub(64, result, result, 32);
        masm.add(64, result, result, matchIndex);
        masm.sub(64, result, result, variant.tableCount() - 1);
        masm.lsl(64, tmp, tmp, 32);
        masm.orr(64, result, result, tmp);
    }

    private void emitCTExtractFirstMatchAndPackResult16(CompilationResultBuilder crb,
                    AArch64MacroAssembler masm,
                    Register baseAddress,
                    Register array,
                    Register result,
                    Register vCandidate,
                    Register vMaskVec,
                    Register vCanon,
                    Register vZero,
                    Register vExtract,
                    DataSection.Data extractCandidateMask) {
        Register matchIndex = asRegister(temp[1]);
        Register tmp = asRegister(temp[2]);
        // Single-vector variant of emitCTExtractFirstMatchAndPackResult32.
        masm.neon.moveVV(FullReg, vCanon, vCandidate);
        masm.neon.cmtstVVV(FullReg, ElementSize.Byte, vCanon, vCanon, vCanon);
        masm.neon.moveVI(FullReg, ElementSize.Byte, vZero, 0);
        initCalcIndexOfFirstMatchMask(masm, vMaskVec, tmp);
        calcIndexOfFirstMatch(masm, matchIndex, vCanon, vZero, vMaskVec, false);
        masm.asr(64, matchIndex, matchIndex, 1);
        loadDataSectionAddress(crb, masm, tmp, extractCandidateMask);
        masm.fldr(128, vMaskVec, AArch64Address.createRegisterOffsetAddress(128, tmp, matchIndex, false));
        masm.neon.tblVVV(FullReg, vExtract, vCandidate, vMaskVec);
        masm.neon.umovGX(ElementSize.Byte, tmp, vExtract, 0);
        masm.and(64, tmp, tmp, 0xff);
        masm.sub(64, result, array, baseAddress);
        if (stride.log2 != 0) {
            masm.asr(64, result, result, stride.log2);
        }
        masm.sub(64, result, result, 16);
        masm.add(64, result, result, matchIndex);
        masm.sub(64, result, result, variant.tableCount() - 1);
        masm.lsl(64, tmp, tmp, 32);
        masm.orr(64, result, result, tmp);
    }

    private static byte[] createCTTailPrevShuffleMask() {
        // Keep the last three bytes from the previous candidate vector. At most three start
        // positions can cross from one 16-byte iteration into the next.
        byte[] mask = new byte[32];
        Arrays.fill(mask, (byte) 0xff);
        mask[29] = 0x0d;
        mask[30] = 0x0e;
        mask[31] = 0x0f;
        return mask;
    }

    private static byte[] createCTExtractCandidateMask() {
        // After locating the first matching byte, this mask extracts exactly that byte so it can be
        // moved into a general-purpose register.
        byte[] mask = new byte[48];
        for (int i = 0; i < 32; i++) {
            mask[i] = (byte) i;
        }
        Arrays.fill(mask, 32, mask.length, (byte) 0xff);
        return mask;
    }
}
