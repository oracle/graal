/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.vm.ci.amd64.AMD64.r8;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;

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
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

/**
 */
@Opcode("AMD64_ARRAY_INDEX_OF")
public final class AMD64ArrayIndexOfOp extends AMD64ComplexVectorOp {
    public static final LIRInstructionClass<AMD64ArrayIndexOfOp> TYPE = LIRInstructionClass.create(AMD64ArrayIndexOfOp.class);

    private static final Register REG_ARRAY = rsi;
    private static final Register REG_OFFSET = rax;
    private static final Register REG_LENGTH = rdx;
    private static final Register REG_FROM_INDEX = rdi;
    private static final Register REG_SEARCH_VALUE_1 = rcx;
    private static final Register REG_SEARCH_VALUE_2 = r8;

    private final Stride stride;
    private final int nValues;
    private final LIRGeneratorTool.ArrayIndexOfVariant variant;
    private final boolean findTwoConsecutive;
    private final boolean withMask;
    private final AMD64Kind vectorKind;
    private final Stride arrayIndexStride;
    private final int constOffset;

    @Def({OperandFlag.REG}) Value resultValue;

    @Use({OperandFlag.REG}) Value arrayReg;
    @Use({OperandFlag.REG}) Value offsetReg;
    @Use({OperandFlag.REG}) Value lengthReg;
    @Use({OperandFlag.REG}) Value fromIndexReg;
    @Use({OperandFlag.REG}) Value searchValue1;
    @Use({OperandFlag.REG, OperandFlag.ILLEGAL}) Value searchValue2;

    @Temp({OperandFlag.REG}) Value arrayTmp;
    @Temp({OperandFlag.REG}) Value offsetTmp;
    @Temp({OperandFlag.REG}) Value lengthTmp;
    @Temp({OperandFlag.REG}) Value fromIndexTmp;
    @Temp({OperandFlag.REG}) Value searchValue1Tmp;
    @Temp({OperandFlag.REG, OperandFlag.ILLEGAL}) Value searchValue2Tmp;

    @Alive({OperandFlag.REG, OperandFlag.STACK, OperandFlag.ILLEGAL}) Value searchValue3;
    @Alive({OperandFlag.REG, OperandFlag.STACK, OperandFlag.ILLEGAL}) Value searchValue4;

    @Temp({OperandFlag.REG}) Value[] vectorCompareVal;
    @Temp({OperandFlag.REG}) Value[] vectorArray;
    @Temp({OperandFlag.REG}) Value[] vectorTemp;

    private AMD64ArrayIndexOfOp(Stride stride, LIRGeneratorTool.ArrayIndexOfVariant variant, int constOffset, int nValues, LIRGeneratorTool tool,
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
        this.arrayTmp = this.arrayReg = arrayPtr;
        this.offsetTmp = this.offsetReg = arrayOffset;
        this.lengthTmp = this.lengthReg = arrayLength;
        this.fromIndexTmp = this.fromIndexReg = fromIndex;
        this.searchValue1Tmp = this.searchValue1 = searchValue1;
        if (variant == LIRGeneratorTool.ArrayIndexOfVariant.Table) {
            this.searchValue2Tmp = searchValue2;
            this.searchValue2 = Value.ILLEGAL;
        } else {
            this.searchValue2Tmp = this.searchValue2 = searchValue2;
        }
        this.searchValue3 = searchValue3;
        this.searchValue4 = searchValue4;

        this.vectorKind = getVectorKind(stride);
        this.vectorCompareVal = allocateVectorRegisters(tool, stride, variant == LIRGeneratorTool.ArrayIndexOfVariant.Table ? 2 : nValues);
        this.vectorArray = allocateVectorRegisters(tool, stride, variant == LIRGeneratorTool.ArrayIndexOfVariant.Table ? stride.value : 4);
        this.vectorTemp = allocateVectorRegisters(tool, stride, getNumberOfRequiredTempVectors(variant, nValues));
    }

    private static int getNumberOfRequiredTempVectors(LIRGeneratorTool.ArrayIndexOfVariant variant, int nValues) {
        switch (variant) {
            case MatchRange:
                return nValues / 2 + 1;
            case Table:
                return 4;
            default:
                return 0;
        }
    }

    private static Register[] asRegisters(Value[] values) {
        Register[] registers = new Register[values.length];
        for (int i = 0; i < registers.length; i++) {
            registers[i] = asRegister(values[i]);
        }
        return registers;
    }

    public static AMD64ArrayIndexOfOp movParamsAndCreate(Stride stride, LIRGeneratorTool.ArrayIndexOfVariant variant, LIRGeneratorTool tool,
                    EnumSet<CPUFeature> runtimeCheckedCPUFeatures, Value result, Value arrayPtr, Value arrayOffset, Value arrayLength, Value fromIndex, Value... searchValues) {

        int nValues = searchValues.length;
        RegisterValue regArray = REG_ARRAY.asValue(arrayPtr.getValueKind());
        RegisterValue regOffset = REG_OFFSET.asValue(arrayOffset.getValueKind());
        RegisterValue regLength = REG_LENGTH.asValue(arrayLength.getValueKind());
        RegisterValue regFromIndex = REG_FROM_INDEX.asValue(fromIndex.getValueKind());
        RegisterValue regSearchValue1 = REG_SEARCH_VALUE_1.asValue(searchValues[0].getValueKind());
        Value regSearchValue2 = nValues > 1 ? REG_SEARCH_VALUE_2.asValue(searchValues[1].getValueKind())
                        : variant == LIRGeneratorTool.ArrayIndexOfVariant.Table ? REG_SEARCH_VALUE_2.asValue() : Value.ILLEGAL;
        Value regSearchValue3 = nValues > 2 ? tool.asAllocatable(searchValues[2]) : Value.ILLEGAL;
        Value regSearchValue4 = nValues > 3 ? tool.asAllocatable(searchValues[3]) : Value.ILLEGAL;

        tool.emitConvertNullToZero(regArray, arrayPtr);
        tool.emitMove(regOffset, arrayOffset);
        tool.emitMove(regLength, arrayLength);
        tool.emitMove(regFromIndex, fromIndex);
        tool.emitMove(regSearchValue1, searchValues[0]);
        if (nValues > 1) {
            tool.emitMove((RegisterValue) regSearchValue2, searchValues[1]);
        }
        final int constOffset;
        if (isConstant(arrayOffset) && asConstant(arrayOffset).asLong() >= 0 && asConstant(arrayOffset).asLong() <= Integer.MAX_VALUE) {
            constOffset = (int) asConstant(arrayOffset).asLong();
        } else {
            constOffset = -1;
        }
        return new AMD64ArrayIndexOfOp(stride, variant, constOffset, nValues, tool,
                        runtimeCheckedCPUFeatures, result, regArray, regOffset, regLength, regFromIndex, regSearchValue1, regSearchValue2, regSearchValue3, regSearchValue4);
    }

    private boolean useConstantOffset() {
        return constOffset >= 0;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler asm) {
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
        int vectorLength = variant == LIRGeneratorTool.ArrayIndexOfVariant.Table ? vectorKind.getSizeInBytes() : vectorKind.getVectorLength();
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

        if (variant == LIRGeneratorTool.ArrayIndexOfVariant.Table) {
            // load lookup tables
            asm.movdqu(AVXSize.XMM, vecCmp[0], new AMD64Address(asRegister(searchValue[0])));
            asm.movdqu(AVXSize.XMM, vecCmp[1], new AMD64Address(asRegister(searchValue[0]), AVXSize.XMM.getBytes()));
            loadMask(crb, asm, Stride.S1, vecTmp[0], 0x0f);
            if (vectorSize == AVXSize.YMM) {
                // duplicate lookup tables
                AMD64Assembler.VexRVMIOp.VPERM2I128.emit(asm, AVXSize.YMM, vecCmp[0], vecCmp[0], vecCmp[0], 0x00);
                AMD64Assembler.VexRVMIOp.VPERM2I128.emit(asm, AVXSize.YMM, vecCmp[1], vecCmp[1], vecCmp[1], 0x00);
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

        // check if vector load is in bounds
        asm.cmpqAndJcc(index, arrayLength, ConditionFlag.LessEqual, runVectorized, false);

        if (supportsAVX2AndYMM()) {
            // region is too short for YMM vectors, try XMM
            Label[] xmmFound = {new Label()};
            // index = fromIndex (+ 1 if findTwoConsecutive) + XMM vector size
            asm.subq(index, vectorLength / 2);
            // check if vector load is in bounds
            asm.cmpqAndJcc(index, arrayLength, ConditionFlag.Greater, variant == LIRGeneratorTool.ArrayIndexOfVariant.Table ? elementWise : qWordWise, false);
            // do one vector comparison from fromIndex
            emitVectorCompare(asm, AVXSize.XMM, 1, arrayPtr, index, vecCmp, vecArray, vecTmp, cmpResult, xmmFound, variant != LIRGeneratorTool.ArrayIndexOfVariant.Table);
            // and one aligned to the array end
            asm.movq(index, arrayLength);
            emitVectorCompare(asm, AVXSize.XMM, 1, arrayPtr, index, vecCmp, vecArray, vecTmp, cmpResult, xmmFound, true);
            // no match, return negative
            asm.jmp(elementWiseNotFound);
            // match found, adjust index by XMM offset
            asm.bind(xmmFound[0]);
            asm.subq(index, (vectorLength / 2) + (findTwoConsecutive ? 1 : 0));
            // return index found
            asm.jmp(bsfAdd);
        }

        int vectorLengthQWord = AVXSize.QWORD.getBytes() / stride.value;
        if (variant != LIRGeneratorTool.ArrayIndexOfVariant.Table) {
            asm.bind(qWordWise);
            // region is too short for XMM vectors, try QWORD
            Label[] qWordFound = {new Label()};
            // index = fromIndex (+ 1 if findTwoConsecutive) + QWORD vector size
            asm.subq(index, vectorLengthQWord);
            // check if vector load is in bounds
            asm.cmpqAndJcc(index, arrayLength, ConditionFlag.Greater, elementWise, false);
            // do one vector comparison from fromIndex
            emitVectorCompare(asm, AVXSize.QWORD, 1, arrayPtr, index, vecCmp, vecArray, vecTmp, cmpResult, qWordFound, true);
            // and one aligned to the array end
            asm.movq(index, arrayLength);
            emitVectorCompare(asm, AVXSize.QWORD, 1, arrayPtr, index, vecCmp, vecArray, vecTmp, cmpResult, qWordFound, true);
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
        asm.subq(index, (variant == LIRGeneratorTool.ArrayIndexOfVariant.Table) ? (supportsAVX2AndYMM() ? (vectorLength / 2) : vectorLength) : vectorLengthQWord);
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
            case MatchRange:
                asm.movSZx(valueSize, ZERO_EXTEND, cmpResult, arrayAddr);
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
            case Table:
                Label greaterThan0xff = new Label();
                Register tmp = asRegister(searchValue2Tmp);
                asm.movSZx(valueSize, ZERO_EXTEND, cmpResult, arrayAddr);
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
        emitVectorCompare(asm, vectorSize, 1, arrayPtr, index, vecCmp, vecArray, vecTmp, cmpResult, vectorFound, false);

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

        boolean bulkLoopShortJmp = !((variant == LIRGeneratorTool.ArrayIndexOfVariant.MatchRange && nValues == 4 || variant == LIRGeneratorTool.ArrayIndexOfVariant.Table) && stride.value > 1);
        /*
         * Check if there are enough array slots remaining for the bulk loop. Note: The alignment
         * following the cmpAndJcc can lead to a jump distance > 127. This prevents safely using a
         * short jump.
         */
        asm.cmpqAndJcc(index, arrayLength, ConditionFlag.Greater, skipBulkVectorLoop, false);

        asm.align(preferredLoopAlignment(crb));
        asm.bind(bulkVectorLoop);
        // memory-aligned bulk comparison
        emitVectorCompare(asm, vectorSize, nVectors, arrayPtr, index, vecCmp, vecArray, vecTmp, cmpResult, vectorFound, false);
        // adjust index
        asm.addq(index, bulkSize);
        // check if there are enough array slots remaining for the bulk loop
        asm.cmpqAndJcc(index, arrayLength, ConditionFlag.LessEqual, bulkVectorLoop, bulkLoopShortJmp);

        asm.bind(skipBulkVectorLoop);
        if (nVectors == 1) {
            // do last load from end of array
            asm.movq(index, arrayLength);
            // compare
            emitVectorCompare(asm, vectorSize, 1, arrayPtr, index, vecCmp, vecArray, vecTmp, cmpResult, vectorFound, true);
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
            emitVectorCompare(asm, vectorSize, 1, arrayPtr, index, vecCmp, vecArray, vecTmp, cmpResult, vectorFound, true);
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
        if (variant == LIRGeneratorTool.ArrayIndexOfVariant.Table) {
            asm.pxor(vectorSize, vecTmp[1], vecTmp[1]);
            asm.pcmpeqb(vectorSize, vecTmp[2], vecTmp[1]);
            asm.pmovmsk(vectorSize, cmpResult, vecTmp[2]);
            asm.notq(cmpResult);
        }
        // find offset
        bsfq(asm, cmpResult, cmpResult);
        if (stride.value > 1 && variant != LIRGeneratorTool.ArrayIndexOfVariant.Table) {
            // convert byte offset to chars
            asm.shrq(cmpResult, stride.log2);
        }
        // add offset to index
        asm.addq(index, cmpResult);

        asm.bind(ret);
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
        if (variant == LIRGeneratorTool.ArrayIndexOfVariant.Table) {
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

    private void emitVectorCompare(AMD64MacroAssembler asm,
                    AVXSize vSize,
                    int nVectors,
                    Register arrayPtr,
                    Register index,
                    Register[] vecCmp,
                    Register[] vecArray,
                    Register[] vecTmp,
                    Register cmpResult,
                    Label[] vectorFound,
                    boolean shortJmp) {
        // load array contents into vectors
        int nVectorLoads = variant == LIRGeneratorTool.ArrayIndexOfVariant.Table ? stride.value : nVectors;
        for (int i = 0; i < nVectorLoads; i++) {
            int base = i * nValues;
            for (int j = 0; j < (withMask || variant == LIRGeneratorTool.ArrayIndexOfVariant.MatchRange ? nValues / 2 : nValues); j++) {
                emitArrayLoad(asm, vSize, vecArray[base + j], arrayPtr, index, getVectorOffset(nVectorLoads - (i + 1), j, vSize));
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
            case Table:
                Register mask0xf = vecTmp[0];
                Register tableHi = vecCmp[0];
                Register tableLo = vecCmp[1];
                switch (stride) {
                    case S1:
                        performTableLookup(asm, vSize, mask0xf, tableHi, tableLo, vecArray[0], vecTmp[1], vecTmp[2], vecTmp[3]);
                        break;
                    case S2:
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
                        // create a mask for all ints > 0xff
                        asm.packusdw(vSize, vecArray[0], vecArray[1]);
                        asm.packusdw(vSize, vecArray[2], vecArray[3]);
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
    private void emitArrayLoad(AMD64MacroAssembler asm, AVXSize targetVectorSize, Register vecDst, Register array, Register index, int displacement) {
        AMD64Address src = new AMD64Address(array, index, arrayIndexStride, displacement);
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

    private static OperandSize getOpSize(Stride stride) {
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
}
