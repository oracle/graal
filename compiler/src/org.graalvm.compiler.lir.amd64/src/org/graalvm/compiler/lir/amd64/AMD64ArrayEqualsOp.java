/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.vm.ci.amd64.AMD64.r9;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isIllegal;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.OR;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.XOR;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import java.util.Arrays;
import java.util.EnumSet;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.SSEOp;
import org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.asm.amd64.AVXKind.AVXSize;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.core.common.StrideUtil;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.LIRValueUtil;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

/**
 * Emits code which compares two arrays of the same length. If the CPU supports any vector
 * instructions specialized code is emitted to leverage these instructions.
 *
 * This op can also compare arrays of different integer types (e.g. {@code byte[]} and
 * {@code char[]}) with on-the-fly sign- or zero-extension.
 */
@Opcode("ARRAY_EQUALS")
public final class AMD64ArrayEqualsOp extends AMD64ComplexVectorOp {
    public static final LIRInstructionClass<AMD64ArrayEqualsOp> TYPE = LIRInstructionClass.create(AMD64ArrayEqualsOp.class);

    private static final Register REG_ARRAY_A = rsi;
    private static final Register REG_OFFSET_A = rax;
    private static final Register REG_ARRAY_B = rdi;
    private static final Register REG_OFFSET_B = rcx;
    private static final Register REG_MASK = r8;
    private static final Register REG_LENGTH = rdx;
    private static final Register REG_STRIDE = r9;

    private final JavaKind elementKind;
    private final int constLength;
    private final Stride argStrideA;
    private final Stride argStrideB;
    private final Stride argStrideMask;
    private final AMD64MacroAssembler.ExtendMode extendMode;
    private final boolean canGenerateConstantLengthCompare;

    @Def({REG}) private Value resultValue;
    @Use({REG}) private Value arrayAValue;
    @Use({REG, ILLEGAL}) private Value offsetAValue;
    @Use({REG}) private Value arrayBValue;
    @Use({REG, ILLEGAL}) private Value offsetBValue;
    @Use({REG, ILLEGAL}) private Value arrayMaskValue;
    @Use({REG}) private Value lengthValue;
    @Use({REG, ILLEGAL}) private Value dynamicStridesValue;

    @Temp({REG}) private Value arrayAValueTemp;
    @Temp({REG, ILLEGAL}) private Value offsetAValueTemp;
    @Temp({REG}) private Value arrayBValueTemp;
    @Temp({REG, ILLEGAL}) private Value offsetBValueTemp;
    @Temp({REG, ILLEGAL}) private Value arrayMaskValueTemp;
    @Temp({REG}) private Value lengthValueTemp;
    @Temp({REG, ILLEGAL}) private Value dynamicStrideValueTemp;

    @Temp({REG, ILLEGAL}) private Value tempXMM;

    @Temp({REG}) private Value[] vectorTemp;

    private AMD64ArrayEqualsOp(LIRGeneratorTool tool, JavaKind elementKind, Stride strideA, Stride strideB, Stride strideMask,
                    EnumSet<CPUFeature> runtimeCheckedCPUFeatures,
                    Value result, Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value mask, Value length, Value dynamicStrides,
                    AMD64MacroAssembler.ExtendMode extendMode, int constLength) {
        super(TYPE, tool, runtimeCheckedCPUFeatures, AVXSize.YMM);
        this.extendMode = extendMode;
        this.constLength = constLength;
        this.elementKind = elementKind;
        if (StrideUtil.useConstantStrides(dynamicStrides)) {
            assert elementKind.isNumericInteger() || strideA == strideB;
            this.argStrideA = strideA;
            this.argStrideB = strideB;
            this.argStrideMask = strideMask;
        } else {
            this.argStrideA = null;
            this.argStrideB = null;
            this.argStrideMask = null;
        }
        this.canGenerateConstantLengthCompare = canGenerateConstantLengthCompare(tool.target(), runtimeCheckedCPUFeatures, elementKind, strideA, strideB, constLength, dynamicStrides, vectorSize);

        this.resultValue = result;
        this.arrayAValue = this.arrayAValueTemp = arrayA;
        this.offsetAValue = this.offsetAValueTemp = offsetA;
        this.arrayBValue = this.arrayBValueTemp = arrayB;
        this.offsetBValue = this.offsetBValueTemp = offsetB;
        this.arrayMaskValue = this.arrayMaskValueTemp = mask;
        this.lengthValue = this.lengthValueTemp = length;
        this.dynamicStridesValue = this.dynamicStrideValueTemp = dynamicStrides;

        if (elementKind == JavaKind.Float) {
            this.tempXMM = tool.newVariable(LIRKind.value(AMD64Kind.SINGLE));
        } else if (elementKind == JavaKind.Double) {
            this.tempXMM = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
        } else {
            this.tempXMM = Value.ILLEGAL;
        }

        // We only need the vector temporaries if we generate SSE code.
        if (supports(tool.target(), runtimeCheckedCPUFeatures, CPUFeature.SSE4_1)) {
            LIRKind lirKind = LIRKind.value(getVectorKind(JavaKind.Byte));
            this.vectorTemp = new Value[(withMask() ? 3 : 2) + (canGenerateConstantLengthCompare ? 1 : 0)];
            for (int i = 0; i < vectorTemp.length; i++) {
                vectorTemp[i] = tool.newVariable(lirKind);
            }
        } else {
            this.vectorTemp = new Value[0];
        }
    }

    public static AMD64ArrayEqualsOp movParamsAndCreate(
                    LIRGeneratorTool tool,
                    EnumSet<CPUFeature> runtimeCheckedCPUFeatures,
                    Value result,
                    Value arrayA, Value offsetA,
                    Value arrayB, Value offsetB,
                    Value mask,
                    Value length,
                    Value dynamicStrides,
                    AMD64MacroAssembler.ExtendMode extendMode) {
        return movParamsAndCreate(tool, null, null, null, null,
                        runtimeCheckedCPUFeatures,
                        result,
                        arrayA, offsetA,
                        arrayB, offsetB,
                        mask,
                        length,
                        dynamicStrides,
                        extendMode);
    }

    public static AMD64ArrayEqualsOp movParamsAndCreate(LIRGeneratorTool tool,
                    Stride strideA, Stride strideB, Stride strideMask,
                    EnumSet<CPUFeature> runtimeCheckedCPUFeatures,
                    Value result,
                    Value arrayA, Value offsetA,
                    Value arrayB, Value offsetB,
                    Value mask,
                    Value length,
                    AMD64MacroAssembler.ExtendMode extendMode) {
        return movParamsAndCreate(tool, null, strideA, strideB, strideMask, runtimeCheckedCPUFeatures,
                        result,
                        arrayA, offsetA,
                        arrayB, offsetB,
                        mask,
                        length,
                        null,
                        extendMode);
    }

    /**
     * Compares array regions of length {@code length} in {@code arrayA} and {@code arrayB},
     * starting at byte offset {@code offsetA} and {@code offsetB}, respectively. If
     * {@code arrayMask} is not {@code null}, it is OR-ed with {@code arrayA} before comparison with
     * {@code arrayB}.
     *
     * @param elementKind Array element kind. This is only relevant when comparing values of
     *            {@link JavaKind#Float} or {@link JavaKind#Double}. In this case, all strides must
     *            be equal to the element kind's {@link JavaKind#getByteCount() byte count}. If no
     *            floating-point comparison should be done, this parameter should be set to
     *            {@link JavaKind#Byte}.
     * @param strideA element size of {@code arrayA}. May be {@code null} if {@code dynamicStrides}
     *            is used.
     * @param strideB element size of {@code arrayB}. May be {@code null} if {@code dynamicStrides}
     *            is used.
     * @param strideMask element size of {@code mask}. May be {@code null} if {@code dynamicStrides}
     *            is used.
     * @param offsetA byte offset to be added to {@code arrayA}.
     * @param offsetB byte offset to be added to {@code arrayB}.
     * @param length length (number of array slots in respective array's stride) of the region to
     *            compare.
     * @param dynamicStrides dynamic stride dispatch as described in {@link StrideUtil}.
     * @param extendMode integer extension mode for the array with the smaller element size.
     */
    public static AMD64ArrayEqualsOp movParamsAndCreate(LIRGeneratorTool tool,
                    JavaKind elementKind,
                    Stride strideA, Stride strideB, Stride strideMask,
                    EnumSet<CPUFeature> runtimeCheckedCPUFeatures,
                    Value result,
                    Value arrayA, Value offsetA,
                    Value arrayB, Value offsetB, Value arrayMask, Value length, Value dynamicStrides,
                    AMD64MacroAssembler.ExtendMode extendMode) {
        RegisterValue regArrayA = REG_ARRAY_A.asValue(arrayA.getValueKind());
        RegisterValue regOffsetA = REG_OFFSET_A.asValue(offsetA.getValueKind());
        RegisterValue regArrayB = REG_ARRAY_B.asValue(arrayB.getValueKind());
        RegisterValue regOffsetB = REG_OFFSET_B.asValue(offsetB.getValueKind());
        Value regMask = arrayMask == null ? Value.ILLEGAL : REG_MASK.asValue(arrayMask.getValueKind());
        RegisterValue regLength = REG_LENGTH.asValue(length.getValueKind());
        Value regStride = dynamicStrides == null ? Value.ILLEGAL : REG_STRIDE.asValue(dynamicStrides.getValueKind());

        tool.emitConvertNullToZero(regArrayA, arrayA);
        tool.emitConvertNullToZero(regArrayB, arrayB);
        tool.emitMove(regLength, length);
        tool.emitMove(regOffsetA, offsetA);
        tool.emitMove(regOffsetB, offsetB);
        if (arrayMask != null) {
            tool.emitMove((RegisterValue) regMask, arrayMask);
        }
        if (dynamicStrides != null) {
            tool.emitMove((RegisterValue) regStride, dynamicStrides);
        }
        return new AMD64ArrayEqualsOp(tool, elementKind == null ? JavaKind.Byte : elementKind, strideA, strideB, strideMask,
                        runtimeCheckedCPUFeatures, result, regArrayA, regOffsetA, regArrayB, regOffsetB, regMask, regLength, regStride,
                        extendMode, LIRValueUtil.isJavaConstant(length) ? LIRValueUtil.asJavaConstant(length).asInt() : -1);
    }

    private static boolean canGenerateConstantLengthCompare(TargetDescription target, EnumSet<CPUFeature> runtimeCheckedCPUFeatures,
                    JavaKind elementKind, Stride strideA, Stride strideB, int constantLength, Value stride, AVXSize vectorSize) {
        return isIllegal(stride) && constantLength >= 0 && canGenerateConstantLengthCompare(target, runtimeCheckedCPUFeatures, elementKind, strideA, strideB, constantLength, vectorSize);
    }

    public static boolean canGenerateConstantLengthCompare(TargetDescription target, EnumSet<CPUFeature> runtimeCheckedCPUFeatures,
                    JavaKind elementKind, Stride strideA, Stride strideB, int constantLength, AVXSize vectorSize) {
        int elementSize = Math.max(strideA.value, strideB.value);
        int minVectorSize = AVXSize.XMM.getBytes() / elementSize;
        int maxVectorSize = vectorSize.getBytes() / elementSize;
        return supports(target, runtimeCheckedCPUFeatures, CPUFeature.SSE4_1) && elementKind.isNumericInteger() && (strideA == strideB || minVectorSize <= constantLength) &&
                        constantLength <= maxVectorSize * 2;
    }

    private boolean isLengthConstant() {
        return constLength >= 0;
    }

    private int constantLength() {
        assert isLengthConstant();
        return constLength;
    }

    private boolean withMask() {
        return !isIllegal(arrayMaskValue);
    }

    private boolean withDynamicStrides() {
        return !isIllegal(dynamicStridesValue);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register result = asRegister(resultValue);

        Label done = new Label();

        Register arrayA = asRegister(arrayAValue);
        Register arrayB = asRegister(arrayBValue);
        Register mask = withMask() ? asRegister(arrayMaskValue) : null;
        // Load array base addresses.
        masm.addq(arrayA, asRegister(offsetAValue));
        masm.addq(arrayB, asRegister(offsetBValue));
        if (canGenerateConstantLengthCompare) {
            emitConstantLengthArrayCompareBytes(masm, result);
        } else {
            Register length = asRegister(lengthValue);
            Register tmp = asRegister(offsetAValueTemp);
            if (withDynamicStrides()) {
                assert elementKind.isNumericInteger();
                Label[] variants = new Label[9];
                for (int i = 0; i < variants.length; i++) {
                    variants[i] = new Label();
                }
                AMD64ControlFlow.RangeTableSwitchOp.emitJumpTable(crb, masm, tmp, asRegister(dynamicStridesValue), 0, 8, Arrays.stream(variants));

                // use the 1-byte-1-byte stride variant for the 2-2 and 4-4 cases by simply shifting
                // the length
                masm.align(preferredBranchTargetAlignment(crb));
                masm.bind(variants[StrideUtil.getDirectStubCallIndex(Stride.S4, Stride.S4)]);
                masm.shll(length, 1);
                masm.align(preferredBranchTargetAlignment(crb));
                masm.bind(variants[StrideUtil.getDirectStubCallIndex(Stride.S2, Stride.S2)]);
                masm.shll(length, 1);
                masm.align(preferredBranchTargetAlignment(crb));
                masm.bind(variants[StrideUtil.getDirectStubCallIndex(Stride.S1, Stride.S1)]);
                emitArrayCompare(crb, masm, Stride.S1, Stride.S1, Stride.S1, result, arrayA, arrayB, mask, length, done, false);
                masm.jmp(done);

                for (Stride strideA : new Stride[]{Stride.S1, Stride.S2, Stride.S4}) {
                    for (Stride strideB : new Stride[]{Stride.S1, Stride.S2, Stride.S4}) {
                        if (strideA.log2 == strideB.log2 || !withMask() && strideA.log2 < strideB.log2) {
                            continue;
                        }
                        if (!withMask()) {
                            masm.align(preferredBranchTargetAlignment(crb));
                            // use the same implementation for e.g. stride 1-2 and 2-1 by swapping
                            // the arguments in one variant
                            masm.bind(variants[StrideUtil.getDirectStubCallIndex(strideB, strideA)]);
                            masm.movq(tmp, arrayA);
                            masm.movq(arrayA, arrayB);
                            masm.movq(arrayB, tmp);
                        }
                        masm.align(crb.target.wordSize * 2);
                        masm.bind(variants[StrideUtil.getDirectStubCallIndex(strideA, strideB)]);
                        emitArrayCompare(crb, masm, strideA, strideB, strideB, result, arrayA, arrayB, mask, length, done, false);
                        masm.jmp(done);
                    }
                }
            } else {
                emitArrayCompare(crb, masm, argStrideA, argStrideB, argStrideMask, result, arrayA, arrayB, mask, length, done, true);
            }
        }
        masm.bind(done);
    }

    private static void emitReturnValue(AMD64MacroAssembler masm, Register result, Label trueLabel, Label falseLabel, Label done, boolean shortJmp) {
        // Return true
        masm.bind(trueLabel);
        masm.movl(result, 1);
        masm.jmp(done, shortJmp);

        // Return false
        masm.bind(falseLabel);
        masm.xorl(result, result);
    }

    private void emitArrayCompare(CompilationResultBuilder crb, AMD64MacroAssembler masm,
                    Stride strideA, Stride strideB, Stride strideMask,
                    Register result, Register array1, Register array2, Register mask, Register length,
                    Label done, boolean shortJmp) {
        Label trueLabel = new Label();
        Label falseLabel = new Label();
        masm.movl(result, length);
        if (masm.supports(CPUFeature.SSE4_1)) {
            emitVectorCompare(crb, masm, strideA, strideB, strideMask, result, array1, array2, mask, length, trueLabel, falseLabel);
        }
        if (strideA == strideB && strideA == strideMask) {
            emit8ByteCompare(crb, masm, strideA, strideB, strideMask, result, array1, array2, mask, length, trueLabel, falseLabel);
            emitTailCompares(masm, strideA, strideB, strideMask, result, array1, array2, mask, length, trueLabel, falseLabel);
        } else {
            emitDifferentKindsElementWiseCompare(crb, masm, strideA, strideB, strideMask, result, array1, array2, mask, length, trueLabel, falseLabel);
        }
        emitReturnValue(masm, result, trueLabel, falseLabel, done, shortJmp);
    }

    /**
     * Emits code that uses SSE4.1/AVX1 128-bit (16-byte) or AVX2 256-bit (32-byte) vector compares.
     */
    private void emitVectorCompare(CompilationResultBuilder crb, AMD64MacroAssembler masm,
                    Stride strideA, Stride strideB, Stride strideMask,
                    Register result, Register arrayA, Register arrayB, Register mask, Register length,
                    Label trueLabel, Label falseLabel) {
        assert masm.supports(CPUFeature.SSE4_1);
        Stride maxStride = Stride.max(strideA, strideB);

        Register vector1 = asRegister(vectorTemp[0]);
        Register vector2 = asRegister(vectorTemp[1]);
        Register vector3 = withMask() ? asRegister(vectorTemp[2]) : null;

        int elementsPerVector = getElementsPerVector(vectorSize, maxStride);

        Label loop = new Label();
        Label compareTail = new Label();

        boolean requiresNaNCheck = elementKind.isNumericFloat();
        Label loopCheck = new Label();
        Label nanCheck = new Label();

        // Compare 16-byte vectors
        masm.andl(result, elementsPerVector - 1); // tail count
        masm.andlAndJcc(length, ~(elementsPerVector - 1), ConditionFlag.Zero, compareTail, false);

        masm.leaq(arrayA, new AMD64Address(arrayA, length, strideA, 0));
        masm.leaq(arrayB, new AMD64Address(arrayB, length, strideB, 0));
        if (withMask()) {
            masm.leaq(mask, new AMD64Address(mask, length, strideMask, 0));
        }
        masm.negq(length);

        // Align the main loop
        masm.align(preferredLoopAlignment(crb));
        masm.bind(loop);
        pmovSZx(masm, vectorSize, vector1, maxStride, arrayA, length, 0, strideA);
        pmovSZx(masm, vectorSize, vector2, maxStride, arrayB, length, 0, strideB);
        if (withMask()) {
            pmovSZx(masm, vectorSize, vector3, maxStride, mask, length, 0, strideMask);
            masm.por(vectorSize, vector1, vector3);
        }
        emitVectorCmp(masm, vector1, vector2, vectorSize);
        masm.jcc(ConditionFlag.NotZero, requiresNaNCheck ? nanCheck : falseLabel, requiresNaNCheck);

        masm.bind(loopCheck);
        masm.addqAndJcc(length, elementsPerVector, ConditionFlag.NotZero, loop, true);

        masm.testlAndJcc(result, result, ConditionFlag.Zero, trueLabel, false);

        if (requiresNaNCheck) {
            assert !withMask();
            Label unalignedCheck = new Label();
            masm.jmpb(unalignedCheck);
            masm.bind(nanCheck);
            emitFloatCompareWithinRange(crb, masm, strideA, strideB, arrayA, arrayB, length, 0, falseLabel, elementsPerVector);
            masm.jmpb(loopCheck);
            masm.bind(unalignedCheck);
        }

        /*
         * Compare the remaining bytes with an unaligned memory load aligned to the end of the
         * array.
         */
        pmovSZx(masm, vectorSize, vector1, maxStride, arrayA, result, -vectorSize.getBytes(), strideA);
        pmovSZx(masm, vectorSize, vector2, maxStride, arrayB, result, -vectorSize.getBytes(), strideB);
        if (withMask()) {
            pmovSZx(masm, vectorSize, vector3, maxStride, mask, result, -vectorSize.getBytes(), strideMask);
            masm.por(vectorSize, vector1, vector3);
        }
        emitVectorCmp(masm, vector1, vector2, vectorSize);
        if (requiresNaNCheck) {
            assert !withMask();
            masm.jcc(ConditionFlag.Zero, trueLabel);
            emitFloatCompareWithinRange(crb, masm, strideA, strideB, arrayA, arrayB, result, -vectorSize.getBytes(), falseLabel, elementsPerVector);
        } else {
            masm.jcc(ConditionFlag.NotZero, falseLabel);
        }
        masm.jmp(trueLabel);

        masm.bind(compareTail);
        masm.movl(length, result);
    }

    private static int getElementsPerVector(AVXSize vSize, Stride maxStride) {
        return vSize.getBytes() >> maxStride.log2;
    }

    private void pmovSZx(AMD64MacroAssembler asm, AVXSize size, Register dst, Stride maxStride, Register src, int displacement, Stride stride) {
        pmovSZx(asm, size, dst, maxStride, src, Register.None, displacement, stride);
    }

    private void pmovSZx(AMD64MacroAssembler asm, AVXSize size, Register dst, Stride maxStride, Register src, Register index, int displacement, Stride stride) {
        asm.pmovSZx(size, extendMode, dst, maxStride, src, stride, index, displacement);
    }

    private static void emitVectorCmp(AMD64MacroAssembler masm, Register vector1, Register vector2, AVXSize size) {
        masm.pxor(size, vector1, vector2);
        masm.ptest(size, vector1);
    }

    /**
     * Vector size used in {@link #emit8ByteCompare}.
     */
    private static final int VECTOR_SIZE = 8;

    /**
     * Emits code that uses 8-byte vector compares.
     */
    private void emit8ByteCompare(CompilationResultBuilder crb, AMD64MacroAssembler masm,
                    Stride strideA, Stride strideB, Stride strideMask,
                    Register result, Register arrayA, Register arrayB, Register mask, Register length, Label trueLabel, Label falseLabel) {
        assert strideA == strideB && strideA == strideMask;
        Label loop = new Label();
        Label compareTail = new Label();

        int elementsPerVector = 8 >> strideA.log2;

        boolean requiresNaNCheck = elementKind.isNumericFloat();
        Label loopCheck = new Label();
        Label nanCheck = new Label();

        Register temp = asRegister(offsetAValueTemp);

        masm.andl(result, elementsPerVector - 1); // tail count
        masm.andlAndJcc(length, ~(elementsPerVector - 1), ConditionFlag.Zero, compareTail, false);

        masm.leaq(arrayA, new AMD64Address(arrayA, length, strideA, 0));
        masm.leaq(arrayB, new AMD64Address(arrayB, length, strideB, 0));
        if (withMask()) {
            masm.leaq(mask, new AMD64Address(mask, length, strideMask, 0));
        }
        masm.negq(length);

        // Align the main loop
        masm.align(preferredLoopAlignment(crb));
        masm.bind(loop);
        masm.movq(temp, new AMD64Address(arrayA, length, strideA, 0));
        if (withMask()) {
            masm.orq(temp, new AMD64Address(mask, length, strideMask, 0));
        }
        masm.cmpqAndJcc(temp, new AMD64Address(arrayB, length, strideB, 0), ConditionFlag.NotEqual, requiresNaNCheck ? nanCheck : falseLabel, requiresNaNCheck);

        masm.bind(loopCheck);
        masm.addqAndJcc(length, elementsPerVector, ConditionFlag.NotZero, loop, true);

        masm.testlAndJcc(result, result, ConditionFlag.Zero, trueLabel, false);

        if (requiresNaNCheck) {
            assert !withMask();
            // NaN check is slow path and hence placed outside of the main loop.
            Label unalignedCheck = new Label();
            masm.jmpb(unalignedCheck);
            masm.bind(nanCheck);
            // At most two iterations, unroll in the emitted code.
            for (int offset = 0; offset < VECTOR_SIZE; offset += strideA.value) {
                emitFloatCompare(masm, strideA, strideB, arrayA, arrayB, length, offset, falseLabel, strideA.value == VECTOR_SIZE);
            }
            masm.jmpb(loopCheck);
            masm.bind(unalignedCheck);
        }

        /*
         * Compare the remaining bytes with an unaligned memory load aligned to the end of the
         * array.
         */
        masm.movq(temp, new AMD64Address(arrayA, result, strideA, -VECTOR_SIZE));
        if (requiresNaNCheck) {
            assert !withMask();
            masm.cmpqAndJcc(temp, new AMD64Address(arrayB, result, strideB, -VECTOR_SIZE), ConditionFlag.Equal, trueLabel, false);
            // At most two iterations, unroll in the emitted code.
            for (int offset = 0; offset < VECTOR_SIZE; offset += strideA.value) {
                emitFloatCompare(masm, strideA, strideB, arrayA, arrayB, result, -VECTOR_SIZE + offset, falseLabel, strideA.value == VECTOR_SIZE);
            }
        } else {
            if (withMask()) {
                masm.orq(temp, new AMD64Address(mask, result, strideMask, -VECTOR_SIZE));
            }
            masm.cmpqAndJcc(temp, new AMD64Address(arrayB, result, strideB, -VECTOR_SIZE), ConditionFlag.NotEqual, falseLabel, true);
        }
        masm.jmpb(trueLabel);

        masm.bind(compareTail);
        masm.movl(length, result);
    }

    /**
     * Emits code to compare the remaining 1 to 4 bytes.
     */
    private void emitTailCompares(AMD64MacroAssembler masm,
                    Stride strideA, Stride strideB, Stride strideMask,
                    Register result, Register arrayA, Register arrayB, Register mask, Register length, Label trueLabel, Label falseLabel) {
        assert strideA == strideB && strideA == strideMask;
        Label compare2Bytes = new Label();
        Label compare1Byte = new Label();

        Register temp = asRegister(offsetAValueTemp);

        if (strideA.value <= 4) {
            // Compare trailing 4 bytes, if any.
            masm.testlAndJcc(result, 4 >> strideA.log2, ConditionFlag.Zero, compare2Bytes, true);
            masm.movl(temp, new AMD64Address(arrayA, 0));
            if (elementKind == JavaKind.Float) {
                assert !withMask();
                masm.cmplAndJcc(temp, new AMD64Address(arrayB, 0), ConditionFlag.Equal, trueLabel, true);
                emitFloatCompare(masm, strideA, strideB, arrayA, arrayB, Register.None, 0, falseLabel, true);
                masm.jmpb(trueLabel);
            } else {
                if (withMask()) {
                    masm.orl(temp, new AMD64Address(mask, 0));
                }
                masm.cmplAndJcc(temp, new AMD64Address(arrayB, 0), ConditionFlag.NotEqual, falseLabel, true);
            }
            if (strideA.value <= 2) {
                // Move array pointers forward.
                masm.leaq(arrayA, new AMD64Address(arrayA, 4));
                masm.leaq(arrayB, new AMD64Address(arrayB, 4));
                if (withMask()) {
                    masm.leaq(mask, new AMD64Address(mask, 4));
                }
                // Compare trailing 2 bytes, if any.
                masm.bind(compare2Bytes);
                masm.testlAndJcc(result, 2 >> strideA.log2, ConditionFlag.Zero, compare1Byte, true);
                masm.movzwl(temp, new AMD64Address(arrayA, 0));
                if (withMask()) {
                    masm.movzwl(length, new AMD64Address(mask, 0));
                    masm.orl(temp, length);
                }
                masm.movzwl(length, new AMD64Address(arrayB, 0));
                masm.cmplAndJcc(temp, length, ConditionFlag.NotEqual, falseLabel, true);

                // The one-byte tail compare is only required for boolean and byte arrays.
                if (strideA.value <= 1) {
                    // Move array pointers forward.
                    masm.leaq(arrayA, new AMD64Address(arrayA, 2));
                    masm.leaq(arrayB, new AMD64Address(arrayB, 2));
                    if (withMask()) {
                        masm.leaq(mask, new AMD64Address(mask, 2));
                    }
                    // Compare trailing byte, if any.
                    // TODO (yz) this can be optimized, i.e., bind after padding
                    masm.bind(compare1Byte);
                    masm.testlAndJcc(result, 1, ConditionFlag.Zero, trueLabel, true);
                    masm.movzbl(temp, new AMD64Address(arrayA, 0));
                    if (withMask()) {
                        masm.movzbl(length, new AMD64Address(mask, 0));
                        masm.orl(temp, length);
                    }
                    masm.movzbl(length, new AMD64Address(arrayB, 0));
                    masm.cmplAndJcc(temp, length, ConditionFlag.NotEqual, falseLabel, true);
                } else {
                    masm.bind(compare1Byte);
                }
            } else {
                masm.bind(compare2Bytes);
            }
        }
    }

    private void emitDifferentKindsElementWiseCompare(CompilationResultBuilder crb, AMD64MacroAssembler masm,
                    Stride strideA, Stride strideB, Stride strideMask,
                    Register result, Register array1, Register array2, Register mask, Register length, Label trueLabel, Label falseLabel) {
        assert strideA != strideB || strideA != strideMask;
        assert elementKind.isNumericInteger();
        Label loop = new Label();
        Label compareTail = new Label();

        int elementsPerLoopIteration = 2;

        Register tmp1 = asRegister(offsetAValueTemp);
        Register tmp2 = asRegister(offsetBValueTemp);

        masm.andl(result, elementsPerLoopIteration - 1); // tail count
        masm.andlAndJcc(length, ~(elementsPerLoopIteration - 1), ConditionFlag.Zero, compareTail, true);

        masm.leaq(array1, new AMD64Address(array1, length, strideA, 0));
        masm.leaq(array2, new AMD64Address(array2, length, strideB, 0));
        if (withMask()) {
            masm.leaq(mask, new AMD64Address(mask, length, strideMask, 0));
        }
        masm.negq(length);

        // Align the main loop
        masm.align(preferredLoopAlignment(crb));
        masm.bind(loop);
        for (int i = 0; i < elementsPerLoopIteration; i++) {
            masm.movSZx(strideA, extendMode, tmp1, new AMD64Address(array1, length, strideA, i << strideA.log2));
            if (withMask()) {
                masm.movSZx(strideMask, extendMode, tmp2, new AMD64Address(mask, length, strideMask, i << strideMask.log2));
                masm.orq(tmp1, tmp2);
            }
            masm.movSZx(strideB, extendMode, tmp2, new AMD64Address(array2, length, strideB, i << strideB.log2));
            masm.cmpqAndJcc(tmp1, tmp2, ConditionFlag.NotEqual, falseLabel, true);
        }
        masm.addqAndJcc(length, elementsPerLoopIteration, ConditionFlag.NotZero, loop, true);

        masm.bind(compareTail);
        masm.testlAndJcc(result, result, ConditionFlag.Zero, trueLabel, true);
        for (int i = 0; i < elementsPerLoopIteration - 1; i++) {
            masm.movSZx(strideA, extendMode, tmp1, new AMD64Address(array1, length, strideA, 0));
            if (withMask()) {
                masm.movSZx(strideMask, extendMode, tmp2, new AMD64Address(mask, length, strideMask, 0));
                masm.orq(tmp1, tmp2);
            }
            masm.movSZx(strideB, extendMode, tmp2, new AMD64Address(array2, length, strideB, 0));
            masm.cmpqAndJcc(tmp1, tmp2, ConditionFlag.NotEqual, falseLabel, true);
            if (i < elementsPerLoopIteration - 2) {
                masm.incrementq(length, 1);
                masm.decqAndJcc(result, ConditionFlag.Zero, trueLabel, true);
            } else {
                masm.jmpb(trueLabel);
            }
        }
    }

    /**
     * Emits code to fall through if {@code src} is NaN, otherwise jump to {@code branchOrdered}.
     */
    private void emitNaNCheck(AMD64MacroAssembler masm, AMD64Address src, Label branchIfNonNaN) {
        assert elementKind.isNumericFloat();
        Register tempXMMReg = asRegister(tempXMM);
        if (elementKind == JavaKind.Float) {
            masm.movflt(tempXMMReg, src);
        } else {
            masm.movdbl(tempXMMReg, src);
        }
        SSEOp.UCOMIS.emit(masm, elementKind == JavaKind.Float ? OperandSize.PS : OperandSize.PD, tempXMMReg, tempXMMReg);
        masm.jcc(ConditionFlag.NoParity, branchIfNonNaN);
    }

    /**
     * Emits code to compare if two floats are bitwise equal or both NaN.
     */
    private void emitFloatCompare(AMD64MacroAssembler masm,
                    Stride strideA, Stride strideB,
                    Register arrayA, Register arrayB, Register index, int offset, Label falseLabel,
                    boolean skipBitwiseCompare) {
        AMD64Address address1 = new AMD64Address(arrayA, index, strideA, offset);
        AMD64Address address2 = new AMD64Address(arrayB, index, strideB, offset);

        Label bitwiseEqual = new Label();

        if (!skipBitwiseCompare) {
            // Bitwise compare
            Register temp = asRegister(offsetAValueTemp);

            if (elementKind == JavaKind.Float) {
                masm.movl(temp, address1);
                masm.cmplAndJcc(temp, address2, ConditionFlag.Equal, bitwiseEqual, true);
            } else {
                masm.movq(temp, address1);
                masm.cmpqAndJcc(temp, address2, ConditionFlag.Equal, bitwiseEqual, true);
            }
        }

        emitNaNCheck(masm, address1, falseLabel);
        emitNaNCheck(masm, address2, falseLabel);

        masm.bind(bitwiseEqual);
    }

    /**
     * Emits code to compare float equality within a range.
     */
    private void emitFloatCompareWithinRange(CompilationResultBuilder crb, AMD64MacroAssembler masm,
                    Stride strideA, Stride strideB, Register arrayA, Register arrayB, Register index, int offset, Label falseLabel, int range) {
        assert elementKind.isNumericFloat();
        Label loop = new Label();
        Register i = asRegister(offsetBValueTemp);

        masm.movq(i, range);
        masm.negq(i);
        // Align the main loop
        masm.align(preferredLoopAlignment(crb));
        masm.bind(loop);
        emitFloatCompare(masm, strideA, strideB, arrayA, arrayB, index, offset, falseLabel, range == 1);
        masm.incrementq(index, 1);
        masm.incqAndJcc(i, ConditionFlag.NotZero, loop, true);
        // Floats within the range are equal, revert change to the register index
        masm.subq(index, range);
    }

    /**
     * Emits specialized assembly for checking equality of memory regions of constant length.
     */
    private void emitConstantLengthArrayCompareBytes(AMD64MacroAssembler asm, Register result) {
        asm.movl(result, 1);
        if (constantLength() == 0) {
            // do nothing
            return;
        }
        Stride maxStride = Stride.max(argStrideA, argStrideB);
        Register arrayA = asRegister(arrayAValue);
        Register arrayB = asRegister(arrayBValue);
        Register mask = withMask() ? asRegister(arrayMaskValue) : null;
        Register vector1 = asRegister(vectorTemp[0]);
        Register vector2 = asRegister(vectorTemp[1]);
        Register vector3 = asRegister(vectorTemp[2]);
        Register vector4 = withMask() ? asRegister(vectorTemp[3]) : null;
        Register tmp = asRegister(lengthValue);
        GraalError.guarantee(constantLength() <= getElementsPerVector(vectorSize, maxStride) * 2, "constant length too long for specialized arrayEquals!");
        AVXSize vSize = vectorSize;
        if (constantLength() < getElementsPerVector(vectorSize, maxStride)) {
            vSize = AVXSize.XMM;
        }
        int elementsPerVector = getElementsPerVector(vSize, maxStride);
        if (elementsPerVector > constantLength()) {
            assert argStrideA == argStrideB && argStrideA == argStrideMask;
            int byteLength = constantLength() << argStrideA.log2;
            // array is shorter than any vector register, use regular XOR instructions
            Stride movStride = (byteLength < 2) ? Stride.S1 : ((byteLength < 4) ? Stride.S2 : ((byteLength < 8) ? Stride.S4 : Stride.S8));
            asm.movSZx(movStride, extendMode, tmp, new AMD64Address(arrayA));
            if (withMask()) {
                emitOrBytes(asm, tmp, new AMD64Address(mask, 0), movStride);
            }
            if (byteLength > movStride.value) {
                emitXorBytes(asm, tmp, new AMD64Address(arrayB), movStride);
                asm.movSZx(movStride, extendMode, arrayA, new AMD64Address(arrayA, byteLength - movStride.value));
                if (withMask()) {
                    emitOrBytes(asm, arrayA, new AMD64Address(mask, byteLength - movStride.value), movStride);
                }
                emitXorBytes(asm, arrayA, new AMD64Address(arrayB, byteLength - movStride.value), movStride);
                asm.xorq(arrayB, arrayB);
                asm.orq(tmp, arrayA);
                asm.cmovl(AMD64Assembler.ConditionFlag.NotZero, result, arrayB);
            } else {
                asm.xorq(arrayA, arrayA);
                emitXorBytes(asm, tmp, new AMD64Address(arrayB), movStride);
                asm.cmovl(AMD64Assembler.ConditionFlag.NotZero, result, arrayA);
            }
        } else {
            pmovSZx(asm, vSize, vector1, maxStride, arrayA, 0, argStrideA);
            pmovSZx(asm, vSize, vector2, maxStride, arrayB, 0, argStrideB);
            if (withMask()) {
                pmovSZx(asm, vSize, vector4, maxStride, mask, 0, argStrideMask);
                asm.por(vSize, vector1, vector4);
            }
            asm.pxor(vSize, vector1, vector2);
            if (constantLength() > elementsPerVector) {
                int endOffset = (constantLength() << maxStride.log2) - vSize.getBytes();
                pmovSZx(asm, vSize, vector3, maxStride, arrayA, endOffset, argStrideA);
                pmovSZx(asm, vSize, vector2, maxStride, arrayB, endOffset, argStrideB);
                if (withMask()) {
                    pmovSZx(asm, vSize, vector4, maxStride, mask, endOffset, argStrideMask);
                    asm.por(vSize, vector3, vector4);
                }
                asm.pxor(vSize, vector3, vector2);
                asm.por(vSize, vector1, vector3);
            }
            asm.xorq(arrayA, arrayA);
            asm.ptest(vSize, vector1);
            asm.cmovl(AMD64Assembler.ConditionFlag.NotZero, result, arrayA);
        }
    }

    private static void emitOrBytes(AMD64MacroAssembler asm, Register dst, AMD64Address src, Stride stride) {
        OperandSize opSize = getOperandSize(stride);
        OR.getRMOpcode(opSize).emit(asm, opSize, dst, src);
    }

    private static void emitXorBytes(AMD64MacroAssembler asm, Register dst, AMD64Address src, Stride stride) {
        OperandSize opSize = getOperandSize(stride);
        XOR.getRMOpcode(opSize).emit(asm, opSize, dst, src);
    }

    private static OperandSize getOperandSize(Stride size) {
        switch (size) {
            case S1:
                return OperandSize.BYTE;
            case S2:
                return OperandSize.WORD;
            case S4:
                return OperandSize.DWORD;
            case S8:
                return OperandSize.QWORD;
            default:
                throw new IllegalStateException();
        }
    }

}
