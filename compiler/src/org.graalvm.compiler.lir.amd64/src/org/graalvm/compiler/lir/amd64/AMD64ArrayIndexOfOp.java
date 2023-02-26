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
package org.graalvm.compiler.lir.amd64;

import static jdk.vm.ci.amd64.AMD64.r8;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.ExtendMode.ZERO_EXTEND;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;

import java.util.EnumSet;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexMoveOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRMIOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRMOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp;
import org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.asm.amd64.AVXKind.AVXSize;
import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

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
    private final boolean findTwoConsecutive;
    private final boolean withMask;
    private final AMD64Kind vectorKind;
    private final Stride arrayIndexStride;
    private final int constOffset;

    @Def({REG}) Value resultValue;

    @Use({REG}) Value arrayReg;
    @Use({REG}) Value offsetReg;
    @Use({REG}) Value lengthReg;
    @Use({REG}) Value fromIndexReg;
    @Use({REG}) Value searchValue1;
    @Use({REG, ILLEGAL}) Value searchValue2;

    @Temp({REG}) Value arrayTmp;
    @Temp({REG}) Value offsetTmp;
    @Temp({REG}) Value lengthTmp;
    @Temp({REG}) Value fromIndexTmp;
    @Temp({REG}) Value searchValue1Tmp;
    @Temp({REG, ILLEGAL}) Value searchValue2Tmp;

    @Alive({REG, STACK, ILLEGAL}) Value searchValue3;
    @Alive({REG, STACK, ILLEGAL}) Value searchValue4;

    @Temp({REG}) Value[] vectorCompareVal;
    @Temp({REG}) Value[] vectorArray;

    private AMD64ArrayIndexOfOp(Stride stride, boolean findTwoConsecutive, boolean withMask, int constOffset, int nValues, LIRGeneratorTool tool,
                    EnumSet<CPUFeature> runtimeCheckedCPUFeatures, Value result, Value arrayPtr, Value arrayOffset, Value arrayLength, Value fromIndex, Value searchValue1, Value searchValue2,
                    Value searchValue3, Value searchValue4) {
        super(TYPE, tool, runtimeCheckedCPUFeatures, AVXSize.YMM);
        this.stride = stride;
        this.arrayIndexStride = stride;
        this.findTwoConsecutive = findTwoConsecutive;
        this.withMask = withMask;
        this.constOffset = constOffset;
        this.nValues = nValues;
        GraalError.guarantee(0 < nValues && nValues <= 4, "only 1 - 4 values supported");
        GraalError.guarantee(stride.value <= 4, "supported strides are 1, 2 and 4 bytes");
        GraalError.guarantee(supports(tool.target(), runtimeCheckedCPUFeatures, CPUFeature.SSE2), "needs at least SSE2 support");
        GraalError.guarantee(!(!withMask && findTwoConsecutive) || nValues == 2, "findTwoConsecutive mode requires exactly 2 search values");
        GraalError.guarantee(!(withMask && findTwoConsecutive) || nValues == 4, "findTwoConsecutive mode with mask requires exactly 4 search values");
        GraalError.guarantee(!(withMask && !findTwoConsecutive) || nValues == 2, "with mask mode requires exactly 2 search values");

        resultValue = result;
        this.arrayTmp = this.arrayReg = arrayPtr;
        this.offsetTmp = this.offsetReg = arrayOffset;
        this.lengthTmp = this.lengthReg = arrayLength;
        this.fromIndexTmp = this.fromIndexReg = fromIndex;
        this.searchValue1Tmp = this.searchValue1 = searchValue1;
        this.searchValue2Tmp = this.searchValue2 = searchValue2;
        this.searchValue3 = searchValue3;
        this.searchValue4 = searchValue4;

        this.vectorKind = getVectorKind(stride);
        this.vectorCompareVal = allocateVectorRegisters(tool, stride, nValues);
        this.vectorArray = allocateVectorRegisters(tool, stride, 4);
    }

    private static Register[] asRegisters(Value[] values) {
        Register[] registers = new Register[values.length];
        for (int i = 0; i < registers.length; i++) {
            registers[i] = asRegister(values[i]);
        }
        return registers;
    }

    public static AMD64ArrayIndexOfOp movParamsAndCreate(Stride stride, boolean findTwoConsecutive, boolean withMask, LIRGeneratorTool tool,
                    EnumSet<CPUFeature> runtimeCheckedCPUFeatures, Value result, Value arrayPtr, Value arrayOffset, Value arrayLength, Value fromIndex, Value... searchValues) {

        int nValues = searchValues.length;
        RegisterValue regArray = REG_ARRAY.asValue(arrayPtr.getValueKind());
        RegisterValue regOffset = REG_OFFSET.asValue(arrayOffset.getValueKind());
        RegisterValue regLength = REG_LENGTH.asValue(arrayLength.getValueKind());
        RegisterValue regFromIndex = REG_FROM_INDEX.asValue(fromIndex.getValueKind());
        RegisterValue regSearchValue1 = REG_SEARCH_VALUE_1.asValue(searchValues[0].getValueKind());
        Value regSearchValue2 = nValues > 1 ? REG_SEARCH_VALUE_2.asValue(searchValues[1].getValueKind()) : Value.ILLEGAL;
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
        return new AMD64ArrayIndexOfOp(stride, findTwoConsecutive, withMask, constOffset, nValues, tool,
                        runtimeCheckedCPUFeatures, result, regArray, regOffset, regLength, regFromIndex, regSearchValue1, regSearchValue2, regSearchValue3, regSearchValue4);
    }

    private boolean useConstantOffset() {
        return constOffset >= 0;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler asm) {
        int nVectors = withMask ? 1 : nValues == 1 ? 4 : nValues == 2 ? 2 : 1;
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
        int vectorLength = vectorKind.getVectorLength();
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

        // move search values to vectors
        for (int i = 0; i < nValues; i++) {
            // fill comparison vector with copies of the search value
            broadcastSearchValue(crb, asm, vecCmp[i], searchValue[i], cmpResult, vecArray[0]);
        }

        // check if vector load is in bounds
        asm.cmpqAndJcc(index, arrayLength, ConditionFlag.LessEqual, runVectorized, false);

        if (supportsAVX2AndYMM()) {
            // region is too short for YMM vectors, try XMM
            Label[] xmmFound = {new Label()};
            // index = fromIndex (+ 1 if findTwoConsecutive) + XMM vector size
            asm.subq(index, vectorLength / 2);
            // check if vector vector load is in bounds
            asm.cmpqAndJcc(index, arrayLength, ConditionFlag.Greater, qWordWise, false);
            // do one vector comparison from fromIndex
            emitVectorCompare(asm, AVXSize.XMM, 1, arrayPtr, index, vecCmp, vecArray, cmpResult, xmmFound, true);
            // and one aligned to the array end
            asm.movq(index, arrayLength);
            emitVectorCompare(asm, AVXSize.XMM, 1, arrayPtr, index, vecCmp, vecArray, cmpResult, xmmFound, true);
            // no match, return negative
            asm.jmp(elementWiseNotFound);
            // match found, adjust index by XMM offset
            asm.bind(xmmFound[0]);
            asm.subq(index, (vectorLength / 2) + (findTwoConsecutive ? 1 : 0));
            // return index found
            asm.jmp(bsfAdd);
        }

        asm.bind(qWordWise);
        int vectorLengthQWord = AVXSize.QWORD.getBytes() / stride.value;
        // region is too short for XMM vectors, try QWORD
        Label[] qWordFound = {new Label()};
        // index = fromIndex (+ 1 if findTwoConsecutive) + QWORD vector size
        asm.subq(index, vectorLengthQWord);
        // check if vector vector load is in bounds
        asm.cmpqAndJcc(index, arrayLength, ConditionFlag.Greater, elementWise, false);
        // do one vector comparison from fromIndex
        emitVectorCompare(asm, AVXSize.QWORD, 1, arrayPtr, index, vecCmp, vecArray, cmpResult, qWordFound, true);
        // and one aligned to the array end
        asm.movq(index, arrayLength);
        emitVectorCompare(asm, AVXSize.QWORD, 1, arrayPtr, index, vecCmp, vecArray, cmpResult, qWordFound, true);
        // no match, return negative
        asm.jmpb(elementWiseNotFound);
        // match found, adjust index by QWORD offset
        asm.bind(qWordFound[0]);
        asm.subq(index, vectorLengthQWord + (findTwoConsecutive ? 1 : 0));
        // return index found
        asm.jmp(bsfAdd);

        // search range is smaller than vector size, do element-wise comparison
        asm.bind(elementWise);
        // index = fromIndex (+ 1 if findTwoConsecutive)
        asm.subq(index, vectorLengthQWord);
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
        if (findTwoConsecutive) {
            AMD64Address arrayAddr = new AMD64Address(arrayPtr, index, arrayIndexStride, -stride.value);
            if (withMask) {
                asm.movSZx(getDoubleOpSize(stride), ZERO_EXTEND, cmpResult, arrayAddr);
                asm.orq(cmpResult, asRegister(searchValue[1]));
                asm.cmpqAndJcc(cmpResult, asRegister(searchValue[0]), AMD64Assembler.ConditionFlag.Equal, elementWiseFound, true);
            } else {
                asm.cmpAndJcc(getDoubleOpSize(stride), asRegister(searchValue[0]), arrayAddr, AMD64Assembler.ConditionFlag.Equal, elementWiseFound, true);
            }
        } else {
            // check for match
            // address = findTwoConsecutive ? array[index - 1] : array[index]
            AMD64Address arrayAddr = new AMD64Address(arrayPtr, index, arrayIndexStride);
            boolean valuesOnStack = searchValuesOnStack(searchValue);
            if (withMask) {
                assert !valuesOnStack;
                assert nValues == 2;
                asm.movSZx(valueSize, ZERO_EXTEND, cmpResult, arrayAddr);
                asm.orq(cmpResult, asRegister(searchValue[1]));
                asm.cmpqAndJcc(cmpResult, asRegister(searchValue[0]), AMD64Assembler.ConditionFlag.Equal, elementWiseFound, true);
            } else if (valuesOnStack) {
                asm.movSZx(valueSize, ZERO_EXTEND, cmpResult, arrayAddr);
                for (int i = 0; i < nValues; i++) {
                    if (isStackSlot(searchValue[i])) {
                        asm.cmpqAndJcc(cmpResult, (AMD64Address) crb.asAddress(searchValue[i]), AMD64Assembler.ConditionFlag.Equal, elementWiseFound, true);
                    } else {
                        asm.cmpqAndJcc(cmpResult, asRegister(searchValue[i]), AMD64Assembler.ConditionFlag.Equal, elementWiseFound, true);
                    }
                }
            } else {
                for (int i = 0; i < nValues; i++) {
                    asm.cmpAndJcc(valueSize, asRegister(searchValue[i]), arrayAddr, AMD64Assembler.ConditionFlag.Equal, elementWiseFound, true);
                }
            }
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
        emitVectorCompare(asm, vectorSize, 1, arrayPtr, index, vecCmp, vecArray, cmpResult, vectorFound, false);

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

        // check if there are enough array slots remaining for the bulk loop
        asm.cmpqAndJcc(index, arrayLength, ConditionFlag.Greater, skipBulkVectorLoop, true);

        asm.align(preferredLoopAlignment(crb));
        asm.bind(bulkVectorLoop);
        // memory-aligned bulk comparison
        emitVectorCompare(asm, vectorSize, nVectors, arrayPtr, index, vecCmp, vecArray, cmpResult, vectorFound, false);
        // adjust index
        asm.addq(index, bulkSize);
        // check if there are enough array slots remaining for the bulk loop
        asm.cmpqAndJcc(index, arrayLength, ConditionFlag.LessEqual, bulkVectorLoop, true);

        asm.bind(skipBulkVectorLoop);
        if (nVectors == 1) {
            // do last load from end of array
            asm.movq(index, arrayLength);
            // compare
            emitVectorCompare(asm, vectorSize, 1, arrayPtr, index, vecCmp, vecArray, cmpResult, vectorFound, true);
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
            emitVectorCompare(asm, vectorSize, 1, arrayPtr, index, vecCmp, vecArray, cmpResult, vectorFound, true);
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
        // find offset
        bsfq(asm, cmpResult, cmpResult);
        if (stride.value > 1) {
            // convert byte offset to chars
            asm.shrq(cmpResult, stride.log2);
        }
        // add offset to index
        asm.addq(index, cmpResult);

        asm.bind(ret);
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
                    AVXSize targetVectorSize,
                    int nVectors,
                    Register arrayPtr,
                    Register index,
                    Register[] vecCmp,
                    Register[] vecArray,
                    Register cmpResult,
                    Label[] vectorFound,
                    boolean shortJmp) {
        // load array contents into vectors
        for (int i = 0; i < nVectors; i++) {
            int base = i * nValues;
            for (int j = 0; j < (withMask ? nValues / 2 : nValues); j++) {
                emitArrayLoad(asm, targetVectorSize, vecArray[base + j], arrayPtr, index, getVectorOffset(nVectors - (i + 1), j, targetVectorSize));
            }
        }
        // compare all loaded bytes to the search value.
        // matching bytes are set to 0xff, non-matching bytes are set to 0x00.
        if (findTwoConsecutive) {
            for (int i = 0; i < nVectors << 1; i += 2) {
                if (withMask) {
                    asm.por(targetVectorSize, vecArray[i], vecCmp[2]);
                    asm.por(targetVectorSize, vecArray[i + 1], vecCmp[3]);
                }
                asm.pcmpeq(targetVectorSize, stride, vecArray[i], vecCmp[0]);
                asm.pcmpeq(targetVectorSize, stride, vecArray[i + 1], vecCmp[1]);
                asm.pand(targetVectorSize, vecArray[i], vecArray[i + 1]);
                asm.pmovmsk(targetVectorSize, cmpResult, vecArray[i]);
                emitVectorCompareCheckVectorFound(asm, targetVectorSize, cmpResult, vectorFound[nVectors - ((i / 2) + 1)], shortJmp);
            }
        } else if (withMask) {
            assert nValues == 2 && nVectors == 1;
            asm.por(targetVectorSize, vecArray[0], vecCmp[1]);
            asm.pcmpeq(targetVectorSize, stride, vecArray[0], vecCmp[0]);
            asm.pmovmsk(targetVectorSize, cmpResult, vecArray[0]);
            emitVectorCompareCheckVectorFound(asm, targetVectorSize, cmpResult, vectorFound[0], shortJmp);
        } else {
            for (int i = 0; i < nVectors; i++) {
                int base = i * nValues;
                for (int j = 0; j < nValues; j++) {
                    asm.pcmpeq(targetVectorSize, stride, vecArray[base + j], vecCmp[j]);
                    if ((j & 1) == 1) {
                        asm.por(targetVectorSize, vecArray[base + j - 1], vecArray[base + j]);
                    }
                }
                if (nValues > 2) {
                    asm.por(targetVectorSize, vecArray[base], vecArray[base + 2]);
                }
                asm.pmovmsk(targetVectorSize, cmpResult, vecArray[base]);
                emitVectorCompareCheckVectorFound(asm, targetVectorSize, cmpResult, vectorFound[nVectors - (i + 1)], shortJmp);
            }
        }
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
                throw GraalError.shouldNotReachHere();
        }
    }

    @SuppressWarnings("fallthrough")
    private void emitArrayLoad(AMD64MacroAssembler asm, AVXSize targetVectorSize, Register vecDst, Register array, Register index, int displacement) {
        AMD64Address src = new AMD64Address(array, index, arrayIndexStride, displacement);
        if (asm.supports(CPUFeature.AVX)) {
            switch (targetVectorSize) {
                case DWORD:
                    VexMoveOp.VMOVD.emit(asm, AVXSize.DWORD, vecDst, src);
                    break;
                case QWORD:
                    VexMoveOp.VMOVQ.emit(asm, AVXSize.QWORD, vecDst, src);
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
                    throw GraalError.shouldNotReachHere();
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
                assert stride == Stride.S4;
                return OperandSize.QWORD;
        }
    }
}
