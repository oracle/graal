/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD2_MULTIPLE_2R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD4_MULTIPLE_4R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize.fromStride;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createBaseRegisterOnlyAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createImmediateAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createPairBaseRegisterOnlyAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createStructureImmediatePostIndexAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createStructureNoOffsetAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler.PREFERRED_BRANCH_TARGET_ALIGNMENT;
import static jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isIllegal;

import java.util.Arrays;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ShiftType;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.StrideUtil;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

/**
 * Emits code which compares two arrays of the same length for equality. Optionally, a third array
 * ({@code arrayMask}) is OR-ed to the first array before comparison.
 */
@Opcode("ARRAY_EQUALS")
public final class AArch64ArrayEqualsOp extends AArch64ComplexVectorOp {
    public static final LIRInstructionClass<AArch64ArrayEqualsOp> TYPE = LIRInstructionClass.create(AArch64ArrayEqualsOp.class);

    private final Stride argStrideA;
    private final Stride argStrideB;
    private final Stride argStrideM;

    @Def({REG}) protected Value resultValue;
    @Alive({REG}) protected Value arrayAValue;
    @Alive({REG}) protected Value offsetAValue;
    @Alive({REG}) protected Value arrayBValue;
    @Alive({REG}) protected Value offsetBValue;
    @Alive({REG}) protected Value lengthValue;
    @Alive({REG, ILLEGAL}) protected Value arrayMaskValue;
    @Alive({REG, ILLEGAL}) private Value dynamicStridesValue;
    @Temp({REG}) protected Value[] temp;
    @Temp({REG}) protected Value[] vectorTemp;

    public AArch64ArrayEqualsOp(LIRGeneratorTool tool, Stride strideA, Stride strideB, Stride strideM, Value result, Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value length,
                    Value mask, Value dynamicStrides) {
        super(TYPE);
        this.argStrideA = strideA;
        this.argStrideB = strideB;
        this.argStrideM = strideM;

        if (strideM != null && strideM != strideB) {
            GraalError.guarantee(strideA == Stride.S2 && strideB == Stride.S2 && strideM == Stride.S1 || strideA == Stride.S1 && strideB == Stride.S2 && strideM == Stride.S1,
                            "The only supported cases where strideMask is not equal to strideB are : S2 - S2 - S1 and S1 - S2 - S1");
        }

        GraalError.guarantee(result.getPlatformKind() == AArch64Kind.DWORD, "int value expected");
        GraalError.guarantee(arrayA.getPlatformKind() == AArch64Kind.QWORD && arrayA.getPlatformKind() == arrayB.getPlatformKind(), "pointer value expected");
        GraalError.guarantee(offsetA.getPlatformKind() == AArch64Kind.QWORD, "long value expected");
        GraalError.guarantee(offsetB.getPlatformKind() == AArch64Kind.QWORD, "long value expected");
        GraalError.guarantee(length.getPlatformKind() == AArch64Kind.DWORD, "int value expected");
        GraalError.guarantee(dynamicStrides == null || dynamicStrides.getPlatformKind() == AArch64Kind.DWORD, "int value expected");

        this.resultValue = result;
        this.arrayAValue = arrayA;
        this.offsetAValue = offsetA;
        this.arrayBValue = arrayB;
        this.offsetBValue = offsetB;
        this.lengthValue = length;
        this.arrayMaskValue = mask == null ? Value.ILLEGAL : mask;
        this.dynamicStridesValue = dynamicStrides == null ? Value.ILLEGAL : dynamicStrides;

        temp = allocateTempRegisters(tool, 2 + (withMask() ? 1 : 0) + (withDynamicStrides() ? 1 : 0));
        vectorTemp = allocateConsecutiveVectorRegisters(tool, withMask() ? 12 : 8);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        try (ScratchRegister sc1 = masm.getScratchRegister(); ScratchRegister sc2 = masm.getScratchRegister()) {
            Register ret = asRegister(resultValue);
            Register arrayA = sc1.getRegister();
            Register arrayB = sc2.getRegister();
            Register length = asRegister(temp[0]);
            Register tmp = asRegister(temp[1]);
            Register mask = withMask() ? asRegister(temp[2]) : null;
            Label end = new Label();

            // Load array base addresses.
            masm.add(64, arrayA, asRegister(arrayAValue), asRegister(offsetAValue));
            masm.add(64, arrayB, asRegister(arrayBValue), asRegister(offsetBValue));
            if (withMask()) {
                masm.mov(64, mask, asRegister(arrayMaskValue));
            }

            // Get array length and store as a 64-bit value.
            masm.mov(32, length, asRegister(lengthValue));
            if (withDynamicStrides()) {
                Label[] variants = new Label[9];
                for (int i = 0; i < variants.length; i++) {
                    variants[i] = new Label();
                }
                Register tmp2 = asRegister(temp[withMask() ? 3 : 2]);
                masm.mov(32, tmp2, asRegister(dynamicStridesValue));
                AArch64ControlFlow.RangeTableSwitchOp.emitJumpTable(crb, masm, tmp, tmp2, 0, 8, Arrays.stream(variants));

                // reuse the 1-byte-1-byte stride variant for the 2-2 and 4-4 cases by simply
                // shifting the length
                masm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
                masm.bind(variants[StrideUtil.getDirectStubCallIndex(Stride.S4, Stride.S4)]);
                masm.lsl(64, length, length, 1);
                masm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
                masm.bind(variants[StrideUtil.getDirectStubCallIndex(Stride.S2, Stride.S2)]);
                masm.lsl(64, length, length, 1);
                masm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
                masm.bind(variants[StrideUtil.getDirectStubCallIndex(Stride.S1, Stride.S1)]);
                emitArrayEquals(masm, Stride.S1, Stride.S1, Stride.S1, arrayA, arrayB, mask, length, tmp, ret, end);
                masm.jmp(end);

                for (Stride strideA : new Stride[]{Stride.S1, Stride.S2, Stride.S4}) {
                    for (Stride strideB : new Stride[]{Stride.S1, Stride.S2, Stride.S4}) {
                        if (strideA.log2 == strideB.log2 || !withMask() && strideA.log2 < strideB.log2) {
                            continue;
                        }
                        if (!withMask()) {
                            masm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
                            // use the same implementation for e.g. stride 1-2 and 2-1 by swapping
                            // the arguments in one variant
                            masm.bind(variants[StrideUtil.getDirectStubCallIndex(strideB, strideA)]);
                            masm.mov(64, tmp, arrayA);
                            masm.mov(64, arrayA, arrayB);
                            masm.mov(64, arrayB, tmp);
                        }
                        masm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
                        masm.bind(variants[StrideUtil.getDirectStubCallIndex(strideA, strideB)]);
                        emitArrayEquals(masm, strideA, strideB, strideB, arrayA, arrayB, mask, length, tmp, ret, end);
                        masm.jmp(end);
                    }
                }
            } else {
                emitArrayEquals(masm, argStrideA, argStrideB, argStrideM, arrayA, arrayB, mask, length, tmp, ret, end);
            }

            // Return: hasMismatch is non-zero iff the arrays differ
            masm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
            masm.bind(end);
            masm.cset(32, ret, ConditionFlag.EQ);
        }
    }

    private boolean withMask() {
        return !isIllegal(arrayMaskValue);
    }

    private boolean withDynamicStrides() {
        return !isIllegal(dynamicStridesValue);
    }

    private void emitArrayEquals(AArch64MacroAssembler asm,
                    Stride strideA,
                    Stride strideB,
                    Stride strideM,
                    Register arrayA,
                    Register arrayB,
                    Register arrayM,
                    Register len,
                    Register tmp,
                    Register ret,
                    Label end) {
        Label tailLessThan64 = new Label();
        Label tailLessThan32 = new Label();
        Label tailLessThan16 = new Label();
        Label tailLessThan8 = new Label();
        Label tailLessThan4 = new Label();
        Label tailLessThan2 = new Label();

        Label vectorLoop = new Label();
        Label tail = new Label();

        Register arrayMax = strideB.value < strideA.value ? arrayA : arrayB;
        Register arrayMin = strideB.value < strideA.value ? arrayB : arrayA;

        Stride strideMax = Stride.max(strideB, strideA);
        Stride strideMin = Stride.min(strideB, strideA);

        // subtract 64 from len. if the result is negative, jump to branch for less than 64 bytes
        asm.subs(64, len, len, 64 >> strideMax.log2);
        asm.branchConditionally(ConditionFlag.MI, tailLessThan64);

        Register refAddress = len;
        asm.add(64, refAddress, arrayMax, len, ShiftType.LSL, strideMax.log2);

        simdCompare64(asm, strideMax, strideMin, strideA, strideM, arrayMax, arrayMin, arrayM, tmp);
        asm.branchConditionally(ConditionFlag.NE, end);

        asm.cmp(64, refAddress, arrayMax);
        asm.branchConditionally(ConditionFlag.LS, tail);

        // align addresses to chunk size
        asm.and(64, tmp, arrayMax, 63);
        asm.sub(64, arrayMin, arrayMin, tmp, ShiftType.LSR, strideMax.log2 - strideMin.log2);
        if (withMask()) {
            asm.sub(64, arrayM, arrayM, tmp, ShiftType.LSR, strideMax.log2 - strideM.log2);
        }
        asm.bic(64, arrayMax, arrayMax, 63);

        // 64 byte loop
        asm.align(PREFERRED_LOOP_ALIGNMENT);
        asm.bind(vectorLoop);
        simdCompare64(asm, strideMax, strideMin, strideA, strideM, arrayMax, arrayMin, arrayM, tmp);
        asm.branchConditionally(ConditionFlag.NE, end);
        asm.cmp(64, arrayMax, refAddress);
        asm.branchConditionally(ConditionFlag.LO, vectorLoop);

        asm.bind(tail);
        // 64 byte loop tail
        asm.sub(64, tmp, arrayMax, refAddress);
        asm.mov(64, arrayMax, refAddress);
        asm.sub(64, arrayMin, arrayMin, tmp, ShiftType.LSR, strideMax.log2 - strideMin.log2);
        if (withMask()) {
            asm.sub(64, arrayM, arrayM, tmp, ShiftType.LSR, strideMax.log2 - strideM.log2);
        }

        simdCompare64(asm, strideMax, strideMin, strideA, strideM, arrayMax, arrayMin, arrayM, tmp);
        asm.jmp(end);

        // tail for 32 - 63 bytes
        tail32(asm, strideMax, strideMin, strideA, strideM, arrayMax, arrayMin, arrayM, len, tmp, tailLessThan64, tailLessThan32, end);
        // tail for 16 - 31 bytes
        tail16(asm, strideA, strideB, strideM, strideMax, strideMin, arrayA, arrayB, arrayM, len, tmp, tailLessThan32, tailLessThan16, end);
        // tail for 8 - 15 bytes
        tailLessThan16(asm, strideA, strideB, strideM, strideMax, arrayA, arrayB, arrayM, len, tmp, ret, tailLessThan16, tailLessThan8, end, 8);
        // tail for 4 - 7 bytes
        tailLessThan16(asm, strideA, strideB, strideM, strideMax, arrayA, arrayB, arrayM, len, tmp, ret, tailLessThan8, tailLessThan4, end, 4);
        // tail for 2 - 3 bytes
        tailLessThan16(asm, strideA, strideB, strideM, strideMax, arrayA, arrayB, arrayM, len, tmp, ret, tailLessThan4, tailLessThan2, end, 2);
        // tail for 0 - 1 bytes
        tailLessThan16(asm, strideA, strideB, strideM, strideMax, arrayA, arrayB, arrayM, len, tmp, ret, tailLessThan2, null, end, 1);
    }

    private Register v(int index) {
        return asRegister(vectorTemp[index]);
    }

    private void simdCompare64(AArch64MacroAssembler asm,
                    Stride strideMax,
                    Stride strideMin,
                    Stride strideA,
                    Stride strideMask,
                    Register arrayMax,
                    Register arrayMin,
                    Register arrayMask,
                    Register tmp) {
        ElementSize minESize = fromStride(strideMin);
        switch (strideMax.log2 - strideMin.log2) {
            case 0:
                // direct comparison
                asm.fldp(128, v(0), v(1), createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arrayMin, 32));
                asm.fldp(128, v(2), v(3), createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arrayMax, 32));
                if (withMask()) {
                    asm.fldp(128, v(8), v(9), createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arrayMask, 32));
                }
                asm.fldp(128, v(4), v(5), createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arrayMin, 32));
                asm.fldp(128, v(6), v(7), createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arrayMax, 32));
                if (withMask()) {
                    int arrayA1 = strideA == strideMin ? 0 : 2;
                    int arrayA2 = strideA == strideMin ? 1 : 3;
                    if (strideMask == strideMax) {
                        asm.fldp(128, v(10), v(11), createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arrayMask, 32));
                    } else {
                        // special case for java.lang.String
                        assert strideMax == Stride.S2 && strideMask == Stride.S1 : Assertions.errorMessage(strideMax, strideMask);
                        asm.neon.uxtlVV(ElementSize.Byte, v(10), v(9));
                        asm.neon.uxtl2VV(ElementSize.Byte, v(11), v(9));
                        asm.neon.uxtl2VV(ElementSize.Byte, v(9), v(8));
                        asm.neon.uxtlVV(ElementSize.Byte, v(8), v(8));
                    }
                    // OR mask to arrayA
                    asm.neon.orrVVV(FullReg, v(arrayA1), v(arrayA1), v(8));
                    asm.neon.orrVVV(FullReg, v(arrayA2), v(arrayA2), v(9));
                }
                // EOR arrayA and arrayB, first 32 bytes
                asm.neon.eorVVV(FullReg, v(0), v(0), v(2));
                asm.neon.eorVVV(FullReg, v(1), v(1), v(3));
                if (withMask()) {
                    int arrayA3 = strideA == strideMin ? 4 : 6;
                    int arrayA4 = strideA == strideMin ? 5 : 7;
                    // OR mask to arrayA
                    asm.neon.orrVVV(FullReg, v(arrayA3), v(arrayA3), v(10));
                    asm.neon.orrVVV(FullReg, v(arrayA4), v(arrayA4), v(11));
                }
                // EOR arrayA and arrayB second 32 bytes
                asm.neon.eorVVV(FullReg, v(4), v(4), v(6));
                asm.neon.eorVVV(FullReg, v(5), v(5), v(7));
                // combine results
                asm.neon.orrVVV(FullReg, v(0), v(0), v(1));
                asm.neon.orrVVV(FullReg, v(4), v(4), v(5));
                asm.neon.orrVVV(FullReg, v(0), v(0), v(4));
                break;
            case 1:
                // 1 -> 2 byte comparison
                // de-interleaving load to separate lower and upper bytes
                asm.neon.ld2MultipleVV(FullReg, minESize, v(0), v(1), createStructureImmediatePostIndexAddress(LD2_MULTIPLE_2R, FullReg, minESize, arrayMax, 32));
                asm.neon.ld2MultipleVV(FullReg, minESize, v(2), v(3), createStructureImmediatePostIndexAddress(LD2_MULTIPLE_2R, FullReg, minESize, arrayMax, 32));
                if (withMask()) {
                    if (strideMask == strideMin) {
                        asm.fldp(128, v(6), v(7), createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arrayMask, 32));
                    } else {
                        asm.neon.ld2MultipleVV(FullReg, minESize, v(6), v(7), createStructureImmediatePostIndexAddress(LD2_MULTIPLE_2R, FullReg, minESize, arrayMask, 32));
                        asm.neon.ld2MultipleVV(FullReg, minESize, v(8), v(9), createStructureImmediatePostIndexAddress(LD2_MULTIPLE_2R, FullReg, minESize, arrayMask, 32));
                    }
                }
                asm.fldp(128, v(4), v(5), createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arrayMin, 32));
                if (withMask()) {
                    int arrayA1 = strideA == strideMax ? 0 : 4;
                    int arrayA2 = strideA == strideMax ? 2 : 5;
                    int arrayM1 = 6;
                    int arrayM2 = strideMask == strideMin ? 7 : 8;
                    // OR lower half elements to arrayA
                    asm.neon.orrVVV(FullReg, v(arrayA1), v(arrayA1), v(arrayM1));
                    asm.neon.orrVVV(FullReg, v(arrayA2), v(arrayA2), v(arrayM2));
                    if (strideMask == strideMax) {
                        if (strideA == strideMax) {
                            // OR upper elements to arrayA
                            asm.neon.orrVVV(FullReg, v(1), v(1), v(7));
                            asm.neon.orrVVV(FullReg, v(3), v(3), v(9));
                        } else {
                            // EOR upper elements to arrayB
                            asm.neon.eorVVV(FullReg, v(1), v(1), v(7));
                            asm.neon.eorVVV(FullReg, v(3), v(3), v(9));
                        }
                    }
                }
                // EOR lower bytes of arrayMax and arrayMin
                asm.neon.eorVVV(FullReg, v(0), v(0), v(4));
                asm.neon.eorVVV(FullReg, v(2), v(2), v(5));
                // OR with upper bytes of arrayMax to check if they are zero
                asm.neon.orrVVV(FullReg, v(0), v(0), v(1));
                asm.neon.orrVVV(FullReg, v(2), v(2), v(3));
                asm.neon.orrVVV(FullReg, v(0), v(0), v(2));
                break;
            case 2:
                // 1 -> 4 byte comparison
                asm.neon.ld4MultipleVVVV(FullReg, minESize, v(0), v(1), v(2), v(3), createStructureImmediatePostIndexAddress(LD4_MULTIPLE_4R, FullReg, minESize, arrayMax, 64));
                if (withMask()) {
                    if (strideMask == strideMin) {
                        asm.fldr(128, v(5), createImmediateAddress(128, AddressingMode.IMMEDIATE_POST_INDEXED, arrayMask, 16));
                    } else {
                        asm.neon.ld4MultipleVVVV(FullReg, minESize, v(5), v(6), v(7), v(8), createStructureImmediatePostIndexAddress(LD4_MULTIPLE_4R, FullReg, minESize, arrayMask, 64));
                    }
                }
                asm.fldr(128, v(4), createImmediateAddress(128, AddressingMode.IMMEDIATE_POST_INDEXED, arrayMin, 16));
                if (withMask()) {
                    int arrayA1 = strideA == strideMax ? 0 : 4;
                    int arrayM1 = 5;
                    // OR lower half elements to arrayA
                    asm.neon.orrVVV(FullReg, v(arrayA1), v(arrayA1), v(arrayM1));
                    if (strideMask == strideMax) {
                        if (strideA == strideMax) {
                            // OR upper elements to arrayA
                            asm.neon.orrVVV(FullReg, v(1), v(1), v(6));
                            asm.neon.orrVVV(FullReg, v(2), v(2), v(7));
                            asm.neon.orrVVV(FullReg, v(3), v(3), v(8));
                        } else {
                            // EOR upper elements to arrayB
                            asm.neon.eorVVV(FullReg, v(1), v(1), v(6));
                            asm.neon.eorVVV(FullReg, v(2), v(2), v(7));
                            asm.neon.eorVVV(FullReg, v(3), v(3), v(8));
                        }
                    }
                }
                // EOR lower bytes of arrayMax and arrayMin
                asm.neon.eorVVV(FullReg, v(0), v(0), v(4));
                // OR with upper bytes of arrayMax to check if they are zero
                asm.neon.orrVVV(FullReg, v(2), v(2), v(3));
                asm.neon.orrVVV(FullReg, v(0), v(0), v(1));
                asm.neon.orrVVV(FullReg, v(0), v(0), v(2));
                break;
            default:
                throw GraalError.unimplemented("comparison of " + strideMin + " to " + strideMax + " not implemented"); // ExcludeFromJacocoGeneratedReport
        }
        cmpZeroVector(asm, v(0), v(0), tmp);
    }

    private void tail32(AArch64MacroAssembler asm,
                    Stride strideMax,
                    Stride strideMin,
                    Stride strideA,
                    Stride strideMask,
                    Register arrayMax,
                    Register arrayMin,
                    Register arrayMask,
                    Register len,
                    Register tmp,
                    Label entry,
                    Label nextTail,
                    Label end) {

        asm.bind(entry);
        asm.adds(64, len, len, 32 >> strideMax.log2);
        asm.branchConditionally(ConditionFlag.MI, nextTail);

        ElementSize minESize = fromStride(strideMin);
        switch (strideMax.log2 - strideMin.log2) {
            case 0:
                // direct comparison
                asm.fldp(128, v(0), v(1), createPairBaseRegisterOnlyAddress(128, arrayMin));
                asm.fldp(128, v(2), v(3), createPairBaseRegisterOnlyAddress(128, arrayMax));
                if (withMask()) {
                    if (strideMask == strideMax) {
                        asm.fldp(128, v(8), v(9), createPairBaseRegisterOnlyAddress(128, arrayMask));
                    } else {
                        asm.fldr(128, v(8), createBaseRegisterOnlyAddress(128, arrayMask));
                    }
                }
                asm.add(64, arrayMin, arrayMin, len, ShiftType.LSL, strideMin.log2);
                asm.add(64, arrayMax, arrayMax, len, ShiftType.LSL, strideMax.log2);
                if (withMask()) {
                    asm.add(64, arrayMask, arrayMask, len, ShiftType.LSL, strideMask.log2);
                }
                asm.fldp(128, v(4), v(5), createPairBaseRegisterOnlyAddress(128, arrayMin));
                asm.fldp(128, v(6), v(7), createPairBaseRegisterOnlyAddress(128, arrayMax));
                if (withMask()) {
                    int arrayA1 = strideA == strideMin ? 0 : 2;
                    int arrayA2 = strideA == strideMin ? 1 : 3;
                    if (strideMask == strideMax) {
                        asm.fldp(128, v(10), v(11), createPairBaseRegisterOnlyAddress(128, arrayMask));
                    } else {
                        asm.fldr(128, v(10), createBaseRegisterOnlyAddress(128, arrayMask));
                        assert strideMax == Stride.S2 && strideMask == Stride.S1 : Assertions.errorMessage(strideMask, strideMax);
                        asm.neon.uxtl2VV(ElementSize.Byte, v(9), v(8));
                        asm.neon.uxtl2VV(ElementSize.Byte, v(11), v(10));
                        asm.neon.uxtlVV(ElementSize.Byte, v(8), v(8));
                        asm.neon.uxtlVV(ElementSize.Byte, v(10), v(10));
                    }
                    asm.neon.orrVVV(FullReg, v(arrayA1), v(arrayA1), v(8));
                    asm.neon.orrVVV(FullReg, v(arrayA2), v(arrayA2), v(9));
                }
                asm.neon.eorVVV(FullReg, v(0), v(0), v(2));
                asm.neon.eorVVV(FullReg, v(1), v(1), v(3));
                if (withMask()) {
                    int arrayA3 = strideA == strideMin ? 4 : 6;
                    int arrayA4 = strideA == strideMin ? 5 : 7;
                    asm.neon.orrVVV(FullReg, v(arrayA3), v(arrayA3), v(10));
                    asm.neon.orrVVV(FullReg, v(arrayA4), v(arrayA4), v(11));
                }
                asm.neon.eorVVV(FullReg, v(4), v(4), v(6));
                asm.neon.eorVVV(FullReg, v(5), v(5), v(7));
                asm.neon.orrVVV(FullReg, v(0), v(0), v(1));
                asm.neon.orrVVV(FullReg, v(4), v(4), v(5));
                asm.neon.orrVVV(FullReg, v(0), v(0), v(4));
                break;
            case 1:
                // 1 -> 2 byte comparison
                asm.neon.ld2MultipleVV(FullReg, minESize, v(0), v(1), createStructureNoOffsetAddress(arrayMax));
                asm.fldr(128, v(4), createBaseRegisterOnlyAddress(128, arrayMin));
                if (withMask()) {
                    if (strideMask == strideMin) {
                        asm.fldr(128, v(6), createBaseRegisterOnlyAddress(128, arrayMask));
                    } else {
                        asm.neon.ld2MultipleVV(FullReg, minESize, v(6), v(7), createStructureNoOffsetAddress(arrayMask));
                    }
                }
                asm.add(64, arrayMin, arrayMin, len, ShiftType.LSL, strideMin.log2);
                asm.add(64, arrayMax, arrayMax, len, ShiftType.LSL, strideMax.log2);
                if (withMask()) {
                    asm.add(64, arrayMask, arrayMask, len, ShiftType.LSL, strideMask.log2);
                }
                asm.neon.ld2MultipleVV(FullReg, minESize, v(2), v(3), createStructureNoOffsetAddress(arrayMax));
                asm.fldr(128, v(5), createBaseRegisterOnlyAddress(128, arrayMin));
                if (withMask()) {
                    if (strideMask == strideMin) {
                        asm.fldr(128, v(7), createBaseRegisterOnlyAddress(128, arrayMask));
                    } else {
                        asm.neon.ld2MultipleVV(FullReg, minESize, v(8), v(9), createStructureNoOffsetAddress(arrayMask));
                    }
                    int arrayA1 = strideA == strideMax ? 0 : 4;
                    int arrayA2 = strideA == strideMax ? 2 : 5;
                    int arrayM1 = 6;
                    int arrayM2 = strideMask == strideMin ? 7 : 8;
                    // OR lower half elements to arrayA
                    asm.neon.orrVVV(FullReg, v(arrayA1), v(arrayA1), v(arrayM1));
                    asm.neon.orrVVV(FullReg, v(arrayA2), v(arrayA2), v(arrayM2));
                    if (strideMask == strideMax) {
                        if (strideA == strideMax) {
                            // OR upper elements to arrayA
                            asm.neon.orrVVV(FullReg, v(1), v(1), v(7));
                            asm.neon.orrVVV(FullReg, v(3), v(3), v(9));
                        } else {
                            // EOR upper elements to arrayB
                            asm.neon.eorVVV(FullReg, v(1), v(1), v(7));
                            asm.neon.eorVVV(FullReg, v(3), v(3), v(9));
                        }
                    }
                }
                asm.neon.eorVVV(FullReg, v(0), v(0), v(4));
                asm.neon.eorVVV(FullReg, v(2), v(2), v(5));
                asm.neon.orrVVV(FullReg, v(0), v(0), v(1));
                asm.neon.orrVVV(FullReg, v(2), v(2), v(3));
                asm.neon.orrVVV(FullReg, v(0), v(0), v(2));
                break;
            case 2:
                // 1 -> 4 byte comparison
                asm.neon.ld2MultipleVV(FullReg, minESize.expand(), v(0), v(1), createStructureNoOffsetAddress(arrayMax));
                asm.fldr(64, v(4), createBaseRegisterOnlyAddress(64, arrayMin));
                if (withMask()) {
                    if (strideMask == strideMin) {
                        asm.fldr(64, v(6), createBaseRegisterOnlyAddress(64, arrayMask));
                    } else {
                        asm.neon.ld2MultipleVV(FullReg, minESize.expand(), v(6), v(7), createStructureNoOffsetAddress(arrayMask));
                    }
                }
                asm.add(64, arrayMin, arrayMin, len, ShiftType.LSL, strideMin.log2);
                asm.add(64, arrayMax, arrayMax, len, ShiftType.LSL, strideMax.log2);
                if (withMask()) {
                    asm.add(64, arrayMask, arrayMask, len, ShiftType.LSL, strideMask.log2);
                }
                asm.neon.ld2MultipleVV(FullReg, minESize.expand(), v(2), v(3), createStructureNoOffsetAddress(arrayMax));
                asm.fldr(64, v(5), createBaseRegisterOnlyAddress(64, arrayMin));
                asm.neon.uxtlVV(minESize, v(4), v(4));
                asm.neon.uxtlVV(minESize, v(5), v(5));
                if (withMask()) {
                    if (strideMask == strideMin) {
                        asm.fldr(64, v(7), createBaseRegisterOnlyAddress(64, arrayMask));
                        asm.neon.uxtlVV(minESize, v(6), v(6));
                        asm.neon.uxtlVV(minESize, v(7), v(7));
                    } else {
                        asm.neon.ld2MultipleVV(FullReg, minESize.expand(), v(8), v(9), createStructureNoOffsetAddress(arrayMask));
                    }
                    int arrayA1 = strideA == strideMax ? 0 : 4;
                    int arrayA2 = strideA == strideMax ? 2 : 5;
                    int arrayM1 = 6;
                    int arrayM2 = strideMask == strideMin ? 7 : 8;
                    // OR lower elements to arrayA
                    asm.neon.orrVVV(FullReg, v(arrayA1), v(arrayA1), v(arrayM1));
                    asm.neon.orrVVV(FullReg, v(arrayA2), v(arrayA2), v(arrayM2));
                    if (strideMask == strideMax) {
                        if (strideA == strideMax) {
                            // OR upper elements to arrayA
                            asm.neon.orrVVV(FullReg, v(1), v(1), v(7));
                            asm.neon.orrVVV(FullReg, v(3), v(3), v(9));
                        } else {
                            // EOR upper elements to arrayB
                            asm.neon.eorVVV(FullReg, v(1), v(1), v(7));
                            asm.neon.eorVVV(FullReg, v(3), v(3), v(9));
                        }
                    }
                }
                asm.neon.eorVVV(FullReg, v(0), v(0), v(4));
                asm.neon.eorVVV(FullReg, v(2), v(2), v(5));
                asm.neon.orrVVV(FullReg, v(0), v(0), v(1));
                asm.neon.orrVVV(FullReg, v(2), v(2), v(3));
                asm.neon.orrVVV(FullReg, v(0), v(0), v(2));
                break;
            default:
                throw GraalError.unimplemented("comparison of " + strideMin + " to " + strideMax + " not implemented"); // ExcludeFromJacocoGeneratedReport
        }
        cmpZeroVector(asm, v(0), v(0), tmp);
        asm.jmp(end);
    }

    private void tail16(AArch64MacroAssembler asm,
                    Stride strideA,
                    Stride strideB,
                    Stride strideM,
                    Stride strideMax,
                    Stride strideMin,
                    Register arrayA,
                    Register arrayB,
                    Register arrayM,
                    Register len,
                    Register tmp,
                    Label entry,
                    Label nextTail,
                    Label end) {
        Register vecArrayA1 = v(0);
        Register vecArrayA2 = v(1);
        Register vecArrayB1 = v(2);
        Register vecArrayB2 = v(3);
        Register vecArrayM1 = withMask() ? v(4) : null;
        Register vecArrayM2 = withMask() ? v(5) : null;
        tailLoad(asm, strideA, strideB, strideM, strideMax, arrayA, arrayB, arrayM, len, vecArrayA1, vecArrayA2, vecArrayB1, vecArrayB2, vecArrayM1, vecArrayM2, entry, nextTail, 16);
        ElementSize minESize = fromStride(strideMin);
        Register vecArrayMin1 = strideA == strideMin ? vecArrayA1 : vecArrayB1;
        Register vecArrayMin2 = strideA == strideMin ? vecArrayA2 : vecArrayB2;
        switch (strideMax.log2 - strideMin.log2) {
            case 0:
                // direct comparison
                if (withMask() && strideM.value < strideMin.value) {
                    asm.neon.uxtlVV(fromStride(strideM), vecArrayM1, vecArrayM1);
                    asm.neon.uxtlVV(fromStride(strideM), vecArrayM2, vecArrayM2);
                }
                break;
            case 1:
                asm.neon.uxtlVV(minESize, vecArrayMin1, vecArrayMin1);
                asm.neon.uxtlVV(minESize, vecArrayMin2, vecArrayMin2);
                if (withMask() && strideM == strideMin) {
                    asm.neon.uxtlVV(minESize, vecArrayM1, vecArrayM1);
                    asm.neon.uxtlVV(minESize, vecArrayM2, vecArrayM2);
                }
                // 1 -> 2 byte comparison
                break;
            case 2:
                asm.neon.uxtlVV(minESize, vecArrayMin1, vecArrayMin1);
                asm.neon.uxtlVV(minESize, vecArrayMin2, vecArrayMin2);
                asm.neon.uxtlVV(minESize.expand(), vecArrayMin1, vecArrayMin1);
                asm.neon.uxtlVV(minESize.expand(), vecArrayMin2, vecArrayMin2);
                if (withMask() && strideM == strideMin) {
                    asm.neon.uxtlVV(minESize, vecArrayM1, vecArrayM1);
                    asm.neon.uxtlVV(minESize, vecArrayM2, vecArrayM2);
                    asm.neon.uxtlVV(minESize.expand(), vecArrayM1, vecArrayM1);
                    asm.neon.uxtlVV(minESize.expand(), vecArrayM2, vecArrayM2);
                }
                // 1 -> 4 byte comparison
                break;
            default:
                throw GraalError.unimplemented("comparison of " + strideMin + " to " + strideMax + " not implemented"); // ExcludeFromJacocoGeneratedReport
        }
        if (withMask()) {
            asm.neon.orrVVV(FullReg, vecArrayA1, vecArrayA1, vecArrayM1);
            asm.neon.orrVVV(FullReg, vecArrayA2, vecArrayA2, vecArrayM2);
        }
        asm.neon.eorVVV(FullReg, vecArrayA1, vecArrayA1, vecArrayB1);
        asm.neon.eorVVV(FullReg, vecArrayA2, vecArrayA2, vecArrayB2);
        asm.neon.orrVVV(FullReg, vecArrayA1, vecArrayA1, vecArrayA2);

        cmpZeroVector(asm, vecArrayA1, vecArrayA1, tmp);
        asm.jmp(end);
    }

    private void tailLoad(AArch64MacroAssembler asm,
                    Stride strideA,
                    Stride strideB,
                    Stride strideM,
                    Stride strideMax,
                    Register arrayA,
                    Register arrayB,
                    Register arrayM,
                    Register len,
                    Register vecArrayA1,
                    Register vecArrayA2,
                    Register vecArrayB1,
                    Register vecArrayB2,
                    Register vecArrayM1,
                    Register vecArrayM2,
                    Label entry,
                    Label nextTail,
                    int nBytes) {
        int bitsA = loadBits(strideA, strideMax, nBytes);
        int bitsB = loadBits(strideB, strideMax, nBytes);
        int bitsM = loadBits(strideM, strideMax, nBytes);
        asm.bind(entry);
        // check if length is big enough for current load size
        asm.adds(64, len, len, nBytes >> strideMax.log2);
        asm.branchConditionally(ConditionFlag.MI, nextTail);
        // load from start of arrays
        asm.fldr(bitsA, vecArrayA1, createBaseRegisterOnlyAddress(bitsA, arrayA));
        asm.fldr(bitsB, vecArrayB1, createBaseRegisterOnlyAddress(bitsB, arrayB));
        if (withMask()) {
            asm.fldr(bitsM, vecArrayM1, createBaseRegisterOnlyAddress(bitsM, arrayM));
        }
        asm.add(64, arrayA, arrayA, len, ShiftType.LSL, strideA.log2);
        asm.add(64, arrayB, arrayB, len, ShiftType.LSL, strideB.log2);
        if (withMask()) {
            asm.add(64, arrayM, arrayM, len, ShiftType.LSL, strideM.log2);
        }
        // load from end of arrays
        asm.fldr(bitsA, vecArrayA2, createBaseRegisterOnlyAddress(bitsA, arrayA));
        asm.fldr(bitsB, vecArrayB2, createBaseRegisterOnlyAddress(bitsB, arrayB));
        if (withMask()) {
            asm.fldr(bitsM, vecArrayM2, createBaseRegisterOnlyAddress(bitsM, arrayM));
        }
    }

    private void tailLessThan16(AArch64MacroAssembler asm,
                    Stride strideA,
                    Stride strideB,
                    Stride strideM,
                    Stride strideMax,
                    Register arrayA,
                    Register arrayB,
                    Register arrayM,
                    Register len,
                    Register tmp,
                    Register ret,
                    Label entry,
                    Label nextTail,
                    Label end,
                    int nBytes) {
        Register vecArrayA1 = v(0);
        Register vecArrayA2 = v(1);
        Register vecArrayB1 = v(2);
        Register vecArrayB2 = v(3);
        Register vecArrayM1 = withMask() ? v(4) : null;
        Register vecArrayM2 = withMask() ? v(5) : null;
        assert nBytes <= 8 : nBytes;
        int bitsA = loadBits(strideA, strideMax, nBytes);
        int bitsB = loadBits(strideB, strideMax, nBytes);
        int bitsM = loadBits(strideM, strideMax, nBytes);
        if (strideMax.value < nBytes) {
            // load array start and end vectors
            tailLoad(asm, strideA, strideB, strideM, strideMax, arrayA, arrayB, arrayM, len, vecArrayA1, vecArrayA2, vecArrayB1, vecArrayB2, vecArrayM1, vecArrayM2, entry, nextTail, nBytes);
            // combine results into single vectors
            asm.neon.insXX(ElementSize.fromSize(bitsA), vecArrayA1, 1, vecArrayA2, 0);
            asm.neon.insXX(ElementSize.fromSize(bitsB), vecArrayB1, 1, vecArrayB2, 0);
            if (withMask()) {
                asm.neon.insXX(ElementSize.fromSize(bitsM), vecArrayM1, 1, vecArrayM2, 0);
            }
            // expand elements if necessary
            tailExtend(asm, strideA, strideMax, vecArrayA1);
            tailExtend(asm, strideB, strideMax, vecArrayB1);
            if (withMask()) {
                tailExtend(asm, strideM, strideMax, vecArrayM1);
                asm.neon.orrVVV(FullReg, vecArrayA1, vecArrayA1, vecArrayM1);
            }
            asm.neon.eorVVV(FullReg, vecArrayA1, vecArrayA1, vecArrayB1);
            cmpZeroVector(asm, vecArrayA1, vecArrayA1, tmp);
        } else if (strideMax.value == nBytes) {
            asm.bind(entry);
            // tail for length == 1
            // at this point, len is either -1 or -2.
            // if len is -2, it was originally 0, otherwise it was 1.
            asm.compare(64, len, -2);
            asm.branchConditionally(ConditionFlag.EQ, end);
            asm.ldr(strideA.getBitCount(), tmp, createBaseRegisterOnlyAddress(strideA.getBitCount(), arrayA));
            if (withMask()) {
                asm.ldr(strideM.getBitCount(), ret, createBaseRegisterOnlyAddress(strideM.getBitCount(), arrayM));
                asm.orr(64, tmp, tmp, ret);
            }
            asm.ldr(strideB.getBitCount(), ret, createBaseRegisterOnlyAddress(strideB.getBitCount(), arrayB));
            asm.cmp(64, tmp, ret);
        }
        asm.jmp(end);
    }

    private static void tailExtend(AArch64MacroAssembler asm, Stride stride, Stride strideMax, Register vecArray) {
        switch (strideMax.log2 - stride.log2) {
            case 0:
                break;
            case 1:
                asm.neon.uxtlVV(fromStride(stride), vecArray, vecArray);
                break;
            case 2:
                asm.neon.uxtlVV(fromStride(stride), vecArray, vecArray);
                asm.neon.uxtlVV(fromStride(stride).expand(), vecArray, vecArray);
                break;
            default:
                throw GraalError.shouldNotReachHere(strideMax.log2 + " " + stride.log2); // ExcludeFromJacocoGeneratedReport
        }
    }

    private static int loadBits(Stride strideA, Stride strideMax, int nBytes) {
        return (nBytes * Byte.SIZE) >> (strideMax.log2 - strideA.log2);
    }
}
