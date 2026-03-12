/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.Greater;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.Less;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.NotZero;
import static jdk.graal.compiler.asm.amd64.AMD64MacroAssembler.ExtendMode.ZERO_EXTEND;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import java.util.EnumSet;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.Value;

/**
 * Equivalent to libC's {@code strlen} function to determine the length of a zero-terminated buffer.
 */
@Opcode("AMD64_INDEX_OF_ZERO")
public final class AMD64IndexOfZeroOp extends AMD64ComplexVectorOp {
    public static final LIRInstructionClass<AMD64IndexOfZeroOp> TYPE = LIRInstructionClass.create(AMD64IndexOfZeroOp.class);

    private static final int VECTOR_LOOP_UNROLL = 4;
    private static final int MIN_PAGE_SIZE = 4096;

    private static final Register REG_ARRAY = rsi;

    private final Stride stride;

    @Def({OperandFlag.REG}) Value resultValue;
    @Alive({OperandFlag.REG}) Value arrayReg;
    @Temp({OperandFlag.REG}) Value[] generalPurposeTmpValues;
    @Temp({OperandFlag.REG}) Value vectorCmp;
    @Temp({OperandFlag.REG}) Value[] vectorArray;

    private AMD64IndexOfZeroOp(Stride stride, LIRGeneratorTool tool, EnumSet<CPUFeature> runtimeCheckedCPUFeatures, Value result, Value arrayPtr) {
        super(TYPE, tool, runtimeCheckedCPUFeatures, AVXSize.YMM);
        this.stride = stride;
        this.resultValue = result;
        this.arrayReg = arrayPtr;
        this.vectorCmp = tool.newVariable(LIRKind.value(getVectorKind(stride)));
        this.vectorArray = allocateVectorRegisters(tool, stride, VECTOR_LOOP_UNROLL);
        this.generalPurposeTmpValues = allocateTempRegisters(tool, AMD64Kind.QWORD, 2);
        GraalError.guarantee(supports(tool.target(), runtimeCheckedCPUFeatures, CPUFeature.SSE4_1), "needs at least SSE4.1 support");
        // Note: on SVM, this check is not sufficient - instead there's a check in
        // CEntryPointSnippets.createIsolate
        GraalError.guarantee((Unsafe.getUnsafe().pageSize() & (MIN_PAGE_SIZE - 1)) == 0, "osPageSize must be a multiple of " + MIN_PAGE_SIZE);
    }

    private int vectorLoopSize() {
        return vectorSize.getBytes() * VECTOR_LOOP_UNROLL;
    }

    public static AMD64IndexOfZeroOp movParamsAndCreate(Stride stride, LIRGeneratorTool tool, EnumSet<CPUFeature> runtimeCheckedCPUFeatures, Value result, Value arrayPtr) {
        RegisterValue regArray = REG_ARRAY.asValue(arrayPtr.getValueKind());
        tool.emitMove(regArray, arrayPtr);
        return new AMD64IndexOfZeroOp(stride, tool, runtimeCheckedCPUFeatures, result, regArray);
    }

    private static Register[] asRegisters(Value[] values) {
        Register[] registers = new Register[values.length];
        for (int i = 0; i < registers.length; i++) {
            registers[i] = asRegister(values[i]);
        }
        return registers;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler asm) {
        AMD64Assembler.AMD64SIMDInstructionEncoding oldEncoding = asm.setTemporaryAvxEncoding(AMD64Assembler.AMD64SIMDInstructionEncoding.VEX);

        Register arrayPtr = asRegister(arrayReg);
        Register index = asRegister(resultValue);
        Register vecCmp = asRegister(vectorCmp);
        Register[] vecArray = asRegisters(vectorArray);
        Register tmp = asRegister(generalPurposeTmpValues[0]);
        Register tmp2 = asRegister(generalPurposeTmpValues[1]);

        Label ret = new Label();
        Label vectorLoop = new Label();
        Label[] vectorFound = {new Label(), new Label(), new Label(), new Label()};

        Label slowPath = new Label();

        // clear vecCmp
        asm.pxor(vectorSize, vecCmp, vecCmp);
        // clear index
        asm.xorq(index, index);

        // First part: deal with unaligned starting address.

        // tmp = arrayPtr
        asm.movq(tmp, arrayPtr);
        // tmp = arrayPtr % pageSize
        asm.andq(tmp, MIN_PAGE_SIZE - 1);
        // check if a vector loop iteration load starting at the current pointer could cross a page
        // boundary. In this case, we have to take a slower path that takes care not to cross the
        // page boundary until all bytes up to the boundary are checked.
        asm.cmpqAndJcc(tmp, MIN_PAGE_SIZE - vectorLoopSize(), Greater, slowPath, false);

        // unaligned vector loads cannot cross page boundary - perform one unaligned peeled loop
        // iteration.

        // emit peeled loop iteration.
        emitVectorCompare(asm, vectorSize, VECTOR_LOOP_UNROLL, arrayPtr, index, vecCmp, vecArray, vectorFound, false);

        // align index.
        // index = arrayPtr
        asm.movq(index, arrayPtr);
        // index = arrayPtr % loopSize
        asm.andq(index, vectorLoopSize() - 1);
        // index = -(arrayPtr % loopSize)
        asm.negq(index);
        // index = -(arrayPtr % loopSize) + loopSize
        asm.addq(index, vectorLoopSize());
        // index + arrayPtr is now aligned to loopSize.

        // Second part: main loop.

        asm.align(preferredLoopAlignment(crb));
        asm.bind(vectorLoop);
        // memory-aligned bulk comparison.
        emitVectorCompare(asm, vectorSize, VECTOR_LOOP_UNROLL, arrayPtr, index, vecCmp, vecArray, vectorFound, true);
        // index += loopSize
        asm.addq(index, vectorLoopSize());
        // loop infinitely until a null value is found
        asm.jmpb(vectorLoop);

        // emit jump targets for matching vectors.
        for (int i = 0; i < VECTOR_LOOP_UNROLL; i++) {
            asm.bind(vectorFound[i]);
            asm.pmovmsk(vectorSize, tmp2, vecArray[i]);
            if (i > 0) {
                asm.addq(index, vectorSize.getBytes() * i);
            }
            // find offset
            bsfq(asm, tmp2, tmp2);
            asm.addq(index, tmp2);
            if (i < VECTOR_LOOP_UNROLL - 1) {
                asm.jmp(ret);
            }
        }

        emitSlowPath(crb, asm, arrayPtr, index, vecCmp, vecArray, tmp, tmp2, slowPath, vectorLoop, ret);

        asm.bind(ret);
        asm.resetAvxEncoding(oldEncoding);
    }

    private void emitSlowPath(CompilationResultBuilder crb, AMD64MacroAssembler asm,
                    Register arrayPtr,
                    Register index,
                    Register vecCmp,
                    Register[] vecArray,
                    Register tmp,
                    Register tmp2,
                    Label slowPath,
                    Label vectorLoop,
                    Label ret) {
        asm.bind(slowPath);
        // Unaligned vector load would cross page boundary - make sure to check all bytes up to the
        // page boundary before reading further. Otherwise, we may cause a segfault by prematurely
        // reading from an adjacent protected page.

        // tmp is still arrayPtr % pageSize.
        // tmp = -(arrayPtr % pageSize)
        asm.negq(tmp);
        // tmp = -(arrayPtr % pageSize) + pageSize
        asm.addq(tmp, MIN_PAGE_SIZE);
        // tmp is now the delta between arrayPtr and the page boundary

        Label singleVectorFound = new Label();
        Label qwordFound = new Label();
        // perform single-vector comparisons up to where the remaining bytes don't fit into a single
        // vector anymore.
        for (int i = 0; i < VECTOR_LOOP_UNROLL - 1; i++) {
            Label skipSingleVector = new Label();
            asm.cmpqAndJcc(tmp, vectorSize.getBytes() * (VECTOR_LOOP_UNROLL - (i + 1)), Less, skipSingleVector, true);
            emitSingleVectorCompare(asm, vectorSize, arrayPtr, index, vecCmp, vecArray, singleVectorFound);
            asm.addq(index, vectorSize.getBytes());
            asm.bind(skipSingleVector);
        }
        // check if the delta is less than a single vector.
        Label lessThan1Vector = new Label();
        asm.cmpqAndJcc(tmp, vectorSize.getBytes(), Less, lessThan1Vector, true);
        // delta (tmp) is greater or equal to a single vector, so we can safely check the remaining
        // bytes with a load aligned to the page boundary, overlapping the load with the last of the
        // previous single vector comparisons
        asm.movq(index, tmp);
        asm.subq(index, vectorSize.getBytes());
        // arrayPtr + index is now exactly at next pageBoundary - vectorSize.getBytes().
        emitSingleVectorCompare(asm, vectorSize, arrayPtr, index, vecCmp, vecArray, singleVectorFound);
        // all bytes up to the page boundary have been checked without finding a match; now we can
        // continue with the aligned vector loop starting at the page boundary.
        asm.movq(index, tmp);
        asm.jmp(vectorLoop);

        asm.bind(singleVectorFound);
        if (supports(CPUFeature.SSE4_1)) {
            asm.pmovmsk(vectorSize, tmp2, vecArray[0]);
        }
        asm.bind(qwordFound);
        bsfq(asm, tmp2, tmp2);
        asm.addq(index, tmp2);
        asm.jmp(ret);

        asm.bind(lessThan1Vector);

        if (supportsAVX2AndYMM()) {
            // delta is less than a YMM vector, check if we can cover it with two overlapping XMM
            // vector loads.
            Label lessThanXMM = new Label();
            asm.cmpqAndJcc(tmp, AVXSize.XMM.getBytes(), Less, lessThanXMM, true);
            emitSingleVectorCompare(asm, AVXSize.XMM, arrayPtr, index, vecCmp, vecArray, singleVectorFound);
            asm.movq(index, tmp);
            asm.subq(index, AVXSize.XMM.getBytes());
            emitSingleVectorCompare(asm, AVXSize.XMM, arrayPtr, index, vecCmp, vecArray, singleVectorFound);
            asm.movq(index, tmp);
            asm.jmp(vectorLoop);
            asm.bind(lessThanXMM);
        }
        // delta is less than a XMM vector, check if we can cover it with two overlapping QWORD
        // vector loads.
        Label lessThanQWORD = new Label();
        asm.cmpqAndJcc(tmp, AVXSize.QWORD.getBytes(), Less, lessThanQWORD, true);
        AMD64ArrayIndexOfOp.emitArrayLoad(asm, AVXSize.QWORD, vecArray[0], arrayPtr, index, Stride.S1, 0);
        asm.pcmpeq(vectorSize, stride, vecArray[0], vecCmp);
        asm.pmovmsk(vectorSize, tmp2, vecArray[0]);
        asm.testlAndJcc(tmp2, 0xff, NotZero, qwordFound, true);
        asm.movq(index, tmp);
        asm.subq(index, AVXSize.QWORD.getBytes());
        AMD64ArrayIndexOfOp.emitArrayLoad(asm, AVXSize.QWORD, vecArray[0], arrayPtr, index, Stride.S1, 0);
        asm.pcmpeq(vectorSize, stride, vecArray[0], vecCmp);
        asm.pmovmsk(vectorSize, tmp2, vecArray[0]);
        asm.testlAndJcc(tmp2, 0xff, NotZero, qwordFound, false);
        asm.movq(index, tmp);
        asm.jmp(vectorLoop);
        asm.bind(lessThanQWORD);

        // delta is less than a single QWORD, process these bytes in a scalar loop.
        Label elementWiseLoop = new Label();
        Label elementWiseFound = new Label();
        // compare one-by-one
        asm.align(preferredLoopAlignment(crb));
        asm.bind(elementWiseLoop);
        asm.movSZx(AMD64ArrayIndexOfOp.getOpSize(stride), ZERO_EXTEND, tmp2, new AMD64Address(arrayPtr, index, Stride.S1));
        asm.testqAndJcc(tmp2, tmp2, ConditionFlag.Zero, elementWiseFound, true);
        // adjust index
        asm.addq(index, stride.value);
        // continue loop until index == delta
        asm.cmpqAndJcc(index, tmp, ConditionFlag.Less, elementWiseLoop, true);
        asm.jmp(vectorLoop);

        asm.bind(elementWiseFound);
    }

    /**
     * Checks {@code nVectors} vectors in {@code vecArray} for occurrences of zero-values with
     * {@code pcmpeq}, and jumps to the current vector's respective target in {@code vectorFound} if
     * a zero value is found.
     */
    private void emitVectorCompare(AMD64MacroAssembler asm,
                    AVXSize vSize,
                    int nVectors,
                    Register arrayPtr,
                    Register index,
                    Register vecCmp,
                    Register[] vecArray,
                    Label[] vectorFound,
                    boolean shortJmp) {
        // load array contents into vectors
        for (int i = 0; i < nVectors; i++) {
            AMD64ArrayIndexOfOp.emitArrayLoad(asm, vSize, vecArray[i], arrayPtr, index, Stride.S1, i * vSize.getBytes());
        }
        for (int i = 0; i < nVectors; i++) {
            asm.pcmpeq(vSize, stride, vecArray[i], vecCmp);
            asm.ptest(vSize, vecArray[i], vecArray[i]);
            asm.jcc(NotZero, vectorFound[i], shortJmp);
        }
    }

    private void emitSingleVectorCompare(AMD64MacroAssembler asm,
                    AVXSize vSize,
                    Register arrayPtr,
                    Register index,
                    Register vecCmp,
                    Register[] vecArray,
                    Label vectorFound) {
        // load array contents into vectors
        AMD64ArrayIndexOfOp.emitArrayLoad(asm, vSize, vecArray[0], arrayPtr, index, Stride.S1, 0);
        asm.pcmpeq(vSize, stride, vecArray[0], vecCmp);
        asm.ptest(vSize, vecArray[0], vecArray[0]);
        asm.jccb(NotZero, vectorFound);
    }
}
