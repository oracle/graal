/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize.fromStride;
import static jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler.PREFERRED_BRANCH_TARGET_ALIGNMENT;
import static jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.aarch64.AArch64.CPU;
import static jdk.vm.ci.aarch64.AArch64.SIMD;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isIllegal;

import java.util.Arrays;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ShiftType;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.code.DataSection;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.StrideUtil;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * Finds the index {@code i} of the first differing elements of {@code arrayA} and {@code arrayB}
 * and returns {@code arrayA[i] - arrayB[i]}. If no such index exists, returns {@code 0}.
 */
@Opcode("ARRAY_REGION_COMPARE_TO")
public final class AArch64ArrayRegionCompareToOp extends AArch64ComplexVectorOp {
    public static final LIRInstructionClass<AArch64ArrayRegionCompareToOp> TYPE = LIRInstructionClass.create(AArch64ArrayRegionCompareToOp.class);

    private final Stride argStrideA;
    private final Stride argStrideB;

    @Def({REG}) protected Value resultValue;
    @Alive({REG}) protected Value arrayAValue;
    @Alive({REG}) protected Value offsetAValue;
    @Alive({REG}) protected Value arrayBValue;
    @Alive({REG}) protected Value offsetBValue;
    @Alive({REG}) protected Value lengthValue;
    @Alive({REG, ILLEGAL}) private Value dynamicStridesValue;
    @Temp({REG}) protected AllocatableValue[] temp;
    @Temp({REG}) protected AllocatableValue[] vectorTemp;

    public AArch64ArrayRegionCompareToOp(LIRGeneratorTool tool, Stride strideA, Stride strideB, Value result, Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value length,
                    Value dynamicStrides) {
        super(TYPE);
        this.argStrideA = strideA;
        this.argStrideB = strideB;

        GraalError.guarantee(result.getPlatformKind() == AArch64Kind.DWORD, "int value expected");
        GraalError.guarantee(arrayA.getPlatformKind() == AArch64Kind.QWORD && arrayA.getPlatformKind() == arrayB.getPlatformKind(), "pointer value expected");
        GraalError.guarantee(offsetA.getPlatformKind() == AArch64Kind.QWORD, "long value expected");
        GraalError.guarantee(offsetB.getPlatformKind() == AArch64Kind.QWORD, "long value expected");
        GraalError.guarantee(length.getPlatformKind() == AArch64Kind.DWORD, "int value expected");

        this.resultValue = result;
        this.arrayAValue = arrayA;
        this.offsetAValue = offsetA;
        this.arrayBValue = arrayB;
        this.offsetBValue = offsetB;
        this.lengthValue = length;
        this.dynamicStridesValue = dynamicStrides == null ? Value.ILLEGAL : dynamicStrides;

        temp = allocateTempRegisters(tool, withDynamicStrides() ? 3 : 2);
        vectorTemp = allocateVectorRegisters(tool, 7);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        try (AArch64MacroAssembler.ScratchRegister sc1 = masm.getScratchRegister(); AArch64MacroAssembler.ScratchRegister sc2 = masm.getScratchRegister()) {
            Register arrayA = sc1.getRegister();
            Register arrayB = sc2.getRegister();
            Register length = asRegister(temp[0]);
            Register tmp = asRegister(temp[1]);
            Register ret = asRegister(resultValue);
            Label end = new Label();

            // Load array base addresses.
            masm.add(64, arrayA, asRegister(arrayAValue), asRegister(offsetAValue));
            masm.add(64, arrayB, asRegister(arrayBValue), asRegister(offsetBValue));

            masm.mov(32, length, asRegister(lengthValue));

            if (withDynamicStrides()) {
                Label[] variants = new Label[9];
                for (int i = 0; i < variants.length; i++) {
                    variants[i] = new Label();
                }
                Register tmp2 = asRegister(temp[2]);
                masm.mov(32, tmp2, asRegister(dynamicStridesValue));
                AArch64ControlFlow.RangeTableSwitchOp.emitJumpTable(crb, masm, tmp, tmp2, 0, 8, Arrays.stream(variants));

                for (Stride stride1 : new Stride[]{Stride.S1, Stride.S2, Stride.S4}) {
                    for (Stride stride2 : new Stride[]{Stride.S1, Stride.S2, Stride.S4}) {
                        masm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
                        masm.bind(variants[StrideUtil.getDirectStubCallIndex(stride1, stride2)]);
                        emitArrayCompare(crb, masm, stride1, stride2, arrayA, arrayB, length, tmp, ret, end);
                        masm.jmp(end);
                    }
                }
            } else {
                emitArrayCompare(crb, masm, argStrideA, argStrideB, arrayA, arrayB, length, tmp, ret, end);
            }
            masm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
            masm.bind(end);
        }
    }

    private boolean withDynamicStrides() {
        return !isIllegal(dynamicStridesValue);
    }

    private void emitArrayCompare(CompilationResultBuilder crb, AArch64MacroAssembler asm, Stride strideA, Stride strideB, Register arrayA, Register arrayB, Register len, Register tmp, Register ret,
                    Label end) {
        Label tailLessThan32 = new Label();
        Label tailLessThan16 = new Label();
        Label tailLessThan8 = new Label();
        Label tailLessThan4 = new Label();
        Label tailLessThan2 = new Label();

        Label returnV1 = new Label();
        Label vectorLoop = new Label();
        Label diffFound = new Label();

        Register vecArrayA1 = asRegister(vectorTemp[0]);
        Register vecArrayA2 = asRegister(vectorTemp[1]);
        Register vecArrayB1 = asRegister(vectorTemp[2]);
        Register vecArrayB2 = asRegister(vectorTemp[3]);
        Register vecTmp1 = asRegister(vectorTemp[4]);
        Register vecTmp2 = asRegister(vectorTemp[5]);
        Register vecMask = asRegister(vectorTemp[6]);

        Register maxStrideArray = strideA.value < strideB.value ? arrayB : arrayA;
        Register minStrideArray = strideA.value < strideB.value ? arrayA : arrayB;

        Stride strideMax = Stride.max(strideA, strideB);
        Stride strideMin = Stride.min(strideA, strideB);

        byte[] maskIndices = new byte[16];
        for (int i = 0; i < maskIndices.length; i++) {
            maskIndices[i] = (byte) i;
        }

        DataSection.Data maskData = writeToDataSection(crb, maskIndices);
        loadDataSectionAddress(crb, asm, tmp, maskData);
        // prepare a mask vector containing ascending byte index values
        asm.fldr(128, vecMask, AArch64Address.createBaseRegisterOnlyAddress(128, tmp));

        // subtract 32 from len. if the result is negative, jump to branch for less than 32 bytes
        asm.subs(64, len, len, 32 >> strideMax.log2);
        asm.branchConditionally(ConditionFlag.MI, tailLessThan32);

        Register refAddress = len;
        asm.add(64, refAddress, maxStrideArray, len, ShiftType.LSL, strideMax.log2);

        // 32 byte loop
        asm.align(PREFERRED_LOOP_ALIGNMENT);
        asm.bind(vectorLoop);
        // load 2 vectors
        loadAndExtend(asm, strideMax, strideA, arrayA, vecArrayA1, vecArrayA2);
        loadAndExtend(asm, strideMax, strideB, arrayB, vecArrayB1, vecArrayB2);
        // check if they are equal
        asm.neon.eorVVV(FullReg, vecTmp1, vecArrayA1, vecArrayB1);
        asm.neon.eorVVV(FullReg, vecTmp2, vecArrayA2, vecArrayB2);
        asm.neon.orrVVV(FullReg, vecTmp2, vecTmp2, vecTmp1);
        cbnzVector(asm, ElementSize.Byte, vecTmp2, vecTmp2, tmp, false, diffFound);
        // if so, continue
        asm.cmp(64, maxStrideArray, refAddress);
        asm.branchConditionally(ConditionFlag.LO, vectorLoop);

        // 32 byte loop tail
        asm.sub(64, tmp, maxStrideArray, refAddress);
        asm.mov(64, maxStrideArray, refAddress);
        asm.sub(64, minStrideArray, minStrideArray, tmp, ShiftType.LSR, strideMax.log2 - strideMin.log2);

        loadAndExtend(asm, strideMax, strideA, arrayA, vecArrayA1, vecArrayA2);
        loadAndExtend(asm, strideMax, strideB, arrayB, vecArrayB1, vecArrayB2);
        asm.neon.eorVVV(FullReg, vecTmp1, vecArrayA1, vecArrayB1);
        asm.neon.eorVVV(FullReg, vecTmp2, vecArrayA2, vecArrayB2);
        asm.neon.orrVVV(FullReg, vecTmp2, vecTmp2, vecTmp1);
        cbnzVector(asm, ElementSize.Byte, vecTmp2, vecTmp2, tmp, false, diffFound);
        asm.mov(64, ret, zr);
        asm.jmp(end);

        // tail for 16 - 31 bytes
        tailLoad2Vec(asm, strideA, strideB, strideMax, arrayA, arrayB, len, vecArrayA1, vecArrayA2, vecArrayB1, vecArrayB2, tailLessThan32, tailLessThan16, 16);
        asm.neon.eorVVV(FullReg, vecTmp1, vecArrayA1, vecArrayB1);
        asm.jmp(diffFound);
        // tail for 8 - 15 bytes
        tailLoad1Vec(asm, strideA, strideB, strideMax, arrayA, arrayB, len, vecArrayA1, vecArrayA2, vecArrayB1, vecArrayB2, tmp, ret, tailLessThan16, tailLessThan8, returnV1, end, 8);
        // tail for 4 - 7 bytes
        tailLoad1Vec(asm, strideA, strideB, strideMax, arrayA, arrayB, len, vecArrayA1, vecArrayA2, vecArrayB1, vecArrayB2, tmp, ret, tailLessThan8, tailLessThan4, returnV1, end, 4);
        // tail for 2 - 3 bytes
        tailLoad1Vec(asm, strideA, strideB, strideMax, arrayA, arrayB, len, vecArrayA1, vecArrayA2, vecArrayB1, vecArrayB2, tmp, ret, tailLessThan4, tailLessThan2, returnV1, end, 2);
        // tail for 0 - 1 bytes
        tailLoad1Vec(asm, strideA, strideB, strideMax, arrayA, arrayB, len, vecArrayA1, vecArrayA2, vecArrayB1, vecArrayB2, tmp, ret, tailLessThan2, null, returnV1, end, 1);

        asm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
        asm.bind(diffFound);
        // check if vecArrayA1 and vecArrayB1 are equal
        cbnzVector(asm, ElementSize.Byte, vecTmp1, vecTmp1, tmp, false, returnV1);
        calcReturnValue(asm, ret, vecArrayA2, vecArrayB2, vecArrayA1, vecArrayB1, vecMask, strideMax);
        asm.jmp(end);

        asm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
        asm.bind(returnV1);
        calcReturnValue(asm, ret, vecArrayA1, vecArrayB1, vecArrayA2, vecArrayB2, vecMask, strideMax);
    }

    /**
     * Load elements of size {@code strideSrc} from {@code arrayAddress} and zero-extend them to
     * {@code strideDst} to fill vector registers {@code vectorLo} and {@code vectorHi}. After
     * loading, {@code arrayAddress} is increased by the amount of bytes loaded.
     * <p>
     * For example: if both strides are {@link Stride#S1}, 32 bytes from {@code arrayAddress} are
     * loaded into the vector registers and {@code arrayAddress} is increased by 32. If
     * {@code strideSrc} is {@link Stride#S1} and {@code strideDst} is {@link Stride#S2}, 16 bytes
     * are loaded, the lower 8 bytes are zero-extended (individually) to 16 bit width and stored in
     * {@code vectorLo}, the upper 8 bytes are zero-extended and stored in {@code vectorHi}, and
     * {@code arrayAddress} is increased by 16.
     */
    private static void loadAndExtend(AArch64MacroAssembler asm, Stride strideDst, Stride strideSrc, Register arrayAddress, Register vectorLo, Register vectorHi) {
        assert arrayAddress.getRegisterCategory().equals(CPU);
        assert vectorLo.getRegisterCategory().equals(SIMD);
        assert vectorHi.getRegisterCategory().equals(SIMD);
        switch (strideDst.log2 - strideSrc.log2) {
            case 0:
                // load 32 bytes into two 16-byte vector registers
                asm.fldp(128, vectorLo, vectorHi, AArch64Address.createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arrayAddress, 32));
                break;
            case 1:
                // load 16 bytes
                asm.fldr(128, vectorLo, AArch64Address.createImmediateAddress(128, AddressingMode.IMMEDIATE_POST_INDEXED, arrayAddress, 16));
                // extend upper half and store in vectorHi
                asm.neon.uxtl2VV(fromStride(strideSrc), vectorHi, vectorLo);
                // extend lower half
                asm.neon.uxtlVV(fromStride(strideSrc), vectorLo, vectorLo);
                break;
            case 2:
                // load 8 bytes
                asm.fldr(64, vectorLo, AArch64Address.createImmediateAddress(64, AddressingMode.IMMEDIATE_POST_INDEXED, arrayAddress, 8));
                // extend to 16 bytes
                asm.neon.uxtlVV(fromStride(strideSrc), vectorLo, vectorLo);
                // extend again
                asm.neon.uxtl2VV(fromStride(strideSrc).expand(), vectorHi, vectorLo);
                asm.neon.uxtlVV(fromStride(strideSrc).expand(), vectorLo, vectorLo);
                break;
            default:
                throw GraalError.unimplemented("conversion from " + strideSrc + " to " + strideDst + " not implemented"); // ExcludeFromJacocoGeneratedReport
        }
    }

    private static void calcReturnValue(AArch64MacroAssembler asm, Register ret, Register vecArrayA, Register vecArrayB, Register vecTmp, Register vecIndex, Register vecMask, Stride strideMax) {
        // set all equal bytes to 0xff, others to 0x00
        asm.neon.cmeqVVV(FullReg, fromStride(strideMax), vecTmp, vecArrayA, vecArrayB);
        // OR with the ascending index mask, this will replace all non-equal bytes with their
        // corresponding byte index, and all equal bytes with 0xff
        asm.neon.orrVVV(FullReg, vecIndex, vecMask, vecTmp);
        // Get the unsigned minimum. This will yield the index of the first non-equal bytes, since
        // all equal ones are filled with 0xff
        asm.neon.uminvSV(FullReg, fromStride(strideMax), vecIndex, vecIndex);
        if (strideMax == Stride.S4) {
            // calculate the difference of array elements
            asm.neon.subVVV(FullReg, fromStride(strideMax), vecTmp, vecArrayA, vecArrayB);
            // load the result for the previously calculated index
            asm.neon.tblVVV(FullReg, vecTmp, vecTmp, vecIndex);
            // return
            asm.neon.moveFromIndex(ElementSize.Word, fromStride(strideMax), ret, vecTmp, 0);
        } else {
            // if stride is not 4 bytes, we have to account for integer overflows, so we first get
            // the relevant array elements and subtract with USUBL
            asm.neon.tblVVV(FullReg, vecArrayA, vecArrayA, vecIndex);
            asm.neon.tblVVV(FullReg, vecArrayB, vecArrayB, vecIndex);
            asm.neon.usublVVV(fromStride(strideMax), vecTmp, vecArrayA, vecArrayB);
            asm.neon.moveFromIndex(ElementSize.Word, fromStride(strideMax).expand(), ret, vecTmp, 0);
        }
    }

    private static void tailLoad(AArch64MacroAssembler asm, Stride strideA, Stride strideB, Stride strideMax,
                    Register arrayA, Register arrayB, Register len,
                    Register vecArrayA1, Register vecArrayA2, Register vecArrayB1, Register vecArrayB2,
                    Label entry, Label nextTail, int nBytes) {
        int bitsA = loadBits(strideA, strideMax, nBytes);
        int bitsB = loadBits(strideB, strideMax, nBytes);
        asm.bind(entry);
        // check if length is big enough for current load size
        asm.adds(64, len, len, nBytes >> strideMax.log2);
        asm.branchConditionally(ConditionFlag.MI, nextTail);
        // load from start of arrays
        asm.fldr(bitsA, vecArrayA1, AArch64Address.createBaseRegisterOnlyAddress(bitsA, arrayA));
        asm.fldr(bitsB, vecArrayB1, AArch64Address.createBaseRegisterOnlyAddress(bitsB, arrayB));
        asm.add(64, arrayA, arrayA, len, ShiftType.LSL, strideA.log2);
        asm.add(64, arrayB, arrayB, len, ShiftType.LSL, strideB.log2);
        // load from end of arrays
        asm.fldr(bitsA, vecArrayA2, AArch64Address.createBaseRegisterOnlyAddress(bitsA, arrayA));
        asm.fldr(bitsB, vecArrayB2, AArch64Address.createBaseRegisterOnlyAddress(bitsB, arrayB));
    }

    private static void tailLoad1Vec(AArch64MacroAssembler asm, Stride strideA, Stride strideB, Stride strideMax,
                    Register arrayA, Register arrayB, Register len,
                    Register vecArrayA1, Register vecArrayA2, Register vecArrayB1, Register vecArrayB2,
                    Register tmp, Register ret, Label entry, Label nextTail, Label tailLoaded, Label end, int nBytes) {
        assert nBytes <= 8 : nBytes;
        int bitsA = loadBits(strideA, strideMax, nBytes);
        int bitsB = loadBits(strideB, strideMax, nBytes);
        if (strideMax.value < nBytes) {
            // load array start and end vectors
            tailLoad(asm, strideA, strideB, strideMax, arrayA, arrayB, len, vecArrayA1, vecArrayA2, vecArrayB1, vecArrayB2, entry, nextTail, nBytes);
            // combine results into single vectors
            asm.neon.insXX(ElementSize.fromSize(bitsA), vecArrayA1, 1, vecArrayA2, 0);
            asm.neon.insXX(ElementSize.fromSize(bitsB), vecArrayB1, 1, vecArrayB2, 0);
            // expand elements if necessary
            if (strideA.value < strideMax.value) {
                tailExtend(asm, strideA, strideMax, vecArrayA1);
            } else if (strideB.value < strideMax.value) {
                tailExtend(asm, strideB, strideMax, vecArrayB1);
            }
            asm.jmp(tailLoaded);
        } else if (strideMax.value == nBytes) {
            asm.bind(entry);
            asm.mov(64, ret, zr);
            // tail for length == 1
            asm.adds(64, len, len, nBytes >> strideMax.log2);
            asm.branchConditionally(ConditionFlag.MI, end);
            asm.ldr(strideA.getBitCount(), tmp, AArch64Address.createBaseRegisterOnlyAddress(strideA.getBitCount(), arrayA));
            asm.ldr(strideB.getBitCount(), ret, AArch64Address.createBaseRegisterOnlyAddress(strideB.getBitCount(), arrayB));
            asm.sub(64, ret, tmp, ret);
            asm.jmp(end);
        }
    }

    private static void tailLoad2Vec(AArch64MacroAssembler asm, Stride strideA, Stride strideB, Stride strideMax,
                    Register arrayA, Register arrayB, Register len,
                    Register vecArrayA1, Register vecArrayA2, Register vecArrayB1, Register vecArrayB2,
                    Label entry, Label nextTail, int nBytes) {
        tailLoad(asm, strideA, strideB, strideMax, arrayA, arrayB, len, vecArrayA1, vecArrayA2, vecArrayB1, vecArrayB2, entry, nextTail, nBytes);
        if (strideA.value < strideMax.value) {
            tailExtend(asm, strideA, strideMax, vecArrayA1);
            tailExtend(asm, strideA, strideMax, vecArrayA2);
        } else if (strideB.value < strideMax.value) {
            tailExtend(asm, strideB, strideMax, vecArrayB1);
            tailExtend(asm, strideB, strideMax, vecArrayB2);
        }
    }

    private static void tailExtend(AArch64MacroAssembler asm, Stride stride, Stride strideMax, Register vecArray) {
        switch (strideMax.log2 - stride.log2) {
            case 1:
                asm.neon.uxtlVV(fromStride(stride), vecArray, vecArray);
                break;
            case 2:
                asm.neon.uxtlVV(fromStride(stride), vecArray, vecArray);
                asm.neon.uxtlVV(fromStride(stride), vecArray, vecArray);
                break;
            default:
                throw GraalError.shouldNotReachHere(strideMax.log2 + " " + stride.log2); // ExcludeFromJacocoGeneratedReport
        }
    }

    private static int loadBits(Stride strideA, Stride strideMax, int nBytes) {
        return (nBytes << 3) >> (strideMax.log2 - strideA.log2);
    }
}
