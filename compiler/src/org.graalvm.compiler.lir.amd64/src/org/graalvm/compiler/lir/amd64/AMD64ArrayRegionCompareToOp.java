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
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.movSZx;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.pcmpeq;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.pmovSZx;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.pmovmsk;
import static org.graalvm.compiler.asm.amd64.AVXKind.AVXSize.XMM;
import static org.graalvm.compiler.asm.amd64.AVXKind.AVXSize.YMM;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import java.util.Arrays;
import java.util.Objects;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Address.Scale;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.asm.amd64.AVXKind.AVXSize;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64;
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

    private final Scale argScaleA;
    private final Scale argScaleB;
    private final AMD64MacroAssembler.ExtendMode extendMode;

    @Def({REG}) private Value resultValue;
    @Use({REG}) private Value arrayAValue;
    @Use({REG}) private Value offsetAValue;
    @Use({REG}) private Value arrayBValue;
    @Use({REG, ILLEGAL}) private Value offsetBValue;
    @Use({REG}) private Value lengthValue;
    @Use({REG, ILLEGAL}) private Value strideValue;

    @Temp({REG}) private Value arrayAValueTemp;
    @Temp({REG}) private Value offsetAValueTemp;
    @Temp({REG}) private Value arrayBValueTemp;
    @Temp({REG, ILLEGAL}) private Value offsetBValueTemp;
    @Temp({REG}) private Value lengthValueTemp;
    @Temp({REG, ILLEGAL}) private Value strideValueTemp;

    @Temp({REG, ILLEGAL}) private Value vectorTemp1;
    @Temp({REG, ILLEGAL}) private Value vectorTemp2;

    private AMD64ArrayRegionCompareToOp(LIRGeneratorTool tool, JavaKind strideA, JavaKind strideB,
                    Value result, Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value length, Value stride, AMD64MacroAssembler.ExtendMode extendMode) {
        super(TYPE, tool, YMM);
        this.extendMode = extendMode;
        if (strideA == null) {
            this.argScaleA = null;
            this.argScaleB = null;
        } else {
            GraalError.guarantee(strideA == JavaKind.Byte || strideA == JavaKind.Char || strideA == JavaKind.Int, "unsupported strideA");
            GraalError.guarantee(strideB == JavaKind.Byte || strideB == JavaKind.Char || strideB == JavaKind.Int, "unsupported strideB");
            this.argScaleA = Objects.requireNonNull(Scale.fromInt(tool.getProviders().getMetaAccess().getArrayIndexScale(strideA)));
            this.argScaleB = Objects.requireNonNull(Scale.fromInt(tool.getProviders().getMetaAccess().getArrayIndexScale(strideB)));
        }
        this.resultValue = result;
        this.arrayAValue = this.arrayAValueTemp = arrayA;
        this.offsetAValue = this.offsetAValueTemp = offsetA;
        this.arrayBValue = this.arrayBValueTemp = arrayB;
        this.offsetBValue = this.offsetBValueTemp = offsetB;
        this.lengthValue = this.lengthValueTemp = length;
        this.strideValue = this.strideValueTemp = stride;

        // We only need the vector register if we generate SIMD code.
        if (isVectorCompareSupported(tool.target(), argScaleA, argScaleB)) {
            LIRKind lirKind = LIRKind.value(getVectorKind(JavaKind.Byte));
            this.vectorTemp1 = tool.newVariable(lirKind);
            this.vectorTemp2 = tool.newVariable(lirKind);
        } else {
            this.vectorTemp1 = Value.ILLEGAL;
            this.vectorTemp2 = Value.ILLEGAL;
        }
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
     * @param extendMode integer extension mode for {@code arrayB}.
     */
    public static AMD64ArrayRegionCompareToOp movParamsAndCreate(LIRGeneratorTool tool, JavaKind strideA, JavaKind strideB,
                    Value result, Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value length, Value stride, AMD64MacroAssembler.ExtendMode extendMode) {
        RegisterValue regArrayA = REG_ARRAY_A.asValue(arrayA.getValueKind());
        RegisterValue regOffsetA = REG_OFFSET_A.asValue(offsetA.getValueKind());
        RegisterValue regArrayB = REG_ARRAY_B.asValue(arrayB.getValueKind());
        RegisterValue regOffsetB = REG_OFFSET_B.asValue(offsetB.getValueKind());
        RegisterValue regLength = REG_LENGTH.asValue(length.getValueKind());
        Value regStride = stride == null ? Value.ILLEGAL : REG_STRIDE.asValue(length.getValueKind());
        tool.emitConvertNullToZero(regArrayA, arrayA);
        tool.emitMove(regOffsetA, offsetA);
        tool.emitConvertNullToZero(regArrayB, arrayB);
        tool.emitMove(regOffsetB, offsetB);
        tool.emitMove(regLength, length);
        if (stride != null) {
            tool.emitMove((AllocatableValue) regStride, stride);
        }
        return new AMD64ArrayRegionCompareToOp(tool, strideA, strideB, result, regArrayA, regOffsetA, regArrayB, regOffsetB, regLength, regStride, extendMode);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register result = asRegister(resultValue);
        Register arrayA = asRegister(arrayAValue);
        Register arrayB = asRegister(arrayBValue);

        // add byte offset to array pointers
        masm.leaq(arrayA, new AMD64Address(arrayA, asRegister(offsetAValue), Scale.Times1));
        masm.leaq(arrayB, new AMD64Address(arrayB, asRegister(offsetBValue), Scale.Times1));

        // load region length in to registers "length" and "result"
        Register length = asRegister(lengthValue);
        masm.movl(result, length);

        Register tmp1 = asRegister(offsetAValue);
        Register tmp2 = asRegister(offsetBValue);
        if (isIllegal(strideValue)) {
            emitArrayCompare(crb, masm, argScaleA, argScaleB, result, arrayA, arrayB, length, tmp1);
        } else {
            masm.xorq(tmp2, tmp2);
            Label[] variants = new Label[9];
            Label done = new Label();
            for (int i = 0; i < variants.length; i++) {
                variants[i] = new Label();
            }
            AMD64ControlFlow.RangeTableSwitchOp.emitJumpTable(crb, masm, tmp1, asRegister(strideValue), 0, 8, Arrays.stream(variants));
            for (Scale scaleA : new Scale[]{Scale.Times1, Scale.Times2, Scale.Times4}) {
                for (Scale scaleB : new Scale[]{Scale.Times1, Scale.Times2, Scale.Times4}) {
                    if (scaleA.log2 < scaleB.log2) {
                        continue;
                    }
                    if (scaleA.log2 > scaleB.log2) {
                        masm.align(crb.target.wordSize * 2);
                        masm.bind(variants[StrideUtil.getDirectStubCallIndex(scaleB, scaleA)]);
                        masm.movq(tmp1, arrayA);
                        masm.movq(arrayA, arrayB);
                        masm.movq(arrayB, tmp1);
                        masm.incl(tmp2);
                    }
                    masm.align(crb.target.wordSize * 2);
                    masm.bind(variants[StrideUtil.getDirectStubCallIndex(scaleA, scaleB)]);
                    emitArrayCompare(crb, masm, scaleA, scaleB, result, arrayA, arrayB, length, tmp1);
                    if (scaleA.log2 > scaleB.log2) {
                        masm.testlAndJcc(tmp2, tmp2, ConditionFlag.Zero, done, false);
                        masm.negl(result);
                    }
                    masm.jmp(done);
                }
            }
            masm.bind(done);
        }
    }

    private static boolean isVectorCompareSupported(TargetDescription target, Scale scaleA, Scale scaleB) {
        // if strides are not equal, we need the SSE4.1 pmovzx instruction, otherwise SSE2 is
        // enough.
        return scaleA == scaleB || ((AMD64) target.arch).getFeatures().contains(CPUFeature.SSE4_1);
    }

    private void emitArrayCompare(CompilationResultBuilder crb, AMD64MacroAssembler masm,
                    Scale scaleA, Scale scaleB,
                    Register result, Register arrayA, Register arrayB, Register length, Register tmp) {
        Label returnLabel = new Label();
        if (isVectorCompareSupported(crb.target, scaleA, scaleB)) {
            emitVectorLoop(crb, masm, scaleA, scaleB, result, arrayA, arrayB, length, tmp, returnLabel);
        }
        emitScalarLoop(crb, masm, scaleA, scaleB, result, arrayA, arrayB, length, tmp, returnLabel);
        masm.bind(returnLabel);
    }

    /**
     * Emits code that uses SSE2/SSE4.1/AVX1 128-bit (16-byte) or AVX2 256-bit (32-byte) vector
     * compares. Underlying algorithm: check vectors for equality with PCMPEQ, identify the first
     * differing elements with PMOVMSK and BSF and return their scalar difference.
     */
    private void emitVectorLoop(CompilationResultBuilder crb, AMD64MacroAssembler masm, Scale scaleA, Scale scaleB,
                    Register result, Register arrayA, Register arrayB, Register length, Register tmp, Label returnLabel) {
        Scale maxScale = AMD64ArrayEqualsOp.max(scaleA, scaleB);

        Register vector1 = asRegister(vectorTemp1);
        Register vector2 = asRegister(vectorTemp2);
        int elementsPerVector = getElementsPerVector(vectorSize, maxScale);

        Label loop = new Label();
        Label scalarTail = new Label();
        Label xmmTail = new Label();
        Label diffFound = new Label();

        // fast path: check first element as scalar
        masm.testlAndJcc(result, result, ConditionFlag.Zero, returnLabel, false);
        movSZx(masm, scaleA, extendMode, result, new AMD64Address(arrayA));
        movSZx(masm, scaleB, extendMode, tmp, new AMD64Address(arrayB));
        masm.subqAndJcc(result, tmp, ConditionFlag.NotZero, returnLabel, false);
        masm.movl(result, length);

        // Compare XMM/YMM vectors
        masm.andl(result, elementsPerVector - 1); // tail count
        masm.andlAndJcc(length, -elementsPerVector, ConditionFlag.Zero, vectorSize == YMM ? xmmTail : scalarTail, false);

        masm.leaq(arrayA, new AMD64Address(arrayA, length, scaleA));
        masm.leaq(arrayB, new AMD64Address(arrayB, length, scaleB));
        masm.negq(length);

        // main loop
        masm.align(crb.target.wordSize * 2);
        masm.bind(loop);
        // load and extend elements of arrayB to match the stride of arrayA
        pmovSZx(masm, vectorSize, vector1, extendMode, maxScale, arrayA, scaleA, length, 0);
        pmovSZx(masm, vectorSize, vector2, extendMode, maxScale, arrayB, scaleB, length, 0);
        // compare elements of arrayA and arrayB
        pcmpeq(masm, vectorSize, maxScale, vector1, vector2);
        // convert result to bitmask
        pmovmsk(masm, vectorSize, tmp, vector1);
        // invert bit mask. if the result is non-zero, compared regions are not equal
        masm.xorlAndJcc(tmp, vectorSize == XMM ? ONES_16 : ONES_32, ConditionFlag.NotZero, diffFound, true);
        // regions are equal, continue the loop
        masm.addqAndJcc(length, elementsPerVector, ConditionFlag.NotZero, loop, true);

        // special case: if tail count is zero, return
        masm.testlAndJcc(result, result, ConditionFlag.Zero, returnLabel, vectorSize == XMM);

        // tail: compare the remaining bytes with a vector load aligned to the end of the array.
        pmovSZx(masm, vectorSize, vector1, extendMode, maxScale, arrayA, scaleA, result, -vectorSize.getBytes());
        pmovSZx(masm, vectorSize, vector2, extendMode, maxScale, arrayB, scaleB, result, -vectorSize.getBytes());
        masm.addq(length, result); // adjust "length" for diffFound
        pcmpeq(masm, vectorSize, maxScale, vector1, vector2);
        masm.subq(length, elementsPerVector); // adjust "length" for diffFound
        pmovmsk(masm, vectorSize, tmp, vector1);
        masm.xorlAndJcc(tmp, vectorSize == XMM ? ONES_16 : ONES_32, ConditionFlag.NotZero, diffFound, true);
        // all elements are equal, return 0
        masm.xorq(result, result);
        if (vectorSize == XMM) {
            masm.jmpb(returnLabel);
        } else {
            masm.jmp(returnLabel);
        }

        masm.bind(diffFound);
        // different elements found in the current region, find the byte index of the first
        // non-equal elements
        masm.bsfq(tmp, tmp);
        if (maxScale.value > 1) {
            // convert byte index to stride
            masm.shrq(tmp, maxScale.log2);
        }
        // add to current vector loop index
        masm.addq(length, tmp);
        // load differing elements and return difference
        movSZx(masm, scaleA, extendMode, result, new AMD64Address(arrayA, length, scaleA));
        movSZx(masm, scaleB, extendMode, tmp, new AMD64Address(arrayB, length, scaleB));
        masm.subq(result, tmp);
        masm.jmpb(returnLabel);

        if (supportsAVX2AndYMM()) {
            // region is too small for YMM vectors, try XMM
            masm.bind(xmmTail);
            masm.cmplAndJcc(result, getElementsPerVector(XMM, maxScale), ConditionFlag.Less, scalarTail, true);
            pmovSZx(masm, XMM, extendMode, vector1, maxScale, arrayA, scaleA, 0);
            pmovSZx(masm, XMM, extendMode, vector2, maxScale, arrayB, scaleB, 0);
            pcmpeq(masm, XMM, maxScale, vector1, vector2);
            pmovmsk(masm, XMM, tmp, vector1);
            masm.xorlAndJcc(tmp, ONES_16, ConditionFlag.NotZero, diffFound, true);

            pmovSZx(masm, XMM, vector1, extendMode, maxScale, arrayA, scaleA, result, -XMM.getBytes());
            pmovSZx(masm, XMM, vector2, extendMode, maxScale, arrayB, scaleB, result, -XMM.getBytes());
            masm.movq(length, result);
            pcmpeq(masm, XMM, maxScale, vector1, vector2);
            masm.subq(length, getElementsPerVector(XMM, maxScale));
            pmovmsk(masm, XMM, tmp, vector1);
            masm.xorlAndJcc(tmp, ONES_16, ConditionFlag.NotZero, diffFound, true);
            masm.xorq(result, result);
            masm.jmpb(returnLabel);
        }

        // scalar tail loop for regions smaller than XMM
        masm.bind(scalarTail);
        masm.movl(length, result);
    }

    private static int getElementsPerVector(AVXSize vSize, Scale maxScale) {
        return vSize.getBytes() >> maxScale.log2;
    }

    private void emitScalarLoop(CompilationResultBuilder crb, AMD64MacroAssembler masm, Scale scaleA, Scale scaleB,
                    Register result, Register arrayA, Register arrayB, Register length, Register tmp, Label returnLabel) {
        Label loop = new Label();

        masm.leaq(arrayA, new AMD64Address(arrayA, length, scaleA));
        masm.leaq(arrayB, new AMD64Address(arrayB, length, scaleB));
        masm.negq(length);

        masm.align(crb.target.wordSize * 2);
        masm.bind(loop);
        movSZx(masm, scaleA, extendMode, result, new AMD64Address(arrayA, length, scaleA));
        movSZx(masm, scaleB, extendMode, tmp, new AMD64Address(arrayB, length, scaleB));
        masm.subqAndJcc(result, tmp, ConditionFlag.NotZero, returnLabel, true);
        masm.incqAndJcc(length, ConditionFlag.NotZero, loop, true);
    }
}
