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
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.movSZx;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.pand;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.pcmpeq;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.pmovmsk;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.por;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.ExtendMode.ZERO_EXTEND;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;

import java.util.Objects;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Address.Scale;
import org.graalvm.compiler.asm.amd64.AMD64Assembler;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexMoveOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRMIOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRMOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp;
import org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.asm.amd64.AVXKind;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

/**
 */
@Opcode("AMD64_ARRAY_INDEX_OF")
public final class AMD64ArrayIndexOfOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64ArrayIndexOfOp> TYPE = LIRInstructionClass.create(AMD64ArrayIndexOfOp.class);

    private static final Register REG_ARRAY = rsi;
    private static final Register REG_OFFSET = rax;
    private static final Register REG_LENGTH = rdx;
    private static final Register REG_FROM_INDEX = rdi;
    private static final Register REG_SEARCH_VALUE_1 = rcx;
    private static final Register REG_SEARCH_VALUE_2 = r8;

    private final JavaKind valueKind;
    private final int nValues;
    private final boolean findTwoConsecutive;
    private final boolean withMask;
    private final AMD64Kind vectorKind;
    private final Scale arrayIndexScale;
    private final int arrayBaseOffset;
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

    private AMD64ArrayIndexOfOp(int arrayBaseOffset, JavaKind valueKind, boolean findTwoConsecutive, boolean withMask, int maxVectorSize, int constOffset, int nValues, LIRGeneratorTool tool,
                    Value result, Value arrayPtr, Value arrayOffset, Value arrayLength, Value fromIndex, Value searchValue1, Value searchValue2, Value searchValue3, Value searchValue4) {
        super(TYPE);
        this.valueKind = valueKind;
        this.arrayIndexScale = Objects.requireNonNull(Scale.fromInt(tool.getProviders().getMetaAccess().getArrayIndexScale(valueKind)));
        this.arrayBaseOffset = arrayBaseOffset;
        this.findTwoConsecutive = findTwoConsecutive;
        this.withMask = withMask;
        this.constOffset = constOffset;
        this.nValues = nValues;
        assert 0 < nValues && nValues <= 4;
        assert valueKind == JavaKind.Byte || valueKind == JavaKind.Char || valueKind == JavaKind.Int;
        assert supports(tool, CPUFeature.SSE2) || supports(tool, CPUFeature.AVX) || supportsAVX2(tool);
        assert !(!withMask && findTwoConsecutive) || nValues == 2;
        assert !(withMask && findTwoConsecutive) || nValues == 4;
        assert !(withMask && !findTwoConsecutive) || nValues == 2;

        resultValue = result;
        this.arrayTmp = this.arrayReg = arrayPtr;
        this.offsetTmp = this.offsetReg = arrayOffset;
        this.lengthTmp = this.lengthReg = arrayLength;
        this.fromIndexTmp = this.fromIndexReg = fromIndex;
        this.searchValue1Tmp = this.searchValue1 = searchValue1;
        this.searchValue2Tmp = this.searchValue2 = searchValue2;
        this.searchValue3 = searchValue3;
        this.searchValue4 = searchValue4;

        vectorKind = getVectorKind(valueKind, maxVectorSize, tool);
        this.vectorCompareVal = allocateVectorRegisters(tool, vectorKind, nValues);
        this.vectorArray = allocateVectorRegisters(tool, vectorKind, 4);
    }

    private static Value[] allocateVectorRegisters(LIRGeneratorTool tool, AMD64Kind vectorKind, int n) {
        Value[] vectors = new Value[n];
        for (int i = 0; i < vectors.length; i++) {
            vectors[i] = tool.newVariable(LIRKind.value(vectorKind));
        }
        return vectors;
    }

    private static Register[] asRegisters(Value[] values) {
        Register[] registers = new Register[values.length];
        for (int i = 0; i < registers.length; i++) {
            registers[i] = asRegister(values[i]);
        }
        return registers;
    }

    public static AMD64ArrayIndexOfOp movParamsAndCreate(int arrayBaseOffset, JavaKind valueKind, boolean findTwoConsecutive, boolean withMask, int maxVectorSize, LIRGeneratorTool tool,
                    Value result, Value arrayPtr, Value arrayOffset, Value arrayLength, Value fromIndex, Value... searchValues) {

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
        return new AMD64ArrayIndexOfOp(arrayBaseOffset, valueKind, findTwoConsecutive, withMask, maxVectorSize, constOffset, nValues, tool,
                        result, regArray, regOffset, regLength, regFromIndex, regSearchValue1, regSearchValue2, regSearchValue3, regSearchValue4);
    }

    private boolean useConstantOffset() {
        return constOffset >= 0;
    }

    private AVXKind.AVXSize getVectorSize() {
        return AVXKind.getDataSize(vectorKind);
    }

    private static AMD64Kind getVectorKind(JavaKind valueKind, int maxVectorSize, LIRGeneratorTool tool) {
        if (supportsAVX2(tool) && (maxVectorSize < 0 || maxVectorSize >= 32)) {
            switch (valueKind) {
                case Byte:
                    return AMD64Kind.V256_BYTE;
                case Char:
                    return AMD64Kind.V256_WORD;
                default:
                    return AMD64Kind.V256_DWORD;
            }
        } else {
            switch (valueKind) {
                case Byte:
                    return AMD64Kind.V128_BYTE;
                case Char:
                    return AMD64Kind.V128_WORD;
                default:
                    return AMD64Kind.V128_DWORD;
            }
        }
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
        int vectorSize = getVectorElementCount();
        int bulkSize = vectorSize * nVectors;

        if (useConstantOffset()) {
            asm.leaq(arrayPtr, new AMD64Address(arrayPtr, constOffset + arrayBaseOffset));
        } else {
            asm.leaq(arrayPtr, new AMD64Address(arrayPtr, asRegister(offsetReg), Scale.Times1, arrayBaseOffset));
        }

        // index = fromIndex + vectorSize (+1 if findTwoConsecutive)
        asm.leaq(index, new AMD64Address(asRegister(fromIndexReg), vectorSize + (findTwoConsecutive ? 1 : 0)));

        // re-use fromIndex register as temp
        Register cmpResult = asRegister(fromIndexReg);

        // move search values to vectors
        for (int i = 0; i < nValues; i++) {
            // fill comparison vector with copies of the search value
            broadcastSearchValue(crb, asm, vecCmp[i], searchValue[i], cmpResult, vecArray[0]);
        }

        // check if vector vector load is in bounds
        asm.cmpqAndJcc(index, arrayLength, ConditionFlag.LessEqual, runVectorized, false);

        if (getVectorSize() == AVXKind.AVXSize.YMM) {
            // region is too short for YMM vectors, try XMM
            Label[] xmmFound = {new Label()};
            // index = fromIndex (+ 1 if findTwoConsecutive) + XMM vector size
            asm.subq(index, vectorSize / 2);
            // check if vector vector load is in bounds
            asm.cmpqAndJcc(index, arrayLength, ConditionFlag.Greater, qWordWise, false);
            // do one vector comparison from fromIndex
            emitVectorCompare(asm, valueKind, AVXKind.AVXSize.XMM, 1, arrayPtr, index, vecCmp, vecArray, cmpResult, xmmFound, true);
            // and one aligned to the array end
            asm.movq(index, arrayLength);
            emitVectorCompare(asm, valueKind, AVXKind.AVXSize.XMM, 1, arrayPtr, index, vecCmp, vecArray, cmpResult, xmmFound, true);
            // no match, return negative
            asm.jmp(elementWiseNotFound);
            // match found, adjust index by XMM offset
            asm.bind(xmmFound[0]);
            asm.subq(index, (vectorSize / 2) + (findTwoConsecutive ? 1 : 0));
            // return index found
            asm.jmp(bsfAdd);
        }

        asm.bind(qWordWise);
        int vectorSizeQWord = AVXKind.AVXSize.QWORD.getBytes() / valueKind.getByteCount();
        // region is too short for XMM vectors, try QWORD
        Label[] qWordFound = {new Label()};
        // index = fromIndex (+ 1 if findTwoConsecutive) + QWORD vector size
        asm.subq(index, vectorSizeQWord);
        // check if vector vector load is in bounds
        asm.cmpqAndJcc(index, arrayLength, ConditionFlag.Greater, elementWise, false);
        // do one vector comparison from fromIndex
        emitVectorCompare(asm, valueKind, AVXKind.AVXSize.QWORD, 1, arrayPtr, index, vecCmp, vecArray, cmpResult, qWordFound, true);
        // and one aligned to the array end
        asm.movq(index, arrayLength);
        emitVectorCompare(asm, valueKind, AVXKind.AVXSize.QWORD, 1, arrayPtr, index, vecCmp, vecArray, cmpResult, qWordFound, true);
        // no match, return negative
        asm.jmpb(elementWiseNotFound);
        // match found, adjust index by QWORD offset
        asm.bind(qWordFound[0]);
        asm.subq(index, vectorSizeQWord + (findTwoConsecutive ? 1 : 0));
        // return index found
        asm.jmp(bsfAdd);

        // search range is smaller than vector size, do element-wise comparison
        asm.bind(elementWise);
        // index = fromIndex (+ 1 if findTwoConsecutive)
        asm.subq(index, vectorSizeQWord);
        // check if enough array slots remain
        asm.cmpqAndJcc(index, arrayLength, ConditionFlag.GreaterEqual, elementWiseNotFound, true);

        OperandSize valueSize = getOpSize(valueKind);
        // combine consecutive search values into one register
        if (findTwoConsecutive) {
            asm.shlq(asRegister(searchValue[1]), valueKind.getBitCount());
            asm.orq(asRegister(searchValue[0]), asRegister(searchValue[1]));
            if (withMask) {
                if (isStackSlot(searchValue[2])) {
                    movSZx(asm, valueSize, ZERO_EXTEND, asRegister(searchValue[1]), (AMD64Address) crb.asAddress(searchValue[2]));
                } else {
                    asm.movq(asRegister(searchValue[1]), asRegister(searchValue[2]));
                }
                asm.shlq(asRegister(searchValue[1]), valueKind.getBitCount());
                if (isStackSlot(searchValue[3])) {
                    movSZx(asm, valueSize, ZERO_EXTEND, cmpResult, (AMD64Address) crb.asAddress(searchValue[3]));
                    asm.orq(asRegister(searchValue[1]), cmpResult);
                } else {
                    asm.orq(asRegister(searchValue[1]), asRegister(searchValue[3]));
                }
            }
        }

        // compare one-by-one
        asm.bind(elementWiseLoop);
        if (findTwoConsecutive) {
            AMD64Address arrayAddr = new AMD64Address(arrayPtr, index, arrayIndexScale, -valueKind.getByteCount());
            if (withMask) {
                movSZx(asm, getDoubleOpSize(valueKind), ZERO_EXTEND, cmpResult, arrayAddr);
                asm.orq(cmpResult, asRegister(searchValue[1]));
                asm.cmpqAndJcc(cmpResult, asRegister(searchValue[0]), AMD64Assembler.ConditionFlag.Equal, elementWiseFound, true);
            } else {
                asm.cmpAndJcc(getDoubleOpSize(valueKind), asRegister(searchValue[0]), arrayAddr, AMD64Assembler.ConditionFlag.Equal, elementWiseFound, true);
            }
        } else {
            // check for match
            // address = findTwoConsecutive ? array[index - 1] : array[index]
            AMD64Address arrayAddr = new AMD64Address(arrayPtr, index, arrayIndexScale);
            boolean valuesOnStack = searchValuesOnStack(searchValue);
            if (withMask) {
                assert !valuesOnStack;
                assert nValues == 2;
                movSZx(asm, valueSize, ZERO_EXTEND, cmpResult, arrayAddr);
                asm.orq(cmpResult, asRegister(searchValue[1]));
                asm.cmpqAndJcc(cmpResult, asRegister(searchValue[0]), AMD64Assembler.ConditionFlag.Equal, elementWiseFound, true);
            } else if (valuesOnStack) {
                movSZx(asm, valueSize, ZERO_EXTEND, cmpResult, arrayAddr);
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
        emitVectorCompare(asm, valueKind, getVectorSize(), 1, arrayPtr, index, vecCmp, vecArray, cmpResult, vectorFound, false);

        // adjust index to vector size alignment
        asm.movl(cmpResult, arrayPtr);
        if (valueKind.getByteCount() > 1) {
            asm.shrl(cmpResult, strideAsPowerOf2());
        }
        asm.addq(index, cmpResult);
        // adjust to next lower multiple of vector size
        asm.andq(index, -vectorSize);
        asm.subq(index, cmpResult);
        // add bulk size
        asm.addq(index, bulkSize);

        // check if there are enough array slots remaining for the bulk loop
        asm.cmpqAndJcc(index, arrayLength, ConditionFlag.Greater, skipBulkVectorLoop, true);

        emitAlign(crb, asm);
        asm.bind(bulkVectorLoop);
        // memory-aligned bulk comparison
        emitVectorCompare(asm, valueKind, getVectorSize(), nVectors, arrayPtr, index, vecCmp, vecArray, cmpResult, vectorFound, false);
        // adjust index
        asm.addq(index, bulkSize);
        // check if there are enough array slots remaining for the bulk loop
        asm.cmpqAndJcc(index, arrayLength, ConditionFlag.LessEqual, bulkVectorLoop, true);

        asm.bind(skipBulkVectorLoop);
        if (nVectors == 1) {
            // do last load from end of array
            asm.movq(index, arrayLength);
            // compare
            emitVectorCompare(asm, valueKind, getVectorSize(), 1, arrayPtr, index, vecCmp, vecArray, cmpResult, vectorFound, true);
        } else {
            // remove bulk offset
            asm.subq(index, bulkSize);
            emitAlign(crb, asm);
            // same loop as bulkVectorLoop, with only one vector
            asm.bind(singleVectorLoop);
            // add vector size
            asm.addq(index, vectorSize);
            // check if vector load is in bounds
            asm.cmpq(index, arrayLength);
            // if load would be over bounds, set the load to the end of the array
            asm.cmovq(AMD64Assembler.ConditionFlag.Greater, index, arrayLength);
            // compare
            emitVectorCompare(asm, valueKind, getVectorSize(), 1, arrayPtr, index, vecCmp, vecArray, cmpResult, vectorFound, true);
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
        asm.bsfq(cmpResult, cmpResult);
        if (valueKind.getByteCount() > 1) {
            // convert byte offset to chars
            asm.shrq(cmpResult, strideAsPowerOf2());
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
        return (i + 1) * getVectorElementCount() + (findTwoConsecutive ? 1 : 0);
    }

    private int getVectorElementCount() {
        return getVectorSize().getBytes() / valueKind.getByteCount();
    }

    private int getVectorOffset(int i, int j, AVXKind.AVXSize vectorSize) {
        if (findTwoConsecutive) {
            return -(((i + 1) * vectorSize.getBytes() + ((j ^ 1) * valueKind.getByteCount())));
        }
        return -(((i + 1) * vectorSize.getBytes()));
    }

    private void broadcastSearchValue(CompilationResultBuilder crb, AMD64MacroAssembler asm, Register dst, Value srcVal, Register tmpReg, Register tmpVector) {
        Register src = asRegOrTmpReg(crb, asm, srcVal, tmpReg);
        AMD64MacroAssembler.movdl(asm, dst, src);
        emitBroadcast(asm, valueKind, dst, tmpVector, getVectorSize());
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

    private static void emitAlign(CompilationResultBuilder crb, AMD64MacroAssembler asm) {
        asm.align(crb.target.wordSize * 2);
    }

    /**
     * Fills {@code vecDst} with copies of its lowest byte, word or dword.
     */
    private static void emitBroadcast(AMD64MacroAssembler asm, JavaKind kind, Register vecDst, Register vecTmp, AVXKind.AVXSize vectorSize) {
        switch (kind) {
            case Byte:
                if (asm.supports(CPUFeature.AVX2)) {
                    VexRMOp.VPBROADCASTB.emit(asm, vectorSize, vecDst, vecDst);
                } else if (asm.supports(CPUFeature.AVX)) {
                    VexRVMOp.VPXOR.emit(asm, vectorSize, vecTmp, vecTmp, vecTmp);
                    VexRVMOp.VPSHUFB.emit(asm, vectorSize, vecDst, vecDst, vecTmp);
                } else if (asm.supports(CPUFeature.SSSE3)) {
                    asm.pxor(vecTmp, vecTmp);
                    asm.pshufb(vecDst, vecTmp);
                } else { // SSE2
                    asm.punpcklbw(vecDst, vecDst);
                    asm.punpcklbw(vecDst, vecDst);
                    asm.pshufd(vecDst, vecDst, 0);
                }
                break;
            case Short:
            case Char:
                if (asm.supports(CPUFeature.AVX2)) {
                    VexRMOp.VPBROADCASTW.emit(asm, vectorSize, vecDst, vecDst);
                } else if (asm.supports(CPUFeature.AVX)) {
                    VexRMIOp.VPSHUFLW.emit(asm, vectorSize, vecDst, vecDst, 0);
                    VexRMIOp.VPSHUFD.emit(asm, vectorSize, vecDst, vecDst, 0);
                } else { // SSE
                    asm.pshuflw(vecDst, vecDst, 0);
                    asm.pshufd(vecDst, vecDst, 0);
                }
                break;
            case Int:
                if (asm.supports(CPUFeature.AVX2)) {
                    VexRMOp.VPBROADCASTD.emit(asm, vectorSize, vecDst, vecDst);
                } else if (asm.supports(CPUFeature.AVX)) {
                    VexRMIOp.VPSHUFD.emit(asm, vectorSize, vecDst, vecDst, 0);
                } else { // SSE
                    asm.pshufd(vecDst, vecDst, 0);
                }
                break;
            default:
                throw new UnsupportedOperationException();
        }
    }

    private void emitVectorCompare(AMD64MacroAssembler asm,
                    JavaKind kind,
                    AVXKind.AVXSize vectorSize,
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
                emitArrayLoad(asm, vectorSize, vecArray[base + j], arrayPtr, index, getVectorOffset(nVectors - (i + 1), j, vectorSize));
            }
        }
        // compare all loaded bytes to the search value.
        // matching bytes are set to 0xff, non-matching bytes are set to 0x00.
        if (findTwoConsecutive) {
            for (int i = 0; i < nVectors << 1; i += 2) {
                if (withMask) {
                    por(asm, vectorSize, vecArray[i], vecCmp[2]);
                    por(asm, vectorSize, vecArray[i + 1], vecCmp[3]);
                }
                pcmpeq(asm, vectorSize, kind, vecArray[i], vecCmp[0]);
                pcmpeq(asm, vectorSize, kind, vecArray[i + 1], vecCmp[1]);
                pand(asm, vectorSize, vecArray[i], vecArray[i + 1]);
                pmovmsk(asm, vectorSize, cmpResult, vecArray[i]);
                emitVectorCompareCheckVectorFound(asm, vectorSize, cmpResult, vectorFound[nVectors - ((i / 2) + 1)], shortJmp);
            }
        } else if (withMask) {
            assert nValues == 2 && nVectors == 1;
            por(asm, vectorSize, vecArray[0], vecCmp[1]);
            pcmpeq(asm, vectorSize, kind, vecArray[0], vecCmp[0]);
            pmovmsk(asm, vectorSize, cmpResult, vecArray[0]);
            emitVectorCompareCheckVectorFound(asm, vectorSize, cmpResult, vectorFound[0], shortJmp);
        } else {
            for (int i = 0; i < nVectors; i++) {
                int base = i * nValues;
                for (int j = 0; j < nValues; j++) {
                    pcmpeq(asm, vectorSize, kind, vecArray[base + j], vecCmp[j]);
                    if ((j & 1) == 1) {
                        por(asm, vectorSize, vecArray[base + j - 1], vecArray[base + j]);
                    }
                }
                if (nValues > 2) {
                    por(asm, vectorSize, vecArray[base], vecArray[base + 2]);
                }
                pmovmsk(asm, vectorSize, cmpResult, vecArray[base]);
                emitVectorCompareCheckVectorFound(asm, vectorSize, cmpResult, vectorFound[nVectors - (i + 1)], shortJmp);
            }
        }
    }

    @SuppressWarnings("fallthrough")
    private static void emitVectorCompareCheckVectorFound(AMD64MacroAssembler asm, AVXKind.AVXSize vectorSize, Register cmpResult, Label branchTarget, boolean shortJmp) {
        switch (vectorSize) {
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
    private void emitArrayLoad(AMD64MacroAssembler asm, AVXKind.AVXSize vectorSize, Register vecDst, Register array, Register index, int displacement) {
        AMD64Address src = new AMD64Address(array, index, arrayIndexScale, displacement);
        if (asm.supports(CPUFeature.AVX)) {
            switch (vectorSize) {
                case DWORD:
                    VexMoveOp.VMOVD.emit(asm, AVXKind.AVXSize.DWORD, vecDst, src);
                    break;
                case QWORD:
                    VexMoveOp.VMOVQ.emit(asm, AVXKind.AVXSize.QWORD, vecDst, src);
                    break;
                case XMM:
                case YMM:
                    VexMoveOp.VMOVDQU32.emit(asm, vectorSize, vecDst, src);
                    break;
                case ZMM:
                    VexMoveOp.VMOVDQU64.emit(asm, vectorSize, vecDst, src);
                    break;
            }
        } else {
            // SSE
            switch (vectorSize) {
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

    private static OperandSize getOpSize(JavaKind kind) {
        switch (kind) {
            case Byte:
                return OperandSize.BYTE;
            case Short:
            case Char:
                return OperandSize.WORD;
            case Int:
                return OperandSize.DWORD;
            default:
                return OperandSize.QWORD;
        }
    }

    private static OperandSize getDoubleOpSize(JavaKind kind) {
        switch (kind) {
            case Byte:
                return OperandSize.WORD;
            case Short:
            case Char:
                return OperandSize.DWORD;
            default:
                assert kind.equals(JavaKind.Int);
                return OperandSize.QWORD;
        }
    }

    private static boolean supportsAVX2(LIRGeneratorTool tool) {
        return supports(tool, CPUFeature.AVX2);
    }

    private static boolean supports(LIRGeneratorTool tool, CPUFeature cpuFeature) {
        return ((AMD64) tool.target().arch).getFeatures().contains(cpuFeature);
    }

    private int strideAsPowerOf2() {
        return Integer.numberOfTrailingZeros(valueKind.getByteCount());
    }

    @Override
    public boolean needsClearUpperVectorRegisters() {
        return true;
    }
}
