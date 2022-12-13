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
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isIllegal;
import static org.graalvm.compiler.asm.amd64.AVXKind.AVXSize.QWORD;
import static org.graalvm.compiler.asm.amd64.AVXKind.AVXSize.XMM;
import static org.graalvm.compiler.asm.amd64.AVXKind.AVXSize.YMM;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import java.util.Arrays;
import java.util.EnumSet;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.asm.amd64.AVXKind.AVXSize;
import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.core.common.StrideUtil;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

/**
 * Emits code to compare two array regions lexicographically, using SIMD instructions where
 * possible.
 *
 * Supported element kinds:
 * <ul>
 * <li>byte+byte</li>
 * <li>char+char</li>
 * <li>int+int</li>
 * <li>char+byte</li>
 * <li>int+byte</li>
 * <li >int+char</li>
 * </ul>
 * If the second element kind is smaller than the first, elements are either sign or zero-extended
 * to fit the first element kind, depending on the {@code extendMode} parameter.
 */
@Opcode("ARRAY_REGION_COMPARE")
public final class AMD64ArrayRegionCompareToOp extends AMD64ComplexVectorOp {
    public static final LIRInstructionClass<AMD64ArrayRegionCompareToOp> TYPE = LIRInstructionClass.create(AMD64ArrayRegionCompareToOp.class);

    private static final Register REG_ARRAY_A = rsi;
    private static final Register REG_OFFSET_A = rax;
    private static final Register REG_ARRAY_B = rdi;
    private static final Register REG_OFFSET_B = rcx;
    private static final Register REG_LENGTH = rdx;
    private static final Register REG_STRIDE = r8;

    private static final int ONES_16 = 0xffff;
    private static final int ONES_32 = 0xffffffff;

    private final Stride argStrideA;
    private final Stride argStrideB;
    private final AMD64MacroAssembler.ExtendMode extendMode;

    @Def({REG}) private Value resultValue;
    @Use({REG}) private Value arrayAValue;
    @Use({REG}) private Value offsetAValue;
    @Use({REG}) private Value arrayBValue;
    @Use({REG, ILLEGAL}) private Value offsetBValue;
    @Use({REG}) private Value lengthValue;
    @Use({REG, ILLEGAL}) private Value dynamicStridesValue;

    @Temp({REG}) private Value arrayAValueTemp;
    @Temp({REG}) private Value offsetAValueTemp;
    @Temp({REG}) private Value arrayBValueTemp;
    @Temp({REG, ILLEGAL}) private Value offsetBValueTemp;
    @Temp({REG}) private Value lengthValueTemp;
    @Temp({REG, ILLEGAL}) private Value dynamicStridesValueTemp;

    @Temp({REG}) Value[] vectorTemp;

    private AMD64ArrayRegionCompareToOp(LIRGeneratorTool tool, Stride strideA, Stride strideB,
                    EnumSet<CPUFeature> runtimeCheckedCPUFeatures, Value result, Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value length, Value dynamicStrides,
                    AMD64MacroAssembler.ExtendMode extendMode) {
        super(TYPE, tool, runtimeCheckedCPUFeatures, YMM);
        this.extendMode = extendMode;
        if (strideA == null) {
            this.argStrideA = null;
            this.argStrideB = null;
        } else {
            GraalError.guarantee(strideA.value <= 4, "unsupported strideA");
            GraalError.guarantee(strideB.value <= 4, "unsupported strideB");
            this.argStrideA = strideA;
            this.argStrideB = strideB;
        }
        this.resultValue = result;
        this.arrayAValue = this.arrayAValueTemp = arrayA;
        this.offsetAValue = this.offsetAValueTemp = offsetA;
        this.arrayBValue = this.arrayBValueTemp = arrayB;
        this.offsetBValue = this.offsetBValueTemp = offsetB;
        this.lengthValue = this.lengthValueTemp = length;
        this.dynamicStridesValue = this.dynamicStridesValueTemp = dynamicStrides;

        this.vectorTemp = allocateVectorRegisters(tool, JavaKind.Byte, isVectorCompareSupported(tool.target(), runtimeCheckedCPUFeatures, argStrideA, argStrideB) ? 4 : 0);
    }

    /**
     * Compares array regions of length {@code length} in {@code arrayA} and {@code arrayB},
     * starting at byte offset {@code offsetA} and {@code offsetB}, respectively.
     *
     * @param strideA element size of {@code arrayA}. May be one of {@link JavaKind#Byte},
     *            {@link JavaKind#Char} or {@link JavaKind#Int}. {@code strideA} must be greater
     *            than or equal to {@code strideB}!
     * @param strideB element size of {@code arrayB}. May be one of {@link JavaKind#Byte},
     *            {@link JavaKind#Char} or {@link JavaKind#Int}. {@code strideB} must be less than
     *            or equal to {@code strideA}!
     * @param offsetA byte offset to be added to {@code arrayA}. Must include the array base offset!
     * @param offsetB byte offset to be added to {@code arrayB}. Must include the array base offset!
     * @param length length (number of array slots in respective array's stride) of the region to
     *            compare.
     * @param dynamicStrides dynamic stride dispatch as described in {@link StrideUtil}.
     * @param extendMode integer extension mode for {@code arrayB}.
     */
    public static AMD64ArrayRegionCompareToOp movParamsAndCreate(LIRGeneratorTool tool, Stride strideA, Stride strideB,
                    EnumSet<CPUFeature> runtimeCheckedCPUFeatures,
                    Value result, Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value length, Value dynamicStrides,
                    AMD64MacroAssembler.ExtendMode extendMode) {
        RegisterValue regArrayA = REG_ARRAY_A.asValue(arrayA.getValueKind());
        RegisterValue regOffsetA = REG_OFFSET_A.asValue(offsetA.getValueKind());
        RegisterValue regArrayB = REG_ARRAY_B.asValue(arrayB.getValueKind());
        RegisterValue regOffsetB = REG_OFFSET_B.asValue(offsetB.getValueKind());
        RegisterValue regLength = REG_LENGTH.asValue(length.getValueKind());
        Value regStride = dynamicStrides == null ? Value.ILLEGAL : REG_STRIDE.asValue(length.getValueKind());
        tool.emitConvertNullToZero(regArrayA, arrayA);
        tool.emitMove(regOffsetA, offsetA);
        tool.emitConvertNullToZero(regArrayB, arrayB);
        tool.emitMove(regOffsetB, offsetB);
        tool.emitMove(regLength, length);
        if (dynamicStrides != null) {
            tool.emitMove((AllocatableValue) regStride, dynamicStrides);
        }
        return new AMD64ArrayRegionCompareToOp(tool, strideA, strideB, runtimeCheckedCPUFeatures, result, regArrayA, regOffsetA, regArrayB, regOffsetB, regLength, regStride, extendMode);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register result = asRegister(resultValue);
        Register arrayA = asRegister(arrayAValue);
        Register arrayB = asRegister(arrayBValue);
        Register length = asRegister(lengthValue);
        Register tmp1 = asRegister(offsetAValue);
        Register tmp2 = asRegister(offsetBValue);

        // add byte offset to array pointers
        masm.leaq(arrayA, new AMD64Address(arrayA, asRegister(offsetAValue), Stride.S1));
        masm.leaq(arrayB, new AMD64Address(arrayB, asRegister(offsetBValue), Stride.S1));

        if (isIllegal(dynamicStridesValue)) {
            emitArrayCompare(crb, masm, argStrideA, argStrideB, result, arrayA, arrayB, length, tmp1, tmp2);
        } else {
            masm.xorq(tmp2, tmp2);
            Label[] variants = new Label[9];
            Label done = new Label();
            for (int i = 0; i < variants.length; i++) {
                variants[i] = new Label();
            }
            AMD64ControlFlow.RangeTableSwitchOp.emitJumpTable(crb, masm, tmp1, asRegister(dynamicStridesValue), 0, 8, Arrays.stream(variants));
            for (Stride strideA : new Stride[]{Stride.S1, Stride.S2, Stride.S4}) {
                for (Stride strideB : new Stride[]{Stride.S1, Stride.S2, Stride.S4}) {
                    masm.align(preferredBranchTargetAlignment(crb));
                    masm.bind(variants[StrideUtil.getDirectStubCallIndex(strideA, strideB)]);
                    emitArrayCompare(crb, masm, strideA, strideB, result, arrayA, arrayB, length, tmp1, tmp2);
                    masm.jmp(done);
                }
            }
            masm.bind(done);
        }
    }

    private static boolean isVectorCompareSupported(TargetDescription target, EnumSet<CPUFeature> runtimeCheckedCPUFeatures, Stride strideA, Stride strideB) {
        // if strides are not equal, we need the SSE4.1 pmovzx instruction, otherwise SSE2 is
        // enough.
        return strideA == strideB || supports(target, runtimeCheckedCPUFeatures, CPUFeature.SSE4_1);
    }

    private void emitArrayCompare(CompilationResultBuilder crb, AMD64MacroAssembler masm,
                    Stride strideA, Stride strideB,
                    Register result, Register arrayA, Register arrayB, Register length, Register tmp1, Register tmp2) {
        Label returnLabel = new Label();
        if (isVectorCompareSupported(crb.target, runtimeCheckedCPUFeatures, strideA, strideB)) {
            emitVectorLoop(crb, masm, strideA, strideB, result, arrayA, arrayB, length, tmp1, tmp2, returnLabel);
        }
        emitScalarLoop(crb, masm, strideA, strideB, result, arrayA, arrayB, length, tmp1, returnLabel);
        masm.bind(returnLabel);
    }

    /**
     * Emits code that uses SSE2/SSE4.1/AVX1 128-bit (16-byte) or AVX2 256-bit (32-byte) vector
     * compares. Underlying algorithm: check vectors for equality with PCMPEQ, identify the first
     * differing elements with PMOVMSK and BSF and return their scalar difference.
     */
    private void emitVectorLoop(CompilationResultBuilder crb, AMD64MacroAssembler masm, Stride strideA, Stride strideB,
                    Register result, Register arrayA, Register arrayB, Register length, Register tmp1, Register tmp2, Label returnLabel) {
        Stride maxStride = Stride.max(strideA, strideB);

        Register vector1 = asRegister(vectorTemp[0]);
        Register vector2 = asRegister(vectorTemp[1]);
        Register vector3 = asRegister(vectorTemp[2]);
        Register vector4 = asRegister(vectorTemp[3]);
        int elementsPerVector = getElementsPerVector(vectorSize, maxStride);

        Label loop = new Label();
        Label qwordTail = new Label();
        Label scalarTail = new Label();
        Label tail = new Label();
        Label diffFound = new Label();

        // fast path: check first element as scalar
        masm.movSZx(strideA, extendMode, result, new AMD64Address(arrayA));
        masm.movSZx(strideB, extendMode, tmp1, new AMD64Address(arrayB));
        masm.subqAndJcc(result, tmp1, ConditionFlag.NotZero, returnLabel, false);
        masm.movl(result, length);

        // Compare XMM/YMM vectors
        masm.andl(result, elementsPerVector - 1); // tail count
        masm.andlAndJcc(length, -elementsPerVector, ConditionFlag.Zero, tail, false);

        masm.leaq(arrayA, new AMD64Address(arrayA, length, strideA));
        masm.leaq(arrayB, new AMD64Address(arrayB, length, strideB));
        masm.negq(length);

        // main loop
        masm.align(preferredLoopAlignment(crb));
        masm.bind(loop);
        // load and extend elements of arrayB to match the stride of arrayA
        masm.pmovSZx(vectorSize, extendMode, vector1, maxStride, arrayA, strideA, length, 0);
        masm.pmovSZx(vectorSize, extendMode, vector2, maxStride, arrayB, strideB, length, 0);
        // compare elements of arrayA and arrayB
        masm.pcmpeq(vectorSize, maxStride, vector1, vector2);
        // convert result to bitmask
        masm.pmovmsk(vectorSize, tmp1, vector1);
        // invert bit mask. if the result is non-zero, compared regions are not equal
        masm.xorlAndJcc(tmp1, vectorSize == XMM ? ONES_16 : ONES_32, ConditionFlag.NotZero, diffFound, true);
        // regions are equal, continue the loop
        masm.addqAndJcc(length, elementsPerVector, ConditionFlag.NotZero, loop, true);

        // special case: if tail count is zero, return
        masm.testlAndJcc(result, result, ConditionFlag.Zero, returnLabel, false);

        // tail: compare the remaining bytes with a vector load aligned to the end of the array.
        masm.pmovSZx(vectorSize, extendMode, vector1, maxStride, arrayA, strideA, result, -vectorSize.getBytes());
        masm.pmovSZx(vectorSize, extendMode, vector2, maxStride, arrayB, strideB, result, -vectorSize.getBytes());
        // adjust "length" for diffFound
        masm.leaq(length, new AMD64Address(length, result, Stride.S1, -elementsPerVector));
        masm.pcmpeq(vectorSize, maxStride, vector1, vector2);
        masm.pmovmsk(vectorSize, tmp1, vector1);
        masm.xorlAndJcc(tmp1, vectorSize == XMM ? ONES_16 : ONES_32, ConditionFlag.NotZero, diffFound, true);
        // all elements are equal, return 0
        masm.xorq(result, result);
        masm.jmp(returnLabel);

        masm.bind(diffFound);
        // different elements found in the current region, find the byte index of the first
        // non-equal elements
        bsfq(masm, tmp2, tmp1);
        if (maxStride.value > 1) {
            // convert byte index to stride
            masm.shrq(tmp2, maxStride.log2);
        }
        // add to current vector loop index
        masm.addq(tmp2, length);
        // load differing elements and return difference
        masm.movSZx(strideA, extendMode, result, new AMD64Address(arrayA, tmp2, strideA));
        masm.movSZx(strideB, extendMode, tmp1, new AMD64Address(arrayB, tmp2, strideB));
        masm.subq(result, tmp1);
        masm.jmp(returnLabel);

        boolean canUseQWORD = !(maxStride == Stride.S4 && Stride.min(strideA, strideB) == Stride.S1);

        masm.bind(tail);
        masm.movl(length, result);

        if (supportsAVX2AndYMM()) {
            // region is too small for YMM vectors, try XMM
            emitVectorizedTail(masm, strideA, strideB,
                            result, arrayA, arrayB, length, tmp1, tmp2, returnLabel, maxStride,
                            vector1, vector2, vector3, vector4, canUseQWORD ? qwordTail : scalarTail, XMM, YMM);
        }

        if (canUseQWORD) {
            masm.bind(qwordTail);
            // region is too small for XMM vectors, try QWORD
            emitVectorizedTail(masm, strideA, strideB,
                            result, arrayA, arrayB, length, tmp1, tmp2, returnLabel, maxStride,
                            vector1, vector2, vector3, vector4, scalarTail, QWORD, XMM);
        }

        // scalar tail loop for regions smaller than QWORD
        masm.bind(scalarTail);
    }

    private void emitVectorizedTail(AMD64MacroAssembler masm, Stride strideA, Stride strideB,
                    Register result, Register arrayA, Register arrayB, Register length, Register tmp1, Register tmp2, Label returnLabel, Stride maxStride,
                    Register vector1, Register vector2, Register vector3, Register vector4, Label nextTail, AVXSize loadSize, AVXSize cmpSize) {
        assert cmpSize.getBytes() == loadSize.getBytes() * 2;
        assert cmpSize == YMM || cmpSize == XMM;
        masm.cmplAndJcc(length, getElementsPerVector(loadSize, maxStride), ConditionFlag.Less, nextTail, false);
        masm.pmovSZx(loadSize, extendMode, vector1, maxStride, arrayA, strideA, 0);
        masm.pmovSZx(loadSize, extendMode, vector2, maxStride, arrayB, strideB, 0);
        masm.pmovSZx(loadSize, extendMode, vector3, maxStride, arrayA, strideA, length, -loadSize.getBytes());
        masm.pmovSZx(loadSize, extendMode, vector4, maxStride, arrayB, strideB, length, -loadSize.getBytes());
        if (cmpSize == YMM) {
            AMD64Assembler.VexRVMIOp.VPERM2I128.emit(masm, cmpSize, vector1, vector3, vector1, 0x02);
            AMD64Assembler.VexRVMIOp.VPERM2I128.emit(masm, cmpSize, vector2, vector4, vector2, 0x02);
        } else {
            masm.movlhps(vector1, vector3);
            masm.movlhps(vector2, vector4);
        }
        masm.pcmpeq(cmpSize, maxStride, vector1, vector2);
        masm.pmovmsk(cmpSize, result, vector1);
        masm.xorlAndJcc(result, cmpSize == XMM ? ONES_16 : ONES_32, ConditionFlag.Zero, returnLabel, false);

        bsfq(masm, tmp2, result);
        if (maxStride.value > 1) {
            // convert byte index to stride
            masm.shrq(tmp2, maxStride.log2);
        }
        masm.leaq(tmp1, new AMD64Address(tmp2, length, Stride.S1, -getElementsPerVector(cmpSize, maxStride)));
        masm.cmpq(tmp2, getElementsPerVector(loadSize, maxStride));
        // add to current vector loop index
        masm.cmovq(ConditionFlag.Greater, tmp2, tmp1);
        // load differing elements and return difference
        masm.movSZx(strideA, extendMode, result, new AMD64Address(arrayA, tmp2, strideA));
        masm.movSZx(strideB, extendMode, tmp1, new AMD64Address(arrayB, tmp2, strideB));
        masm.subq(result, tmp1);
        masm.jmp(returnLabel);
    }

    private static int getElementsPerVector(AVXSize vSize, Stride maxStride) {
        return vSize.getBytes() >> maxStride.log2;
    }

    private void emitScalarLoop(CompilationResultBuilder crb, AMD64MacroAssembler masm, Stride strideA, Stride strideB,
                    Register result, Register arrayA, Register arrayB, Register length, Register tmp, Label returnLabel) {
        Label loop = new Label();

        masm.leaq(arrayA, new AMD64Address(arrayA, length, strideA));
        masm.leaq(arrayB, new AMD64Address(arrayB, length, strideB));
        masm.negq(length);

        masm.align(preferredLoopAlignment(crb));
        masm.bind(loop);
        masm.movSZx(strideA, extendMode, result, new AMD64Address(arrayA, length, strideA));
        masm.movSZx(strideB, extendMode, tmp, new AMD64Address(arrayB, length, strideB));
        masm.subqAndJcc(result, tmp, ConditionFlag.NotZero, returnLabel, true);
        masm.incqAndJcc(length, ConditionFlag.NotZero, loop, true);
    }
}
