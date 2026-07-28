/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.amd64;

import static jdk.graal.compiler.asm.amd64.AMD64MacroAssembler.ExtendMode.ZERO_EXTEND;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.XMM;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.YMM;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;

import java.util.Arrays;
import java.util.EnumSet;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.code.DataSection;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

/**
 */
@Opcode("AMD64_ARRAY_INDEX_OF")
public final class AMD64ArrayIndexOfOp extends AMD64ComplexVectorOp {
    public static final LIRInstructionClass<AMD64ArrayIndexOfOp> TYPE = LIRInstructionClass.create(AMD64ArrayIndexOfOp.class);

    private final Stride stride;
    private final int nValues;
    private final LIRGeneratorTool.ArrayIndexOfVariant variant;
    private final boolean findTwoConsecutive;
    private final boolean withMask;
    private final AMD64Kind vectorKind;
    private final Stride arrayIndexStride;
    private final int constOffset;

    @Def({OperandFlag.REG}) Value resultValue;

    @UseKill({OperandFlag.REG}) Value arrayReg;
    @UseKill({OperandFlag.REG}) Value offsetReg;
    @UseKill({OperandFlag.REG}) Value lengthReg;
    @UseKill({OperandFlag.REG}) Value fromIndexReg;
    @UseKill({OperandFlag.REG}) Value searchValue1;
    @UseKill({OperandFlag.REG, OperandFlag.ILLEGAL}) Value searchValue2;

    @Temp({OperandFlag.REG, OperandFlag.ILLEGAL}) Value tableLookupTemp;

    @Alive({OperandFlag.REG, OperandFlag.STACK, OperandFlag.ILLEGAL}) Value searchValue3;
    @Alive({OperandFlag.REG, OperandFlag.STACK, OperandFlag.ILLEGAL}) Value searchValue4;

    @Temp({OperandFlag.REG}) Value[] vectorCompareVal;
    @Temp({OperandFlag.REG}) Value[] vectorArray;
    @Temp({OperandFlag.REG}) Value[] vectorTemp;

    public AMD64ArrayIndexOfOp(Stride stride, LIRGeneratorTool.ArrayIndexOfVariant variant, int constOffset, int nValues, LIRGeneratorTool tool,
                    EnumSet<CPUFeature> runtimeCheckedCPUFeatures, Value result, Value arrayPtr, Value arrayOffset, Value arrayLength, Value fromIndex, Value searchValue1, Value searchValue2,
                    Value searchValue3, Value searchValue4) {
        super(TYPE, tool, runtimeCheckedCPUFeatures, AVXSize.YMM);
        this.stride = stride;
        this.arrayIndexStride = stride;
        this.variant = variant;
        this.findTwoConsecutive = variant == LIRGeneratorTool.ArrayIndexOfVariant.FindTwoConsecutive || variant == LIRGeneratorTool.ArrayIndexOfVariant.FindTwoConsecutiveWithMask;
        this.withMask = variant == LIRGeneratorTool.ArrayIndexOfVariant.WithMask || variant == LIRGeneratorTool.ArrayIndexOfVariant.FindTwoConsecutiveWithMask;
        this.constOffset = constOffset;
        this.nValues = nValues;
        GraalError.guarantee(supports(tool.target(), runtimeCheckedCPUFeatures, CPUFeature.SSE2), "needs at least SSE2 support");

        resultValue = result;
        this.arrayReg = arrayPtr;
        this.offsetReg = arrayOffset;
        this.lengthReg = arrayLength;
        this.fromIndexReg = fromIndex;
        this.searchValue1 = searchValue1;
        if (variant.isTable()) {
            this.searchValue2 = Value.ILLEGAL;
            this.tableLookupTemp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
        } else {
            this.searchValue2 = searchValue2;
            this.tableLookupTemp = Value.ILLEGAL;
        }
        this.searchValue3 = searchValue3;
        this.searchValue4 = searchValue4;

        this.vectorKind = getVectorKind(stride);
        this.vectorCompareVal = allocateVectorRegisters(tool, stride, variant.isTable() ? variant.tableCount() * 2 : nValues);
        this.vectorArray = allocateVectorRegisters(tool, stride, variant.isTable() ? stride.value : 4);
        this.vectorTemp = allocateVectorRegisters(tool, stride, getNumberOfRequiredTempVectors(variant, stride, nValues));
    }

    private static int getNumberOfRequiredTempVectors(LIRGeneratorTool.ArrayIndexOfVariant variant, Stride stride, int nValues) {
        return switch (variant) {
            case MatchRange, MatchRangeForeignEndian -> nValues / 2 + 1;
            case Table, TableForeignEndian -> 4;
            case FindTwoConsecutiveTables, FindTwoConsecutiveTablesForeignEndian -> 5;
            case FindThreeConsecutiveTables, FindThreeConsecutiveTablesForeignEndian -> 6;
            case FindFourConsecutiveTables, FindFourConsecutiveTablesForeignEndian -> 8 - stride.value;
            default -> 0;
        };
    }

    private static Register[] asRegisters(Value[] values) {
        Register[] registers = new Register[values.length];
        for (int i = 0; i < registers.length; i++) {
            registers[i] = asRegister(values[i]);
        }
        return registers;
    }

    private boolean useConstantOffset() {
        return constOffset >= 0;
    }

    private boolean isConsecutiveTableVariant() {
        return variant.returnsLong();
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler asm) {
        AMD64Assembler.AMD64SIMDInstructionEncoding oldEncoding = asm.setTemporaryAvxEncoding(AMD64Assembler.AMD64SIMDInstructionEncoding.VEX);

        int nVectors = getNumberOfVectorsInBulkLoop();
        Register arrayPtr = asRegister(arrayReg);
        Register arrayLength = asRegister(lengthReg);
        Register index = asRegister(resultValue);
        Value[] searchValue = {
                        nValues > 0 ? searchValue1 : null,
                        nValues > 1 ? searchValue2 : null,
                        nValues > 2 ? searchValue3 : null,
                        nValues > 3 ? searchValue4 : null,
        };
        Register[] vecCmp = asRegisters(vectorCompareVal);
        Register[] vecArray = asRegisters(vectorArray);
        Register[] vecTmp = asRegisters(vectorTemp);
        if (isConsecutiveTableVariant()) {
            emitFindConsecutiveTablesCode(crb, asm, arrayPtr, arrayLength, index, asRegister(searchValue[0]), vecCmp, vecArray, vecTmp);
            asm.resetAvxEncoding(oldEncoding);
            return;
        }
        DataSection.Data reverseBytesMask = null;
        Label ret = new Label();
        Label bulkVectorLoop = new Label();
        Label singleVectorLoop = new Label();
        Label[] vectorFound = {
                        new Label(),
                        new Label(),
                        new Label(),
                        new Label(),
        };
        Label runVectorized = new Label();
        Label qWordWise = new Label();
        Label elementWise = new Label();
        Label elementWiseLoop = new Label();
        Label elementWiseFound = new Label();
        Label elementWiseNotFound = new Label();
        Label skipBulkVectorLoop = new Label();
        Label bsfAdd = new Label();
        int vectorLength = variant.isTable() ? vectorKind.getSizeInBytes() : vectorKind.getVectorLength();
        int bulkSize = vectorLength * nVectors;

        if (useConstantOffset()) {
            asm.leaq(arrayPtr, new AMD64Address(arrayPtr, constOffset));
        } else {
            asm.leaq(arrayPtr, new AMD64Address(arrayPtr, asRegister(offsetReg), Stride.S1));
        }

        // index = fromIndex + vectorLength (+1 if findTwoConsecutive)
        asm.leaq(index, new AMD64Address(asRegister(fromIndexReg), vectorLength + (findTwoConsecutive ? 1 : 0)));

        // re-use fromIndex register as temp
        Register cmpResult = asRegister(fromIndexReg);

        if (variant.isTable()) {
            // load lookup tables
            loadMask(crb, asm, Stride.S1, vecTmp[0], 0x0f);
            asm.movdqu(vectorSize, vecCmp[0], new AMD64Address(asRegister(searchValue[0])));
            if (vectorSize == AVXSize.XMM) {
                asm.movdqu(AVXSize.XMM, vecCmp[1], new AMD64Address(asRegister(searchValue[0]), AVXSize.XMM.getBytes()));
            } else {
                assert vectorSize == AVXSize.YMM : Assertions.errorMessage(vectorSize);
                // duplicate lookup tables
                AMD64Assembler.VexRVMIOp.VPERM2I128.emit(asm, AVXSize.YMM, vecCmp[1], vecCmp[0], vecCmp[0], 0x11);
                AMD64Assembler.VexRVMIOp.VPERM2I128.emit(asm, AVXSize.YMM, vecCmp[0], vecCmp[0], vecCmp[0], 0x00);
                if (stride == Stride.S4) {
                    // load permutation table for fixing the results of narrowing operations via
                    // PACKUSDW/PACKUSWB
                    loadMask(crb, asm, vecTmp[3], getAVX2IntToBytePackingUnscrambleMap());
                }
            }
        } else {
            // move search values to vectors
            for (int i = 0; i < nValues; i++) {
                // fill comparison vector with copies of the search value
                broadcastSearchValue(crb, asm, vecCmp[i], searchValue[i], cmpResult, vecArray[0]);
            }
        }
        if (variant.isForeignEndian()) {
            GraalError.guarantee(stride.value > 1, "ForeignEndian search modes are not implemented for Stride 1");
            reverseBytesMask = writeToDataSection(crb, getReverseBytesMask(stride));
        }

        // check if vector load is in bounds
        asm.cmpqAndJcc(index, arrayLength, ConditionFlag.LessEqual, runVectorized, false);

        if (supportsAVX2AndYMM()) {
            // region is too short for YMM vectors, try XMM
            Label[] xmmFound = {new Label()};
            // index = fromIndex (+ 1 if findTwoConsecutive) + XMM vector size
            asm.subq(index, vectorLength / 2);
            // check if vector load is in bounds
            asm.cmpqAndJcc(index, arrayLength, ConditionFlag.Greater, variant.isTable() ? elementWise : qWordWise, false);
            // do one vector comparison from fromIndex
            emitVectorCompare(crb, asm, AVXSize.XMM, 1, arrayPtr, index, vecCmp, vecArray, vecTmp, reverseBytesMask, cmpResult, xmmFound, false);
            // and one aligned to the array end
            asm.movq(index, arrayLength);
            emitVectorCompare(crb, asm, AVXSize.XMM, 1, arrayPtr, index, vecCmp, vecArray, vecTmp, reverseBytesMask, cmpResult, xmmFound, true);
            // no match, return negative
            asm.jmp(elementWiseNotFound);
            // match found, adjust index by XMM offset
            asm.bind(xmmFound[0]);
            asm.subq(index, (vectorLength / 2) + (findTwoConsecutive ? 1 : 0));
            // return index found
            asm.jmp(bsfAdd);
        }

        int vectorLengthQWord = AVXSize.QWORD.getBytes() / stride.value;
        if (!variant.isTable()) {
            asm.bind(qWordWise);
            // region is too short for XMM vectors, try QWORD
            Label[] qWordFound = {new Label()};
            // index = fromIndex (+ 1 if findTwoConsecutive) + QWORD vector size
            asm.subq(index, vectorLengthQWord);
            // check if vector load is in bounds
            asm.cmpqAndJcc(index, arrayLength, ConditionFlag.Greater, elementWise, false);
            // do one vector comparison from fromIndex
            emitVectorCompare(crb, asm, AVXSize.QWORD, 1, arrayPtr, index, vecCmp, vecArray, vecTmp, reverseBytesMask, cmpResult, qWordFound, false);
            // and one aligned to the array end
            asm.movq(index, arrayLength);
            emitVectorCompare(crb, asm, AVXSize.QWORD, 1, arrayPtr, index, vecCmp, vecArray, vecTmp, reverseBytesMask, cmpResult, qWordFound, true);
            // no match, return negative
            asm.jmpb(elementWiseNotFound);
            // match found, adjust index by QWORD offset
            asm.bind(qWordFound[0]);
            asm.subq(index, vectorLengthQWord + (findTwoConsecutive ? 1 : 0));
            // return index found
            asm.jmp(bsfAdd);
        }

        // search range is smaller than vector size, do element-wise comparison
        asm.bind(elementWise);
        // index = fromIndex (+ 1 if findTwoConsecutive)
        asm.subq(index, variant.isTable() ? (supportsAVX2AndYMM() ? (vectorLength / 2) : vectorLength) : vectorLengthQWord);
        // check if enough array slots remain
        asm.cmpqAndJcc(index, arrayLength, ConditionFlag.GreaterEqual, elementWiseNotFound, true);

        OperandSize valueSize = getOpSize(stride);
        // combine consecutive search values into one register
        if (findTwoConsecutive) {
            asm.shlq(asRegister(searchValue[1]), stride.getBitCount());
            asm.orq(asRegister(searchValue[0]), asRegister(searchValue[1]));
            if (withMask) {
                if (isStackSlot(searchValue[3])) {
                    asm.movSZx(valueSize, ZERO_EXTEND, asRegister(searchValue[1]), (AMD64Address) crb.asAddress(searchValue[3]));
                } else {
                    asm.movq(asRegister(searchValue[1]), asRegister(searchValue[3]));
                }
                asm.shlq(asRegister(searchValue[1]), stride.getBitCount());
                if (isStackSlot(searchValue[2])) {
                    asm.movSZx(valueSize, ZERO_EXTEND, cmpResult, (AMD64Address) crb.asAddress(searchValue[2]));
                    asm.orq(asRegister(searchValue[1]), cmpResult);
                } else {
                    asm.orq(asRegister(searchValue[1]), asRegister(searchValue[2]));
                }
            }
        }

        // compare one-by-one
        asm.bind(elementWiseLoop);
        boolean valuesOnStack = searchValuesOnStack(searchValue);
        // address = findTwoConsecutive ? array[index - 1] : array[index]
        AMD64Address arrayAddr = new AMD64Address(arrayPtr, index, arrayIndexStride, findTwoConsecutive ? -stride.value : 0);
        switch (variant) {
            case MatchAny:
                asm.movSZx(valueSize, ZERO_EXTEND, cmpResult, arrayAddr);
                for (int i = 0; i < nValues; i++) {
                    cmplAndJcc(crb, asm, cmpResult, searchValue[i], elementWiseFound, ConditionFlag.Equal, true);
                }
                break;
            case MatchRange, MatchRangeForeignEndian:
                asm.movSZx(valueSize, ZERO_EXTEND, cmpResult, arrayAddr);
                if (variant == LIRGeneratorTool.ArrayIndexOfVariant.MatchRangeForeignEndian) {
                    reverseBytesScalar(asm, valueSize, cmpResult);
                }
                for (int i = 0; i < nValues; i += 2) {
                    Label noMatch = new Label();
                    cmplAndJcc(crb, asm, cmpResult, searchValue[i], noMatch, ConditionFlag.Below, true);
                    cmplAndJcc(crb, asm, cmpResult, searchValue[i + 1], elementWiseFound, ConditionFlag.BelowEqual, true);
                    asm.bind(noMatch);
                }
                break;
            case WithMask:
                assert !valuesOnStack;
                assert nValues == 2 : nValues;
                asm.movSZx(valueSize, ZERO_EXTEND, cmpResult, arrayAddr);
                asm.orq(cmpResult, asRegister(searchValue[1]));
                asm.cmpqAndJcc(cmpResult, asRegister(searchValue[0]), AMD64Assembler.ConditionFlag.Equal, elementWiseFound, true);
                break;
            case FindTwoConsecutive:
                asm.cmpAndJcc(getDoubleOpSize(stride), asRegister(searchValue[0]), arrayAddr, AMD64Assembler.ConditionFlag.Equal, elementWiseFound, true);
                break;
            case FindTwoConsecutiveWithMask:
                asm.movSZx(getDoubleOpSize(stride), ZERO_EXTEND, cmpResult, arrayAddr);
                asm.orq(cmpResult, asRegister(searchValue[1]));
                asm.cmpqAndJcc(cmpResult, asRegister(searchValue[0]), AMD64Assembler.ConditionFlag.Equal, elementWiseFound, true);
                break;
            case Table, TableForeignEndian:
                Label greaterThan0xff = new Label();
                Register tmp = asRegister(tableLookupTemp);
                asm.movSZx(valueSize, ZERO_EXTEND, cmpResult, arrayAddr);
                if (variant == LIRGeneratorTool.ArrayIndexOfVariant.TableForeignEndian) {
                    reverseBytesScalar(asm, valueSize, cmpResult);
                }
                if (stride.value > 1) {
                    asm.cmpqAndJcc(cmpResult, 0xff, ConditionFlag.Above, greaterThan0xff, true);
                }
                asm.movq(tmp, cmpResult);
                asm.sarq(cmpResult, 4);
                asm.andq(tmp, 0xf);
                asm.movzbq(cmpResult, new AMD64Address(asRegister(searchValue[0]), cmpResult, Stride.S1, 0));
                asm.movzbq(tmp, new AMD64Address(asRegister(searchValue[0]), tmp, Stride.S1, 16));
                asm.andqAndJcc(cmpResult, tmp, ConditionFlag.NotZero, elementWiseFound, true);
                asm.bind(greaterThan0xff);
                break;
        }
        // adjust index
        asm.incrementq(index, 1);
        // continue loop
        asm.cmpqAndJcc(index, arrayLength, ConditionFlag.Less, elementWiseLoop, true);

        asm.bind(elementWiseNotFound);
        asm.xorq(index, index);

        if (findTwoConsecutive) {
            asm.bind(elementWiseFound);
            asm.decrementq(index, 1);
        } else {
            asm.decrementq(index, 1);
            asm.bind(elementWiseFound);
        }
        asm.jmp(ret);

        // vectorized implementation
        asm.bind(runVectorized);

        // do one unaligned vector comparison pass and adjust alignment afterwards
        emitVectorCompare(crb, asm, vectorSize, 1, arrayPtr, index, vecCmp, vecArray, vecTmp, reverseBytesMask, cmpResult, vectorFound, false);

        // adjust index to vector size alignment
        asm.movl(cmpResult, arrayPtr);
        if (stride.value > 1) {
            asm.shrl(cmpResult, stride.log2);
        }
        asm.addq(index, cmpResult);
        // adjust to next lower multiple of vector size
        asm.andq(index, -vectorLength);
        asm.subq(index, cmpResult);
        // add bulk size
        asm.addq(index, bulkSize);

        boolean bulkLoopShortJmp = !((variant.isMatchRange() && nValues == 4 || variant.isTable()) && stride.value > 1);
        /*
         * Check if there are enough array slots remaining for the bulk loop. Note: The alignment
         * following the cmpAndJcc can lead to a jump distance > 127. This prevents safely using a
         * short jump.
         */
        asm.cmpqAndJcc(index, arrayLength, ConditionFlag.Greater, skipBulkVectorLoop, false);

        asm.align(preferredLoopAlignment(crb));
        asm.bind(bulkVectorLoop);
        // memory-aligned bulk comparison
        emitVectorCompare(crb, asm, vectorSize, nVectors, arrayPtr, index, vecCmp, vecArray, vecTmp, reverseBytesMask, cmpResult, vectorFound, false);
        // adjust index
        asm.addq(index, bulkSize);
        // check if there are enough array slots remaining for the bulk loop
        asm.cmpqAndJcc(index, arrayLength, ConditionFlag.LessEqual, bulkVectorLoop, bulkLoopShortJmp);

        asm.bind(skipBulkVectorLoop);
        if (nVectors == 1) {
            // do last load from end of array
            asm.movq(index, arrayLength);
            // compare
            emitVectorCompare(crb, asm, vectorSize, 1, arrayPtr, index, vecCmp, vecArray, vecTmp, reverseBytesMask, cmpResult, vectorFound, true);
        } else {
            // remove bulk offset
            asm.subq(index, bulkSize);
            asm.align(preferredLoopAlignment(crb));
            // same loop as bulkVectorLoop, with only one vector
            asm.bind(singleVectorLoop);
            // add vector size
            asm.addq(index, vectorLength);
            // check if vector load is in bounds
            asm.cmpq(index, arrayLength);
            // if load would be over bounds, set the load to the end of the array
            asm.cmovq(AMD64Assembler.ConditionFlag.Greater, index, arrayLength);
            // compare
            emitVectorCompare(crb, asm, vectorSize, 1, arrayPtr, index, vecCmp, vecArray, vecTmp, reverseBytesMask, cmpResult, vectorFound, true);
            // check if there are enough array slots remaining for the loop
            asm.cmpqAndJcc(index, arrayLength, ConditionFlag.Less, singleVectorLoop, true);
        }

        asm.movl(index, -1);
        asm.jmpb(ret);
        for (int i = 0; i < nVectors; i++) {
            asm.bind(vectorFound[i]);
            // add static offset
            asm.subq(index, getResultIndexDelta(i));
            if (i < nVectors - 1) {
                asm.jmpb(bsfAdd);
            }
        }
        asm.bind(bsfAdd);
        if (variant.isTable()) {
            asm.pxor(vectorSize, vecTmp[1], vecTmp[1]);
            asm.pcmpeqb(vectorSize, vecTmp[2], vecTmp[1]);
            asm.pmovmsk(vectorSize, cmpResult, vecTmp[2]);
            asm.notq(cmpResult);
        }
        // find offset
        bsfq(asm, cmpResult, cmpResult);
        if (stride.value > 1 && !variant.isTable()) {
            // convert byte offset to chars
            asm.shrq(cmpResult, stride.log2);
        }
        // add offset to index
        asm.addq(index, cmpResult);

        asm.bind(ret);

        asm.resetAvxEncoding(oldEncoding);
    }

    private static void reverseBytesScalar(AMD64MacroAssembler asm, OperandSize valueSize, Register value) {
        switch (valueSize) {
            case BYTE -> {
            }
            case WORD -> asm.rorw(value, 8);
            case DWORD -> asm.bswapl(value);
            case QWORD -> asm.bswapq(value);
            default -> GraalError.shouldNotReachHereUnexpectedValue(valueSize);
        }
    }

    private int getNumberOfVectorsInBulkLoop() {
        switch (variant) {
            case MatchAny:
                return nValues == 1 ? 4 : nValues == 2 ? 2 : 1;
            case FindTwoConsecutive:
                return 2;
            default:
                return 1;
        }
    }

    private static void cmplAndJcc(CompilationResultBuilder crb, AMD64MacroAssembler asm, Register src1, Value src2, Label branchTarget, ConditionFlag cc, boolean isShortJmp) {
        if (isStackSlot(src2)) {
            asm.cmplAndJcc(src1, (AMD64Address) crb.asAddress(src2), cc, branchTarget, isShortJmp);
        } else {
            asm.cmplAndJcc(src1, asRegister(src2), cc, branchTarget, isShortJmp);
        }
    }

    private boolean searchValuesOnStack(Value[] searchValue) {
        for (int i = 0; i < nValues; i++) {
            if (isStackSlot(searchValue[i])) {
                return true;
            }
        }
        return false;
    }

    private int getResultIndexDelta(int i) {
        if (variant.isTable()) {
            return vectorSize.getBytes();
        }
        return (i + 1) * vectorKind.getVectorLength() + (findTwoConsecutive ? 1 : 0);
    }

    private int getVectorOffset(int i, int j, AVXSize targetVectorSize) {
        if (findTwoConsecutive) {
            return -(((i + 1) * targetVectorSize.getBytes() + ((j ^ 1) * stride.value)));
        }
        return -(((i + 1) * targetVectorSize.getBytes()));
    }

    private void broadcastSearchValue(CompilationResultBuilder crb, AMD64MacroAssembler asm, Register dst, Value srcVal, Register tmpReg, Register tmpVector) {
        Register src = asRegOrTmpReg(crb, asm, srcVal, tmpReg);
        asm.movdl(dst, src);
        emitBroadcast(asm, stride, dst, tmpVector, vectorSize);
    }

    private static boolean isConstant(Value val) {
        assert !(val instanceof ConstantValue) || ((ConstantValue) val).isJavaConstant();
        return val instanceof ConstantValue;
    }

    private static JavaConstant asConstant(Value val) {
        return ((ConstantValue) val).getJavaConstant();
    }

    private static Register asRegOrTmpReg(CompilationResultBuilder crb, AMD64MacroAssembler asm, Value val, Register tmpReg) {
        if (isRegister(val)) {
            return asRegister(val);
        } else if (isStackSlot(val)) {
            asm.movl(tmpReg, (AMD64Address) crb.asAddress(val));
            return tmpReg;
        } else {
            assert isConstant(val);
            asm.movl(tmpReg, asConstant(val).asInt());
            return tmpReg;
        }
    }

    /**
     * Fills {@code vecDst} with copies of its lowest byte, word or dword.
     */
    private static void emitBroadcast(AMD64MacroAssembler asm, Stride stride, Register vecDst, Register vecTmp, AVXSize targetVectorSize) {
        switch (stride) {
            case S1:
                if (asm.supports(CPUFeature.AVX2)) {
                    VexRMOp.VPBROADCASTB.emit(asm, targetVectorSize, vecDst, vecDst);
                } else if (asm.supports(CPUFeature.AVX)) {
                    VexRVMOp.VPXOR.emit(asm, targetVectorSize, vecTmp, vecTmp, vecTmp);
                    VexRVMOp.VPSHUFB.emit(asm, targetVectorSize, vecDst, vecDst, vecTmp);
                } else if (asm.supports(CPUFeature.SSSE3)) {
                    asm.pxor(vecTmp, vecTmp);
                    asm.pshufb(vecDst, vecTmp);
                } else { // SSE2
                    asm.punpcklbw(vecDst, vecDst);
                    asm.punpcklbw(vecDst, vecDst);
                    asm.pshufd(vecDst, vecDst, 0);
                }
                break;
            case S2:
                if (asm.supports(CPUFeature.AVX2)) {
                    VexRMOp.VPBROADCASTW.emit(asm, targetVectorSize, vecDst, vecDst);
                } else if (asm.supports(CPUFeature.AVX)) {
                    VexRMIOp.VPSHUFLW.emit(asm, targetVectorSize, vecDst, vecDst, 0);
                    VexRMIOp.VPSHUFD.emit(asm, targetVectorSize, vecDst, vecDst, 0);
                } else { // SSE
                    asm.pshuflw(vecDst, vecDst, 0);
                    asm.pshufd(vecDst, vecDst, 0);
                }
                break;
            case S4:
                if (asm.supports(CPUFeature.AVX2)) {
                    VexRMOp.VPBROADCASTD.emit(asm, targetVectorSize, vecDst, vecDst);
                } else if (asm.supports(CPUFeature.AVX)) {
                    VexRMIOp.VPSHUFD.emit(asm, targetVectorSize, vecDst, vecDst, 0);
                } else { // SSE
                    asm.pshufd(vecDst, vecDst, 0);
                }
                break;
            default:
                throw new UnsupportedOperationException();
        }
    }

    @SuppressWarnings("fallthrough")
    private void emitVectorCompare(CompilationResultBuilder crb,
                    AMD64MacroAssembler asm,
                    AVXSize vSize,
                    int nVectors,
                    Register arrayPtr,
                    Register index,
                    Register[] vecCmp,
                    Register[] vecArray,
                    Register[] vecTmp,
                    DataSection.Data reverseBytesMask,
                    Register cmpResult,
                    Label[] vectorFound,
                    boolean shortJmp) {
        // load array contents into vectors
        int nVectorLoads = variant.isTable() ? stride.value : nVectors;
        for (int i = 0; i < nVectorLoads; i++) {
            int base = i * nValues;
            for (int j = 0; j < (withMask || variant.isMatchRange() ? nValues / 2 : nValues); j++) {
                emitArrayLoad(asm, vSize, vecArray[base + j], arrayPtr, index, arrayIndexStride, getVectorOffset(nVectorLoads - (i + 1), j, vSize));
            }
        }
        switch (variant) {
            case MatchAny:
                for (int i = 0; i < nVectors; i++) {
                    int base = i * nValues;
                    for (int j = 0; j < nValues; j++) {
                        asm.pcmpeq(vSize, stride, vecArray[base + j], vecCmp[j]);
                        if ((j & 1) == 1) {
                            asm.por(vSize, vecArray[base + j - 1], vecArray[base + j]);
                        }
                    }
                    if (nValues > 2) {
                        asm.por(vSize, vecArray[base], vecArray[base + 2]);
                    }
                    asm.pmovmsk(vSize, cmpResult, vecArray[base]);
                    emitVectorCompareCheckVectorFound(asm, vSize, cmpResult, vectorFound[nVectors - (i + 1)], shortJmp);
                }
                break;
            case MatchRangeForeignEndian:
                assert nVectors == 1 : nVectors;
                asm.pshufb(vSize == AVXSize.QWORD ? AVXSize.XMM : vSize, vecArray[0], (AMD64Address) crb.recordDataSectionReference(reverseBytesMask));
                // fallthrough
            case MatchRange:
                assert nVectors == 1 : nVectors;
                if (nValues == 2) {
                    asm.pminu(vSize, stride, vecTmp[0], vecCmp[0], vecArray[0]);
                    asm.pminu(vSize, stride, vecTmp[1], vecArray[0], vecCmp[1]);
                    asm.pcmpeq(vSize, stride, vecTmp[0], vecCmp[0]);
                    asm.pcmpeq(vSize, stride, vecTmp[1], vecArray[0]);
                    asm.pand(vSize, vecTmp[0], vecTmp[1]);
                } else {
                    assert nValues == 4 : nValues;
                    asm.pminu(vSize, stride, vecTmp[0], vecCmp[0], vecArray[0]);
                    asm.pminu(vSize, stride, vecTmp[1], vecArray[0], vecCmp[1]);
                    asm.pcmpeq(vSize, stride, vecTmp[0], vecCmp[0]);
                    asm.pcmpeq(vSize, stride, vecTmp[1], vecArray[0]);
                    asm.pand(vSize, vecTmp[0], vecTmp[1]);
                    asm.pminu(vSize, stride, vecTmp[1], vecCmp[2], vecArray[0]);
                    asm.pminu(vSize, stride, vecTmp[2], vecArray[0], vecCmp[3]);
                    asm.pcmpeq(vSize, stride, vecTmp[1], vecCmp[2]);
                    asm.pcmpeq(vSize, stride, vecTmp[2], vecArray[0]);
                    asm.pand(vSize, vecTmp[1], vecTmp[2]);
                    asm.por(vSize, vecTmp[0], vecTmp[1]);
                }
                asm.pmovmsk(vSize, cmpResult, vecTmp[0]);
                emitVectorCompareCheckVectorFound(asm, vSize, cmpResult, vectorFound[0], shortJmp);
                break;
            case WithMask:
                assert nValues == 2 && nVectors == 1 : Assertions.errorMessage(nValues, nVectors);
                asm.por(vSize, vecArray[0], vecCmp[1]);
                asm.pcmpeq(vSize, stride, vecArray[0], vecCmp[0]);
                asm.pmovmsk(vSize, cmpResult, vecArray[0]);
                emitVectorCompareCheckVectorFound(asm, vSize, cmpResult, vectorFound[0], shortJmp);
                break;
            case FindTwoConsecutive:
                for (int i = 0; i < nVectors << 1; i += 2) {
                    asm.pcmpeq(vSize, stride, vecArray[i], vecCmp[0]);
                    asm.pcmpeq(vSize, stride, vecArray[i + 1], vecCmp[1]);
                    asm.pand(vSize, vecArray[i], vecArray[i + 1]);
                    asm.pmovmsk(vSize, cmpResult, vecArray[i]);
                    emitVectorCompareCheckVectorFound(asm, vSize, cmpResult, vectorFound[nVectors - ((i / 2) + 1)], shortJmp);
                }
                break;
            case FindTwoConsecutiveWithMask:
                for (int i = 0; i < nVectors << 1; i += 2) {
                    asm.por(vSize, vecArray[i], vecCmp[2]);
                    asm.por(vSize, vecArray[i + 1], vecCmp[3]);
                    asm.pcmpeq(vSize, stride, vecArray[i], vecCmp[0]);
                    asm.pcmpeq(vSize, stride, vecArray[i + 1], vecCmp[1]);
                    asm.pand(vSize, vecArray[i], vecArray[i + 1]);
                    asm.pmovmsk(vSize, cmpResult, vecArray[i]);
                    emitVectorCompareCheckVectorFound(asm, vSize, cmpResult, vectorFound[nVectors - ((i / 2) + 1)], shortJmp);
                }
                break;
            case Table, TableForeignEndian:
                Register mask0xf = vecTmp[0];
                Register tableHi = vecCmp[0];
                Register tableLo = vecCmp[1];
                switch (stride) {
                    case S1:
                        performTableLookup(asm, vSize, mask0xf, tableHi, tableLo, vecArray[0], vecTmp[1], vecTmp[2], vecTmp[3]);
                        break;
                    case S2:
                        reverseBytesIfForeignEndian(crb, asm, vecArray, reverseBytesMask);
                        // narrow string chars to bytes
                        asm.packuswb(vSize, vecTmp[1], vecArray[0], vecArray[1]);
                        // right-shift chars by 8
                        asm.psrlw(vSize, vecArray[0], vecArray[0], 8);
                        asm.psrlw(vSize, vecArray[1], vecArray[1], 8);
                        // create a mask for all chars > 0xff
                        asm.pxor(vSize, vecTmp[2], vecTmp[2]);
                        asm.packuswb(vSize, vecArray[0], vecArray[1]);
                        asm.pcmpeqb(vSize, vecArray[0], vecTmp[2]);
                        // lookup
                        performTableLookup(asm, vSize, mask0xf, tableHi, tableLo, vecTmp[1], vecArray[1], vecTmp[2], vecTmp[3]);
                        // eliminate matches of chars > 0xff
                        asm.pand(vSize, vecTmp[2], vecArray[0]);
                        // unscramble
                        if (vSize == AVXSize.YMM) {
                            VexRMIOp.VPERMQ.emit(asm, vSize, vecTmp[2], vecTmp[2], 0b11011000);
                        }
                        break;
                    case S4:
                        reverseBytesIfForeignEndian(crb, asm, vecArray, reverseBytesMask);
                        // narrow string ints to bytes
                        asm.packusdw(vSize, vecTmp[1], vecArray[0], vecArray[1]);
                        asm.packusdw(vSize, vecTmp[2], vecArray[2], vecArray[3]);
                        // right-shift ints by 8
                        asm.psrld(vSize, vecArray[0], vecArray[0], 8);
                        asm.psrld(vSize, vecArray[1], vecArray[1], 8);
                        asm.psrld(vSize, vecArray[2], vecArray[2], 8);
                        asm.psrld(vSize, vecArray[3], vecArray[3], 8);
                        // finish narrowing
                        asm.packuswb(vSize, vecTmp[1], vecTmp[2]);
                        // create a mask for all ints <= 0xff
                        asm.packssdw(vSize, vecArray[0], vecArray[1]);
                        asm.packssdw(vSize, vecArray[2], vecArray[3]);
                        asm.pxor(vSize, vecTmp[2], vecTmp[2]);
                        asm.packuswb(vSize, vecArray[0], vecArray[2]);
                        asm.pcmpeqb(vSize, vecArray[0], vecTmp[2]);
                        // lookup
                        performTableLookup(asm, vSize, mask0xf, tableHi, tableLo, vecTmp[1], vecArray[1], vecTmp[2], vecArray[2]);
                        // eliminate matches of ints > 0xff
                        asm.pand(vSize, vecTmp[2], vecArray[0]);
                        // unscramble
                        if (vSize == AVXSize.YMM) {
                            VexRVMOp.VPERMD.emit(asm, vSize, vecTmp[2], vecTmp[3], vecTmp[2]);
                        }
                        break;
                    default:
                        throw GraalError.shouldNotReachHereUnexpectedValue(stride); // ExcludeFromJacocoGeneratedReport
                }
                asm.ptest(vSize, vecTmp[2], vecTmp[2]);
                asm.jcc(ConditionFlag.NotZero, vectorFound[0], shortJmp);
                break;
        }
    }

    private static void performTableLookup(AMD64MacroAssembler asm, AVXSize vSize,
                    Register vMask0xf,
                    Register vTableHi,
                    Register vTableLo,
                    Register vArray,
                    Register vTmp1,
                    Register vTmp2,
                    Register vTmp3) {
        // split bytes into low and high nibbles (4-bit values)
        asm.psrlw(vSize, vTmp1, vArray, 4);
        asm.pand(vSize, vArray, vMask0xf);
        asm.pand(vSize, vTmp1, vMask0xf);
        // perform table lookup using nibbles as indices
        asm.pshufb(vSize, vTmp2, vTableHi, vTmp1);
        asm.pshufb(vSize, vTmp3, vTableLo, vArray);
        // AND lookup results. If the result is non-zero, a match was found
        asm.pand(vSize, vTmp2, vTmp3);
    }

    @SuppressWarnings("fallthrough")
    private static void emitVectorCompareCheckVectorFound(AMD64MacroAssembler asm, AVXSize targetVectorSize, Register cmpResult, Label branchTarget, boolean shortJmp) {
        switch (targetVectorSize) {
            case DWORD:
                asm.andlAndJcc(cmpResult, 0xf, ConditionFlag.NotZero, branchTarget, shortJmp);
                break;
            case QWORD:
                asm.andlAndJcc(cmpResult, 0xff, ConditionFlag.NotZero, branchTarget, shortJmp);
                break;
            case XMM:
            case YMM:
                asm.testlAndJcc(cmpResult, cmpResult, ConditionFlag.NotZero, branchTarget, shortJmp);
                break;
            case ZMM:
                throw GraalError.shouldNotReachHereUnexpectedValue(targetVectorSize); // ExcludeFromJacocoGeneratedReport
        }
    }

    @SuppressWarnings("fallthrough")
    static void emitArrayLoad(AMD64MacroAssembler asm, AVXSize targetVectorSize, Register vecDst, Register array, Register index, Stride stride, int displacement) {
        AMD64Address src = new AMD64Address(array, index, stride, displacement);
        if (asm.supports(CPUFeature.AVX)) {
            switch (targetVectorSize) {
                case DWORD:
                    // Move a dword into an XMM register
                    VexMoveOp.VMOVD.emit(asm, AVXSize.XMM, vecDst, src);
                    break;
                case QWORD:
                    // Move a qword into an XMM register
                    VexMoveOp.VMOVQ.emit(asm, AVXSize.XMM, vecDst, src);
                    break;
                case XMM:
                case YMM:
                    VexMoveOp.VMOVDQU32.emit(asm, targetVectorSize, vecDst, src);
                    break;
                case ZMM:
                    VexMoveOp.VMOVDQU64.emit(asm, targetVectorSize, vecDst, src);
                    break;
            }
        } else {
            // SSE
            switch (targetVectorSize) {
                case DWORD:
                    asm.movdl(vecDst, src);
                    break;
                case QWORD:
                    asm.movdq(vecDst, src);
                    break;
                case XMM:
                case YMM:
                    asm.movdqu(vecDst, src);
                    break;
                case ZMM:
                    throw GraalError.shouldNotReachHereUnexpectedValue(targetVectorSize); // ExcludeFromJacocoGeneratedReport
            }
        }
    }

    static OperandSize getOpSize(Stride stride) {
        switch (stride) {
            case S1:
                return OperandSize.BYTE;
            case S2:
                return OperandSize.WORD;
            case S4:
                return OperandSize.DWORD;
            default:
                return OperandSize.QWORD;
        }
    }

    private static OperandSize getDoubleOpSize(Stride stride) {
        switch (stride) {
            case S1:
                return OperandSize.WORD;
            case S2:
                return OperandSize.DWORD;
            default:
                assert stride == Stride.S4 : stride;
                return OperandSize.QWORD;
        }
    }

    private void reverseBytesIfForeignEndian(CompilationResultBuilder crb, AMD64MacroAssembler asm, Register[] vecArray, DataSection.Data reverseBytesMask) {
        if (reverseBytesMask != null) {
            for (Register register : vecArray) {
                asm.pshufb(vectorSize, register, (AMD64Address) crb.recordDataSectionReference(reverseBytesMask));
            }
        }
    }

    private void emitFindConsecutiveTablesCode(CompilationResultBuilder crb,
                    AMD64MacroAssembler asm,
                    Register arrayPtr,
                    Register arrayLength,
                    Register index,
                    Register tables,
                    Register[] vecCmp,
                    Register[] vecArray,
                    Register[] vecTmp) {
        int tableCount = variant.tableCount();
        assert tableCount >= 2 : tableCount;
        assert stride.value <= 4 : stride.value;
        assert vecArray.length >= stride.value : vecArray.length;

        // arrayPtr points at the first searchable byte, index starts at fromIndex.
        if (useConstantOffset()) {
            asm.leaq(arrayPtr, new AMD64Address(arrayPtr, constOffset));
        } else {
            asm.leaq(arrayPtr, new AMD64Address(arrayPtr, asRegister(offsetReg), Stride.S1));
        }
        asm.movq(index, asRegister(fromIndexReg));

        // Helper masks for the consecutive-table path:
        // - reverseBytesMask byte-reverses foreign-endian S2/S4 input before narrowing
        // - extractCandidateMask extracts the first matching byte from a candidate vector
        // - mask0x0f isolates the two nibble indices used by the lookup tables
        // - s4UnscrambleMap restores element order after the S4 narrowing pack operations
        DataSection.Data reverseBytesMask = variant.isForeignEndian() ? writeToDataSection(crb, getReverseBytesMask(stride)) : null;
        DataSection.Data extractCandidateMask = writeToDataSection(crb, createExtractCandidateMask(vectorSize));
        DataSection.Data mask0x0f = createMask(crb, Stride.S1, 0x0f);
        DataSection.Data s4UnscrambleMap = stride == Stride.S4 ? writeToDataSection(crb, getAVX2IntToBytePackingUnscrambleMap()) : null;
        boolean usePackedCarry = usePackedCarryForConsecutiveTables();

        // Load one hi/lo lookup-table pair per logical table. YMM duplicates each 128-bit half
        // because PSHUFB works independently in the low and high lanes.
        if (vectorSize == AVXSize.XMM) {
            for (int i = 0; i < variant.tableCount() * 2; i++) {
                asm.movdqu(vectorSize, vecCmp[i], new AMD64Address(tables, i * 16));
            }
        } else {
            assert vectorSize == AVXSize.YMM : Assertions.errorMessage(vectorSize);
            for (int i = 0; i < variant.tableCount(); i++) {
                // load both 16-byte tables with a single YMM load
                asm.movdqu(vectorSize, vecCmp[i * 2], new AMD64Address(tables, i * 32));
            }
            for (int i = 0; i < variant.tableCount(); i++) {
                // duplicate upper half into a separate vector
                AMD64Assembler.VexRVMIOp.VPERM2I128.emit(asm, AVXSize.YMM, vecCmp[i * 2 + 1], vecCmp[i * 2], vecCmp[i * 2], 0x11);
            }
            for (int i = 0; i < variant.tableCount(); i++) {
                // duplicate lower half
                AMD64Assembler.VexRVMIOp.VPERM2I128.emit(asm, AVXSize.YMM, vecCmp[i * 2], vecCmp[i * 2], vecCmp[i * 2], 0x00);
            }
        }

        int vectorLength = vectorSize.getBytes();
        Register lastVectorStart = asRegister(offsetReg);
        Register cmpMask = asRegister(fromIndexReg);
        Register tmp = asRegister(tableLookupTemp);
        // vectorCandidate holds the final "all tables matched" byte mask for the current vector.
        // vPrevCandidate caches the previous vector's intermediate candidates to account for
        // matches that cross vector boundaries.
        Register vectorCandidate = vecTmp[vecTmp.length - 1];
        int firstPrevCandidateTempIndex = vecTmp.length - tableCount;
        Register[] vPrevCandidate = Arrays.copyOfRange(vecTmp, firstPrevCandidateTempIndex, firstPrevCandidateTempIndex + (variant.tableCount() - 1));
        Label vectorLoop = new Label();
        Label vectorLoopAfterLoad = new Label();
        Label vectorFound = new Label();
        Label shortVector = new Label();
        Label noMatch = new Label();
        Label ret = new Label();

        // Set previous candidate vectors to zero.
        for (Register register : vPrevCandidate) {
            asm.pxor(vectorSize, register, register);
        }
        // Calculate main loop break point.
        asm.movq(lastVectorStart, arrayLength);
        asm.subq(lastVectorStart, vectorLength);
        if (vectorSize == YMM) {
            // this intrinsic is never called with a search window (array length - fromIndex)
            // smaller than 16, so we only need to check for window lengths less than 32 on YMM. In
            // all other cases, the search window will always fit at least one main loop iteration.
            asm.cmpqAndJcc(index, lastVectorStart, ConditionFlag.Greater, shortVector, false);
        }

        // main vector loop
        asm.align(preferredLoopAlignment(crb));
        asm.bind(vectorLoop);
        emitConsecutiveTablesMainLoopBody(crb, asm, vectorSize, arrayPtr, index, vecCmp, vecArray, vecTmp, vPrevCandidate, vectorCandidate, reverseBytesMask, mask0x0f,
                        s4UnscrambleMap, vectorLoopAfterLoad, vectorFound);
        asm.addq(index, vectorLength);
        asm.cmpqAndJcc(index, lastVectorStart, ConditionFlag.LessEqual, vectorLoop, tableCount == 2 && stride == Stride.S1);

        // Tail handling. At this point, there are between 0 and vectorSize - 1 elements remaining.
        asm.cmpqAndJcc(index, arrayLength, ConditionFlag.GreaterEqual, noMatch, false);
        // To process the tail, we rewind the index to arrayLength - vectorSize and perform one
        // additional main loop iteration. After that iteration, the condition above will trigger
        // and exit the function. For correctness, we need to adjust vPrevCandidate before jumping
        // to the main loop again: The input data loaded in the last operation will overlap with the
        // previous "regular" iteration, but there may still be cross-boundary matches to be
        // considered, specifically if (tailLength > vectorLength - tableCount).
        if (tableCount == 2) {
            // in two-table mode, we don't need to preserve the previous candidate: there is always
            // an overlap of at least one element with the previous loop iteration, so any
            // cross-vector-boundary match would already be found in the previous iteration.
            asm.pxor(vectorSize, vPrevCandidate[0], vPrevCandidate[0]);
        } else if (!usePackedCarry) {
            // In 3 or 4 table mode, a cross-vector-boundary match is possible, so we need to
            // preserve and adjust prevCandidate's last 1 or 2 bytes. To make the prevCandidate
            // vectors line up with our adjusted index, we use a shuffle mask of
            // [0x80 x ((2 * vectorSize) - 3), 0x0d, 0x0e, 0x0f], which we load offset by the tail
            // length. This will zero all elements where the mask is 0x80, and adjust the last bytes
            // if tail length is large enough. For example, if we're using YMM vectors, any tail
            // length < 30 will zero the vector. Tail length 30 will move prevCandidate's elements
            // at position 29 and 30 to position 30 and 31. Tail length 31 will move element 30 to
            // 31.
            asm.movq(cmpMask, arrayLength);
            asm.subq(cmpMask, index);
            asm.leaq(tmp, getMaskOnce(crb, createCTTailPrevShuffleMask(vectorSize), vectorSize.getBytes() * 2));
            asm.movdqu(vectorSize, vectorCandidate, new AMD64Address(tmp, cmpMask, Stride.S1));
            for (Register vPrev : vPrevCandidate) {
                asm.pshufb(vectorSize, vPrev, vectorCandidate);
            }
        } else {
            // Packed S2/S4 4-table mode keeps carry in packed-byte form, which we can't easily
            // adjust to a different index. In this case, we check if the tail is long enough to
            // need adjustment, and if so, we jump to the dedicated path for small input arrays.
            // Otherwise, we simply clear the previous candidate vectors and jump to the main loop
            // as before.
            asm.movq(cmpMask, arrayLength);
            asm.subq(cmpMask, index);
            asm.cmpqAndJcc(cmpMask, vectorLength - (tableCount - 1), ConditionFlag.GreaterEqual, shortVector, false);
            asm.pxor(vectorSize, vPrevCandidate[0], vPrevCandidate[0]);
            asm.movq(index, lastVectorStart);
            asm.jmp(vectorLoop);
        }
        asm.movq(index, lastVectorStart);
        asm.jmp(vectorLoop);

        asm.bind(vectorFound);
        // vectorCandidate contains non-zero bytes at every end position of a matching sequence.
        // Find the first one, extract the matching byte
        Register vectorFoundScratch = vecTmp[0];
        // convert matches to 0x00, non-matches to 0xff
        asm.pxor(vectorSize, vectorFoundScratch, vectorFoundScratch);
        asm.pcmpeqb(vectorSize, vectorFoundScratch, vectorCandidate);
        // extract sign bits
        asm.pmovmsk(vectorSize, cmpMask, vectorFoundScratch);
        // invert
        asm.notl(cmpMask);
        if (vectorSize == AVXSize.XMM) {
            asm.andl(cmpMask, 0xffff);
        }
        // get first match index
        bsfq(asm, cmpMask, cmpMask);
        // Extract match byte. For this, we use the following shuffle mask (on YMM):
        // [0..16, 0x80 x 32, 0..16]
        // We load from this mask using the match index as offset. This will move the match byte to
        // position 0 if the match index is < 16, and to position 16 otherwise. The 0x80 values in
        // the mask's "middle" ensure that when match index is < 16, vector byte 16 will be cleared
        // by the shuffle, and vice versa. This allows us to POR the upper and lower vector half to
        // reliably get the match byte into vector element 0.
        asm.leaq(tmp, (AMD64Address) crb.recordDataSectionReference(extractCandidateMask));
        asm.movdqu(vectorSize, vectorFoundScratch, new AMD64Address(tmp, cmpMask, Stride.S1));
        asm.pshufb(vectorSize, vectorCandidate, vectorFoundScratch);
        if (vectorSize == YMM) {
            // split into upper and lower half vector
            AMD64Assembler.VexMRIOp.VEXTRACTI128.emit(asm, YMM, vectorFoundScratch, vectorCandidate, 1);
            // POR halves
            asm.por(vectorSize, vectorCandidate, vectorFoundScratch);
        }
        // load match byte into general purpose register
        asm.movdq(tmp, vectorCandidate);
        asm.andl(tmp, 0xff);
        asm.addl(index, cmpMask);
        // adjust matching index to start of sequence
        asm.subl(index, tableCount - 1);
        // pack result into [high 32 bits = byte, low 32 bits = index].
        asm.shlq(tmp, 32);
        asm.orq(index, tmp);
        asm.jmp(ret);

        if (vectorSize == YMM || usePackedCarry) {
            // Build one synthetic vector from region [index...arrayLength]. This path is used when
            // vectorSize is YMM and arrayLength - fromIndex is less than 32, or we're in packed
            // carry mode and (tailLength > vectorLength - tableCount).
            asm.bind(shortVector);
            asm.movq(cmpMask, index);
            asm.subq(cmpMask, arrayLength);
            switch (stride) {
                case S1 -> {
                    assert vectorSize == YMM : Assertions.errorMessage(vectorSize);
                    // overlapping half vector loads from index and arrayLength-(half vector size)
                    asm.movdqu(XMM, vecTmp[2], new AMD64Address(arrayPtr, index, arrayIndexStride));
                    asm.movdqu(XMM, vecArray[0], new AMD64Address(arrayPtr, arrayLength, arrayIndexStride, -XMM.getBytes()));
                    // adjust the upper half: effectively, this right-shifts the entire XMM vector
                    // by (vectorLength - tailLength).
                    asm.leaq(tmp, (AMD64Address) crb.recordDataSectionReference(extractCandidateMask));
                    asm.movdqu(XMM, vecTmp[3], new AMD64Address(tmp, cmpMask, Stride.S1, vectorSize.getBytes()));
                    asm.pshufb(XMM, vecArray[0], vecTmp[3]);
                    // combine halves into one consecutive YMM vector
                    AMD64Assembler.VexRVMIOp.VPERM2I128.emit(asm, vectorSize, vecArray[0], vecArray[0], vecTmp[2], 0x02);

                    // emit main loop body without vector loading logic
                    emitConsecutiveTablesMatch(crb, asm, vectorSize, vecCmp, vecArray, vecTmp, vPrevCandidate, vectorCandidate, mask0x0f);

                    // Ignore trailing bytes.
                    asm.leaq(tmp, getMaskOnce(crb, createShortVectorS1TailMask(vectorSize)));
                    asm.pandU(vectorSize, vectorCandidate, new AMD64Address(tmp, cmpMask, Stride.S1, vectorSize.getBytes()), vecTmp[2]);
                    asm.ptest(vectorSize, vectorCandidate, vectorCandidate);
                    asm.jcc(ConditionFlag.NotZero, vectorFound);
                    asm.jmp(noMatch);
                }
                case S2 -> {
                    // Narrow the first and last vectors to bytes, then shuffle them into one
                    // synthetic vector that exactly covers the tail.
                    asm.movdqu(vectorSize, vecArray[0], new AMD64Address(arrayPtr, index, arrayIndexStride));
                    asm.movdqu(vectorSize, vecArray[1], new AMD64Address(arrayPtr, arrayLength, arrayIndexStride, -vectorSize.getBytes()));
                    reverseBytesIfForeignEndian(crb, asm, vecArray, reverseBytesMask);

                    Register vNarrowedBytes = usePackedCarry ? vPrevCandidate[2] : vecTmp[1];
                    Register vValidMask = usePackedCarry ? vecTmp[1] : vecTmp[2];
                    emitCTNarrowInputS2(asm, vectorSize, vecArray, vNarrowedBytes, vValidMask);
                    // slight deviation from S1 case: in S2 and S4, we first build a continuous
                    // shuffle mask and apply it to both vValidMask and vNarrowedBytes.
                    Register tailShuffleMask = vecArray[0];
                    emitShortVectorCreateShuffleMask(crb, asm, vecArray, cmpMask, tmp, extractCandidateMask);
                    asm.pshufb(vectorSize, vValidMask, tailShuffleMask);
                    asm.pshufb(vectorSize, vNarrowedBytes, tailShuffleMask);
                    // on S2/S4, we can reuse the main loop because it already expects a validMask
                    asm.jmp(vectorLoopAfterLoad);
                }
                case S4 -> {
                    // S4 needs four source vectors before narrowing because each result byte is
                    // produced from a 32-bit lane.
                    asm.movdqu(vectorSize, vecArray[0], new AMD64Address(arrayPtr, index, arrayIndexStride));
                    asm.movdqu(vectorSize, vecArray[1], new AMD64Address(arrayPtr, index, arrayIndexStride, vectorSize.getBytes()));
                    asm.movdqu(vectorSize, vecArray[2], new AMD64Address(arrayPtr, arrayLength, arrayIndexStride, -(vectorSize.getBytes() * 2)));
                    asm.movdqu(vectorSize, vecArray[3], new AMD64Address(arrayPtr, arrayLength, arrayIndexStride, -vectorSize.getBytes()));
                    reverseBytesIfForeignEndian(crb, asm, vecArray, reverseBytesMask);

                    // analogous to S2 case
                    Register vNarrowedBytes = usePackedCarry ? vPrevCandidate[2] : vecTmp[1];
                    Register vValidMask = vecArray[3];
                    emitCTNarrowInputS4(crb, asm, vectorSize, vecArray, vNarrowedBytes, vValidMask, s4UnscrambleMap);

                    Register tailShuffleMask = vecArray[0];
                    emitShortVectorCreateShuffleMask(crb, asm, vecArray, cmpMask, tmp, extractCandidateMask);
                    asm.pshufb(vectorSize, vNarrowedBytes, tailShuffleMask);
                    asm.pshufb(vectorSize, vValidMask, tailShuffleMask);
                    asm.jmp(vectorLoopAfterLoad);
                }
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(stride); // ExcludeFromJacocoGeneratedReport
            }
        }

        asm.bind(noMatch);
        asm.movl(index, -1);

        asm.bind(ret);
    }

    private boolean usePackedCarryForConsecutiveTables() {
        // Four-table S2/S4 searches pack prevCandidate vectors into a single vector to reduce
        // register pressure, and allow the four tables to stay alive in dedicated registers across
        // loop iterations
        return variant.tableCount() == 4 && (stride == Stride.S2 || stride == Stride.S4);
    }

    private void emitShortVectorCreateShuffleMask(CompilationResultBuilder crb, AMD64MacroAssembler asm,
                    Register[] vecArray,
                    Register negativeTailLength,
                    Register tmp,
                    DataSection.Data extractCandidateMask) {
        // Build the PSHUFB mask that splices bytes overlapping tail vector loads into one
        // synthetic contiguous vector.
        asm.leaq(tmp, (AMD64Address) crb.recordDataSectionReference(extractCandidateMask));
        if (vectorSize == YMM) {
            asm.movdqu(AVXSize.XMM, vecArray[0], new AMD64Address(tmp));
            asm.movdqu(AVXSize.XMM, vecArray[1], new AMD64Address(tmp, negativeTailLength, Stride.S1, YMM.getBytes()));
            AMD64Assembler.VexRVMIOp.VPERM2I128.emit(asm, YMM, vecArray[0], vecArray[0], vecArray[1], 0x20);
        } else {
            asm.movdq(vecArray[0], new AMD64Address(tmp));
            asm.movdq(vecArray[1], new AMD64Address(tmp, negativeTailLength, Stride.S1, XMM.getBytes() + 8));
            asm.movlhps(vecArray[0], vecArray[1]);
        }
    }

    private static byte[] createShortVectorS1TailMask(AVXSize vSize) {
        // Keeps only the byte positions that belong to the real tail in the synthetic S1 vector.
        byte[] mask = new byte[vSize.getBytes() * 2];
        Arrays.fill(mask, 0, vSize.getBytes(), (byte) 0xff);
        return mask;
    }

    private static byte[] createCTTailPrevShuffleMask(AVXSize vSize) {
        // Keep the last three bytes from the previous candidate vector. At most three start
        // positions can cross from one vector into the next because we handle up to four
        // consecutive tables.
        byte[] mask = new byte[vSize.getBytes() * 2];
        Arrays.fill(mask, (byte) 0x80);
        int last = mask.length - 1;
        mask[last - 2] = 0x0d;
        mask[last - 1] = 0x0e;
        mask[last] = 0x0f;
        return mask;
    }

    private static byte[] createExtractCandidateMask(AVXSize vSize) {
        // After locating the first matching byte, this mask extracts exactly that byte so it can be
        // moved into a general-purpose register.
        byte[] mask = new byte[vSize.getBytes() * 2];
        for (int i = 0; i < 16; i++) {
            mask[i] = (byte) i;
        }
        Arrays.fill(mask, 16, 32, (byte) 0x80);
        if (vSize == YMM) {
            System.arraycopy(mask, 0, mask, 32, 32);
        }
        return mask;
    }

    private void emitConsecutiveTablesMainLoopBody(CompilationResultBuilder crb,
                    AMD64MacroAssembler asm,
                    AVXSize vSize,
                    Register arrayPtr,
                    Register index,
                    Register[] vLUT,
                    Register[] vArray,
                    Register[] vTmp,
                    Register[] vPrevCandidate,
                    Register vCandidate,
                    DataSection.Data reverseBytesMask,
                    DataSection.Data mask0x0f,
                    DataSection.Data s4UnscrambleMap,
                    Label afterLoad,
                    Label vectorFound) {
        // Load the raw input vectors for the current stride.
        for (int i = 0; i < stride.value; i++) {
            emitArrayLoad(asm, vSize, vArray[i], arrayPtr, index, arrayIndexStride, i * vSize.getBytes());
        }
        reverseBytesIfForeignEndian(crb, asm, vArray, reverseBytesMask);
        final boolean packedCarry = usePackedCarryForConsecutiveTables();
        final Register vNarrowedBytes = packedCarry ? vPrevCandidate[2] : vTmp[1];
        switch (stride) {
            case S1 -> {
            }
            // S2/S4 first narrow their input to bytes and build a validity mask that clears
            // original values above 0xff, since the lookup tables only cover byte values.
            case S2 -> emitCTNarrowInputS2(asm, vSize, vArray, vNarrowedBytes, packedCarry ? vTmp[1] : vTmp[2]);
            case S4 -> emitCTNarrowInputS4(crb, asm, vSize, vArray, vNarrowedBytes, vArray[3], s4UnscrambleMap);
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(stride); // ExcludeFromJacocoGeneratedReport
        }
        // The short-tail path jumps here after constructing a synthetic input vector.
        asm.bind(afterLoad);
        emitConsecutiveTablesMatch(crb, asm, vSize, vLUT, vArray, vTmp, vPrevCandidate, vCandidate, mask0x0f);
        // check for a match.
        asm.ptest(vectorSize, vCandidate, vCandidate);
        asm.jccb(ConditionFlag.NotZero, vectorFound);
    }

    private void emitConsecutiveTablesMatch(CompilationResultBuilder crb,
                    AMD64MacroAssembler asm,
                    AVXSize vSize,
                    Register[] vLUT,
                    Register[] vArray,
                    Register[] vTmp,
                    Register[] vPrevCandidate,
                    Register vCandidate,
                    DataSection.Data mask0x0f) {
        int tableCount = variant.tableCount();
        // main table matching logic. the register assignment of input data varies with stride and
        // table count, to avoid extra register moves.
        // @formatter:off
        /*
         * Register assignment summary for the input state:
         *
         *                     vNarrowedBytes     vValidByteMask
         * S1 / 2,3,4 tables   vArray[0]          n/a
         * S2 / 2,3   tables   vTmp[1]            vTmp[2]
         * S2 /     4 tables   vPrevCandidate[2]  vTmp[1]
         * S4 / 2,3   tables   vTmp[1]            vArray[3]
         * S4 /     4 tables   vPrevCandidate[2]  vArray[3]
        */
        // @formatter:on

        switch (stride) {
            case S1 -> {
                Register vLoNibble = vArray[0];
                Register vHiNibble = vTmp[0];
                Register vCurCandidate = vTmp[1];
                Register vScratch = vTmp[2];

                // S1 already operates on bytes, so one nibble split serves all tables.
                emitSplitIntoNibbles(crb, asm, vSize, mask0x0f, vArray[0], vHiNibble, vLoNibble);
                for (int i = 0; i < tableCount; i++) {
                    // process table lookups one by one. result is accumulated in vCandidate.
                    emitCTProcessTable(asm, vSize, vLUT, i, vHiNibble, vLoNibble, null, vPrevCandidate, vCurCandidate, vCandidate, vScratch);
                }
            }
            case S2, S4 -> {
                Register vCurCandidate = vArray[1];

                switch (tableCount) {
                    case 2, 3 -> {
                        Register vLoNibble = vTmp[1];
                        Register vHiNibble = vTmp[0];
                        Register vValidByteMask = stride == Stride.S2 ? vTmp[2] : vArray[3];
                        Register vScratch = stride == Stride.S2 ? vArray[0] : vArray[2];
                        // Two- and three-table searches work the same as in the S1 variant
                        emitSplitIntoNibbles(crb, asm, vSize, mask0x0f, vLoNibble, vHiNibble, vLoNibble);
                        for (int i = 0; i < tableCount; i++) {
                            emitCTProcessTable(asm, vSize, vLUT, i, vHiNibble, vLoNibble, vValidByteMask, vPrevCandidate, vCurCandidate, vCandidate, vScratch);
                        }
                    }
                    case 4 -> {
                        // S2/4 4-table variant uses a slightly more complex approach for saving
                        // previous candidate bytes in order to leave enough registers for the
                        // narrowing input load (2 for S2, 4 for S4), and all the lookup tables
                        // (8 vectors)
                        Register vLoNibble = vPrevCandidate[2];
                        Register vHiNibble = vPrevCandidate[1];
                        Register vCarryPrev = vPrevCandidate[0];
                        // vCarryNext reuses vArray[0] to assemble the packed carry for the next
                        // iteration. Its low bytes accumulate the trailing candidate bytes as:
                        // [c2[-1], c1[-2], c1[-1], c0[-3], c0[-2], c0[-1]]
                        // where ci[-k] is the k-th byte from the end of the current candidate-i
                        // candidate vector.
                        Register vCarryNext = vArray[0];
                        Register vValidByteMask = stride == Stride.S2 ? vTmp[1] : vArray[3];
                        Register vScratch = stride == Stride.S2 ? vTmp[0] : vArray[2];
                        emitSplitIntoNibbles(crb, asm, vSize, mask0x0f, vLoNibble, vHiNibble, vLoNibble);

                        // first table lookup
                        emitCTLookup(asm, vSize, vLUT, 0, vHiNibble, vLoNibble, vCurCandidate, vScratch, vValidByteMask);
                        // vCarryPrev is still
                        // [..., c2[-1], c1[-2], c1[-1], c0[-3], c0[-2], c0[-1]],
                        // so it can be used directly for concatenation with vCurCandidate
                        emitPalignr(asm, vSize, vCandidate, vCarryPrev, vCurCandidate, 3);
                        // vCarryNext = [c0[-3], c0[-2], c0[-1], ...]
                        asm.palignr(vSize, vCarryNext, vCarryNext, vCurCandidate, 16 - 3);

                        // second table lookup
                        emitCTLookup(asm, vSize, vLUT, 1, vHiNibble, vLoNibble, vCurCandidate, vScratch, vValidByteMask);
                        // shift out the three last bytes:
                        // vCarryPrev = [..., c2[-1], c1[-2], c1[-1]]
                        asm.palignr(vSize, vCarryPrev, vCarryPrev, vCarryPrev, 16 - 3);
                        emitPalignr(asm, vSize, vScratch, vCarryPrev, vCurCandidate, 2);
                        asm.pand(vSize, vCandidate, vScratch);
                        // vCarryNext = [c1[-2], c1[-1], c0[-3], c0[-2], c0[-1], ...]
                        asm.palignr(vSize, vCarryNext, vCarryNext, vCurCandidate, 16 - 2);

                        emitCTLookup(asm, vSize, vLUT, 2, vHiNibble, vLoNibble, vCurCandidate, vScratch, vValidByteMask);
                        // shift out last two bytes:
                        // vCarryPrev = [..., c2[-1]]
                        asm.palignr(vSize, vCarryPrev, vCarryPrev, vCarryPrev, 16 - 2);
                        emitPalignr(asm, vSize, vScratch, vCarryPrev, vCurCandidate, 1);
                        asm.pand(vSize, vCandidate, vScratch);
                        // vCarryNext = [c2[-1], c1[-2], c1[-1], c0[-3], c0[-2], c0[-1], ...]
                        asm.palignr(vSize, vCarryNext, vCarryNext, vCurCandidate, 16 - 1);

                        emitCTLookup(asm, vSize, vLUT, 3, vHiNibble, vLoNibble, vCurCandidate, vScratch, vValidByteMask);
                        asm.pand(vSize, vCandidate, vCurCandidate);
                        // save vCarryNext into vCarryPrev for the next iteration, and move the
                        // packed bytes towards the end of the vector at the same time:
                        // vCarryPrev = [..., c2[-1], c1[-2], c1[-1], c0[-3], c0[-2], c0[-1]]
                        asm.palignr(vSize, vCarryPrev, vCarryNext, vCarryNext, 16 - 10);

                    }
                    default -> throw GraalError.shouldNotReachHereUnexpectedValue(tableCount); // ExcludeFromJacocoGeneratedReport
                }
            }
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(stride); // ExcludeFromJacocoGeneratedReport
        }
    }

    private static void emitSplitIntoNibbles(CompilationResultBuilder crb,
                    AMD64MacroAssembler asm,
                    AVXSize vSize,
                    DataSection.Data mask0x0f,
                    Register vInputBytes,
                    Register vHiNibble,
                    Register vLoNibble) {
        // Split the byte input into hi/lo nibble indices for the two lookup tables.
        asm.psrlw(vSize, vHiNibble, vInputBytes, 4);
        asm.pand(vSize, vLoNibble, vInputBytes, (AMD64Address) crb.recordDataSectionReference(mask0x0f));
        asm.pand(vSize, vHiNibble, vHiNibble, (AMD64Address) crb.recordDataSectionReference(mask0x0f));
    }

    private static void emitCTLookup(AMD64MacroAssembler asm,
                    AVXSize vSize,
                    Register[] vLUT,
                    int lutIndex,
                    Register vHiNibble,
                    Register vLoNibble,
                    Register vCandidateOut,
                    Register vScratch,
                    Register vValidByteMask) {
        // Evaluate one logical table from its hi/lo nibble lookup pair.
        // Look up the hi- and low-nibble masks and intersect them.
        asm.pshufb(vSize, vCandidateOut, vLUT[lutIndex * 2], vHiNibble);
        asm.pshufb(vSize, vScratch, vLUT[(lutIndex * 2) + 1], vLoNibble);
        asm.pand(vSize, vCandidateOut, vScratch);
        if (vValidByteMask != null) {
            // filter matches of narrowed elements whose original value was > 0xff.
            asm.pand(vSize, vCandidateOut, vValidByteMask);
        }
    }

    private static void emitCTProcessTable(AMD64MacroAssembler asm,
                    AVXSize vSize,
                    Register[] vLUT,
                    int lutIndex,
                    Register vHiNibble,
                    Register vLoNibble,
                    Register vValidByteMask,
                    Register[] vPrevCandidate,
                    Register vCurCandidate,
                    Register vCandidate,
                    Register vScratch) {
        int tableCount = vLUT.length >> 1;
        if (lutIndex < tableCount - 1) {
            // perform table lookup
            emitCTLookup(asm, vSize, vLUT, lutIndex, vHiNibble, vLoNibble, vCurCandidate, vScratch, vValidByteMask);
            // shift-in the previous iteration's lookup result.
            emitPalignr(asm, vSize, lutIndex == 0 ? vCandidate : vScratch, vPrevCandidate[lutIndex], vCurCandidate, tableCount - (lutIndex + 1));
            // save current result for next iteration
            asm.movdqu(vSize, vPrevCandidate[lutIndex], vCurCandidate);
            if (lutIndex != 0) {
                // accumulate result into vCandidate. The first table instead overwrites vCandidate
                asm.pand(vSize, vCandidate, vScratch);
            }
        } else {
            // The last table doesn't need any carry-over from the previous iteration.
            emitCTLookup(asm, vSize, vLUT, lutIndex, vHiNibble, vLoNibble, vHiNibble, vLoNibble, vValidByteMask);
            asm.pand(vSize, vCandidate, vHiNibble);
            // after the last table lookup, vCandidate holds the combined result of all table
            // lookups and all vPrevCandidate registers have been updated for the next iteration.
        }
    }

    private static void emitPalignr(AMD64MacroAssembler asm,
                    AVXSize vSize,
                    Register vShiftedCandidate,
                    Register vPrevCandidate,
                    Register vCurrentCandidate,
                    int shiftCount) {
        assert 1 <= shiftCount && shiftCount <= 3 : shiftCount;
        if (vSize == AVXSize.YMM) {
            // PALIGNR does not cross the 128-bit lane boundary, so first concatenate the relevant
            // halves with VPERM2I128 and then apply PALIGNR within each lane.
            AMD64Assembler.VexRVMIOp.VPERM2I128.emit(asm, vSize, vShiftedCandidate, vPrevCandidate, vCurrentCandidate, 0x21);
            asm.palignr(vSize, vShiftedCandidate, vCurrentCandidate, vShiftedCandidate, 16 - shiftCount);
        } else {
            asm.palignr(vSize, vShiftedCandidate, vCurrentCandidate, vPrevCandidate, 16 - shiftCount);
        }
    }

    private static void emitCTNarrowInputS2(AMD64MacroAssembler asm,
                    AVXSize vSize,
                    Register[] vecArray,
                    Register vNarrowedBytes,
                    Register vValidMask) {
        // Convert two vectors of 16-bit values into packed bytes plus a mask that keeps only
        // original values in the 0..255 range.
        // narrow string chars to bytes
        asm.packuswb(vSize, vNarrowedBytes, vecArray[0], vecArray[1]);
        // right-shift chars by 8
        asm.psrlw(vSize, vecArray[0], vecArray[0], 8);
        asm.psrlw(vSize, vecArray[1], vecArray[1], 8);
        // create a mask for all chars <= 0xff
        asm.packuswb(vSize, vecArray[0], vecArray[0], vecArray[1]);
        asm.pxor(vSize, vValidMask, vValidMask);
        asm.pcmpeqb(vSize, vValidMask, vecArray[0]);
        if (vSize == AVXSize.YMM) {
            VexRMIOp.VPERMQ.emit(asm, vSize, vNarrowedBytes, vNarrowedBytes, 0b11011000);
            VexRMIOp.VPERMQ.emit(asm, vSize, vValidMask, vValidMask, 0b11011000);
        }
    }

    private static void emitCTNarrowInputS4(CompilationResultBuilder crb,
                    AMD64MacroAssembler asm,
                    AVXSize vSize,
                    Register[] vecArray,
                    Register vNarrowedBytes,
                    Register vValidMask,
                    DataSection.Data vS4UnscrambleMap) {
        GraalError.guarantee(vSize != AVXSize.YMM || vS4UnscrambleMap != null, "missing YMM S4 unscramble map");
        // Convert four vectors of 32-bit values into packed bytes plus a mask that keeps only
        // original values in the 0..255 range.
        // narrow string ints to bytes
        asm.packusdw(vSize, vNarrowedBytes, vecArray[0], vecArray[1]);
        // right-shift ints by 8
        asm.psrld(vSize, vecArray[0], vecArray[0], 8);
        asm.psrld(vSize, vecArray[1], vecArray[1], 8);
        // create the upper half of the <= 0xff validity check before reusing vecArray[1]
        asm.packssdw(vSize, vecArray[0], vecArray[0], vecArray[1]);
        // finish narrowing using vecArray[1] as a temporary for the second half
        asm.packusdw(vSize, vecArray[1], vecArray[2], vecArray[3]);
        asm.psrld(vSize, vecArray[2], vecArray[2], 8);
        asm.psrld(vSize, vecArray[3], vecArray[3], 8);
        asm.packuswb(vSize, vNarrowedBytes, vecArray[1]);
        // create a mask for all ints <= 0xff
        asm.packssdw(vSize, vecArray[2], vecArray[2], vecArray[3]);
        asm.pxor(vSize, vValidMask, vValidMask);
        asm.packuswb(vSize, vecArray[0], vecArray[0], vecArray[2]);
        asm.pcmpeqb(vSize, vValidMask, vecArray[0]);
        if (vSize == AVXSize.YMM) {
            asm.movdqu(vSize, vecArray[2], (AMD64Address) crb.recordDataSectionReference(vS4UnscrambleMap));
            VexRVMOp.VPERMD.emit(asm, vSize, vNarrowedBytes, vecArray[2], vNarrowedBytes);
            VexRVMOp.VPERMD.emit(asm, vSize, vValidMask, vecArray[2], vValidMask);
        }
    }
}
