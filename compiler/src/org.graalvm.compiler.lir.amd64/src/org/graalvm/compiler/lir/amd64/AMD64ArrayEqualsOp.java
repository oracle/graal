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
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.OR;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.XOR;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.movSZx;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.por;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.ptest;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.pxor;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import java.util.Objects;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Address.Scale;
import org.graalvm.compiler.asm.amd64.AMD64Assembler;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.SSEOp;
import org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.asm.amd64.AVXKind;
import org.graalvm.compiler.asm.amd64.AVXKind.AVXSize;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.LIRValueUtil;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
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

    private final JavaKind kind1;
    private final JavaKind kind2;
    private final JavaKind kindMask;
    private final int array1BaseOffset;
    private final int array2BaseOffset;
    private final int maskBaseOffset;
    private final int constOffset1;
    private final int constOffset2;
    private final int constLength;
    private final Scale array1IndexScale;
    private final Scale array2IndexScale;
    private final Scale arrayMaskIndexScale;
    private final Scale maxScale;
    private final AMD64MacroAssembler.ExtendMode extendMode;

    @Def({REG}) private Value resultValue;
    @Use({REG}) private Value array1Value;
    @Use({REG, ILLEGAL}) private Value offset1Value;
    @Use({REG}) private Value array2Value;
    @Use({REG, ILLEGAL}) private Value offset2Value;
    @Use({REG, ILLEGAL}) private Value maskValue;
    @Use({REG}) private Value lengthValue;

    @Temp({REG}) private Value array1ValueTemp;
    @Temp({REG, ILLEGAL}) private Value offset1ValueTemp;
    @Temp({REG}) private Value array2ValueTemp;
    @Temp({REG, ILLEGAL}) private Value offset2ValueTemp;
    @Temp({REG, ILLEGAL}) private Value maskValueTemp;
    @Temp({REG}) private Value lengthValueTemp;

    @Temp({REG, ILLEGAL}) private Value tempXMM;

    @Temp({REG, ILLEGAL}) private Value vectorTemp1;
    @Temp({REG, ILLEGAL}) private Value vectorTemp2;
    @Temp({REG, ILLEGAL}) private Value vectorTemp3;

    private AMD64ArrayEqualsOp(LIRGeneratorTool tool, JavaKind kind1, JavaKind kind2, JavaKind kindMask, int array1BaseOffset, int array2BaseOffset, int maskBaseOffset,
                    Value result, Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value mask, Value length, AMD64MacroAssembler.ExtendMode extendMode,
                    int constOffset1, int constOffset2, int constLength) {
        super(TYPE, tool, AVXSize.YMM);

        this.kind1 = kind1;
        this.kind2 = kind2;
        this.kindMask = kindMask;
        this.extendMode = extendMode;
        this.constOffset1 = constOffset1;
        this.constOffset2 = constOffset2;
        this.constLength = constLength;

        assert kind1.isNumericInteger() && kind2.isNumericInteger() || kind1 == kind2;

        this.array1BaseOffset = array1BaseOffset;
        this.array2BaseOffset = array2BaseOffset;
        this.maskBaseOffset = maskBaseOffset;
        this.array1IndexScale = Objects.requireNonNull(Scale.fromInt(tool.getProviders().getMetaAccess().getArrayIndexScale(kind1)));
        this.array2IndexScale = Objects.requireNonNull(Scale.fromInt(tool.getProviders().getMetaAccess().getArrayIndexScale(kind2)));
        this.arrayMaskIndexScale = Objects.requireNonNull(Scale.fromInt(tool.getProviders().getMetaAccess().getArrayIndexScale(kindMask)));
        this.maxScale = array1IndexScale.value > array2IndexScale.value ? array1IndexScale : array2IndexScale;
        assert arrayMaskIndexScale.value <= maxScale.value;

        this.resultValue = result;
        this.array1Value = this.array1ValueTemp = arrayA;
        // if constOffset is 0, no offset parameter was used, but we still need the register as a
        // temp register, so we set the @Use property to ILLEGAL but keep the register in the @Temp
        // property
        this.offset1Value = constOffset1 == 0 ? Value.ILLEGAL : offsetA;
        this.offset1ValueTemp = offsetA;
        this.array2Value = this.array2ValueTemp = arrayB;
        this.offset2Value = constOffset2 == 0 ? Value.ILLEGAL : offsetB;
        this.offset2ValueTemp = offsetB;
        this.maskValue = this.maskValueTemp = mask;
        this.lengthValue = this.lengthValueTemp = length;

        if (kind1 == JavaKind.Float) {
            this.tempXMM = tool.newVariable(LIRKind.value(AMD64Kind.SINGLE));
        } else if (kind1 == JavaKind.Double) {
            this.tempXMM = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
        } else {
            this.tempXMM = Value.ILLEGAL;
        }

        // We only need the vector temporaries if we generate SSE code.
        if (supports(tool.target(), CPUFeature.SSE4_1)) {
            LIRKind lirKind = LIRKind.value(getVectorKind(JavaKind.Byte));
            this.vectorTemp1 = tool.newVariable(lirKind);
            this.vectorTemp2 = tool.newVariable(lirKind);
            this.vectorTemp3 = withMask() ? tool.newVariable(lirKind) : Value.ILLEGAL;
        } else {
            this.vectorTemp1 = Value.ILLEGAL;
            this.vectorTemp2 = Value.ILLEGAL;
            this.vectorTemp3 = Value.ILLEGAL;
        }
    }

    public static AMD64ArrayEqualsOp movParamsAndCreate(LIRGeneratorTool tool, JavaKind strideA, JavaKind strideB, JavaKind strideMask, int baseOffsetA, int baseOffsetB, int baseOffsetMask,
                    Value result, Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value mask, Value length, AMD64MacroAssembler.ExtendMode extendMode) {

        RegisterValue regArrayA = REG_ARRAY_A.asValue(arrayA.getValueKind());
        RegisterValue regOffsetA = REG_OFFSET_A.asValue(offsetA == null ? LIRKind.value(AMD64Kind.QWORD) : offsetA.getValueKind());
        RegisterValue regArrayB = REG_ARRAY_B.asValue(arrayB.getValueKind());
        RegisterValue regOffsetB = REG_OFFSET_B.asValue(offsetB == null ? LIRKind.value(AMD64Kind.QWORD) : offsetB.getValueKind());
        Value regMask = mask == null ? Value.ILLEGAL : REG_MASK.asValue(mask.getValueKind());
        RegisterValue regLength = REG_LENGTH.asValue(length.getValueKind());

        tool.emitConvertNullToZero(regArrayA, arrayA);
        tool.emitConvertNullToZero(regArrayB, arrayB);
        tool.emitMove(regLength, length);
        if (offsetA != null) {
            tool.emitMove(regOffsetA, offsetA);
        }
        if (offsetB != null) {
            tool.emitMove(regOffsetB, offsetB);
        }
        if (mask != null) {
            tool.emitMove((RegisterValue) regMask, mask);
        }
        return new AMD64ArrayEqualsOp(tool, strideA, strideB, strideMask, baseOffsetA, baseOffsetB, baseOffsetMask,
                        result, regArrayA, regOffsetA, regArrayB, regOffsetB, regMask, regLength,
                        extendMode, constOffset(offsetA), constOffset(offsetB), LIRValueUtil.isJavaConstant(length) ? LIRValueUtil.asJavaConstant(length).asInt() : -1);
    }

    private static int constOffset(Value offset) {
        if (offset == null) {
            // no offset parameter (e.g. in Arrays.equal)
            return 0;
        }
        if (LIRValueUtil.isJavaConstant(offset) && LIRValueUtil.asJavaConstant(offset).asLong() <= Integer.MAX_VALUE) {
            // constant offset parameter
            return (int) LIRValueUtil.asJavaConstant(offset).asLong();
        }
        // non-constant offset parameter
        return -1;
    }

    private boolean canGenerateConstantLengthCompare() {
        return isLengthConstant() && kind1.isNumericInteger() && (kind1 == kind2 || getElementsPerVector(AVXSize.XMM) <= constantLength());
    }

    private boolean isOffset1Constant() {
        return constOffset1 >= 0;
    }

    private boolean isOffset2Constant() {
        return constOffset2 >= 0;
    }

    private boolean isLengthConstant() {
        return constLength >= 0;
    }

    private int constantLength() {
        assert constLength >= 0;
        return constLength;
    }

    private boolean withMask() {
        return !maskValue.equals(Value.ILLEGAL);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register result = asRegister(resultValue);

        Label trueLabel = new Label();
        Label falseLabel = new Label();
        Label done = new Label();

        Register array1 = asRegister(array1Value);
        Register array2 = asRegister(array2Value);
        Register mask = withMask() ? asRegister(maskValue) : null;
        // Load array base addresses.
        if (isOffset1Constant()) {
            masm.leaq(array1, new AMD64Address(array1, constOffset1 + array1BaseOffset));
        } else {
            masm.leaq(array1, new AMD64Address(array1, asRegister(offset1Value), Scale.Times1, array1BaseOffset));
        }
        if (isOffset2Constant()) {
            masm.leaq(array2, new AMD64Address(array2, constOffset2 + array2BaseOffset));
        } else {
            masm.leaq(array2, new AMD64Address(array2, asRegister(offset2Value), Scale.Times1, array2BaseOffset));
        }
        if (withMask()) {
            masm.leaq(mask, new AMD64Address(mask, maskBaseOffset));
        }
        if (canGenerateConstantLengthCompare() && masm.supports(CPUFeature.SSE4_1)) {
            emitConstantLengthArrayCompareBytes(crb, masm, falseLabel);
        } else {
            Register length = asRegister(lengthValue);
            // copy
            masm.movl(result, length);
            emitArrayCompare(crb, masm, result, array1, array2, mask, length, trueLabel, falseLabel);
        }

        // Return true
        masm.bind(trueLabel);
        masm.movl(result, 1);
        masm.jmpb(done);

        // Return false
        masm.bind(falseLabel);
        masm.xorl(result, result);

        // That's it
        masm.bind(done);
    }

    private void emitArrayCompare(CompilationResultBuilder crb, AMD64MacroAssembler masm,
                    Register result, Register array1, Register array2, Register mask, Register length,
                    Label trueLabel, Label falseLabel) {
        if (masm.supports(CPUFeature.SSE4_1)) {
            emitVectorCompare(crb, masm, result, array1, array2, mask, length, trueLabel, falseLabel);
        }
        if (kind1 == kind2 && kind1 == kindMask) {
            emit8ByteCompare(crb, masm, result, array1, array2, mask, length, trueLabel, falseLabel);
            emitTailCompares(masm, result, array1, array2, mask, length, trueLabel, falseLabel);
        } else {
            emitDifferentKindsElementWiseCompare(crb, masm, result, array1, array2, mask, length, trueLabel, falseLabel);
        }
    }

    /**
     * Emits code that uses SSE4.1/AVX1 128-bit (16-byte) or AVX2 256-bit (32-byte) vector compares.
     */
    private void emitVectorCompare(CompilationResultBuilder crb, AMD64MacroAssembler masm,
                    Register result, Register array1, Register array2, Register mask, Register length,
                    Label trueLabel, Label falseLabel) {
        assert masm.supports(CPUFeature.SSE4_1);

        Register vector1 = asRegister(vectorTemp1);
        Register vector2 = asRegister(vectorTemp2);
        Register vector3 = withMask() ? asRegister(vectorTemp3) : null;

        int elementsPerVector = getElementsPerVector(vectorSize);

        Label loop = new Label();
        Label compareTail = new Label();

        boolean requiresNaNCheck = kind1.isNumericFloat();
        Label loopCheck = new Label();
        Label nanCheck = new Label();

        // Compare 16-byte vectors
        masm.andl(result, elementsPerVector - 1); // tail count
        masm.andlAndJcc(length, ~(elementsPerVector - 1), ConditionFlag.Zero, compareTail, false);

        masm.leaq(array1, new AMD64Address(array1, length, array1IndexScale, 0));
        masm.leaq(array2, new AMD64Address(array2, length, array2IndexScale, 0));
        if (withMask()) {
            masm.leaq(mask, new AMD64Address(mask, length, arrayMaskIndexScale, 0));
        }
        masm.negq(length);

        // Align the main loop
        masm.align(crb.target.wordSize * 2);
        masm.bind(loop);
        emitVectorLoad1(masm, vector1, array1, length, 0, vectorSize);
        emitVectorLoad2(masm, vector2, array2, length, 0, vectorSize);
        if (withMask()) {
            emitVectorLoadMask(masm, vector3, mask, length, 0, vectorSize);
            por(masm, vectorSize, vector1, vector3);
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
            emitFloatCompareWithinRange(crb, masm, array1, array2, length, 0, falseLabel, elementsPerVector);
            masm.jmpb(loopCheck);
            masm.bind(unalignedCheck);
        }

        /*
         * Compare the remaining bytes with an unaligned memory load aligned to the end of the
         * array.
         */
        emitVectorLoad1(masm, vector1, array1, result, scaleDisplacement1(-vectorSize.getBytes()), vectorSize);
        emitVectorLoad2(masm, vector2, array2, result, scaleDisplacement2(-vectorSize.getBytes()), vectorSize);
        if (withMask()) {
            emitVectorLoadMask(masm, vector3, mask, result, scaleDisplacementMask(-vectorSize.getBytes()), vectorSize);
            por(masm, vectorSize, vector1, vector3);
        }
        emitVectorCmp(masm, vector1, vector2, vectorSize);
        if (requiresNaNCheck) {
            assert !withMask();
            masm.jcc(ConditionFlag.Zero, trueLabel);
            emitFloatCompareWithinRange(crb, masm, array1, array2, result, -vectorSize.getBytes(), falseLabel, elementsPerVector);
        } else {
            masm.jcc(ConditionFlag.NotZero, falseLabel);
        }
        masm.jmp(trueLabel);

        masm.bind(compareTail);
        masm.movl(length, result);
    }

    private int getElementsPerVector(AVXSize vSize) {
        return vSize.getBytes() >> Math.max(array1IndexScale.log2, array2IndexScale.log2);
    }

    private void emitVectorLoad1(AMD64MacroAssembler asm, Register dst, Register src, int displacement, AVXSize size) {
        emitVectorLoad1(asm, dst, src, Register.None, displacement, size);
    }

    private void emitVectorLoad2(AMD64MacroAssembler asm, Register dst, Register src, int displacement, AVXSize size) {
        emitVectorLoad2(asm, dst, src, Register.None, displacement, size);
    }

    private void emitVectorLoad1(AMD64MacroAssembler asm, Register dst, Register src, Register index, int displacement, AVXSize size) {
        emitVectorLoad(asm, dst, src, index, displacement, array1IndexScale, size);
    }

    private void emitVectorLoad2(AMD64MacroAssembler asm, Register dst, Register src, Register index, int displacement, AVXSize size) {
        emitVectorLoad(asm, dst, src, index, displacement, array2IndexScale, size);
    }

    private void emitVectorLoadMask(AMD64MacroAssembler asm, Register dst, Register src, Register index, int displacement, AVXSize size) {
        emitVectorLoad(asm, dst, src, index, displacement, arrayMaskIndexScale, size);
    }

    private void emitVectorLoad(AMD64MacroAssembler asm, Register dst, Register src, Register index, int displacement, Scale scale, AVXSize size) {
        AMD64Address address = new AMD64Address(src, index, scale, displacement);
        if (scale.value < maxScale.value) {
            if (size == AVXSize.YMM) {
                AMD64MacroAssembler.loadAndExtendAVX(asm, size, extendMode, dst, maxScale, address, scale);
            } else {
                AMD64MacroAssembler.loadAndExtendSSE(asm, extendMode, dst, maxScale, address, scale);
            }
        } else {
            if (size == AVXSize.YMM) {
                asm.vmovdqu(dst, address);
            } else {
                asm.movdqu(dst, address);
            }
        }
    }

    private int scaleDisplacement1(int displacement) {
        return scaleDisplacement(displacement, array1IndexScale);
    }

    private int scaleDisplacement2(int displacement) {
        return scaleDisplacement(displacement, array2IndexScale);
    }

    private int scaleDisplacementMask(int displacement) {
        return scaleDisplacement(displacement, arrayMaskIndexScale);
    }

    private int scaleDisplacement(int displacement, Scale scale) {
        if (scale.value < maxScale.value) {
            return displacement >> (maxScale.log2 - scale.log2);
        }
        return displacement;
    }

    private static void emitVectorCmp(AMD64MacroAssembler masm, Register vector1, Register vector2, AVXKind.AVXSize size) {
        pxor(masm, size, vector1, vector2);
        ptest(masm, size, vector1);
    }

    /**
     * Vector size used in {@link #emit8ByteCompare}.
     */
    private static final int VECTOR_SIZE = 8;

    /**
     * Emits code that uses 8-byte vector compares.
     */
    private void emit8ByteCompare(CompilationResultBuilder crb, AMD64MacroAssembler masm,
                    Register result, Register array1, Register array2, Register mask, Register length, Label trueLabel, Label falseLabel) {
        assert kind1 == kind2 && kind1 == kindMask;
        Label loop = new Label();
        Label compareTail = new Label();

        int elementsPerVector = 8 >> array1IndexScale.log2;

        boolean requiresNaNCheck = kind1.isNumericFloat();
        Label loopCheck = new Label();
        Label nanCheck = new Label();

        Register temp = asRegister(offset1ValueTemp);

        masm.andl(result, elementsPerVector - 1); // tail count
        masm.andlAndJcc(length, ~(elementsPerVector - 1), ConditionFlag.Zero, compareTail, false);

        masm.leaq(array1, new AMD64Address(array1, length, array1IndexScale, 0));
        masm.leaq(array2, new AMD64Address(array2, length, array2IndexScale, 0));
        if (withMask()) {
            masm.leaq(mask, new AMD64Address(mask, length, arrayMaskIndexScale, 0));
        }
        masm.negq(length);

        // Align the main loop
        masm.align(crb.target.wordSize * 2);
        masm.bind(loop);
        masm.movq(temp, new AMD64Address(array1, length, array1IndexScale, 0));
        if (withMask()) {
            masm.orq(temp, new AMD64Address(mask, length, arrayMaskIndexScale, 0));
        }
        masm.cmpqAndJcc(temp, new AMD64Address(array2, length, array2IndexScale, 0), ConditionFlag.NotEqual, requiresNaNCheck ? nanCheck : falseLabel, requiresNaNCheck);

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
            for (int offset = 0; offset < VECTOR_SIZE; offset += kind1.getByteCount()) {
                emitFloatCompare(masm, array1, array2, length, offset, falseLabel, kind1.getByteCount() == VECTOR_SIZE);
            }
            masm.jmpb(loopCheck);
            masm.bind(unalignedCheck);
        }

        /*
         * Compare the remaining bytes with an unaligned memory load aligned to the end of the
         * array.
         */
        masm.movq(temp, new AMD64Address(array1, result, array1IndexScale, -VECTOR_SIZE));
        if (requiresNaNCheck) {
            assert !withMask();
            masm.cmpqAndJcc(temp, new AMD64Address(array2, result, array2IndexScale, -VECTOR_SIZE), ConditionFlag.Equal, trueLabel, false);
            // At most two iterations, unroll in the emitted code.
            for (int offset = 0; offset < VECTOR_SIZE; offset += kind1.getByteCount()) {
                emitFloatCompare(masm, array1, array2, result, -VECTOR_SIZE + offset, falseLabel, kind1.getByteCount() == VECTOR_SIZE);
            }
        } else {
            if (withMask()) {
                masm.orq(temp, new AMD64Address(mask, result, arrayMaskIndexScale, -VECTOR_SIZE));
            }
            masm.cmpqAndJcc(temp, new AMD64Address(array2, result, array2IndexScale, -VECTOR_SIZE), ConditionFlag.NotEqual, falseLabel, true);
        }
        masm.jmpb(trueLabel);

        masm.bind(compareTail);
        masm.movl(length, result);
    }

    /**
     * Emits code to compare the remaining 1 to 4 bytes.
     */
    private void emitTailCompares(AMD64MacroAssembler masm,
                    Register result, Register array1, Register array2, Register mask, Register length, Label trueLabel, Label falseLabel) {
        assert kind1 == kind2 && kind1 == kindMask;
        Label compare2Bytes = new Label();
        Label compare1Byte = new Label();

        Register temp = asRegister(offset1ValueTemp);

        if (kind1.getByteCount() <= 4) {
            // Compare trailing 4 bytes, if any.
            masm.testlAndJcc(result, array1IndexScale.log2 == 0 ? 4 : 4 >> array1IndexScale.log2, ConditionFlag.Zero, compare2Bytes, true);
            masm.movl(temp, new AMD64Address(array1, 0));
            if (kind1 == JavaKind.Float) {
                assert !withMask();
                masm.cmplAndJcc(temp, new AMD64Address(array2, 0), ConditionFlag.Equal, trueLabel, true);
                emitFloatCompare(masm, array1, array2, Register.None, 0, falseLabel, true);
                masm.jmpb(trueLabel);
            } else {
                if (withMask()) {
                    masm.orl(temp, new AMD64Address(mask, 0));
                }
                masm.cmplAndJcc(temp, new AMD64Address(array2, 0), ConditionFlag.NotEqual, falseLabel, true);
            }
            if (kind1.getByteCount() <= 2) {
                // Move array pointers forward.
                masm.leaq(array1, new AMD64Address(array1, 4));
                masm.leaq(array2, new AMD64Address(array2, 4));
                if (withMask()) {
                    masm.leaq(mask, new AMD64Address(mask, 4));
                }

                // Compare trailing 2 bytes, if any.
                masm.bind(compare2Bytes);
                masm.testlAndJcc(result, array1IndexScale.log2 == 0 ? 2 : 2 >> array1IndexScale.log2, ConditionFlag.Zero, compare1Byte, true);
                masm.movzwl(temp, new AMD64Address(array1, 0));
                if (withMask()) {
                    masm.movzwl(length, new AMD64Address(mask, 0));
                    masm.orl(temp, length);
                }
                masm.movzwl(length, new AMD64Address(array2, 0));
                masm.cmplAndJcc(temp, length, ConditionFlag.NotEqual, falseLabel, true);

                // The one-byte tail compare is only required for boolean and byte arrays.
                if (kind1.getByteCount() <= 1) {
                    // Move array pointers forward before we compare the last trailing byte.
                    masm.leaq(array1, new AMD64Address(array1, 2));
                    masm.leaq(array2, new AMD64Address(array2, 2));
                    if (withMask()) {
                        masm.leaq(mask, new AMD64Address(mask, 2));
                    }

                    // Compare trailing byte, if any.
                    // TODO (yz) this can be optimized, i.e., bind after padding
                    masm.bind(compare1Byte);
                    masm.testlAndJcc(result, 1, ConditionFlag.Zero, trueLabel, true);
                    masm.movzbl(temp, new AMD64Address(array1, 0));
                    if (withMask()) {
                        masm.movzbl(length, new AMD64Address(mask, 0));
                        masm.orl(temp, length);
                    }
                    masm.movzbl(length, new AMD64Address(array2, 0));
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
                    Register result, Register array1, Register array2, Register mask, Register length, Label trueLabel, Label falseLabel) {
        assert kind1 != kind2 || kind1 != kindMask;
        assert kind1.isNumericInteger() && kind2.isNumericInteger();
        Label loop = new Label();
        Label compareTail = new Label();

        int elementsPerLoopIteration = 2;

        Register tmp1 = asRegister(offset1ValueTemp);
        Register tmp2 = asRegister(offset2ValueTemp);

        masm.andl(result, elementsPerLoopIteration - 1); // tail count
        masm.andlAndJcc(length, ~(elementsPerLoopIteration - 1), ConditionFlag.Zero, compareTail, true);

        masm.leaq(array1, new AMD64Address(array1, length, array1IndexScale, 0));
        masm.leaq(array2, new AMD64Address(array2, length, array2IndexScale, 0));
        if (withMask()) {
            masm.leaq(mask, new AMD64Address(mask, length, arrayMaskIndexScale, 0));
        }
        masm.negq(length);

        // clear comparison registers because of the missing movzlq instruction
        masm.xorq(tmp1, tmp1);
        masm.xorq(tmp2, tmp2);

        // Align the main loop
        masm.align(crb.target.wordSize * 2);
        masm.bind(loop);
        for (int i = 0; i < elementsPerLoopIteration; i++) {
            movSZx(masm, array1IndexScale, extendMode, tmp1, new AMD64Address(array1, length, array1IndexScale, i << array1IndexScale.log2));
            if (withMask()) {
                movSZx(masm, arrayMaskIndexScale, extendMode, tmp2, new AMD64Address(mask, length, arrayMaskIndexScale, i << arrayMaskIndexScale.log2));
                masm.orq(tmp1, tmp2);
            }
            movSZx(masm, array2IndexScale, extendMode, tmp2, new AMD64Address(array2, length, array2IndexScale, i << array2IndexScale.log2));
            masm.cmpqAndJcc(tmp1, tmp2, ConditionFlag.NotEqual, falseLabel, true);
        }
        masm.addqAndJcc(length, elementsPerLoopIteration, ConditionFlag.NotZero, loop, true);

        masm.bind(compareTail);
        masm.testlAndJcc(result, result, ConditionFlag.Zero, trueLabel, true);
        for (int i = 0; i < elementsPerLoopIteration - 1; i++) {
            movSZx(masm, array1IndexScale, extendMode, tmp1, new AMD64Address(array1, length, array1IndexScale, 0));
            if (withMask()) {
                movSZx(masm, arrayMaskIndexScale, extendMode, tmp2, new AMD64Address(mask, length, arrayMaskIndexScale, 0));
                masm.orq(tmp1, tmp2);
            }
            movSZx(masm, array2IndexScale, extendMode, tmp2, new AMD64Address(array2, length, array2IndexScale, 0));
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
        assert kind1.isNumericFloat();
        Register tempXMMReg = asRegister(tempXMM);
        if (kind1 == JavaKind.Float) {
            masm.movflt(tempXMMReg, src);
        } else {
            masm.movdbl(tempXMMReg, src);
        }
        SSEOp.UCOMIS.emit(masm, kind1 == JavaKind.Float ? OperandSize.PS : OperandSize.PD, tempXMMReg, tempXMMReg);
        masm.jcc(ConditionFlag.NoParity, branchIfNonNaN);
    }

    /**
     * Emits code to compare if two floats are bitwise equal or both NaN.
     */
    private void emitFloatCompare(AMD64MacroAssembler masm, Register base1, Register base2, Register index, int offset, Label falseLabel,
                    boolean skipBitwiseCompare) {
        AMD64Address address1 = new AMD64Address(base1, index, array1IndexScale, offset);
        AMD64Address address2 = new AMD64Address(base2, index, array2IndexScale, offset);

        Label bitwiseEqual = new Label();

        if (!skipBitwiseCompare) {
            // Bitwise compare
            Register temp = asRegister(offset1ValueTemp);

            if (kind1 == JavaKind.Float) {
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
                    Register base1, Register base2, Register index, int offset, Label falseLabel, int range) {
        assert kind1.isNumericFloat();
        Label loop = new Label();
        Register i = asRegister(offset2ValueTemp);

        masm.movq(i, range);
        masm.negq(i);
        // Align the main loop
        masm.align(crb.target.wordSize * 2);
        masm.bind(loop);
        emitFloatCompare(masm, base1, base2, index, offset, falseLabel, range == 1);
        masm.incrementq(index, 1);
        masm.incqAndJcc(i, ConditionFlag.NotZero, loop, true);
        // Floats within the range are equal, revert change to the register index
        masm.subq(index, range);
    }

    /**
     * Emits specialized assembly for checking equality of memory regions
     * {@code arrayPtr1[0..nBytes]} and {@code arrayPtr2[0..nBytes]}. If they match, execution
     * continues directly after the emitted code block, otherwise we jump to {@code noMatch}.
     */
    private void emitConstantLengthArrayCompareBytes(
                    CompilationResultBuilder crb,
                    AMD64MacroAssembler asm,
                    Label noMatch) {
        if (constantLength() == 0) {
            // do nothing
            return;
        }
        Register arrayPtr1 = asRegister(array1Value);
        Register arrayPtr2 = asRegister(array2Value);
        Register maskPtr = withMask() ? asRegister(maskValue) : null;
        Register vector1 = asRegister(vectorTemp1);
        Register vector2 = asRegister(vectorTemp2);
        Register vector3 = withMask() ? asRegister(vectorTemp3) : null;
        Register tmp = asRegister(lengthValue);
        AVXSize vSize = vectorSize;
        if (constantLength() < getElementsPerVector(vectorSize)) {
            vSize = AVXSize.XMM;
        }
        int elementsPerVector = getElementsPerVector(vSize);
        if (elementsPerVector > constantLength()) {
            assert kind1 == kind2 && kind1 == kindMask;
            int byteLength = constantLength() << array1IndexScale.log2;
            // array is shorter than any vector register, use regular XOR instructions
            Scale movScale = (byteLength < 2) ? Scale.Times1 : ((byteLength < 4) ? Scale.Times2 : ((byteLength < 8) ? Scale.Times4 : Scale.Times8));
            movSZx(asm, movScale, extendMode, tmp, new AMD64Address(arrayPtr1));
            if (withMask()) {
                emitOrBytes(asm, tmp, new AMD64Address(maskPtr, 0), movScale);
            }
            emitXorBytes(asm, tmp, new AMD64Address(arrayPtr2), movScale);
            asm.jccb(ConditionFlag.NotZero, noMatch);
            if (byteLength > movScale.value) {
                movSZx(asm, movScale, extendMode, tmp, new AMD64Address(arrayPtr1, byteLength - movScale.value));
                if (withMask()) {
                    emitOrBytes(asm, tmp, new AMD64Address(maskPtr, byteLength - movScale.value), movScale);
                }
                emitXorBytes(asm, tmp, new AMD64Address(arrayPtr2, byteLength - movScale.value), movScale);
                asm.jccb(AMD64Assembler.ConditionFlag.NotZero, noMatch);
            }
        } else {
            int tailCount = constantLength() & (elementsPerVector - 1);
            int vectorCount = constantLength() & ~(elementsPerVector - 1);
            int bytesPerVector = vSize.getBytes();
            if (vectorCount > 0) {
                Label loopBegin = new Label();
                if (vectorCount > 1) {
                    asm.leaq(arrayPtr1, new AMD64Address(arrayPtr1, vectorCount << array1IndexScale.log2));
                    asm.leaq(arrayPtr2, new AMD64Address(arrayPtr2, vectorCount << array2IndexScale.log2));
                    if (withMask()) {
                        asm.leaq(maskPtr, new AMD64Address(maskPtr, vectorCount << arrayMaskIndexScale.log2));
                    }
                    asm.movq(tmp, -vectorCount);
                    asm.align(crb.target.wordSize * 2);
                    asm.bind(loopBegin);
                }
                emitVectorLoad1(asm, vector1, arrayPtr1, vectorCount > 1 ? tmp : Register.None, 0, vSize);
                emitVectorLoad2(asm, vector2, arrayPtr2, vectorCount > 1 ? tmp : Register.None, 0, vSize);
                if (withMask()) {
                    emitVectorLoadMask(asm, vector3, maskPtr, vectorCount > 1 ? tmp : Register.None, 0, vSize);
                    por(asm, vSize, vector1, vector3);
                }
                pxor(asm, vSize, vector1, vector2);
                ptest(asm, vSize, vector1);
                asm.jccb(ConditionFlag.NotZero, noMatch);
                if (vectorCount > 1) {
                    asm.addqAndJcc(tmp, elementsPerVector, ConditionFlag.NotZero, loopBegin, true);
                }
            }
            if (tailCount > 0) {
                int endIndex = vectorCount > 1 ? tailCount : constantLength();
                emitVectorLoad1(asm, vector1, arrayPtr1, (endIndex << array1IndexScale.log2) - scaleDisplacement1(bytesPerVector), vSize);
                emitVectorLoad2(asm, vector2, arrayPtr2, (endIndex << array2IndexScale.log2) - scaleDisplacement2(bytesPerVector), vSize);
                if (withMask()) {
                    emitVectorLoadMask(asm, vector3, maskPtr, Register.None, (endIndex << arrayMaskIndexScale.log2) - scaleDisplacement2(bytesPerVector), vSize);
                    por(asm, vSize, vector1, vector3);
                }
                pxor(asm, vSize, vector1, vector2);
                ptest(asm, vSize, vector1);
                asm.jccb(ConditionFlag.NotZero, noMatch);
            }
        }
    }

    private static void emitOrBytes(AMD64MacroAssembler asm, Register dst, AMD64Address src, Scale scale) {
        OperandSize opSize = getOperandSize(scale);
        OR.getRMOpcode(opSize).emit(asm, opSize, dst, src);
    }

    private static void emitXorBytes(AMD64MacroAssembler asm, Register dst, AMD64Address src, Scale scale) {
        OperandSize opSize = getOperandSize(scale);
        XOR.getRMOpcode(opSize).emit(asm, opSize, dst, src);
    }

    private static OperandSize getOperandSize(Scale size) {
        switch (size) {
            case Times1:
                return OperandSize.BYTE;
            case Times2:
                return OperandSize.WORD;
            case Times4:
                return OperandSize.DWORD;
            case Times8:
                return OperandSize.QWORD;
            default:
                throw new IllegalStateException();
        }
    }
}
